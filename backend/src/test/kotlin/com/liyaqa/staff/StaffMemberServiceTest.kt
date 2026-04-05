package com.liyaqa.staff

import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.Role
import com.liyaqa.role.RoleRepository
import com.liyaqa.staff.dto.CreateStaffMemberRequest
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
class StaffMemberServiceTest {
    @Mock lateinit var staffMemberRepository: StaffMemberRepository

    @Mock lateinit var staffBranchAssignmentRepository: StaffBranchAssignmentRepository

    @Mock lateinit var userRepository: UserRepository

    @Mock lateinit var userRoleRepository: UserRoleRepository

    @Mock lateinit var roleRepository: RoleRepository

    @Mock lateinit var branchRepository: BranchRepository

    @Mock lateinit var organizationRepository: OrganizationRepository

    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var permissionService: PermissionService

    @Mock lateinit var passwordEncoder: PasswordEncoder

    @InjectMocks lateinit var service: StaffMemberService

    private val org = Organization(nameAr = "منظمة", nameEn = "Org", email = "o@t.com")
    private val club = Club(organizationId = org.id, nameAr = "نادي", nameEn = "Club")
    private val branch = Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Branch")

    private val clubRole =
        Role(nameAr = "دور", nameEn = "Staff Role", scope = "club", organizationId = org.id, clubId = club.id)

    private val callerUser = User(email = "caller@test.com", passwordHash = "hash", organizationId = org.id, clubId = club.id)
    private val callerRoleId = UUID.randomUUID()

    private fun createRequest(
        roleId: UUID = clubRole.publicId,
        branchIds: List<UUID> = listOf(branch.publicId),
    ) = CreateStaffMemberRequest(
        email = "new@test.com",
        password = "Pass1234!",
        firstNameAr = "أحمد",
        firstNameEn = "Ahmed",
        lastNameAr = "العمري",
        lastNameEn = "Al-Omari",
        roleId = roleId,
        branchIds = branchIds,
        joinedAt = LocalDate.of(2025, 1, 1),
    )

