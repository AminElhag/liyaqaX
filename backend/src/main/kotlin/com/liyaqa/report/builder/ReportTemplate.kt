package com.liyaqa.report.builder

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "report_templates")
class ReportTemplate(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "name", nullable = false, length = 200)
    var name: String,
    @Column(name = "description", length = 500)
    var description: String? = null,
    @Column(name = "metrics", nullable = false, columnDefinition = "TEXT")
    var metrics: String,
    @Column(name = "dimensions", nullable = false, columnDefinition = "TEXT")
    var dimensions: String,
    @Column(name = "filters", columnDefinition = "TEXT")
    var filters: String? = null,
    @Column(name = "metric_scope", length = 50)
    var metricScope: String? = null,
    @Column(name = "is_system", nullable = false)
    val isSystem: Boolean = false,
) : AuditEntity()
