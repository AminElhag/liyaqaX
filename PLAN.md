# PLAN.md — Scheduled Report Emails + PDF Exports (Plan 20)

## Status
Ready for implementation

## Branch
feat/scheduled-reports

## Goal
Let club owners schedule any saved report template to run automatically
(daily, weekly, or monthly) and deliver the result by email as a PDF
attachment. One-off PDF export of any report result is also added. The
email is sent to one or more configured recipients per schedule. The
scheduler runs server-side via Spring's `@Scheduled` + a `ReportSchedule`
entity. PDF generation uses iText (open source) on the backend — no
browser or headless Chrome needed.

## Context
- `ReportTemplate` and `ReportResult` entities exist (Plan 19).
- `ReportBuilderService.runReport()` already executes reports and caches
  results — the scheduler calls this directly.
- `ReportResultRepository` already stores the last result as JSON.
- No email infrastructure exists yet — this plan introduces the first
  email sending capability in the project (JavaMailSender + SMTP config).
- No PDF generation exists yet — this plan introduces iText 7 Community.
- `ddl-auto: create-drop` in dev — Flyway V12 for `report_schedules` table.
- Redis is running — schedule lock uses Redis `SETNX` to prevent duplicate
  runs when multiple backend instances are deployed.

---

## Scope — what this plan covers

### Backend
- [ ] `ReportSchedule.kt` entity + `ReportScheduleRepository.kt`
- [ ] `ReportSchedulerService.kt` — Spring `@Scheduled` cron, Redis lock,
  calls `ReportBuilderService.runReport()`, sends email
- [ ] `ReportPdfService.kt` — generates PDF from `ReportResult` using iText 7
- [ ] `ReportEmailService.kt` — composes and sends email with PDF attachment
  via JavaMailSender
- [ ] `ReportSchedulePulseController.kt` — CRUD for schedules per club
- [ ] `ReportExportPulseController.kt` — `GET /report-templates/{id}/export/pdf`
  (on-demand PDF of last result)
- [ ] DTOs for schedule endpoints
- [ ] `application.yml` additions: SMTP config, scheduler toggle
- [ ] Flyway V12: `report_schedules` table
- [ ] Unit tests: `ReportPdfServiceTest`, `ReportEmailServiceTest`,
  `ReportSchedulerServiceTest`
- [ ] Integration tests: `ReportSchedulePulseControllerTest`

### Frontend (web-pulse additions)
- [ ] Schedule management UI on `/reports/builder/$templateId`:
  "Schedule" tab alongside the existing Run tab
- [ ] `src/api/reportSchedules.ts` — API client
- [ ] `ScheduleForm.tsx` — frequency picker (daily/weekly/monthly),
  day/time selector, recipient email list
- [ ] "Export PDF" button on the template detail page (on-demand)
- [ ] Schedule status badge on template list (Active / Paused / Never run)

---

## Out of scope — do not implement in this plan
- In-app notification when a scheduled report runs (Plan 21)
- Slack / WhatsApp delivery of reports (no messaging gateway yet)
- Custom email templates / branding (plain HTML email only)
- Report scheduling for the four fixed reports (Plan 18 reports) —
  scheduler only works with custom `ReportTemplate` entities
- Report result history viewer (only last result stored)
- Retry logic for failed email sends (log + alert only for now)
- Unsubscribe / bounce handling (future plan)

---

## Decisions already made

- **JavaMailSender + SMTP**: Spring Boot's built-in mail support.
  SMTP credentials come from environment variables (never hardcoded).
  In dev: `spring.mail.host=localhost`, `spring.mail.port=1025`
  pointing to Mailpit (a local SMTP catcher — added to docker-compose).
  In prod: configured via `.env` → `MAIL_HOST`, `MAIL_PORT`,
  `MAIL_USERNAME`, `MAIL_PASSWORD`.

