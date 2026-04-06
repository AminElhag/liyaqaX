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
}
