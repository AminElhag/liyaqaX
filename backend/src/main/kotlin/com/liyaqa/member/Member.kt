package com.liyaqa.member

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "members")
class Member(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "branch_id", nullable = false)
    val branchId: Long,
    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    val userId: Long,
    @Column(name = "first_name_ar", nullable = false, length = 100)
    var firstNameAr: String,
    @Column(name = "first_name_en", nullable = false, length = 100)
    var firstNameEn: String,
    @Column(name = "last_name_ar", nullable = false, length = 100)
    var lastNameAr: String,
    @Column(name = "last_name_en", nullable = false, length = 100)
    var lastNameEn: String,
    @Column(name = "phone", nullable = false, length = 50)
    var phone: String,
    @Column(name = "national_id", length = 50)
    var nationalId: String? = null,
    @Column(name = "date_of_birth")
    var dateOfBirth: LocalDate? = null,
    // gender: male | female | unspecified
    @Column(name = "gender", length = 20)
    var gender: String? = null,
    // membership_status: pending | active | frozen | expired | terminated
    @Column(name = "membership_status", nullable = false, length = 50)
    var membershipStatus: String = "pending",
    @Column(name = "notes", columnDefinition = "TEXT")
    var notes: String? = null,
    @Column(name = "joined_at", nullable = false)
    var joinedAt: LocalDate = LocalDate.now(),
    @Column(name = "preferred_language", length = 10)
    var preferredLanguage: String? = null,
    @Column(name = "member_import_job_id")
    var memberImportJobId: Long? = null,
) : AuditEntity()
