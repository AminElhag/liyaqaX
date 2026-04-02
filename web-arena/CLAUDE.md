# CLAUDE.md — web-arena (Member Self-Service Portal)

Inherits all rules from the root `CLAUDE.md`, including the global RBAC requirement.
This file adds rules specific to `web-arena`.

## Required reading for every frontend session
Read these files in this order before any frontend work:
1. CLAUDE.md (root)
2. web-arena/CLAUDE.md (this file)
3. docs/rbac.md
4. docs/domain-glossary.md

---

## 1. What this app is

**Arena** is the self-service portal for club members.
It allows members to manage their accounts, book classes, track their fitness journey,
communicate with their trainer, and pay for club services — from any browser.

Users of this app are club members only.
Club staff use `web-pulse`. Trainers use `web-coach`. Internal team uses `web-nexus`.

### Strategic scope boundary — web vs. mobile

Arena is intentionally scoped to give members a fully functional experience
while reserving high-engagement, real-time, and device-native features for the future mobile app.
This boundary is a product decision, not a technical limitation.
Do not add features from the mobile column without an explicit product decision to do so.

| Capability | Arena (web) | Mobile app (future) |
|---|---|---|
| Account & membership management | Yes | Yes |
| Invoice payment (card via gateway) | Yes | Yes |
| GX class booking | Yes | Yes |
| PT session view & reschedule request | Yes | Yes |
| Trainer messaging | Yes | Yes |
| Progress & body metrics view | Yes | Yes |
| Club info & announcements | Yes | Yes |
| In-app notifications (on-page) | Yes | Yes |
| Push notifications | No | Yes |
| QR code check-in at gate | No | Yes |
| Wearable / fitness tracker integration | No | Yes |
| Offline access | No | Yes |
| Apple Pay / Google Pay | No | Yes |
| Camera-based body metrics | No | Yes |
| Personalized AI workout suggestions | No | Yes |
| Social feed & member community | No | Yes |
| Live class streaming | No | Yes |
| Gamification (streaks, badges, leaderboard) | No | Yes |

If a stakeholder or developer requests adding a "mobile column" feature to Arena,
the response is: that feature is reserved for the mobile app. Document the request as a mobile backlog item instead.

---

## 2. Stack

- **React 18** with TypeScript (`strict: true`)
- **Vite** as the build tool and dev server
- **TanStack Router** for type-safe routing (file-based routing under `src/routes/`)
- **TanStack Query** for all server state
- **Zustand** for UI state only (active branch, modal state, notification panel)
- **React Hook Form + Zod** for all forms and validation
- **Tailwind CSS** for styling
- **shadcn/ui** as the component base (extend, never modify internals)
- **Recharts** for progress charts (body metrics, session history)
- **i18next** for localization (Arabic + English, RTL support)
- **date-fns** for all date manipulation — no moment.js
- **MSW** for API mocking in development and tests
- **Vitest + Testing Library** for unit and component tests

---

## 3. RBAC — Arena roles

Arena has a single user type. There is no admin role, no elevated privilege, no staff access.

| Role | Who holds it | What they can do |
|---|---|---|
| `member` | Any registered club member | Access their own account data only — see section 4 for full scope |

Rules:
- Every `member` can only access their own data. Member A cannot view, request, or affect Member B's account in any way.
- This isolation is enforced on the backend via the JWT `memberId` claim. The frontend never passes a member ID as a user-supplied parameter — the backend derives it from the token.
- Club staff, trainers, and internal team members attempting to log in to Arena are rejected at the auth guard with a message directing them to their respective app.
- There is no impersonation, no admin bypass, and no support mode within Arena itself. Support is handled by staff in Pulse on behalf of the member.
- A member whose membership is `terminated` can still log in but sees a read-only view of their history and a prompt to contact the club to rejoin.
- A member whose membership is `expired` (within grace period) can log in, see their account, and pay to renew. They cannot book classes until renewed.
- A member whose membership is `frozen` can log in but cannot book classes during the freeze period.

---

## 4. What members can and cannot do

This table is the authoritative scope reference. When in doubt about whether a feature belongs in Arena, check here first.

