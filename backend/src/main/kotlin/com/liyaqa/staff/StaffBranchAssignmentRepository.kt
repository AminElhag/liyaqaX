package com.liyaqa.staff

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface StaffBranchAssignmentRepository : JpaRepository<StaffBranchAssignment, Long> {
    fun findAllByStaffMemberId(staffMemberId: Long): List<StaffBranchAssignment>

    fun findByStaffMemberIdAndBranchId(
        staffMemberId: Long,
        branchId: Long,
    ): StaffBranchAssignment?

    fun existsByStaffMemberIdAndBranchId(
        staffMemberId: Long,
        branchId: Long,
    ): Boolean

    @Transactional
    fun deleteByStaffMemberIdAndBranchId(
        staffMemberId: Long,
        branchId: Long,
    )

    @Transactional
    fun deleteAllByStaffMemberId(staffMemberId: Long)

    fun countByStaffMemberId(staffMemberId: Long): Long

    fun countByBranchId(branchId: Long): Long
}
