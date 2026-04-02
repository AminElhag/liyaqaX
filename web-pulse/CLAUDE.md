# CLAUDE.md — web-pulse (Club Operations Dashboard)

Inherits all rules from the root `CLAUDE.md`, including the global RBAC requirement.
This file adds rules specific to `web-pulse`.

## Required reading for every frontend session
Read these files in this order before any frontend work:
1. CLAUDE.md (root)
2. web-pulse/CLAUDE.md (this file)
3. docs/rbac.md
4. docs/domain-glossary.md

---

## 1. What this app is

**Pulse** is the club operations dashboard used by the club owner and club employees.
It is never accessible to club members, trainers, or any external users.

Its responsibilities:
- Manage the full member lifecycle: onboarding, renewals, freezes, transfers, termination
- Financial operations: payment collection, invoicing, debt tracking, cash reconciliation
- PT (Personal Training): packages, session scheduling, attendance, trainer utilization
- GX (Group Exercise): class scheduling, member bookings, attendance, capacity management
- Staff management: profiles, shift scheduling, performance metrics, access control
- Lead pipeline: capture, follow-up, conversion tracking
- Reporting: revenue, retention, utilization — per branch or across the club
- Notifications and alerts for the operations team

Users of this app are the club owner and club staff only.
Club members and trainers are explicitly excluded — they have their own dedicated app.

---

## 2. Stack

- **React 18** with TypeScript (`strict: true`)
- **Vite** as the build tool and dev server
- **TanStack Router** for type-safe routing (file-based routing under `src/routes/`)
- **TanStack Query** for all server state
- **Zustand** for UI state only (sidebar, active branch context, modal state)
- **React Hook Form + Zod** for all forms and validation
- **Tailwind CSS** for styling
- **shadcn/ui** as the component base (extend, never modify internals)
- **Recharts** for charts and dashboards
- **@tanstack/react-virtual** for virtualized lists (member lists, session logs)
- **i18next** for localization (Arabic + English, RTL support)
- **date-fns** for all date manipulation — no moment.js
- **MSW** for API mocking in development and tests
- **Vitest + Testing Library** for unit and component tests

---

## 3. RBAC — Pulse roles

Pulse has its own club-level RBAC. Roles are defined in `src/types/permissions.ts`.
Every user must have exactly one role. The role is embedded in the JWT and loaded into `useAuthStore` at app init.

| Role | Who holds it | What they can do |
|---|---|---|
| `club-owner` | The club owner or designated administrator | Full access to all features, all branches, all financial data, all staff management |
| `branch-manager` | Manager of one or more branches | Full operations access within their assigned branches; cannot manage other branches or view club-wide financials |
| `receptionist` | Front-desk staff | Member check-in, new member registration, payment collection, membership renewal; no financial reports, no staff management |
| `sales-agent` | Sales / membership consultant | Lead management, member onboarding, plan upsell; can collect payments; no scheduling, no staff management |
| `pt-trainer` | Personal trainer | View their own assigned sessions and member PT profiles; cannot access financial data or other trainers' schedules |
| `gx-instructor` | Group exercise instructor | View their own class schedule and attendance; no financial or member financial data |
| `read-only` | Observers, auditors, silent partners | Read access to reports and member lists; no writes of any kind |

Rules:
- `pt-trainer` and `gx-instructor` are staff roles, but **they must not be granted access to this app**. They use `web-coach`. If a user with these roles attempts to log in to Pulse, they are rejected at the auth guard with a clear message directing them to Coach.
- Branch scope is part of the token claims for `branch-manager`, `receptionist`, `sales-agent`. They only see data for their assigned branches. `club-owner` sees all branches.
- Every sensitive action (delete, financial write, staff config) checks role + branch scope before executing.

---

## 4. Project structure

