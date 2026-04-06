package com.liyaqa.portal

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "club_portal_settings")
class ClubPortalSettings(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "club_id", nullable = false, unique = true, updatable = false)
    val clubId: Long,
    @Column(name = "gx_booking_enabled", nullable = false)
    var gxBookingEnabled: Boolean = true,
    @Column(name = "pt_view_enabled", nullable = false)
    var ptViewEnabled: Boolean = true,
    @Column(name = "invoice_view_enabled", nullable = false)
    var invoiceViewEnabled: Boolean = true,
    @Column(name = "online_payment_enabled", nullable = false)
    var onlinePaymentEnabled: Boolean = false,
    @Column(name = "portal_message", length = 500)
    var portalMessage: String? = null,
) : AuditEntity()
