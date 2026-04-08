package com.liyaqa.cashdrawer.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class CloseSessionRequest(
    @field:NotNull(message = "Counted closing balance is required")
    @field:Min(0, message = "Counted closing balance must be non-negative")
    val countedClosingHalalas: Long,
    val notes: String? = null,
)