- **iText 7 Community (AGPL)**: open-source PDF generation. Added to
  `build.gradle.kts`. Simple tabular layout: report name, date range,
  run timestamp at top; data table below with column headers and rows.
  Club name and logo text in header. Page numbers in footer.
  Arabic text handled via iText's built-in Unicode support — RTL columns
  rendered correctly if column values are Arabic strings.

- **`ReportSchedule` per template**: one schedule per template per club.
  Attempting to create a second schedule for the same template returns 409.
  A schedule can be paused (isActive = false) without deleting it.

- **Cron frequencies**:
  - `daily` → runs at 07:00 Asia/Riyadh every day
  - `weekly` → runs at 07:00 Asia/Riyadh every Monday
  - `monthly` → runs at 07:00 Asia/Riyadh on the 1st of each month
  Fixed times — not configurable per schedule in this plan.
  Date range for each run:
  - daily → yesterday (dateFrom = dateTo = yesterday)
  - weekly → last 7 days
  - monthly → previous calendar month

- **Redis distributed lock**: before each scheduled run, acquire
  `SETNX report_schedule_lock:{scheduleId}` with 5-minute TTL. If lock
  already held → skip this run (another instance is handling it). Release
  lock after run completes or fails. Prevents duplicate emails in
  multi-instance deployments.

- **Recipients stored as JSON array on `ReportSchedule`**: up to 10 email
  addresses per schedule. Stored as `VARCHAR(2000)` JSON string.
  Validated as valid email format on create/update.

- **Email format**: subject = `"[Liyaqa] {reportName} — {dateRange}"`.
  Body = plain HTML with: club name, report name, date range, row count,
  "Please find the report attached." footer.
  Attachment = `{reportName}_{dateFrom}_{dateTo}.pdf`.

- **On-demand PDF**: `GET /report-templates/{id}/export/pdf` generates a
  PDF from the last stored `ReportResult`. Returns 404 if no result exists.
  Returns `application/pdf` with `Content-Disposition: attachment`.
  This is separate from the existing CSV export endpoint.

- **Audit logging**: `REPORT_SCHEDULE_CREATED`, `REPORT_SCHEDULE_UPDATED`,
  `REPORT_SCHEDULE_DELETED`, `REPORT_EMAIL_SENT` added to `AuditAction`.

- **Mailpit in docker-compose**: add `mailpit` service (port 1025 SMTP,
  port 8025 web UI) to `docker-compose.yml` for local dev email catching.
  All dev emails land in Mailpit's web inbox at `localhost:8025`.

---

## Entity design

### ReportSchedule

Fields beyond standard AuditEntity columns:

```
template_id       BIGINT NOT NULL UNIQUE   FK → report_templates(id)
                                           UNIQUE: one schedule per template
club_id           BIGINT NOT NULL          FK → clubs(id) (for tenant isolation)
frequency         VARCHAR(20) NOT NULL     'daily' | 'weekly' | 'monthly'
recipients_json   VARCHAR(2000) NOT NULL   JSON array of email strings, max 10
is_active         BOOLEAN NOT NULL DEFAULT true
last_run_at       TIMESTAMPTZ              nullable — set after each successful run
last_run_status   VARCHAR(20)              nullable — 'success' | 'failed'
last_error        TEXT                     nullable — error message if last_run_status='failed'
```

### Flyway V12

```sql
CREATE TABLE report_schedules (
    id                BIGSERIAL PRIMARY KEY,
    public_id         UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    template_id       BIGINT NOT NULL UNIQUE REFERENCES report_templates(id),
    club_id           BIGINT NOT NULL REFERENCES clubs(id),
    frequency         VARCHAR(20) NOT NULL,
    recipients_json   VARCHAR(2000) NOT NULL,
    is_active         BOOLEAN NOT NULL DEFAULT true,
    last_run_at       TIMESTAMPTZ,
    last_run_status   VARCHAR(20),
    last_error        TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ
);

CREATE INDEX idx_report_schedules_club_id      ON report_schedules(club_id);
CREATE INDEX idx_report_schedules_template_id  ON report_schedules(template_id);
CREATE INDEX idx_report_schedules_is_active    ON report_schedules(is_active)
    WHERE deleted_at IS NULL;
```

