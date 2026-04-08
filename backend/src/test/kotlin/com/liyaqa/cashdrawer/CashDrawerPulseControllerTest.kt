package com.liyaqa.cashdrawer

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.role.Role
import com.liyaqa.role.RoleRepository
import com.liyaqa.security.JwtService
import com.liyaqa.staff.StaffBranchAssignment
import com.liyaqa.staff.StaffBranchAssignmentRepository
import com.liyaqa.staff.StaffMember
import com.liyaqa.staff.StaffMemberRepository
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.LocalDate
import java.util.UUID

private const val TEST_PASSWORD = "Test@12345678"
private const val BASE_PATH = "/api/v1/cash-drawer/sessions"

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CashDrawerPulseControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var branchRepository: BranchRepository

    @Autowired lateinit var roleRepository: RoleRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var staffMemberRepository: StaffMemberRepository

    @Autowired lateinit var staffBranchAssignmentRepository: StaffBranchAssignmentRepository

    @Autowired lateinit var sessionRepository: CashDrawerSessionRepository

    @Autowired lateinit var entryRepository: CashDrawerEntryRepository

    @Autowired lateinit var passwordEncoder: PasswordEncoder

    @MockBean lateinit var permissionService: PermissionService

    private val callerRoleId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var role: Role
    private lateinit var user: User
    private lateinit var staff: StaffMember

    // For tenant isolation test
    private lateinit var otherOrg: Organization
    private lateinit var otherClub: Club

    @BeforeEach
    fun setup() {
        org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"))
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"))
        branch = branchRepository.save(Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Branch"))
        role =
            roleRepository.save(
                Role(nameAr = "موظف", nameEn = "Receptionist", scope = "club", organizationId = org.id, clubId = club.id),
            )
        user =
            userRepository.save(
                User(
                    email = "cash-test@test.com",
                    passwordHash = passwordEncoder.encode(TEST_PASSWORD),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        staff =
            staffMemberRepository.save(
                StaffMember(
                    organizationId = org.id,
                    clubId = club.id,
                    userId = user.id,
                    roleId = role.id,
                    firstNameAr = "فاطمة",
                    firstNameEn = "Fatima",
                    lastNameAr = "الزهراني",
                    lastNameEn = "Al-Zahrani",
                    joinedAt = LocalDate.now(),
                ),
            )
        staffBranchAssignmentRepository.save(
            StaffBranchAssignment(staffMemberId = staff.id, branchId = branch.id, organizationId = org.id),
        )

        // Other tenant for isolation test
        otherOrg = organizationRepository.save(Organization(nameAr = "أخرى", nameEn = "Other Org", email = "other@test.com"))
        otherClub = clubRepository.save(Club(organizationId = otherOrg.id, nameAr = "نادي آخر", nameEn = "Other Club"))
    }

    @AfterEach
    fun cleanup() {
        entryRepository.deleteAllInBatch()
        sessionRepository.deleteAllInBatch()
        staffBranchAssignmentRepository.deleteAllInBatch()
        staffMemberRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        roleRepository.deleteAllInBatch()
        branchRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
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
        return "Bearer ${jwtService.generateToken(UUID.randomUUID().toString(), claims)}"
    }

    // ── Full shift flow: open → entries → close → reconcile ─────────────────

    @Test
    fun `full shift flow - open, add entries, close, reconcile`() {
        val token =
            bearerToken(
                permissions =
                    arrayOf(
                        "cash-drawer:open",
                        "cash-drawer:close",
                        "cash-drawer:read",
                        "cash-drawer:entry:create",
                        "cash-drawer:reconcile",
                    ),
            )

        // 1. Open session
        val openResult =
            mockMvc.post("$BASE_PATH?branchId=${branch.publicId}") {
                header("Authorization", token)
                contentType = MediaType.APPLICATION_JSON
                content = """{"openingFloatHalalas": 50000}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.status", equalTo("open"))
                jsonPath("$.openingFloat.halalas", equalTo(50000))
                jsonPath("$.id", notNullValue())
            }.andReturn()

        val sessionId = com.jayway.jsonpath.JsonPath.read<String>(openResult.response.contentAsString, "$.id")

        // 2. Add entries
        mockMvc.post("$BASE_PATH/$sessionId/entries") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"entryType":"cash_in","amountHalalas":15000,"description":"Membership payment"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.entryType", equalTo("cash_in"))
            jsonPath("$.amount.halalas", equalTo(15000))
        }

        mockMvc.post("$BASE_PATH/$sessionId/entries") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"entryType":"cash_in","amountHalalas":36000,"description":"PT package"}"""
        }.andExpect {
            status { isCreated() }
        }

        mockMvc.post("$BASE_PATH/$sessionId/entries") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"entryType":"cash_out","amountHalalas":4000,"description":"Cleaning supplies"}"""
        }.andExpect {
            status { isCreated() }
        }

        // 3. Close session (expected = 50000 + 15000 + 36000 - 4000 = 97000)
        mockMvc.patch("$BASE_PATH/$sessionId/close") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"countedClosingHalalas": 96000}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", equalTo("closed"))
            jsonPath("$.expectedClosing.halalas", equalTo(97000))
            jsonPath("$.countedClosing.halalas", equalTo(96000))
            jsonPath("$.difference.halalas", equalTo(-1000))
        }

        // 4. Reconcile
        mockMvc.patch("$BASE_PATH/$sessionId/reconcile") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"reconciliationStatus":"approved"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", equalTo("reconciled"))
            jsonPath("$.reconciliationStatus", equalTo("approved"))
        }

        // 5. Verify entries
        mockMvc.get("$BASE_PATH/$sessionId/entries") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()", equalTo(3))
        }
    }

    // ── Tenant isolation ────────────────────────────────────────────────────

    @Test
    fun `other tenant cannot access sessions`() {
        val token = bearerToken(permissions = arrayOf("cash-drawer:open", "cash-drawer:read"))

        // Create a session
        val openResult =
            mockMvc.post("$BASE_PATH?branchId=${branch.publicId}") {
                header("Authorization", token)
                contentType = MediaType.APPLICATION_JSON
                content = """{"openingFloatHalalas": 10000}"""
            }.andExpect {
                status { isCreated() }
            }.andReturn()

        val sessionId = com.jayway.jsonpath.JsonPath.read<String>(openResult.response.contentAsString, "$.id")

        // Other tenant tries to read it
        val otherToken = otherTenantToken()
        whenever(permissionService.hasPermission(callerRoleId, "cash-drawer:read")).thenReturn(true)

        mockMvc.get("$BASE_PATH/$sessionId") {
            header("Authorization", otherToken)
        }.andExpect {
            status { isNotFound() }
        }
    }

    // ── Permission check: receptionist cannot reconcile ─────────────────────

    @Test
    fun `receptionist cannot reconcile returns 403`() {
        val openToken = bearerToken(permissions = arrayOf("cash-drawer:open", "cash-drawer:close", "cash-drawer:entry:create"))

        val openResult =
            mockMvc.post("$BASE_PATH?branchId=${branch.publicId}") {
                header("Authorization", openToken)
                contentType = MediaType.APPLICATION_JSON
                content = """{"openingFloatHalalas": 50000}"""
            }.andExpect {
                status { isCreated() }
            }.andReturn()

        val sessionId = com.jayway.jsonpath.JsonPath.read<String>(openResult.response.contentAsString, "$.id")

        // Close the session first
        mockMvc.patch("$BASE_PATH/$sessionId/close") {
            header("Authorization", openToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"countedClosingHalalas": 50000}"""
        }.andExpect {
            status { isOk() }
        }

        // Try to reconcile without permission
        mockMvc.patch("$BASE_PATH/$sessionId/reconcile") {
            header("Authorization", forbiddenToken())
            contentType = MediaType.APPLICATION_JSON
            content = """{"reconciliationStatus":"approved"}"""
        }.andExpect {
            status { isForbidden() }
        }
    }
}
