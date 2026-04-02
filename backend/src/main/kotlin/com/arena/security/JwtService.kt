package com.arena.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${arena.jwt.secret}")
    private val secret: String,
    @Value("\${arena.jwt.access-token-expiry:PT15M}")
    private val accessTokenExpiry: Duration,
) {
    private val signingKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    fun generateAccessToken(
        userId: UUID,
        role: String,
        organizationId: UUID? = null,
        clubId: UUID? = null,
        branchIds: List<UUID> = emptyList(),
        memberId: UUID? = null,
    ): String {
        val now = Instant.now()
        val claims =
            mutableMapOf<String, Any>(
                "role" to role,
            )
        organizationId?.let { claims["organizationId"] = it.toString() }
        clubId?.let { claims["clubId"] = it.toString() }
        if (branchIds.isNotEmpty()) {
            claims["branchIds"] = branchIds.map { it.toString() }
        }
        memberId?.let { claims["memberId"] = it.toString() }

        return Jwts.builder()
            .subject(userId.toString())
            .claims(claims)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTokenExpiry)))
            .signWith(signingKey)
            .compact()
    }

    fun parseToken(token: String): Claims {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }

    fun validateToken(token: String): Boolean {
        return try {
            parseToken(token)
            true
        } catch (_: ExpiredJwtException) {
            false
        } catch (_: JwtException) {
            false
        }
    }

    fun getUserId(claims: Claims): UUID = UUID.fromString(claims.subject)

    fun getRole(claims: Claims): String = claims["role"] as String

    fun getOrganizationId(claims: Claims): UUID? = (claims["organizationId"] as? String)?.let { UUID.fromString(it) }

    fun getClubId(claims: Claims): UUID? = (claims["clubId"] as? String)?.let { UUID.fromString(it) }

    @Suppress("UNCHECKED_CAST")
    fun getBranchIds(claims: Claims): List<UUID> =
        (claims["branchIds"] as? List<String>)
            ?.map { UUID.fromString(it) }
            ?: emptyList()

    fun getMemberId(claims: Claims): UUID? = (claims["memberId"] as? String)?.let { UUID.fromString(it) }

    fun getAccessTokenExpiry(): Duration = accessTokenExpiry
}
