# API.md — Backend API Design Rules & Standards

This file defines the rules for every API endpoint, request/response contract,
and OpenAPI specification in this project.

No endpoint is designed or documented in isolation.
Every endpoint is defined as part of the feature that requires it —
never speculatively, never ahead of implementation.

This file is permanent. It is read at the start of every backend and
frontend development session alongside `backend/CLAUDE.md` and `backend/DATABASE.md`.
It does not contain any endpoint definitions — those live in the OpenAPI spec
files under `docs/api/` and in the Springdoc-generated output.

---

## 1. API design philosophy

- **Resource-oriented**: URLs represent resources (nouns), HTTP methods represent actions (verbs).
- **Consistent over clever**: a boring, predictable API is a good API.
- **Contract-first within a feature**: before writing a controller or a frontend query,
  agree on the request/response shape. The contract is the handshake.
- **Additive evolution**: new fields can be added to responses at any time.
  Removing, renaming, or changing the type of an existing field is a breaking change
  and requires a new API version.
- **Fail loudly**: every error returns a structured, machine-readable response.
  Silent failures, empty 200 responses for errors, and generic "something went wrong"
  messages are never acceptable.

---

## 2. Versioning

Per ADR-0011. URL path versioning is used for all endpoints.

### Format

```
/api/v{n}/{resource}
```

Examples:
```
GET  /api/v1/members
GET  /api/v1/members/{id}
POST /api/v1/memberships
```

### Rules

- Every endpoint is versioned from the day it is created. There is no unversioned API.
- The current version is `v1`. A `v2` is only introduced when a breaking change
  cannot be avoided. Additive changes (new optional fields) never require a new version.
- When a new version is introduced, the old version is kept running for a
  **deprecation window** of at minimum 3 months.
- Deprecated endpoints return a `Deprecation` header:
  ```
  Deprecation: true
  Sunset: Sat, 01 Jan 2026 00:00:00 GMT
  Link: <https://api.example.com/api/v2/members>; rel="successor-version"
  ```
- Version is in the path only. Never in a header, query param, or subdomain.

---

## 3. URL structure

### Resource naming

- Plural nouns for collections: `/members`, `/memberships`, `/pt-sessions`
- kebab-case for multi-word resources: `/membership-plans`, `/pt-packages`, `/gx-classes`
- Never verbs in URLs: `/api/v1/members` not `/api/v1/getMembers`
- Never abbreviations: `/organizations` not `/orgs`, `/memberships` not `/mems`

### Hierarchy

Nest resources only when the child cannot exist or be accessed without the parent,
and the nesting depth does not exceed two levels:

```
/api/v1/clubs/{clubId}/branches               ✓ branches belong to a club
/api/v1/members/{memberId}/memberships        ✓ memberships belong to a member
/api/v1/members/{memberId}/pt-sessions        ✓ sessions belong to a member

/api/v1/clubs/{clubId}/branches/{branchId}/members/{memberId}/memberships/{id}  ✗ too deep
```

When nesting would exceed two levels, flatten and use query parameters:
```
GET /api/v1/memberships?memberId={id}&branchId={id}   ✓ flattened with filters
```

### Path parameters vs query parameters

| Use case | Use |
|---|---|
| Identifying a specific resource | Path parameter: `/members/{id}` |
| Filtering a collection | Query parameter: `/members?status=active` |
| Sorting a collection | Query parameter: `/members?sort=createdAt&order=desc` |
| Paginating a collection | Query parameter: `/members?page=0&size=20` |
| Optional scoping | Query parameter: `/sessions?branchId={id}` |

### ID format in URLs

Path parameters always use `public_id` (UUID format), never the internal integer `id`:
```
GET /api/v1/members/550e8400-e29b-41d4-a716-446655440000   ✓
GET /api/v1/members/42                                      ✗ never expose internal IDs
```

---

## 4. HTTP methods

Use HTTP methods for their defined semantics. Never improvise.

| Method | Semantics | Body | Idempotent | Safe |
|---|---|---|---|---|
| `GET` | Retrieve a resource or collection | None | Yes | Yes |
| `POST` | Create a new resource | Required | No | No |
| `PUT` | Replace a resource entirely | Required | Yes | No |
| `PATCH` | Partial update of a resource | Required | No | No |
| `DELETE` | Remove a resource (soft delete) | None | Yes | No |

