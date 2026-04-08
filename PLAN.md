# Plan 29 — Club Branding & White-Label Portal

## Status
Ready for implementation

## Branch
`feature/plan-29-branding`

## Goal
Allow club owners to personalise the web-arena member portal with their own logo, primary colour, secondary colour, and portal title. Members see the club's brand — not generic Liyaqa chrome. Staff settings page in web-pulse provides a live preview before saving.

## Context
- `ClubPortalSettings` entity already exists from the web-arena plan with fields: `gxBookingEnabled`, `ptViewEnabled`, `invoiceViewEnabled`, `onlinePaymentEnabled`, `portalMessage`, `selfRegistrationEnabled`
- `GET /api/v1/arena/portal-settings` already returns `ClubPortalSettings` data to the arena frontend
- `PATCH /api/v1/pulse/portal-settings` already exists for staff to update settings
- web-arena reads portal settings on load and uses them for feature flags
- web-pulse Settings page already has a Portal Settings section — branding fields go in here
- Next Flyway migration: **V19** — four new columns on `club_portal_settings`

---

## Scope — what this plan covers

- [ ] Flyway V19 — add `logo_url`, `primary_color_hex`, `secondary_color_hex`, `portal_title` to `club_portal_settings`
- [ ] Extend `ClubPortalSettings` entity with 4 new fields
- [ ] Extend existing `PATCH /api/v1/pulse/portal-settings` to accept branding fields
- [ ] Extend existing `GET /api/v1/arena/portal-settings` to return branding fields
- [ ] Input validation for hex colour format and URL format
- [ ] web-pulse: Branding section added to Portal Settings page with live preview panel
- [ ] web-arena: apply branding CSS variables from portal settings on app load
- [ ] web-arena: logo in header with silent fallback to Liyaqa logo on error
- [ ] web-arena: `<title>` set to `portalTitle` if configured
- [ ] New audit action: `BRANDING_UPDATED`
- [ ] New permission: `branding:update` (Owner only)
- [ ] Tests — unit + integration + frontend

## Out of scope — do not implement in this plan

- File upload for logo (CDN URL paste only — no file upload per project rules)
- Branding in web-pulse, web-coach, or web-nexus
- Email template branding
- Custom fonts
- Dark mode toggle
- Per-branch branding (branding is per-club)

---

## Decisions already made

- **4 branding fields**: `logoUrl` (VARCHAR 500), `primaryColorHex` (VARCHAR 7, e.g. `#1A73E8`), `secondaryColorHex` (VARCHAR 7), `portalTitle` (VARCHAR 100)
- **Flyway V19** — adds 4 nullable columns to existing `club_portal_settings` table
- **No new entity** — extends existing `ClubPortalSettings`
- **No new controller** — extends existing portal settings endpoints
- **web-arena only** — staff apps unchanged
- **Logo fallback**: `onError` handler on `<img>` sets `src` to Liyaqa logo; no visible error shown
- **Live preview in web-pulse**: as owner types URL or adjusts colours, a mock portal header renders in real time on the same settings page
- **CSS variables**: web-arena injects `--color-primary` and `--color-secondary` into `:root` from portal settings; Tailwind utilities reference these via `var()` in a theme extension
- **`branding:update`** permission: Owner only — same restriction as existing `portal-settings:update`

---

## Entity design

### ClubPortalSettings (extended)

Add to existing entity:

```kotlin
@Column(name = "logo_url", length = 500)
var logoUrl: String? = null

@Column(name = "primary_color_hex", length = 7)
var primaryColorHex: String? = null

@Column(name = "secondary_color_hex", length = 7)
var secondaryColorHex: String? = null

@Column(name = "portal_title", length = 100)
var portalTitle: String? = null
```

---

## Flyway V19

```sql
-- V19__club_portal_branding.sql

ALTER TABLE club_portal_settings
    ADD COLUMN IF NOT EXISTS logo_url          VARCHAR(500),
    ADD COLUMN IF NOT EXISTS primary_color_hex  VARCHAR(7),
    ADD COLUMN IF NOT EXISTS secondary_color_hex VARCHAR(7),
    ADD COLUMN IF NOT EXISTS portal_title       VARCHAR(100);
```

---

## Validation rules

All validation enforced in the service layer (not just controller):

| Field | Rule | Error |
|-------|------|-------|
| `logoUrl` | If provided: must be a valid URL (starts with `https://`) | `400` — "Logo URL must start with https://" |
| `primaryColorHex` | If provided: must match `^#[0-9A-Fa-f]{6}$` | `400` — "Primary colour must be a valid hex code (e.g. #1A73E8)" |
| `secondaryColorHex` | If provided: must match `^#[0-9A-Fa-f]{6}$` | `400` — "Secondary colour must be a valid hex code" |
| `portalTitle` | If provided: 1–100 characters, non-blank | `400` — "Portal title must be between 1 and 100 characters" |

