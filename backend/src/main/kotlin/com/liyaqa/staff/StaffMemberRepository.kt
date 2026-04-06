package com.liyaqa.staff

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface StaffMemberRepository : JpaRepository<StaffMember, Long> {
    fun findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(
        publicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): Optional<StaffMember>

    fun findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(
        organizationId: Long,
        clubId: Long,
        pageable: Pageable,
    ): Page<StaffMember>

    fun findByUserIdAndDeletedAtIsNull(userId: Long): Optional<StaffMember>

    fun existsByUserIdAndDeletedAtIsNull(userId: Long): Boolean

    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<StaffMember>

    fun findByUserIdAndClubIdAndDeletedAtIsNull(
        userId: Long,
        clubId: Long,
    ): Optional<StaffMember>

    fun countByClubIdAndDeletedAtIsNull(clubId: Long): Long
}
