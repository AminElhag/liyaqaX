package com.liyaqa.shift.service

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.branch.BranchRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.shift.dto.CreateShiftRequest
import com.liyaqa.shift.dto.MyShiftItem
import com.liyaqa.shift.dto.MyShiftsResponse
import com.liyaqa.shift.dto.RosterResponse
import com.liyaqa.shift.dto.RosterShiftItem
import com.liyaqa.shift.dto.ShiftResponse
import com.liyaqa.shift.dto.SwapSummary
import com.liyaqa.shift.dto.UpdateShiftRequest
import com.liyaqa.shift.entity.StaffShift
import com.liyaqa.shift.repository.ShiftSwapRequestRepository
import com.liyaqa.shift.repository.StaffShiftRepository
import com.liyaqa.staff.StaffBranchAssignmentRepository
import com.liyaqa.staff.StaffMember
import com.liyaqa.staff.StaffMemberRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
@Transactional(readOnly = true)
class StaffShiftService(
    private val shiftRepository: StaffShiftRepository,
    private val swapRepository: ShiftSwapRequestRepository,
    private val staffMemberRepository: StaffMemberRepository,
    private val staffBranchAssignmentRepository: StaffBranchAssignmentRepository,
    private val branchRepository: BranchRepository,
    private val auditService: AuditService,
) {
    @Transactional
    fun createShift(
        clubId: Long,
        callerUserId: Long,
        request: CreateShiftRequest,
    ): ShiftResponse {
        val staff = findStaffByPublicId(request.staffMemberPublicId)
        val branch =
            branchRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(request.branchPublicId, clubId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "not-found", "Branch not found.") }

        if (!staffBranchAssignmentRepository.existsByStaffMemberIdAndBranchId(staff.id, branch.id)) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Staff member is not assigned to this branch.",
                "STAFF_NOT_AT_BRANCH",
            )
        }

        validateEndAfterStart(request.startAt, request.endAt)
        checkOverlap(staff.id, request.startAt, request.endAt, 0L)

        val shift =
            shiftRepository.save(
                StaffShift(
                    staffMemberId = staff.id,
                    branchId = branch.id,
                    startAt = request.startAt,
                    endAt = request.endAt,
                    notes = request.notes?.trim(),
                    createdByUserId = callerUserId,
                ),
            )

        auditService.logFromContext(
            action = AuditAction.SHIFT_CREATED,
            entityType = "StaffShift",
            entityId = shift.publicId.toString(),
        )

        val staffName = "${staff.firstNameEn} ${staff.lastNameEn}"
        return ShiftResponse(
            shiftId = shift.publicId,
            staffMemberName = staffName,
            branchName = branch.nameEn,
            startAt = shift.startAt,
            endAt = shift.endAt,
            notes = shift.notes,
        )
    }

    @Transactional
    fun updateShift(
        clubId: Long,
        shiftPublicId: UUID,
        request: UpdateShiftRequest,
    ): ShiftResponse {
        val shift = findShiftOrThrow(shiftPublicId)
        val branch =
            branchRepository.findById(shift.branchId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "not-found", "Branch not found.") }

        if (branch.clubId != clubId) {
            throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "Shift does not belong to your club.")
        }

        val newStart = request.startAt ?: shift.startAt
        val newEnd = request.endAt ?: shift.endAt
        validateEndAfterStart(newStart, newEnd)

        if (request.startAt != null || request.endAt != null) {
            checkOverlap(shift.staffMemberId, newStart, newEnd, shift.id)
        }

        shift.startAt = newStart
        shift.endAt = newEnd
        if (request.notes != null) {
            shift.notes = request.notes.trim()
        }

        shiftRepository.save(shift)

        auditService.logFromContext(
            action = AuditAction.SHIFT_UPDATED,
            entityType = "StaffShift",
            entityId = shift.publicId.toString(),
        )

        val staff = staffMemberRepository.findById(shift.staffMemberId).orElse(null)
        val staffName = staff?.let { "${it.firstNameEn} ${it.lastNameEn}" } ?: "Unknown"
        return ShiftResponse(
            shiftId = shift.publicId,
            staffMemberName = staffName,
            branchName = branch.nameEn,
            startAt = shift.startAt,
            endAt = shift.endAt,
            notes = shift.notes,
        )
    }

    @Transactional
    fun deleteShift(
        clubId: Long,
        shiftPublicId: UUID,
    ) {
        val shift = findShiftOrThrow(shiftPublicId)
        val branch =
            branchRepository.findById(shift.branchId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "not-found", "Branch not found.") }

        if (branch.clubId != clubId) {
            throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "Shift does not belong to your club.")
        }

        val openSwaps = swapRepository.countOpenSwapsForShift(shift.id)
        if (openSwaps > 0) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Cannot delete a shift with a pending swap request.",
                "SHIFT_HAS_PENDING_SWAP",
            )
        }

        shift.deletedAt = Instant.now()
        shiftRepository.save(shift)

        auditService.logFromContext(
            action = AuditAction.SHIFT_DELETED,
            entityType = "StaffShift",
            entityId = shift.publicId.toString(),
        )
    }

    fun getRoster(
        clubId: Long,
        branchPublicId: UUID,
        weekStart: LocalDate,
    ): RosterResponse {
        val branch =
            branchRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(branchPublicId, clubId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "not-found", "Branch not found.") }

        val weekEnd = weekStart.plusDays(7)
        val weekStartInstant = weekStart.atStartOfDay().toInstant(ZoneOffset.UTC)
        val weekEndInstant = weekEnd.atStartOfDay().toInstant(ZoneOffset.UTC)

        val shifts = shiftRepository.findByBranchAndWeek(branch.id, weekStartInstant, weekEndInstant)

        val rosterItems =
            shifts.map { proj ->
                val hasPending = swapRepository.countOpenSwapsForShift(proj.id) > 0

                RosterShiftItem(
                    shiftId = proj.publicId,
                    staffMemberId = proj.staffPublicId,
                    staffMemberName = proj.staffNameEn ?: proj.staffNameAr,
                    startAt = proj.startAt,
                    endAt = proj.endAt,
                    notes = proj.notes,
                    hasPendingSwap = hasPending,
                )
            }

        return RosterResponse(
            branchName = branch.nameEn,
            weekStart = weekStart,
            weekEnd = weekStart.plusDays(6),
            shifts = rosterItems,
        )
    }

    fun getMyShifts(staffMemberId: Long): MyShiftsResponse {
        val now = Instant.now()
        val until = now.plus(14, ChronoUnit.DAYS)

        val projections = shiftRepository.findUpcoming(staffMemberId, now, until)

        val items =
            projections.map { proj ->
                val shift = shiftRepository.findByPublicIdAndDeletedAtIsNull(proj.publicId).orElse(null)
                val swapSummary =
                    if (shift != null) {
                        val openSwaps =
                            swapRepository.findByShiftIdAndStatusIn(
                                shift.id,
                                listOf("PENDING_ACCEPTANCE", "PENDING_APPROVAL"),
                            )
                        openSwaps.firstOrNull()?.let { swap ->
                            val targetStaff = staffMemberRepository.findById(swap.targetStaffId).orElse(null)
                            val targetName = targetStaff?.let { "${it.firstNameEn} ${it.lastNameEn}" } ?: "Unknown"
                            SwapSummary(
                                swapId = swap.publicId,
                                targetStaffName = targetName,
                                status = swap.status,
                            )
                        }
                    } else {
                        null
                    }

                MyShiftItem(
                    shiftId = proj.publicId,
                    branchName = proj.branchName,
                    startAt = proj.startAt,
                    endAt = proj.endAt,
                    notes = proj.notes,
                    swapRequest = swapSummary,
                )
            }

        return MyShiftsResponse(shifts = items)
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    fun findShiftOrThrow(publicId: UUID): StaffShift =
        shiftRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "not-found", "Shift not found.") }

    fun checkOverlap(
        staffMemberId: Long,
        startAt: Instant,
        endAt: Instant,
        excludeId: Long,
    ) {
        val overlaps = shiftRepository.countOverlapping(staffMemberId, startAt, endAt, excludeId)
        if (overlaps > 0) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Staff member already has a shift from $startAt to $endAt that overlaps.",
                "SHIFT_OVERLAP",
            )
        }
    }

    private fun validateEndAfterStart(
        startAt: Instant,
        endAt: Instant,
    ) {
        if (!endAt.isAfter(startAt)) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Shift end time must be after start time.",
            )
        }
    }

    private fun findStaffByPublicId(publicId: UUID): StaffMember =
        staffMemberRepository.findByPublicIdAndDeletedAtIsNull(publicId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "not-found", "Staff member not found.") }
}
