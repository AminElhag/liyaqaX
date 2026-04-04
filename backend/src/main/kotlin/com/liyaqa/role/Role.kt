package com.liyaqa.role

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "roles")
class Role(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "name_ar", nullable = false, length = 100)
    var nameAr: String,
    @Column(name = "name_en", nullable = false, length = 100)
    var nameEn: String,
    // "platform" | "club" | "trainer" | "member"
    @Column(name = "scope", nullable = false, length = 20)
    val scope: String,
    // null for platform-scoped roles
    @Column(name = "organization_id")
    val organizationId: Long? = null,
    // null for platform-scoped roles
    @Column(name = "club_id")
    val clubId: Long? = null,
    // true = created by platform; cannot be deleted by club admins
    @Column(name = "is_system", nullable = false)
    val isSystem: Boolean = false,
) : AuditEntity()
