package com.liyaqa.payment.online.client

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.util.Base64

@Component
class MoyasarClient(
    @Value("\${moyasar.api-key}") private val apiKey: String,
    @Value("\${moyasar.publishable-key}") private val publishableKey: String,
    @Value("\${moyasar.callback-url-base}") private val callbackUrlBase: String,
    restTemplateBuilder: RestTemplateBuilder,
) {
    @Suppress("DEPRECATION")
    private val restTemplate: RestTemplate =
        restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(30))
            .build()

    private val baseUrl = "https://api.moyasar.com/v1"

    fun createPayment(request: MoyasarCreatePaymentRequest): MoyasarPaymentResponse {
        val body = mapOf(
            "amount" to request.amount,
            "currency" to request.currency,
            "description" to request.description,
            "publishable_api_key" to publishableKey,
            "callback_url" to request.callbackUrl,
            "source" to mapOf("type" to "creditcard"),
            "metadata" to request.metadata,
        )

        val entity = HttpEntity(body, authHeaders())
        val response = restTemplate.exchange(
            "$baseUrl/payments",
            HttpMethod.POST,
            entity,
            MoyasarPaymentResponse::class.java,
        )
        return response.body ?: throw RuntimeException("Empty response from Moyasar")
    }

    fun fetchPayment(moyasarId: String): MoyasarPaymentResponse {
        val entity = HttpEntity<Void>(authHeaders())
        val response = restTemplate.exchange(
            "$baseUrl/payments/$moyasarId",
            HttpMethod.GET,
            entity,
            MoyasarPaymentResponse::class.java,
        )
        return response.body ?: throw RuntimeException("Empty response from Moyasar")
    }

    fun buildCallbackUrl(moyasarId: String): String =
        "$callbackUrlBase/payment-callback?id=$moyasarId"

    private fun authHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val credentials = Base64.getEncoder().encodeToString("$apiKey:".toByteArray())
        headers.set("Authorization", "Basic $credentials")
        return headers
    }
}
