package com.liyaqa.shift.dto

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PendingSwapsResponse(
    val swapRequests: List<PendingSwapItem>,
)

data class PendingSwapItem(
    val swapId: UUID,
    val shiftDate: LocalDate,
    val shiftStart: Instant,
    val shiftEnd: Instant,
    val requesterName: String,
    val targetName: String,
    val status: String,
    val requesterNote: String?,
)
