package com.liyaqa.gx

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.portal.ClubPortalSettings
import com.liyaqa.portal.ClubPortalSettingsRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtService
import com.liyaqa.trainer.Trainer
import com.liyaqa.trainer.TrainerRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GXWaitlistControllerIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Autowired lateinit var jwtService: JwtService

    @Autowired lateinit var organizationRepository: OrganizationRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var branchRepository: BranchRepository

    @Autowired lateinit var userRepository: UserRepository

    @Autowired lateinit var memberRepository: MemberRepository

    @Autowired lateinit var classTypeRepository: GXClassTypeRepository

    @Autowired lateinit var classInstanceRepository: GXClassInstanceRepository

    @Autowired lateinit var bookingRepository: GXBookingRepository

    @Autowired lateinit var waitlistRepository: GXWaitlistRepository

    @Autowired lateinit var trainerRepository: TrainerRepository

    @Autowired lateinit var portalSettingsRepository: ClubPortalSettingsRepository

    @MockBean lateinit var permissionService: PermissionService

    private lateinit var org: Organization
    private lateinit var club: Club
    private lateinit var branch: Branch
    private lateinit var member: Member
    private lateinit var memberUser: User
    private lateinit var fullClassInstance: GXClassInstance
    private lateinit var availableClassInstance: GXClassInstance

    companion object {
        private const val TEST_PASSWORD = "Test@12345678"
    }

    @BeforeEach
    fun setup() {
        cleanup()

        org =
            organizationRepository.save(
                Organization(
                    nameAr = "Test Org", nameEn = "Test Org",
                    email = "test@test.com", country = "SA", timezone = "Asia/Riyadh",
                ),
            )
        club =
            clubRepository.save(
                Club(organizationId = org.id, nameAr = "Test Club", nameEn = "Test Club"),
            )
        branch =
            branchRepository.save(
                Branch(
                    organizationId = org.id, clubId = club.id,
                    nameAr = "Test Branch", nameEn = "Test Branch",
                ),
            )

        portalSettingsRepository.save(
            ClubPortalSettings(
                clubId = club.id,
                gxBookingEnabled = true,
                ptViewEnabled = true,
                invoiceViewEnabled = true,
            ),
        )

        memberUser =
            userRepository.save(
                User(email = "member@test.com", passwordHash = "hash"),
            )
        member =
            memberRepository.save(
                Member(
                    organizationId = org.id, clubId = club.id, branchId = branch.id,
                    userId = memberUser.id,
                    firstNameAr = "أحمد", firstNameEn = "Ahmed",
                    lastNameAr = "الرشيدي", lastNameEn = "Al-Rashidi",
                    phone = "+966501234567",
                ),
            )

        val trainer =
            trainerRepository.save(
                Trainer(
                    organizationId = org.id, clubId = club.id, userId = memberUser.id,
                    firstNameAr = "نورا", firstNameEn = "Noura",
                    lastNameAr = "الحربي", lastNameEn = "Al-Harbi",
                    phone = "+966509999999",
                    joinedAt = java.time.LocalDate.now(),
                ),
            )

        val classType =
            classTypeRepository.save(
                GXClassType(
                    organizationId = org.id,
                    clubId = club.id,
                    nameAr = "يوغا",
                    nameEn = "Yoga",
                    defaultDurationMinutes = 60,
                    defaultCapacity = 15,
                    color = "#8B5CF6",
                ),
            )

        // Full class (capacity 1, 1 booking)
        fullClassInstance =
            classInstanceRepository.save(
                GXClassInstance(
                    organizationId = org.id, clubId = club.id, branchId = branch.id,
                    classTypeId = classType.id, instructorId = trainer.id,
                    scheduledAt = Instant.now().plus(2, ChronoUnit.DAYS),
                    capacity = 1, bookingsCount = 1,
                ),
            )

        // Available class (capacity 10, 0 bookings)
        availableClassInstance =
            classInstanceRepository.save(
                GXClassInstance(
                    organizationId = org.id, clubId = club.id, branchId = branch.id,
                    classTypeId = classType.id, instructorId = trainer.id,
                    scheduledAt = Instant.now().plus(3, ChronoUnit.DAYS),
                    capacity = 10, bookingsCount = 0,
                ),
            )
    }

    @AfterEach
    fun cleanup() {
        waitlistRepository.deleteAll()
        bookingRepository.deleteAll()
        classInstanceRepository.deleteAll()
        classTypeRepository.deleteAll()
        portalSettingsRepository.deleteAll()
        memberRepository.deleteAll()
        trainerRepository.deleteAll()
        userRepository.deleteAll()
        branchRepository.deleteAll()
        clubRepository.deleteAll()
        organizationRepository.deleteAll()
    }

    private fun memberToken(): String {
        return "Bearer ${
            jwtService.generateToken(
                "member@test.com",
                mapOf(
                    "scope" to "member",
                    "memberId" to member.publicId.toString(),
                    "organizationId" to org.publicId.toString(),
                    "clubId" to club.publicId.toString(),
                    "branchId" to branch.publicId.toString(),
                ),
            )
        }"
    }

    private fun staffToken(vararg permissions: String): String {
        val roleId = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")
        permissions.forEach { whenever(permissionService.hasPermission(roleId, it)).thenReturn(true) }
        return "Bearer ${
            jwtService.generateToken(
                "staff@test.com",
                mapOf(
                    "roleId" to roleId.toString(),
                    "scope" to "club",
                    "organizationId" to org.publicId.toString(),
                    "clubId" to club.publicId.toString(),
                ),
            )
        }"
    }

    @Test
    fun `POST arena gx waitlist returns 201 with correct position`() {
        mockMvc.post("/api/v1/arena/gx/${fullClassInstance.publicId}/waitlist") {
            header("Authorization", memberToken())
        }.andExpect {
            status { isCreated() }
            jsonPath("$.position") { value(1) }
            jsonPath("$.status") { value("WAITING") }
        }
    }

    @Test
    fun `POST arena gx waitlist returns 409 when class has available spots`() {
        mockMvc.post("/api/v1/arena/gx/${availableClassInstance.publicId}/waitlist") {
            header("Authorization", memberToken())
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `POST arena gx waitlist returns 409 when already on waitlist`() {
        // First join
        mockMvc.post("/api/v1/arena/gx/${fullClassInstance.publicId}/waitlist") {
            header("Authorization", memberToken())
        }.andExpect {
            status { isCreated() }
        }

        // Second join — should fail
        mockMvc.post("/api/v1/arena/gx/${fullClassInstance.publicId}/waitlist") {
            header("Authorization", memberToken())
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `DELETE arena gx waitlist removes WAITING entry`() {
        // Join first
        mockMvc.post("/api/v1/arena/gx/${fullClassInstance.publicId}/waitlist") {
            header("Authorization", memberToken())
        }.andExpect {
            status { isCreated() }
        }

        // Leave
        mockMvc.delete("/api/v1/arena/gx/${fullClassInstance.publicId}/waitlist") {
            header("Authorization", memberToken())
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `POST arena gx waitlist accept creates booking when entry is OFFERED`() {
        // Join waitlist
        mockMvc.post("/api/v1/arena/gx/${fullClassInstance.publicId}/waitlist") {
            header("Authorization", memberToken())
        }.andExpect {
            status { isCreated() }
        }

        // Manually set the entry to OFFERED (simulating promotion)
        val entry = waitlistRepository.findByClassAndMember(fullClassInstance.id, member.id)!!
        entry.status = GXWaitlistStatus.OFFERED
        entry.notifiedAt = Instant.now()
        waitlistRepository.save(entry)

        // Increase capacity so acceptance succeeds
        fullClassInstance.capacity = 2
        classInstanceRepository.save(fullClassInstance)

        mockMvc.post("/api/v1/arena/gx/${fullClassInstance.publicId}/waitlist/accept") {
            header("Authorization", memberToken())
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `POST arena gx waitlist accept returns 409 when entry is WAITING not OFFERED`() {
        // Join waitlist (stays in WAITING)
        mockMvc.post("/api/v1/arena/gx/${fullClassInstance.publicId}/waitlist") {
            header("Authorization", memberToken())
        }.andExpect {
            status { isCreated() }
        }

        mockMvc.post("/api/v1/arena/gx/${fullClassInstance.publicId}/waitlist/accept") {
            header("Authorization", memberToken())
        }.andExpect {
            status { isConflict() }
        }
    }

    @Test
    fun `GET pulse gx classes waitlist-entries returns list of entries for staff`() {
        // Add a waitlist entry
        waitlistRepository.save(
            GXWaitlistEntry(
                classInstanceId = fullClassInstance.id,
                memberId = member.id,
                position = 1,
            ),
        )

        mockMvc.get("/api/v1/gx/classes/${fullClassInstance.publicId}/waitlist-entries") {
            header("Authorization", staffToken("gx-class:manage-bookings"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.waitlistCount") { value(1) }
        }
    }

    @Test
    fun `GET pulse gx classes waitlist-entries returns 403 without gx-class manage-bookings`() {
        mockMvc.get("/api/v1/gx/classes/${fullClassInstance.publicId}/waitlist-entries") {
            header("Authorization", staffToken())
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `DELETE pulse gx classes waitlist-entries removes entry`() {
        val entry =
            waitlistRepository.save(
                GXWaitlistEntry(
                    classInstanceId = fullClassInstance.id,
                    memberId = member.id,
                    position = 1,
                ),
            )

        mockMvc.delete("/api/v1/gx/classes/${fullClassInstance.publicId}/waitlist-entries/${entry.publicId}") {
            header("Authorization", staffToken("gx-class:manage-bookings"))
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `cancelling booking promotes next WAITING member to OFFERED`() {
        // Create a confirmed booking for another user
        val otherUser = userRepository.save(User(email = "other@test.com", passwordHash = "hash"))
        val otherMember =
            memberRepository.save(
                Member(
                    organizationId = org.id, clubId = club.id, branchId = branch.id,
                    userId = otherUser.id,
                    firstNameAr = "سارة", firstNameEn = "Sarah",
                    lastNameAr = "المنصوري", lastNameEn = "Al-Mansouri",
                    phone = "+966509876543",
                ),
            )

        val booking =
            bookingRepository.save(
                GXBooking(
                    organizationId = org.id,
                    clubId = club.id,
                    instanceId = fullClassInstance.id,
                    memberId = otherMember.id,
                    bookingStatus = "confirmed",
                ),
            )

        // Member joins waitlist
        waitlistRepository.save(
            GXWaitlistEntry(
                classInstanceId = fullClassInstance.id,
                memberId = member.id,
                position = 1,
            ),
        )

        // Staff cancels the booking
        mockMvc.delete("/api/v1/gx/classes/${fullClassInstance.publicId}/bookings/${booking.publicId}") {
            header("Authorization", staffToken("gx-class:manage-bookings"))
        }.andExpect {
            status { isOk() }
        }

        // Verify the waitlist entry was promoted to OFFERED
        val updated = waitlistRepository.findByClassAndMember(fullClassInstance.id, member.id)
        assertThat(updated?.status).isEqualTo(GXWaitlistStatus.OFFERED)
    }
}
