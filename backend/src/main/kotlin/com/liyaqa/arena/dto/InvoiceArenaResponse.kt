package com.liyaqa.arena.dto

import java.time.Instant
import java.util.UUID

data class InvoiceArenaResponse(
    val id: UUID,
    val invoiceNumber: String,
    val issuedAt: Instant,
    val subtotalHalalas: Long,
    val vatAmountHalalas: Long,
    val totalHalalas: Long,
    val zatcaStatus: String,
)
