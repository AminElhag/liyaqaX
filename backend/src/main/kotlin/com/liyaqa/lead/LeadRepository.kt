package com.liyaqa.lead

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface LeadRepository : JpaRepository<Lead, Long> {
    fun findByPublicIdAndClubIdAndDeletedAtIsNull(
        publicId: UUID,
        clubId: Long,
    ): Optional<Lead>

    fun findAllByClubIdAndDeletedAtIsNull(
        clubId: Long,
        pageable: Pageable,
    ): Page<Lead>

    fun countByLeadSourceIdAndDeletedAtIsNull(leadSourceId: Long): Long

    @Query(
        """
        SELECT l.leadSourceId, COUNT(l)
        FROM Lead l
        WHERE l.clubId = :clubId AND l.deletedAt IS NULL AND l.leadSourceId IS NOT NULL
        GROUP BY l.leadSourceId
        """,
    )
    fun countByClubIdGroupedBySourceIdRaw(clubId: Long): List<Array<Any>>
}

fun LeadRepository.countByClubIdGroupedBySourceId(clubId: Long): Map<Long, Long> =
    countByClubIdGroupedBySourceIdRaw(clubId).associate { row ->
        (row[0] as Long) to (row[1] as Long)
    }
