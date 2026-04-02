# Architecture Decision Records

This directory contains all architecture decisions for the
Arena platform. Each decision is recorded with its context,
the decision made, and the consequences.

When making a new architectural decision:
1. Create a new file: docs/adr/XXXX-short-title.md
2. Use the format: Status / Context / Decision / Consequences
3. Add a row to the table in this file
4. Reference the ADR number in the relevant PLAN.md

| Number | Title | Decision (one sentence) | Status |
|---|---|---|---|
| 0001 | Use UUID v4 for all public-facing IDs | All public-facing identifiers use UUID v4; database PKs may remain auto-increment integers internally but the API surface exclusively uses UUIDs. | Accepted |
| 0002 | Store monetary values as integers in halalas | All monetary amounts are stored, transmitted, and computed as integers in halalas (1 SAR = 100 halalas). | Accepted |
| 0003 | Stateless JWT authentication (no server-side sessions) | Use stateless JWT authentication for all API access with no server-side sessions maintained. | Accepted |
| 0004 | Flyway for all database schema migrations | Use Flyway for all database schema changes as versioned SQL migration scripts reviewed in PRs. | Accepted |
| 0005 | Multi-tenancy via shared schema filtered by organization_id | Use a shared database schema with row-level tenant isolation filtered by organization_id. | Accepted |
| 0006 | Feature-based packaging over layer-based packaging | Use feature-based packaging where each feature owns its controller, service, repository, domain model, and DTOs together. | Accepted |
| 0007 | RFC 7807 Problem Details for all API error responses | All API error responses use the RFC 7807 Problem Details format with a single global exception handler. | Accepted |
| 0008 | Single backend API serves all clients (web and mobile) | A single Spring Boot backend API serves all five client applications, with client-specific behavior driven by JWT role claims. | Accepted |
| 0009 | ZATCA invoice generation is server-side only | All ZATCA invoice generation, signing, and submission is performed exclusively on the backend. | Accepted |
| 0010 | Kotlin Multiplatform and Compose Multiplatform for mobile | Use KMP for shared business logic and CMP for shared UI across Android and iOS. | Accepted |
| 0011 | URL path versioning for the REST API (/api/v1/) | Use URL path versioning for all API endpoints with new versions introduced only for breaking changes. | Accepted |
| 0012 | Soft deletes via deleted_at nullable timestamp | Implement soft deletes on critical entities using a nullable deleted_at timestamp column. | Accepted |
| 0013 | Arabic as the default language with English as secondary | Arabic is the default language for all customer-facing applications; English is the default for Nexus. | Accepted |

ADRs are immutable once their status is Accepted.
To supersede a decision, create a new ADR and update
the original status to: Superseded by ADR-XXXX
