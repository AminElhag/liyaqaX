package com.liyaqa.membership.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateMembershipPlanRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val nameAr: String,
    @field:NotBlank
    @field:Size(max = 255)
    val nameEn: String,
    val descriptionAr: String? = null,
    val descriptionEn: String? = null,
    @field:Min(1)
    val priceHalalas: Long,
    @field:Min(1)
    val durationDays: Int,
    @field:Min(0)
    val gracePeriodDays: Int = 0,
    val freezeAllowed: Boolean = true,
    @field:Min(0)
    val maxFreezeDays: Int = 30,
    val gxClassesIncluded: Boolean = true,
    val ptSessionsIncluded: Boolean = false,
    @field:Min(0)
    val sortOrder: Int = 0,
)
