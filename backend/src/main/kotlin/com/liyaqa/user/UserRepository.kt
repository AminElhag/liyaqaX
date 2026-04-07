package com.liyaqa.user

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmailAndDeletedAtIsNull(email: String): Optional<User>

    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): Optional<User>

    fun findAllByOrganizationIdAndDeletedAtIsNull(
        organizationId: Long,
        pageable: Pageable,
    ): Page<User>

    fun findAllByClubIdAndDeletedAtIsNull(
        clubId: Long,
        pageable: Pageable,
    ): Page<User>

    @Query(
        value = """
            SELECT DISTINCT u.id FROM users u
            JOIN user_roles ur ON u.id = ur.user_id
            JOIN roles r ON ur.role_id = r.id
            JOIN role_permissions rp ON r.id = rp.role_id
            JOIN permissions p ON rp.permission_id = p.id
            WHERE p.code = :permissionCode
              AND r.scope = 'platform'
              AND u.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findPlatformUserIdsWithPermission(permissionCode: String): List<Long>
}
