package com.liyaqa.zatca.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class ZatcaEncryptionService(
    @Value("\${zatca.encryption.key}") private val encryptionKeyBase64: String,
) {
    private val gcmTagLength = 128
    private val gcmIvLength = 12

    private fun getKey(): SecretKeySpec {
        val keyBytes = Base64.getDecoder().decode(encryptionKeyBase64)
        require(keyBytes.size == 32) { "ZATCA encryption key must be 256 bits (32 bytes)" }
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(gcmIvLength).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey(), GCMParameterSpec(gcmTagLength, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        val combined = iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encrypted: String): String {
        val combined = Base64.getDecoder().decode(encrypted)
        val iv = combined.copyOfRange(0, gcmIvLength)
        val ciphertext = combined.copyOfRange(gcmIvLength, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(gcmTagLength, iv))
        return String(cipher.doFinal(ciphertext))
    }
}
