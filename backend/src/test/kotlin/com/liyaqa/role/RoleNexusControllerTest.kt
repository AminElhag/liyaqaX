package com.liyaqa.role

import com.liyaqa.permission.Permission
import com.liyaqa.permission.PermissionRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.rbac.RolePermission
import com.liyaqa.rbac.RolePermissionRepository
import com.liyaqa.rbac.UserRole
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.security.JwtService
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleNexusControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtService: JwtService

    @Autowired
    lateinit var roleRepository: RoleRepository

    @Autowired
    lateinit var permissionRepository: PermissionRepository

    @Autowired
    lateinit var rolePermissionRepository: RolePermissionRepository

    @Autowired
    lateinit var userRoleRepository: UserRoleRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @MockBean
    lateinit var permissionService: PermissionService

    private val testRoleId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @BeforeEach
    fun setup() {
        cleanup()
    }

    @AfterEach
    fun cleanup() {
        userRoleRepository.deleteAllInBatch()
        rolePermissionRepository.deleteAllInBatch()
        roleRepository.deleteAllInBatch()
        permissionRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
    }

    private fun nexusToken(vararg permissions: String): String {
        permissions.forEach { perm ->
            whenever(permissionService.hasPermission(testRoleId, perm)).thenReturn(true)
        }
        return "Bearer ${
            jwtService.generateToken(
                "test-user",
                mapOf("roleId" to testRoleId.toString(), "scope" to "platform"),
            )
        }"
    }

    private fun clubToken(): String =
        "Bearer ${
            jwtService.generateToken(
                "club-user",
                mapOf("roleId" to noPermRoleId.toString(), "scope" to "club"),
            )
        }"

    private fun forbiddenToken(): String =
        "Bearer ${jwtService.generateToken("no-perm", mapOf("roleId" to noPermRoleId.toString(), "scope" to "platform"))}"

    private fun createPlatformRole(
        name: String = "Custom Role",
        isSystem: Boolean = false,
    ): Role =
        roleRepository.save(
            Role(nameAr = name, nameEn = name, scope = "platform", isSystem = isSystem),
        )

    private fun createPermission(code: String): Permission =
        permissionRepository.save(
            Permission(code = code, resource = code.substringBefore(':'), action = code.substringAfter(':')),
        )

    // ── List ─────────────────────────────────────────────────────────────────

    @Test
    fun `GET list returns platform roles`() {
        createPlatformRole("Admin", isSystem = true)
        createPlatformRole("Custom")

        mockMvc.get("/api/v1/nexus/roles") {
            header("Authorization", nexusToken("role:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()", equalTo(2))
        }
    }

    // ── Create ───────────────────────────────────────────────────────────────

    @Test
    fun `POST creates platform role`() {
        mockMvc.post("/api/v1/nexus/roles") {
            header("Authorization", nexusToken("role:create"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"New Role"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id", notNullValue())
            jsonPath("$.nameEn", equalTo("New Role"))
            jsonPath("$.scope", equalTo("platform"))
            jsonPath("$.isSystem", equalTo(false))
        }
    }

    @Test
    fun `POST returns 409 for duplicate name`() {
        createPlatformRole("Existing")

        mockMvc.post("/api/v1/nexus/roles") {
            header("Authorization", nexusToken("role:create"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Existing"}"""
        }.andExpect {
            status { isConflict() }
        }
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Test
    fun `PATCH updates role name`() {
        val role = createPlatformRole("Old Name")

        mockMvc.patch("/api/v1/nexus/roles/${role.publicId}") {
            header("Authorization", nexusToken("role:update"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"New Name"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.nameEn", equalTo("New Name"))
        }
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Test
    fun `DELETE succeeds for clean custom role`() {
        val role = createPlatformRole("To Delete")

        mockMvc.delete("/api/v1/nexus/roles/${role.publicId}") {
            header("Authorization", nexusToken("role:delete"))
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `DELETE returns 409 for system role`() {
        val role = createPlatformRole("System", isSystem = true)

        mockMvc.delete("/api/v1/nexus/roles/${role.publicId}") {
            header("Authorization", nexusToken("role:delete"))
        }.andExpect {
            status { isConflict() }
            jsonPath("$.detail", equalTo("System roles cannot be deleted."))
        }
    }

    @Test
    fun `DELETE returns 409 for role with staff`() {
        val role = createPlatformRole("With Staff")
        val user = userRepository.save(User(email = "staff@test.com", passwordHash = "hash"))
        userRoleRepository.save(UserRole(userId = user.id, roleId = role.id))

        mockMvc.delete("/api/v1/nexus/roles/${role.publicId}") {
            header("Authorization", nexusToken("role:delete"))
        }.andExpect {
            status { isConflict() }
        }
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    @Test
    fun `PUT replaces permission set`() {
        val role = createPlatformRole()
        val p1 = createPermission("member:read")
        val p2 = createPermission("member:create")
        rolePermissionRepository.save(RolePermission(roleId = role.id, permissionId = p1.id))

        mockMvc.put("/api/v1/nexus/roles/${role.publicId}/permissions") {
            header("Authorization", nexusToken("role:update"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"permissionIds":["${p2.publicId}"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()", equalTo(1))
            jsonPath("$[0].code", equalTo("member:create"))
        }
    }

    @Test
    fun `POST adds single permission`() {
        val role = createPlatformRole()
        val p = createPermission("member:read")

        mockMvc.post("/api/v1/nexus/roles/${role.publicId}/permissions/${p.publicId}") {
            header("Authorization", nexusToken("role:update"))
        }.andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(1))
        }
    }

    @Test
    fun `DELETE removes permission but not the last one`() {
        val role = createPlatformRole()
        val p = createPermission("member:read")
        rolePermissionRepository.save(RolePermission(roleId = role.id, permissionId = p.id))

        mockMvc.delete("/api/v1/nexus/roles/${role.publicId}/permissions/${p.publicId}") {
            header("Authorization", nexusToken("role:update"))
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    // ── Scope enforcement ────────────────────────────────────────────────────

    @Test
    fun `club-scope JWT calling nexus endpoint returns 403`() {
        mockMvc.get("/api/v1/nexus/roles") {
            header("Authorization", clubToken())
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `no permission returns 403`() {
        mockMvc.get("/api/v1/nexus/roles") {
            header("Authorization", forbiddenToken())
        }.andExpect {
            status { isForbidden() }
        }
    }
}
