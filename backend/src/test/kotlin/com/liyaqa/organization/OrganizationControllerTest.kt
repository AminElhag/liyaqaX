package com.liyaqa.organization

import com.liyaqa.security.JwtService
import com.liyaqa.security.Roles
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post

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

    @AfterEach
    fun cleanup() {
        organizationRepository.deleteAllInBatch()
    }

    private fun bearerToken(role: String): String {
        val token = jwtService.generateToken("test-user", mapOf("role" to role))
        return "Bearer $token"
    }

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
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
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
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
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
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
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
            header("Authorization", bearerToken(Roles.NEXUS_READ_ONLY_AUDITOR))
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
            header("Authorization", bearerToken(Roles.NEXUS_SUPPORT_AGENT))
        }.andExpect {
            status { isOk() }
            jsonPath("$.id", equalTo(org.publicId.toString()))
            jsonPath("$.nameEn", equalTo("Test Org"))
        }
    }

    @Test
    fun `GET by id returns 404 for non-existent organization`() {
        mockMvc.get("/api/v1/organizations/${java.util.UUID.randomUUID()}") {
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `PATCH updates organization`() {
        val org = createOrg()

        mockMvc.patch("/api/v1/organizations/${org.publicId}") {
            header("Authorization", bearerToken(Roles.NEXUS_SUPPORT_AGENT))
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
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
        }.andExpect {
            status { isNoContent() }
        }

        mockMvc.get("/api/v1/organizations/${org.publicId}") {
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `POST returns 403 for club owner`() {
        mockMvc.post("/api/v1/organizations") {
            header("Authorization", bearerToken(Roles.CLUB_OWNER))
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
            header("Authorization", bearerToken(Roles.CLUB_OWNER))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `DELETE returns 403 for nexus read-only-auditor`() {
        val org = createOrg()

        mockMvc.delete("/api/v1/organizations/${org.publicId}") {
            header("Authorization", bearerToken(Roles.NEXUS_READ_ONLY_AUDITOR))
        }.andExpect {
            status { isForbidden() }
        }
    }
}
