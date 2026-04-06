# PLAN.md — web-nexus (Internal Platform Dashboard)

## Status
Ready for implementation

## Branch
feat/web-nexus

## Goal
Build the Liyaqa internal platform dashboard at web-nexus (port 5173). This
is the tool used exclusively by the Liyaqa team (super admins, support agents,
integration specialists, read-only auditors). It provides full visibility and
control over the multi-tenant hierarchy: organizations, clubs, branches, staff,
and platform-wide metrics. Four platform roles with different permission scopes
are already seeded. No new entities are required — this plan wires up the
existing backend to a new frontend and adds a small set of platform-scoped
read endpoints.

## Context
- `Organization`, `Club`, `Branch`, `StaffMember`, `Trainer`, `Member`,
  `Membership`, `Invoice`, `Lead`, `PTSession`, `GXClassInstance` all exist.
- Four platform roles already seeded: Super Admin, Support Agent,
  Integration Specialist, Read-Only Auditor.
- JWT `scope = "platform"` already defined. Claims include `roleId`.
  Permission resolution uses the same Redis-cached RBAC system as web-pulse.
- `GET /api/v1/auth/me` already returns permissions for the logged-in user.
- web-pulse is the reference implementation for sidebar layout, TanStack
  Router, i18n, Tailwind patterns.
- `ddl-auto: create-drop` in dev — no Flyway migration needed.
- No new entities required — pure read + a handful of targeted platform writes.

---

## Scope — what this plan covers

### Backend
- [ ] `OrganizationNexusController.kt` — list + detail + create + update orgs
- [ ] `ClubNexusController.kt` — list + detail + create + update clubs per org
- [ ] `BranchNexusController.kt` — list + detail + create + update branches per club
- [ ] `MemberNexusController.kt` — cross-org member search + detail (read-only)
- [ ] `PlatformStatsNexusController.kt` — platform-wide KPIs
- [ ] `AuditNexusController.kt` — platform audit log viewer
- [ ] `NexusAuthHelper.kt` — JWT scope check for platform endpoints
- [ ] DTOs for all nexus endpoints
- [ ] Unit tests: `OrganizationNexusServiceTest`, `PlatformStatsNexusServiceTest`
- [ ] Integration tests: all nexus controllers

### Frontend (web-nexus — NEW app)
- [ ] Bootstrap `web-nexus/` Vite + React 18 + TypeScript app
  (same stack as web-pulse: TanStack Router, TanStack Query, Zustand,
  React Hook Form, Zod, i18next, Tailwind — port 5173)
- [ ] Email + password login (scope check: rejects non-`platform` tokens)
- [ ] App shell: collapsible sidebar + topbar (same pattern as web-pulse)
- [ ] Platform home — KPI cards (total orgs, clubs, branches, active members,
  MRR estimate, active memberships)
- [ ] Organizations list + detail (clubs inside, key metrics)
- [ ] Clubs list + detail (branches, staff count, member count, revenue)
- [ ] Branches list + detail (staff, trainers, members)
- [ ] Members search — cross-org global member lookup
- [ ] Audit log viewer — paginated, filterable
- [ ] i18n: Arabic (default) + English

---

## Out of scope — do not implement in this plan
- System impersonation (`system:impersonate` permission — future plan,
  needs careful security design)
- Deleting organizations, clubs, or branches (destructive — future plan)
- Platform billing / subscription management (not built yet)
- Role + permission management UI (seeded roles only — future plan)
- ZATCA certificate management (Phase 2 — blocked)
- Email / SMS notifications
- File upload

---

## Decisions already made

- **Email + password login, scope = "platform"**: reuses `POST /api/v1/auth/login`.
  web-nexus checks `scope === "platform"` on the returned JWT. Any other scope
  shows "This app is for Liyaqa platform staff only".

- **Four platform roles, different nav visibility**:
  - Super Admin — all screens
  - Support Agent — orgs/clubs/branches (read-only), members search, no audit log
  - Integration Specialist — orgs/clubs/branches (read-only), no members, no audit
  - Read-Only Auditor — audit log only + platform stats
  Enforced via `PermissionGate` on frontend + `@PreAuthorize` on backend.

- **Platform permission codes** (new — added to seeded roles):
  ```
  organization:create, organization:read, organization:update
  club:create, club:read, club:update
  branch:create, branch:read, branch:update
  member:read (cross-org search — platform-only)
  platform:stats:view
  audit:read
  ```
  Most of these already exist on the seeded roles. `platform:stats:view` is new.

