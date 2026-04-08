package com.liyaqa.member

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface HealthWaiverRepository : JpaRepository<HealthWaiver, Long> {
    fun findByClubIdAndIsActiveTrueAndDeletedAtIsNull(clubId: Long): Optional<HealthWaiver>

    fun findByPublicIdAndOrganizationIdAndDeletedAtIsNull(
        publicId: UUID,
        organizationId: Long,
    ): Optional<HealthWaiver>
}