    private fun stubOrgAndClub() {
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId)).thenReturn(Optional.of(org))
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(club.publicId, org.id)).thenReturn(Optional.of(club))
    }

    private fun stubRoleLookup(role: Role = clubRole) {
        whenever(roleRepository.findByPublicIdAndDeletedAtIsNull(role.publicId)).thenReturn(Optional.of(role))
    }

    private fun stubBranchLookup() {
        whenever(
            branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branch.publicId, org.id, club.id),
        ).thenReturn(Optional.of(branch))
    }

    private fun stubCreateHappyPath() {
        stubOrgAndClub()
        stubRoleLookup()
        stubBranchLookup()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("new@test.com")).thenReturn(Optional.empty())
        whenever(permissionService.getPermissions(callerRoleId)).thenReturn(setOf("staff:create", "staff:read"))
        whenever(permissionService.getPermissions(clubRole.publicId)).thenReturn(setOf("staff:read"))
        whenever(passwordEncoder.encode(any())).thenReturn("encoded")
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] as User }
        whenever(userRoleRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(staffMemberRepository.save(any<StaffMember>())).thenAnswer { it.arguments[0] as StaffMember }
        whenever(staffBranchAssignmentRepository.saveAll(any<Iterable<StaffBranchAssignment>>()))
            .thenAnswer { it.arguments[0] as Iterable<*> }
    }

    // ── Rule 1: Email uniqueness ──────────────────────────────────────────────

    @Test
    fun `create with duplicate email throws conflict`() {
        stubOrgAndClub()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("new@test.com"))
            .thenReturn(Optional.of(callerUser))

        assertThatThrownBy {
            service.create(org.publicId, club.publicId, callerUser.publicId, callerRoleId, "club", createRequest())
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    // ── Rule 2: Role scope validation ─────────────────────────────────────────

    @Test
    fun `create with platform role throws 422`() {
        stubOrgAndClub()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("new@test.com")).thenReturn(Optional.empty())
        val platformRole = Role(nameAr = "م", nameEn = "Platform", scope = "platform")
        stubRoleLookup(platformRole)

        assertThatThrownBy {
            service.create(
                org.publicId,
                club.publicId,
                callerUser.publicId,
                callerRoleId,
                "club",
                createRequest(roleId = platformRole.publicId),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `create with role from different club throws 422`() {
        stubOrgAndClub()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("new@test.com")).thenReturn(Optional.empty())
        val otherClubRole = Role(nameAr = "آخر", nameEn = "Other", scope = "club", organizationId = org.id, clubId = 999L)
        stubRoleLookup(otherClubRole)

        assertThatThrownBy {
            service.create(
                org.publicId,
                club.publicId,
                callerUser.publicId,
                callerRoleId,
                "club",
                createRequest(roleId = otherClubRole.publicId),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Rule 3: Permission elevation prevention ───────────────────────────────

    @Test
    fun `create with more powerful role throws 403 for club caller`() {
        stubOrgAndClub()
        stubRoleLookup()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("new@test.com")).thenReturn(Optional.empty())
        whenever(permissionService.getPermissions(callerRoleId)).thenReturn(setOf("staff:read"))
        whenever(permissionService.getPermissions(clubRole.publicId)).thenReturn(setOf("staff:read", "staff:delete"))

        assertThatThrownBy {
            service.create(org.publicId, club.publicId, callerUser.publicId, callerRoleId, "club", createRequest())
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `create with more powerful role succeeds for platform caller`() {
        stubOrgAndClub()
        stubRoleLookup()
        stubBranchLookup()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("new@test.com")).thenReturn(Optional.empty())
        whenever(passwordEncoder.encode(any())).thenReturn("encoded")
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] as User }
        whenever(userRoleRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(staffMemberRepository.save(any<StaffMember>())).thenAnswer { it.arguments[0] as StaffMember }
        whenever(staffBranchAssignmentRepository.saveAll(any<Iterable<StaffBranchAssignment>>()))
            .thenAnswer { it.arguments[0] as Iterable<*> }

        val response =
            service.create(org.publicId, club.publicId, callerUser.publicId, callerRoleId, "platform", createRequest())

        assertThat(response.firstNameEn).isEqualTo("Ahmed")
    }

    // ── Rule 4: Branch scope validation ───────────────────────────────────────

    @Test
    fun `create with branch from different club throws 422`() {
        stubOrgAndClub()
        stubRoleLookup()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("new@test.com")).thenReturn(Optional.empty())
        whenever(permissionService.getPermissions(callerRoleId)).thenReturn(setOf("staff:create", "staff:read"))
        whenever(permissionService.getPermissions(clubRole.publicId)).thenReturn(setOf("staff:read"))
        val invalidBranchId = UUID.randomUUID()
        whenever(branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(invalidBranchId, org.id, club.id))
            .thenReturn(Optional.empty())

        assertThatThrownBy {
            service.create(
                org.publicId,
                club.publicId,
                callerUser.publicId,
                callerRoleId,
                "club",
                createRequest(branchIds = listOf(invalidBranchId)),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Rule 5: Minimum one branch ────────────────────────────────────────────

    @Test
    fun `remove last branch throws 422`() {
        stubOrgAndClub()
        val staff =
            StaffMember(
                organizationId = org.id, clubId = club.id, userId = 1L, roleId = clubRole.id,
                firstNameAr = "أحمد", firstNameEn = "Ahmed", lastNameAr = "ع", lastNameEn = "A", joinedAt = LocalDate.now(),
            )
        whenever(staffMemberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(staff.publicId, org.id, club.id))
            .thenReturn(Optional.of(staff))
        whenever(branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branch.publicId, org.id, club.id))
            .thenReturn(Optional.of(branch))
        whenever(staffBranchAssignmentRepository.existsByStaffMemberIdAndBranchId(staff.id, branch.id)).thenReturn(true)
        whenever(staffBranchAssignmentRepository.countByStaffMemberId(staff.id)).thenReturn(1L)

        assertThatThrownBy {
            service.removeBranch(org.publicId, club.publicId, staff.publicId, branch.publicId)
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Rule 6: Soft delete cascade ───────────────────────────────────────────

    @Test
    fun `delete soft deletes staff and deactivates user`() {
        stubOrgAndClub()
        val targetUser = User(email = "target@test.com", passwordHash = "hash", organizationId = org.id, clubId = club.id)
        val staff =
            StaffMember(
                organizationId = org.id, clubId = club.id, userId = targetUser.id, roleId = clubRole.id,
                firstNameAr = "أحمد", firstNameEn = "Ahmed", lastNameAr = "ع", lastNameEn = "A", joinedAt = LocalDate.now(),
            )
        whenever(staffMemberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(staff.publicId, org.id, club.id))
            .thenReturn(Optional.of(staff))
        // caller is a different user (userId = 1L != targetUser.id = 0L)
        val differentCaller = User(email = "diff@test.com", passwordHash = "hash")
        setEntityId(differentCaller, 999L)
        whenever(userRepository.findByPublicIdAndDeletedAtIsNull(differentCaller.publicId)).thenReturn(Optional.of(differentCaller))
        whenever(userRepository.findById(targetUser.id)).thenReturn(Optional.of(targetUser))
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] as User }
        whenever(staffMemberRepository.save(any<StaffMember>())).thenAnswer { it.arguments[0] as StaffMember }

        service.delete(org.publicId, club.publicId, staff.publicId, differentCaller.publicId)

        assertThat(staff.deletedAt).isNotNull()
        assertThat(targetUser.isActive).isFalse()
        verify(userRepository).save(targetUser)
        verify(staffMemberRepository).save(staff)
    }

    // ── Rule 7: Self-deletion prevention ──────────────────────────────────────

    @Test
    fun `delete own account throws 422`() {
        stubOrgAndClub()
        val staff =
            StaffMember(
                organizationId = org.id, clubId = club.id, userId = callerUser.id, roleId = clubRole.id,
                firstNameAr = "أحمد", firstNameEn = "Ahmed", lastNameAr = "ع", lastNameEn = "A", joinedAt = LocalDate.now(),
            )
        whenever(staffMemberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(staff.publicId, org.id, club.id))
            .thenReturn(Optional.of(staff))
        whenever(userRepository.findByPublicIdAndDeletedAtIsNull(callerUser.publicId)).thenReturn(Optional.of(callerUser))

        assertThatThrownBy {
            service.delete(org.publicId, club.publicId, staff.publicId, callerUser.publicId)
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `create staff member succeeds with valid data`() {
        stubCreateHappyPath()

        val response =
            service.create(org.publicId, club.publicId, callerUser.publicId, callerRoleId, "club", createRequest())

        assertThat(response.firstNameEn).isEqualTo("Ahmed")
        assertThat(response.lastNameEn).isEqualTo("Al-Omari")
        assertThat(response.email).isEqualTo("new@test.com")
        assertThat(response.role.nameEn).isEqualTo("Staff Role")
        assertThat(response.branches).hasSize(1)
        verify(userRepository).save(any<User>())
        verify(staffMemberRepository).save(any<StaffMember>())
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
