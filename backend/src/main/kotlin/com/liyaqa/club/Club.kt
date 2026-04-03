package com.liyaqa.club

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "clubs")
class Club(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "name_ar", nullable = false)
    var nameAr: String,
    @Column(name = "name_en", nullable = false)
    var nameEn: String,
    @Column(name = "email")
    var email: String? = null,
    @Column(name = "phone", length = 50)
    var phone: String? = null,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : AuditEntity()
