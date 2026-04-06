package com.liyaqa.cashdrawer.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateEntryRequest(
    @field:NotBlank(message = "Entry type is required")
    @field:Pattern(regexp = "^(cash_in|cash_out|float_adjustment)$", message = "Must be 'cash_in', 'cash_out', or 'float_adjustment'")
    val entryType: String,
    @field:Min(1, message = "Amount must be greater than zero")
    val amountHalalas: Long,
    @field:NotBlank(message = "Description is required")
    @field:Size(max = 255, message = "Description must not exceed 255 characters")
    val description: String,
    val paymentId: UUID? = null,
    val recordedAt: Instant? = null,
)
