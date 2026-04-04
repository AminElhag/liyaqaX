package com.liyaqa.trainer

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "trainer_certifications")
class TrainerCertification(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "trainer_id", nullable = false, updatable = false)
    val trainerId: Long,
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "name_ar", nullable = false, length = 255)
    var nameAr: String,
    @Column(name = "name_en", nullable = false, length = 255)
    var nameEn: String,
    @Column(name = "issuing_body", length = 255)
    var issuingBody: String? = null,
    @Column(name = "issued_at")
    var issuedAt: LocalDate? = null,
    @Column(name = "expires_at")
    var expiresAt: LocalDate? = null,
    // 'pending-review' | 'approved' | 'rejected' | 'expired'
    @Column(name = "status", nullable = false, length = 50)
    var status: String = "pending-review",
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    var rejectionReason: String? = null,
) : AuditEntity()
