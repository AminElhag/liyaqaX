package com.liyaqa.gx

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface GXBookingRepository : JpaRepository<GXBooking, Long> {
    fun findByPublicIdAndOrganizationId(
        publicId: UUID,
        organizationId: Long,
    ): Optional<GXBooking>

    fun findByInstanceIdAndMemberId(
        instanceId: Long,
        memberId: Long,
    ): Optional<GXBooking>

    fun findAllByInstanceId(
        instanceId: Long,
        pageable: Pageable,
    ): Page<GXBooking>

    fun findAllByInstanceIdAndBookingStatusIn(
        instanceId: Long,
        statuses: List<String>,
    ): List<GXBooking>

    fun findAllByInstanceIdAndBookingStatus(
        instanceId: Long,
        bookingStatus: String,
    ): List<GXBooking>

    fun findFirstByInstanceIdAndBookingStatusOrderByWaitlistPositionAsc(
        instanceId: Long,
        bookingStatus: String,
    ): Optional<GXBooking>

    fun findAllByMemberIdAndOrganizationId(
        memberId: Long,
        organizationId: Long,
        pageable: Pageable,
    ): Page<GXBooking>

    fun existsByInstanceIdAndMemberId(
        instanceId: Long,
        memberId: Long,
    ): Boolean
}
