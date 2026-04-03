package com.liyaqa.organization.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class UpdateOrganizationRequest(
    @field:Size(max = 255)
    val nameAr: String? = null,
    @field:Size(max = 255)
    val nameEn: String? = null,
    @field:Email
    @field:Size(max = 255)
    val email: String? = null,
    @field:Size(max = 50)
    val phone: String? = null,
    @field:Size(max = 10)
    val country: String? = null,
    @field:Size(max = 50)
    val timezone: String? = null,
    @field:Size(max = 10)
    val locale: String? = null,
    val isActive: Boolean? = null,
    @field:Size(max = 50)
    val vatNumber: String? = null,
    @field:Size(max = 50)
    val crNumber: String? = null,
)
