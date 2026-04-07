package com.liyaqa.report.builder.dto

import java.util.UUID

data class ReportResultResponse(
    val templateId: UUID,
    val runAt: String,
    val dateFrom: String,
    val dateTo: String,
    val columns: List<String>,
    val rows: List<Map<String, Any?>>,
    val rowCount: Int,
    val truncated: Boolean,
    val fromCache: Boolean,
)
