package com.liyaqa.permission

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface PermissionRepository : JpaRepository<Permission, Long> {
    fun findByCode(code: String): Optional<Permission>

    fun findAllByCodeIn(codes: Collection<String>): List<Permission>

    fun findByPublicId(publicId: UUID): Optional<Permission>
}
