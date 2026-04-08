# RULES-SUMMARY.md — Critical Rules Claude Code Enforces

Cowork must know these rules to write accurate plans.
Every plan must be consistent with these rules.

---

## Backend Rules

### Package structure
- All packages: `com.liyaqa.[domain]`
- DTOs in: `com.liyaqa.[domain].dto`
- Never: `com.arena.*` (old name — do not use)

### Entity rules
- Every entity extends `AuditEntity` (id, publicId, timestamps, deletedAt)
- Exception: join tables (UserRole, StaffBranchAssignment) extend a minimal base
- `publicId: UUID = UUID.randomUUID()` — generated on construction
- Internal PKs are `Long` — never exposed in API
- Public API IDs are `UUID` (publicId) — never Long

### Money rules
- All monetary values: `BIGINT` in halalas
- 1 SAR = 100 halalas
- `priceHalalas: Long` not `price: Double`
- Responses include both: `priceHalalas: Long` + `priceSar: String`
- `priceSar` is computed: `"%.2f".format(halalas / 100.0)` — never stored

### CRITICAL: @Query annotation rule
**The most common bug in this project.**
NEVER use JPQL @Query for:
- Date/time arithmetic
- PostgreSQL functions: YEAR(), EXTRACT(), INTERVAL
- Type comparisons that Hibernate rejects

ALWAYS use `nativeQuery = true` with SQL column names (snake_case):
```kotlin
// WRONG — causes ALL integration tests to fail
@Query("SELECT m FROM Membership m WHERE YEAR(m.issuedAt) = :year")

// CORRECT
@Query(
    value = "SELECT * FROM memberships m WHERE EXTRACT(YEAR FROM m.issued_at) = :year",
    nativeQuery = true
)
```

For pageable native queries, ALWAYS add countQuery:
```kotlin
@Query(
    value = "SELECT * FROM table t WHERE ...",
    countQuery = "SELECT COUNT(*) FROM table t WHERE ...",
    nativeQuery = true
)
```

### Controller rules
- Every endpoint has `@Operation` and `@PreAuthorize`
- `@PreAuthorize("hasPermission(null, 'permission:code')")`
- Path variables use UUID (publicId) — never Long
- Always return `ResponseEntity<T>` with explicit HTTP status
- No business logic in controllers — delegate to service

### Service rules
- `@Transactional(readOnly = true)` on the class
- `@Transactional` override on write methods
- Never return JPA entities from public methods — always map to DTO
- Business rule violations throw `ArenaException` with correct HTTP status
- Tenant scope from TenantContext or JWT claims — never from request body

### Testing rules
- Never hardcode passwords in test files
  USE: `const val TEST_PASSWORD = "Test@12345678"`
  NEVER: `"password123"`, `"Pass1234!"`, `"correctpass"` etc.
- Integration tests use Testcontainers (real PostgreSQL)
- Unit tests mock repositories
- Every business rule must have a unit test

---

## Frontend Rules

### JWT handling
- JWT stored in memory only (useAuthStore)
- Never localStorage, never sessionStorage
- On page refresh → redirect to login (intended behavior)
- On 401 response → clear auth store + redirect to login

### Scope check on login
- web-pulse rejects any JWT where `scope !== "club"`
- Shows message: "This app is for club staff only"

### Permission checking
- `PermissionGate` removes elements from DOM — never CSS hide
- `hasPermission()` helper from `src/lib/permissions.ts`
- Permissions fetched from `GET /api/v1/auth/me` on app init

### Routing
- TanStack Router with file-based routing
- Auth guard in `__root.tsx`
- All routes require auth except `/auth/login`

### Money display
- User inputs in SAR → form converts to halalas on submit (× 100)
- API returns both `priceHalalas` and `priceSar`
- Always display `priceSar` — never display raw halalas to users
- `formatCurrency()` in `src/lib/formatCurrency.ts`

### Language / RTL
- Default locale: Arabic (ar) with RTL layout
- Language toggle in topbar
- Use logical CSS properties: `ms-`, `me-`, `ps-`, `pe-`
- Never: `ml-`, `mr-`, `pl-`, `pr-`

### npm rules
- Run `npm install` from INSIDE the app directory
- Each app has its own `package-lock.json`
- Root `package.json` has NO `workspaces` field
- Never run npm from project root for frontend work

---

## Git / CI Rules

### Branch naming
```
feat/[domain]-[feature]     e.g. feat/zatca-phase1
fix/[description]           e.g. fix/jpql-native-queries
chore/[description]         e.g. chore/update-dependencies
```

### Commit format (Conventional Commits)
```
feat(domain): description
fix(domain): description
chore(scope): description
docs(scope): description
```

### PR rules
- Target branch: `develop`
- Title must follow Conventional Commits format
- CI must be green before merging
- PLAN.md must be deleted before merging

### CI pipeline checks
1. Secret scan (GitGuardian)
2. Backend build
3. Backend tests (Testcontainers)
4. Backend lint (ktlint)
5. Frontend checks × 4 (typecheck + lint + test + build)
6. Security scan

---

## Naming Conventions

| Thing | Convention | Example |
|---|---|---|
| Entity class | PascalCase | `MembershipPlan` |
| Repository | `[Entity]Repository` | `MembershipPlanRepository` |
| Service | `[Entity]Service` | `MembershipPlanService` |
| Controller | `[Entity][App]Controller` | `MembershipPlanPulseController` |
| Request DTO | `Create[Entity]Request` | `CreateMembershipPlanRequest` |
| Response DTO | `[Entity]Response` | `MembershipPlanResponse` |
| Summary DTO | `[Entity]SummaryResponse` | `MembershipPlanSummaryResponse` |
| Package | `com.liyaqa.[domain]` | `com.liyaqa.membership` |
| Table | snake_case plural | `membership_plans` |
| API path | kebab-case | `/api/v1/membership-plans` |
| Permission code | `resource:action` | `membership:create` |

---

## What NOT to Include in Plans

These are common mistakes to avoid:

1. **No speculative entities** — only entities the feature actually needs
2. **No "while I'm at it" features** — strict scope only
3. **No file upload** — not implemented yet, always out of scope
4. **No email/SMS notifications** — not implemented yet
5. **No Flyway migration in dev** — we use `ddl-auto: create-drop` in dev
6. **No hardcoded role checks** — always use permission codes
7. **No payment gateway** — only cash/card/transfer in current plans
8. **No ZATCA Phase 2** — Phase 1 only until certificates are obtained
