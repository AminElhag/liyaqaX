package com.liyaqa.zatca.service

import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

@Service
class ZatcaPhase2QrService {
    fun buildPhase2QrCode(
        sellerName: String,
        vatNumber: String,
        timestamp: String,
        totalWithVat: String,
        vatAmount: String,
        digitalSignatureBase64: String,
        publicKeyDerBase64: String,
        certificatePem: String,
    ): String {
        val baos = ByteArrayOutputStream()

        // Tag 1: Seller name
        writeTlv(baos, 1, sellerName.toByteArray(Charsets.UTF_8))
        // Tag 2: VAT Registration Number
        writeTlv(baos, 2, vatNumber.toByteArray(Charsets.UTF_8))
        // Tag 3: Timestamp
        writeTlv(baos, 3, timestamp.toByteArray(Charsets.UTF_8))
        // Tag 4: Invoice total with VAT
        writeTlv(baos, 4, totalWithVat.toByteArray(Charsets.UTF_8))
        // Tag 5: VAT amount
        writeTlv(baos, 5, vatAmount.toByteArray(Charsets.UTF_8))
        // Tag 6: Digital signature (ECDSA)
        writeTlv(baos, 6, Base64.getDecoder().decode(digitalSignatureBase64))
        // Tag 7: Public key (DER)
        writeTlv(baos, 7, Base64.getDecoder().decode(publicKeyDerBase64))
        // Tag 8: Certificate SHA-256 hash
        val certBytes = certificatePem.toByteArray()
        val certHash = MessageDigest.getInstance("SHA-256").digest(certBytes)
        writeTlv(baos, 8, certHash)

        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    private fun writeTlv(
        baos: ByteArrayOutputStream,
        tag: Int,
        value: ByteArray,
    ) {
        baos.write(tag)
        baos.write(value.size)
        baos.write(value)
    }
}
