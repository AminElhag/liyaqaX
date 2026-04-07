
# PLAN.md — Custom Report Builder (Plan 19)

## Status
Ready for implementation

## Branch
feat/custom-report-builder

## Goal
Let club owners and managers build and save their own reports by selecting
metrics, dimensions, date ranges, and filters from the existing data. A saved
report template is stored per club, can be run on demand, and its last result
is cached for 10 minutes. The builder UI lives in web-pulse under /reports/builder.
The four fixed reports from Plan 18 remain unchanged — the custom builder is
additive only.

## Context
- Plan 18 built four fixed reports: revenue, retention, lead funnel, cash drawer.
  All four are read-only aggregations using `nativeQuery = true` services.
- `ReportPulseController`, four report services, and the reports UI (KpiCard,
  ReportDateRangePicker, ReportTable, CsvExportButton) all exist and can be
  reused or extended.
- `ddl-auto: create-drop` in dev — Flyway V11 needed for two new tables:
  `report_templates` and `report_results`.
- Redis 7 is running — used here for 10-minute result caching.
- Permission `report:revenue:view`, `report:retention:view`,
  `report:leads:view`, `report:cash-drawer:view` all exist. Custom reports
  add a new permission: `report:custom:run`.

---

## Scope — what this plan covers

### Backend
- [ ] `ReportTemplate.kt` entity + `ReportTemplateRepository.kt`
- [ ] `ReportResult.kt` entity + `ReportResultRepository.kt` (last run snapshot)
- [ ] `ReportBuilderService.kt` — build + execute a dynamic report query,
  cache result in Redis for 10 minutes
- [ ] `ReportTemplateService.kt` — CRUD for templates (per club)
- [ ] `ReportBuilderPulseController.kt` — template CRUD + run endpoints
- [ ] `MetaReportPulseController.kt` — GET available metrics and dimensions
- [ ] DTOs for all builder endpoints
- [ ] Flyway V11: `report_templates` + `report_results` tables
- [ ] Unit tests: `ReportBuilderServiceTest`, `ReportTemplateServiceTest`
- [ ] Integration tests: `ReportBuilderPulseControllerTest`

### Frontend (web-pulse additions)
- [ ] `/reports/builder` — template list + "New Report" button
- [ ] `/reports/builder/new` — report builder UI (metric + dimension +
  date range + filter selectors)
- [ ] `/reports/builder/$templateId` — saved template viewer + run button +
  last result display
- [ ] `src/api/reportBuilder.ts` — API client module
- [ ] Components: `MetricSelector`, `DimensionSelector`, `FilterBuilder`,
  `ReportPreviewTable`, `SaveTemplateModal`
- [ ] Reuse existing: `KpiCard`, `ReportDateRangePicker`, `ReportTable`,
  `CsvExportButton`

---

## Out of scope — do not implement in this plan
- Scheduled report emails / PDF exports — Plan 20
- Cross-org platform reports in web-nexus — separate plan
- Chart type selection in builder (builder output is always tabular)
- Real-time / live data (WebSocket) — separate plan
- Sharing reports between clubs — separate plan
- Excel export (CSV only — same as fixed reports)

---

## Decisions already made

- **Available metrics are fixed, not freeform**: the builder does not allow
  arbitrary SQL. It exposes a curated catalogue of pre-approved metrics
  (e.g. "Revenue", "New Members", "Active Memberships", "Bookings"). Each
  metric maps to a named aggregate SQL fragment in the backend. Users pick
  from a list — they cannot type raw SQL.

- **Available dimensions are fixed**: `day`, `week`, `month`, `staff_member`,
  `branch`, `membership_plan`, `class_type`, `lead_source`. Each maps to a
  GROUP BY fragment. Backend validates the combination is legal before executing.

- **Available filters are fixed**: `branch`, `membership_plan`, `date_range`,
  `staff_member`, `class_type`, `lead_source`. Each filter maps to a WHERE
  clause fragment.

