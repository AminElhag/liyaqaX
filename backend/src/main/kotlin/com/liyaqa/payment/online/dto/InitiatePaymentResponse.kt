package com.liyaqa.payment.online.dto

import java.util.UUID

data class InitiatePaymentResponse(
    val transactionId: UUID,
    val hostedUrl: String,
    val amountSar: String,
    val planName: String,
)
