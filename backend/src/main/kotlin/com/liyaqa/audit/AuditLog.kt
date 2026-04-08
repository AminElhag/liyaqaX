package com.liyaqa.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_logs")
class AuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "actor_id", nullable = false)
    val actorId: String,
    @Column(name = "actor_scope", nullable = false)
    val actorScope: String,
    @Column(name = "action", nullable = false)
    val action: String,
    @Column(name = "entity_type", nullable = false)
    val entityType: String,
    @Column(name = "entity_id", nullable = false)
    val entityId: String,
    @Column(name = "organization_id")
    val organizationId: String? = null,
    @Column(name = "club_id")
    val clubId: String? = null,
    @Column(name = "changes_json", columnDefinition = "TEXT")
    val changesJson: String? = null,
    @Column(name = "ip_address")
    val ipAddress: String? = null,
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
