package com.liyaqa.member

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
interface MemberNoteRepository : JpaRepository<MemberNote, Long> {
    @Query(
        value = """
            SELECT * FROM member_notes
            WHERE member_id = :memberId
              AND deleted_at IS NULL
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
        """,
        nativeQuery = true,
    )
    fun findByMemberId(
        @Param("memberId") memberId: Long,
        @Param("limit") limit: Int,
        @Param("offset") offset: Int,
    ): List<MemberNote>

    @Query(
        value = """
            SELECT mn.* FROM member_notes mn
            JOIN members m ON m.id = mn.member_id
            WHERE mn.follow_up_at BETWEEN :from AND :to
              AND mn.deleted_at IS NULL
              AND mn.note_type = 'FOLLOW_UP'
              AND m.club_id = :clubId
            ORDER BY mn.follow_up_at ASC
            LIMIT 200
        """,
        nativeQuery = true,
    )
    fun findFollowUpsDueWithin(
        @Param("clubId") clubId: Long,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): List<MemberNote>

    @Query(
        value = """
            SELECT * FROM member_notes
            WHERE DATE(follow_up_at AT TIME ZONE 'Asia/Riyadh') = :today
              AND deleted_at IS NULL
              AND note_type = 'FOLLOW_UP'
        """,
        nativeQuery = true,
    )
    fun findFollowUpsDueToday(
        @Param("today") today: LocalDate,
    ): List<MemberNote>

    @Query(
        value = """
            SELECT * FROM member_notes
            WHERE public_id = :publicId
              AND deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findByPublicId(
        @Param("publicId") publicId: UUID,
    ): MemberNote?
}
