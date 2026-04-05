package com.liyaqa.zatca

import com.liyaqa.club.ClubRepository
import com.liyaqa.invoice.InvoiceService
import com.liyaqa.organization.OrganizationRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Profile("dev")
class ZatcaBackfillRunner(
    private val clubRepository: ClubRepository,
    private val organizationRepository: OrganizationRepository,
    private val invoiceService: InvoiceService,
) {
    private val log = LoggerFactory.getLogger(ZatcaBackfillRunner::class.java)

    @EventListener(ApplicationReadyEvent::class)
    @Order(100)
    @Transactional
    fun backfillPendingInvoices() {
        val clubs = clubRepository.findAll()
        for (club in clubs) {
            val org =
                organizationRepository.findById(club.organizationId).orElse(null)
                    ?: continue
            invoiceService.backfillPendingInvoices(club, org)
        }
        log.info("ZATCA backfill complete — pending invoices updated to 'generated'.")
    }
}
