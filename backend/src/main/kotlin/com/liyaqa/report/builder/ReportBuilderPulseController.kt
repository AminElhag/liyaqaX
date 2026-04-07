package com.liyaqa.report.builder

import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.rbac.PermissionService
import com.liyaqa.report.builder.dto.CreateReportTemplateRequest
import com.liyaqa.report.builder.dto.ReportResultResponse
import com.liyaqa.report.builder.dto.ReportTemplateResponse
import com.liyaqa.report.builder.dto.RunReportRequest
import com.liyaqa.report.builder.dto.UpdateReportTemplateRequest
import com.liyaqa.security.JwtClaims
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/report-templates")
@Tag(name = "Report Builder (Pulse)", description = "Custom report template CRUD and execution")
@Validated
class ReportBuilderPulseController(
    private val reportTemplateService: ReportTemplateService,
    private val reportBuilderService: ReportBuilderService,
    private val permissionService: PermissionService,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'report:custom:run')")
    @Operation(summary = "List report templates for the current club")
    fun listTemplates(authentication: Authentication): ResponseEntity<List<ReportTemplateResponse>> {
        val clubId = resolveClubId(authentication)
        return ResponseEntity.ok(reportTemplateService.listTemplates(clubId))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'report:custom:run')")
    @Operation(summary = "Get a report template by ID")
    fun getTemplate(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<ReportTemplateResponse> {
        val clubId = resolveClubId(authentication)
        return ResponseEntity.ok(reportTemplateService.getTemplate(id, clubId))
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'report:custom:run')")
    @Operation(summary = "Create a new report template")
    fun createTemplate(
        @Valid @RequestBody request: CreateReportTemplateRequest,
        authentication: Authentication,
    ): ResponseEntity<ReportTemplateResponse> {
        val clubId = resolveClubId(authentication)
        val response = reportTemplateService.createTemplate(request, clubId)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'report:custom:run')")
    @Operation(summary = "Update a report template")
    fun updateTemplate(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateReportTemplateRequest,
        authentication: Authentication,
    ): ResponseEntity<ReportTemplateResponse> {
        val clubId = resolveClubId(authentication)
        return ResponseEntity.ok(reportTemplateService.updateTemplate(id, request, clubId))
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'report:custom:run')")
    @Operation(summary = "Soft delete a report template")
    fun deleteTemplate(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val clubId = resolveClubId(authentication)
        reportTemplateService.deleteTemplate(id, clubId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/run")
    @Operation(summary = "Execute a report template and return results")
    fun runReport(
        @PathVariable id: UUID,
        @Valid @RequestBody request: RunReportRequest,
        authentication: Authentication,
    ): ResponseEntity<ReportResultResponse> {
        val claims = authentication.pulseContext()
        val clubId = resolveClubId(authentication)
        val template = reportTemplateService.findOrThrow(id, clubId)

        enforceMetricScopeGate(claims, template)

        val actorId = claims.userPublicId?.toString() ?: "unknown"
        return ResponseEntity.ok(reportBuilderService.runReport(template, request, clubId, actorId))
    }

    @GetMapping("/{id}/result")
    @Operation(summary = "Get the last cached result for a report template")
    fun getLastResult(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<ReportResultResponse> {
        val claims = authentication.pulseContext()
        val clubId = resolveClubId(authentication)
        val template = reportTemplateService.findOrThrow(id, clubId)

        enforceMetricScopeGate(claims, template)

        return ResponseEntity.ok(reportBuilderService.getLastResult(template))
    }

    @GetMapping("/{id}/export")
    @Operation(summary = "Download last result as CSV")
    fun exportCsv(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<ByteArray> {
        val claims = authentication.pulseContext()
        val clubId = resolveClubId(authentication)
        val template = reportTemplateService.findOrThrow(id, clubId)

        enforceMetricScopeGate(claims, template)

        val csv = reportBuilderService.exportCsv(template)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-${template.name}.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv)
    }

    private fun enforceMetricScopeGate(
        claims: JwtClaims,
        template: ReportTemplate,
    ) {
        val roleId =
            claims.roleId ?: throw ArenaException(
                HttpStatus.FORBIDDEN,
                "forbidden",
                "No role in token.",
            )

        val hasCustomRun = permissionService.hasPermission(roleId, "report:custom:run")
        if (hasCustomRun) return

        val hasLeadsView = permissionService.hasPermission(roleId, "report:leads:view")
        if (hasLeadsView && template.metricScope == "leads") return

        throw ArenaException(
            HttpStatus.FORBIDDEN,
            "forbidden",
            "You do not have permission to run this report.",
        )
    }

    private fun resolveClubId(authentication: Authentication): Long {
        val claims = authentication.pulseContext()
        val orgPublicId =
            claims.organizationId
                ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No organization scope in token.")
        val clubPublicId =
            claims.clubId
                ?: throw ArenaException(HttpStatus.FORBIDDEN, "forbidden", "No club scope in token.")

        val orgId =
            organizationRepository.findByPublicIdAndDeletedAtIsNull(orgPublicId)
                .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Organization not found.") }
                .id

        return clubRepository.findByPublicIdAndOrganizationIdAndDeletedAtIsNull(clubPublicId, orgId)
            .orElseThrow { ArenaException(HttpStatus.NOT_FOUND, "resource-not-found", "Club not found.") }
            .id
    }
}

private fun Authentication.pulseContext(): JwtClaims =
    details as? JwtClaims
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")
