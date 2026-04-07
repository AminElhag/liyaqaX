# PROJECT-STATE.md — Liyaqa Full Project Knowledge Base

This file is the single source of truth about the Liyaqa project.
Read this at the start of every Cowork session.

---

## 1. What Is Liyaqa

Multi-tenant SaaS gym management platform for Saudi Arabia.
Manages clubs, members, staff, trainers, memberships, PT, GX, finances.

**Key facts:**
- Target market: Saudi Arabia
- Default language: Arabic (RTL), English secondary
- Currency: SAR stored as halalas (1 SAR = 100 halalas, BIGINT)
- VAT rate: 15% (Saudi standard)
- Compliance requirement: ZATCA e-invoicing
- Tenant hierarchy: Organization → Club → Branch

---

## 2. Repository Structure

```
/
├── CLAUDE.md                    ← Global rules (Claude Code reads this first)
├── docker-compose.yml           ← PostgreSQL + Redis
├── .env.example
├── package.json                 ← Monorepo stub — NO workspaces field
├── docs/
│   ├── domain-glossary.md
│   ├── rbac.md
│   └── adr/                     ← ADRs 0001–0014
├── backend/                     ← Spring Boot / Kotlin
│   ├── CLAUDE.md
│   ├── DATABASE.md
│   ├── API.md
│   ├── TEMPLATES.md
│   └── src/
├── web-nexus/                   ← Internal dashboard (port 5173) ← COMPLETE
├── web-pulse/                   ← Club staff app (port 5174) ← COMPLETE
├── web-coach/                   ← Trainer app (port 5175) ← COMPLETE
├── web-arena/                   ← Member portal (port 5176) ← COMPLETE
└── mobile-arena/                ← KMP/CMP mobile ← NOT STARTED
```

---

## 3. Technology Stack

### Backend
- Kotlin + Spring Boot 3.x
- PostgreSQL 16, Redis 7
- Gradle (Kotlin DSL only — build.gradle.kts)
- Flyway migrations (currently V5)
- JJWT for JWT, Testcontainers for tests
- ddl-auto: create-drop in dev (no migration needed for dev)

### Frontend (all four web apps)
- React 18, TypeScript strict, Vite
- TanStack Router (file-based) + TanStack Query
- Zustand (UI state only — never server state)
- React Hook Form + Zod
- Tailwind CSS, i18next (Arabic RTL default)
- Vitest + Testing Library

### Mobile
- Kotlin Multiplatform (KMP) + Compose Multiplatform (CMP)

### Package manager
- Backend: Gradle
- Frontend: **npm** (NOT pnpm — pnpm caused persistent issues)
- Each frontend app has its own package-lock.json in its own directory
- Root package.json has NO workspaces field

---

## 4. Architecture Decisions (All 14 ADRs)

| # | Title | Decision |
|---|---|---|
| 0001 | Public IDs | UUID v4 for all API-facing IDs; BIGINT PKs internally |
| 0002 | Money | BIGINT halalas — never DECIMAL/FLOAT |
| 0003 | Auth | Stateless JWT — access 15min, refresh 7 days |
| 0004 | Migrations | Flyway versioned SQL files |
| 0005 | Multi-tenancy | Shared schema, organization_id on every table |
| 0006 | Packaging | Feature-based: com.liyaqa.member not com.liyaqa.service |
| 0007 | Errors | RFC 7807 Problem Details JSON |
| 0008 | API | Single Spring Boot backend for all clients |
| 0009 | ZATCA | Server-side only — never frontend |
| 0010 | Mobile | KMP + CMP for Android and iOS |
| 0011 | Versioning | URL path: /api/v1/ |
| 0012 | Deletes | Soft delete via deleted_at nullable timestamp |
| 0013 | Language | Arabic default, English secondary |
| 0014 | RBAC | Fully dynamic per-club roles/permissions, Redis cache |

---

## 5. RBAC System

**Fully dynamic** — no hardcoded role strings in application code.

### How it works
1. User logs in → JWT contains `roleId` (UUID) + `scope` string
2. Every request → backend resolves permissions from Redis cache
3. Cache key: `role_permissions:{roleId}` TTL: 5 minutes
4. If cache miss → fetch from DB → cache result