- **Report execution builds a safe parameterized native query**: the backend
  assembles the query from pre-approved metric/dimension/filter fragments
  using string concatenation of trusted enum values — not user input. All
  user-supplied filter values (IDs, dates) are passed as JDBC parameters.
  Never interpolated into SQL string.

- **Result stored in `report_results` table AND Redis**: on `POST
  /report-templates/{id}/run`, result rows are serialized as JSON and stored
  in `report_results.resultJson` (last-run snapshot). Simultaneously cached
  in Redis at `report_result:{templateId}` with 10-minute TTL. On
  `GET /report-templates/{id}/result`, check Redis first — cache hit returns
  immediately, cache miss loads from `report_results` table.

- **Max 10 metric columns per report**: validation returns 422 if more than
  10 metrics selected. Prevents runaway query complexity.

- **Max 50,000 rows per report result**: query adds `LIMIT 50000`. If result
  is truncated, response includes `"truncated": true` and `"rowCount": 50000`.

- **Template is per-club**: `report_templates.club_id` is mandatory. A club
  owner can only see and run their own templates. Tenant isolation enforced
  via JWT `clubId`.

- **`report:custom:run` permission**: Owner and Branch Manager can run and
  manage templates. Sales Agent can run templates that include lead metrics
  (enforced via template `metricScope` — see entity design).

- **Redis cache key includes run parameters**: `report_result:{templateId}:{paramHash}`
  where `paramHash` is a SHA-256 of the run-time overrides (date range,
  filter values). Same template with different date ranges gets separate
  cache entries.

- **Flyway V11**: next available version after V10 (audit_logs).

---

## Entity design

### ReportTemplate

Fields beyond standard AuditEntity columns:

```
club_id           BIGINT NOT NULL   FK → clubs(id)
name              VARCHAR(200) NOT NULL
description       VARCHAR(500)      nullable
metrics           TEXT NOT NULL     JSON array of metric codes e.g. ["revenue","new_members"]
dimensions        TEXT NOT NULL     JSON array of dimension codes e.g. ["month","branch"]
filters           TEXT              nullable, JSON object of filter config
                                    e.g. {"branch_id": null, "plan_id": null}
metric_scope      VARCHAR(50)       nullable — "revenue"|"leads"|"members"|"gx"|"pt"
                                    used to gate which roles can run this template
is_system         BOOLEAN NOT NULL DEFAULT false  (for future platform-seeded templates)
```

### ReportResult

Fields beyond standard AuditEntity columns:

```
template_id       BIGINT NOT NULL   FK → report_templates(id)
run_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
run_by_user_id    VARCHAR(100) NOT NULL   (actor UUID from JWT)
date_from         DATE NOT NULL
date_to           DATE NOT NULL
result_json       TEXT NOT NULL     JSON array of row objects
row_count         INTEGER NOT NULL
truncated         BOOLEAN NOT NULL DEFAULT false
run_params_hash   VARCHAR(64)       SHA-256 of the run-time parameters (for cache key)
```

Only the most recent result per template is kept active. Older results are
soft-deleted (AuditEntity `deletedAt`) when a new run completes.

### Flyway V11

```sql
CREATE TABLE report_templates (
    id            BIGSERIAL PRIMARY KEY,
    public_id     UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    club_id       BIGINT NOT NULL REFERENCES clubs(id),
    name          VARCHAR(200) NOT NULL,
    description   VARCHAR(500),
    metrics       TEXT NOT NULL,
    dimensions    TEXT NOT NULL,
    filters       TEXT,
    metric_scope  VARCHAR(50),
    is_system     BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ
);

CREATE TABLE report_results (
    id                BIGSERIAL PRIMARY KEY,
    public_id         UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    template_id       BIGINT NOT NULL REFERENCES report_templates(id),
    run_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    run_by_user_id    VARCHAR(100) NOT NULL,
    date_from         DATE NOT NULL,
    date_to           DATE NOT NULL,
    result_json       TEXT NOT NULL,
    row_count         INTEGER NOT NULL,
    truncated         BOOLEAN NOT NULL DEFAULT false,
    run_params_hash   VARCHAR(64),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ
);

CREATE INDEX idx_report_templates_club_id  ON report_templates(club_id);
CREATE INDEX idx_report_results_template_id ON report_results(template_id);
CREATE INDEX idx_report_results_run_at      ON report_results(run_at DESC);
```

