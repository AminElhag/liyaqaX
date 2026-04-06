package com.liyaqa.coach

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.pt.PTPackage
import com.liyaqa.pt.PTPackageCatalog
import com.liyaqa.pt.PTPackageCatalogRepository
import com.liyaqa.pt.PTPackageRepository
import com.liyaqa.pt.PTSession
import com.liyaqa.pt.PTSessionRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtService
import com.liyaqa.trainer.Trainer
import com.liyaqa.trainer.TrainerRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import java.time.LocalDate
import java.time.ZoneId

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PtCoachControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var passwordEncoder: PasswordEncoder

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var branchRepository: BranchRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var trainerRepository: TrainerRepository

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var ptPackageCatalogRepository: PTPackageCatalogRepository

    @Autowired lateinit var ptPackageRepository: PTPackageRepository

    @Autowired lateinit var ptSessionRepository: PTSessionRepository

    @MockBean lateinit var permissionService: PermissionService

    private val riyadhZone = ZoneId.of("Asia/Riyadh")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var ptTrainer: Trainer
    private lateinit var otherTrainer: Trainer
    private lateinit var member: Member
    private lateinit var upcomingSession: PTSession
    private lateinit var pastSession: PTSession

    @BeforeEach
    fun setup() {
        org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"))
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"))
        branch = branchRepository.save(Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Branch"))

        val ptUser =
            userRepository.save(
                User(
                    email = "pt@test.com",
                    passwordHash = passwordEncoder.encode("Test@12345678"),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        ptTrainer =
            trainerRepository.save(
                Trainer(
                    organizationId = org.id, clubId = club.id, userId = ptUser.id,
                    firstNameAr = "خالد", firstNameEn = "Khalid", lastNameAr = "الشمري", lastNameEn = "Al-Shammari",
                    joinedAt = LocalDate.now(),
                ),
            )

        val otherUser =
            userRepository.save(
                User(
                    email = "other@test.com",
                    passwordHash = passwordEncoder.encode("Test@12345678"),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        otherTrainer =
            trainerRepository.save(
                Trainer(
                    organizationId = org.id, clubId = club.id, userId = otherUser.id,
                    firstNameAr = "آخر", firstNameEn = "Other", lastNameAr = "مدرب", lastNameEn = "Trainer",
                    joinedAt = LocalDate.now(),
                ),
            )

        val memberUser =
            userRepository.save(
                User(
                    email = "member@test.com",
                    passwordHash = passwordEncoder.encode("Test@12345678"),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        member =
            memberRepository.save(
                Member(
                    organizationId = org.id, clubId = club.id, branchId = branch.id, userId = memberUser.id,
                    firstNameAr = "أحمد", firstNameEn = "Ahmed", lastNameAr = "الرشيدي", lastNameEn = "Al-Rashidi",
                    phone = "+966501234099",
                ),
            )

        val today = LocalDate.now(riyadhZone)
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
        val ptPackage =
            ptPackageRepository.save(
                PTPackage(
                    organizationId = org.id, clubId = club.id, branchId = branch.id,
                    memberId = member.id, trainerId = ptTrainer.id, catalogId = catalog.id,
                    sessionsTotal = 10, startsAt = today.minusDays(14), expiresAt = today.plusDays(76),
                ),
            )

        upcomingSession =
            ptSessionRepository.save(
                PTSession(
                    organizationId = org.id, clubId = club.id, branchId = branch.id,
                    trainerId = ptTrainer.id, memberId = member.id, packageId = ptPackage.id,
                    scheduledAt = today.plusDays(1).atTime(10, 0).atZone(riyadhZone).toInstant(),
                ),
            )
        pastSession =
            ptSessionRepository.save(
                PTSession(
                    organizationId = org.id, clubId = club.id, branchId = branch.id,
                    trainerId = ptTrainer.id, memberId = member.id, packageId = ptPackage.id,
                    scheduledAt = today.minusDays(3).atTime(10, 0).atZone(riyadhZone).toInstant(),
                    sessionStatus = "scheduled",
                ),
            )
    }

    @AfterEach
    fun cleanup() {
        ptSessionRepository.deleteAllInBatch()
        ptPackageRepository.deleteAllInBatch()
        ptPackageCatalogRepository.deleteAllInBatch()
        memberRepository.deleteAllInBatch()
        trainerRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
        branchRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    private fun trainerToken(
        trainer: Trainer,
        types: List<String>,
    ): String {
        val claims =
            mapOf(
                "scope" to "trainer",
                "trainerId" to trainer.publicId.toString(),
                "trainerTypes" to types,
                "clubId" to club.publicId.toString(),
                "organizationId" to org.publicId.toString(),
            )
        return "Bearer ${jwtService.generateToken(trainer.publicId.toString(), claims)}"
    }

    @Test
    fun `GET upcoming sessions returns future sessions`() {
        mockMvc.get("/api/v1/coach/pt/sessions?status=upcoming") {
            header("Authorization", trainerToken(ptTrainer, listOf("pt")))
        }.andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(1))
            jsonPath("$[0].status", equalTo("scheduled"))
            jsonPath("$[0].memberName", equalTo("Ahmed Al-Rashidi"))
        }
    }

    @Test
    fun `GET past sessions returns past sessions`() {
        mockMvc.get("/api/v1/coach/pt/sessions?status=past") {
            header("Authorization", trainerToken(ptTrainer, listOf("pt")))
        }.andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(1))
        }
    }

    @Test
    fun `PATCH mark session as attended`() {
        mockMvc.patch("/api/v1/coach/pt/sessions/${upcomingSession.publicId}/attendance") {
            header("Authorization", trainerToken(ptTrainer, listOf("pt")))
            contentType = MediaType.APPLICATION_JSON
            content = """{"status": "attended"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", equalTo("attended"))
        }
    }

    @Test
    fun `PATCH mark session as missed`() {
        mockMvc.patch("/api/v1/coach/pt/sessions/${upcomingSession.publicId}/attendance") {
            header("Authorization", trainerToken(ptTrainer, listOf("pt")))
            contentType = MediaType.APPLICATION_JSON
            content = """{"status": "missed"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status", equalTo("missed"))
        }
    }

    @Test
    fun `PATCH already attended session returns 422`() {
        upcomingSession.sessionStatus = "attended"
        ptSessionRepository.save(upcomingSession)

        mockMvc.patch("/api/v1/coach/pt/sessions/${upcomingSession.publicId}/attendance") {
            header("Authorization", trainerToken(ptTrainer, listOf("pt")))
            contentType = MediaType.APPLICATION_JSON
            content = """{"status": "attended"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    fun `PATCH other trainer's session returns 403`() {
        mockMvc.patch("/api/v1/coach/pt/sessions/${upcomingSession.publicId}/attendance") {
            header("Authorization", trainerToken(otherTrainer, listOf("pt")))
            contentType = MediaType.APPLICATION_JSON
            content = """{"status": "attended"}"""
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `GX-only trainer cannot access PT endpoints`() {
        mockMvc.get("/api/v1/coach/pt/sessions?status=upcoming") {
            header("Authorization", trainerToken(ptTrainer, listOf("gx")))
        }.andExpect {
            status { isForbidden() }
        }
    }
}
