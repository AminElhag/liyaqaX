package com.liyaqa.coach.dto

import java.time.Instant
import java.util.UUID

data class ScheduleItemResponse(
    val type: String,
    val id: UUID,
    val startTime: Instant,
    val endTime: Instant?,
    val title: String,
    val memberOrClassName: String,
    val status: String,
    val bookedCount: Int? = null,
    val capacity: Int? = null,
)
