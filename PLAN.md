# PLAN.md — Notification System (Plan 21)

## Status
Ready for implementation

## Branch
feat/notification-system

## Goal
Add a persistent in-app notification system with optional email delivery.
Notifications are triggered by key business events (membership expiring,
payment collected, GX class booked, etc.) and delivered to the relevant
user — staff in web-pulse, members in web-arena. A notification bell in
each app's topbar shows the unread count and links to a notification list.
Email delivery is optional per notification type, reusing the
JavaMailSender infrastructure from Plan 20.

## Context
- JavaMailSender + Mailpit are already set up (Plan 20).
- `AuditService` already writes records on every business event — the
  notification system sits alongside it, not on top of it.
- `AuditAction` enum already lists all write events — notification triggers
  map to a subset of these.
- `Member`, `Membership`, `MembershipPlan`, `GXBooking`, `GXClassInstance`,
  `Payment`, `Invoice`, `PTSession`, `Lead` all exist.
- All four web apps have a topbar (`AppHeader`) — notification bell added
  to each relevant one (web-pulse, web-arena).
- web-coach and web-nexus get the bell too but with fewer event types.
- `ddl-auto: create-drop` in dev — Flyway V13 for `notifications` table.
- Redis already running — used for unread count caching per user.

---

## Scope — what this plan covers

### Backend
- [ ] `Notification.kt` entity + `NotificationRepository.kt`
- [ ] `NotificationService.kt` — create, list, mark read, mark all read
- [ ] `NotificationTriggerService.kt` — listens to business events via
  Spring application events and creates the right notifications
- [ ] `NotificationSchedulerService.kt` — `@Scheduled` daily job that
  generates proactive notifications (expiring memberships, upcoming PT sessions)
- [ ] `NotificationPulseController.kt` — staff notifications
- [ ] `NotificationArenaController.kt` — member notifications
- [ ] `NotificationCoachController.kt` — trainer notifications
- [ ] Wire `ApplicationEvent` publishing into existing services
- [ ] Flyway V13: `notifications` table
- [ ] Unit tests: `NotificationServiceTest`, `NotificationTriggerServiceTest`
- [ ] Integration tests: all three notification controllers

### Frontend
- [ ] web-pulse: notification bell in AppHeader, notification drawer/list
- [ ] web-arena: notification bell in AppHeader, notification list screen
- [ ] web-coach: notification bell in AppHeader, notification drawer
- [ ] Shared pattern: `useNotifications` hook, `NotificationBell`,
  `NotificationItem` components per app

---

## Out of scope — do not implement in this plan
- Push notifications (mobile — no mobile app yet)
- WebSocket / real-time push to browser (polling only in this plan)
- SMS notifications (no SMS gateway)
- Notification preferences UI per user (future plan — all enabled by default)
- Notification grouping / threading (flat list only)
- web-nexus notifications (platform team does not need operational alerts)

---

## Decisions already made

- **Spring ApplicationEvents for decoupling**: existing services publish
  a typed `ApplicationEvent` subclass (e.g. `MembershipAssignedEvent`,
  `PaymentCollectedEvent`) immediately after the business operation succeeds.
  `NotificationTriggerService` is an `@EventListener` — it receives the
  event and creates the notification. This keeps notification logic
  completely out of the business services.

- **`Notification` entity is append-only for creation, mutable for
  `readAt`**: created once, never updated except to set `read_at` when
  the user reads it. No soft delete — notifications expire after 90 days
  (handled by a scheduled cleanup job in `NotificationSchedulerService`).

- **Recipient is a `userId` (User.publicId UUID)**: notifications target
  a specific user — staff member, trainer, or member — via their User
  record. The `scope` field (`club`, `trainer`, `member`) determines which
  app's API serves it.

- **Unread count in Redis**: `notification_unread:{userId}` stores the
  count as an integer. Incremented on notification creation, decremented
  on read (or reset to 0 on mark-all-read). TTL: 24 hours (refreshed on
  access). Cache miss → count from DB. This avoids a DB count query on
  every topbar render.

