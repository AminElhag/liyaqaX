package com.liyaqa.member

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface MemberRegistrationIntentRepository : JpaRepository<MemberRegistrationIntent, Long> {
    fun findByMemberIdAndResolvedAtIsNull(memberId: Long): Optional<MemberRegistrationIntent>

    fun findByMemberPublicIdAndResolvedAtIsNull(memberPublicId: UUID): Optional<MemberRegistrationIntent>
}