---

## API endpoints

### ReportSchedulePulseController — `/api/v1/report-templates/{templateId}/schedule`

```
GET    /api/v1/report-templates/{templateId}/schedule        get schedule (or 404)
POST   /api/v1/report-templates/{templateId}/schedule        create schedule
PATCH  /api/v1/report-templates/{templateId}/schedule        update frequency/recipients/isActive
DELETE /api/v1/report-templates/{templateId}/schedule        delete schedule
```

### ReportExportPulseController — additions to existing export

```
GET    /api/v1/report-templates/{id}/export/pdf    on-demand PDF of last result
```

(Existing CSV: `GET /api/v1/report-templates/{id}/export` — unchanged.)

Required permission: `report:custom:run` for all schedule endpoints.

---

## Request / Response shapes

### ReportScheduleResponse
```json
{
  "id": "uuid",
  "templateId": "uuid",
  "templateName": "string",
  "frequency": "daily | weekly | monthly",
  "recipients": ["owner@elixir.com"],
  "isActive": true,
  "lastRunAt": "ISO 8601 | null",
  "lastRunStatus": "success | failed | null",
  "lastError": "string | null",
  "nextRunAt": "ISO 8601"
}
```

`nextRunAt` is computed by the service based on `frequency` and current time.

### CreateReportScheduleRequest
```json
{
  "frequency": "daily | weekly | monthly (required)",
  "recipients": ["email1@example.com", "email2@example.com"],
  "isActive": true
}
```

### UpdateReportScheduleRequest
```json
{
  "frequency": "daily | weekly | monthly (optional)",
  "recipients": ["email@example.com"] "(optional)",
  "isActive": false "(optional — use to pause/resume)"
}
```

---

## Business rules — enforce in service layer

1. **One schedule per template**: `template_id` has a UNIQUE constraint.
   Return 409 "A schedule already exists for this template. Update or delete
   it instead."

2. **Max 10 recipients**: return 422 if `recipients` array has more than 10
   entries.

3. **Valid email format**: each recipient must match a basic email regex.
   Return 422 "Invalid email address: {value}" for the first invalid one.

4. **Template must belong to this club**: verify `template.clubId == JWT clubId`
   before creating a schedule. Return 403 if not.

5. **Paused schedules are skipped**: scheduler checks `is_active = true` before
   running. Paused schedules are not deleted — `isActive = false` is the pause
   mechanism.

6. **Redis distributed lock**: acquire `report_schedule_lock:{scheduleId}`
   before each run. If lock held → log INFO "Skipping {scheduleId} — already
   running" and return. Never skip silently.

7. **On run failure**: catch all exceptions. Set `lastRunStatus = "failed"`,
   store error message in `lastError` (truncated to 500 chars). Log ERROR.
   Do NOT throw — scheduler must continue to next schedule.

8. **Email send failure is non-fatal for result storage**: if PDF generation
   or email send fails AFTER the report has been computed and stored, log
   ERROR and update `lastRunStatus = "failed"` — but the `ReportResult` is
   still persisted. User can still fetch the result via the API.

9. **PDF max 1000 rows**: if `ReportResult.rowCount > 1000`, PDF shows first
   1000 rows with a footer note: "Showing first 1,000 of {total} rows.
   Download the full dataset via CSV export."

10. **`nextRunAt` computation**: always computed in Asia/Riyadh timezone,
    converted to UTC for storage and API response.

---

## Seed data updates

Add to `docker-compose.yml`:
```yaml
mailpit:
  image: axllent/mailpit:latest
  ports:
    - "1025:1025"   # SMTP
    - "8025:8025"   # Web UI
```

Add to `application-dev.yml`:
```yaml
spring:
  mail:
    host: localhost
    port: 1025
    username: ""
    password: ""
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false
```

Add to `.env.example`:
```
MAIL_HOST=
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=noreply@liyaqa.com
```

