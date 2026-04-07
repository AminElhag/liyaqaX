package com.liyaqa.gx

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface GXWaitlistRepository : JpaRepository<GXWaitlistEntry, Long> {
    @Query(
        value = """
            SELECT * FROM gx_waitlist_entries
            WHERE class_instance_id = :classInstanceId
              AND status = 'WAITING'
            ORDER BY position ASC
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findNextWaiting(classInstanceId: Long): GXWaitlistEntry?

    @Query(
        value = """
            SELECT * FROM gx_waitlist_entries
            WHERE class_instance_id = :classInstanceId
              AND status IN ('WAITING', 'OFFERED')
        """,
        nativeQuery = true,
    )
    fun findActiveEntriesForClass(classInstanceId: Long): List<GXWaitlistEntry>

    @Query(
        value = """
            SELECT * FROM gx_waitlist_entries
            WHERE class_instance_id = :classInstanceId
              AND member_id = :memberId
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findByClassAndMember(
        classInstanceId: Long,
        memberId: Long,
    ): GXWaitlistEntry?

    @Query(
        value = """
            SELECT * FROM gx_waitlist_entries
            WHERE member_id = :memberId
              AND status IN ('WAITING', 'OFFERED')
            ORDER BY created_at DESC
        """,
        nativeQuery = true,
    )
    fun findActiveEntriesForMember(memberId: Long): List<GXWaitlistEntry>

    @Query(
        value = """
            SELECT * FROM gx_waitlist_entries
            WHERE status = 'OFFERED'
              AND notified_at < :threshold
        """,
        nativeQuery = true,
    )
    fun findExpiredOffers(threshold: Instant): List<GXWaitlistEntry>

    @Query(
        value = """
            SELECT COUNT(*) FROM gx_waitlist_entries
            WHERE class_instance_id = :classInstanceId
              AND status IN ('WAITING', 'OFFERED')
        """,
        nativeQuery = true,
    )
    fun countActiveForClass(classInstanceId: Long): Long

    @Query(
        value = """
            SELECT COALESCE(MAX(position), 0) + 1
            FROM gx_waitlist_entries
            WHERE class_instance_id = :classInstanceId
              AND status IN ('WAITING', 'OFFERED')
        """,
        nativeQuery = true,
    )
    fun nextPosition(classInstanceId: Long): Int
}
