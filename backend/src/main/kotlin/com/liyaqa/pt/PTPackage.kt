package com.liyaqa.pt

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "pt_packages")
class PTPackage(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: Long,
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "trainer_id", nullable = false)
    var trainerId: Long,
    @Column(name = "catalog_id", nullable = false, updatable = false)
    val catalogId: Long,
    @Column(name = "sessions_total", nullable = false)
    var sessionsTotal: Int,
    @Column(name = "sessions_used", nullable = false)
    var sessionsUsed: Int = 0,
    @Column(name = "package_status", nullable = false, length = 50)
    var packageStatus: String = "active",
    @Column(name = "starts_at", nullable = false)
    var startsAt: LocalDate,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDate,
) : AuditEntity()
