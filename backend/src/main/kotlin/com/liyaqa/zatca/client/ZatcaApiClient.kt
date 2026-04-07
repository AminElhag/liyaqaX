package com.liyaqa.zatca.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.util.Base64

@Component
class ZatcaApiClient(
    private val objectMapper: ObjectMapper,
    @Value("\${zatca.api.base-url}") private val baseUrl: String,
) {
    private val restTemplate = RestTemplate()

    fun issuanceComplianceCsid(
        csrBase64: String,
        otp: String,
    ): JsonNode {
        val headers = buildHeaders(null, null, otp)
        val body = mapOf("csr" to csrBase64)
        return post("$baseUrl/compliance", headers, body)
    }

    fun complianceInvoiceCheck(
        binarySecurityToken: String,
        secret: String,
        invoiceHash: String,
        uuid: String,
        invoiceBase64: String,
    ): JsonNode {
        val headers = buildHeaders(binarySecurityToken, secret, null)
        val body =
            mapOf(
                "invoiceHash" to invoiceHash,
                "uuid" to uuid,
                "invoice" to invoiceBase64,
            )
        return post("$baseUrl/compliance/invoices", headers, body)
    }

    fun issuanceProductionCsid(
        complianceBinaryToken: String,
        complianceSecret: String,
        complianceRequestId: String,
    ): JsonNode {
        val headers = buildHeaders(complianceBinaryToken, complianceSecret, null)
        val body = mapOf("compliance_request_id" to complianceRequestId)
        return post("$baseUrl/production/csids", headers, body)
    }

    fun renewProductionCsid(
        complianceBinaryToken: String,
        complianceSecret: String,
        otp: String,
        csrBase64: String,
    ): JsonNode {
        val headers = buildHeaders(complianceBinaryToken, complianceSecret, otp)
        val body = mapOf("csr" to csrBase64)
        return patch("$baseUrl/production/csids", headers, body)
    }

    fun reportSimplifiedInvoice(
        productionBinaryToken: String,
        productionSecret: String,
        invoiceHash: String,
        uuid: String,
        invoiceBase64: String,
    ): JsonNode {
        val headers = buildHeaders(productionBinaryToken, productionSecret, null)
        headers["Clearance-Status"] = listOf("0")
        val body =
            mapOf(
                "invoiceHash" to invoiceHash,
                "uuid" to uuid,
                "invoice" to invoiceBase64,
            )
        return post("$baseUrl/invoices/reporting/single", headers, body)
    }

    private fun buildHeaders(
        binaryToken: String?,
        secret: String?,
        otp: String?,
    ): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["Accept-Version"] = listOf("V2")
        headers["Accept-Language"] = listOf("en")

        if (binaryToken != null && secret != null) {
            val credentials = "$binaryToken:$secret"
            val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray())
            headers["Authorization"] = listOf("Basic $encoded")
        }
        if (otp != null) {
            headers["OTP"] = listOf(otp)
        }
        return headers
    }

    private fun post(
        url: String,
        headers: HttpHeaders,
        body: Any,
    ): JsonNode {
        val entity = HttpEntity(objectMapper.writeValueAsString(body), headers)
        val response = restTemplate.exchange(url, HttpMethod.POST, entity, String::class.java)
        return objectMapper.readTree(response.body)
    }

    private fun patch(
        url: String,
        headers: HttpHeaders,
        body: Any,
    ): JsonNode {
        val entity = HttpEntity(objectMapper.writeValueAsString(body), headers)
        val response = restTemplate.exchange(url, HttpMethod.PATCH, entity, String::class.java)
        return objectMapper.readTree(response.body)
    }
}
