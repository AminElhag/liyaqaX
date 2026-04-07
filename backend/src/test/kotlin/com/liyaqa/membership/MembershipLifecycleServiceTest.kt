package com.liyaqa.membership

import com.liyaqa.audit.AuditService
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.invoice.Invoice
import com.liyaqa.invoice.InvoiceService
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.dto.FreezeMembershipRequest
import com.liyaqa.membership.dto.RenewMembershipRequest
import com.liyaqa.membership.dto.TerminateMembershipRequest
import com.liyaqa.membership.dto.UnfreezeMembershipRequest
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.payment.Payment
import com.liyaqa.payment.PaymentRepository
import com.liyaqa.payment.PaymentService
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class MembershipLifecycleServiceTest {
    @Mock lateinit var membershipRepository: MembershipRepository

    @Mock lateinit var membershipPlanRepository: MembershipPlanRepository

    @Mock lateinit var freezePeriodRepository: FreezePeriodRepository

    @Mock lateinit var memberRepository: MemberRepository

    @Mock lateinit var organizationRepository: OrganizationRepository

    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var userRepository: UserRepository

    @Mock lateinit var paymentRepository: PaymentRepository

    @Mock lateinit var paymentService: PaymentService

    @Mock lateinit var invoiceService: InvoiceService

    @Mock lateinit var auditService: AuditService

    @Mock lateinit var eventPublisher: org.springframework.context.ApplicationEventPublisher

    @InjectMocks lateinit var service: MembershipService

    private val org = Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com")
    private val club = Club(organizationId = org.id, nameAr = "نادي", nameEn = "Elixir Gym")

    private fun member(status: String = "active") =
        Member(
            organizationId = org.id,
            clubId = club.id,
            branchId = 1L,
            userId = 1L,
            firstNameAr = "أحمد",
            firstNameEn = "Ahmed",
            lastNameAr = "الرشيدي",
            lastNameEn = "Al-Rashidi",
            phone = "+966501234567",
            membershipStatus = status,
        )

    private fun plan(
        freezeAllowed: Boolean = true,
        maxFreezeDays: Int = 30,
    ) = MembershipPlan(
        organizationId = org.id,
        clubId = club.id,
        nameAr = "شهري أساسي",
        nameEn = "Basic Monthly",
        priceHalalas = 15000,
        durationDays = 30,
        gracePeriodDays = 3,
        freezeAllowed = freezeAllowed,
        maxFreezeDays = maxFreezeDays,
    )

    private fun callerUser() =
        User(
            email = "reception@elixir.com",
            passwordHash = "hashed",
            organizationId = org.id,
            clubId = club.id,
        )

    private fun activeMembership(
        plan: MembershipPlan,
        status: String = "active",
        freezeDaysUsed: Int = 0,
    ) = Membership(
        organizationId = org.id,
        clubId = club.id,
        branchId = 1L,
        memberId = 1L,
        planId = plan.id,
        membershipStatus = status,
        startDate = LocalDate.now().minusDays(10),
        endDate = LocalDate.now().plusDays(20),
        graceEndDate = LocalDate.now().plusDays(23),
        freezeDaysUsed = freezeDaysUsed,
    )

    private fun stubBaseLookups(
        member: Member,
        membership: Membership,
    ) {
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId))
            .thenReturn(Optional.of(org))
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(club.publicId, org.id))
            .thenReturn(Optional.of(club))
        whenever(
            memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(
                member.publicId,
                org.id,
                club.id,
            ),
        ).thenReturn(Optional.of(member))
        whenever(
            membershipRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(
                membership.publicId,
                org.id,
            ),
        ).thenReturn(Optional.of(membership))
    }

    private fun stubPlanLookupById(plan: MembershipPlan) {
        whenever(membershipPlanRepository.findById(plan.id))
            .thenReturn(Optional.of(plan))
    }

    private fun stubPlanLookupByPublicId(plan: MembershipPlan) {
        whenever(
            membershipPlanRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(
                plan.publicId,
                org.id,
            ),
        ).thenReturn(Optional.of(plan))
    }

    // ── Freeze tests ───────────────────────────────────────────��────────────

    @Nested
    inner class FreezeTests {
        @Test
        fun `freeze happy path - extends endDate and sets status to frozen`() {
            val member = member()
            val plan = plan()
            val membership = activeMembership(plan)
            val user = callerUser()
            stubBaseLookups(member, membership)
            stubPlanLookupById(plan)
            whenever(userRepository.findByPublicIdAndDeletedAtIsNull(user.publicId))
                .thenReturn(Optional.of(user))
            whenever(freezePeriodRepository.existsByMembershipIdAndActualEndDateIsNull(membership.id))
                .thenReturn(false)
            whenever(freezePeriodRepository.save(any<FreezePeriod>()))
                .thenAnswer { it.arguments[0] as FreezePeriod }
            whenever(membershipRepository.save(any<Membership>()))
                .thenAnswer { it.arguments[0] as Membership }
            whenever(memberRepository.save(any<Member>()))
                .thenAnswer { it.arguments[0] as Member }

            val request =
                FreezeMembershipRequest(
                    freezeStartDate = LocalDate.now(),
                    freezeEndDate = LocalDate.now().plusDays(7),
                    reason = "Travel",
                )

            val originalEndDate = membership.endDate
            val response =
                service.freeze(
                    org.publicId,
                    club.publicId,
                    member.publicId,
                    membership.publicId,
                    request,
                    user.publicId,
                )

            assertThat(response.status).isEqualTo("frozen")
            assertThat(response.endDate).isEqualTo(originalEndDate.plusDays(7))
            assertThat(response.freezeDaysUsed).isEqualTo(7)
            assertThat(member.membershipStatus).isEqualTo("frozen")
            verify(freezePeriodRepository).save(any<FreezePeriod>())
        }

        @Test
        fun `freeze on non-active membership returns 422`() {
            val member = member("expired")
            val plan = plan()
            val membership = activeMembership(plan, status = "expired")
            val user = callerUser()
            stubBaseLookups(member, membership)
            stubPlanLookupById(plan)
            whenever(userRepository.findByPublicIdAndDeletedAtIsNull(user.publicId))
                .thenReturn(Optional.of(user))

            val request =
                FreezeMembershipRequest(
                    freezeStartDate = LocalDate.now(),
                    freezeEndDate = LocalDate.now().plusDays(7),
                )

            assertThatThrownBy {
                service.freeze(
                    org.publicId,
                    club.publicId,
                    member.publicId,
                    membership.publicId,
                    request,
                    user.publicId,
                )
            }
                .isInstanceOf(ArenaException::class.java)
                .extracting("status")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        }

        @Test
        fun `freeze when plan disallows returns 422`() {
            val member = member()
            val plan = plan(freezeAllowed = false, maxFreezeDays = 0)
            val membership = activeMembership(plan)
            val user = callerUser()
            stubBaseLookups(member, membership)
            stubPlanLookupById(plan)
            whenever(userRepository.findByPublicIdAndDeletedAtIsNull(user.publicId))
                .thenReturn(Optional.of(user))

            val request =
                FreezeMembershipRequest(
                    freezeStartDate = LocalDate.now(),
                    freezeEndDate = LocalDate.now().plusDays(7),
                )

            assertThatThrownBy {
                service.freeze(
                    org.publicId,
                    club.publicId,
                    member.publicId,
                    membership.publicId,
                    request,
                    user.publicId,
                )
            }
                .isInstanceOf(ArenaException::class.java)
                .extracting("status")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        }

        @Test
        fun `freeze exceeding days limit returns 422`() {
            val member = member()
            val plan = plan(maxFreezeDays = 10)
            val membership = activeMembership(plan, freezeDaysUsed = 5)
            val user = callerUser()
            stubBaseLookups(member, membership)
            stubPlanLookupById(plan)
            whenever(userRepository.findByPublicIdAndDeletedAtIsNull(user.publicId))
                .thenReturn(Optional.of(user))

            val request =
                FreezeMembershipRequest(
                    freezeStartDate = LocalDate.now(),
                    freezeEndDate = LocalDate.now().plusDays(7),
                )

            assertThatThrownBy {
                service.freeze(
                    org.publicId,
                    club.publicId,
                    member.publicId,
                    membership.publicId,
                    request,
                    user.publicId,
                )
            }
                .isInstanceOf(ArenaException::class.java)
                .extracting("status")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        }

        @Test
        fun `freeze with overlapping active freeze returns 409`() {
            val member = member()
            val plan = plan()
            val membership = activeMembership(plan)
            val user = callerUser()
            stubBaseLookups(member, membership)
            stubPlanLookupById(plan)
            whenever(userRepository.findByPublicIdAndDeletedAtIsNull(user.publicId))
                .thenReturn(Optional.of(user))
            whenever(freezePeriodRepository.existsByMembershipIdAndActualEndDateIsNull(membership.id))
                .thenReturn(true)

            val request =
                FreezeMembershipRequest(
                    freezeStartDate = LocalDate.now(),
                    freezeEndDate = LocalDate.now().plusDays(7),
                )

            assertThatThrownBy {
                service.freeze(
                    org.publicId,
                    club.publicId,
                    member.publicId,
                    membership.publicId,
                    request,
                    user.publicId,
                )
            }
                .isInstanceOf(ArenaException::class.java)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT)
        }
    }

    // ── Unfreeze tests ────────────────────────────────────────��─────────────

    @Nested
    inner class UnfreezeTests {
        @Test
        fun `unfreeze happy path - adjusts endDate and restores active status`() {
            val member = member("frozen")
            val plan = plan()
            val membership = activeMembership(plan, status = "frozen", freezeDaysUsed = 10)
            val originalEndDate = membership.endDate
            stubBaseLookups(member, membership)
            stubPlanLookupById(plan)
            whenever(membershipRepository.save(any<Membership>()))
                .thenAnswer { it.arguments[0] as Membership }
            whenever(memberRepository.save(any<Member>()))
                .thenAnswer { it.arguments[0] as Member }

            val freezePeriod =
                FreezePeriod(
                    organizationId = org.id,
                    membershipId = membership.id,
                    memberId = member.id,
                    freezeStartDate = LocalDate.now().minusDays(3),
                    freezeEndDate = LocalDate.now().plusDays(7),
                    durationDays = 10,
                    requestedById = 1L,
                )
            whenever(freezePeriodRepository.findByMembershipIdAndActualEndDateIsNull(membership.id))
                .thenReturn(Optional.of(freezePeriod))
            whenever(freezePeriodRepository.save(any<FreezePeriod>()))
                .thenAnswer { it.arguments[0] as FreezePeriod }

            val response =
                service.unfreeze(
                    org.publicId,
                    club.publicId,
                    member.publicId,
                    membership.publicId,
                    UnfreezeMembershipRequest(),
                )

            assertThat(response.status).isEqualTo("active")
            assertThat(member.membershipStatus).isEqualTo("active")
            assertThat(freezePeriod.actualEndDate).isEqualTo(LocalDate.now())
            // 10 original freeze days, 3 actual days frozen, 7 days recovered
            assertThat(response.endDate).isEqualTo(originalEndDate.minusDays(7))
        }

        @Test
        fun `unfreeze on non-frozen membership returns 422`() {
            val member = member()
            val plan = plan()
            val membership = activeMembership(plan, status = "active")
            stubBaseLookups(member, membership)
            stubPlanLookupById(plan)

            assertThatThrownBy {
                service.unfreeze(
                    org.publicId,
                    club.publicId,
                    member.publicId,
                    membership.publicId,
                    UnfreezeMembershipRequest(),
                )
            }
                .isInstanceOf(ArenaException::class.java)
                .extracting("status")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        }
    }

    // ── Renew tests ─────────────────────────────────────────────────────────

    @Nested
    inner class RenewTests {
        @Test
        fun `renew happy path - creates new membership with payment and invoice`() {
            val member = member()
            val plan = plan()
            val membership = activeMembership(plan)
            val user = callerUser()
            stubBaseLookups(member, membership)
            stubPlanLookupByPublicId(plan)
            whenever(userRepository.findByPublicIdAndDeletedAtIsNull(user.publicId))
                .thenReturn(Optional.of(user))
            whenever(membershipRepository.save(any<Membership>()))
                .thenAnswer { it.arguments[0] as Membership }
            whenever(memberRepository.save(any<Member>()))
                .thenAnswer { it.arguments[0] as Member }
            whenever(
                paymentService.recordPayment(
                    any(),
                    anyOrNull(),
                    any(),
                    any(),
                    anyOrNull(),
                    any(),
                    anyOrNull(),
                ),
            ).thenReturn(
                Payment(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = member.branchId,
                    memberId = member.id,
                    membershipId = 1L,
                    amountHalalas = plan.priceHalalas,
                    paymentMethod = "cash",
                    collectedById = user.id,
                ),
            )
            whenever(invoiceService.createInvoice(any(), any(), any(), any(), any()))
                .thenReturn(
                    Invoice(
                        organizationId = org.id,
                        clubId = club.id,
                        branchId = member.branchId,
                        memberId = member.id,
                        paymentId = 1L,
                        invoiceNumber = "INV-2026-ELI-00002",
                        subtotalHalalas = plan.priceHalalas,
                        vatRate = BigDecimal("0.1500"),
                        vatAmountHalalas = 2250,
                        totalHalalas = plan.priceHalalas + 2250,
                    ),
                )

            val request =
                RenewMembershipRequest(
                    planId = plan.publicId,
                    paymentMethod = "cash",
                    amountHalalas = plan.priceHalalas,
                )

            val response =
                service.renew(
                    org.publicId,
                    club.publicId,
                    member.publicId,
                    membership.publicId,
                    request,
                    user.publicId,
                )

            assertThat(response.status).isEqualTo("active")
            assertThat(response.startDate).isEqualTo(membership.endDate.plusDays(1))
            assertThat(response.payment).isNotNull
            assertThat(response.invoice).isNotNull
            assertThat(member.membershipStatus).isEqualTo("active")
        }

        @Test
        fun `renew with wrong amount returns 422`() {
            val member = member()
            val plan = plan()
            val membership = activeMembership(plan)
            val user = callerUser()
            stubBaseLookups(member, membership)
            stubPlanLookupByPublicId(plan)
            whenever(userRepository.findByPublicIdAndDeletedAtIsNull(user.publicId))
                .thenReturn(Optional.of(user))

            val request =
                RenewMembershipRequest(
                    planId = plan.publicId,
                    paymentMethod = "cash",
                    amountHalalas = 99999,
                )

            assertThatThrownBy {
                service.renew(
                    org.publicId,
                    club.publicId,
                    member.publicId,
                    membership.publicId,
                    request,
                    user.publicId,
                )
            }
                .isInstanceOf(ArenaException::class.java)
                .extracting("status")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        }

        @Test
        fun `renew terminated membership returns 422`() {
            val member = member("terminated")
            val plan = plan()
            val membership = activeMembership(plan, status = "terminated")
            val user = callerUser()
            stubBaseLookups(member, membership)
            stubPlanLookupByPublicId(plan)
            whenever(userRepository.findByPublicIdAndDeletedAtIsNull(user.publicId))
                .thenReturn(Optional.of(user))

            val request =
                RenewMembershipRequest(
                    planId = plan.publicId,
                    paymentMethod = "cash",
                    amountHalalas = plan.priceHalalas,
                )

            assertThatThrownBy {
                service.renew(
                    org.publicId,
                    club.publicId,
                    member.publicId,
                    membership.publicId,
                    request,
                    user.publicId,
                )
            }
                .isInstanceOf(ArenaException::class.java)
                .extracting("status")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        }
    }

    // ── Terminate tests ─────────────────────────────────────────────────────

    @Nested
    inner class TerminateTests {
        @Test
        fun `terminate happy path - sets status to terminated with reason`() {
            val member = member()
            val plan = plan()
            val membership = activeMembership(plan)
            stubBaseLookups(member, membership)
            stubPlanLookupById(plan)
            whenever(membershipRepository.save(any<Membership>()))
                .thenAnswer { it.arguments[0] as Membership }
            whenever(memberRepository.save(any<Member>()))
                .thenAnswer { it.arguments[0] as Member }

            val request = TerminateMembershipRequest(reason = "Member requested cancellation")

            val response =
                service.terminate(
                    org.publicId,
                    club.publicId,
                    member.publicId,
                    membership.publicId,
                    request,
                )

            assertThat(response.status).isEqualTo("terminated")
            assertThat(member.membershipStatus).isEqualTo("terminated")
            assertThat(membership.notes).isEqualTo("Member requested cancellation")
        }

        @Test
        fun `terminate expired membership returns 422`() {
            val member = member("expired")
            val plan = plan()
            val membership = activeMembership(plan, status = "expired")
            stubBaseLookups(member, membership)
            stubPlanLookupById(plan)

            val request = TerminateMembershipRequest(reason = "Test")

            assertThatThrownBy {
                service.terminate(
                    org.publicId,
                    club.publicId,
                    member.publicId,
                    membership.publicId,
                    request,
                )
            }
                .isInstanceOf(ArenaException::class.java)
                .extracting("status")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
        }
    }

    // ── Expiry batch tests ──────────────────────────────────────────────────

    @Nested
    inner class ExpiryTests {
        @Test
        fun `expireOverdueMemberships transitions correct memberships`() {
            val plan = plan()
            val m1 =
                Membership(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = 1L,
                    memberId = 10L,
                    planId = plan.id,
                    membershipStatus = "active",
                    startDate = LocalDate.now().minusDays(40),
                    endDate = LocalDate.now().minusDays(1),
                )
            val m2 =
                Membership(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = 1L,
                    memberId = 20L,
                    planId = plan.id,
                    membershipStatus = "active",
                    startDate = LocalDate.now().minusDays(40),
                    endDate = LocalDate.now().minusDays(1),
                )
            val member1 =
                Member(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = 1L,
                    userId = 10L,
                    firstNameAr = "أ",
                    firstNameEn = "A",
                    lastNameAr = "ب",
                    lastNameEn = "B",
                    phone = "123",
                )
            val member2 =
                Member(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = 1L,
                    userId = 20L,
                    firstNameAr = "ج",
                    firstNameEn = "C",
                    lastNameAr = "د",
                    lastNameEn = "D",
                    phone = "456",
                )

            whenever(membershipRepository.findOverdueMemberships(any(), any()))
                .thenReturn(listOf(m1, m2))
            whenever(membershipRepository.save(any<Membership>()))
                .thenAnswer { it.arguments[0] as Membership }
            whenever(memberRepository.findById(10L))
                .thenReturn(Optional.of(member1))
            whenever(memberRepository.findById(20L))
                .thenReturn(Optional.of(member2))
            whenever(memberRepository.save(any<Member>()))
                .thenAnswer { it.arguments[0] as Member }

            service.expireOverdueMemberships()

            assertThat(m1.membershipStatus).isEqualTo("expired")
            assertThat(m2.membershipStatus).isEqualTo("expired")
            assertThat(member1.membershipStatus).isEqualTo("expired")
            assertThat(member2.membershipStatus).isEqualTo("expired")
        }
    }
}