All four fields are optional — a `null` value means "use platform default".

---

## API changes

### PATCH /api/v1/pulse/portal-settings (extended)

Add to existing request body:

```json
{
  "logoUrl": "https://cdn.elixirgym.sa/logo.png",
  "primaryColorHex": "#1A73E8",
  "secondaryColorHex": "#F8F9FA",
  "portalTitle": "Elixir Gym"
}
```

Partial updates supported — any combination of fields; unset fields are left unchanged.

### GET /api/v1/arena/portal-settings (extended)

Add to existing response:

```json
{
  "gxBookingEnabled": true,
  "ptViewEnabled": true,
  "invoiceViewEnabled": true,
  "onlinePaymentEnabled": false,
  "portalMessage": "Welcome to Elixir Gym!",
  "selfRegistrationEnabled": true,
  "logoUrl": "https://cdn.elixirgym.sa/logo.png",
  "primaryColorHex": "#1A73E8",
  "secondaryColorHex": "#F8F9FA",
  "portalTitle": "Elixir Gym"
}
```

All four new fields are nullable — `null` if not configured.

---

## New audit action

Add to `AuditAction.kt`:

```kotlin
BRANDING_UPDATED,
```

Fired in the portal settings service after a successful PATCH that changes any branding field. Log only changed fields in `changes` map.

---

## New permission

Add to `PermissionConstants.kt`:

```kotlin
const val BRANDING_UPDATE = "branding:update"
```

Seed to: **Owner** role only.

The existing `PATCH /api/v1/pulse/portal-settings` is guarded by `portal-settings:update`. Add a secondary check: if the request body contains any branding field (`logoUrl`, `primaryColorHex`, `secondaryColorHex`, `portalTitle`), also require `branding:update`. This means Branch Managers can still toggle feature flags but cannot change branding.

---

## Frontend additions

### web-pulse — Portal Settings page (Settings → Portal)

Add a **Branding** section below the existing feature toggles:

```
──────────────────────────────────────────────────────
 Branding
──────────────────────────────────────────────────────
 Logo URL          [ https://cdn.elixirgym.sa/logo.png ]
 Portal Title      [ Elixir Gym                        ]
 Primary Color     [■ #1A73E8  ] (color picker + hex input)
 Secondary Color   [■ #F8F9FA  ] (color picker + hex input)
──────────────────────────────────────────────────────
 Preview
 ┌────────────────────────────────────────────────────┐
 │ [LOGO]  Elixir Gym                    [AR] Profile │  ← mock arena header
 │         ████████████  (primary bg)                 │
 │  [ Book a Class ]  [ My Membership ]               │  ← primary-colored buttons
 └────────────────────────────────────────────────────┘
──────────────────────────────────────────────────────
 [ Save Branding ]
```

- Preview panel updates in real time as fields change (no API call — purely CSS-driven in the component)
- Color input: HTML `<input type="color">` bound to hex field; hex input field beside it for manual entry
- Logo preview inside the mock header: `<img src={logoUrl} onError={() => setLogoSrc(liyaqaLogo)} />`
- "Save Branding" button is separate from the existing "Save Portal Settings" button — calls the same `PATCH` endpoint but with only the branding fields in the body
- Entire Branding section is hidden (not just disabled) for users without `branding:update` permission

**New i18n strings** (`ar.json` + `en.json`):
```
settings.branding.title
settings.branding.logo_url
settings.branding.logo_url_placeholder
settings.branding.portal_title
settings.branding.portal_title_placeholder
settings.branding.primary_color
settings.branding.secondary_color
settings.branding.preview_title
settings.branding.save_button
settings.branding.saved_toast
settings.branding.permission_note
```

### web-arena — apply branding on load

**In the root layout or `App.tsx`**:

After `GET /api/v1/arena/portal-settings` resolves, inject CSS variables:

```ts
function applyBranding(settings: PortalSettings) {
  const root = document.documentElement;
  if (settings.primaryColorHex) {
    root.style.setProperty('--color-primary', settings.primaryColorHex);
  }
  if (settings.secondaryColorHex) {
    root.style.setProperty('--color-secondary', settings.secondaryColorHex);
  }
  if (settings.portalTitle) {
    document.title = settings.portalTitle;
  }
}
```

