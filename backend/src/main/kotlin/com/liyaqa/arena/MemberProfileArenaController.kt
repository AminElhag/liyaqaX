package com.liyaqa.arena

import com.liyaqa.arena.dto.ClubSummary
import com.liyaqa.arena.dto.MemberMeResponse
import com.liyaqa.arena.dto.MembershipSummary
import com.liyaqa.arena.dto.UpdateProfileRequest
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.membership.MembershipPlanRepository
import com.liyaqa.membership.MembershipRepository
import com.liyaqa.portal.ClubPortalSettingsService
import com.liyaqa.portal.dto.ClubPortalSettingsResponse
import com.liyaqa.user.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@RestController
@RequestMapping("/api/v1/arena")
@Tag(name = "Arena Profile", description = "Member profile and portal settings")
@Validated
class MemberProfileArenaController(
    private val memberRepository: MemberRepository,
    private val membershipRepository: MembershipRepository,
    private val membershipPlanRepository: MembershipPlanRepository,
    private val clubRepository: ClubRepository,
    private val userRepository: UserRepository,
    private val portalSettingsService: ClubPortalSettingsService,
) {
    @GetMapping("/me")
    @Operation(summary = "Get the authenticated member's profile and membership summary")
    fun getMe(authentication: Authentication): ResponseEntity<MemberMeResponse> {
        val member = resolveMember(authentication)
        val club =
            clubRepository.findById(member.clubId)
                .orElseThrow { internalError("Club not found.") }
        val user =
            userRepository.findById(member.userId)
                .orElse(null)

        val activeMembership =
            membershipRepository.findByMemberIdAndMembershipStatusInAndDeletedAtIsNull(
                member.id,
                listOf("active", "frozen", "expired"),
            ).orElse(null)

        val membershipSummary =
            activeMembership?.let { ms ->
                val plan =
                    membershipPlanRepository.findById(ms.planId).orElse(null)
                plan?.let {
                    MembershipSummary(
                        planName = it.nameEn,
                        planNameAr = it.nameAr,
                        status = ms.membershipStatus,
                        startDate = ms.startDate.toString(),
                        expiresAt = ms.endDate.toString(),
                        daysRemaining =
                            ChronoUnit.DAYS.between(LocalDate.now(), ms.endDate)
                                .coerceAtLeast(0),
                    )
                }
            }

        return ResponseEntity.ok(
            MemberMeResponse(
                id = member.publicId,
                firstName = member.firstNameEn,
                lastName = member.lastNameEn,
                firstNameAr = member.firstNameAr,
                lastNameAr = member.lastNameAr,
                phone = member.phone,
                email = user?.email,
                preferredLanguage = member.preferredLanguage,
                memberStatus = member.membershipStatus,
                club =
                    ClubSummary(
                        id = club.publicId,
                        name = club.nameEn,
                        nameAr = club.nameAr,
                    ),
                membership = membershipSummary,
            ),
        )
    }

    @PatchMapping("/profile")
    @Operation(summary = "Update member profile fields")
    fun updateProfile(
        @Valid @RequestBody request: UpdateProfileRequest,
        authentication: Authentication,
    ): ResponseEntity<MemberMeResponse> {
        val member = resolveMember(authentication)

        request.firstNameAr?.let { member.firstNameAr = it }
        request.lastNameAr?.let { member.lastNameAr = it }
        request.preferredLanguage?.let { member.preferredLanguage = it }

        memberRepository.save(member)

        return getMe(authentication)
    }

    @GetMapping("/portal-settings")
    @Operation(summary = "Get portal feature flags for the member's club")
    fun getPortalSettings(authentication: Authentication): ResponseEntity<ClubPortalSettingsResponse> {
        val member = resolveMember(authentication)
        return ResponseEntity.ok(portalSettingsService.getSettings(member.clubId))
    }

    private fun resolveMember(authentication: Authentication): Member {
        val claims = authentication.arenaContext()
        val memberPublicId = claims.requireMemberId()
        return memberRepository.findAll().firstOrNull { it.publicId == memberPublicId && it.deletedAt == null }
            ?: throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Member not found.")
    }

    private fun internalError(detail: String) = ArenaException(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", detail)
}
