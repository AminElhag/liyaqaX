package com.liyaqa.notification

import com.liyaqa.notification.events.LeadAssignedEvent
import com.liyaqa.notification.events.MembershipAssignedEvent
import com.liyaqa.notification.events.PaymentCollectedEvent
import com.liyaqa.staff.StaffMemberRepository
import com.liyaqa.user.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class NotificationTriggerServiceTest {
    @Mock lateinit var notificationService: NotificationService

    @Mock lateinit var notificationEmailService: NotificationEmailService

    @Mock lateinit var staffMemberRepository: StaffMemberRepository

    @Mock lateinit var userRepository: UserRepository

    @InjectMocks lateinit var triggerService: NotificationTriggerService

    @Test
    fun `MembershipAssignedEvent creates MEMBERSHIP_ASSIGNED notification`() {
        val event =
            MembershipAssignedEvent(
                membershipPublicId = UUID.randomUUID(),
                memberUserId = 1L,
                planNameEn = "Basic Monthly",
            )
        whenever(notificationService.create(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(null)

        triggerService.onMembershipAssigned(event)

        verify(notificationService).create(any(), any(), eq(NotificationType.MEMBERSHIP_ASSIGNED), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `PaymentCollectedEvent creates PAYMENT_COLLECTED notification and sends email`() {
        val notif =
            Notification(
                recipientUserId = "u-1",
                recipientScope = "member",
                type = NotificationType.PAYMENT_COLLECTED,
                titleKey = "t",
                bodyKey = "b",
            )
        val event =
            PaymentCollectedEvent(
                paymentPublicId = UUID.randomUUID(),
                memberUserId = 1L,
                amountHalalas = 15000L,
                memberEmail = "member@example.com",
            )
        whenever(notificationService.create(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(notif)

        triggerService.onPaymentCollected(event)

        verify(notificationService).create(any(), any(), eq(NotificationType.PAYMENT_COLLECTED), anyOrNull(), anyOrNull(), anyOrNull())
        verify(notificationEmailService).sendNotificationEmail(any(), eq(NotificationType.PAYMENT_COLLECTED), anyOrNull())
    }

    @Test
    fun `LeadAssignedEvent creates LEAD_ASSIGNED notification for assignee`() {
        val event =
            LeadAssignedEvent(
                leadPublicId = UUID.randomUUID(),
                leadName = "Ahmed Al-Rashidi",
                assigneeUserId = 5L,
            )
        whenever(notificationService.create(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(null)

        triggerService.onLeadAssigned(event)

        verify(notificationService).create(any(), any(), eq(NotificationType.LEAD_ASSIGNED), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `exception in listener does not propagate`() {
        val event =
            MembershipAssignedEvent(
                membershipPublicId = UUID.randomUUID(),
                memberUserId = 1L,
                planNameEn = "Basic",
            )
        whenever(notificationService.create(any(), any(), any(), any(), any(), any()))
            .thenThrow(RuntimeException("DB error"))

        // Should not throw
        triggerService.onMembershipAssigned(event)

        verify(notificationService).create(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `PaymentCollectedEvent without email skips email sending`() {
        val notif =
            Notification(
                recipientUserId = "u-1",
                recipientScope = "member",
                type = NotificationType.PAYMENT_COLLECTED,
                titleKey = "t",
                bodyKey = "b",
            )
        val event =
            PaymentCollectedEvent(
                paymentPublicId = UUID.randomUUID(),
                memberUserId = 1L,
                amountHalalas = 15000L,
                memberEmail = null,
            )
        whenever(notificationService.create(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(notif)

        triggerService.onPaymentCollected(event)

        verify(notificationEmailService, never()).sendNotificationEmail(any(), any(), anyOrNull())
    }
}