- **Polling, not WebSocket**: each app polls `GET /notifications/unread-count`
  every 30 seconds. On count change → fetch the notification list.
  Simple, reliable, no infrastructure change needed.

- **Email delivery is per notification type**: a `NotificationType` enum
  carries a boolean `sendEmail`. Types that send email:
  `MEMBERSHIP_EXPIRING_SOON`, `PAYMENT_COLLECTED`, `PT_SESSION_REMINDER`.
  Types that are in-app only: `GX_CLASS_BOOKED`, `GX_CLASS_CANCELLED`,
  `LEAD_ASSIGNED`, `MEMBERSHIP_FROZEN`. Email uses the existing
  `ReportEmailService` pattern (JavaMailSender, HTML body, no attachment).

- **Notification types and recipients**:

  | Type | Trigger | Recipient | Email? |
  |---|---|---|---|
  | MEMBERSHIP_EXPIRING_SOON | Scheduler: expiry in 7 days | Member | ✅ |
  | MEMBERSHIP_ASSIGNED | MembershipAssignedEvent | Member | ❌ |
  | MEMBERSHIP_FROZEN | MembershipFrozenEvent | Member | ❌ |
  | PAYMENT_COLLECTED | PaymentCollectedEvent | Member | ✅ |
  | GX_CLASS_BOOKED | GxBookedEvent | Member | ❌ |
  | GX_CLASS_CANCELLED | GxCancelledEvent | Member | ❌ |
  | GX_CLASS_REMINDER | Scheduler: class in 2 hours | Member | ❌ |
  | PT_SESSION_REMINDER | Scheduler: session tomorrow | Member | ✅ |
  | PT_ATTENDANCE_MARKED | PtAttendanceMarkedEvent | Member | ❌ |
  | LEAD_ASSIGNED | LeadAssignedEvent | Staff (assignee) | ❌ |
  | NEW_MEMBER_REGISTERED | MemberCreatedEvent | Staff (branch manager) | ❌ |
  | LOW_GX_SPOTS | Scheduler: class < 3 spots, 24h before | Staff (GX instructor) | ❌ |

- **`NotificationSchedulerService` runs at 06:00 UTC daily**:
  - Membership expiring in exactly 7 days → `MEMBERSHIP_EXPIRING_SOON`
    (deduplicated: skip if already sent in last 24h for same membership)
  - PT sessions tomorrow → `PT_SESSION_REMINDER` for each member
  - GX classes with < 3 spots in next 24h → `LOW_GX_SPOTS` for instructor
  - Delete notifications older than 90 days (cleanup)

- **Deduplication for scheduler notifications**: before creating a
  proactive notification, check if an identical one (same type + same
  `entityId`) was created in the last 24 hours. Skip if so. Prevents
  duplicate reminders from scheduler restarts.

- **Flyway V13**.

---

## Entity design

### Notification

Fields beyond standard AuditEntity columns (no soft delete — use cleanup
scheduler instead):

```
recipient_user_id   VARCHAR(100) NOT NULL   (User.publicId UUID)
recipient_scope     VARCHAR(20) NOT NULL    'club' | 'trainer' | 'member'
type                VARCHAR(60) NOT NULL    NotificationType enum value
title_key           VARCHAR(100) NOT NULL   i18n key for title
body_key            VARCHAR(100) NOT NULL   i18n key for body
params_json         TEXT                    nullable, JSON of i18n interpolation params
entity_type         VARCHAR(100)            nullable ('Membership', 'Payment', etc.)
entity_id           VARCHAR(100)            nullable (publicId of related entity)
read_at             TIMESTAMPTZ             nullable — null = unread
email_sent_at       TIMESTAMPTZ             nullable — null = not sent or not applicable
```

Note: title and body are stored as i18n keys + params rather than
rendered strings, so the frontend can render in the user's preferred
language.

### Flyway V13

