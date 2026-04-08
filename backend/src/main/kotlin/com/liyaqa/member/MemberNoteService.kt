package com.liyaqa.member

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.audit.softDelete
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.gx.GXBookingRepository
import com.liyaqa.gx.GXClassInstanceRepository
import com.liyaqa.member.dto.CreateNoteRequest
import com.liyaqa.member.dto.FollowUpItem
import com.liyaqa.member.dto.FollowUpResponse
import com.liyaqa.member.dto.NoteResponse
import com.liyaqa.member.dto.TimelineEvent
import com.liyaqa.member.dto.TimelineResponse
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.payment.PaymentRepository
import com.liyaqa.pt.PTPackageRepository
import com.liyaqa.rbac.UserRoleRepository
import com.liyaqa.role.RoleRepository
import com.liyaqa.trainer.TrainerRepository
import com.liyaqa.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Service
@Transactional(readOnly = true)
class MemberNoteService(
    private val memberNoteRepository: MemberNoteRepository,
    private val memberRepository: MemberRepository,
    private val userRepository: UserRepository,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
    private val paymentRepository: PaymentRepository,
    private val membershipRepository: MembershipRepository,
    private val trainerRepository: TrainerRepository,
    private val ptPackageRepository: PTPackageRepository,
    private val gxClassInstanceRepository: GXClassInstanceRepository,
    private val gxBookingRepository: GXBookingRepository,
    private val userRoleRepository: UserRoleRepository,
    private val roleRepository: RoleRepository,
    private val auditService: AuditService,
) {
    companion object {
        private val RIYADH_ZONE = ZoneId.of("Asia/Riyadh")
        private val MANAGER_ROLE_NAMES = setOf("Owner", "Branch Manager")
        private val TRAINER_ALLOWED_TYPES = setOf(MemberNoteType.GENERAL, MemberNoteType.HEALTH)
    }

    @Transactional
    fun createNote(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
        request: CreateNoteRequest,
        actorUserPublicId: UUID,
        isTrainerScope: Boolean,
    ): NoteResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)
        val actorUser =
            userRepository.findByPublicIdAndDeletedAtIsNull(actorUserPublicId)
                .orElseThrow { ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "User not found.") }

        // Parse note type
        val noteType =
            try {
                MemberNoteType.valueOf(request.noteType.uppercase())
            } catch (_: IllegalArgumentException) {
                throw ArenaException(HttpStatus.BAD_REQUEST, "validation-failed", "Invalid note type: ${request.noteType}")
            }

        // Rule 1 — trainer type restriction
        if (isTrainerScope && noteType !in TRAINER_ALLOWED_TYPES) {
            throw ArenaException(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "Trainers can only create general and health notes.",
            )
        }

        // Rule 2 — content required (Bean Validation handles @NotBlank, but enforce length)
        if (request.content.isBlank()) {
            throw ArenaException(HttpStatus.BAD_REQUEST, "validation-failed", "Content must not be empty.")
        }
        if (request.content.length > 1000) {
            throw ArenaException(HttpStatus.BAD_REQUEST, "validation-failed", "Content must not exceed 1000 characters.")
        }

        // Rule 3 — followUpAt guard
        val followUpAt = request.followUpAt?.let { parseFollowUpAt(it) }
        if (followUpAt != null && noteType != MemberNoteType.FOLLOW_UP) {
            throw ArenaException(
                HttpStatus.BAD_REQUEST,
                "validation-failed",
                "Follow-up date is only accepted on follow_up type notes.",
            )
        }
        if (followUpAt != null && followUpAt.isBefore(Instant.now())) {
            throw ArenaException(
                HttpStatus.BAD_REQUEST,
                "validation-failed",
                "Follow-up date must be in the future.",
            )
        }

        val note =
            memberNoteRepository.save(
                MemberNote(
                    organizationId = org.id,
                    clubId = club.id,
                    memberId = member.id,
                    createdByUserId = actorUser.id,
                    noteType = noteType,
                    content = request.content,
                    followUpAt = followUpAt,
                ),
            )

        auditService.logFromContext(
            AuditAction.MEMBER_NOTE_ADDED,
            "MemberNote",
            note.publicId.toString(),
        )

        return toNoteResponse(note, actorUser.email)
    }

    @Transactional
    fun deleteNote(
        notePublicId: UUID,
        actorUserPublicId: UUID,
    ) {
        val note =
            memberNoteRepository.findByPublicId(notePublicId)
                ?: throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Note not found.")

        // Rule 6 — REJECTION notes cannot be deleted
        if (note.noteType == MemberNoteType.REJECTION) {
            throw ArenaException(
                HttpStatus.CONFLICT,
                "conflict",
                "Rejection notes cannot be deleted.",
            )
        }

        val actorUser =
            userRepository.findByPublicIdAndDeletedAtIsNull(actorUserPublicId)
                .orElseThrow { ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "User not found.") }

        // Rule 5 — author or manager only
        val isAuthor = note.createdByUserId == actorUser.id
        if (!isAuthor) {
            val isManager = checkIsManager(actorUser.id)
            if (!isManager) {
                throw ArenaException(
                    HttpStatus.FORBIDDEN,
                    "forbidden",
                    "Only the note author or a manager can delete this note.",
                )
            }
        }

        // Rule 4 — soft delete only
        note.softDelete()
        memberNoteRepository.save(note)

        auditService.logFromContext(
            AuditAction.MEMBER_NOTE_DELETED,
            "MemberNote",
            note.publicId.toString(),
        )
    }

    fun getTimeline(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
        actorUserPublicId: UUID,
        cursor: String?,
        limit: Int = 20,
    ): TimelineResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)
        val actorUser =
            userRepository.findByPublicIdAndDeletedAtIsNull(actorUserPublicId)
                .orElseThrow { ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "User not found.") }

        val isManager = checkIsManager(actorUser.id)

        // Fetch from 3 sources with generous limits to allow post-cursor filtering
        val fetchLimit = limit + 50

        val noteEvents =
            memberNoteRepository.findByMemberId(member.id, fetchLimit, 0)
                .map { note ->
                    val creatorName = resolveUserName(note.createdByUserId)
                    val canDelete =
                        (note.createdByUserId == actorUser.id || isManager) &&
                            note.noteType != MemberNoteType.REJECTION
                    TimelineEvent.NoteEvent(
                        eventAt = note.createdAt,
                        eventType = "NOTE_${note.noteType.name}",
                        noteId = note.publicId,
                        content = note.content,
                        noteType = note.noteType.name,
                        followUpAt = note.followUpAt,
                        createdByName = creatorName,
                        canDelete = canDelete,
                    )
                }

        val membershipEvents =
            membershipRepository.findByMemberIdForTimeline(member.id, fetchLimit)
                .map { ms ->
                    val eventType = mapMembershipStatus(ms.getStatus())
                    TimelineEvent.MembershipEvent(
                        eventAt = ms.getCreatedAt(),
                        eventType = eventType,
                        membershipId = ms.getPublicId(),
                        planName = ms.getPlanNameEn(),
                        detail = buildMembershipDetail(eventType, ms.getPlanNameEn(), ms.getStartDate(), ms.getEndDate()),
                    )
                }

        val paymentEvents =
            paymentRepository.findByMemberIdForTimeline(member.id, fetchLimit)
                .map { p ->
                    TimelineEvent.PaymentEvent(
                        eventAt = p.getPaidAt(),
                        eventType = "PAYMENT_COLLECTED",
                        paymentId = p.getPublicId(),
                        amountSar = "%.2f".format(p.getAmountHalalas() / 100.0),
                        method = p.getPaymentMethod(),
                    )
                }

        // Merge and sort newest first
        val allEvents =
            (noteEvents + membershipEvents + paymentEvents)
                .sortedByDescending { it.eventAt }

        // Apply cursor-based pagination
        val filtered =
            if (cursor != null) {
                val parts = cursor.split("_", limit = 2)
                val cursorInstant = Instant.parse(parts[0])
                val cursorId = parts.getOrNull(1)?.toLongOrNull() ?: 0
                allEvents.filter { event ->
                    event.eventAt < cursorInstant ||
                        (event.eventAt == cursorInstant && getEventId(event) < cursorId)
                }
            } else {
                allEvents
            }

        val page = filtered.take(limit)
        val nextCursor =
            if (page.size == limit && filtered.size > limit) {
                val last = page.last()
                "${last.eventAt}_${getEventId(last)}"
            } else {
                null
            }

        return TimelineResponse(events = page, nextCursor = nextCursor)
    }

    fun getFollowUps(
        orgPublicId: UUID,
        clubPublicId: UUID,
    ): FollowUpResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)

        val now = Instant.now()
        val sevenDaysLater = now.plus(Duration.ofDays(7))
        val notes = memberNoteRepository.findFollowUpsDueWithin(club.id, now, sevenDaysLater)

        val today = LocalDate.now(RIYADH_ZONE)

        val items =
            notes.map { note ->
                val member = memberRepository.findById(note.memberId).orElse(null) ?: return@map null
                val creatorName = resolveUserName(note.createdByUserId)
                val followUpDate = note.followUpAt!!.atZone(RIYADH_ZONE).toLocalDate()
                val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, followUpDate)

                FollowUpItem(
                    noteId = note.publicId,
                    followUpAt = note.followUpAt,
                    content = note.content,
                    memberName = "${member.firstNameEn} ${member.lastNameEn}".trim(),
                    memberPublicId = member.publicId,
                    createdByName = creatorName,
                    daysUntilDue = daysUntil,
                )
            }.filterNotNull()

        return FollowUpResponse(followUps = items)
    }

    fun listNotesForTrainer(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
        trainerPublicId: UUID,
    ): List<NoteResponse> {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)

        // Rule 8 — trainer can only access notes for their own members
        verifyTrainerMemberAccess(trainerPublicId, member.id, org.id, club.id)

        val notes = memberNoteRepository.findByMemberId(member.id, 100, 0)
        return notes.map { note ->
            val creatorName = resolveUserName(note.createdByUserId)
            toNoteResponse(note, creatorName)
        }
    }

    @Transactional
    fun createNoteAsTrainer(
        orgPublicId: UUID,
        clubPublicId: UUID,
        memberPublicId: UUID,
        request: CreateNoteRequest,
        actorUserPublicId: UUID,
        trainerPublicId: UUID,
    ): NoteResponse {
        val org = findOrgOrThrow(orgPublicId)
        val club = findClubOrThrow(clubPublicId, org.id)
        val member = findMemberOrThrow(memberPublicId, org.id, club.id)

        // Rule 8 — trainer can only create notes for their own members
        verifyTrainerMemberAccess(trainerPublicId, member.id, org.id, club.id)

        return createNote(orgPublicId, clubPublicId, memberPublicId, request, actorUserPublicId, isTrainerScope = true)
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private fun verifyTrainerMemberAccess(
        trainerPublicId: UUID,
        memberId: Long,
        orgId: Long,
        clubId: Long,
    ) {
        val trainer =
            trainerRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(
                trainerPublicId,
                orgId,
                clubId,
            ).orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Trainer not found.") }

        // Check PTPackage: any package for this trainer and member
        val hasPtLink =
            ptPackageRepository.findAllByTrainerIdAndDeletedAtIsNull(trainer.id)
                .any { it.memberId == memberId }

        if (hasPtLink) return

        // Check GXBooking: any booking where the class is taught by this trainer
        val trainerInstances = gxClassInstanceRepository.findAllByInstructorIdAndDeletedAtIsNull(trainer.id)
        val hasGxLink =
            trainerInstances.any { instance ->
                gxBookingRepository.existsByInstanceIdAndMemberId(instance.id, memberId)
            }

        if (!hasGxLink) {
            throw ArenaException(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "You can only access notes for members you train.",
            )
        }
    }

    private fun checkIsManager(userId: Long): Boolean {
        val userRole = userRoleRepository.findByUserId(userId).orElse(null) ?: return false
        val role = roleRepository.findById(userRole.roleId).orElse(null) ?: return false
        return role.nameEn in MANAGER_ROLE_NAMES
    }

    private fun resolveUserName(userId: Long): String {
        val user = userRepository.findById(userId).orElse(null) ?: return "Unknown"
        return user.email.substringBefore("@")
    }

    private fun parseFollowUpAt(dateStr: String): Instant {
        return try {
            LocalDate.parse(dateStr).atStartOfDay(RIYADH_ZONE).toInstant()
        } catch (_: Exception) {
            try {
                Instant.parse(dateStr)
            } catch (_: Exception) {
                throw ArenaException(
                    HttpStatus.BAD_REQUEST,
                    "validation-failed",
                    "Invalid follow-up date format. Use yyyy-MM-dd or ISO 8601.",
                )
            }
        }
    }

    private fun toNoteResponse(
        note: MemberNote,
        creatorName: String,
    ): NoteResponse =
        NoteResponse(
            noteId = note.publicId,
            noteType = note.noteType.name,
            content = note.content,
            followUpAt = note.followUpAt,
            createdByName = creatorName,
            createdAt = note.createdAt,
        )

    private fun mapMembershipStatus(status: String): String =
        when (status.lowercase()) {
            "active" -> "MEMBERSHIP_JOINED"
            "frozen" -> "MEMBERSHIP_FROZEN"
            "terminated" -> "MEMBERSHIP_TERMINATED"
            "expired" -> "MEMBERSHIP_RENEWED"
            else -> "MEMBERSHIP_JOINED"
        }

    private fun buildMembershipDetail(
        eventType: String,
        planName: String,
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate,
    ): String =
        when (eventType) {
            "MEMBERSHIP_JOINED" -> "Joined on $planName plan"
            "MEMBERSHIP_RENEWED" -> "Renewed $planName plan"
            "MEMBERSHIP_FROZEN" -> "Frozen until $endDate"
            "MEMBERSHIP_TERMINATED" -> "Terminated $planName plan"
            else -> "$planName membership"
        }

    private fun getEventId(event: TimelineEvent): Long =
        when (event) {
            is TimelineEvent.NoteEvent -> event.noteId.leastSignificantBits
            is TimelineEvent.MembershipEvent -> event.membershipId.leastSignificantBits
            is TimelineEvent.PaymentEvent -> event.paymentId.leastSignificantBits
        }

    private fun findOrgOrThrow(orgPublicId: UUID) =
        organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.") }

    private fun findClubOrThrow(
        clubPublicId: UUID,
        organizationId: Long,
    ) = clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, organizationId)
        .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.") }

    private fun findMemberOrThrow(
        memberPublicId: UUID,
        organizationId: Long,
        clubId: Long,
    ) = memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(memberPublicId, organizationId, clubId)
        .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Member not found.") }
}
