package com.liyaqa.pt

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "pt_package_catalogs")
class PTPackageCatalog(
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
    @Column(name = "session_count", nullable = false)
    var sessionCount: Int,
    @Column(name = "price_halalas", nullable = false)
    var priceHalalas: Long,
    @Column(name = "validity_days", nullable = false)
    var validityDays: Int = 90,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : AuditEntity()
