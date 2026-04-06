package com.liyaqa.cashdrawer

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "cash_drawer_sessions")
class CashDrawerSession(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: Long,
    @Column(name = "opened_by_staff_id", nullable = false, updatable = false)
    val openedByStaffId: Long,
    @Column(name = "closed_by_staff_id")
    var closedByStaffId: Long? = null,
    @Column(name = "reconciled_by_staff_id")
    var reconciledByStaffId: Long? = null,
    // status: open | closed | reconciled
    @Column(name = "status", nullable = false, length = 20)
    var status: String = "open",
    @Column(name = "opening_float_halalas", nullable = false)
    val openingFloatHalalas: Long = 0,
    @Column(name = "counted_closing_halalas")
    var countedClosingHalalas: Long? = null,
    @Column(name = "expected_closing_halalas")
    var expectedClosingHalalas: Long? = null,
    @Column(name = "difference_halalas")
    var differenceHalalas: Long? = null,
    // reconciliation_status: approved | flagged
    @Column(name = "reconciliation_status", length = 20)
    var reconciliationStatus: String? = null,
    @Column(name = "reconciliation_notes", columnDefinition = "TEXT")
    var reconciliationNotes: String? = null,
    @Column(name = "opened_at", nullable = false, updatable = false)
    val openedAt: Instant = Instant.now(),
    @Column(name = "closed_at")
    var closedAt: Instant? = null,
    @Column(name = "reconciled_at")
    var reconciledAt: Instant? = null,
) : AuditEntity()
