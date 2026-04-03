package com.liyaqa.auth

import com.liyaqa.rbac.UserRole
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.Role
import com.liyaqa.role.RoleRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var roleRepository: RoleRepository

    @Autowired
    lateinit var userRoleRepository: UserRoleRepository

    @Autowired
    lateinit var passwordEncoder: PasswordEncoder

    @AfterEach
    fun cleanup() {
        userRoleRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        roleRepository.deleteAllInBatch()
    }

    private fun setupUserWithRole(
        email: String,
        password: String,
        roleNameEn: String,
        scope: String,
    ): Pair<User, Role> {
        val role =
            roleRepository.save(
                Role(nameAr = "دور تجريبي", nameEn = roleNameEn, scope = scope, isSystem = true),
            )
        val user =
            userRepository.save(
                User(email = email, passwordHash = passwordEncoder.encode(password)),
            )
        userRoleRepository.save(UserRole(userId = user.id, roleId = role.id))
        return user to role
    }

    @Test
    fun `login returns response with roleId, scope, and roleName`() {
        val (user, role) = setupUserWithRole("admin@test.com", "password123", "Super Admin", "platform")

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "admin@test.com", "password": "password123"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken", notNullValue())
            jsonPath("$.userId", equalTo(user.publicId.toString()))
            jsonPath("$.roleId", equalTo(role.publicId.toString()))
            jsonPath("$.scope", equalTo("platform"))
            jsonPath("$.roleName", equalTo("Super Admin"))
        }
    }

    @Test
    fun `login returns 401 for wrong password`() {
        setupUserWithRole("user@test.com", "correctpass", "Member", "member")

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "user@test.com", "password": "wrongpass"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `login returns 401 for non-existent user`() {
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "ghost@test.com", "password": "anypass"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }
}
