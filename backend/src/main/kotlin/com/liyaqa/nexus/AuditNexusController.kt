package com.liyaqa.nexus

import com.liyaqa.nexus.dto.AuditLogMeta
import com.liyaqa.nexus.dto.AuditLogPageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/nexus/audit")
@Tag(name = "Audit Log (Nexus)", description = "Platform audit log viewer")
class AuditNexusController {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'audit:read')")
    @Operation(summary = "Get audit log entries")
    fun getAuditLog(authentication: Authentication): ResponseEntity<AuditLogPageResponse> {
        authentication.nexusContext()
        return ResponseEntity.ok(
            AuditLogPageResponse(
                content = emptyList(),
                totalElements = 0,
                meta = AuditLogMeta(note = "Audit logging will be available in a future release"),
            ),
        )
    }
}
