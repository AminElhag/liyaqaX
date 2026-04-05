package com.liyaqa.trainer

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.Role
import com.liyaqa.role.RoleRepository
import com.liyaqa.staff.StaffMemberRepository
import com.liyaqa.trainer.dto.CreateTrainerRequest
import com.liyaqa.trainer.dto.UpdateTrainerRequest
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class TrainerServiceTest {
    @Mock lateinit var trainerRepository: TrainerRepository

    @Mock lateinit var trainerBranchAssignmentRepository: TrainerBranchAssignmentRepository

    @Mock lateinit var trainerCertificationRepository: TrainerCertificationRepository

    @Mock lateinit var trainerSpecializationRepository: TrainerSpecializationRepository

    @Mock lateinit var userRepository: UserRepository

    @Mock lateinit var userRoleRepository: UserRoleRepository

    @Mock lateinit var roleRepository: RoleRepository

    @Mock lateinit var branchRepository: BranchRepository

    @Mock lateinit var organizationRepository: OrganizationRepository

    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var staffMemberRepository: StaffMemberRepository

    @Mock lateinit var passwordEncoder: PasswordEncoder

    @InjectMocks lateinit var service: TrainerService

    private val org = Organization(nameAr = "منظمة", nameEn = "Org", email = "o@t.com")
    private val club = Club(organizationId = org.id, nameAr = "نادي", nameEn = "Club")
    private val branch = Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Branch")

    private val ptRole =
        Role(nameAr = "مدرب شخصي", nameEn = "PT Trainer", scope = "trainer", organizationId = org.id, clubId = club.id)

    private val callerUser = User(email = "caller@test.com", passwordHash = "hash", organizationId = org.id, clubId = club.id)

    private fun createRequest(
        trainerTypes: List<String> = listOf("pt"),
        branchIds: List<UUID> = listOf(branch.publicId),
    ) = CreateTrainerRequest(
        email = "trainer@test.com",
        password = "Test@12345678",
        firstNameAr = "خالد",
        firstNameEn = "Khalid",
        lastNameAr = "الشمري",
        lastNameEn = "Al-Shammari",
        trainerTypes = trainerTypes,
        branchIds = branchIds,
        joinedAt = LocalDate.of(2025, 1, 1),
    )

    private fun stubOrgAndClub() {
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId)).thenReturn(Optional.of(org))
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(club.publicId, org.id))
            .thenReturn(Optional.of(club))
    }

    private fun stubBranchLookup() {
        whenever(
            branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branch.publicId, org.id, club.id),
        ).thenReturn(Optional.of(branch))
    }

    private fun stubTrainerRoleLookup() {
        whenever(roleRepository.findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(org.id, club.id))
            .thenReturn(listOf(ptRole))
    }

    private fun stubCreateHappyPath() {
        stubOrgAndClub()
        stubBranchLookup()
        stubTrainerRoleLookup()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("trainer@test.com")).thenReturn(Optional.empty())
        whenever(staffMemberRepository.existsByUserIdAndDeletedAtIsNull(any())).thenReturn(false)
        whenever(passwordEncoder.encode(any())).thenReturn("encoded")
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] as User }
        whenever(userRoleRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(trainerRepository.save(any<Trainer>())).thenAnswer { it.arguments[0] as Trainer }
        whenever(trainerBranchAssignmentRepository.saveAll(any<Iterable<TrainerBranchAssignment>>()))
            .thenAnswer { it.arguments[0] as Iterable<*> }
    }

    // ── Rule 1: Email uniqueness ─────────────────────────────────────────────

    @Test
    fun `create with duplicate email throws conflict`() {
        stubOrgAndClub()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("trainer@test.com"))
            .thenReturn(Optional.of(callerUser))

        assertThatThrownBy {
            service.create(org.publicId, club.publicId, callerUser.publicId, createRequest())
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    // ── Rule 2: Minimum one trainerType ──────────────────────────────────────

    @Test
    fun `create with empty trainerTypes throws 422`() {
        stubOrgAndClub()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("trainer@test.com")).thenReturn(Optional.empty())

        assertThatThrownBy {
            service.create(org.publicId, club.publicId, callerUser.publicId, createRequest(trainerTypes = emptyList()))
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `create with invalid trainerType throws 422`() {
        stubOrgAndClub()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("trainer@test.com")).thenReturn(Optional.empty())

        assertThatThrownBy {
            service.create(
                org.publicId,
                club.publicId,
                callerUser.publicId,
                createRequest(trainerTypes = listOf("invalid")),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Rule 3: Branch scope validation ──────────────────────────────────────

    @Test
    fun `create with branch from different club throws 422`() {
        stubOrgAndClub()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("trainer@test.com")).thenReturn(Optional.empty())
        val invalidBranchId = UUID.randomUUID()
        whenever(branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(invalidBranchId, org.id, club.id))
            .thenReturn(Optional.empty())

        assertThatThrownBy {
            service.create(
                org.publicId,
                club.publicId,
                callerUser.publicId,
                createRequest(branchIds = listOf(invalidBranchId)),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Rule 4: Minimum one branch ───────────────────────────────────────────

    @Test
    fun `remove last branch throws 422`() {
        stubOrgAndClub()
        val trainer =
            Trainer(
                organizationId = org.id,
                clubId = club.id,
                userId = 1L,
                firstNameAr = "خالد",
                firstNameEn = "Khalid",
                lastNameAr = "ش",
                lastNameEn = "S",
                joinedAt = LocalDate.now(),
            )
        whenever(
            trainerRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(trainer.publicId, org.id, club.id),
        ).thenReturn(Optional.of(trainer))
        whenever(
            branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branch.publicId, org.id, club.id),
        ).thenReturn(Optional.of(branch))
        whenever(trainerBranchAssignmentRepository.existsByTrainerIdAndBranchId(trainer.id, branch.id)).thenReturn(true)
        whenever(trainerBranchAssignmentRepository.countByTrainerId(trainer.id)).thenReturn(1L)

        assertThatThrownBy {
            service.removeBranch(org.publicId, club.publicId, trainer.publicId, branch.publicId)
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Rule 5: Soft delete cascade ──────────────────────────────────────────

    @Test
    fun `delete soft deletes trainer and deactivates user`() {
        stubOrgAndClub()
        val targetUser = User(email = "target@test.com", passwordHash = "hash", organizationId = org.id, clubId = club.id)
        val trainer =
            Trainer(
                organizationId = org.id,
                clubId = club.id,
                userId = targetUser.id,
                firstNameAr = "خالد",
                firstNameEn = "Khalid",
                lastNameAr = "ش",
                lastNameEn = "S",
                joinedAt = LocalDate.now(),
            )
        whenever(
            trainerRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(trainer.publicId, org.id, club.id),
        ).thenReturn(Optional.of(trainer))
        val differentCaller = User(email = "diff@test.com", passwordHash = "hash")
        setEntityId(differentCaller, 999L)
        whenever(userRepository.findByPublicIdAndDeletedAtIsNull(differentCaller.publicId))
            .thenReturn(Optional.of(differentCaller))
        whenever(userRepository.findById(targetUser.id)).thenReturn(Optional.of(targetUser))
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] as User }
        whenever(trainerRepository.save(any<Trainer>())).thenAnswer { it.arguments[0] as Trainer }

        service.delete(org.publicId, club.publicId, trainer.publicId, differentCaller.publicId)

        assertThat(trainer.deletedAt).isNotNull()
        assertThat(targetUser.isActive).isFalse()
        verify(userRepository).save(targetUser)
        verify(trainerRepository).save(trainer)
    }

    // ── Rule 6: Self-deletion prevention ─────────────────────────────────────

    @Test
    fun `delete own account throws 422`() {
        stubOrgAndClub()
        val trainer =
            Trainer(
                organizationId = org.id,
                clubId = club.id,
                userId = callerUser.id,
                firstNameAr = "خالد",
                firstNameEn = "Khalid",
                lastNameAr = "ش",
                lastNameEn = "S",
                joinedAt = LocalDate.now(),
            )
        whenever(
            trainerRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(trainer.publicId, org.id, club.id),
        ).thenReturn(Optional.of(trainer))
        whenever(userRepository.findByPublicIdAndDeletedAtIsNull(callerUser.publicId))
            .thenReturn(Optional.of(callerUser))

        assertThatThrownBy {
            service.delete(org.publicId, club.publicId, trainer.publicId, callerUser.publicId)
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Rule 7: No dual profile ──────────────────────────────────────────────

    @Test
    fun `create throws conflict if user is already staff`() {
        stubOrgAndClub()
        stubBranchLookup()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("trainer@test.com")).thenReturn(Optional.empty())
        whenever(passwordEncoder.encode(any())).thenReturn("encoded")
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] as User }
        whenever(staffMemberRepository.existsByUserIdAndDeletedAtIsNull(any())).thenReturn(true)

        assertThatThrownBy {
            service.create(org.publicId, club.publicId, callerUser.publicId, createRequest())
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    fun `create trainer succeeds with valid data`() {
        stubCreateHappyPath()

        val response =
            service.create(org.publicId, club.publicId, callerUser.publicId, createRequest())

        assertThat(response.firstNameEn).isEqualTo("Khalid")
        assertThat(response.lastNameEn).isEqualTo("Al-Shammari")
        assertThat(response.email).isEqualTo("trainer@test.com")
        assertThat(response.trainerTypes).containsExactly("pt")
        assertThat(response.branches).hasSize(1)
        assertThat(response.isActive).isTrue()
        verify(userRepository).save(any<User>())
        verify(trainerRepository).save(any<Trainer>())
    }

    @Test
    fun `update trainer updates only provided fields`() {
        stubOrgAndClub()
        val trainer =
            Trainer(
                organizationId = org.id,
                clubId = club.id,
                userId = callerUser.id,
                firstNameAr = "خالد",
                firstNameEn = "Khalid",
                lastNameAr = "الشمري",
                lastNameEn = "Al-Shammari",
                joinedAt = LocalDate.now(),
            )
        whenever(
            trainerRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(trainer.publicId, org.id, club.id),
        ).thenReturn(Optional.of(trainer))
        whenever(trainerRepository.save(any<Trainer>())).thenAnswer { it.arguments[0] as Trainer }
        whenever(userRepository.findById(callerUser.id)).thenReturn(Optional.of(callerUser))
        whenever(userRoleRepository.findAllByUserId(callerUser.id)).thenReturn(emptyList())
        whenever(trainerBranchAssignmentRepository.findAllByTrainerId(trainer.id)).thenReturn(emptyList())
        whenever(trainerCertificationRepository.findAllByTrainerIdAndDeletedAtIsNull(trainer.id)).thenReturn(emptyList())
        whenever(trainerSpecializationRepository.findAllByTrainerIdAndDeletedAtIsNull(trainer.id)).thenReturn(emptyList())

        val response =
            service.update(
                org.publicId,
                club.publicId,
                trainer.publicId,
                UpdateTrainerRequest(firstNameEn = "Updated", bioEn = "New bio"),
            )

        assertThat(response.firstNameEn).isEqualTo("Updated")
        assertThat(response.bioEn).isEqualTo("New bio")
        assertThat(response.lastNameEn).isEqualTo("Al-Shammari")
    }

    companion object {
        private fun setEntityId(
            entity: Any,
            id: Long,
        ) {
            var cls: Class<*>? = entity.javaClass
            while (cls != null) {
                try {
                    val field = cls.getDeclaredField("id")
                    field.isAccessible = true
                    field.set(entity, id)
                    return
                } catch (_: NoSuchFieldException) {
                    cls = cls.superclass
                }
            }
        }
    }
}
