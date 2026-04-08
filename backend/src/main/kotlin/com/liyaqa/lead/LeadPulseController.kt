package com.liyaqa.lead

import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.lead.dto.ConvertLeadRequest
import com.liyaqa.lead.dto.CreateLeadNoteRequest
import com.liyaqa.lead.dto.CreateLeadRequest
import com.liyaqa.lead.dto.LeadNoteResponse
import com.liyaqa.lead.dto.LeadResponse
import com.liyaqa.lead.dto.LeadSummaryResponse
import com.liyaqa.lead.dto.StageTransitionRequest
import com.liyaqa.lead.dto.UpdateLeadRequest
import com.liyaqa.security.JwtClaims
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "Leads (Pulse)", description = "Lead pipeline management endpoints — club operations")
@Validated
class LeadPulseController(
    private val leadService: LeadService,
) {
    @PostMapping
    @PreAuthorize("hasPermission(null, 'lead:create')")
    @Operation(summary = "Create a new lead")
    fun create(
        @Valid @RequestBody request: CreateLeadRequest,
        authentication: Authentication,
    ): ResponseEntity<LeadResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            leadService.createLead(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                staffPublicId = claims.requireUserPublicId(),
                request = request,
            ),
        )
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'lead:read')")
    @Operation(summary = "List leads for the caller's club")
    fun list(
        @PageableDefault(size = 20) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<LeadSummaryResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            leadService.listLeads(claims.requireOrganizationId(), claims.requireClubId(), pageable),
        )
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'lead:read')")
    @Operation(summary = "Get a lead by ID")
    fun getById(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<LeadResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            leadService.getLead(claims.requireOrganizationId(), claims.requireClubId(), id),
        )
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'lead:update')")
    @Operation(summary = "Update a lead's information")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateLeadRequest,
        authentication: Authentication,
    ): ResponseEntity<LeadResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            leadService.updateLead(claims.requireOrganizationId(), claims.requireClubId(), id, request),
        )
    }

    @PatchMapping("/{id}/stage")
    @PreAuthorize("hasPermission(null, 'lead:update')")
    @Operation(summary = "Move a lead to a different stage")
    fun moveStage(
        @PathVariable id: UUID,
        @Valid @RequestBody request: StageTransitionRequest,
        authentication: Authentication,
    ): ResponseEntity<LeadResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            leadService.moveStage(claims.requireOrganizationId(), claims.requireClubId(), id, request),
        )
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize("hasPermission(null, 'lead:read')")
    @Operation(summary = "Add a note to a lead")
    fun addNote(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateLeadNoteRequest,
        authentication: Authentication,
    ): ResponseEntity<LeadNoteResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            leadService.addNote(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                leadPublicId = id,
                staffPublicId = claims.requireUserPublicId(),
                request = request,
            ),
        )
    }

    @GetMapping("/{id}/notes")
    @PreAuthorize("hasPermission(null, 'lead:read')")
    @Operation(summary = "List notes for a lead")
    fun listNotes(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<List<LeadNoteResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            leadService.listNotes(claims.requireOrganizationId(), claims.requireClubId(), id),
        )
    }

    @PostMapping("/{id}/convert")
    @PreAuthorize("hasPermission(null, 'lead:convert')")
    @Operation(summary = "Convert a lead to a member")
    fun convert(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ConvertLeadRequest,
        authentication: Authentication,
    ): ResponseEntity<LeadResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            leadService.convertLead(claims.requireOrganizationId(), claims.requireClubId(), id, request),
        )
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'lead:delete')")
    @Operation(summary = "Soft-delete a lead")
    fun delete(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        leadService.deleteLead(claims.requireOrganizationId(), claims.requireClubId(), id)
        return ResponseEntity.noContent().build()
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

private fun JwtClaims.requireUserPublicId(): UUID =
    userPublicId
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "User identity missing from token.")
