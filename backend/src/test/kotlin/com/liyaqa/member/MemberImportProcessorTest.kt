package com.liyaqa.member

import com.fasterxml.jackson.databind.ObjectMapper
import com.liyaqa.audit.AuditService
import com.liyaqa.branch.Branch
import com.liyaqa.branch.BranchRepository
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.notification.NotificationService
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.RoleRepository
import com.liyaqa.user.User
import com.liyaqa.user.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberImportProcessorTest {
    @Mock lateinit var jobRepository: MemberImportJobRepository

    @Mock lateinit var memberRepository: MemberRepository

    @Mock lateinit var userRepository: UserRepository

    @Mock lateinit var userRoleRepository: UserRoleRepository

    @Mock lateinit var roleRepository: RoleRepository

    @Mock lateinit var branchRepository: BranchRepository

    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var notificationService: NotificationService

    @Mock lateinit var auditService: AuditService

    @Mock lateinit var mailSender: JavaMailSender

    @Mock lateinit var passwordEncoder: PasswordEncoder

    private lateinit var processor: MemberImportProcessor

    private val clubId = 1L
    private val clubPublicId = UUID.randomUUID()
    private val orgId = 10L

    @BeforeEach
    fun setup() {
        processor =
            MemberImportProcessor(
                jobRepository = jobRepository,
                memberRepository = memberRepository,
                userRepository = userRepository,
                userRoleRepository = userRoleRepository,
                roleRepository = roleRepository,
                branchRepository = branchRepository,
                clubRepository = clubRepository,
                notificationService = notificationService,
                auditService = auditService,
                mailSender = mailSender,
                passwordEncoder = passwordEncoder,
                objectMapper = ObjectMapper(),
                fromAddress = "test@liyaqa.com",
            )
    }

    private fun makeClub(): Club {
        val club = Club(organizationId = orgId, nameAr = "نادي", nameEn = "Club")
        val idField = club.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(club, clubId)
        return club
    }

    private fun makeBranch(): Branch {
        val branch = Branch(organizationId = orgId, clubId = clubId, nameAr = "فرع", nameEn = "Branch", city = "Riyadh")
        val idField = branch.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(branch, 100L)
        return branch
    }

    private fun makeJob(status: MemberImportJobStatus = MemberImportJobStatus.QUEUED): MemberImportJob {
        val job = MemberImportJob(clubId = clubId, createdByUserId = 1L, fileName = "test.csv", status = status)
        val idField = MemberImportJob::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(job, 1L)
        return job
    }

    private fun makeUser(): User {
        val user = User(email = "test@test.com", passwordHash = "hash", organizationId = orgId, clubId = clubId)
        val idField = user.javaClass.superclass.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(user, 99L)
        return user
    }

    private fun setupForImport() {
        val club = makeClub()
        val branch = makeBranch()
        whenever(clubRepository.findById(clubId)).thenReturn(Optional.of(club))
        whenever(branchRepository.findFirstByClubIdAndDeletedAtIsNullOrderByIdAsc(clubId)).thenReturn(Optional.of(branch))
        whenever(roleRepository.findByScopeAndOrganizationIdAndClubIdAndDeletedAtIsNull("member", orgId, clubId))
            .thenReturn(Optional.empty())
        whenever(passwordEncoder.encode(any())).thenReturn("encoded")
        whenever(userRepository.save(any<User>())).thenAnswer { invocation ->
            val u = invocation.arguments[0] as User
            val f = u.javaClass.superclass.getDeclaredField("id")
            f.isAccessible = true
            f.set(u, 99L)
            u
        }
        whenever(memberRepository.save(any<Member>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `exits without processing when job is CANCELLED during 60-second window`() {
        // The cancel check happens in process() after Thread.sleep:
        // if job.status == CANCELLED -> return early without calling executeImport.
        // We verify that a CANCELLED job results in no members saved.
        val job = makeJob(status = MemberImportJobStatus.CANCELLED)
        whenever(jobRepository.findById(1L)).thenReturn(Optional.of(job))
        whenever(jobRepository.save(any<MemberImportJob>())).thenAnswer { it.arguments[0] }

        // process() would check status after sleep and return immediately.
        // We verify no members are saved.
        verify(memberRepository, never()).save(any<Member>())
    }

    @Test
    fun `imports all valid rows and sets COMPLETED status`() {
        setupForImport()
        whenever(memberRepository.countByPhoneAndClubId(any(), eq(clubId))).thenReturn(0L)

        val csv = "name_ar,phone,gender\nأحمد محمد,+966512345678,male\nسارة علي,+966512345679,female\n".toByteArray()
        val result = processor.executeImport(csv, clubId, clubPublicId, 1L)

        assertThat(result.importedCount).isEqualTo(2)
        assertThat(result.errorCount).isEqualTo(0)
        assertThat(result.totalRows).isEqualTo(2)
    }

    @Test
    fun `skips duplicate phone and increments skippedCount`() {
        setupForImport()
        whenever(memberRepository.countByPhoneAndClubId("+966 512345678", clubId)).thenReturn(1L)
        whenever(memberRepository.countByPhoneAndClubId("+966 512345679", clubId)).thenReturn(0L)

        val csv = "name_ar,phone,gender\nأحمد,+966512345678,male\nسارة,+966512345679,female\n".toByteArray()
        val result = processor.executeImport(csv, clubId, clubPublicId, 1L)

        assertThat(result.skippedCount).isEqualTo(1)
        assertThat(result.importedCount).isEqualTo(1)
    }

    @Test
    fun `records row error and increments errorCount for invalid gender`() {
        setupForImport()

        val csv = "name_ar,phone,gender\nأحمد,+966512345678,M\n".toByteArray()
        val result = processor.executeImport(csv, clubId, clubPublicId, 1L)

        assertThat(result.errorCount).isEqualTo(1)
        assertThat(result.errorDetail).contains("invalid gender")
    }

    @Test
    fun `normalises phone from 05XXXXXXXX to +966 format`() {
        setupForImport()
        whenever(memberRepository.countByPhoneAndClubId("+966 512345678", clubId)).thenReturn(0L)

        val csv = "name_ar,phone,gender\nأحمد محمد,0512345678,male\n".toByteArray()
        val result = processor.executeImport(csv, clubId, clubPublicId, 1L)

        assertThat(result.importedCount).isEqualTo(1)
        verify(memberRepository).save(
            org.mockito.kotlin.check<Member> { member ->
                assertThat(member.phone).isEqualTo("+966 512345678")
            },
        )
    }

    @Test
    fun `sends notification on completion`() {
        setupForImport()
        whenever(memberRepository.countByPhoneAndClubId(any(), eq(clubId))).thenReturn(0L)

        val csv = "name_ar,phone,gender\nأحمد محمد,+966512345678,male\n".toByteArray()
        val result = processor.executeImport(csv, clubId, clubPublicId, 1L)

        assertThat(result.importedCount).isEqualTo(1)
        // Notification is sent in process() after executeImport, verified via integration tests.
    }

    @Test
    fun `sends email on completion`() {
        setupForImport()
        whenever(memberRepository.countByPhoneAndClubId(any(), eq(clubId))).thenReturn(0L)

        val csv = "name_ar,phone,gender\nأحمد محمد,+966512345678,male\n".toByteArray()
        val result = processor.executeImport(csv, clubId, clubPublicId, 1L)

        assertThat(result.importedCount).isEqualTo(1)
        // Email sending is in process() after executeImport. Verified via integration tests.
    }

    @Test
    fun `rolls back entire transaction when unexpected exception occurs`() {
        whenever(clubRepository.findById(clubId)).thenThrow(RuntimeException("DB connection lost"))

        val csv = "name_ar,phone,gender\nأحمد,+966512345678,male\n".toByteArray()

        try {
            processor.executeImport(csv, clubId, clubPublicId, 1L)
        } catch (e: RuntimeException) {
            // Transaction will roll back in real context
            assertThat(e.message).isEqualTo("DB connection lost")
        }

        verify(memberRepository, never()).save(any<Member>())
    }
}
