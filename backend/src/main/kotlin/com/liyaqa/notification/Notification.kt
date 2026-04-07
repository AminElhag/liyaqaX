package com.liyaqa.notification

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notifications")
class Notification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "recipient_user_id", nullable = false)
    val recipientUserId: String,
    @Column(name = "recipient_scope", nullable = false, length = 20)
    val recipientScope: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 60)
    val type: NotificationType,
    @Column(name = "title_key", nullable = false, length = 100)
    val titleKey: String,
    @Column(name = "body_key", nullable = false, length = 100)
    val bodyKey: String,
    @Column(name = "params_json", columnDefinition = "TEXT")
    val paramsJson: String? = null,
    @Column(name = "entity_type", length = 100)
    val entityType: String? = null,
    @Column(name = "entity_id", length = 100)
    val entityId: String? = null,
    @Column(name = "read_at")
    var readAt: Instant? = null,
    @Column(name = "email_sent_at")
    var emailSentAt: Instant? = null,
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)
