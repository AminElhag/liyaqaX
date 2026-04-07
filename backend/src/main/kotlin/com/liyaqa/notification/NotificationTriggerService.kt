package com.liyaqa.notification

import com.liyaqa.notification.events.GxBookedEvent
import com.liyaqa.notification.events.GxCancelledEvent
import com.liyaqa.notification.events.LeadAssignedEvent
import com.liyaqa.notification.events.MemberCreatedEvent
import com.liyaqa.notification.events.MembershipAssignedEvent
import com.liyaqa.notification.events.MembershipFrozenEvent
import com.liyaqa.notification.events.PaymentCollectedEvent
import com.liyaqa.notification.events.PtAttendanceMarkedEvent
import com.liyaqa.staff.StaffMemberRepository
import com.liyaqa.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class NotificationTriggerService(
    private val notificationService: NotificationService,
    private val notificationEmailService: NotificationEmailService,
    private val staffMemberRepository: StaffMemberRepository,
    private val userRepository: UserRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(NotificationTriggerService::class.java)
    }

    @EventListener
    fun onMembershipAssigned(event: MembershipAssignedEvent) {
        try {
            notificationService.create(
                recipientUserId = event.memberUserId,
                recipientScope = "member",
                type = NotificationType.MEMBERSHIP_ASSIGNED,
                paramsJson = """{"planName":"${event.planNameEn}"}""",
                entityType = "Membership",
                entityId = event.membershipPublicId.toString(),
            )
        } catch (e: Exception) {
            log.warn("Failed to create MEMBERSHIP_ASSIGNED notification: {}", e.message)
        }
    }

    @EventListener
    fun onMembershipFrozen(event: MembershipFrozenEvent) {
        try {
            notificationService.create(
                recipientUserId = event.memberUserId,
                recipientScope = "member",
                type = NotificationType.MEMBERSHIP_FROZEN,
                paramsJson = """{"freezeEndDate":"${event.freezeEndDate}"}""",
                entityType = "Membership",
                entityId = event.membershipPublicId.toString(),
            )
        } catch (e: Exception) {
            log.warn("Failed to create MEMBERSHIP_FROZEN notification: {}", e.message)
        }
    }

    @EventListener
    fun onPaymentCollected(event: PaymentCollectedEvent) {
        try {
            val amountSar = "%.2f".format(event.amountHalalas / 100.0)
            val notification =
                notificationService.create(
                    recipientUserId = event.memberUserId,
                    recipientScope = "member",
                    type = NotificationType.PAYMENT_COLLECTED,
                    paramsJson = """{"amountSar":"$amountSar"}""",
                    entityType = "Payment",
                    entityId = event.paymentPublicId.toString(),
                )
            if (notification != null && event.memberEmail != null) {
                sendEmailSafely(notification, event.memberEmail)
            }
        } catch (e: Exception) {
            log.warn("Failed to create PAYMENT_COLLECTED notification: {}", e.message)
        }
    }

    @EventListener
    fun onGxBooked(event: GxBookedEvent) {
        try {
            notificationService.create(
                recipientUserId = event.memberUserId,
                recipientScope = "member",
                type = NotificationType.GX_CLASS_BOOKED,
                paramsJson = """{"className":"${event.className}","classDate":"${event.classDate}"}""",
                entityType = "GXBooking",
                entityId = event.bookingPublicId.toString(),
            )
        } catch (e: Exception) {
            log.warn("Failed to create GX_CLASS_BOOKED notification: {}", e.message)
        }
    }

    @EventListener
    fun onGxCancelled(event: GxCancelledEvent) {
        try {
            notificationService.create(
                recipientUserId = event.memberUserId,
                recipientScope = "member",
                type = NotificationType.GX_CLASS_CANCELLED,
                paramsJson = """{"className":"${event.className}","classDate":"${event.classDate}"}""",
                entityType = "GXBooking",
                entityId = event.bookingPublicId.toString(),
            )
        } catch (e: Exception) {
            log.warn("Failed to create GX_CLASS_CANCELLED notification: {}", e.message)
        }
    }

    @EventListener
    fun onPtAttendanceMarked(event: PtAttendanceMarkedEvent) {
        try {
            notificationService.create(
                recipientUserId = event.memberUserId,
                recipientScope = "member",
                type = NotificationType.PT_ATTENDANCE_MARKED,
                paramsJson = """{"status":"${event.status}","trainerName":"${event.trainerName}"}""",
                entityType = "PTSession",
                entityId = event.sessionPublicId.toString(),
            )
        } catch (e: Exception) {
            log.warn("Failed to create PT_ATTENDANCE_MARKED notification: {}", e.message)
        }
    }

    @EventListener
    fun onLeadAssigned(event: LeadAssignedEvent) {
        try {
            notificationService.create(
                recipientUserId = event.assigneeUserId,
                recipientScope = "club",
                type = NotificationType.LEAD_ASSIGNED,
                paramsJson = """{"leadName":"${event.leadName}"}""",
                entityType = "Lead",
                entityId = event.leadPublicId.toString(),
            )
        } catch (e: Exception) {
            log.warn("Failed to create LEAD_ASSIGNED notification: {}", e.message)
        }
    }

    @EventListener
    fun onMemberCreated(event: MemberCreatedEvent) {
        try {
            val branchManagers =
                staffMemberRepository.findAllByClubIdAndDeletedAtIsNull(event.clubId)
                    .filter { it.roleId != 0L }

            val managerUsers = userRepository.findAllById(branchManagers.map { it.userId })
            for (user in managerUsers) {
                notificationService.create(
                    recipientUserId = user.id,
                    recipientScope = "club",
                    type = NotificationType.NEW_MEMBER_REGISTERED,
                    paramsJson = """{"memberName":"${event.memberName}"}""",
                    entityType = "Member",
                    entityId = event.memberPublicId.toString(),
                )
            }
        } catch (e: Exception) {
            log.warn("Failed to create NEW_MEMBER_REGISTERED notification: {}", e.message)
        }
    }

    private fun sendEmailSafely(
        notification: Notification,
        recipientEmail: String,
    ) {
        try {
            notificationEmailService.sendNotificationEmail(
                recipientEmail = recipientEmail,
                type = notification.type,
                paramsJson = notification.paramsJson,
            )
            notification.emailSentAt = java.time.Instant.now()
        } catch (e: Exception) {
            log.error("Failed to send notification email for {}: {}", notification.type, e.message)
        }
    }
}
