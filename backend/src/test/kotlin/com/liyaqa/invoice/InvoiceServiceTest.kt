package com.liyaqa.invoice

import com.liyaqa.club.Club
import com.liyaqa.member.Member
import com.liyaqa.payment.Payment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class InvoiceServiceTest {
    @Mock lateinit var invoiceRepository: InvoiceRepository

    @InjectMocks lateinit var service: InvoiceService

    // ── VAT calculation ─────────────────────────────────────────────────────

    @Test
    fun `calculateVat computes 15 percent correctly`() {
        assertThat(service.calculateVat(15000)).isEqualTo(2250)
    }

    @Test
    fun `calculateVat rounds half up`() {
        // 333 * 0.15 = 49.95 → rounds to 50
        assertThat(service.calculateVat(333)).isEqualTo(50)
    }

    @Test
    fun `calculateVat for one halala`() {
        // 1 * 0.15 = 0.15 → rounds to 0
        assertThat(service.calculateVat(1)).isEqualTo(0)
    }

    @Test
    fun `calculateVat for large amount`() {
        // 129900 * 0.15 = 19485
        assertThat(service.calculateVat(129900)).isEqualTo(19485)
    }

    // ── Invoice number generation ───────────────────────────────────────────

    @Test
    fun `generateInvoiceNumber follows format`() {
        val club =
            Club(
                organizationId = 1L,
                nameAr = "نادي إكسير",
                nameEn = "Elixir Gym",
            )
        whenever(invoiceRepository.countByClubIdAndYear(any(), any())).thenReturn(0L)

        val invoiceNumber = service.generateInvoiceNumber(club)

        assertThat(invoiceNumber).startsWith("INV-")
        assertThat(invoiceNumber).contains("-ELI-")
        assertThat(invoiceNumber).endsWith("-00001")
    }

    @Test
    fun `generateInvoiceNumber increments sequence`() {
        val club =
            Club(
                organizationId = 1L,
                nameAr = "نادي",
                nameEn = "Elixir Gym",
            )
        whenever(invoiceRepository.countByClubIdAndYear(any(), any())).thenReturn(5L)

        val invoiceNumber = service.generateInvoiceNumber(club)

        assertThat(invoiceNumber).endsWith("-00006")
    }

    // ── Invoice total integrity (Rule 8) ────────────────────────────────────

    @Test
    fun `createInvoiceStub verifies total equals subtotal plus vat`() {
        val payment =
            Payment(
                organizationId = 1L,
                clubId = 1L,
                branchId = 1L,
                memberId = 1L,
                amountHalalas = 15000,
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
        val club =
            Club(
                organizationId = 1L,
                nameAr = "نادي",
                nameEn = "Elixir Gym",
            )

        whenever(invoiceRepository.countByClubIdAndYear(any(), any())).thenReturn(0L)
        whenever(invoiceRepository.save(any<Invoice>())).thenAnswer { it.arguments[0] as Invoice }

        val invoice = service.createInvoiceStub(payment, member, club, 15000)

        assertThat(invoice.subtotalHalalas).isEqualTo(15000)
        assertThat(invoice.vatAmountHalalas).isEqualTo(2250)
        assertThat(invoice.totalHalalas).isEqualTo(17250)
        assertThat(invoice.totalHalalas).isEqualTo(invoice.subtotalHalalas + invoice.vatAmountHalalas)
    }
}
