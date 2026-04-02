package com.arena.token

import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByTokenHashAndRevokedAtIsNull(tokenHash: String): RefreshToken?
}
