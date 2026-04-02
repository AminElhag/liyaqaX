package com.liyaqa.club

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface ClubRepository : JpaRepository<Club, Long> {
    fun findByPublicIdAndOrganizationIdAndDeletedAtIsNull(
        publicId: UUID,
        organizationId: Long,
    ): Optional<Club>

    fun findAllByOrganizationIdAndDeletedAtIsNull(
        organizationId: Long,
        pageable: Pageable,
    ): Page<Club>
}