No new `DevDataLoader` changes — seed report schedules would require
valid recipient emails not guaranteed in dev.

---

## Files to generate

### Backend — new files
```
report/
  schedule/
    ReportSchedule.kt
    ReportScheduleRepository.kt
    ReportSchedulerService.kt      (@Scheduled cron, Redis lock, orchestrator)
    ReportSchedulePulseController.kt
    ReportPdfService.kt            (iText 7 PDF generation)
    ReportEmailService.kt          (JavaMailSender composition + send)
    dto/
      ReportScheduleResponse.kt
      CreateReportScheduleRequest.kt
      UpdateReportScheduleRequest.kt

resources/db/migration/V12__report_schedules.sql
```

### Backend — modified files
```
audit/AuditAction.kt              +4 new codes
config/SecurityConfig.kt          no change (schedule endpoints use existing auth)
build.gradle.kts                  add iText 7 Community dependency
application.yml                   add spring.mail config block
application-dev.yml               add dev SMTP pointing to Mailpit
.env.example                      add MAIL_* vars
docker-compose.yml                add mailpit service
```

### Frontend — web-pulse additions
```
src/api/reportSchedules.ts
src/routes/reports/builder/$templateId.tsx    (modify: add Schedule tab)
src/components/reportBuilder/
  ScheduleForm.tsx
  ScheduleBadge.tsx               (Active / Paused / Never run)
```

---

## Implementation order

