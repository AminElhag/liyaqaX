package com.arena.user

import com.arena.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "users")
class User(
    @Column(name = "email", nullable = false, length = 255)
    val email: String,
    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,
    @Column(name = "role", nullable = false, length = 50)
    val role: String,
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,
) : AuditEntity()
