package com.liyaqa.invoice.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class InvoiceResponse(
    val id: UUID,
    val invoiceNumber: String,
    val memberId: UUID,
    val memberName: String,
    val subtotalHalalas: Long,
    val subtotalSar: String,
    val vatRate: BigDecimal,
    val vatAmountHalalas: Long,
    val vatAmountSar: String,
    val totalHalalas: Long,
    val totalSar: String,
    val paymentMethod: String,
    val issuedAt: Instant,
    val zatcaStatus: String,
    val zatcaUuid: String? = null,
    val zatcaHash: String? = null,
    val zatcaQrCode: String? = null,
    val previousInvoiceHash: String? = null,
    val invoiceCounterValue: Long? = null,
)
