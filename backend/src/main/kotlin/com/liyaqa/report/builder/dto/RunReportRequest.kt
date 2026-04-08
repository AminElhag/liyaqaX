package com.liyaqa.report.builder.dto

import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class RunReportRequest(
    @field:NotNull
    val dateFrom: LocalDate,
    @field:NotNull
    val dateTo: LocalDate,
    val filters: Map<String, String?>? = null,
)
