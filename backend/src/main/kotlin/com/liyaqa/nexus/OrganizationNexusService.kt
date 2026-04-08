package com.liyaqa.nexus

import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.dto.toPageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.MemberRepository
import com.liyaqa.nexus.dto.ClubSummaryItem
import com.liyaqa.nexus.dto.CreateOrganizationNexusRequest
import com.liyaqa.nexus.dto.OrgDetailResponse
import com.liyaqa.nexus.dto.OrgListItemResponse
import com.liyaqa.nexus.dto.UpdateOrganizationNexusRequest
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class OrganizationNexusService(
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val branchRepository: BranchRepository,
    private val memberRepository: MemberRepository,
) {
    fun list(
        q: String?,
        pageable: Pageable,
    ): PageResponse<OrgListItemResponse> {
        val page =
            if (q.isNullOrBlank()) {
                organizationRepository.findAllByDeletedAtIsNull(pageable)
            } else {
                organizationRepository.findAllByDeletedAtIsNullAndNameSearch(q, pageable)
            }
        return page.map { it.toListItem() }.toPageResponse()
    }

    fun getById(publicId: UUID): OrgDetailResponse {
        val org = findOrThrow(publicId)
        val clubs = clubRepository.findAllByOrganizationIdAndDeletedAtIsNull(org.id)
        return org.toDetailResponse(clubs)
    }

    @Transactional
    fun create(request: CreateOrganizationNexusRequest): OrgDetailResponse {
        if (organizationRepository.existsByNameEnAndDeletedAtIsNull(request.nameEn)) {
            throw ArenaException(HttpStatus.CONFLICT, "conflict", "Name already exists.")
        }
        val org =
            organizationRepository.save(
                Organization(
                    nameAr = request.nameAr,
                    nameEn = request.nameEn,
                    email = request.email,
                    vatNumber = request.vatNumber,
                ),
            )
        return org.toDetailResponse(emptyList())
    }

    @Transactional
    fun update(
        publicId: UUID,
        request: UpdateOrganizationNexusRequest,
    ): OrgDetailResponse {
        val org = findOrThrow(publicId)

        request.nameEn?.let { newName ->
            if (organizationRepository.existsByNameEnAndDeletedAtIsNullAndIdNot(newName, org.id)) {
                throw ArenaException(HttpStatus.CONFLICT, "conflict", "Name already exists.")
            }
            org.nameEn = newName
        }
        request.nameAr?.let { org.nameAr = it }
        request.vatNumber?.let { org.vatNumber = it }

        val saved = organizationRepository.save(org)
        val clubs = clubRepository.findAllByOrganizationIdAndDeletedAtIsNull(saved.id)
        return saved.toDetailResponse(clubs)
    }

    private fun findOrThrow(publicId: UUID): Organization =
        organizationRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.")
            }

    private fun Organization.toListItem(): OrgListItemResponse {
        val clubCount = clubRepository.countByOrganizationIdAndDeletedAtIsNull(id)
        val memberCount = memberRepository.countByOrganizationIdAndMembershipStatusAndDeletedAtIsNull(id, "active")
        return OrgListItemResponse(
            id = publicId,
            nameAr = nameAr,
            nameEn = nameEn,
            vatNumber = vatNumber,
            clubCount = clubCount.toInt(),
            activeMemberCount = memberCount.toInt(),
            createdAt = createdAt,
        )
    }

    private fun Organization.toDetailResponse(clubs: List<Club>): OrgDetailResponse =
        OrgDetailResponse(
            id = publicId,
            nameAr = nameAr,
            nameEn = nameEn,
            vatNumber = vatNumber,
            createdAt = createdAt,
            clubs =
                clubs.map { club ->
                    val branchCount = branchRepository.countByClubIdAndDeletedAtIsNull(club.id)
                    val memberCount =
                        memberRepository.countByClubIdAndMembershipStatusAndDeletedAtIsNull(club.id, "active")
                    ClubSummaryItem(
                        id = club.publicId,
                        nameAr = club.nameAr,
                        nameEn = club.nameEn,
                        branchCount = branchCount.toInt(),
                        activeMemberCount = memberCount.toInt(),
                    )
                },
        )
}
