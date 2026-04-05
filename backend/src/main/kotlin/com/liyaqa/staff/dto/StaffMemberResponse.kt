package com.liyaqa.staff.dto

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class StaffRoleSummary(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
)

data class StaffMemberResponse(
    val id: UUID,
    val userId: UUID,
    val organizationId: UUID,
    val clubId: UUID,
    val firstNameAr: String,
    val firstNameEn: String,
    val lastNameAr: String,
    val lastNameEn: String,
    val email: String,
    val phone: String?,
    val nationalId: String?,
    val role: StaffRoleSummary,
    val branches: List<StaffBranchResponse>,
    val employmentType: String,
    val joinedAt: LocalDate,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
