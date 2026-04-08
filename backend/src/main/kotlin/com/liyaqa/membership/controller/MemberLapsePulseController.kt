package com.liyaqa.membership.controller

import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.MemberNoteRepository
import com.liyaqa.member.MemberNoteService
import com.liyaqa.member.MemberNoteType
import com.liyaqa.member.MemberRepository
import com.liyaqa.member.dto.CreateNoteRequest
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.membership.dto.BulkRenewalOfferRequest
import com.liyaqa.membership.dto.BulkRenewalOfferResponse
import com.liyaqa.membership.dto.LapsedMemberResponse
import com.liyaqa.membership.dto.LapsedMembersPageResponse
import com.liyaqa.membership.dto.RenewalOfferResponse
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.security.JwtClaims
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@RestController
@RequestMapping("/api/v1/pulse")
@Tag(name = "Lapsed Members", description = "Lapsed member recovery for club staff")
@Validated
class MemberLapsePulseController(
    private val memberRepository: MemberRepository,
    private val membershipRepository: MembershipRepository,
    private val membershipPlanRepository: MembershipPlanRepository,
    private val memberNoteService: MemberNoteService,
    private val memberNoteRepository: MemberNoteRepository,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
) {
    companion object {
        private val RIYADH_ZONE = ZoneId.of("Asia/Riyadh")
        private const val MAX_BULK_SIZE = 100
        private const val RENEWAL_OFFER_CONTENT = "Renewal offer sent"
    }

    @GetMapping("/memberships/lapsed")
    @PreAuthorize("hasPermission(null, 'membership:read')")
    @Operation(summary = "Get paginated list of lapsed members for this club")
    fun getLapsedMembers(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        authentication: Authentication,
    ): ResponseEntity<LapsedMembersPageResponse> {
        val claims = authentication.pulseContext()
        val club = resolveClub(claims)

        val total = memberRepository.countLapsedByClub(club.id)
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        val members = memberRepository.findLapsedByClub(club.id, pageSize, offset)

        val today = LocalDate.now(RIYADH_ZONE)
        val items =
            members.map { member ->
                val lastMembership =
                    membershipRepository.findByMemberIdAndMembershipStatusAndDeletedAtIsNull(member.id, "lapsed")
                        .orElse(null)
                val plan = lastMembership?.let { membershipPlanRepository.findById(it.planId).orElse(null) }
                val expiredOn = lastMembership?.endDate ?: today
                val daysSinceLapse = ChronoUnit.DAYS.between(expiredOn, today).coerceAtLeast(0)
                val hasFollowUp = hasRecentFollowUp(member.id, 7)

                LapsedMemberResponse(
                    memberPublicId = member.publicId,
                    nameAr = "${member.firstNameAr} ${member.lastNameAr}".trim(),
                    nameEn = "${member.firstNameEn} ${member.lastNameEn}".trim(),
                    phone = member.phone,
                    lastMembershipPlan = plan?.nameEn ?: "",
                    expiredOn = expiredOn,
                    daysSinceLapse = daysSinceLapse,
                    hasOpenFollowUp = hasFollowUp,
                )
            }

        return ResponseEntity.ok(
            LapsedMembersPageResponse(
                total = total,
                page = page,
                pageSize = pageSize,
                members = items,
            ),
        )
    }

    @PostMapping("/members/{memberPublicId}/renewal-offer")
    @PreAuthorize("hasPermission(null, 'member-note:create')")
    @Operation(summary = "Send renewal offer to a lapsed member")
    fun sendRenewalOffer(
        @PathVariable memberPublicId: UUID,
        authentication: Authentication,
    ): ResponseEntity<RenewalOfferResponse> {
        val claims = authentication.pulseContext()
        val org = resolveOrg(claims)
        val club = resolveClub(claims)
        val member =
            memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(memberPublicId, org.id, club.id)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Member not found.") }

        // Dedup: skip if follow_up note with "Renewal offer sent" in last 24h
        if (hasRecentRenewalOffer(member.id)) {
            val followUpAt = Instant.now().plus(Duration.ofDays(3))
            return ResponseEntity.status(HttpStatus.CREATED).body(
                RenewalOfferResponse(
                    noteId = UUID.randomUUID(),
                    followUpAt = followUpAt,
                    message = "Renewal offer already sent recently for ${member.firstNameEn} ${member.lastNameEn}.",
                ),
            )
        }

        val followUpDate = LocalDate.now(RIYADH_ZONE).plusDays(3)
        val noteResponse =
            memberNoteService.createNote(
                orgPublicId = org.publicId,
                clubPublicId = club.publicId,
                memberPublicId = member.publicId,
                request =
                    CreateNoteRequest(
                        noteType = MemberNoteType.FOLLOW_UP.name,
                        content = RENEWAL_OFFER_CONTENT,
                        followUpAt = followUpDate.toString(),
                    ),
                actorUserPublicId = claims.requireUserPublicId(),
                isTrainerScope = false,
            )

        val memberName = "${member.firstNameEn} ${member.lastNameEn}".trim()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            RenewalOfferResponse(
                noteId = noteResponse.noteId,
                followUpAt = noteResponse.followUpAt ?: Instant.now().plus(Duration.ofDays(3)),
                message = "Renewal offer follow-up created for $memberName. Due in 3 days.",
            ),
        )
    }

    @PostMapping("/memberships/lapsed/renewal-offer-bulk")
    @PreAuthorize("hasPermission(null, 'member-note:create')")
    @Operation(summary = "Bulk send renewal offers to lapsed members")
    fun sendBulkRenewalOffers(
        @Valid @RequestBody request: BulkRenewalOfferRequest,
        authentication: Authentication,
    ): ResponseEntity<BulkRenewalOfferResponse> {
        if (request.memberPublicIds.size > MAX_BULK_SIZE) {
            throw ArenaException(
                HttpStatus.BAD_REQUEST,
                "validation-failed",
                "Maximum $MAX_BULK_SIZE members per bulk request. Received: ${request.memberPublicIds.size}.",
            )
        }

        val claims = authentication.pulseContext()
        val org = resolveOrg(claims)
        val club = resolveClub(claims)

        var created = 0
        var skipped = 0
        val followUpDate = LocalDate.now(RIYADH_ZONE).plusDays(3)

        for (memberPublicId in request.memberPublicIds) {
            val member =
                memberRepository.findByPublicIdAndOrganizationIdAndClubIdAndDeletedAtIsNull(memberPublicId, org.id, club.id)
                    .orElse(null) ?: continue

            if (hasRecentRenewalOffer(member.id)) {
                skipped++
                continue
            }

            try {
                memberNoteService.createNote(
                    orgPublicId = org.publicId,
                    clubPublicId = club.publicId,
                    memberPublicId = member.publicId,
                    request =
                        CreateNoteRequest(
                            noteType = MemberNoteType.FOLLOW_UP.name,
                            content = RENEWAL_OFFER_CONTENT,
                            followUpAt = followUpDate.toString(),
                        ),
                    actorUserPublicId = claims.requireUserPublicId(),
                    isTrainerScope = false,
                )
                created++
            } catch (_: Exception) {
                skipped++
            }
        }

        return ResponseEntity.ok(BulkRenewalOfferResponse(created = created, skipped = skipped))
    }

    private fun hasRecentFollowUp(
        memberId: Long,
        withinDays: Long,
    ): Boolean {
        val cutoff = Instant.now().minus(Duration.ofDays(withinDays))
        val notes = memberNoteRepository.findByMemberId(memberId, 50, 0)
        return notes.any { note ->
            note.noteType == MemberNoteType.FOLLOW_UP &&
                note.createdAt.isAfter(cutoff)
        }
    }

    private fun hasRecentRenewalOffer(memberId: Long): Boolean {
        val cutoff = Instant.now().minus(Duration.ofHours(24))
        val notes = memberNoteRepository.findByMemberId(memberId, 50, 0)
        return notes.any { note ->
            note.noteType == MemberNoteType.FOLLOW_UP &&
                note.content.startsWith(RENEWAL_OFFER_CONTENT) &&
                note.createdAt.isAfter(cutoff)
        }
    }

    private fun resolveOrg(claims: JwtClaims) =
        organizationRepository.findByPublicIdAndDeletedAtIsNull(claims.requireOrganizationId())
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.") }

    private fun resolveClub(claims: JwtClaims) =
        clubRepository.findByPublicIdAndDeletedAtIsNull(claims.requireClubId())
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.") }
}

// ── Auth helpers ──────────────────────────────────────────────────────────────

private fun Authentication.pulseContext(): JwtClaims =
    details as? JwtClaims
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")

private fun JwtClaims.requireOrganizationId(): UUID =
    organizationId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No organization scope in token.")

private fun JwtClaims.requireClubId(): UUID =
    clubId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")

private fun JwtClaims.requireUserPublicId(): UUID =
    userPublicId
        ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No user identity in token.")
