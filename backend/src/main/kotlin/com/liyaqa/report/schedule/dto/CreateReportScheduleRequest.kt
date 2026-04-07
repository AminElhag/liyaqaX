package com.liyaqa.report.schedule.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class CreateReportScheduleRequest(
    @field:NotBlank
    val frequency: String,
    @field:NotEmpty
    val recipients: List<String>,
    val isActive: Boolean = true,
)
