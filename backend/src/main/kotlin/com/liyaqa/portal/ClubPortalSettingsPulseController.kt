package com.liyaqa.portal

import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.portal.dto.ClubPortalSettingsResponse
import com.liyaqa.portal.dto.UpdatePortalSettingsRequest
import com.liyaqa.security.JwtClaims
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/portal-settings")
@Tag(name = "Portal Settings (Pulse)", description = "Club portal settings management")
@Validated
class ClubPortalSettingsPulseController(
    private val settingsService: ClubPortalSettingsService,
    private val clubRepository: ClubRepository,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'portal-settings:update')")
    @Operation(summary = "Get portal settings for the caller's club")
    fun getSettings(authentication: Authentication): ResponseEntity<ClubPortalSettingsResponse> {
        val clubId = authentication.resolveClubInternalId()
        return ResponseEntity.ok(settingsService.getSettings(clubId))
    }

    @PatchMapping
    @PreAuthorize("hasPermission(null, 'portal-settings:update')")
    @Operation(summary = "Update portal feature flags and message")
    fun updateSettings(
        @Valid @RequestBody request: UpdatePortalSettingsRequest,
        authentication: Authentication,
    ): ResponseEntity<ClubPortalSettingsResponse> {
        val clubId = authentication.resolveClubInternalId()
        return ResponseEntity.ok(settingsService.updateSettings(clubId, request))
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
