# CLAUDE.md — web-coach (Trainer Dashboard)

Inherits all rules from the root `CLAUDE.md`, including the global RBAC requirement.
This file adds rules specific to `web-coach`.

## Required reading for every frontend session
Read these files in this order before any frontend work:
1. CLAUDE.md (root)
2. web-coach/CLAUDE.md (this file)
3. docs/rbac.md
4. docs/domain-glossary.md

---

## 1. What this app is

**Coach** is the dedicated dashboard for club trainers — PT trainers and GX instructors.
It is not accessible to club staff (who use `web-pulse`), members, or the internal platform team.

Its responsibilities:
- Personal schedule and calendar: all PT sessions and GX classes in one view
- PT session management: session notes, attendance logging, progress tracking per member
- GX class management: enrollment lists, attendance taking, class notes
- Member communication: in-app messaging with assigned PT members and GX class participants
- Member progress tracking: body metrics, goals, training notes, progress summaries
- Trainer profile and credentials: bio, photo, certifications, specializations
- Availability management: working hours, time-off requests, blocked slots
- Performance and earnings visibility: sessions delivered, utilization, commission summary

Users of this app are `pt-trainer` and `gx-instructor` roles only.
Club staff (owner, manager, receptionist, sales) use `web-pulse`.
Club members use their own dedicated app (not in scope here).

---

## 2. Stack

- **React 18** with TypeScript (`strict: true`)
- **Vite** as the build tool and dev server
- **TanStack Router** for type-safe routing (file-based routing under `src/routes/`)
- **TanStack Query** for all server state
- **Zustand** for UI state only (sidebar, active view, modal state, message thread)
- **React Hook Form + Zod** for all forms and validation
- **Tailwind CSS** for styling
- **shadcn/ui** as the component base (extend, never modify internals)
- **@schedule-x/react** for the calendar and schedule views
- **@tanstack/react-virtual** for virtualized lists (message threads, session history)
- **i18next** for localization (Arabic + English, RTL support)
- **date-fns** for all date manipulation — no moment.js
- **MSW** for API mocking in development and tests
- **Vitest + Testing Library** for unit and component tests

---

## 3. RBAC — Coach roles

Coach has two roles. Roles are defined in `src/types/permissions.ts`.

| Role | Who holds it | What they can do |
|---|---|---|
| `pt-trainer` | Personal trainer | View and manage their own PT sessions, communicate with their assigned PT members, manage their profile and availability, view their own earnings |
| `gx-instructor` | Group exercise instructor | View and manage their own GX classes, take attendance, broadcast to class participants, manage profile and availability, view their own earnings |

Rules:
- A user can hold both `pt-trainer` and `gx-instructor` roles simultaneously (a trainer who teaches GX classes). In this case they see all features of both roles.
- Trainers only ever see their own data. A `pt-trainer` cannot view another trainer's sessions, members, messages, or earnings — even if they are in the same branch.
- Club staff attempting to log in to Coach are rejected at the auth guard with a message directing them to Pulse.
- Members attempting to log in to Coach are rejected at the auth guard with a message directing them to the member app.
- There is no admin or elevated role within Coach. Management actions (approving leave, reassigning sessions) are always done by club staff in Pulse.

---

## 4. Project structure

