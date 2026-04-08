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
@Table(name = "shift_swap_requests")
class ShiftSwapRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "shift_id", nullable = false, updatable = false)
    val shiftId: Long,
    @Column(name = "requester_staff_id", nullable = false, updatable = false)
    val requesterStaffId: Long,
    @Column(name = "target_staff_id", nullable = false, updatable = false)
    val targetStaffId: Long,
    @Column(name = "status", nullable = false, length = 30)
    var status: String = "PENDING_ACCEPTANCE",
    @Column(name = "requester_note", length = 300)
    val requesterNote: String? = null,
    @Column(name = "resolved_by_user_id")
    var resolvedByUserId: Long? = null,
    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
