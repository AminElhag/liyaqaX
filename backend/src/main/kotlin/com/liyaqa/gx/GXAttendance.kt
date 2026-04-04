package com.liyaqa.gx

import com.liyaqa.common.audit.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "gx_attendance")
class GXAttendance(
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "instance_id", nullable = false, updatable = false)
    val instanceId: Long,
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "attendance_status", nullable = false, length = 50)
    var attendanceStatus: String,
    @Column(name = "marked_at", nullable = false)
    var markedAt: Instant = Instant.now(),
    @Column(name = "marked_by_id", nullable = false)
    var markedById: Long,
) : BaseEntity()
