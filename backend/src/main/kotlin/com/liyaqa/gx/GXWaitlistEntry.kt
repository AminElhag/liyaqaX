package com.liyaqa.gx

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "gx_waitlist_entries",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_waitlist_member_class",
            columnNames = ["class_instance_id", "member_id"],
        ),
    ],
)
class GXWaitlistEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "class_instance_id", nullable = false)
    val classInstanceId: Long,
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Column(name = "position", nullable = false)
    val position: Int,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: GXWaitlistStatus = GXWaitlistStatus.WAITING,
    @Column(name = "notified_at")
    var notifiedAt: Instant? = null,
    @Column(name = "accepted_booking_id")
    var acceptedBookingId: Long? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
