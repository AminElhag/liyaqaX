package com.liyaqa.zatca.dto

import java.util.UUID

data class ZatcaFailedInvoiceResponse(
    val invoicePublicId: UUID,
    val invoiceNumber: String?,
    val clubName: String,
    val memberName: String,
    val amountSar: String,
    val createdAt: String,
    val zatcaRetryCount: Int,
    val zatcaLastError: String?,
    val zatcaStatus: String,
)
