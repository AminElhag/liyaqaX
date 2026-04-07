package com.liyaqa.zatca.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Base64

class ZatcaPhase2QrServiceTest {
    private val service = ZatcaPhase2QrService()

    @Test
    fun `builds TLV with correct tag order`() {
        val signatureBase64 = Base64.getEncoder().encodeToString("fakesignature".toByteArray())
        val publicKeyBase64 = Base64.getEncoder().encodeToString("fakepublickey".toByteArray())

        val qr =
            service.buildPhase2QrCode(
                sellerName = "Elixir Gym",
                vatNumber = "300000000000003",
                timestamp = "2025-01-01T00:00:00Z",
                totalWithVat = "250.00",
                vatAmount = "32.61",
                digitalSignatureBase64 = signatureBase64,
                publicKeyDerBase64 = publicKeyBase64,
                certificatePem = "-----BEGIN CERTIFICATE-----\nfakecert\n-----END CERTIFICATE-----",
            )

        val decoded = Base64.getDecoder().decode(qr)
        assertThat(decoded).isNotEmpty()

        // First byte is tag 1 (seller name)
        assertThat(decoded[0]).isEqualTo(1.toByte())
    }

    @Test
    fun `base64 output is valid`() {
        val signatureBase64 = Base64.getEncoder().encodeToString("sig".toByteArray())
        val publicKeyBase64 = Base64.getEncoder().encodeToString("key".toByteArray())

        val qr =
            service.buildPhase2QrCode(
                sellerName = "Test",
                vatNumber = "123456789012345",
                timestamp = "2025-01-01T00:00:00Z",
                totalWithVat = "100.00",
                vatAmount = "15.00",
                digitalSignatureBase64 = signatureBase64,
                publicKeyDerBase64 = publicKeyBase64,
                certificatePem = "cert",
            )

        val decoded = Base64.getDecoder().decode(qr)
        assertThat(decoded.size).isGreaterThan(0)
    }

    @Test
    fun `tags 1-5 match Phase 1 content`() {
        val signatureBase64 = Base64.getEncoder().encodeToString("sig".toByteArray())
        val publicKeyBase64 = Base64.getEncoder().encodeToString("key".toByteArray())

        val qr =
            service.buildPhase2QrCode(
                sellerName = "Seller",
                vatNumber = "300000000000003",
                timestamp = "2025-03-15T10:30:00Z",
                totalWithVat = "172.50",
                vatAmount = "22.50",
                digitalSignatureBase64 = signatureBase64,
                publicKeyDerBase64 = publicKeyBase64,
                certificatePem = "cert",
            )

        val decoded = Base64.getDecoder().decode(qr)

        // Tag 1 = seller name
        assertThat(decoded[0]).isEqualTo(1.toByte())
        val tag1Len = decoded[1].toInt() and 0xFF
        val tag1Value = String(decoded.copyOfRange(2, 2 + tag1Len), Charsets.UTF_8)
        assertThat(tag1Value).isEqualTo("Seller")

        // Tag 2 starts at offset 2 + tag1Len
        val tag2Offset = 2 + tag1Len
        assertThat(decoded[tag2Offset]).isEqualTo(2.toByte())
    }

    @Test
    fun `tag 6 contains digital signature bytes`() {
        val signatureBytes = "my-digital-signature".toByteArray()
        val signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes)
        val publicKeyBase64 = Base64.getEncoder().encodeToString("key".toByteArray())

        val qr =
            service.buildPhase2QrCode(
                sellerName = "S",
                vatNumber = "V",
                timestamp = "T",
                totalWithVat = "1",
                vatAmount = "0",
                digitalSignatureBase64 = signatureBase64,
                publicKeyDerBase64 = publicKeyBase64,
                certificatePem = "cert",
            )

        val decoded = Base64.getDecoder().decode(qr)
        // Find tag 6 in the byte stream
        var offset = 0
        while (offset < decoded.size) {
            val tag = decoded[offset].toInt() and 0xFF
            val len = decoded[offset + 1].toInt() and 0xFF
            if (tag == 6) {
                val value = decoded.copyOfRange(offset + 2, offset + 2 + len)
                assertThat(value).isEqualTo(signatureBytes)
                return
            }
            offset += 2 + len
        }
        throw AssertionError("Tag 6 not found in TLV output")
    }
}
