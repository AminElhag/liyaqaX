package com.liyaqa.member.dto

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class MemberBranchResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
)

data class MemberResponse(
    val id: UUID,
    val userId: UUID,
    val organizationId: UUID,
    val clubId: UUID,
    val branch: MemberBranchResponse,
    val firstNameAr: String,
    val firstNameEn: String,
    val lastNameAr: String,
    val lastNameEn: String,
    val email: String,
    val phone: String,
    val nationalId: String?,
    val dateOfBirth: LocalDate?,
    val gender: String?,
    val membershipStatus: String,
    val notes: String?,
    val joinedAt: LocalDate,
    val emergencyContacts: List<EmergencyContactResponse>,
    val hasSignedWaiver: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
