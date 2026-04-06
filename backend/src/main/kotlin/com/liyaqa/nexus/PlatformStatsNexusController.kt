package com.liyaqa.nexus

import com.liyaqa.nexus.dto.PlatformStatsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/nexus/stats")
@Tag(name = "Platform Stats (Nexus)", description = "Platform-wide KPIs")
class PlatformStatsNexusController(
    private val platformStatsNexusService: PlatformStatsNexusService,
) {
    @GetMapping
    @PreAuthorize("hasPermission(null, 'platform:stats:view')")
    @Operation(summary = "Get platform-wide KPIs")
    fun getStats(authentication: Authentication): ResponseEntity<PlatformStatsResponse> {
        authentication.nexusContext()
        return ResponseEntity.ok(platformStatsNexusService.getStats())
    }
}
