package com.liyaqa.membership.dto

import java.time.LocalDate
import java.util.UUID

data class ExpiringMembershipResponse(
    val memberId: UUID,
    val memberName: String,
    val memberPhone: String,
    val planNameAr: String,
    val planNameEn: String,
    val endDate: LocalDate,
    val daysRemaining: Int,
    val membershipId: UUID,
    val membershipStatus: String,
)