**Header component** (`ArenaHeader.tsx` or equivalent):
- Replace hard-coded Liyaqa logo with:
  ```tsx
  <img
    src={portalSettings.logoUrl ?? liyaqaLogoUrl}
    onError={(e) => { e.currentTarget.src = liyaqaLogoUrl; }}
    alt={portalSettings.portalTitle ?? 'Liyaqa'}
    className="h-8 w-auto"
  />
  ```
- Portal title text in the header: `{portalSettings.portalTitle ?? 'Liyaqa'}`

**Tailwind config** (`tailwind.config.ts`):
```ts
theme: {
  extend: {
    colors: {
      primary: 'var(--color-primary, #6366F1)',   // indigo default
      secondary: 'var(--color-secondary, #F1F5F9)', // slate-100 default
    }
  }
}
```

All existing `bg-indigo-600`, `text-indigo-600` etc. in web-arena should be replaced with `bg-primary`, `text-primary` etc. to make them theme-aware.

**New i18n strings** (`ar.json` + `en.json`):
```
(none — branding is visual only, no new user-facing strings in web-arena)
```

---

## Files to generate

### New files

**Backend:**
- `backend/src/main/resources/db/migration/V19__club_portal_branding.sql`
- `backend/src/test/kotlin/com/liyaqa/portal/service/PortalBrandingServiceTest.kt`
- `backend/src/test/kotlin/com/liyaqa/portal/controller/PortalBrandingControllerIntegrationTest.kt`

**Frontend:**
- `apps/web-pulse/src/components/settings/BrandingSection.tsx`
- `apps/web-pulse/src/components/settings/BrandingPreview.tsx`
- `apps/web-arena/src/utils/applyBranding.ts`
- `apps/web-pulse/src/tests/branding-settings.test.tsx`
- `apps/web-arena/src/tests/branding-arena.test.tsx`

### Files to modify

- `backend/.../portal/entity/ClubPortalSettings.kt` — add 4 fields
- `backend/.../portal/dto/PortalSettingsResponse.kt` — add 4 fields
- `backend/.../portal/dto/UpdatePortalSettingsRequest.kt` — add 4 fields
- `backend/.../portal/service/PortalSettingsService.kt` — add validation + `BRANDING_UPDATED` audit + `branding:update` guard
- `backend/.../audit/model/AuditAction.kt` — add `BRANDING_UPDATED`
- `backend/.../permission/PermissionConstants.kt` — add `BRANDING_UPDATE`
- `backend/DevDataLoader.kt` — seed `branding:update` to Owner
- `apps/web-pulse/src/routes/settings/portal.tsx` — add Branding section + permission gate
- `apps/web-pulse/src/locales/ar.json` + `en.json`
- `apps/web-arena/src/App.tsx` (or root layout) — call `applyBranding()` after portal settings load
- `apps/web-arena/src/components/ArenaHeader.tsx` — logo + portal title from settings
- `apps/web-arena/tailwind.config.ts` — extend theme with `--color-primary` / `--color-secondary`
- `apps/web-arena/src/api/portalSettings.ts` — extend `PortalSettings` type with 4 new fields

---

## Implementation order

### Step 1 — Flyway V19 + entity extension
- Write `V19__club_portal_branding.sql`
- Add 4 nullable fields to `ClubPortalSettings.kt`
- Verify: `./gradlew flywayMigrate`

### Step 2 — Permission + audit action
- Add `BRANDING_UPDATE = "branding:update"` to `PermissionConstants.kt`
- Add `BRANDING_UPDATED` to `AuditAction.kt`
- Seed `branding:update` to Owner in `DevDataLoader`
- Verify: `./gradlew compileKotlin`

### Step 3 — Extend service + DTOs
- Add 4 fields to `UpdatePortalSettingsRequest` and `PortalSettingsResponse`
- In `PortalSettingsService.update()`:
  - If any branding field is present in request → check caller has `branding:update` → `403` if not
  - Validate `logoUrl` (https only), hex codes (regex), `portalTitle` length
  - Update entity fields
  - If any branding field changed → log `BRANDING_UPDATED` audit action with changed fields only
- Verify: unit tests in `PortalBrandingServiceTest`

### Step 4 — Controller (no new endpoints — extend existing)
- `PATCH /api/v1/pulse/portal-settings` already exists — changes in Step 3 are service-layer only
- `GET /api/v1/arena/portal-settings` already exists — response DTO extended in Step 3
- Verify: integration tests in `PortalBrandingControllerIntegrationTest`

