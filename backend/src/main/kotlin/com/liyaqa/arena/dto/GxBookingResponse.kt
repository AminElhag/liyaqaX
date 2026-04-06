package com.liyaqa.arena.dto

import java.time.Instant
import java.util.UUID

data class GxBookingResponse(
    val id: UUID,
    val instanceId: UUID,
    val className: String,
    val classNameAr: String,
    val instructorName: String,
    val startTime: Instant,
    val status: String,
    val bookedAt: Instant,
    val cancelledAt: Instant?,
)
