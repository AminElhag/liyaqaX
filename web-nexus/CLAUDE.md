# CLAUDE.md — web-nexus (Internal Platform Dashboard)

Inherits all rules from the root `CLAUDE.md`. This file adds rules specific to `web-nexus`.

---

## 1. What this app is

**Nexus** is the internal platform management dashboard used exclusively by our team.
It is never exposed to organizations, clubs, or their members.

Its responsibilities:
- Manage and configure every organization, club, and branch on the platform
- Connect and configure third-party integrations (ZATCA, Qoyod, payment gateways) per organization
- Provide troubleshooting and support tools for our support and integration teams
- Surface platform-wide and per-organization observability (logs, metrics, events, job status)
- Manage internal Nexus users and their access permissions
- Control feature flags and gradual feature rollouts
- Send platform-level communications to organizations

Users of this app are our internal team members only — never external customers.

---

## 2. Stack

- **React 18** with TypeScript (`strict: true`)
- **Vite** as the build tool and dev server
- **TanStack Router** for type-safe routing (file-based routing under `src/routes/`)
- **TanStack Query** for all server state (fetching, caching, mutations)
- **Zustand** for UI state only (sidebar state, active org context, modal state, etc.)
- **React Hook Form + Zod** for all forms and input validation
- **Tailwind CSS** for styling
- **shadcn/ui** as the component base (extend, never override internals)
- **Recharts** for charts and metrics visualizations
- **i18next** for localization (Arabic + English, with RTL support)
- **MSW (Mock Service Worker)** for API mocking during development and testing
- **Vitest + Testing Library** for unit and component tests

---

## 3. Project structure

```
web-nexus/
├── public/
├── src/
│   ├── api/                    ← typed API client modules (one file per domain)
│   │   ├── client.ts           ← base Axios instance with auth headers + interceptors
│   │   ├── organizations.ts
│   │   ├── integrations.ts
│   │   ├── logs.ts
│   │   ├── jobs.ts
│   │   ├── featureFlags.ts
│   │   ├── communications.ts
│   │   └── team.ts
│   │
│   ├── routes/                 ← TanStack Router file-based routes
│   │   ├── __root.tsx          ← root layout (sidebar, topbar, auth guard)
│   │   ├── index.tsx           ← platform overview dashboard
│   │   ├── organizations/
│   │   │   ├── index.tsx       ← organization list + search
│   │   │   ├── $orgId.tsx      ← org detail layout (loads org context)
│   │   │   ├── $orgId.overview.tsx
│   │   │   ├── $orgId.settings.tsx
│   │   │   ├── $orgId.integrations.tsx
│   │   │   ├── $orgId.logs.tsx
│   │   │   ├── $orgId.jobs.tsx
│   │   │   └── $orgId.clubs.tsx
│   │   ├── feature-flags/
│   │   │   ├── index.tsx
│   │   │   └── $flagId.tsx
│   │   ├── communications/
│   │   │   ├── index.tsx
│   │   │   └── new.tsx
│   │   ├── team/               ← internal Nexus user management
│   │   │   ├── index.tsx
│   │   │   └── $userId.tsx
│   │   └── settings/
│   │       └── index.tsx
│   │
│   ├── components/
│   │   ├── ui/                 ← shadcn/ui base components (never edit directly)
│   │   ├── layout/             ← Sidebar, Topbar, PageShell, ErrorBoundary
│   │   ├── org/                ← OrgCard, OrgStatusBadge, OrgContextHeader
│   │   ├── integrations/       ← IntegrationCard, StatusBadge, WizardStep
│   │   ├── logs/               ← LogViewer, LogEntry, LogFilters, EventTimeline
│   │   ├── jobs/               ← JobQueue, JobStatusBadge, RetriggerButton
│   │   ├── metrics/            ← MetricCard, TrendChart, HealthGrid
│   │   └── shared/             ← ConfirmDialog, PermissionGate, ImpersonationBanner
│   │
│   ├── hooks/                  ← custom hooks (co-locate with feature if single-use)
│   ├── stores/                 ← Zustand stores
│   │   ├── useAuthStore.ts     ← current user, role, permissions
│   │   ├── useSidebarStore.ts
│   │   ├── useOrgContextStore.ts
│   │   └── useModalStore.ts
│   ├── lib/                    ← formatCurrency, formatDate, cn, validators
│   ├── types/
│   │   ├── api.ts              ← API response/request shapes + Zod schemas
│   │   ├── domain.ts           ← Org, Club, Branch, Integration, Job, Flag...
│   │   └── permissions.ts      ← Role enum, Permission type, role-permission map
│   └── i18n/
│       ├── en.json
│       └── ar.json
│
├── .env.example
├── index.html
├── vite.config.ts
├── tailwind.config.ts
├── tsconfig.json
└── package.json
```

---

## 4. Routing conventions

