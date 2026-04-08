package com.liyaqa.coach

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.gx.GXClassInstance
import com.liyaqa.gx.GXClassInstanceRepository
import com.liyaqa.gx.GXClassType
import com.liyaqa.gx.GXClassTypeRepository
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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScheduleCoachControllerTest {
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

    @Autowired lateinit var gxClassTypeRepository: GXClassTypeRepository

    @Autowired lateinit var gxClassInstanceRepository: GXClassInstanceRepository

    @MockBean lateinit var permissionService: PermissionService

    private val riyadhZone = ZoneId.of("Asia/Riyadh")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var ptTrainer: Trainer
    private lateinit var gxTrainer: Trainer
    private lateinit var member: Member

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

        val gxUser =
            userRepository.save(
                User(
                    email = "gx@test.com",
                    passwordHash = passwordEncoder.encode("Test@12345678"),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        gxTrainer =
            trainerRepository.save(
                Trainer(
                    organizationId = org.id, clubId = club.id, userId = gxUser.id,
                    firstNameAr = "نورة", firstNameEn = "Noura", lastNameAr = "الحربي", lastNameEn = "Al-Harbi",
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
        val todayAt10 = today.atTime(10, 0).atZone(riyadhZone).toInstant()
        val todayAt14 = today.atTime(14, 0).atZone(riyadhZone).toInstant()

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
                    sessionsTotal = 10, startsAt = today.minusDays(7), expiresAt = today.plusDays(83),
                ),
            )
        ptSessionRepository.save(
            PTSession(
                organizationId = org.id,
                clubId = club.id,
                branchId = branch.id,
                trainerId = ptTrainer.id,
                memberId = member.id,
                packageId = ptPackage.id,
                scheduledAt = todayAt10,
            ),
        )

        val yoga =
            gxClassTypeRepository.save(
                GXClassType(organizationId = org.id, clubId = club.id, nameAr = "يوغا", nameEn = "Yoga", color = "#8B5CF6"),
            )
        gxClassInstanceRepository.save(
            GXClassInstance(
                organizationId = org.id,
                clubId = club.id,
                branchId = branch.id,
                classTypeId = yoga.id,
                instructorId = gxTrainer.id,
                scheduledAt = todayAt14,
                durationMinutes = 60,
                capacity = 15,
            ),
        )
    }

    @AfterEach
    fun cleanup() {
        ptSessionRepository.deleteAllInBatch()
        ptPackageRepository.deleteAllInBatch()
        ptPackageCatalogRepository.deleteAllInBatch()
        gxClassInstanceRepository.deleteAllInBatch()
        gxClassTypeRepository.deleteAllInBatch()
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
    fun `PT trainer sees only PT sessions in schedule`() {
        mockMvc.get("/api/v1/coach/schedule") {
            header("Authorization", trainerToken(ptTrainer, listOf("pt")))
        }.andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(1))
            jsonPath("$[0].type", equalTo("pt"))
            jsonPath("$[0].memberOrClassName", equalTo("Ahmed Al-Rashidi"))
        }
    }

    @Test
    fun `GX trainer sees only GX classes in schedule`() {
        mockMvc.get("/api/v1/coach/schedule") {
            header("Authorization", trainerToken(gxTrainer, listOf("gx")))
        }.andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(1))
            jsonPath("$[0].type", equalTo("gx"))
            jsonPath("$[0].title", equalTo("Yoga"))
        }
    }

    @Test
    fun `dual trainer sees both PT and GX in schedule`() {
        val dualUser =
            userRepository.save(
                User(
                    email = "dual@test.com",
                    passwordHash = passwordEncoder.encode("Test@12345678"),
                    organizationId = org.id,
                    clubId = club.id,
                ),
            )
        val dualTrainer =
            trainerRepository.save(
                Trainer(
                    organizationId = org.id,
                    clubId = club.id,
                    userId = dualUser.id,
                    firstNameAr = "ثنائي",
                    firstNameEn = "Dual",
                    lastNameAr = "مدرب",
                    lastNameEn = "Trainer",
                    joinedAt = LocalDate.now(),
                ),
            )

        val today = LocalDate.now(riyadhZone)
        val catalog = ptPackageCatalogRepository.findAll().first()
        val ptPackage =
            ptPackageRepository.save(
                PTPackage(
                    organizationId = org.id, clubId = club.id, branchId = branch.id,
                    memberId = member.id, trainerId = dualTrainer.id, catalogId = catalog.id,
                    sessionsTotal = 10, startsAt = today.minusDays(7), expiresAt = today.plusDays(83),
                ),
            )
        ptSessionRepository.save(
            PTSession(
                organizationId = org.id,
                clubId = club.id,
                branchId = branch.id,
                trainerId = dualTrainer.id,
                memberId = member.id,
                packageId = ptPackage.id,
                scheduledAt = today.atTime(11, 0).atZone(riyadhZone).toInstant(),
            ),
        )

        val yoga = gxClassTypeRepository.findAll().first()
        gxClassInstanceRepository.save(
            GXClassInstance(
                organizationId = org.id,
                clubId = club.id,
                branchId = branch.id,
                classTypeId = yoga.id,
                instructorId = dualTrainer.id,
                scheduledAt = today.atTime(16, 0).atZone(riyadhZone).toInstant(),
                durationMinutes = 60,
                capacity = 15,
            ),
        )

        mockMvc.get("/api/v1/coach/schedule") {
            header("Authorization", trainerToken(dualTrainer, listOf("pt", "gx")))
        }.andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(2))
            jsonPath("$[0].type", equalTo("pt"))
            jsonPath("$[1].type", equalTo("gx"))
        }
    }

    @Test
    fun `date more than 30 days ahead returns 422`() {
        val futureDate = LocalDate.now(riyadhZone).plusDays(31).format(DateTimeFormatter.ISO_DATE)
        mockMvc.get("/api/v1/coach/schedule?date=$futureDate") {
            header("Authorization", trainerToken(ptTrainer, listOf("pt")))
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    fun `returns 401 without authentication`() {
        mockMvc.get("/api/v1/coach/schedule")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `non-trainer scope returns 403`() {
        val claims =
            mapOf(
                "scope" to "club",
                "organizationId" to org.publicId.toString(),
                "clubId" to club.publicId.toString(),
            )
        val token = "Bearer ${jwtService.generateToken("staff-user", claims)}"
        mockMvc.get("/api/v1/coach/schedule") {
            header("Authorization", token)
        }.andExpect {
            status { isForbidden() }
        }
    }
}
