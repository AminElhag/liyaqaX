package com.liyaqa.staff

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.Role
import com.liyaqa.role.RoleRepository
import com.liyaqa.security.JwtService
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
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffMemberPulseControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var branchRepository: BranchRepository

    @Autowired lateinit var roleRepository: RoleRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var userRoleRepository: UserRoleRepository

    @Autowired lateinit var staffMemberRepository: StaffMemberRepository

    @Autowired lateinit var staffBranchAssignmentRepository: StaffBranchAssignmentRepository

    @MockBean lateinit var permissionService: PermissionService

    private val callerRoleId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var staffRole: Role
    private lateinit var powerfulRole: Role

    @BeforeEach
    fun setup() {
        org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"))
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"))
        branch = branchRepository.save(Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Branch"))
        staffRole =
            roleRepository.save(
                Role(nameAr = "موظف", nameEn = "Receptionist", scope = "club", organizationId = org.id, clubId = club.id),
            )
        powerfulRole =
            roleRepository.save(
                Role(nameAr = "مالك", nameEn = "Owner", scope = "club", organizationId = org.id, clubId = club.id),
            )
    }

    @AfterEach
    fun cleanup() {
        staffBranchAssignmentRepository.deleteAllInBatch()
        staffMemberRepository.deleteAllInBatch()
        userRoleRepository.deleteAllInBatch()
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

    private fun createBody(roleId: UUID = staffRole.publicId) =
        """
        {
            "email": "new-staff-${UUID.randomUUID()}@test.com",
            "password": "Pass1234!",
            "firstNameAr": "أحمد",
            "firstNameEn": "Ahmed",
            "lastNameAr": "العمري",
            "lastNameEn": "Al-Omari",
            "roleId": "$roleId",
            "branchIds": ["${branch.publicId}"],
            "employmentType": "full-time",
            "joinedAt": "2025-01-15"
        }
        """.trimIndent()

    @Test
    fun `POST creates staff member with valid permissions`() {
        val token = bearerToken(permissions = arrayOf("staff:create"))
        whenever(permissionService.getPermissions(callerRoleId)).thenReturn(setOf("staff:create", "staff:read"))
        whenever(permissionService.getPermissions(staffRole.publicId)).thenReturn(setOf("staff:read"))

        mockMvc.post("/api/v1/staff") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = createBody()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id", notNullValue())
            jsonPath("$.firstNameEn", equalTo("Ahmed"))
            jsonPath("$.lastNameEn", equalTo("Al-Omari"))
            jsonPath("$.role.nameEn", equalTo("Receptionist"))
            jsonPath("$.branches.length()", equalTo(1))
            jsonPath("$.employmentType", equalTo("full-time"))
            jsonPath("$.isActive", equalTo(true))
        }
    }

    @Test
    fun `POST returns 403 without staff create permission`() {
        mockMvc.post("/api/v1/staff") {
            header("Authorization", forbiddenToken())
            contentType = MediaType.APPLICATION_JSON
            content = createBody()
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `POST returns 403 for role elevation`() {
        val token = bearerToken(permissions = arrayOf("staff:create"))
        whenever(permissionService.getPermissions(callerRoleId)).thenReturn(setOf("staff:create"))
        whenever(permissionService.getPermissions(powerfulRole.publicId))
            .thenReturn(setOf("staff:create", "staff:delete", "staff:update"))

        mockMvc.post("/api/v1/staff") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = createBody(roleId = powerfulRole.publicId)
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `GET list returns staff for club`() {
        val createToken = bearerToken(permissions = arrayOf("staff:create"))
        whenever(permissionService.getPermissions(callerRoleId)).thenReturn(setOf("staff:create", "staff:read"))
        whenever(permissionService.getPermissions(staffRole.publicId)).thenReturn(setOf("staff:read"))

        mockMvc.post("/api/v1/staff") {
            header("Authorization", createToken)
            contentType = MediaType.APPLICATION_JSON
            content = createBody()
        }.andExpect { status { isCreated() } }

        val readToken = bearerToken(permissions = arrayOf("staff:read"))

        mockMvc.get("/api/v1/staff") {
            header("Authorization", readToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()", equalTo(1))
            jsonPath("$.pagination.totalElements", equalTo(1))
        }
    }

    @Test
    fun `POST returns 401 without authentication`() {
        mockMvc.post("/api/v1/staff") {
            contentType = MediaType.APPLICATION_JSON
            content = createBody()
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `POST returns 400 for invalid request body`() {
        val token = bearerToken(permissions = arrayOf("staff:create"))

        mockMvc.post("/api/v1/staff") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "not-valid"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
