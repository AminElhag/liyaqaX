package com.liyaqa.arena

import com.liyaqa.arena.dto.PtPackageArenaResponse
import com.liyaqa.arena.dto.PtSessionArenaResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.Member
import com.liyaqa.member.MemberRepository
import com.liyaqa.portal.ClubPortalSettingsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/arena/pt")
@Tag(name = "Arena PT", description = "Member PT session and package views")
@Validated
class PtArenaController(
    private val memberRepository: MemberRepository,
    private val portalSettingsService: ClubPortalSettingsService,
) {
    @GetMapping("/sessions")
    @Operation(summary = "Get member's PT sessions (upcoming and past)")
    fun getSessions(authentication: Authentication): ResponseEntity<List<PtSessionArenaResponse>> {
        val member = resolveMember(authentication)
        portalSettingsService.requireFeatureEnabled(member.clubId, "pt")
        // PT session entities not yet implemented — return empty list
        return ResponseEntity.ok(emptyList())
    }

    @GetMapping("/packages")
    @Operation(summary = "Get member's PT packages with session counts")
    fun getPackages(authentication: Authentication): ResponseEntity<List<PtPackageArenaResponse>> {
        val member = resolveMember(authentication)
        portalSettingsService.requireFeatureEnabled(member.clubId, "pt")
        // PT package entities not yet implemented — return empty list
        return ResponseEntity.ok(emptyList())
    }

    private fun resolveMember(authentication: Authentication): Member {
        val claims = authentication.arenaContext()
        val memberPublicId = claims.requireMemberId()
        return memberRepository.findAll().firstOrNull { it.publicId == memberPublicId && it.deletedAt == null }
            ?: throw ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Member not found.")
    }
}