### Account & membership

| Action | Allowed | Notes |
|---|---|---|
| View current membership status & details | Yes | |
| View membership history | Yes | All past plans |
| Request membership upgrade | Yes | Triggers a staff review in Pulse; not instant |
| Request membership freeze | Yes | Subject to plan rules; staff approves in Pulse |
| Update personal info (name, phone, photo) | Yes | |
| Update emergency contact | Yes | |
| Change password | Yes | |
| View and download invoices | Yes | All past invoices as PDF |
| Sign / re-sign health waiver | Yes | Required before first booking; prompted on login if updated |
| Delete account | No | Must be requested via the club directly |
| Change membership plan directly | No | Request only — staff action required |
| View other members' data | No | |

### Payments

| Action | Allowed | Notes |
|---|---|---|
| View outstanding balance | Yes | |
| Pay outstanding balance online | Yes | Card via payment gateway |
| Renew membership online | Yes | Card payment; generates invoice |
| Purchase a PT package online | Yes | Only if club has enabled self-service PT purchase |
| Pay for one-off club services | Yes | Locker, guest pass, etc. — if club enables |
| View payment history | Yes | All past transactions |
| Download receipt | Yes | Per transaction |
| Save a payment method | Yes | Tokenized — no raw card data |
| Request a refund | No | Must go through club staff |
| View pricing of other plans | Yes | Read-only plan catalog |

### GX class booking

| Action | Allowed | Notes |
|---|---|---|
| Browse class schedule | Yes | For their branch |
| Book a class spot | Yes | If membership is active and capacity available |
| Join class waitlist | Yes | Auto-promoted when spot opens |
| Cancel a booking | Yes | With cancellation policy warning |
| View upcoming booked classes | Yes | |
| View class history | Yes | Past attended classes |
| Book a class while frozen | No | Membership must be active |
| Book classes at another branch | No | Branch-scoped; cross-branch is a mobile feature |

### PT sessions

| Action | Allowed | Notes |
|---|---|---|
| View upcoming PT sessions | Yes | |
| View session history & trainer notes | Yes | Notes written by trainer after session |
| Request session reschedule | Yes | Goes to trainer/reception — not a direct calendar change |
| View PT package balance | Yes | Remaining sessions, expiry |
| View progress (metrics, goals) | Yes | Body metrics chart, goals set by trainer |
| Add own body metrics entry | Yes | Weight, measurements — trainer also adds |
| Cancel a PT session | No | Must request via messaging or contact reception |
| Book a PT session directly | No | Booked by reception or trainer via Pulse/Coach |

### Communication

| Action | Allowed | Notes |
|---|---|---|
| Message their assigned PT trainer | Yes | One-to-one; only if active PT package exists |
| Read trainer broadcast messages | Yes | From GX classes they're enrolled in |
| Contact club reception via message | No | Phone or in-person only in web version |
| Message other members | No | Community features are mobile-only |

---

## 5. Project structure