---

## API endpoints

### MetaReportPulseController — `/api/v1/reports/meta`

```
GET /api/v1/reports/meta/metrics      list of available metric codes + labels
GET /api/v1/reports/meta/dimensions   list of available dimension codes + labels
GET /api/v1/reports/meta/filters      list of available filter codes + config schema
```

No permission gate — any authenticated club user can read the catalogue.

### ReportBuilderPulseController — `/api/v1/report-templates`

```
GET    /api/v1/report-templates              list templates for this club
GET    /api/v1/report-templates/{id}         template detail
POST   /api/v1/report-templates              create template
PATCH  /api/v1/report-templates/{id}         update template
DELETE /api/v1/report-templates/{id}         soft delete template

POST   /api/v1/report-templates/{id}/run     execute report → returns result
GET    /api/v1/report-templates/{id}/result  last cached result
GET    /api/v1/report-templates/{id}/export  CSV download of last result
```

Required permission: `report:custom:run` for all endpoints.

---

## Request / Response shapes

### MetricMetaResponse
```json
{
  "code": "revenue",
  "label": "Revenue",
  "labelAr": "الإيرادات",
  "unit": "sar",
  "scope": "revenue",
  "description": "Total payments collected"
}
```

### DimensionMetaResponse
```json
{
  "code": "month",
  "label": "Month",
  "labelAr": "الشهر",
  "compatibleMetricScopes": ["revenue", "members", "leads", "gx", "pt"]
}
```

### ReportTemplateResponse
```json
{
  "id": "uuid",
  "name": "string",
  "description": "string | null",
  "metrics": ["revenue", "new_members"],
  "dimensions": ["month", "branch"],
  "filters": { "branch_id": null, "plan_id": null },
  "metricScope": "revenue",
  "isSystem": false,
  "lastRunAt": "ISO 8601 | null",
  "createdAt": "ISO 8601"
}
```

### CreateReportTemplateRequest
```json
{
  "name": "string (required)",
  "description": "string (optional)",
  "metrics": ["revenue", "new_members"],
  "dimensions": ["month"],
  "filters": { "branch_id": null }
}
```

### RunReportRequest
```json
{
  "dateFrom": "yyyy-MM-dd (required)",
  "dateTo": "yyyy-MM-dd (required)",
  "filters": {
    "branch_id": "uuid (optional override)",
    "plan_id": "uuid (optional override)"
  }
}
```

### ReportResultResponse
```json
{
  "templateId": "uuid",
  "runAt": "ISO 8601",
  "dateFrom": "yyyy-MM-dd",
  "dateTo": "yyyy-MM-dd",
  "columns": ["month", "branch", "revenue_sar", "new_members"],
  "rows": [
    { "month": "2026-03", "branch": "Riyadh", "revenue_sar": "15000.00", "new_members": 42 }
  ],
  "rowCount": 1,
  "truncated": false,
  "fromCache": true
}
```

---

## Available metrics catalogue (backend enum)

