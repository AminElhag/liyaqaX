package com.liyaqa.shift.service

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.shift.dto.CreateSwapRequest
import com.liyaqa.shift.dto.PendingSwapItem
import com.liyaqa.shift.dto.PendingSwapsResponse
import com.liyaqa.shift.entity.ShiftSwapRequest
import com.liyaqa.shift.repository.ShiftSwapRequestRepository
import com.liyaqa.shift.repository.StaffShiftRepository
import com.liyaqa.staff.StaffMemberRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ShiftSwapService(
    private val swapRepository: ShiftSwapRequestRepository,
    private val shiftRepository: StaffShiftRepository,
    private val staffMemberRepository: StaffMemberRepository,
    private val staffShiftService: StaffShiftService,
    private val auditService: AuditService,
) {
    @Transactional
    fun requestSwap(
        shiftPublicId: UUID,
        requesterStaffId: Long,
        request: CreateSwapRequest,
    ): UUID {
        val shift = staffShiftService.findShiftOrThrow(shiftPublicId)

        if (shift.staffMemberId != requesterStaffId) {
            throw ArenaException(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "Only the shift owner can request a swap.",
            )
        }

        val targetStaff =
            staffMemberRepository.findByPublicIdAndDeletedAtIsNull(request.targetStaffPublicId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "not-found", "Target staff member not found.") }

        val requesterStaff =
            staffMemberRepository.findById(requesterStaffId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "not-found", "Requester staff not found.") }

        if (targetStaff.clubId != requesterStaff.clubId) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Target staff member must be in the same club.",
            )
        }

        val existingOpen = swapRepository.countOpenSwapsForShift(shift.id)
        if (existingOpen > 0) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "A swap request is already pending for this shift.",
                "SWAP_ALREADY_PENDING",
            )
        }

        val swap =
            swapRepository.save(
                ShiftSwapRequest(
                    shiftId = shift.id,
                    requesterStaffId = requesterStaffId,
                    targetStaffId = targetStaff.id,
                    requesterNote = request.requesterNote?.trim(),
                ),
            )

        return swap.publicId
    }

    @Transactional
    fun respondToSwap(
        swapPublicId: UUID,
        callerStaffId: Long,
        action: String,
    ) {
        val swap = findSwapOrThrow(swapPublicId)

        if (swap.status != "PENDING_ACCEPTANCE") {
            throw ArenaException(HttpStatus.CONFLICT, "conflict", "Swap request is not awaiting acceptance.")
        }

        if (swap.targetStaffId != callerStaffId) {
            throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "Only the target staff can respond.")
        }

        when (action.lowercase()) {
            "accept" -> {
                val shift =
                    shiftRepository.findById(swap.shiftId)
                        .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "not-found", "Shift not found.") }

                staffShiftService.checkOverlap(swap.targetStaffId, shift.startAt, shift.endAt, 0L)

                swap.status = "PENDING_APPROVAL"
            }
            "decline" -> {
                swap.status = "DECLINED"
            }
            else -> throw ArenaException(HttpStatus.BAD_REQUEST, "bad-request", "Action must be 'accept' or 'decline'.")
        }

        swapRepository.save(swap)
    }

    @Transactional
    fun resolveSwap(
        swapPublicId: UUID,
        resolverUserId: Long,
        action: String,
    ) {
        val swap = findSwapOrThrow(swapPublicId)

        if (swap.status != "PENDING_APPROVAL") {
            throw ArenaException(HttpStatus.CONFLICT, "conflict", "Swap request is not awaiting approval.")
        }

        when (action.lowercase()) {
            "approve" -> {
                val shift =
                    shiftRepository.findById(swap.shiftId)
                        .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "not-found", "Shift not found.") }

                shift.staffMemberId = swap.targetStaffId
                shiftRepository.save(shift)

                swap.status = "APPROVED"
                swap.resolvedByUserId = resolverUserId
                swap.resolvedAt = Instant.now()

                auditService.logFromContext(
                    action = AuditAction.SHIFT_SWAP_APPROVED,
                    entityType = "ShiftSwapRequest",
                    entityId = swap.publicId.toString(),
                )
            }
            "reject" -> {
                swap.status = "REJECTED"
                swap.resolvedByUserId = resolverUserId
                swap.resolvedAt = Instant.now()

                auditService.logFromContext(
                    action = AuditAction.SHIFT_SWAP_REJECTED,
                    entityType = "ShiftSwapRequest",
                    entityId = swap.publicId.toString(),
                )
            }
            else -> throw ArenaException(HttpStatus.BAD_REQUEST, "bad-request", "Action must be 'approve' or 'reject'.")
        }

        swapRepository.save(swap)
    }

    @Transactional
    fun cancelSwap(
        swapPublicId: UUID,
        callerStaffId: Long,
    ) {
        val swap = findSwapOrThrow(swapPublicId)

        if (swap.status != "PENDING_ACCEPTANCE") {
            throw ArenaException(HttpStatus.CONFLICT, "conflict", "Only PENDING_ACCEPTANCE swaps can be cancelled.")
        }

        if (swap.requesterStaffId != callerStaffId) {
            throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "Only the requester can cancel.")
        }

        swap.status = "CANCELLED"
        swapRepository.save(swap)
    }

    fun getPendingApprovals(clubId: Long): PendingSwapsResponse {
        val projections = swapRepository.findPendingApprovalByClub(clubId)

        val items =
            projections.map { proj ->
                PendingSwapItem(
                    swapId = proj.publicId,
                    shiftDate = proj.shiftStart.atOffset(ZoneOffset.UTC).toLocalDate(),
                    shiftStart = proj.shiftStart,
                    shiftEnd = proj.shiftEnd,
                    requesterName = proj.requesterNameEn ?: proj.requesterNameAr,
                    targetName = proj.targetNameEn ?: proj.targetNameAr,
                    status = "PENDING_APPROVAL",
                    requesterNote = proj.requesterNote,
                )
            }

        return PendingSwapsResponse(swapRequests = items)
    }

    private fun findSwapOrThrow(publicId: UUID): ShiftSwapRequest =
        swapRepository.findByPublicId(publicId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "not-found", "Swap request not found.") }
}
