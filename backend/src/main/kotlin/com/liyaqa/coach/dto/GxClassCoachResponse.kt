package com.liyaqa.coach.dto

import java.time.Instant
import java.util.UUID

data class GxClassCoachResponse(
    val id: UUID,
    val classType: GxClassTypeSummary,
    val startTime: Instant,
    val endTime: Instant,
    val capacity: Int,
    val bookedCount: Int,
    val attendedCount: Int,
)

data class GxClassTypeSummary(
    val name: String,
    val nameAr: String,
    val color: String?,
)
