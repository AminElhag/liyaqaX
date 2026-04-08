package com.liyaqa.organization

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface OrganizationRepository : JpaRepository<Organization, Long> {
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<Organization>

    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Organization>

    fun existsByEmailAndDeletedAtIsNull(email: String): Boolean

    fun existsByEmailAndDeletedAtIsNullAndIdNot(
        email: String,
        id: Long,
    ): Boolean

    @org.springframework.data.jpa.repository.Query(
        value = """
            SELECT * FROM organizations
            WHERE deleted_at IS NULL
              AND (LOWER(name_en) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(name_ar) LIKE LOWER(CONCAT('%', :q, '%')))
        """,
        countQuery = """
            SELECT COUNT(*) FROM organizations
            WHERE deleted_at IS NULL
              AND (LOWER(name_en) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(name_ar) LIKE LOWER(CONCAT('%', :q, '%')))
        """,
        nativeQuery = true,
    )
    fun findAllByDeletedAtIsNullAndNameSearch(
        @org.springframework.data.repository.query.Param("q") q: String,
        pageable: Pageable,
    ): Page<Organization>

    fun countByDeletedAtIsNull(): Long

    fun existsByNameEnAndDeletedAtIsNull(nameEn: String): Boolean

    fun existsByNameEnAndDeletedAtIsNullAndIdNot(
        nameEn: String,
        id: Long,
    ): Boolean
}
