package com.liyaqa.portal

import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.portal.dto.ClubPortalSettingsResponse
import com.liyaqa.portal.dto.UpdatePortalSettingsRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ClubPortalSettingsService(
    private val settingsRepository: ClubPortalSettingsRepository,
    private val clubRepository: ClubRepository,
) {
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
        val settings = getOrCreateSettings(clubId)
        request.gxBookingEnabled?.let { settings.gxBookingEnabled = it }
        request.ptViewEnabled?.let { settings.ptViewEnabled = it }
        request.invoiceViewEnabled?.let { settings.invoiceViewEnabled = it }
        request.onlinePaymentEnabled?.let { settings.onlinePaymentEnabled = it }
        request.portalMessage?.let { settings.portalMessage = it.ifBlank { null } }
        request.selfRegistrationEnabled?.let { settings.selfRegistrationEnabled = it }
        return settingsRepository.save(settings).toResponse()
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
        )
}
