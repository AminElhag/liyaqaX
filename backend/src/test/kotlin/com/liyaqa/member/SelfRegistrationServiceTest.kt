package com.liyaqa.member

import com.liyaqa.arena.dto.SelfRegistrationRequest
import com.liyaqa.audit.AuditService
import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.membership.MembershipPlan
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.portal.ClubPortalSettings
import com.liyaqa.portal.ClubPortalSettingsService
import com.liyaqa.rbac.UserRole
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.Role
import com.liyaqa.role.RoleRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SelfRegistrationServiceTest {
    @Mock lateinit var memberRepository: MemberRepository

    @Mock lateinit var memberRegistrationIntentRepository: MemberRegistrationIntentRepository

    @Mock lateinit var membershipPlanRepository: MembershipPlanRepository

    @Mock lateinit var userRepository: UserRepository

    @Mock lateinit var userRoleRepository: UserRoleRepository

    @Mock lateinit var roleRepository: RoleRepository

    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var organizationRepository: OrganizationRepository

    @Mock lateinit var branchRepository: BranchRepository

    @Mock lateinit var emergencyContactRepository: EmergencyContactRepository

    @Mock lateinit var portalSettingsService: ClubPortalSettingsService

    @Mock lateinit var passwordEncoder: PasswordEncoder

    @Mock lateinit var auditService: AuditService

    @Mock lateinit var eventPublisher: ApplicationEventPublisher

    private lateinit var service: SelfRegistrationService

    private val org = Organization(nameAr = "منظمة", nameEn = "Test Org", email = "org@test.com")
    private val club = Club(organizationId = 1L, nameAr = "نادي", nameEn = "Test Club")
    private val branch =
        Branch(
            organizationId = 1L,
            clubId = 1L,
            nameAr = "فرع",
            nameEn = "Test Branch",
        )
    private val memberRole =
        Role(
            nameAr = "عضو",
            nameEn = "Member",
            scope = "member",
            organizationId = 1L,
            clubId = 1L,
        )

    @BeforeEach
    fun setup() {
        service =
            SelfRegistrationService(
                memberRepository,
                memberRegistrationIntentRepository,
                membershipPlanRepository,
                userRepository,
                userRoleRepository,
                roleRepository,
                clubRepository,
                organizationRepository,
                branchRepository,
                emergencyContactRepository,
                portalSettingsService,
                passwordEncoder,
                auditService,
                eventPublisher,
            )
    }

    private fun claims(
        phone: String = "+966501234567",
        clubId: Long = 1L,
    ): Claims {
        val map =
            mutableMapOf<String, Any>(
                "phone" to phone,
                "clubId" to clubId,
                "scope" to "registration",
            )
        return Jwts.claims().add(map).build()
    }

    private fun enabledSettings() = ClubPortalSettings(clubId = 1L, selfRegistrationEnabled = true)

    private fun disabledSettings() = ClubPortalSettings(clubId = 1L, selfRegistrationEnabled = false)

    private fun stubCommon() {
        whenever(clubRepository.findById(1L)).thenReturn(Optional.of(club))
        whenever(organizationRepository.findById(club.organizationId)).thenReturn(Optional.of(org))
        whenever(portalSettingsService.getOrCreateSettings(club.id)).thenReturn(enabledSettings())
        whenever(memberRepository.findByPhoneAndClubIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.empty())
        whenever(userRepository.findByEmailAndDeletedAtIsNull(any())).thenReturn(Optional.empty())
        whenever(branchRepository.findFirstByClubIdAndDeletedAtIsNullOrderByIdAsc(any())).thenReturn(Optional.of(branch))
        whenever(passwordEncoder.encode(any())).thenReturn("hashed")
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] as User }
        whenever(userRoleRepository.save(any<UserRole>())).thenAnswer { it.arguments[0] as UserRole }
        whenever(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] as Member }
        whenever(roleRepository.findByScopeAndOrganizationIdAndClubIdAndDeletedAtIsNull(any(), any(), any()))
            .thenReturn(Optional.of(memberRole))
    }

    @Test
    fun `selfRegistrationDisabled throws forbidden`() {
        whenever(clubRepository.findById(1L)).thenReturn(Optional.of(club))
        whenever(organizationRepository.findById(club.organizationId)).thenReturn(Optional.of(org))
        whenever(portalSettingsService.getOrCreateSettings(club.id)).thenReturn(disabledSettings())

        assertThatThrownBy {
            service.register(claims(), SelfRegistrationRequest(nameAr = "أحمد"))
        }.isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `phoneAlreadyUsed sameClub throws conflict`() {
        whenever(clubRepository.findById(1L)).thenReturn(Optional.of(club))
        whenever(organizationRepository.findById(club.organizationId)).thenReturn(Optional.of(org))
        whenever(portalSettingsService.getOrCreateSettings(club.id)).thenReturn(enabledSettings())
        val existing =
            Member(
                organizationId = 1L, clubId = 1L, branchId = 1L, userId = 1L,
                firstNameAr = "أ", firstNameEn = "A", lastNameAr = "ب", lastNameEn = "B",
                phone = "+966501234567", membershipStatus = "active",
            )
        whenever(memberRepository.findByPhoneAndClubIdAndDeletedAtIsNull("+966501234567", club.id))
            .thenReturn(Optional.of(existing))

        assertThatThrownBy {
            service.register(claims(), SelfRegistrationRequest(nameAr = "أحمد"))
        }.isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `bothNamesBlank throws unprocessable`() {
        whenever(clubRepository.findById(1L)).thenReturn(Optional.of(club))
        whenever(organizationRepository.findById(club.organizationId)).thenReturn(Optional.of(org))
        whenever(portalSettingsService.getOrCreateSettings(club.id)).thenReturn(enabledSettings())
        whenever(memberRepository.findByPhoneAndClubIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.empty())

        assertThatThrownBy {
            service.register(claims(), SelfRegistrationRequest(nameEn = "", nameAr = ""))
        }.isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `planNotInClub throws unprocessable`() {
        whenever(clubRepository.findById(1L)).thenReturn(Optional.of(club))
        whenever(organizationRepository.findById(club.organizationId)).thenReturn(Optional.of(org))
        whenever(portalSettingsService.getOrCreateSettings(club.id)).thenReturn(enabledSettings())
        whenever(memberRepository.findByPhoneAndClubIdAndDeletedAtIsNull(any(), any())).thenReturn(Optional.empty())

        val fakePlanId = UUID.randomUUID()
        whenever(membershipPlanRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(fakePlanId, club.id))
            .thenReturn(Optional.empty())

        assertThatThrownBy {
            service.register(claims(), SelfRegistrationRequest(nameAr = "أحمد", desiredMembershipPlanId = fakePlanId))
        }.isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Test
    fun `successWithPlan creates intent and publishes event`() {
        stubCommon()
        val plan =
            MembershipPlan(
                organizationId = 1L,
                clubId = 1L,
                nameAr = "شهري",
                nameEn = "Monthly",
                priceHalalas = 15000,
                durationDays = 30,
            )
        whenever(membershipPlanRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(plan.publicId, club.id))
            .thenReturn(Optional.of(plan))
        whenever(memberRegistrationIntentRepository.save(any<MemberRegistrationIntent>()))
            .thenAnswer { it.arguments[0] as MemberRegistrationIntent }

        val result =
            service.register(
                claims(),
                SelfRegistrationRequest(nameAr = "أحمد الراشدي", desiredMembershipPlanId = plan.publicId),
            )

        assertThat(result.status).isEqualTo("pending_activation")
        assertThat(result.memberId).isNotNull()
        verify(memberRegistrationIntentRepository).save(any<MemberRegistrationIntent>())
        verify(eventPublisher).publishEvent(any<com.liyaqa.notification.events.MemberCreatedEvent>())
    }

    @Test
    fun `successWithoutPlan no intent created`() {
        stubCommon()

        val result =
            service.register(
                claims(),
                SelfRegistrationRequest(nameAr = "أحمد"),
            )

        assertThat(result.status).isEqualTo("pending_activation")
        verify(memberRepository).save(any<Member>())
        verify(eventPublisher).publishEvent(any<com.liyaqa.notification.events.MemberCreatedEvent>())
    }

    @Test
    fun `phoneUsed differentClub allowed`() {
        stubCommon()
        // No existing member found in this club (mocked as Optional.empty in stubCommon)
        val result =
            service.register(
                claims(),
                SelfRegistrationRequest(nameEn = "Ahmed"),
            )

        assertThat(result.status).isEqualTo("pending_activation")
    }
}
