package com.liyaqa.gx.dto

import java.time.Instant
import java.util.UUID

data class GXClassInstanceSummaryResponse(
    val id: UUID,
    val classTypeNameAr: String,
    val classTypeNameEn: String,
    val classTypeColor: String?,
    val instructorNameEn: String,
    val scheduledAt: Instant,
    val durationMinutes: Int,
    val capacity: Int,
    val bookingsCount: Int,
    val availableSpots: Int,
    val room: String?,
    val status: String,
)
