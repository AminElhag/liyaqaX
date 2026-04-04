package com.liyaqa.gx

import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.gx.dto.AttendanceEntry
import com.liyaqa.gx.dto.BulkAttendanceRequest
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.Membership
import com.liyaqa.membership.MembershipPlan
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
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
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class GXBookingServiceTest {
    @Mock lateinit var bookingRepository: GXBookingRepository

    @Mock lateinit var attendanceRepository: GXAttendanceRepository

    @Mock lateinit var classInstanceRepository: GXClassInstanceRepository

    @Mock lateinit var memberRepository: MemberRepository

    @Mock lateinit var membershipRepository: MembershipRepository

    @Mock lateinit var membershipPlanRepository: MembershipPlanRepository

    @Mock lateinit var organizationRepository: OrganizationRepository

    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var userRepository: UserRepository

    @InjectMocks lateinit var service: GXBookingService

    // ── Helpers ────────────────────────────────────────────────────────────

    private val orgPublicId = UUID.randomUUID()

    private fun organization(): Organization =
        Organization(
            nameAr = "Test",
            nameEn = "Test Org",
            email = "test@test.com",
            country = "SA",
            timezone = "Asia/Riyadh",
        ).apply {
            val f = this::class.java.superclass.getDeclaredField("id")
            f.isAccessible = true
            f.set(this, 1L)
        }

    private fun classInstance(
        clubId: Long = 10L,
        capacity: Int = 2,
        bookingsCount: Int = 0,
        waitlistCount: Int = 0,
        status: String = "scheduled",
    ): GXClassInstance =
        GXClassInstance(
            organizationId = 1L,
            clubId = clubId,
            branchId = 100L,
            classTypeId = 200L,
            instructorId = 50L,
            scheduledAt = Instant.now().plusSeconds(86400),
            capacity = capacity,
            bookingsCount = bookingsCount,
            waitlistCount = waitlistCount,
            instanceStatus = status,
        ).apply {
            val f = this::class.java.superclass.getDeclaredField("id")
            f.isAccessible = true
            f.set(this, 500L)
        }

    private fun member(clubId: Long = 10L): Member =
        Member(
            organizationId = 1L,
            clubId = clubId,
            branchId = 100L,
            userId = 8L,
            firstNameAr = "أحمد",
            firstNameEn = "Ahmed",
            lastNameAr = "الرشيدي",
            lastNameEn = "Al-Rashidi",
            phone = "+966501234567",
        ).apply {
            val f = this::class.java.superclass.getDeclaredField("id")
            f.isAccessible = true
            f.set(this, 300L)
        }

    private fun membership(
        memberId: Long = 300L,
        planId: Long = 400L,
        status: String = "active",
    ): Membership =
        Membership(
            organizationId = 1L,
            clubId = 10L,
            branchId = 100L,
            memberId = memberId,
            planId = planId,
            membershipStatus = status,
            startDate = LocalDate.now(),
            endDate = LocalDate.now().plusDays(30),
        )

    private fun membershipPlan(gxIncluded: Boolean = true): MembershipPlan =
        MembershipPlan(
            organizationId = 1L,
            clubId = 10L,
            nameAr = "Basic",
            nameEn = "Basic",
            priceHalalas = 15000,
            durationDays = 30,
            gxClassesIncluded = gxIncluded,
        ).apply {
            val f = this::class.java.superclass.getDeclaredField("id")
            f.isAccessible = true
            f.set(this, 400L)
        }

    private fun stubBookHappyPath(
        instance: GXClassInstance = classInstance(),
        member: Member = member(),
    ) {
        val org = organization()
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId))
            .thenReturn(Optional.of(org))
        whenever(classInstanceRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(instance.publicId, org.id))
            .thenReturn(Optional.of(instance))
        whenever(memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(member.publicId, org.id, instance.clubId))
            .thenReturn(Optional.of(member))
        whenever(bookingRepository.existsByInstanceIdAndMemberId(instance.id, member.id))
            .thenReturn(false)
        whenever(membershipRepository.findByMemberIdAndMembershipStatusInAndDeletedAtIsNull(any(), any()))
            .thenReturn(Optional.of(membership()))
        whenever(membershipPlanRepository.findById(400L))
            .thenReturn(Optional.of(membershipPlan()))
        whenever(bookingRepository.save(any<GXBooking>()))
            .thenAnswer { it.arguments[0] as GXBooking }
        whenever(classInstanceRepository.save(any<GXClassInstance>()))
            .thenAnswer { it.arguments[0] as GXClassInstance }
    }

    // ── Rule 1: Capacity check ─────────────────────────────────────────────

    @Test
    fun `book member confirmed when class has capacity`() {
        val instance = classInstance(capacity = 15, bookingsCount = 5)
        val member = member()
        stubBookHappyPath(instance, member)

        val response = service.bookMember(orgPublicId, instance.publicId, member.publicId)

        assertThat(response.status).isEqualTo("confirmed")
        assertThat(instance.bookingsCount).isEqualTo(6)
    }

    @Test
    fun `book member waitlisted when class is full`() {
        val instance = classInstance(capacity = 2, bookingsCount = 2, waitlistCount = 0)
        val member = member()
        stubBookHappyPath(instance, member)

        val response = service.bookMember(orgPublicId, instance.publicId, member.publicId)

        assertThat(response.status).isEqualTo("waitlist")
        assertThat(response.waitlistPosition).isEqualTo(1)
        assertThat(instance.waitlistCount).isEqualTo(1)
    }

    // ── Rule 2: Duplicate booking prevention ───────────────────────────────

    @Test
    fun `book member fails when already booked`() {
        val org = organization()
        val instance = classInstance()
        val member = member()

        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId))
            .thenReturn(Optional.of(org))
        whenever(classInstanceRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(instance.publicId, org.id))
            .thenReturn(Optional.of(instance))
        whenever(memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(member.publicId, org.id, instance.clubId))
            .thenReturn(Optional.of(member))
        whenever(bookingRepository.existsByInstanceIdAndMemberId(instance.id, member.id))
            .thenReturn(true)

        assertThatThrownBy { service.bookMember(orgPublicId, instance.publicId, member.publicId) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    // ── Rule 3: Membership GX check ────────────────────────────────────────

    @Test
    fun `book member fails when plan does not include GX`() {
        val org = organization()
        val instance = classInstance()
        val member = member()

        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId))
            .thenReturn(Optional.of(org))
        whenever(classInstanceRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(instance.publicId, org.id))
            .thenReturn(Optional.of(instance))
        whenever(memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(member.publicId, org.id, instance.clubId))
            .thenReturn(Optional.of(member))
        whenever(bookingRepository.existsByInstanceIdAndMemberId(instance.id, member.id))
            .thenReturn(false)
        whenever(membershipRepository.findByMemberIdAndMembershipStatusInAndDeletedAtIsNull(any(), any()))
            .thenReturn(Optional.of(membership()))
        whenever(membershipPlanRepository.findById(400L))
            .thenReturn(Optional.of(membershipPlan(gxIncluded = false)))

        assertThatThrownBy { service.bookMember(orgPublicId, instance.publicId, member.publicId) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `book member fails when no active membership`() {
        val org = organization()
        val instance = classInstance()
        val member = member()

        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId))
            .thenReturn(Optional.of(org))
        whenever(classInstanceRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(instance.publicId, org.id))
            .thenReturn(Optional.of(instance))
        whenever(memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(member.publicId, org.id, instance.clubId))
            .thenReturn(Optional.of(member))
        whenever(bookingRepository.existsByInstanceIdAndMemberId(instance.id, member.id))
            .thenReturn(false)
        whenever(membershipRepository.findByMemberIdAndMembershipStatusInAndDeletedAtIsNull(any(), any()))
            .thenReturn(Optional.empty())

        assertThatThrownBy { service.bookMember(orgPublicId, instance.publicId, member.publicId) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Rule 4: Member club scope ──────────────────────────────────────────

    @Test
    fun `book member fails when member not found in club scope`() {
        val org = organization()
        val instance = classInstance(clubId = 10L)
        val wrongClubMemberPublicId = UUID.randomUUID()

        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId))
            .thenReturn(Optional.of(org))
        whenever(classInstanceRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(instance.publicId, org.id))
            .thenReturn(Optional.of(instance))
        // The member lookup filters by instance.clubId — returns empty for wrong club member
        whenever(
            memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(
                wrongClubMemberPublicId,
                org.id,
                instance.clubId,
            ),
        ).thenReturn(Optional.empty())

        assertThatThrownBy { service.bookMember(orgPublicId, instance.publicId, wrongClubMemberPublicId) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    // ── Rule 6: Waitlist promotion on cancellation ─────────────────────────

    @Test
    fun `cancel confirmed booking promotes first waitlisted member`() {
        val org = organization()
        val instance = classInstance(capacity = 2, bookingsCount = 2, waitlistCount = 1)
        val member = member()

        val confirmedBooking =
            GXBooking(
                organizationId = 1L,
                clubId = 10L,
                instanceId = 500L,
                memberId = 300L,
                bookingStatus = "confirmed",
            ).apply {
                val f = this::class.java.superclass.getDeclaredField("id")
                f.isAccessible = true
                f.set(this, 600L)
            }

        val waitlistedBooking =
            GXBooking(
                organizationId = 1L,
                clubId = 10L,
                instanceId = 500L,
                memberId = 301L,
                bookingStatus = "waitlist",
                waitlistPosition = 1,
            ).apply {
                val f = this::class.java.superclass.getDeclaredField("id")
                f.isAccessible = true
                f.set(this, 601L)
            }

        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId))
            .thenReturn(Optional.of(org))
        whenever(classInstanceRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(instance.publicId, org.id))
            .thenReturn(Optional.of(instance))
        whenever(bookingRepository.findByPublicIdAndOrganizationId(confirmedBooking.publicId, org.id))
            .thenReturn(Optional.of(confirmedBooking))
        whenever(bookingRepository.findFirstByInstanceIdAndBookingStatusOrderByWaitlistPositionAsc(500L, "waitlist"))
            .thenReturn(Optional.of(waitlistedBooking))
        whenever(bookingRepository.save(any<GXBooking>()))
            .thenAnswer { it.arguments[0] as GXBooking }
        whenever(classInstanceRepository.save(any<GXClassInstance>()))
            .thenAnswer { it.arguments[0] as GXClassInstance }
        whenever(memberRepository.findById(300L))
            .thenReturn(Optional.of(member))

        service.cancelBooking(orgPublicId, instance.publicId, confirmedBooking.publicId)

        // Verify waitlisted booking was promoted
        assertThat(waitlistedBooking.bookingStatus).isEqualTo("promoted")
        assertThat(waitlistedBooking.waitlistPosition).isNull()
        // Verify counts: bookingsCount stayed at 2 (decremented then incremented)
        assertThat(instance.bookingsCount).isEqualTo(2)
        // Waitlist count went down by 1
        assertThat(instance.waitlistCount).isEqualTo(0)
    }

    @Test
    fun `full waitlist promotion scenario - fill, waitlist, cancel, promote`() {
        val org = organization()
        val instance = classInstance(capacity = 1, bookingsCount = 0, waitlistCount = 0)

        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId))
            .thenReturn(Optional.of(org))
        whenever(classInstanceRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(instance.publicId, org.id))
            .thenReturn(Optional.of(instance))

        // Step 1: Book member A (confirmed — fills the class)
        val memberA =
            member(clubId = 10L).apply {
                val f = this::class.java.superclass.getDeclaredField("id")
                f.isAccessible = true
                f.set(this, 300L)
            }
        val memberAPublicId = memberA.publicId

        whenever(memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(memberAPublicId, org.id, 10L))
            .thenReturn(Optional.of(memberA))
        whenever(bookingRepository.existsByInstanceIdAndMemberId(instance.id, memberA.id))
            .thenReturn(false)
        whenever(membershipRepository.findByMemberIdAndMembershipStatusInAndDeletedAtIsNull(any(), any()))
            .thenReturn(Optional.of(membership(memberId = memberA.id)))
        whenever(membershipPlanRepository.findById(400L))
            .thenReturn(Optional.of(membershipPlan()))
        whenever(bookingRepository.save(any<GXBooking>()))
            .thenAnswer { it.arguments[0] as GXBooking }
        whenever(classInstanceRepository.save(any<GXClassInstance>()))
            .thenAnswer { it.arguments[0] as GXClassInstance }

        val bookingA = service.bookMember(orgPublicId, instance.publicId, memberAPublicId)
        assertThat(bookingA.status).isEqualTo("confirmed")
        assertThat(instance.bookingsCount).isEqualTo(1)

        // Step 2: Book member B (waitlisted — class is full)
        val memberB =
            Member(
                organizationId = 1L,
                clubId = 10L,
                branchId = 100L,
                userId = 9L,
                firstNameAr = "سارة",
                firstNameEn = "Sarah",
                lastNameAr = "المنصوري",
                lastNameEn = "Al-Mansouri",
                phone = "+966509876543",
            ).apply {
                val f = this::class.java.superclass.getDeclaredField("id")
                f.isAccessible = true
                f.set(this, 301L)
            }
        val memberBPublicId = memberB.publicId

        whenever(memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(memberBPublicId, org.id, 10L))
            .thenReturn(Optional.of(memberB))
        whenever(bookingRepository.existsByInstanceIdAndMemberId(instance.id, memberB.id))
            .thenReturn(false)
        whenever(membershipRepository.findByMemberIdAndMembershipStatusInAndDeletedAtIsNull(memberB.id, listOf("active", "frozen")))
            .thenReturn(Optional.of(membership(memberId = memberB.id)))

        val bookingB = service.bookMember(orgPublicId, instance.publicId, memberBPublicId)
        assertThat(bookingB.status).isEqualTo("waitlist")
        assertThat(bookingB.waitlistPosition).isEqualTo(1)
        assertThat(instance.waitlistCount).isEqualTo(1)

        // Step 3: Cancel member A's booking — should promote member B
        val savedBookingA =
            GXBooking(
                organizationId = 1L,
                clubId = 10L,
                instanceId = instance.id,
                memberId = memberA.id,
                bookingStatus = "confirmed",
            ).apply {
                val f = this::class.java.superclass.getDeclaredField("id")
                f.isAccessible = true
                f.set(this, 600L)
            }

        val savedBookingB =
            GXBooking(
                organizationId = 1L,
                clubId = 10L,
                instanceId = instance.id,
                memberId = memberB.id,
                bookingStatus = "waitlist",
                waitlistPosition = 1,
            ).apply {
                val f = this::class.java.superclass.getDeclaredField("id")
                f.isAccessible = true
                f.set(this, 601L)
            }

        whenever(bookingRepository.findByPublicIdAndOrganizationId(savedBookingA.publicId, org.id))
            .thenReturn(Optional.of(savedBookingA))
        whenever(bookingRepository.findFirstByInstanceIdAndBookingStatusOrderByWaitlistPositionAsc(instance.id, "waitlist"))
            .thenReturn(Optional.of(savedBookingB))
        whenever(memberRepository.findById(memberA.id))
            .thenReturn(Optional.of(memberA))

        service.cancelBooking(orgPublicId, instance.publicId, savedBookingA.publicId)

        // Verify: member A cancelled, member B promoted
        assertThat(savedBookingA.bookingStatus).isEqualTo("cancelled")
        assertThat(savedBookingA.cancelledAt).isNotNull()
        assertThat(savedBookingB.bookingStatus).isEqualTo("promoted")
        assertThat(savedBookingB.waitlistPosition).isNull()
        // bookingsCount = capacity (1) since one was removed and one promoted
        assertThat(instance.bookingsCount).isEqualTo(1)
        assertThat(instance.waitlistCount).isEqualTo(0)
    }

    // ── Rule 8: Attendance upsert ──────────────────────────────────────────

    @Test
    fun `submit attendance updates existing record`() {
        val org = organization()
        val instance = classInstance()
        val member = member()
        val callerPublicId = UUID.randomUUID()
        val callerUser =
            User(
                email = "staff@test.com",
                passwordHash = "hash",
            ).apply {
                val f = this::class.java.superclass.getDeclaredField("id")
                f.isAccessible = true
                f.set(this, 99L)
            }

        val existingAttendance =
            GXAttendance(
                organizationId = 1L,
                instanceId = 500L,
                memberId = 300L,
                attendanceStatus = "absent",
                markedById = 99L,
            )

        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId))
            .thenReturn(Optional.of(org))
        whenever(classInstanceRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(instance.publicId, org.id))
            .thenReturn(Optional.of(instance))
        whenever(userRepository.findByPublicIdAndDeletedAtIsNull(callerPublicId))
            .thenReturn(Optional.of(callerUser))
        whenever(memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(member.publicId, org.id, instance.clubId))
            .thenReturn(Optional.of(member))
        whenever(attendanceRepository.findByInstanceIdAndMemberId(500L, 300L))
            .thenReturn(Optional.of(existingAttendance))
        whenever(attendanceRepository.save(any<GXAttendance>()))
            .thenAnswer { it.arguments[0] as GXAttendance }
        whenever(classInstanceRepository.save(any<GXClassInstance>()))
            .thenAnswer { it.arguments[0] as GXClassInstance }

        val request =
            BulkAttendanceRequest(
                attendance =
                    listOf(
                        AttendanceEntry(memberId = member.publicId, status = "present"),
                    ),
            )

        val responses = service.submitAttendance(orgPublicId, instance.publicId, request, callerPublicId)

        assertThat(responses).hasSize(1)
        assertThat(responses[0].status).isEqualTo("present")
        assertThat(existingAttendance.attendanceStatus).isEqualTo("present")
    }

    // ── Rule 9: Attendance window ──────────────────────────────────────────

    @Test
    fun `submit attendance fails for cancelled class`() {
        val org = organization()
        val instance = classInstance(status = "cancelled")

        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId))
            .thenReturn(Optional.of(org))
        whenever(classInstanceRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(instance.publicId, org.id))
            .thenReturn(Optional.of(instance))

        val request =
            BulkAttendanceRequest(
                attendance =
                    listOf(
                        AttendanceEntry(memberId = UUID.randomUUID(), status = "present"),
                    ),
            )

        assertThatThrownBy { service.submitAttendance(orgPublicId, instance.publicId, request, UUID.randomUUID()) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }
}
