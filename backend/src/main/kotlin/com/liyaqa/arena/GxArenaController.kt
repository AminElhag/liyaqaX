package com.liyaqa.arena

import com.liyaqa.arena.dto.GxBookingResponse
import com.liyaqa.arena.dto.GxClassTypeSummary
import com.liyaqa.arena.dto.GxScheduleItemResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.gx.GXBooking
import com.liyaqa.gx.GXBookingRepository
import com.liyaqa.gx.GXClassInstance
import com.liyaqa.gx.GXClassInstanceRepository
import com.liyaqa.gx.GXClassTypeRepository
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.portal.ClubPortalSettingsService
import com.liyaqa.trainer.TrainerRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@RestController
@RequestMapping("/api/v1/arena/gx")
@Tag(name = "Arena GX", description = "Member GX class schedule and booking")
@Validated
class GxArenaController(
    private val classInstanceRepository: GXClassInstanceRepository,
    private val classTypeRepository: GXClassTypeRepository,
    private val bookingRepository: GXBookingRepository,
    private val memberRepository: MemberRepository,
    private val trainerRepository: TrainerRepository,
    private val portalSettingsService: ClubPortalSettingsService,
) {
    @GetMapping("/schedule")
    @Operation(summary = "Get upcoming GX class schedule for the next 7 days")
    fun getSchedule(authentication: Authentication): ResponseEntity<List<GxScheduleItemResponse>> {
        val member = resolveMember(authentication)
        portalSettingsService.requireFeatureEnabled(member.clubId, "gx")

        val now = Instant.now()
        val sevenDaysLater = now.plus(7, ChronoUnit.DAYS)

        val instances =
            classInstanceRepository.findAllByOrganizationIdAndBranchIdAndScheduledAtBetweenAndDeletedAtIsNull(
                member.organizationId,
                member.branchId,
                now,
                sevenDaysLater,
                Pageable.ofSize(100),
            ).content

        val classTypeIds = instances.map { it.classTypeId }.toSet()
        val classTypesById = classTypeRepository.findAllById(classTypeIds).associateBy { it.id }
        val instructorIds = instances.map { it.instructorId }.toSet()
        val trainersById = trainerRepository.findAllById(instructorIds).associateBy { it.id }

        val response =
            instances.map { instance ->
                val classType = classTypesById[instance.classTypeId]
                val trainer = trainersById[instance.instructorId]
                val isBooked = bookingRepository.existsByInstanceIdAndMemberId(instance.id, member.id)
                GxScheduleItemResponse(
                    id = instance.publicId,
                    classType =
                        GxClassTypeSummary(
                            name = classType?.nameEn ?: "",
                            nameAr = classType?.nameAr ?: "",
                            color = classType?.color,
                        ),
                    instructorName = trainer?.let { "${it.firstNameEn} ${it.lastNameEn}" } ?: "",
                    startTime = instance.scheduledAt,
                    endTime = instance.scheduledAt.plusSeconds(instance.durationMinutes.toLong() * 60),
                    capacity = instance.capacity,
                    bookedCount = instance.bookingsCount,
                    spotsRemaining = (instance.capacity - instance.bookingsCount).coerceAtLeast(0),
                    isBooked = isBooked,
                )
            }

        return ResponseEntity.ok(response)
    }

    @PostMapping("/{instanceId}/book")
    @Operation(summary = "Book a spot in a GX class")
    fun bookClass(
        @PathVariable instanceId: UUID,
        authentication: Authentication,
    ): ResponseEntity<GxBookingResponse> {
        val member = resolveMember(authentication)
        portalSettingsService.requireFeatureEnabled(member.clubId, "gx")

        val instance =
            classInstanceRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(
                instanceId,
                member.organizationId,
            ).orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Class not found.") }

        // Rule 8 — future only
        if (instance.scheduledAt.isBefore(Instant.now())) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Cannot book a past class.",
            )
        }

        // Rule 7 — no duplicate
        if (bookingRepository.existsByInstanceIdAndMemberId(instance.id, member.id)) {
            throw ArenaException(HttpStatus.CONFLICT, "conflict", "Already booked.")
        }

        // Rule 6 — capacity check
        if (instance.bookingsCount >= instance.capacity) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Class is fully booked.",
            )
        }

        instance.bookingsCount++
        classInstanceRepository.save(instance)

        val booking =
            bookingRepository.save(
                GXBooking(
                    organizationId = member.organizationId,
                    clubId = member.clubId,
                    instanceId = instance.id,
                    memberId = member.id,
                    bookingStatus = "confirmed",
                ),
            )

        return ResponseEntity.status(HttpStatus.CREATED).body(toBookingResponse(booking, instance, member))
    }

    @DeleteMapping("/{instanceId}/book")
    @Operation(summary = "Cancel a GX class booking")
    fun cancelBooking(
        @PathVariable instanceId: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val member = resolveMember(authentication)
        portalSettingsService.requireFeatureEnabled(member.clubId, "gx")

        val instance =
            classInstanceRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(
                instanceId,
                member.organizationId,
            ).orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Class not found.") }

        val booking =
            bookingRepository.findByInstanceIdAndMemberId(instance.id, member.id)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Booking not found.") }

        // Rule 9 — own booking only
        if (booking.memberId != member.id) {
            throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "You can only cancel your own booking.")
        }

        booking.bookingStatus = "cancelled"
        booking.cancelledAt = Instant.now()
        bookingRepository.save(booking)

        instance.bookingsCount = (instance.bookingsCount - 1).coerceAtLeast(0)
        classInstanceRepository.save(instance)

        return ResponseEntity.noContent().build()
    }

    @GetMapping("/bookings")
    @Operation(summary = "Get member's GX booking history")
    fun getBookings(
        @PageableDefault(size = 20, sort = ["bookedAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<List<GxBookingResponse>> {
        val member = resolveMember(authentication)
        portalSettingsService.requireFeatureEnabled(member.clubId, "gx")

        val bookings = bookingRepository.findAllByMemberIdAndOrganizationId(member.id, member.organizationId, pageable)
        val instanceIds = bookings.map { it.instanceId }.toSet()
        val instancesById = classInstanceRepository.findAllById(instanceIds).associateBy { it.id }

        val response =
            bookings.content.map { booking ->
                val instance = instancesById[booking.instanceId]
                toBookingResponse(booking, instance, member)
            }

        return ResponseEntity.ok(response)
    }

    private fun toBookingResponse(
        booking: GXBooking,
        instance: GXClassInstance?,
        member: Member,
    ): GxBookingResponse {
        val classType = instance?.let { classTypeRepository.findById(it.classTypeId).orElse(null) }
        val trainer = instance?.let { trainerRepository.findById(it.instructorId).orElse(null) }
        return GxBookingResponse(
            id = booking.publicId,
            instanceId = instance?.publicId ?: UUID.randomUUID(),
            className = classType?.nameEn ?: "",
            classNameAr = classType?.nameAr ?: "",
            instructorName = trainer?.let { "${it.firstNameEn} ${it.lastNameEn}" } ?: "",
            startTime = instance?.scheduledAt ?: Instant.now(),
            status = booking.bookingStatus,
            bookedAt = booking.bookedAt,
            cancelledAt = booking.cancelledAt,
        )
    }

    private fun resolveMember(authentication: Authentication): Member {
        val claims = authentication.arenaContext()
        val memberPublicId = claims.requireMemberId()
        return memberRepository.findAll().firstOrNull { it.publicId == memberPublicId && it.deletedAt == null }
            ?: throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Member not found.")
    }
}
