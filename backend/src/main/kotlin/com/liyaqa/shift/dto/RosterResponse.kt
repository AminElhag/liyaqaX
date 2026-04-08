package com.liyaqa.shift.dto

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class RosterResponse(
    val branchName: String,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val shifts: List<RosterShiftItem>,
)

data class RosterShiftItem(
    val shiftId: UUID,
    val staffMemberId: UUID,
    val staffMemberName: String,
    val startAt: Instant,
    val endAt: Instant,
    val notes: String?,
    val hasPendingSwap: Boolean,
)
