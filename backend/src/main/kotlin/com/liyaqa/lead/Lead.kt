package com.liyaqa.lead

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "leads")
class Lead(
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,
    @Column(name = "club_id", nullable = false, updatable = false)
    val clubId: Long,
    @Column(name = "branch_id")
    var branchId: Long? = null,
    @Column(name = "lead_source_id")
    var leadSourceId: Long? = null,
    @Column(name = "assigned_staff_id")
    var assignedStaffId: Long? = null,
    @Column(name = "converted_member_id")
    var convertedMemberId: Long? = null,
    @Column(name = "first_name", nullable = false, length = 100)
    var firstName: String,
    @Column(name = "last_name", nullable = false, length = 100)
    var lastName: String,
    @Column(name = "first_name_ar", length = 100)
    var firstNameAr: String? = null,
    @Column(name = "last_name_ar", length = 100)
    var lastNameAr: String? = null,
    @Column(name = "phone", length = 20)
    var phone: String? = null,
    @Column(name = "email", length = 255)
    var email: String? = null,
    // gender: male | female
    @Column(name = "gender", length = 10)
    var gender: String? = null,
    @Column(name = "notes", columnDefinition = "TEXT")
    var notes: String? = null,
    // stage: new | contacted | interested | converted | lost
    @Column(name = "stage", nullable = false, length = 20)
    var stage: String = "new",
    @Column(name = "lost_reason", columnDefinition = "TEXT")
    var lostReason: String? = null,
    @Column(name = "contacted_at")
    var contactedAt: Instant? = null,
    @Column(name = "interested_at")
    var interestedAt: Instant? = null,
    @Column(name = "converted_at")
    var convertedAt: Instant? = null,
    @Column(name = "lost_at")
    var lostAt: Instant? = null,
) : AuditEntity()