```
web-pulse/
├── public/
├── src/
│   ├── api/                        ← typed API client modules
│   │   ├── client.ts               ← Axios instance, auth headers, interceptors
│   │   ├── members.ts
│   │   ├── memberships.ts
│   │   ├── payments.ts
│   │   ├── invoices.ts
│   │   ├── pt.ts                   ← PT packages, sessions, trainers
│   │   ├── gx.ts                   ← GX classes, bookings, attendance
│   │   ├── staff.ts
│   │   ├── leads.ts
│   │   ├── reports.ts
│   │   └── notifications.ts
│   │
│   ├── routes/
│   │   ├── __root.tsx              ← root layout, auth guard, branch context loader
│   │   ├── index.tsx               ← club overview dashboard
│   │   ├── members/
│   │   │   ├── index.tsx           ← member list with search and filters
│   │   │   ├── new.tsx             ← new member registration
│   │   │   ├── $memberId.tsx       ← member profile layout
│   │   │   ├── $memberId.overview.tsx
│   │   │   ├── $memberId.membership.tsx
│   │   │   ├── $memberId.payments.tsx
│   │   │   ├── $memberId.pt.tsx
│   │   │   ├── $memberId.gx.tsx
│   │   │   └── $memberId.body-metrics.tsx
│   │   ├── memberships/
│   │   │   ├── index.tsx           ← renewals queue, expiring members
│   │   │   └── plans.tsx           ← plan catalog (view only for non-owners)
│   │   ├── finance/
│   │   │   ├── index.tsx           ← financial overview
│   │   │   ├── payments.tsx        ← payment log
│   │   │   ├── invoices.tsx        ← invoice list
│   │   │   ├── debts.tsx           ← outstanding balances
│   │   │   ├── cash-drawer.tsx     ← daily cash reconciliation
│   │   │   └── expenses.tsx
│   │   ├── pt/
│   │   │   ├── index.tsx           ← PT session calendar
│   │   │   ├── packages.tsx        ← PT package management
│   │   │   └── trainers.tsx        ← trainer utilization view
│   │   ├── gx/
│   │   │   ├── index.tsx           ← GX class schedule
│   │   │   ├── $classId.tsx        ← class detail + bookings
│   │   │   └── attendance.tsx
│   │   ├── leads/
│   │   │   ├── index.tsx           ← lead pipeline (kanban)
│   │   │   └── $leadId.tsx
│   │   ├── staff/
│   │   │   ├── index.tsx
│   │   │   └── $staffId.tsx
│   │   ├── reports/
│   │   │   ├── index.tsx
│   │   │   ├── revenue.tsx
│   │   │   ├── retention.tsx
│   │   │   └── utilization.tsx
│   │   └── settings/
│   │       └── index.tsx           ← club owner only
│   │
│   ├── components/
│   │   ├── ui/                     ← shadcn/ui base (never edit)
│   │   ├── layout/                 ← Sidebar, Topbar, BranchSelector, PageShell
│   │   ├── members/                ← MemberCard, MemberStatusBadge, MemberSearch
│   │   ├── membership/             ← PlanBadge, RenewalAlert, FreezeForm
│   │   ├── finance/                ← PaymentForm, InvoicePreview, DebtBadge
│   │   ├── pt/                     ← SessionCard, PTPackageForm, TrainerSchedule
│   │   ├── gx/                     ← ClassCard, BookingList, CapacityBar
│   │   ├── leads/                  ← LeadCard, PipelineColumn, FollowUpForm
│   │   ├── reports/                ← MetricCard, TrendChart, ReportExportButton
│   │   └── shared/                 ← ConfirmDialog, PermissionGate, EmptyState
│   │
│   ├── hooks/
│   ├── stores/
│   │   ├── useAuthStore.ts         ← current user, role, branch scope
│   │   ├── useBranchStore.ts       ← active branch selection
│   │   ├── useSidebarStore.ts
│   │   └── useModalStore.ts
│   ├── lib/
│   │   ├── formatCurrency.ts       ← SAR / halala formatting
│   │   ├── formatDate.ts           ← ISO → display, UTC → Asia/Riyadh
│   │   ├── cn.ts
│   │   └── permissions.ts          ← hasPermission(role, action) helper
│   ├── types/
│   │   ├── api.ts
│   │   ├── domain.ts               ← Member, Membership, Payment, PTSession, GXClass...
│   │   └── permissions.ts          ← Role enum, Permission type, role-permission map
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

## 5. Branch context

Pulse is used within a single club but may span multiple branches.

- The active branch is selected from a `<BranchSelector>` in the top navigation bar.
- Branch selection is persisted in `useBranchStore` (Zustand) and reflected in a `?branch=<branchId>` URL param on all routes.
- `club-owner` can switch between all branches and view a "All branches" aggregate mode.
- `branch-manager`, `receptionist`, and `sales-agent` only see their assigned branches in the selector. They cannot select branches outside their scope.
- All API calls include the active `branchId` as a query param or path segment. Never infer branch from the backend session.
- The branch selector is always visible in the topbar. Changing branches re-fetches all active route data.

---

## 6. API client

- All API calls go through `src/api/` modules. No raw `fetch` or Axios in components or hooks.
- `src/api/client.ts` attaches `Authorization`, `X-Club-Id`, `X-Branch-Id`, and `X-Correlation-Id` headers to every request.
- `401` → clear auth state, redirect to login with `?redirect=` param.
- `403` → redirect to 403 page.
- API response shapes are Zod schemas in `src/types/api.ts`. Validate every response at the boundary.
- Query key factories live alongside fetch functions in each domain module.

---

## 7. State management

**TanStack Query** for all server state.
- `staleTime` tuning: member list → 2 min; PT schedule → 30 sec; financial reports → 5 min; cash drawer → 0 (always fresh).
- Optimistic updates for quick-feedback actions: check-in attendance, mark session complete.
- After any mutation that changes shared data (payment collected → outstanding balance updated), invalidate related queries.

**Zustand** for UI state only.
- `useAuthStore` — user, role, assigned branch IDs, permissions
- `useBranchStore` — active branch selection
- `useSidebarStore` — collapsed/expanded, active nav section
- `useModalStore` — open modal + typed props

No server data in Zustand. If you find yourself syncing query results into a store, restructure using TanStack Query's shared `queryKey`.

---

## 8. Member lifecycle rules

The member lifecycle follows a strict set of states. State transitions must be validated on the backend and reflected accurately in the UI.

```
lead → prospect → active → frozen → expired → terminated
                     ↑________↓  (unfreeze)
                     ↑_____________________↓  (rejoin)
