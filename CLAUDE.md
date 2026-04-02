# CLAUDE.md — Global Monorepo Rules

This file applies to the entire repository. Every subfolder inherits these rules.
Subfolder-specific `CLAUDE.md` files extend (never contradict) what is defined here.

## Reference documents

The following documents are part of this project and must be
read alongside the relevant CLAUDE.md in every session:

| File | Purpose |
|---|---|
| `docs/domain-glossary.md` | Authoritative definitions for all business terms |
| `docs/rbac.md` | Cross-system role and permission rules |
| `backend/DATABASE.md` | Database standards and migration rules |
| `backend/API.md` | REST API design rules and OpenAPI standards |
| `backend/TEMPLATES.md` | Backend file generation templates |
| `docs/adr/` | All architecture decision records |

When a term, a permission, a database convention, or an API
pattern is used in a PLAN.md or in code, it must be consistent
with the definitions in these documents.

---

## CRITICAL — Development workflow rule

**Do not write any code, create any files, scaffold any structure, or implement any feature unless explicitly instructed to do so.**

This project is built step by step. The `CLAUDE.md` files across this monorepo define the architecture, conventions, and rules — they are planning documents, not implementation triggers.

When a task is given:
- Discuss, clarify, and confirm the scope first if anything is ambiguous.
- Wait for an explicit instruction such as "implement this", "build this", "create this", or "write the code for this" before producing any implementation.
- Proposing an approach or asking a clarifying question is always appropriate. Writing code before being asked is not.

This rule applies to every file in every subproject in the monorepo, including `backend/`, `web-*/`, and `mobile-arena/`.

---

## 1. Repository layout

```
/
├── CLAUDE.md                        ← this file (global rules)
├── docker-compose.yml               ← full local stack
├── docker-compose.override.yml      ← local dev overrides (gitignored)
├── .env.example                     ← template for all env vars (committed)
├── .env                             ← actual secrets (gitignored)
├── .editorconfig                    ← enforced editor settings
├── .gitignore
├── README.md
├── CHANGELOG.md
├── docs/
│   └── adr/                         ← Architecture Decision Records
│
├── backend/                         ← Spring Boot / Kotlin API
│   ├── CLAUDE.md
│   └── src/
│
├── web-nexus/                       ← React + TS + Vite — internal platform dashboard (our team only)
│   ├── CLAUDE.md
│   └── src/
│
├── web-pulse/                       ← React + TS + Vite — club operations dashboard (club owner + staff)
│   ├── CLAUDE.md
│   └── src/
│
├── web-coach/                       ← React + TS + Vite — trainer dashboard (trainers only)
│   ├── CLAUDE.md
│   └── src/
│
├── web-arena/                       ← React + TS + Vite — member self-service portal
│   ├── CLAUDE.md
│   └── src/
│
└── mobile-arena/                    ← KMP + CMP — member mobile app (Android + iOS) [git submodule]
    ├── CLAUDE.md
    ├── androidApp/
    ├── iosApp/
    └── shared/
```

- Never create files at the repo root that belong to a specific service.
- Services communicate over HTTP only. Never import code across service boundaries.
- `mobile-arena` is a **git submodule** pointing to its own repository. Always run `git submodule update --init --recursive` after cloning the monorepo. When updating the submodule, commit the updated submodule pointer in the monorepo as a separate commit with scope `chore(mobile-arena): bump submodule to vX.Y.Z`.

---

## 2. RBAC — global requirement for all web apps

Every web application in this monorepo must implement Role-Based Access Control (RBAC).
This is a non-negotiable requirement, not an optional enhancement.

### Rules that apply to every frontend app

