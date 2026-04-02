package com.arena.user

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmailAndDeletedAtIsNull(email: String): User?

    fun findByPublicIdAndDeletedAtIsNull(publicId: UUID): User?
}
