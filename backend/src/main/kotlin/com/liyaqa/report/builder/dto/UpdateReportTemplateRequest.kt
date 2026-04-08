package com.liyaqa.report.builder.dto

import jakarta.validation.constraints.Size

data class UpdateReportTemplateRequest(
    @field:Size(max = 200)
    val name: String? = null,
    @field:Size(max = 500)
    val description: String? = null,
    val metrics: List<String>? = null,
    val dimensions: List<String>? = null,
    val filters: Map<String, String?>? = null,
)
