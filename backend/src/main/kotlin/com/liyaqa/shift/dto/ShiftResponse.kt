package com.liyaqa.shift.dto

import java.time.Instant
import java.util.UUID

data class ShiftResponse(
    val shiftId: UUID,
    val staffMemberName: String,
    val branchName: String,
    val startAt: Instant,
    val endAt: Instant,
    val notes: String?,
)
