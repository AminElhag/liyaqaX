package com.liyaqa.report.schedule.dto

import java.time.Instant
import java.util.UUID

data class ReportScheduleResponse(
    val id: UUID,
    val templateId: UUID,
    val templateName: String,
    val frequency: String,
    val recipients: List<String>,
    val isActive: Boolean,
    val lastRunAt: Instant?,
    val lastRunStatus: String?,
    val lastError: String?,
    val nextRunAt: Instant,
    val createdAt: Instant,
)
