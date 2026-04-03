package com.liyaqa.club.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class UpdateClubRequest(
    @field:Size(max = 255)
    val nameAr: String? = null,
    @field:Size(max = 255)
    val nameEn: String? = null,
    @field:Email
    @field:Size(max = 255)
    val email: String? = null,
    @field:Size(max = 50)
    val phone: String? = null,
    val isActive: Boolean? = null,
)
