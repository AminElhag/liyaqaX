package com.liyaqa.portal.dto

import jakarta.validation.constraints.Size

data class UpdatePortalSettingsRequest(
    val gxBookingEnabled: Boolean? = null,
    val ptViewEnabled: Boolean? = null,
    val invoiceViewEnabled: Boolean? = null,
    val onlinePaymentEnabled: Boolean? = null,
    @field:Size(max = 500, message = "Portal message must be at most 500 characters")
    val portalMessage: String? = null,
)
