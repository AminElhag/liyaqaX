package com.liyaqa.auth

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional

@Repository
interface MemberOtpRepository : JpaRepository<MemberOtp, Long> {
    fun countByPhoneAndUsedFalseAndExpiresAtAfter(
        phone: String,
        now: Instant,
    ): Long

    fun findByPhoneAndOtpHashAndUsedFalseAndExpiresAtAfter(
        phone: String,
        otpHash: String,
        now: Instant,
    ): Optional<MemberOtp>
}
