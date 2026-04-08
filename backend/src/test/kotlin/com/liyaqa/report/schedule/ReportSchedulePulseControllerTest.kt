package com.liyaqa.report.schedule

import com.fasterxml.jackson.databind.ObjectMapper
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.report.builder.ReportResult
import com.liyaqa.report.builder.ReportResultRepository
import com.liyaqa.report.builder.ReportTemplate
import com.liyaqa.report.builder.ReportTemplateRepository
import com.liyaqa.role.Role
import com.liyaqa.role.RoleRepository
import com.liyaqa.security.JwtService
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.hamcrest.Matchers.containsString
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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.LocalDate
import java.util.UUID

private const val TEST_PASSWORD = "Test@12345678"

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportSchedulePulseControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var roleRepository: RoleRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var reportTemplateRepository: ReportTemplateRepository

    @Autowired lateinit var reportResultRepository: ReportResultRepository

    @Autowired lateinit var reportScheduleRepository: ReportScheduleRepository

    @Autowired lateinit var passwordEncoder: PasswordEncoder

    @Autowired lateinit var objectMapper: ObjectMapper

    @MockBean lateinit var permissionService: PermissionService

    @MockBean lateinit var reportEmailService: ReportEmailService

    private val callerRoleId = UUID.fromString("00000000-0000-0000-0000-000000000099")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var otherOrg: Organization
    private lateinit var otherClub: Club
    private lateinit var role: Role
    private lateinit var user: User
    private lateinit var template: ReportTemplate

    @BeforeEach
    fun setup() {
        org =
            organizationRepository.save(
                Organization(nameAr = "منظمة", nameEn = "Schedule Test Org", email = "sched@test.com"),
            )
        club =
            clubRepository.save(
                Club(organizationId = org.id, nameAr = "نادي", nameEn = "Schedule Test Club"),
            )
        otherOrg =
            organizationRepository.save(
                Organization(nameAr = "أخرى", nameEn = "Other Sched Org", email = "other-sched@test.com"),
            )
        otherClub =
            clubRepository.save(
                Club(organizationId = otherOrg.id, nameAr = "نادي آخر", nameEn = "Other Sched Club"),
            )
        role =
            roleRepository.save(
                Role(nameAr = "مالك", nameEn = "Owner", scope = "club", organizationId = org.id, clubId = club.id),
            )
        user =
            userRepository.save(
                User(
                    email = "sched-test@test.com",
                    passwordHash = passwordEncoder.encode(TEST_PASSWORD),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        template =
            reportTemplateRepository.save(
                ReportTemplate(
                    clubId = club.id,
                    name = "Test Revenue Report",
                    metrics = """["revenue"]""",
                    dimensions = """["month"]""",
                ),
            )
    }

    @AfterEach
    fun cleanup() {
        reportScheduleRepository.deleteAllInBatch()
        reportResultRepository.deleteAllInBatch()
        reportTemplateRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        roleRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    private fun bearerToken(): String {
        whenever(permissionService.hasPermission(callerRoleId, "report:custom:run")).thenReturn(true)
        val claims =
            mapOf(
                "roleId" to callerRoleId.toString(),
                "scope" to "club",
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
            )
        return "Bearer ${jwtService.generateToken(user.publicId.toString(), claims)}"
    }

    private fun otherTenantToken(): String {
        whenever(permissionService.hasPermission(callerRoleId, "report:custom:run")).thenReturn(true)
        val claims =
            mapOf(
                "roleId" to callerRoleId.toString(),
                "scope" to "club",
                "organizationId" to otherOrg.publicId.toString(),
                "clubId" to otherClub.publicId.toString(),
            )
        return "Bearer ${jwtService.generateToken(UUID.randomUUID().toString(), claims)}"
    }

    private fun scheduleUrl() = "/api/v1/report-templates/${template.publicId}/schedule"

    // ── Rule 1: one schedule per template ──

    @Test
    fun `create schedule returns 201`() {
        val token = bearerToken()
        mockMvc.post(scheduleUrl()) {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"frequency":"daily","recipients":["owner@test.com"]}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.frequency") { value("daily") }
            jsonPath("$.isActive") { value(true) }
            jsonPath("$.templateName") { value("Test Revenue Report") }
        }
    }

    @Test
    fun `create second schedule for same template returns 409`() {
        val token = bearerToken()
        mockMvc.post(scheduleUrl()) {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"frequency":"daily","recipients":["a@test.com"]}"""
        }.andExpect { status { isCreated() } }

        mockMvc.post(scheduleUrl()) {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"frequency":"weekly","recipients":["b@test.com"]}"""
        }.andExpect { status { isConflict() } }
    }

    // ── Rule 2: max 10 recipients ──

    @Test
    fun `create with 11 recipients returns 422`() {
        val recipients = (1..11).map { "user$it@test.com" }
        val token = bearerToken()
        mockMvc.post(scheduleUrl()) {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content =
                objectMapper.writeValueAsString(
                    mapOf("frequency" to "daily", "recipients" to recipients),
                )
        }.andExpect { status { isUnprocessableEntity() } }
    }

    // ── Rule 3: valid email format ──

    @Test
    fun `create with invalid email returns 422`() {
        val token = bearerToken()
        mockMvc.post(scheduleUrl()) {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"frequency":"daily","recipients":["not-an-email"]}"""
        }.andExpect { status { isUnprocessableEntity() } }
    }

    // ── Rule 4: template must belong to this club ──

    @Test
    fun `create for other clubs template returns 404`() {
        val token = otherTenantToken()
        mockMvc.post(scheduleUrl()) {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"frequency":"daily","recipients":["a@test.com"]}"""
        }.andExpect { status { isNotFound() } }
    }

    // ── Rule 5: pause / resume ──

    @Test
    fun `patch isActive false pauses schedule`() {
        val token = bearerToken()
        mockMvc.post(scheduleUrl()) {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"frequency":"daily","recipients":["a@test.com"]}"""
        }.andExpect { status { isCreated() } }

        mockMvc.patch(scheduleUrl()) {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"isActive":false}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.isActive") { value(false) }
        }
    }

    // ── Delete + GET 404 ──

    @Test
    fun `delete then GET returns 404`() {
        val token = bearerToken()
        mockMvc.post(scheduleUrl()) {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"frequency":"weekly","recipients":["a@test.com"]}"""
        }.andExpect { status { isCreated() } }

        mockMvc.delete(scheduleUrl()) {
            header("Authorization", token)
        }.andExpect { status { isNoContent() } }

        mockMvc.get(scheduleUrl()) {
            header("Authorization", token)
        }.andExpect { status { isNotFound() } }
    }

    // ── PDF export ──

    @Test
    fun `export pdf with result returns application pdf`() {
        reportResultRepository.save(
            ReportResult(
                templateId = template.id,
                runByUserId = user.publicId.toString(),
                dateFrom = LocalDate.of(2026, 1, 1),
                dateTo = LocalDate.of(2026, 1, 31),
                resultJson = """[{"month":"2026-01","revenue":50000}]""",
                rowCount = 1,
            ),
        )

        val token = bearerToken()
        mockMvc.get("/api/v1/report-templates/${template.publicId}/export/pdf") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            header { string("Content-Disposition", containsString(".pdf")) }
            content { contentType(MediaType.APPLICATION_PDF) }
        }
    }

    @Test
    fun `export pdf with no result returns 404`() {
        val token = bearerToken()
        mockMvc.get("/api/v1/report-templates/${template.publicId}/export/pdf") {
            header("Authorization", token)
        }.andExpect { status { isNotFound() } }
    }
}
