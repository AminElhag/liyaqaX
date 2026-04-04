package com.liyaqa.membership

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "membership_plans")
class MembershipPlan(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "name_ar", nullable = false)
    var nameAr: String,
    @Column(name = "name_en", nullable = false)
    var nameEn: String,
    @Column(name = "description_ar")
    var descriptionAr: String? = null,
    @Column(name = "description_en")
    var descriptionEn: String? = null,
    @Column(name = "price_halalas", nullable = false)
    var priceHalalas: Long,
    @Column(name = "duration_days", nullable = false)
    var durationDays: Int,
    @Column(name = "grace_period_days", nullable = false)
    var gracePeriodDays: Int = 0,
    @Column(name = "freeze_allowed", nullable = false)
    var freezeAllowed: Boolean = true,
    @Column(name = "max_freeze_days", nullable = false)
    var maxFreezeDays: Int = 30,
    @Column(name = "gx_classes_included", nullable = false)
    var gxClassesIncluded: Boolean = true,
    @Column(name = "pt_sessions_included", nullable = false)
    var ptSessionsIncluded: Boolean = false,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,
) : AuditEntity()
