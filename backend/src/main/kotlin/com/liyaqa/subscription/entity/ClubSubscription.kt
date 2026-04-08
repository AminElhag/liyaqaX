package com.liyaqa.subscription.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "club_subscriptions")
class ClubSubscription(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "club_id", nullable = false)
    val clubId: Long,
    @Column(name = "plan_id", nullable = false)
    var planId: Long,
    @Column(name = "status", nullable = false, length = 20)
    var status: String,
    @Column(name = "current_period_start", nullable = false)
    var currentPeriodStart: Instant,
    @Column(name = "current_period_end", nullable = false)
    var currentPeriodEnd: Instant,
    @Column(name = "grace_period_ends_at", nullable = false)
    var gracePeriodEndsAt: Instant,
    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,
    @Column(name = "assigned_by_user_id", nullable = false, updatable = false)
    val assignedByUserId: Long,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
