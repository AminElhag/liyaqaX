package com.liyaqa.lead.dto

import java.time.Instant
import java.util.UUID

data class LeadResponse(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val firstNameAr: String?,
    val lastNameAr: String?,
    val phone: String?,
    val email: String?,
    val gender: String?,
    val stage: String,
    val lostReason: String?,
    val leadSource: LeadSourceSummary?,
    val assignedStaff: LeadStaffSummary?,
    val branch: LeadBranchSummary?,
    val convertedMemberId: UUID?,
    val contactedAt: Instant?,
    val interestedAt: Instant?,
    val convertedAt: Instant?,
    val lostAt: Instant?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class LeadSourceSummary(
    val id: UUID,
    val name: String,
    val nameAr: String,
    val color: String,
)

data class LeadStaffSummary(
    val id: UUID,
    val firstName: String,
    val lastName: String,
)

data class LeadBranchSummary(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
)
