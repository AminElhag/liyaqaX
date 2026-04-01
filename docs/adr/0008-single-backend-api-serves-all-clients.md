# ADR-0008 — Single backend API serves all clients (web and mobile)

## Status
Accepted

## Context
The platform has five client applications: four web apps (Nexus, Pulse, Coach, Arena) and one mobile app (Arena Mobile). Two architectural approaches were considered: a Backend-for-Frontend (BFF) pattern with a dedicated API layer per client, or a single unified API serving all clients. A BFF approach would allow tailoring responses per client but introduces significant operational overhead — five separate API services to build, deploy, monitor, and keep in sync with shared business logic. The clients differ primarily in RBAC scope and UI concerns, not in the shape of the data they consume. Authorization differences are handled by JWT role claims, and presentation-layer concerns (formatting, field selection) belong in the clients themselves. The mobile app shares the same backend as web-arena, with mobile-specific endpoints only where strictly required by device capabilities (e.g., push token registration).

## Decision
A single Spring Boot backend API serves all five client applications. Client-specific behavior is driven by JWT role claims and tenant context, not by separate API surfaces. Mobile-specific endpoints are added only when a device capability has no web equivalent (e.g., push notification token registration, QR code token generation). Services communicate over HTTP only — no cross-service code imports.

## Consequences
- One codebase, one deployment pipeline, one set of migrations, one monitoring stack — dramatically lower operational complexity compared to five BFFs.
- Business logic is implemented once and enforced consistently across all clients — no risk of divergent validation or authorization rules between BFF layers.
- RBAC enforcement happens in one place: the backend checks JWT role claims on every request, serving the appropriate data scope regardless of which client is calling.
- API design must accommodate all clients' needs without becoming bloated — careful use of DTOs, sparse fieldsets, and versioning is required.
- A single API is a single point of failure — horizontal scaling, health checks, and circuit breakers are essential.
- Mobile-specific needs (offline sync, push tokens) may occasionally require endpoints that web clients do not use, but these are kept minimal and clearly scoped.
