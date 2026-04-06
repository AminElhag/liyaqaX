package com.liyaqa.lead.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateLeadSourceRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
    @field:NotBlank
    @field:Size(max = 100)
    val nameAr: String,
    @field:Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    val color: String? = null,
    val displayOrder: Int? = null,
)
