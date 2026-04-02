package com.liyaqa.club

import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.security.JwtService
import com.liyaqa.security.Roles
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
class ClubControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtService: JwtService

    @Autowired
    lateinit var organizationRepository: OrganizationRepository

    @Autowired
    lateinit var clubRepository: ClubRepository

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

    private fun bearerToken(role: String): String {
        val token = jwtService.generateToken("test-user", mapOf("role" to role))
        return "Bearer $token"
    }

    private fun basePath() = "/api/v1/organizations/${org.publicId}/clubs"

    private fun createClub(): Club =
        clubRepository.save(
            Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"),
        )

    @Test
    fun `POST creates club with nexus super-admin`() {
        mockMvc.post(basePath()) {
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
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
        mockMvc.post("/api/v1/organizations/${java.util.UUID.randomUUID()}/clubs") {
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
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
            header("Authorization", bearerToken(Roles.NEXUS_READ_ONLY_AUDITOR))
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
            header("Authorization", bearerToken(Roles.NEXUS_SUPPORT_AGENT))
        }.andExpect {
            status { isOk() }
            jsonPath("$.id", equalTo(club.publicId.toString()))
            jsonPath("$.nameEn", equalTo("Test Club"))
        }
    }

    @Test
    fun `GET by id returns 404 for non-existent club`() {
        mockMvc.get("${basePath()}/${java.util.UUID.randomUUID()}") {
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `PATCH updates club`() {
        val club = createClub()

        mockMvc.patch("${basePath()}/${club.publicId}") {
            header("Authorization", bearerToken(Roles.NEXUS_SUPPORT_AGENT))
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
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
        }.andExpect {
            status { isNoContent() }
        }

        mockMvc.get("${basePath()}/${club.publicId}") {
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `POST returns 403 for club owner`() {
        mockMvc.post(basePath()) {
            header("Authorization", bearerToken(Roles.CLUB_OWNER))
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
            header("Authorization", bearerToken(Roles.NEXUS_READ_ONLY_AUDITOR))
        }.andExpect {
            status { isForbidden() }
        }
    }
}
