package com.liyaqa.auth

import com.liyaqa.auth.dto.OtpRequestRequest
import com.liyaqa.auth.dto.OtpVerifyRequest
import com.liyaqa.auth.dto.OtpVerifyResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/arena/auth")
@Tag(name = "Arena Auth", description = "Member OTP authentication endpoints")
@Validated
class MemberArenaController(
    private val memberAuthService: MemberAuthService,
) {
    @PostMapping("/otp/request")
    @Operation(summary = "Request an OTP for phone-based login")
    fun requestOtp(
        @Valid @RequestBody request: OtpRequestRequest,
    ): ResponseEntity<Void> {
        memberAuthService.requestOtp(request)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify OTP and receive an access token")
    fun verifyOtp(
        @Valid @RequestBody request: OtpVerifyRequest,
    ): ResponseEntity<OtpVerifyResponse> = ResponseEntity.ok(memberAuthService.verifyOtp(request))
}
