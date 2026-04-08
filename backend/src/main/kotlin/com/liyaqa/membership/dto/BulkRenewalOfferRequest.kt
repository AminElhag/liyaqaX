package com.liyaqa.membership.dto

import jakarta.validation.constraints.NotEmpty
import java.util.UUID

data class BulkRenewalOfferRequest(
    @field:NotEmpty(message = "At least one member ID is required")
    val memberPublicIds: List<UUID>,
)
