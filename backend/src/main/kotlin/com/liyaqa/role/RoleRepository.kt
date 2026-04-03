package com.liyaqa.role

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface RoleRepository : JpaRepository<Role, Long> {
    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<Role>

    fun findAllByScopeAndDeletedAtIsNull(scope: String): List<Role>

    fun findAllByOrganizationIdAndClubIdAndDeletedAtIsNull(
        organizationId: Long,
        clubId: Long,
    ): List<Role>
}
