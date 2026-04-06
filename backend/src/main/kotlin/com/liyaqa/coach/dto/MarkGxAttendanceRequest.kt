package com.liyaqa.coach.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import java.util.UUID

data class MarkGxAttendanceRequest(
    @field:NotEmpty(message = "Attendances list must not be empty")
    @field:Valid
    val attendances: List<GxBookingAttendanceItem>,
)

data class GxBookingAttendanceItem(
    val bookingId: UUID,
    val attended: Boolean,
)
