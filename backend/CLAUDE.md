# CLAUDE.md — Backend Engineering Rules

This file defines the engineering standards Claude must follow in this codebase.
Apply these rules to every file, endpoint, and PR — no exceptions.

---

## 1. Project structure

- Use **feature-based packaging**, not layer-based. Each feature owns its controller, service, repository, and DTOs together.
- Apply **Clean / Hexagonal Architecture** for complex bounded contexts: domain logic must be free of framework annotations. Define ports (interfaces) and adapters (infrastructure implementations) separately.
- Keep the domain model pure: no HTTP, no ORM, no framework dependencies inside domain classes.

---

## 2. API design

- Follow **REST conventions**: nouns for resources, HTTP verbs for actions, plural resource names (`/members`, not `/getMember`).
- **Version every API from day one** using URL path versioning: `/api/v1/`, `/api/v2/`. Never omit the version prefix.
- Introduce a new version **only for breaking changes** (removing a field, changing a type, altering required params). Additive changes (new optional fields) do not require a version bump.
- Communicate deprecated versions via a `Deprecation` response header. Document the sunset date.
- Always use **DTOs** for request and response bodies. Never expose internal domain or database entities directly from an endpoint.
- Validate all input at the boundary using declarative validation (`@NotBlank`, `@Size`, `@Pattern`, etc.). Never trust client data.

---

## 3. Error handling

- Use **RFC 7807 Problem Details** as the standard error response shape:
  ```json
  {
    "type": "https://example.com/errors/validation-failed",
    "title": "Validation failed",
    "status": 400,
    "detail": "The field 'email' must not be blank.",
    "instance": "/api/v1/members"
  }
  ```
- Route all uncaught exceptions through a **single global handler**. The error response shape must be consistent everywhere.
- Never expose stack traces, internal class names, or database messages in production error responses.
- Map HTTP status codes correctly: `400` for client errors, `401` for unauthenticated, `403` for unauthorized, `404` for not found, `409` for conflicts, `422` for business rule violations, `500` for unexpected server errors.

---

## 4. Authentication & authorization

- Use **stateless JWT** or **OAuth2** for APIs. Avoid server-side sessions.
- Apply **deny-by-default**: secure all endpoints by default, then explicitly permit public paths.
- Implement **RBAC** (Role-Based Access Control). Enforce permissions at the service layer, not only at the controller.
- Hash passwords with a strong adaptive algorithm (e.g., bcrypt, Argon2). Never store plaintext or reversible passwords.
- Validate token expiry, signature, and claims on every request.

---

## 5. Data & persistence

- **Never use `ddl-auto=update` or `ddl-auto=create` in production.** Use a migration tool (Flyway or Liquibase) for all schema changes. Every change is a versioned, reviewed migration script.
- Use **parameterized queries** exclusively. No string-concatenated SQL, ever.
- Avoid N+1 query problems: use `JOIN FETCH`, `@EntityGraph`, or batch loading for associations you know you'll need.
- Place `@Transactional` on service methods, not on controllers. Use `@Transactional(readOnly = true)` on all query-only methods.
- Design for **soft deletes** on critical entities (add `deleted_at`, `is_deleted`) rather than hard deletes, to preserve audit history.

---

## 6. Caching

- Cache at the **service layer**, not the controller or repository.
- Always define a **TTL** (time-to-live). Never cache without an expiry.
- Design an explicit **cache invalidation strategy** before adding any cache. Stale data is worse than slow data in most business contexts.
- Use cache keys that include all the dimensions that affect the result (e.g., tenant ID, locale, user role).
- Document what is cached and why, alongside the TTL and invalidation trigger.

---

## 7. Async processing & queues

- Use **async processing** (message queues, background jobs) for any operation that is not required to complete within the HTTP request: emails, webhooks, report generation, third-party sync.
- All async consumers must be **idempotent**: processing the same message twice must produce the same result. Store a processed event/message ID and skip duplicates.
- Apply an `Idempotency-Key` pattern for any write operation that could be retried by the client (payments, invoice creation, subscription changes). Store the result keyed to that value.
- Use **dead-letter queues** for failed messages. Never silently discard failures.

---

## 8. Resilience

