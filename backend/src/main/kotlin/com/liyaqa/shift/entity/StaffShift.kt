package com.liyaqa.shift.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "staff_shifts")
class StaffShift(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "staff_member_id", nullable = false)
    var staffMemberId: Long,
    @Column(name = "branch_id", nullable = false)
    val branchId: Long,
    @Column(name = "start_at", nullable = false)
    var startAt: Instant,
    @Column(name = "end_at", nullable = false)
    var endAt: Instant,
    @Column(name = "notes", length = 500)
    var notes: String? = null,
    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    val createdByUserId: Long,
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)
