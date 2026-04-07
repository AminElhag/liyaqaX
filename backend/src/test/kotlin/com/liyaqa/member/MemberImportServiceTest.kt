package com.liyaqa.member

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MemberImportServiceTest {
    @Mock lateinit var jobRepository: MemberImportJobRepository

    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var auditService: AuditService

    @Mock lateinit var processor: MemberImportProcessor

    @InjectMocks lateinit var service: MemberImportService

    private val clubPublicId = UUID.randomUUID()
    private val actorUserId = 1L
    private val actorPublicId = UUID.randomUUID().toString()
    private val actorScope = "platform"

    private fun club(): Club {
        val club = Club(organizationId = 1L, nameAr = "نادي", nameEn = "Club")
        val field = club.javaClass.superclass.getDeclaredField("id")
        field.isAccessible = true
        field.set(club, 1L)
        val pubField = Club::class.java.getDeclaredField("publicId")
        pubField.isAccessible = true
        pubField.set(club, clubPublicId)
        return club
    }

    private fun validCsv(): ByteArray = "name_ar,phone,gender\nأحمد,+966512345678,male\n".toByteArray()

    private fun multipartFile(
        content: ByteArray,
        filename: String = "test.csv",
    ) = MockMultipartFile("file", filename, "text/csv", content)

    @Test
    fun `validates CSV successfully when all required headers are present`() {
        val club = club()
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(club))
        whenever(jobRepository.save(any<MemberImportJob>())).thenAnswer { invocation ->
            val job = invocation.arguments[0] as MemberImportJob
            val field = MemberImportJob::class.java.getDeclaredField("id")
            field.isAccessible = true
            field.set(job, 1L)
            job
        }

        val result =
            service.importMembers(
                clubPublicId = clubPublicId,
                file = multipartFile(validCsv()),
                actorUserId = actorUserId,
                actorPublicId = actorPublicId,
                actorScope = actorScope,
            )

        assertThat(result.status).isEqualTo("QUEUED")
        assertThat(result.fileName).isEqualTo("test.csv")
    }

    @Test
    fun `throws 422 when required headers are missing`() {
        val club = club()
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(club))

        val csv = "name_ar,email\nأحمد,a@b.com\n".toByteArray()

        assertThatThrownBy {
            service.importMembers(clubPublicId, multipartFile(csv), actorUserId, actorPublicId, actorScope)
        }.hasMessageContaining("Missing required CSV headers")
    }

    @Test
    fun `throws 422 when file is empty`() {
        val club = club()
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(club))

        assertThatThrownBy {
            service.importMembers(clubPublicId, multipartFile(ByteArray(0)), actorUserId, actorPublicId, actorScope)
        }.hasMessageContaining("empty")
    }

    @Test
    fun `throws 422 when file is not parseable CSV`() {
        val club = club()
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(club))

        val badContent = byteArrayOf(0x00, 0x01, 0xFF.toByte(), 0xFE.toByte())

        assertThatThrownBy {
            service.importMembers(clubPublicId, multipartFile(badContent), actorUserId, actorPublicId, actorScope)
        }.hasMessageContaining("Missing required CSV headers")
    }

    @Test
    fun `creates job with QUEUED status on valid file`() {
        val club = club()
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(club))
        whenever(jobRepository.save(any<MemberImportJob>())).thenAnswer { invocation ->
            val job = invocation.arguments[0] as MemberImportJob
            val field = MemberImportJob::class.java.getDeclaredField("id")
            field.isAccessible = true
            field.set(job, 1L)
            job
        }

        service.importMembers(clubPublicId, multipartFile(validCsv()), actorUserId, actorPublicId, actorScope)

        verify(jobRepository).save(
            check<MemberImportJob> { job ->
                assertThat(job.status).isEqualTo(MemberImportJobStatus.QUEUED)
                assertThat(job.clubId).isEqualTo(1L)
                assertThat(job.createdByUserId).isEqualTo(actorUserId)
            },
        )
    }

    @Test
    fun `logs MEMBER_IMPORT_STARTED audit action`() {
        val club = club()
        whenever(clubRepository.findByPublicIdAndDeletedAtIsNull(clubPublicId)).thenReturn(Optional.of(club))
        whenever(jobRepository.save(any<MemberImportJob>())).thenAnswer { invocation ->
            val job = invocation.arguments[0] as MemberImportJob
            val field = MemberImportJob::class.java.getDeclaredField("id")
            field.isAccessible = true
            field.set(job, 1L)
            job
        }

        service.importMembers(clubPublicId, multipartFile(validCsv()), actorUserId, actorPublicId, actorScope)

        verify(auditService).log(
            action = eq(AuditAction.MEMBER_IMPORT_STARTED),
            entityType = eq("MemberImportJob"),
            entityId = any(),
            actorId = eq(actorPublicId),
            actorScope = eq(actorScope),
            organizationId = anyOrNull(),
            clubId = anyOrNull(),
            changesJson = anyOrNull(),
            ipAddress = anyOrNull(),
        )
    }
}
