package com.liyaqa.cashdrawer

import com.liyaqa.common.audit.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "cash_drawer_entries")
class CashDrawerEntry(
    @Column(name = "session_id", nullable = false, updatable = false)
    val sessionId: Long,
    @Column(name = "staff_id", nullable = false, updatable = false)
    val staffId: Long,
    @Column(name = "payment_id", updatable = false)
    val paymentId: Long? = null,
    // entry_type: cash_in | cash_out | float_adjustment
    @Column(name = "entry_type", nullable = false, length = 20, updatable = false)
    val entryType: String,
    @Column(name = "amount_halalas", nullable = false, updatable = false)
    val amountHalalas: Long,
    @Column(name = "description", nullable = false, length = 255, updatable = false)
    val description: String,
    @Column(name = "recorded_at", nullable = false, updatable = false)
    val recordedAt: Instant = Instant.now(),
) : BaseEntity()
