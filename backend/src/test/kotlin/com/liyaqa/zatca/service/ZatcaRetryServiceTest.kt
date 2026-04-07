package com.liyaqa.zatca.service

import com.liyaqa.audit.AuditService
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.invoice.Invoice
import com.liyaqa.invoice.InvoiceRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ZatcaRetryServiceTest {
    @Mock lateinit var invoiceRepository: InvoiceRepository

    @Mock lateinit var auditService: AuditService

    private lateinit var service: ZatcaRetryService

    @BeforeEach
    fun setUp() {
        service = ZatcaRetryService(invoiceRepository, auditService)
    }

    private fun testInvoice(
        status: String = "failed",
        retryCount: Int = 5,
        lastError: String? = "some error",
    ): Invoice {
        val invoice =
            Invoice(
                organizationId = 1L,
                clubId = 1L,
                branchId = 1L,
                memberId = 1L,
                paymentId = 1L,
                invoiceNumber = "INV-2026-001",
                subtotalHalalas = 15000L,
                vatAmountHalalas = 2250L,
                totalHalalas = 17250L,
            )
        invoice.zatcaStatus = status
        invoice.zatcaRetryCount = retryCount
        invoice.zatcaLastError = lastError
        return invoice
    }

    @Test
    fun `retryInvoice resets zatcaRetryCount to 0 and status to generated`() {
        val invoice = testInvoice()
        val invoicePublicId = invoice.publicId

        whenever(invoiceRepository.findByPublicId(invoicePublicId)).thenReturn(Optional.of(invoice))
        whenever(invoiceRepository.save(any<Invoice>())).thenAnswer { it.arguments[0] }

        service.retryInvoice(invoicePublicId)

        assertThat(invoice.zatcaRetryCount).isEqualTo(0)
        assertThat(invoice.zatcaStatus).isEqualTo("generated")
        verify(invoiceRepository).save(invoice)
    }

    @Test
    fun `retryInvoice throws NOT_FOUND when invoice does not exist`() {
        val fakeId = UUID.randomUUID()
        whenever(invoiceRepository.findByPublicId(fakeId)).thenReturn(Optional.empty())

        assertThatThrownBy { service.retryInvoice(fakeId) }
            .isInstanceOf(ArenaException::class.java)
            .matches { (it as ArenaException).status == HttpStatus.NOT_FOUND }
    }

    @Test
    fun `retryInvoice throws CONFLICT when invoice status is not failed`() {
        val invoice = testInvoice(status = "reported")
        whenever(invoiceRepository.findByPublicId(invoice.publicId)).thenReturn(Optional.of(invoice))

        assertThatThrownBy { service.retryInvoice(invoice.publicId) }
            .isInstanceOf(ArenaException::class.java)
            .matches { (it as ArenaException).status == HttpStatus.CONFLICT }
    }

    @Test
    fun `retryInvoice clears zatcaLastError`() {
        val invoice = testInvoice(lastError = "ZATCA rejected: invalid signature")
        whenever(invoiceRepository.findByPublicId(invoice.publicId)).thenReturn(Optional.of(invoice))
        whenever(invoiceRepository.save(any<Invoice>())).thenAnswer { it.arguments[0] }

        service.retryInvoice(invoice.publicId)

        assertThat(invoice.zatcaLastError).isNull()
    }

    @Test
    fun `retryAllFailedForClub resets all failed invoices for that club`() {
        val clubPublicId = UUID.randomUUID()
        val inv1 = testInvoice()
        val inv2 = testInvoice()

        val idField = inv1.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.setLong(inv1, 10L)
        idField.setLong(inv2, 20L)

        whenever(invoiceRepository.findFailedZatcaReportingByClub(clubPublicId)).thenReturn(listOf(10L, 20L))
        whenever(invoiceRepository.findById(10L)).thenReturn(Optional.of(inv1))
        whenever(invoiceRepository.findById(20L)).thenReturn(Optional.of(inv2))
        whenever(invoiceRepository.save(any<Invoice>())).thenAnswer { it.arguments[0] }

        service.retryAllFailedForClub(clubPublicId)

        assertThat(inv1.zatcaRetryCount).isEqualTo(0)
        assertThat(inv1.zatcaStatus).isEqualTo("generated")
        assertThat(inv1.zatcaLastError).isNull()
        assertThat(inv2.zatcaRetryCount).isEqualTo(0)
        assertThat(inv2.zatcaStatus).isEqualTo("generated")
        assertThat(inv2.zatcaLastError).isNull()
    }

    @Test
    fun `retryAllFailedForClub does nothing when no failed invoices`() {
        val clubPublicId = UUID.randomUUID()
        whenever(invoiceRepository.findFailedZatcaReportingByClub(clubPublicId)).thenReturn(emptyList())

        service.retryAllFailedForClub(clubPublicId)

        verify(auditService).logFromContext(any(), any(), any(), anyOrNull())
    }
}
