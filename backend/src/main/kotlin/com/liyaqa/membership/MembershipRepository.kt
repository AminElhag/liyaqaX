package com.liyaqa.membership

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@Repository
interface MembershipRepository : JpaRepository<Membership, Long> {
    fun findByPublicIdAndOrganizationIdAndDeletedAtIsNull(
        publicId: UUID,
        organizationId: Long,
    ): Optional<Membership>

    fun findAllByMemberIdAndDeletedAtIsNull(
        memberId: Long,
        pageable: Pageable,
    ): Page<Membership>

    fun countByMembershipStatusAndDeletedAtIsNull(membershipStatus: String): Long

    fun findByMemberIdAndMembershipStatusInAndDeletedAtIsNull(
        memberId: Long,
        statuses: List<String>,
    ): Optional<Membership>

    fun findByMemberIdAndMembershipStatusAndDeletedAtIsNull(
        memberId: Long,
        membershipStatus: String,
    ): Optional<Membership>

    fun existsByMemberIdAndMembershipStatusInAndDeletedAtIsNull(
        memberId: Long,
        statuses: List<String>,
    ): Boolean

    fun findAllByEndDateAndMembershipStatusAndDeletedAtIsNull(
        endDate: LocalDate,
        membershipStatus: String,
    ): List<Membership>

    @Query(
        value = """
            SELECT * FROM memberships m
            WHERE m.membership_status IN :statuses
              AND m.deleted_at IS NULL
              AND (
                  (m.grace_end_date IS NOT NULL AND m.grace_end_date < :today)
                  OR (m.grace_end_date IS NULL AND m.end_date < :today)
              )
        """,
        nativeQuery = true,
    )
    fun findOverdueMemberships(
        @Param("statuses") statuses: List<String>,
        @Param("today") today: LocalDate,
    ): List<Membership>

    @Query(
        value = """
            SELECT COALESCE(SUM(
                CASE
                    WHEN mp.duration_days <= 31 THEN mp.price_halalas
                    WHEN mp.duration_days <= 93 THEN mp.price_halalas / 3
                    ELSE mp.price_halalas / 12
                END
            ), 0)
            FROM memberships m
            JOIN membership_plans mp ON m.plan_id = mp.id
            WHERE m.club_id = :clubId
              AND m.membership_status = 'active'
              AND m.deleted_at IS NULL
              AND mp.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun estimateMrrHalalasForClub(
        @Param("clubId") clubId: Long,
    ): Long

    @Query(
        value = """
            SELECT COALESCE(SUM(
                CASE
                    WHEN mp.duration_days <= 31 THEN mp.price_halalas
                    WHEN mp.duration_days <= 93 THEN mp.price_halalas / 3
                    ELSE mp.price_halalas / 12
                END
            ), 0)
            FROM memberships m
            JOIN membership_plans mp ON m.plan_id = mp.id
            WHERE m.membership_status = 'active'
              AND m.deleted_at IS NULL
              AND mp.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun estimateTotalMrrHalalas(): Long

    @Query(
        value = """
            SELECT * FROM memberships m
            WHERE m.organization_id = :orgId
              AND m.club_id = :clubId
              AND m.membership_status IN :statuses
              AND m.deleted_at IS NULL
              AND m.end_date >= :today
              AND m.end_date <= :cutoffDate
            ORDER BY m.end_date ASC
        """,
        countQuery = """
            SELECT COUNT(*) FROM memberships m
            WHERE m.organization_id = :orgId
              AND m.club_id = :clubId
              AND m.membership_status IN :statuses
              AND m.deleted_at IS NULL
              AND m.end_date >= :today
              AND m.end_date <= :cutoffDate
        """,
        nativeQuery = true,
    )
    fun findExpiringMemberships(
        @Param("orgId") orgId: Long,
        @Param("clubId") clubId: Long,
        @Param("statuses") statuses: List<String>,
        @Param("today") today: LocalDate,
        @Param("cutoffDate") cutoffDate: LocalDate,
        pageable: Pageable,
    ): Page<Membership>
}
