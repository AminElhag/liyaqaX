package com.liyaqa.gx.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateGXClassTypeRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val nameAr: String,
    @field:NotBlank
    @field:Size(max = 255)
    val nameEn: String,
    val descriptionAr: String? = null,
    val descriptionEn: String? = null,
    @field:Min(1)
    @field:Max(480)
    val defaultDurationMinutes: Int = 60,
    @field:Min(1)
    @field:Max(500)
    val defaultCapacity: Int = 20,
    @field:Pattern(regexp = "^#[0-9A-Fa-f]{6}$")
    val color: String? = null,
)
