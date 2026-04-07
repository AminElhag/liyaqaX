# Plan 28 — Bulk Member Import (CSV)

## Status
Ready for implementation

## Branch
`feature/plan-28-csv-import`

## Goal
Allow Integration Specialists to upload a CSV file of existing members (migrating from a legacy gym system) via web-nexus. The backend validates all rows, then processes asynchronously. The entire import is fully reversible — a rollback endpoint soft-deletes all members created by a given job. No memberships are created; plan assignment happens manually after import.

## Context
- `Member` entity already exists with all required fields
- Notification system (Plan 21) already supports new types — wire `MEMBER_IMPORT_COMPLETED` in
- `JavaMailSender` already configured (Plan 20)
- RBAC already supports custom permission codes — add `member:import`
- Soft delete pattern already used platform-wide (`deleted_at` nullable timestamp)
- Next Flyway migration: **V16**

---

## Scope — what this plan covers

- [ ] Flyway V16 — `member_import_jobs` table + `member_import_job_id` column on `members`
- [ ] `MemberImportJob` entity
- [ ] `MemberImportService` — validation pass + job creation
- [ ] `MemberImportProcessor` — `@Async` bean, full transactional import pass
- [ ] `MemberImportRollbackService` — soft-deletes all members created by a job
- [ ] 4 endpoints on `MemberImportNexusController`
- [ ] New notification type: `MEMBER_IMPORT_COMPLETED`
- [ ] New audit actions: `MEMBER_IMPORT_STARTED`, `MEMBER_IMPORT_COMPLETED`, `MEMBER_IMPORT_CANCELLED`, `MEMBER_IMPORT_ROLLED_BACK`
- [ ] New permission: `member:import` seeded to Integration Specialist
- [ ] web-nexus: upload modal on club detail page, job status polling, result summary, rollback button
- [ ] Tests — unit + integration + frontend

## Out of scope — do not implement in this plan

- Membership creation during import (members only)
- Email attachment of the error CSV (notification + plain-text email only)
- Editing an existing member via import (no upsert — duplicate phone = skip)
- Re-running a rolled-back job

---

## Decisions already made

- **Async with 60-second queued window** — job accepted with `202 Accepted`, sits in `queued` state for 60 seconds before `MemberImportProcessor` picks it up; cancel is allowed during this window
- **Full atomic transaction** — the entire import pass runs inside a single `@Transactional` method; if it fails mid-way, nothing is written
- **Rollback via soft-delete** — `member_import_job_id` FK on `members` links every imported member to its job; rollback sets `deleted_at` on all of them
- **No row limit** — file size capped at 10 MB (Spring multipart config); row count unbounded
- **Required columns**: `name_ar`, `phone`, `gender` — Optional: `name_en`, `email`, `date_of_birth`
- **Duplicate phone** — skip row, include in result summary; does NOT fail the import
- **Completion signal** — both in-app notification (`MEMBER_IMPORT_COMPLETED`) and email via `JavaMailSender`
- **Roles** — Integration Specialist (web-nexus) only via `member:import` permission

---

## Entity design

### MemberImportJob

```kotlin
@Entity
@Table(name = "member_import_jobs")
class MemberImportJob(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    val publicId: UUID = UUID.randomUUID(),

    @Column(name = "club_id", nullable = false)
    val clubId: Long,

    @Column(name = "created_by_user_id", nullable = false)
    val createdByUserId: Long,

    @Column(name = "file_name", nullable = false)
    val fileName: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: MemberImportJobStatus = MemberImportJobStatus.QUEUED,

    @Column(name = "total_rows")
    var totalRows: Int? = null,

    @Column(name = "imported_count")
    var importedCount: Int? = null,

    @Column(name = "skipped_count")
    var skippedCount: Int? = null,

    @Column(name = "error_count")
    var errorCount: Int? = null,

    // Newline-separated list of "row N: reason" strings for rows that failed validation
    @Column(name = "error_detail", columnDefinition = "TEXT")
    var errorDetail: String? = null,

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)

enum class MemberImportJobStatus {
    QUEUED,       // waiting for 60-second delay before processing
    PROCESSING,   // async processor is running
    COMPLETED,    // all rows processed (some may be skipped/errored)
    CANCELLED,    // cancelled during QUEUED window
    ROLLED_BACK   // completed job was rolled back — members soft-deleted
}
```