```
web-arena/
├── public/
├── src/
│   ├── api/
│   │   ├── client.ts               ← Axios instance, auth headers, interceptors
│   │   ├── account.ts              ← member profile, membership, waiver
│   │   ├── payments.ts             ← balance, pay, history, receipts, saved cards
│   │   ├── classes.ts              ← GX schedule, booking, waitlist, history
│   │   ├── sessions.ts             ← PT session list, reschedule request, notes
│   │   ├── progress.ts             ← body metrics, goals
│   │   ├── messaging.ts            ← trainer thread
│   │   ├── club.ts                 ← branch info, announcements, staff directory
│   │   └── notifications.ts
│   │
│   ├── routes/
│   │   ├── __root.tsx              ← root layout, auth guard (member only)
│   │   ├── index.tsx               ← home: membership status + upcoming agenda
│   │   ├── membership/
│   │   │   ├── index.tsx           ← membership detail, status, history
│   │   │   ├── upgrade.tsx         ← plan catalog + upgrade request form
│   │   │   └── freeze.tsx          ← freeze request form
│   │   ├── classes/
│   │   │   ├── index.tsx           ← GX schedule browser + booking
│   │   │   └── my-classes.tsx      ← upcoming bookings + history
│   │   ├── sessions/
│   │   │   ├── index.tsx           ← upcoming PT sessions + package balance
│   │   │   └── history.tsx         ← past sessions + trainer notes
│   │   ├── progress/
│   │   │   └── index.tsx           ← body metrics chart + goals
│   │   ├── payments/
│   │   │   ├── index.tsx           ← balance, pay now, history
│   │   │   └── invoices.tsx        ← invoice list + download
│   │   ├── messages/
│   │   │   └── index.tsx           ← trainer message thread
│   │   ├── club/
│   │   │   ├── index.tsx           ← branch info, hours, map
│   │   │   ├── schedule.tsx        ← full class schedule (public view)
│   │   │   └── trainers.tsx        ← trainer bios + specializations
│   │   ├── account/
│   │   │   ├── index.tsx           ← personal info editor
│   │   │   ├── security.tsx        ← change password
│   │   │   └── preferences.tsx     ← language, notification settings
│   │   └── auth/
│   │       ├── login.tsx
│   │       ├── forgot-password.tsx
│   │       └── reset-password.tsx
│   │
│   ├── components/
│   │   ├── ui/                     ← shadcn/ui base (never edit)
│   │   ├── layout/
│   │   │   ├── Topbar.tsx          ← logo, nav links, notification bell, profile menu
│   │   │   ├── MobileNav.tsx       ← bottom tab bar on mobile/tablet viewports
│   │   │   ├── PageShell.tsx
│   │   │   └── ErrorBoundary.tsx
│   │   ├── membership/
│   │   │   ├── MembershipCard.tsx  ← status, plan name, expiry — hero element on home
│   │   │   ├── StatusBanner.tsx    ← expiry warning, freeze notice, overdue payment
│   │   │   └── PlanCard.tsx        ← used in upgrade flow
│   │   ├── classes/
│   │   │   ├── ClassCard.tsx
│   │   │   ├── ClassSchedule.tsx   ← filterable weekly grid
│   │   │   ├── BookingButton.tsx   ← handles state: book / waitlist / cancel / full
│   │   │   └── WaitlistBadge.tsx
│   │   ├── sessions/
│   │   │   ├── SessionCard.tsx
│   │   │   └── PackageBalanceBar.tsx
│   │   ├── progress/
│   │   │   ├── MetricsChart.tsx
│   │   │   ├── MetricEntryForm.tsx
│   │   │   └── GoalCard.tsx
│   │   ├── payments/
│   │   │   ├── PaymentForm.tsx     ← card input via gateway iframe / hosted fields
│   │   │   ├── SavedCardSelector.tsx
│   │   │   └── InvoiceRow.tsx
│   │   ├── messages/
│   │   │   ├── MessageThread.tsx
│   │   │   └── ComposeBar.tsx
│   │   └── shared/
│   │       ├── ConfirmDialog.tsx
│   │       ├── MobilePromoBanner.tsx ← see section 6
│   │       └── EmptyState.tsx
│   │
│   ├── hooks/
│   ├── stores/
│   │   ├── useAuthStore.ts         ← member identity, membership status
│   │   ├── useNotificationStore.ts ← unread count, in-app alert list
│   │   └── useModalStore.ts
│   ├── lib/
│   │   ├── formatCurrency.ts
│   │   ├── formatDate.ts
│   │   └── cn.ts
│   ├── types/
│   │   ├── api.ts
│   │   ├── domain.ts               ← Member, Membership, GXClass, PTSession, Payment...
│   │   └── permissions.ts          ← Role enum ('member'), membership status type
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

## 6. Mobile promo banner — `<MobilePromoBanner>`

Arena is intentionally web-scoped. The `<MobilePromoBanner>` component is how we communicate the mobile value proposition to members without being disruptive.

### Rules

- The banner appears **once per session** on mobile and tablet viewports (< 1024px), shown at the bottom of the screen after 30 seconds of activity.
- It is **never shown on desktop** — on desktop, members are at a computer; the prompt is irrelevant.
- It is dismissible. Once dismissed, it does not reappear for 7 days (stored in `localStorage`).
- It must not block content or intercept scroll — it sits above the bottom nav bar as a non-blocking sheet.
- Content: "Get more with the Arena mobile app — check in with QR, track workouts, get push alerts." With a CTA linking to the app store (or a waitlist page until the app is launched).
- The banner is **never shown on these pages**: login, forgot password, reset password, waiver signing.
- The banner component accepts a `featureHighlight` prop so it can surface different features contextually:
  - On the GX booking page: "Book faster and get instant push alerts on mobile."
  - On the progress page: "Connect your wearable and track workouts automatically on mobile."
  - On the home page (default): the generic message above.

---

## 7. Home page — the member's dashboard

The home page (`/`) is the first thing a member sees after login.
It must answer three questions immediately: where am I, what's next, and do I owe anything.

Content, in order:
1. **Membership card** — plan name, status badge, expiry date, branch name. If expiring within 14 days: amber highlight + "Renew now" CTA. If expired: red + "Renew to regain access". If frozen: blue + unfreeze date.
2. **Outstanding balance** — shown only if balance > 0. Prominent, with a "Pay now" CTA. Never hidden.
3. **Upcoming agenda** — next 3 upcoming items (PT sessions + GX bookings), chronological. Each shows time, type, name, location. "See all" link to full schedule.
4. **Club announcements** — latest 2 announcements from the club. "See all" link.
5. **Progress snapshot** — most recently logged body metric (weight or whichever was last entered) with a delta from the previous entry (e.g., "−1.2 kg since last entry"). Link to full progress page.

Rules:
- The home page loads with a single parallel query batch — do not waterfall these requests.
- If the member has no upcoming sessions or classes: show an encouraging empty state, not a blank section.
- If the member has never logged a body metric: show a "Log your first measurement" CTA, not the progress snapshot.
- The membership card is always the first element regardless of viewport size.

---

## 8. Membership status gates

The member's `membershipStatus` controls what they can do in the app.
These gates are enforced on the backend but must be reflected clearly in the UI.

| Status | Can book classes | Can pay online | Can message trainer | UI behaviour |
|---|---|---|---|---|
| `active` | Yes | Yes | Yes | Normal access |
| `frozen` | No | Yes | Yes | Banner explaining freeze + unfreeze date; booking buttons disabled with tooltip |
| `expired` | No | Yes | Yes (read-only thread) | Prominent renewal prompt on every page; class booking blocked |
| `expired-grace` | No | Yes | Yes | Same as expired; grace period days remaining shown |
| `terminated` | No | No | No | Read-only history view; contact club message |
| `pending` | No | No | No | Onboarding incomplete — prompt to complete registration and sign waiver |

- Status-dependent UI must use the `useAuthStore` `membershipStatus` field — never re-fetch status per page.
- Disabled booking buttons always show a tooltip explaining why (e.g., "Your membership is frozen until 15 Mar 2025").
- The renewal flow is available from every page via the topbar "Renew" button when status is `expired` or `expired-grace`.

---

## 9. GX class booking flow

The booking flow must be fast, clear, and resilient to race conditions (capacity can change between page load and submit).

### Schedule browser

- Default view: current week, all class types, all times.
- Filters: class type (yoga, HIIT, spinning, etc.), day of week, time of day (morning / afternoon / evening), instructor.
- Each class card shows: name, instructor, time, duration, room, enrolled / capacity, status.
- Class status variants for the booking button:

| State | Button label | Behavior |
|---|---|---|
| Available (spots open) | Book | Opens confirm dialog |
| Nearly full (≤ 20% remaining) | Book — X spots left | Opens confirm dialog with urgency note |
| Full | Join waitlist | Joins waitlist; position shown after |
| Member is booked | Cancel booking | Opens cancel dialog with policy warning |
| Member is on waitlist | On waitlist (#N) | Shows position; cancel waitlist option |
| Membership not active | — | Button hidden; status banner shown instead |
| Class in the past | Attended / Missed | Read-only badge |

### Booking confirmation

- Show a confirm dialog before any booking or cancellation action — never book on a single click.
- Confirm dialog for booking: class name, instructor, date/time, room, cancellation policy summary, "Confirm booking" button.
- Confirm dialog for cancellation: class name, date/time, cancellation fee warning if within the penalty window (fetched from the plan's cancellation policy), "Cancel booking" button.
- After successful booking: show a success toast and update the button state optimistically. If the server rejects (race condition — class filled between load and submit), show an inline error and offer the waitlist option.

### Cancellation policy display

- The cancellation policy (free cancellation window, penalty details) is fetched once per session and cached in TanStack Query with a long `staleTime` (1 hour).
- Always show the policy on the cancellation confirm dialog. Never assume the member knows the policy.
- If cancellation would incur a fee, the dialog must show the fee amount prominently and require an explicit acknowledgement checkbox before the confirm button is enabled.

---

## 10. Payment flow

Payments in Arena handle real money. These rules are non-negotiable.

### General rules

- **Never store raw card data.** Payment forms use the gateway's hosted fields or iframe embed. Card data never touches Arena's frontend or backend.
- All payment actions (pay balance, renew membership, purchase PT package) show a **payment summary screen** before the final confirm — amount, description, payment method, VAT breakdown. The member must see this before any charge.
- After a successful payment: show a success screen with the invoice number and a "Download receipt" link. Never just redirect silently.
- After a failed payment: show the failure reason from the gateway (in plain language, not a raw error code) and offer retry options.
- Payment processing shows a loading state that blocks the submit button. Double-submission must be impossible — the button is disabled from the moment of first submit until the result is received.

### Saved payment methods

- Members can save a tokenized card for future payments.
- Saved cards are displayed as: card type (Visa / Mastercard), last 4 digits, expiry month/year.
- Maximum 3 saved cards per member.
- Removing a saved card requires a confirm dialog.
- The default saved card is pre-selected on the payment form. Members can select a different saved card or enter a new one.

### Renewal flow

- Accessible from: the home page membership card "Renew now" CTA, the membership detail page, and the topbar button when status is `expired`.
- Shows: current plan, renewal period, amount (with VAT breakdown), available payment methods.
- If the club offers multiple plans, the member can choose to renew at a different (upgraded) plan on this screen.
- After successful renewal: membership status updates immediately in `useAuthStore`. No page reload required.

---

## 11. Progress tracking (member view)

- Members can view the body metrics and goals that their trainer has logged for them.
- Members can also **add their own metric entries** (self-reported weight, measurements). Self-reported entries are visually distinguished from trainer-entered entries in the chart and table.
- Metrics chart: line chart (Recharts) with time on x-axis. Toggle individual metrics on/off. Default view shows weight only.
- Goals: shown as cards with current value, target value, target date, and a progress bar. Members cannot edit goals — only trainers set goals in Coach.
- Members can add a note to their own metric entries (e.g., "After holiday — expect this to come down").
- No metrics are computed by the frontend. Derived values (BMI, body fat % trend) are returned by the backend.

---

## 12. Trainer messaging (member view)

- A member can only message the trainer they have an **active PT package** with.
- If the member has no active PT package: the messages page shows "You don't have an active PT package. Book a PT session to connect with a trainer." No compose form.
- If the PT package expires: the thread becomes read-only. A banner explains why and suggests renewing.
- Message thread UI: chronological, newest at the bottom. Auto-scrolls to the latest message on open.
- Compose bar: text only, 1000-character limit, live character count shown near the limit.
- Trainer broadcast messages (from GX classes) appear in a separate read-only "Announcements from your classes" section — they are not mixed into the PT message thread.
- Unread message count: shown on the Messages nav item and on the topbar notification bell.

---

## 13. In-app notifications

Arena uses **in-app notifications only** — no push notifications on web (that is a mobile feature).

Notifications are shown in a slide-out panel triggered from the topbar bell icon.
The panel shows the latest 20 notifications, newest first.

| Notification | Trigger | Action on click |
|---|---|---|
| Membership expiring in 7 days | System | Navigate to renewal flow |
| Membership expired | System | Navigate to renewal flow |
| Payment overdue | System | Navigate to payments page |
| PT session tomorrow | System (day before) | Navigate to session detail |
| Class booking confirmed | Member action | Navigate to my classes |
| Waitlist promoted | System | Navigate to class and confirm booking |
| Trainer message received | Trainer sends message | Navigate to message thread |
| Club announcement | Club staff via Pulse | Navigate to club info page |
| PT package expiring (1 session left) | System | Navigate to PT sessions page |

Rules:
- Notifications are fetched on page load and whenever the bell is opened. No polling on web (polling is acceptable only for the message thread).
- Message thread polls every 30 seconds while the thread page is open and the browser tab is visible (`document.visibilityState === 'visible'`). Stop polling when the tab is hidden.
- Clicking a notification marks it as read and navigates. Marking all as read is a single bulk action button in the panel header.
- Notifications older than 30 days are not shown.

---

## 14. Waiver & health disclaimer

- Members must sign the club's health waiver before their first GX booking or PT session.
- On login, if the waiver has not been signed (or has been updated since the last signature), a **full-screen modal** is shown that cannot be dismissed without signing or logging out.
- The waiver text is fetched from the backend (it is club-configurable in Pulse).
- Signing is a checkbox + "I agree and sign" button. The member's name and the timestamp are recorded server-side.
- If the club updates the waiver, all members are prompted to re-sign on next login.
- Waiver signature is enforced on the backend before any booking API call. The frontend prompt is UX only.

---

## 15. Authentication

- Login: email + password. No social login in v1.
- Forgot password: email input → reset link sent by backend → reset password form.
- After login, the JWT is stored in memory only (`useAuthStore` — not `localStorage`, not a cookie). On page refresh, the member is asked to log in again. This is intentional — it prevents token theft via XSS.
- Session expiry: JWT expires after the configured TTL. When the API returns `401`, the member is redirected to login with the `?redirect=` param so they land back where they were.
- No "remember me" functionality on web. That is a mobile feature.
- Rate limiting on login attempts is enforced on the backend. The frontend shows a generic "Too many attempts — please try again later" message on `429`.
- After successful login: if there is a pending `?redirect=` param, navigate there. Otherwise navigate to `/` (home).

---

## 16. Localization

- Default language is determined by the member's saved preference in their profile. On first visit, default to **Arabic** (primary market).
- Language can be changed at any time from the account preferences page and from a language switcher in the topbar.
- When locale is `ar`, root has `dir="rtl"`, all layout uses logical CSS properties.
- All strings use i18n keys. No hardcoded user-facing strings.
- Numbers: `Intl.NumberFormat` with active locale.
- Dates: `Intl.DateTimeFormat` with explicit Asia/Riyadh timezone and Hijri calendar shown alongside Gregorian on all date displays and pickers.
- Monetary amounts: always display in SAR with the Arabic currency symbol (ر.س) in Arabic locale, "SAR" in English locale.

---

## 17. Responsive design

Arena is the only app in this monorepo that must be **fully responsive across all viewports**.
Members access it from phones, tablets, and desktops equally.

| Breakpoint | Layout |
|---|---|
| Mobile (< 768px) | Single column. Bottom tab navigation (Home, Classes, Sessions, Messages, Account). Topbar is minimal: logo + notification bell only. |
| Tablet (768px – 1023px) | Single or two-column. Bottom tab navigation. Topbar adds the Renew button if applicable. |
| Desktop (1024px+) | Full sidebar navigation. Standard topbar. Two or three column layouts for schedule and progress views. |

Rules:
- The bottom tab navigation (`<MobileNav>`) is rendered only below 1024px. It is removed from the DOM on desktop — not hidden with CSS.
- Touch targets are minimum 44×44px for all interactive elements across all viewports.
- The class schedule grid collapses from a 7-column week view (desktop) to a 1-day scrollable view (mobile). On mobile, show one day at a time with prev/next day navigation.
- Payment forms must work correctly on mobile — hosted fields from the payment gateway must be tested on mobile viewports explicitly.
- No horizontal scrolling at any viewport width. Content reflows.

---

## 18. Styling

- Tailwind CSS utilities only. `cn()` for all conditional class names.
- Arena uses a **warmer, more energetic visual tone** than the internal apps (Nexus, Pulse, Coach), which are utility-focused. Arena is consumer-facing.
- Status colors are consistent with the membership status gate semantics:
  - Active: green
  - Frozen: blue
  - Expiring soon / warning: amber
  - Expired / overdue: red
  - Terminated: gray
- The `<MembershipCard>` component is the visual anchor of the home page. It should feel premium — a well-designed card, not a plain data row.
- Loading states use skeleton screens (not spinners) for the home page and class schedule — these are content-heavy views where a blank flash before data loads feels broken.
- Error states always offer a recovery action (retry button, contact support link) — never a dead end.

---

## 19. Security considerations specific to Arena

Arena is the only publicly accessible app. The others (Nexus, Pulse, Coach) are staff/internal tools.
Because Arena faces the public internet and handles payments, additional care is required.

- The login page must not reveal whether an email address is registered. "Invalid email or password" always — never "email not found".
- Password reset links expire after 15 minutes. This is enforced on the backend; the frontend shows an "This link has expired" page and offers to request a new one.
- The JWT is never stored in `localStorage` or `sessionStorage`. Memory only.
- The payment form uses the gateway's hosted fields. The CSP header must be configured to allow the gateway's iframe origin and nothing else for payment contexts.
- All member-identifying information (name, phone, membership ID) is stripped from URL params, query strings, and browser history. Navigation uses internal IDs or no ID at all (the backend derives identity from the JWT).
- The app must not log member personal data to the browser console in any environment.

---

## 20. Error handling

- All async errors through TanStack Query's `error` state or mutation `onError`.
- Global `<ErrorBoundary>` for unexpected runtime errors — shows a friendly recovery screen with a "Go home" button.
- RFC 7807 API errors show the `detail` field in a toast (plain Arabic or English — never a raw error code).
- `401` → clear auth state, redirect to login with `?redirect=`.
- `403` → show inline "You don't have access to this" — not a full-page redirect (members won't hit 403 in normal flows; this is a safety net).
- `429` → show "Too many requests — please wait a moment and try again."
- **Payment failures** show a dedicated failure screen (not a toast) with the reason, a retry option, and a "Contact the club" link.
- **Booking race condition failures** (class filled between load and submit) show an inline error on the class card with an "Join waitlist instead" offer.

---

## 21. Testing

- **Vitest + Testing Library** for all tests.
- **MSW** for all API mocking.
- Required coverage:
  - Auth guard: staff and trainer roles are blocked; `member` role is allowed; terminated member sees read-only view.
  - Membership status gates: booking buttons are disabled for `frozen`, `expired`, `terminated` statuses with correct tooltip text.
  - Booking flow: confirm dialog appears before booking; cancellation fee checkbox required when policy applies; race condition error handled gracefully with waitlist offer.
  - Payment form: submit button disabled after first click; success screen shown after payment; failure screen shown on gateway error.
  - Waiver modal: cannot be dismissed without signing when waiver is unsigned; does not appear after signing.
  - Mobile promo banner: not shown on desktop; not shown again within 7 days after dismissal; not shown on auth pages.
  - Notifications: unread count decrements after marking all read; thread polling stops when tab is hidden.

---

## 22. Performance

- Route-based code splitting via TanStack Router.
- The home page uses `Promise.all` to batch all parallel queries — no waterfalling.
- Class schedule grid uses `React.lazy` + `Suspense` — it is a heavy component.
- Progress charts are lazy-loaded.
- Message thread polls only when the tab is visible — use the Page Visibility API.
- `staleTime` tuning:
  - Membership status: `staleTime: 0` — always fresh (gates access to features).
  - Class schedule: `staleTime: 120_000` — classes don't change minute-to-minute.
  - Class capacity: `staleTime: 0` — always fresh on the booking confirmation step.
  - Payment history: `staleTime: 300_000`.
  - Club info / announcements: `staleTime: 600_000`.
- Images (trainer photos, member photos) are served via the backend with `Cache-Control` headers. Never store image data in app state.
- Skeleton screens on home and schedule — avoid layout shift on load.
