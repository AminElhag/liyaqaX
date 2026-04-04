package com.liyaqa.member.dto

import java.time.LocalDate
import java.util.UUID

data class MemberSummaryResponse(
    val id: UUID,
    val firstNameAr: String,
    val firstNameEn: String,
    val lastNameAr: String,
    val lastNameEn: String,
    val email: String,
    val phone: String,
    val membershipStatus: String,
    val branch: MemberBranchResponse,
    val joinedAt: LocalDate,
)
