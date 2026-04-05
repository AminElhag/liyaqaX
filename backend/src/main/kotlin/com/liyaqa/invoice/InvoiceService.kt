package com.liyaqa.invoice

import com.liyaqa.club.Club
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.invoice.dto.InvoiceResponse
import com.liyaqa.invoice.dto.InvoiceSummaryResponse
import com.liyaqa.member.Member
import com.liyaqa.organization.Organization
import com.liyaqa.payment.Payment
import com.liyaqa.zatca.ZatcaHashUtil
import com.liyaqa.zatca.ZatcaQrService
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
    private val invoiceCounterRepository: InvoiceCounterRepository,
    private val zatcaQrService: ZatcaQrService,
) {
    companion object {
        val VAT_RATE: BigDecimal = BigDecimal("0.1500")
    }

    @Transactional
    fun createInvoice(
        payment: Payment,
        member: Member,
        club: Club,
        organization: Organization,
        subtotalHalalas: Long,
    ): Invoice {
        // Step 1 — Calculate subtotal, VAT (15%), total
        val vatAmountHalalas = calculateVat(subtotalHalalas)
        val totalHalalas = subtotalHalalas + vatAmountHalalas

        check(totalHalalas == subtotalHalalas + vatAmountHalalas) {
            "Invoice total integrity check failed: $totalHalalas != $subtotalHalalas + $vatAmountHalalas"
        }

        // Step 2 — Generate invoice number
        val invoiceNumber = generateInvoiceNumber(club)

        val issuedAt = Instant.now()

        val invoice =
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
                issuedAt = issuedAt,
            )

        // Step 3 — Get next counter value (atomic)
        val counterValue = invoiceCounterRepository.incrementAndGet(club.id)

        // Step 4 — Get previous invoice hash (PIH chain)
        val previousHash =
            invoiceRepository
                .findTopByClubIdAndInvoiceCounterValueIsNotNullOrderByInvoiceCounterValueDesc(club.id)
                .map { it.zatcaHash ?: ZatcaHashUtil.INITIAL_PIH }
                .orElse(ZatcaHashUtil.INITIAL_PIH)

        // Step 5 — Get organization VAT number and seller name for QR
        val vatNumber = zatcaQrService.resolveVatNumber(club, organization)
        val sellerName = zatcaQrService.resolveSellerName(organization)

        // Step 6 — Format issuedAt as ISO 8601 string
        val timestamp = issuedAt.toString()

        // Step 7 — Format amounts as SAR decimal strings
        val totalWithVatSar = zatcaQrService.formatSarAmount(totalHalalas)
        val vatAmountSar = zatcaQrService.formatSarAmount(vatAmountHalalas)

        // Step 8 — Generate invoice hash
        val zatcaHash =
            zatcaQrService.generateHash(
                uuid = invoice.publicId.toString(),
                invoiceNumber = invoiceNumber,
                issuedAt = timestamp,
                sellerVatNumber = vatNumber,
                totalWithVatSar = totalWithVatSar,
                vatAmountSar = vatAmountSar,
            )

        // Step 9 — Generate QR code
        val zatcaQrCode =
            zatcaQrService.generateQrCode(
                sellerName = sellerName,
                vatNumber = vatNumber,
                timestamp = timestamp,
                totalWithVatSar = totalWithVatSar,
                vatAmountSar = vatAmountSar,
            )

        // Step 10 — Set ZATCA fields on invoice
        invoice.zatcaStatus = "generated"
        invoice.zatcaUuid = invoice.publicId.toString()
        invoice.zatcaHash = zatcaHash
        invoice.zatcaQrCode = zatcaQrCode
        invoice.previousInvoiceHash = previousHash
        invoice.invoiceCounterValue = counterValue

        // Step 11 — Save invoice with all fields
        return invoiceRepository.save(invoice)
    }

    @Transactional
    fun backfillPendingInvoices(
        club: Club,
        organization: Organization,
    ) {
        val pending =
            invoiceRepository.findAllByClubIdAndZatcaStatus(club.id, "pending")
        for (invoice in pending) {
            val vatNumber = zatcaQrService.resolveVatNumber(club, organization)
            val sellerName = zatcaQrService.resolveSellerName(organization)
            val timestamp = invoice.issuedAt.toString()
            val totalWithVatSar = zatcaQrService.formatSarAmount(invoice.totalHalalas)
            val vatAmountSar = zatcaQrService.formatSarAmount(invoice.vatAmountHalalas)

            val counterValue = invoiceCounterRepository.incrementAndGet(club.id)

            val previousHash =
                invoiceRepository
                    .findTopByClubIdAndInvoiceCounterValueIsNotNullOrderByInvoiceCounterValueDesc(club.id)
                    .filter { it.id != invoice.id }
                    .map { it.zatcaHash ?: ZatcaHashUtil.INITIAL_PIH }
                    .orElse(ZatcaHashUtil.INITIAL_PIH)

            val zatcaHash =
                zatcaQrService.generateHash(
                    uuid = invoice.publicId.toString(),
                    invoiceNumber = invoice.invoiceNumber,
                    issuedAt = timestamp,
                    sellerVatNumber = vatNumber,
                    totalWithVatSar = totalWithVatSar,
                    vatAmountSar = vatAmountSar,
                )

            val zatcaQrCode =
                zatcaQrService.generateQrCode(
                    sellerName = sellerName,
                    vatNumber = vatNumber,
                    timestamp = timestamp,
                    totalWithVatSar = totalWithVatSar,
                    vatAmountSar = vatAmountSar,
                )

            invoice.zatcaStatus = "generated"
            invoice.zatcaUuid = invoice.publicId.toString()
            invoice.zatcaHash = zatcaHash
            invoice.zatcaQrCode = zatcaQrCode
            invoice.previousInvoiceHash = previousHash
            invoice.invoiceCounterValue = counterValue
            invoiceRepository.save(invoice)
        }
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
            zatcaUuid = invoice.zatcaUuid,
            zatcaHash = invoice.zatcaHash,
            zatcaQrCode = invoice.zatcaQrCode,
            previousInvoiceHash = invoice.previousInvoiceHash,
            invoiceCounterValue = invoice.invoiceCounterValue,
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
