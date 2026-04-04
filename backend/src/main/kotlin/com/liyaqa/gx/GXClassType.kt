package com.liyaqa.gx

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "gx_class_types")
class GXClassType(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "name_ar", nullable = false)
    var nameAr: String,
    @Column(name = "name_en", nullable = false)
    var nameEn: String,
    @Column(name = "description_ar", columnDefinition = "TEXT")
    var descriptionAr: String? = null,
    @Column(name = "description_en", columnDefinition = "TEXT")
    var descriptionEn: String? = null,
    @Column(name = "default_duration_minutes", nullable = false)
    var defaultDurationMinutes: Int = 60,
    @Column(name = "default_capacity", nullable = false)
    var defaultCapacity: Int = 20,
    @Column(name = "color", length = 7)
    var color: String? = null,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : AuditEntity()
