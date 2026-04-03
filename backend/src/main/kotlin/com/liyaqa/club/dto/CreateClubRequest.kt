package com.liyaqa.club.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateClubRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val nameAr: String,
    @field:NotBlank
    @field:Size(max = 255)
    val nameEn: String,
    @field:Email
    @field:Size(max = 255)
    val email: String? = null,
    @field:Size(max = 50)
    val phone: String? = null,
)
