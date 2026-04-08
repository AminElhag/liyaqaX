package com.liyaqa.payment.online.dto

import java.time.Instant

data class PaymentStatusResponse(
    val moyasarId: String,
    val status: String,
    val paymentMethod: String?,
    val amountSar: String,
    val paidAt: Instant?,
)
