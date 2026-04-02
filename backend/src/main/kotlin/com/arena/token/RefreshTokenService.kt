package com.arena.token

import com.arena.common.exception.ErrorCodes
import com.arena.common.exception.UnauthorizedException
import com.arena.user.User
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    @Value("\${arena.jwt.refresh-token-expiry:P7D}")
    private val refreshTokenExpiry: Duration,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun createToken(
        user: User,
        deviceInfo: String? = null,
    ): String {
        val rawToken = generateRawToken()
        val tokenHash = hashToken(rawToken)

        val refreshToken =
            RefreshToken(
                user = user,
                tokenHash = tokenHash,
                expiresAt = Instant.now().plus(refreshTokenExpiry),
                deviceInfo = deviceInfo,
            )
        refreshTokenRepository.save(refreshToken)

        return rawToken
    }

    @Transactional
    fun rotateToken(
        rawToken: String,
        deviceInfo: String? = null,
    ): Pair<RefreshToken, String> {
        val tokenHash = hashToken(rawToken)
        val existing =
            refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
                ?: throw UnauthorizedException(
                    errorCode = ErrorCodes.TOKEN_INVALID,
                    message = "Invalid refresh token",
                )

        if (existing.expiresAt.isBefore(Instant.now())) {
            existing.revokedAt = Instant.now()
            refreshTokenRepository.save(existing)
            throw UnauthorizedException(
                errorCode = ErrorCodes.TOKEN_EXPIRED,
                message = "Refresh token has expired",
            )
        }

        // Revoke the old token
        existing.revokedAt = Instant.now()
        refreshTokenRepository.save(existing)

        // Issue a new one
        val newRawToken = generateRawToken()
        val newTokenHash = hashToken(newRawToken)

        val newRefreshToken =
            RefreshToken(
                user = existing.user,
                tokenHash = newTokenHash,
                expiresAt = Instant.now().plus(refreshTokenExpiry),
                deviceInfo = deviceInfo,
            )
        refreshTokenRepository.save(newRefreshToken)

        return newRefreshToken to newRawToken
    }

    @Transactional
    fun revokeToken(rawToken: String) {
        val tokenHash = hashToken(rawToken)
        val existing =
            refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
                ?: throw UnauthorizedException(
                    errorCode = ErrorCodes.TOKEN_INVALID,
                    message = "Invalid refresh token",
                )

        existing.revokedAt = Instant.now()
        refreshTokenRepository.save(existing)
    }

    private fun generateRawToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        fun hashToken(rawToken: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(rawToken.toByteArray())
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
        }
    }
}
