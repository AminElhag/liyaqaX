package com.liyaqa.membership

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
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
}