- File-based routing via TanStack Router. All route files live under `src/routes/`.
- Authentication guard lives in `__root.tsx`. Every route is protected by default.
- Role guards are enforced at the route level using `beforeLoad` — not inside page components.
- Dynamic segments follow TanStack Router's `$param` convention: `$orgId`, `$clubId`, `$flagId`.
- Org-scoped routes inherit the org detail layout from `$orgId.tsx`, which loads and provides the org context to all children.
- All active filters and view state are reflected in URL search params so views are shareable and bookmarkable.

---

## 5. API client

- All API calls go through typed modules in `src/api/`. No raw `fetch` or Axios calls inside components or hooks.
- `src/api/client.ts` holds the single Axios instance. It:
  - Attaches the `Authorization: Bearer <token>` header to every request
  - Attaches `X-Nexus-User-Id` and `X-Correlation-Id` headers to every request
  - Handles `401` by clearing auth state and redirecting to login (preserving the intended URL)
  - Handles `403` by navigating to the 403 page
- Domain modules export typed async functions:
  ```typescript
  // src/api/organizations.ts
  export const getOrganization = (orgId: string): Promise<Organization> => ...
  export const updateOrgSettings = (orgId: string, data: UpdateOrgSettingsInput): Promise<Organization> => ...
  ```
- API response shapes are defined as Zod schemas in `src/types/api.ts` and validated at the client boundary on every response. Never trust the shape blindly.
- Query key factories live alongside the fetch functions in each domain module.

---

## 6. State management

**TanStack Query** owns all server state.
- Tune `staleTime` per data type: org settings → 5 minutes; integration status → 30 seconds; live logs → 0.
- Use optimistic updates for fast-feedback mutations (toggling a feature flag, updating a setting).
- Invalidate related queries after any mutation that changes shared data.

**Zustand** owns UI state only.
- `useAuthStore` — current Nexus user, role, permissions, impersonation state
- `useSidebarStore` — collapsed/expanded, active nav section
- `useOrgContextStore` — the currently viewed org (id, name, status, tier) for the scoped header bar
- `useModalStore` — which modal is open and its typed props

No server data ever lives in Zustand. If you find yourself copying a query result into a Zustand store, that is the wrong pattern.

---

## 7. Component rules

- One component per file. Filename = component name in PascalCase.
- Components are presentational: no direct API calls, no Zustand writes inside leaf components.
- Data fetching lives in route components or dedicated hook files (`useOrganizationDetail.ts`).
- Use `shadcn/ui` as the base. Extend via props or wrapper components. Never modify files inside `src/components/ui/`.
- Every action that is destructive or irreversible (suspend org, revoke credentials, delete config, manual override) must trigger a `<ConfirmDialog>` before the API call is made.
- Privileged UI elements are wrapped in `<PermissionGate>`. Never use CSS to hide privileged controls — gate them out of the DOM entirely.

---

## 8. Forms

- React Hook Form + Zod for every form. The Zod schema is the single source of truth for validation and TypeScript types.
- Schema files live next to the form component, or in `src/types/` if shared across multiple forms.
- Validate on submit. Show field-level inline errors. Show a toast on success or on unexpected API error.
- All labels and error messages use i18n keys — no hardcoded strings.
- Multi-step flows (integration setup, org onboarding) use a wizard pattern. Each step validates before advancing. No partial saves — the entity is either fully configured or not committed.

---

## 9. Permissions & role-based access

Nexus has its own internal RBAC, separate from the platform RBAC.

| Role | What they can do |
|---|---|
| `super-admin` | Full access — all reads and writes, all lifecycle actions |
| `support-agent` | Read all org and club data, use troubleshooting tools, no config writes |
| `integration-specialist` | Read + write integration configs, view integration logs, no org lifecycle actions |
| `read-only-auditor` | Read-only access to all data, logs, and metrics — no writes of any kind |

Rules:
- Roles and permissions are loaded once at app init and stored in `useAuthStore`.
- `<PermissionGate role="super-admin">` conditionally renders controls — absent from the DOM for unauthorized users, not just hidden.
- Route guards use `beforeLoad` to redirect unauthorized users to the 403 page before the route renders.
- All permission checks are also enforced on the backend. Frontend role checks are UX convenience only.

---

## 10. Impersonation

Impersonation allows a Nexus user to act as an organization for debugging purposes.

- Impersonation sessions are initiated from the org detail page by `super-admin` and `support-agent` roles only.
- While active, every API request includes `X-Impersonation-Org-Id: <orgId>`. The backend scopes the response to that org's context.
- A persistent `<ImpersonationBanner>` is shown at the top of every page during an impersonation session, clearly stating the org name and the session expiry countdown.
- Impersonation sessions expire after 30 minutes. The UI shows a countdown and auto-exits, clearing the impersonation header.
- Every impersonation start, action taken, and end is audit-logged on the backend against the Nexus user.
- The banner includes an "Exit impersonation" button that is always accessible regardless of current route.

---

## 11. Integration status indicators

Every third-party integration card shows a live status badge:

| Status | Meaning |
|---|---|
| `connected` | Credentials valid, last health check passed |
| `degraded` | Connected but recent errors detected |
| `disconnected` | Credentials missing or last health check failed |
| `pending` | Setup started but not yet completed |

