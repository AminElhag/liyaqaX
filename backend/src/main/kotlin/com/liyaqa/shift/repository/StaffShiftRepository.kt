package com.liyaqa.shift.repository

import com.liyaqa.shift.entity.StaffShift
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface StaffShiftRepository : JpaRepository<StaffShift, Long> {
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<StaffShift>

    @Query(
        value = """
            SELECT COUNT(*) FROM staff_shifts
            WHERE staff_member_id = :staffMemberId
              AND deleted_at IS NULL
              AND start_at < :endAt
              AND end_at > :startAt
              AND id != :excludeId
        """,
        nativeQuery = true,
    )
    fun countOverlapping(
        @Param("staffMemberId") staffMemberId: Long,
        @Param("startAt") startAt: Instant,
        @Param("endAt") endAt: Instant,
        @Param("excludeId") excludeId: Long,
    ): Long

    @Query(
        value = """
            SELECT s.id AS id,
                   s.public_id AS publicId,
                   s.staff_member_id AS staffMemberId,
                   sm.public_id AS staffPublicId,
                   CONCAT(sm.first_name_en, ' ', sm.last_name_en) AS staffNameEn,
                   CONCAT(sm.first_name_ar, ' ', sm.last_name_ar) AS staffNameAr,
                   s.start_at AS startAt,
                   s.end_at AS endAt,
                   s.notes AS notes
            FROM staff_shifts s
            JOIN staff_members sm ON sm.id = s.staff_member_id
            WHERE s.branch_id = :branchId
              AND s.start_at >= :weekStart
              AND s.start_at < :weekEnd
              AND s.deleted_at IS NULL
            ORDER BY s.start_at
        """,
        nativeQuery = true,
    )
    fun findByBranchAndWeek(
        @Param("branchId") branchId: Long,
        @Param("weekStart") weekStart: Instant,
        @Param("weekEnd") weekEnd: Instant,
    ): List<ShiftRosterProjection>

    @Query(
        value = """
            SELECT s.public_id AS publicId,
                   b.name_en AS branchName,
                   s.start_at AS startAt,
                   s.end_at AS endAt,
                   s.notes AS notes
            FROM staff_shifts s
            JOIN branches b ON b.id = s.branch_id
            WHERE s.staff_member_id = :staffMemberId
              AND s.start_at >= :now
              AND s.start_at < :until
              AND s.deleted_at IS NULL
            ORDER BY s.start_at
        """,
        nativeQuery = true,
    )
    fun findUpcoming(
        @Param("staffMemberId") staffMemberId: Long,
        @Param("now") now: Instant,
        @Param("until") until: Instant,
    ): List<MyShiftProjection>
}