### Step 5 — Frontend: web-arena
- Extend `PortalSettings` TypeScript type with 4 new nullable fields
- Write `applyBranding.ts` utility
- Call `applyBranding()` in root layout after portal settings query resolves
- Update `ArenaHeader.tsx`: logo with `onError` fallback, portal title text
- Extend `tailwind.config.ts` with CSS variable colours
- Replace hardcoded `indigo` classes in web-arena with `primary` / `secondary` theme classes
- Verify: `npm run typecheck`

### Step 6 — Frontend: web-pulse
- Write `BrandingPreview.tsx` — mock arena header with live prop-driven preview
- Write `BrandingSection.tsx` — logo URL input, portal title input, two colour pickers, preview, save button; hidden for users without `branding:update`
- Add `BrandingSection` to portal settings route below existing toggles
- Add i18n strings
- Verify: `npm run typecheck`

### Step 7 — Tests

**Unit: `PortalBrandingServiceTest`**
- `update saves logoUrl when valid https URL provided`
- `update throws 400 when logoUrl does not start with https`
- `update throws 400 when primaryColorHex is invalid format`
- `update throws 400 when secondaryColorHex is invalid format`
- `update throws 400 when portalTitle exceeds 100 characters`
- `update throws 403 when caller lacks branding:update and branding field is present`
- `update allows feature flag change without branding:update permission`
- `update logs BRANDING_UPDATED audit action when branding field changes`
- `update does not log BRANDING_UPDATED when only feature flags change`

**Integration: `PortalBrandingControllerIntegrationTest`**
- `PATCH /pulse/portal-settings saves branding fields for Owner`
- `PATCH /pulse/portal-settings returns 403 for Branch Manager attempting branding update`
- `PATCH /pulse/portal-settings returns 400 for invalid hex code`
- `PATCH /pulse/portal-settings returns 400 for http logo URL`
- `PATCH /pulse/portal-settings allows Branch Manager to update feature flags`
- `GET /arena/portal-settings returns branding fields when set`
- `GET /arena/portal-settings returns null branding fields when not configured`

**Frontend: `branding-settings.test.tsx` (pulse)**
- Branding section renders for Owner
- Branding section is hidden for Branch Manager
- Preview panel updates logo when logoUrl input changes
- Preview panel updates button colour when primaryColorHex changes
- Save button calls PATCH with branding fields only

**Frontend: `branding-arena.test.tsx` (arena)**
- applyBranding sets CSS variable when primaryColorHex is provided
- applyBranding sets document.title when portalTitle is provided
- applyBranding does not set CSS variable when field is null
- ArenaHeader renders logoUrl when provided
- ArenaHeader falls back to Liyaqa logo on img error

---

## RBAC matrix rows added by this plan

| Permission | Owner | Branch Manager | Receptionist | Sales Agent |
|------------|-------|----------------|--------------|-------------|
| `branding:update` | ✅ | — | — | — |

---

## Definition of Done

- [ ] Flyway V19 runs cleanly: 4 nullable columns added to `club_portal_settings`
- [ ] `ClubPortalSettings` entity has 4 new nullable fields
- [ ] `branding:update` permission seeded to Owner
- [ ] `BRANDING_UPDATED` audit action added and fired when branding field changes
- [ ] Validation: `logoUrl` requires `https://`, hex codes match `^#[0-9A-Fa-f]{6}$`, `portalTitle` max 100 chars
- [ ] Branch Manager cannot update branding fields — returns `403`
- [ ] Branch Manager CAN update feature flags without `branding:update` — returns `200`
- [ ] `GET /arena/portal-settings` returns all 4 branding fields (null if not set)
- [ ] web-arena: CSS variables `--color-primary` and `--color-secondary` injected on load
- [ ] web-arena: `document.title` set to `portalTitle` when configured
- [ ] web-arena: logo renders from `logoUrl`; silently falls back to Liyaqa logo on error
- [ ] web-arena: all hardcoded `indigo` Tailwind classes replaced with `primary` theme class
- [ ] web-pulse: Branding section visible to Owner, hidden to Branch Manager
- [ ] web-pulse: live preview panel updates in real time without API calls
- [ ] web-pulse: colour pickers + hex inputs work bidirectionally
- [ ] All i18n strings added in Arabic and English (web-pulse only)
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] `./gradlew build` — BUILD SUCCESSFUL, no warnings
- [ ] `npm run typecheck` — no errors in web-pulse or web-arena
- [ ] `PROJECT-STATE.md` updated: Plan 29 complete, test counts, V19 noted
- [ ] `PLAN-29-branding.md` deleted before merging

When all items are checked, confirm: **"Plan 29 — Club Branding & White-Label complete. X backend tests, Y frontend tests."**
