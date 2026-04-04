package com.liyaqa.gx

import com.liyaqa.common.audit.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "gx_bookings")
class GXBooking(
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "instance_id", nullable = false, updatable = false)
    val instanceId: Long,
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "booking_status", nullable = false, length = 50)
    var bookingStatus: String = "confirmed",
    @Column(name = "booked_at", nullable = false)
    val bookedAt: Instant = Instant.now(),
    @Column(name = "waitlist_position")
    var waitlistPosition: Int? = null,
    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,
    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    var cancellationReason: String? = null,
) : BaseEntity()
