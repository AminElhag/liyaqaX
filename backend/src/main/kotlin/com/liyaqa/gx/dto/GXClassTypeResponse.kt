package com.liyaqa.gx.dto

import java.time.Instant
import java.util.UUID

data class GXClassTypeResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val descriptionAr: String?,
    val descriptionEn: String?,
    val defaultDurationMinutes: Int,
    val defaultCapacity: Int,
    val color: String?,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
