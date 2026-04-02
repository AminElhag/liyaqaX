package com.arena.security

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.security.SignatureException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.UUID

class JwtServiceTest {
    private val secret = "test-secret-key-that-is-at-least-32-bytes-long!!"
    private val expiry = Duration.ofMinutes(15)
    private val jwtService = JwtService(secret, expiry)

    private val userId = UUID.randomUUID()
    private val role = Roles.CLUB_RECEPTIONIST
    private val organizationId = UUID.randomUUID()
    private val clubId = UUID.randomUUID()
    private val branchId1 = UUID.randomUUID()
    private val branchId2 = UUID.randomUUID()

    @Test
    fun `generateAccessToken creates a valid token with correct claims`() {
        val token =
            jwtService.generateAccessToken(
                userId = userId,
                role = role,
                organizationId = organizationId,
                clubId = clubId,
                branchIds = listOf(branchId1, branchId2),
            )

        val claims = jwtService.parseToken(token)

        assertEquals(userId, jwtService.getUserId(claims))
        assertEquals(role, jwtService.getRole(claims))
        assertEquals(organizationId, jwtService.getOrganizationId(claims))
        assertEquals(clubId, jwtService.getClubId(claims))
        assertEquals(listOf(branchId1, branchId2), jwtService.getBranchIds(claims))
        assertNull(jwtService.getMemberId(claims))
        assertNotNull(claims.issuedAt)
        assertNotNull(claims.expiration)
    }

    @Test
    fun `generateAccessToken includes memberId for member role`() {
        val memberId = UUID.randomUUID()
        val token =
            jwtService.generateAccessToken(
                userId = userId,
                role = Roles.MEMBER,
                memberId = memberId,
            )

        val claims = jwtService.parseToken(token)

        assertEquals(Roles.MEMBER, jwtService.getRole(claims))
        assertEquals(memberId, jwtService.getMemberId(claims))
    }

    @Test
    fun `generateAccessToken omits optional claims when not provided`() {
        val token =
            jwtService.generateAccessToken(
                userId = userId,
                role = role,
            )

        val claims = jwtService.parseToken(token)

        assertEquals(userId, jwtService.getUserId(claims))
        assertEquals(role, jwtService.getRole(claims))
        assertNull(jwtService.getOrganizationId(claims))
        assertNull(jwtService.getClubId(claims))
        assertTrue(jwtService.getBranchIds(claims).isEmpty())
        assertNull(jwtService.getMemberId(claims))
    }

    @Test
    fun `validateToken returns true for a valid token`() {
        val token = jwtService.generateAccessToken(userId = userId, role = role)

        assertTrue(jwtService.validateToken(token))
    }

    @Test
    fun `validateToken returns false for an expired token`() {
        val shortLivedService = JwtService(secret, Duration.ofSeconds(-1))
        val token = shortLivedService.generateAccessToken(userId = userId, role = role)

        assertFalse(jwtService.validateToken(token))
    }

    @Test
    fun `validateToken returns false for a tampered token`() {
        val token = jwtService.generateAccessToken(userId = userId, role = role)
        val tampered = token.dropLast(5) + "XXXXX"

        assertFalse(jwtService.validateToken(tampered))
    }

    @Test
    fun `validateToken returns false for a token signed with a different key`() {
        val otherService =
            JwtService(
                "different-secret-key-that-is-at-least-32-bytes!!",
                expiry,
            )
        val token = otherService.generateAccessToken(userId = userId, role = role)

        assertFalse(jwtService.validateToken(token))
    }

    @Test
    fun `parseToken throws ExpiredJwtException for expired token`() {
        val shortLivedService = JwtService(secret, Duration.ofSeconds(-1))
        val token = shortLivedService.generateAccessToken(userId = userId, role = role)

        assertThrows<ExpiredJwtException> {
            jwtService.parseToken(token)
        }
    }

    @Test
    fun `parseToken throws SignatureException for wrong signature`() {
        val otherService =
            JwtService(
                "different-secret-key-that-is-at-least-32-bytes!!",
                expiry,
            )
        val token = otherService.generateAccessToken(userId = userId, role = role)

        assertThrows<SignatureException> {
            jwtService.parseToken(token)
        }
    }

    @Test
    fun `getAccessTokenExpiry returns configured duration`() {
        assertEquals(Duration.ofMinutes(15), jwtService.getAccessTokenExpiry())
    }
}
