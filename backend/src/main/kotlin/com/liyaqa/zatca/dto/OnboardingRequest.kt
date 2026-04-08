package com.liyaqa.zatca.dto

import jakarta.validation.constraints.NotBlank

data class OnboardingRequest(
    @field:NotBlank
    val otp: String,
)
