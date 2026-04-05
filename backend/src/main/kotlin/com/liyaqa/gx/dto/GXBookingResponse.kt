package com.liyaqa.gx.dto

import java.time.Instant
import java.util.UUID

data class GXBookingResponse(
    val id: UUID,
    val instanceId: UUID,
    val member: GXMemberSummary,
    val status: String,
    val waitlistPosition: Int?,
    val bookedAt: Instant,
    val cancelledAt: Instant?,
)

data class GXMemberSummary(
    val id: UUID,
    val firstNameAr: String,
    val firstNameEn: String,
    val lastNameAr: String,
    val lastNameEn: String,
)