```
revenue              → SUM(p.amount_halalas) from payments WHERE status='collected'
refunds              → SUM(p.amount_halalas) from payments WHERE status='refunded'
net_revenue          → revenue - refunds
new_members          → COUNT(m.id) from members WHERE created_at in range
active_memberships   → COUNT(ms.id) from memberships WHERE status='active'
expired_memberships  → COUNT(ms.id) from memberships WHERE status='expired'
frozen_memberships   → COUNT(ms.id) from memberships WHERE status='frozen'
gx_bookings          → COUNT(b.id) from gx_bookings
gx_attendance        → COUNT(b.id) from gx_bookings WHERE attended=true
pt_sessions          → COUNT(s.id) from pt_sessions
pt_attendance        → COUNT(s.id) from pt_sessions WHERE status='attended'
leads_created        → COUNT(l.id) from leads WHERE created_at in range
leads_converted      → COUNT(l.id) from leads WHERE status='converted'
lead_conversion_rate → leads_converted / leads_created * 100 (%)
cash_in              → SUM(e.amount_halalas) from cash_drawer_entries WHERE type='cash_in'
cash_out             → SUM(e.amount_halalas) from cash_drawer_entries WHERE type='cash_out'
```

## Available dimensions catalogue (backend enum)

```
day          → DATE_TRUNC('day', <date_column>)
week         → DATE_TRUNC('week', <date_column>)
month        → DATE_TRUNC('month', <date_column>)
branch       → b.name (JOIN branches)
membership_plan → mp.name (JOIN membership_plans)
class_type   → ct.name (JOIN gx_class_types)
lead_source  → ls.name (JOIN lead_sources)
staff_member → sm.first_name || ' ' || sm.last_name (JOIN staff_members)
```

## Available filters catalogue (backend enum)

```
branch_id        → WHERE branch_id = :branchId
plan_id          → WHERE plan_id = :planId
class_type_id    → WHERE class_type_id = :classTypeId
lead_source_id   → WHERE lead_source_id = :leadSourceId
staff_member_id  → WHERE staff_member_id = :staffMemberId
```

---

## Business rules — enforce in service layer

1. **Metric/dimension/filter codes must be in the approved catalogue**: any
   unknown code in `CreateReportTemplateRequest` returns 422 "Unknown metric:
   {code}". Never pass user-supplied codes into a SQL string.

2. **Max 10 metrics per template**: return 422 "Maximum 10 metrics allowed."

3. **Metric + dimension compatibility**: not all metrics can be grouped by all
   dimensions. GX/PT metrics cannot use `staff_member` dimension (trainers, not
   staff). Revenue metrics cannot use `class_type` dimension. Return 422
   "Metric {code} is not compatible with dimension {code}" on violation.
   Compatibility matrix stored as a map in `ReportBuilderService`.

4. **At least one metric and one dimension required**: return 422 if either
   list is empty.

5. **Date range max 366 days**: `dateTo - dateFrom > 366` returns 422 "Date
   range cannot exceed 366 days."

6. **Tenant isolation**: all queries append `AND club_id = :clubId` (from JWT).
   A club owner can never run a report that includes another club's data.

7. **Result caching**: check Redis key `report_result:{templateId}:{paramHash}`
   before executing SQL. Cache hit: return immediately with `fromCache: true`.
   Cache miss: execute, store in Redis (TTL 10 min), persist to `report_results`
   table, return with `fromCache: false`.

8. **Soft-delete old results**: when a new run completes, soft-delete all
   previous `ReportResult` rows for this template (`deleted_at = NOW()`).
   Only one active result per template at a time.

9. **CSV export uses last result**: `GET /export` loads from `report_results`
   (not Redis) and streams as `text/csv`. Column headers = column codes.
   If no result exists yet → 404 "No result available. Run the report first."

10. **metricScope gate for Sales Agent**: if `template.metricScope ∉
    {"leads"}` and caller has only `report:leads:view`, return 403.
    Owner and Branch Manager with `report:custom:run` can run any template.

---

## Seed data updates

Add to `DevDataLoader.kt`:
```
Add report:custom:run permission to Owner and Branch Manager roles.

Seed 2 system report templates for Elixir Gym:
  Template 1: "Monthly Revenue by Branch"
    metrics: ["revenue", "net_revenue", "new_members"]
    dimensions: ["month", "branch"]
    metricScope: "revenue"
    isSystem: true

  Template 2: "Lead Conversion by Source"
    metrics: ["leads_created", "leads_converted", "lead_conversion_rate"]
    dimensions: ["month", "lead_source"]
    metricScope: "leads"
    isSystem: true
```

