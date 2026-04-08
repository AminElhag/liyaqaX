package com.liyaqa.role

import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.permission.Permission
import com.liyaqa.permission.PermissionRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.rbac.RolePermissionRepository
import com.liyaqa.rbac.UserRole
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.security.JwtService
import com.liyaqa.staff.StaffMember
import com.liyaqa.staff.StaffMemberRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.assertj.core.api.Assertions.assertThat
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
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RolePulseControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtService: JwtService

    @Autowired
    lateinit var roleRepository: RoleRepository

    @Autowired
    lateinit var permissionRepository: PermissionRepository

    @Autowired
    lateinit var rolePermissionRepository: RolePermissionRepository

    @Autowired
    lateinit var userRoleRepository: UserRoleRepository

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var organizationRepository: OrganizationRepository

    @Autowired
    lateinit var clubRepository: ClubRepository

    @Autowired
    lateinit var staffMemberRepository: StaffMemberRepository

    @MockBean
    lateinit var permissionService: PermissionService

    private val testRoleId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private lateinit var org: Organization
    private lateinit var club: Club

    @BeforeEach
    fun setup() {
        cleanup()
        org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"))
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club", email = "club@test.com"))
    }

    @AfterEach
    fun cleanup() {
        staffMemberRepository.deleteAllInBatch()
        userRoleRepository.deleteAllInBatch()
        rolePermissionRepository.deleteAllInBatch()
        roleRepository.deleteAllInBatch()
        permissionRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
    }

    private fun pulseToken(vararg permissions: String): String {
        permissions.forEach { perm ->
            whenever(permissionService.hasPermission(testRoleId, perm)).thenReturn(true)
        }
        return "Bearer ${
            jwtService.generateToken(
                "club-user",
                mapOf(
                    "roleId" to testRoleId.toString(),
                    "scope" to "club",
                    "organizationId" to org.publicId.toString(),
                    "clubId" to club.publicId.toString(),
                ),
            )
        }"
    }

    private fun createClubRole(
        name: String = "Custom Club Role",
        isSystem: Boolean = false,
    ): Role =
        roleRepository.save(
            Role(
                nameAr = name,
                nameEn = name,
                scope = "club",
                organizationId = org.id,
                clubId = club.id,
                isSystem = isSystem,
            ),
        )

    private fun createPermission(code: String): Permission =
        permissionRepository.save(
            Permission(code = code, resource = code.substringBefore(':'), action = code.substringAfter(':')),
        )

    // ── List ─────────────────────────────────────────────────────────────────

    @Test
    fun `GET list returns club roles`() {
        createClubRole("Owner", isSystem = true)
        createClubRole("Custom")

        mockMvc.get("/api/v1/roles") {
            header("Authorization", pulseToken("role:read"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()", equalTo(2))
        }
    }

    // ── Create ───────────────────────────────────────────────────────────────

    @Test
    fun `POST creates club role`() {
        mockMvc.post("/api/v1/roles") {
            header("Authorization", pulseToken("role:create"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Senior Trainer"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id", notNullValue())
            jsonPath("$.nameEn", equalTo("Senior Trainer"))
            jsonPath("$.scope", equalTo("club"))
        }
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Test
    fun `DELETE succeeds for clean club role`() {
        val role = createClubRole("To Delete")

        mockMvc.delete("/api/v1/roles/${role.publicId}") {
            header("Authorization", pulseToken("role:delete"))
        }.andExpect {
            status { isNoContent() }
        }
    }

    // ── Role from different club returns 403 ─────────────────────────────────

    @Test
    fun `GET role from different club returns 403`() {
        val otherOrg = organizationRepository.save(Organization(nameAr = "أخرى", nameEn = "Other Org", email = "other@test.com"))
        val otherClub =
            clubRepository.save(
                Club(organizationId = otherOrg.id, nameAr = "نادي آخر", nameEn = "Other Club", email = "otherclub@test.com"),
            )
        val otherRole =
            roleRepository.save(
                Role(nameAr = "Other", nameEn = "Other", scope = "club", organizationId = otherOrg.id, clubId = otherClub.id),
            )

        mockMvc.get("/api/v1/roles/${otherRole.publicId}") {
            header("Authorization", pulseToken("role:read"))
        }.andExpect {
            status { isForbidden() }
        }
    }

    // ── Staff role assignment ────────────────────────────────────────────────

    @Test
    fun `PATCH staff role updates UserRole`() {
        val oldRole = createClubRole("Old Role")
        val newRole = createClubRole("New Role")
        val user = userRepository.save(User(email = "staff@test.com", passwordHash = "hash", organizationId = org.id, clubId = club.id))
        userRoleRepository.save(UserRole(userId = user.id, roleId = oldRole.id))
        val staff =
            staffMemberRepository.save(
                StaffMember(
                    organizationId = org.id,
                    clubId = club.id,
                    userId = user.id,
                    roleId = oldRole.id,
                    firstNameAr = "أحمد",
                    firstNameEn = "Ahmed",
                    lastNameAr = "العمري",
                    lastNameEn = "Al-Omari",
                    joinedAt = LocalDate.of(2024, 1, 1),
                ),
            )

        mockMvc.patch("/api/v1/staff/${staff.publicId}/role") {
            header("Authorization", pulseToken("staff:update"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"roleId":"${newRole.publicId}"}"""
        }.andExpect {
            status { isNoContent() }
        }

        // Verify the role was updated
        val updatedStaff = staffMemberRepository.findById(staff.id).get()
        assertThat(updatedStaff.roleId).isEqualTo(newRole.id)
    }
}
