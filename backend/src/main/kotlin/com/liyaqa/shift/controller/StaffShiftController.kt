package com.liyaqa.shift.controller

import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.security.JwtClaims
import com.liyaqa.shift.dto.CreateShiftRequest
import com.liyaqa.shift.dto.CreateSwapRequest
import com.liyaqa.shift.dto.MyShiftsResponse
import com.liyaqa.shift.dto.PendingSwapsResponse
import com.liyaqa.shift.dto.RosterResponse
import com.liyaqa.shift.dto.ShiftResponse
import com.liyaqa.shift.dto.SwapActionRequest
import com.liyaqa.shift.dto.UpdateShiftRequest
import com.liyaqa.shift.service.ShiftSwapService
import com.liyaqa.shift.service.StaffShiftService
import com.liyaqa.staff.StaffMemberRepository
import com.liyaqa.user.UserRepository
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
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/pulse/shifts")
@Tag(name = "Staff Shifts (Pulse)", description = "Shift scheduling and swap management")
@Validated
class StaffShiftController(
    private val staffShiftService: StaffShiftService,
    private val shiftSwapService: ShiftSwapService,
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val staffMemberRepository: StaffMemberRepository,
) {
    @PostMapping
    @PreAuthorize("hasPermission(null, 'shift:manage')")
    @Operation(summary = "Create a shift for a staff member")
    fun createShift(
        @Valid @RequestBody request: CreateShiftRequest,
        authentication: Authentication,
    ): ResponseEntity<ShiftResponse> {
        val claims = authentication.pulseContext()
        val club = resolveClub(claims)
        val user = resolveUser(claims)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            staffShiftService.createShift(club.id, user.id, request),
        )
    }

    @PatchMapping("/{shiftId}")
    @PreAuthorize("hasPermission(null, 'shift:manage')")
    @Operation(summary = "Update shift times or notes")
    fun updateShift(
        @PathVariable shiftId: UUID,
        @Valid @RequestBody request: UpdateShiftRequest,
        authentication: Authentication,
    ): ResponseEntity<ShiftResponse> {
        val claims = authentication.pulseContext()
        val club = resolveClub(claims)
        return ResponseEntity.ok(staffShiftService.updateShift(club.id, shiftId, request))
    }

    @DeleteMapping("/{shiftId}")
    @PreAuthorize("hasPermission(null, 'shift:manage')")
    @Operation(summary = "Soft-delete a shift")
    fun deleteShift(
        @PathVariable shiftId: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        val club = resolveClub(claims)
        staffShiftService.deleteShift(club.id, shiftId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'shift:manage')")
    @Operation(summary = "Get roster grid for a branch and week")
    fun getRoster(
        @RequestParam branchPublicId: UUID,
        @RequestParam weekStart: LocalDate,
        authentication: Authentication,
    ): ResponseEntity<RosterResponse> {
        val claims = authentication.pulseContext()
        val club = resolveClub(claims)
        return ResponseEntity.ok(staffShiftService.getRoster(club.id, branchPublicId, weekStart))
    }

    @GetMapping("/my")
    @PreAuthorize("hasPermission(null, 'shift:read')")
    @Operation(summary = "Get own upcoming shifts (next 14 days)")
    fun getMyShifts(authentication: Authentication): ResponseEntity<MyShiftsResponse> {
        val claims = authentication.pulseContext()
        val staffMember = resolveStaffMember(claims)
        return ResponseEntity.ok(staffShiftService.getMyShifts(staffMember.id))
    }

    @PostMapping("/{shiftId}/swap-requests")
    @PreAuthorize("hasPermission(null, 'shift:read')")
    @Operation(summary = "Request a shift swap")
    fun requestSwap(
        @PathVariable shiftId: UUID,
        @Valid @RequestBody request: CreateSwapRequest,
        authentication: Authentication,
    ): ResponseEntity<Map<String, UUID>> {
        val claims = authentication.pulseContext()
        val staffMember = resolveStaffMember(claims)
        val swapId = shiftSwapService.requestSwap(shiftId, staffMember.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("swapId" to swapId))
    }

    @PatchMapping("/swap-requests/{swapId}/respond")
    @PreAuthorize("hasPermission(null, 'shift:read')")
    @Operation(summary = "Target accepts or declines a swap request")
    fun respondToSwap(
        @PathVariable swapId: UUID,
        @Valid @RequestBody request: SwapActionRequest,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        val staffMember = resolveStaffMember(claims)
        shiftSwapService.respondToSwap(swapId, staffMember.id, request.action)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/swap-requests/{swapId}/resolve")
    @PreAuthorize("hasPermission(null, 'shift:manage')")
    @Operation(summary = "Manager approves or rejects a swap request")
    fun resolveSwap(
        @PathVariable swapId: UUID,
        @Valid @RequestBody request: SwapActionRequest,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        val user = resolveUser(claims)
        shiftSwapService.resolveSwap(swapId, user.id, request.action)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/swap-requests/pending")
    @PreAuthorize("hasPermission(null, 'shift:manage')")
    @Operation(summary = "List pending-approval swaps for the club")
    fun getPendingSwaps(authentication: Authentication): ResponseEntity<PendingSwapsResponse> {
        val claims = authentication.pulseContext()
        val club = resolveClub(claims)
        return ResponseEntity.ok(shiftSwapService.getPendingApprovals(club.id))
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun resolveClub(claims: JwtClaims) =
        clubRepository.findByPublicIdAndDeletedAtIsNull(claims.requireClubId())
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.") }

    private fun resolveUser(claims: JwtClaims) =
        userRepository.findByPublicIdAndDeletedAtIsNull(claims.requireUserPublicId())
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "User not found.") }

    private fun resolveStaffMember(claims: JwtClaims) =
        staffMemberRepository.findByUserIdAndDeletedAtIsNull(
            resolveUser(claims).id,
        ).orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Staff member not found.") }
}

// ── Auth helpers ──────────────────────────────────────────────────────────────

private fun Authentication.pulseContext(): JwtClaims =
    details as? JwtClaims
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")

private fun JwtClaims.requireUserPublicId(): UUID =
    userPublicId
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Invalid user identity in token.")

private fun JwtClaims.requireClubId(): UUID =
    clubId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")
