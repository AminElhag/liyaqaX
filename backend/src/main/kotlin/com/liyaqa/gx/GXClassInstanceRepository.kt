package com.liyaqa.gx

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface GXClassInstanceRepository : JpaRepository<GXClassInstance, Long> {
    fun findByPublicIdAndOrganizationIdAndDeletedAtIsNull(
        publicId: UUID,
        organizationId: Long,
    ): Optional<GXClassInstance>

    fun findAllByOrganizationIdAndBranchIdAndDeletedAtIsNull(
        organizationId: Long,
        branchId: Long,
        pageable: Pageable,
    ): Page<GXClassInstance>

    fun findAllByOrganizationIdAndBranchIdAndScheduledAtBetweenAndDeletedAtIsNull(
        organizationId: Long,
        branchId: Long,
        from: Instant,
        to: Instant,
        pageable: Pageable,
    ): Page<GXClassInstance>

    @Query(
        """
        SELECT COUNT(i) > 0
        FROM GXClassInstance i
        WHERE i.instructorId = :instructorId
          AND i.instanceStatus != 'cancelled'
          AND i.deletedAt IS NULL
          AND i.scheduledAt < :endAt
          AND (i.scheduledAt + (i.durationMinutes * 60000000000L)) > :startAt
        """,
    )
    fun existsOverlappingInstance(
        instructorId: Long,
        startAt: Instant,
        endAt: Instant,
    ): Boolean
}
