package com.liyaqa.gx

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "gx_class_instances")
class GXClassInstance(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: Long,
    @Column(name = "class_type_id", nullable = false, updatable = false)
    val classTypeId: Long,
    @Column(name = "instructor_id", nullable = false)
    var instructorId: Long,
    @Column(name = "scheduled_at", nullable = false)
    var scheduledAt: Instant,
    @Column(name = "duration_minutes", nullable = false)
    var durationMinutes: Int = 60,
    @Column(name = "capacity", nullable = false)
    var capacity: Int = 20,
    @Column(name = "bookings_count", nullable = false)
    var bookingsCount: Int = 0,
    @Column(name = "waitlist_count", nullable = false)
    var waitlistCount: Int = 0,
    @Column(name = "room", length = 100)
    var room: String? = null,
    @Column(name = "instance_status", nullable = false, length = 50)
    var instanceStatus: String = "scheduled",
    @Column(name = "notes", columnDefinition = "TEXT")
    var notes: String? = null,
) : AuditEntity()
