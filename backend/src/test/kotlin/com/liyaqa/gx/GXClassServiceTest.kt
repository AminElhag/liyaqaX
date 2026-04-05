package com.liyaqa.gx

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.gx.dto.CreateGXClassInstanceRequest
import com.liyaqa.gx.dto.CreateGXClassTypeRequest
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.trainer.Trainer
import com.liyaqa.trainer.TrainerRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class GXClassServiceTest {
    @Mock lateinit var classTypeRepository: GXClassTypeRepository

    @Mock lateinit var classInstanceRepository: GXClassInstanceRepository

    @Mock lateinit var bookingRepository: GXBookingRepository

    @Mock lateinit var organizationRepository: OrganizationRepository

    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var branchRepository: BranchRepository

    @Mock lateinit var trainerRepository: TrainerRepository

    @InjectMocks lateinit var service: GXClassService

    // ── Helpers ────────────────────────────────────────────────────────────

    private val orgPublicId = UUID.randomUUID()
    private val clubPublicId = UUID.randomUUID()
    private val branchPublicId = UUID.randomUUID()

    private fun organization() =
        Organization(
            nameAr = "Test",
            nameEn = "Test Org",
            email = "test@test.com",
            country = "SA",
            timezone = "Asia/Riyadh",
        ).apply {
            val idField = this::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, 1L)
        }

    private fun club(orgId: Long = 1L) =
        Club(
            organizationId = orgId,
            nameAr = "Test",
            nameEn = "Test Club",
            email = "club@test.com",
        ).apply {
            val idField = this::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, 10L)
        }

    private fun branch(
        orgId: Long = 1L,
        clubId: Long = 10L,
    ) = Branch(
        organizationId = orgId,
        clubId = clubId,
        nameAr = "Test",
        nameEn = "Test Branch",
        city = "Riyadh",
    ).apply {
        val idField = this::class.java.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(this, 100L)
    }

    private fun trainer(
        orgId: Long = 1L,
        clubId: Long = 10L,
    ) = Trainer(
        organizationId = orgId,
        clubId = clubId,
        userId = 5L,
        firstNameAr = "Test",
        firstNameEn = "Test",
        lastNameAr = "Trainer",
        lastNameEn = "Trainer",
        joinedAt = LocalDate.now(),
    ).apply {
        val idField = this::class.java.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(this, 50L)
    }

    private fun classType(
        orgId: Long = 1L,
        clubId: Long = 10L,
    ) = GXClassType(
        organizationId = orgId,
        clubId = clubId,
        nameAr = "يوغا",
        nameEn = "Yoga",
        defaultDurationMinutes = 60,
        defaultCapacity = 15,
    ).apply {
        val idField = this::class.java.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(this, 200L)
    }

    private fun stubOrgAndClub() {
        val org = organization()
        val club = club()
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId))
            .thenReturn(Optional.of(org))
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, org.id))
            .thenReturn(Optional.of(club))
    }

    // ── Class Type CRUD ────────────────────────────────────────────────────

    @Test
    fun `create class type successfully`() {
        stubOrgAndClub()
        whenever(classTypeRepository.save(any<GXClassType>()))
            .thenAnswer { it.arguments[0] as GXClassType }

        val request =
            CreateGXClassTypeRequest(
                nameAr = "يوغا",
                nameEn = "Yoga",
                defaultDurationMinutes = 60,
                defaultCapacity = 15,
                color = "#8B5CF6",
            )

        val response = service.createClassType(orgPublicId, clubPublicId, request)

        assertThat(response.nameEn).isEqualTo("Yoga")
        assertThat(response.defaultDurationMinutes).isEqualTo(60)
        assertThat(response.color).isEqualTo("#8B5CF6")
    }

    // ── Rule 5: Instructor club scope ──────────────────────────────────────

    @Test
    fun `create instance fails when instructor belongs to different club`() {
        val org = organization()
        val club = club()
        val branch = branch()
        val ct = classType()
        val wrongClubTrainer = trainer(clubId = 999L)

        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId))
            .thenReturn(Optional.of(org))
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, org.id))
            .thenReturn(Optional.of(club))
        whenever(branchRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(branchPublicId, org.id))
            .thenReturn(Optional.of(branch))

        val classTypePublicId = ct.publicId
        val instructorPublicId = wrongClubTrainer.publicId
        whenever(classTypeRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(classTypePublicId, org.id, club.id))
            .thenReturn(Optional.of(ct))
        whenever(trainerRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(instructorPublicId, org.id, club.id))
            .thenReturn(Optional.of(wrongClubTrainer))

        val request =
            CreateGXClassInstanceRequest(
                classTypeId = classTypePublicId,
                instructorId = instructorPublicId,
                scheduledAt = Instant.now().plusSeconds(86400),
            )

        assertThatThrownBy { service.createClassInstance(orgPublicId, clubPublicId, branchPublicId, request) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Rule 10: Instructor double-booking ─────────────────────────────────

    @Test
    fun `create instance fails when instructor has overlapping class`() {
        val org = organization()
        val club = club()
        val branch = branch()
        val ct = classType()
        val instructor = trainer()

        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId))
            .thenReturn(Optional.of(org))
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, org.id))
            .thenReturn(Optional.of(club))
        whenever(branchRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(branchPublicId, org.id))
            .thenReturn(Optional.of(branch))

        val classTypePublicId = ct.publicId
        val instructorPublicId = instructor.publicId
        whenever(classTypeRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(classTypePublicId, org.id, club.id))
            .thenReturn(Optional.of(ct))
        whenever(trainerRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(instructorPublicId, org.id, club.id))
            .thenReturn(Optional.of(instructor))
        whenever(classInstanceRepository.existsOverlappingInstance(any(), any(), any()))
            .thenReturn(1)

        val request =
            CreateGXClassInstanceRequest(
                classTypeId = classTypePublicId,
                instructorId = instructorPublicId,
                scheduledAt = Instant.now().plusSeconds(86400),
            )

        assertThatThrownBy { service.createClassInstance(orgPublicId, clubPublicId, branchPublicId, request) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `create instance successfully when no overlap`() {
        val org = organization()
        val club = club()
        val branch = branch()
        val ct = classType()
        val instructor = trainer()

        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId))
            .thenReturn(Optional.of(org))
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, org.id))
            .thenReturn(Optional.of(club))
        whenever(branchRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(branchPublicId, org.id))
            .thenReturn(Optional.of(branch))
        whenever(
            classTypeRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(
                ct.publicId,
                org.id,
                club.id,
            ),
        ).thenReturn(Optional.of(ct))
        whenever(
            trainerRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(
                instructor.publicId,
                org.id,
                club.id,
            ),
        ).thenReturn(Optional.of(instructor))
        whenever(classInstanceRepository.existsOverlappingInstance(any(), any(), any()))
            .thenReturn(0)
        whenever(classInstanceRepository.save(any<GXClassInstance>()))
            .thenAnswer { it.arguments[0] as GXClassInstance }

        val request =
            CreateGXClassInstanceRequest(
                classTypeId = ct.publicId,
                instructorId = instructor.publicId,
                scheduledAt = Instant.now().plusSeconds(86400),
                room = "Room 1",
            )

        val response = service.createClassInstance(orgPublicId, clubPublicId, branchPublicId, request)

        assertThat(response.classType.nameEn).isEqualTo("Yoga")
        assertThat(response.room).isEqualTo("Room 1")
        assertThat(response.status).isEqualTo("scheduled")
    }

    // ── Rule 7 — Attendance window (tested via cancel status) ──────────────

    @Test
    fun `cancel instance that is already cancelled throws conflict`() {
        val org = organization()
        val instance =
            GXClassInstance(
                organizationId = org.id,
                clubId = 10L,
                branchId = 100L,
                classTypeId = 200L,
                instructorId = 50L,
                scheduledAt = Instant.now(),
                instanceStatus = "cancelled",
            )

        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId))
            .thenReturn(Optional.of(org))
        whenever(classInstanceRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(instance.publicId, org.id))
            .thenReturn(Optional.of(instance))

        assertThatThrownBy { service.cancelClassInstance(orgPublicId, instance.publicId) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }
}
