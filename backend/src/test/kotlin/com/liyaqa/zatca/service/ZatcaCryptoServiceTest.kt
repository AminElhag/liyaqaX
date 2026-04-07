package com.liyaqa.zatca.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.Signature

class ZatcaCryptoServiceTest {
    private lateinit var service: ZatcaCryptoService

    @BeforeEach
    fun setUp() {
        service = ZatcaCryptoService()
    }

    @Test
    fun `generates valid secp256k1 key pair`() {
        val keyPair = service.generateKeyPair()

        assertThat(keyPair.public).isNotNull()
        assertThat(keyPair.private).isNotNull()
        assertThat(keyPair.public.algorithm).isEqualTo("EC")
    }

    @Test
    fun `exports and imports private key round-trip`() {
        val keyPair = service.generateKeyPair()
        val exported = service.exportPrivateKeyBase64(keyPair)

        val imported = service.importPrivateKeyFromBase64(exported)

        assertThat(imported.encoded).isEqualTo(keyPair.private.encoded)
    }

    @Test
    fun `builds CSR with correct subject fields`() {
        val keyPair = service.generateKeyPair()
        val csrPem =
            service.buildCsr(
                keyPair = keyPair,
                vatNumber = "300000000000003",
                egsSerialNumber = "1-Liyaqa|2-300000000000003|3-gym-test",
                organizationName = "Elixir Gym",
            )

        assertThat(csrPem).contains("BEGIN CERTIFICATE REQUEST")
        assertThat(csrPem).contains("END CERTIFICATE REQUEST")
    }

    @Test
    fun `signs and verifies data with generated key pair`() {
        val keyPair = service.generateKeyPair()
        val privateKeyBase64 = service.exportPrivateKeyBase64(keyPair)
        val data = "test data to sign".toByteArray()

        val signature = service.signData(privateKeyBase64, data)

        assertThat(signature).isNotEmpty()
        val verified = verifySignature(keyPair, data, signature)
        assertThat(verified).isTrue()
    }

    @Test
    fun `getPublicKeyDerBase64 returns valid base64`() {
        val keyPair = service.generateKeyPair()
        val publicKeyBase64 = service.getPublicKeyDerBase64(keyPair)

        assertThat(publicKeyBase64).isNotBlank()
        val decoded = java.util.Base64.getDecoder().decode(publicKeyBase64)
        assertThat(decoded).isNotEmpty()
    }

    private fun verifySignature(
        keyPair: KeyPair,
        data: ByteArray,
        signature: ByteArray,
    ): Boolean {
        val verifier = Signature.getInstance("SHA256withECDSA", "BC")
        verifier.initVerify(keyPair.public)
        verifier.update(data)
        return verifier.verify(signature)
    }
}
