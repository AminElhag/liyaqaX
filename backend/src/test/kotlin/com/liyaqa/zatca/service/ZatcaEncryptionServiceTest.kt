package com.liyaqa.zatca.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Base64

class ZatcaEncryptionServiceTest {
    // 32-byte key encoded as Base64
    private val validKey = Base64.getEncoder().encodeToString("aaaabbbbccccddddeeeeffffgggghhhh".toByteArray())
    private val service = ZatcaEncryptionService(validKey)

    @Test
    fun `encrypts and decrypts private key correctly`() {
        val plaintext = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQ=="

        val encrypted = service.encrypt(plaintext)
        val decrypted = service.decrypt(encrypted)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun `different IV produces different ciphertext`() {
        val plaintext = "same plaintext data"

        val encrypted1 = service.encrypt(plaintext)
        val encrypted2 = service.encrypt(plaintext)

        assertThat(encrypted1).isNotEqualTo(encrypted2)

        // Both decrypt to the same value
        assertThat(service.decrypt(encrypted1)).isEqualTo(plaintext)
        assertThat(service.decrypt(encrypted2)).isEqualTo(plaintext)
    }

    @Test
    fun `throws on wrong key size`() {
        val shortKey = Base64.getEncoder().encodeToString("short16byteskey!".toByteArray())

        assertThatThrownBy { ZatcaEncryptionService(shortKey).encrypt("test") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("256 bits")
    }
}