### Member entity change

Add one nullable FK column:

```kotlin
@Column(name = "member_import_job_id", nullable = true)
var memberImportJobId: Long? = null
```

Set to `job.id` for every member created by an import. Null for all manually created members.

---

## Flyway V16

```sql
-- V16__member_import.sql

CREATE TABLE member_import_jobs (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    club_id             BIGINT NOT NULL REFERENCES clubs(id),
    created_by_user_id  BIGINT NOT NULL REFERENCES users(id),
    file_name           VARCHAR(255) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    total_rows          INTEGER,
    imported_count      INTEGER,
    skipped_count       INTEGER,
    error_count         INTEGER,
    error_detail        TEXT,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE members
    ADD COLUMN member_import_job_id BIGINT REFERENCES member_import_jobs(id);

CREATE INDEX idx_member_import_jobs_club_id ON member_import_jobs(club_id);
CREATE INDEX idx_members_import_job_id ON members(member_import_job_id);
```

---

## CSV format

### Required columns (header row must match exactly — case-insensitive)

| Column | Type | Validation |
|--------|------|-----------|
| `name_ar` | String | Required, 2–100 chars |
| `phone` | String | Required, Saudi format (+966XXXXXXXXX or 05XXXXXXXX), normalised to +966 |
| `gender` | String | Required, must be `male` or `female` (case-insensitive) |

### Optional columns

| Column | Type | Validation |
|--------|------|-----------|
| `name_en` | String | 2–100 chars if provided |
| `email` | String | Valid email format if provided |
| `date_of_birth` | String | ISO 8601 date (YYYY-MM-DD) if provided, must be in the past |

### Row-level errors (skip row, add to error_detail)

- Missing required field
- Invalid phone format
- Invalid gender value
- `date_of_birth` is in the future
- Phone already exists for a member in this club (duplicate — skip, logged as skipped not error)

### File-level errors (reject entire file, return 422 before job creation)

- Not a valid CSV (parse failure)
- Missing required header columns
- File is empty (0 data rows)

---

## API endpoints

| Method | Path | Permission | Description |
|--------|------|------------|-------------|
| `POST` | `/api/v1/nexus/clubs/{clubPublicId}/members/import` | `member:import` | Upload CSV, create job, return 202 + jobId |
| `GET` | `/api/v1/nexus/member-import-jobs/{jobPublicId}` | `member:import` | Job status + result counts |
| `DELETE` | `/api/v1/nexus/member-import-jobs/{jobPublicId}` | `member:import` | Cancel job if status = QUEUED |
| `POST` | `/api/v1/nexus/member-import-jobs/{jobPublicId}/rollback` | `member:import` | Rollback completed job — soft-delete all imported members |

---

## Request / Response shapes

### POST /import → 202 Accepted

```json
{
  "jobId": "uuid",
  "status": "QUEUED",
  "fileName": "elixir-members-2024.csv",
  "message": "Import queued. Processing will begin in ~60 seconds."
}
```

### GET /member-import-jobs/{id}

```json
{
  "jobId": "uuid",
  "status": "COMPLETED",
  "fileName": "elixir-members-2024.csv",
  "totalRows": 312,
  "importedCount": 298,
  "skippedCount": 11,
  "errorCount": 3,
  "errorDetail": "Row 14: phone +966501234567 already exists\nRow 87: invalid gender value 'M'\nRow 203: date_of_birth 2026-01-01 is in the future",
  "startedAt": "2026-04-07T10:01:05Z",
  "completedAt": "2026-04-07T10:01:18Z",
  "createdAt": "2026-04-07T10:00:04Z"
}
```

### POST /rollback → 200 OK

```json
{
  "message": "Rollback complete. 298 members soft-deleted."
}
```

