package com.liyaqa.gx

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface GXAttendanceRepository : JpaRepository<GXAttendance, Long> {
    fun findByInstanceIdAndMemberId(
        instanceId: Long,
        memberId: Long,
    ): Optional<GXAttendance>

    fun findAllByInstanceId(instanceId: Long): List<GXAttendance>
}
