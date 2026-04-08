package com.liyaqa.member

import com.liyaqa.common.exception.ArenaException
import com.liyaqa.member.dto.MemberImportAcceptedResponse
import com.liyaqa.member.dto.MemberImportJobResponse
import com.liyaqa.nexus.nexusContext
import com.liyaqa.user.UserRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/nexus")
@Tag(name = "Member Import (Nexus)", description = "Bulk CSV member import for platform staff")
class MemberImportNexusController(
    private val memberImportService: MemberImportService,
    private val rollbackService: MemberImportRollbackService,
    private val userRepository: UserRepository,
) {
    @PostMapping("/clubs/{clubPublicId}/members/import")
    @PreAuthorize("hasPermission(null, 'member:import')")
    @Operation(summary = "Upload CSV and create import job")
    fun uploadCsv(
        @PathVariable clubPublicId: UUID,
        @RequestParam("file") file: MultipartFile,
        authentication: Authentication,
    ): ResponseEntity<MemberImportAcceptedResponse> {
        val claims = authentication.nexusContext()
        val user =
            userRepository.findByPublicIdAndDeletedAtIsNull(claims.userPublicId!!)
                .orElseThrow { ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "User not found.") }

        val response =
            memberImportService.importMembers(
                clubPublicId = clubPublicId,
                file = file,
                actorUserId = user.id,
                actorPublicId = claims.userPublicId.toString(),
                actorScope = claims.scope ?: "platform",
            )
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
    }

    @GetMapping("/member-import-jobs/{jobPublicId}")
    @PreAuthorize("hasPermission(null, 'member:import')")
    @Operation(summary = "Get import job status and result counts")
    fun getJob(
        @PathVariable jobPublicId: UUID,
        authentication: Authentication,
    ): ResponseEntity<MemberImportJobResponse> {
        authentication.nexusContext()
        return ResponseEntity.ok(memberImportService.getJob(jobPublicId))
    }

    @DeleteMapping("/member-import-jobs/{jobPublicId}")
    @PreAuthorize("hasPermission(null, 'member:import')")
    @Operation(summary = "Cancel a queued import job")
    fun cancelJob(
        @PathVariable jobPublicId: UUID,
        authentication: Authentication,
    ): ResponseEntity<Map<String, String>> {
        val claims = authentication.nexusContext()
        memberImportService.cancelJob(
            jobPublicId = jobPublicId,
            actorPublicId = claims.userPublicId.toString(),
            actorScope = claims.scope ?: "platform",
        )
        return ResponseEntity.ok(mapOf("message" to "Job cancelled."))
    }

    @PostMapping("/member-import-jobs/{jobPublicId}/rollback")
    @PreAuthorize("hasPermission(null, 'member:import')")
    @Operation(summary = "Rollback a completed import — soft-delete all imported members")
    fun rollbackJob(
        @PathVariable jobPublicId: UUID,
        authentication: Authentication,
    ): ResponseEntity<Map<String, String>> {
        val claims = authentication.nexusContext()
        val deletedCount =
            rollbackService.rollback(
                jobPublicId = jobPublicId,
                actorPublicId = claims.userPublicId.toString(),
                actorScope = claims.scope ?: "platform",
            )
        return ResponseEntity.ok(mapOf("message" to "Rollback complete. $deletedCount members soft-deleted."))
    }
}
