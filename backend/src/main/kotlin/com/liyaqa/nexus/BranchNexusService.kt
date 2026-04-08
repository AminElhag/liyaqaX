package com.liyaqa.nexus

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.dto.toPageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.MemberRepository
import com.liyaqa.nexus.dto.BranchDetailNexusResponse
import com.liyaqa.nexus.dto.BranchListItemResponse
import com.liyaqa.nexus.dto.CreateBranchNexusRequest
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.staff.StaffBranchAssignmentRepository
import com.liyaqa.trainer.TrainerBranchAssignmentRepository
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class BranchNexusService(
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val branchRepository: BranchRepository,
    private val memberRepository: MemberRepository,
    private val staffBranchAssignmentRepository: StaffBranchAssignmentRepository,
    private val trainerBranchAssignmentRepository: TrainerBranchAssignmentRepository,
) {
    fun list(
        orgPublicId: UUID,
        clubPublicId: UUID,
        pageable: Pageable,
    ): PageResponse<BranchListItemResponse> {
        val (_, club) = resolveOrgAndClub(orgPublicId, clubPublicId)
        return branchRepository.findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(club.organizationId, club.id, pageable)
            .map { it.toListItem() }
            .toPageResponse()
    }

    fun getById(
        orgPublicId: UUID,
        clubPublicId: UUID,
        branchPublicId: UUID,
    ): BranchDetailNexusResponse {
        val (_, club) = resolveOrgAndClub(orgPublicId, clubPublicId)
        val branch = findBranchOrThrow(branchPublicId, club.id)
        return branch.toDetail()
    }

    @Transactional
    fun create(
        orgPublicId: UUID,
        clubPublicId: UUID,
        request: CreateBranchNexusRequest,
    ): BranchDetailNexusResponse {
        val (org, club) = resolveOrgAndClub(orgPublicId, clubPublicId)
        if (branchRepository.existsByClubIdAndNameEnAndDeletedAtIsNull(club.id, request.nameEn)) {
            throw ArenaException(HttpStatus.CONFLICT, "conflict", "Name already exists.")
        }
        val branch =
            branchRepository.save(
                Branch(
                    organizationId = org.id,
                    clubId = club.id,
                    nameAr = request.nameAr,
                    nameEn = request.nameEn,
                ),
            )
        return branch.toDetail()
    }

    @Transactional
    fun update(
        orgPublicId: UUID,
        clubPublicId: UUID,
        branchPublicId: UUID,
        request: CreateBranchNexusRequest,
    ): BranchDetailNexusResponse {
        val (_, club) = resolveOrgAndClub(orgPublicId, clubPublicId)
        val branch = findBranchOrThrow(branchPublicId, club.id)

        if (branchRepository.existsByClubIdAndNameEnAndDeletedAtIsNullAndIdNot(club.id, request.nameEn, branch.id)) {
            throw ArenaException(HttpStatus.CONFLICT, "conflict", "Name already exists.")
        }
        branch.nameAr = request.nameAr
        branch.nameEn = request.nameEn

        return branchRepository.save(branch).toDetail()
    }

    private fun resolveOrgAndClub(
        orgPublicId: UUID,
        clubPublicId: UUID,
    ): Pair<Organization, Club> {
        val org =
            organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.")
                }
        val club =
            clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, org.id)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.")
                }
        return org to club
    }

    private fun findBranchOrThrow(
        branchPublicId: UUID,
        clubId: Long,
    ): Branch =
        branchRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(branchPublicId, clubId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Branch not found.")
            }

    private fun Branch.toListItem(): BranchListItemResponse {
        val staffCount = staffBranchAssignmentRepository.countByBranchId(id)
        val trainerCount = trainerBranchAssignmentRepository.countByBranchId(id)
        val memberCount = memberRepository.countByBranchIdAndMembershipStatusAndDeletedAtIsNull(id, "active")
        return BranchListItemResponse(
            id = publicId,
            nameAr = nameAr,
            nameEn = nameEn,
            staffCount = staffCount.toInt(),
            trainerCount = trainerCount.toInt(),
            activeMemberCount = memberCount.toInt(),
            createdAt = createdAt,
        )
    }

    private fun Branch.toDetail(): BranchDetailNexusResponse {
        val staffCount = staffBranchAssignmentRepository.countByBranchId(id)
        val trainerCount = trainerBranchAssignmentRepository.countByBranchId(id)
        val memberCount = memberRepository.countByBranchIdAndMembershipStatusAndDeletedAtIsNull(id, "active")
        return BranchDetailNexusResponse(
            id = publicId,
            nameAr = nameAr,
            nameEn = nameEn,
            staffCount = staffCount.toInt(),
            trainerCount = trainerCount.toInt(),
            activeMemberCount = memberCount.toInt(),
            createdAt = createdAt,
        )
    }
}
