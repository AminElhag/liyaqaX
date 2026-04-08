package com.liyaqa.checkin.controller

import com.liyaqa.arena.arenaContext
import com.liyaqa.arena.requireMemberId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/arena")
@Tag(name = "Arena Check-In QR", description = "Member QR code for check-in")
class MemberCheckInArenaController {
    @GetMapping("/me/qr")
    @Operation(summary = "Get the member's check-in QR code value")
    fun getQrValue(authentication: Authentication): ResponseEntity<Map<String, String>> {
        val claims = authentication.arenaContext()
        val memberId = claims.requireMemberId()
        return ResponseEntity.ok(mapOf("qrValue" to memberId.toString()))
    }
}
