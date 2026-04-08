package com.liyaqa.zatca.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.liyaqa.invoice.Invoice
import com.liyaqa.invoice.InvoiceRepository
import com.liyaqa.zatca.client.ZatcaApiClient
import com.liyaqa.zatca.entity.ClubZatcaCertificate
import com.liyaqa.zatca.repository.ClubZatcaCertificateRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class ZatcaReportingServiceTest {
    @Mock lateinit var invoiceRepository: InvoiceRepository

    @Mock lateinit var certRepository: ClubZatcaCertificateRepository

    @Mock lateinit var xmlService: ZatcaXmlService

    @Mock lateinit var encryptionService: ZatcaEncryptionService

    @Mock lateinit var apiClient: ZatcaApiClient

    private lateinit var service: ZatcaReportingService

    @BeforeEach
    fun setUp() {
        service =
            ZatcaReportingService(
                invoiceRepository, certRepository, xmlService, encryptionService, apiClient,
            )
    }

    @Test
    fun `skips invoice when club has no active CSID`() {
        val invoice = testInvoice(zatcaStatus = "generated")
        whenever(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice))
        whenever(certRepository.findByClubIdAndDeletedAtIsNull(invoice.clubId)).thenReturn(Optional.empty())
        whenever(invoiceRepository.save(any<Invoice>())).thenAnswer { it.arguments[0] }

        service.reportInvoice(1L)

        assertThat(invoice.zatcaStatus).isEqualTo("skipped")
        assertThat(invoice.zatcaLastError).contains("not onboarded")
    }

    @Test
    fun `does not re-report already-reported invoices`() {
        val invoice = testInvoice(zatcaStatus = "reported")
        whenever(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice))

        service.reportInvoice(1L)

        assertThat(invoice.zatcaStatus).isEqualTo("reported")
    }

    @Test
    fun `marks invoice as reported on success`() {
        val invoice = testInvoice(zatcaStatus = "generated")
        val cert = testCert()
        val objectMapper = ObjectMapper()

        whenever(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice))
        whenever(certRepository.findByClubIdAndDeletedAtIsNull(invoice.clubId)).thenReturn(Optional.of(cert))
        whenever(encryptionService.decrypt(any())).thenReturn("fakePrivateKeyBase64")
        whenever(xmlService.signInvoiceXml(any(), any(), any(), any())).thenReturn("signedXmlBase64")
        whenever(invoiceRepository.save(any<Invoice>())).thenAnswer { it.arguments[0] }
        whenever(apiClient.reportSimplifiedInvoice(any(), any(), any(), any(), any()))
            .thenReturn(objectMapper.readTree("""{"reportingStatus": "REPORTED", "validationResults": {"status": "PASS"}}"""))

        service.reportInvoice(1L)

        assertThat(invoice.zatcaStatus).isEqualTo("reported")
        assertThat(invoice.zatcaReportedAt).isNotNull()
    }

    @Test
    fun `increments retry count on API failure`() {
        val invoice = testInvoice(zatcaStatus = "generated")
        val cert = testCert()

        whenever(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice))
        whenever(certRepository.findByClubIdAndDeletedAtIsNull(invoice.clubId)).thenReturn(Optional.of(cert))
        whenever(encryptionService.decrypt(any())).thenThrow(RuntimeException("decrypt failed"))
        whenever(invoiceRepository.save(any<Invoice>())).thenAnswer { it.arguments[0] }

        service.reportInvoice(1L)

        assertThat(invoice.zatcaRetryCount).isEqualTo(1)
        assertThat(invoice.zatcaStatus).isEqualTo("generated")
        assertThat(invoice.zatcaLastError).contains("decrypt failed")
    }

    @Test
    fun `marks as failed when retry count reaches MAX_RETRIES`() {
        val invoice = testInvoice(zatcaStatus = "generated", retryCount = 4)
        val cert = testCert()

        whenever(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice))
        whenever(certRepository.findByClubIdAndDeletedAtIsNull(invoice.clubId)).thenReturn(Optional.of(cert))
        whenever(encryptionService.decrypt(any())).thenThrow(RuntimeException("persistent failure"))
        whenever(invoiceRepository.save(any<Invoice>())).thenAnswer { it.arguments[0] }

        service.reportInvoice(1L)

        assertThat(invoice.zatcaRetryCount).isEqualTo(5)
        assertThat(invoice.zatcaStatus).isEqualTo("failed")
    }

    private fun testInvoice(
        zatcaStatus: String,
        retryCount: Int = 0,
    ): Invoice {
        val invoice =
            Invoice(
                organizationId = 1L,
                clubId = 1L,
                branchId = 1L,
                memberId = 1L,
                paymentId = 1L,
                invoiceNumber = "INV-TEST-001",
                subtotalHalalas = 15000L,
                vatAmountHalalas = 2250L,
                totalHalalas = 17250L,
                zatcaStatus = zatcaStatus,
                zatcaHash = "testhash",
                zatcaUuid = "test-uuid",
            )
        invoice.zatcaRetryCount = retryCount
        return invoice
    }

    private fun testCert(): ClubZatcaCertificate =
        ClubZatcaCertificate(
            clubId = 1L,
            environment = "sandbox",
        ).apply {
            onboardingStatus = "active"
            privateKeyEncrypted = "encryptedKey"
            productionBinaryToken = "token"
            productionSecret = "secret"
            certificatePem = "certPem"
        }
}
