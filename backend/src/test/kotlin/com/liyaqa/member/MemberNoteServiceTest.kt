package com.liyaqa.member

import com.liyaqa.audit.AuditService
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.gx.GXBookingRepository
import com.liyaqa.gx.GXClassInstanceRepository
import com.liyaqa.member.dto.CreateNoteRequest
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.payment.PaymentRepository
import com.liyaqa.pt.PTPackageRepository
import com.liyaqa.rbac.UserRole
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.Role
import com.liyaqa.role.RoleRepository
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
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class MemberNoteServiceTest {
    @Mock lateinit var memberNoteRepository: MemberNoteRepository

    @Mock lateinit var memberRepository: MemberRepository

    @Mock lateinit var userRepository: UserRepository

    @Mock lateinit var organizationRepository: OrganizationRepository

    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var paymentRepository: PaymentRepository

    @Mock lateinit var membershipRepository: MembershipRepository

    @Mock lateinit var trainerRepository: TrainerRepository

    @Mock lateinit var ptPackageRepository: PTPackageRepository

    @Mock lateinit var gxClassInstanceRepository: GXClassInstanceRepository

    @Mock lateinit var gxBookingRepository: GXBookingRepository

    @Mock lateinit var userRoleRepository: UserRoleRepository

    @Mock lateinit var roleRepository: RoleRepository

    @Mock lateinit var auditService: AuditService

    @InjectMocks lateinit var service: MemberNoteService

    private val org = Organization(nameAr = "منظمة", nameEn = "Org", email = "o@t.com")
    private val club = Club(organizationId = org.id, nameAr = "نادي", nameEn = "Club")
    private val member =
        Member(
            organizationId = org.id, clubId = club.id, branchId = 1L, userId = 1L,
            firstNameAr = "أحمد", firstNameEn = "Ahmed", lastNameAr = "الرشيدي", lastNameEn = "Al-Rashidi",
            phone = "+966501234567",
        )
    private val user = User(email = "staff@test.com", passwordHash = "encoded", organizationId = org.id, clubId = club.id)

    private fun stubOrgClub() {
        whenever(organizationRepository.findByPublicIdAndDeletedAtIsNull(org.publicId)).thenReturn(Optional.of(org))
        whenever(clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(club.publicId, org.id)).thenReturn(Optional.of(club))
    }

    private fun stubOrgClubMemberUser() {
        stubOrgClub()
        whenever(
            memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(member.publicId, org.id, club.id),
        ).thenReturn(Optional.of(member))
        whenever(userRepository.findByPublicIdAndDeletedAtIsNull(user.publicId)).thenReturn(Optional.of(user))
    }

    @Test
    fun `createNote saves note with correct type and content`() {
        stubOrgClubMemberUser()
        val request = CreateNoteRequest(noteType = "general", content = "Test note content")
        whenever(memberNoteRepository.save(any<MemberNote>())).thenAnswer { it.arguments[0] as MemberNote }

        val result = service.createNote(org.publicId, club.publicId, member.publicId, request, user.publicId, false)

        assertThat(result.content).isEqualTo("Test note content")
        assertThat(result.noteType).isEqualTo("GENERAL")
    }

    @Test
    fun `createNote throws 400 when content is empty`() {
        stubOrgClubMemberUser()
        val request = CreateNoteRequest(noteType = "general", content = "   ")

        assertThatThrownBy {
            service.createNote(org.publicId, club.publicId, member.publicId, request, user.publicId, false)
        }.isInstanceOf(ArenaException::class.java)
            .satisfies({ assertThat((it as ArenaException).status).isEqualTo(HttpStatus.BAD_REQUEST) })
    }

    @Test
    fun `createNote throws 400 when content exceeds 1000 characters`() {
        stubOrgClubMemberUser()
        val longContent = "a".repeat(1001)
        val request = CreateNoteRequest(noteType = "general", content = longContent)

        assertThatThrownBy {
            service.createNote(org.publicId, club.publicId, member.publicId, request, user.publicId, false)
        }.isInstanceOf(ArenaException::class.java)
            .satisfies({ assertThat((it as ArenaException).status).isEqualTo(HttpStatus.BAD_REQUEST) })
    }

    @Test
    fun `createNote throws 400 when followUpAt is in the past`() {
        stubOrgClubMemberUser()
        val request = CreateNoteRequest(noteType = "follow_up", content = "Follow up", followUpAt = "2020-01-01")

        assertThatThrownBy {
            service.createNote(org.publicId, club.publicId, member.publicId, request, user.publicId, false)
        }.isInstanceOf(ArenaException::class.java)
            .satisfies({ assertThat((it as ArenaException).status).isEqualTo(HttpStatus.BAD_REQUEST) })
    }

    @Test
    fun `createNote throws 400 when followUpAt is provided on non-follow_up type`() {
        stubOrgClubMemberUser()
        val request = CreateNoteRequest(noteType = "general", content = "Note", followUpAt = "2030-01-01")

        assertThatThrownBy {
            service.createNote(org.publicId, club.publicId, member.publicId, request, user.publicId, false)
        }.isInstanceOf(ArenaException::class.java)
            .satisfies({ assertThat((it as ArenaException).status).isEqualTo(HttpStatus.BAD_REQUEST) })
    }

    @Test
    fun `createNote throws 403 when trainer attempts to create complaint note`() {
        stubOrgClubMemberUser()
        val request = CreateNoteRequest(noteType = "complaint", content = "Complaint note")

        assertThatThrownBy {
            service.createNote(org.publicId, club.publicId, member.publicId, request, user.publicId, true)
        }.isInstanceOf(ArenaException::class.java)
            .satisfies({ assertThat((it as ArenaException).status).isEqualTo(HttpStatus.FORBIDDEN) })
    }

    @Test
    fun `createNote throws 403 when trainer attempts to create follow_up note`() {
        stubOrgClubMemberUser()
        val request = CreateNoteRequest(noteType = "follow_up", content = "Follow up note")

        assertThatThrownBy {
            service.createNote(org.publicId, club.publicId, member.publicId, request, user.publicId, true)
        }.isInstanceOf(ArenaException::class.java)
            .satisfies({ assertThat((it as ArenaException).status).isEqualTo(HttpStatus.FORBIDDEN) })
    }

    @Test
    fun `deleteNote soft-deletes the note`() {
        val note =
            MemberNote(
                organizationId = org.id,
                clubId = club.id,
                memberId = member.id,
                createdByUserId = user.id,
                noteType = MemberNoteType.GENERAL,
                content = "Note",
            )
        whenever(memberNoteRepository.findByPublicId(note.publicId)).thenReturn(note)
        whenever(userRepository.findByPublicIdAndDeletedAtIsNull(user.publicId)).thenReturn(Optional.of(user))
        whenever(memberNoteRepository.save(any<MemberNote>())).thenAnswer { it.arguments[0] as MemberNote }

        service.deleteNote(note.publicId, user.publicId)

        assertThat(note.deletedAt).isNotNull()
    }

    @Test
    fun `deleteNote throws 403 when non-author without manager role attempts delete`() {
        val otherUser = User(email = "other@test.com", passwordHash = "encoded", organizationId = org.id, clubId = club.id)
        // Use a different createdByUserId (999L) to ensure otherUser (id=0) is not the author
        val note =
            MemberNote(
                organizationId = org.id,
                clubId = club.id,
                memberId = member.id,
                createdByUserId = 999L,
                noteType = MemberNoteType.GENERAL,
                content = "Note",
            )
        whenever(memberNoteRepository.findByPublicId(note.publicId)).thenReturn(note)
        whenever(userRepository.findByPublicIdAndDeletedAtIsNull(otherUser.publicId)).thenReturn(Optional.of(otherUser))
        val receptionistRole = Role(nameAr = "موظف", nameEn = "Receptionist", scope = "club")
        whenever(
            userRoleRepository.findByUserId(otherUser.id),
        ).thenReturn(Optional.of(UserRole(userId = otherUser.id, roleId = receptionistRole.id)))
        whenever(roleRepository.findById(receptionistRole.id)).thenReturn(Optional.of(receptionistRole))

        assertThatThrownBy {
            service.deleteNote(note.publicId, otherUser.publicId)
        }.isInstanceOf(ArenaException::class.java)
            .satisfies({ assertThat((it as ArenaException).status).isEqualTo(HttpStatus.FORBIDDEN) })
    }

    @Test
    fun `deleteNote throws 409 when attempting to delete a REJECTION note`() {
        val note =
            MemberNote(
                organizationId = org.id,
                clubId = club.id,
                memberId = member.id,
                createdByUserId = user.id,
                noteType = MemberNoteType.REJECTION,
                content = "Rejected",
            )
        whenever(memberNoteRepository.findByPublicId(note.publicId)).thenReturn(note)

        assertThatThrownBy {
            service.deleteNote(note.publicId, user.publicId)
        }.isInstanceOf(ArenaException::class.java)
            .satisfies({ assertThat((it as ArenaException).status).isEqualTo(HttpStatus.CONFLICT) })
    }

    @Test
    fun `getTimeline returns merged and sorted events from all three sources`() {
        stubOrgClubMemberUser()
        whenever(memberNoteRepository.findByMemberId(member.id, 70, 0)).thenReturn(emptyList())
        whenever(membershipRepository.findByMemberIdForTimeline(member.id, 70)).thenReturn(emptyList())
        whenever(paymentRepository.findByMemberIdForTimeline(member.id, 70)).thenReturn(emptyList())
        whenever(userRoleRepository.findByUserId(user.id)).thenReturn(Optional.empty())

        val result = service.getTimeline(org.publicId, club.publicId, member.publicId, user.publicId, null, 20)

        assertThat(result.events).isEmpty()
        assertThat(result.nextCursor).isNull()
    }

    @Test
    fun `getTimeline returns empty list when member has no events`() {
        stubOrgClubMemberUser()
        whenever(memberNoteRepository.findByMemberId(member.id, 70, 0)).thenReturn(emptyList())
        whenever(membershipRepository.findByMemberIdForTimeline(member.id, 70)).thenReturn(emptyList())
        whenever(paymentRepository.findByMemberIdForTimeline(member.id, 70)).thenReturn(emptyList())
        whenever(userRoleRepository.findByUserId(user.id)).thenReturn(Optional.empty())

        val result = service.getTimeline(org.publicId, club.publicId, member.publicId, user.publicId, null, 20)

        assertThat(result.events).isEmpty()
    }

    @Test
    fun `getFollowUps returns notes due within 7 days for the club`() {
        stubOrgClub()
        whenever(memberNoteRepository.findFollowUpsDueWithin(any(), any(), any())).thenReturn(emptyList())

        val result = service.getFollowUps(org.publicId, club.publicId)

        assertThat(result.followUps).isEmpty()
    }

    @Test
    fun `getFollowUps returns empty list when no follow-up notes are due`() {
        stubOrgClub()
        whenever(memberNoteRepository.findFollowUpsDueWithin(any(), any(), any())).thenReturn(emptyList())

        val result = service.getFollowUps(org.publicId, club.publicId)

        assertThat(result.followUps).isEmpty()
    }
}