---

## Business rules — enforce in service layer

1. File-level validation runs synchronously before the job is created. If it fails, return `422` with an array of error messages — no job record is created.
2. A job can only be cancelled when `status = QUEUED`. Any other status returns `409 Conflict`.
3. A job can only be rolled back when `status = COMPLETED`. Any other status returns `409 Conflict`.
4. The import processor runs the entire import pass inside a single `@Transactional` method. If any unexpected exception occurs, the transaction rolls back and the job is marked `status = COMPLETED` with `errorDetail` noting the failure.
5. Phone numbers are normalised before duplicate checking: strip spaces/dashes, convert `05XXXXXXXX` → `+966 5XXXXXXXX`. The normalised form is what gets stored on the `Member`.
6. Duplicate phone check is scoped to the club — the same phone may exist in a different club and is NOT a duplicate.
7. `memberImportJobId` is set on every `Member` created by the processor. Members created manually always have `memberImportJobId = null`.
8. Rollback sets `deleted_at = Instant.now()` on all members where `member_import_job_id = job.id` AND `deleted_at IS NULL`. It then sets `job.status = ROLLED_BACK`.
9. A rolled-back job cannot be re-run or rolled back again.
10. The 60-second delay is implemented via `Thread.sleep(60_000)` at the start of the async processor method, before `status` is changed to `PROCESSING`. The cancel check reads the latest job status from the DB after the sleep — if it is `CANCELLED`, the processor exits without doing anything.

---

## Seed data updates

No changes to dev seed data needed. The import feature is triggered manually via the UI.

---

## Notification + Email on completion

**Notification** (uses existing `NotificationTriggerService`):
- Type: `MEMBER_IMPORT_COMPLETED`
- Target: the user who created the job (`createdByUserId`)
- Body (en): `"Import of {fileName} complete: {importedCount} imported, {skippedCount} skipped, {errorCount} errors."`
- Body (ar): `"اكتمل استيراد {fileName}: {importedCount} مستورد، {skippedCount} تم تخطيه، {errorCount} أخطاء."`

**Email** (uses existing `JavaMailSender`):
- Subject (en): `Liyaqa — Member Import Complete: {fileName}`
- Body: plain-text summary with same counts
- No attachment

---

## Frontend additions (web-nexus)

**Club detail page** (`/nexus/clubs/{id}`):
- "Import Members" button (visible only to users with `member:import` permission)
- Clicking opens an upload modal

**Upload modal:**
- File picker (accepts `.csv` only)
- "Download sample CSV" link (static file showing the 6 columns with one example row)
- Upload button → calls `POST /import` → shows "Import queued" confirmation with job ID
- Closes modal, opens job status panel

**Job status panel** (appears below club detail, or as a drawer):
- Polls `GET /member-import-jobs/{id}` every 3 seconds while `status = QUEUED` or `PROCESSING`
- Shows: status badge, progress message, counts when completed
- **Cancel button** — visible when `status = QUEUED`, calls `DELETE /member-import-jobs/{id}`
- **Rollback button** — visible when `status = COMPLETED`, requires confirmation dialog ("This will soft-delete all {importedCount} members created by this import. Are you sure?"), calls `POST /rollback`
- **Error detail** — if `errorCount > 0`, shows error lines in a scrollable code block
- Job history table showing last 10 import jobs for the club (status, file name, counts, date)

**New i18n strings** (`ar.json` + `en.json`):
```
import.button
import.modal.title
import.modal.download_sample
import.modal.upload
import.status.queued
import.status.processing
import.status.completed
import.status.cancelled
import.status.rolled_back
import.result.imported
import.result.skipped
import.result.errors
import.cancel.button
import.cancel.confirm
import.rollback.button
import.rollback.confirm
import.rollback.success
import.error.file_level
```

---

## Files to generate

### New files

