package com.liyaqa.lead

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "lead_sources")
class LeadSource(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "name", nullable = false, length = 100)
    var name: String,
    @Column(name = "name_ar", nullable = false, length = 100)
    var nameAr: String,
    @Column(name = "color", nullable = false, length = 7)
    var color: String = "#6B7280",
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
) : AuditEntity()
