package com.liyaqa.lead.dto

import java.time.Instant
import java.util.UUID

data class LeadSourceResponse(
    val id: UUID,
    val name: String,
    val nameAr: String,
    val color: String,
    val isActive: Boolean,
    val displayOrder: Int,
    val leadCount: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)
