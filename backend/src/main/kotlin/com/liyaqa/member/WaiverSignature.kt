package com.liyaqa.member

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "waiver_signatures")
class WaiverSignature(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "waiver_id", nullable = false, updatable = false)
    val waiverId: Long,
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "signed_at", nullable = false)
    val signedAt: Instant = Instant.now(),
    @Column(name = "ip_address", length = 50)
    val ipAddress: String? = null,
)
