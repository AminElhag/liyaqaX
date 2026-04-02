package com.liyaqa.common.audit

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@MappedSuperclass
abstract class AuditEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // Internal primary key — NEVER expose this in API responses or accept it as input
    val id: Long = 0,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)

// Soft delete helper — call instead of repository.delete()
fun AuditEntity.softDelete() {
    this.deletedAt = Instant.now()
}

val AuditEntity.isDeleted: Boolean
    get() = deletedAt != null
