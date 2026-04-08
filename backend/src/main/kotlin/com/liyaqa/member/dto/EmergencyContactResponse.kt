package com.liyaqa.member.dto

import java.time.Instant
import java.util.UUID

data class EmergencyContactResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val phone: String,
    val relationship: String?,
    val createdAt: Instant,
)