- Every user who can log in has exactly one role. The role is returned by the backend on authentication and stored in the app's auth store.
- Roles and their permitted actions are defined in `src/types/permissions.ts` in each app. The role-to-permission map is the single source of truth for that app.
- UI elements that require a permission the current user does not have are **removed from the DOM entirely** — not hidden with CSS, not disabled without explanation. Use a `<PermissionGate>` wrapper component.
- Route-level access control is enforced in the router's `beforeLoad` hook, before the component renders. An unauthorized navigation redirects to a dedicated 403 page.
- The frontend RBAC is UX enforcement only. The backend enforces the same rules independently. Never rely solely on frontend role checks to protect data.
- When a user's role changes (e.g., a staff member is promoted), the change takes effect on their next login. The current session reflects the role it was issued with.
- Every app must have a `read-only` or equivalent low-privilege role that can be assigned to staff who need visibility but no write access.

### Rules that apply to the backend

- Every API endpoint declares which roles may call it. Deny by default.
- Role is extracted from the JWT claims on every request — never from a session or request body.
- Endpoints that perform writes always verify both authentication (valid token) and authorization (correct role) before executing any business logic.
- Admin-level operations (e.g., deleting records, overriding system state) require an explicit elevated role — not just "any authenticated user".

---

## 3. Git workflow

### Branch strategy

```
main              ← production-ready at all times; protected
develop           ← integration branch; all feature branches target here
release/x.y.z     ← cut from develop when preparing a release
hotfix/short-name ← cut from main for urgent production fixes
```

### Branch naming

Pattern: `<type>/<scope>-<short-description>`

| Type | When to use |
|---|---|
| `feat` | New feature |
| `fix` | Bug fix |
| `chore` | Tooling, deps, config — no production code change |
| `refactor` | Code restructure — no behavior change |
| `docs` | Documentation only |
| `test` | Adding or fixing tests |
| `release` | Release preparation |
| `hotfix` | Urgent production fix |

Examples:
```
feat/auth-jwt-refresh
fix/member-subscription-expiry-calculation
chore/upgrade-spring-boot-3.3
refactor/tenant-context-propagation
hotfix/invoice-vat-rounding
```

Rules:
- Lowercase and hyphens only. No underscores, no spaces.
- Maximum 60 characters total.
- Always branch from `develop`, never directly from `main` (except `hotfix` and `release`).
- Delete the branch after it is merged.

### Commit message format — Conventional Commits

```
<type>(<scope>): <short summary in imperative mood>

[optional body — explain WHY, not WHAT]

[optional footer]
BREAKING CHANGE: <description>
Closes #<issue-number>
```

**Types:** `feat` | `fix` | `chore` | `refactor` | `docs` | `test` | `perf` | `ci`

**Scopes** — use the domain or service name:
```
backend  web-nexus  web-pulse  web-coach  web-arena  mobile-arena
auth  membership  billing  invoice  tenant  branch  plan  pt  gx  lead  schedule  messaging  booking
ci  deps  config
```

Examples:
```
feat(membership): add auto-renewal logic for recurring plans
fix(invoice): correct VAT rounding for fractional halala amounts
chore(deps): upgrade Kotlin to 2.0.21
refactor(tenant): move isolation filter to JPA interceptor
test(billing): add integration test for Qoyod payment sync
BREAKING CHANGE: /api/v1/plans renamed to /api/v2/plans with new response shape
```

Rules:
- Summary line: imperative mood (`add`, `fix`, `remove` — not `added`, `fixes`), no trailing period, max 72 characters.
- Scope is the domain, not the service (`feat(auth)` not `feat(backend)`).
- Every breaking change must have `BREAKING CHANGE:` in the footer.
- Reference the issue number in the footer when one exists (`Closes #42`).

### Pull request rules

- Every PR targets `develop` (or `main` for hotfixes only).
- PR title must follow Conventional Commits format — it becomes the squash-merge commit.
- At least one approval required before merging.
- All CI checks must be green. No exceptions.
- One concern per PR. Split unrelated changes into separate PRs.
- PR description must answer: what changed, why, and how to test it.
- No `console.log`, `System.out.println`, commented-out code, or `TODO` without a linked issue number in the diff.

### Merge strategy

