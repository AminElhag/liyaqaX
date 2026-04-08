package com.liyaqa.pt

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "pt_sessions")
class PTSession(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: Long,
    @Column(name = "trainer_id", nullable = false)
    var trainerId: Long,
    @Column(name = "member_id", nullable = false)
    var memberId: Long,
    @Column(name = "package_id", nullable = false)
    var packageId: Long,
    @Column(name = "scheduled_at", nullable = false)
    var scheduledAt: Instant,
    @Column(name = "duration_minutes", nullable = false)
    var durationMinutes: Int = 60,
    @Column(name = "session_status", nullable = false, length = 50)
    var sessionStatus: String = "scheduled",
    @Column(name = "notes", columnDefinition = "TEXT")
    var notes: String? = null,
) : AuditEntity()
