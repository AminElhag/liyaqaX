package com.liyaqa.invoice

import com.liyaqa.common.audit.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "invoice_counters")
class InvoiceCounter(
    @Column(name = "club_id", nullable = false, unique = true)
    val clubId: Long,
    @Column(name = "last_value", nullable = false)
    var lastValue: Long = 0,
) : BaseEntity()