**Backend:**
- `backend/src/main/kotlin/com/liyaqa/member/entity/MemberImportJob.kt`
- `backend/src/main/kotlin/com/liyaqa/member/entity/MemberImportJobStatus.kt`
- `backend/src/main/kotlin/com/liyaqa/member/repository/MemberImportJobRepository.kt`
- `backend/src/main/kotlin/com/liyaqa/member/service/MemberImportService.kt`
- `backend/src/main/kotlin/com/liyaqa/member/service/MemberImportProcessor.kt`
- `backend/src/main/kotlin/com/liyaqa/member/service/MemberImportRollbackService.kt`
- `backend/src/main/kotlin/com/liyaqa/member/dto/MemberImportJobResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/member/dto/MemberImportAcceptedResponse.kt`
- `backend/src/main/kotlin/com/liyaqa/member/controller/MemberImportNexusController.kt`
- `backend/src/main/resources/db/migration/V16__member_import.sql`
- `backend/src/test/kotlin/com/liyaqa/member/service/MemberImportServiceTest.kt`
- `backend/src/test/kotlin/com/liyaqa/member/service/MemberImportProcessorTest.kt`
- `backend/src/test/kotlin/com/liyaqa/member/controller/MemberImportControllerIntegrationTest.kt`

**Frontend:**
- `apps/web-nexus/src/components/members/ImportMembersModal.tsx`
- `apps/web-nexus/src/components/members/ImportJobStatusPanel.tsx`
- `apps/web-nexus/src/api/memberImport.ts`
- `apps/web-nexus/src/routes/clubs/$clubId/index.tsx` (modify — add Import button)
- `apps/web-nexus/src/tests/member-import.test.tsx`

### Files to modify

- `backend/src/main/kotlin/com/liyaqa/member/entity/Member.kt` — add `memberImportJobId` field
- `backend/src/main/kotlin/com/liyaqa/notification/model/NotificationType.kt` — add `MEMBER_IMPORT_COMPLETED`
- `backend/src/main/kotlin/com/liyaqa/audit/model/AuditAction.kt` — add 4 new actions
- `backend/src/main/kotlin/com/liyaqa/permission/PermissionConstants.kt` — add `member:import`
- `backend/src/main/resources/data-dev.sql` (or DevDataLoader) — seed `member:import` to Integration Specialist role
- `apps/web-nexus/src/locales/ar.json`
- `apps/web-nexus/src/locales/en.json`

---

## Implementation order

### Step 1 — Flyway V16 + entities
- Write `V16__member_import.sql`
- Write `MemberImportJob.kt` + `MemberImportJobStatus.kt`
- Add `memberImportJobId: Long?` field to `Member.kt`
- Write `MemberImportJobRepository.kt` with native queries (see Step 4)
- Verify: `./gradlew flywayMigrate` succeeds

### Step 2 — Permission + audit actions + notification type
- Add `MEMBER_IMPORT = "member:import"` to `PermissionConstants.kt`
- Add `MEMBER_IMPORT_STARTED`, `MEMBER_IMPORT_COMPLETED`, `MEMBER_IMPORT_CANCELLED`, `MEMBER_IMPORT_ROLLED_BACK` to `AuditAction.kt`
- Add `MEMBER_IMPORT_COMPLETED` to `NotificationType.kt`
- Seed `member:import` to Integration Specialist role in `DevDataLoader` or SQL
- Verify: `./gradlew compileKotlin`

### Step 3 — MemberImportService (validation pass + job creation)
- Parse CSV using Apache Commons CSV (already in classpath — if not, add to `build.gradle.kts`)
- File-level validation: required headers present, file not empty, parseable
- Row-level validation: collect all errors with row numbers
- If file-level errors → throw `ArenaException` with 422 and error list (no job created)
- If passes → persist `MemberImportJob` with `status = QUEUED`
- Log `MEMBER_IMPORT_STARTED` audit action
- Return the saved job
- Verify: unit tests in `MemberImportServiceTest`

### Step 4 — Repository queries
Add to `MemberImportJobRepository`:

