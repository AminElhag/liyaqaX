package com.liyaqa.payment

import com.liyaqa.common.audit.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "payments")
class Payment(
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: Long,
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "membership_id", updatable = false)
    val membershipId: Long? = null,
    @Column(name = "amount_halalas", nullable = false, updatable = false)
    val amountHalalas: Long,
    // payment_method: cash | card | bank-transfer | other
    @Column(name = "payment_method", nullable = false, length = 50, updatable = false)
    val paymentMethod: String,
    @Column(name = "reference_number", updatable = false)
    val referenceNumber: String? = null,
    @Column(name = "collected_by_id", nullable = false, updatable = false)
    val collectedById: Long,
    @Column(name = "paid_at", nullable = false, updatable = false)
    val paidAt: Instant = Instant.now(),
    @Column(name = "notes", columnDefinition = "TEXT", updatable = false)
    val notes: String? = null,
) : BaseEntity()
