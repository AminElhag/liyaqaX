package com.liyaqa.branch

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "branches")
class Branch(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "name_ar", nullable = false, length = 255)
    var nameAr: String,
    @Column(name = "name_en", nullable = false, length = 255)
    var nameEn: String,
    @Column(name = "address_ar", columnDefinition = "TEXT")
    var addressAr: String? = null,
    @Column(name = "address_en", columnDefinition = "TEXT")
    var addressEn: String? = null,
    @Column(name = "city", length = 100)
    var city: String? = null,
    @Column(name = "phone", length = 50)
    var phone: String? = null,
    @Column(name = "email", length = 255)
    var email: String? = null,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : AuditEntity()
