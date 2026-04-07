package com.liyaqa.member.dto

import java.util.UUID

data class MemberImportAcceptedResponse(
    val jobId: UUID,
    val status: String,
    val fileName: String,
    val message: String,
)
