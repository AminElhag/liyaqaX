package com.liyaqa.gx

import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.notification.NotificationService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class GXWaitlistServiceTest {
    @Mock lateinit var waitlistRepository: GXWaitlistRepository

    @Mock lateinit var classInstanceRepository: GXClassInstanceRepository

    @Mock lateinit var bookingRepository: GXBookingRepository

    @Mock lateinit var memberRepository: MemberRepository

    @Mock lateinit var notificationService: NotificationService

    @Mock lateinit var classTypeRepository: GXClassTypeRepository

    @InjectMocks lateinit var service: GXWaitlistService

    private fun classInstance(
        capacity: Int = 10,
        bookingsCount: Int = 10,
        scheduledAt: Instant = Instant.now().plusSeconds(86400),
    ): GXClassInstance =
        GXClassInstance(
            organizationId = 1L,
            clubId = 10L,
            branchId = 100L,
            classTypeId = 200L,
            instructorId = 50L,
            scheduledAt = scheduledAt,
            capacity = capacity,
            bookingsCount = bookingsCount,
        ).apply {
            val f = this::class.java.superclass.getDeclaredField("id")
            f.isAccessible = true
            f.set(this, 500L)
        }

    private fun member(id: Long = 300L): Member =
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
            f.set(this, id)
        }

    private fun waitlistEntry(
        classInstanceId: Long = 500L,
        memberId: Long = 300L,
        position: Int = 1,
        status: GXWaitlistStatus = GXWaitlistStatus.WAITING,
    ): GXWaitlistEntry =
        GXWaitlistEntry(
            classInstanceId = classInstanceId,
            memberId = memberId,
            position = position,
            status = status,
        ).apply {
            val f = this::class.java.getDeclaredField("id")
            f.isAccessible = true
            f.set(this, 700L)
        }

    // ── joinWaitlist ──────────────────────────────────────────────────────────

    @Test
    fun `joinWaitlist returns position 1 when no one else is waiting`() {
        val instance = classInstance(capacity = 10, bookingsCount = 10)
        whenever(classInstanceRepository.findById(500L)).thenReturn(Optional.of(instance))
        whenever(bookingRepository.findByInstanceIdAndMemberId(500L, 300L)).thenReturn(Optional.empty())
        whenever(waitlistRepository.findByClassAndMember(500L, 300L)).thenReturn(null)
        whenever(waitlistRepository.nextPosition(500L)).thenReturn(1)
        whenever(waitlistRepository.save(any<GXWaitlistEntry>())).thenAnswer { it.arguments[0] as GXWaitlistEntry }

        val response = service.joinWaitlist(500L, 300L)

        assertThat(response.position).isEqualTo(1)
        assertThat(response.status).isEqualTo("WAITING")
    }

    @Test
    fun `joinWaitlist returns position 2 when one person is already waiting`() {
        val instance = classInstance(capacity = 10, bookingsCount = 10)
        whenever(classInstanceRepository.findById(500L)).thenReturn(Optional.of(instance))
        whenever(bookingRepository.findByInstanceIdAndMemberId(500L, 300L)).thenReturn(Optional.empty())
        whenever(waitlistRepository.findByClassAndMember(500L, 300L)).thenReturn(null)
        whenever(waitlistRepository.nextPosition(500L)).thenReturn(2)
        whenever(waitlistRepository.save(any<GXWaitlistEntry>())).thenAnswer { it.arguments[0] as GXWaitlistEntry }

        val response = service.joinWaitlist(500L, 300L)

        assertThat(response.position).isEqualTo(2)
    }

    @Test
    fun `joinWaitlist throws 409 when class has available spots`() {
        val instance = classInstance(capacity = 10, bookingsCount = 5)
        whenever(classInstanceRepository.findById(500L)).thenReturn(Optional.of(instance))

        assertThatThrownBy { service.joinWaitlist(500L, 300L) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `joinWaitlist throws 409 when member already has active booking`() {
        val instance = classInstance()
        val booking =
            GXBooking(
                organizationId = 1L,
                clubId = 10L,
                instanceId = 500L,
                memberId = 300L,
                bookingStatus = "confirmed",
            )
        whenever(classInstanceRepository.findById(500L)).thenReturn(Optional.of(instance))
        whenever(bookingRepository.findByInstanceIdAndMemberId(500L, 300L)).thenReturn(Optional.of(booking))

        assertThatThrownBy { service.joinWaitlist(500L, 300L) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `joinWaitlist throws 409 when member already on waitlist`() {
        val instance = classInstance()
        whenever(classInstanceRepository.findById(500L)).thenReturn(Optional.of(instance))
        whenever(bookingRepository.findByInstanceIdAndMemberId(500L, 300L)).thenReturn(Optional.empty())
        whenever(waitlistRepository.findByClassAndMember(500L, 300L))
            .thenReturn(waitlistEntry(status = GXWaitlistStatus.WAITING))

        assertThatThrownBy { service.joinWaitlist(500L, 300L) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `joinWaitlist throws 400 when class is in the past`() {
        val instance = classInstance(scheduledAt = Instant.now().minusSeconds(3600))
        whenever(classInstanceRepository.findById(500L)).thenReturn(Optional.of(instance))

        assertThatThrownBy { service.joinWaitlist(500L, 300L) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.BAD_REQUEST)
    }

    // ── promoteNext ───────────────────────────────────────────────────────────

    @Test
    fun `promoteNext sets first WAITING entry to OFFERED and sends notification`() {
        val instance = classInstance(capacity = 10, bookingsCount = 9)
        val entry = waitlistEntry(status = GXWaitlistStatus.WAITING)
        val member = member()
        whenever(classInstanceRepository.findById(500L)).thenReturn(Optional.of(instance))
        whenever(waitlistRepository.findNextWaiting(500L)).thenReturn(entry)
        whenever(waitlistRepository.save(any<GXWaitlistEntry>())).thenAnswer { it.arguments[0] as GXWaitlistEntry }
        whenever(memberRepository.findById(300L)).thenReturn(Optional.of(member))
        whenever(classTypeRepository.findById(200L)).thenReturn(Optional.empty())

        service.promoteNext(500L)

        assertThat(entry.status).isEqualTo(GXWaitlistStatus.OFFERED)
        assertThat(entry.notifiedAt).isNotNull()
    }

    @Test
    fun `promoteNext does nothing when no WAITING entries exist`() {
        val instance = classInstance(capacity = 10, bookingsCount = 9)
        whenever(classInstanceRepository.findById(500L)).thenReturn(Optional.of(instance))
        whenever(waitlistRepository.findNextWaiting(500L)).thenReturn(null)

        service.promoteNext(500L)

        verify(waitlistRepository, never()).save(any())
    }

    // ── acceptOffer ───────────────────────────────────────────────────────────

    @Test
    fun `acceptOffer creates booking and sets entry to ACCEPTED`() {
        val instance = classInstance(capacity = 10, bookingsCount = 9)
        val entry =
            waitlistEntry(status = GXWaitlistStatus.OFFERED).apply {
                notifiedAt = Instant.now()
            }
        val member = member()
        whenever(classInstanceRepository.findById(500L)).thenReturn(Optional.of(instance))
        whenever(waitlistRepository.findByClassAndMember(500L, 300L)).thenReturn(entry)
        whenever(memberRepository.findById(300L)).thenReturn(Optional.of(member))
        whenever(bookingRepository.save(any<GXBooking>())).thenAnswer { it.arguments[0] as GXBooking }
        whenever(waitlistRepository.save(any<GXWaitlistEntry>())).thenAnswer { it.arguments[0] as GXWaitlistEntry }
        whenever(classInstanceRepository.save(any<GXClassInstance>())).thenAnswer { it.arguments[0] as GXClassInstance }
        whenever(classTypeRepository.findById(200L)).thenReturn(Optional.empty())

        val booking = service.acceptOffer(500L, 300L)

        assertThat(booking.bookingStatus).isEqualTo("confirmed")
        assertThat(entry.status).isEqualTo(GXWaitlistStatus.ACCEPTED)
        assertThat(instance.bookingsCount).isEqualTo(10)
    }

    @Test
    fun `acceptOffer throws 409 when entry is not OFFERED`() {
        val instance = classInstance()
        val entry = waitlistEntry(status = GXWaitlistStatus.WAITING)
        whenever(classInstanceRepository.findById(500L)).thenReturn(Optional.of(instance))
        whenever(waitlistRepository.findByClassAndMember(500L, 300L)).thenReturn(entry)

        assertThatThrownBy { service.acceptOffer(500L, 300L) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `acceptOffer throws 409 when class is full at accept time (race condition)`() {
        val instance = classInstance(capacity = 10, bookingsCount = 10)
        val entry =
            waitlistEntry(status = GXWaitlistStatus.OFFERED).apply {
                notifiedAt = Instant.now()
            }
        whenever(classInstanceRepository.findById(500L)).thenReturn(Optional.of(instance))
        whenever(waitlistRepository.findByClassAndMember(500L, 300L)).thenReturn(entry)
        whenever(waitlistRepository.save(any<GXWaitlistEntry>())).thenAnswer { it.arguments[0] as GXWaitlistEntry }

        assertThatThrownBy { service.acceptOffer(500L, 300L) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)

        // Entry reverted to WAITING
        assertThat(entry.status).isEqualTo(GXWaitlistStatus.WAITING)
    }

    // ── leaveWaitlist ─────────────────────────────────────────────────────────

    @Test
    fun `leaveWaitlist removes WAITING entry cleanly`() {
        val entry = waitlistEntry(status = GXWaitlistStatus.WAITING)
        whenever(waitlistRepository.findByClassAndMember(500L, 300L)).thenReturn(entry)
        whenever(waitlistRepository.save(any<GXWaitlistEntry>())).thenAnswer { it.arguments[0] as GXWaitlistEntry }

        service.leaveWaitlist(500L, 300L)

        assertThat(entry.status).isEqualTo(GXWaitlistStatus.CANCELLED)
    }

    @Test
    fun `leaveWaitlist calls promoteNext when leaving an OFFERED entry`() {
        val instance = classInstance(capacity = 10, bookingsCount = 9)
        val entry = waitlistEntry(status = GXWaitlistStatus.OFFERED)
        whenever(waitlistRepository.findByClassAndMember(500L, 300L)).thenReturn(entry)
        whenever(waitlistRepository.save(any<GXWaitlistEntry>())).thenAnswer { it.arguments[0] as GXWaitlistEntry }
        whenever(classInstanceRepository.findById(500L)).thenReturn(Optional.of(instance))
        whenever(waitlistRepository.findNextWaiting(500L)).thenReturn(null)

        service.leaveWaitlist(500L, 300L)

        assertThat(entry.status).isEqualTo(GXWaitlistStatus.CANCELLED)
        verify(classInstanceRepository).findById(500L)
    }

    @Test
    fun `leaveWaitlist throws 409 when entry is ACCEPTED`() {
        val entry = waitlistEntry(status = GXWaitlistStatus.ACCEPTED)
        whenever(waitlistRepository.findByClassAndMember(500L, 300L)).thenReturn(entry)

        assertThatThrownBy { service.leaveWaitlist(500L, 300L) }
            .isInstanceOf(ArenaException::class.java)
            .extracting("status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    // ── cancelAllForClass ─────────────────────────────────────────────────────

    @Test
    fun `cancelAllForClass sets all active entries to CANCELLED and notifies members`() {
        val member = member()
        val entry1 =
            waitlistEntry(status = GXWaitlistStatus.WAITING).apply {
                val f = this::class.java.getDeclaredField("id")
                f.isAccessible = true
                f.set(this, 701L)
            }
        val entry2 =
            waitlistEntry(status = GXWaitlistStatus.OFFERED).apply {
                val f = this::class.java.getDeclaredField("id")
                f.isAccessible = true
                f.set(this, 702L)
            }
        val instance = classInstance()
        whenever(waitlistRepository.findActiveEntriesForClass(500L)).thenReturn(listOf(entry1, entry2))
        whenever(waitlistRepository.save(any<GXWaitlistEntry>())).thenAnswer { it.arguments[0] as GXWaitlistEntry }
        whenever(memberRepository.findById(300L)).thenReturn(Optional.of(member))
        whenever(classInstanceRepository.findById(500L)).thenReturn(Optional.of(instance))
        whenever(classTypeRepository.findById(200L)).thenReturn(Optional.empty())

        service.cancelAllForClass(500L)

        assertThat(entry1.status).isEqualTo(GXWaitlistStatus.CANCELLED)
        assertThat(entry2.status).isEqualTo(GXWaitlistStatus.CANCELLED)
    }
}
