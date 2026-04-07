package com.liyaqa.lead

import com.liyaqa.audit.AuditService
import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.lead.dto.ConvertLeadRequest
import com.liyaqa.lead.dto.CreateLeadNoteRequest
import com.liyaqa.lead.dto.CreateLeadRequest
import com.liyaqa.lead.dto.StageTransitionRequest
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.RoleRepository
import com.liyaqa.staff.StaffMember
import com.liyaqa.staff.StaffMemberRepository
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
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDate
import java.util.Optional

private const val TEST_PASSWORD = "Test@12345678"

@ExtendWith(MockitoExtension::class)
class LeadServiceTest {
    @Mock lateinit var leadRepository: LeadRepository

    @Mock lateinit var leadNoteRepository: LeadNoteRepository

    @Mock lateinit var leadSourceRepository: LeadSourceRepository

    @Mock lateinit var memberRepository: MemberRepository

    @Mock lateinit var staffMemberRepository: StaffMemberRepository

    @Mock lateinit var branchRepository: BranchRepository

    @Mock lateinit var organizationRepository: OrganizationRepository

    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var userRepository: UserRepository

    @Mock lateinit var userRoleRepository: UserRoleRepository

    @Mock lateinit var roleRepository: RoleRepository

    @Mock lateinit var passwordEncoder: PasswordEncoder

    @Mock lateinit var auditService: AuditService

    @Mock lateinit var eventPublisher: org.springframework.context.ApplicationEventPublisher

    @InjectMocks lateinit var service: LeadService

    private val org = Organization(nameAr = "منظمة", nameEn = "Org", email = "o@t.com")
    private val club = Club(organizationId = org.id, nameAr = "نادي", nameEn = "Club")
    private val branch = Branch(organizationId = org.id, clubId = club.id, nameAr = "فرع", nameEn = "Branch")
    private val staff =
        StaffMember(
            organizationId = org.id,
            clubId = club.id,
            userId = 10L,
            roleId = 1L,
            firstNameAr = "محمد",
            firstNameEn = "Mohammed",
            lastNameAr = "القحطاني",
            lastNameEn = "Al-Qahtani",
            joinedAt = LocalDate.now(),
        )
    private val staffUser =
        User(
            email = "staff@test.com",
            passwordHash = "encoded",
            organizationId = org.id,
            clubId = club.id,
        )

    private val source =
        LeadSource(
            organizationId = org.id,
            clubId = club.id,
            name = "Walk-in",
            nameAr = "حضور مباشر",
            color = "#10B981",
        )