- **Nexus controllers are separate from Pulse controllers**: naming convention
  `[Domain]NexusController`. They check `scope = "platform"` via
  `NexusAuthHelper` and then check the specific permission via `@PreAuthorize`.

- **Cross-org member search**: Support Agents need to look up a member by
  phone/email/name across all organizations. `GET /api/v1/nexus/members?q=`
  searches across all tenants. This is a platform-only endpoint — not exposed
  in web-pulse or web-arena.

- **Platform stats are computed on request**: no caching layer yet. Simple
  aggregate queries over existing tables. Numbers are approximate (best-effort,
  not financial-grade). All native `@Query` with `nativeQuery = true` to avoid
  JPQL date arithmetic bugs.

- **Audit log already exists**: `AuditLog` entity (if present) or we expose
  the existing audit trail. If no AuditLog entity exists, the audit screen
  shows a placeholder "Audit logging coming in a future plan" — do not create
  the entity speculatively.

- **No branch selector in topbar**: web-pulse has a branch selector because
  staff are scoped to branches. Platform staff see everything — no branch
  selector needed.

- **MRR estimate**: sum of active membership `priceHalalas` / plan duration
  normalized to monthly. Displayed as SAR. Labeled "Estimated MRR" with a
  tooltip explaining it's approximate. Never treated as financial truth.

---

## Entity design

No new entities. One new permission code:

```
platform:stats:view   → assigned to Super Admin and Read-Only Auditor
```

All other permission codes already exist in the RBAC seed data.

---

## API endpoints

### NexusAuthHelper — used by all nexus controllers
Extracts and validates `scope = "platform"` from JWT. Returns 403 if scope
is not `platform`.

### OrganizationNexusController — `/api/v1/nexus/organizations`

```
GET    /api/v1/nexus/organizations              paginated list with search
GET    /api/v1/nexus/organizations/{id}         org detail with club summary
POST   /api/v1/nexus/organizations              create new org
PATCH  /api/v1/nexus/organizations/{id}         update org
```

Required permissions: `organization:read` (GET), `organization:create` (POST),
`organization:update` (PATCH).

### ClubNexusController — `/api/v1/nexus/organizations/{orgId}/clubs`

```
GET    /api/v1/nexus/organizations/{orgId}/clubs          list clubs for org
GET    /api/v1/nexus/organizations/{orgId}/clubs/{id}     club detail with metrics
POST   /api/v1/nexus/organizations/{orgId}/clubs          create club
PATCH  /api/v1/nexus/organizations/{orgId}/clubs/{id}     update club
```

Required permissions: `club:read` (GET), `club:create` (POST),
`club:update` (PATCH).

### BranchNexusController — `/api/v1/nexus/organizations/{orgId}/clubs/{clubId}/branches`

```
GET    .../branches          list branches for club
GET    .../branches/{id}     branch detail (staff count, trainer count, member count)
POST   .../branches          create branch
PATCH  .../branches/{id}     update branch
```

Required permissions: `branch:read` (GET), `branch:create` (POST),
`branch:update` (PATCH).

### MemberNexusController — `/api/v1/nexus/members`

```
GET    /api/v1/nexus/members?q=&page=&size=   cross-org member search
GET    /api/v1/nexus/members/{id}             member detail (read-only)
```

Required permission: `member:read`.

### PlatformStatsNexusController — `/api/v1/nexus/stats`

```
GET    /api/v1/nexus/stats    platform-wide KPIs
```

Required permission: `platform:stats:view`.

### AuditNexusController — `/api/v1/nexus/audit`

```
GET    /api/v1/nexus/audit?page=&size=&actorId=&action=&from=&to=   audit log
```

Required permission: `audit:read`.
If no `AuditLog` entity exists, returns empty list with a `meta.note` field:
`"Audit logging will be available in a future release"`.

---

## Request / Response shapes

### OrgListItemResponse
```json
{
  "id": "uuid",
  "name": "string",
  "nameAr": "string",
  "vatNumber": "string | null",
  "clubCount": 3,
  "activeMemberCount": 420,
  "createdAt": "ISO 8601"
}
```

