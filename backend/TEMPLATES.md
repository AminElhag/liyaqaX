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
- Package:    `com.arena.<domain>`
- DTO package: `com.arena.<domain>.dto`
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
├── <Domain>Mapper.kt              ← entity ↔ DTO mapping (if not using MapStruct)
├── <Domain>Validator.kt           ← complex validation logic extracted from service
├── <Domain>EventPublisher.kt      ← domain event publishing
└── dto/
    └── <Domain>FilterRequest.kt   ← query filter parameters DTO
```

---

## 3. File templates

Replace `<Domain>` with PascalCase domain name (e.g., `Member`, `Membership`).
Replace `<domain>` with camelCase domain name (e.g., `member`, `membership`).
Replace `<domain_table>` with snake_case plural (e.g., `members`, `memberships`).

---

### 3.1 Entity — `<Domain>.kt`

```kotlin
package com.arena.<domain>

import com.arena.common.audit.AuditEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "<domain_table>")
class <Domain>(

    // public_id is the only ID exposed in the API — never the internal id
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),

    // tenant scope — always first domain fields after audit fields
    @Column(name = "organization_id", nullable = false, updatable = false)
    val organizationId: Long,

    // domain fields — added when the feature is implemented

) : AuditEntity()

// AuditEntity provides:
// id: Long (BIGSERIAL primary key — internal only)
// createdAt: Instant
// updatedAt: Instant (managed by DB trigger — never set by application code)
// deletedAt: Instant? (null = active, non-null = soft deleted)
```

---

### 3.2 Repository — `<Domain>Repository.kt`

```kotlin
package com.arena.<domain>

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface <Domain>Repository : JpaRepository<<Domain>, Long> {

    // Standard finder by public ID — used by all API endpoints
    // Always scoped to organizationId — never fetch without tenant filter
    fun findByPublicIdAndOrganizationIdAndDeletedAtIsNull(
        publicId: UUID,
        organizationId: Long
    ): Optional<<Domain>>

    // Additional query methods added when the feature requires them
    // Every method that returns tenant data must include organizationId parameter
    // Never add a finder that could return data across tenant boundaries
}
```

---

### 3.3 Service — `<Domain>Service.kt`

```kotlin
package com.arena.<domain>

import com.arena.<domain>.dto.<Domain>Request
import com.arena.<domain>.dto.<Domain>Response
import com.arena.common.dto.PageResponse
import com.arena.common.exception.ResourceNotFoundException
import com.arena.common.tenant.TenantContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class <Domain>Service(
    private val <domain>Repository: <Domain>Repository,
) {

    // All methods added when the feature is implemented.
    //
    // Rules this service must follow:
    // - Every method that reads data is @Transactional(readOnly = true) (inherited from class)
    // - Every method that writes data overrides with @Transactional
    // - Tenant isolation: always pass organizationId from TenantContext, never from request body
    // - Never return the entity directly — always map to a response DTO
    // - Business rule violations throw ArenaException with appropriate ErrorCode
    // - This service never calls another service directly —
    //   cross-domain orchestration belongs in an application service or use case class
}
```

---

### 3.4 Controller — `<Domain>Controller.kt`

```kotlin
package com.arena.<domain>

import com.arena.<domain>.dto.<Domain>Request
import com.arena.<domain>.dto.<Domain>Response
import com.arena.common.dto.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/<domain_path>")
@Tag(name = "<Domain>", description = "<Domain> management endpoints")
@Validated
class <Domain>Controller(
    private val <domain>Service: <Domain>Service,
) {

    // Endpoints added when the feature is implemented.
    //
    // Rules this controller must follow:
    // - No business logic — delegate everything to the service
    // - No direct repository access
    // - Every endpoint has @Operation and @PreAuthorize
    // - @PreAuthorize uses constants from Roles.kt — never raw strings
    // - Input validation via Bean Validation annotations on request DTOs
    // - Always return ResponseEntity<T> with an explicit status code
    // - Path variables use UUID (public_id) — never Long (internal id)
}
```

---

### 3.5 Request DTO — `<Domain>Request.kt`

```kotlin
package com.arena.<domain>.dto

// Request DTO for creating or updating a <Domain>.
// Fields and validation annotations added when the feature is implemented.
//
// Rules:
// - All fields use camelCase
// - Validation annotations from jakarta.validation.constraints
// - IDs reference other resources as UUID (publicId) — never Long
// - Monetary amounts as Long with field name ending in Halalas
// - No JPA annotations — this is never an entity
// - No business logic
data class <Domain>Request(
    // fields added per feature
)
```

---

### 3.6 Response DTO — `<Domain>Response.kt`

```kotlin
package com.arena.<domain>.dto

import java.time.Instant
import java.util.UUID

