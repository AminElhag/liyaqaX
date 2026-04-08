package com.liyaqa.payment.online.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MoyasarWebhookVerifierTest {
    private val secret = "test-webhook-secret"
    private val verifier = MoyasarWebhookVerifier(secret)

    private fun computeHmac(body: ByteArray, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `verify returns true for correct signature`() {
        val body = """{"id":"pay_123","status":"paid"}""".toByteArray()
        val signature = computeHmac(body, secret)

        assertThat(verifier.verify(body, signature)).isTrue()
    }

    @Test
    fun `verify returns false for tampered body`() {
        val body = """{"id":"pay_123","status":"paid"}""".toByteArray()
        val signature = computeHmac(body, secret)
        val tamperedBody = """{"id":"pay_123","status":"failed"}""".toByteArray()

        assertThat(verifier.verify(tamperedBody, signature)).isFalse()
    }

    @Test
    fun `verify returns false for wrong secret`() {
        val body = """{"id":"pay_123","status":"paid"}""".toByteArray()
        val wrongSignature = computeHmac(body, "wrong-secret")

        assertThat(verifier.verify(body, wrongSignature)).isFalse()
    }
}
