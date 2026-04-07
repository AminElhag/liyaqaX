package com.liyaqa.member.dto

import java.util.UUID

data class ActivateMemberRequest(
    val membershipPlanId: UUID? = null,
)
