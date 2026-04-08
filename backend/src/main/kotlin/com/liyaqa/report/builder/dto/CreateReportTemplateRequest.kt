package com.liyaqa.report.builder.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class CreateReportTemplateRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val name: String,
    @field:Size(max = 500)
    val description: String? = null,
    @field:NotEmpty
    val metrics: List<String>,
    @field:NotEmpty
    val dimensions: List<String>,
    val filters: Map<String, String?>? = null,
)
