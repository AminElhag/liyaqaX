package com.liyaqa.checkin.service

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.checkin.entity.MemberCheckIn
import com.liyaqa.checkin.repository.MemberCheckInRepository
import com.liyaqa.checkin.repository.RecentCheckInProjection
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Optional
import java.util.UUID

class MemberCheckInServiceTest {
    private val checkInRepository: MemberCheckInRepository = mock()
    private val memberRepository: MemberRepository = mock()
    private val branchRepository: BranchRepository = mock()
    private val organizationRepository: OrganizationRepository = mock()
    private val userRepository: UserRepository = mock()
    private val membershipRepository: MembershipRepository = mock()
    private val membershipPlanRepository: MembershipPlanRepository = mock()
    private val auditService: AuditService = mock()

    private lateinit var service: MemberCheckInService

    private val orgPublicId = UUID.randomUUID()
    private val clubPublicId = UUID.randomUUID()
    private val branchPublicId = UUID.randomUUID()
    private val memberPublicId = UUID.randomUUID()
    private val actorUserPublicId = UUID.randomUUID()

    private val org: Organization = mock()
    private val branch: Branch = mock()
    private val actorUser: User = mock()

    @BeforeEach
    fun setup() {
        service =
            MemberCheckInService(
                checkInRepository, memberRepository, branchRepository,
                organizationRepository, userRepository, membershipRepository,
                membershipPlanRepository, auditService,
            )

        whenever(org.id).thenReturn(1L)
        whenever(org.publicId).thenReturn(orgPublicId)
        whenever(branch.id).thenReturn(10L)
        whenever(branch.publicId).thenReturn(branchPublicId)
        whenever(branch.nameEn).thenReturn("Elixir Gym - Riyadh")
        whenever(branch.clubId).thenReturn(5L)
        whenever(actorUser.id).thenReturn(100L)

        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId))
            .thenReturn(Optional.of(org))
        whenever(branchRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(branchPublicId, org.id))
            .thenReturn(Optional.empty())
        whenever(branchRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(branchPublicId, org.id))
            .thenReturn(Optional.of(branch))
        whenever(userRepository.findByPublicIdAndDeletedAtIsNull(actorUserPublicId))
            .thenReturn(Optional.of(actorUser))
    }

    private fun activeMember(): Member {
        val member: Member = mock()
        whenever(member.id).thenReturn(50L)
        whenever(member.publicId).thenReturn(memberPublicId)
        whenever(member.membershipStatus).thenReturn("active")
        whenever(member.firstNameEn).thenReturn("Ahmed")
        whenever(member.lastNameEn).thenReturn("Al-Rashidi")
        whenever(member.phone).thenReturn("+966501234567")
        whenever(memberRepository.findByPublicIdAndDeletedAtIsNull(memberPublicId))
            .thenReturn(Optional.of(member))
        return member
    }

    private fun setupNoDuplicate() {
        whenever(checkInRepository.countRecentCheckIns(eq(50L), eq(10L), any())).thenReturn(0L)
    }

    private fun setupSavedCheckIn(): MemberCheckIn {
        val savedCheckIn =
            MemberCheckIn(
                id = 1L,
                memberId = 50L,
                branchId = 10L,
                checkedInByUserId = 100L,
                method = "staff_phone",
            )
        whenever(checkInRepository.save(any<MemberCheckIn>())).thenReturn(savedCheckIn)
        whenever(checkInRepository.countTodayByBranch(eq(10L), any())).thenReturn(47L)
        whenever(membershipRepository.findByMemberIdAndMembershipStatusInAndDeletedAtIsNull(eq(50L), any()))
            .thenReturn(Optional.empty())
        return savedCheckIn
    }

    @Test
    fun `checkIn succeeds for active member and records check-in`() {
        activeMember()
        setupNoDuplicate()
        setupSavedCheckIn()

        val response =
            service.checkIn(
                memberPublicId,
                "staff_phone",
                actorUserPublicId,
                branchPublicId,
                orgPublicId,
                clubPublicId,
            )

        assertNotNull(response)
        assertEquals("Ahmed Al-Rashidi", response.memberName)
        assertEquals("+966501234567", response.memberPhone)
        assertEquals("staff_phone", response.method)
        verify(checkInRepository).save(any<MemberCheckIn>())
    }

    @Test
    fun `checkIn throws 409 with MEMBERSHIP_LAPSED when member is lapsed`() {
        val member: Member = mock()
        whenever(member.id).thenReturn(50L)
        whenever(member.membershipStatus).thenReturn("lapsed")
        whenever(memberRepository.findByPublicIdAndDeletedAtIsNull(memberPublicId))
            .thenReturn(Optional.of(member))
        whenever(membershipRepository.findByMemberIdAndMembershipStatusAndDeletedAtIsNull(eq(50L), eq("active")))
            .thenReturn(Optional.empty())

        val ex =
            assertThrows(ArenaException::class.java) {
                service.checkIn(
                    memberPublicId,
                    "staff_phone",
                    actorUserPublicId,
                    branchPublicId,
                    orgPublicId,
                    clubPublicId,
                )
            }

        assertEquals(409, ex.status.value())
        assertEquals("MEMBERSHIP_LAPSED", ex.errorCode)
    }

    @Test
    fun `checkIn throws 409 when member is inactive`() {
        val member: Member = mock()
        whenever(member.membershipStatus).thenReturn("inactive")
        whenever(memberRepository.findByPublicIdAndDeletedAtIsNull(memberPublicId))
            .thenReturn(Optional.of(member))

        val ex =
            assertThrows(ArenaException::class.java) {
                service.checkIn(
                    memberPublicId,
                    "staff_phone",
                    actorUserPublicId,
                    branchPublicId,
                    orgPublicId,
                    clubPublicId,
                )
            }

        assertEquals(409, ex.status.value())
        assertEquals("Member account is not active.", ex.message)
    }

    @Test
    fun `checkIn throws 409 when member is terminated`() {
        val member: Member = mock()
        whenever(member.membershipStatus).thenReturn("terminated")
        whenever(memberRepository.findByPublicIdAndDeletedAtIsNull(memberPublicId))
            .thenReturn(Optional.of(member))

        val ex =
            assertThrows(ArenaException::class.java) {
                service.checkIn(
                    memberPublicId,
                    "staff_phone",
                    actorUserPublicId,
                    branchPublicId,
                    orgPublicId,
                    clubPublicId,
                )
            }

        assertEquals(409, ex.status.value())
        assertEquals("Member account is not active.", ex.message)
    }

    @Test
    fun `checkIn throws 409 with duplicate message when checked in within 60 minutes`() {
        activeMember()
        whenever(checkInRepository.countRecentCheckIns(eq(50L), eq(10L), any())).thenReturn(1L)

        val recentCheckIn =
            MemberCheckIn(
                memberId = 50L,
                branchId = 10L,
                checkedInByUserId = 100L,
                method = "staff_phone",
                checkedInAt = Instant.now().minusSeconds(300),
            )
        whenever(checkInRepository.findTopByMemberIdAndBranchIdOrderByCheckedInAtDesc(50L, 10L))
            .thenReturn(recentCheckIn)

        val ex =
            assertThrows(ArenaException::class.java) {
                service.checkIn(
                    memberPublicId,
                    "staff_phone",
                    actorUserPublicId,
                    branchPublicId,
                    orgPublicId,
                    clubPublicId,
                )
            }

        assertEquals(409, ex.status.value())
        assert(ex.message.contains("Already checked in"))
    }

    @Test
    fun `checkIn allows check-in after 60-minute window has passed`() {
        activeMember()
        setupNoDuplicate()
        setupSavedCheckIn()

        val response =
            service.checkIn(
                memberPublicId,
                "qr_scan",
                actorUserPublicId,
                branchPublicId,
                orgPublicId,
                clubPublicId,
            )

        assertNotNull(response)
        verify(checkInRepository).save(any<MemberCheckIn>())
    }

    @Test
    fun `checkIn logs MEMBER_CHECKED_IN audit action`() {
        activeMember()
        setupNoDuplicate()
        setupSavedCheckIn()

        service.checkIn(
            memberPublicId,
            "staff_phone",
            actorUserPublicId,
            branchPublicId,
            orgPublicId,
            clubPublicId,
        )

        verify(auditService).logFromContext(
            action = eq(AuditAction.MEMBER_CHECKED_IN),
            entityType = eq("MemberCheckIn"),
            entityId = any(),
            changesJson = any(),
        )
    }

    @Test
    fun `checkIn returns todayCount in response`() {
        activeMember()
        setupNoDuplicate()
        setupSavedCheckIn()

        val response =
            service.checkIn(
                memberPublicId,
                "staff_phone",
                actorUserPublicId,
                branchPublicId,
                orgPublicId,
                clubPublicId,
            )

        assertEquals(47L, response.todayCount)
    }

    @Test
    fun `getTodayCount returns correct count for branch using Riyadh timezone`() {
        whenever(checkInRepository.countTodayByBranch(eq(10L), any())).thenReturn(23L)

        val response = service.getTodayCount(branchPublicId, orgPublicId)

        assertEquals(23L, response.count)
        assertEquals("Elixir Gym - Riyadh", response.branchName)
    }

    @Test
    fun `getRecent returns last 20 check-ins for branch`() {
        val projection: RecentCheckInProjection = mock()
        whenever(projection.publicId).thenReturn(UUID.randomUUID())
        whenever(projection.memberNameEn).thenReturn("Ahmed Al-Rashidi")
        whenever(projection.memberNameAr).thenReturn("أحمد الرشيدي")
        whenever(projection.phone).thenReturn("+966501234567")
        whenever(projection.method).thenReturn("qr_scan")
        whenever(projection.checkedInAt).thenReturn(Instant.now())

        whenever(checkInRepository.findRecentByBranch(10L)).thenReturn(listOf(projection))

        val response = service.getRecent(branchPublicId, orgPublicId)

        assertEquals(1, response.checkIns.size)
        assertEquals("Ahmed Al-Rashidi", response.checkIns[0].memberName)
    }
}