### OrgDetailResponse
```json
{
  "id": "uuid",
  "name": "string",
  "nameAr": "string",
  "vatNumber": "string | null",
  "createdAt": "ISO 8601",
  "clubs": [
    {
      "id": "uuid",
      "name": "string",
      "nameAr": "string",
      "branchCount": 2,
      "activeMemberCount": 210
    }
  ]
}
```

### CreateOrganizationRequest
```json
{
  "name": "string (required)",
  "nameAr": "string (required)",
  "vatNumber": "string (optional)"
}
```

### ClubDetailResponse
```json
{
  "id": "uuid",
  "name": "string",
  "nameAr": "string",
  "vatNumber": "string | null",
  "branchCount": 2,
  "staffCount": 8,
  "activeMemberCount": 210,
  "activeMembers": 210,
  "estimatedMrrHalalas": 31500000,
  "estimatedMrrSar": "315000.00"
}
```

### BranchDetailResponse
```json
{
  "id": "uuid",
  "name": "string",
  "nameAr": "string",
  "staffCount": 4,
  "trainerCount": 2,
  "activeMemberCount": 105
}
```

### MemberSearchItemResponse
```json
{
  "id": "uuid",
  "firstName": "string",
  "lastName": "string",
  "phone": "string",
  "email": "string | null",
  "clubName": "string",
  "organizationName": "string",
  "membershipStatus": "active | expired | frozen | none"
}
```

### PlatformStatsResponse
```json
{
  "totalOrganizations": 12,
  "totalClubs": 34,
  "totalBranches": 78,
  "totalActiveMembers": 8420,
  "totalActiveMemberships": 7910,
  "estimatedMrrHalalas": 1188000000,
  "estimatedMrrSar": "11880000.00",
  "newMembersLast30Days": 312,
  "generatedAt": "ISO 8601"
}
```

---

## Business rules — enforce in service layer

1. **Platform scope only** — all nexus endpoints check `scope = "platform"`
   via `NexusAuthHelper`. Return 403 for any other scope.

2. **Permission gates per endpoint** — each endpoint uses `@PreAuthorize`
   with the specific permission code. Read-Only Auditor cannot call POST/PATCH.
   Support Agent cannot call audit endpoints. Integration Specialist cannot
   search members.

3. **Cross-org member search requires `q` param** — minimum 2 characters.
   Return 422 "Search query must be at least 2 characters" if shorter.
   Searches across `firstName`, `lastName`, `phone`, `email` (case-insensitive,
   partial match). Max 50 results per page.

4. **Org/Club/Branch create: unique name per parent** — organization `name`
   must be unique across all orgs. Club `name` must be unique within the org.
   Branch `name` must be unique within the club. Return 409 "Name already
   exists" on violation.

5. **MRR estimate calculation**: for each active membership, take the plan's
   `priceHalalas` and normalize to monthly:
   - Monthly plan: `priceHalalas × 1`
   - Quarterly plan: `priceHalalas / 3`
   - Annual plan: `priceHalalas / 12`
   Sum all values. Round to nearest halala. Always labeled "estimated" in
   both API response field name and UI tooltip.

6. **Stats are best-effort**: `GET /nexus/stats` runs aggregate queries
   directly — no caching. All date queries use `nativeQuery = true`.
   Response always includes `generatedAt` timestamp so caller knows when
   numbers were computed.

7. **Audit log returns empty if no entity**: if `AuditLog` entity/table does
   not exist, `GET /nexus/audit` returns `{ "content": [], "totalElements": 0,
   "meta": { "note": "Audit logging will be available in a future release" } }`.
   Do not throw an exception or 500.

8. **Soft-deleted records excluded**: all list endpoints exclude records where
   `deleted_at IS NOT NULL`.

---

## Seed data updates

Add to `DevDataLoader.kt`:

```
Add platform:stats:view permission to:
  - Super Admin role
  - Read-Only Auditor role

(All other platform permission codes should already be on the seeded roles —
verify and add any missing ones during Step 6.)
```

---

## Frontend additions (web-nexus)

### Bootstrap web-nexus app
New Vite app at `web-nexus/` with same stack as web-pulse:
React 18, TypeScript strict, TanStack Router (file-based), TanStack Query,
Zustand (auth state + permissions), React Hook Form + Zod, Tailwind CSS, i18next.
Port: 5173. JWT scope check: rejects non-`platform` tokens with
"This app is for Liyaqa platform staff only".

