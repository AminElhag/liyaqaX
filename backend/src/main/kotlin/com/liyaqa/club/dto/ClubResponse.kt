package com.liyaqa.club.dto

import java.time.Instant
import java.util.UUID

data class ClubResponse(
    val id: UUID,
    val organizationId: UUID,
    val nameAr: String,
    val nameEn: String,
    val email: String?,
    val phone: String?,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
