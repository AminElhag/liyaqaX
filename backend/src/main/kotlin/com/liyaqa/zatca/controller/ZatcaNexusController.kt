package com.liyaqa.zatca.controller

import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.nexus.nexusContext
import com.liyaqa.zatca.dto.OnboardingRequest
import com.liyaqa.zatca.dto.OnboardingStatusResponse
import com.liyaqa.zatca.repository.ClubZatcaCertificateRepository
import com.liyaqa.zatca.service.ZatcaOnboardingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/zatca")
@Tag(name = "ZATCA (Nexus)", description = "ZATCA Phase 2 onboarding management (platform admin)")
@Validated
class ZatcaNexusController(
    private val onboardingService: ZatcaOnboardingService,
    private val certRepository: ClubZatcaCertificateRepository,
    private val clubRepository: ClubRepository,
) {
    @PostMapping("/clubs/{clubPublicId}/onboard")
    @Operation(summary = "Onboard club to ZATCA Phase 2")
    @PreAuthorize("hasPermission(null, 'zatca:onboard')")
    fun onboardClub(
        @PathVariable clubPublicId: UUID,
        @Valid @RequestBody request: OnboardingRequest,
        authentication: Authentication,
    ): ResponseEntity<Map<String, String>> {
        authentication.nexusContext()
        onboardingService.onboardClub(clubPublicId, request.otp)
        return ResponseEntity.ok(mapOf("message" to "Club onboarded successfully to ZATCA Phase 2"))
    }

    @GetMapping("/clubs/{clubPublicId}/status")
    @Operation(summary = "Get ZATCA onboarding status for a club")
    @PreAuthorize("hasPermission(null, 'zatca:read')")
    fun getClubStatus(
        @PathVariable clubPublicId: UUID,
        authentication: Authentication,
    ): ResponseEntity<OnboardingStatusResponse> {
        authentication.nexusContext()
        val club =
            clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found") }
        val cert = certRepository.findByClubIdAndDeletedAtIsNull(club.id).orElse(null)
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

    @GetMapping("/clubs")
    @Operation(summary = "List all clubs with ZATCA status")
    @PreAuthorize("hasPermission(null, 'zatca:read')")
    fun listClubsZatcaStatus(authentication: Authentication): ResponseEntity<List<OnboardingStatusResponse>> {
        authentication.nexusContext()
        val certs = certRepository.findAll().filter { it.deletedAt == null }
        return ResponseEntity.ok(
            certs.map { cert ->
                OnboardingStatusResponse(
                    status = cert.onboardingStatus,
                    environment = cert.environment,
                    csidExpiresAt = cert.csidExpiresAt?.toString(),
                    onboardingStatus = cert.onboardingStatus,
                )
            },
        )
    }

    @PostMapping("/clubs/{clubPublicId}/renew")
    @Operation(summary = "Renew club CSID")
    @PreAuthorize("hasPermission(null, 'zatca:onboard')")
    fun renewClubCsid(
        @PathVariable clubPublicId: UUID,
        @Valid @RequestBody request: OnboardingRequest,
        authentication: Authentication,
    ): ResponseEntity<Map<String, String>> {
        authentication.nexusContext()
        onboardingService.renewClubCsid(clubPublicId, request.otp)
        return ResponseEntity.ok(mapOf("message" to "CSID renewed successfully"))
    }
}
