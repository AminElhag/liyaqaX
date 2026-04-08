package com.liyaqa.payment.online.controller

import com.liyaqa.common.exception.ArenaException
import com.liyaqa.payment.online.dto.TransactionHistoryResponse
import com.liyaqa.payment.online.service.OnlinePaymentService
import com.liyaqa.security.JwtClaims
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/pulse/members")
@Tag(name = "Pulse Online Payments", description = "Staff view of member online payments")
class OnlinePaymentPulseController(
    private val onlinePaymentService: OnlinePaymentService,
) {
    @GetMapping("/{memberId}/online-payments")
    @Operation(summary = "Get member's online payment history (staff view)")
    @PreAuthorize("hasPermission(null, 'online-payment:read')")
    fun getMemberOnlinePayments(
        @PathVariable memberId: UUID,
        authentication: Authentication,
    ): ResponseEntity<TransactionHistoryResponse> {
        val claims = authentication.details as? JwtClaims
            ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")
        val clubPublicId = claims.clubId
            ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")

        return ResponseEntity.ok(
            onlinePaymentService.getMemberHistoryForStaff(memberId, clubPublicId),
        )
    }
}
