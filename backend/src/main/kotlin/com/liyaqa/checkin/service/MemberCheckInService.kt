package com.liyaqa.checkin.service

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.branch.BranchRepository
import com.liyaqa.checkin.dto.CheckInResponse
import com.liyaqa.checkin.dto.RecentCheckInItem
import com.liyaqa.checkin.dto.RecentCheckInsResponse
import com.liyaqa.checkin.dto.TodayCountResponse
import com.liyaqa.checkin.entity.MemberCheckIn
import com.liyaqa.checkin.repository.MemberCheckInRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MemberCheckInService(
    private val checkInRepository: MemberCheckInRepository,
    private val memberRepository: MemberRepository,
    private val branchRepository: BranchRepository,
    private val organizationRepository: OrganizationRepository,
    private val userRepository: UserRepository,
    private val membershipRepository: MembershipRepository,
    private val membershipPlanRepository: MembershipPlanRepository,
    private val auditService: AuditService,
) {
    companion object {
        private val RIYADH_ZONE = ZoneId.of("Asia/Riyadh")
        private val DUPLICATE_WINDOW = Duration.ofMinutes(60)
    }

    @Transactional
    fun checkIn(
        memberPublicId: UUID,
        method: String,
        actorUserPublicId: UUID,
        branchPublicId: UUID,
        organizationPublicId: UUID,
        clubPublicId: UUID,
    ): CheckInResponse {
        val org =
            organizationRepository.findByPublicIdAndDeletedAtIsNull(organizationPublicId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.") }

        val branch =
            branchRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(branchPublicId, org.id)
                .orElseGet {
                    branchRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(branchPublicId, org.id)
                        .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Branch not found.") }
                }

        val member =
            memberRepository.findByPublicIdAndDeletedAtIsNull(memberPublicId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Member not found.") }

        val actorUser =
            userRepository.findByPublicIdAndDeletedAtIsNull(actorUserPublicId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "User not found.") }

        validateMemberStatus(member)
        checkDuplicate(member.id, branch.id)

        val checkIn =
            checkInRepository.save(
                MemberCheckIn(
                    memberId = member.id,
                    branchId = branch.id,
                    checkedInByUserId = actorUser.id,
                    method = method,
                ),
            )

        auditService.logFromContext(
            action = AuditAction.MEMBER_CHECKED_IN,
            entityType = "MemberCheckIn",
            entityId = checkIn.publicId.toString(),
            changesJson = """{"memberId":"${member.publicId}","branchId":"${branch.publicId}","method":"$method"}""",
        )

        val todayCount = countToday(branch.id)
        val membershipPlanName = resolveMembershipPlanName(member.id)

        return CheckInResponse(
            checkInId = checkIn.publicId,
            memberName = "${member.firstNameEn} ${member.lastNameEn}",
            memberPhone = member.phone,
            membershipPlan = membershipPlanName,
            checkedInAt = checkIn.checkedInAt,
            branchName = branch.nameEn,
            method = method,
            todayCount = todayCount,
        )
    }

    fun getTodayCount(
        branchPublicId: UUID,
        organizationPublicId: UUID,
    ): TodayCountResponse {
        val org =
            organizationRepository.findByPublicIdAndDeletedAtIsNull(organizationPublicId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.") }

        val branch =
            branchRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(branchPublicId, org.id)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Branch not found.") }

        val todayRiyadh = LocalDate.now(RIYADH_ZONE)
        val count = checkInRepository.countTodayByBranch(branch.id, todayRiyadh)

        return TodayCountResponse(
            count = count,
            branchName = branch.nameEn,
            date = todayRiyadh,
        )
    }

    fun getRecent(
        branchPublicId: UUID,
        organizationPublicId: UUID,
    ): RecentCheckInsResponse {
        val org =
            organizationRepository.findByPublicIdAndDeletedAtIsNull(organizationPublicId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.") }

        val branch =
            branchRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(branchPublicId, org.id)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Branch not found.") }

        val projections = checkInRepository.findRecentByBranch(branch.id)

        return RecentCheckInsResponse(
            checkIns =
                projections.map { p ->
                    RecentCheckInItem(
                        checkInId = p.publicId,
                        memberName = p.memberNameEn ?: p.memberNameAr,
                        memberPhone = p.phone,
                        method = p.method,
                        checkedInAt = p.checkedInAt,
                    )
                },
        )
    }

    private fun validateMemberStatus(member: Member) {
        when (member.membershipStatus) {
            "active" -> return
            "lapsed" -> {
                val membership =
                    membershipRepository.findByMemberIdAndMembershipStatusAndDeletedAtIsNull(
                        member.id,
                        "active",
                    ).orElse(null)
                val endDateStr = membership?.endDate?.toString() ?: "N/A"
                throw ArenaException(
                    status = HttpStatus.CONFLICT,
                    errorType = "conflict",
                    message = "Membership expired on $endDateStr. Please renew before checking in.",
                    errorCode = "MEMBERSHIP_LAPSED",
                )
            }
            else -> throw ArenaException(
                status = HttpStatus.CONFLICT,
                errorType = "conflict",
                message = "Member account is not active.",
            )
        }
    }

    private fun checkDuplicate(
        memberId: Long,
        branchId: Long,
    ) {
        val threshold = Instant.now().minus(DUPLICATE_WINDOW)
        val recentCount = checkInRepository.countRecentCheckIns(memberId, branchId, threshold)
        if (recentCount > 0) {
            val lastCheckIn = checkInRepository.findTopByMemberIdAndBranchIdOrderByCheckedInAtDesc(memberId, branchId)
            val minutesAgo =
                lastCheckIn?.let {
                    Duration.between(it.checkedInAt, Instant.now()).toMinutes()
                } ?: DUPLICATE_WINDOW.toMinutes()
            throw ArenaException(
                status = HttpStatus.CONFLICT,
                errorType = "conflict",
                message = "Already checked in $minutesAgo minutes ago at this branch.",
            )
        }
    }

    private fun countToday(branchId: Long): Long {
        val todayRiyadh = LocalDate.now(RIYADH_ZONE)
        return checkInRepository.countTodayByBranch(branchId, todayRiyadh)
    }

    private fun resolveMembershipPlanName(memberId: Long): String? {
        val membership =
            membershipRepository.findByMemberIdAndMembershipStatusInAndDeletedAtIsNull(
                memberId, listOf("active", "frozen"),
            ).orElse(null) ?: return null

        return membershipPlanRepository.findById(membership.planId)
            .map { it.nameEn }
            .orElse(null)
    }
}