---

## Files to generate

### Backend — new files
```
report/
  builder/
    ReportBuilderService.kt
    ReportTemplateService.kt
    ReportBuilderPulseController.kt
    MetaReportPulseController.kt
    MetricCatalogue.kt          (enum + SQL fragment map)
    DimensionCatalogue.kt       (enum + SQL fragment map)
    FilterCatalogue.kt          (enum + SQL fragment map)
    CompatibilityMatrix.kt      (metric ↔ dimension legal combinations)
    ReportTemplate.kt
    ReportTemplateRepository.kt
    ReportResult.kt
    ReportResultRepository.kt
    dto/
      MetricMetaResponse.kt
      DimensionMetaResponse.kt
      FilterMetaResponse.kt
      ReportTemplateResponse.kt
      CreateReportTemplateRequest.kt
      UpdateReportTemplateRequest.kt
      RunReportRequest.kt
      ReportResultResponse.kt

resources/db/migration/V11__report_templates_and_results.sql
```

### Backend — modified files
```
config/DevDataLoader.kt    add report:custom:run permission + 2 seed templates
audit/AuditAction.kt       add REPORT_TEMPLATE_CREATED, REPORT_TEMPLATE_DELETED,
                           REPORT_RUN
```

### Frontend — web-pulse additions
```
src/api/reportBuilder.ts
src/routes/reports/builder/
  index.tsx                  (template list)
  new.tsx                    (builder form)
  $templateId.tsx            (template detail + run + result)
src/components/reportBuilder/
  MetricSelector.tsx
  DimensionSelector.tsx
  FilterBuilder.tsx
  ReportPreviewTable.tsx
  SaveTemplateModal.tsx
  CompatibilityWarning.tsx
```

---

## Implementation order

