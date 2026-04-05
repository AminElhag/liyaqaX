package com.liyaqa.zatca

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Base64

class ZatcaHashUtilTest {
    // ── Consistency ─────────────────────────────────────────────────────────

    @Test
    fun `same inputs produce the same hash`() {
        val hash1 =
            ZatcaHashUtil.hashInvoice(
                uuid = "550e8400-e29b-41d4-a716-446655440000",
                invoiceNumber = "INV-2025-ELI-00001",
                issuedAt = "2025-03-15T10:30:00Z",
                sellerVatNumber = "300000000000003",
                totalWithVatSar = "172.50",
                vatAmountSar = "22.50",
            )
        val hash2 =
            ZatcaHashUtil.hashInvoice(
                uuid = "550e8400-e29b-41d4-a716-446655440000",
                invoiceNumber = "INV-2025-ELI-00001",
                issuedAt = "2025-03-15T10:30:00Z",
                sellerVatNumber = "300000000000003",
                totalWithVatSar = "172.50",
                vatAmountSar = "22.50",
            )

        assertThat(hash1).isEqualTo(hash2)
    }

    // ── Different inputs produce different hashes ───────────────────────────

    @Test
    fun `different uuid produces different hash`() {
        val hash1 = hashWith(uuid = "aaaa-1111")
        val hash2 = hashWith(uuid = "bbbb-2222")

        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `different invoice number produces different hash`() {
        val hash1 = hashWith(invoiceNumber = "INV-001")
        val hash2 = hashWith(invoiceNumber = "INV-002")

        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `different amount produces different hash`() {
        val hash1 = hashWith(totalWithVatSar = "100.00")
        val hash2 = hashWith(totalWithVatSar = "200.00")

        assertThat(hash1).isNotEqualTo(hash2)
    }

    // ── Output format ───────────────────────────────────────────────────────

    @Test
    fun `hash output is valid base64`() {
        val hash = hashWith()

        val decoded = Base64.getDecoder().decode(hash)
        assertThat(decoded).hasSize(32) // SHA-256 = 32 bytes
    }

    // ── INITIAL_PIH ─────────────────────────────────────────────────────────

    @Test
    fun `INITIAL_PIH is valid base64`() {
        val decoded = Base64.getDecoder().decode(ZatcaHashUtil.INITIAL_PIH)
        assertThat(decoded).isNotEmpty()
    }

    @Test
    fun `INITIAL_PIH decodes to SHA-256 of zero`() {
        val decoded = String(Base64.getDecoder().decode(ZatcaHashUtil.INITIAL_PIH))
        assertThat(decoded).isEqualTo(
            "5feceb66ffc86f38d952786c6d696c79c2dbc239dd4e91b46729d73a27fb57e9",
        )
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private fun hashWith(
        uuid: String = "550e8400-e29b-41d4-a716-446655440000",
        invoiceNumber: String = "INV-2025-ELI-00001",
        issuedAt: String = "2025-03-15T10:30:00Z",
        sellerVatNumber: String = "300000000000003",
        totalWithVatSar: String = "172.50",
        vatAmountSar: String = "22.50",
    ): String =
        ZatcaHashUtil.hashInvoice(
            uuid = uuid,
            invoiceNumber = invoiceNumber,
            issuedAt = issuedAt,
            sellerVatNumber = sellerVatNumber,
            totalWithVatSar = totalWithVatSar,
            vatAmountSar = vatAmountSar,
        )
}
