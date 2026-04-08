package com.liyaqa.coach

import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.MemberNoteService
import com.liyaqa.member.dto.CreateNoteRequest
import com.liyaqa.member.dto.NoteResponse
import com.liyaqa.security.JwtClaims
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
@RequestMapping("/api/v1/coach/members")
@Tag(name = "Member Notes (Coach)", description = "Member notes for trainers")
@Validated
class MemberNoteCoachController(
    private val memberNoteService: MemberNoteService,
) {
    @PostMapping("/{memberPublicId}/notes")
    @Operation(summary = "Create a note for a member (trainer — general and health types only)")
    fun createNote(
        @PathVariable memberPublicId: UUID,
        @Valid @RequestBody request: CreateNoteRequest,
        authentication: Authentication,
    ): ResponseEntity<NoteResponse> {
        val claims = authentication.coachContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            memberNoteService.createNoteAsTrainer(
                orgPublicId = claims.requireCoachOrganizationId(),
                clubPublicId = claims.requireCoachClubId(),
                memberPublicId = memberPublicId,
                request = request,
                actorUserPublicId = requireUserPublicId(claims),
                trainerPublicId = claims.requireTrainerId(),
            ),
        )
    }

    @GetMapping("/{memberPublicId}/notes")
    @Operation(summary = "List notes for a member the trainer trains")
    fun listNotes(
        @PathVariable memberPublicId: UUID,
        authentication: Authentication,
    ): ResponseEntity<List<NoteResponse>> {
        val claims = authentication.coachContext()
        return ResponseEntity.ok(
            memberNoteService.listNotesForTrainer(
                orgPublicId = claims.requireCoachOrganizationId(),
                clubPublicId = claims.requireCoachClubId(),
                memberPublicId = memberPublicId,
                trainerPublicId = claims.requireTrainerId(),
            ),
        )
    }
}

private fun requireUserPublicId(claims: JwtClaims): UUID =
    claims.userPublicId
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "No user in token.")
