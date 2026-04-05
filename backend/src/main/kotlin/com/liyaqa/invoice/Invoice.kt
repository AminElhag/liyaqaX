package com.liyaqa.invoice

import com.liyaqa.common.audit.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "invoices")
class Invoice(
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: Long,
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "payment_id", nullable = false, unique = true, updatable = false)
    val paymentId: Long,
    @Column(name = "invoice_number", nullable = false, unique = true, updatable = false, length = 100)
    val invoiceNumber: String,
    @Column(name = "subtotal_halalas", nullable = false, updatable = false)
    val subtotalHalalas: Long,
    @Column(name = "vat_rate", nullable = false, updatable = false, precision = 5, scale = 4)
    val vatRate: BigDecimal = BigDecimal("0.1500"),
    @Column(name = "vat_amount_halalas", nullable = false, updatable = false)
    val vatAmountHalalas: Long,
    @Column(name = "total_halalas", nullable = false, updatable = false)
    val totalHalalas: Long,
    @Column(name = "issued_at", nullable = false, updatable = false)
    val issuedAt: Instant = Instant.now(),
    // zatca_status: pending | submitted | accepted | rejected
    @Column(name = "zatca_status", nullable = false, length = 50)
    var zatcaStatus: String = "pending",
    @Column(name = "zatca_uuid")
    var zatcaUuid: String? = null,
    @Column(name = "zatca_hash")
    var zatcaHash: String? = null,
    @Column(name = "zatca_qr_code", columnDefinition = "TEXT")
    var zatcaQrCode: String? = null,
    @Column(name = "previous_invoice_hash", columnDefinition = "TEXT")
    var previousInvoiceHash: String? = null,
    @Column(name = "invoice_counter_value")
    var invoiceCounterValue: Long? = null,
) : BaseEntity()
