package com.liyaqa.zatca.scheduler

import com.liyaqa.invoice.InvoiceRepository
import com.liyaqa.notification.Notification
import com.liyaqa.notification.NotificationService
import com.liyaqa.notification.NotificationType
import com.liyaqa.user.UserRepository
import com.liyaqa.zatca.entity.ClubZatcaCertificate
import com.liyaqa.zatca.repository.ClubZatcaCertificateRepository
import com.liyaqa.zatca.service.ZatcaReportingService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit

@ExtendWith(MockitoExtension::class)
class ZatcaSchedulerAlertTest {
    @Mock lateinit var invoiceRepository: InvoiceRepository

    @Mock lateinit var reportingService: ZatcaReportingService

    @Mock lateinit var certRepository: ClubZatcaCertificateRepository

    @Mock lateinit var notificationService: NotificationService

    @Mock lateinit var userRepository: UserRepository

    private lateinit var scheduler: ZatcaReportingScheduler

    @BeforeEach
    fun setUp() {
        scheduler = ZatcaReportingScheduler(
            invoiceRepository,
            reportingService,
            certRepository,
            notificationService,
            userRepository,
        )
    }

    private fun testCert(daysUntilExpiry: Long = 15): ClubZatcaCertificate {
        val cert = ClubZatcaCertificate(clubId = 1L, environment = "production")
        cert.onboardingStatus = "active"
        cert.csidExpiresAt = Instant.now().plus(daysUntilExpiry, ChronoUnit.DAYS)
        return cert
    }

    @Test
    fun `alertExpiringCsids creates notification for each expiring certificate`() {
        val cert1 = testCert(10)
        val cert2 = testCert(20)
        whenever(certRepository.findExpiringSoon(any())).thenReturn(listOf(cert1, cert2))
        whenever(userRepository.findPlatformUserIdsWithPermission("zatca:read")).thenReturn(listOf(1L))
        whenever(notificationService.create(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(null)

        scheduler.alertExpiringCsids()

        verify(notificationService, times(2)).create(
            eq(1L), eq("platform"), eq(NotificationType.ZATCA_CSID_EXPIRING_SOON),
            anyOrNull(), anyOrNull(), anyOrNull(),
        )
    }

    @Test
    fun `alertExpiringCsids skips when no expiring CSIDs`() {
        whenever(certRepository.findExpiringSoon(any())).thenReturn(emptyList())

        scheduler.alertExpiringCsids()

        verify(notificationService, never()).create(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `alertInvoicesApproachingDeadline creates notification when count greater than 0`() {
        whenever(invoiceRepository.countInvoicesApproachingDeadline(any())).thenReturn(3L)
        whenever(userRepository.findPlatformUserIdsWithPermission("zatca:read")).thenReturn(listOf(1L))
        whenever(notificationService.create(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(null)

        scheduler.alertInvoicesApproachingDeadline()

        verify(notificationService).create(
            eq(1L), eq("platform"), eq(NotificationType.ZATCA_INVOICE_DEADLINE_AT_RISK),
            anyOrNull(), anyOrNull(), anyOrNull(),
        )
    }

    @Test
    fun `alertInvoicesApproachingDeadline skips when count is 0`() {
        whenever(invoiceRepository.countInvoicesApproachingDeadline(any())).thenReturn(0L)

        scheduler.alertInvoicesApproachingDeadline()

        verify(notificationService, never()).create(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull())
    }
}