```sql
CREATE TABLE notifications (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    recipient_user_id   VARCHAR(100) NOT NULL,
    recipient_scope     VARCHAR(20) NOT NULL,
    type                VARCHAR(60) NOT NULL,
    title_key           VARCHAR(100) NOT NULL,
    body_key            VARCHAR(100) NOT NULL,
    params_json         TEXT,
    entity_type         VARCHAR(100),
    entity_id           VARCHAR(100),
    read_at             TIMESTAMPTZ,
    email_sent_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_recipient  ON notifications(recipient_user_id, read_at);
CREATE INDEX idx_notifications_type       ON notifications(type);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
CREATE INDEX idx_notifications_entity     ON notifications(entity_type, entity_id);
```

---

## API endpoints

### NotificationPulseController — `/api/v1/notifications` (club staff)

```
GET    /api/v1/notifications?page=0&size=20&unreadOnly=false
GET    /api/v1/notifications/unread-count
PATCH  /api/v1/notifications/{id}/read
PATCH  /api/v1/notifications/read-all
```

### NotificationArenaController — `/api/v1/arena/notifications` (members)

```
GET    /api/v1/arena/notifications?page=0&size=20&unreadOnly=false
GET    /api/v1/arena/notifications/unread-count
PATCH  /api/v1/arena/notifications/{id}/read
PATCH  /api/v1/arena/notifications/read-all
```

### NotificationCoachController — `/api/v1/coach/notifications` (trainers)

```
GET    /api/v1/coach/notifications?page=0&size=20&unreadOnly=false
GET    /api/v1/coach/notifications/unread-count
PATCH  /api/v1/coach/notifications/{id}/read
PATCH  /api/v1/coach/notifications/read-all
```

No new permissions needed — all notification endpoints require only a
valid JWT with the matching scope.

---

## Request / Response shapes

### NotificationResponse
```json
{
  "id": "uuid",
  "type": "MEMBERSHIP_EXPIRING_SOON",
  "titleKey": "notification.membership_expiring.title",
  "bodyKey": "notification.membership_expiring.body",
  "params": { "planName": "Basic Monthly", "daysRemaining": 7 },
  "entityType": "Membership",
  "entityId": "uuid",
  "readAt": "ISO 8601 | null",
  "createdAt": "ISO 8601"
}
```

### UnreadCountResponse
```json
{ "count": 3 }
```

### i18n key examples (resolved on frontend)
```json
{
  "notification.membership_expiring.title": "Membership expiring soon",
  "notification.membership_expiring.body": "Your {{planName}} membership expires in {{daysRemaining}} days.",
  "notification.payment_collected.title": "Payment received",
  "notification.payment_collected.body": "A payment of {{amountSar}} SAR has been collected.",
  "notification.gx_booked.title": "Class booked",
  "notification.gx_booked.body": "You are booked for {{className}} on {{classDate}}.",
  "notification.pt_session_reminder.title": "PT session tomorrow",
  "notification.pt_session_reminder.body": "Your PT session with {{trainerName}} is tomorrow at {{time}}.",
  "notification.lead_assigned.title": "New lead assigned",
  "notification.lead_assigned.body": "{{leadName}} has been assigned to you.",
  "notification.low_gx_spots.title": "Low spots alert",
  "notification.low_gx_spots.body": "{{className}} tomorrow has only {{spotsRemaining}} spots left."
}
```

---

## ApplicationEvents to publish from existing services

```kotlin
// New event classes in notification/events/
MembershipAssignedEvent(membership, member)
MembershipFrozenEvent(membership, member)
PaymentCollectedEvent(payment, member)
GxBookedEvent(booking, member, classInstance)
GxCancelledEvent(booking, member, classInstance)
PtAttendanceMarkedEvent(session, member)
LeadAssignedEvent(lead, assigneeStaffMember)
MemberCreatedEvent(member, branchManagerUser)
```

