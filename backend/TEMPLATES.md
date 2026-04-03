# TEMPLATES.md — Backend File Generation Templates

This file defines the standard structure for every type of source file
in the backend. When a PLAN.md introduces a new domain or a new layer
within an existing domain, Claude generates the files by following
these templates exactly.

Templates are patterns, not implementations.
They define structure, naming, annotations, and imports —
never business logic.

This file is read at the start of every backend development session
alongside CLAUDE.md, DATABASE.md, and API.md.

---

## 1. How to use this file

When a PLAN.md says "create the member domain files", this means:
generate one file per layer listed in section 2 for the `member` domain,
following the corresponding template in section 3.

The domain name drives everything:
- Package:    `com.liyaqa.<domain>`
- DTO package: `com.liyaqa.<domain>.dto`
- Class names: `<DomainName>Controller`, `<DomainName>Service`, etc.
- Table name:  `<domain_name>s` (plural snake_case)

If a domain needs only some layers (e.g., no controller because it is
internal-only), the PLAN.md states which layers to generate.
Never generate layers that the PLAN.md does not ask for.

---

## 2. Standard layers per domain

A full domain consists of these files.
Not every domain needs all layers — the PLAN.md decides.

```
<domain>/
├── <Domain>.kt                    ← JPA entity
├── <Domain>Repository.kt          ← Spring Data JPA repository interface
├── <Domain>Service.kt             ← business logic
├── <Domain>Controller.kt          ← REST controller
└── dto/
    ├── <Domain>Request.kt         ← request body DTO
    ├── <Domain>Response.kt        ← full response DTO
    └── <Domain>SummaryResponse.kt ← lightweight list-item DTO (when needed)
```

Additional files added when the PLAN.md requires them:
```
<domain>/
├── <Domain>Mapper.kt              ← entity ↔ DTO mapping
├── <Domain>Validator.kt           ← complex validation logic
├── <Domain>EventPublisher.kt      ← domain event publishing
└── dto/
    └── <Domain>FilterRequest.kt   ← query filter parameters DTO
```

---

## 3. File templates

Replace `<Domain>` with PascalCase domain name.
Replace `<domain>` with camelCase domain name.
Replace `<domain_table>` with snake_case plural.

### 3.1 Entity — `<Domain>.kt`

```kotlin
package com.liyaqa.<domain>

import com.liyaqa.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "<domain_table>")
class <Domain>(

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),

    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,

    // domain fields added when feature is implemented

) : AuditEntity()
```

### 3.2 Repository — `<Domain>Repository.kt`

```kotlin
package com.liyaqa.<domain>

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface <Domain>Repository : JpaRepository<<Domain>, Long> {

    fun findByPublicIdAndOrganizationIdAndDeletedAtIsNull(
        publicId: UUID,
        organizationId: Long
    ): Optional<<Domain>>
}
```

### 3.3 Service — `<Domain>Service.kt`

```kotlin
package com.liyaqa.<domain>

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class <Domain>Service(
    private val <domain>Repository: <Domain>Repository,
) {
    // Methods added when feature is implemented
    // Rules:
    // - Write methods override with @Transactional
    // - Always pass organizationId from TenantContext
    // - Never return entity directly — map to response DTO
    // - Business rule violations throw ArenaException
}
```

### 3.4 Controller — `<Domain>Controller.kt`

```kotlin
package com.liyaqa.<domain>

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/<domain_path>")
@Tag(name = "<Domain>", description = "<Domain> management endpoints")
@Validated
class <Domain>Controller(
    private val <domain>Service: <Domain>Service,
) {
    // Endpoints added when feature is implemented
    // Rules:
    // - No business logic — delegate to service
    // - Every endpoint has @Operation and @PreAuthorize
    // - Use constants from Roles.kt — never raw strings
    // - Always return ResponseEntity<T> with explicit status
    // - Path variables use UUID (public_id) — never Long
}
```

### 3.5 Request DTO — `<Domain>Request.kt`

