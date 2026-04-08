package com.liyaqa.payment.online.controller

import com.liyaqa.arena.arenaContext
import com.liyaqa.arena.requireClubId
import com.liyaqa.arena.requireMemberId
import com.liyaqa.payment.online.dto.InitiatePaymentRequest
import com.liyaqa.payment.online.dto.InitiatePaymentResponse
import com.liyaqa.payment.online.dto.PaymentStatusResponse
import com.liyaqa.payment.online.dto.TransactionHistoryResponse
import com.liyaqa.payment.online.service.OnlinePaymentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/arena/payments")
@Tag(name = "Arena Online Payments", description = "Member online payment operations")
@Validated
class OnlinePaymentArenaController(
    private val onlinePaymentService: OnlinePaymentService,
) {
    @PostMapping("/initiate")
    @Operation(summary = "Initiate a Moyasar payment for a membership")
    fun initiatePayment(
        @Valid @RequestBody request: InitiatePaymentRequest,
        authentication: Authentication,
    ): ResponseEntity<InitiatePaymentResponse> {
        val claims = authentication.arenaContext()
        val memberPublicId = claims.requireMemberId()
        val clubPublicId = claims.requireClubId()

        val response = onlinePaymentService.initiatePayment(
            memberPublicId = memberPublicId,
            membershipPublicId = request.membershipPublicId,
            clubPublicId = clubPublicId,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/{moyasarId}/status")
    @Operation(summary = "Get payment status by Moyasar ID")
    fun getPaymentStatus(
        @PathVariable moyasarId: String,
        authentication: Authentication,
    ): ResponseEntity<PaymentStatusResponse> {
        val claims = authentication.arenaContext()
        val memberPublicId = claims.requireMemberId()

        return ResponseEntity.ok(onlinePaymentService.getStatus(moyasarId, memberPublicId))
    }

    @GetMapping("/history")
    @Operation(summary = "Get member's online payment history")
    fun getPaymentHistory(
        authentication: Authentication,
    ): ResponseEntity<TransactionHistoryResponse> {
        val claims = authentication.arenaContext()
        val memberPublicId = claims.requireMemberId()

        return ResponseEntity.ok(onlinePaymentService.getMemberHistory(memberPublicId))
    }
}
