package com.liyaqa.gx.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateGXClassInstanceRequest(
    @field:NotNull
    val classTypeId: UUID,
    @field:NotNull
    val instructorId: UUID,
    @field:NotNull
    val scheduledAt: Instant,
    @field:Min(1)
    @field:Max(480)
    val durationMinutes: Int? = null,
    @field:Min(1)
    @field:Max(500)
    val capacity: Int? = null,
    @field:Size(max = 100)
    val room: String? = null,
    val notes: String? = null,
)
