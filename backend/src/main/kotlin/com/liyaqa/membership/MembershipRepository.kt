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