### JWT shapes

Platform user:
```json
{ "sub": "uuid", "roleId": "uuid", "scope": "platform" }
```

Club staff:
```json
{ "sub": "uuid", "roleId": "uuid", "scope": "club",
  "organizationId": "uuid", "clubId": "uuid", "branchIds": ["uuid"] }
```

Trainer:
```json
{ "sub": "uuid", "roleId": "uuid", "scope": "trainer",
  "trainerTypes": ["pt","gx"], "trainerId": "uuid",
  "organizationId": "uuid", "clubId": "uuid", "branchIds": ["uuid"] }
```

Member:
```json
{ "sub": "uuid", "roleId": "uuid", "scope": "member",
  "memberId": "uuid", "organizationId": "uuid",
  "clubId": "uuid", "branchId": "uuid" }
```

### Key permission codes
```
organization:create/read/update/delete
club:create/read/update/delete
branch:create/read/update/delete
staff:create/read/update/delete
member:create/read/update/delete
membership:create/read/update/freeze/unfreeze
payment:collect/read/refund
invoice:read/generate
pt-package:create/read
pt-session:create/read/update/mark-attendance
gx-class:create/read/update/manage-bookings/mark-attendance
lead:create/read/update/convert
report:revenue:view/retention:view/utilization:view
role:create/read/update/delete
system:impersonate
audit:read
```

### Default seeded roles
Platform: Super Admin, Support Agent, Integration Specialist, Read-Only Auditor
Club: Owner, Branch Manager, Receptionist, Sales Agent, PT Trainer, GX Instructor
Member: Member

All seeded roles have `isSystem = true` — cannot be deleted via UI.

---

## 6. What Has Been Built (Completed Plans)

### Backend

