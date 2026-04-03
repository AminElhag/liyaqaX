package com.liyaqa.branch

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface BranchRepository : JpaRepository<Branch, Long> {
    fun findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(
        publicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): Optional<Branch>

    fun findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(
        organizationId: Long,
        clubId: Long,
        pageable: Pageable,
    ): Page<Branch>
}
