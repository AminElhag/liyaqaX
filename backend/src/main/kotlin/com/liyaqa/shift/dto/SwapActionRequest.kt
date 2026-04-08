package com.liyaqa.shift.dto

import jakarta.validation.constraints.NotBlank

data class SwapActionRequest(
    @field:NotBlank
    val action: String,
)
