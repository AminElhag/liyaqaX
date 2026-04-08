package com.liyaqa.nexus

import com.liyaqa.audit.AuditLogRepository
import com.liyaqa.audit.dto.AuditLogResponse
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.dto.toPageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneOffset

@RestController
@RequestMapping("/api/v1/nexus/audit")
@Tag(name = "Audit Log (Nexus)", description = "Platform audit log viewer")
class AuditNexusController(
    private val auditLogRepository: AuditLogRepository,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'audit:read')")
    @Operation(summary = "Get audit log entries")
    fun getAuditLog(
        @RequestParam(required = false) actorId: String?,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) entityType: String?,
        @RequestParam(required = false) organizationId: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @PageableDefault(size = 20) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<AuditLogResponse>> {
        authentication.nexusContext()

        val fromInstant = from?.atStartOfDay()?.toInstant(ZoneOffset.UTC)
        val toInstant = to?.plusDays(1)?.atStartOfDay()?.toInstant(ZoneOffset.UTC)

        val page =
            auditLogRepository.findAllFiltered(
                actorId = actorId,
                action = action,
                entityType = entityType,
                organizationId = organizationId,
                fromDate = fromInstant,
                toDate = toInstant,
                pageable = pageable,
            )

        val response =
            page.map { auditLog ->
                AuditLogResponse(
                    id = auditLog.publicId,
                    actorId = auditLog.actorId,
                    actorScope = auditLog.actorScope,
                    action = auditLog.action,
                    entityType = auditLog.entityType,
                    entityId = auditLog.entityId,
                    organizationId = auditLog.organizationId,
                    clubId = auditLog.clubId,
                    changesJson = auditLog.changesJson,
                    ipAddress = auditLog.ipAddress,
                    createdAt = auditLog.createdAt,
                )
            }.toPageResponse()

        return ResponseEntity.ok(response)
    }
}