Services that need `applicationEventPublisher.publishEvent(...)` added:
- `MembershipService` → publish `MembershipAssignedEvent`, `MembershipFrozenEvent`
- `PaymentService` → publish `PaymentCollectedEvent`
- `GxArenaService` → publish `GxBookedEvent`, `GxCancelledEvent`
- `PtCoachService` → publish `PtAttendanceMarkedEvent`
- `LeadService` → publish `LeadAssignedEvent`
- `MemberService` → publish `MemberCreatedEvent`

---

## Business rules — enforce in service layer

1. **Recipient must exist**: `NotificationService.create()` verifies
   `recipientUserId` is a known User publicId. Log WARN and skip if not
   found — never throw (same pattern as AuditService).

2. **Notifications never throw**: `NotificationTriggerService` catches all
   exceptions in `@EventListener` methods. A notification failure must
   never cause the business operation to fail or roll back.

3. **Deduplication for scheduler notifications**: before creating
   `MEMBERSHIP_EXPIRING_SOON`, `PT_SESSION_REMINDER`, `LOW_GX_SPOTS`,
   `GX_CLASS_REMINDER` — check if an identical notification (same type +
   same `entityId`) was created in the last 24 hours. Skip if found.

4. **Mark-read scoped to recipient**: `PATCH /notifications/{id}/read`
   verifies `notification.recipientUserId == JWT sub`. Return 403 if not.

5. **Unread count from Redis**: `GET /notifications/unread-count` checks
   Redis key `notification_unread:{userId}` first. Cache miss → COUNT(*)
   from DB where `readAt IS NULL`. Store in Redis with 24h TTL.

6. **Redis unread count update**: on notification creation → `INCR
   notification_unread:{userId}`. On mark-read → `DECR`. On mark-all-read
   → `SET notification_unread:{userId} 0`. Always refresh TTL to 24h.

7. **Email for applicable types**: after persisting the notification,
   if `NotificationType.sendEmail = true` AND the recipient has an email
   address → send via `ReportEmailService` pattern (JavaMailSender).
   Email failure is non-fatal — log ERROR, set `emailSentAt` only on success.

8. **Cleanup**: scheduler deletes notifications older than 90 days daily.
   Uses `DELETE FROM notifications WHERE created_at < NOW() - INTERVAL '90 days'`
   via `nativeQuery = true`.

9. **`LOW_GX_SPOTS` sent to instructor only**: only the GX instructor
   assigned to the class instance receives this notification — not all
   staff members.

10. **`NEW_MEMBER_REGISTERED` sent to branch manager**: fetch the Branch
    Manager `UserRole` for the same branch as the new member. If multiple
    branch managers exist → notify all of them.

---

## Seed data updates

No new seed data needed. On first `bootRun` with dev profile, the
scheduler will generate notifications based on existing seeded data:
- Ahmed's Basic Monthly membership (7 days before expiry if timing matches)
- Ahmed's upcoming PT session reminder
- Upcoming GX class low-spots alert for Noura

These appear naturally without any DevDataLoader changes.

---

## Files to generate

### Backend — new files
```
notification/
  Notification.kt
  NotificationRepository.kt
  NotificationService.kt
  NotificationTriggerService.kt    (@EventListener for all event types)
  NotificationSchedulerService.kt  (@Scheduled 06:00 UTC, cleanup)
  NotificationEmailService.kt      (thin wrapper over JavaMailSender for notifications)
  NotificationPulseController.kt
  NotificationArenaController.kt
  NotificationCoachController.kt
  events/
    MembershipAssignedEvent.kt
    MembershipFrozenEvent.kt
    PaymentCollectedEvent.kt
    GxBookedEvent.kt
    GxCancelledEvent.kt
    PtAttendanceMarkedEvent.kt
    LeadAssignedEvent.kt
    MemberCreatedEvent.kt
  dto/
    NotificationResponse.kt
    UnreadCountResponse.kt

resources/db/migration/V13__notifications.sql
```

