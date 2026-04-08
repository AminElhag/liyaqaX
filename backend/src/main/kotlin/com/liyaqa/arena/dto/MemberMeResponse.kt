package com.liyaqa.arena.dto

import java.util.UUID

data class MemberMeResponse(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val firstNameAr: String?,
    val lastNameAr: String?,
    val phone: String,
    val email: String?,
    val preferredLanguage: String?,
    val memberStatus: String,
    val club: ClubSummary,
    val membership: MembershipSummary?,
)

data class ClubSummary(
    val id: UUID,
    val name: String,
    val nameAr: String,
)

data class MembershipSummary(
    val planName: String,
    val planNameAr: String,
    val status: String,
    val startDate: String,
    val expiresAt: String,
    val daysRemaining: Long,
)