```
Step 1 — Catalogues + entity definitions
  report/builder/MetricCatalogue.kt — enum of 16 metric codes + SQL fragments
  report/builder/DimensionCatalogue.kt — enum of 8 dimension codes + SQL fragments
  report/builder/FilterCatalogue.kt — enum of 5 filter codes + WHERE fragments
  report/builder/CompatibilityMatrix.kt — Map<MetricCode, Set<DimensionCode>>
    encoding all legal combinations (rules 3)
  ReportTemplate.kt + ReportTemplateRepository.kt
  ReportResult.kt + ReportResultRepository.kt
  Verify: ./gradlew build -x test

Step 2 — Flyway V11 migration
  resources/db/migration/V11__report_templates_and_results.sql
  CREATE TABLE report_templates + report_results + 3 indexes
  Verify: ./gradlew flywayMigrate

Step 3 — ReportTemplateService
  report/builder/ReportTemplateService.kt:
    listTemplates(clubId) → excludes deleted
    getTemplate(id, clubId) → 403 if wrong club (rule 6)
    createTemplate(request, clubId) → validate codes (rule 1), metric count (rule 2),
      compatibility (rule 3), at-least-one (rule 4)
    updateTemplate(id, request, clubId) → same validations
    deleteTemplate(id, clubId) → soft delete
    auditService.log() for create/delete
  DTOs: ReportTemplateResponse, CreateReportTemplateRequest, UpdateReportTemplateRequest
  Verify: ./gradlew build -x test

Step 4 — ReportBuilderService (query execution + caching)
  report/builder/ReportBuilderService.kt:
    runReport(template, request, clubId):
      1. Validate dateFrom/dateTo (rule 5: max 366 days)
      2. Compute paramHash = SHA-256(templateId + dateFrom + dateTo + filters JSON)
      3. Check Redis "report_result:{templateId}:{paramHash}" → cache hit → return
      4. Build parameterized native SQL from catalogues + compatibility matrix
         - SELECT {dimension columns}, {metric aggregates}
           FROM {primary table for first metric}
           LEFT JOIN {dimension tables}
           WHERE club_id = :clubId AND <date range> AND <filters>
           GROUP BY {dimension columns}
           ORDER BY {first dimension}
           LIMIT 50000
      5. Execute, collect rows as List<Map<String, Any>>
      6. Truncate flag if rowCount = 50000 (rule: max rows)
      7. Serialize to JSON, store in Redis TTL=10min, persist ReportResult
      8. Soft-delete previous ReportResult for template (rule 8)
      9. Return ReportResultResponse with fromCache=false
    getLastResult(templateId, clubId): Redis → DB fallback
    exportCsv(templateId, clubId): load from DB, stream CSV (rule 9)
  Verify: ./gradlew build -x test

Step 5 — MetaReportPulseController + ReportBuilderPulseController
  MetaReportPulseController.kt:
    GET /reports/meta/metrics → MetricCatalogue.all()
    GET /reports/meta/dimensions → DimensionCatalogue.all()
    GET /reports/meta/filters → FilterCatalogue.all()
  ReportBuilderPulseController.kt:
    All 8 endpoints (list, get, create, update, delete, run, result, export)
    Permission: report:custom:run on write/run endpoints
    metricScope gate on run (rule 10)
  Verify: ./gradlew build -x test

Step 6 — Seed data + permissions
  DevDataLoader.kt:
    Add report:custom:run to Owner + Branch Manager
    Seed 2 system templates for Elixir Gym
  audit/AuditAction.kt: REPORT_TEMPLATE_CREATED, REPORT_TEMPLATE_DELETED, REPORT_RUN
  Verify: ./gradlew bootRun --args='--spring.profiles.active=dev'
  Manual: POST /api/v1/auth/login as owner → POST /report-templates/{id}/run
    → result returned with correct columns and rows

Step 7 — Backend tests
  ReportBuilderServiceTest.kt (unit):
    - runReport: happy path (revenue + month + branch)
    - cache hit: Redis key present → returns fromCache=true
    - date range > 366 days → 422
    - unknown metric code → 422
    - metric/dimension incompatibility → 422
    - max 10 metrics enforced → 422
    - LIMIT 50000 enforced: mock returns 50001 rows → truncated=true
    - old ReportResult soft-deleted after new run
  ReportTemplateServiceTest.kt (unit):
    - create with valid codes, duplicate name (409)
    - delete: soft deletes, wrong club (403)
    - update: partial fields updated
  ReportBuilderPulseControllerTest.kt (integration):
    - create template, run it, get result, export CSV
    - run with wrong club JWT → 403 (tenant isolation)
    - Sales Agent running leads template → 200; revenue template → 403
    - GET /reports/meta/metrics → full catalogue returned
  Verify: ./gradlew test --no-daemon

Step 8 — Backend final checks
  ./gradlew ktlintFormat --no-daemon
  ./gradlew ktlintCheck --no-daemon
  ./gradlew build --no-daemon

Step 9 — Frontend: builder UI
  src/api/reportBuilder.ts:
    getMetrics(), getDimensions(), getFilters()
    listTemplates(), getTemplate(id), createTemplate(), updateTemplate(), deleteTemplate()
    runReport(id, request), getLastResult(id), exportCsv(id)
  src/routes/reports/builder/index.tsx:
    Table of saved templates: name, metrics count, dimensions, last run, Run button
    "New Report" button → /reports/builder/new
    Each row → /reports/builder/$templateId
    PermissionGate: report:custom:run
  Verify: npm run dev → /reports/builder shows 2 seeded templates

Step 10 — Frontend: builder form (new template)
  src/routes/reports/builder/new.tsx:
    MetricSelector: multi-select checkboxes grouped by scope (Revenue, Members,
      GX, PT, Leads, Cash). Each metric checkbox shows unit (SAR / count / %).
    DimensionSelector: radio group (one primary dimension required) + optional
      second dimension (e.g., month + branch).
    FilterBuilder: per-filter optional value pickers (branch dropdown, plan
      dropdown, etc. — loaded from existing API endpoints).
    CompatibilityWarning: inline warning if selected metric+dimension combo is
      incompatible (client-side pre-validation before submit).
    "Save Report" → POST /report-templates → redirect to $templateId view.
  Verify: npm run dev → create "Revenue by Branch" report → saved and shown

Step 11 — Frontend: template detail + run + result
  src/routes/reports/builder/$templateId.tsx:
    Template header: name, description (inline edit), metrics + dimensions chips.
    "Run Report" section: date range pickers (max 366 days enforced client-side),
      filter overrides (branch, plan, etc.)
    "Run" button → POST /report-templates/{id}/run → loading state →
      ReportPreviewTable with result rows.
    "fromCache" badge: shows "Cached result (refreshes in ~X min)" if fromCache=true.
    "Export CSV" button → GET /report-templates/{id}/export → download.
    "Delete" button (with confirm modal, gated by report:custom:run).
    Reuse existing KpiCard for aggregated totals at top of result.
  Verify: npm run dev → run "Monthly Revenue by Branch" → see result table
    with month + branch + revenue columns

Step 12 — Frontend tests + final checks
  MetricSelector.test.tsx — renders grouped metrics, incompatible combos warn
  DimensionSelector.test.tsx — second dimension optional
  ReportPreviewTable.test.tsx — renders columns + rows, fromCache badge
  FilterBuilder.test.tsx — filter value selected/cleared
  Verify: npm test && npm run typecheck && npm run lint && npm run build
```

