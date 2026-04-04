package com.liyaqa.gx.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.util.UUID

data class BulkAttendanceRequest(
    @field:NotEmpty
    @field:Valid
    val attendance: List<AttendanceEntry>,
)

data class AttendanceEntry(
    @field:NotNull
    val memberId: UUID,
    @field:NotNull
    @field:Pattern(regexp = "^(present|absent|late)$")
    val status: String,
)
