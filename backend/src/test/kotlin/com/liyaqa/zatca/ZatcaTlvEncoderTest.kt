package com.liyaqa.zatca

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Base64

class ZatcaTlvEncoderTest {
    // ── Tag encoding ────────────────────────────────────────────────────────

    @Test
    fun `encodes all 5 TLV tags with correct tag bytes`() {
        val base64 =
            ZatcaTlvEncoder.generateQrCode(
                sellerName = "A",
                vatNumber = "B",
                timestamp = "C",
                totalWithVatSar = "D",
                vatAmountSar = "E",
            )

        val bytes = Base64.getDecoder().decode(base64)
        val tags = decodeTlvTags(bytes)

        assertThat(tags).hasSize(5)
        assertThat(tags[0].tag).isEqualTo(0x01.toByte())
        assertThat(tags[1].tag).isEqualTo(0x02.toByte())
        assertThat(tags[2].tag).isEqualTo(0x03.toByte())
        assertThat(tags[3].tag).isEqualTo(0x04.toByte())
        assertThat(tags[4].tag).isEqualTo(0x05.toByte())
    }

    @Test
    fun `each tag contains the correct value`() {
        val base64 =
            ZatcaTlvEncoder.generateQrCode(
                sellerName = "Elixir Gym",
                vatNumber = "300000000000003",
                timestamp = "2025-03-15T10:30:00Z",
                totalWithVatSar = "172.50",
                vatAmountSar = "22.50",
            )

        val bytes = Base64.getDecoder().decode(base64)
        val tags = decodeTlvTags(bytes)

        assertThat(tags[0].value).isEqualTo("Elixir Gym")
        assertThat(tags[1].value).isEqualTo("300000000000003")
        assertThat(tags[2].value).isEqualTo("2025-03-15T10:30:00Z")
        assertThat(tags[3].value).isEqualTo("172.50")
        assertThat(tags[4].value).isEqualTo("22.50")
    }

    // ── Arabic UTF-8 encoding ───────────────────────────────────────────────

    @Test
    fun `Arabic seller name encodes correctly as UTF-8`() {
        val arabicName = "\u0646\u0627\u062F\u064A \u0625\u0643\u0633\u064A\u0631"

        val base64 =
            ZatcaTlvEncoder.generateQrCode(
                sellerName = arabicName,
                vatNumber = "300000000000003",
                timestamp = "2025-03-15T10:30:00Z",
                totalWithVatSar = "172.50",
                vatAmountSar = "22.50",
            )

        val bytes = Base64.getDecoder().decode(base64)
        val tags = decodeTlvTags(bytes)

        assertThat(tags[0].value).isEqualTo(arabicName)
    }

    @Test
    fun `Arabic name TLV length is byte count not char count`() {
        // Arabic chars are multi-byte in UTF-8 (2 bytes each typically)
        val arabicName = "\u0646\u0627\u062F\u064A"
        val expectedByteLength = arabicName.toByteArray(Charsets.UTF_8).size

        val base64 =
            ZatcaTlvEncoder.generateQrCode(
                sellerName = arabicName,
                vatNumber = "1",
                timestamp = "1",
                totalWithVatSar = "1",
                vatAmountSar = "1",
            )

        val bytes = Base64.getDecoder().decode(base64)

        // First tag: byte[0] = 0x01, byte[1] = length
        assertThat(bytes[0]).isEqualTo(0x01.toByte())
        assertThat(bytes[1].toInt() and 0xFF).isEqualTo(expectedByteLength)
    }

    // ── Known input → known output ──────────────────────────────────────────

    @Test
    fun `known input produces deterministic base64 output`() {
        val result1 =
            ZatcaTlvEncoder.generateQrCode(
                sellerName = "Test",
                vatNumber = "123",
                timestamp = "2025-01-01T00:00:00Z",
                totalWithVatSar = "100.00",
                vatAmountSar = "15.00",
            )
        val result2 =
            ZatcaTlvEncoder.generateQrCode(
                sellerName = "Test",
                vatNumber = "123",
                timestamp = "2025-01-01T00:00:00Z",
                totalWithVatSar = "100.00",
                vatAmountSar = "15.00",
            )

        assertThat(result1).isEqualTo(result2)
        assertThat(result1).isNotBlank()
    }

    @Test
    fun `output is valid base64`() {
        val base64 =
            ZatcaTlvEncoder.generateQrCode(
                sellerName = "Test",
                vatNumber = "123",
                timestamp = "2025-01-01T00:00:00Z",
                totalWithVatSar = "100.00",
                vatAmountSar = "15.00",
            )

        val decoded = Base64.getDecoder().decode(base64)
        assertThat(decoded).isNotEmpty()
    }

    // ── Edge cases ──────────────────────────────────────────────────────────

    @Test
    fun `rejects value longer than 255 bytes`() {
        val longValue = "A".repeat(256)

        assertThatThrownBy {
            ZatcaTlvEncoder.generateQrCode(
                sellerName = longValue,
                vatNumber = "1",
                timestamp = "1",
                totalWithVatSar = "1",
                vatAmountSar = "1",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("TLV value too long")
    }

    // ── Helper ──────────────────────────────────────────────────────────────

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
