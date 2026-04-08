package com.liyaqa.member.dto

import java.time.Instant
import java.util.UUID

data class FollowUpResponse(
    val followUps: List<FollowUpItem>,
)

data class FollowUpItem(
    val noteId: UUID,
    val followUpAt: Instant,
    val content: String,
    val memberName: String,
    val memberPublicId: UUID,
    val createdByName: String,
    val daysUntilDue: Long,
)
