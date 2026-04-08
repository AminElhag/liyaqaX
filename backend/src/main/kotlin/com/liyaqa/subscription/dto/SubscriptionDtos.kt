package com.liyaqa.subscription.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

// ── Plan DTOs ────────────────────────────────────────────────────────────────

data class CreatePlanRequest(
    @field:NotBlank @field:Size(max = 100)
    val name: String,
    @field:Min(0)
    val monthlyPriceHalalas: Long,
    @field:Min(0)
    val maxBranches: Int,
    @field:Min(0)
    val maxStaff: Int,
    val features: String? = null,
)

data class UpdatePlanRequest(
    @field:Size(max = 100)
    val name: String? = null,
    val monthlyPriceHalalas: Long? = null,
    val maxBranches: Int? = null,
    val maxStaff: Int? = null,
    val features: String? = null,
)

data class SubscriptionPlanResponse(
    val id: UUID,
    val name: String,
    val monthlyPriceHalalas: Long,
    val monthlyPriceSar: String,
    val maxBranches: Int,
    val maxStaff: Int,
    val features: String?,
    val isActive: Boolean,
)

// ── Subscription DTOs ────────────────────────────────────────────────────────

data class AssignSubscriptionRequest(
    @field:NotNull
    val planPublicId: UUID,
    @field:NotBlank
    val periodStartDate: String,
    @field:Min(1)
    val periodMonths: Int,
)

data class ExtendSubscriptionRequest(
    @field:Min(1)
    val additionalMonths: Int,
)

data class ClubSubscriptionResponse(
    val id: UUID,
    val clubId: UUID,
    val planName: String,
    val monthlyPriceHalalas: Long,
    val monthlyPriceSar: String,
    val status: String,
    val currentPeriodStart: Instant,
    val currentPeriodEnd: Instant,
    val gracePeriodEndsAt: Instant,
    val cancelledAt: Instant?,
    val createdAt: Instant,
)

data class SubscriptionDashboardItem(
    val clubId: UUID,
    val clubName: String,
    val planName: String,
    val status: String,
    val currentPeriodEnd: Instant,
    val gracePeriodEndsAt: Instant,
    val daysUntilExpiry: Long,
    val monthlyPriceSar: String,
)

data class SubscriptionDashboardResponse(
    val subscriptions: List<SubscriptionDashboardItem>,
    val totalCount: Long,
    val page: Int,
    val pageSize: Int,
)

data class ExpiringSubscriptionItem(
    val clubId: UUID,
    val clubName: String,
    val planName: String,
    val status: String,
    val currentPeriodEnd: Instant,
    val daysUntilExpiry: Long,
)

data class ExpiringSubscriptionsResponse(
    val expiringSoon: List<ExpiringSubscriptionItem>,
)
