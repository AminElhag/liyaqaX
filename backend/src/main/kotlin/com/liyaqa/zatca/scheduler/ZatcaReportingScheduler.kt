package com.liyaqa.zatca.scheduler

import com.liyaqa.invoice.InvoiceRepository
import com.liyaqa.notification.NotificationService
import com.liyaqa.notification.NotificationType
import com.liyaqa.user.UserRepository
import com.liyaqa.zatca.repository.ClubZatcaCertificateRepository
import com.liyaqa.zatca.service.ZatcaReportingService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class ZatcaReportingScheduler(
    private val invoiceRepository: InvoiceRepository,
    private val reportingService: ZatcaReportingService,
    private val certRepository: ClubZatcaCertificateRepository,
    private val notificationService: NotificationService,
    private val userRepository: UserRepository,
) {
    private val log = LoggerFactory.getLogger(ZatcaReportingScheduler::class.java)

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    fun reportPendingInvoices() {
        val pending = invoiceRepository.findPendingZatcaReporting()
        if (pending.isEmpty()) return

        log.info("ZATCA scheduler: {} invoices pending reporting", pending.size)
        pending.forEach { invoiceId ->
            try {
                reportingService.reportInvoice(invoiceId)
            } catch (ex: Exception) {
                log.error("Unexpected error reporting invoice {}: {}", invoiceId, ex.message)
            }
        }
    }

    @Scheduled(cron = "0 0 6 * * *")
    fun alertFailedInvoices() {
        val failed = invoiceRepository.findFailedZatcaReporting()
        if (failed.isNotEmpty()) {
            log.error("ZATCA ALERT: {} invoices permanently failed reporting. Manual review required.", failed.size)
        }
    }

    @Scheduled(cron = "0 0 4 * * *")
    fun alertExpiringCsids() {
        val threshold = Instant.now().plus(30, ChronoUnit.DAYS)
        val expiring = certRepository.findExpiringSoon(threshold)
        if (expiring.isEmpty()) return

        log.warn("ZATCA: {} CSIDs expiring within 30 days", expiring.size)

        val adminUserIds = userRepository.findPlatformUserIdsWithPermission("zatca:read")
        if (adminUserIds.isEmpty()) return

        expiring.forEach { cert ->
            val daysUntilExpiry = ChronoUnit.DAYS.between(Instant.now(), cert.csidExpiresAt)
            adminUserIds.forEach { userId ->
                notificationService.create(
                    recipientUserId = userId,
                    recipientScope = "platform",
                    type = NotificationType.ZATCA_CSID_EXPIRING_SOON,
                    paramsJson = """{"daysRemaining":$daysUntilExpiry,"clubId":"${cert.clubId}"}""",
                    entityType = "Club",
                    entityId = cert.clubId.toString(),
                )
            }
        }
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000)
    fun alertInvoicesApproachingDeadline() {
        val threshold = Instant.now().minus(23, ChronoUnit.HOURS)
        val count = invoiceRepository.countInvoicesApproachingDeadline(threshold)
        if (count == 0L) return

        log.error(
            "ZATCA DEADLINE RISK: {} invoices unreported and older than 23 hours. " +
                "ZATCA requires reporting within 24 hours.",
            count,
        )

        val adminUserIds = userRepository.findPlatformUserIdsWithPermission("zatca:read")
        adminUserIds.forEach { userId ->
            notificationService.create(
                recipientUserId = userId,
                recipientScope = "platform",
                type = NotificationType.ZATCA_INVOICE_DEADLINE_AT_RISK,
                paramsJson = """{"count":$count}""",
                entityType = "Invoice",
                entityId = "deadline-risk",
            )
        }
    }
}