### Rules

- `GET` requests never modify state. Ever.
- `POST` to a collection creates a new member: `POST /api/v1/members`
- `PUT` replaces the entire resource. All fields must be supplied.
  If partial update is the intent, use `PATCH`.
- `PATCH` updates only the supplied fields. Omitted fields are unchanged.
- `DELETE` triggers a soft delete (sets `deleted_at`). It does not physically remove data.
- Non-CRUD actions that do not map cleanly to a resource use `POST` with a
  descriptive sub-resource noun:
  ```
  POST /api/v1/memberships/{id}/freeze      ← freeze a membership
  POST /api/v1/memberships/{id}/unfreeze    ← unfreeze a membership
  POST /api/v1/pt-sessions/{id}/cancel      ← cancel a session
  POST /api/v1/invoices/{id}/submit-zatca   ← submit to ZATCA
  ```

---

## 5. HTTP status codes

Use the most specific appropriate status code. Never return `200` for an error.

### Success codes

| Code | When to use |
|---|---|
| `200 OK` | Successful GET, PUT, PATCH |
| `201 Created` | Successful POST that creates a resource |
| `204 No Content` | Successful DELETE or action with no response body |
| `202 Accepted` | Request accepted for async processing (e.g., ZATCA submission) |

### Client error codes

| Code | When to use |
|---|---|
| `400 Bad Request` | Malformed request, invalid JSON, missing required field |
| `401 Unauthorized` | No valid authentication token provided |
| `403 Forbidden` | Authenticated but not authorized for this action or resource |
| `404 Not Found` | Resource does not exist or is soft-deleted |
| `409 Conflict` | Resource state conflict (e.g., member already has active membership) |
| `410 Gone` | Resource existed but has been permanently removed (rare) |
| `422 Unprocessable Entity` | Request is valid but violates a business rule |
| `429 Too Many Requests` | Rate limit exceeded |

### Server error codes

| Code | When to use |
|---|---|
| `500 Internal Server Error` | Unexpected error — never returned intentionally |
| `502 Bad Gateway` | Upstream service (ZATCA, Qoyod) returned an error |
| `503 Service Unavailable` | Service temporarily unavailable (maintenance, overload) |

### `404` vs `403` — the security rule

When a resource exists but the requester is not authorized to see it,
return `403`, not `404`. Returning `404` to hide the existence of a resource
is acceptable only in specific security-sensitive contexts (e.g., admin-only
resources should return `404` to non-admin users to avoid information leakage).
Document the choice when `404` is used for this purpose.

---

## 6. Request design

### Request body

- All request bodies are JSON. `Content-Type: application/json` is always required
  on requests with a body.
- Request body fields use **camelCase**.
- Required vs optional fields are explicit in the OpenAPI spec.
  Never rely on implicit defaults for required fields.
- IDs in request bodies are always `publicId` strings (UUID format),
  never internal integer IDs.

### Field naming conventions

| Concept | Convention | Example |
|---|---|---|
| Identifiers | camelCase, `Id` suffix | `memberId`, `planId` |
| Timestamps | camelCase, `At` suffix | `createdAt`, `expiresAt` |
| Booleans | camelCase, `is`/`has`/`can` prefix | `isActive`, `hasSignedWaiver` |
| Monetary amounts | camelCase, `Halalas` suffix | `priceHalalas`, `totalHalalas` |
| Localized fields | camelCase, `Ar`/`En` suffix | `nameAr`, `nameEn` |
| Enums / status | camelCase | `membershipStatus`, `paymentMethod` |
| Collections | camelCase, plural | `items`, `sessions`, `plans` |

### Validation rules

- All input validation is performed at the controller layer using Bean Validation.
- Validation errors return `400 Bad Request` with RFC 7807 body listing all invalid fields.
- Business rule violations (e.g., "member already has active membership") return `422`.
- Never validate business rules at the controller layer — those belong in the service layer.
- Input sanitization (trimming whitespace, normalizing casing) happens in the service layer,
  not in the controller or entity.

---

## 7. Response design

### Response body structure

All responses return JSON. `Content-Type: application/json` always.

