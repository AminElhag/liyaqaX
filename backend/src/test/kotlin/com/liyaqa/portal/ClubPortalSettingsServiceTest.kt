package com.liyaqa.portal

import com.liyaqa.club.Club
import com.liyaqa.club.ClubRepository
import com.liyaqa.organization.Organization
import com.liyaqa.organization.OrganizationRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class ClubPortalSettingsServiceTest {
    @Autowired lateinit var settingsService: ClubPortalSettingsService

    @Autowired lateinit var settingsRepository: ClubPortalSettingsRepository

    @Autowired lateinit var clubRepository: ClubRepository

    @Autowired lateinit var organizationRepository: OrganizationRepository

    private lateinit var org: Organization
    private lateinit var club: Club

    @BeforeEach
    fun setup() {
        org =
            organizationRepository.save(
                Organization(nameAr = "تجربة", nameEn = "Test Org", email = "test@test.com", country = "SA", timezone = "Asia/Riyadh"),
            )
        club =
            clubRepository.save(
                Club(organizationId = org.id, nameAr = "نادي", nameEn = "Test Club"),
            )
    }

    @AfterEach
    fun cleanup() {
        settingsRepository.deleteAllInBatch()
        clubRepository.deleteAllInBatch()
        organizationRepository.deleteAllInBatch()
    }

    @Test
    fun `getOrCreateSettings creates defaults when none exist`() {
        val settings = settingsService.getOrCreateSettings(club.id)
        assertTrue(settings.gxBookingEnabled)
        assertTrue(settings.ptViewEnabled)
        assertTrue(settings.invoiceViewEnabled)
        assertFalse(settings.onlinePaymentEnabled)
    }

    @Test
    fun `isFeatureEnabled returns correct value`() {
        settingsService.getOrCreateSettings(club.id)
        assertTrue(settingsService.isFeatureEnabled(club.id, "gx"))
        assertTrue(settingsService.isFeatureEnabled(club.id, "pt"))
        assertTrue(settingsService.isFeatureEnabled(club.id, "invoice"))
        assertFalse(settingsService.isFeatureEnabled(club.id, "payment"))
    }

    @Test
    fun `updateSettings changes flags`() {
        val request =
            com.liyaqa.portal.dto.UpdatePortalSettingsRequest(
                gxBookingEnabled = false,
                portalMessage = "Hello!",
            )
        val updated = settingsService.updateSettings(club.id, request)
        assertFalse(updated.gxBookingEnabled)
        assertTrue(updated.ptViewEnabled)
        assertEquals("Hello!", updated.portalMessage)
    }

    @Test
    fun `requireFeatureEnabled throws when feature disabled`() {
        val settings = settingsService.getOrCreateSettings(club.id)
        settings.gxBookingEnabled = false
        settingsRepository.save(settings)

        assertThrows(com.liyaqa.common.exception.ArenaException::class.java) {
            settingsService.requireFeatureEnabled(club.id, "gx")
        }
    }
}
