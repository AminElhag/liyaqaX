package com.liyaqa.zatca.controller

import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.security.JwtClaims
import com.liyaqa.zatca.dto.OnboardingStatusResponse
import com.liyaqa.zatca.repository.ClubZatcaCertificateRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/pulse/zatca")
@Tag(name = "ZATCA (Pulse)", description = "ZATCA integration status for club staff")
class ZatcaPulseController(
    private val certRepository: ClubZatcaCertificateRepository,
    private val clubRepository: ClubRepository,
) {
    @GetMapping("/status")
    @Operation(summary = "Get this club's ZATCA integration status")
    @PreAuthorize("hasPermission(null, 'zatca:read')")
    fun getMyClubZatcaStatus(authentication: Authentication): ResponseEntity<OnboardingStatusResponse> {
        val clubId = authentication.resolveClubInternalId()
        val cert = certRepository.findByClubIdAndDeletedAtIsNull(clubId).orElse(null)
        return ResponseEntity.ok(
            if (cert == null) {
                OnboardingStatusResponse("not_onboarded", null, null, "pending")
            } else {
                OnboardingStatusResponse(
                    cert.onboardingStatus,
                    cert.environment,
                    cert.csidExpiresAt?.toString(),
                    cert.onboardingStatus,
                )
            },
        )
    }

    private fun Authentication.resolveClubInternalId(): Long {
        val claims =
            details as? JwtClaims
                ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")
        val clubPublicId =
            claims.clubId
                ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")
        val club =
            clubRepository.findAll().firstOrNull { it.publicId == clubPublicId }
                ?: throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.")
        return club.id
    }
}
