package com.liyaqa.report.schedule.dto

data class UpdateReportScheduleRequest(
    val frequency: String? = null,
    val recipients: List<String>? = null,
    val isActive: Boolean? = null,
)
