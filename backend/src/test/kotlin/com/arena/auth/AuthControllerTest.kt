package com.arena.auth

import com.arena.auth.dto.LoginRequest
import com.arena.auth.dto.LogoutRequest
import com.arena.auth.dto.RefreshRequest
import com.arena.security.Roles
import com.arena.user.User
import com.arena.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    private val testEmail = "test@arena.app"
    private val testPassword = "SecureP@ss123"

    @BeforeEach
    fun setUp() {
        userRepository.deleteAll()
        val user =
            User(
                email = testEmail,
                passwordHash = passwordEncoder.encode(testPassword),
                role = Roles.CLUB_RECEPTIONIST,
            )
        userRepository.save(user)
    }

    // ── Login ───────────────────────────────────────────────

    @Test
    fun `login with valid credentials returns tokens`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LoginRequest(testEmail, testPassword))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
    }

    @Test
    fun `login with wrong password returns 401`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LoginRequest(testEmail, "wrong"))),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.type").value("https://arena.app/errors/invalid-credentials"))
            .andExpect(jsonPath("$.detail").value("Invalid credentials"))
    }

    @Test
    fun `login with unknown email returns 401 with same message`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LoginRequest("unknown@arena.app", testPassword))),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Invalid credentials"))
    }

    @Test
    fun `login with disabled account returns 401`() {
        val user = userRepository.findByEmailAndDeletedAtIsNull(testEmail)!!
        user.isActive = false
        userRepository.save(user)

        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LoginRequest(testEmail, testPassword))),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.detail").value("Invalid credentials"))
    }

    @Test
    fun `login with missing email returns 400 validation error`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"password": "test"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.type").value("https://arena.app/errors/validation-failed"))
    }

    // ── Refresh ─────────────────────────────────────────────

    @Test
    fun `refresh with valid token returns new tokens`() {
        val loginResult = performLogin()
        val refreshToken = loginResult["refreshToken"] as String

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshRequest(refreshToken))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
    }

    @Test
    fun `refresh rotates token — old token is invalid after use`() {
        val loginResult = performLogin()
        val refreshToken = loginResult["refreshToken"] as String

        // First refresh succeeds
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshRequest(refreshToken))),
        )
            .andExpect(status().isOk)

        // Second refresh with same token fails
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshRequest(refreshToken))),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh with invalid token returns 401`() {
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshRequest("invalid-token"))),
        )
            .andExpect(status().isUnauthorized)
    }

    // ── Logout ──────────────────────────────────────────────

    @Test
    fun `logout revokes refresh token`() {
        val loginResult = performLogin()
        val accessToken = loginResult["accessToken"] as String
        val refreshToken = loginResult["refreshToken"] as String

        mockMvc.perform(
            post("/api/v1/auth/logout")
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LogoutRequest(refreshToken))),
        )
            .andExpect(status().isNoContent)

        // Refresh with revoked token fails
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshRequest(refreshToken))),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `logout without access token returns 401`() {
        val loginResult = performLogin()
        val refreshToken = loginResult["refreshToken"] as String

        mockMvc.perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(LogoutRequest(refreshToken))),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.type").value("https://arena.app/errors/unauthorized"))
    }

    // ── Protected endpoint ──────────────────────────────────

    @Test
    fun `request to protected endpoint without token returns 401`() {
        mockMvc.perform(
            post("/api/v1/some-protected-resource")
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.type").value("https://arena.app/errors/unauthorized"))
            .andExpect(jsonPath("$.status").value(401))
    }

    // ── Health endpoint ─────────────────────────────────────

    @Test
    fun `health endpoint is accessible without token`() {
        mockMvc.perform(
            get("/actuator/health").accept(MediaType.APPLICATION_JSON),
        )
            .andExpect(status().isOk)
    }

    // ── Helpers ─────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun performLogin(): Map<String, Any> {
        val result =
            mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(LoginRequest(testEmail, testPassword))),
            )
                .andExpect(status().isOk)
                .andReturn()

        return objectMapper.readValue(result.response.contentAsString, Map::class.java) as Map<String, Any>
    }
}
