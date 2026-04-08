package com.liyaqa.shift.dto

import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateShiftRequest(
    @field:NotNull
    val staffMemberPublicId: UUID,
    @field:NotNull
    val branchPublicId: UUID,
    @field:NotNull
    val startAt: Instant,
    @field:NotNull
    val endAt: Instant,
    val notes: String? = null,
)
