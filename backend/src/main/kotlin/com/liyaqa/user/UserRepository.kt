package com.liyaqa.user

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
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
}
