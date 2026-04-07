package com.liyaqa.pt

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
interface PTSessionRepository : JpaRepository<PTSession, Long> {
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<PTSession>

    fun findAllByTrainerIdAndScheduledAtBetweenAndDeletedAtIsNull(
        trainerId: Long,
        from: Instant,
        to: Instant,
    ): List<PTSession>

    fun findAllByTrainerIdAndScheduledAtAfterAndDeletedAtIsNull(
        trainerId: Long,
        after: Instant,
        pageable: Pageable,
    ): Page<PTSession>

    fun findAllByTrainerIdAndScheduledAtBeforeAndDeletedAtIsNull(
        trainerId: Long,
        before: Instant,
        pageable: Pageable,
    ): Page<PTSession>

    fun findAllByScheduledAtBetweenAndSessionStatusAndDeletedAtIsNull(
        from: Instant,
        to: Instant,
        sessionStatus: String,
    ): List<PTSession>
}
