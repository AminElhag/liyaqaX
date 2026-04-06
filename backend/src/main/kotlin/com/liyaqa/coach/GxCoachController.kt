package com.liyaqa.coach

import com.liyaqa.coach.dto.GxBookingCoachResponse
import com.liyaqa.coach.dto.GxClassCoachResponse
import com.liyaqa.coach.dto.GxClassTypeSummary
import com.liyaqa.coach.dto.MarkGxAttendanceRequest
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.gx.GXAttendance
import com.liyaqa.gx.GXAttendanceRepository
import com.liyaqa.gx.GXBookingRepository
import com.liyaqa.gx.GXClassInstanceRepository
import com.liyaqa.gx.GXClassTypeRepository
import com.liyaqa.member.MemberRepository
import com.liyaqa.trainer.TrainerRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@RestController
@RequestMapping("/api/v1/coach/gx")
@Tag(name = "Coach GX", description = "Trainer GX class endpoints")
@Validated
class GxCoachController(
    private val trainerRepository: TrainerRepository,
    private val classInstanceRepository: GXClassInstanceRepository,
    private val classTypeRepository: GXClassTypeRepository,
    private val bookingRepository: GXBookingRepository,
    private val attendanceRepository: GXAttendanceRepository,
    private val memberRepository: MemberRepository,
) {
    private val riyadhZone = ZoneId.of("Asia/Riyadh")

    @GetMapping("/classes")
    @Operation(summary = "Get GX class instances for this trainer")
    fun getClasses(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @PageableDefault(size = 20, sort = ["scheduledAt"], direction = Sort.Direction.ASC)
        pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<List<GxClassCoachResponse>> {
        val claims = authentication.coachContext()
        claims.requireTrainerType("gx")
        val trainerPublicId = claims.requireTrainerId()

        val trainer =
            trainerRepository.findByPublicIdAndDeletedAtIsNull(trainerPublicId)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Trainer not found.")
                }

        val rangeFrom = from?.atStartOfDay(riyadhZone)?.toInstant() ?: Instant.now()
        val rangeTo =
            to?.plusDays(1)?.atStartOfDay(riyadhZone)?.toInstant()
                ?: Instant.now().plus(7, ChronoUnit.DAYS)

        val instances =
            classInstanceRepository.findAllByInstructorIdAndScheduledAtBetweenAndDeletedAtIsNull(
                trainer.id,
                rangeFrom,
                rangeTo,
                pageable,
            )

        val classTypeIds = instances.content.map { it.classTypeId }.toSet()
        val classTypesById = classTypeRepository.findAllById(classTypeIds).associateBy { it.id }

        val instanceIds = instances.content.map { it.id }.toSet()
        val attendanceCounts =
            instanceIds.associateWith { instanceId ->
                attendanceRepository.findAllByInstanceId(instanceId)
                    .count { it.attendanceStatus == "present" }
            }

        val response =
            instances.content.map { instance ->
                val classType = classTypesById[instance.classTypeId]
                GxClassCoachResponse(
                    id = instance.publicId,
                    classType =
                        GxClassTypeSummary(
                            name = classType?.nameEn ?: "",
                            nameAr = classType?.nameAr ?: "",
                            color = classType?.color,
                        ),
                    startTime = instance.scheduledAt,
                    endTime = instance.scheduledAt.plusSeconds(instance.durationMinutes.toLong() * 60),
                    capacity = instance.capacity,
                    bookedCount = instance.bookingsCount,
                    attendedCount = attendanceCounts[instance.id] ?: 0,
                )
            }

        return ResponseEntity.ok(response)
    }

    @GetMapping("/classes/{id}/bookings")
    @Operation(summary = "Get bookings list for a GX class instance")
    fun getClassBookings(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<List<GxBookingCoachResponse>> {
        val claims = authentication.coachContext()
        claims.requireTrainerType("gx")
        val trainerPublicId = claims.requireTrainerId()

        val trainer =
            trainerRepository.findByPublicIdAndDeletedAtIsNull(trainerPublicId)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Trainer not found.")
                }

        val instance =
            classInstanceRepository.findByPublicIdAndInstructorIdAndDeletedAtIsNull(id, trainer.id)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Class not found.")
                }

        val bookings =
            bookingRepository.findAllByInstanceIdAndBookingStatusIn(
                instance.id,
                listOf("confirmed"),
            )

        val memberIds = bookings.map { it.memberId }.toSet()
        val membersById = memberRepository.findAllById(memberIds).associateBy { it.id }
        val attendanceByMember =
            attendanceRepository.findAllByInstanceId(instance.id)
                .associateBy { it.memberId }

        val response =
            bookings.map { booking ->
                val member = membersById[booking.memberId]
                val attendance = attendanceByMember[booking.memberId]
                GxBookingCoachResponse(
                    id = booking.publicId,
                    memberName = member?.let { "${it.firstNameEn} ${it.lastNameEn}" } ?: "",
                    bookedAt = booking.bookedAt,
                    attended =
                        attendance?.let {
                            it.attendanceStatus == "present"
                        },
                )
            }

        return ResponseEntity.ok(response)
    }

    @PatchMapping("/classes/{id}/attendance")
    @Operation(summary = "Bulk mark attendance for a GX class")
    @Transactional
    fun markAttendance(
        @PathVariable id: UUID,
        @Valid @RequestBody request: MarkGxAttendanceRequest,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.coachContext()
        claims.requireTrainerType("gx")
        val trainerPublicId = claims.requireTrainerId()

        val trainer =
            trainerRepository.findByPublicIdAndDeletedAtIsNull(trainerPublicId)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Trainer not found.")
                }

        val instance =
            classInstanceRepository.findByPublicIdAndInstructorIdAndDeletedAtIsNull(id, trainer.id)
                .orElseThrow {
                    ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Class not found.")
                }

        val thirtyMinutesFromNow = Instant.now().plus(30, ChronoUnit.MINUTES)
        if (instance.scheduledAt.isAfter(thirtyMinutesFromNow)) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Class has not started yet.",
            )
        }

        val bookingsByPublicId =
            bookingRepository.findAllByInstanceIdAndBookingStatusIn(
                instance.id,
                listOf("confirmed"),
            ).associateBy { it.publicId }

        for (item in request.attendances) {
            val booking =
                bookingsByPublicId[item.bookingId]
                    ?: throw ArenaException(
                        HttpStatus.NOT_FOUND,
                        "resource-not-found",
                        "Booking ${item.bookingId} not found.",
                    )

            val existing = attendanceRepository.findByInstanceIdAndMemberId(instance.id, booking.memberId)
            if (existing.isPresent) {
                val attendance = existing.get()
                attendance.attendanceStatus = if (item.attended) "present" else "absent"
                attendance.markedAt = Instant.now()
                attendanceRepository.save(attendance)
            } else {
                attendanceRepository.save(
                    GXAttendance(
                        organizationId = instance.organizationId,
                        instanceId = instance.id,
                        memberId = booking.memberId,
                        attendanceStatus = if (item.attended) "present" else "absent",
                        markedById = trainer.id,
                    ),
                )
            }
        }

        return ResponseEntity.noContent().build()
    }
}
