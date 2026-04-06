package com.liyaqa.nexus

import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.ClubRepository
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.nexus.dto.PlatformStatsResponse
import com.liyaqa.organization.OrganizationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class PlatformStatsNexusService(
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val branchRepository: BranchRepository,
    private val memberRepository: MemberRepository,
    private val membershipRepository: MembershipRepository,
) {
    fun getStats(): PlatformStatsResponse {
        val totalOrgs = organizationRepository.countByDeletedAtIsNull()
        val totalClubs = clubRepository.countByDeletedAtIsNull()
        val totalBranches = branchRepository.countByDeletedAtIsNull()
        val totalActiveMembers = memberRepository.countByMembershipStatusAndDeletedAtIsNull("active")
        val totalActiveMemberships = membershipRepository.countByMembershipStatusAndDeletedAtIsNull("active")
        val mrrHalalas = membershipRepository.estimateTotalMrrHalalas()
        val mrrSar = BigDecimal(mrrHalalas).divide(BigDecimal(100), 2, RoundingMode.HALF_UP).toPlainString()
        val thirtyDaysAgo = LocalDate.now().minusDays(30)
        val newMembers = memberRepository.countNewMembersSince(thirtyDaysAgo)

        return PlatformStatsResponse(
            totalOrganizations = totalOrgs,
            totalClubs = totalClubs,
            totalBranches = totalBranches,
            totalActiveMembers = totalActiveMembers,
            totalActiveMemberships = totalActiveMemberships,
            estimatedMrrHalalas = mrrHalalas,
            estimatedMrrSar = mrrSar,
            newMembersLast30Days = newMembers,
            generatedAt = Instant.now(),
        )
    }
}
