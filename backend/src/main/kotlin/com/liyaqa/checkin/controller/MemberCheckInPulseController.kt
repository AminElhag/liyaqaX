package com.liyaqa.checkin.controller

import com.liyaqa.checkin.dto.CheckInRequest
import com.liyaqa.checkin.dto.CheckInResponse
import com.liyaqa.checkin.dto.RecentCheckInsResponse
import com.liyaqa.checkin.dto.TodayCountResponse
import com.liyaqa.checkin.service.MemberCheckInService
import com.liyaqa.common.exception.ArenaException
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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/pulse/check-in")
@Tag(name = "Check-In (Pulse)", description = "Member check-in endpoints — club operations")
@Validated
class MemberCheckInPulseController(
    private val checkInService: MemberCheckInService,
) {
    @PostMapping
    @PreAuthorize("hasPermission(null, 'check-in:create')")
    @Operation(summary = "Check in a member at the active branch")
    fun checkIn(
        @Valid @RequestBody request: CheckInRequest,
        authentication: Authentication,
    ): ResponseEntity<CheckInResponse> {
        val claims = authentication.pulseContext()
        val branchId = claims.requireFirstBranchId()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            checkInService.checkIn(
                memberPublicId = request.memberPublicId,
                method = request.method,
                actorUserPublicId = claims.requireUserPublicId(),
                branchPublicId = branchId,
                organizationPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
            ),
        )
    }

    @GetMapping("/today-count")
    @PreAuthorize("hasPermission(null, 'check-in:read')")
    @Operation(summary = "Today's check-in count for the active branch")
    fun todayCount(authentication: Authentication): ResponseEntity<TodayCountResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            checkInService.getTodayCount(
                branchPublicId = claims.requireFirstBranchId(),
                organizationPublicId = claims.requireOrganizationId(),
            ),
        )
    }

    @GetMapping("/recent")
    @PreAuthorize("hasPermission(null, 'check-in:read')")
    @Operation(summary = "Last 20 check-ins at the active branch")
    fun recent(authentication: Authentication): ResponseEntity<RecentCheckInsResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            checkInService.getRecent(
                branchPublicId = claims.requireFirstBranchId(),
                organizationPublicId = claims.requireOrganizationId(),
            ),
        )
    }
}

// ── Auth helpers ──────────────────────────────────────────────────────────────

private fun Authentication.pulseContext(): JwtClaims =
    details as? JwtClaims
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")

private fun JwtClaims.requireUserPublicId(): UUID =
    userPublicId
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Invalid user identity in token.")

private fun JwtClaims.requireOrganizationId(): UUID =
    organizationId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No organization scope in token.")

private fun JwtClaims.requireClubId(): UUID =
    clubId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")

private fun JwtClaims.requireFirstBranchId(): UUID =
    branchIds.firstOrNull()
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No branch assigned in token.")
