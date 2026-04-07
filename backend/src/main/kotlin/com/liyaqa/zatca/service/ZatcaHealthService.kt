package com.liyaqa.zatca.service

import com.liyaqa.invoice.InvoiceRepository
import com.liyaqa.zatca.dto.ZatcaFailedInvoiceResponse
import com.liyaqa.zatca.dto.ZatcaHealthSummary
import com.liyaqa.zatca.repository.ClubZatcaCertificateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class ZatcaHealthService(
    private val certRepository: ClubZatcaCertificateRepository,
    private val invoiceRepository: InvoiceRepository,
) {
    fun getHealthSummary(): ZatcaHealthSummary {
        val now = Instant.now()
        val thirtyDaysFromNow = now.plus(30, ChronoUnit.DAYS)

        return ZatcaHealthSummary(
            totalActiveCsids = certRepository.countByStatusAndNotDeleted("active"),
            csidsExpiringSoon = certRepository.countExpiringSoon(thirtyDaysFromNow),
            clubsNotOnboarded = certRepository.countByStatusNotAndNotDeleted("active"),
            invoicesPending = invoiceRepository.countPendingZatcaReporting(),
            invoicesFailed = invoiceRepository.countFailedZatcaReporting(),
            invoicesDeadlineAtRisk = invoiceRepository.countInvoicesApproachingDeadline(
                now.minus(23, ChronoUnit.HOURS),
            ),
        )
    }

    fun getFailedInvoices(): List<ZatcaFailedInvoiceResponse> {
        return invoiceRepository.findFailedZatcaInvoicesWithClub()
            .map { row ->
                ZatcaFailedInvoiceResponse(
                    invoicePublicId = row.invoicePublicId,
                    invoiceNumber = row.invoiceNumber,
                    clubName = row.clubName,
                    memberName = row.memberName,
                    amountSar = "%.2f".format(row.amountHalalas / 100.0),
                    createdAt = row.createdAt.toString(),
                    zatcaRetryCount = row.zatcaRetryCount,
                    zatcaLastError = row.zatcaLastError,
                    zatcaStatus = row.zatcaStatus,
                )
            }
    }
}
