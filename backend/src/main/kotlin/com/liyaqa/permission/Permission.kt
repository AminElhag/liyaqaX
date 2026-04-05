package com.liyaqa.permission

import com.liyaqa.common.audit.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "permissions")
class Permission(
    @Column(name = "code", nullable = false, unique = true, length = 100)
    val code: String,
    @Column(name = "resource", nullable = false, length = 50)
    val resource: String,
    @Column(name = "action", nullable = false, length = 100)
    val action: String,
    @Column(name = "description_ar", columnDefinition = "TEXT")
    val descriptionAr: String? = null,
    @Column(name = "description_en", columnDefinition = "TEXT")
    val descriptionEn: String? = null,
) : BaseEntity()
