package com.liyaqa.membership.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.time.LocalDate
import java.util.UUID

data class AssignMembershipRequest(
    @field:NotNull(message = "Plan ID is required")
    val planId: UUID,
    val startDate: LocalDate? = null,
    @field:NotBlank(message = "Payment method is required")
    @field:Pattern(
        regexp = "cash|card|bank-transfer|other",
        message = "Payment method must be one of: cash, card, bank-transfer, other",
    )
    val paymentMethod: String,
    @field:NotNull(message = "Amount is required")
    @field:Min(value = 1, message = "Amount must be positive")
    val amountHalalas: Long,
    val referenceNumber: String? = null,
    val notes: String? = null,
)