### Login — /auth/login
Standard email + password. On success: checks `scope === "platform"`.
Non-platform token shows error. On success → store JWT + permissions in
Zustand → redirect to `/`.

### App shell
Collapsible left sidebar (same pattern as web-pulse).
Nav items and their required permissions:
- Home / Stats (`platform:stats:view`)
- Organizations (`organization:read`)
- Members Search (`member:read`)
- Audit Log (`audit:read`)

Header: "Liyaqa Platform" logo, logged-in user name, language toggle, logout.
No branch selector (platform users see everything).

### Home / Stats — /
Six KPI cards: Total Organizations, Total Clubs, Total Branches,
Active Members, Active Memberships, Estimated MRR (SAR).
Two supporting numbers below: New Members (last 30 days), Generated At timestamp.
"Refresh" button to re-fetch stats on demand.
Gated by `platform:stats:view` — auditors and super admins only.

### Organizations — /organizations
Paginated table: org name (AR + EN), VAT number, club count, active member count,
created date. Search input. "New Organization" button (Super Admin only,
gated by `organization:create`).
Tap row → org detail page.

### Org Detail — /organizations/$orgId
Org name, VAT number, created date.
Clubs table: club name, branch count, active members. Tap → club detail.
"Edit" button (gated by `organization:update`) → inline edit form.
"Add Club" button (gated by `club:create`) → create club modal.

### Club Detail — /organizations/$orgId/clubs/$clubId
Club name, VAT number, branch count, staff count, active member count,
Estimated MRR card.
Branches table: branch name, staff count, trainer count, member count.
Tap → branch detail.
"Edit" and "Add Branch" buttons (gated by respective permissions).

### Branch Detail — /organizations/$orgId/clubs/$clubId/branches/$branchId
Branch name, staff count, trainer count, active member count.
Read-only — no edit for branches in this plan (create/update exists but
branch detail editing is staff-ops, not platform-ops).

### Members Search — /members
Search input (min 2 chars, debounced 300ms).
Results table: member name, phone, email, club, org, membership status badge.
Tap row → member detail (read-only profile: name, contact, active membership summary).
Gated by `member:read`.

### Audit Log — /audit
Paginated table: timestamp, actor (name + email), action, entity type, entity ID.
Filters: date range, action type.
If backend returns empty with meta note → show info banner instead of empty state.
Gated by `audit:read`.

### i18n key sample
```json
{
  "login.scope_error": "This app is for Liyaqa platform staff only",
  "stats.title": "Platform Overview",
  "stats.total_orgs": "Organizations",
  "stats.total_clubs": "Clubs",
  "stats.total_branches": "Branches",
  "stats.active_members": "Active Members",
  "stats.estimated_mrr": "Estimated MRR",
  "stats.mrr_tooltip": "Approximate monthly recurring revenue based on active memberships. Not a financial statement.",
  "stats.generated_at": "As of {{time}}",
  "orgs.new": "New Organization",
  "orgs.vat": "VAT Number",
  "members.search_placeholder": "Search by name, phone, or email...",
  "members.min_chars": "Enter at least 2 characters to search",
  "audit.empty_note": "Audit logging will be available in a future release"
}
```

---

## Files to generate

### Backend — new files
```
nexus/
  NexusAuthHelper.kt
  OrganizationNexusController.kt
  ClubNexusController.kt
  BranchNexusController.kt
  MemberNexusController.kt
  PlatformStatsNexusController.kt
  AuditNexusController.kt
  dto/
    OrgListItemResponse.kt
    OrgDetailResponse.kt
    CreateOrganizationRequest.kt
    UpdateOrganizationRequest.kt
    ClubDetailResponse.kt
    CreateClubNexusRequest.kt
    UpdateClubNexusRequest.kt
    BranchDetailResponse.kt
    CreateBranchNexusRequest.kt
    MemberSearchItemResponse.kt
    MemberDetailNexusResponse.kt
    PlatformStatsResponse.kt
    AuditLogResponse.kt
```

### Backend — modified files
```
config/DevDataLoader.kt    add platform:stats:view permission to Super Admin + Auditor
config/SecurityConfig.kt   no changes needed (nexus routes use @PreAuthorize)
```

