package com.liyaqa.member

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "member_notes")
class MemberNote(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    val createdByUserId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 20)
    val noteType: MemberNoteType = MemberNoteType.GENERAL,
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    val content: String,
    @Column(name = "follow_up_at")
    val followUpAt: Instant? = null,
) : AuditEntity()
