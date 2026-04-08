package com.liyaqa.audit.dto

import java.time.Instant
import java.util.UUID

data class AuditLogResponse(
    val id: UUID,
    val actorId: String,
    val actorScope: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val organizationId: String?,
    val clubId: String?,
    val changesJson: String?,
    val ipAddress: String?,
    val createdAt: Instant,
)
