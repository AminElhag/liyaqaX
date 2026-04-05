package com.liyaqa.permission

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface PermissionRepository : JpaRepository<Permission, Long> {
    fun findByCode(code: String): Optional<Permission>

    fun findAllByCodeIn(codes: Collection<String>): List<Permission>
}
