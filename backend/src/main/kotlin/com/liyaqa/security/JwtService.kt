package com.liyaqa.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date

@Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiry-seconds:3600}") private val expirySeconds: Long,
) {
    private val key by lazy { Keys.hmacShaKeyFor(secret.toByteArray()) }

    fun generateToken(
        subject: String,
        claims: Map<String, Any>,
    ): String =
        Jwts.builder()
            .subject(subject)
            .claims(claims)
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(expirySeconds)))
            .signWith(key)
            .compact()

    fun generateRegistrationToken(
        phone: String,
        clubId: Long,
    ): String =
        Jwts.builder()
            .subject(phone)
            .claims(
                mapOf(
                    "scope" to "registration",
                    "phone" to phone,
                    "clubId" to clubId,
                ),
            )
            .issuedAt(Date.from(Instant.now()))
            .expiration(Date.from(Instant.now().plusSeconds(REGISTRATION_TOKEN_TTL_SECONDS)))
            .signWith(key)
            .compact()

    fun parseToken(token: String): Claims =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

    fun parseRegistrationToken(token: String): Claims {
        val claims = parseToken(token)
        val scope = claims["scope"] as? String
        if (scope != "registration") {
            throw io.jsonwebtoken.JwtException("Invalid token scope")
        }
        return claims
    }

    companion object {
        private const val REGISTRATION_TOKEN_TTL_SECONDS = 900L // 15 minutes
    }
}
