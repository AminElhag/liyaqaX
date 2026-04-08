package com.liyaqa.payment.online.client

data class MoyasarCreatePaymentRequest(
    val amount: Long,
    val currency: String = "SAR",
    val description: String,
    val publishableApiKey: String,
    val callbackUrl: String,
    val metadata: Map<String, String>,
)
