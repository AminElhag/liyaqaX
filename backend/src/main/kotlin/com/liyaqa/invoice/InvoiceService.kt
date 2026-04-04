package com.liyaqa.invoice

import com.liyaqa.club.Club
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.invoice.dto.InvoiceResponse
import com.liyaqa.invoice.dto.InvoiceSummaryResponse
import com.liyaqa.member.Member
import com.liyaqa.payment.Payment
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Service
@Transactional(readOnly = true)
class InvoiceService(
    private val invoiceRepository: InvoiceRepository,
) {
    companion object {
        val VAT_RATE: BigDecimal = BigDecimal("0.1500")
    }

    @Transactional
    fun createInvoiceStub(
        payment: Payment,
        member: Member,
        club: Club,
        subtotalHalalas: Long,
    ): Invoice {
        val vatAmountHalalas = calculateVat(subtotalHalalas)
        val totalHalalas = subtotalHalalas + vatAmountHalalas

        // Rule 8 — Invoice total integrity check
        check(totalHalalas == subtotalHalalas + vatAmountHalalas) {
            "Invoice total integrity check failed: $totalHalalas != $subtotalHalalas + $vatAmountHalalas"
        }

        val invoiceNumber = generateInvoiceNumber(club)

        return invoiceRepository.save(
            Invoice(
                organizationId = payment.organizationId,
                clubId = payment.clubId,
                branchId = payment.branchId,
                memberId = payment.memberId,
                paymentId = payment.id,
                invoiceNumber = invoiceNumber,
                subtotalHalalas = subtotalHalalas,
                vatRate = VAT_RATE,
                vatAmountHalalas = vatAmountHalalas,
                totalHalalas = totalHalalas,
                issuedAt = Instant.now(),
                zatcaStatus = "pending",
            ),
        )
    }

    fun calculateVat(subtotalHalalas: Long): Long =
        BigDecimal(subtotalHalalas)
            .multiply(VAT_RATE)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()

    fun generateInvoiceNumber(club: Club): String {
        val year = Instant.now().atZone(ZoneOffset.UTC).year
        val clubCode = club.nameEn.take(3).uppercase()
        val sequence = invoiceRepository.countByClubIdAndYear(club.id, year) + 1
        return "INV-$year-$clubCode-${"%05d".format(sequence)}"
    }

    fun findByPaymentId(paymentId: Long): Invoice? = invoiceRepository.findByPaymentId(paymentId).orElse(null)

    fun toResponse(
        invoice: Invoice,
        memberPublicId: UUID,
        memberName: String,
        paymentMethod: String,
    ): InvoiceResponse =
        InvoiceResponse(
            id = invoice.publicId,
            invoiceNumber = invoice.invoiceNumber,
            memberId = memberPublicId,
            memberName = memberName,
            subtotalHalalas = invoice.subtotalHalalas,
            subtotalSar = formatSar(invoice.subtotalHalalas),
            vatRate = invoice.vatRate,
            vatAmountHalalas = invoice.vatAmountHalalas,
            vatAmountSar = formatSar(invoice.vatAmountHalalas),
            totalHalalas = invoice.totalHalalas,
            totalSar = formatSar(invoice.totalHalalas),
            paymentMethod = paymentMethod,
            issuedAt = invoice.issuedAt,
            zatcaStatus = invoice.zatcaStatus,
        )

    fun toSummaryResponse(invoice: Invoice): InvoiceSummaryResponse =
        InvoiceSummaryResponse(
            id = invoice.publicId,
            invoiceNumber = invoice.invoiceNumber,
            totalHalalas = invoice.totalHalalas,
            totalSar = formatSar(invoice.totalHalalas),
            issuedAt = invoice.issuedAt,
            zatcaStatus = invoice.zatcaStatus,
        )

    fun findByPublicIdAndOrgId(
        publicId: UUID,
        organizationId: Long,
    ): Invoice =
        invoiceRepository.findByPublicIdAndOrganizationId(publicId, organizationId)
            .orElseThrow {
                ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Invoice not found.")
            }

    private fun formatSar(halalas: Long): String = "%.2f".format(halalas / 100.0)
}
