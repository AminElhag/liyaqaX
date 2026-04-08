package com.liyaqa.membership

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "memberships")
class Membership(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: Long,
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "plan_id", nullable = false, updatable = false)
    val planId: Long,
    // membership_status: pending | active | frozen | expired | terminated | lapsed
    @Column(name = "membership_status", nullable = false, length = 50)
    var membershipStatus: String = "pending",
    @Column(name = "start_date", nullable = false)
    val startDate: LocalDate,
    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate,
    @Column(name = "grace_end_date")
    var graceEndDate: LocalDate? = null,
    @Column(name = "freeze_days_used", nullable = false)
    var freezeDaysUsed: Int = 0,
    @Column(name = "notes", columnDefinition = "TEXT")
    var notes: String? = null,
) : AuditEntity()
