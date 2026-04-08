package com.liyaqa.membership.service

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.Membership
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.notification.NotificationService
import com.liyaqa.notification.NotificationType
import com.liyaqa.staff.StaffMember
import com.liyaqa.staff.StaffMemberRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class MemberLapseServiceTest {
    @Mock lateinit var membershipRepository: MembershipRepository

    @Mock lateinit var memberRepository: MemberRepository

    @Mock lateinit var auditService: AuditService

    @Mock lateinit var notificationService: NotificationService

    @Mock lateinit var staffMemberRepository: StaffMemberRepository

    @Mock lateinit var userRepository: UserRepository

    @InjectMocks lateinit var service: MemberLapseService

    private fun createMembership(
        memberId: Long = 1L,
        status: String = "active",
    ): Membership =
        Membership(
            organizationId = 1L,
            clubId = 1L,
            branchId = 1L,
            memberId = memberId,
            planId = 1L,
            membershipStatus = status,
            startDate = LocalDate.now().minusDays(60),
            endDate = LocalDate.now().minusDays(1),
        )

    private fun createMember(status: String = "active"): Member =
        Member(
            organizationId = 1L,
            clubId = 1L,
            branchId = 1L,
            userId = 10L,
            firstNameAr = "أحمد",
            firstNameEn = "Ahmed",
            lastNameAr = "الرشيدي",
            lastNameEn = "Al-Rashidi",
            phone = "+966500000000",
            membershipStatus = status,
        )

    @Test
    fun `lapseExpiredMemberships transitions active membership and member to LAPSED`() {
        val membership = createMembership()
        val member = createMember()

        whenever(membershipRepository.findExpiredActiveMemberships(any())).thenReturn(listOf(membership))
        whenever(memberRepository.findById(1L)).thenReturn(Optional.of(member))
        whenever(membershipRepository.countActiveMembershipsByMemberId(any())).thenReturn(0)
        whenever(staffMemberRepository.findAllByClubIdAndDeletedAtIsNull(any())).thenReturn(emptyList())

        service.lapseExpiredMemberships()

        verify(membershipRepository).save(membership)
        assert(membership.membershipStatus == "lapsed")
        verify(memberRepository).save(member)
        assert(member.membershipStatus == "lapsed")
    }

    @Test
    fun `lapseExpiredMemberships skips already-lapsed memberships (idempotent)`() {
        whenever(membershipRepository.findExpiredActiveMemberships(any())).thenReturn(emptyList())

        service.lapseExpiredMemberships()

        verify(memberRepository, never()).save(any())
    }

    @Test
    fun `lapseExpiredMemberships skips member if another active membership exists`() {
        val membership = createMembership()
        val member = createMember()

        whenever(membershipRepository.findExpiredActiveMemberships(any())).thenReturn(listOf(membership))
        whenever(memberRepository.findById(1L)).thenReturn(Optional.of(member))
        whenever(membershipRepository.countActiveMembershipsByMemberId(any())).thenReturn(1)

        service.lapseExpiredMemberships()

        verify(membershipRepository).save(membership)
        assert(membership.membershipStatus == "lapsed")
        assert(member.membershipStatus == "active")
        verify(memberRepository, never()).save(any())
    }

    @Test
    fun `lapseExpiredMemberships fires MEMBERSHIP_LAPSED notification for each lapsed member`() {
        val membership = createMembership()
        val member = createMember()
        val staffMember =
            StaffMember(
                organizationId = 1L,
                clubId = 1L,
                userId = 20L,
                roleId = 5L,
                firstNameAr = "موظف",
                firstNameEn = "Staff",
                lastNameAr = "الموظف",
                lastNameEn = "Member",
                joinedAt = LocalDate.now(),
            )
        val staffUser =
            User(
                email = "staff@test.com",
                passwordHash = "hash",
            )

        whenever(membershipRepository.findExpiredActiveMemberships(any())).thenReturn(listOf(membership))
        whenever(memberRepository.findById(1L)).thenReturn(Optional.of(member))
        whenever(membershipRepository.countActiveMembershipsByMemberId(any())).thenReturn(0)
        whenever(staffMemberRepository.findAllByClubIdAndDeletedAtIsNull(any())).thenReturn(listOf(staffMember))
        whenever(userRepository.findAllById(any<Iterable<Long>>())).thenReturn(listOf(staffUser))

        service.lapseExpiredMemberships()

        verify(notificationService).create(
            recipientUserId = any(),
            recipientScope = eq("club"),
            type = eq(NotificationType.MEMBERSHIP_LAPSED),
            paramsJson = any(),
            entityType = eq("Member"),
            entityId = any(),
        )
    }

    @Test
    fun `lapseExpiredMemberships logs MEMBERSHIP_LAPSED audit action`() {
        val membership = createMembership()
        val member = createMember()

        whenever(membershipRepository.findExpiredActiveMemberships(any())).thenReturn(listOf(membership))
        whenever(memberRepository.findById(1L)).thenReturn(Optional.of(member))
        whenever(membershipRepository.countActiveMembershipsByMemberId(any())).thenReturn(0)
        whenever(staffMemberRepository.findAllByClubIdAndDeletedAtIsNull(any())).thenReturn(emptyList())

        service.lapseExpiredMemberships()

        verify(auditService).log(
            action = eq(AuditAction.MEMBERSHIP_LAPSED),
            entityType = eq("Membership"),
            entityId = any(),
            actorId = eq("system"),
            actorScope = eq("system"),
            organizationId = any(),
            clubId = any(),
            changesJson = any(),
            ipAddress = eq(null),
        )
    }

    @Test
    fun `reactivateMemberIfLapsed transitions LAPSED member to ACTIVE`() {
        val member = createMember(status = "lapsed")
        whenever(memberRepository.findById(1L)).thenReturn(Optional.of(member))

        service.reactivateMemberIfLapsed(1L)

        assert(member.membershipStatus == "active")
        verify(memberRepository).save(member)
    }

    @Test
    fun `reactivateMemberIfLapsed does nothing if member is not LAPSED`() {
        val member = createMember(status = "active")
        whenever(memberRepository.findById(1L)).thenReturn(Optional.of(member))

        service.reactivateMemberIfLapsed(1L)

        assert(member.membershipStatus == "active")
        verify(memberRepository, never()).save(any())
    }

    @Test
    fun `reactivateMemberIfLapsed logs MEMBER_REACTIVATED audit action`() {
        val member = createMember(status = "lapsed")
        whenever(memberRepository.findById(1L)).thenReturn(Optional.of(member))

        service.reactivateMemberIfLapsed(1L)

        verify(auditService).log(
            action = eq(AuditAction.MEMBER_REACTIVATED),
            entityType = eq("Member"),
            entityId = any(),
            actorId = eq("system"),
            actorScope = eq("system"),
            organizationId = any(),
            clubId = any(),
            changesJson = any(),
            ipAddress = eq(null),
        )
    }
}
