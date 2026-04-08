package com.liyaqa.invoice

import com.liyaqa.club.Club
import com.liyaqa.member.Member
import com.liyaqa.organization.Organization
import com.liyaqa.payment.Payment
import com.liyaqa.zatca.ZatcaHashUtil
import com.liyaqa.zatca.ZatcaQrService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class InvoiceServiceTest {
    @Mock lateinit var invoiceRepository: InvoiceRepository

    @Mock lateinit var invoiceCounterRepository: InvoiceCounterRepository

    @Mock lateinit var zatcaQrService: ZatcaQrService

    @InjectMocks lateinit var service: InvoiceService

    private val org =
        Organization(
            nameAr = "مؤسسة لياقة التجريبية",
            nameEn = "Liyaqa Demo Org",
            email = "org@test.com",
            vatNumber = "300000000000003",
        )
    private val club =
        Club(
            organizationId = 1L,
            nameAr = "نادي إكسير",
            nameEn = "Elixir Gym",
            vatNumber = "300000000000003",
        )

    // ── VAT calculation ─────────────────────────────────────────────────────

    @Test
    fun `calculateVat computes 15 percent correctly`() {
        assertThat(service.calculateVat(15000)).isEqualTo(2250)
    }

    @Test
    fun `calculateVat rounds half up`() {
        assertThat(service.calculateVat(333)).isEqualTo(50)
    }

    @Test
    fun `calculateVat for one halala`() {
        assertThat(service.calculateVat(1)).isEqualTo(0)
    }

    @Test
    fun `calculateVat for large amount`() {
        assertThat(service.calculateVat(129900)).isEqualTo(19485)
    }

    // ── Invoice number generation ───────────────────────────────────────────

    @Test
    fun `generateInvoiceNumber follows format`() {
        whenever(invoiceRepository.countByClubIdAndYear(any(), any())).thenReturn(0L)

        val invoiceNumber = service.generateInvoiceNumber(club)

        assertThat(invoiceNumber).startsWith("INV-")
        assertThat(invoiceNumber).contains("-ELI-")
        assertThat(invoiceNumber).endsWith("-00001")
    }

    @Test
    fun `generateInvoiceNumber increments sequence`() {
        whenever(invoiceRepository.countByClubIdAndYear(any(), any())).thenReturn(5L)

        val invoiceNumber = service.generateInvoiceNumber(club)

        assertThat(invoiceNumber).endsWith("-00006")
    }

    // ── createInvoice with ZATCA Phase 1 ────────────────────────────────────

    @Test
    fun `createInvoice verifies total equals subtotal plus vat`() {
        stubCreateInvoiceDeps()

        val invoice = callCreateInvoice(15000)

        assertThat(invoice.subtotalHalalas).isEqualTo(15000)
        assertThat(invoice.vatAmountHalalas).isEqualTo(2250)
        assertThat(invoice.totalHalalas).isEqualTo(17250)
        assertThat(invoice.totalHalalas).isEqualTo(invoice.subtotalHalalas + invoice.vatAmountHalalas)
    }

    @Test
    fun `createInvoice sets zatcaStatus to generated`() {
        stubCreateInvoiceDeps()

        val invoice = callCreateInvoice(15000)

        assertThat(invoice.zatcaStatus).isEqualTo("generated")
    }

    @Test
    fun `createInvoice populates all ZATCA fields`() {
        stubCreateInvoiceDeps()

        val invoice = callCreateInvoice(15000)

        assertThat(invoice.zatcaUuid).isNotNull()
        assertThat(invoice.zatcaHash).isNotNull()
        assertThat(invoice.zatcaQrCode).isNotNull()
        assertThat(invoice.previousInvoiceHash).isNotNull()
        assertThat(invoice.invoiceCounterValue).isEqualTo(1L)
    }

    @Test
    fun `createInvoice uses INITIAL_PIH for first invoice`() {
        stubCreateInvoiceDeps()

        val invoice = callCreateInvoice(15000)

        assertThat(invoice.previousInvoiceHash).isEqualTo(ZatcaHashUtil.INITIAL_PIH)
    }

    @Test
    fun `createInvoice sets zatcaUuid to publicId`() {
        stubCreateInvoiceDeps()

        val invoice = callCreateInvoice(15000)

        assertThat(invoice.zatcaUuid).isEqualTo(invoice.publicId.toString())
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun stubCreateInvoiceDeps() {
        whenever(invoiceRepository.countByClubIdAndYear(any(), any())).thenReturn(0L)
        whenever(invoiceRepository.save(any<Invoice>())).thenAnswer { it.arguments[0] as Invoice }
        whenever(invoiceCounterRepository.incrementAndGet(any())).thenReturn(1L)
        whenever(
            invoiceRepository.findTopByClubIdAndInvoiceCounterValueIsNotNullOrderByInvoiceCounterValueDesc(any()),
        ).thenReturn(Optional.empty())
        whenever(zatcaQrService.resolveVatNumber(any(), any())).thenReturn("300000000000003")
        whenever(zatcaQrService.resolveSellerName(any())).thenReturn("مؤسسة لياقة التجريبية")
        whenever(zatcaQrService.formatSarAmount(any())).thenCallRealMethod()
        whenever(zatcaQrService.generateQrCode(any(), any(), any(), any(), any())).thenCallRealMethod()
        whenever(zatcaQrService.generateHash(any(), any(), any(), any(), any(), any())).thenCallRealMethod()
    }

    private fun callCreateInvoice(subtotalHalalas: Long): Invoice {
        val payment =
            Payment(
                organizationId = 1L,
                clubId = 1L,
                branchId = 1L,
                memberId = 1L,
                amountHalalas = subtotalHalalas,
                paymentMethod = "cash",
                collectedById = 1L,
            )
        val member =
            Member(
                organizationId = 1L,
                clubId = 1L,
                branchId = 1L,
                userId = 1L,
                firstNameAr = "أحمد",
                firstNameEn = "Ahmed",
                lastNameAr = "الرشيدي",
                lastNameEn = "Al-Rashidi",
                phone = "+966501234567",
            )
        return service.createInvoice(payment, member, club, org, subtotalHalalas)
    }
}