### Frontend — new app
```
web-nexus/
  package.json                 (React 18, TypeScript, Vite, TanStack Router/Query,
                                Zustand, RHF, Zod, i18next, Tailwind)
  vite.config.ts               (port 5173)
  tsconfig.json
  index.html
  src/
    main.tsx
    router.tsx
    store/authStore.ts         (JWT in memory, user profile, permissions set)
    lib/
      api.ts
      permissions.ts           (hasPermission helper)
      formatCurrency.ts
    types/
      domain.ts
    i18n/
      index.ts
      en.json
      ar.json
    api/
      auth.ts
      organizations.ts
      clubs.ts
      branches.ts
      members.ts
      stats.ts
      audit.ts
    routes/
      __root.tsx               (auth guard + permissions fetch)
      auth/
        login.tsx
      index.tsx                (platform stats / home)
      organizations/
        index.tsx
        $orgId.tsx
        $orgId.clubs.$clubId.tsx
        $orgId.clubs.$clubId.branches.$branchId.tsx
      members/
        index.tsx
        $memberId.tsx
      audit.tsx
    components/
      shell/
        Sidebar.tsx
        AppHeader.tsx
      stats/
        KpiCard.tsx
      organizations/
        OrgTable.tsx
        OrgForm.tsx
        ClubTable.tsx
        ClubForm.tsx
      members/
        MemberSearchResult.tsx
        MembershipStatusBadge.tsx
      audit/
        AuditTable.tsx
        AuditFilters.tsx
      common/
        PermissionGate.tsx
        StatusBadge.tsx
        EmptyState.tsx
        LoadingSpinner.tsx
        Pagination.tsx
```

---

## Implementation order

