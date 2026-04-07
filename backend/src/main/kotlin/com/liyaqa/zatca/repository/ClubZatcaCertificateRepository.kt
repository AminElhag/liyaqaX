package com.liyaqa.zatca.repository

import com.liyaqa.zatca.entity.ClubZatcaCertificate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.Optional

@Repository
interface ClubZatcaCertificateRepository : JpaRepository<ClubZatcaCertificate, Long> {
    fun findByClubIdAndDeletedAtIsNull(clubId: Long): Optional<ClubZatcaCertificate>

    @Query(
        value = """
            SELECT czc.* FROM club_zatca_certificates czc
            WHERE czc.onboarding_status = 'active'
              AND czc.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findAllActive(): List<ClubZatcaCertificate>

    @Query(
        value = """
            SELECT czc.* FROM club_zatca_certificates czc
            WHERE czc.onboarding_status = 'active'
              AND czc.csid_expires_at < :expiryThreshold
              AND czc.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findExpiringSoon(expiryThreshold: Instant): List<ClubZatcaCertificate>
}
