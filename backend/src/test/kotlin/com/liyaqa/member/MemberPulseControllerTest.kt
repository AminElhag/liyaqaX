package com.liyaqa.member

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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberPulseControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var branchRepository: BranchRepository

    @Autowired lateinit var roleRepository: RoleRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var userRoleRepository: UserRoleRepository

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var emergencyContactRepository: EmergencyContactRepository

    @Autowired lateinit var healthWaiverRepository: HealthWaiverRepository

    @Autowired lateinit var waiverSignatureRepository: WaiverSignatureRepository

    @MockBean lateinit var permissionService: PermissionService

    private val callerRoleId = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000020")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var memberRole: Role

    @BeforeEach
    fun setup() {
        org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"))
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"))
        branch = branchRepository.save(Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Branch"))
        memberRole =
            roleRepository.save(
                Role(nameAr = "عضو", nameEn = "Member", scope = "member", organizationId = org.id, clubId = club.id),
            )
    }

    @AfterEach
    fun cleanup() {
        waiverSignatureRepository.deleteAllInBatch()
        healthWaiverRepository.deleteAllInBatch()
        emergencyContactRepository.deleteAllInBatch()
        memberRepository.deleteAllInBatch()
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

    private fun createMemberBody(email: String = "member-${UUID.randomUUID()}@test.com") =
        """
        {
            "email": "$email",
            "password": "Test@12345678",
            "firstNameAr": "أحمد",
            "firstNameEn": "Ahmed",
            "lastNameAr": "الرشيدي",
            "lastNameEn": "Al-Rashidi",
            "phone": "+966501234567",
            "branchId": "${branch.publicId}",
            "emergencyContact": {
                "nameAr": "محمد",
                "nameEn": "Mohammed",
                "phone": "+966507654321",
                "relationship": "Brother"
            }
        }
        """.trimIndent()

    // ── POST /members ────────────────────────────────────────────────────────

    @Test
    fun `POST creates member with valid permissions`() {
        val token = bearerToken(permissions = arrayOf("member:create"))

        mockMvc.post("/api/v1/members") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = createMemberBody()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id", notNullValue())
            jsonPath("$.firstNameEn", equalTo("Ahmed"))
            jsonPath("$.lastNameEn", equalTo("Al-Rashidi"))
            jsonPath("$.membershipStatus", equalTo("pending"))
            jsonPath("$.emergencyContacts.length()", equalTo(1))
            jsonPath("$.emergencyContacts[0].nameEn", equalTo("Mohammed"))
            jsonPath("$.branch.nameEn", equalTo("Branch"))
        }
    }

    @Test
    fun `POST returns 403 without member create permission`() {
        mockMvc.post("/api/v1/members") {
            header("Authorization", forbiddenToken())
            contentType = MediaType.APPLICATION_JSON
            content = createMemberBody()
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `POST returns 401 without authentication`() {
        mockMvc.post("/api/v1/members") {
            contentType = MediaType.APPLICATION_JSON
            content = createMemberBody()
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `POST returns 400 for invalid request body`() {
        val token = bearerToken(permissions = arrayOf("member:create"))

        mockMvc.post("/api/v1/members") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "not-valid"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST returns 409 for duplicate email`() {
        val token = bearerToken(permissions = arrayOf("member:create"))
        val email = "duplicate-${UUID.randomUUID()}@test.com"

        mockMvc.post("/api/v1/members") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = createMemberBody(email)
        }.andExpect { status { isCreated() } }

        mockMvc.post("/api/v1/members") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = createMemberBody(email)
        }.andExpect {
            status { isConflict() }
        }
    }

    // ── GET /members ─────────────────────────────────────────────────────────

    @Test
    fun `GET list returns members for club`() {
        val createToken = bearerToken(permissions = arrayOf("member:create"))
        mockMvc.post("/api/v1/members") {
            header("Authorization", createToken)
            contentType = MediaType.APPLICATION_JSON
            content = createMemberBody()
        }.andExpect { status { isCreated() } }

        val readToken = bearerToken(permissions = arrayOf("member:read"))

        mockMvc.get("/api/v1/members") {
            header("Authorization", readToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()", equalTo(1))
            jsonPath("$.pagination.totalElements", equalTo(1))
            jsonPath("$.items[0].firstNameEn", equalTo("Ahmed"))
            jsonPath("$.items[0].membershipStatus", equalTo("pending"))
        }
    }

    @Test
    fun `GET list returns 403 without member read permission`() {
        mockMvc.get("/api/v1/members") {
            header("Authorization", forbiddenToken())
        }.andExpect {
            status { isForbidden() }
        }
    }

    // ── GET /members/{id} ────────────────────────────────────────────────────

    @Test
    fun `GET by ID returns member profile`() {
        val createToken = bearerToken(permissions = arrayOf("member:create"))
        val result =
            mockMvc.post("/api/v1/members") {
                header("Authorization", createToken)
                contentType = MediaType.APPLICATION_JSON
                content = createMemberBody()
            }.andExpect { status { isCreated() } }
                .andReturn()

        val memberId =
            com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.response.contentAsString)["id"].asText()

        val readToken = bearerToken(permissions = arrayOf("member:read"))

        mockMvc.get("/api/v1/members/$memberId") {
            header("Authorization", readToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id", equalTo(memberId))
            jsonPath("$.firstNameEn", equalTo("Ahmed"))
            jsonPath("$.emergencyContacts.length()", equalTo(1))
        }
    }

    // ── DELETE /members/{id} ─────────────────────────────────────────────────

    @Test
    fun `DELETE soft-deletes member`() {
        val createToken = bearerToken(permissions = arrayOf("member:create"))
        val result =
            mockMvc.post("/api/v1/members") {
                header("Authorization", createToken)
                contentType = MediaType.APPLICATION_JSON
                content = createMemberBody()
            }.andExpect { status { isCreated() } }
                .andReturn()

        val memberId =
            com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.response.contentAsString)["id"].asText()

        val deleteToken = bearerToken(permissions = arrayOf("member:delete"))

        mockMvc.delete("/api/v1/members/$memberId") {
            header("Authorization", deleteToken)
        }.andExpect {
            status { isNoContent() }
        }

        val readToken = bearerToken(permissions = arrayOf("member:read"))
        mockMvc.get("/api/v1/members/$memberId") {
            header("Authorization", readToken)
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `DELETE returns 403 without member delete permission`() {
        mockMvc.delete("/api/v1/members/${UUID.randomUUID()}") {
            header("Authorization", forbiddenToken())
        }.andExpect {
            status { isForbidden() }
        }
    }
}