| Merge | Strategy | Reason |
|---|---|---|
| Feature → develop | Squash merge | Clean, linear history on develop |
| Develop → release | Merge commit | Preserve integration point |
| Release / hotfix → main | Merge commit + tag | Traceable release boundary |

- Never force-push to `main` or `develop`.

---

## 4. Versioning

Follow **Semantic Versioning**: `MAJOR.MINOR.PATCH`

| Increment | Trigger |
|---|---|
| `MAJOR` | Breaking API or data change, incompatible migration |
| `MINOR` | New backward-compatible feature |
| `PATCH` | Backward-compatible bug fix |

- Version source of truth: `build.gradle.kts` (backend), `package.json` (frontends).
- Every release is tagged in Git: `v1.4.2`.
- `CHANGELOG.md` at repo root is updated with every release. Unreleased changes accumulate under `## [Unreleased]`.

---

## 5. Environment variables & secrets

### .env.example (always committed, always current)

Every environment variable the project needs must be documented here.
Update `.env.example` first whenever adding a new variable.

```dotenv
# ── Backend ───────────────────────────────────────────────────────────────
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/appdb
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=

JWT_SECRET=
JWT_EXPIRY_SECONDS=3600

SPRING_PROFILES_ACTIVE=dev

# ── Frontend — web-nexus (Vite) ───────────────────────────────────────────
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_APP_ENV=development

# ── Frontend — web-pulse (Vite) ───────────────────────────────────────────
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_APP_ENV=development

# ── Frontend — web-coach (Vite) ───────────────────────────────────────────
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_APP_ENV=development

# ── Frontend — web-arena (Vite) ───────────────────────────────────────────
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_APP_ENV=development

# ── External services ─────────────────────────────────────────────────────
QOYOD_API_KEY=
ZATCA_ENV=sandbox
ZATCA_CERTIFICATE=
```

### Rules

- Never commit `.env`, `*.pem`, `*.key`, or any file containing real credentials.
- Never hardcode secrets, API keys, or passwords anywhere in source code.
- In production, secrets are injected via a secrets manager (AWS Secrets Manager, HashiCorp Vault). CI/CD pipelines use the platform's encrypted secret store.
- All Vite frontend env vars must be prefixed `VITE_`. Non-prefixed vars are never exposed to the browser.
- Backend env vars map to `application.yml` via Spring's relaxed binding. Do not duplicate the same config value in both places.

---

## 6. Domain & business conventions

These rules apply to every service, every layer, and every language.

### Monetary values

- All monetary amounts are stored and transmitted as **integers in the smallest currency unit**.
- For SAR: use halalas (1 SAR = 100 halalas). Never use `float` or `double` for money.
- Use `Long` (Kotlin/Java) or `number` (TypeScript, validated as integer) for monetary fields.
- Field naming includes the unit: `priceHalalas`, `totalHalalas`, `depositHalalas`.
- Formatting to SAR display (symbol, decimal separator) happens only at the presentation layer.

### Dates & times

- All datetimes are **UTC** in storage, API transport, and logs.
- API format: ISO 8601 — `2025-03-15T10:30:00Z`.
- Convert to Asia/Riyadh (UTC+3) only at the presentation layer.
- Never store timezone-aware datetimes in the database. Store UTC instants, derive local time on read.
- Kotlin/Java: `Instant` for timestamps, `LocalDate` for calendar dates. TypeScript: ISO 8601 strings with explicit parsing.

### Identifiers

- All public-facing IDs are **UUIDs (v4)**. Never expose auto-increment integers in APIs.
- Database primary keys may be integer internally; the API surface always uses UUIDs.
- UUID fields: `id` on the primary entity, `<entity>Id` on references — `memberId`, `branchId`, `planId`.

### Language & localization

- The system is **bilingual: Arabic (ar) and English (en)**. Both are always required.
- Localized string fields use a consistent suffix pattern: `nameAr` / `nameEn`, `descriptionAr` / `descriptionEn`.
- API responses include both language values. The client selects based on the user's locale preference.
- Arabic content is always RTL. Any frontend component rendering Arabic must set `dir="rtl"` and use logical CSS properties.
- Never hardcode user-facing strings in code. All strings use i18n keys.

