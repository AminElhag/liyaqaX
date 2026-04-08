package com.liyaqa.gx

import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.notification.NotificationService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class GXWaitlistSchedulerTest {
    @Mock lateinit var waitlistRepository: GXWaitlistRepository

    @Mock lateinit var waitlistService: GXWaitlistService

    @Mock lateinit var memberRepository: MemberRepository

    @Mock lateinit var classInstanceRepository: GXClassInstanceRepository

    @Mock lateinit var classTypeRepository: GXClassTypeRepository

    @Mock lateinit var notificationService: NotificationService

    @InjectMocks lateinit var scheduler: GXWaitlistScheduler

    private fun classInstance(): GXClassInstance =
        GXClassInstance(
            organizationId = 1L,
            clubId = 10L,
            branchId = 100L,
            classTypeId = 200L,
            instructorId = 50L,
            scheduledAt = Instant.now().plusSeconds(86400),
        ).apply {
            val f = this::class.java.superclass.getDeclaredField("id")
            f.isAccessible = true
            f.set(this, 500L)
        }

    private fun member(): Member =
        Member(
            organizationId = 1L,
            clubId = 10L,
            branchId = 100L,
            userId = 8L,
            firstNameAr = "أحمد",
            firstNameEn = "Ahmed",
            lastNameAr = "الرشيدي",
            lastNameEn = "Al-Rashidi",
            phone = "+966501234567",
        ).apply {
            val f = this::class.java.superclass.getDeclaredField("id")
            f.isAccessible = true
            f.set(this, 300L)
        }

    private fun offeredEntry(notifiedAt: Instant): GXWaitlistEntry =
        GXWaitlistEntry(
            classInstanceId = 500L,
            memberId = 300L,
            position = 1,
            status = GXWaitlistStatus.OFFERED,
        ).apply {
            this.notifiedAt = notifiedAt
            val f = this::class.java.getDeclaredField("id")
            f.isAccessible = true
            f.set(this, 700L)
        }

    @Test
    fun `expireStaleOffers sets OFFERED entries older than 2 hours to EXPIRED`() {
        val staleEntry = offeredEntry(Instant.now().minus(3, ChronoUnit.HOURS))
        whenever(waitlistRepository.findExpiredOffers(any())).thenReturn(listOf(staleEntry))
        whenever(waitlistRepository.save(any<GXWaitlistEntry>())).thenAnswer { it.arguments[0] as GXWaitlistEntry }
        whenever(memberRepository.findById(300L)).thenReturn(Optional.of(member()))
        whenever(classInstanceRepository.findById(500L)).thenReturn(Optional.of(classInstance()))
        whenever(classTypeRepository.findById(200L)).thenReturn(Optional.empty())

        scheduler.expireStaleOffers()

        org.assertj.core.api.Assertions.assertThat(staleEntry.status).isEqualTo(GXWaitlistStatus.EXPIRED)
    }

    @Test
    fun `expireStaleOffers calls promoteNext for each expired entry`() {
        val staleEntry = offeredEntry(Instant.now().minus(3, ChronoUnit.HOURS))
        whenever(waitlistRepository.findExpiredOffers(any())).thenReturn(listOf(staleEntry))
        whenever(waitlistRepository.save(any<GXWaitlistEntry>())).thenAnswer { it.arguments[0] as GXWaitlistEntry }
        whenever(memberRepository.findById(300L)).thenReturn(Optional.of(member()))
        whenever(classInstanceRepository.findById(500L)).thenReturn(Optional.of(classInstance()))
        whenever(classTypeRepository.findById(200L)).thenReturn(Optional.empty())

        scheduler.expireStaleOffers()

        verify(waitlistService).promoteNext(500L)
    }

    @Test
    fun `expireStaleOffers skips OFFERED entries within the 2-hour window`() {
        // No expired entries
        whenever(waitlistRepository.findExpiredOffers(any())).thenReturn(emptyList())

        scheduler.expireStaleOffers()

        verify(waitlistRepository, never()).save(any())
        verify(waitlistService, never()).promoteNext(any())
    }

    @Test
    fun `expireStaleOffers does nothing when no expired entries exist`() {
        whenever(waitlistRepository.findExpiredOffers(any())).thenReturn(emptyList())

        scheduler.expireStaleOffers()

        verify(waitlistRepository, never()).save(any())
    }
}
