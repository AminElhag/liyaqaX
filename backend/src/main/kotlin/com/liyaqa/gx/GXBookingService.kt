package com.liyaqa.gx

import com.liyaqa.club.ClubRepository
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.dto.toPageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.gx.dto.AttendanceEntry
import com.liyaqa.gx.dto.BulkAttendanceRequest
import com.liyaqa.gx.dto.GXAttendanceResponse
import com.liyaqa.gx.dto.GXBookingResponse
import com.liyaqa.gx.dto.GXMemberSummary
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.user.UserRepository
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class GXBookingService(
    private val bookingRepository: GXBookingRepository,
    private val attendanceRepository: GXAttendanceRepository,
    private val classInstanceRepository: GXClassInstanceRepository,
    private val memberRepository: MemberRepository,
    private val membershipRepository: MembershipRepository,
    private val membershipPlanRepository: MembershipPlanRepository,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
) {
    companion object {
        private val ACTIVE_MEMBERSHIP_STATUSES = listOf("active", "frozen")
    }

    // ── Booking ────────────────────────────────────────────────────────────

    @Transactional
    fun bookMember(
        orgPublicId: UUID,
        instancePublicId: UUID,
        memberPublicId: UUID,
    ): GXBookingResponse {
        val org = findOrgOrThrow(orgPublicId)
        val instance = findInstanceOrThrow(instancePublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, instance.clubId)

        // Business rule 2 — Duplicate booking prevention
        if (bookingRepository.existsByInstanceIdAndMemberId(instance.id, member.id)) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "This member already has a booking for this class.",
            )
        }

        // Business rule 4 — Member club scope
        if (member.clubId != instance.clubId) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Member does not belong to the same club as this class.",
            )
        }

        // Business rule 3 — Membership GX check
        val activeMembership =
            membershipRepository.findByMemberIdAndMembershipStatusInAndDeletedAtIsNull(
                member.id,
                ACTIVE_MEMBERSHIP_STATUSES,
            ).orElse(null)

        if (activeMembership == null) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Member does not have an active membership.",
            )
        }

        val plan = membershipPlanRepository.findById(activeMembership.planId).orElse(null)
        if (plan == null || !plan.gxClassesIncluded) {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Member's membership plan does not include GX classes.",
            )
        }

        // Business rule 1 — Capacity check → confirmed or waitlist
        val booking =
            if (instance.bookingsCount < instance.capacity) {
                instance.bookingsCount++
                classInstanceRepository.save(instance)
                bookingRepository.save(
                    GXBooking(
                        organizationId = org.id,
                        clubId = instance.clubId,
                        instanceId = instance.id,
                        memberId = member.id,
                        bookingStatus = "confirmed",
                    ),
                )
            } else {
                val nextPosition = instance.waitlistCount + 1
                instance.waitlistCount++
                classInstanceRepository.save(instance)
                bookingRepository.save(
                    GXBooking(
                        organizationId = org.id,
                        clubId = instance.clubId,
                        instanceId = instance.id,
                        memberId = member.id,
                        bookingStatus = "waitlist",
                        waitlistPosition = nextPosition,
                    ),
                )
            }

        return toBookingResponse(booking, instance, member)
    }

    @Transactional
    fun cancelBooking(
        orgPublicId: UUID,
        instancePublicId: UUID,
        bookingPublicId: UUID,
        reason: String? = null,
    ): GXBookingResponse {
        val org = findOrgOrThrow(orgPublicId)
        val instance = findInstanceOrThrow(instancePublicId, org.id)
        val booking =
            bookingRepository.findByPublicIdAndOrganizationId(bookingPublicId, org.id)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Booking not found.") }

        if (booking.bookingStatus == "cancelled") {
            throw ArenaException(HttpStatus.CONFLICT, "conflict", "Booking is already cancelled.")
        }

        val wasConfirmed = booking.bookingStatus == "confirmed" || booking.bookingStatus == "promoted"
        val wasWaitlist = booking.bookingStatus == "waitlist"

        booking.bookingStatus = "cancelled"
        booking.cancelledAt = Instant.now()
        booking.cancellationReason = reason
        bookingRepository.save(booking)

        if (wasConfirmed) {
            instance.bookingsCount = (instance.bookingsCount - 1).coerceAtLeast(0)

            // Business rule 6 — Waitlist promotion on cancellation
            val nextWaitlist =
                bookingRepository.findFirstByInstanceIdAndBookingStatusOrderByWaitlistPositionAsc(
                    instance.id,
                    "waitlist",
                )
            if (nextWaitlist.isPresent) {
                val promoted = nextWaitlist.get()
                promoted.bookingStatus = "promoted"
                promoted.waitlistPosition = null
                bookingRepository.save(promoted)

                instance.bookingsCount++
                instance.waitlistCount = (instance.waitlistCount - 1).coerceAtLeast(0)
            }
            classInstanceRepository.save(instance)
        } else if (wasWaitlist) {
            instance.waitlistCount = (instance.waitlistCount - 1).coerceAtLeast(0)
            classInstanceRepository.save(instance)
        }

        val member = memberRepository.findById(booking.memberId).orElseThrow()
        return toBookingResponse(booking, instance, member)
    }

    // Business rule 9 — Class cancellation cascade
    @Transactional
    fun cancelAllBookingsForInstance(instance: GXClassInstance) {
        val activeBookings =
            bookingRepository.findAllByInstanceIdAndBookingStatusIn(
                instance.id,
                listOf("confirmed", "waitlist", "promoted"),
            )
        for (booking in activeBookings) {
            booking.bookingStatus = "cancelled"
            booking.cancelledAt = Instant.now()
            booking.cancellationReason = "Class cancelled"
        }
        bookingRepository.saveAll(activeBookings)
        instance.bookingsCount = 0
        instance.waitlistCount = 0
        classInstanceRepository.save(instance)
    }

    fun listBookingsForInstance(
        orgPublicId: UUID,
        instancePublicId: UUID,
        pageable: Pageable,
    ): PageResponse<GXBookingResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val instance = findInstanceOrThrow(instancePublicId, org.id)
        return bookingRepository
            .findAllByInstanceId(instance.id, pageable)
            .map { booking ->
                val member = memberRepository.findById(booking.memberId).orElseThrow()
                toBookingResponse(booking, instance, member)
            }
            .toPageResponse()
    }

    fun listWaitlistForInstance(
        orgPublicId: UUID,
        instancePublicId: UUID,
    ): List<GXBookingResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val instance = findInstanceOrThrow(instancePublicId, org.id)
        return bookingRepository
            .findAllByInstanceIdAndBookingStatus(instance.id, "waitlist")
            .sortedBy { it.waitlistPosition }
            .map { booking ->
                val member = memberRepository.findById(booking.memberId).orElseThrow()
                toBookingResponse(booking, instance, member)
            }
    }

    fun listMemberBookings(
        orgPublicId: UUID,
        memberPublicId: UUID,
        pageable: Pageable,
    ): PageResponse<GXBookingResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val clubId = resolveClubIdFromOrg(org)
        val member = findMemberOrThrow(memberPublicId, org.id, clubId)
        return bookingRepository
            .findAllByMemberIdAndOrganizationId(member.id, org.id, pageable)
            .map { booking ->
                val instance = classInstanceRepository.findById(booking.instanceId).orElseThrow()
                toBookingResponse(booking, instance, member)
            }
            .toPageResponse()
    }

    // ── Attendance ─────���───────────────────────────────────────────────────

    @Transactional
    fun submitAttendance(
        orgPublicId: UUID,
        instancePublicId: UUID,
        request: BulkAttendanceRequest,
        callerUserPublicId: UUID,
    ): List<GXAttendanceResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val instance = findInstanceOrThrow(instancePublicId, org.id)

        // Business rule 7 — Attendance window
        if (instance.instanceStatus == "cancelled") {
            throw ArenaException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "business-rule-violation",
                "Cannot submit attendance for a cancelled class.",
            )
        }

        val callerUser =
            userRepository.findByPublicIdAndDeletedAtIsNull(callerUserPublicId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "User not found.") }

        val clubId = instance.clubId
        val responses = mutableListOf<GXAttendanceResponse>()

        for (entry in request.attendance) {
            val member = findMemberOrThrow(entry.memberId, org.id, clubId)
            val attendance = recordAttendance(org.id, instance.id, member.id, entry, callerUser.id)
            responses.add(toAttendanceResponse(attendance, instance, member))
        }

        // Mark instance as completed when attendance is submitted
        if (instance.instanceStatus == "scheduled") {
            instance.instanceStatus = "completed"
            classInstanceRepository.save(instance)
        }

        return responses
    }

    fun listAttendanceForInstance(
        orgPublicId: UUID,
        instancePublicId: UUID,
    ): List<GXAttendanceResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val instance = findInstanceOrThrow(instancePublicId, org.id)
        return attendanceRepository.findAllByInstanceId(instance.id).map { attendance ->
            val member = memberRepository.findById(attendance.memberId).orElseThrow()
            toAttendanceResponse(attendance, instance, member)
        }
    }

    // ── Private helpers ────────────────��───────────────────────────────────

    // Business rule 8 — Attendance once per member per instance (upsert)
    private fun recordAttendance(
        organizationId: Long,
        instanceId: Long,
        memberId: Long,
        entry: AttendanceEntry,
        markedById: Long,
    ): GXAttendance {
        val existing = attendanceRepository.findByInstanceIdAndMemberId(instanceId, memberId)
        return if (existing.isPresent) {
            val att = existing.get()
            att.attendanceStatus = entry.status
            att.markedAt = Instant.now()
            att.markedById = markedById
            attendanceRepository.save(att)
        } else {
            attendanceRepository.save(
                GXAttendance(
                    organizationId = organizationId,
                    instanceId = instanceId,
                    memberId = memberId,
                    attendanceStatus = entry.status,
                    markedById = markedById,
                ),
            )
        }
    }

    private fun findOrgOrThrow(orgPublicId: UUID): Organization =
        organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.") }

    private fun findInstanceOrThrow(
        instancePublicId: UUID,
        organizationId: Long,
    ): GXClassInstance =
        classInstanceRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(instancePublicId, organizationId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Class instance not found.") }

    private fun findMemberOrThrow(
        memberPublicId: UUID,
        organizationId: Long,
        clubId: Long,
    ): Member =
        memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(memberPublicId, organizationId, clubId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Member not found.") }

    private fun resolveClubIdFromOrg(org: Organization): Long =
        clubRepository.findAllByOrganizationIdAndDeletedAtIsNull(org.id, Pageable.ofSize(1))
            .content
            .firstOrNull()?.id
            ?: throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "No club found for organization.")

    private fun toBookingResponse(
        booking: GXBooking,
        instance: GXClassInstance,
        member: Member,
    ): GXBookingResponse =
        GXBookingResponse(
            id = booking.publicId,
            instanceId = instance.publicId,
            member =
                GXMemberSummary(
                    id = member.publicId,
                    firstNameAr = member.firstNameAr,
                    firstNameEn = member.firstNameEn,
                    lastNameAr = member.lastNameAr,
                    lastNameEn = member.lastNameEn,
                ),
            status = booking.bookingStatus,
            waitlistPosition = booking.waitlistPosition,
            bookedAt = booking.bookedAt,
            cancelledAt = booking.cancelledAt,
        )

    private fun toAttendanceResponse(
        attendance: GXAttendance,
        instance: GXClassInstance,
        member: Member,
    ): GXAttendanceResponse =
        GXAttendanceResponse(
            id = attendance.publicId,
            instanceId = instance.publicId,
            member =
                GXMemberSummary(
                    id = member.publicId,
                    firstNameAr = member.firstNameAr,
                    firstNameEn = member.firstNameEn,
                    lastNameAr = member.lastNameAr,
                    lastNameEn = member.lastNameEn,
                ),
            status = attendance.attendanceStatus,
            markedAt = attendance.markedAt,
        )
}
