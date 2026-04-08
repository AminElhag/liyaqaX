package com.liyaqa.checkin.repository

import com.liyaqa.checkin.entity.MemberCheckIn
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

@Repository
interface MemberCheckInRepository : JpaRepository<MemberCheckIn, Long> {

    @Query(
        value = """
            SELECT COUNT(*) FROM member_check_ins
            WHERE member_id = :memberId
              AND branch_id = :branchId
              AND checked_in_at > :threshold
        """,
        nativeQuery = true,
    )
    fun countRecentCheckIns(
        @Param("memberId") memberId: Long,
        @Param("branchId") branchId: Long,
        @Param("threshold") threshold: Instant,
    ): Long

    @Query(
        value = """
            SELECT COUNT(*) FROM member_check_ins
            WHERE branch_id = :branchId
              AND DATE(checked_in_at AT TIME ZONE 'Asia/Riyadh') = :today
        """,
        nativeQuery = true,
    )
    fun countTodayByBranch(
        @Param("branchId") branchId: Long,
        @Param("today") today: LocalDate,
    ): Long

    @Query(
        value = """
            SELECT ci.public_id AS publicId,
                   CONCAT(m.first_name_en, ' ', m.last_name_en) AS memberNameEn,
                   CONCAT(m.first_name_ar, ' ', m.last_name_ar) AS memberNameAr,
                   m.phone AS phone,
                   ci.method AS method,
                   ci.checked_in_at AS checkedInAt
            FROM member_check_ins ci
            JOIN members m ON m.id = ci.member_id
            WHERE ci.branch_id = :branchId
            ORDER BY ci.checked_in_at DESC
            LIMIT 20
        """,
        nativeQuery = true,
    )
    fun findRecentByBranch(
        @Param("branchId") branchId: Long,
    ): List<RecentCheckInProjection>

    fun findTopByMemberIdAndBranchIdOrderByCheckedInAtDesc(
        memberId: Long,
        branchId: Long,
    ): MemberCheckIn?

    @Query(
        value = """
            SELECT COUNT(*) FROM member_check_ins ci
            JOIN branches b ON b.id = ci.branch_id
            WHERE b.club_id = :clubId
              AND ci.checked_in_at BETWEEN :fromDate AND :toDate
        """,
        nativeQuery = true,
    )
    fun countByClubAndDateRange(
        @Param("clubId") clubId: Long,
        @Param("fromDate") fromDate: Instant,
        @Param("toDate") toDate: Instant,
    ): Long
}
