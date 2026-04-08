package com.liyaqa.portal.dto

import jakarta.validation.constraints.Size

data class UpdatePortalSettingsRequest(
    val gxBookingEnabled: Boolean? = null,
    val ptViewEnabled: Boolean? = null,
    val invoiceViewEnabled: Boolean? = null,
    val onlinePaymentEnabled: Boolean? = null,
    @field:Size(max = 500, message = "Portal message must be at most 500 characters")
    val portalMessage: String? = null,
    val selfRegistrationEnabled: Boolean? = null,
    val logoUrl: String? = null,
    val primaryColorHex: String? = null,
    val secondaryColorHex: String? = null,
    val portalTitle: String? = null,
) {
    fun hasBrandingFields(): Boolean =
        logoUrl != null ||
            primaryColorHex != null ||
            secondaryColorHex != null ||
            portalTitle != null
}
