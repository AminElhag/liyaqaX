package com.liyaqa.payment.dto

import java.time.Instant
import java.util.UUID

data class PaymentResponse(
    val id: UUID,
    val memberId: UUID,
    val memberName: String,
    val amountHalalas: Long,
    val amountSar: String,
    val paymentMethod: String,
    val referenceNumber: String?,
    val invoiceNumber: String?,
    val collectedBy: String,
    val paidAt: Instant,
)
