package com.liyaqa.coach

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.coach.dto.MarkPtAttendanceRequest
import com.liyaqa.coach.dto.PtSessionCoachResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.MemberRepository
import com.liyaqa.pt.PTPackageCatalogRepository
import com.liyaqa.pt.PTPackageRepository
import com.liyaqa.pt.PTSessionRepository
import com.liyaqa.trainer.TrainerRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/coach/pt")
@Tag(name = "Coach PT", description = "Trainer PT session endpoints")
@Validated
class PtCoachController(
    private val trainerRepository: TrainerRepository,
    private val ptSessionRepository: PTSessionRepository,
    private val ptPackageRepository: PTPackageRepository,
    private val ptPackageCatalogRepository: PTPackageCatalogRepository,
    private val memberRepository: MemberRepository,
    private val auditService: AuditService,
) {
    @GetMapping("/sessions")
    @Operation(summary = "Get PT sessions for this trainer (upcoming or past)")
    fun getSessions(
        @RequestParam(defaultValue = "upcoming") status: String,
        @PageableDefault(size = 20, sort = ["scheduledAt"], direction = Sort.Direction.ASC)
        pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<List<PtSessionCoachResponse>> {
        val claims = authentication.coachContext()
        claims.requireTrainerType("pt")
        val trainerPublicId = claims.requireTrainerId()

        val trainer =
            trainerRepository.findByPublicIdAndDeletedAtIsNull(trainerPublicId)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Trainer not found.")
                }

        val now = Instant.now()
        val sessions =
            when (status) {
                "upcoming" ->
                    ptSessionRepository.findAllByTrainerIdAndScheduledAtAfterAndDeletedAtIsNull(
                        trainer.id,
                        now,
                        pageable,
                    )
                "past" ->
                    ptSessionRepository.findAllByTrainerIdAndScheduledAtBeforeAndDeletedAtIsNull(
                        trainer.id,
                        now,
                        pageable,
                    )
                else -> throw ArenaException(
                    HttpStatus.BAD_REQUEST,
                    "validation-failed",
                    "Status must be 'upcoming' or 'past'.",
                )
            }

        val memberIds = sessions.content.map { it.memberId }.toSet()
        val membersById = memberRepository.findAllById(memberIds).associateBy { it.id }
        val packageIds = sessions.content.map { it.packageId }.toSet()
        val packagesById = ptPackageRepository.findAllById(packageIds).associateBy { it.id }
        val catalogIds = packagesById.values.map { it.catalogId }.toSet()
        val catalogsById = ptPackageCatalogRepository.findAllById(catalogIds).associateBy { it.id }

        val response =
            sessions.content.map { session ->
                val member = membersById[session.memberId]
                val pkg = packagesById[session.packageId]
                val catalog = pkg?.let { catalogsById[it.catalogId] }
                PtSessionCoachResponse(
                    id = session.publicId,
                    scheduledAt = session.scheduledAt,
                    status = session.sessionStatus,
                    memberName = member?.let { "${it.firstNameEn} ${it.lastNameEn}" } ?: "",
                    packageName = catalog?.nameEn ?: "",
                    notes = session.notes,
                )
            }

        return ResponseEntity.ok(response)
    }

    @PatchMapping("/sessions/{id}/attendance")
    @Operation(summary = "Mark PT session as attended or missed")
    fun markAttendance(
        @PathVariable id: UUID,
        @Valid @RequestBody request: MarkPtAttendanceRequest,
        authentication: Authentication,
    ): ResponseEntity<PtSessionCoachResponse> {
        val claims = authentication.coachContext()
        claims.requireTrainerType("pt")
        val trainerPublicId = claims.requireTrainerId()

        val trainer =
            trainerRepository.findByPublicIdAndDeletedAtIsNull(trainerPublicId)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Trainer not found.")
                }

        val session =
            ptSessionRepository.findByPublicIdAndDeletedAtIsNull(id)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Session not found.")
                }

        if (session.trainerId != trainer.id) {
            throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "This session belongs to another trainer.")
        }

        if (session.sessionStatus != "scheduled") {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Session is already ${session.sessionStatus}.",
            )
        }

        session.sessionStatus = request.status
        ptSessionRepository.save(session)

        auditService.logFromContext(
            action = AuditAction.PT_ATTENDANCE_MARKED,
            entityType = "PTSession",
            entityId = session.publicId.toString(),
            changesJson = """{"status":"${request.status}"}""",
        )

        val member = memberRepository.findById(session.memberId).orElse(null)
        val pkg = ptPackageRepository.findById(session.packageId).orElse(null)
        val catalog = pkg?.let { ptPackageCatalogRepository.findById(it.catalogId).orElse(null) }

        return ResponseEntity.ok(
            PtSessionCoachResponse(
                id = session.publicId,
                scheduledAt = session.scheduledAt,
                status = session.sessionStatus,
                memberName = member?.let { "${it.firstNameEn} ${it.lastNameEn}" } ?: "",
                packageName = catalog?.nameEn ?: "",
                notes = session.notes,
            ),
        )
    }
}
