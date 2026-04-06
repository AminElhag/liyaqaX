package com.liyaqa.lead

import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.lead.dto.CreateLeadSourceRequest
import com.liyaqa.lead.dto.LeadSourceResponse
import com.liyaqa.lead.dto.UpdateLeadSourceRequest
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class LeadSourceService(
    private val leadSourceRepository: LeadSourceRepository,
    private val leadRepository: LeadRepository,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
) {
    fun listByClub(
        orgPublicId: UUID,
        clubPublicId: UUID,
    ): List<LeadSourceResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)

        val sources = leadSourceRepository.findAllByClubIdAndDeletedAtIsNull(club.id)
        val leadCounts = leadRepository.countByClubIdGroupedBySourceId(club.id)

        return sources
            .sortedBy { it.displayOrder }
            .map { it.toResponse(leadCounts[it.id] ?: 0L) }
    }

    @Transactional
    fun create(
        orgPublicId: UUID,
        clubPublicId: UUID,
        request: CreateLeadSourceRequest,
    ): LeadSourceResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)

        if (leadSourceRepository.existsByClubIdAndNameAndDeletedAtIsNull(club.id, request.name)) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "A lead source with name '${request.name}' already exists for this club.",
            )
        }

        val source =
            leadSourceRepository.save(
                LeadSource(
                    organizationId = org.id,
                    clubId = club.id,
                    name = request.name,
                    nameAr = request.nameAr,
                    color = request.color ?: "#6B7280",
                    displayOrder = request.displayOrder ?: 0,
                ),
            )

        return source.toResponse(0L)
    }

    @Transactional
    fun update(
        orgPublicId: UUID,
        clubPublicId: UUID,
        sourcePublicId: UUID,
        request: UpdateLeadSourceRequest,
    ): LeadSourceResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val source = findSourceOrThrow(sourcePublicId, club.id)

        request.name?.let { newName ->
            if (newName != source.name && leadSourceRepository.existsByClubIdAndNameAndDeletedAtIsNull(club.id, newName)) {
                throw ArenaException(
                    HttpStatus.CONFLICT,
                    "conflict",
                    "A lead source with name '$newName' already exists for this club.",
                )
            }
            source.name = newName
        }
        request.nameAr?.let { source.nameAr = it }
        request.color?.let { source.color = it }
        request.displayOrder?.let { source.displayOrder = it }

        leadSourceRepository.save(source)

        val leadCount = leadRepository.countByLeadSourceIdAndDeletedAtIsNull(source.id)
        return source.toResponse(leadCount)
    }

    @Transactional
    fun toggleActive(
        orgPublicId: UUID,
        clubPublicId: UUID,
        sourcePublicId: UUID,
    ): LeadSourceResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val source = findSourceOrThrow(sourcePublicId, club.id)

        source.isActive = !source.isActive
        leadSourceRepository.save(source)

        val leadCount = leadRepository.countByLeadSourceIdAndDeletedAtIsNull(source.id)
        return source.toResponse(leadCount)
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun findOrgOrThrow(orgPublicId: UUID): Organization =
        organizationRepository
            .findByPublicIdAndDeletedAtIsNull(orgPublicId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.")
            }

    private fun findClubOrThrow(
        clubPublicId: UUID,
        organizationId: Long,
    ): Club =
        clubRepository
            .findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, organizationId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.")
            }

    private fun findSourceOrThrow(
        sourcePublicId: UUID,
        clubId: Long,
    ): LeadSource =
        leadSourceRepository
            .findByPublicIdAndClubIdAndDeletedAtIsNull(sourcePublicId, clubId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Lead source not found.")
            }

    private fun LeadSource.toResponse(leadCount: Long) =
        LeadSourceResponse(
            id = publicId,
            name = name,
            nameAr = nameAr,
            color = color,
            isActive = isActive,
            displayOrder = displayOrder,
            leadCount = leadCount,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