```
Step 1 — ReportSchedule entity + Flyway V12
  report/schedule/ReportSchedule.kt — entity with all fields
  report/schedule/ReportScheduleRepository.kt:
    findByTemplateIdAndDeletedAtIsNull()
    findAllByIsActiveTrueAndDeletedAtIsNull()  ← used by scheduler
  resources/db/migration/V12__report_schedules.sql
  Verify: ./gradlew build -x test

Step 2 — ReportPdfService (iText 7)
  build.gradle.kts: add iText 7 Community dependency
    implementation("com.itextpdf:itext7-core:7.2.5")
  report/schedule/ReportPdfService.kt:
    generatePdf(reportName, clubName, dateFrom, dateTo, columns, rows): ByteArray
    Layout: header (club name, report name, date range, run timestamp),
      data table (columns as headers, rows as cells), footer (page X of Y)
    Max 1000 rows in PDF (rule 9) with footer note if truncated
    Arabic strings rendered via iText Unicode — no special RTL layout needed
      (cell content direction follows Unicode bidi algorithm)
  ReportPdfServiceTest.kt (unit):
    - generates non-empty PDF bytes for sample data
    - 1000-row cap: 1001 rows → PDF has 1000 rows + truncation note
    - empty result → PDF with "No data" row
  Verify: ./gradlew test -t ReportPdfServiceTest

Step 3 — ReportEmailService
  application.yml: add spring.mail config block (injected from env)
  application-dev.yml: dev SMTP → Mailpit localhost:1025
  docker-compose.yml: add mailpit service
  .env.example: add MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM
  report/schedule/ReportEmailService.kt:
    sendReportEmail(recipients, reportName, clubName, dateFrom, dateTo,
                    pdfBytes, rowCount):
      Subject: "[Liyaqa] {reportName} — {dateFrom} to {dateTo}"
      Body: plain HTML with club name, report name, row count, footer
      Attachment: {reportName}_{dateFrom}_{dateTo}.pdf (application/pdf)
      Sends via JavaMailSender — throws on failure (caller handles rule 8)
  ReportEmailServiceTest.kt (unit, MockitoExtension):
    - sends email to all recipients
    - attachment filename formatted correctly
    - subject line formatted correctly
  Verify: ./gradlew test -t ReportEmailServiceTest

Step 4 — ReportSchedulerService
  report/schedule/ReportSchedulerService.kt:
    @Scheduled(cron = "0 0 4 * * *", zone = "UTC")  ← 07:00 Riyadh = 04:00 UTC
    runDueSchedules():
      loadAll active, non-deleted schedules
      For each: check frequency vs current day/weekday
        → daily: always run
        → weekly: run only on Monday
        → monthly: run only on 1st of month
      Acquire Redis lock (rule 6): SETNX + expire 5 min
      Compute dateFrom/dateTo for this frequency
      Call ReportBuilderService.runReport(template, request, clubId)
      Call ReportPdfService.generatePdf(result)
      Call ReportEmailService.sendReportEmail(recipients, ...)
      Update schedule: lastRunAt, lastRunStatus (rule 7, rule 8)
      Release Redis lock
    nextRunAt(frequency, now): compute next run in Asia/Riyadh, return as UTC
  ReportSchedulerServiceTest.kt (unit):
    - daily schedule: runs every call
    - weekly schedule: runs on Monday, skipped on Tuesday
    - monthly schedule: runs on 1st, skipped on 15th
    - Redis lock held → skipped with INFO log (rule 6)
    - run failure → lastRunStatus=failed, lastError set, no throw (rule 7)
    - email failure after result stored → result persisted, status=failed (rule 8)
  Verify: ./gradlew test -t ReportSchedulerServiceTest

Step 5 — ReportSchedulePulseController + PDF export endpoint
  report/schedule/ReportSchedulePulseController.kt:
    GET/POST/PATCH/DELETE /report-templates/{templateId}/schedule
    All rules: 1 (one per template), 2 (max 10 recipients), 3 (valid email),
      4 (template belongs to club), 5 (isActive pause)
    Audit: REPORT_SCHEDULE_CREATED / UPDATED / DELETED
  ReportExportPulseController.kt (modify existing export controller):
    GET /report-templates/{id}/export/pdf:
      Load last ReportResult from DB (not Redis — persistence required)
      If no result → 404 "No result available. Run the report first."
      Generate PDF via ReportPdfService
      Return application/pdf with Content-Disposition: attachment
  audit/AuditAction.kt: +REPORT_SCHEDULE_CREATED, _UPDATED, _DELETED, _EMAIL_SENT
  DTOs: ReportScheduleResponse, CreateReportScheduleRequest, UpdateReportScheduleRequest
  Verify: ./gradlew build -x test

Step 6 — Backend tests
  ReportSchedulePulseControllerTest.kt (integration):
    - create schedule: happy path → 201
    - create second schedule for same template → 409 (rule 1)
    - create with 11 recipients → 422 (rule 2)
    - create with invalid email → 422 (rule 3)
    - create for another club's template → 403 (rule 4)
    - PATCH isActive=false → paused status in response
    - DELETE → soft deleted, GET returns 404
    - GET /export/pdf with result → returns PDF bytes (content-type check)
    - GET /export/pdf with no result → 404
  Verify: ./gradlew test --no-daemon

Step 7 — Backend final checks
  ./gradlew ktlintFormat --no-daemon
  ./gradlew ktlintCheck --no-daemon
  ./gradlew build --no-daemon
  Manual dev verify:
    docker compose up -d (includes mailpit)
    ./gradlew bootRun
    POST /api/v1/auth/login as owner
    POST /api/v1/report-templates/{id}/run  ← generate a result first
    POST /api/v1/report-templates/{id}/schedule {"frequency":"daily","recipients":["test@test.com"]}
    GET /api/v1/report-templates/{id}/export/pdf → download PDF
    Check Mailpit at localhost:8025 for the email (trigger via scheduler
    debug endpoint or wait for 04:00 UTC cron)

Step 8 — Frontend: Schedule tab on template detail
  src/api/reportSchedules.ts:
    getSchedule(templateId), createSchedule(), updateSchedule(), deleteSchedule()
  src/routes/reports/builder/$templateId.tsx (modify):
    Add "Schedule" tab next to "Run" tab
    If no schedule: show ScheduleForm to create one
    If schedule exists: show current config + ScheduleBadge + edit form
    "Pause" / "Resume" toggle button
    "Remove Schedule" button with confirm modal
  src/components/reportBuilder/ScheduleForm.tsx:
    Frequency radio group (Daily / Weekly / Monthly)
    Next scheduled run preview (computed client-side from frequency)
    Recipients: tag input (add/remove email addresses, max 10)
    "Save Schedule" → POST or PATCH
  src/components/reportBuilder/ScheduleBadge.tsx:
    Active (green) / Paused (amber) / Never run (grey) + last run timestamp
  Verify: npm run dev → open a template → Schedule tab → create daily schedule
    → badge shows "Active, never run yet"

Step 9 — "Export PDF" button
  src/routes/reports/builder/$templateId.tsx (modify):
    Add "Export PDF" button next to existing "Export CSV" in results section
    On click → GET /report-templates/{id}/export/pdf → trigger browser download
    Disabled if no result exists yet (tooltip: "Run the report first")
  Verify: npm run dev → run a report → Export PDF → PDF downloads in browser

Step 10 — Schedule status on template list
  src/routes/reports/builder/index.tsx (modify):
    Add "Schedule" column to template list table
    Fetch schedule status per template (or include in template list response)
    Show ScheduleBadge inline
  Verify: npm run dev → /reports/builder → schedule badges visible per row

Step 11 — Frontend tests + final checks
  ScheduleForm.test.tsx:
    - renders frequency options
    - max 10 recipients enforced client-side (11th input disabled)
    - invalid email shows inline error
    - "Next run" preview updates when frequency changes
  ScheduleBadge.test.tsx:
    - Active / Paused / Never run states render correctly
  Verify: npm test && npm run typecheck && npm run lint && npm run build
```

