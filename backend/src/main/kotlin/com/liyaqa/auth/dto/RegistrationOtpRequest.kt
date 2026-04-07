package com.liyaqa.auth.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.util.UUID

data class RegistrationOtpRequestRequest(
    @field:NotBlank(message = "Phone number is required")
    @field:Pattern(regexp = "^\\+966\\d{9}$", message = "Phone must be in +966XXXXXXXXX format")
    val phone: String,
    @field:NotNull(message = "Club ID is required")
    val clubId: UUID,
)

data class RegistrationOtpVerifyRequest(
    @field:NotBlank(message = "Phone number is required")
    @field:Pattern(regexp = "^\\+966\\d{9}$", message = "Phone must be in +966XXXXXXXXX format")
    val phone: String,
    @field:NotBlank(message = "OTP is required")
    @field:Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 digits")
    val otp: String,
    @field:NotNull(message = "Club ID is required")
    val clubId: UUID,
)

data class RegistrationOtpVerifyResponse(
    val registrationToken: String,
)
