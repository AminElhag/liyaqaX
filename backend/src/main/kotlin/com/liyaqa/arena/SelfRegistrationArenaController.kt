package com.liyaqa.arena

import com.liyaqa.arena.dto.RegistrationCompleteResponse
import com.liyaqa.arena.dto.SelfRegistrationRequest
import com.liyaqa.auth.MemberAuthService
import com.liyaqa.auth.dto.RegistrationOtpRequestRequest
import com.liyaqa.auth.dto.RegistrationOtpVerifyRequest
import com.liyaqa.auth.dto.RegistrationOtpVerifyResponse
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.SelfRegistrationService
import com.liyaqa.portal.ClubPortalSettingsService
import com.liyaqa.security.JwtService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/arena/register")
@Tag(name = "Arena Self-Registration", description = "Member self-registration endpoints (public)")
@Validated
class SelfRegistrationArenaController(
    private val memberAuthService: MemberAuthService,
    private val selfRegistrationService: SelfRegistrationService,
    private val jwtService: JwtService,
    private val portalSettingsService: ClubPortalSettingsService,
    private val clubRepository: ClubRepository,
) {
    @GetMapping("/check")
    @Operation(summary = "Check if self-registration is enabled for a club")
    fun checkRegistrationEnabled(
        @RequestParam clubId: UUID,
    ): ResponseEntity<Map<String, Boolean>> {
        val club =
            clubRepository.findByPublicIdAndDeletedAtIsNull(clubId)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.")
                }
        val settings = portalSettingsService.getOrCreateSettings(club.id)
        return ResponseEntity.ok(mapOf("selfRegistrationEnabled" to settings.selfRegistrationEnabled))
    }

    @PostMapping("/otp/request")
    @Operation(summary = "Request an OTP for self-registration phone verification")
    fun requestOtp(
        @Valid @RequestBody request: RegistrationOtpRequestRequest,
    ): ResponseEntity<Void> {
        memberAuthService.requestRegistrationOtp(request)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify OTP and receive a registration token")
    fun verifyOtp(
        @Valid @RequestBody request: RegistrationOtpVerifyRequest,
    ): ResponseEntity<RegistrationOtpVerifyResponse> = ResponseEntity.ok(memberAuthService.verifyRegistrationOtp(request))

    @PostMapping("/complete")
    @Operation(summary = "Complete self-registration with profile data and optional plan selection")
    fun complete(
        @RequestHeader("Authorization") authHeader: String,
        @Valid @RequestBody request: SelfRegistrationRequest,
    ): ResponseEntity<RegistrationCompleteResponse> {
        val token = authHeader.removePrefix("Bearer ").trim()
        val claims =
            try {
                jwtService.parseRegistrationToken(token)
            } catch (e: Exception) {
                throw ArenaException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized",
                    "Invalid or expired registration token.",
                )
            }

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(selfRegistrationService.register(claims, request))
    }
}
