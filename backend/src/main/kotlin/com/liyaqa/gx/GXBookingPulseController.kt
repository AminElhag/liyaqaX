package com.liyaqa.gx

import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.gx.dto.BookMemberRequest
import com.liyaqa.gx.dto.BulkAttendanceRequest
import com.liyaqa.gx.dto.GXAttendanceResponse
import com.liyaqa.gx.dto.GXBookingResponse
import com.liyaqa.gx.dto.WaitlistListResponse
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/gx")
@Tag(name = "GX Bookings (Pulse)", description = "GX class booking and attendance — club operations")
@Validated
class GXBookingPulseController(
    private val gxBookingService: GXBookingService,
    private val gxWaitlistService: GXWaitlistService,
    private val classInstanceRepository: GXClassInstanceRepository,
) {
    // ── Booking endpoints ──────────────────────────────────────────────────

    @PostMapping("/classes/{classId}/bookings")
    @PreAuthorize("hasPermission(null, 'gx-class:manage-bookings')")
    @Operation(summary = "Book a member into a GX class")
    fun bookMember(
        @PathVariable classId: UUID,
        @Valid @RequestBody request: BookMemberRequest,
        authentication: Authentication,
    ): ResponseEntity<GXBookingResponse> {
        val claims = authentication.pulseContext()
        val response =
            gxBookingService.bookMember(
                orgPublicId = claims.requireOrganizationId(),
                instancePublicId = classId,
                memberPublicId = request.memberId,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/classes/{classId}/bookings")
    @PreAuthorize("hasPermission(null, 'gx-class:read')")
    @Operation(summary = "List bookings for a GX class instance")
    fun listBookings(
        @PathVariable classId: UUID,
        @PageableDefault(size = 50) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<GXBookingResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            gxBookingService.listBookingsForInstance(
                orgPublicId = claims.requireOrganizationId(),
                instancePublicId = classId,
                pageable = pageable,
            ),
        )
    }

    @DeleteMapping("/classes/{classId}/bookings/{bookingId}")
    @PreAuthorize("hasPermission(null, 'gx-class:manage-bookings')")
    @Operation(summary = "Cancel a GX class booking")
    fun cancelBooking(
        @PathVariable classId: UUID,
        @PathVariable bookingId: UUID,
        authentication: Authentication,
    ): ResponseEntity<GXBookingResponse> {
        val claims = authentication.pulseContext()
        val response =
            gxBookingService.cancelBooking(
                orgPublicId = claims.requireOrganizationId(),
                instancePublicId = classId,
                bookingPublicId = bookingId,
            )
        return ResponseEntity.ok(response)
    }

    @GetMapping("/classes/{classId}/waitlist")
    @PreAuthorize("hasPermission(null, 'gx-class:read')")
    @Operation(summary = "View waitlist for a GX class instance")
    fun viewWaitlist(
        @PathVariable classId: UUID,
        authentication: Authentication,
    ): ResponseEntity<List<GXBookingResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            gxBookingService.listWaitlistForInstance(
                orgPublicId = claims.requireOrganizationId(),
                instancePublicId = classId,
            ),
        )
    }

    // ── Waitlist endpoints ──────────────────────────────────────────────────

    @GetMapping("/classes/{classId}/waitlist-entries")
    @PreAuthorize("hasPermission(null, 'gx-class:manage-bookings')")
    @Operation(summary = "List waitlist entries for a GX class instance")
    fun listWaitlistEntries(
        @PathVariable classId: UUID,
        authentication: Authentication,
    ): ResponseEntity<WaitlistListResponse> {
        authentication.pulseContext()
        val instance = resolveInstance(classId)
        return ResponseEntity.ok(gxWaitlistService.listEntriesForClass(instance.id))
    }

    @DeleteMapping("/classes/{classId}/waitlist-entries/{entryId}")
    @PreAuthorize("hasPermission(null, 'gx-class:manage-bookings')")
    @Operation(summary = "Staff removes a member from the waitlist")
    fun removeWaitlistEntry(
        @PathVariable classId: UUID,
        @PathVariable entryId: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        authentication.pulseContext()
        val instance = resolveInstance(classId)
        val entry =
            gxWaitlistService.findEntryByPublicId(entryId)
                ?: throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Waitlist entry not found.")
        gxWaitlistService.staffRemoveEntry(instance.id, entry.id)
        return ResponseEntity.noContent().build()
    }

    private fun resolveInstance(classPublicId: UUID): GXClassInstance =
        classInstanceRepository.findAll()
            .firstOrNull { it.publicId == classPublicId && it.deletedAt == null }
            ?: throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Class not found.")

    // ── Attendance endpoints ───────────────────────────────────────────────

    @PostMapping("/classes/{classId}/attendance")
    @PreAuthorize("hasPermission(null, 'gx-class:mark-attendance')")
    @Operation(summary = "Submit bulk attendance for a GX class")
    fun submitAttendance(
        @PathVariable classId: UUID,
        @Valid @RequestBody request: BulkAttendanceRequest,
        authentication: Authentication,
    ): ResponseEntity<List<GXAttendanceResponse>> {
        val claims = authentication.pulseContext()
        val responses =
            gxBookingService.submitAttendance(
                orgPublicId = claims.requireOrganizationId(),
                instancePublicId = classId,
                request = request,
                callerUserPublicId = claims.requireUserPublicId(),
            )
        return ResponseEntity.ok(responses)
    }

    @GetMapping("/classes/{classId}/attendance")
    @PreAuthorize("hasPermission(null, 'gx-class:mark-attendance')")
    @Operation(summary = "List attendance records for a GX class")
    fun listAttendance(
        @PathVariable classId: UUID,
        authentication: Authentication,
    ): ResponseEntity<List<GXAttendanceResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            gxBookingService.listAttendanceForInstance(
                orgPublicId = claims.requireOrganizationId(),
                instancePublicId = classId,
            ),
        )
    }

    // ── Member GX history ──────────────────────────────────────────────────

    @GetMapping("/members/{memberId}/gx-bookings")
    @PreAuthorize("hasPermission(null, 'gx-class:read')")
    @Operation(summary = "Get a member's GX booking history")
    fun getMemberBookings(
        @PathVariable memberId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<GXBookingResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            gxBookingService.listMemberBookings(
                orgPublicId = claims.requireOrganizationId(),
                memberPublicId = memberId,
                pageable = pageable,
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

private fun JwtClaims.requireUserPublicId(): UUID =
    userPublicId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No user identity in token.")
