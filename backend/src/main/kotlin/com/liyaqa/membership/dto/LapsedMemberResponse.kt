package com.liyaqa.membership.dto

import java.time.LocalDate
import java.util.UUID

data class LapsedMemberResponse(
    val memberPublicId: UUID,
    val nameAr: String,
    val nameEn: String,
    val phone: String,
    val lastMembershipPlan: String,
    val expiredOn: LocalDate,
    val daysSinceLapse: Long,
    val hasOpenFollowUp: Boolean,
)

data class LapsedMembersPageResponse(
    val total: Long,
    val page: Int,
    val pageSize: Int,
    val members: List<LapsedMemberResponse>,
)