### Backend — modified files
```
membership/MembershipService.kt      publish MembershipAssignedEvent, MembershipFrozenEvent
payment/PaymentService.kt            publish PaymentCollectedEvent
arena/GxArenaService.kt              publish GxBookedEvent, GxCancelledEvent
coach/PtCoachService.kt              publish PtAttendanceMarkedEvent
lead/LeadService.kt                  publish LeadAssignedEvent
member/MemberService.kt              publish MemberCreatedEvent
audit/AuditAction.kt                 no change (notifications are separate from audit)
```

### Frontend — web-pulse additions
```
src/api/notifications.ts
src/hooks/useNotifications.ts        (polling every 30s, unread count, list)
src/components/notifications/
  NotificationBell.tsx               (bell icon + unread badge)
  NotificationDrawer.tsx             (slide-out panel with list)
  NotificationItem.tsx               (single notification row)
src/components/shell/AppHeader.tsx   (modify: add NotificationBell)
```

### Frontend — web-arena additions
```
src/api/notifications.ts
src/hooks/useNotifications.ts
src/components/notifications/
  NotificationBell.tsx
  NotificationItem.tsx
src/routes/notifications.tsx         (full-page list for mobile)
src/components/shell/AppHeader.tsx   (modify: add NotificationBell)
```

### Frontend — web-coach additions
```
src/api/notifications.ts
src/hooks/useNotifications.ts
src/components/notifications/
  NotificationBell.tsx
  NotificationDrawer.tsx
  NotificationItem.tsx
src/components/shell/AppHeader.tsx   (modify: add NotificationBell)
```

---

## Implementation order

