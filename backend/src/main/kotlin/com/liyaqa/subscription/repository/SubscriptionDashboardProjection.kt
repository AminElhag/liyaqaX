package com.liyaqa.subscription.repository

import java.time.Instant
import java.util.UUID

interface SubscriptionDashboardProjection {
    fun getPublicId(): UUID
    fun getClubId(): Long
    fun getClubName(): String
    fun getPlanName(): String
    fun getMonthlyPriceHalalas(): Long
    fun getStatus(): String
    fun getCurrentPeriodEnd(): Instant
    fun getGracePeriodEndsAt(): Instant
    fun getCancelledAt(): Instant?
}
