package com.liyaqa.trainer

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface TrainerCertificationRepository : JpaRepository<TrainerCertification, Long> {
    fun findAllByTrainerIdAndDeletedAtIsNull(trainerId: Long): List<TrainerCertification>

    fun findByPublicIdAndTrainerIdAndDeletedAtIsNull(
        publicId: UUID,
        trainerId: Long,
    ): Optional<TrainerCertification>
}
