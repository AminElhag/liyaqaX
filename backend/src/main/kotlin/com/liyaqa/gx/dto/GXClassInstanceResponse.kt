package com.liyaqa.gx.dto

import java.time.Instant
import java.util.UUID

data class GXClassInstanceResponse(
    val id: UUID,
    val classType: GXClassTypeSummary,
    val instructor: GXInstructorSummary,
    val scheduledAt: Instant,
    val durationMinutes: Int,
    val capacity: Int,
    val bookingsCount: Int,
    val waitlistCount: Int,
    val availableSpots: Int,
    val room: String?,
    val status: String,
    val notes: String?,
    val createdAt: Instant,
)

data class GXClassTypeSummary(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val color: String?,
)

data class GXInstructorSummary(
    val id: UUID,
    val firstNameAr: String,
    val firstNameEn: String,
    val lastNameAr: String,
    val lastNameEn: String,
)