```kotlin
package com.liyaqa.<domain>.dto

data class <Domain>Request(
    // fields added per feature
    // Rules:
    // - camelCase fields
    // - Bean Validation annotations
    // - IDs as UUID — never Long
    // - Monetary amounts as Long ending in Halalas
    // - No JPA annotations
)
```

### 3.6 Response DTO — `<Domain>Response.kt`

```kotlin
package com.liyaqa.<domain>.dto

import java.time.Instant
import java.util.UUID

data class <Domain>Response(
    val id: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    // domain fields added per feature
    // Rules:
    // - id is always UUID (publicId) — never Long
    // - Timestamps as Instant (ISO 8601 UTC)
    // - Monetary as Long ending in Halalas
    // - No JPA annotations
    // - No circular references
)
```

### 3.7 Summary Response DTO — `<Domain>SummaryResponse.kt`

```kotlin
package com.liyaqa.<domain>.dto

import java.util.UUID

data class <Domain>SummaryResponse(
    val id: UUID,
    // minimal identifying fields only
)
```

---

## 4. AuditEntity base class

Every entity extends this.
Defined in `common/audit/AuditEntity.kt`.

```kotlin
package com.liyaqa.common.audit

import jakarta.persistence.Column
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@MappedSuperclass
abstract class AuditEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
)

fun AuditEntity.softDelete() {
    this.deletedAt = Instant.now()
}

val AuditEntity.isDeleted: Boolean
    get() = deletedAt != null
```

---

## 5. Minimal skeleton — files that exist before any feature

These files must exist before feature development begins:

```
backend/src/main/kotlin/com/liyaqa/
  LiyaqaApplication.kt
  common/audit/AuditEntity.kt
  common/exception/ArenaException.kt
  common/exception/ErrorCodes.kt
  common/exception/GlobalExceptionHandler.kt
  common/dto/ProblemDetailResponse.kt
  common/dto/PageResponse.kt
  common/tenant/TenantContext.kt
  common/tenant/TenantInterceptor.kt
  config/SecurityConfig.kt
  config/JacksonConfig.kt
  config/OpenApiConfig.kt
  security/Roles.kt
  security/JwtService.kt
  security/JwtAuthFilter.kt
```

All other files are generated domain by domain
as features are planned.

---

## 6. File generation prompt for PLAN.md tasks

When a PLAN.md introduces a new domain, use this pattern:

> "Following the templates in `backend/TEMPLATES.md`,
> generate the skeleton files for the `<domain>` domain.
>
> Domain name: `<Domain>` (PascalCase)
> Package: `com.liyaqa.<domain>`
> Table name: `<domain_table>`
> Layers needed: [list from PLAN.md]
>
> Rules:
> - Follow templates exactly
> - Skeleton files contain package declaration +
>   minimal empty class/object — never just package alone
>   (ktlint requires files to not be empty)
> - No implementation code
> - Comments from templates included as-is"

---

## 7. Naming quick reference

| Concept | Pattern | Example |
|---|---|---|
| Entity class | `<Domain>` | `Member`, `MembershipPlan` |
| Repository | `<Domain>Repository` | `MemberRepository` |
| Service | `<Domain>Service` | `MemberService` |
| Controller | `<Domain>Controller` | `MemberController` |
| Request DTO | `Create<Domain>Request` | `CreateMemberRequest` |
| Update DTO | `Update<Domain>Request` | `UpdateMemberRequest` |
| Response DTO | `<Domain>Response` | `MemberResponse` |
| Summary DTO | `<Domain>SummaryResponse` | `MemberSummaryResponse` |
| Package | `com.liyaqa.<domain>` | `com.liyaqa.member` |
| DTO Package | `com.liyaqa.<domain>.dto` | `com.liyaqa.member.dto` |
| API path | `/api/v1/<domain-path>` | `/api/v1/members` |
| Table name | `<domain_table>` | `members`, `membership_plans` |
