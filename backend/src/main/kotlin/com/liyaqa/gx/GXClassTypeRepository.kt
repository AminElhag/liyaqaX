package com.liyaqa.gx

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface GXClassTypeRepository : JpaRepository<GXClassType, Long> {
    fun findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(
        publicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): Optional<GXClassType>

    fun findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(
        organizationId: Long,
        clubId: Long,
        pageable: Pageable,
    ): Page<GXClassType>

    fun findAllByOrganizationIdAndClubIdAndIsActiveAndDeletedAtIsNull(
        organizationId: Long,
        clubId: Long,
        isActive: Boolean,
        pageable: Pageable,
    ): Page<GXClassType>
}
