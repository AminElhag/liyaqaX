package com.liyaqa.member

import com.liyaqa.audit.AuditService
import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.dto.CreateMemberRequest
import com.liyaqa.member.dto.EmergencyContactRequest
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.Role
import com.liyaqa.role.RoleRepository
import com.liyaqa.staff.StaffMemberRepository
import com.liyaqa.trainer.TrainerRepository
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
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class MemberServiceTest {
    @Mock lateinit var memberRepository: MemberRepository

    @Mock lateinit var emergencyContactRepository: EmergencyContactRepository

    @Mock lateinit var healthWaiverRepository: HealthWaiverRepository

    @Mock lateinit var waiverSignatureRepository: WaiverSignatureRepository

    @Mock lateinit var userRepository: UserRepository

    @Mock lateinit var userRoleRepository: UserRoleRepository

    @Mock lateinit var roleRepository: RoleRepository

    @Mock lateinit var branchRepository: BranchRepository

    @Mock lateinit var organizationRepository: OrganizationRepository

    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var staffMemberRepository: StaffMemberRepository

    @Mock lateinit var trainerRepository: TrainerRepository

    @Mock lateinit var passwordEncoder: PasswordEncoder

    @Mock lateinit var auditService: AuditService

    @Mock lateinit var eventPublisher: org.springframework.context.ApplicationEventPublisher

    @Mock lateinit var memberRegistrationIntentRepository: MemberRegistrationIntentRepository

    @Mock lateinit var membershipPlanRepository: com.liyaqa.membership.MembershipPlanRepository

    @Mock lateinit var membershipRepository: com.liyaqa.membership.MembershipRepository

    @InjectMocks lateinit var service: MemberService

    private val org = Organization(nameAr = "منظمة", nameEn = "Org", email = "o@t.com")
    private val club = Club(organizationId = org.id, nameAr = "نادي", nameEn = "Club")
    private val branch = Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Branch")
    private val memberRole =
        Role(nameAr = "عضو", nameEn = "Member", scope = "member", organizationId = org.id, clubId = club.id)

    private fun createRequest() =
        CreateMemberRequest(
            email = "member@test.com",
            password = "Test@12345678",
            firstNameAr = "أحمد",
            firstNameEn = "Ahmed",
            lastNameAr = "الرشيدي",
            lastNameEn = "Al-Rashidi",
            phone = "+966501234567",
            branchId = branch.publicId,
            emergencyContact =
                EmergencyContactRequest(
                    nameAr = "محمد",
                    nameEn = "Mohammed",
                    phone = "+966507654321",
                ),
        )

    private fun stubOrgAndClub() {
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId))
            .thenReturn(Optional.of(org))
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(club.publicId, org.id))
            .thenReturn(Optional.of(club))
    }

    private fun stubCreateHappyPath() {
        stubOrgAndClub()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("member@test.com")).thenReturn(Optional.empty())
        whenever(branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branch.publicId, org.id, club.id))
            .thenReturn(Optional.of(branch))
        whenever(passwordEncoder.encode(any())).thenReturn("encoded")
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] as User }
        whenever(staffMemberRepository.existsByUserIdAndDeletedAtIsNull(any())).thenReturn(false)
        whenever(trainerRepository.existsByUserIdAndDeletedAtIsNull(any())).thenReturn(false)
        whenever(roleRepository.findByScopeAndOrganizationIdAndClubIdAndDeletedAtIsNull("member", org.id, club.id))
            .thenReturn(Optional.of(memberRole))
        whenever(userRoleRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] as Member }
        whenever(emergencyContactRepository.save(any<EmergencyContact>())).thenAnswer { it.arguments[0] as EmergencyContact }
        whenever(healthWaiverRepository.findByClubIdAndIsActiveTrueAndDeletedAtIsNull(club.id)).thenReturn(Optional.empty())
    }

    // ── Rule 1: Email uniqueness ─────────────────────────────────────────────

    @Test
    fun `create with duplicate email throws conflict`() {
        stubOrgAndClub()
        val existingUser = User(email = "member@test.com", passwordHash = "hash")
        whenever(userRepository.findByEmailAndDeletedAtIsNull("member@test.com"))
            .thenReturn(Optional.of(existingUser))

        assertThatThrownBy { service.create(org.publicId, club.publicId, createRequest()) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    // ── Rule 2: Branch scope ─────────────────────────────────────────────────

    @Test
    fun `create with branch from different club throws 422`() {
        stubOrgAndClub()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("member@test.com")).thenReturn(Optional.empty())
        whenever(branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branch.publicId, org.id, club.id))
            .thenReturn(Optional.empty())

        assertThatThrownBy { service.create(org.publicId, club.publicId, createRequest()) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Rule 3: Emergency contact required ───────────────────────────────────

    @Test
    fun `delete last emergency contact throws 422`() {
        stubOrgAndClub()
        val member =
            Member(
                organizationId = org.id, clubId = club.id, branchId = branch.id, userId = 1L,
                firstNameAr = "أ", firstNameEn = "A", lastNameAr = "ب", lastNameEn = "B", phone = "123",
            )
        whenever(memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(member.publicId, org.id, club.id))
            .thenReturn(Optional.of(member))

        val contact = EmergencyContact(memberId = member.id, organizationId = org.id, nameAr = "م", nameEn = "M", phone = "456")
        whenever(emergencyContactRepository.findByPublicIdAndOrganizationId(contact.publicId, org.id))
            .thenReturn(Optional.of(contact))
        whenever(emergencyContactRepository.findAllByMemberIdAndOrganizationId(member.id, org.id))
            .thenReturn(listOf(contact))

        assertThatThrownBy {
            service.deleteEmergencyContact(org.publicId, club.publicId, member.publicId, contact.publicId)
        }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    // ── Rule 4: Single user type ─────────────────────────────────────────────

    @Test
    fun `create throws conflict when user is already a staff member`() {
        stubOrgAndClub()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("member@test.com")).thenReturn(Optional.empty())
        whenever(branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branch.publicId, org.id, club.id))
            .thenReturn(Optional.of(branch))
        whenever(passwordEncoder.encode(any())).thenReturn("encoded")
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] as User }
        whenever(staffMemberRepository.existsByUserIdAndDeletedAtIsNull(any())).thenReturn(true)

        assertThatThrownBy { service.create(org.publicId, club.publicId, createRequest()) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `create throws conflict when user is already a trainer`() {
        stubOrgAndClub()
        whenever(userRepository.findByEmailAndDeletedAtIsNull("member@test.com")).thenReturn(Optional.empty())
        whenever(branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branch.publicId, org.id, club.id))
            .thenReturn(Optional.of(branch))
        whenever(passwordEncoder.encode(any())).thenReturn("encoded")
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] as User }
        whenever(staffMemberRepository.existsByUserIdAndDeletedAtIsNull(any())).thenReturn(false)
        whenever(trainerRepository.existsByUserIdAndDeletedAtIsNull(any())).thenReturn(true)

        assertThatThrownBy { service.create(org.publicId, club.publicId, createRequest()) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    // ── Rule 5: Soft delete deactivates user ─────────────────────────────────

    @Test
    fun `delete soft deletes member and deactivates user`() {
        stubOrgAndClub()
        val user = User(email = "m@test.com", passwordHash = "hash", organizationId = org.id, clubId = club.id)
        val member =
            Member(
                organizationId = org.id, clubId = club.id, branchId = branch.id, userId = user.id,
                firstNameAr = "أ", firstNameEn = "A", lastNameAr = "ب", lastNameEn = "B", phone = "123",
            )
        whenever(memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(member.publicId, org.id, club.id))
            .thenReturn(Optional.of(member))
        whenever(userRepository.findById(user.id)).thenReturn(Optional.of(user))
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] as User }
        whenever(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] as Member }

        service.delete(org.publicId, club.publicId, member.publicId)

        assertThat(member.deletedAt).isNotNull()
        assertThat(user.isActive).isFalse()
        verify(userRepository).save(user)
        verify(memberRepository).save(member)
    }

    // ── Rule 6: Status always pending ────────────────────────────────────────

    @Test
    fun `create always sets status to pending`() {
        stubCreateHappyPath()

        val response = service.create(org.publicId, club.publicId, createRequest())

        assertThat(response.membershipStatus).isEqualTo("pending")
    }

    // ── Rule 7: Waiver signature ─────────────────────────────────────────────

    @Test
    fun `sign waiver succeeds when not already signed`() {
        stubOrgAndClub()
        val member =
            Member(
                organizationId = org.id, clubId = club.id, branchId = branch.id, userId = 1L,
                firstNameAr = "أ", firstNameEn = "A", lastNameAr = "ب", lastNameEn = "B", phone = "123",
            )
        whenever(memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(member.publicId, org.id, club.id))
            .thenReturn(Optional.of(member))
        val waiver = HealthWaiver(organizationId = org.id, clubId = club.id, contentAr = "ع", contentEn = "E")
        whenever(healthWaiverRepository.findByClubIdAndIsActiveTrueAndDeletedAtIsNull(club.id))
            .thenReturn(Optional.of(waiver))
        whenever(waiverSignatureRepository.existsByMemberIdAndWaiverId(member.id, waiver.id)).thenReturn(false)
        whenever(waiverSignatureRepository.save(any<WaiverSignature>())).thenAnswer { it.arguments[0] as WaiverSignature }

        val result = service.signWaiver(org.publicId, club.publicId, member.publicId, "127.0.0.1")

        assertThat(result.hasSignedCurrentWaiver).isTrue()
        assertThat(result.waiverVersion).isEqualTo(1)
        verify(waiverSignatureRepository).save(any<WaiverSignature>())
    }

    @Test
    fun `sign waiver throws conflict when already signed`() {
        stubOrgAndClub()
        val member =
            Member(
                organizationId = org.id, clubId = club.id, branchId = branch.id, userId = 1L,
                firstNameAr = "أ", firstNameEn = "A", lastNameAr = "ب", lastNameEn = "B", phone = "123",
            )
        whenever(memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(member.publicId, org.id, club.id))
            .thenReturn(Optional.of(member))
        val waiver = HealthWaiver(organizationId = org.id, clubId = club.id, contentAr = "ع", contentEn = "E")
        whenever(healthWaiverRepository.findByClubIdAndIsActiveTrueAndDeletedAtIsNull(club.id))
            .thenReturn(Optional.of(waiver))
        whenever(waiverSignatureRepository.existsByMemberIdAndWaiverId(member.id, waiver.id)).thenReturn(true)

        assertThatThrownBy { service.signWaiver(org.publicId, club.publicId, member.publicId, "127.0.0.1") }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    fun `create member succeeds with valid data`() {
        stubCreateHappyPath()

        val response = service.create(org.publicId, club.publicId, createRequest())

        assertThat(response.firstNameEn).isEqualTo("Ahmed")
        assertThat(response.lastNameEn).isEqualTo("Al-Rashidi")
        assertThat(response.email).isEqualTo("member@test.com")
        assertThat(response.phone).isEqualTo("+966501234567")
        assertThat(response.membershipStatus).isEqualTo("pending")
        assertThat(response.emergencyContacts).hasSize(1)
        assertThat(response.emergencyContacts[0].nameEn).isEqualTo("Mohammed")
        verify(userRepository).save(any<User>())
        verify(memberRepository).save(any<Member>())
        verify(emergencyContactRepository).save(any<EmergencyContact>())
    }
}