---

## Acceptance criteria

### Backend
- [ ] `GET /reports/meta/metrics` returns all 16 metric codes with labels
- [ ] `POST /report-templates` with unknown metric code returns 422
- [ ] `POST /report-templates` with 11 metrics returns 422
- [ ] `POST /report-templates` with incompatible metric+dimension returns 422
- [ ] `POST /report-templates/{id}/run` with 367-day range returns 422
- [ ] Running a report returns correct columns and rows for seeded data
- [ ] Second run with same params returns `fromCache: true`
- [ ] Result capped at 50,000 rows with `truncated: true`
- [ ] Old `ReportResult` soft-deleted after new run
- [ ] `GET /report-templates/{id}/export` returns `text/csv` with correct headers
- [ ] Club A owner cannot run Club B's template (403)
- [ ] Sales Agent can run leads-scope template, gets 403 on revenue-scope
- [ ] All 375+ existing tests still pass

### Frontend
- [ ] `/reports/builder` shows 2 seeded templates for Elixir Gym
- [ ] Builder form groups metrics by scope, shows unit labels
- [ ] Incompatible metric+dimension shows inline warning before saving
- [ ] Date range > 366 days shows validation error before submit
- [ ] "Run Report" shows loading state, then result table
- [ ] "Cached result" badge appears on second run
- [ ] "Export CSV" triggers file download
- [ ] `npm run typecheck`, `lint`, `test`, `build` all pass

---

## RBAC matrix rows added by this plan

| Permission | Owner | Branch Manager | Receptionist | Sales Agent | PT Trainer | GX Instructor |
|---|---|---|---|---|---|---|
| report:custom:run | ✅ | ✅ | ❌ | ❌ (leads-scope only via metricScope) | ❌ | ❌ |

Note: Sales Agent can only run templates where `metricScope = "leads"` —
enforced in `ReportBuilderService.runReport()` (rule 10).

---

## Definition of done

- All acceptance criteria checked
- All 10 business rules covered by unit or integration tests
- SQL injection prevention verified: no user input ever interpolated into query string
- Cache hit/miss tested with mocked Redis
- Tenant isolation: club A cannot run club B's template
- CSV export tested end-to-end
- Both fixed reports (Plan 18) and custom builder coexist without conflict
- All CI checks pass on PR
- PLAN.md deleted before merging
- PR title: `feat(reports): add custom report builder with saved templates and cached results`
- Target branch: `develop`
