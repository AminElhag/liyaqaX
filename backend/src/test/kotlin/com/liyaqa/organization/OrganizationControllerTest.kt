package com.liyaqa.organization

import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtService
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.AfterEach
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
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtService: JwtService

    @Autowired
    lateinit var organizationRepository: OrganizationRepository

    @MockBean
    lateinit var permissionService: PermissionService

    private val testRoleId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @AfterEach
    fun cleanup() {
        organizationRepository.deleteAllInBatch()
    }

    private fun bearerToken(vararg permissions: String): String {
        permissions.forEach { perm ->
            whenever(permissionService.hasPermission(testRoleId, perm)).thenReturn(true)
        }
        return "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to testRoleId.toString()))}"
    }

    private fun forbiddenToken(): String = "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to noPermRoleId.toString()))}"

    private val createBody =
        """
        {
            "nameAr": "منظمة تجريبية",
            "nameEn": "Test Org",
            "email": "test@example.com"
        }
        """.trimIndent()

    private fun createOrg(): Organization =
        organizationRepository.save(
            Organization(nameAr = "منظمة", nameEn = "Test Org", email = "test@example.com"),
        )

    @Test
    fun `POST creates organization with nexus super-admin`() {
        mockMvc.post("/api/v1/organizations") {
            header("Authorization", bearerToken("organization:create"))
            contentType = MediaType.APPLICATION_JSON
            content = createBody
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id", notNullValue())
            jsonPath("$.nameEn", equalTo("Test Org"))
            jsonPath("$.email", equalTo("test@example.com"))
            jsonPath("$.country", equalTo("SA"))
        }
    }

    @Test
    fun `POST returns 409 for duplicate email`() {
        createOrg()

        mockMvc.post("/api/v1/organizations") {
            header("Authorization", bearerToken("organization:create"))
            contentType = MediaType.APPLICATION_JSON
            content = createBody
        }.andExpect {
            status { isConflict() }
            jsonPath("$.status", equalTo(409))
        }
    }

    @Test
    fun `POST returns 400 for invalid request`() {
        mockMvc.post("/api/v1/organizations") {
            header("Authorization", bearerToken("organization:create"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameAr": "", "nameEn": "", "email": "not-an-email"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET list returns paginated organizations with nexus read-only-auditor`() {
        createOrg()

        mockMvc.get("/api/v1/organizations") {
            header("Authorization", bearerToken("organization:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()", equalTo(1))
            jsonPath("$.pagination.totalElements", equalTo(1))
        }
    }

    @Test
    fun `GET by id returns organization`() {
        val org = createOrg()

        mockMvc.get("/api/v1/organizations/${org.publicId}") {
            header("Authorization", bearerToken("organization:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.id", equalTo(org.publicId.toString()))
            jsonPath("$.nameEn", equalTo("Test Org"))
        }
    }

    @Test
    fun `GET by id returns 404 for non-existent organization`() {
        mockMvc.get("/api/v1/organizations/${UUID.randomUUID()}") {
            header("Authorization", bearerToken("organization:read"))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `PATCH updates organization`() {
        val org = createOrg()

        mockMvc.patch("/api/v1/organizations/${org.publicId}") {
            header("Authorization", bearerToken("organization:update"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameEn": "Updated Org"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.nameEn", equalTo("Updated Org"))
        }
    }

    @Test
    fun `DELETE soft-deletes organization`() {
        val org = createOrg()

        mockMvc.delete("/api/v1/organizations/${org.publicId}") {
            header("Authorization", bearerToken("organization:delete"))
        }.andExpect {
            status { isNoContent() }
        }

        mockMvc.get("/api/v1/organizations/${org.publicId}") {
            header("Authorization", bearerToken("organization:read"))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `POST returns 403 for club owner`() {
        mockMvc.post("/api/v1/organizations") {
            header("Authorization", forbiddenToken())
            contentType = MediaType.APPLICATION_JSON
            content = createBody
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `POST returns 401 without authentication`() {
        mockMvc.post("/api/v1/organizations") {
            contentType = MediaType.APPLICATION_JSON
            content = createBody
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `GET returns 403 for club owner`() {
        mockMvc.get("/api/v1/organizations") {
            header("Authorization", forbiddenToken())
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `DELETE returns 403 for nexus read-only-auditor`() {
        val org = createOrg()

        mockMvc.delete("/api/v1/organizations/${org.publicId}") {
            header("Authorization", forbiddenToken())
        }.andExpect {
            status { isForbidden() }
        }
    }
}