```
Step 1 — Notification entity + Flyway V13
  notification/Notification.kt — entity, no soft delete, readAt nullable
  notification/NotificationRepository.kt:
    findByRecipientUserIdOrderByCreatedAtDesc(userId, pageable)
    countByRecipientUserIdAndReadAtIsNull(userId)
    existsByTypeAndEntityIdAndCreatedAtAfter(type, entityId, cutoff) ← dedup check
    deleteByCreatedAtBefore(cutoff) ← cleanup (nativeQuery=true)
  resources/db/migration/V13__notifications.sql — table + 4 indexes
  Verify: ./gradlew build -x test

Step 2 — ApplicationEvent classes + publish from services
  notification/events/*.kt — 8 event data classes (Kotlin data class)
  Modify MembershipService, PaymentService, GxArenaService, PtCoachService,
    LeadService, MemberService — inject ApplicationEventPublisher,
    call publishEvent() after successful business operation
  No logic in services — just publish. NotificationTriggerService handles the rest.
  Verify: ./gradlew build -x test

Step 3 — NotificationService (core CRUD)
  notification/NotificationService.kt:
    create(recipientUserId, scope, type, titleKey, bodyKey, paramsJson?,
           entityType?, entityId?):
      - Verify recipient exists (rule 1) — WARN + return if not
      - Deduplicate scheduler types (rule 3)
      - Persist Notification
      - INCR Redis unread count (rule 6)
      - If type.sendEmail → NotificationEmailService.send() non-fatally (rule 7)
    listNotifications(userId, unreadOnly, pageable)
    getUnreadCount(userId): Redis → DB fallback (rule 5)
    markRead(notificationId, userId): rule 4, DECR Redis (rule 6)
    markAllRead(userId): bulk update, SET Redis to 0 (rule 6)
  DTOs: NotificationResponse, UnreadCountResponse
  Verify: ./gradlew build -x test

Step 4 — NotificationTriggerService (@EventListener)
  notification/NotificationTriggerService.kt:
    @EventListener for each event type → calls NotificationService.create()
    All in try/catch — never throws (rule 2)
    Maps event data to titleKey + bodyKey + paramsJson
    Example:
      on MembershipAssignedEvent → create MEMBERSHIP_ASSIGNED for member
      on PaymentCollectedEvent → create PAYMENT_COLLECTED for member
      on LeadAssignedEvent → create LEAD_ASSIGNED for assignee staff member
      on MemberCreatedEvent → create NEW_MEMBER_REGISTERED for branch managers
  Verify: ./gradlew build -x test

Step 5 — NotificationSchedulerService
  notification/NotificationSchedulerService.kt:
    @Scheduled(cron = "0 0 6 * * *", zone = "UTC") runDailyNotifications():
      1. MEMBERSHIP_EXPIRING_SOON: find memberships expiring in 7 days,
         dedup (rule 3), create for each member
      2. PT_SESSION_REMINDER: find PT sessions tomorrow for each member,
         dedup, create
      3. LOW_GX_SPOTS: find GX instances in next 24h with < 3 spots,
         dedup, create for instructor (rule 9)
      4. Cleanup: delete notifications older than 90 days (rule 8)
    All DB queries: nativeQuery=true (no JPQL date arithmetic)
  Verify: ./gradlew build -x test

Step 6 — NotificationEmailService + controllers
  notification/NotificationEmailService.kt:
    sendNotificationEmail(recipientEmail, titleKey, bodyKey, paramsJson?):
      Resolve subject + body from hardcoded English strings (not i18n on
      backend — backend always sends English email; member's language
      preference applies to in-app display only)
      Send via JavaMailSender — non-fatal on failure
  NotificationPulseController.kt — 4 endpoints, scope=club JWT
  NotificationArenaController.kt — 4 endpoints, scope=member JWT
  NotificationCoachController.kt — 4 endpoints, scope=trainer JWT
  Verify: ./gradlew build -x test

Step 7 — Backend tests
  NotificationServiceTest.kt (unit):
    - create: persists + Redis INCR
    - create: unknown recipient → WARN + skip, no throw (rule 1)
    - create: dedup scheduler type within 24h → skipped (rule 3)
    - markRead: own notification → readAt set, Redis DECR (rule 4)
    - markRead: other user's notification → 403 (rule 4)
    - markAllRead: Redis SET 0 (rule 6)
    - getUnreadCount: Redis hit → no DB query; cache miss → DB count + cache
  NotificationTriggerServiceTest.kt (unit):
    - MembershipAssignedEvent → MEMBERSHIP_ASSIGNED created for member
    - PaymentCollectedEvent → PAYMENT_COLLECTED + email sent for member
    - LeadAssignedEvent → LEAD_ASSIGNED for correct assignee
    - Exception in listener → caught, not re-thrown (rule 2)
  NotificationPulseControllerTest.kt (integration):
    - GET list: returns staff notifications only
    - GET unread-count: returns correct count
    - PATCH read: marks own notification read
    - PATCH read other user's → 403
    - PATCH read-all: all unread → read
  Verify: ./gradlew test --no-daemon

Step 8 — Backend final checks
  ./gradlew ktlintFormat --no-daemon
  ./gradlew ktlintCheck --no-daemon
  ./gradlew build --no-daemon

Step 9 — Frontend: web-pulse notification bell + drawer
  src/api/notifications.ts — getNotifications, getUnreadCount, markRead, markAllRead
  src/hooks/useNotifications.ts:
    useQuery for list + unreadCount
    Poll unreadCount every 30s (refetchInterval: 30000)
    On count increase → invalidate list query
    markRead / markAllRead mutations
  src/components/notifications/NotificationBell.tsx:
    Bell icon (Lucide BellIcon) + red badge with unread count (hidden if 0)
    Click → opens NotificationDrawer
  src/components/notifications/NotificationDrawer.tsx:
    Slide-out panel from right (web-pulse sidebar-style)
    "Notifications" header + "Mark all read" button
    List of NotificationItem components
    Empty state: "No notifications"
  src/components/notifications/NotificationItem.tsx:
    Icon by type, title (i18n resolved), body (i18n resolved with params),
    timestamp (relative: "2 hours ago"), unread dot, click → markRead
  Modify src/components/shell/AppHeader.tsx: add NotificationBell
  Verify: npm run dev → login as owner → bell shows count → open drawer →
    items visible → mark read → count decrements

Step 10 — Frontend: web-arena notification screen
  src/api/notifications.ts (arena base URL)
  src/hooks/useNotifications.ts
  src/components/notifications/NotificationBell.tsx — tap → /notifications
  src/routes/notifications.tsx:
    Full-page list (mobile-first, not a drawer)
    "Mark all read" button at top
    NotificationItem list with tap-to-read
    Unread items have highlighted background
  Modify src/components/shell/AppHeader.tsx: add NotificationBell
  Verify: npm run dev → login as Ahmed → bell shows count → tap → full page

Step 11 — Frontend: web-coach notification drawer
  src/api/notifications.ts (coach base URL)
  src/hooks/useNotifications.ts
  src/components/notifications/ (same pattern as web-pulse)
  Modify src/components/shell/AppHeader.tsx: add NotificationBell
  Verify: npm run dev → login as Khalid → bell shows PT session reminder

Step 12 — Frontend tests + final checks (all three apps)
  web-pulse:
    NotificationBell.test.tsx — badge shows count, hidden when 0
    NotificationItem.test.tsx — resolves i18n key + params, unread dot
    NotificationDrawer.test.tsx — mark all read button calls mutation
  web-arena:
    NotificationBell.test.tsx — links to /notifications
  web-coach:
    NotificationBell.test.tsx — renders with count
  All apps:
    npm test && npm run typecheck && npm run lint && npm run build
```

