package com.liyaqa.nexus

import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.dto.toPageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.nexus.dto.ClubDetailNexusResponse
import com.liyaqa.nexus.dto.ClubListItemResponse
import com.liyaqa.nexus.dto.CreateClubNexusRequest
import com.liyaqa.nexus.dto.UpdateClubNexusRequest
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.staff.StaffMemberRepository
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ClubNexusService(
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val branchRepository: BranchRepository,
    private val memberRepository: MemberRepository,
    private val staffMemberRepository: StaffMemberRepository,
    private val membershipRepository: MembershipRepository,
    private val membershipPlanRepository: MembershipPlanRepository,
) {
    fun list(
        orgPublicId: UUID,
        pageable: Pageable,
    ): PageResponse<ClubListItemResponse> {
        val org = findOrgOrThrow(orgPublicId)
        return clubRepository.findAllByOrganizationIdAndDeletedAtIsNull(org.id, pageable)
            .map { it.toListItem() }
            .toPageResponse()
    }

    fun getById(
        orgPublicId: UUID,
        clubPublicId: UUID,
    ): ClubDetailNexusResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        return club.toDetail()
    }

    @Transactional
    fun create(
        orgPublicId: UUID,
        request: CreateClubNexusRequest,
    ): ClubDetailNexusResponse {
        val org = findOrgOrThrow(orgPublicId)
        if (clubRepository.existsByOrganizationIdAndNameEnAndDeletedAtIsNull(org.id, request.nameEn)) {
            throw ArenaException(HttpStatus.CONFLICT, "conflict", "Name already exists.")
        }
        val club =
            clubRepository.save(
                Club(
                    organizationId = org.id,
                    nameAr = request.nameAr,
                    nameEn = request.nameEn,
                    vatNumber = request.vatNumber,
                ),
            )
        return club.toDetail()
    }

    @Transactional
    fun update(
        orgPublicId: UUID,
        clubPublicId: UUID,
        request: UpdateClubNexusRequest,
    ): ClubDetailNexusResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)

        request.nameEn?.let { newName ->
            if (clubRepository.existsByOrganizationIdAndNameEnAndDeletedAtIsNullAndIdNot(org.id, newName, club.id)) {
                throw ArenaException(HttpStatus.CONFLICT, "conflict", "Name already exists.")
            }
            club.nameEn = newName
        }
        request.nameAr?.let { club.nameAr = it }
        request.vatNumber?.let { club.vatNumber = it }

        return clubRepository.save(club).toDetail()
    }

    fun estimateMrrHalalas(clubId: Long): Long = membershipRepository.estimateMrrHalalasForClub(clubId)

    private fun findOrgOrThrow(orgPublicId: UUID): Organization =
        organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.")
            }

    private fun findClubOrThrow(
        clubPublicId: UUID,
        organizationId: Long,
    ): Club =
        clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, organizationId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.")
            }

    private fun Club.toListItem(): ClubListItemResponse {
        val branchCount = branchRepository.countByClubIdAndDeletedAtIsNull(id)
        val staffCount = staffMemberRepository.countByClubIdAndDeletedAtIsNull(id)
        val memberCount = memberRepository.countByClubIdAndMembershipStatusAndDeletedAtIsNull(id, "active")
        return ClubListItemResponse(
            id = publicId,
            nameAr = nameAr,
            nameEn = nameEn,
            vatNumber = vatNumber,
            branchCount = branchCount.toInt(),
            staffCount = staffCount.toInt(),
            activeMemberCount = memberCount.toInt(),
            createdAt = createdAt,
        )
    }

    private fun Club.toDetail(): ClubDetailNexusResponse {
        val branchCount = branchRepository.countByClubIdAndDeletedAtIsNull(id)
        val staffCount = staffMemberRepository.countByClubIdAndDeletedAtIsNull(id)
        val memberCount = memberRepository.countByClubIdAndMembershipStatusAndDeletedAtIsNull(id, "active")
        val mrrHalalas = estimateMrrHalalas(id)
        val mrrSar = BigDecimal(mrrHalalas).divide(BigDecimal(100), 2, RoundingMode.HALF_UP).toPlainString()
        return ClubDetailNexusResponse(
            id = publicId,
            nameAr = nameAr,
            nameEn = nameEn,
            vatNumber = vatNumber,
            branchCount = branchCount.toInt(),
            staffCount = staffCount.toInt(),
            activeMemberCount = memberCount.toInt(),
            estimatedMrrHalalas = mrrHalalas,
            estimatedMrrSar = mrrSar,
            createdAt = createdAt,
        )
    }
}
