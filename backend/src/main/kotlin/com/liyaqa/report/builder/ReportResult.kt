package com.liyaqa.report.builder

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "report_results")
class ReportResult(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "template_id", nullable = false)
    val templateId: Long,
    @Column(name = "run_at", nullable = false)
    val runAt: Instant = Instant.now(),
    @Column(name = "run_by_user_id", nullable = false, length = 100)
    val runByUserId: String,
    @Column(name = "date_from", nullable = false)
    val dateFrom: LocalDate,
    @Column(name = "date_to", nullable = false)
    val dateTo: LocalDate,
    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    val resultJson: String,
    @Column(name = "row_count", nullable = false)
    val rowCount: Int,
    @Column(name = "truncated", nullable = false)
    val truncated: Boolean = false,
    @Column(name = "run_params_hash", length = 64)
    val runParamsHash: String? = null,
) : AuditEntity()
