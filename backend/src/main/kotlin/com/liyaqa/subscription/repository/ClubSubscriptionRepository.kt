package com.liyaqa.subscription.repository

import com.liyaqa.subscription.entity.ClubSubscription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@Repository
interface ClubSubscriptionRepository : JpaRepository<ClubSubscription, Long> {

    fun findByPublicIdAndClubId(publicId: UUID, clubId: Long): Optional<ClubSubscription>

    @Query(
        value = """
            SELECT * FROM club_subscriptions
            WHERE club_id = :clubId
              AND status NOT IN ('CANCELLED', 'EXPIRED')
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findActiveByClubId(clubId: Long): ClubSubscription?

    @Query(
        value = """
            SELECT * FROM club_subscriptions
            WHERE status = 'ACTIVE'
              AND current_period_end <= :now
        """,
        nativeQuery = true,
    )
    fun findActiveExpired(now: Instant): List<ClubSubscription>

    @Query(
        value = """
            SELECT * FROM club_subscriptions
            WHERE status = 'GRACE'
              AND grace_period_ends_at <= :now
        """,
        nativeQuery = true,
    )
    fun findGraceExpired(now: Instant): List<ClubSubscription>

    @Query(
        value = """
            SELECT * FROM club_subscriptions
            WHERE status = 'ACTIVE'
              AND DATE(current_period_end AT TIME ZONE 'Asia/Riyadh') = :targetDate
        """,
        nativeQuery = true,
    )
    fun findExpiringOnDate(targetDate: LocalDate): List<ClubSubscription>

    @Query(
        value = """
            SELECT cs.public_id, cs.club_id, c.name_en AS club_name, sp.name AS plan_name,
                   sp.monthly_price_halalas, cs.status, cs.current_period_end,
                   cs.grace_period_ends_at, cs.cancelled_at
            FROM club_subscriptions cs
            JOIN clubs c ON c.id = cs.club_id
            JOIN subscription_plans sp ON sp.id = cs.plan_id
            WHERE cs.status != 'CANCELLED'
            ORDER BY cs.current_period_end
            LIMIT :pageSize OFFSET :offset
        """,
        nativeQuery = true,
    )
    fun findAllForDashboard(pageSize: Int, offset: Int): List<SubscriptionDashboardProjection>

    @Query(
        value = """
            SELECT COUNT(*) FROM club_subscriptions
            WHERE status != 'CANCELLED'
        """,
        nativeQuery = true,
    )
    fun countForDashboard(): Long

    @Query(
        value = """
            SELECT cs.public_id, cs.club_id, c.name_en AS club_name, sp.name AS plan_name,
                   sp.monthly_price_halalas, cs.status, cs.current_period_end,
                   cs.grace_period_ends_at, cs.cancelled_at
            FROM club_subscriptions cs
            JOIN clubs c ON c.id = cs.club_id
            JOIN subscription_plans sp ON sp.id = cs.plan_id
            WHERE cs.status = 'ACTIVE'
              AND cs.current_period_end <= :cutoff
            ORDER BY cs.current_period_end
        """,
        nativeQuery = true,
    )
    fun findExpiringSoon(cutoff: Instant): List<SubscriptionDashboardProjection>

    fun findAllByClubIdOrderByCreatedAtDesc(clubId: Long): List<ClubSubscription>
}
