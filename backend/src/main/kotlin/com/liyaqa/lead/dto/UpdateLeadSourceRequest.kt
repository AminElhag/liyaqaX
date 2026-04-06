package com.liyaqa.lead.dto

import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateLeadSourceRequest(
    @field:Size(max = 100)
    val name: String? = null,
    @field:Size(max = 100)
    val nameAr: String? = null,
    @field:Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    val color: String? = null,
    val displayOrder: Int? = null,
)
