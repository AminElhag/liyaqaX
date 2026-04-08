package com.liyaqa.coach.dto

import java.time.Instant
import java.util.UUID

data class PtSessionCoachResponse(
    val id: UUID,
    val scheduledAt: Instant,
    val status: String,
    val memberName: String,
    val packageName: String,
    val notes: String?,
)
