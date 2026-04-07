package com.liyaqa.member.dto

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PendingMemberResponse(
    val id: UUID,
    val nameEn: String,
    val nameAr: String,
    val phone: String,
    val email: String?,
    val dateOfBirth: LocalDate?,
    val gender: String?,
    val registeredAt: Instant,
    val intent: PendingMemberIntentResponse?,
)

data class PendingMemberIntentResponse(
    val planId: UUID?,
    val planNameEn: String?,
    val planNameAr: String?,
    val planPriceSar: String?,
)
