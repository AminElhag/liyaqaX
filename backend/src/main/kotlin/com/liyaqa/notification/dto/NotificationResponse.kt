package com.liyaqa.notification.dto

import java.time.Instant
import java.util.UUID

data class NotificationResponse(
    val id: UUID,
    val type: String,
    val titleKey: String,
    val bodyKey: String,
    val params: Map<String, Any>?,
    val entityType: String?,
    val entityId: String?,
    val readAt: Instant?,
    val createdAt: Instant,
)

data class UnreadCountResponse(
    val count: Long,
)
