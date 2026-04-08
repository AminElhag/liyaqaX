package com.liyaqa.membership.service

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.notification.NotificationService
import com.liyaqa.notification.NotificationType
import com.liyaqa.staff.StaffMemberRepository
import com.liyaqa.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

@Service
class MemberLapseService(
    private val membershipRepository: MembershipRepository,
    private val memberRepository: MemberRepository,
    private val auditService: AuditService,
    private val notificationService: NotificationService,
    private val staffMemberRepository: StaffMemberRepository,
    private val userRepository: UserRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(MemberLapseService::class.java)
        private val RIYADH_ZONE = ZoneId.of("Asia/Riyadh")
    }

    @Transactional
    fun lapseExpiredMemberships() {
        val today = LocalDate.now(RIYADH_ZONE)
        val expired = membershipRepository.findExpiredActiveMemberships(today)

        var lapsedCount = 0
        for (membership in expired) {
            membership.membershipStatus = "lapsed"
            membershipRepository.save(membership)

            val member = memberRepository.findById(membership.memberId).orElse(null) ?: continue
            if (member.membershipStatus != "active") continue

            // Business rule 3: skip member if another active membership exists
            val otherActive = membershipRepository.countActiveMembershipsByMemberId(member.id)
            if (otherActive > 0) continue

            member.membershipStatus = "lapsed"
            memberRepository.save(member)

            auditService.log(
                action = AuditAction.MEMBERSHIP_LAPSED,
                entityType = "Membership",
                entityId = membership.publicId.toString(),
                actorId = "system",
                actorScope = "system",
                organizationId = member.organizationId.toString(),
                clubId = member.clubId.toString(),
                changesJson = """{"memberId":"${member.publicId}","endDate":"${membership.endDate}"}""",
            )

            notifyClubStaff(member.clubId, member.publicId.toString(), member.firstNameEn, member.lastNameEn, membership.endDate)

            lapsedCount++
        }

        if (lapsedCount > 0) log.info("Lapsed {} expired memberships", lapsedCount)
    }

    @Transactional
    fun reactivateMemberIfLapsed(memberId: Long) {
        val member = memberRepository.findById(memberId).orElse(null) ?: return
        if (member.membershipStatus != "lapsed") return

        member.membershipStatus = "active"
        memberRepository.save(member)

        auditService.log(
            action = AuditAction.MEMBER_REACTIVATED,
            entityType = "Member",
            entityId = member.publicId.toString(),
            actorId = "system",
            actorScope = "system",
            organizationId = member.organizationId.toString(),
            clubId = member.clubId.toString(),
            changesJson = """{"previousStatus":"lapsed"}""",
        )
    }

    private fun notifyClubStaff(
        clubId: Long,
        memberPublicId: String,
        firstNameEn: String,
        lastNameEn: String,
        endDate: LocalDate,
    ) {
        try {
            val staffMembers = staffMemberRepository.findAllByClubIdAndDeletedAtIsNull(clubId)
            val users = userRepository.findAllById(staffMembers.map { it.userId })
            val memberName = "$firstNameEn $lastNameEn".trim()
            for (user in users) {
                notificationService.create(
                    recipientUserId = user.id,
                    recipientScope = "club",
                    type = NotificationType.MEMBERSHIP_LAPSED,
                    paramsJson = """{"memberName":"$memberName","endDate":"$endDate"}""",
                    entityType = "Member",
                    entityId = memberPublicId,
                )
            }
        } catch (e: Exception) {
            log.warn("Failed to create MEMBERSHIP_LAPSED notifications for club {}: {}", clubId, e.message)
        }
    }
}
