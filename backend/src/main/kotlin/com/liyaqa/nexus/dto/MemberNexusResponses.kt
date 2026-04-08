package com.liyaqa.nexus.dto

import java.time.Instant
import java.util.UUID

data class MemberSearchItemResponse(
    val id: UUID,
    val firstNameAr: String,
    val firstNameEn: String,
    val lastNameAr: String,
    val lastNameEn: String,
    val phone: String,
    val email: String?,
    val clubName: String?,
    val organizationName: String?,
    val membershipStatus: String,
)

data class MemberDetailNexusResponse(
    val id: UUID,
    val firstNameAr: String,
    val firstNameEn: String,
    val lastNameAr: String,
    val lastNameEn: String,
    val phone: String,
    val email: String?,
    val membershipStatus: String,
    val clubName: String?,
    val organizationName: String?,
    val createdAt: Instant,
)
