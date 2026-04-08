package com.liyaqa.coach

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.gx.GXAttendanceRepository
import com.liyaqa.gx.GXBooking
import com.liyaqa.gx.GXBookingRepository
import com.liyaqa.gx.GXClassInstance
import com.liyaqa.gx.GXClassInstanceRepository
import com.liyaqa.gx.GXClassType
import com.liyaqa.gx.GXClassTypeRepository
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GxCoachControllerTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var passwordEncoder: PasswordEncoder

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var branchRepository: BranchRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var trainerRepository: TrainerRepository

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var gxClassTypeRepository: GXClassTypeRepository

    @Autowired lateinit var gxClassInstanceRepository: GXClassInstanceRepository

    @Autowired lateinit var gxBookingRepository: GXBookingRepository

    @Autowired lateinit var gxAttendanceRepository: GXAttendanceRepository

    @MockBean lateinit var permissionService: PermissionService

    private val riyadhZone = ZoneId.of("Asia/Riyadh")

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var gxTrainer: Trainer
    private lateinit var otherTrainer: Trainer
    private lateinit var member: Member
    private lateinit var pastInstance: GXClassInstance
    private lateinit var futureInstance: GXClassInstance
    private lateinit var booking: GXBooking

    @BeforeEach
    fun setup() {
        org = organizationRepository.save(Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com"))
        club = clubRepository.save(Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"))
        branch = branchRepository.save(Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Branch"))

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

        val yoga =
            gxClassTypeRepository.save(
                GXClassType(organizationId = org.id, clubId = club.id, nameAr = "يوغا", nameEn = "Yoga", color = "#8B5CF6"),
            )

        pastInstance =
            gxClassInstanceRepository.save(
                GXClassInstance(
                    organizationId = org.id, clubId = club.id, branchId = branch.id,
                    classTypeId = yoga.id, instructorId = gxTrainer.id,
                    scheduledAt = Instant.now().minus(2, ChronoUnit.HOURS),
                    durationMinutes = 60, capacity = 15, bookingsCount = 1,
                ),
            )

        futureInstance =
            gxClassInstanceRepository.save(
                GXClassInstance(
                    organizationId = org.id, clubId = club.id, branchId = branch.id,
                    classTypeId = yoga.id, instructorId = gxTrainer.id,
                    scheduledAt = Instant.now().plus(2, ChronoUnit.HOURS),
                    durationMinutes = 60, capacity = 15, bookingsCount = 1,
                ),
            )

        booking =
            gxBookingRepository.save(
                GXBooking(
                    organizationId = org.id, clubId = club.id,
                    instanceId = pastInstance.id, memberId = member.id, bookingStatus = "confirmed",
                ),
            )

        gxBookingRepository.save(
            GXBooking(
                organizationId = org.id,
                clubId = club.id,
                instanceId = futureInstance.id,
                memberId = member.id,
                bookingStatus = "confirmed",
            ),
        )
    }

    @AfterEach
    fun cleanup() {
        gxAttendanceRepository.deleteAllInBatch()
        gxBookingRepository.deleteAllInBatch()
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
    fun `GET classes returns instructor's classes`() {
        val today = LocalDate.now(riyadhZone)
        val from = today.minusDays(1)
        val to = today.plusDays(1)
        mockMvc.get("/api/v1/coach/gx/classes?from=$from&to=$to") {
            header("Authorization", trainerToken(gxTrainer, listOf("gx")))
        }.andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(2))
        }
    }

    @Test
    fun `GET class bookings returns booking list`() {
        mockMvc.get("/api/v1/coach/gx/classes/${pastInstance.publicId}/bookings") {
            header("Authorization", trainerToken(gxTrainer, listOf("gx")))
        }.andExpect {
            status { isOk() }
            jsonPath("$", hasSize<Any>(1))
            jsonPath("$[0].memberName", equalTo("Ahmed Al-Rashidi"))
        }
    }

    @Test
    fun `PATCH mark attendance on past class succeeds`() {
        mockMvc.patch("/api/v1/coach/gx/classes/${pastInstance.publicId}/attendance") {
            header("Authorization", trainerToken(gxTrainer, listOf("gx")))
            contentType = MediaType.APPLICATION_JSON
            content = """{"attendances": [{"bookingId": "${booking.publicId}", "attended": true}]}"""
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `PATCH mark attendance on future class returns 422`() {
        val futureBooking = gxBookingRepository.findAllByInstanceIdAndBookingStatusIn(futureInstance.id, listOf("confirmed")).first()
        mockMvc.patch("/api/v1/coach/gx/classes/${futureInstance.publicId}/attendance") {
            header("Authorization", trainerToken(gxTrainer, listOf("gx")))
            contentType = MediaType.APPLICATION_JSON
            content = """{"attendances": [{"bookingId": "${futureBooking.publicId}", "attended": true}]}"""
        }.andExpect {
            status { isUnprocessableEntity() }
        }
    }

    @Test
    fun `other trainer's class returns 404`() {
        mockMvc.get("/api/v1/coach/gx/classes/${pastInstance.publicId}/bookings") {
            header("Authorization", trainerToken(otherTrainer, listOf("gx")))
        }.andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `PT-only trainer cannot access GX endpoints`() {
        mockMvc.get("/api/v1/coach/gx/classes") {
            header("Authorization", trainerToken(gxTrainer, listOf("pt")))
        }.andExpect {
            status { isForbidden() }
        }
    }
}
