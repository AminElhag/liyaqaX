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
import java.util.Base64
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class InvoiceZatcaTest {
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

    // ── QR code decodes to 5 correct TLV tags ──────────────────────────────

    @Test
    fun `invoice QR code decodes to exactly 5 TLV tags`() {
        stubDeps()
        val invoice = createInvoice(15000)

        val bytes = Base64.getDecoder().decode(invoice.zatcaQrCode)
        val tags = decodeTlvTags(bytes)

        assertThat(tags).hasSize(5)
        assertThat(tags[0].tag).isEqualTo(0x01.toByte())
        assertThat(tags[1].tag).isEqualTo(0x02.toByte())
        assertThat(tags[2].tag).isEqualTo(0x03.toByte())
        assertThat(tags[3].tag).isEqualTo(0x04.toByte())
        assertThat(tags[4].tag).isEqualTo(0x05.toByte())
    }

    @Test
    fun `QR tag 01 contains seller name in Arabic`() {
        stubDeps()
        val invoice = createInvoice(15000)

        val tags = decodeTlvTags(Base64.getDecoder().decode(invoice.zatcaQrCode))

        assertThat(tags[0].value).isEqualTo("مؤسسة لياقة التجريبية")
    }

    @Test
    fun `QR tag 02 contains VAT number`() {
        stubDeps()
        val invoice = createInvoice(15000)

        val tags = decodeTlvTags(Base64.getDecoder().decode(invoice.zatcaQrCode))

        assertThat(tags[1].value).isEqualTo("300000000000003")
    }

    @Test
    fun `QR tag 04 contains total with VAT in SAR`() {
        stubDeps()
        val invoice = createInvoice(15000)

        val tags = decodeTlvTags(Base64.getDecoder().decode(invoice.zatcaQrCode))

        assertThat(tags[3].value).isEqualTo("172.50")
    }

    @Test
    fun `QR tag 05 contains VAT amount in SAR`() {
        stubDeps()
        val invoice = createInvoice(15000)

        val tags = decodeTlvTags(Base64.getDecoder().decode(invoice.zatcaQrCode))

        assertThat(tags[4].value).isEqualTo("22.50")
    }

    // ── Counter increments correctly ────────────────────────────────────────

    @Test
    fun `counter increments sequentially across invoices`() {
        stubDeps()
        whenever(invoiceCounterRepository.incrementAndGet(any()))
            .thenReturn(1L, 2L, 3L)

        val invoice1 = createInvoice(10000)
        val invoice2 = createInvoice(20000)
        val invoice3 = createInvoice(30000)

        assertThat(invoice1.invoiceCounterValue).isEqualTo(1L)
        assertThat(invoice2.invoiceCounterValue).isEqualTo(2L)
        assertThat(invoice3.invoiceCounterValue).isEqualTo(3L)
    }

    // ── PIH chain is correct ────────────────────────────────────────────────

    @Test
    fun `first invoice PIH is INITIAL_PIH`() {
        stubDeps()
        val invoice = createInvoice(15000)

        assertThat(invoice.previousInvoiceHash).isEqualTo(ZatcaHashUtil.INITIAL_PIH)
    }

    @Test
    fun `second invoice PIH equals first invoice hash`() {
        stubDeps()
        val first = createInvoice(15000)

        // For second invoice, repo returns the first invoice as the latest
        whenever(
            invoiceRepository.findTopByClubIdAndInvoiceCounterValueIsNotNullOrderByInvoiceCounterValueDesc(any()),
        ).thenReturn(Optional.of(first))
        whenever(invoiceCounterRepository.incrementAndGet(any())).thenReturn(2L)

        val second = createInvoice(20000)

        assertThat(second.previousInvoiceHash).isEqualTo(first.zatcaHash)
    }

    @Test
    fun `PIH chain links three invoices correctly`() {
        stubDeps()
        val first = createInvoice(10000)

        whenever(
            invoiceRepository.findTopByClubIdAndInvoiceCounterValueIsNotNullOrderByInvoiceCounterValueDesc(any()),
        ).thenReturn(Optional.of(first))
        whenever(invoiceCounterRepository.incrementAndGet(any())).thenReturn(2L)
        val second = createInvoice(20000)

        whenever(
            invoiceRepository.findTopByClubIdAndInvoiceCounterValueIsNotNullOrderByInvoiceCounterValueDesc(any()),
        ).thenReturn(Optional.of(second))
        whenever(invoiceCounterRepository.incrementAndGet(any())).thenReturn(3L)
        val third = createInvoice(30000)

        assertThat(first.previousInvoiceHash).isEqualTo(ZatcaHashUtil.INITIAL_PIH)
        assertThat(second.previousInvoiceHash).isEqualTo(first.zatcaHash)
        assertThat(third.previousInvoiceHash).isEqualTo(second.zatcaHash)

        // Each hash is unique
        assertThat(first.zatcaHash).isNotEqualTo(second.zatcaHash)
        assertThat(second.zatcaHash).isNotEqualTo(third.zatcaHash)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun stubDeps() {
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

    private fun createInvoice(subtotalHalalas: Long): Invoice {
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

    private data class TlvTag(val tag: Byte, val value: String)

    private fun decodeTlvTags(bytes: ByteArray): List<TlvTag> {
        val tags = mutableListOf<TlvTag>()
        var offset = 0
        while (offset < bytes.size) {
            val tag = bytes[offset]
            val length = bytes[offset + 1].toInt() and 0xFF
            val value = String(bytes, offset + 2, length, Charsets.UTF_8)
            tags.add(TlvTag(tag, value))
            offset += 2 + length
        }
        return tags
    }
}