```

- **Lead**: prospect who has not yet joined. Managed in the leads pipeline.
- **Prospect**: lead who has visited or expressed intent but has not paid.
- **Active**: has a current paid membership.
- **Frozen**: membership paused at member's request. Freeze duration extends the membership end date.
- **Expired**: membership end date has passed with no renewal. Grace period may apply per plan config.
- **Terminated**: manually closed by staff, or after a configurable period post-expiry.

UI rules:
- Member status badge is always visible and color-coded: active (green), frozen (blue), expired (amber), terminated (red).
- The action buttons available on a member profile depend on their current status. A frozen member shows "Unfreeze" not "Freeze". An expired member shows "Renew" not "Freeze".
- Reversible state changes (freeze, unfreeze) can be done by `receptionist` and above.
- Irreversible state changes (terminate) require `branch-manager` or `club-owner` role and a confirmation dialog with a mandatory reason field.

---

## 9. Financial rules

Financial data is sensitive. Enforce these rules strictly.

- Only `club-owner` and `branch-manager` can view financial reports and the cash drawer reconciliation.
- `receptionist` and `sales-agent` can collect payments and generate invoices but cannot view aggregate reports.
- Every payment record is **immutable** once saved. Corrections are made via a refund + re-payment flow, not by editing the original record.
- Refunds require `branch-manager` or `club-owner` approval. A refund without an approver's action is never processed.
- The cash drawer reconciliation is a daily workflow. It must be opened at the start of the day (with an opening balance) and closed at end of day (with a closing count). A discrepancy flag appears if the computed balance does not match the declared closing balance.
- All monetary values are in halalas (integers). The UI converts to SAR for display using `formatCurrency()` from `src/lib/formatCurrency.ts`. Never format money inline in a component.
- Invoice generation calls the backend — never compute invoice totals on the frontend.
- ZATCA-compliant invoices are generated server-side and downloaded as PDF. The frontend only triggers generation and polls for the download URL.

---

## 10. PT session rules

- A PT session can only be booked against a member who has an active PT package with remaining credits.
- Booking a session deducts one credit from the package balance. Cancellation within the allowed window restores the credit.
- Late cancellations (within the club-configured cutoff, e.g., 2 hours before) consume the credit — this is a business rule enforced on the backend.
- The PT session calendar shows the trainer's schedule. Trainers are not users of Pulse — their schedule is managed by the receptionist or branch manager on their behalf.
- Session status values: `scheduled`, `completed`, `cancelled`, `late-cancelled`, `no-show`.
- `no-show` is set manually by staff. It consumes the session credit.
- A member's PT package balance is always shown prominently on their profile and on the session booking form.

---

## 11. GX class rules

- Classes have a maximum capacity set at creation. The booking form shows remaining spots.
- When a class reaches capacity, new bookings go to a waitlist. When a booked member cancels, the first waitlist member is automatically promoted and notified.
- Attendance is taken from the class detail page. The instructor view (read-only) shows the same attendance list but without edit access.
- Recurring classes are created from a template. Changes to a recurring class can apply to: this session only, this and all future sessions, or all sessions in the series.
- Cancelled classes notify all booked members via the backend notification service. The frontend triggers the cancellation; the backend handles notifications.

---

## 12. Lead pipeline

- The lead pipeline is displayed as a **Kanban board** with columns: `new` → `contacted` → `trial-scheduled` → `trial-done` → `converted` / `lost`.
- Drag-and-drop between columns triggers a status update mutation.
- Every lead card shows: name, source, assigned staff, last contact date, next follow-up date.
- Follow-up tasks have due dates. Overdue follow-ups are highlighted in red on the board.
- When a lead is converted, the "Convert to member" action opens the new member registration form pre-filled with the lead's data.
- Lead source is required on creation: `walk-in`, `referral`, `social-media`, `website`, `call`, `other`.
- `sales-agent` and above can manage leads. `receptionist` can view but not reassign.

---

## 13. Renewals queue

- The renewals queue is a dedicated view under `/memberships` showing members expiring in the next 30 days, sorted by expiry date ascending.
- Each row shows: member name, plan, expiry date, days remaining, last payment amount, a "Renew now" action.
- "Renew now" opens a pre-filled payment form with the same plan and amount. Staff can change the plan before confirming.
- The queue auto-refreshes every 5 minutes while the page is open (`refetchInterval: 300_000`).
- Expired members (past expiry, within grace period) appear in a separate "Overdue" section of the same view, highlighted in amber.

---

## 14. Reporting

- Reports are always scoped to the active branch unless the user is `club-owner` viewing "All branches".
- All reports support a custom date range picker. Default range is the current calendar month.
- Reports are rendered client-side using Recharts from data returned by the backend. Never compute report aggregates on the frontend.
- Every report page has an **Export** button. Exports are generated server-side (PDF and Excel). The frontend triggers the export job, polls for completion, and downloads the file when ready.
- Available reports:
  - Revenue: total collected, by plan type, by payment method, by staff member
  - Retention: active count, new joins, churned, net change, churn rate, average tenure
  - Debt: total outstanding, by aging bucket (0–7d, 8–30d, 31–90d, 90d+)
  - PT utilization: sessions delivered vs capacity, per trainer
  - GX utilization: fill rate per class, per class type, per instructor
  - Lead conversion: leads by source, conversion rate, time-to-convert, by sales agent

---

## 15. Notifications & alerts

The topbar notification bell shows real-time alerts for the active branch. Notifications are fetched on a 60-second polling interval.

Alert types and who sees them:

| Alert | Roles |
|---|---|
| Member expiring in 3 days | `branch-manager`, `receptionist`, `sales-agent` |
| Member expired (grace period active) | `branch-manager`, `receptionist` |
| Unpaid balance over threshold | `branch-manager`, `club-owner` |
| PT package nearly exhausted (1 session left) | `receptionist`, `branch-manager` |
| GX class near capacity (≥ 80% full) | `branch-manager`, `gx-instructor` (their classes only) |
| Cash drawer not reconciled by end of day | `branch-manager`, `club-owner` |
| Member birthday today | `receptionist`, `sales-agent` |
| Refund pending approval | `branch-manager`, `club-owner` |

- Notifications are read-only in the UI. Actions (e.g., renew a member) navigate to the relevant page.
- Read notifications are dismissed per user. Unread count is shown as a badge on the bell icon.

---

## 16. Forms

- React Hook Form + Zod for every form. Zod schema is the source of truth for validation and types.
- Validate on submit. Show field-level inline errors in both Arabic and English using i18n keys.
- Show a toast notification on success. Show the RFC 7807 `detail` field on API error.
- Multi-step flows (new member registration, PT package assignment) use a wizard pattern. Each step validates before advancing. No partial entity saves.
- Forms that collect payment must show a clear summary of the amount, payment method, and member name before the submit button. Accidental payment submission is a serious operational problem.
- The payment form always requires selecting a payment method: `cash`, `card`, `bank-transfer`, `other`. The method selection affects which additional fields are required (e.g., card requires a reference/terminal ID, bank transfer requires a reference number).

---

## 17. Localization

- Default language is **Arabic** for Pulse. This is a club-facing operational tool used primarily in Saudi Arabia.
- English must be fully supported.
- When locale is `ar`, root has `dir="rtl"`, all layout uses logical CSS properties (`ms-`, `me-`, `ps-`, `pe-`).
- All user-facing strings use i18n keys. No hardcoded strings in components.
- Numbers: `Intl.NumberFormat` with the active locale.
- Dates: `Intl.DateTimeFormat` with explicit Asia/Riyadh timezone display.
- The Hijri calendar must be shown alongside the Gregorian calendar on all date pickers and date displays, using `Intl.DateTimeFormat` with `calendar: 'islamic-umalqura'`.

---

## 18. Styling

- Tailwind CSS utility classes only. No inline `style={{}}` unless dynamically computed.
- `cn()` (clsx + tailwind-merge) for all conditional class names.
- Status colors are semantic and consistent across the whole app:
  - Active / success: green tokens
  - Expiring soon / warning: amber tokens
  - Expired / overdue: orange tokens
  - Terminated / error: red tokens
  - Frozen: blue tokens
  - Inactive / neutral: gray tokens
- Never introduce ad hoc color choices for status indicators. Use only the tokens above.
- Destructive actions use `text-destructive` / `bg-destructive` from shadcn.
- Pulse is a **desktop-first tool** (1280px+) but must be usable on tablets (768px+) since reception desks often use tablets. Full mobile optimization is not required but the layout must not break at 768px.

---

## 19. Error handling

- All async errors through TanStack Query's `error` state or mutation `onError`.
- Global `<ErrorBoundary>` catches unexpected runtime errors and shows a recovery screen.
- RFC 7807 errors show the `detail` field in a toast.
- `401` → redirect to login, preserving URL.
- `403` → redirect to 403 page with a clear message explaining the role restriction.
- Payment and financial mutation errors must show a prominent, non-dismissible alert (not just a toast) to ensure the staff member is aware the transaction did not complete.

---

## 20. Testing

- **Vitest + Testing Library** for all tests.
- **MSW** for API mocking at the network layer.
- Required coverage:
  - Member status state machine: assert correct action buttons appear for each status.
  - Role gating: `pt-trainer` and `gx-instructor` are blocked at the auth guard; each PermissionGate renders absent for unauthorized roles.
  - Payment form: assert submit is blocked without all required fields; assert summary is shown before confirm.
  - Cash drawer: assert discrepancy flag appears when declared balance differs from computed balance.
  - Renewals queue: assert expiring and expired members appear in correct sections.
  - Lead pipeline: assert drag-and-drop triggers correct status mutation.

---

## 21. Performance

- Route-based code splitting via TanStack Router. No monolithic bundle.
- Heavy views (reports with charts, member list with 500+ rows, GX schedule grid) use `React.lazy` + `Suspense`.
- Member list and session logs use TanStack Virtual for virtual scrolling.
- The PT calendar uses a week-view grid. Only load sessions for the visible week. Prefetch adjacent weeks on idle.
- Report charts use `staleTime: 300_000` — reports do not need to be live. Do not auto-refetch reports.
- The cash drawer uses `staleTime: 0` — always fetch fresh.
