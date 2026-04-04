package com.liyaqa.club

import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtService
import org.hamcrest.Matchers.equalTo
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
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClubControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtService: JwtService

    @Autowired
    lateinit var organizationRepository: OrganizationRepository

    @Autowired
    lateinit var clubRepository: ClubRepository

    @MockBean
    lateinit var permissionService: PermissionService

    private val testRoleId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private lateinit var org: Organization

    @BeforeEach
    fun setup() {
        org =
            organizationRepository.save(
                Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"),
            )
    }

    @AfterEach
    fun cleanup() {
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    private fun bearerToken(vararg permissions: String): String {
        permissions.forEach { perm ->
            whenever(permissionService.hasPermission(testRoleId, perm)).thenReturn(true)
        }
        return "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to testRoleId.toString()))}"
    }

    private fun forbiddenToken(): String = "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to noPermRoleId.toString()))}"

    private fun basePath() = "/api/v1/organizations/${org.publicId}/clubs"

    private fun createClub(): Club =
        clubRepository.save(
            Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"),
        )

    @Test
    fun `POST creates club with nexus super-admin`() {
        mockMvc.post(basePath()) {
            header("Authorization", bearerToken("club:create"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameAr": "نادي جديد", "nameEn": "New Club"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id", notNullValue())
            jsonPath("$.nameEn", equalTo("New Club"))
            jsonPath("$.organizationId", equalTo(org.publicId.toString()))
        }
    }

    @Test
    fun `POST returns 404 for non-existent organization`() {
        mockMvc.post("/api/v1/organizations/${UUID.randomUUID()}/clubs") {
            header("Authorization", bearerToken("club:create"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameAr": "نادي", "nameEn": "Club"}"""
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `GET list returns paginated clubs`() {
        createClub()

        mockMvc.get(basePath()) {
            header("Authorization", bearerToken("club:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()", equalTo(1))
            jsonPath("$.pagination.totalElements", equalTo(1))
        }
    }

    @Test
    fun `GET by id returns club`() {
        val club = createClub()

        mockMvc.get("${basePath()}/${club.publicId}") {
            header("Authorization", bearerToken("club:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.id", equalTo(club.publicId.toString()))
            jsonPath("$.nameEn", equalTo("Test Club"))
        }
    }

    @Test
    fun `GET by id returns 404 for non-existent club`() {
        mockMvc.get("${basePath()}/${UUID.randomUUID()}") {
            header("Authorization", bearerToken("club:read"))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `PATCH updates club`() {
        val club = createClub()

        mockMvc.patch("${basePath()}/${club.publicId}") {
            header("Authorization", bearerToken("club:update"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameEn": "Updated Club"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.nameEn", equalTo("Updated Club"))
        }
    }

    @Test
    fun `DELETE soft-deletes club`() {
        val club = createClub()

        mockMvc.delete("${basePath()}/${club.publicId}") {
            header("Authorization", bearerToken("club:delete"))
        }.andExpect {
            status { isNoContent() }
        }

        mockMvc.get("${basePath()}/${club.publicId}") {
            header("Authorization", bearerToken("club:read"))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `POST returns 403 for club owner`() {
        mockMvc.post(basePath()) {
            header("Authorization", forbiddenToken())
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameAr": "نادي", "nameEn": "Club"}"""
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `POST returns 401 without authentication`() {
        mockMvc.post(basePath()) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameAr": "نادي", "nameEn": "Club"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `DELETE returns 403 for nexus read-only-auditor`() {
        val club = createClub()

        mockMvc.delete("${basePath()}/${club.publicId}") {
            header("Authorization", forbiddenToken())
        }.andExpect {
            status { isForbidden() }
        }
    }
}
