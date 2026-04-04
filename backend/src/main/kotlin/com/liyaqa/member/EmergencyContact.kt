package com.liyaqa.member

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "emergency_contacts")
class EmergencyContact(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "name_ar", nullable = false, length = 255)
    var nameAr: String,
    @Column(name = "name_en", nullable = false, length = 255)
    var nameEn: String,
    @Column(name = "phone", nullable = false, length = 50)
    var phone: String,
    @Column(name = "relationship", length = 100)
    var relationship: String? = null,
)
