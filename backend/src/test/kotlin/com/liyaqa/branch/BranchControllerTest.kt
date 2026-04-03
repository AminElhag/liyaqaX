package com.liyaqa.branch

import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
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
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BranchControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtService: JwtService

    @Autowired
    lateinit var organizationRepository: OrganizationRepository

    @Autowired
    lateinit var clubRepository: ClubRepository

    @Autowired
    lateinit var branchRepository: BranchRepository

    private lateinit var org: Organization
    private lateinit var club: Club

    @BeforeEach
    fun setup() {
        org =
            organizationRepository.save(
                Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"),
            )
        club =
            clubRepository.save(
                Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"),
            )
    }

    @AfterEach
    fun cleanup() {
        branchRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    private fun bearerToken(role: String): String {
        val token = jwtService.generateToken("test-user", mapOf("role" to role))
        return "Bearer $token"
    }

    private fun basePath() = "/api/v1/organizations/${org.publicId}/clubs/${club.publicId}/branches"

    private fun createBranch(): Branch =
        branchRepository.save(
            Branch(
                organizationId = org.id,
                clubId = club.id,
                nameAr = "فرع الرياض",
                nameEn = "Riyadh Branch",
                city = "Riyadh",
            ),
        )

    @Test
    fun `POST creates branch with nexus super-admin`() {
        mockMvc.post(basePath()) {
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameAr": "فرع الرياض", "nameEn": "Riyadh Branch", "city": "Riyadh"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id", notNullValue())
            jsonPath("$.nameEn", equalTo("Riyadh Branch"))
            jsonPath("$.city", equalTo("Riyadh"))
            jsonPath("$.organizationId", equalTo(org.publicId.toString()))
            jsonPath("$.clubId", equalTo(club.publicId.toString()))
            jsonPath("$.isActive", equalTo(true))
        }
    }

    @Test
    fun `POST returns 404 for non-existent organization`() {
        mockMvc.post("/api/v1/organizations/${UUID.randomUUID()}/clubs/${club.publicId}/branches") {
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameAr": "فرع", "nameEn": "Branch"}"""
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `POST returns 404 when club does not belong to org`() {
        val otherOrg =
            organizationRepository.save(
                Organization(nameAr = "منظمة أخرى", nameEn = "Other Org", email = "other@test.com"),
            )

        mockMvc.post("/api/v1/organizations/${otherOrg.publicId}/clubs/${club.publicId}/branches") {
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameAr": "فرع", "nameEn": "Branch"}"""
        }.andExpect {
            status { isNotFound() }
        }

        organizationRepository.delete(otherOrg)
    }

    @Test
    fun `GET list returns paginated branches`() {
        createBranch()

        mockMvc.get(basePath()) {
            header("Authorization", bearerToken(Roles.NEXUS_READ_ONLY_AUDITOR))
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()", equalTo(1))
            jsonPath("$.items[0].nameEn", equalTo("Riyadh Branch"))
            jsonPath("$.items[0].city", equalTo("Riyadh"))
            jsonPath("$.pagination.totalElements", equalTo(1))
        }
    }

    @Test
    fun `GET by id returns branch`() {
        val branch = createBranch()

        mockMvc.get("${basePath()}/${branch.publicId}") {
            header("Authorization", bearerToken(Roles.NEXUS_SUPPORT_AGENT))
        }.andExpect {
            status { isOk() }
            jsonPath("$.id", equalTo(branch.publicId.toString()))
            jsonPath("$.nameEn", equalTo("Riyadh Branch"))
            jsonPath("$.organizationId", equalTo(org.publicId.toString()))
            jsonPath("$.clubId", equalTo(club.publicId.toString()))
        }
    }

    @Test
    fun `GET by id returns 404 for non-existent branch`() {
        mockMvc.get("${basePath()}/${UUID.randomUUID()}") {
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `GET by id returns 404 when branch belongs to different club`() {
        val otherClub =
            clubRepository.save(
                Club(organizationId = org.id, nameAr = "نادي آخر", nameEn = "Other Club"),
            )
        val branch =
            branchRepository.save(
                Branch(organizationId = org.id, clubId = otherClub.id, nameAr = "فرع", nameEn = "Branch"),
            )

        mockMvc.get("${basePath()}/${branch.publicId}") {
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
        }.andExpect {
            status { isNotFound() }
        }

        branchRepository.delete(branch)
        clubRepository.delete(otherClub)
    }

    @Test
    fun `PATCH updates branch fields`() {
        val branch = createBranch()

        mockMvc.patch("${basePath()}/${branch.publicId}") {
            header("Authorization", bearerToken(Roles.NEXUS_SUPPORT_AGENT))
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameEn": "Updated Branch", "city": "Jeddah"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.nameEn", equalTo("Updated Branch"))
            jsonPath("$.city", equalTo("Jeddah"))
            jsonPath("$.nameAr", equalTo("فرع الرياض"))
        }
    }

    @Test
    fun `DELETE soft-deletes branch`() {
        val branch = createBranch()

        mockMvc.delete("${basePath()}/${branch.publicId}") {
            header("Authorization", bearerToken(Roles.NEXUS_SUPER_ADMIN))
        }.andExpect {
            status { isNoContent() }
        }

        mockMvc.get("${basePath()}/${branch.publicId}") {
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
            content = """{"nameAr": "فرع", "nameEn": "Branch"}"""
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `POST returns 401 without authentication`() {
        mockMvc.post(basePath()) {
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameAr": "فرع", "nameEn": "Branch"}"""
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `DELETE returns 403 for nexus read-only-auditor`() {
        val branch = createBranch()

        mockMvc.delete("${basePath()}/${branch.publicId}") {
            header("Authorization", bearerToken(Roles.NEXUS_READ_ONLY_AUDITOR))
        }.andExpect {
            status { isForbidden() }
        }
    }
}
