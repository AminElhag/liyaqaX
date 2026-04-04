package com.liyaqa.staff

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "staff_members")
class StaffMember(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    val userId: Long,
    @Column(name = "role_id", nullable = false)
    var roleId: Long,
    @Column(name = "first_name_ar", nullable = false, length = 100)
    var firstNameAr: String,
    @Column(name = "first_name_en", nullable = false, length = 100)
    var firstNameEn: String,
    @Column(name = "last_name_ar", nullable = false, length = 100)
    var lastNameAr: String,
    @Column(name = "last_name_en", nullable = false, length = 100)
    var lastNameEn: String,
    @Column(name = "phone", length = 50)
    var phone: String? = null,
    @Column(name = "national_id", length = 50)
    var nationalId: String? = null,
    // 'full-time' | 'part-time' | 'contractor'
    @Column(name = "employment_type", nullable = false, length = 50)
    var employmentType: String = "full-time",
    @Column(name = "joined_at", nullable = false)
    var joinedAt: LocalDate,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) : AuditEntity()
