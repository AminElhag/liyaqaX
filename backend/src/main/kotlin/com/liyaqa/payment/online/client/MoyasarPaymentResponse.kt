package com.liyaqa.payment.online.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class MoyasarPaymentResponse(
    val id: String,
    val status: String,
    val amount: Long,
    val currency: String? = null,
    val url: String? = null,
    val source: MoyasarSource? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MoyasarSource(
    val type: String? = null,
    @JsonProperty("company")
    val company: String? = null,
)
