package com.liyaqa.report.schedule

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "report_schedules")
class ReportSchedule(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "template_id", nullable = false, unique = true)
    val templateId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "frequency", nullable = false, length = 20)
    var frequency: String,
    @Column(name = "recipients_json", nullable = false, length = 2000)
    var recipientsJson: String,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    @Column(name = "last_run_at")
    var lastRunAt: Instant? = null,
    @Column(name = "last_run_status", length = 20)
    var lastRunStatus: String? = null,
    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,
) : AuditEntity()
