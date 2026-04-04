package com.liyaqa.gx

import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.gx.dto.CreateGXClassInstanceRequest
import com.liyaqa.gx.dto.CreateGXClassTypeRequest
import com.liyaqa.gx.dto.GXClassInstanceResponse
import com.liyaqa.gx.dto.GXClassTypeResponse
import com.liyaqa.security.JwtClaims
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/gx")
@Tag(name = "GX Classes (Pulse)", description = "GX class type and instance management — club operations")
@Validated
class GXClassPulseController(
    private val gxClassService: GXClassService,
) {
    // ── Class Type endpoints ───────────────────────────────────────────────

    @PostMapping("/class-types")
    @PreAuthorize("hasPermission(null, 'gx-class:create')")
    @Operation(summary = "Create a GX class type")
    fun createClassType(
        @Valid @RequestBody request: CreateGXClassTypeRequest,
        authentication: Authentication,
    ): ResponseEntity<GXClassTypeResponse> {
        val claims = authentication.pulseContext()
        val response =
            gxClassService.createClassType(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                request = request,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/class-types")
    @PreAuthorize("hasPermission(null, 'gx-class:read')")
    @Operation(summary = "List all GX class types")
    fun listClassTypes(
        @PageableDefault(size = 50) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<GXClassTypeResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            gxClassService.listClassTypes(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                pageable = pageable,
            ),
        )
    }

    @GetMapping("/class-types/{id}")
    @PreAuthorize("hasPermission(null, 'gx-class:read')")
    @Operation(summary = "Get a GX class type by ID")
    fun getClassType(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<GXClassTypeResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            gxClassService.getClassType(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                classTypePublicId = id,
            ),
        )
    }

    @PatchMapping("/class-types/{id}")
    @PreAuthorize("hasPermission(null, 'gx-class:update')")
    @Operation(summary = "Update a GX class type")
    fun updateClassType(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateGXClassTypeRequest,
        authentication: Authentication,
    ): ResponseEntity<GXClassTypeResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            gxClassService.updateClassType(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                classTypePublicId = id,
                request = request,
            ),
        )
    }

    @DeleteMapping("/class-types/{id}")
    @PreAuthorize("hasPermission(null, 'gx-class:update')")
    @Operation(summary = "Soft-delete a GX class type")
    fun deleteClassType(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val claims = authentication.pulseContext()
        gxClassService.deleteClassType(
            orgPublicId = claims.requireOrganizationId(),
            clubPublicId = claims.requireClubId(),
            classTypePublicId = id,
        )
        return ResponseEntity.noContent().build()
    }

    // ── Class Instance endpoints ───────────────────────────────────────────

    @PostMapping("/classes")
    @PreAuthorize("hasPermission(null, 'gx-class:create')")
    @Operation(summary = "Schedule a GX class instance")
    fun createClassInstance(
        @Valid @RequestBody request: CreateGXClassInstanceRequest,
        @RequestParam branchId: UUID,
        authentication: Authentication,
    ): ResponseEntity<GXClassInstanceResponse> {
        val claims = authentication.pulseContext()
        val response =
            gxClassService.createClassInstance(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                branchPublicId = branchId,
                request = request,
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping("/classes")
    @PreAuthorize("hasPermission(null, 'gx-class:read')")
    @Operation(summary = "List GX class instances for a branch")
    fun listClassInstances(
        @RequestParam branchId: UUID,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @PageableDefault(size = 50) pageable: Pageable,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<GXClassInstanceResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            gxClassService.listClassInstances(
                orgPublicId = claims.requireOrganizationId(),
                branchPublicId = branchId,
                from = from,
                to = to,
                pageable = pageable,
            ),
        )
    }

    @GetMapping("/classes/{id}")
    @PreAuthorize("hasPermission(null, 'gx-class:read')")
    @Operation(summary = "Get a GX class instance by ID")
    fun getClassInstance(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<GXClassInstanceResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            gxClassService.getClassInstance(
                orgPublicId = claims.requireOrganizationId(),
                instancePublicId = id,
            ),
        )
    }

    @PatchMapping("/classes/{id}")
    @PreAuthorize("hasPermission(null, 'gx-class:update')")
    @Operation(summary = "Update a GX class instance")
    fun updateClassInstance(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateGXClassInstanceRequest,
        authentication: Authentication,
    ): ResponseEntity<GXClassInstanceResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            gxClassService.updateClassInstance(
                orgPublicId = claims.requireOrganizationId(),
                instancePublicId = id,
                request = request,
            ),
        )
    }

    @PostMapping("/classes/{id}/cancel")
    @PreAuthorize("hasPermission(null, 'gx-class:update')")
    @Operation(summary = "Cancel a GX class instance and all its bookings")
    fun cancelClassInstance(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<GXClassInstanceResponse> {
        val claims = authentication.pulseContext()
        val response =
            gxClassService.cancelClassInstance(
                orgPublicId = claims.requireOrganizationId(),
                instancePublicId = id,
            )
        return ResponseEntity.ok(response)
    }
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