```
Step 1 — NexusAuthHelper + OrganizationNexusController
  nexus/NexusAuthHelper.kt — requirePlatformScope(), extract roleId from JWT
  nexus/OrganizationNexusController.kt:
    GET /nexus/organizations?q=&page=&size= — list with search, excludes deleted
    GET /nexus/organizations/{id} — org detail with clubs summary
    POST /nexus/organizations — create (rule 4: unique name)
    PATCH /nexus/organizations/{id} — update
  DTOs: OrgListItemResponse, OrgDetailResponse, CreateOrganizationRequest, UpdateOrganizationRequest
  All list queries use nativeQuery=true if any date or aggregate involved
  Verify: ./gradlew build -x test

Step 2 — ClubNexusController + BranchNexusController
  nexus/ClubNexusController.kt:
    GET /nexus/organizations/{orgId}/clubs — list
    GET /nexus/organizations/{orgId}/clubs/{id} — detail with MRR estimate (rule 5)
    POST + PATCH — create/update (rule 4: unique name within org)
  nexus/BranchNexusController.kt:
    GET .../branches — list
    GET .../branches/{id} — detail with counts
    POST + PATCH — create/update (rule 4: unique name within club)
  DTOs: ClubDetailResponse, BranchDetailResponse, Create/Update requests
  Verify: ./gradlew build -x test

Step 3 — MemberNexusController
  nexus/MemberNexusController.kt:
    GET /nexus/members?q=&page=&size= — cross-org search (rule 3: min 2 chars)
    GET /nexus/members/{id} — member detail (read-only)
  Search: ILIKE across firstName, lastName, phone, email using nativeQuery=true
  DTOs: MemberSearchItemResponse, MemberDetailNexusResponse
  Verify: ./gradlew build -x test

Step 4 — PlatformStatsNexusController
  nexus/PlatformStatsNexusController.kt:
    GET /nexus/stats — platform KPIs (rule 6: best-effort, nativeQuery=true)
    MRR estimate: SUM with plan duration normalization (rule 5)
  DTO: PlatformStatsResponse (includes generatedAt)
  All aggregate queries: nativeQuery=true
  Verify: ./gradlew build -x test

Step 5 — AuditNexusController
  nexus/AuditNexusController.kt:
    GET /nexus/audit?page=&size=&from=&to= — paginated audit log
    If AuditLog entity doesn't exist → return empty page + meta note (rule 7)
  DTO: AuditLogResponse
  Verify: ./gradlew build -x test

Step 6 — Seed data + permissions
  Update DevDataLoader.kt:
    Add platform:stats:view permission to Super Admin + Read-Only Auditor
    Verify all existing platform permission codes are present on correct roles
  Verify: ./gradlew bootRun --args='--spring.profiles.active=dev'
  Manual: POST /api/v1/auth/login {email: "admin@liyaqa.com", password: "Admin1234!"}
    → JWT has scope=platform
  Manual: GET /api/v1/nexus/stats with admin JWT → returns counts

Step 7 — Backend tests
  OrganizationNexusControllerTest.kt (integration):
    - list orgs, search, create (happy path + duplicate name 409),
      update, non-platform scope (403)
  ClubNexusControllerTest.kt (integration):
    - list clubs, club detail with MRR, create, duplicate name (409)
  MemberNexusControllerTest.kt (integration):
    - cross-org search happy path, q < 2 chars (422), integration specialist (403)
  PlatformStatsNexusControllerTest.kt (integration):
    - stats returned with correct structure, auditor can access, support agent (403)
  OrganizationNexusServiceTest.kt (unit):
    - MRR estimate: monthly/quarterly/annual normalization, mixed plan types
    - Unique name enforcement
  Verify: ./gradlew test --no-daemon

Step 8 — Backend final checks
  ./gradlew ktlintFormat --no-daemon
  ./gradlew ktlintCheck --no-daemon
  ./gradlew build --no-daemon

Step 9 — Bootstrap web-nexus app
  Create web-nexus/ with package.json (port 5173)
  Install: react, react-dom, typescript, vite, @tanstack/react-router,
    @tanstack/react-query, zustand, react-hook-form, zod, i18next,
    react-i18next, tailwindcss, axios
  Setup: vite.config.ts, tsconfig.json, tailwind.config.js, index.html,
    src/main.tsx, src/router.tsx
  src/store/authStore.ts — JWT in memory, user profile, permissions Set<string>
  src/lib/permissions.ts — hasPermission(permission: string): boolean
  src/lib/api.ts — axios instance → backend port 8080
  Verify: cd web-nexus && npm run dev → blank app at localhost:5173

Step 10 — Auth flow + app shell
  src/api/auth.ts — login, logout, refresh
  src/routes/auth/login.tsx:
    Email + password, check scope === "platform"
    Non-platform → "This app is for Liyaqa platform staff only"
  src/routes/__root.tsx:
    Auth guard: no JWT → /auth/login
    On mount: GET /api/v1/auth/me → populate permissions in Zustand
    On 401 → clear + redirect to login
  src/components/shell/Sidebar.tsx:
    Nav items with PermissionGate wrapping each section
  src/components/shell/AppHeader.tsx:
    "Liyaqa Platform" + user name + language toggle + logout
  src/components/common/PermissionGate.tsx
  Verify: npm run dev → login with admin@liyaqa.com → sidebar visible
    Login with owner@elixir.com → rejected with scope error

Step 11 — Platform stats home
  src/api/stats.ts — getStats()
  src/routes/index.tsx:
    6 KpiCard components in 3×2 grid
    MRR card with tooltip explaining "estimated"
    Refresh button, generatedAt timestamp
    PermissionGate: platform:stats:view (auditor + super admin see this)
  src/components/stats/KpiCard.tsx
  Verify: npm run dev → home shows counts for Liyaqa Demo Org data

Step 12 — Organizations screens
  src/api/organizations.ts — listOrgs, getOrg, createOrg, updateOrg
  src/routes/organizations/index.tsx:
    Searchable paginated table (OrgTable)
    "New Organization" button (PermissionGate: organization:create)
    OrgForm modal for create
  src/routes/organizations/$orgId.tsx:
    Org detail: name, VAT, ClubTable with metrics
    "Edit" button (PermissionGate: organization:update) → inline OrgForm
    "Add Club" button (PermissionGate: club:create) → ClubForm modal
  src/routes/organizations/$orgId.clubs.$clubId.tsx:
    Club detail: metrics, MRR card, BranchTable
    "Edit" and "Add Branch" (gated)
  src/routes/organizations/$orgId.clubs.$clubId.branches.$branchId.tsx:
    Branch detail: name + counts (read-only)
  Verify: npm run dev → create a new org, navigate into it, see club table

Step 13 — Members search
  src/api/members.ts — searchMembers(q, page), getMember(id)
  src/routes/members/index.tsx:
    Search input (debounced 300ms, min 2 chars, show helper text below)
    MemberSearchResult table with status badge
  src/routes/members/$memberId.tsx:
    Read-only member detail: name, phone, email, club, org, membership status
  src/components/members/MemberSearchResult.tsx
  Verify: npm run dev → search "Ahmed" → Ahmed Al-Rashidi appears

Step 14 — Audit log
  src/api/audit.ts — getAuditLog(params)
  src/routes/audit.tsx:
    Date range filter + action filter + paginated AuditTable
    If response has meta.note → show InfoBanner instead of empty state
  src/components/audit/AuditTable.tsx
  src/components/audit/AuditFilters.tsx
  Verify: npm run dev → audit screen shows info banner (no AuditLog entity yet)

Step 15 — Frontend tests
  Login.test.tsx — rejects non-platform scope
  PermissionGate.test.tsx — hides children when permission absent
  KpiCard.test.tsx — renders value and label correctly
  OrgTable.test.tsx — renders org list, search input visible
  MemberSearchResult.test.tsx — shows correct membership status badge
  Sidebar.test.tsx — support agent: no audit log item; auditor: no org create button
  Verify: npm test

Step 16 — Frontend final checks
  npm run typecheck
  npm run lint
  npm run build
```

