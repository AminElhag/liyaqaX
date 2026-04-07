package com.liyaqa.member

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "member_registration_intents")
class MemberRegistrationIntent(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "member_public_id", nullable = false, updatable = false)
    val memberPublicId: UUID,
    @Column(name = "membership_plan_id")
    val membershipPlanId: Long? = null,
    @Column(name = "membership_plan_public_id")
    val membershipPlanPublicId: UUID? = null,
    @Column(name = "membership_plan_name_en", length = 200)
    val membershipPlanNameEn: String? = null,
    @Column(name = "membership_plan_name_ar", length = 200)
    val membershipPlanNameAr: String? = null,
    @Column(name = "membership_plan_price_halalas")
    val membershipPlanPriceHalalas: Long? = null,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,
    @Column(name = "resolved_by")
    var resolvedBy: Long? = null,
) : AuditEntity()
