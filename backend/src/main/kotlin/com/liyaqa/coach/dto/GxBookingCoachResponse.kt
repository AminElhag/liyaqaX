package com.liyaqa.coach.dto

import java.time.Instant
import java.util.UUID

data class GxBookingCoachResponse(
    val id: UUID,
    val memberName: String,
    val bookedAt: Instant,
    val attended: Boolean?,
)