```kotlin
@Query(
    value = "SELECT * FROM member_import_jobs WHERE public_id = :publicId",
    nativeQuery = true
)
fun findByPublicId(publicId: UUID): MemberImportJob?

@Query(
    value = """
        SELECT * FROM member_import_jobs
        WHERE club_id = :clubId
        ORDER BY created_at DESC
        LIMIT 10
    """,
    nativeQuery = true
)
fun findTop10ByClubIdOrderByCreatedAtDesc(clubId: Long): List<MemberImportJob>
```

Add to `MemberRepository`:

```kotlin
@Query(
    value = """
        SELECT COUNT(*) FROM members
        WHERE phone = :phone
          AND club_id = :clubId
          AND deleted_at IS NULL
    """,
    nativeQuery = true
)
fun countByPhoneAndClubId(phone: String, clubId: Long): Long

@Modifying
@Query(
    value = """
        UPDATE members
        SET deleted_at = NOW()
        WHERE member_import_job_id = :jobId
          AND deleted_at IS NULL
    """,
    nativeQuery = true
)
fun softDeleteByImportJobId(jobId: Long): Int
```

### Step 5 — MemberImportProcessor (@Async)
- Annotate class with `@Component`, method with `@Async`
- On pickup: `Thread.sleep(60_000)` → re-read job from DB → if `status = CANCELLED` exit immediately
- Set `job.status = PROCESSING`, `job.startedAt = Instant.now()`
- Run import pass in a single `@Transactional` method:
  - Re-parse the CSV bytes stored in memory (passed as a `ByteArray` parameter)
  - For each row: validate → normalise phone → check duplicate → create `Member` with `memberImportJobId = job.id`
  - Track `importedCount`, `skippedCount`, `errorCount`, `errorDetail`
- After transaction commits: set `job.status = COMPLETED`, `job.completedAt = Instant.now()`
- Log `MEMBER_IMPORT_COMPLETED` audit action
- Call `notificationService.createNotification(...)` for `MEMBER_IMPORT_COMPLETED`
- Call `JavaMailSender` to send plain-text email to the job creator
- Verify: unit tests in `MemberImportProcessorTest`

### Step 6 — MemberImportRollbackService
```kotlin
@Service
@Transactional
class MemberImportRollbackService(
    private val jobRepository: MemberImportJobRepository,
    private val memberRepository: MemberRepository,
    private val auditService: AuditService
) {
    fun rollback(jobPublicId: UUID): Int {
        val job = jobRepository.findByPublicId(jobPublicId)
            ?: throw ArenaException("Import job not found", HttpStatus.NOT_FOUND)

        if (job.status != MemberImportJobStatus.COMPLETED) {
            throw ArenaException(
                "Only completed jobs can be rolled back (current: ${job.status})",
                HttpStatus.CONFLICT
            )
        }

        val deletedCount = memberRepository.softDeleteByImportJobId(job.id)
        job.status = MemberImportJobStatus.ROLLED_BACK

        auditService.log(
            action = AuditAction.MEMBER_IMPORT_ROLLED_BACK,
            entityType = "MemberImportJob",
            entityId = job.publicId.toString(),
            changes = mapOf("deletedCount" to deletedCount.toString())
        )

        return deletedCount
    }
}
```

### Step 7 — MemberImportNexusController
- 4 endpoints as defined in the API table above
- `@PreAuthorize("hasPermission(null, 'member:import')")` on all four
- `@Operation` Swagger annotations on all four
- `POST /import` — `@RequestParam file: MultipartFile` — calls `MemberImportService`, returns `202`
- `GET /{jobPublicId}` — returns `MemberImportJobResponse`
- `DELETE /{jobPublicId}` — cancel; checks `status = QUEUED`, sets `CANCELLED`, logs `MEMBER_IMPORT_CANCELLED`
- `POST /{jobPublicId}/rollback` — calls `MemberImportRollbackService`
- Verify: `./gradlew compileKotlin`

### Step 8 — Frontend: web-nexus
- `ImportMembersModal.tsx` — file picker, sample CSV link, upload handler
- `ImportJobStatusPanel.tsx` — status badge, counts, polling (3s while active), cancel button, rollback button with confirmation dialog, error detail block, last-10 jobs table
- `memberImport.ts` — 4 API functions (upload, getJob, cancelJob, rollbackJob) using TanStack Query
- Modify club detail page — add "Import Members" button gated on `member:import` permission
- Add all i18n strings (Arabic + English)
- Verify: `npm run typecheck && npm test`

