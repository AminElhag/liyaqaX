package com.liyaqa.payment.online.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "online_payment_transactions")
class OnlinePaymentTransaction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),

    @Column(name = "moyasar_id", nullable = false, unique = true, updatable = false)
    val moyasarId: String,

    @Column(name = "membership_id", nullable = false, updatable = false)
    val membershipId: Long,

    @Column(name = "member_id", nullable = false, updatable = false)
    val memberId: Long,

    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,

    @Column(name = "amount_halalas", nullable = false, updatable = false)
    val amountHalalas: Long,

    // INITIATED | PAID | FAILED | CANCELLED
    @Column(name = "status", nullable = false, length = 20)
    var status: String,

    // mada | creditcard | applepay — populated from webhook payload
    @Column(name = "payment_method", length = 20)
    var paymentMethod: String? = null,

    @Column(name = "moyasar_hosted_url", length = 500, updatable = false)
    val moyasarHostedUrl: String,

    @Column(name = "callback_received_at")
    var callbackReceivedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
