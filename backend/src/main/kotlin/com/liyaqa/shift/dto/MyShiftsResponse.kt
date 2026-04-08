package com.liyaqa.shift.dto

import java.time.Instant
import java.util.UUID

data class MyShiftsResponse(
    val shifts: List<MyShiftItem>,
)

data class MyShiftItem(
    val shiftId: UUID,
    val branchName: String,
    val startAt: Instant,
    val endAt: Instant,
    val notes: String?,
    val swapRequest: SwapSummary?,
)

data class SwapSummary(
    val swapId: UUID,
    val targetStaffName: String,
    val status: String,
)