    private fun stubOrgAndClub() {
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId))
            .thenReturn(Optional.of(org))
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(club.publicId, org.id))
            .thenReturn(Optional.of(club))
    }

    private fun createRequest(
        phone: String? = "+966501234567",
        email: String? = null,
    ) = CreateLeadRequest(
        firstName = "Sara",
        lastName = "Al-Ghamdi",
        phone = phone,
        email = email,
    )

    private fun makeLead(
        stage: String = "new",
        convertedMemberId: Long? = null,
    ) = Lead(
        organizationId = org.id,
        clubId = club.id,
        firstName = "Sara",
        lastName = "Al-Ghamdi",
        phone = "+966501234567",
        stage = stage,
        convertedMemberId = convertedMemberId,
    )

    // ── Rule 1: phone or email required ─────────────────────────────────────

    @Test
    fun `createLead happy path with phone`() {
        stubOrgAndClub()
        whenever(leadRepository.save(any<Lead>())).thenAnswer { it.arguments[0] as Lead }

        val result = service.createLead(org.publicId, club.publicId, staffUser.publicId, createRequest())

        assertThat(result.firstName).isEqualTo("Sara")
        assertThat(result.stage).isEqualTo("new")
    }

    @Test
    fun `createLead rejects when both phone and email are null`() {
        stubOrgAndClub()

        assertThatThrownBy {
            service.createLead(org.publicId, club.publicId, staffUser.publicId, createRequest(phone = null, email = null))
        }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ArenaException).status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            })
    }

    // ── Rule 3: active source only ──────────────────────────────────────────

    @Test
    fun `createLead rejects inactive source`() {
        stubOrgAndClub()
        val inactiveSource =
            LeadSource(
                organizationId = org.id,
                clubId = club.id,
                name = "Old",
                nameAr = "قديم",
                isActive = false,
            )
        whenever(leadSourceRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(inactiveSource.publicId, club.id))
            .thenReturn(Optional.of(inactiveSource))

        assertThatThrownBy {
            service.createLead(
                org.publicId,
                club.publicId,
                staffUser.publicId,
                createRequest().copy(leadSourceId = inactiveSource.publicId),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ArenaException).status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            })
    }

    // ── Rule 4: stage transitions ───────────────────────────────────────────

    @Test
    fun `moveStage allows new to contacted`() {
        stubOrgAndClub()
        val lead = makeLead(stage = "new")
        whenever(leadRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(lead.publicId, club.id))
            .thenReturn(Optional.of(lead))
        whenever(leadRepository.save(any<Lead>())).thenAnswer { it.arguments[0] as Lead }

        val result =
            service.moveStage(
                org.publicId,
                club.publicId,
                lead.publicId,
                StageTransitionRequest(stage = "contacted"),
            )

        assertThat(result.stage).isEqualTo("contacted")
        assertThat(result.contactedAt).isNotNull()
    }

    @Test
    fun `moveStage allows contacted to interested`() {
        stubOrgAndClub()
        val lead = makeLead(stage = "contacted")
        whenever(leadRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(lead.publicId, club.id))
            .thenReturn(Optional.of(lead))
        whenever(leadRepository.save(any<Lead>())).thenAnswer { it.arguments[0] as Lead }

        val result =
            service.moveStage(
                org.publicId,
                club.publicId,
                lead.publicId,
                StageTransitionRequest(stage = "interested"),
            )

        assertThat(result.stage).isEqualTo("interested")
    }

    @Test
    fun `moveStage rejects backward from interested to new`() {
        stubOrgAndClub()
        val lead = makeLead(stage = "interested")
        whenever(leadRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(lead.publicId, club.id))
            .thenReturn(Optional.of(lead))

        assertThatThrownBy {
            service.moveStage(
                org.publicId,
                club.publicId,
                lead.publicId,
                StageTransitionRequest(stage = "new"),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ArenaException).status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            })
    }

    @Test
    fun `moveStage allows lost to new re-open`() {
        stubOrgAndClub()
        val lead = makeLead(stage = "lost")
        whenever(leadRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(lead.publicId, club.id))
            .thenReturn(Optional.of(lead))
        whenever(leadRepository.save(any<Lead>())).thenAnswer { it.arguments[0] as Lead }

        val result =
            service.moveStage(
                org.publicId,
                club.publicId,
                lead.publicId,
                StageTransitionRequest(stage = "new"),
            )

        assertThat(result.stage).isEqualTo("new")
        assertThat(result.lostReason).isNull()
    }

    // ── Rule 5: lost requires reason ────────────────────────────────────────

    @Test
    fun `moveStage to lost requires lostReason`() {
        stubOrgAndClub()
        val lead = makeLead(stage = "contacted")
        whenever(leadRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(lead.publicId, club.id))
            .thenReturn(Optional.of(lead))

        assertThatThrownBy {
            service.moveStage(
                org.publicId,
                club.publicId,
                lead.publicId,
                StageTransitionRequest(stage = "lost"),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ArenaException).status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
                assertThat((ex as ArenaException).message).contains("reason")
            })
    }

    @Test
    fun `moveStage to lost with reason succeeds`() {
        stubOrgAndClub()
        val lead = makeLead(stage = "interested")
        whenever(leadRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(lead.publicId, club.id))
            .thenReturn(Optional.of(lead))
        whenever(leadRepository.save(any<Lead>())).thenAnswer { it.arguments[0] as Lead }

        val result =
            service.moveStage(
                org.publicId,
                club.publicId,
                lead.publicId,
                StageTransitionRequest(stage = "lost", lostReason = "Not interested anymore"),
            )

        assertThat(result.stage).isEqualTo("lost")
        assertThat(result.lostReason).isEqualTo("Not interested anymore")
        assertThat(result.lostAt).isNotNull()
    }

    // ── Rule 6: converted lead is immutable ─────────────────────────────────

    @Test
    fun `moveStage rejects on converted lead`() {
        stubOrgAndClub()
        val lead = makeLead(stage = "converted", convertedMemberId = 99L)
        whenever(leadRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(lead.publicId, club.id))
            .thenReturn(Optional.of(lead))

        assertThatThrownBy {
            service.moveStage(
                org.publicId,
                club.publicId,
                lead.publicId,
                StageTransitionRequest(stage = "contacted"),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ArenaException).status).isEqualTo(HttpStatus.CONFLICT)
            })
    }

    // ── Rule 7: convert idempotency ─────────────────────────────────────────

    @Test
    fun `convertLead rejects if already converted`() {
        stubOrgAndClub()
        val lead = makeLead(stage = "interested", convertedMemberId = 99L)
        whenever(leadRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(lead.publicId, club.id))
            .thenReturn(Optional.of(lead))

        assertThatThrownBy {
            service.convertLead(
                org.publicId,
                club.publicId,
                lead.publicId,
                ConvertLeadRequest(branchId = branch.publicId),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ArenaException).status).isEqualTo(HttpStatus.CONFLICT)
                assertThat((ex as ArenaException).message).contains("already been converted")
            })
    }

    // ── Rule 8: convert creates Member atomically ───────────────────────────

    @Test
    fun `convertLead creates member and updates lead`() {
        stubOrgAndClub()
        val lead = makeLead(stage = "interested")
        lead.email = "sara@test.com"
        whenever(leadRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(lead.publicId, club.id))
            .thenReturn(Optional.of(lead))
        whenever(branchRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(branch.publicId, org.id, club.id))
            .thenReturn(Optional.of(branch))
        whenever(userRepository.findByEmailAndDeletedAtIsNull("sara@test.com"))
            .thenReturn(Optional.empty())
        whenever(passwordEncoder.encode(any())).thenReturn("encoded")
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] as User }
        whenever(roleRepository.findByScopeAndOrganizationIdAndClubIdAndDeletedAtIsNull("member", org.id, club.id))
            .thenReturn(Optional.empty())
        whenever(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] as Member }
        whenever(leadRepository.save(any<Lead>())).thenAnswer { it.arguments[0] as Lead }

        val result =
            service.convertLead(
                org.publicId,
                club.publicId,
                lead.publicId,
                ConvertLeadRequest(branchId = branch.publicId),
            )

        assertThat(result.stage).isEqualTo("converted")
        assertThat(result.convertedAt).isNotNull()
    }

    // ── Rule 9: staff assignment scope ───────────────────────────────────────

    @Test
    fun `createLead rejects staff from different club`() {
        stubOrgAndClub()
        val otherClubStaff =
            StaffMember(
                organizationId = org.id,
                clubId = 999L,
                userId = 20L,
                roleId = 1L,
                firstNameAr = "خالد",
                firstNameEn = "Khalid",
                lastNameAr = "المحمدي",
                lastNameEn = "Al-Mohammadi",
                joinedAt = LocalDate.now(),
            )
        whenever(staffMemberRepository.findByPublicIdAndDeletedAtIsNull(otherClubStaff.publicId))
            .thenReturn(Optional.of(otherClubStaff))

        assertThatThrownBy {
            service.createLead(
                org.publicId,
                club.publicId,
                staffUser.publicId,
                createRequest().copy(assignedStaffId = otherClubStaff.publicId),
            )
        }
            .isInstanceOf(ArenaException::class.java)
            .satisfies({ ex ->
                assertThat((ex as ArenaException).status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
            })
    }

    // ── Rule 10: notes are immutable ────────────────────────────────────────

    @Test
    fun `addNote persists and returns note`() {
        stubOrgAndClub()
        val lead = makeLead(stage = "contacted")
        whenever(leadRepository.findByPublicIdAndClubIdAndDeletedAtIsNull(lead.publicId, club.id))
            .thenReturn(Optional.of(lead))
        whenever(userRepository.findByPublicIdAndDeletedAtIsNull(staffUser.publicId))
            .thenReturn(Optional.of(staffUser))
        whenever(staffMemberRepository.findByUserIdAndClubIdAndDeletedAtIsNull(staffUser.id, club.id))
            .thenReturn(Optional.of(staff))
        whenever(leadNoteRepository.save(any<LeadNote>())).thenAnswer { it.arguments[0] as LeadNote }

        val result =
            service.addNote(
                org.publicId,
                club.publicId,
                lead.publicId,
                staffUser.publicId,
                CreateLeadNoteRequest(body = "Called and discussed membership options"),
            )

        assertThat(result.body).isEqualTo("Called and discussed membership options")
        assertThat(result.staff.firstName).isEqualTo("Mohammed")
    }
}
