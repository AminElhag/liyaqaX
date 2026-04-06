package com.liyaqa.arena.dto

import java.time.Instant
import java.util.UUID

data class PtSessionArenaResponse(
    val id: UUID,
    val scheduledAt: Instant,
    val status: String,
    val trainerName: String,
    val packageName: String,
    val sessionsUsed: Int,
    val sessionsTotal: Int,
)
