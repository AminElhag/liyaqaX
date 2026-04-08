package com.liyaqa.payment.online.controller

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.liyaqa.payment.online.service.OnlinePaymentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhooks", description = "External webhook handlers")
class MoyasarWebhookController(
    private val onlinePaymentService: OnlinePaymentService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(MoyasarWebhookController::class.java)

    @PostMapping("/moyasar")
    @Operation(summary = "Moyasar payment webhook — public endpoint, HMAC-verified")
    fun handleMoyasarWebhook(
        @RequestBody rawBody: ByteArray,
        @RequestHeader("Moyasar-Signature", required = false) signature: String?,
    ): ResponseEntity<Void> {
        val sig = signature ?: ""

        try {
            val json: JsonNode = objectMapper.readTree(rawBody)
            val data = json.get("data") ?: json
            val moyasarId = data.get("id")?.asText() ?: ""
            val status = data.get("status")?.asText() ?: ""
            val sourceType = data.get("source")?.get("type")?.asText()

            onlinePaymentService.handleWebhook(rawBody, sig, moyasarId, status, sourceType)
        } catch (e: Exception) {
            log.error("Failed to process Moyasar webhook", e)
        }

        // Always return 200 to prevent Moyasar retries
        return ResponseEntity.ok().build()
    }
}
