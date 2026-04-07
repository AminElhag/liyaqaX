package com.liyaqa.report.builder.dto

import java.time.Instant
import java.util.UUID

data class ReportTemplateResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val metrics: List<String>,
    val dimensions: List<String>,
    val filters: Map<String, String?>?,
    val metricScope: String?,
    val isSystem: Boolean,
    val lastRunAt: Instant?,
    val createdAt: Instant,
)
