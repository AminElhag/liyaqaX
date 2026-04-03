package com.liyaqa.organization.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateOrganizationRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val nameAr: String,
    @field:NotBlank
    @field:Size(max = 255)
    val nameEn: String,
    @field:NotBlank
    @field:Email
    @field:Size(max = 255)
    val email: String,
    @field:Size(max = 50)
    val phone: String? = null,
    @field:Size(max = 50)
    val vatNumber: String? = null,
    @field:Size(max = 50)
    val crNumber: String? = null,
)
