package com.liyaqa.payment.online.dto

import jakarta.validation.constraints.NotNull
import java.util.UUID

data class InitiatePaymentRequest(
    @field:NotNull(message = "membershipPublicId is required")
    val membershipPublicId: UUID,
)
