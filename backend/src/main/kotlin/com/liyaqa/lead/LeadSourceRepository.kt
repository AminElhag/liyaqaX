package com.liyaqa.lead

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface LeadSourceRepository : JpaRepository<LeadSource, Long> {
    fun findByPublicIdAndClubIdAndDeletedAtIsNull(
        publicId: UUID,
        clubId: Long,
    ): Optional<LeadSource>

    fun findAllByClubIdAndDeletedAtIsNull(clubId: Long): List<LeadSource>

    fun existsByClubIdAndNameAndDeletedAtIsNull(
        clubId: Long,
        name: String,
    ): Boolean
}
