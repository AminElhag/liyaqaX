package com.liyaqa.shift.repository

import com.liyaqa.shift.entity.ShiftSwapRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface ShiftSwapRequestRepository : JpaRepository<ShiftSwapRequest, Long> {
    fun findByPublicId(publicId: UUID): Optional<ShiftSwapRequest>

    @Query(
        value = """
            SELECT COUNT(*) FROM shift_swap_requests
            WHERE shift_id = :shiftId
              AND status IN ('PENDING_ACCEPTANCE', 'PENDING_APPROVAL')
        """,
        nativeQuery = true,
    )
    fun countOpenSwapsForShift(
        @Param("shiftId") shiftId: Long,
    ): Long

    @Query(
        value = """
            SELECT sr.public_id AS publicId,
                   s.start_at AS shiftStart,
                   s.end_at AS shiftEnd,
                   CONCAT(sm_req.first_name_en, ' ', sm_req.last_name_en) AS requesterNameEn,
                   CONCAT(sm_req.first_name_ar, ' ', sm_req.last_name_ar) AS requesterNameAr,
                   CONCAT(sm_tgt.first_name_en, ' ', sm_tgt.last_name_en) AS targetNameEn,
                   CONCAT(sm_tgt.first_name_ar, ' ', sm_tgt.last_name_ar) AS targetNameAr,
                   sr.requester_note AS requesterNote
            FROM shift_swap_requests sr
            JOIN staff_shifts s       ON s.id = sr.shift_id
            JOIN branches b           ON b.id = s.branch_id
            JOIN staff_members sm_req ON sm_req.id = sr.requester_staff_id
            JOIN staff_members sm_tgt ON sm_tgt.id = sr.target_staff_id
            WHERE b.club_id = :clubId
              AND sr.status = 'PENDING_APPROVAL'
            ORDER BY sr.created_at
        """,
        nativeQuery = true,
    )
    fun findPendingApprovalByClub(
        @Param("clubId") clubId: Long,
    ): List<PendingSwapProjection>

    fun findByShiftIdAndStatusIn(
        shiftId: Long,
        statuses: List<String>,
    ): List<ShiftSwapRequest>
}
