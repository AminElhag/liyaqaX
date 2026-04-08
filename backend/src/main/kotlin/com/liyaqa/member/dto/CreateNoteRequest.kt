package com.liyaqa.member.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateNoteRequest(
    val noteType: String,
    @field:NotBlank
    @field:Size(max = 1000)
    val content: String,
    val followUpAt: String? = null,
)
