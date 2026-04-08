package com.liyaqa.shift.controller

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtService
import com.liyaqa.shift.entity.ShiftSwapRequest
import com.liyaqa.shift.entity.StaffShift
import com.liyaqa.shift.repository.ShiftSwapRequestRepository
import com.liyaqa.shift.repository.StaffShiftRepository
import com.liyaqa.staff.StaffBranchAssignment
import com.liyaqa.staff.StaffBranchAssignmentRepository
import com.liyaqa.staff.StaffMember
import com.liyaqa.staff.StaffMemberRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffShiftControllerIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var passwordEncoder: PasswordEncoder

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var branchRepository: BranchRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var staffMemberRepository: StaffMemberRepository

    @Autowired lateinit var staffBranchAssignmentRepository: StaffBranchAssignmentRepository

    @Autowired lateinit var shiftRepository: StaffShiftRepository

    @Autowired lateinit var swapRepository: ShiftSwapRequestRepository

    @MockBean lateinit var permissionService: PermissionService

    private val managerRoleId = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000020")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var managerUser: User
    private lateinit var staffUser: User
    private lateinit var staffMember: StaffMember
    private lateinit var targetUser: User
    private lateinit var targetStaff: StaffMember

    companion object {
        private const val TEST_PASSWORD = "Test@12345678"
    }

    private fun shiftJson(
        staffId: UUID = staffMember.publicId,
        branchId: UUID = branch.publicId,
        start: String = "2026-04-14T06:00:00Z",
        end: String = "2026-04-14T14:00:00Z",
    ): String =
        """{"staffMemberPublicId":"$staffId","branchPublicId":"$branchId","startAt":"$start","endAt":"$end"}"""

    @BeforeEach
    fun setup() {
        org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@shift.com"))
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"))
        branch = branchRepository.save(Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Test Branch"))

        managerUser = userRepository.save(
            User(
                email = "mgr-shift@test.com",
                passwordHash = passwordEncoder.encode(TEST_PASSWORD),
                organizationId = org.id,
                clubId = club.id,
            ),
        )
        staffUser = userRepository.save(
            User(
                email = "staff-shift@test.com",
                passwordHash = passwordEncoder.encode(TEST_PASSWORD),
                organizationId = org.id,
                clubId = club.id,
            ),
        )
        targetUser = userRepository.save(
            User(
                email = "target-shift@test.com",
                passwordHash = passwordEncoder.encode(TEST_PASSWORD),
                organizationId = org.id,
                clubId = club.id,
            ),
        )

        staffMember =
            staffMemberRepository.save(
                StaffMember(
                    organizationId = org.id, clubId = club.id, userId = staffUser.id, roleId = 1L,
                    firstNameAr = "خالد", firstNameEn = "Khalid", lastNameAr = "العتيبي", lastNameEn = "Al-Otaibi",
                    joinedAt = LocalDate.now(),
                ),
            )
        targetStaff =
            staffMemberRepository.save(
                StaffMember(
                    organizationId = org.id, clubId = club.id, userId = targetUser.id, roleId = 1L,
                    firstNameAr = "سارة", firstNameEn = "Sara", lastNameAr = "الزهراني", lastNameEn = "Al-Zahrani",
                    joinedAt = LocalDate.now(),
                ),
            )

        staffBranchAssignmentRepository.save(
            StaffBranchAssignment(staffMemberId = staffMember.id, branchId = branch.id, organizationId = org.id),
        )
        staffBranchAssignmentRepository.save(
            StaffBranchAssignment(staffMemberId = targetStaff.id, branchId = branch.id, organizationId = org.id),
        )
    }

    @AfterEach
    fun cleanup() {
        swapRepository.deleteAllInBatch()
        shiftRepository.deleteAllInBatch()
        staffBranchAssignmentRepository.deleteAllInBatch()
        staffMemberRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        branchRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    private fun managerToken(vararg permissions: String): String {
        permissions.forEach { whenever(permissionService.hasPermission(managerRoleId, it)).thenReturn(true) }
        val claims =
            mapOf(
                "roleId" to managerRoleId.toString(),
                "scope" to "club",
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
                "branchIds" to listOf(branch.publicId.toString()),
            )
        return "Bearer ${jwtService.generateToken(managerUser.publicId.toString(), claims)}"
    }

    private fun staffToken(vararg permissions: String): String {
        permissions.forEach { whenever(permissionService.hasPermission(managerRoleId, it)).thenReturn(true) }
        val claims =
            mapOf(
                "roleId" to managerRoleId.toString(),
                "scope" to "club",
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
                "branchIds" to listOf(branch.publicId.toString()),
            )
        return "Bearer ${jwtService.generateToken(staffUser.publicId.toString(), claims)}"
    }

    private fun targetToken(vararg permissions: String): String {
        permissions.forEach { whenever(permissionService.hasPermission(managerRoleId, it)).thenReturn(true) }
        val claims =
            mapOf(
                "roleId" to managerRoleId.toString(),
                "scope" to "club",
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
                "branchIds" to listOf(branch.publicId.toString()),
            )
        return "Bearer ${jwtService.generateToken(targetUser.publicId.toString(), claims)}"
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

    // ── POST /pulse/shifts ──────────────────────────────────────────────────

    @Test
    fun `POST pulse shifts returns 201 for valid shift`() {
        val token = managerToken("shift:manage")
        mockMvc.post("/api/v1/pulse/shifts") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = shiftJson()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.shiftId", notNullValue())
            jsonPath("$.staffMemberName", equalTo("Khalid Al-Otaibi"))
        }
    }

    @Test
    fun `POST pulse shifts returns 409 SHIFT_OVERLAP for overlapping shift`() {
        shiftRepository.save(
            StaffShift(
                staffMemberId = staffMember.id,
                branchId = branch.id,
                startAt = Instant.parse("2026-04-14T06:00:00Z"),
                endAt = Instant.parse("2026-04-14T14:00:00Z"),
                createdByUserId = managerUser.id,
            ),
        )

        val token = managerToken("shift:manage")
        mockMvc.post("/api/v1/pulse/shifts") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = shiftJson(start = "2026-04-14T08:00:00Z", end = "2026-04-14T16:00:00Z")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.errorCode", equalTo("SHIFT_OVERLAP"))
        }
    }

    @Test
    fun `POST pulse shifts returns 422 when staff not at branch`() {
        staffBranchAssignmentRepository.deleteAllInBatch()
        staffBranchAssignmentRepository.save(
            StaffBranchAssignment(staffMemberId = targetStaff.id, branchId = branch.id, organizationId = org.id),
        )

        val token = managerToken("shift:manage")
        mockMvc.post("/api/v1/pulse/shifts") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = shiftJson()
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.errorCode", equalTo("STAFF_NOT_AT_BRANCH"))
        }
    }

    @Test
    fun `POST pulse shifts returns 403 without shift manage permission`() {
        mockMvc.post("/api/v1/pulse/shifts") {
            header("Authorization", forbiddenToken())
            contentType = MediaType.APPLICATION_JSON
            content = shiftJson()
        }.andExpect {
            status { isForbidden() }
        }
    }

    // ── GET /pulse/shifts ───────────────────────────────────────────────────

    @Test
    fun `GET pulse shifts returns roster grid for branch and week`() {
        shiftRepository.save(
            StaffShift(
                staffMemberId = staffMember.id,
                branchId = branch.id,
                startAt = Instant.parse("2026-04-14T06:00:00Z"),
                endAt = Instant.parse("2026-04-14T14:00:00Z"),
                createdByUserId = managerUser.id,
            ),
        )

        val token = managerToken("shift:manage")
        mockMvc.get("/api/v1/pulse/shifts") {
            header("Authorization", token)
            param("branchPublicId", branch.publicId.toString())
            param("weekStart", "2026-04-13")
        }.andExpect {
            status { isOk() }
            jsonPath("$.weekStart", equalTo("2026-04-13"))
            jsonPath("$.shifts", hasSize<Any>(1))
        }
    }

    // ── GET /pulse/shifts/my ────────────────────────────────────────────────

    @Test
    fun `GET pulse shifts my returns upcoming shifts for authenticated staff`() {
        val futureStart = Instant.now().plusSeconds(86400)
        val futureEnd = futureStart.plusSeconds(28800)
        shiftRepository.save(
            StaffShift(
                staffMemberId = staffMember.id,
                branchId = branch.id,
                startAt = futureStart,
                endAt = futureEnd,
                createdByUserId = managerUser.id,
            ),
        )

        val token = staffToken("shift:read")
        mockMvc.get("/api/v1/pulse/shifts/my") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.shifts", hasSize<Any>(1))
        }
    }

    // ── DELETE /pulse/shifts/{id} ───────────────────────────────────────────

    @Test
    fun `DELETE pulse shifts returns 204 for valid shift`() {
        val shift =
            shiftRepository.save(
                StaffShift(
                    staffMemberId = staffMember.id,
                    branchId = branch.id,
                    startAt = Instant.parse("2026-04-14T06:00:00Z"),
                    endAt = Instant.parse("2026-04-14T14:00:00Z"),
                    createdByUserId = managerUser.id,
                ),
            )

        val token = managerToken("shift:manage")
        mockMvc.delete("/api/v1/pulse/shifts/${shift.publicId}") {
            header("Authorization", token)
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `DELETE pulse shifts returns 409 when pending swap exists`() {
        val shift =
            shiftRepository.save(
                StaffShift(
                    staffMemberId = staffMember.id,
                    branchId = branch.id,
                    startAt = Instant.parse("2026-04-14T06:00:00Z"),
                    endAt = Instant.parse("2026-04-14T14:00:00Z"),
                    createdByUserId = managerUser.id,
                ),
            )
        swapRepository.save(
            ShiftSwapRequest(
                shiftId = shift.id,
                requesterStaffId = staffMember.id,
                targetStaffId = targetStaff.id,
                status = "PENDING_ACCEPTANCE",
            ),
        )

        val token = managerToken("shift:manage")
        mockMvc.delete("/api/v1/pulse/shifts/${shift.publicId}") {
            header("Authorization", token)
        }.andExpect {
            status { isConflict() }
            jsonPath("$.errorCode", equalTo("SHIFT_HAS_PENDING_SWAP"))
        }
    }

    // ── Swap lifecycle ──────────────────────────────────────────────────────

    @Test
    fun `POST swap-requests creates PENDING_ACCEPTANCE swap`() {
        val shift =
            shiftRepository.save(
                StaffShift(
                    staffMemberId = staffMember.id,
                    branchId = branch.id,
                    startAt = Instant.parse("2026-04-14T06:00:00Z"),
                    endAt = Instant.parse("2026-04-14T14:00:00Z"),
                    createdByUserId = managerUser.id,
                ),
            )

        val token = staffToken("shift:read")
        mockMvc.post("/api/v1/pulse/shifts/${shift.publicId}/swap-requests") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"targetStaffPublicId":"${targetStaff.publicId}","requesterNote":"Doctor appointment"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.swapId", notNullValue())
        }
    }

    @Test
    fun `PATCH swap-requests respond accept moves to PENDING_APPROVAL`() {
        val shift =
            shiftRepository.save(
                StaffShift(
                    staffMemberId = staffMember.id,
                    branchId = branch.id,
                    startAt = Instant.parse("2026-04-14T06:00:00Z"),
                    endAt = Instant.parse("2026-04-14T14:00:00Z"),
                    createdByUserId = managerUser.id,
                ),
            )
        val swap =
            swapRepository.save(
                ShiftSwapRequest(
                    shiftId = shift.id,
                    requesterStaffId = staffMember.id,
                    targetStaffId = targetStaff.id,
                    status = "PENDING_ACCEPTANCE",
                ),
            )

        val token = targetToken("shift:read")
        mockMvc.patch("/api/v1/pulse/shifts/swap-requests/${swap.publicId}/respond") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"action":"accept"}"""
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `PATCH swap-requests resolve approve transfers ownership`() {
        val shift =
            shiftRepository.save(
                StaffShift(
                    staffMemberId = staffMember.id,
                    branchId = branch.id,
                    startAt = Instant.parse("2026-04-14T06:00:00Z"),
                    endAt = Instant.parse("2026-04-14T14:00:00Z"),
                    createdByUserId = managerUser.id,
                ),
            )
        val swap =
            swapRepository.save(
                ShiftSwapRequest(
                    shiftId = shift.id,
                    requesterStaffId = staffMember.id,
                    targetStaffId = targetStaff.id,
                    status = "PENDING_APPROVAL",
                ),
            )

        val token = managerToken("shift:manage")
        mockMvc.patch("/api/v1/pulse/shifts/swap-requests/${swap.publicId}/resolve") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"action":"approve"}"""
        }.andExpect {
            status { isNoContent() }
        }

        val updatedShift = shiftRepository.findById(shift.id).get()
        assert(updatedShift.staffMemberId == targetStaff.id) { "Shift ownership should have transferred to target staff" }
    }

    @Test
    fun `GET swap-requests pending returns manager pending approvals`() {
        val shift =
            shiftRepository.save(
                StaffShift(
                    staffMemberId = staffMember.id,
                    branchId = branch.id,
                    startAt = Instant.parse("2026-04-14T06:00:00Z"),
                    endAt = Instant.parse("2026-04-14T14:00:00Z"),
                    createdByUserId = managerUser.id,
                ),
            )
        swapRepository.save(
            ShiftSwapRequest(
                shiftId = shift.id,
                requesterStaffId = staffMember.id,
                targetStaffId = targetStaff.id,
                status = "PENDING_APPROVAL",
            ),
        )

        val token = managerToken("shift:manage")
        mockMvc.get("/api/v1/pulse/shifts/swap-requests/pending") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.swapRequests", hasSize<Any>(1))
        }
    }
}
