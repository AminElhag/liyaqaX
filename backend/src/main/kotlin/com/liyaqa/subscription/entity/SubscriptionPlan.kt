package com.liyaqa.subscription.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "subscription_plans")
class SubscriptionPlan(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "name", nullable = false, length = 100)
    var name: String,
    @Column(name = "monthly_price_halalas", nullable = false)
    var monthlyPriceHalalas: Long,
    @Column(name = "max_branches", nullable = false)
    var maxBranches: Int,
    @Column(name = "max_staff", nullable = false)
    var maxStaff: Int,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features", columnDefinition = "jsonb")
    var features: String? = null,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)
