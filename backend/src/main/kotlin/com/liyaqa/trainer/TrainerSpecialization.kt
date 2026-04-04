package com.liyaqa.trainer

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "trainer_specializations")
class TrainerSpecialization(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "trainer_id", nullable = false, updatable = false)
    val trainerId: Long,
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "name_ar", nullable = false, length = 100)
    var nameAr: String,
    @Column(name = "name_en", nullable = false, length = 100)
    var nameEn: String,
) : AuditEntity()
