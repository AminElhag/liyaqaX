package com.liyaqa.membership.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size

data class UpdateMembershipPlanRequest(
    @field:Size(max = 255)
    val nameAr: String? = null,
    @field:Size(max = 255)
    val nameEn: String? = null,
    val descriptionAr: String? = null,
    val descriptionEn: String? = null,
    @field:Min(1)
    val priceHalalas: Long? = null,
    @field:Min(1)
    val durationDays: Int? = null,
    @field:Min(0)
    val gracePeriodDays: Int? = null,
    val freezeAllowed: Boolean? = null,
    @field:Min(0)
    val maxFreezeDays: Int? = null,
    val gxClassesIncluded: Boolean? = null,
    val ptSessionsIncluded: Boolean? = null,
    val isActive: Boolean? = null,
    @field:Min(0)
    val sortOrder: Int? = null,
)