---

## Acceptance criteria

### Backend
- [ ] Flyway V12 creates `report_schedules` table with UNIQUE on `template_id`
- [ ] `POST /schedule` creates schedule, second call returns 409
- [ ] 11 recipients returns 422, invalid email returns 422
- [ ] `PATCH isActive=false` pauses schedule — scheduler skips it
- [ ] Scheduler runs daily schedules on every trigger
- [ ] Scheduler runs weekly schedules on Monday only
- [ ] Scheduler runs monthly schedules on 1st of month only
- [ ] Redis lock prevents duplicate runs (mock: lock held → INFO log + skip)
- [ ] Run failure updates `lastRunStatus=failed` and `lastError`, does not throw
- [ ] Email failure after result stored: result persisted, status=failed
- [ ] `GET /export/pdf` returns `application/pdf` with attachment header
- [ ] `GET /export/pdf` with no result returns 404
- [ ] PDF contains report name, date range, column headers, data rows
- [ ] PDF shows max 1000 rows with truncation note for larger results
- [ ] All 388+ existing tests still pass

### Frontend
- [ ] Schedule tab visible on template detail page
- [ ] Creating a schedule shows "Active, never run yet" badge
- [ ] Pausing a schedule shows amber "Paused" badge
- [ ] Max 10 recipients enforced in tag input
- [ ] "Next run" preview shows correct date for each frequency
- [ ] "Export PDF" button triggers file download
- [ ] "Export PDF" disabled with tooltip when no result exists
- [ ] Schedule badge visible in template list table
- [ ] `npm run typecheck`, `lint`, `test`, `build` all pass

---

## RBAC matrix

No new permissions. `report:custom:run` (added in Plan 19) covers all
schedule management and PDF export endpoints.

---

## Definition of done

- All acceptance criteria checked
- Mailpit running in docker-compose — dev emails catchable at localhost:8025
- All 10 business rules covered by tests
- Redis lock tested: mock held lock → skip + INFO log
- PDF generation tested with real iText output (non-empty bytes)
- Email sending tested with MockMvc / mocked JavaMailSender
- Scheduler frequency logic tested for all three frequencies + edge cases
- All CI checks pass on PR
- PLAN.md deleted before merging
- PR title: `feat(reports): add scheduled report emails and PDF export`
- Target branch: `develop`

