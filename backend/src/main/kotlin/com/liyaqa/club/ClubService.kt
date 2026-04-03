package com.liyaqa.club

import com.liyaqa.club.dto.ClubResponse
import com.liyaqa.club.dto.CreateClubRequest
import com.liyaqa.club.dto.UpdateClubRequest
import com.liyaqa.common.audit.softDelete
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.dto.toPageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ClubService(
    private val clubRepository: ClubRepository,
    private val organizationRepository: OrganizationRepository,
) {
    @Transactional
    fun create(
        orgPublicId: UUID,
        request: CreateClubRequest,
    ): ClubResponse {
        val organization = findOrgOrThrow(orgPublicId)

        val club =
            Club(
                organizationId = organization.id,
                nameAr = request.nameAr,
                nameEn = request.nameEn,
                email = request.email,
                phone = request.phone,
            )
        return clubRepository.save(club).toResponse(organization.publicId)
    }

    fun getByPublicId(
        orgPublicId: UUID,
        clubPublicId: UUID,
    ): ClubResponse {
        val organization = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, organization.id)
        return club.toResponse(organization.publicId)
    }

    fun getAll(
        orgPublicId: UUID,
        pageable: Pageable,
    ): PageResponse<ClubResponse> {
        val organization = findOrgOrThrow(orgPublicId)
        return clubRepository.findAllByOrganizationIdAndDeletedAtIsNull(organization.id, pageable)
            .map { it.toResponse(organization.publicId) }
            .toPageResponse()
    }

    @Transactional
    fun update(
        orgPublicId: UUID,
        clubPublicId: UUID,
        request: UpdateClubRequest,
    ): ClubResponse {
        val organization = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, organization.id)

        request.nameAr?.let { club.nameAr = it }
        request.nameEn?.let { club.nameEn = it }
        request.email?.let { club.email = it }
        request.phone?.let { club.phone = it }
        request.isActive?.let { club.isActive = it }

        return clubRepository.save(club).toResponse(organization.publicId)
    }

    @Transactional
    fun delete(
        orgPublicId: UUID,
        clubPublicId: UUID,
    ) {
        val organization = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, organization.id)
        club.softDelete()
        clubRepository.save(club)
    }

    private fun findOrgOrThrow(orgPublicId: UUID): Organization =
        organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId)
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
        clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, organizationId)
            .orElseThrow {
                ArenaException(
                    HttpStatus.NOT_FOUND,
                    "resource-not-found",
                    "Club not found.",
                )
            }

    private fun Club.toResponse(orgPublicId: UUID) =
        ClubResponse(
            id = publicId,
            organizationId = orgPublicId,
            nameAr = nameAr,
            nameEn = nameEn,
            email = email,
            phone = phone,
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
