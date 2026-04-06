package com.liyaqa.lead

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LeadNoteRepository : JpaRepository<LeadNote, Long> {
    fun findAllByLeadIdOrderByCreatedAtAsc(leadId: Long): List<LeadNote>
}
