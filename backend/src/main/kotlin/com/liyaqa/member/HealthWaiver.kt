package com.liyaqa.member

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "health_waivers")
class HealthWaiver(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "content_ar", nullable = false, columnDefinition = "TEXT")
    var contentAr: String,
    @Column(name = "content_en", nullable = false, columnDefinition = "TEXT")
    var contentEn: String,
    @Column(name = "version", nullable = false)
    var version: Int = 1,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : AuditEntity()
