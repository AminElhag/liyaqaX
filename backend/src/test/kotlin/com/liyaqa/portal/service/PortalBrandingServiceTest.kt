package com.liyaqa.portal.service

import com.liyaqa.audit.AuditAction
import com.liyaqa.audit.AuditService
import com.liyaqa.club.ClubRepository
import com.liyaqa.common.exception.ArenaException
import com.liyaqa.permission.PermissionConstants
import com.liyaqa.portal.ClubPortalSettings
import com.liyaqa.portal.ClubPortalSettingsRepository
import com.liyaqa.portal.ClubPortalSettingsService
import com.liyaqa.portal.dto.UpdatePortalSettingsRequest
import com.liyaqa.rbac.PermissionService
import com.liyaqa.security.JwtClaims
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PortalBrandingServiceTest {
    @Mock lateinit var settingsRepository: ClubPortalSettingsRepository

    @Mock lateinit var clubRepository: ClubRepository

    @Mock lateinit var auditService: AuditService

    @Mock lateinit var permissionService: PermissionService

    private lateinit var service: ClubPortalSettingsService

    private val testClubId = 1L
    private val ownerRoleId = UUID.randomUUID()
    private val branchManagerRoleId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        service =
            ClubPortalSettingsService(
                settingsRepository = settingsRepository,
                clubRepository = clubRepository,
                auditService = auditService,
                permissionService = permissionService,
            )
    }

    private fun setSecurityContext(roleId: UUID) {
        val claims =
            JwtClaims(
                userPublicId = UUID.randomUUID(),
                roleId = roleId,
                scope = "club",
                organizationId = UUID.randomUUID(),
                clubId = UUID.randomUUID(),
                branchIds = emptyList(),
            )
        val auth =
            UsernamePasswordAuthenticationToken("user", null, emptyList()).apply {
                details = claims
            }
        SecurityContextHolder.getContext().authentication = auth
    }

    private fun existingSettings(): ClubPortalSettings {
        val settings = ClubPortalSettings(clubId = testClubId)
        whenever(settingsRepository.findByClubId(testClubId)).thenReturn(Optional.of(settings))
        whenever(settingsRepository.save(any<ClubPortalSettings>())).thenAnswer { it.arguments[0] }
        return settings
    }

    @Test
    fun `update saves logoUrl when valid https URL provided`() {
        setSecurityContext(ownerRoleId)
        whenever(permissionService.hasPermission(ownerRoleId, PermissionConstants.BRANDING_UPDATE)).thenReturn(true)
        val settings = existingSettings()

        val request = UpdatePortalSettingsRequest(logoUrl = "https://cdn.example.com/logo.png")
        val result = service.updateSettings(testClubId, request)

        assertEquals("https://cdn.example.com/logo.png", settings.logoUrl)
        assertNotNull(result)
    }

    @Test
    fun `update throws 400 when logoUrl does not start with https`() {
        setSecurityContext(ownerRoleId)
        whenever(permissionService.hasPermission(ownerRoleId, PermissionConstants.BRANDING_UPDATE)).thenReturn(true)
        existingSettings()

        val request = UpdatePortalSettingsRequest(logoUrl = "http://cdn.example.com/logo.png")
        val ex =
            assertThrows<ArenaException> {
                service.updateSettings(testClubId, request)
            }
        assertEquals(400, ex.status.value())
    }

    @Test
    fun `update throws 400 when primaryColorHex is invalid format`() {
        setSecurityContext(ownerRoleId)
        whenever(permissionService.hasPermission(ownerRoleId, PermissionConstants.BRANDING_UPDATE)).thenReturn(true)
        existingSettings()

        val request = UpdatePortalSettingsRequest(primaryColorHex = "red")
        val ex =
            assertThrows<ArenaException> {
                service.updateSettings(testClubId, request)
            }
        assertEquals(400, ex.status.value())
    }

    @Test
    fun `update throws 400 when secondaryColorHex is invalid format`() {
        setSecurityContext(ownerRoleId)
        whenever(permissionService.hasPermission(ownerRoleId, PermissionConstants.BRANDING_UPDATE)).thenReturn(true)
        existingSettings()

        val request = UpdatePortalSettingsRequest(secondaryColorHex = "#GGG")
        val ex =
            assertThrows<ArenaException> {
                service.updateSettings(testClubId, request)
            }
        assertEquals(400, ex.status.value())
    }

    @Test
    fun `update throws 400 when portalTitle exceeds 100 characters`() {
        setSecurityContext(ownerRoleId)
        whenever(permissionService.hasPermission(ownerRoleId, PermissionConstants.BRANDING_UPDATE)).thenReturn(true)
        existingSettings()

        val request = UpdatePortalSettingsRequest(portalTitle = "A".repeat(101))
        val ex =
            assertThrows<ArenaException> {
                service.updateSettings(testClubId, request)
            }
        assertEquals(400, ex.status.value())
    }

    @Test
    fun `update throws 403 when caller lacks branding update and branding field is present`() {
        setSecurityContext(branchManagerRoleId)
        whenever(permissionService.hasPermission(branchManagerRoleId, PermissionConstants.BRANDING_UPDATE)).thenReturn(false)
        existingSettings()

        val request = UpdatePortalSettingsRequest(primaryColorHex = "#1A73E8")
        val ex =
            assertThrows<ArenaException> {
                service.updateSettings(testClubId, request)
            }
        assertEquals(403, ex.status.value())
    }

    @Test
    fun `update allows feature flag change without branding update permission`() {
        setSecurityContext(branchManagerRoleId)
        existingSettings()

        val request = UpdatePortalSettingsRequest(gxBookingEnabled = false)
        val result = service.updateSettings(testClubId, request)

        assertEquals(false, result.gxBookingEnabled)
    }

    @Test
    fun `update logs BRANDING_UPDATED audit action when branding field changes`() {
        setSecurityContext(ownerRoleId)
        whenever(permissionService.hasPermission(ownerRoleId, PermissionConstants.BRANDING_UPDATE)).thenReturn(true)
        existingSettings()

        val request = UpdatePortalSettingsRequest(primaryColorHex = "#1A73E8", portalTitle = "Elixir Gym")
        service.updateSettings(testClubId, request)

        verify(auditService).logFromContext(
            action = eq(AuditAction.BRANDING_UPDATED),
            entityType = eq("ClubPortalSettings"),
            entityId = any(),
            changesJson = argThat { contains("primaryColorHex") && contains("portalTitle") },
        )
    }

    @Test
    fun `update does not log BRANDING_UPDATED when only feature flags change`() {
        setSecurityContext(branchManagerRoleId)
        existingSettings()

        val request = UpdatePortalSettingsRequest(gxBookingEnabled = false, ptViewEnabled = false)
        service.updateSettings(testClubId, request)

        verify(auditService, never()).logFromContext(
            action = eq(AuditAction.BRANDING_UPDATED),
            entityType = any(),
            entityId = any(),
            changesJson = any(),
        )
    }
}
