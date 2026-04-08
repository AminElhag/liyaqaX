package com.liyaqa.branch

import com.liyaqa.branch.dto.BranchResponse
import com.liyaqa.branch.dto.BranchSummaryResponse
import com.liyaqa.branch.dto.CreateBranchRequest
import com.liyaqa.branch.dto.UpdateBranchRequest
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.audit.softDelete
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.dto.toPageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.subscription.service.SubscriptionService
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class BranchService(
    private val branchRepository: BranchRepository,
    private val clubRepository: ClubRepository,
    private val organizationRepository: OrganizationRepository,
    private val subscriptionService: SubscriptionService,
) {
    @Transactional
    fun create(
        orgPublicId: UUID,
        clubPublicId: UUID,
        request: CreateBranchRequest,
    ): BranchResponse {
        val organization = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, organization.id)

        enforcebranchLimit(club.id)

        val branch =
            Branch(
                organizationId = organization.id,
                clubId = club.id,
                nameAr = request.nameAr,
                nameEn = request.nameEn,
                addressAr = request.addressAr,
                addressEn = request.addressEn,
                city = request.city,
                phone = request.phone,
                email = request.email,
            )
        return branchRepository.save(branch).toResponse(organization.publicId, club.publicId)
    }

    fun getByPublicId(
        orgPublicId: UUID,
        clubPublicId: UUID,
        branchPublicId: UUID,
    ): BranchResponse {
        val organization = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, organization.id)
        val branch = findBranchOrThrow(branchPublicId, organization.id, club.id)
        return branch.toResponse(organization.publicId, club.publicId)
    }

    fun getAll(
        orgPublicId: UUID,
        clubPublicId: UUID,
        pageable: Pageable,
    ): PageResponse<BranchSummaryResponse> {
        val organization = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, organization.id)
        return branchRepository
            .findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(organization.id, club.id, pageable)
            .map { it.toSummaryResponse() }
            .toPageResponse()
    }

    @Transactional
    fun update(
        orgPublicId: UUID,
        clubPublicId: UUID,
        branchPublicId: UUID,
        request: UpdateBranchRequest,
    ): BranchResponse {
        val organization = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, organization.id)
        val branch = findBranchOrThrow(branchPublicId, organization.id, club.id)

        request.nameAr?.let { branch.nameAr = it }
        request.nameEn?.let { branch.nameEn = it }
        request.addressAr?.let { branch.addressAr = it }
        request.addressEn?.let { branch.addressEn = it }
        request.city?.let { branch.city = it }
        request.phone?.let { branch.phone = it }
        request.email?.let { branch.email = it }
        request.isActive?.let { branch.isActive = it }

        return branchRepository.save(branch).toResponse(organization.publicId, club.publicId)
    }

    @Transactional
    fun delete(
        orgPublicId: UUID,
        clubPublicId: UUID,
        branchPublicId: UUID,
    ) {
        val organization = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, organization.id)
        val branch = findBranchOrThrow(branchPublicId, organization.id, club.id)
        // TODO(#1): check for active staff and members before allowing soft delete (enforced in Plan 4 and Plan 5)
        branch.softDelete()
        branchRepository.save(branch)
    }

    private fun enforcebranchLimit(clubId: Long) {
        val subscription = subscriptionService.findActiveByClubId(clubId) ?: return
        val plan = subscriptionService.findPlanById(subscription.planId) ?: return
        if (plan.maxBranches == 0) return
        val currentCount = branchRepository.countByClubIdAndDeletedAtIsNull(clubId)
        if (currentCount >= plan.maxBranches) {
            throw ArenaException(
                HttpStatus.PAYMENT_REQUIRED,
                "business-rule-violation",
                "Your plan allows a maximum of ${plan.maxBranches} branches. Upgrade to add more.",
                "PLAN_LIMIT_EXCEEDED",
            )
        }
    }

    private fun findOrgOrThrow(orgPublicId: UUID): Organization =
        organizationRepository
            .findByPublicIdAndDeletedAtIsNull(orgPublicId)
            .orElseThrow {
                ArenaException(
                    HttpStatus.NOT_FOUND,
                    "resource-not-found",
                    "Organization not found.",
                )
            }

    private fun findClubOrThrow(
        clubPublicId: UUID,
        organizationId: Long,
    ): Club =
        clubRepository
            .findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, organizationId)
            .orElseThrow {
                ArenaException(
                    HttpStatus.NOT_FOUND,
                    "resource-not-found",
                    "Club not found.",
                )
            }

    private fun findBranchOrThrow(
        branchPublicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): Branch =
        branchRepository
            .findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branchPublicId, organizationId, clubId)
            .orElseThrow {
                ArenaException(
                    HttpStatus.NOT_FOUND,
                    "resource-not-found",
                    "Branch not found.",
                )
            }

    private fun Branch.toResponse(
        orgPublicId: UUID,
        clubPublicId: UUID,
    ) = BranchResponse(
        id = publicId,
        organizationId = orgPublicId,
        clubId = clubPublicId,
        nameAr = nameAr,
        nameEn = nameEn,
        addressAr = addressAr,
        addressEn = addressEn,
        city = city,
        phone = phone,
        email = email,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun Branch.toSummaryResponse() =
        BranchSummaryResponse(
            id = publicId,
            nameAr = nameAr,
            nameEn = nameEn,
            city = city,
            isActive = isActive,
        )
}
