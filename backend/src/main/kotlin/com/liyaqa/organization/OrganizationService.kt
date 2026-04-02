package com.liyaqa.organization

import com.liyaqa.common.audit.softDelete
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.dto.toPageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.dto.CreateOrganizationRequest
import com.liyaqa.organization.dto.OrganizationResponse
import com.liyaqa.organization.dto.UpdateOrganizationRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class OrganizationService(
    private val organizationRepository: OrganizationRepository,
) {
    @Transactional
    fun create(request: CreateOrganizationRequest): OrganizationResponse {
        if (organizationRepository.existsByEmailAndDeletedAtIsNull(request.email)) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "An organization with this email already exists.",
            )
        }

        val organization =
            Organization(
                nameAr = request.nameAr,
                nameEn = request.nameEn,
                email = request.email,
                phone = request.phone,
                vatNumber = request.vatNumber,
                crNumber = request.crNumber,
            )
        return organizationRepository.save(organization).toResponse()
    }

    fun getByPublicId(publicId: UUID): OrganizationResponse {
        val organization = findOrThrow(publicId)
        return organization.toResponse()
    }

    fun getAll(pageable: Pageable): PageResponse<OrganizationResponse> =
        organizationRepository.findAllByDeletedAtIsNull(pageable)
            .map { it.toResponse() }
            .toPageResponse()

    @Transactional
    fun update(
        publicId: UUID,
        request: UpdateOrganizationRequest,
    ): OrganizationResponse {
        val organization = findOrThrow(publicId)

        request.email?.let { newEmail ->
            if (organizationRepository.existsByEmailAndDeletedAtIsNullAndIdNot(newEmail, organization.id)) {
                throw ArenaException(
                    HttpStatus.CONFLICT,
                    "conflict",
                    "An organization with this email already exists.",
                )
            }
            organization.email = newEmail
        }

        request.nameAr?.let { organization.nameAr = it }
        request.nameEn?.let { organization.nameEn = it }
        request.phone?.let { organization.phone = it }
        request.country?.let { organization.country = it }
        request.timezone?.let { organization.timezone = it }
        request.locale?.let { organization.locale = it }
        request.isActive?.let { organization.isActive = it }
        request.vatNumber?.let { organization.vatNumber = it }
        request.crNumber?.let { organization.crNumber = it }

        return organizationRepository.save(organization).toResponse()
    }

    @Transactional
    fun delete(publicId: UUID) {
        val organization = findOrThrow(publicId)
        organization.softDelete()
        organizationRepository.save(organization)
    }

    private fun findOrThrow(publicId: UUID): Organization =
        organizationRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow {
                ArenaException(
                    HttpStatus.NOT_FOUND,
                    "resource-not-found",
                    "Organization not found.",
                )
            }

    private fun Organization.toResponse() =
        OrganizationResponse(
            id = publicId,
            nameAr = nameAr,
            nameEn = nameEn,
            email = email,
            phone = phone,
            country = country,
            timezone = timezone,
            locale = locale,
            isActive = isActive,
            vatNumber = vatNumber,
            crNumber = crNumber,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
