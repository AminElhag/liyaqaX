package com.liyaqa.member.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class EmergencyContactRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val nameAr: String,
    @field:NotBlank
    @field:Size(max = 255)
    val nameEn: String,
    @field:NotBlank
    @field:Size(max = 50)
    val phone: String,
    @field:Size(max = 100)
    val relationship: String? = null,
)