- Status is fetched via TanStack Query with `staleTime: 30_000` on integration pages.
- "Test connection" triggers a mutation calling the backend health-check endpoint and invalidates the status query on completion.
- Integration setup uses a wizard pattern. No partial saves.
- Integration error history (last N failures, timestamps, error details) is shown in a collapsible panel on the integration detail view.

---

## 12. Log viewer

The log viewer is used by the support team daily and must meet these requirements:

- **Filters**: organization, club, branch, actor (user), action type, entity type, date range, severity level.
- **URL-driven**: all active filters are reflected in URL search params so filtered views are shareable.
- **Virtual scrolling** via TanStack Virtual for large result sets. Use cursor-based infinite scroll — no page number pagination.
- Log entries are **read-only and immutable**. No editing, no deletion controls exist in this UI.
- Clicking a log entry opens an **inline detail panel** (not a modal) showing full context: actor, target entity, before/after state, request/response payload where available.
- **Timestamps** are shown in both UTC and Asia/Riyadh (UTC+3) on every entry, side by side.
- The event timeline view (`$orgId.logs.tsx`) shows a chronological feed of all system events for one organization, grouped by day, suitable for reconstructing what happened during an incident.

---

## 13. Background jobs panel

- Shows the job queue for an organization: pending, running, failed, and completed jobs.
- Failed jobs show the error message, the number of retry attempts, and the time of last failure.
- `integration-specialist` and `super-admin` roles can **re-trigger** a failed job with one click (with a confirm dialog).
- Re-triggering a job is idempotent on the backend. The UI reflects the new job status in real time by invalidating and refetching the job query after the mutation.
- Completed and cancelled jobs are shown in a read-only history table, not the active queue.

---

## 14. Feature flags

- Feature flags are managed globally and can be overridden per organization.
- The flags list shows: flag name, description, global state (on/off), number of orgs with overrides.
- Toggling a flag globally shows a confirmation dialog stating how many organizations will be affected.
- Per-org overrides are set on the org's integrations/settings page — not on the global flags page.
- Every flag change is audit-logged with the Nexus user, timestamp, previous value, and new value.
- Flags follow a strict naming convention: `SCREAMING_SNAKE_CASE`, e.g., `ZATCA_EINVOICE_ENABLED`, `QOYOD_AUTO_SYNC`.

---

## 15. Localization

- Default language is **English**. Arabic must be fully supported.
- Every string uses an i18n key via `react-i18next`. No hardcoded user-facing strings.
- When locale is `ar`, the root element has `dir="rtl"` and all layout uses logical CSS properties (`ms-`, `me-`, `ps-`, `pe-`).
- Numbers: `Intl.NumberFormat` with the active locale. Never format manually.
- Dates: `Intl.DateTimeFormat` with explicit timezone display. Always show the timezone label.

---

## 16. Styling

- Tailwind utility classes only. No inline `style={{}}` unless the value is dynamically computed.
- No custom CSS files except Tailwind base overrides in `src/index.css`.
- Use `cn()` (clsx + tailwind-merge) for conditional class names.
- Destructive actions use `text-destructive` / `bg-destructive` tokens. Status colors use the design system — no ad hoc color choices.
- Data-dense views (log tables, job queues, metrics grids) use compact spacing. Do not apply default padding to table rows.
- Nexus is a **desktop-first tool**: optimized for 1280px+. It must not break below 1280px but mobile optimization is not a requirement.

---

## 17. Error handling

- All async errors surface through TanStack Query's `error` state or mutation `onError`.
- A global `<ErrorBoundary>` in `src/components/layout/` catches unexpected runtime errors and shows a recovery screen.
- RFC 7807 API errors are parsed and the `detail` field is shown in the toast notification.
- Network errors and 5xx responses show: "Something went wrong — our team has been notified." Never show raw error messages or stack traces.
- `401` → redirect to login, preserving intended URL in `?redirect=` param.
- `403` → redirect to the dedicated 403 page.

---

## 18. Testing

- **Vitest + Testing Library** for all component and hook tests.
- **MSW** for API mocking at the network layer. Never mock modules or internal functions.
- Test observable behavior and user interactions — not implementation details.
- Required test coverage:
  - Every permission-gated component: assert absent from DOM for unauthorized roles.
  - Every form: assert validation errors appear and submit is not called with invalid input.
  - Every confirmation dialog: assert the action is not executed without confirmation.
  - Log viewer: assert filter state is reflected in URL params and filter changes update the query.
  - Impersonation banner: assert it renders when impersonation is active and is absent otherwise.

---

## 19. Performance

- Route-based code splitting is automatic via TanStack Router. Do not bundle the whole app.
- Heavy components (charts, log viewer, rich text editors) are lazy-loaded with `React.lazy` + `Suspense`.
- Metrics dashboard: `staleTime: 60_000`, `refetchInterval: 60_000`. Do not poll more than once per minute for aggregate data.
- Virtual scrolling required for any list that can exceed 100 items: logs, member lists, job queues, org lists.
- Icons are inline SVGs or Vite asset imports. No external icon CDN requests.
