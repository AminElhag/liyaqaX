package com.liyaqa.portal.dto

data class ClubPortalSettingsResponse(
    val gxBookingEnabled: Boolean,
    val ptViewEnabled: Boolean,
    val invoiceViewEnabled: Boolean,
    val onlinePaymentEnabled: Boolean,
    val portalMessage: String?,
    val selfRegistrationEnabled: Boolean,
    val logoUrl: String?,
    val primaryColorHex: String?,
    val secondaryColorHex: String?,
    val portalTitle: String?,
)
