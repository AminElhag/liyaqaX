package com.liyaqa.member.dto

import java.time.Instant
import java.util.UUID

data class WaiverStatusResponse(
    val hasSignedCurrentWaiver: Boolean,
    val waiverId: UUID?,
    val waiverVersion: Int?,
    val signedAt: Instant?,
)
