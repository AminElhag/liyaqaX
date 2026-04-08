package com.liyaqa.pt

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PTPackageRepository : JpaRepository<PTPackage, Long> {
    fun findAllByTrainerIdAndDeletedAtIsNull(trainerId: Long): List<PTPackage>
}
