package com.liyaqa.payment.online.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class MoyasarWebhookVerifier(
    @Value("\${moyasar.webhook-secret}") private val secret: String,
) {
    fun verify(rawBody: ByteArray, signatureHeader: String): Boolean {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val computed = mac.doFinal(rawBody).joinToString("") { "%02x".format(it) }
        return computed == signatureHeader
    }
}
