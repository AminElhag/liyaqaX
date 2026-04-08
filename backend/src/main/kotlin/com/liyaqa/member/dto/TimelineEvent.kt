package com.liyaqa.member.dto

import java.time.Instant
import java.util.UUID

sealed class TimelineEvent {
    abstract val eventAt: Instant
    abstract val eventType: String

    data class NoteEvent(
        override val eventAt: Instant,
        override val eventType: String,
        val noteId: UUID,
        val content: String,
        val noteType: String,
        val followUpAt: Instant?,
        val createdByName: String,
        val canDelete: Boolean,
    ) : TimelineEvent()

    data class MembershipEvent(
        override val eventAt: Instant,
        override val eventType: String,
        val membershipId: UUID,
        val planName: String,
        val detail: String,
    ) : TimelineEvent()

    data class PaymentEvent(
        override val eventAt: Instant,
        override val eventType: String,
        val paymentId: UUID,
        val amountSar: String,
        val method: String,
    ) : TimelineEvent()
}
