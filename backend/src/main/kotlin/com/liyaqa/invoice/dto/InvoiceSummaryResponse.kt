package com.liyaqa.invoice.dto

import java.time.Instant
import java.util.UUID

data class InvoiceSummaryResponse(
    val id: UUID,
    val invoiceNumber: String,
    val totalHalalas: Long,
    val totalSar: String,
    val issuedAt: Instant,
    val zatcaStatus: String,
)
