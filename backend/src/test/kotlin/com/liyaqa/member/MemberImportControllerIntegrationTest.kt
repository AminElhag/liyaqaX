package com.liyaqa.member

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtService
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.domain.PageRequest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import java.util.UUID

private const val TEST_PASSWORD = "Test@12345678"

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberImportControllerIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var branchRepository: BranchRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var jobRepository: MemberImportJobRepository

    @Autowired lateinit var passwordEncoder: PasswordEncoder

    @MockBean lateinit var permissionService: PermissionService

    private val roleId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val noPermRoleId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var actorUser: User

    @BeforeEach
    fun setup() {
        org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"))
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"))
        branch =
            branchRepository.save(
                Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Test Branch", city = "Riyadh"),
            )
        actorUser =
            userRepository.save(
                User(email = "actor@test.com", passwordHash = passwordEncoder.encode(TEST_PASSWORD)),
            )
    }

    @AfterEach
    fun cleanup() {
        memberRepository.deleteAllInBatch()
        jobRepository.deleteAllInBatch()
        branchRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
    }

    private fun platformToken(vararg permissions: String): String {
        permissions.forEach { whenever(permissionService.hasPermission(roleId, it)).thenReturn(true) }
        return "Bearer ${
            jwtService.generateToken(
                actorUser.publicId.toString(),
                mapOf("roleId" to roleId.toString(), "scope" to "platform"),
            )
        }"
    }

    private fun forbiddenToken(): String =
        "Bearer ${
            jwtService.generateToken(
                actorUser.publicId.toString(),
                mapOf("roleId" to noPermRoleId.toString(), "scope" to "platform"),
            )
        }"

    private fun validCsvFile(): MockMultipartFile =
        MockMultipartFile(
            "file",
            "members.csv",
            "text/csv",
            "name_ar,phone,gender\nأحمد محمد,+966512345678,male\n".toByteArray(),
        )

    @Test
    fun `POST import returns 202 and creates job`() {
        mockMvc.multipart("/api/v1/nexus/clubs/${club.publicId}/members/import") {
            file(validCsvFile())
            header("Authorization", platformToken("member:import"))
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.status", equalTo("QUEUED"))
            jsonPath("$.fileName", equalTo("members.csv"))
        }
    }

    @Test
    fun `POST import returns 422 when headers missing`() {
        val badCsv = MockMultipartFile("file", "bad.csv", "text/csv", "name_ar,email\nأحمد,a@b.com\n".toByteArray())

        mockMvc.multipart("/api/v1/nexus/clubs/${club.publicId}/members/import") {
            file(badCsv)
            header("Authorization", platformToken("member:import"))
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    fun `POST import returns 403 without member import permission`() {
        mockMvc.multipart("/api/v1/nexus/clubs/${club.publicId}/members/import") {
            file(validCsvFile())
            header("Authorization", forbiddenToken())
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `GET job returns job status with counts`() {
        val job =
            jobRepository.save(
                MemberImportJob(
                    clubId = club.id,
                    createdByUserId = actorUser.id,
                    fileName = "test.csv",
                    status = MemberImportJobStatus.COMPLETED,
                    totalRows = 10,
                    importedCount = 8,
                    skippedCount = 1,
                    errorCount = 1,
                ),
            )

        mockMvc.get("/api/v1/nexus/member-import-jobs/${job.publicId}") {
            header("Authorization", platformToken("member:import"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", equalTo("COMPLETED"))
            jsonPath("$.importedCount", equalTo(8))
            jsonPath("$.skippedCount", equalTo(1))
            jsonPath("$.errorCount", equalTo(1))
        }
    }

    @Test
    fun `DELETE job cancels QUEUED job`() {
        val job =
            jobRepository.save(
                MemberImportJob(
                    clubId = club.id,
                    createdByUserId = actorUser.id,
                    fileName = "test.csv",
                    status = MemberImportJobStatus.QUEUED,
                ),
            )

        mockMvc.delete("/api/v1/nexus/member-import-jobs/${job.publicId}") {
            header("Authorization", platformToken("member:import"))
        }.andExpect {
            status { isOk() }
        }

        val updated = jobRepository.findById(job.id).get()
        assert(updated.status == MemberImportJobStatus.CANCELLED)
    }

    @Test
    fun `DELETE job returns 409 when job is PROCESSING`() {
        val job =
            jobRepository.save(
                MemberImportJob(
                    clubId = club.id,
                    createdByUserId = actorUser.id,
                    fileName = "test.csv",
                    status = MemberImportJobStatus.PROCESSING,
                ),
            )

        mockMvc.delete("/api/v1/nexus/member-import-jobs/${job.publicId}") {
            header("Authorization", platformToken("member:import"))
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `POST rollback soft-deletes all imported members`() {
        val job =
            jobRepository.save(
                MemberImportJob(
                    clubId = club.id,
                    createdByUserId = actorUser.id,
                    fileName = "test.csv",
                    status = MemberImportJobStatus.COMPLETED,
                    importedCount = 2,
                ),
            )

        val importedUser1 =
            userRepository.save(
                User(
                    email = "imp1@test.com",
                    passwordHash = passwordEncoder.encode(TEST_PASSWORD),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        val importedUser2 =
            userRepository.save(
                User(
                    email = "imp2@test.com",
                    passwordHash = passwordEncoder.encode(TEST_PASSWORD),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )

        memberRepository.save(
            Member(
                organizationId = org.id, clubId = club.id, branchId = branch.id,
                userId = importedUser1.id, firstNameAr = "أحمد", firstNameEn = "Ahmed",
                lastNameAr = "محمد", lastNameEn = "Mohammed", phone = "+966 512345678",
                gender = "male", memberImportJobId = job.id,
            ),
        )
        memberRepository.save(
            Member(
                organizationId = org.id, clubId = club.id, branchId = branch.id,
                userId = importedUser2.id, firstNameAr = "سارة", firstNameEn = "Sara",
                lastNameAr = "علي", lastNameEn = "Ali", phone = "+966 512345679",
                gender = "female", memberImportJobId = job.id,
            ),
        )

        mockMvc.post("/api/v1/nexus/member-import-jobs/${job.publicId}/rollback") {
            header("Authorization", platformToken("member:import"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.message", equalTo("Rollback complete. 2 members soft-deleted."))
        }
    }

    @Test
    fun `POST rollback returns 409 when job is not COMPLETED`() {
        val job =
            jobRepository.save(
                MemberImportJob(
                    clubId = club.id,
                    createdByUserId = actorUser.id,
                    fileName = "test.csv",
                    status = MemberImportJobStatus.QUEUED,
                ),
            )

        mockMvc.post("/api/v1/nexus/member-import-jobs/${job.publicId}/rollback") {
            header("Authorization", platformToken("member:import"))
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `rolled-back members do not appear in GET members for the club`() {
        val job =
            jobRepository.save(
                MemberImportJob(
                    clubId = club.id,
                    createdByUserId = actorUser.id,
                    fileName = "test.csv",
                    status = MemberImportJobStatus.COMPLETED,
                    importedCount = 1,
                ),
            )

        val importedUser =
            userRepository.save(
                User(
                    email = "rolledback@test.com",
                    passwordHash = passwordEncoder.encode(TEST_PASSWORD),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        memberRepository.save(
            Member(
                organizationId = org.id, clubId = club.id, branchId = branch.id,
                userId = importedUser.id, firstNameAr = "أحمد", firstNameEn = "Ahmed",
                lastNameAr = "محمد", lastNameEn = "Mohammed", phone = "+966 599999999",
                gender = "male", memberImportJobId = job.id,
            ),
        )

        // Rollback
        mockMvc.post("/api/v1/nexus/member-import-jobs/${job.publicId}/rollback") {
            header("Authorization", platformToken("member:import", "member:read"))
        }.andExpect {
            status { isOk() }
        }

        // Verify member does not appear in search
        val activeMembers =
            memberRepository.findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(
                org.id,
                club.id,
                PageRequest.of(0, 100),
            )
        assert(activeMembers.content.none { it.phone == "+966 599999999" })
    }
}
