package com.liyaqa.coach

import com.liyaqa.coach.dto.ScheduleItemResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.gx.GXClassInstanceRepository
import com.liyaqa.gx.GXClassTypeRepository
import com.liyaqa.member.MemberRepository
import com.liyaqa.pt.PTSessionRepository
import com.liyaqa.trainer.TrainerRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId

@RestController
@RequestMapping("/api/v1/coach/schedule")
@Tag(name = "Coach Schedule", description = "Trainer combined schedule")
@Validated
class ScheduleCoachController(
    private val trainerRepository: TrainerRepository,
    private val ptSessionRepository: PTSessionRepository,
    private val classInstanceRepository: GXClassInstanceRepository,
    private val classTypeRepository: GXClassTypeRepository,
    private val memberRepository: MemberRepository,
) {
    private val riyadhZone = ZoneId.of("Asia/Riyadh")

    @GetMapping
    @Operation(summary = "Get trainer's schedule for a given date (default: today)")
    fun getSchedule(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        authentication: Authentication,
    ): ResponseEntity<List<ScheduleItemResponse>> {
        val claims = authentication.coachContext()
        val trainerPublicId = claims.requireTrainerId()

        val requestedDate = date ?: LocalDate.now(riyadhZone)
        val maxDate = LocalDate.now(riyadhZone).plusDays(30)
        if (requestedDate.isAfter(maxDate)) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Cannot view schedule more than 30 days ahead.",
            )
        }

        val trainer =
            trainerRepository.findByPublicIdAndDeletedAtIsNull(trainerPublicId)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Trainer not found.")
                }

        val dayStart = requestedDate.atStartOfDay(riyadhZone).toInstant()
        val dayEnd = requestedDate.plusDays(1).atStartOfDay(riyadhZone).toInstant()

        val items = mutableListOf<ScheduleItemResponse>()

        if (claims.trainerTypes.contains("pt")) {
            val ptSessions =
                ptSessionRepository.findAllByTrainerIdAndScheduledAtBetweenAndDeletedAtIsNull(
                    trainer.id,
                    dayStart,
                    dayEnd,
                )
            val memberIds = ptSessions.map { it.memberId }.toSet()
            val membersById = memberRepository.findAllById(memberIds).associateBy { it.id }

            ptSessions.mapTo(items) { session ->
                val member = membersById[session.memberId]
                val memberName = member?.let { "${it.firstNameEn} ${it.lastNameEn}" } ?: ""
                ScheduleItemResponse(
                    type = "pt",
                    id = session.publicId,
                    startTime = session.scheduledAt,
                    endTime = session.scheduledAt.plusSeconds(session.durationMinutes.toLong() * 60),
                    title = "PT Session",
                    memberOrClassName = memberName,
                    status = session.sessionStatus,
                )
            }
        }

        if (claims.trainerTypes.contains("gx")) {
            val gxInstances =
                classInstanceRepository.findAllByInstructorIdAndScheduledAtBetweenAndDeletedAtIsNull(
                    trainer.id,
                    dayStart,
                    dayEnd,
                )
            val classTypeIds = gxInstances.map { it.classTypeId }.toSet()
            val classTypesById = classTypeRepository.findAllById(classTypeIds).associateBy { it.id }

            gxInstances.mapTo(items) { instance ->
                val classType = classTypesById[instance.classTypeId]
                ScheduleItemResponse(
                    type = "gx",
                    id = instance.publicId,
                    startTime = instance.scheduledAt,
                    endTime = instance.scheduledAt.plusSeconds(instance.durationMinutes.toLong() * 60),
                    title = classType?.nameEn ?: "GX Class",
                    memberOrClassName = classType?.nameEn ?: "",
                    status = instance.instanceStatus,
                    bookedCount = instance.bookingsCount,
                    capacity = instance.capacity,
                )
            }
        }

        items.sortBy { it.startTime }

        return ResponseEntity.ok(items)
    }
}
