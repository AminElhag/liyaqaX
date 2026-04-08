package com.liyaqa.member

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MemberImportJobRepository : JpaRepository<MemberImportJob, Long> {
    @Query(
        value = "SELECT * FROM member_import_jobs WHERE public_id = :publicId",
        nativeQuery = true,
    )
    fun findByPublicId(
        @Param("publicId") publicId: UUID,
    ): MemberImportJob?

    @Query(
        value = """
            SELECT * FROM member_import_jobs
            WHERE club_id = :clubId
            ORDER BY created_at DESC
            LIMIT 10
        """,
        nativeQuery = true,
    )
    fun findTop10ByClubIdOrderByCreatedAtDesc(
        @Param("clubId") clubId: Long,
    ): List<MemberImportJob>
}
