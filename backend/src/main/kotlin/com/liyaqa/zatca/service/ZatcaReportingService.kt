package com.liyaqa.zatca.service

import com.liyaqa.invoice.InvoiceRepository
import com.liyaqa.zatca.client.ZatcaApiClient
import com.liyaqa.zatca.repository.ClubZatcaCertificateRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ZatcaReportingService(
    private val invoiceRepository: InvoiceRepository,
    private val certRepository: ClubZatcaCertificateRepository,
    private val xmlService: ZatcaXmlService,
    private val encryptionService: ZatcaEncryptionService,
    private val apiClient: ZatcaApiClient,
) {
    private val log = LoggerFactory.getLogger(ZatcaReportingService::class.java)
    private val maxRetries = 5

    @Transactional
    fun reportInvoice(invoiceId: Long) {
        val invoice =
            invoiceRepository.findById(invoiceId).orElseThrow {
                IllegalStateException("Invoice not found: $invoiceId")
            }

        if (invoice.zatcaStatus == "reported") return

        val cert =
            certRepository.findByClubIdAndDeletedAtIsNull(invoice.clubId)
                .orElse(null)

        if (cert == null || cert.onboardingStatus != "active") {
            invoice.zatcaStatus = "skipped"
            invoice.zatcaLastError = "Club not onboarded for ZATCA Phase 2"
            invoiceRepository.save(invoice)
            return
        }

        try {
            val privateKeyBase64 = encryptionService.decrypt(cert.privateKeyEncrypted)

            val signedXmlBase64 =
                xmlService.signInvoiceXml(
                    invoiceXml = invoice.zatcaQrCode ?: "",
                    invoiceHash = invoice.zatcaHash ?: "",
                    privateKeyBase64 = privateKeyBase64,
                    certificatePem = cert.certificatePem ?: "",
                )
            invoice.zatcaSignedXml = signedXmlBase64
            invoice.zatcaStatus = "signed"
            invoiceRepository.save(invoice)

            val response =
                apiClient.reportSimplifiedInvoice(
                    productionBinaryToken = cert.productionBinaryToken!!,
                    productionSecret = cert.productionSecret!!,
                    invoiceHash = invoice.zatcaHash ?: "",
                    uuid = invoice.zatcaUuid ?: invoice.publicId.toString(),
                    invoiceBase64 = signedXmlBase64,
                )

            val reportingStatus = response.get("reportingStatus")?.asText()
            val validationStatus = response.get("validationResults")?.get("status")?.asText()

            if (reportingStatus == "REPORTED" || validationStatus == "PASS") {
                invoice.zatcaStatus = "reported"
                invoice.zatcaReportedAt = Instant.now()
                invoice.zatcaReportResponse = response.toString()
                log.info("Invoice {} reported to ZATCA successfully", invoice.publicId)
            } else {
                throw RuntimeException("ZATCA returned non-success: ${response.toPrettyString()}")
            }
        } catch (ex: Exception) {
            invoice.zatcaRetryCount = invoice.zatcaRetryCount + 1
            invoice.zatcaLastError = ex.message?.take(1000)
            invoice.zatcaStatus = if (invoice.zatcaRetryCount >= maxRetries) "failed" else "generated"
            log.error("ZATCA reporting failed for invoice {}: {}", invoice.publicId, ex.message)
        }

        invoiceRepository.save(invoice)
    }
}
