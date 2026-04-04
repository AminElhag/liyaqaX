package com.liyaqa.membership.dto

import java.util.UUID

data class MembershipPlanSummaryResponse(
    val id: UUID,
    val nameAr: String,
    val nameEn: String,
    val priceHalalas: Long,
    val priceSar: String,
    val durationDays: Int,
    val isActive: Boolean,
)