| Domain | Entities | Key endpoints |
|---|---|---|
| Auth | User, UserRole | POST /api/v1/auth/login, /refresh, /logout, GET /me |
| Organization | Organization | CRUD /api/v1/organizations |
| Club | Club | CRUD /api/v1/organizations/{id}/clubs |
| Branch | Branch | CRUD /api/v1/organizations/{id}/clubs/{id}/branches |
| RBAC | Role, Permission, RolePermission | (seeded, no management API yet) |
| Staff | StaffMember, StaffBranchAssignment | CRUD /api/v1/staff (Pulse + Nexus) |
| Trainer | Trainer, TrainerBranchAssignment, TrainerCertification | CRUD /api/v1/trainers |
| Member | Member, EmergencyContact, HealthWaiver, WaiverSignature | CRUD /api/v1/members |
| MembershipPlan | MembershipPlan | CRUD /api/v1/membership-plans |
| Membership | Membership, FreezePeriod | CRUD + renew/freeze/unfreeze/terminate |
| Payment | Payment | POST + GET /api/v1/members/{id}/payments |
| Invoice | Invoice, InvoiceCounter | GET /api/v1/members/{id}/invoices, GET /api/v1/invoices/{id}/qr-code |
| Lead | Lead, LeadNote, LeadSource | CRUD /api/v1/leads, /api/v1/lead-sources |
| Cash Drawer | CashDrawerSession, CashDrawerEntry | /api/v1/cash-drawer/sessions, /entries |
| Reports | (read-only, no new entities) | /api/v1/reports/revenue|retention|leads|cash-drawer + /export |
| PT | PTPackageCatalog, PTPackage, PTSession | /api/v1/pt/catalog, /api/v1/pt-sessions |
| GX | GXClassType, GXClassInstance, GXBooking, GXAttendance, GXWaitlistEntry | /api/v1/gx/*, /api/v1/arena/gx/*/waitlist |
| Portal | ClubPortalSettings | GET/PATCH /api/v1/portal-settings |
| Arena Auth | MemberOtp | POST /api/v1/arena/auth/otp/request, /otp/verify, /logout |
| Arena Profile | (Member + existing entities) | GET /api/v1/arena/me, PATCH /api/v1/arena/profile, GET /api/v1/arena/membership, GET /api/v1/arena/portal-settings |
| Arena GX | (GXClassInstance, GXBooking) | GET /api/v1/arena/gx/schedule, POST/DELETE /api/v1/arena/gx/{id}/book, GET /api/v1/arena/gx/bookings |
| Arena PT | (PTPackage, PTSession) | GET /api/v1/arena/pt/sessions, GET /api/v1/arena/pt/packages |
| Arena Invoices | (Invoice) | GET /api/v1/arena/invoices, GET /api/v1/arena/invoices/{id} |
| Coach Profile | (Trainer, TrainerCertification) | GET /api/v1/coach/me |
| Coach Schedule | (PTSession, GXClassInstance) | GET /api/v1/coach/schedule |
| Coach PT | (PTSession) | GET /api/v1/coach/pt/sessions, PATCH /api/v1/coach/pt/sessions/{id}/attendance |
| Coach GX | (GXClassInstance, GXBooking) | GET /api/v1/coach/gx/classes, GET /api/v1/coach/gx/classes/{id}/bookings, PATCH /api/v1/coach/gx/classes/{id}/attendance |
| Nexus Orgs | (Organization, Club, Branch) | CRUD /api/v1/nexus/organizations, .../clubs, .../branches |
| Nexus Members | (Member, Membership) | GET /api/v1/nexus/members (cross-org search), GET /api/v1/nexus/members/{id} |
| Nexus Stats | (aggregate) | GET /api/v1/nexus/stats |
| Nexus Audit | AuditLog | GET /api/v1/nexus/audit (paginated, filterable) |
| Role Management | Role, Permission, RolePermission, UserRole | CRUD /api/v1/nexus/roles, /api/v1/roles, PATCH /api/v1/staff/{id}/role |
| Custom Reports | ReportTemplate, ReportResult | CRUD /api/v1/report-templates, POST /run, GET /result, GET /export, GET /reports/meta/* |
| Scheduled Reports | ReportSchedule | CRUD /api/v1/report-templates/{id}/schedule, GET /export/pdf |
| Notifications | Notification | GET /api/v1/pulse/notifications, /arena/notifications, /coach/notifications + mark-read, mark-all-read, unread-count |

**Current test count: 521+ backend tests + 147 frontend tests (web-pulse) + 18 frontend tests (web-nexus)**
**Current Flyway migrations: V1 through V17** (V7 = skipped/reserved, V8 = lead_sources/leads/lead_notes, V9 = cash_drawer_sessions/cash_drawer_entries, V10 = audit_logs, V11 = report_templates + report_results, V12 = report_schedules, V13 = notifications, V14 = member_self_registration, V15 = zatca_phase2, V16 = member_import, V17 = gx_waitlist)
⚠️ NOTE: V7 was skipped.

### New entities added (web-arena plan)
- `ClubPortalSettings` — per-club feature flags: gxBookingEnabled, ptViewEnabled, invoiceViewEnabled, onlinePaymentEnabled, portalMessage
- `MemberOtp` — phone OTP tracking: phone, otpHash (SHA-256), expiresAt, used, memberId
- `Member.preferredLanguage` — VARCHAR(10), nullable, 'ar' or 'en'

### New permission added
- `portal-settings:update` — assigned to Owner and Branch Manager

### Frontend — web-pulse (complete through Plan 18)

Screens built:
- Login page with scope check (rejects non-club users)
- App shell: collapsible sidebar, topbar, branch selector
- Staff list + detail
- Member list + registration form (2-step wizard) + profile
- Membership plans catalog (card grid, create/edit)
- Member membership tab (assign plan, payment, history)
- Member PT tab (packages, sessions)
- Member GX tab (bookings)
- /pt → weekly calendar, packages catalog, trainer utilization
- /gx → weekly schedule grid, class detail, attendance
- /memberships → renewals queue (expiring members)
- Invoice detail with QR code (ZATCA Phase 1)
- Invoice print view (Arabic, ZATCA-compliant)
- Payments tab updated with invoice list + ZATCA status badges
- Lead list with filters + pagination
- Lead kanban board (drag-and-drop stage transitions)
- Lead detail + notes timeline + convert-to-member flow
- Lead Sources settings page (add/toggle/reorder)
- Cash drawer current session (open/add entries/close)
- Cash drawer session detail + reconciliation modal
- Cash drawer history with filters
- Reports hub with permission-gated cards
- Revenue report (line chart, KPIs, CSV export, comparison period)
- Retention report (bar chart, atRisk table, churn rate)
- Lead funnel report (stage breakdown, top sources, lost reasons)
- Cash drawer summary report (bar + line chart, shortages/surpluses)

**Frontend test count: 112 tests**

---

## 7. Seed Data (Dev Profile)

```
Organization: "Liyaqa Demo Org" / "مؤسسة لياقة التجريبية"
Club:         "Elixir Gym" / "نادي إكسير" (vatNumber = "300000000000003")
Branch 1:     "Elixir Gym - Riyadh"
Branch 2:     "Elixir Gym - Jeddah"

Users:
  admin@liyaqa.com      Admin1234!   → Super Admin (platform)
  owner@elixir.com      Owner1234!   → Owner (club)
  manager@elixir.com    Manager1234! → Branch Manager
  reception@elixir.com  Recept1234!  → Receptionist
  sales@elixir.com      Sales1234!   → Sales Agent
  pt@elixir.com         Trainer1234! → PT Trainer (Khalid Al-Otaibi)
  gx@elixir.com         Trainer1234! → GX Instructor (Noura Al-Harbi)
  member@elixir.com     Member1234!  → Member (Ahmed Al-Rashidi)

Plans: Basic Monthly 150 SAR, Quarterly 399 SAR, Annual 1299 SAR
Ahmed: Active Basic Monthly + 10-session PT package + Monday Yoga booking
PT catalog: 10-session (1500 SAR), 20-session (2800 SAR)
GX types: Yoga (purple), HIIT (red), Spinning (amber)
GX schedule: 5 instances next week at Riyadh branch
```

---

## 8. Known Critical Issues (Warn Claude Code About These)

### JPQL query bug (recurring — 4 times so far)
**Symptom:** ALL integration tests fail at Spring context startup
with `QueryCreationException` → `SemanticException`.
**Cause:** A `@Query` annotation uses JPQL syntax for date/time
operations that Hibernate rejects.
**Rule:** For ANY query involving dates, intervals, functions like
YEAR(), EXTRACT(), or type arithmetic → ALWAYS use `nativeQuery = true`
with SQL table/column names (snake_case).
**Also:** Native queries with Pageable require a separate `countQuery`.
**Files fixed so far:** PTSessionRepository, GXClassInstanceRepository,
MembershipRepository, InvoiceRepository.

### Test passwords (security)
Test files must use constants, never inline strings:
`const val TEST_PASSWORD = "Test@12345678"`
Never: `"password123"`, `"Pass1234!"`, `"correctpass"` etc.

### npm lockfile location
Each frontend app needs its own package-lock.json.
Run `npm install` from INSIDE the app directory.
Root package.json must NOT have a `workspaces` field.

---

## 9. Remaining Roadmap

### ✅ ZATCA Phase 1 — COMPLETE (all 14 steps done)
- TLV QR code (tags 0x01–0x05), PIH chain, invoice counter
- zatcaStatus = "generated" on creation
- QR code, invoice detail, Arabic print view in web-pulse

### ✅ Plan 14 — Lead Pipeline — COMPLETE (all 16 steps done)
- Lead + LeadNote + LeadSource entities
- 4-stage pipeline (new → contacted → interested → converted/lost)
- Configurable sources per club (staff with lead-source:update permission)
- Convert-to-member flow (atomic, idempotent)
- Kanban board + lead list + lead detail + sources settings in web-pulse

### ✅ Plan 16 — Cash Drawer Reconciliation — COMPLETE (all 15 steps done)
- CashDrawerSession + CashDrawerEntry entities
- Shift-based: open → entries → close (counted balance) → reconcile (manager)
- Expected balance computed server-side at close; difference stored
- 3 entry types: cash_in, cash_out, float_adjustment (free-text description)
- Current session screen + session detail + history in web-pulse

### ✅ Plan 18 — Reports & Analytics — COMPLETE (all 19 steps done)
- 4 reports: revenue, retention & churn, lead funnel, cash drawer summary
- All time breakdowns: day/week/month/custom range, auto-selected
- KPI cards + Recharts charts + data table + CSV export per report
- Per-report permissions (Sales Agent sees leads, not revenue)
- No new migrations — pure read-only aggregations over existing data
- 296 backend tests, 112 frontend tests

### ✅ Plan 20 — Scheduled Report Emails + PDF Exports — COMPLETE (all 11 steps done)
- `ReportSchedule` entity — one per template (UNIQUE), Flyway V12, pause/resume via `isActive`
- `ReportPdfService` — iText 7 Community, tabular PDF layout, 1,000-row cap with truncation note
- `ReportEmailService` — JavaMailSender, HTML email with PDF attachment
- `ReportSchedulerService` — `@Scheduled` cron 04:00 UTC (= 07:00 Riyadh), Redis `SETNX` lock, daily/weekly/monthly frequency logic
- Mailpit added to docker-compose — all dev emails catchable at localhost:8025
- `GET /export/pdf` on-demand PDF endpoint added to existing export controller
- web-pulse: Schedule tab on template detail (ScheduleForm + ScheduleBadge), Export PDF button, schedule badge column in template list
- 4 new AuditAction codes: REPORT_SCHEDULE_CREATED/UPDATED/DELETED, REPORT_EMAIL_SENT
- 21 new tests (9 integration + 12 unit) — 424+ backend tests, 135 frontend tests

### ✅ Custom Report Builder (Plan 19) — COMPLETE (all 12 steps done)
- `ReportTemplate` + `ReportResult` entities — Flyway V11, per-club, soft-deleted
- `MetricCatalogue` (16 codes), `DimensionCatalogue` (8 codes), `FilterCatalogue` (5 codes), `CompatibilityMatrix` — all pre-approved SQL fragments, no user input ever touches SQL
- `ReportBuilderService` — safe parameterized native query assembly, Redis 10-min caching (key = templateId + SHA-256 of params), soft-deletes old results on each run, 50,000 row cap
- `MetaReportPulseController` (3 catalogue endpoints) + `ReportBuilderPulseController` (8 endpoints)
- `metricScope` gate: Sales Agent can only run `leads`-scoped templates
- 2 seeded system templates: "Monthly Revenue by Branch", "Lead Conversion by Source"
- web-pulse: `/reports/builder` list, `/new` builder form with compatibility warnings, `/$templateId` run + result + "Cached result" badge + CSV export
- 3 new AuditAction codes: REPORT_TEMPLATE_CREATED, REPORT_TEMPLATE_DELETED, REPORT_RUN
- 13 backend tests (8 service + 5 builder), 4 frontend tests — all pass
- 388+ backend tests, 130 frontend tests

### ✅ Role Management UI — COMPLETE (all 9 steps done)
- `Role.isSystem` field added — all 11 seeded roles protected from deletion
- `RoleManagementService` — create/update/delete roles, permission diff + replace, Redis cache invalidation on every write
- 7 new controllers: nexus (platform roles + permissions + permission list), pulse (club roles + permissions + permission list + staff role assignment)
- web-nexus: Roles section with grouped permission checkboxes (by domain prefix), system lock badges, "Save All" with 5-min cache toast
- web-pulse: Settings → Roles, plus "Change Role" on staff detail page (Owner only)
- Redis invalidation: both old AND new roleId keys invalidated on staff role reassignment
- 6 new AuditAction codes: ROLE_CREATED/UPDATED/DELETED, ROLE_PERMISSION_ADDED/REMOVED, STAFF_ROLE_ASSIGNED
- 3 test files covering all 10 business rules
- 375+ backend tests, 131 frontend tests

### ✅ Audit Logging — COMPLETE (all 8 steps done)
- `AuditLog` entity — append-only, no soft delete, no AuditEntity extension, Flyway V10
- `AuditService.log()` — never throws, truncates changesJson at 4000 chars, extracts actor from SecurityContext
- 27 `AuditAction` codes wired into 12 existing services across 10 domains
- `AuditNexusController` updated — real paginated data with filters (actor, action, entityType, org, date range)
- web-nexus audit screen now shows live data (meta.note banner removed)
- Services wired: Member, Membership, Payment, Staff, Trainer, Lead, CashDrawer, GxArena, PtCoach, MemberAuth
- MEMBER_LOGIN audited on success only — failed OTP attempts not logged
- 7 existing test files updated to mock AuditService for constructor injection
- 356 backend tests, 0 failures, ktlint clean

### ✅ web-nexus — Internal Platform Dashboard — COMPLETE (all 16 steps done)
- Email + password login with scope check (rejects non-platform tokens)
- Collapsible sidebar with PermissionGate on every nav item (4 roles, different visibility)
- Platform stats home: 6 KPI cards (orgs, clubs, branches, active members, memberships, estimated MRR)
- MRR normalization: monthly ×1, quarterly ÷3, annual ÷12 — labeled "estimated" with tooltip
- Organizations → Clubs → Branches drill-down with create/edit (Super Admin only)
- Cross-org member search: min 2 chars, ILIKE across name/phone/email, max 50 results
- Audit log screen: graceful empty + info banner (no AuditLog entity yet — never 500s)
- New package: com.liyaqa.nexus — NexusAuthHelper + 6 controllers + 3 services + 10 DTOs
- New permissions: platform:stats:view (Super Admin + Auditor), member:read cross-org (Support Agent)
- 4 integration test files, 19 test cases covering all 8 business rules
- 344+ backend tests, 126 frontend tests (7 new)

### ✅ web-coach — Trainer Dashboard — COMPLETE (all 15 steps done)
- Email + password login with scope check (rejects non-trainer tokens)
- Sidebar layout gated by trainerTypes (PT-only trainer sees no GX tab, GX-only sees no PT tab)
- Today's Schedule: combined PT + GX timeline with date picker (max 30 days ahead)
- PT Sessions: upcoming/past tabs with mark attended/missed modal
- GX Classes: weekly grid + class detail with bulk attendance marking (disabled for future classes)
- Profile: trainer info, type badges, certifications with "Expires soon" warning (< 30 days)
- New package: com.liyaqa.coach — CoachAuthHelper + 4 controllers + 8 DTOs
- 3 integration test classes, 20 test cases covering all 8 business rules
- 325+ backend tests, 119 frontend tests (7 new)

### ✅ web-arena — Member Self-Service Portal — COMPLETE (all 18 steps done)
- Phone OTP login (SHA-256 hashed, 10-min TTL, 3/10min rate limit, no phone existence leak)
- Language selection screen (Arabic-first, shown once when preferredLanguage is null)
- Mobile-first app shell: bottom nav, header, portal-settings-gated nav items
- Home dashboard: membership card, quick actions, portal message
- GX schedule + book/cancel with capacity/duplicate/past-class enforcement
- PT sessions + packages view
- Invoices list + detail with ZATCA QR code
- Profile screen with Arabic name fields + language toggle
- ClubPortalSettings per-club feature flags (gxBookingEnabled, ptViewEnabled, invoiceViewEnabled)
- Portal-settings management screen added to web-pulse (Owner + Branch Manager)
- 305+ backend tests, 112 frontend tests

### ✅ Plan 21 — Notification System — COMPLETE (all 12 steps done)
- `Notification` entity — append-only, no soft delete, 90-day hard delete via scheduler, Flyway V13
- 12 notification types (MEMBERSHIP_EXPIRING_SOON, PAYMENT_COLLECTED, GX_CLASS_BOOKED, GX_CLASS_CANCELLED, GX_CLASS_REMINDER, PT_SESSION_REMINDER, LOW_GX_SPOTS, LEAD_ASSIGNED, MEMBER_JOINED, MEMBERSHIP_FROZEN, MEMBERSHIP_TERMINATED, INVOICE_GENERATED)
- 8 Spring ApplicationEvent classes — MembershipService, PaymentService, GxArenaController, PtCoachController, LeadService, MemberService each publish typed events
- `NotificationTriggerService` — @EventListener for all 8 events, all try/catch, WARN on failure (never throws)
- `NotificationSchedulerService` — @Scheduled 06:00 UTC daily: expiring memberships (7 days ahead), PT session reminders (24h ahead), low GX spots (< 20% capacity), 90-day cleanup
- Deduplication: proactive notifications check last 24h for same (type + entityId) pair before creating
- Redis unread count: key `notification_unread:{userId}` — INCR on create, DECR on mark-read, SET 0 on mark-all-read
- 3 controllers: NotificationPulseController (scope=club), NotificationArenaController (scope=member), NotificationCoachController (scope=trainer)
- Email sent for MEMBERSHIP_EXPIRING_SOON, PAYMENT_COLLECTED, PT_SESSION_REMINDER via JavaMailSender (reused from Plan 20)
- Frontend: bell + drawer in web-pulse and web-coach; bell → full-page /notifications in web-arena; 30-second polling
- 12 new backend tests (7 service + 5 trigger) — 437+ total; 8 new frontend tests in web-pulse — 143 total
- 1 pre-existing flaky test in AuditNexusControllerTest (not introduced by this plan)

### ✅ Plan 31 — ZATCA Health Dashboard & CSID Expiry Alerts — COMPLETE (all 10 steps done)
- 2 new notification types: `ZATCA_CSID_EXPIRING_SOON`, `ZATCA_INVOICE_DEADLINE_AT_RISK` — scheduler-driven, platform admins only
- `ZatcaHealthService` — aggregates 6 health KPIs across all clubs (active CSIDs, expiring, not onboarded, pending, failed, deadline-at-risk)
- `ZatcaRetryService` — resets failed invoices (status → generated, retryCount → 0, lastError → null) with audit logging
- `FailedZatcaInvoiceProjection` — interface projection for efficient multi-table join queries
- 8 new native queries across `ClubZatcaCertificateRepository` and `InvoiceRepository` — all `nativeQuery = true`
- 2 new scheduler jobs in `ZatcaReportingScheduler`: CSID expiry alert (daily 04:00 UTC), deadline risk alert (hourly)
- `ZATCA_CSID_RENEWED` + `ZATCA_INVOICE_RETRY_REQUESTED` audit actions, CSID renewal wired into `ZatcaOnboardingService`
- `zatca:retry` permission seeded for Super Admin (NexusAdmin) only
- 4 new endpoints on `ZatcaNexusController`: GET /health, GET /invoices/failed, POST /invoices/{id}/retry, POST /clubs/{id}/retry-all
- web-nexus: 6 health cards (color-coded, 60s auto-refresh), tab layout (Clubs + Failed Invoices), retry buttons with permission gate
- 19 new backend tests (3 unit classes + 1 integration class), 5 new frontend tests — 502+ backend tests, 18 frontend tests
- 1 pre-existing flaky test in AuditNexusControllerTest (not introduced by this plan)

### ✅ Plan 25 — GX Class Waitlist — COMPLETE (all 9 steps done)
- `GXWaitlistEntry` entity — Flyway V17, operational (no soft delete), `uq_waitlist_member_class` unique constraint
- `GXWaitlistStatus` enum: WAITING, OFFERED, ACCEPTED, EXPIRED, CANCELLED
- `GXWaitlistService` — join, accept, leave, promoteNext, cancelAllForClass, staffRemoveEntry (15 business rules)
- `GXWaitlistScheduler` — hourly `@Scheduled(fixedDelay)`, expires OFFERED entries older than 2 hours, immediately promotes next
- 3 new notification types: `GX_WAITLIST_OFFERED`, `GX_WAITLIST_EXPIRED`, `GX_WAITLIST_CONFIRMED`
- 5 new endpoints: 3 arena (POST/DELETE waitlist, POST accept) + 2 pulse (GET waitlist-entries, DELETE entry)
- Spot-opening hooks: booking cancellation (arena + pulse), staff removal, capacity increase, class cancellation
- Race condition handling: acceptOffer reverts to WAITING if class full at accept time (409)
- web-arena: Join Waitlist button, position badge, amber OFFERED banner, Accept Spot CTA, Waitlist tab on bookings
- web-pulse: waitlist count on GX class card, Waitlist tab on class detail with Remove button
- 19 new backend tests (15 service + 4 scheduler), 10 integration tests, 6 frontend tests (4 arena + 2 pulse)

### Next — Remaining roadmap
```
mobile-arena — KMP/CMP mobile app (Android + iOS) — not started (parked)
Plan 17 — ZATCA Phase 2 (Fatoora API) — blocked (needs ZATCA certificates per club)
Plan 22 — Member self-registration — allow members to register without staff
```

### Mobile
```
Plan 21 — mobile-arena (KMP/CMP) — native iOS + Android member app
```

---

## 10. ZATCA Phase 1 — COMPLETE

**Shipped:** Phase 1 (generation only — no Fatoora API submission).
**Phase 2** (Fatoora API, UBL XML, SDK signing) is a separate future plan.

### What was built
- `ZatcaTlvEncoder` — pure Kotlin TLV encoding, tags 0x01–0x05
- `ZatcaHashUtil` — SHA-256 canonical string hash + INITIAL_PIH constant
- `ZatcaQrService` — orchestrates QR generation per invoice
- `InvoiceCounter` entity — per-club atomic sequential counter (cannot reset)
- `createInvoice` — full 11-step ZATCA flow replaces old stub
- PIH chain: each invoice stores hash of previous; first invoice uses INITIAL_PIH
- Club.vatNumber added; fallback: club → org → "PENDING-VAT-REGISTRATION"

### Invoice entity ZATCA fields (all populated on creation)
```kotlin
var zatcaStatus: String = "generated"
var zatcaUuid: String?               // = invoice.publicId.toString()
var zatcaHash: String?               // SHA-256 of canonical string
var zatcaQrCode: String?             // base64 TLV, tags 0x01–0x05
var previousInvoiceHash: String?     // PIH chain
var invoiceCounterValue: Long?       // sequential per club
```

### TLV QR code fields (Phase 1, in order)
```
Tag 0x01: Seller name (organization.nameAr, UTF-8)
Tag 0x02: VAT registration number (club.vatNumber ?: org.vatNumber)
Tag 0x03: Invoice timestamp (issuedAt, ISO 8601 UTC with Z)
Tag 0x04: Invoice total with VAT (SAR decimal string, 2dp)
Tag 0x05: VAT amount (SAR decimal string, 2dp)
```
Tags 0x06–0x09 are Phase 2 only (ECDSA signature, public key, CA stamp).

### Phase 2 data foundation (ready)
All fields needed by Phase 2 SDK are already stored:
invoice_counter_value, previous_invoice_hash, zatca_uuid, zatca_hash, zatca_qr_code.
When Phase 2 ships, SDK replaces zatca_hash and zatca_qr_code with signed versions.

---

## 11. How Plans Are Written

Every PLAN.md Claude writes for this project must follow this structure:

```markdown
# PLAN.md — [Feature Name]

## Status
Ready for implementation

## Branch
feat/[branch-name]

## Goal
[What this plan builds and why — 2-3 sentences]

## Context
[What already exists that this plan extends]

## Scope — what this plan covers
- [ ] Backend item 1
- [ ] Frontend item 1

## Out of scope — do not implement in this plan
- item A
- item B

## Decisions already made
- Decision 1 (with reasoning if non-obvious)

## Entity design
[New JPA entities with all field definitions]

## API endpoints
[HTTP method + path for every new endpoint]

## Request / Response shapes
[JSON examples for key DTOs]

## Business rules — enforce in service layer
1. Rule one — what to check, what to return on violation
2. Rule two

## Seed data updates
[What gets added/changed in DevDataLoader]

## Frontend additions
[Screens, components, API functions if applicable]

## Files to generate
### New files
[list]
### Files to modify
[list]

## Implementation order
Step 1 — [name]
  [specific files and what to implement]
  Verify: [exact command]

Step 2 — ...

## Acceptance criteria
### Backend
- [ ] criterion

### Frontend (if applicable)
- [ ] criterion

## RBAC matrix rows added by this plan
[table with permission codes as rows]

## Definition of done
- All acceptance criteria checked
- All tests pass (backend: ./gradlew test, frontend: npm test)
- ktlintCheck passes
- PLAN.md deleted before merging
- PR title: feat([domain]): [description]
- Target branch: develop
```

---

## 12. Service Ports

| Service | Port |
|---|---|
| Backend | 8080 |
| web-nexus | 5173 |
| web-pulse | 5174 |
| web-coach | 5175 |
| web-arena | 5176 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| Mailpit SMTP | 1025 |
| Mailpit Web UI | 8025 |
