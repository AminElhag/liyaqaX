package com.liyaqa.subscription.controller

import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtService
import com.liyaqa.subscription.entity.ClubSubscription
import com.liyaqa.subscription.entity.SubscriptionPlan
import com.liyaqa.subscription.repository.ClubSubscriptionRepository
import com.liyaqa.subscription.repository.SubscriptionPlanRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubscriptionControllerIntegrationTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var jwtService: JwtService
    @Autowired lateinit var organizationRepository: OrganizationRepository
    @Autowired lateinit var clubRepository: ClubRepository
    @Autowired lateinit var planRepository: SubscriptionPlanRepository
    @Autowired lateinit var subscriptionRepository: ClubSubscriptionRepository
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var passwordEncoder: PasswordEncoder

    @MockBean lateinit var permissionService: PermissionService

    private val roleId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var plan: SubscriptionPlan
    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"))
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club", email = "club@test.com"))
        plan = planRepository.save(SubscriptionPlan(name = "Growth", monthlyPriceHalalas = 120_000, maxBranches = 3, maxStaff = 30))
        user = userRepository.save(User(email = "admin@test.com", passwordHash = passwordEncoder.encode("Test@12345678")))
    }

    @AfterEach
    fun cleanup() {
        subscriptionRepository.deleteAllInBatch()
        planRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    private fun platformToken(vararg permissions: String): String {
        permissions.forEach { whenever(permissionService.hasPermission(roleId, it)).thenReturn(true) }
        return "Bearer ${jwtService.generateToken(user.publicId.toString(), mapOf("roleId" to roleId.toString(), "scope" to "platform"))}"
    }

    private fun forbiddenToken(): String =
        "Bearer ${jwtService.generateToken("test-user", mapOf("roleId" to noPermRoleId.toString(), "scope" to "platform"))}"

    private fun createSubscription(status: String = "ACTIVE"): ClubSubscription {
        val now = Instant.now()
        return subscriptionRepository.save(
            ClubSubscription(
                clubId = club.id, planId = plan.id, status = status,
                currentPeriodStart = now, currentPeriodEnd = now.plus(Duration.ofDays(30)),
                gracePeriodEndsAt = now.plus(Duration.ofDays(37)),
                assignedByUserId = user.id,
            ),
        )
    }

    @Test
    fun `POST nexus subscription-plans creates plan`() {
        mockMvc.post("/api/v1/nexus/subscription-plans") {
            header("Authorization", platformToken("subscription:manage"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Starter","monthlyPriceHalalas":50000,"maxBranches":1,"maxStaff":10}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.name", equalTo("Starter"))
            jsonPath("$.monthlyPriceHalalas", equalTo(50000))
        }
    }

    @Test
    fun `POST nexus subscription-plans returns 403 without subscription manage`() {
        mockMvc.post("/api/v1/nexus/subscription-plans") {
            header("Authorization", forbiddenToken())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Starter","monthlyPriceHalalas":50000,"maxBranches":1,"maxStaff":10}"""
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `GET nexus subscription-plans returns active plans`() {
        mockMvc.get("/api/v1/nexus/subscription-plans") {
            header("Authorization", platformToken("subscription:read"))
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `POST nexus clubs subscription assigns plan to club`() {
        mockMvc.post("/api/v1/nexus/clubs/${club.publicId}/subscription") {
            header("Authorization", platformToken("subscription:manage"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"planPublicId":"${plan.publicId}","periodStartDate":"${LocalDate.now()}","periodMonths":1}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status", equalTo("ACTIVE"))
            jsonPath("$.planName", equalTo("Growth"))
        }
    }

    @Test
    fun `POST nexus clubs subscription returns 409 when already active`() {
        createSubscription()

        mockMvc.post("/api/v1/nexus/clubs/${club.publicId}/subscription") {
            header("Authorization", platformToken("subscription:manage"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"planPublicId":"${plan.publicId}","periodStartDate":"${LocalDate.now()}","periodMonths":1}"""
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `POST nexus clubs subscription extend extends period`() {
        createSubscription()

        mockMvc.post("/api/v1/nexus/clubs/${club.publicId}/subscription/extend") {
            header("Authorization", platformToken("subscription:manage"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"additionalMonths":1}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", equalTo("ACTIVE"))
        }
    }

    @Test
    fun `POST nexus clubs subscription cancel cancels subscription`() {
        createSubscription()

        mockMvc.post("/api/v1/nexus/clubs/${club.publicId}/subscription/cancel") {
            header("Authorization", platformToken("subscription:manage"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", equalTo("CANCELLED"))
        }
    }

    @Test
    fun `GET nexus subscriptions returns paginated dashboard`() {
        createSubscription()

        mockMvc.get("/api/v1/nexus/subscriptions") {
            header("Authorization", platformToken("subscription:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.totalCount", equalTo(1))
            jsonPath("$.subscriptions[0].clubName", notNullValue())
        }
    }

    @Test
    fun `GET nexus subscriptions expiring returns clubs expiring in 30 days`() {
        mockMvc.get("/api/v1/nexus/subscriptions/expiring") {
            header("Authorization", platformToken("subscription:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.expiringSoon", notNullValue())
        }
    }
}
