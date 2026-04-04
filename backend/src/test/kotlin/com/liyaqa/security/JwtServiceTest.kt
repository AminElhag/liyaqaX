package com.liyaqa.security

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class JwtServiceTest {
    private val jwtService =
        JwtService(
            secret = "test-only-secret-key-that-must-be-at-least-256-bits-long-for-hmac-sha256",
            expirySeconds = 3600L,
        )

    @Test
    fun `generateToken includes roleId and scope claims`() {
        val roleId = UUID.randomUUID()
        val token =
            jwtService.generateToken(
                "user-subject",
                mapOf("roleId" to roleId.toString(), "scope" to "platform"),
            )

        val claims = jwtService.parseToken(token)

        assertThat(claims.subject).isEqualTo("user-subject")
        assertThat(claims["roleId"]).isEqualTo(roleId.toString())
        assertThat(claims["scope"]).isEqualTo("platform")
    }

    @Test
    fun `generateToken does not contain legacy role claim`() {
        val token =
            jwtService.generateToken(
                "user-subject",
                mapOf("roleId" to UUID.randomUUID().toString(), "scope" to "club"),
            )

        val claims = jwtService.parseToken(token)

        assertThat(claims["role"]).isNull()
    }

    @Test
    fun `generateToken encodes subject correctly`() {
        val subject = UUID.randomUUID().toString()
        val token = jwtService.generateToken(subject, mapOf("roleId" to UUID.randomUUID().toString()))

        assertThat(jwtService.parseToken(token).subject).isEqualTo(subject)
    }

    @Test
    fun `parseToken throws on tampered token`() {
        val token = jwtService.generateToken("user", mapOf("roleId" to UUID.randomUUID().toString()))
        val tampered = token.dropLast(5) + "XXXXX"

        assertThatThrownBy { jwtService.parseToken(tampered) }
            .isInstanceOf(Exception::class.java)
    }

    @Test
    fun `parseToken throws on completely invalid input`() {
        assertThatThrownBy { jwtService.parseToken("not.a.valid.jwt") }
            .isInstanceOf(Exception::class.java)
    }
}
