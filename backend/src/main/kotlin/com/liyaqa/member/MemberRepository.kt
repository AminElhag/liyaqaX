package com.liyaqa.member

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface MemberRepository : JpaRepository<Member, Long> {
    fun findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(
        publicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): Optional<Member>

    fun findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(
        organizationId: Long,
        clubId: Long,
        pageable: Pageable,
    ): Page<Member>

    fun findByUserIdAndDeletedAtIsNull(userId: Long): Optional<Member>

    fun existsByUserIdAndDeletedAtIsNull(userId: Long): Boolean

    fun findByPhoneAndDeletedAtIsNull(phone: String): Optional<Member>

    fun findByPhoneAndClubIdAndDeletedAtIsNull(
        phone: String,
        clubId: Long,
    ): Optional<Member>

    fun findAllByClubIdAndMembershipStatusAndDeletedAtIsNull(
        clubId: Long,
        membershipStatus: String,
        pageable: Pageable,
    ): Page<Member>

    fun countByMembershipStatusAndDeletedAtIsNull(membershipStatus: String): Long

    @org.springframework.data.jpa.repository.Query(
        value = """
            SELECT COUNT(*) FROM members
            WHERE deleted_at IS NULL
              AND joined_at >= :since
        """,
        nativeQuery = true,
    )
    fun countNewMembersSince(
        @org.springframework.data.repository.query.Param("since") since: java.time.LocalDate,
    ): Long

    fun countByOrganizationIdAndMembershipStatusAndDeletedAtIsNull(
        organizationId: Long,
        membershipStatus: String,
    ): Long

    fun countByClubIdAndMembershipStatusAndDeletedAtIsNull(
        clubId: Long,
        membershipStatus: String,
    ): Long

    fun countByBranchIdAndMembershipStatusAndDeletedAtIsNull(
        branchId: Long,
        membershipStatus: String,
    ): Long

    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<Member>

    @org.springframework.data.jpa.repository.Query(
        value = """
            SELECT m.* FROM members m
            LEFT JOIN users u ON m.user_id = u.id
            WHERE m.deleted_at IS NULL
              AND (
                  LOWER(m.first_name_en) LIKE LOWER(CONCAT('%', :q, '%'))
                  OR LOWER(m.last_name_en) LIKE LOWER(CONCAT('%', :q, '%'))
                  OR LOWER(m.first_name_ar) LIKE LOWER(CONCAT('%', :q, '%'))
                  OR LOWER(m.last_name_ar) LIKE LOWER(CONCAT('%', :q, '%'))
                  OR m.phone LIKE CONCAT('%', :q, '%')
                  OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
              )
        """,
        countQuery = """
            SELECT COUNT(*) FROM members m
            LEFT JOIN users u ON m.user_id = u.id
            WHERE m.deleted_at IS NULL
              AND (
                  LOWER(m.first_name_en) LIKE LOWER(CONCAT('%', :q, '%'))
                  OR LOWER(m.last_name_en) LIKE LOWER(CONCAT('%', :q, '%'))
                  OR LOWER(m.first_name_ar) LIKE LOWER(CONCAT('%', :q, '%'))
                  OR LOWER(m.last_name_ar) LIKE LOWER(CONCAT('%', :q, '%'))
                  OR m.phone LIKE CONCAT('%', :q, '%')
                  OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
              )
        """,
        nativeQuery = true,
    )
    fun searchAcrossOrgs(
        @org.springframework.data.repository.query.Param("q") q: String,
        pageable: Pageable,
    ): Page<Member>
}
