package com.liyaqa.gx

import com.liyaqa.common.exception.ArenaException
import com.liyaqa.gx.dto.WaitlistEntryResponse
import com.liyaqa.gx.dto.WaitlistJoinResponse
import com.liyaqa.gx.dto.WaitlistListResponse
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.notification.NotificationService
import com.liyaqa.notification.NotificationType
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
@Transactional(readOnly = true)
class GXWaitlistService(
    private val waitlistRepository: GXWaitlistRepository,
    private val classInstanceRepository: GXClassInstanceRepository,
    private val bookingRepository: GXBookingRepository,
    private val memberRepository: MemberRepository,
    private val notificationService: NotificationService,
    private val classTypeRepository: GXClassTypeRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(GXWaitlistService::class.java)
        private const val OFFER_WINDOW_HOURS = 2L
    }

    @Transactional
    fun joinWaitlist(
        classInstanceId: Long,
        memberId: Long,
    ): WaitlistJoinResponse {
        val instance =
            classInstanceRepository.findById(classInstanceId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Class not found.") }

        // Rule 4 — past class guard
        if (instance.scheduledAt.isBefore(Instant.now())) {
            throw ArenaException(HttpStatus.BAD_REQUEST, "bad-request", "Cannot join waitlist for a past class.")
        }

        // Rule 1 — class must be full
        if (instance.bookingsCount < instance.capacity) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Class has available spots — book directly.",
            )
        }

        // Rule 2 — duplicate booking guard
        val existingBooking = bookingRepository.findByInstanceIdAndMemberId(instance.id, memberId)
        if (existingBooking.isPresent && existingBooking.get().bookingStatus != "cancelled") {
            throw ArenaException(HttpStatus.CONFLICT, "conflict", "You already have a booking for this class.")
        }

        // Rule 3 — duplicate waitlist guard
        val existingEntry = waitlistRepository.findByClassAndMember(instance.id, memberId)
        if (existingEntry != null && existingEntry.status in listOf(GXWaitlistStatus.WAITING, GXWaitlistStatus.OFFERED)) {
            throw ArenaException(HttpStatus.CONFLICT, "conflict", "You are already on the waitlist for this class.")
        }

        // Rule 5 — position assignment with pessimistic lock via FOR UPDATE on instance
        // The @Transactional boundary + the findById lock on the instance row serializes concurrent inserts
        val nextPos = waitlistRepository.nextPosition(instance.id)

        val entry =
            waitlistRepository.save(
                GXWaitlistEntry(
                    classInstanceId = instance.id,
                    memberId = memberId,
                    position = nextPos,
                ),
            )

        return WaitlistJoinResponse(
            entryId = entry.publicId,
            position = nextPos,
            status = entry.status.name,
            message = "You are #$nextPos on the waitlist.",
        )
    }

    @Transactional
    fun acceptOffer(
        classInstanceId: Long,
        memberId: Long,
    ): GXBooking {
        val instance =
            classInstanceRepository.findById(classInstanceId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Class not found.") }

        val entry =
            waitlistRepository.findByClassAndMember(instance.id, memberId)
                ?: throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Waitlist entry not found.")

        // Rule 7 — only OFFERED entries can be accepted
        if (entry.status != GXWaitlistStatus.OFFERED) {
            throw ArenaException(HttpStatus.CONFLICT, "conflict", "Your waitlist entry is not in offered status.")
        }

        // Rule 8 — race condition: check capacity at accept time
        if (instance.bookingsCount >= instance.capacity) {
            entry.status = GXWaitlistStatus.WAITING
            entry.notifiedAt = null
            waitlistRepository.save(entry)
            throw ArenaException(HttpStatus.CONFLICT, "conflict", "Spot no longer available.")
        }

        val member = memberRepository.findById(memberId).orElseThrow()

        val booking =
            bookingRepository.save(
                GXBooking(
                    organizationId = member.organizationId,
                    clubId = member.clubId,
                    instanceId = instance.id,
                    memberId = memberId,
                    bookingStatus = "confirmed",
                ),
            )

        entry.status = GXWaitlistStatus.ACCEPTED
        entry.acceptedBookingId = booking.id
        waitlistRepository.save(entry)

        instance.bookingsCount++
        classInstanceRepository.save(instance)

        // Send confirmation notification
        try {
            val classType = classTypeRepository.findById(instance.classTypeId).orElse(null)
            notificationService.create(
                recipientUserId = member.userId,
                recipientScope = "member",
                type = NotificationType.GX_WAITLIST_CONFIRMED,
                paramsJson = """{"className":"${classType?.nameEn ?: ""}","classDate":"${instance.scheduledAt}"}""",
                entityType = "GXWaitlistEntry",
                entityId = entry.publicId.toString(),
            )
        } catch (e: Exception) {
            log.warn("Failed to create GX_WAITLIST_CONFIRMED notification: {}", e.message)
        }

        return booking
    }

    @Transactional
    fun leaveWaitlist(
        classInstanceId: Long,
        memberId: Long,
    ) {
        val entry =
            waitlistRepository.findByClassAndMember(classInstanceId, memberId)
                ?: throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Waitlist entry not found.")

        // Rule 14 — cannot leave if ACCEPTED
        if (entry.status == GXWaitlistStatus.ACCEPTED) {
            throw ArenaException(HttpStatus.CONFLICT, "conflict", "Cannot leave waitlist — you already have a booking.")
        }

        if (entry.status !in listOf(GXWaitlistStatus.WAITING, GXWaitlistStatus.OFFERED)) {
            throw ArenaException(HttpStatus.CONFLICT, "conflict", "Waitlist entry is not active.")
        }

        val wasOffered = entry.status == GXWaitlistStatus.OFFERED
        entry.status = GXWaitlistStatus.CANCELLED
        waitlistRepository.save(entry)

        // Rule 14 — if was OFFERED, promote next immediately
        if (wasOffered) {
            promoteNext(classInstanceId)
        }
    }

    @Transactional
    fun promoteNext(classInstanceId: Long) {
        val instance = classInstanceRepository.findById(classInstanceId).orElse(null) ?: return

        // Precondition: there must be an available spot
        if (instance.bookingsCount >= instance.capacity) return

        val nextEntry = waitlistRepository.findNextWaiting(classInstanceId) ?: return

        nextEntry.status = GXWaitlistStatus.OFFERED
        nextEntry.notifiedAt = Instant.now()
        waitlistRepository.save(nextEntry)

        // Send offered notification
        try {
            val member = memberRepository.findById(nextEntry.memberId).orElse(null)
            val classType = classTypeRepository.findById(instance.classTypeId).orElse(null)
            val deadline = nextEntry.notifiedAt!!.plus(OFFER_WINDOW_HOURS, ChronoUnit.HOURS)
            if (member != null) {
                notificationService.create(
                    recipientUserId = member.userId,
                    recipientScope = "member",
                    type = NotificationType.GX_WAITLIST_OFFERED,
                    paramsJson =
                        """{"className":"${classType?.nameEn ?: ""}",""" +
                            """"classDate":"${instance.scheduledAt}","deadline":"$deadline"}""",
                    entityType = "GXWaitlistEntry",
                    entityId = nextEntry.publicId.toString(),
                )
            }
        } catch (e: Exception) {
            log.warn("Failed to create GX_WAITLIST_OFFERED notification: {}", e.message)
        }
    }

    @Transactional
    fun cancelAllForClass(classInstanceId: Long) {
        val activeEntries = waitlistRepository.findActiveEntriesForClass(classInstanceId)
        for (entry in activeEntries) {
            entry.status = GXWaitlistStatus.CANCELLED
            waitlistRepository.save(entry)

            // Rule 13 — notify each affected member with GX_CLASS_CANCELLED
            try {
                val member = memberRepository.findById(entry.memberId).orElse(null)
                val instance = classInstanceRepository.findById(classInstanceId).orElse(null)
                val classType = instance?.let { classTypeRepository.findById(it.classTypeId).orElse(null) }
                if (member != null) {
                    notificationService.create(
                        recipientUserId = member.userId,
                        recipientScope = "member",
                        type = NotificationType.GX_CLASS_CANCELLED,
                        paramsJson = """{"className":"${classType?.nameEn ?: ""}","classDate":"${instance?.scheduledAt ?: ""}"}""",
                        entityType = "GXClassInstance",
                        entityId = instance?.publicId?.toString(),
                    )
                }
            } catch (e: Exception) {
                log.warn("Failed to create GX_CLASS_CANCELLED notification for waitlist entry: {}", e.message)
            }
        }
    }

    // Staff: remove a member from the waitlist
    @Transactional
    fun staffRemoveEntry(
        classInstanceId: Long,
        entryId: Long,
    ) {
        val entry =
            waitlistRepository.findById(entryId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Waitlist entry not found.") }

        if (entry.classInstanceId != classInstanceId) {
            throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Waitlist entry not found.")
        }

        if (entry.status !in listOf(GXWaitlistStatus.WAITING, GXWaitlistStatus.OFFERED)) {
            throw ArenaException(HttpStatus.CONFLICT, "conflict", "Waitlist entry is not active.")
        }

        val wasOffered = entry.status == GXWaitlistStatus.OFFERED
        entry.status = GXWaitlistStatus.CANCELLED
        waitlistRepository.save(entry)

        // Rule 15 — if was OFFERED, promote next
        if (wasOffered) {
            promoteNext(classInstanceId)
        }
    }

    // List entries for staff view
    fun listEntriesForClass(classInstanceId: Long): WaitlistListResponse {
        val entries =
            waitlistRepository.findActiveEntriesForClass(classInstanceId)
                .sortedBy { it.position }

        val responses =
            entries.map { entry ->
                val member = memberRepository.findById(entry.memberId).orElse(null)
                toEntryResponse(entry, member)
            }

        return WaitlistListResponse(
            waitlistCount = entries.size,
            entries = responses,
        )
    }

    // List entries for member's arena bookings tab
    fun listEntriesForMember(memberId: Long): List<WaitlistEntryResponse> {
        val entries = waitlistRepository.findActiveEntriesForMember(memberId)
        return entries.map { entry ->
            val member = memberRepository.findById(entry.memberId).orElse(null)
            toEntryResponse(entry, member)
        }
    }

    // Get the member's waitlist status for a specific class
    fun getMemberEntryForClass(
        classInstanceId: Long,
        memberId: Long,
    ): GXWaitlistEntry? {
        val entry = waitlistRepository.findByClassAndMember(classInstanceId, memberId)
        if (entry != null && entry.status in listOf(GXWaitlistStatus.WAITING, GXWaitlistStatus.OFFERED)) {
            return entry
        }
        return null
    }

    fun findEntryByPublicId(publicId: java.util.UUID): GXWaitlistEntry? =
        waitlistRepository.findAll().firstOrNull { it.publicId == publicId }

    private fun toEntryResponse(
        entry: GXWaitlistEntry,
        member: Member?,
    ): WaitlistEntryResponse =
        WaitlistEntryResponse(
            entryId = entry.publicId,
            position = entry.position,
            status = entry.status.name,
            memberName = member?.let { "${it.firstNameEn} ${it.lastNameEn}" } ?: "",
            memberPhone = member?.phone ?: "",
            notifiedAt = entry.notifiedAt,
            offerExpiresAt = entry.notifiedAt?.plus(OFFER_WINDOW_HOURS, ChronoUnit.HOURS),
            createdAt = entry.createdAt,
        )
}