**Single resource response** — return the resource object directly:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Ahmed Al-Rashidi",
  "membershipStatus": "active",
  "createdAt": "2025-01-15T10:30:00Z"
}
```

**Collection response** — always paginated, always wrapped:
```json
{
  "items": [ ... ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 143,
    "totalPages": 8,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

**Async operation response** — for operations that start a background job:
```json
{
  "jobId": "uuid",
  "status": "accepted",
  "statusUrl": "/api/v1/jobs/uuid"
}
```

### Field rules

- Response fields use **camelCase**.
- `id` in all responses is always the `publicId` (UUID string). Never the internal integer.
- Timestamps are ISO 8601 UTC strings: `"2025-01-15T10:30:00Z"`. Never Unix timestamps.
  Never local time strings without a timezone offset.
- Monetary amounts are integers in halalas. The field name includes the unit: `priceHalalas`.
- Null fields: omit optional fields that are null rather than including `"field": null`,
  unless the frontend needs to distinguish "not set" from "not returned".
  Document the choice in the OpenAPI spec.
- Never return internal database IDs (`BIGINT` primary keys) in any response.
- Never return passwords, tokens, secrets, or credentials in any response.
- Never return other tenants' data. Every response is tenant-scoped.

### Envelope policy

Do not wrap every response in a generic `{ "data": {...}, "meta": {...} }` envelope.
Return resources directly for single-resource responses.
Use the paginated wrapper only for collections.
Reserve envelopes for async jobs and bulk operations.

---

## 8. Error responses — RFC 7807

Every error response follows RFC 7807 Problem Details for HTTP APIs.
Per ADR-0007.

### Structure

```json
{
  "type": "https://arena.app/errors/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields failed validation.",
  "instance": "/api/v1/members",
  "errors": [
    {
      "field": "email",
      "code": "INVALID_FORMAT",
      "message": "Must be a valid email address"
    },
    {
      "field": "phoneNumber",
      "code": "REQUIRED",
      "message": "Phone number is required"
    }
  ]
}
```

### Field definitions

| Field | Required | Description |
|---|---|---|
| `type` | Yes | URI identifying the error type. Stable across environments. |
| `title` | Yes | Short human-readable summary. Does not change between occurrences. |
| `status` | Yes | HTTP status code. Must match the actual response status. |
| `detail` | Yes | Human-readable explanation specific to this occurrence. |
| `instance` | Yes | The request URI that produced the error. |
| `errors` | No | Array of field-level errors for validation failures only. |

### Error type URIs

Error type URIs follow this pattern:
```
https://arena.app/errors/<kebab-case-error-name>
```

Define a consistent set of error types and reuse them:
```
https://arena.app/errors/validation-failed
https://arena.app/errors/resource-not-found
https://arena.app/errors/unauthorized
https://arena.app/errors/forbidden
https://arena.app/errors/conflict
https://arena.app/errors/business-rule-violation
https://arena.app/errors/rate-limit-exceeded
https://arena.app/errors/integration-error
https://arena.app/errors/internal-error
```

### Rules

- Every error — without exception — returns an RFC 7807 body.
- `detail` is written for a human reading it. No stack traces, no exception class names,
  no database error messages, no internal system details.
- In production, `500` errors return a generic detail:
  "An unexpected error occurred. Our team has been notified."
  The actual exception is logged server-side with the correlation ID.
- Validation errors always include the `errors` array with per-field detail.
- Business rule violations (`422`) include a `detail` that explains the rule:
  "A member can only have one active membership at a time."

---

## 9. Pagination

All collection endpoints are paginated. No endpoint returns an unbounded list.

### Query parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `page` | integer | `0` | Zero-based page number |
| `size` | integer | `20` | Items per page. Max: `100` |
| `sort` | string | `createdAt` | Field to sort by |
| `order` | string | `desc` | Sort direction: `asc` or `desc` |

### Rules

- Default page size is `20`. Maximum page size is `100`.
  Requests for more than 100 items return `400 Bad Request`.
- Sort field must be in an allowlist defined per endpoint.
  Sorting on arbitrary fields is not supported — it opens SQL injection vectors
  and causes unpredictable performance.
- The `totalElements` and `totalPages` fields are always included.
  The frontend must never count items to determine if more pages exist.
- Cursor-based pagination is used for high-volume, append-only collections
  (audit logs, integration logs, message history) where offset pagination
  is too slow. Cursor-based endpoints use `cursor` and `limit` params
  instead of `page` and `size`, and return `nextCursor` in the response.

---

## 10. Filtering and searching

### Query parameter conventions

| Filter type | Convention | Example |
|---|---|---|
| Exact match | `?field=value` | `?status=active` |
| Multiple values | `?field=v1&field=v2` | `?status=active&status=frozen` |
| Date range | `?field[gte]=date&field[lte]=date` | `?expiresAt[gte]=2025-01-01` |
| Text search | `?q=search+term` | `?q=ahmed` |
| Tenant scope | `?branchId=uuid` | Always UUID, always optional if JWT provides scope |

### Rules

- Filter parameters are always optional. An endpoint without filters returns all
  records the requester is authorized to see.
- Tenant scope filters (`organizationId`, `clubId`, `branchId`) are derived from
  the JWT claims first. A query parameter can further narrow the scope but never
  broaden it beyond what the token allows.
- Text search (`?q=`) performs a case-insensitive search on defined fields only.
  The searchable fields are documented per endpoint in the OpenAPI spec.
- Never allow filtering on arbitrary columns — define the allowed filter params
  explicitly per endpoint.

---

## 11. Authentication & authorization headers

### Request headers

Every authenticated request must include:
```
Authorization: Bearer <access_token>
```

Every request (authenticated or not) should include:
```
X-Correlation-Id: <uuid>      ← client-generated, used for request tracing
X-Client-App: web-pulse        ← identifies the calling application
X-Client-Version: 1.0.0        ← version of the calling application
```

### Authorization model

- Token is a JWT. Claims include: `sub` (userId), `role`, `organizationId`,
  `clubId` (if scoped), `branchIds` (array), `exp`, `iat`.
- Every endpoint declares its required role(s) in the OpenAPI spec
  under `security` and in a code-level annotation.
- Role enforcement happens in the service layer, not only in the controller.
  A controller that reaches a service method without the correct role
  must fail at the service boundary as a second line of defense.
- Tenant isolation is enforced by extracting `organizationId` from the JWT,
  never from the request body or path. A client cannot request data for
  a different organization by passing a different ID.

---

## 12. Idempotency

Any write operation that could be safely retried must support idempotency.
This is mandatory for: payment collection, invoice generation, ZATCA submission,
subscription creation, and any operation where duplicate execution causes
business or financial harm.

### Implementation

The client sends a unique key with the request:
```
Idempotency-Key: <uuid>
```

The server stores the response keyed to this value. On a duplicate request
with the same key, the server returns the stored response without re-executing.
Idempotency keys expire after 24 hours.

### Rules

- Idempotency key is a UUID generated by the client per logical operation.
  Retrying the same logical operation reuses the same key.
  A new operation always uses a new key.
- The `Idempotency-Key` header is documented as required on all endpoints
  that need it in the OpenAPI spec.
- The server never executes the operation twice for the same key within the
  expiry window, even under concurrent requests.
- Idempotency is enforced at the service layer, not just the controller.

---

## 13. Rate limiting

Rate limiting is applied at the API gateway or filter layer.

### Response headers

Every API response includes:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 87
X-RateLimit-Reset: 1704067200
```

### Exceeded limit response

```
HTTP/1.1 429 Too Many Requests
Retry-After: 30
```

With RFC 7807 body:
```json
{
  "type": "https://arena.app/errors/rate-limit-exceeded",
  "title": "Rate limit exceeded",
  "status": 429,
  "detail": "You have exceeded the rate limit of 100 requests per minute.",
  "instance": "/api/v1/members"
}
```

---

## 14. OpenAPI specification

### Location and format

- OpenAPI specs live under `docs/api/`.
- One spec file per domain (not one giant file):
  ```
  docs/api/
  ├── openapi.yaml          ← root spec, imports all domain specs
  ├── members.yaml
  ├── memberships.yaml
  ├── finance.yaml
  ├── pt.yaml
  ├── gx.yaml
  ├── staff.yaml
  ├── leads.yaml
  ├── integrations.yaml
  ├── auth.yaml
  └── shared-schemas.yaml   ← reusable components (Pagination, Error, etc.)
  ```
- YAML format. Not JSON. YAML is more readable for human editing.
- OpenAPI version: 3.1.0.

### Spec-first within a feature

When developing a new endpoint as part of a feature:
1. Write or update the OpenAPI spec entry first.
2. Review the contract (request shape, response shape, error cases).
3. Implement the backend controller, service, and repository.
4. Generate or update the frontend TypeScript types from the spec.

The spec is the contract. Code implements the contract. Never write code
and generate the spec from it — that produces specs that document what
was built, not what was agreed.

### Required documentation per endpoint

Every endpoint in the OpenAPI spec must document:

```yaml
/api/v1/members/{id}:
  get:
    summary: Get a member by ID          # one line
    description: |                        # full description if needed
      Returns the member profile...
    operationId: getMemberById            # unique, camelCase
    tags: [Members]                       # domain tag
    security:
      - bearerAuth: []                    # auth requirement
    x-required-role: receptionist        # custom extension: minimum role
    parameters:
      - name: id
        in: path
        required: true
        schema:
          type: string
          format: uuid
    responses:
      '200':
        description: Member found
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/MemberResponse'
      '401':
        $ref: '#/components/responses/Unauthorized'
      '403':
        $ref: '#/components/responses/Forbidden'
      '404':
        $ref: '#/components/responses/NotFound'
```

### Shared components

Define reusable schemas in `shared-schemas.yaml` and reference them everywhere.
Never duplicate a schema definition across files.

Mandatory shared components to define once and reuse:
```yaml
components:
  schemas:
    ProblemDetail:       # RFC 7807 error response
    Pagination:          # pagination wrapper for collections
    MonetaryAmount:      # { amountHalalas: integer, currency: "SAR" }
    LocalizedText:       # { ar: string, en: string }
    AuditFields:         # createdAt, updatedAt, deletedAt
  responses:
    Unauthorized:        # 401
    Forbidden:           # 403
    NotFound:            # 404
    Conflict:            # 409
    ValidationFailed:    # 400 with errors array
    BusinessRuleViolation: # 422
    RateLimitExceeded:   # 429
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

---

## 15. Generating TypeScript types from the spec

Frontend apps (`web-nexus`, `web-pulse`, `web-coach`, `web-arena`) must
generate their API types from the OpenAPI spec — never write them by hand.

Tool: `openapi-typescript` (runs as a `pnpm` script in each frontend app).

```bash
pnpm generate:types
```

This generates `src/types/api.generated.ts` in each frontend app.
That file is committed to version control and regenerated whenever the spec changes.

Rules:
- Never manually edit `api.generated.ts`. It is always overwritten by the generator.
- Hand-written API types that duplicate what the spec defines are not permitted.
- When the backend spec changes, regenerate types in all affected frontend apps
  as part of the same PR that changes the spec.

---

## 16. API documentation — Springdoc

The backend generates live API documentation via Springdoc OpenAPI.

- Documentation is available at `/swagger-ui.html` in dev and staging environments only.
- It is disabled in production (`springdoc.api-docs.enabled=false` in `application-prod.yml`).
- The Springdoc-generated output is used for developer reference only.
  The `docs/api/` spec files are the source of truth — not the generated output.
- Every controller method has `@Operation`, `@ApiResponse`, and `@Tag` annotations
  that match the OpenAPI spec exactly. Divergence between annotations and spec is a bug.

---

## 17. Breaking vs non-breaking changes

### Non-breaking (safe to ship without a version bump)

- Adding a new optional field to a response
- Adding a new optional query parameter
- Adding a new endpoint
- Adding a new value to an enum in a response
- Relaxing a validation constraint (e.g., increasing max length)

### Breaking (requires a new API version)

- Removing a field from a request or response
- Renaming a field
- Changing the type of a field
- Making an optional field required
- Changing the URL structure
- Changing the HTTP method of an endpoint
- Removing an endpoint
- Changing error response structure
- Tightening a validation constraint

When a breaking change is unavoidable:
1. Create `v2` of the affected endpoint only — not the entire API.
2. Keep `v1` running for the deprecation window.
3. Add `Deprecation` and `Sunset` headers to `v1` responses.
4. Update all client apps to use `v2` before the sunset date.
5. Write an ADR documenting why the breaking change was necessary.

---

## 18. What never belongs in an API response

- Internal database IDs (BIGINT primary keys)
- Passwords, password hashes, or salts
- JWT tokens or refresh tokens (except the auth endpoint that issues them)
- Raw exception messages or stack traces
- Other tenants' data
- Internal system paths, server names, or infrastructure details
- Credentials for any external service
- Personally identifiable information beyond what the endpoint's purpose requires