---

## Acceptance criteria

### Backend
- [ ] `GET /nexus/organizations` returns Liyaqa Demo Org with correct club count
- [ ] `POST /nexus/organizations` with duplicate name returns 409
- [ ] `GET /nexus/organizations/{id}` returns org detail with clubs
- [ ] `GET /nexus/stats` returns all 7 KPI fields including estimatedMrrSar
- [ ] MRR estimate correctly normalizes quarterly (÷3) and annual (÷12) plans
- [ ] `GET /nexus/members?q=ah` returns Ahmed Al-Rashidi
- [ ] `GET /nexus/members?q=a` (1 char) returns 422
- [ ] `GET /nexus/audit` returns empty page + meta.note (no AuditLog entity)
- [ ] Support Agent calling `GET /nexus/audit` returns 403
- [ ] Integration Specialist calling `GET /nexus/members` returns 403
- [ ] Non-platform JWT calling any nexus endpoint returns 403
- [ ] All 325+ existing tests still pass

### Frontend (web-nexus)
- [ ] Login with `admin@liyaqa.com` → all nav items visible
- [ ] Login with support agent account → no Audit Log nav item
- [ ] Login with club staff token → rejected with platform scope error
- [ ] Home stats screen shows correct KPI values
- [ ] MRR card shows tooltip with "estimated" explanation
- [ ] Org list is searchable and paginated
- [ ] "New Organization" button absent for Support Agent (no create permission)
- [ ] Members search requires 2+ chars, shows helper text below
- [ ] Search "Ahmed" returns Ahmed Al-Rashidi with correct club
- [ ] Audit screen shows info banner (not empty state or error)
- [ ] Arabic RTL layout correct throughout
- [ ] `npm run typecheck`, `npm run lint`, `npm run test`, `npm run build` all pass

---

## RBAC matrix rows added by this plan

| Permission | Super Admin | Support Agent | Integration Specialist | Read-Only Auditor |
|---|---|---|---|---|
| platform:stats:view | ✅ | ❌ | ❌ | ✅ |
| organization:read | ✅ | ✅ | ✅ | ❌ |
| organization:create | ✅ | ❌ | ❌ | ❌ |
| organization:update | ✅ | ❌ | ❌ | ❌ |
| club:read | ✅ | ✅ | ✅ | ❌ |
| club:create | ✅ | ❌ | ❌ | ❌ |
| club:update | ✅ | ❌ | ❌ | ❌ |
| branch:read | ✅ | ✅ | ✅ | ❌ |
| branch:create | ✅ | ❌ | ❌ | ❌ |
| branch:update | ✅ | ❌ | ❌ | ❌ |
| member:read (cross-org) | ✅ | ✅ | ❌ | ❌ |
| audit:read | ✅ | ❌ | ❌ | ✅ |

---

## Definition of done

- All acceptance criteria checked
- All 8 business rules covered by unit or integration tests
- Platform scope rejection tested (non-platform token → error message)
- MRR normalization tested for monthly, quarterly, and annual plan types
- Cross-org member search tested with min-chars validation
- Audit screen gracefully handles missing AuditLog entity
- web-nexus runs independently on port 5173 (`cd web-nexus && npm run dev`)
- All CI checks pass on PR
- PLAN.md deleted before merging
- PR title: `feat(nexus): implement internal platform dashboard with org management and stats`
- Target branch: `develop`