### Tenant hierarchy

The data model follows: **Organization → Club → Branch**

- Every tenant-scoped entity carries `organizationId` at minimum.
- Tenant context is always explicit — never inferred from session state or assumed from the request.
- Field names are consistent everywhere: `organizationId`, `clubId`, `branchId`. No abbreviations (`orgId`, `gymId`) and no snake_case variants in application code.

---

## 7. Naming conventions

### Universal (applies across all languages and layers)

| Concept | Pattern | Example |
|---|---|---|
| Boolean fields | `is` / `has` / `can` prefix | `isActive`, `hasExpired`, `canRenew` |
| Timestamp fields | `At` suffix | `createdAt`, `updatedAt`, `deletedAt` |
| ID reference fields | `Id` suffix | `memberId`, `planId`, `organizationId` |
| Monetary fields | unit suffix | `priceHalalas`, `totalHalalas` |
| Localized string fields | language suffix | `nameAr`, `nameEn` |
| Soft delete marker | nullable timestamp | `deletedAt` (null = active) |

### Kotlin / Java (backend)

| Construct | Convention | Example |
|---|---|---|
| Classes, interfaces, enums | PascalCase | `MembershipService`, `TenantContext` |
| Functions, variables | camelCase | `calculateRenewalDate`, `isExpired` |
| Constants, top-level vals | SCREAMING_SNAKE_CASE | `MAX_RETRY_ATTEMPTS` |
| Packages | lowercase, dot-separated | `com.app.membership.service` |
| Database tables | snake_case, plural | `membership_plans`, `branch_members` |
| Database columns | snake_case | `created_at`, `organization_id` |
| API endpoint paths | kebab-case, plural nouns | `/api/v1/membership-plans` |

### TypeScript / React (frontend)

| Construct | Convention | Example |
|---|---|---|
| Components | PascalCase file + export | `MemberCard.tsx` |
| Hooks | `use` prefix, camelCase | `useMembershipPlans.ts` |
| Utilities / helpers | camelCase | `formatCurrency.ts`, `parseIsoDate.ts` |
| Types, interfaces | PascalCase | `MembershipPlan`, `ApiResponse<T>` |
| Enums | PascalCase members | `MemberStatus.Active` |
| Constants | SCREAMING_SNAKE_CASE | `DEFAULT_PAGE_SIZE` |
| Test files | co-located, `.test.ts(x)` suffix | `MemberCard.test.tsx` |

---

## 8. Local development

### Prerequisites

- Docker + Docker Compose
- JDK 21+ (shared by backend and KMP mobile shared module)
- Node.js 20+ — use **pnpm** as the package manager (not npm, not yarn)
- **Mobile only**: Android Studio (latest stable) with the Kotlin Multiplatform plugin
- **Mobile only**: Xcode 15+ (macOS only, required for iOS builds)
- **Mobile only**: CocoaPods (`sudo gem install cocoapods`) for iOS dependency resolution

### First-time setup

```bash
cp .env.example .env           # fill in required values
docker compose up -d           # start PostgreSQL, Redis, and local dependencies
cd backend && ./gradlew flywayMigrate
cd backend && ./gradlew bootRun
cd web-nexus  && pnpm install && pnpm dev
cd web-pulse  && pnpm install && pnpm dev
cd web-coach  && pnpm install && pnpm dev
cd web-arena  && pnpm install && pnpm dev
# Mobile — init submodule first if not done
git submodule update --init --recursive
cd mobile-arena/iosApp && pod install   # macOS only
```

### Service ports

| Service | Local port |
|---|---|
| Backend API | `8080` |
| web-nexus | `5173` |
| web-pulse | `5174` |
| web-coach | `5175` |
| web-arena | `5176` |
| PostgreSQL | `5432` |
| Redis | `6379` |

### Common commands

