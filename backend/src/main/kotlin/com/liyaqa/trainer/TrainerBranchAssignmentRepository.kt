package com.liyaqa.trainer

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface TrainerBranchAssignmentRepository : JpaRepository<TrainerBranchAssignment, Long> {
    fun findAllByTrainerId(trainerId: Long): List<TrainerBranchAssignment>

    fun findByTrainerIdAndBranchId(
        trainerId: Long,
        branchId: Long,
    ): TrainerBranchAssignment?

    fun existsByTrainerIdAndBranchId(
        trainerId: Long,
        branchId: Long,
    ): Boolean

    @Transactional
    fun deleteByTrainerIdAndBranchId(
        trainerId: Long,
        branchId: Long,
    )

    @Transactional
    fun deleteAllByTrainerId(trainerId: Long)

    fun countByTrainerId(trainerId: Long): Long

    fun countByBranchId(branchId: Long): Long
}
