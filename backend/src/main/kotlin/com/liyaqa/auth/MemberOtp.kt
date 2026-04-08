package com.liyaqa.auth

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "member_otps")
class MemberOtp(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "phone", nullable = false, length = 20)
    val phone: String,
    @Column(name = "otp_hash", nullable = false)
    val otpHash: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
    @Column(name = "used", nullable = false)
    var used: Boolean = false,
    @Column(name = "member_id")
    var memberId: Long? = null,
) : AuditEntity()