// Response DTO returned by the API for a <Domain> resource.
// Fields added when the feature is implemented.
//
// Rules:
// - id field is always UUID (publicId) — never the internal Long id
// - All timestamps are Instant (serialized as ISO 8601 UTC strings)
// - Monetary amounts as Long with field name ending in Halalas
// - No JPA annotations — this is never an entity
// - No circular references — use summary DTOs for nested resources
data class <Domain>Response(
    val id: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    // domain fields added per feature
)
```

---

### 3.7 Summary Response DTO — `<Domain>SummaryResponse.kt`

```kotlin
package com.arena.<domain>.dto

import java.util.UUID

// Lightweight DTO for list views and nested references.
// Contains only the fields needed to identify and display a <Domain>
// in a list or as a nested object inside another response.
// Full detail is available via the GET /<domain_path>/{id} endpoint.
//
// Add this file only when the PLAN.md explicitly needs a list/summary view
// that differs from the full response.
data class <Domain>SummaryResponse(
    val id: UUID,
    // minimal identifying fields only — added per feature
)
```

---

## 4. AuditEntity base class

Every entity extends this. Defined once in `common/audit/AuditEntity.kt`.
This is part of the minimal skeleton that exists before any feature.

```kotlin
package com.arena.common.audit

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
    // Internal primary key — NEVER expose this in API responses or accept it as input
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

// Soft delete helper — call instead of repository.delete()
fun AuditEntity.softDelete() {
    this.deletedAt = Instant.now()
}

val AuditEntity.isDeleted: Boolean
    get() = deletedAt != null
```

---

## 5. Minimal skeleton — files that exist before any feature

These files must exist before feature development begins because
everything depends on them. They are the only files created upfront.
All domain files are generated on demand via PLAN.md.

```
backend/src/main/kotlin/com/arena/

  ArenaApplication.kt              ← Spring Boot entry point
  
  common/
    audit/
      AuditEntity.kt               ← base entity (section 4 above)
    exception/
      ArenaException.kt            ← base exception class
      ErrorCodes.kt                ← error code constants
      GlobalExceptionHandler.kt    ← @RestControllerAdvice
    dto/
      ProblemDetailResponse.kt     ← RFC 7807 error response shape
      PageResponse.kt              ← paginated collection wrapper
    tenant/
      TenantContext.kt             ← thread-local tenant scope holder
      TenantInterceptor.kt         ← populates TenantContext from JWT

  config/
    SecurityConfig.kt              ← SecurityFilterChain
    JacksonConfig.kt               ← ObjectMapper configuration
    OpenApiConfig.kt               ← Springdoc configuration

  security/
    Roles.kt                       ← role constants (Roles.CLUB_OWNER etc.)
    JwtService.kt                  ← JWT creation and validation
    JwtAuthFilter.kt               ← OncePerRequestFilter for JWT

backend/src/test/kotlin/com/arena/
  ArenaApplicationTest.kt          ← context loads test
```

All other files are generated domain by domain as features are planned.

---

## 6. File generation prompt for PLAN.md tasks

When a PLAN.md introduces a new domain, use this prompt pattern to
generate the files for that domain:

```
Following the templates in backend/TEMPLATES.md,
generate the skeleton files for the <domain> domain.

Domain name: <Domain> (PascalCase)
Package: com.arena.<domain>
Table name: <domain_table>
Layers needed: [list from PLAN.md — e.g., entity, repository, service, controller, request DTO, response DTO]

Rules:
- Follow the templates in TEMPLATES.md exactly
- No implementation code — structure only
- Comments from the templates are included as-is
- Do not add any imports beyond what the template shows
- Do not add any methods beyond what the template shows
```

This ensures every new domain file is generated consistently
regardless of which session or which feature introduces it.

---

## 7. Naming quick reference

| Concept | Pattern | Example |
|---|---|---|
| Entity class | `<Domain>` | `Member`, `MembershipPlan` |
| Repository | `<Domain>Repository` | `MemberRepository` |
| Service | `<Domain>Service` | `MemberService` |
| Controller | `<Domain>Controller` | `MemberController` |
| Request DTO | `<Domain>Request` | `MemberRequest` |
| Create Request | `Create<Domain>Request` | `CreateMemberRequest` |
| Update Request | `Update<Domain>Request` | `UpdateMemberRequest` |
| Response DTO | `<Domain>Response` | `MemberResponse` |
| Summary DTO | `<Domain>SummaryResponse` | `MemberSummaryResponse` |
| Filter DTO | `<Domain>FilterRequest` | `MemberFilterRequest` |
| Package | `com.arena.<domain>` | `com.arena.member` |
| DTO Package | `com.arena.<domain>.dto` | `com.arena.member.dto` |
| API path | `/api/v1/<domain-path>` | `/api/v1/members` |
| Table name | `<domain_table>` | `members`, `membership_plans` |
