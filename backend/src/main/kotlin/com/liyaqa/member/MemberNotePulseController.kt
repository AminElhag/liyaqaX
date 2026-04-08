package com.liyaqa.member

import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.dto.CreateNoteRequest
import com.liyaqa.member.dto.FollowUpResponse
import com.liyaqa.member.dto.NoteResponse
import com.liyaqa.member.dto.TimelineResponse
import com.liyaqa.security.JwtClaims
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/pulse")
@Tag(name = "Member Notes (Pulse)", description = "Member notes and activity timeline — club operations")
@Validated
class MemberNotePulseController(
    private val memberNoteService: MemberNoteService,
) {
    @PostMapping("/members/{memberPublicId}/notes")
    @PreAuthorize("hasPermission(null, 'member-note:create')")
    @Operation(summary = "Create a new note for a member")
    fun createNote(
        @PathVariable memberPublicId: UUID,
        @Valid @RequestBody request: CreateNoteRequest,
        authentication: Authentication,
    ): ResponseEntity<NoteResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            memberNoteService.createNote(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                memberPublicId = memberPublicId,
                request = request,
                actorUserPublicId = claims.requireUserPublicId(),
                isTrainerScope = false,
            ),
        )
    }

    @DeleteMapping("/members/{memberPublicId}/notes/{notePublicId}")
    @PreAuthorize("hasPermission(null, 'member-note:delete')")
    @Operation(summary = "Soft-delete a member note")
    fun deleteNote(
        @PathVariable memberPublicId: UUID,
        @PathVariable notePublicId: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        memberNoteService.deleteNote(
            notePublicId = notePublicId,
            actorUserPublicId = claims.requireUserPublicId(),
        )
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/members/{memberPublicId}/timeline")
    @PreAuthorize("hasPermission(null, 'member-note:read')")
    @Operation(summary = "Get combined activity timeline for a member")
    fun getTimeline(
        @PathVariable memberPublicId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false, defaultValue = "20") limit: Int,
        authentication: Authentication,
    ): ResponseEntity<TimelineResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            memberNoteService.getTimeline(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                memberPublicId = memberPublicId,
                actorUserPublicId = claims.requireUserPublicId(),
                cursor = cursor,
                limit = limit,
            ),
        )
    }

    @GetMapping("/follow-ups")
    @PreAuthorize("hasPermission(null, 'member-note:follow-up:read')")
    @Operation(summary = "List follow-up notes due within 7 days")
    fun getFollowUps(authentication: Authentication): ResponseEntity<FollowUpResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            memberNoteService.getFollowUps(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
            ),
        )
    }
}

// ── Auth helpers ──────────────────────────────────────────────────────────────

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
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "No user in token.")
