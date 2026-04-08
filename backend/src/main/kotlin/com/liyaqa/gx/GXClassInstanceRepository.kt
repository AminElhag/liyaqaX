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

    fun findAllByInstructorIdAndDeletedAtIsNull(instructorId: Long): List<GXClassInstance>

    fun findAllByInstructorIdAndScheduledAtBetweenAndDeletedAtIsNull(
        instructorId: Long,
        from: Instant,
        to: Instant,
    ): List<GXClassInstance>

    fun findByPublicIdAndInstructorIdAndDeletedAtIsNull(
        publicId: UUID,
        instructorId: Long,
    ): Optional<GXClassInstance>

    fun findAllByInstructorIdAndScheduledAtBetweenAndDeletedAtIsNull(
        instructorId: Long,
        from: Instant,
        to: Instant,
        pageable: Pageable,
    ): Page<GXClassInstance>

    fun findAllByScheduledAtBetweenAndInstanceStatusAndDeletedAtIsNull(
        from: Instant,
        to: Instant,
        instanceStatus: String,
    ): List<GXClassInstance>

    @Query(
        value = """
            SELECT COUNT(i.id) > 0
            FROM gx_class_instances i
            WHERE i.instructor_id = :instructorId
              AND i.status != 'cancelled'
              AND i.deleted_at IS NULL
              AND i.scheduled_at < :endAt
              AND (i.scheduled_at + (i.duration_minutes || ' minutes')::interval) > :startAt
        """,
        nativeQuery = true,
    )
    fun existsOverlappingInstance(
        instructorId: Long,
        startAt: Instant,
        endAt: Instant,
    ): Int
}
