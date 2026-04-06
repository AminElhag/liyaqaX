package com.liyaqa.nexus

import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtService
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationNexusControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @MockBean lateinit var permissionService: PermissionService

    private val roleId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @AfterEach
    fun cleanup() {
        organizationRepository.deleteAllInBatch()
    }

    private fun platformToken(vararg permissions: String): String {
        permissions.forEach { whenever(permissionService.hasPermission(roleId, it)).thenReturn(true) }
        return "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to roleId.toString(), "scope" to "platform"))}"
    }

    private fun clubToken(): String =
        "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to roleId.toString(), "scope" to "club"))}"

    private fun forbiddenToken(): String =
        "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to noPermRoleId.toString(), "scope" to "platform"))}"

    private fun createOrg(
        nameEn: String = "Test Org",
        email: String = "test@example.com",
    ): Organization =
        organizationRepository.save(
            Organization(nameAr = "منظمة", nameEn = nameEn, email = email),
        )

    @Test
    fun `GET list returns paginated organizations`() {
        createOrg()
        mockMvc.get("/api/v1/nexus/organizations") {
            header("Authorization", platformToken("organization:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()", equalTo(1))
            jsonPath("$.items[0].nameEn", equalTo("Test Org"))
        }
    }

    @Test
    fun `GET list supports search`() {
        createOrg(nameEn = "Alpha Gym", email = "alpha@test.com")
        createOrg(nameEn = "Beta Gym", email = "beta@test.com")

        mockMvc.get("/api/v1/nexus/organizations?q=Alpha") {
            header("Authorization", platformToken("organization:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.items", hasSize<Any>(1))
            jsonPath("$.items[0].nameEn", equalTo("Alpha Gym"))
        }
    }

    @Test
    fun `GET detail returns org with clubs`() {
        val org = createOrg()
        mockMvc.get("/api/v1/nexus/organizations/${org.publicId}") {
            header("Authorization", platformToken("organization:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.id", equalTo(org.publicId.toString()))
            jsonPath("$.clubs", notNullValue())
        }
    }

    @Test
    fun `POST creates organization`() {
        mockMvc.post("/api/v1/nexus/organizations") {
            header("Authorization", platformToken("organization:create"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameAr": "منظمة جديدة", "nameEn": "New Org", "email": "new@test.com"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.nameEn", equalTo("New Org"))
        }
    }

    @Test
    fun `POST returns 409 for duplicate name`() {
        createOrg(nameEn = "Duplicate Org", email = "first@test.com")

        mockMvc.post("/api/v1/nexus/organizations") {
            header("Authorization", platformToken("organization:create"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameAr": "مكرر", "nameEn": "Duplicate Org", "email": "second@test.com"}"""
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `PATCH updates organization`() {
        val org = createOrg()
        mockMvc.patch("/api/v1/nexus/organizations/${org.publicId}") {
            header("Authorization", platformToken("organization:update"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameEn": "Updated Name"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.nameEn", equalTo("Updated Name"))
        }
    }

    @Test
    fun `non-platform scope returns 403`() {
        mockMvc.get("/api/v1/nexus/organizations") {
            header("Authorization", clubToken())
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `missing permission returns 403`() {
        mockMvc.get("/api/v1/nexus/organizations") {
            header("Authorization", forbiddenToken())
        }.andExpect {
            status { isForbidden() }
        }
    }
}
