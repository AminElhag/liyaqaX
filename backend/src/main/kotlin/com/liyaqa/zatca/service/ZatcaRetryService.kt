package com.liyaqa.zatca.service

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.invoice.InvoiceRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ZatcaRetryService(
    private val invoiceRepository: InvoiceRepository,
    private val auditService: AuditService,
) {
    @Transactional
    fun retryInvoice(invoicePublicId: UUID) {
        val invoice = invoiceRepository.findByPublicId(invoicePublicId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Invoice not found") }

        if (invoice.zatcaStatus != "failed") {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Invoice is not in failed state (current: ${invoice.zatcaStatus})",
            )
        }

        invoice.zatcaRetryCount = 0
        invoice.zatcaStatus = "generated"
        invoice.zatcaLastError = null
        invoiceRepository.save(invoice)

        auditService.logFromContext(
            action = AuditAction.ZATCA_INVOICE_RETRY_REQUESTED,
            entityType = "Invoice",
            entityId = invoice.publicId.toString(),
            changesJson = """{"invoicePublicId":"$invoicePublicId"}""",
        )
    }

    @Transactional
    fun retryAllFailedForClub(clubPublicId: UUID) {
        val invoiceIds = invoiceRepository.findFailedZatcaReportingByClub(clubPublicId)
        invoiceIds.forEach { invoiceId ->
            val invoice = invoiceRepository.findById(invoiceId).orElse(null) ?: return@forEach
            invoice.zatcaRetryCount = 0
            invoice.zatcaStatus = "generated"
            invoice.zatcaLastError = null
            invoiceRepository.save(invoice)
        }
        auditService.logFromContext(
            action = AuditAction.ZATCA_INVOICE_RETRY_REQUESTED,
            entityType = "Club",
            entityId = clubPublicId.toString(),
            changesJson = """{"count":"${invoiceIds.size}","clubPublicId":"$clubPublicId"}""",
        )
    }
}
