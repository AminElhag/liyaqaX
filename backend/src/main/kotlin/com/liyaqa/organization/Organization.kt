package com.liyaqa.organization

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "organizations")
class Organization(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "name_ar", nullable = false)
    var nameAr: String,
    @Column(name = "name_en", nullable = false)
    var nameEn: String,
    @Column(name = "email", nullable = false, unique = true)
    var email: String,
    @Column(name = "phone", length = 50)
    var phone: String? = null,
    @Column(name = "country", nullable = false, length = 10)
    var country: String = "SA",
    @Column(name = "timezone", nullable = false, length = 50)
    var timezone: String = "Asia/Riyadh",
    @Column(name = "locale", nullable = false, length = 10)
    var locale: String = "ar",
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    @Column(name = "vat_number", length = 50)
    var vatNumber: String? = null,
    @Column(name = "cr_number", length = 50)
    var crNumber: String? = null,
) : AuditEntity()