| Command | Directory | Purpose |
|---|---|---|
| `./gradlew bootRun` | `backend/` | Start API (dev profile) |
| `./gradlew test` | `backend/` | Run all backend tests |
| `./gradlew flywayMigrate` | `backend/` | Apply pending DB migrations |
| `./gradlew flywayInfo` | `backend/` | Show migration status |
| `./gradlew :shared:test` | `mobile-arena/` | Run shared module unit tests |
| `./gradlew :androidApp:assembleDebug` | `mobile-arena/` | Build Android debug APK |
| `./gradlew :shared:iosArm64Test` | `mobile-arena/` | Run shared tests targeting iOS sim |
| `pnpm dev` | `web-*/` | Start Vite dev server |
| `pnpm build` | `web-*/` | Production build |
| `pnpm test` | `web-*/` | Run Vitest |
| `pnpm typecheck` | `web-*/` | TypeScript check (no emit) |
| `pnpm lint` | `web-*/` | ESLint |
| `docker compose up -d` | root | Start all infrastructure |
| `docker compose down -v` | root | Stop and wipe all volumes |

---

## 9. CI/CD pipeline

Every push to any branch triggers this pipeline. All steps must be green before merging.

```
1. Secret scan          ← reject any commit containing credentials or keys
2. Build                ← compile backend; typecheck all frontends
3. Lint                 ← ESLint (frontends), ktlint (backend)
4. Test                 ← full test suite (Gradle + Vitest)
5. Security scan        ← OWASP dependency check, pnpm audit
6. Build Docker images  ← only on develop and main
7. Deploy to staging    ← only on develop (automatic)
8. Deploy to production ← only on main (requires manual approval)
```

- Database migrations run as a **pre-deployment step**, before the new application starts.
- Migrations must be **backward-compatible** with the previous running version to allow safe rollback.
- Build artifacts are **immutable and versioned**. The exact artifact that passed CI is the one deployed to production.
- No deployment to production without a green staging deployment first.

---

## 10. Code quality gates

These are hard requirements — not guidelines. CI enforces them.

### All services

- No secrets detected in source (enforced by secret scanner in CI).
- No `TODO` / `FIXME` without a linked issue number (`// TODO(#123): ...`).
- No commented-out code blocks.
- No debug output left in committed code (`console.log`, `System.out.println`, `println`).

### Backend

- `./gradlew test` passes with zero failures.
- Compiles with zero warnings on strict mode.
- No string-concatenated SQL anywhere in the codebase.

### Mobile (mobile-arena shared module)

- `./gradlew :shared:test` passes with zero failures.
- Shared module compiles for all targets: `androidTarget`, `iosArm64`, `iosSimulatorArm64`, `iosX64`.
- No platform-specific imports (`android.*`, `UIKit`, `Foundation`) anywhere inside `shared/`. Violations break the build.
- No business logic inside `androidApp/` or `iosApp/`. UI and DI wiring only.
- `pnpm lint` passes with zero ESLint errors.
- `pnpm test` passes with zero failures.
- No `any` type usage (`@typescript-eslint/no-explicit-any: error`).

---

## 11. Documentation standards

- Every service has a `README.md` covering: what it does, how to run it locally, required env vars, and how to run tests.
- API documentation is generated from code (OpenAPI/Springdoc). Never write API docs by hand.
- Architecture decisions are recorded as **ADRs** in `docs/adr/` using the format:
  `docs/adr/0001-use-uuid-for-public-ids.md` — title, context, decision, consequences.
- `CHANGELOG.md` at the repo root is updated with every release. Follow Keep a Changelog format.
- Code comments explain **why**, not **what**. If you need a comment to explain what the code does, simplify the code first.

---

## General principles

- **Consistency over preference.** A convention that exists must be followed — even if you would do it differently.
- **One source of truth.** If the same value is defined in two places, one of them is wrong.
- **Explicit over implicit.** Name things clearly. No magic values, no hidden coupling.
- **Leave it better than you found it.** Every PR is an opportunity to improve a name, remove dead code, or add a missing test.
