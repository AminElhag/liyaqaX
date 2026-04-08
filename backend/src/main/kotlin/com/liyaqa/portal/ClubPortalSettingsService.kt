package com.liyaqa.portal

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.permission.PermissionConstants
import com.liyaqa.portal.dto.ClubPortalSettingsResponse
import com.liyaqa.portal.dto.UpdatePortalSettingsRequest
import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtClaims
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ClubPortalSettingsService(
    private val settingsRepository: ClubPortalSettingsRepository,
    private val clubRepository: ClubRepository,
    private val auditService: AuditService,
    private val permissionService: PermissionService,
) {
    companion object {
        private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
    }

    @Transactional
    fun getSettings(clubId: Long): ClubPortalSettingsResponse = getOrCreateSettings(clubId).toResponse()

    fun getSettingsByClubPublicId(clubPublicId: UUID): ClubPortalSettingsResponse {
        val club =
            clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, resolveOrgId(clubPublicId))
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.") }
        return getOrCreateSettings(club.id).toResponse()
    }

    @Transactional
    fun getOrCreateSettings(clubId: Long): ClubPortalSettings =
        settingsRepository.findByClubId(clubId).orElseGet {
            settingsRepository.save(ClubPortalSettings(clubId = clubId))
        }

    @Transactional
    fun updateSettings(
        clubId: Long,
        request: UpdatePortalSettingsRequest,
    ): ClubPortalSettingsResponse {
        if (request.hasBrandingFields()) {
            requireBrandingPermission()
            validateBrandingFields(request)
        }

        val settings = getOrCreateSettings(clubId)
        val brandingChanges = mutableMapOf<String, String>()

        request.gxBookingEnabled?.let { settings.gxBookingEnabled = it }
        request.ptViewEnabled?.let { settings.ptViewEnabled = it }
        request.invoiceViewEnabled?.let { settings.invoiceViewEnabled = it }
        request.onlinePaymentEnabled?.let { settings.onlinePaymentEnabled = it }
        request.portalMessage?.let { settings.portalMessage = it.ifBlank { null } }
        request.selfRegistrationEnabled?.let { settings.selfRegistrationEnabled = it }

        request.logoUrl?.let {
            if (it != settings.logoUrl) brandingChanges["logoUrl"] = it
            settings.logoUrl = it.ifBlank { null }
        }
        request.primaryColorHex?.let {
            if (it != settings.primaryColorHex) brandingChanges["primaryColorHex"] = it
            settings.primaryColorHex = it.ifBlank { null }
        }
        request.secondaryColorHex?.let {
            if (it != settings.secondaryColorHex) brandingChanges["secondaryColorHex"] = it
            settings.secondaryColorHex = it.ifBlank { null }
        }
        request.portalTitle?.let {
            if (it != settings.portalTitle) brandingChanges["portalTitle"] = it
            settings.portalTitle = it.ifBlank { null }
        }

        val saved = settingsRepository.save(settings)

        if (brandingChanges.isNotEmpty()) {
            auditService.logFromContext(
                action = AuditAction.BRANDING_UPDATED,
                entityType = "ClubPortalSettings",
                entityId = saved.publicId.toString(),
                changesJson = brandingChanges.entries.joinToString(", ", "{", "}") { "\"${it.key}\": \"${it.value}\"" },
            )
        }

        return saved.toResponse()
    }

    fun isFeatureEnabled(
        clubId: Long,
        feature: String,
    ): Boolean {
        val settings = getOrCreateSettings(clubId)
        return when (feature) {
            "gx" -> settings.gxBookingEnabled
            "pt" -> settings.ptViewEnabled
            "invoice" -> settings.invoiceViewEnabled
            "payment" -> settings.onlinePaymentEnabled
            else -> false
        }
    }

    fun requireFeatureEnabled(
        clubId: Long,
        feature: String,
    ) {
        if (!isFeatureEnabled(clubId, feature)) {
            throw ArenaException(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "This feature is not enabled for your club.",
            )
        }
    }

    private fun validateBrandingFields(request: UpdatePortalSettingsRequest) {
        request.logoUrl?.let {
            if (it.isNotBlank() && !it.startsWith("https://")) {
                throw ArenaException(HttpStatus.BAD_REQUEST, "validation-failed", "Logo URL must start with https://")
            }
        }
        request.primaryColorHex?.let {
            if (it.isNotBlank() && !HEX_COLOR_REGEX.matches(it)) {
                throw ArenaException(
                    HttpStatus.BAD_REQUEST,
                    "validation-failed",
                    "Primary colour must be a valid hex code (e.g. #1A73E8)",
                )
            }
        }
        request.secondaryColorHex?.let {
            if (it.isNotBlank() && !HEX_COLOR_REGEX.matches(it)) {
                throw ArenaException(
                    HttpStatus.BAD_REQUEST,
                    "validation-failed",
                    "Secondary colour must be a valid hex code",
                )
            }
        }
        request.portalTitle?.let {
            if (it.isNotBlank() && (it.length < 1 || it.length > 100)) {
                throw ArenaException(
                    HttpStatus.BAD_REQUEST,
                    "validation-failed",
                    "Portal title must be between 1 and 100 characters",
                )
            }
        }
    }

    private fun requireBrandingPermission() {
        val auth = SecurityContextHolder.getContext().authentication
        val claims = auth?.details as? JwtClaims
        val roleId =
            claims?.roleId
                ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No role in token.")
        if (!permissionService.hasPermission(roleId, PermissionConstants.BRANDING_UPDATE)) {
            throw ArenaException(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "You do not have permission to update branding settings.",
            )
        }
    }

    private fun resolveOrgId(clubPublicId: UUID): Long =
        clubRepository.findAll().firstOrNull { it.publicId == clubPublicId }?.organizationId
            ?: throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.")

    private fun ClubPortalSettings.toResponse() =
        ClubPortalSettingsResponse(
            gxBookingEnabled = gxBookingEnabled,
            ptViewEnabled = ptViewEnabled,
            invoiceViewEnabled = invoiceViewEnabled,
            onlinePaymentEnabled = onlinePaymentEnabled,
            portalMessage = portalMessage,
            selfRegistrationEnabled = selfRegistrationEnabled,
            logoUrl = logoUrl,
            primaryColorHex = primaryColorHex,
            secondaryColorHex = secondaryColorHex,
            portalTitle = portalTitle,
        )
}
