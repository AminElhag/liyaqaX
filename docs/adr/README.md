# Architecture Decision Records

This directory contains all architecture decisions for the
Liyaqa platform. Each decision is recorded with its context,
the decision made, and the consequences.

When making a new architectural decision:
1. Create a new file: docs/adr/XXXX-short-title.md
2. Use the format: Status / Context / Decision / Consequences
3. Add a row to the table in this file
4. Reference the ADR number in the relevant PLAN.md

| Number | Title | Decision (one sentence) | Status |
|---|---|---|---|
| 0001 | Use UUID v4 for all public-facing IDs | All API-exposed IDs are UUID v4; internal PKs remain BIGINT | Accepted |
| 0002 | Store monetary values as integers in halalas | All money stored as BIGINT in halalas (1 SAR = 100 halalas) | Accepted |
| 0003 | Stateless JWT authentication | No server-side sessions; access token 15min, refresh token 7 days | Accepted |
| 0004 | Flyway for database schema migrations | All schema changes via versioned Flyway migration files | Accepted |
| 0005 | Multi-tenancy via shared schema with organization_id | Single database, all tenant tables carry organization_id | Accepted |
| 0006 | Feature-based packaging over layer-based | Code organized by domain feature, not by technical layer | Accepted |
| 0007 | RFC 7807 Problem Details for all API error responses | All errors return RFC 7807 JSON with type, title, status, detail | Accepted |
| 0008 | Single backend API serves all clients | One Spring Boot API serves web, mobile, and internal apps | Accepted |
| 0009 | ZATCA invoice generation is server-side only | Invoices generated and signed on backend; frontend triggers only | Accepted |
| 0010 | Kotlin Multiplatform and Compose Multiplatform for mobile | Shared business logic and UI via KMP/CMP for Android and iOS | Accepted |
| 0011 | URL path versioning for the REST API | All endpoints versioned via /api/v1/ path prefix | Accepted |
| 0012 | Soft deletes via deleted_at nullable timestamp | Records never hard deleted; deleted_at IS NULL means active | Accepted |
| 0013 | Arabic as the default language with English as secondary | Arabic is default locale; all user-facing text requires both languages | Accepted |
| 0014 | Dynamic Configurable RBAC with Role-ID JWT Claims | Roles and permissions are fully dynamic per club, resolved via Redis using roleId JWT claim | Accepted |

---

ADRs are immutable once their status is Accepted.
To supersede a decision, create a new ADR and update
the original status to: Superseded by ADR-XXXX
