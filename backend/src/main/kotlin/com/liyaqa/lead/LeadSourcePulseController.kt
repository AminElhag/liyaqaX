package com.liyaqa.lead

import com.liyaqa.common.exception.ArenaException
import com.liyaqa.lead.dto.CreateLeadSourceRequest
import com.liyaqa.lead.dto.LeadSourceResponse
import com.liyaqa.lead.dto.UpdateLeadSourceRequest
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/lead-sources")
@Tag(name = "Lead Sources (Pulse)", description = "Lead source management endpoints — club operations")
@Validated
class LeadSourcePulseController(
    private val leadSourceService: LeadSourceService,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'lead-source:read')")
    @Operation(summary = "List lead sources for the caller's club")
    fun list(authentication: Authentication): ResponseEntity<List<LeadSourceResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            leadSourceService.listByClub(claims.requireOrganizationId(), claims.requireClubId()),
        )
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'lead-source:create')")
    @Operation(summary = "Create a new lead source")
    fun create(
        @Valid @RequestBody request: CreateLeadSourceRequest,
        authentication: Authentication,
    ): ResponseEntity<LeadSourceResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            leadSourceService.create(claims.requireOrganizationId(), claims.requireClubId(), request),
        )
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'lead-source:update')")
    @Operation(summary = "Update a lead source's name, color, or order")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateLeadSourceRequest,
        authentication: Authentication,
    ): ResponseEntity<LeadSourceResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            leadSourceService.update(claims.requireOrganizationId(), claims.requireClubId(), id, request),
        )
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasPermission(null, 'lead-source:update')")
    @Operation(summary = "Toggle a lead source active/inactive")
    fun toggle(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<LeadSourceResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            leadSourceService.toggleActive(claims.requireOrganizationId(), claims.requireClubId(), id),
        )
    }
}

// ── Auth helpers ─────────────────────────────────────────────────────────────

private fun Authentication.pulseContext(): JwtClaims =
    details as? JwtClaims
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")

private fun JwtClaims.requireOrganizationId(): UUID =
    organizationId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No organization scope in token.")

private fun JwtClaims.requireClubId(): UUID =
    clubId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")
