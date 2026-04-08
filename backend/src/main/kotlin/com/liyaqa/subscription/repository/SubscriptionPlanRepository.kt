package com.liyaqa.subscription.repository

import com.liyaqa.subscription.entity.SubscriptionPlan
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface SubscriptionPlanRepository : JpaRepository<SubscriptionPlan, Long> {

    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<SubscriptionPlan>

    @Query(
        value = """
            SELECT * FROM subscription_plans
            WHERE deleted_at IS NULL
              AND is_active = TRUE
            ORDER BY monthly_price_halalas
        """,
        nativeQuery = true,
    )
    fun findAllActivePlans(): List<SubscriptionPlan>

    @Query(
        value = """
            SELECT COUNT(*) FROM club_subscriptions
            WHERE plan_id = :planId
              AND status NOT IN ('CANCELLED', 'EXPIRED')
        """,
        nativeQuery = true,
    )
    fun countActiveSubscriptionsForPlan(planId: Long): Long
}
