package com.liyaqa.trainer

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface TrainerRepository : JpaRepository<Trainer, Long> {
    fun findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(
        publicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): Optional<Trainer>

    fun findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(
        organizationId: Long,
        clubId: Long,
        pageable: Pageable,
    ): Page<Trainer>

    fun findByUserIdAndDeletedAtIsNull(userId: Long): Optional<Trainer>

    fun existsByUserIdAndDeletedAtIsNull(userId: Long): Boolean

    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<Trainer>

    fun countByClubIdAndDeletedAtIsNull(clubId: Long): Long
}
