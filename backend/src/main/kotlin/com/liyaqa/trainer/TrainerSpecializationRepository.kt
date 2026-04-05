package com.liyaqa.trainer

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TrainerSpecializationRepository : JpaRepository<TrainerSpecialization, Long> {
    fun findAllByTrainerIdAndDeletedAtIsNull(trainerId: Long): List<TrainerSpecialization>
}
