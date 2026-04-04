package com.liyaqa.membership

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface MembershipPlanRepository : JpaRepository<MembershipPlan, Long> {
    fun findByPublicIdAndOrganizationIdAndDeletedAtIsNull(
        publicId: UUID,
        organizationId: Long,
    ): Optional<MembershipPlan>

    fun findAllByClubIdAndDeletedAtIsNull(
        clubId: Long,
        pageable: Pageable,
    ): Page<MembershipPlan>

    fun findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(
        organizationId: Long,
        clubId: Long,
        pageable: Pageable,
    ): Page<MembershipPlan>

    fun existsByClubIdAndNameEnAndDeletedAtIsNull(
        clubId: Long,
        nameEn: String,
    ): Boolean

    fun existsByClubIdAndNameEnAndDeletedAtIsNullAndIdNot(
        clubId: Long,
        nameEn: String,
        id: Long,
    ): Boolean
}
