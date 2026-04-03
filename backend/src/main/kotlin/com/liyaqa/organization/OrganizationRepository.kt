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
}
