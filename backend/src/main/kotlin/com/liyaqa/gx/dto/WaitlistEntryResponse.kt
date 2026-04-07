package com.liyaqa.gx.dto

import java.time.Instant
import java.util.UUID

data class WaitlistEntryResponse(
    val entryId: UUID,
    val position: Int,
    val status: String,
    val memberName: String,
    val memberPhone: String,
    val notifiedAt: Instant?,
    val offerExpiresAt: Instant?,
    val createdAt: Instant,
)