### Step 9 — Tests

**Unit: `MemberImportServiceTest`**
- `validates CSV successfully when all required headers are present`
- `throws 422 when required headers are missing`
- `throws 422 when file is empty`
- `throws 422 when file is not parseable CSV`
- `creates job with QUEUED status on valid file`
- `logs MEMBER_IMPORT_STARTED audit action`

**Unit: `MemberImportProcessorTest`**
- `exits without processing when job is CANCELLED during 60-second window`
- `imports all valid rows and sets COMPLETED status`
- `skips duplicate phone and increments skippedCount`
- `records row error and increments errorCount for invalid gender`
- `normalises phone from 05XXXXXXXX to +966 format`
- `sends notification on completion`
- `sends email on completion`
- `rolls back entire transaction when unexpected exception occurs`

**Integration: `MemberImportControllerIntegrationTest`**
- `POST /import returns 202 and creates job`
- `POST /import returns 422 when headers missing`
- `POST /import returns 403 without member:import permission`
- `GET /job returns job status with counts`
- `DELETE /job cancels QUEUED job`
- `DELETE /job returns 409 when job is PROCESSING`
- `POST /rollback soft-deletes all imported members`
- `POST /rollback returns 409 when job is not COMPLETED`
- `rolled-back members do not appear in GET /members for the club`

**Frontend: `member-import.test.tsx`**
- renders Import Members button for users with member:import permission
- does not render Import Members button without permission
- upload modal shows file picker and sample CSV link
- job status panel shows cancel button when status is QUEUED
- job status panel shows rollback button when status is COMPLETED
- rollback confirmation dialog appears before calling rollback endpoint

---

## RBAC matrix rows added by this plan

| Permission | Integration Specialist | Super Admin | Club Owner |
|------------|----------------------|-------------|------------|
| `member:import` | ✅ | — | — |

---

## Definition of Done

- [ ] Flyway V16 runs cleanly: `member_import_jobs` table created, `member_import_job_id` column on `members`
- [ ] `MemberImportJob` entity + `MemberImportJobStatus` enum compile
- [ ] `memberImportJobId` field added to `Member` entity
- [ ] `member:import` permission constant defined and seeded to Integration Specialist
- [ ] 4 audit actions and `MEMBER_IMPORT_COMPLETED` notification type added
- [ ] CSV validation: file-level errors return 422 before job creation; row-level errors are collected and stored
- [ ] Phone normalisation: `05XXXXXXXX` → `+966 5XXXXXXXX`
- [ ] 60-second queued window: cancel during window leaves DB clean
- [ ] Full atomic import transaction: exception mid-import = zero members written
- [ ] Rollback: `POST /rollback` soft-deletes all members with matching `member_import_job_id`
- [ ] Rollback blocked on non-COMPLETED jobs with 409 response
- [ ] Cancel blocked on non-QUEUED jobs with 409 response
- [ ] Completion fires both in-app notification and email to job creator
- [ ] All 4 endpoints have `@Operation` and `@PreAuthorize`
- [ ] All repository queries use `nativeQuery = true`
- [ ] web-nexus: Import Members button visible only with `member:import` permission
- [ ] web-nexus: job status panel polls correctly and shows cancel / rollback buttons at the right states
- [ ] web-nexus: rollback requires confirmation dialog before calling endpoint
- [ ] All i18n strings added in Arabic and English
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] `./gradlew build` — BUILD SUCCESSFUL, no warnings
- [ ] `npm run typecheck` — no errors
- [ ] `PROJECT-STATE.md` updated: Plan 28 complete, test counts, V16 noted
- [ ] `PLAN-28-csv-import.md` deleted before merging

When all items are checked, confirm: **"Plan 28 — Bulk Member Import complete. X backend tests, Y frontend tests."**