- Apply the following to every network call (external API, downstream service, database under load):
  - **Timeout**: never block indefinitely. Set explicit connect and read timeouts.
  - **Retry with exponential backoff + jitter**: retry transient failures, not business logic failures.
  - **Circuit breaker**: fail fast when a downstream is consistently unhealthy.
- Never let one slow dependency cascade into a full outage. Isolate failures at the boundary.

---

## 9. Rate limiting

- Apply rate limiting at the **API gateway or filter layer**, before requests reach business logic.
- Limit per tenant/organization, not just per IP.
- Return `429 Too Many Requests` with a `Retry-After` header when the limit is exceeded.
- Use the **token bucket** or **sliding window** algorithm for smooth enforcement.

---

## 10. Security

- Follow the **OWASP Top 10** as a baseline checklist.
- Apply the **principle of least privilege** to every DB user, service account, and IAM role.
- Store all secrets in a **secrets manager** (AWS Secrets Manager, HashiCorp Vault, etc.) — never in source code, `.env` files committed to VCS, or plain environment variables in production.
- Rotate secrets on a defined schedule.
- Enforce **HTTPS everywhere**. Set `Strict-Transport-Security` (HSTS) headers.
- Sanitize all inputs. Never trust anything from the client side.
- Audit-log every write to sensitive or financial data.

---

## 11. Observability

- Use **structured logging** (JSON format in production). Include a correlation/trace ID on every log line within a request.
- Log at the **service boundary**: what was called, by whom, how long it took, and the outcome. Do not log inside every method.
- Never log sensitive data: passwords, tokens, PII, payment card numbers.
- Expose health, readiness, and metrics endpoints (`/health`, `/ready`, `/metrics`).
- Use the **three pillars**: logs (what happened), metrics (how often / how fast), traces (where time was spent).

---

## 12. Testing

Follow the **test pyramid**:

| Layer | Scope | Tools | Volume |
|---|---|---|---|
| Unit | Single class, no Spring context | JUnit, Mockito | Most tests |
| Integration | Real DB, real beans | Testcontainers, `@DataJpaTest` | Medium |
| Contract | API shape between services | Pact / OpenAPI | Selective |
| E2E | Full stack, happy paths only | REST Assured | Few |

- Unit tests must be **fast and context-free**: no Spring boot context, no database, no network.
- Mock external dependencies at the service boundary in unit tests.
- Integration tests must use a **real database** (via Testcontainers), not H2 in production-database mode.
- Every bug fix ships with a regression test.

---

## 13. CI/CD

- Every push to any branch runs the **full test suite**. Merging to main requires all tests green.
- No secrets in CI environment variables stored in plain text. Use the CI platform's secret store.
- Use **blue-green** or **canary deployments** for production releases. Always have a rollback path.
- Database migrations run **before** the new application version starts, and must be backward-compatible with the previous version (so rollback is safe).
- Build artifacts are **immutable and versioned**. The same artifact that passed CI is the one deployed to production.

---

## 14. Documentation

- Generate API docs from code using **OpenAPI / Swagger**. Docs must stay in sync with the implementation automatically, not manually.
- Every endpoint must document: auth requirements, request schema, response schema, and all possible error responses.
- Maintain a **CHANGELOG** file at the repo root. Every breaking change and every new version gets an entry.
- Write a `README.md` that covers: how to run locally, environment variables required, how to run tests, and how to apply migrations.

---

## 15. Multi-tenancy (SaaS)

- Enforce **tenant isolation at the data layer**, not just the controller layer. A missing filter must make a query fail, not return all tenants' data.
- Propagate tenant context (org ID, tenant ID) via a thread-local or request-scoped context object, populated once at the filter/interceptor layer.
- Apply tenant filtering automatically via a JPA interceptor, Hibernate Filter, or repository base class — so developers cannot accidentally forget it.
- Audit log all cross-tenant operations. Any admin bypass of tenant isolation must be explicit, logged, and require elevated privileges.
- Test tenant isolation explicitly: assert that tenant A cannot read or write tenant B's data, in integration tests.

---

## General principles

- **Make it work, make it right, make it fast** — in that order.
- Prefer **explicit over implicit**. If something isn't obvious from reading the code, document it.
- Apply the **single responsibility principle** at every level: method, class, module, service.
- Write code that is easy to delete, not just easy to extend.
- Leave the codebase cleaner than you found it.
