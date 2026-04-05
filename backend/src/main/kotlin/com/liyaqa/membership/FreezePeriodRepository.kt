package com.liyaqa.membership

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface FreezePeriodRepository : JpaRepository<FreezePeriod, Long> {
    fun findByMembershipIdAndActualEndDateIsNull(membershipId: Long): Optional<FreezePeriod>

    fun findAllByMembershipIdOrderByFreezeStartDateDesc(membershipId: Long): List<FreezePeriod>

    fun existsByMembershipIdAndActualEndDateIsNull(membershipId: Long): Boolean
}
