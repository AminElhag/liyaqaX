package com.liyaqa.membership

import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
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
class MembershipPlanPulseControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var membershipPlanRepository: MembershipPlanRepository

    @MockBean lateinit var permissionService: PermissionService

    private val callerRoleId = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000020")

    private lateinit var org: Organization
    private lateinit var club: Club

    @BeforeEach
    fun setup() {
        org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"))
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"))
    }

    @AfterEach
    fun cleanup() {
        membershipPlanRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    private fun bearerToken(vararg permissions: String): String {
        permissions.forEach { perm ->
            whenever(permissionService.hasPermission(callerRoleId, perm)).thenReturn(true)
        }
        val claims =
            mapOf(
                "roleId" to callerRoleId.toString(),
                "scope" to "club",
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
            )
        return "Bearer ${jwtService.generateToken(UUID.randomUUID().toString(), claims)}"
    }

    private fun forbiddenToken(): String {
        val claims =
            mapOf(
                "roleId" to noPermRoleId.toString(),
                "scope" to "club",
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
            )
        return "Bearer ${jwtService.generateToken(UUID.randomUUID().toString(), claims)}"
    }

    private fun createPlanBody(
        nameEn: String = "Basic Monthly",
        priceHalalas: Long = 15000,
        durationDays: Int = 30,
    ) = """
        {
            "nameAr": "شهري أساسي",
            "nameEn": "$nameEn",
            "priceHalalas": $priceHalalas,
            "durationDays": $durationDays,
            "gracePeriodDays": 3,
            "freezeAllowed": true,
            "maxFreezeDays": 14,
            "gxClassesIncluded": true,
            "ptSessionsIncluded": false,
            "sortOrder": 1
        }
        """.trimIndent()

    private fun createPlan(nameEn: String = "Basic Monthly"): MembershipPlan =
        membershipPlanRepository.save(
            MembershipPlan(
                organizationId = org.id,
                clubId = club.id,
                nameAr = "شهري أساسي",
                nameEn = nameEn,
                priceHalalas = 15000,
                durationDays = 30,
                gracePeriodDays = 3,
                freezeAllowed = true,
                maxFreezeDays = 14,
                sortOrder = 1,
            ),
        )

    // ── POST ────────────────────────────────────────────────────────────────

    @Test
    fun `POST creates plan with valid permissions`() {
        mockMvc.post("/api/v1/membership-plans") {
            header("Authorization", bearerToken("membership-plan:create"))
            contentType = MediaType.APPLICATION_JSON
            content = createPlanBody()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id", notNullValue())
            jsonPath("$.nameEn", equalTo("Basic Monthly"))
            jsonPath("$.priceHalalas", equalTo(15000))
            jsonPath("$.priceSar", equalTo("150.00"))
            jsonPath("$.durationDays", equalTo(30))
            jsonPath("$.organizationId", equalTo(org.publicId.toString()))
            jsonPath("$.clubId", equalTo(club.publicId.toString()))
        }
    }

    @Test
    fun `POST returns 403 without permission`() {
        mockMvc.post("/api/v1/membership-plans") {
            header("Authorization", forbiddenToken())
            contentType = MediaType.APPLICATION_JSON
            content = createPlanBody()
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `POST returns 401 without authentication`() {
        mockMvc.post("/api/v1/membership-plans") {
            contentType = MediaType.APPLICATION_JSON
            content = createPlanBody()
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `POST returns 400 for zero price`() {
        mockMvc.post("/api/v1/membership-plans") {
            header("Authorization", bearerToken("membership-plan:create"))
            contentType = MediaType.APPLICATION_JSON
            content = createPlanBody(priceHalalas = 0)
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST returns 409 for duplicate nameEn`() {
        createPlan("Basic Monthly")

        mockMvc.post("/api/v1/membership-plans") {
            header("Authorization", bearerToken("membership-plan:create"))
            contentType = MediaType.APPLICATION_JSON
            content = createPlanBody(nameEn = "Basic Monthly")
        }.andExpect {
            status { isConflict() }
        }
    }

    // ── GET list ────────────────────────────────────────────────────────────

    @Test
    fun `GET returns paginated plans`() {
        createPlan()

        mockMvc.get("/api/v1/membership-plans") {
            header("Authorization", bearerToken("membership-plan:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()", equalTo(1))
            jsonPath("$.pagination.totalElements", equalTo(1))
            jsonPath("$.items[0].nameEn", equalTo("Basic Monthly"))
        }
    }

    // ── GET by id ───────────────────────────────────────────────────────────

    @Test
    fun `GET by id returns plan`() {
        val plan = createPlan()

        mockMvc.get("/api/v1/membership-plans/${plan.publicId}") {
            header("Authorization", bearerToken("membership-plan:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.id", equalTo(plan.publicId.toString()))
            jsonPath("$.nameEn", equalTo("Basic Monthly"))
            jsonPath("$.priceSar", equalTo("150.00"))
        }
    }

    @Test
    fun `GET by id returns 404 for non-existent plan`() {
        mockMvc.get("/api/v1/membership-plans/${UUID.randomUUID()}") {
            header("Authorization", bearerToken("membership-plan:read"))
        }.andExpect {
            status { isNotFound() }
        }
    }

    // ── PATCH ───────────────────────────────────────────────────────────────

    @Test
    fun `PATCH updates plan`() {
        val plan = createPlan()

        mockMvc.patch("/api/v1/membership-plans/${plan.publicId}") {
            header("Authorization", bearerToken("membership-plan:update"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"nameEn": "Updated Plan", "priceHalalas": 20000}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.nameEn", equalTo("Updated Plan"))
            jsonPath("$.priceHalalas", equalTo(20000))
            jsonPath("$.priceSar", equalTo("200.00"))
        }
    }

    @Test
    fun `PATCH returns 422 for grace period exceeding duration`() {
        val plan = createPlan()

        mockMvc.patch("/api/v1/membership-plans/${plan.publicId}") {
            header("Authorization", bearerToken("membership-plan:update"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"gracePeriodDays": 999}"""
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @Test
    fun `DELETE soft-deletes plan`() {
        val plan = createPlan()

        mockMvc.delete("/api/v1/membership-plans/${plan.publicId}") {
            header("Authorization", bearerToken("membership-plan:delete"))
        }.andExpect {
            status { isNoContent() }
        }

        mockMvc.get("/api/v1/membership-plans/${plan.publicId}") {
            header("Authorization", bearerToken("membership-plan:read"))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `DELETE returns 403 without permission`() {
        val plan = createPlan()

        mockMvc.delete("/api/v1/membership-plans/${plan.publicId}") {
            header("Authorization", forbiddenToken())
        }.andExpect {
            status { isForbidden() }
        }
    }
}
