package com.liyaqa.nexus.dto

import jakarta.validation.constraints.Size

data class UpdateOrganizationNexusRequest(
    @field:Size(max = 255)
    val nameAr: String? = null,
    @field:Size(max = 255)
    val nameEn: String? = null,
    @field:Size(max = 50)
    val vatNumber: String? = null,
)
