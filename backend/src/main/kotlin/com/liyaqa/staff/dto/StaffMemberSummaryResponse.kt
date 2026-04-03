package com.liyaqa.staff.dto

import java.util.UUID

data class StaffMemberSummaryResponse(
    val id: UUID,
    val firstNameAr: String,
    val firstNameEn: String,
    val lastNameAr: String,
    val lastNameEn: String,
    val email: String,
    val role: StaffRoleSummary,
    val isActive: Boolean,
)
