package com.liyaqa.portal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ClubPortalSettingsRepository : JpaRepository<ClubPortalSettings, Long> {
    fun findByClubId(clubId: Long): Optional<ClubPortalSettings>
}
