package com.liyaqa.cashdrawer.dto

import jakarta.validation.constraints.Min

data class OpenSessionRequest(
    @field:Min(0, message = "Opening float must be non-negative")
    val openingFloatHalalas: Long,
    val notes: String? = null,
)
