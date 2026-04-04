package com.liyaqa.member

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface EmergencyContactRepository : JpaRepository<EmergencyContact, Long> {
    fun findAllByMemberIdAndOrganizationId(
        memberId: Long,
        organizationId: Long,
    ): List<EmergencyContact>

    fun findByPublicIdAndOrganizationId(
        publicId: UUID,
        organizationId: Long,
    ): Optional<EmergencyContact>
}
