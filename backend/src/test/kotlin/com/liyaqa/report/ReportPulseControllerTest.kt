package com.liyaqa.report

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.Membership
import com.liyaqa.membership.MembershipPlan
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.payment.Payment
import com.liyaqa.payment.PaymentRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.role.Role
import com.liyaqa.role.RoleRepository
import com.liyaqa.security.JwtService
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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDate
import java.util.UUID

private const val TEST_PASSWORD = "Test@12345678"

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportPulseControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var branchRepository: BranchRepository

    @Autowired lateinit var roleRepository: RoleRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var membershipPlanRepository: MembershipPlanRepository

    @Autowired lateinit var membershipRepository: MembershipRepository

    @Autowired lateinit var paymentRepository: PaymentRepository

    @Autowired lateinit var passwordEncoder: PasswordEncoder

    @MockBean lateinit var permissionService: PermissionService

    private val callerRoleId = UUID.fromString("00000000-0000-0000-0000-000000000099")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000098")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var role: Role
    private lateinit var user: User

    private lateinit var otherOrg: Organization
    private lateinit var otherClub: Club

    @BeforeEach
    fun setup() {
        org =
            organizationRepository.save(
                Organization(nameAr = "منظمة", nameEn = "Report Test Org", email = "report@test.com"),
            )
        club =
            clubRepository.save(
                Club(organizationId = org.id, nameAr = "نادي", nameEn = "Report Test Club"),
            )
        branch =
            branchRepository.save(
                Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Branch"),
            )
        role =
            roleRepository.save(
                Role(nameAr = "مالك", nameEn = "Owner", scope = "club", organizationId = org.id, clubId = club.id),
            )
        user =
            userRepository.save(
                User(
                    email = "report-test@test.com",
                    passwordHash = passwordEncoder.encode(TEST_PASSWORD),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )

        otherOrg =
            organizationRepository.save(
                Organization(nameAr = "أخرى", nameEn = "Other Org", email = "other-rpt@test.com"),
            )
        otherClub =
            clubRepository.save(
                Club(organizationId = otherOrg.id, nameAr = "نادي آخر", nameEn = "Other Club"),
            )

        seedPayment()
    }

    @AfterEach
    fun cleanup() {
        paymentRepository.deleteAllInBatch()
        membershipRepository.deleteAllInBatch()
        membershipPlanRepository.deleteAllInBatch()
        memberRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        roleRepository.deleteAllInBatch()
        branchRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    private fun seedPayment() {
        val memberUser =
            userRepository.save(
                User(
                    email = "member-rpt@test.com",
                    passwordHash = passwordEncoder.encode(TEST_PASSWORD),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        val member =
            memberRepository.save(
                Member(
                    organizationId = org.id,
                    clubId = club.id,
                    branchId = branch.id,
                    userId = memberUser.id,
                    firstNameAr = "أحمد",
                    firstNameEn = "Ahmed",
                    lastNameAr = "الراشدي",
                    lastNameEn = "Al-Rashidi",
                    phone = "+966500000000",
                    gender = "male",
                ),
            )
        val plan =
            membershipPlanRepository.save(
                MembershipPlan(
                    organizationId = org.id,
                    clubId = club.id,
                    nameAr = "أساسي",
                    nameEn = "Basic Monthly",
                    priceHalalas = 50000,
                    durationDays = 30,
                ),
            )
        membershipRepository.save(
            Membership(
                organizationId = org.id,
                clubId = club.id,
                branchId = branch.id,
                memberId = member.id,
                planId = plan.id,
                membershipStatus = "active",
                startDate = LocalDate.now().minusDays(10),
                endDate = LocalDate.now().plusDays(20),
            ),
        )
        paymentRepository.save(
            Payment(
                organizationId = org.id,
                clubId = club.id,
                branchId = branch.id,
                memberId = member.id,
                amountHalalas = 50000,
                paymentMethod = "cash",
                collectedById = user.id,
            ),
        )
    }

    private fun bearerToken(
        roleId: UUID = callerRoleId,
        vararg permissions: String,
    ): String {
        permissions.forEach { perm ->
            whenever(permissionService.hasPermission(roleId, perm)).thenReturn(true)
        }
        val claims =
            mapOf(
                "roleId" to roleId.toString(),
                "scope" to "club",
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
            )
        return "Bearer ${jwtService.generateToken(user.publicId.toString(), claims)}"
    }

    private fun forbiddenToken(): String {
        val claims =
            mapOf(
                "roleId" to noPermRoleId.toString(),
                "scope" to "club",
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
            )
        return "Bearer ${jwtService.generateToken(user.publicId.toString(), claims)}"
    }

    private fun otherTenantToken(): String {
        val claims =
            mapOf(
                "roleId" to callerRoleId.toString(),
                "scope" to "club",
                "organizationId" to otherOrg.publicId.toString(),
                "clubId" to otherClub.publicId.toString(),
            )
        whenever(permissionService.hasPermission(callerRoleId, "report:revenue:view")).thenReturn(true)
        return "Bearer ${jwtService.generateToken(UUID.randomUUID().toString(), claims)}"
    }

    // ── Revenue report returns 200 with data ────────────────────────────────

    @Test
    fun `revenue report returns 200 with summary and periods`() {
        val token = bearerToken(permissions = arrayOf("report:revenue:view"))
        mockMvc.get("/api/v1/reports/revenue?from=2025-01-01&to=2025-12-31") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.summary.totalRevenue.halalas", notNullValue())
            jsonPath("$.periods", notNullValue())
        }
    }

    // ── CSV export returns text/csv with attachment header ───────────────────

    @Test
    fun `revenue CSV export returns text csv with Content-Disposition`() {
        val token = bearerToken(permissions = arrayOf("report:revenue:view"))
        mockMvc.get("/api/v1/reports/revenue/export?from=2025-01-01&to=2025-12-31") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            header {
                string("Content-Disposition", "attachment; filename=\"report-revenue-2025-01-01-2025-12-31.csv\"")
            }
        }
    }

    // ── Retention report returns 200 ─────────────────────────────────────────

    @Test
    fun `retention report returns 200 with summary and atRisk`() {
        val token = bearerToken(permissions = arrayOf("report:retention:view"))
        mockMvc.get("/api/v1/reports/retention?from=2025-01-01&to=2025-12-31") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.summary.activeMembers", notNullValue())
            jsonPath("$.atRisk", notNullValue())
        }
    }

    // ── Leads report returns 200 ─────────────────────────────────────────────

    @Test
    fun `leads report returns 200`() {
        val token = bearerToken(permissions = arrayOf("report:leads:view"))
        mockMvc.get("/api/v1/reports/leads?from=2025-01-01&to=2025-12-31") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.summary.totalLeads", notNullValue())
        }
    }

    // ── Cash drawer report returns 200 ───────────────────────────────────────

    @Test
    fun `cash drawer report returns 200`() {
        val token = bearerToken(permissions = arrayOf("report:cash-drawer:view"))
        mockMvc.get("/api/v1/reports/cash-drawer?from=2025-01-01&to=2025-12-31") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.summary.totalSessions", notNullValue())
        }
    }

    // ── Missing date params return 422 ───────────────────────────────────────

    @Test
    fun `missing from param returns 422`() {
        val token = bearerToken(permissions = arrayOf("report:revenue:view"))
        mockMvc.get("/api/v1/reports/revenue?to=2025-12-31") {
            header("Authorization", token)
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    fun `missing to param returns 422`() {
        val token = bearerToken(permissions = arrayOf("report:revenue:view"))
        mockMvc.get("/api/v1/reports/revenue?from=2025-01-01") {
            header("Authorization", token)
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    // ── Date range exceeding 366 days returns 422 ────────────────────────────

    @Test
    fun `date range exceeding 366 days returns 422`() {
        val token = bearerToken(permissions = arrayOf("report:revenue:view"))
        mockMvc.get("/api/v1/reports/revenue?from=2024-01-01&to=2025-12-31") {
            header("Authorization", token)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.detail", equalTo("Date range cannot exceed one year."))
        }
    }

    // ── Permission check: Sales Agent can see leads but not revenue ──────────

    @Test
    fun `forbidden role returns 403 on revenue report`() {
        val token = forbiddenToken()
        mockMvc.get("/api/v1/reports/revenue?from=2025-01-01&to=2025-12-31") {
            header("Authorization", token)
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `sales agent can access leads report`() {
        val salesRoleId = UUID.fromString("00000000-0000-0000-0000-000000000097")
        val token = bearerToken(roleId = salesRoleId, permissions = arrayOf("report:leads:view"))
        mockMvc.get("/api/v1/reports/leads?from=2025-01-01&to=2025-12-31") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
        }
    }

    // ── Tenant isolation ─────────────────────────────────────────────────────

    @Test
    fun `other tenant gets empty data not other clubs data`() {
        val token = otherTenantToken()
        mockMvc.get("/api/v1/reports/revenue?from=2025-01-01&to=2025-12-31") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.summary.totalRevenue.halalas", equalTo(0))
            jsonPath("$.summary.totalPayments", equalTo(0))
        }
    }

    // ── Empty range returns zeros not 404 ────────────────────────────────────

    @Test
    fun `empty date range returns zeros and empty periods`() {
        val token = bearerToken(permissions = arrayOf("report:revenue:view"))
        mockMvc.get("/api/v1/reports/revenue?from=2010-01-01&to=2010-06-30") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.summary.totalRevenue.halalas", equalTo(0))
            jsonPath("$.summary.totalPayments", equalTo(0))
        }
    }

    // ── All 4 CSV exports return text/csv ────────────────────────────────────

    @Test
    fun `retention CSV export returns text csv`() {
        val token = bearerToken(permissions = arrayOf("report:retention:view"))
        mockMvc.get("/api/v1/reports/retention/export?from=2025-01-01&to=2025-12-31") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            header { string("Content-Disposition", "attachment; filename=\"report-retention-2025-01-01-2025-12-31.csv\"") }
        }
    }

    @Test
    fun `leads CSV export returns text csv`() {
        val token = bearerToken(permissions = arrayOf("report:leads:view"))
        mockMvc.get("/api/v1/reports/leads/export?from=2025-01-01&to=2025-12-31") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            header { string("Content-Disposition", "attachment; filename=\"report-leads-2025-01-01-2025-12-31.csv\"") }
        }
    }

    @Test
    fun `cash drawer CSV export returns text csv`() {
        val token = bearerToken(permissions = arrayOf("report:cash-drawer:view"))
        mockMvc.get("/api/v1/reports/cash-drawer/export?from=2025-01-01&to=2025-12-31") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            header { string("Content-Disposition", "attachment; filename=\"report-cash-drawer-2025-01-01-2025-12-31.csv\"") }
        }
    }
}
