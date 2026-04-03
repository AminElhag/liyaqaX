package com.liyaqa.rbac

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Repository
interface UserRoleRepository : JpaRepository<UserRole, Long> {
    fun findByUserId(userId: Long): Optional<UserRole>

    @Transactional
    fun deleteByUserId(userId: Long)
}
