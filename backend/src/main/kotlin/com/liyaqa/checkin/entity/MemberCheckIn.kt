package com.liyaqa.checkin.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "member_check_ins")
class MemberCheckIn(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Column(name = "branch_id", nullable = false)
    val branchId: Long,
    @Column(name = "checked_in_by_user_id", nullable = false)
    val checkedInByUserId: Long,
    @Column(name = "method", nullable = false, length = 20)
    val method: String,
    @Column(name = "checked_in_at", nullable = false, updatable = false)
    val checkedInAt: Instant = Instant.now(),
)
