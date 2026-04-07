package com.liyaqa.report.schedule

import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.organization.OrganizationRepository
import com.liyaqa.report.schedule.dto.CreateReportScheduleRequest
import com.liyaqa.report.schedule.dto.ReportScheduleResponse
import com.liyaqa.report.schedule.dto.UpdateReportScheduleRequest
import com.liyaqa.security.JwtClaims
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
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
@RequestMapping("/api/v1/report-templates/{templateId}/schedule")
@Tag(name = "Report Schedule (Pulse)", description = "Scheduled report email delivery management")
@Validated
class ReportSchedulePulseController(
    private val reportScheduleService: ReportScheduleService,
    private val organizationRepository: OrganizationRepository,
    private val clubRepository: ClubRepository,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'report:custom:run')")
    @Operation(summary = "Get the schedule for a report template")
    fun getSchedule(
        @PathVariable templateId: UUID,
        authentication: Authentication,
    ): ResponseEntity<ReportScheduleResponse> {
        val clubId = resolveClubId(authentication)
        return ResponseEntity.ok(reportScheduleService.getSchedule(templateId, clubId))
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'report:custom:run')")
    @Operation(summary = "Create a schedule for a report template")
    fun createSchedule(
        @PathVariable templateId: UUID,
        @Valid @RequestBody request: CreateReportScheduleRequest,
        authentication: Authentication,
    ): ResponseEntity<ReportScheduleResponse> {
        val clubId = resolveClubId(authentication)
        val response = reportScheduleService.createSchedule(templateId, request, clubId)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PatchMapping
    @PreAuthorize("hasPermission(null, 'report:custom:run')")
    @Operation(summary = "Update a schedule (frequency, recipients, pause/resume)")
    fun updateSchedule(
        @PathVariable templateId: UUID,
        @Valid @RequestBody request: UpdateReportScheduleRequest,
        authentication: Authentication,
    ): ResponseEntity<ReportScheduleResponse> {
        val clubId = resolveClubId(authentication)
        return ResponseEntity.ok(reportScheduleService.updateSchedule(templateId, request, clubId))
    }

    @DeleteMapping
    @PreAuthorize("hasPermission(null, 'report:custom:run')")
    @Operation(summary = "Delete a schedule")
    fun deleteSchedule(
        @PathVariable templateId: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val clubId = resolveClubId(authentication)
        reportScheduleService.deleteSchedule(templateId, clubId)
        return ResponseEntity.noContent().build()
    }

    private fun resolveClubId(authentication: Authentication): Long {
        val claims =
            authentication.details as? JwtClaims
                ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.")

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
