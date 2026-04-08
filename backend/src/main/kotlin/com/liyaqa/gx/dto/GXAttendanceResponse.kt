package com.liyaqa.gx.dto

import java.time.Instant
import java.util.UUID

data class GXAttendanceResponse(
    val id: UUID,
    val instanceId: UUID,
    val member: GXMemberSummary,
    val status: String,
    val markedAt: Instant,
)
