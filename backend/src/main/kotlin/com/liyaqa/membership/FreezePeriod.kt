package com.liyaqa.membership

import com.liyaqa.common.audit.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "freeze_periods")
class FreezePeriod(
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "membership_id", nullable = false, updatable = false)
    val membershipId: Long,
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "freeze_start_date", nullable = false)
    val freezeStartDate: LocalDate,
    @Column(name = "freeze_end_date", nullable = false)
    val freezeEndDate: LocalDate,
    @Column(name = "actual_end_date")
    var actualEndDate: LocalDate? = null,
    @Column(name = "duration_days", nullable = false)
    val durationDays: Int,
    @Column(name = "reason", columnDefinition = "TEXT")
    val reason: String? = null,
    @Column(name = "requested_by_id", nullable = false, updatable = false)
    val requestedById: Long,
) : BaseEntity()
