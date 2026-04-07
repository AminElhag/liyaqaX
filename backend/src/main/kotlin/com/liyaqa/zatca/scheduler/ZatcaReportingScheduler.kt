package com.liyaqa.zatca.scheduler

import com.liyaqa.invoice.InvoiceRepository
import com.liyaqa.zatca.service.ZatcaReportingService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ZatcaReportingScheduler(
    private val invoiceRepository: InvoiceRepository,
    private val reportingService: ZatcaReportingService,
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
}