```
web-coach/
├── public/
├── src/
│   ├── api/
│   │   ├── client.ts               ← Axios instance, auth headers, interceptors
│   │   ├── schedule.ts             ← PT sessions + GX classes calendar data
│   │   ├── sessions.ts             ← PT session detail, notes, attendance
│   │   ├── classes.ts              ← GX class detail, enrollment, attendance
│   │   ├── members.ts              ← trainer's assigned PT members (read-only profile)
│   │   ├── progress.ts             ← body metrics, goals, training notes
│   │   ├── messaging.ts            ← message threads, send, read status
│   │   ├── availability.ts         ← working hours, blocked slots, leave requests
│   │   ├── profile.ts              ← trainer bio, photo, certifications
│   │   └── performance.ts          ← earnings, session history, utilization
│   │
│   ├── routes/
│   │   ├── __root.tsx              ← root layout, auth guard (trainer roles only)
│   │   ├── index.tsx               ← today's agenda (default landing page)
│   │   ├── schedule/
│   │   │   ├── index.tsx           ← weekly/monthly calendar view
│   │   │   └── $sessionId.tsx      ← session detail panel
│   │   ├── sessions/
│   │   │   ├── index.tsx           ← PT session list with filters
│   │   │   └── $sessionId.tsx      ← session detail: notes, attendance, plan
│   │   ├── classes/
│   │   │   ├── index.tsx           ← GX class schedule list
│   │   │   └── $classId.tsx        ← class detail: enrollment, attendance, notes
│   │   ├── members/
│   │   │   ├── index.tsx           ← assigned PT members list
│   │   │   └── $memberId.tsx       ← member profile: progress, notes, package
│   │   ├── messages/
│   │   │   ├── index.tsx           ← message inbox (thread list)
│   │   │   └── $threadId.tsx       ← message thread
│   │   ├── availability/
│   │   │   └── index.tsx           ← availability editor + leave requests
│   │   ├── performance/
│   │   │   └── index.tsx           ← sessions delivered, earnings, ratings
│   │   └── profile/
│   │       └── index.tsx           ← bio, photo, certifications, specializations
│   │
│   ├── components/
│   │   ├── ui/                     ← shadcn/ui base (never edit)
│   │   ├── layout/                 ← Sidebar, Topbar, PageShell, ErrorBoundary
│   │   ├── schedule/               ← CalendarView, AgendaCard, SessionBadge
│   │   ├── sessions/               ← SessionCard, SessionNoteForm, AttendanceToggle
│   │   ├── classes/                ← ClassCard, EnrollmentList, BulkAttendanceForm
│   │   ├── members/                ← MemberCard, ProgressChart, GoalTracker
│   │   ├── messages/               ← ThreadList, MessageBubble, ComposeForm
│   │   ├── availability/           ← AvailabilityGrid, BlockSlotForm, LeaveRequestForm
│   │   ├── performance/            ← EarningsCard, UtilizationChart, SessionHistoryTable
│   │   └── shared/                 ← ConfirmDialog, PermissionGate, EmptyState, FileUpload
│   │
│   ├── hooks/
│   ├── stores/
│   │   ├── useAuthStore.ts         ← current trainer, roles, club/branch context
│   │   ├── useSidebarStore.ts
│   │   ├── useCalendarStore.ts     ← active view (week/month/day), focused date
│   │   └── useModalStore.ts
│   ├── lib/
│   │   ├── formatCurrency.ts
│   │   ├── formatDate.ts           ← ISO → display, UTC → Asia/Riyadh, Hijri support
│   │   ├── cn.ts
│   │   └── permissions.ts          ← hasRole(role) helper
│   ├── types/
│   │   ├── api.ts
│   │   ├── domain.ts               ← TrainerSession, GXClass, PTMember, Message, Goal...
│   │   └── permissions.ts          ← Role enum ('pt-trainer' | 'gx-instructor')
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

## 5. Landing page — today's agenda

The default route (`/`) is the **today's agenda view**, not a generic dashboard.

It shows, in chronological order for today:
- All PT sessions scheduled for today: time, member name, session type, room/location, status
- All GX classes scheduled for today: time, class name, enrolled count / capacity, room
- Unread message count badge
- Expiring PT packages: members whose package runs out within 7 days (prompt to notify them)
- Any pending leave requests awaiting approval

This view auto-refreshes every 60 seconds while the page is open.
It is the page trainers open at the start of every workday and return to between sessions.
Design it for glanceability — large text, clear time labels, obvious status colors.

---

## 6. Calendar & schedule

- The schedule view uses `@schedule-x/react` rendering PT sessions and GX classes together on one calendar.
- Three views are available: **day**, **week** (default), **month**.
- PT sessions appear in one color family; GX classes in another — visually distinct at a glance.
- Clicking a session or class opens a detail side panel without navigating away from the calendar.
- The trainer cannot create, delete, or move sessions from this view. Scheduling is managed by club staff in Pulse. The calendar is read-only for the trainer.
- The trainer can add notes and mark attendance from the detail panel — those are the only write actions available from the calendar.
- Conflict highlighting: if two events overlap (a data integrity issue that should not occur but might during edge cases), both are highlighted in red with a warning.
- The calendar shows a "no sessions today" empty state with an encouraging message — do not show a blank grid.

---

## 7. PT session management

### What a trainer can do with a session

| Action | Allowed | Notes |
|---|---|---|
| View session detail | Yes | Always |
| Add pre-session plan / notes | Yes | Before session start time |
| Log post-session notes | Yes | After session start time |
| Mark attendance (present / no-show / late-cancel) | Yes | Once per session |
| Request reschedule | Yes | Creates a request in Pulse for staff to action |
| Cancel session (trainer-side) | Yes | With mandatory reason; notifies member via backend |
| Change session time directly | No | Only Pulse staff can move a booked session |
| View member's full financial record | No | Trainers see package balance only, not payment history |

### Session notes

- Pre-session notes: workout plan, exercises, sets/reps targets. Saved as a draft until the session time.
- Post-session notes: what was actually done, intensity rating (1–5), trainer observations, next session focus.
- Notes are visible to the member in their app after the session. Trainers must write notes with this in mind — they are not private.
- Notes are also visible to the branch manager in Pulse for oversight.
- Maximum note length: 2000 characters. Show a live character count.

### Attendance marking

- Attendance can only be marked within a window: 30 minutes before session start to 2 hours after session start.
- Outside this window, attendance marking is locked. The trainer must contact staff via Pulse messaging to make a correction.
- Status options: `present`, `no-show`, `late-cancel`, `trainer-cancelled`.
- `no-show` and `late-cancel` consume the session credit. `trainer-cancelled` does not. This is enforced on the backend but the UI must show a clear warning before the trainer confirms a `trainer-cancelled` status (it affects their performance metrics).

---

## 8. GX class management

### What a trainer can do with a class

| Action | Allowed | Notes |
|---|---|---|
| View enrollment list | Yes | Names and contact info of enrolled members |
| Take attendance | Yes | Bulk mark + individual exceptions |
| Add class notes | Yes | Post-class only |
| Broadcast message to enrolled members | Yes | Via messaging module |
| View waitlist | Yes | Read-only |
| Cancel a class | No | Only Pulse staff can cancel a class |
| Change class capacity | No | Only Pulse staff |
| Add or remove individual member bookings | No | Members book through their app; staff manage via Pulse |

### Attendance taking

- Attendance UI: show all enrolled members as a list. Default all to `present`. Trainer taps/clicks exceptions to mark `absent` or `late`.
- Bulk actions: "Mark all present" button as default starting state. "Mark all absent" for low-attendance scenarios.
- Attendance can be taken from session start until 3 hours after. After that, it locks and requires staff intervention in Pulse.
- Attendance is submitted as a single batch — not saved incrementally. A "Submit attendance" button sends the whole list at once. Show a confirmation before submitting (it cannot be undone without staff help).

---

## 9. Member profiles (trainer view)

Trainers have a **read-only, scoped view** of their assigned PT members. The scope is intentionally narrow.

A trainer can see:
- Member's name, photo, contact number, and preferred language
- Active PT package: type, total sessions, sessions used, sessions remaining, expiry date
- Body metrics history: weight, body fat %, measurements over time (chart + table)
- Goals set with this trainer: target, start value, current value, target date, status
- Training notes from previous sessions (all sessions with this trainer only)
- Upcoming scheduled sessions

A trainer cannot see:
- Member's membership plan or payment history
- Members assigned to other trainers
- Members who only attend GX classes (no PT package with this trainer)
- Any contact information beyond phone number (no address, no emergency contact, no ID numbers)

This scope is enforced on the backend. The frontend should not even request data outside this scope.

---

## 10. Progress tracking

- Body metrics are entered by the trainer on behalf of the member during or after a session, or by the member themselves in the member app. Both appear in the same timeline.
- Metrics tracked: weight (kg), body fat %, muscle mass %, BMI, chest/waist/hip/arm/thigh measurements (cm). Trainers can enter any subset — not all are required.
- Display as a line chart (Recharts) with time on the x-axis. Allow toggling which metrics are visible on the chart.
- Goals have a status: `active`, `achieved`, `abandoned`. Trainers can mark a goal as achieved or abandoned with a note.
- The progress summary PDF is generated server-side. The trainer triggers it from the member profile, selects a date range, and downloads the result. Never generate the PDF client-side.

---

## 11. Messaging

Messaging in Coach is **scoped and purposeful** — not a general chat tool.

### Who a trainer can message

- **PT members**: any member with an active PT package assigned to this trainer. One-to-one threads.
- **GX class broadcast**: send a message to all members enrolled in a specific upcoming class. One-to-many, no replies from members to a broadcast.
- **Club staff** (Pulse users): trainers can message their branch manager or reception team for operational matters (e.g., "I need to reschedule tomorrow's 9am session"). This is one-to-one with a Pulse user, not a group chat.

### Rules

- Trainers cannot initiate a message with a member who has no active PT package with them. Once a package expires, the thread becomes read-only for 30 days, then archives.
- All messages are stored server-side and are visible to the branch manager in Pulse for compliance and oversight. Trainers are informed of this in the UI (a persistent notice in the messaging section).
- Message content must not include payment terms, pricing, or financial offers. If a trainer tries to send a message containing pricing keywords, show a warning (not a block — the backend is the enforcement layer).
- No file attachments in the initial version. Text only, with a 1000-character limit per message.
- Message delivery status: `sent`, `delivered`, `read`. Show as subtle indicators on sent messages.
- Unread message count is shown as a badge on the Messages nav item and in the topbar notification bell.

### Message templates

- Trainers can save and reuse message templates for common scenarios.
- Templates are personal to the trainer — not shared with other trainers.
- Maximum 10 saved templates. Each template has a name (internal) and body text.
- Templates can include placeholders: `{{member_name}}`, `{{next_session_date}}`, `{{sessions_remaining}}`.
- The compose form offers a "Use template" button that opens a template picker and inserts the selected template, replacing placeholders with live values.

---

## 12. Availability management

- The availability editor shows a weekly grid (Mon–Sun, configurable hours).
- Trainers set their recurring availability by clicking and dragging on the grid to mark available slots.
- Specific dates can override the recurring pattern: mark a date as unavailable (e.g., a public holiday) or available (e.g., an extra Saturday).
- **Leave requests**: formal requests for full or partial days off. Submitted through the app, visible to the branch manager in Pulse for approval or rejection.
  - Required fields: date range, reason (free text), whether existing sessions need reassignment.
  - Status: `pending`, `approved`, `rejected`.
  - When approved, the trainer sees the approved leave on their calendar. Sessions in the period are flagged in Pulse for staff to reassign.
  - When rejected, the trainer sees the rejection reason (entered by the manager in Pulse).
- Trainers cannot delete or modify availability in a way that would conflict with already-booked sessions. The UI shows a warning and blocks the save if a conflict would be created.
- Availability changes take effect after a configurable lead time (e.g., 24 hours) — this prevents a trainer from removing availability for a session booked for tomorrow.

---

## 13. Profile & credentials

- Trainers manage their own profile: display name, photo, bio (Arabic and English), specializations, languages spoken, years of experience.
- Profile changes go live immediately — no approval required for bio and photo.
- **Certifications**: uploaded as PDF or image. Each certification has: name, issuing body, issue date, expiry date, file.
  - After upload, the certification shows as `pending-review` until a branch manager approves it in Pulse.
  - Approved certifications are shown publicly on the trainer's profile in the member app.
  - Rejected certifications show the rejection reason from Pulse.
  - Expiring certifications (within 30 days) trigger a notification and are highlighted on the profile page.
- **Services offered**: the trainer lists the types of PT they offer (e.g., strength training, weight loss, rehabilitation). This list is used by Pulse staff when creating PT packages and matching members to trainers.
- Profile completeness indicator: show a percentage completion score and a checklist of missing items (photo, bio, at least one certification, at least one specialization). Incomplete profiles are flagged to the branch manager in Pulse.

---

## 14. Performance & earnings

- Trainers can view their own performance data. They cannot view other trainers' data.
- **Sessions delivered**: count of completed PT sessions and GX classes per period, with a trend chart.
- **PT package utilization**: for each active package, how many sessions have been used vs. the total. Packages with low utilization (member not attending) are highlighted — the trainer should follow up.
- **Earnings**: the commission amount owed per period, based on the commission configuration set in Pulse. Trainers see the computed total and the breakdown by session. They do not see the commission rate formula or configuration — only the result.
- **Ratings**: if the member app supports session ratings (future feature), average rating per trainer is shown here.
- Date range selector: current month (default), last month, last 3 months, custom range.
- Export: the performance summary can be exported as a PDF. Generated server-side, downloaded via the same trigger-and-poll pattern used elsewhere.
- No financial data beyond earnings is shown (no club revenue, no member payment history, no plan pricing).

---

## 15. Notifications

Notifications are fetched on a **30-second polling interval** (more frequent than Pulse because trainers need timely session alerts).

| Notification | Trigger |
|---|---|
| New PT session booked | Reception books a session in Pulse |
| Session cancelled | Member or staff cancels a session |
| Reschedule request from member | Member requests via member app |
| Leave request approved | Branch manager approves in Pulse |
| Leave request rejected | Branch manager rejects in Pulse, includes reason |
| New message from PT member | Member sends a message |
| PT package expiring soon | Member has ≤ 1 session remaining or package expires in ≤ 7 days |
| GX enrollment change | Member books or cancels from a class |
| Certification expiring | 30 days before the certification expiry date |
| Session attendance window closing | 15-minute warning before attendance marking locks |

Rules:
- Notifications are per-trainer. A trainer only receives notifications relevant to their own sessions, classes, and members.
- Clicking a notification navigates to the relevant page and entity (session, class, member, message thread).
- Notifications are marked read when clicked. Unread count is shown on the bell icon in the topbar.
- Notifications are stored server-side and persisted across sessions for 30 days.

---

## 16. Forms

- React Hook Form + Zod for every form.
- Validate on submit. Field-level inline errors in Arabic and English via i18n keys.
- Toast on success. RFC 7807 `detail` field shown on API error.
- Session note forms auto-save as a draft every 30 seconds using a debounced mutation. Show a "Draft saved" indicator. The draft is discarded when the form is submitted or cancelled.
- Attendance submission always shows a confirmation step: "You are about to submit attendance for N members. This cannot be undone without staff help. Confirm?"
- Leave request form requires explicit confirmation of the date range and whether sessions need reassignment before submission.

---

## 17. Localization

- Default language is **Arabic**. English must be fully supported.
- When locale is `ar`, root has `dir="rtl"`, all layout uses logical CSS properties.
- All strings use i18n keys. No hardcoded user-facing strings.
- Numbers: `Intl.NumberFormat` with active locale.
- Dates: `Intl.DateTimeFormat` with explicit Asia/Riyadh timezone. Hijri calendar shown alongside Gregorian on all date pickers and date displays.
- Session duration is displayed in minutes: "45 min" not "0:45".

---

## 18. Styling

- Tailwind CSS utilities only. `cn()` for conditional classes.
- **PT sessions** use the teal color family across the app (calendar events, session badges, package indicators).
- **GX classes** use the purple color family across the app.
- This color distinction is consistent everywhere — calendar, lists, detail panels, notifications. Never swap them.
- Status colors:
  - `present` / `completed`: green
  - `no-show` / `trainer-cancelled`: red
  - `late-cancel`: amber
  - `scheduled` / `upcoming`: blue
  - `pending-review` (certifications, leave): amber
  - `approved`: green
  - `rejected`: red
- Coach must be fully usable on **tablets** (768px+). Trainers frequently use tablets at the gym floor between sessions. Fully functional mobile view (375px+) is also required — trainers check their schedule and mark attendance on their phones.
- Touch targets must be at least 44×44px. No hover-only interactions for primary actions.

---

## 19. Error handling

- All async errors through TanStack Query's `error` state or mutation `onError`.
- Global `<ErrorBoundary>` for unexpected runtime errors.
- RFC 7807 errors show `detail` in a toast.
- `401` → redirect to login preserving URL.
- `403` → redirect to 403 page. If a `club-owner` or `receptionist` accidentally tries to access Coach, the 403 page clearly states: "Coach is for trainers only. If you manage the club, use Pulse."
- Attendance submission failures show a prominent non-dismissible alert (not just a toast) — the trainer must know the submission did not go through.
- Session note auto-save failures show a subtle persistent indicator ("Draft not saved — check your connection") without interrupting the trainer's flow.

---

## 20. Testing

- **Vitest + Testing Library** for all tests.
- **MSW** for API mocking at the network layer.
- Required coverage:
  - Auth guard: `club-owner`, `receptionist`, `member` roles are blocked; `pt-trainer` and `gx-instructor` are allowed.
  - Role scoping: a `pt-trainer` cannot access GX-only routes; a `gx-instructor` cannot access PT session management routes (when not holding both roles).
  - Session notes: auto-save fires after 30 seconds of inactivity; draft discarded on submit.
  - Attendance: marking window enforced — form is disabled outside the allowed window; confirmation step shown before submit.
  - Messaging: compose form blocked for members with no active PT package.
  - Availability: conflict detection fires when a change would clash with a booked session.
  - Progress chart: toggling a metric removes it from the chart without unmounting.

---

## 21. Performance

- Route-based code splitting via TanStack Router.
- Calendar view (`@schedule-x/react`) is lazy-loaded — it is a heavy component.
- Message thread list uses TanStack Virtual for long threads.
- Session history table uses TanStack Virtual for trainers with long histories.
- Today's agenda auto-refreshes every 60 seconds (`refetchInterval: 60_000`).
- Notifications poll every 30 seconds (`refetchInterval: 30_000`).
- Performance reports use `staleTime: 300_000` — they do not need to be live.
- Calendar data prefetches the adjacent week on idle using TanStack Query's `prefetchQuery`.
- All images (member photos, trainer photo) are served via the backend with appropriate cache headers. The frontend always uses the backend URL — never a data URL or blob URL stored in state.
