package com.liyaqa.zatca.dto

data class OnboardingStatusResponse(
    val status: String,
    val environment: String?,
    val csidExpiresAt: String?,
    val onboardingStatus: String,
)
