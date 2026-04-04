package com.liyaqa.membership.dto

import java.time.Instant
import java.util.UUID

data class MembershipPlanResponse(
    val id: UUID,
    val organizationId: UUID,
    val clubId: UUID,
    val nameAr: String,
    val nameEn: String,
    val descriptionAr: String?,
    val descriptionEn: String?,
    val priceHalalas: Long,
    val priceSar: String,
    val durationDays: Int,
    val gracePeriodDays: Int,
    val freezeAllowed: Boolean,
    val maxFreezeDays: Int,
    val gxClassesIncluded: Boolean,
    val ptSessionsIncluded: Boolean,
    val isActive: Boolean,
    val sortOrder: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
