package com.liyaqa.nexus.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateBranchNexusRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val nameAr: String,
    @field:NotBlank
    @field:Size(max = 255)
    val nameEn: String,
)
