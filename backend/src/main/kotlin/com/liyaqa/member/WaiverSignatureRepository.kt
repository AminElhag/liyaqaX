package com.liyaqa.member

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface WaiverSignatureRepository : JpaRepository<WaiverSignature, Long> {
    fun findByMemberIdAndWaiverId(
        memberId: Long,
        waiverId: Long,
    ): Optional<WaiverSignature>

    fun existsByMemberIdAndWaiverId(
        memberId: Long,
        waiverId: Long,
    ): Boolean

    fun findAllByMemberId(memberId: Long): List<WaiverSignature>
}
