package com.liyaqa.trainer

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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrainerPulseControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var branchRepository: BranchRepository

    @Autowired lateinit var roleRepository: RoleRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var userRoleRepository: UserRoleRepository

    @Autowired lateinit var trainerRepository: TrainerRepository

    @Autowired lateinit var trainerBranchAssignmentRepository: TrainerBranchAssignmentRepository

    @Autowired lateinit var trainerCertificationRepository: TrainerCertificationRepository

    @Autowired lateinit var trainerSpecializationRepository: TrainerSpecializationRepository

    @MockBean lateinit var permissionService: PermissionService

    private val callerRoleId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var ptRole: Role

    @BeforeEach
    fun setup() {
        org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"))
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"))
        branch = branchRepository.save(Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Branch"))
        ptRole =
            roleRepository.save(
                Role(
                    nameAr = "مدرب شخصي",
                    nameEn = "PT Trainer",
                    scope = "trainer",
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
    }

    @AfterEach
    fun cleanup() {
        trainerSpecializationRepository.deleteAllInBatch()
        trainerCertificationRepository.deleteAllInBatch()
        trainerBranchAssignmentRepository.deleteAllInBatch()
        trainerRepository.deleteAllInBatch()
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

    private fun bearerTokenForUser(
        userPublicId: UUID,
        vararg permissions: String,
    ): String {
        val roleId = callerRoleId
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
        return "Bearer ${jwtService.generateToken(userPublicId.toString(), claims)}"
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

    private fun createBody() =
        """
        {
            "email": "trainer-${UUID.randomUUID()}@test.com",
            "password": "Pass1234!",
            "firstNameAr": "خالد",
            "firstNameEn": "Khalid",
            "lastNameAr": "الشمري",
            "lastNameEn": "Al-Shammari",
            "trainerTypes": ["pt"],
            "branchIds": ["${branch.publicId}"],
            "joinedAt": "2025-03-01"
        }
        """.trimIndent()

    // ── POST /api/v1/trainers ────────────────────────────────────────────────

    @Test
    fun `POST creates trainer with valid permissions`() {
        val token = bearerToken(permissions = arrayOf("staff:create"))

        mockMvc.post("/api/v1/trainers") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = createBody()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id", notNullValue())
            jsonPath("$.firstNameEn", equalTo("Khalid"))
            jsonPath("$.lastNameEn", equalTo("Al-Shammari"))
            jsonPath("$.trainerTypes.length()", equalTo(1))
            jsonPath("$.trainerTypes[0]", equalTo("pt"))
            jsonPath("$.branches.length()", equalTo(1))
            jsonPath("$.isActive", equalTo(true))
        }
    }

    @Test
    fun `POST returns 403 without staff create permission`() {
        mockMvc.post("/api/v1/trainers") {
            header("Authorization", forbiddenToken())
            contentType = MediaType.APPLICATION_JSON
            content = createBody()
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `POST returns 401 without authentication`() {
        mockMvc.post("/api/v1/trainers") {
            contentType = MediaType.APPLICATION_JSON
            content = createBody()
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `POST returns 400 for invalid request body`() {
        val token = bearerToken(permissions = arrayOf("staff:create"))

        mockMvc.post("/api/v1/trainers") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"email": "not-valid"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    // ── GET /api/v1/trainers ─────────────────────────────────────────────────

    @Test
    fun `GET list returns trainers for club`() {
        val createToken = bearerToken(permissions = arrayOf("staff:create"))

        mockMvc.post("/api/v1/trainers") {
            header("Authorization", createToken)
            contentType = MediaType.APPLICATION_JSON
            content = createBody()
        }.andExpect { status { isCreated() } }

        val readToken = bearerToken(permissions = arrayOf("staff:read"))

        mockMvc.get("/api/v1/trainers") {
            header("Authorization", readToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()", equalTo(1))
            jsonPath("$.pagination.totalElements", equalTo(1))
        }
    }

    // ── GET /api/v1/trainers/{id} ────────────────────────────────────────────

    @Test
    fun `GET by id returns trainer details`() {
        val createToken = bearerToken(permissions = arrayOf("staff:create"))

        val result =
            mockMvc.post("/api/v1/trainers") {
                header("Authorization", createToken)
                contentType = MediaType.APPLICATION_JSON
                content = createBody()
            }.andExpect { status { isCreated() } }
                .andReturn()

        val id =
            com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.response.contentAsString)["id"].asText()

        val readToken = bearerToken(permissions = arrayOf("staff:read"))

        mockMvc.get("/api/v1/trainers/$id") {
            header("Authorization", readToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.id", equalTo(id))
            jsonPath("$.firstNameEn", equalTo("Khalid"))
            jsonPath("$.trainerTypes", notNullValue())
            jsonPath("$.branches", notNullValue())
            jsonPath("$.certifications", notNullValue())
            jsonPath("$.specializations", notNullValue())
        }
    }

    // ── PATCH /api/v1/trainers/{id} ──────────────────────────────────────────

    @Test
    fun `PATCH updates trainer fields`() {
        val createToken = bearerToken(permissions = arrayOf("staff:create"))

        val result =
            mockMvc.post("/api/v1/trainers") {
                header("Authorization", createToken)
                contentType = MediaType.APPLICATION_JSON
                content = createBody()
            }.andExpect { status { isCreated() } }
                .andReturn()

        val id =
            com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.response.contentAsString)["id"].asText()

        val updateToken = bearerToken(permissions = arrayOf("staff:update"))

        mockMvc.patch("/api/v1/trainers/$id") {
            header("Authorization", updateToken)
            contentType = MediaType.APPLICATION_JSON
            content = """{"firstNameEn": "Updated", "bioEn": "New bio"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.firstNameEn", equalTo("Updated"))
            jsonPath("$.bioEn", equalTo("New bio"))
            jsonPath("$.lastNameEn", equalTo("Al-Shammari"))
        }
    }

    // ── DELETE /api/v1/trainers/{id} ─────────────────────────────────────────

    @Test
    fun `DELETE soft-deletes trainer`() {
        val createToken = bearerToken(permissions = arrayOf("staff:create"))

        val result =
            mockMvc.post("/api/v1/trainers") {
                header("Authorization", createToken)
                contentType = MediaType.APPLICATION_JSON
                content = createBody()
            }.andExpect { status { isCreated() } }
                .andReturn()

        val id =
            com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.response.contentAsString)["id"].asText()

        // Create a separate admin user for deletion (different from the trainer)
        val adminUser =
            userRepository.save(
                com.liyaqa.user.User(
                    email = "admin-deleter@test.com",
                    passwordHash = "hash",
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        val deleteToken = bearerTokenForUser(adminUser.publicId, "staff:delete")

        mockMvc.delete("/api/v1/trainers/$id") {
            header("Authorization", deleteToken)
        }.andExpect {
            status { isNoContent() }
        }

        val readToken = bearerToken(permissions = arrayOf("staff:read"))

        mockMvc.get("/api/v1/trainers/$id") {
            header("Authorization", readToken)
        }.andExpect {
            status { isNotFound() }
        }
    }

    // ── Branch assignment ────────────────────────────────────────────────────

    @Test
    fun `POST branch assigns trainer to branch`() {
        val createToken = bearerToken(permissions = arrayOf("staff:create"))

        val result =
            mockMvc.post("/api/v1/trainers") {
                header("Authorization", createToken)
                contentType = MediaType.APPLICATION_JSON
                content = createBody()
            }.andExpect { status { isCreated() } }
                .andReturn()

        val id =
            com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.response.contentAsString)["id"].asText()

        val secondBranch =
            branchRepository.save(
                Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع ٢", nameEn = "Branch 2"),
            )

        val updateToken = bearerToken(permissions = arrayOf("staff:update"))

        mockMvc.post("/api/v1/trainers/$id/branches/${secondBranch.publicId}") {
            header("Authorization", updateToken)
        }.andExpect {
            status { isNoContent() }
        }

        val readToken = bearerToken(permissions = arrayOf("staff:read"))

        mockMvc.get("/api/v1/trainers/$id/branches") {
            header("Authorization", readToken)
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()", equalTo(2))
        }
    }
}
