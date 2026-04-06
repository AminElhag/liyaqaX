package com.liyaqa.coach.dto

import jakarta.validation.constraints.Pattern

data class MarkPtAttendanceRequest(
    @field:Pattern(regexp = "attended|missed", message = "Status must be 'attended' or 'missed'")
    val status: String,
)