---

## Acceptance criteria

### Backend
- [ ] Flyway V13 creates `notifications` table with 4 indexes
- [ ] Assigning a membership creates a `MEMBERSHIP_ASSIGNED` notification for member
- [ ] Collecting a payment creates a `PAYMENT_COLLECTED` notification + sends email
- [ ] GX booking creates `GX_CLASS_BOOKED` notification for member
- [ ] Lead assigned creates `LEAD_ASSIGNED` for the assignee staff member only
- [ ] Scheduler: membership expiring in 7 days → `MEMBERSHIP_EXPIRING_SOON` created
- [ ] Scheduler: dedup — same notification not created twice in 24h (rule 3)
- [ ] Scheduler: cleanup deletes notifications older than 90 days
- [ ] `GET /notifications/unread-count` returns from Redis on second call (no DB query)
- [ ] `PATCH /notifications/{id}/read` returns 403 for wrong user (rule 4)
- [ ] `PATCH /notifications/read-all` sets Redis unread count to 0
- [ ] Notification creation failure never propagates to business operation (rule 2)
- [ ] Email failure after notification persisted: notification saved, emailSentAt null
- [ ] All 424+ existing tests still pass

### Frontend
- [ ] Bell badge visible with correct count in web-pulse topbar
- [ ] Badge hidden when unread count is 0
- [ ] Drawer opens on bell click, lists notifications in descending order
- [ ] Clicking a notification item marks it read, unread dot disappears
- [ ] "Mark all read" clears all unread dots and resets count to 0
- [ ] web-arena: bell taps to /notifications full-page list
- [ ] web-coach: bell opens drawer with PT/GX notifications
- [ ] i18n params correctly interpolated ("expires in 7 days", not "expires in {{daysRemaining}} days")
- [ ] Polling: unread count refreshes every 30 seconds automatically
- [ ] All three apps: typecheck, lint, test, build pass

---

## RBAC matrix

No new permissions. Notification endpoints require only a valid JWT with
the matching scope — no additional permission codes needed.

---

## Definition of done

- All acceptance criteria checked
- All 10 business rules covered by tests
- Event listener exception swallowing tested (rule 2)
- Redis unread count cache hit/miss tested (rule 5)
- Deduplication tested: same scheduler notification within 24h → skipped
- Email notification tested with mocked JavaMailSender
- All three apps have working notification bells (manual verify)
- All CI checks pass on PR
- PLAN.md deleted before merging
- PR title: `feat(notifications): add in-app notification system with email delivery`
- Target branch: `develop`

