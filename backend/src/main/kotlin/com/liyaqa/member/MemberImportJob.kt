package com.liyaqa.member

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "member_import_jobs")
class MemberImportJob(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "club_id", nullable = false)
    val clubId: Long,
    @Column(name = "created_by_user_id", nullable = false)
    val createdByUserId: Long,
    @Column(name = "file_name", nullable = false)
    val fileName: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: MemberImportJobStatus = MemberImportJobStatus.QUEUED,
    @Column(name = "total_rows")
    var totalRows: Int? = null,
    @Column(name = "imported_count")
    var importedCount: Int? = null,
    @Column(name = "skipped_count")
    var skippedCount: Int? = null,
    @Column(name = "error_count")
    var errorCount: Int? = null,
    @Column(name = "error_detail", columnDefinition = "TEXT")
    var errorDetail: String? = null,
    @Column(name = "started_at")
    var startedAt: Instant? = null,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
