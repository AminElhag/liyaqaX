package com.liyaqa.auth.dto

import java.util.UUID

data class OtpVerifyResponse(
    val accessToken: String,
    val member: OtpMemberSummary,
)

data class OtpMemberSummary(
    val id: UUID,
    val firstName: String,
    val lastName: String,
    val preferredLanguage: String?,
)
