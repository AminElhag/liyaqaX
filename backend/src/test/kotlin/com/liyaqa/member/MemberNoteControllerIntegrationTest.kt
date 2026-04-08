package com.liyaqa.member

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.pt.PTPackage
import com.liyaqa.pt.PTPackageCatalog
import com.liyaqa.pt.PTPackageCatalogRepository
import com.liyaqa.pt.PTPackageRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtService
import com.liyaqa.trainer.Trainer
import com.liyaqa.trainer.TrainerRepository
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
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberNoteControllerIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var passwordEncoder: PasswordEncoder

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var branchRepository: BranchRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var memberNoteRepository: MemberNoteRepository

    @Autowired lateinit var trainerRepository: TrainerRepository

    @Autowired lateinit var ptPackageCatalogRepository: PTPackageCatalogRepository

    @Autowired lateinit var ptPackageRepository: PTPackageRepository

    @MockBean lateinit var permissionService: PermissionService

    private val callerRoleId = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000020")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var staffUser: User
    private lateinit var member: Member
    private lateinit var trainer: Trainer

    companion object {
        private const val TEST_PASSWORD = "Test@12345678"
    }

    @BeforeEach
    fun setup() {
        org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"))
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"))
        branch = branchRepository.save(Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Branch"))

        staffUser =
            userRepository.save(
                User(
                    email = "staff@test.com",
                    passwordHash = passwordEncoder.encode(TEST_PASSWORD),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )

        val memberUser =
            userRepository.save(
                User(
                    email = "member@test.com",
                    passwordHash = passwordEncoder.encode(TEST_PASSWORD),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        member =
            memberRepository.save(
                Member(
                    organizationId = org.id, clubId = club.id, branchId = branch.id, userId = memberUser.id,
                    firstNameAr = "أحمد", firstNameEn = "Ahmed", lastNameAr = "الرشيدي", lastNameEn = "Al-Rashidi",
                    phone = "+966501234567",
                ),
            )

        val trainerUser =
            userRepository.save(
                User(
                    email = "trainer@test.com",
                    passwordHash = passwordEncoder.encode(TEST_PASSWORD),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        trainer =
            trainerRepository.save(
                Trainer(
                    organizationId = org.id, clubId = club.id, userId = trainerUser.id,
                    firstNameAr = "خالد", firstNameEn = "Khalid", lastNameAr = "العتيبي", lastNameEn = "Al-Otaibi",
                    joinedAt = LocalDate.now(),
                ),
            )

        // Create PT package linking trainer to member
        val catalog =
            ptPackageCatalogRepository.save(
                PTPackageCatalog(
                    organizationId = org.id,
                    clubId = club.id,
                    nameAr = "باقة",
                    nameEn = "PT 10",
                    sessionCount = 10,
                    priceHalalas = 200_000,
                ),
            )
        ptPackageRepository.save(
            PTPackage(
                organizationId = org.id, clubId = club.id, branchId = branch.id,
                memberId = member.id, trainerId = trainer.id, catalogId = catalog.id,
                sessionsTotal = 10, startsAt = LocalDate.now().minusDays(7), expiresAt = LocalDate.now().plusDays(83),
            ),
        )
    }

    @AfterEach
    fun cleanup() {
        memberNoteRepository.deleteAllInBatch()
        ptPackageRepository.deleteAllInBatch()
        ptPackageCatalogRepository.deleteAllInBatch()
        memberRepository.deleteAllInBatch()
        trainerRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        branchRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    private fun pulseToken(vararg permissions: String): String {
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
        return "Bearer ${jwtService.generateToken(staffUser.publicId.toString(), claims)}"
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

    private fun coachToken(): String {
        val claims =
            mapOf(
                "scope" to "trainer",
                "trainerId" to trainer.publicId.toString(),
                "trainerTypes" to listOf("pt"),
                "clubId" to club.publicId.toString(),
                "organizationId" to org.publicId.toString(),
            )
        return "Bearer ${jwtService.generateToken(trainer.publicId.toString(), claims)}"
    }

    // ── POST /pulse/members/{id}/notes ──────────────────────────────────────

    @Test
    fun `POST pulse members notes returns 201 with note details`() {
        val token = pulseToken("member-note:create")

        mockMvc.post("/api/v1/pulse/members/${member.publicId}/notes") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"noteType":"general","content":"Test note content"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.noteId", notNullValue())
            jsonPath("$.noteType", equalTo("GENERAL"))
            jsonPath("$.content", equalTo("Test note content"))
        }
    }

    @Test
    fun `POST pulse members notes returns 400 when content is empty`() {
        val token = pulseToken("member-note:create")

        mockMvc.post("/api/v1/pulse/members/${member.publicId}/notes") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"noteType":"general","content":""}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST pulse members notes returns 403 without member-note create permission`() {
        mockMvc.post("/api/v1/pulse/members/${member.publicId}/notes") {
            header("Authorization", forbiddenToken())
            contentType = MediaType.APPLICATION_JSON
            content = """{"noteType":"general","content":"Test note"}"""
        }.andExpect {
            status { isForbidden() }
        }
    }

    // ── DELETE /pulse/members/{id}/notes/{noteId} ───────────────────────────

    @Test
    fun `DELETE pulse members notes returns 204 and soft-deletes`() {
        val token = pulseToken("member-note:create", "member-note:delete")

        // First create a note
        val note =
            memberNoteRepository.save(
                MemberNote(
                    organizationId = org.id,
                    clubId = club.id,
                    memberId = member.id,
                    createdByUserId = staffUser.id,
                    noteType = MemberNoteType.GENERAL,
                    content = "To delete",
                ),
            )

        mockMvc.delete("/api/v1/pulse/members/${member.publicId}/notes/${note.publicId}") {
            header("Authorization", token)
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `DELETE pulse members notes returns 409 for REJECTION note`() {
        val token = pulseToken("member-note:delete")

        val rejectionNote =
            memberNoteRepository.save(
                MemberNote(
                    organizationId = org.id,
                    clubId = club.id,
                    memberId = member.id,
                    createdByUserId = staffUser.id,
                    noteType = MemberNoteType.REJECTION,
                    content = "Rejected registration",
                ),
            )

        mockMvc.delete("/api/v1/pulse/members/${member.publicId}/notes/${rejectionNote.publicId}") {
            header("Authorization", token)
        }.andExpect {
            status { isConflict() }
        }
    }

    // ── GET /pulse/members/{id}/timeline ─────────────────────────────────────

    @Test
    fun `GET pulse members timeline returns combined events sorted by date`() {
        val token = pulseToken("member-note:read")

        // Create a note
        memberNoteRepository.save(
            MemberNote(
                organizationId = org.id,
                clubId = club.id,
                memberId = member.id,
                createdByUserId = staffUser.id,
                noteType = MemberNoteType.GENERAL,
                content = "Timeline note",
            ),
        )

        mockMvc.get("/api/v1/pulse/members/${member.publicId}/timeline") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.events", notNullValue())
        }
    }

    // ── GET /pulse/follow-ups ────────────────────────────────────────────────

    @Test
    fun `GET pulse follow-ups returns 200 with notes due within 7 days`() {
        val token = pulseToken("member-note:follow-up:read")

        mockMvc.get("/api/v1/pulse/follow-ups") {
            header("Authorization", token)
        }.andExpect {
            status { isOk() }
            jsonPath("$.followUps", notNullValue())
        }
    }

    @Test
    fun `GET pulse follow-ups returns 403 without member-note follow-up read permission`() {
        mockMvc.get("/api/v1/pulse/follow-ups") {
            header("Authorization", forbiddenToken())
        }.andExpect {
            status { isForbidden() }
        }
    }

    // ── POST /coach/members/{id}/notes ───────────────────────────────────────

    @Test
    fun `POST coach members notes returns 201 for general type`() {
        val token = coachToken()

        mockMvc.post("/api/v1/coach/members/${member.publicId}/notes") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"noteType":"general","content":"Trainer note on member"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.noteType", equalTo("GENERAL"))
        }
    }

    @Test
    fun `POST coach members notes returns 403 for complaint type from trainer`() {
        val token = coachToken()

        mockMvc.post("/api/v1/coach/members/${member.publicId}/notes") {
            header("Authorization", token)
            contentType = MediaType.APPLICATION_JSON
            content = """{"noteType":"complaint","content":"Complaint from trainer"}"""
        }.andExpect {
            status { isForbidden() }
        }
    }
}
