package com.liyaqa.arena.dto

import java.time.Instant
import java.util.UUID

data class GxScheduleItemResponse(
    val id: UUID,
    val classType: GxClassTypeSummary,
    val instructorName: String,
    val startTime: Instant,
    val endTime: Instant,
    val capacity: Int,
    val bookedCount: Int,
    val spotsRemaining: Int,
    val isBooked: Boolean,
)

data class GxClassTypeSummary(
    val name: String,
    val nameAr: String,
    val color: String?,
)
