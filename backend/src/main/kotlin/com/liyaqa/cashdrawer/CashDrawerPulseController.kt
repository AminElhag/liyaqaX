package com.liyaqa.cashdrawer

import com.liyaqa.cashdrawer.dto.CashDrawerEntryResponse
import com.liyaqa.cashdrawer.dto.CashDrawerSessionResponse
import com.liyaqa.cashdrawer.dto.CashDrawerSessionSummaryResponse
import com.liyaqa.cashdrawer.dto.CloseSessionRequest
import com.liyaqa.cashdrawer.dto.CreateEntryRequest
import com.liyaqa.cashdrawer.dto.OpenSessionRequest
import com.liyaqa.cashdrawer.dto.ReconcileSessionRequest
import com.liyaqa.common.dto.PageResponse
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.security.JwtClaims
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/cash-drawer/sessions")
@Tag(name = "Cash Drawer (Pulse)", description = "Cash drawer session management — club operations")
@Validated
class CashDrawerPulseController(
    private val cashDrawerSessionService: CashDrawerSessionService,
) {
    @PostMapping
    @PreAuthorize("hasPermission(null, 'cash-drawer:open')")
    @Operation(summary = "Open a new cash drawer session")
    fun openSession(
        @RequestParam branchId: UUID,
        @Valid @RequestBody request: OpenSessionRequest,
        authentication: Authentication,
    ): ResponseEntity<CashDrawerSessionResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            cashDrawerSessionService.openSession(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                userPublicId = claims.requireUserPublicId(),
                branchPublicId = branchId,
                request = request,
            ),
        )
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'cash-drawer:read')")
    @Operation(summary = "List cash drawer sessions")
    fun listSessions(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) branchId: UUID?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "openedAt") sort: String,
        @RequestParam(defaultValue = "desc") order: String,
        authentication: Authentication,
    ): ResponseEntity<PageResponse<CashDrawerSessionSummaryResponse>> {
        val claims = authentication.pulseContext()
        val direction = if (order.equals("asc", ignoreCase = true)) Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(page, size.coerceAtMost(100), Sort.by(direction, sort))

        return ResponseEntity.ok(
            cashDrawerSessionService.listSessions(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                branchPublicId = branchId,
                status = status,
                pageable = pageable,
            ),
        )
    }

    @GetMapping("/current")
    @PreAuthorize("hasPermission(null, 'cash-drawer:read')")
    @Operation(summary = "Get the current open session for a branch")
    fun getCurrentSession(
        @RequestParam branchId: UUID,
        authentication: Authentication,
    ): ResponseEntity<CashDrawerSessionResponse> {
        val claims = authentication.pulseContext()
        val session =
            cashDrawerSessionService.getCurrentSession(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                branchPublicId = branchId,
            ) ?: return ResponseEntity.noContent().build()

        return ResponseEntity.ok(session)
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'cash-drawer:read')")
    @Operation(summary = "Get a cash drawer session by ID")
    fun getSession(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<CashDrawerSessionResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            cashDrawerSessionService.getSession(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                sessionPublicId = id,
            ),
        )
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasPermission(null, 'cash-drawer:close')")
    @Operation(summary = "Close a cash drawer session")
    fun closeSession(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CloseSessionRequest,
        authentication: Authentication,
    ): ResponseEntity<CashDrawerSessionResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            cashDrawerSessionService.closeSession(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                sessionPublicId = id,
                userPublicId = claims.requireUserPublicId(),
                request = request,
            ),
        )
    }

    @PatchMapping("/{id}/reconcile")
    @PreAuthorize("hasPermission(null, 'cash-drawer:reconcile')")
    @Operation(summary = "Reconcile a closed cash drawer session")
    fun reconcileSession(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ReconcileSessionRequest,
        authentication: Authentication,
    ): ResponseEntity<CashDrawerSessionResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            cashDrawerSessionService.reconcileSession(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                sessionPublicId = id,
                userPublicId = claims.requireUserPublicId(),
                request = request,
            ),
        )
    }

    @PostMapping("/{id}/entries")
    @PreAuthorize("hasPermission(null, 'cash-drawer:entry:create')")
    @Operation(summary = "Add an entry to an open cash drawer session")
    fun addEntry(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateEntryRequest,
        authentication: Authentication,
    ): ResponseEntity<CashDrawerEntryResponse> {
        val claims = authentication.pulseContext()
        return ResponseEntity.status(HttpStatus.CREATED).body(
            cashDrawerSessionService.addEntry(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                sessionPublicId = id,
                userPublicId = claims.requireUserPublicId(),
                request = request,
            ),
        )
    }

    @GetMapping("/{id}/entries")
    @PreAuthorize("hasPermission(null, 'cash-drawer:read')")
    @Operation(summary = "List entries for a cash drawer session")
    fun listEntries(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<List<CashDrawerEntryResponse>> {
        val claims = authentication.pulseContext()
        return ResponseEntity.ok(
            cashDrawerSessionService.listEntries(
                orgPublicId = claims.requireOrganizationId(),
                clubPublicId = claims.requireClubId(),
                sessionPublicId = id,
            ),
        )
    }
}

// ── Auth helpers ─────────────────────────────────────────────────────────────

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
        ?: throw ArenaException(HttpStatus.UNAUTHORIZED, "unauthorized", "User identity missing from token.")
