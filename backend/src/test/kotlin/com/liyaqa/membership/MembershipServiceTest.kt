package com.liyaqa.membership

import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.invoice.Invoice
import com.liyaqa.invoice.InvoiceService
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.dto.AssignMembershipRequest
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.payment.Payment
import com.liyaqa.payment.PaymentRepository
import com.liyaqa.payment.PaymentService
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MembershipServiceTest {
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

    @InjectMocks lateinit var service: MembershipService

    private val org = Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com")
    private val club = Club(organizationId = org.id, nameAr = "نادي", nameEn = "Elixir Gym")

    private fun member() =
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
            membershipStatus = "pending",
        )

    private fun plan() =
        MembershipPlan(
            organizationId = org.id,
            clubId = club.id,
            nameAr = "شهري أساسي",
            nameEn = "Basic Monthly",
            priceHalalas = 15000,
            durationDays = 30,
            gracePeriodDays = 3,
        )

    private fun callerUser() =
        User(
            email = "reception@elixir.com",
            passwordHash = "hashed",
            organizationId = org.id,
            clubId = club.id,
        )

    private fun validRequest(plan: MembershipPlan) =
        AssignMembershipRequest(
            planId = plan.publicId,
            paymentMethod = "cash",
            amountHalalas = plan.priceHalalas,
        )

    private fun stubLookups(
        member: Member,
        plan: MembershipPlan,
        user: User,
    ) {
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId))
            .thenReturn(Optional.of(org))
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(club.publicId, org.id))
            .thenReturn(Optional.of(club))
        whenever(memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(member.publicId, org.id, club.id))
            .thenReturn(Optional.of(member))
        whenever(membershipPlanRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(plan.publicId, org.id))
            .thenReturn(Optional.of(plan))
        whenever(userRepository.findByPublicIdAndDeletedAtIsNull(user.publicId))
            .thenReturn(Optional.of(user))
    }

    private fun stubSaves(
        member: Member,
        plan: MembershipPlan,
        user: User,
    ) {
        whenever(membershipRepository.existsByMemberIdAndMembershipStatusInAndDeletedAtIsNull(any(), any()))
            .thenReturn(false)
        whenever(membershipRepository.save(any<Membership>()))
            .thenAnswer { it.arguments[0] as Membership }
        whenever(paymentService.recordPayment(any(), anyOrNull(), any(), any(), anyOrNull(), any(), anyOrNull()))
            .thenReturn(
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
        whenever(invoiceService.createInvoiceStub(any(), any(), any(), any()))
            .thenReturn(
                Invoice(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = member.branchId,
                    memberId = member.id,
                    paymentId = 1L,
                    invoiceNumber = "INV-2026-ELI-00001",
                    subtotalHalalas = plan.priceHalalas,
                    vatRate = BigDecimal("0.1500"),
                    vatAmountHalalas = 2250,
                    totalHalalas = plan.priceHalalas + 2250,
                ),
            )
        whenever(memberRepository.save(any<Member>()))
            .thenAnswer { it.arguments[0] as Member }
    }

    // ── Successful assignment ────────────────────────────────────────────────

    @Test
    fun `assign plan successfully creates membership, payment, invoice, activates member`() {
        val member = member()
        val plan = plan()
        val user = callerUser()
        stubLookups(member, plan, user)
        stubSaves(member, plan, user)

        val response = service.assignPlan(org.publicId, club.publicId, member.publicId, validRequest(plan), user.publicId)

        assertThat(response.status).isEqualTo("active")
        assertThat(response.plan.nameEn).isEqualTo("Basic Monthly")
        assertThat(response.plan.priceHalalas).isEqualTo(15000)
        assertThat(response.startDate).isEqualTo(LocalDate.now())
        assertThat(response.endDate).isEqualTo(LocalDate.now().plusDays(30))
        assertThat(response.payment).isNotNull
        assertThat(response.invoice).isNotNull
        verify(memberRepository).save(any<Member>())
    }

    // ── Rule 1: One active membership at a time ─────────────────────────────

    @Test
    fun `assign plan to member with active membership returns 409`() {
        val member = member()
        val plan = plan()
        val user = callerUser()
        stubLookups(member, plan, user)
        whenever(membershipRepository.existsByMemberIdAndMembershipStatusInAndDeletedAtIsNull(any(), any()))
            .thenReturn(true)

        assertThatThrownBy {
            service.assignPlan(org.publicId, club.publicId, member.publicId, validRequest(plan), user.publicId)
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    // ── Rule 2: Payment amount must match plan price ────────────────────────

    @Test
    fun `assign plan with wrong amount returns 422`() {
        val member = member()
        val plan = plan()
        val user = callerUser()
        stubLookups(member, plan, user)
        whenever(membershipRepository.existsByMemberIdAndMembershipStatusInAndDeletedAtIsNull(any(), any()))
            .thenReturn(false)

        val badRequest = validRequest(plan).copy(amountHalalas = 10000)

        assertThatThrownBy {
            service.assignPlan(org.publicId, club.publicId, member.publicId, badRequest, user.publicId)
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Rule 3: Plan must belong to the same club ───────────────────────────

    @Test
    fun `assign plan from different club returns 422`() {
        val member = member()
        val plan =
            MembershipPlan(
                organizationId = org.id,
                clubId = club.id + 999,
                nameAr = "خطة",
                nameEn = "Other Club Plan",
                priceHalalas = 15000,
                durationDays = 30,
            )
        val user = callerUser()
        stubLookups(member, plan, user)

        assertThatThrownBy {
            service.assignPlan(org.publicId, club.publicId, member.publicId, validRequest(plan), user.publicId)
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Rule 4: Plan must be active ─────────────────────────────────────────

    @Test
    fun `assign inactive plan returns 422`() {
        val member = member()
        val plan = plan().also { it.isActive = false }
        val user = callerUser()
        stubLookups(member, plan, user)

        assertThatThrownBy {
            service.assignPlan(org.publicId, club.publicId, member.publicId, validRequest(plan), user.publicId)
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Rule 5: VAT calculation ─────────────────────────────────────────────

    @Test
    fun `assign plan calls invoice service with correct subtotal`() {
        val member = member()
        val plan = plan()
        val user = callerUser()
        stubLookups(member, plan, user)
        stubSaves(member, plan, user)

        service.assignPlan(org.publicId, club.publicId, member.publicId, validRequest(plan), user.publicId)

        verify(invoiceService).createInvoiceStub(any(), any(), any(), eq(15000L))
    }

    // ── Rule 6: Member status update ────────────────────────────────────────

    @Test
    fun `assign plan updates member status to active`() {
        val member = member()
        val plan = plan()
        val user = callerUser()
        stubLookups(member, plan, user)
        stubSaves(member, plan, user)

        service.assignPlan(org.publicId, club.publicId, member.publicId, validRequest(plan), user.publicId)

        assertThat(member.membershipStatus).isEqualTo("active")
        verify(memberRepository).save(member)
    }

    // ── Rule 7: Atomicity — verified by @Transactional annotation presence ──

    // ── Grace period ────────────────────────────────────────────────────────

    @Test
    fun `assign plan with grace period sets graceEndDate`() {
        val member = member()
        val plan = plan()
        val user = callerUser()
        stubLookups(member, plan, user)
        stubSaves(member, plan, user)

        val response = service.assignPlan(org.publicId, club.publicId, member.publicId, validRequest(plan), user.publicId)

        assertThat(response.graceEndDate).isEqualTo(LocalDate.now().plusDays(33))
    }

    @Test
    fun `assign plan without grace period sets graceEndDate to null`() {
        val member = member()
        val plan = plan().also { it.gracePeriodDays = 0 }
        val user = callerUser()
        stubLookups(member, plan, user)
        stubSaves(member, plan, user)

        val response = service.assignPlan(org.publicId, club.publicId, member.publicId, validRequest(plan), user.publicId)

        assertThat(response.graceEndDate).isNull()
    }

    // ── Not found cases ─────────────────────────────────────────────────────

    @Test
    fun `assign plan with unknown member returns 404`() {
        val plan = plan()
        val user = callerUser()
        val unknownId = UUID.randomUUID()
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId))
            .thenReturn(Optional.of(org))
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(club.publicId, org.id))
            .thenReturn(Optional.of(club))
        whenever(memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(unknownId, org.id, club.id))
            .thenReturn(Optional.empty())

        assertThatThrownBy {
            service.assignPlan(org.publicId, club.publicId, unknownId, validRequest(plan), user.publicId)
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    // ── Get active membership ───────────────────────────────────────────────

    @Test
    fun `get active membership returns null when none exists`() {
        val member = member()
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId))
            .thenReturn(Optional.of(org))
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(club.publicId, org.id))
            .thenReturn(Optional.of(club))
        whenever(memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(member.publicId, org.id, club.id))
            .thenReturn(Optional.of(member))
        whenever(membershipRepository.findByMemberIdAndMembershipStatusInAndDeletedAtIsNull(member.id, listOf("active", "frozen")))
            .thenReturn(Optional.empty())

        val result = service.getActiveMembership(org.publicId, club.publicId, member.publicId)

        assertThat(result).isNull()
    }
}
