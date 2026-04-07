package com.liyaqa.notification.events

import java.util.UUID

data class MembershipAssignedEvent(
    val membershipPublicId: UUID,
    val memberUserId: Long,
    val planNameEn: String,
)

data class MembershipFrozenEvent(
    val membershipPublicId: UUID,
    val memberUserId: Long,
    val freezeEndDate: String,
)

data class PaymentCollectedEvent(
    val paymentPublicId: UUID,
    val memberUserId: Long,
    val amountHalalas: Long,
    val memberEmail: String?,
)

data class GxBookedEvent(
    val bookingPublicId: UUID,
    val memberUserId: Long,
    val className: String,
    val classDate: String,
)

data class GxCancelledEvent(
    val bookingPublicId: UUID,
    val memberUserId: Long,
    val className: String,
    val classDate: String,
)

data class PtAttendanceMarkedEvent(
    val sessionPublicId: UUID,
    val memberUserId: Long,
    val status: String,
    val trainerName: String,
)

data class LeadAssignedEvent(
    val leadPublicId: UUID,
    val leadName: String,
    val assigneeUserId: Long,
)

data class MemberCreatedEvent(
    val memberPublicId: UUID,
    val memberName: String,
    val branchId: Long,
    val clubId: Long,
)
