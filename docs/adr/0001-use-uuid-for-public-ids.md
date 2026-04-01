# ADR-0001 — Use UUID v4 for all public-facing IDs

## Status
Accepted

## Context
The system exposes IDs in REST API responses, URL paths, and cross-service references. Auto-increment integers leak information about record volume and creation order, are trivially guessable, and create enumeration attack surfaces — a concern for a public-facing member portal handling financial and personal data. The system also spans multiple clients (four web apps and a mobile app) and may eventually involve cross-service or cross-database references where sequential integers would collide or require coordination. UUID v7 (time-ordered) was considered but rejected as unnecessary given that ordering is handled by explicit timestamp fields (`createdAt`).

## Decision
All public-facing identifiers use UUID v4. Database primary keys may remain auto-increment integers internally, but the API surface exclusively uses UUIDs. UUID fields follow the naming convention `id` on the primary entity and `<entity>Id` on references (e.g., `memberId`, `branchId`, `planId`).

## Consequences
- Eliminates enumeration attacks — IDs are not guessable.
- Decouples internal storage identity from the public API contract, allowing future database changes without breaking clients.
- UUIDs are larger than integers (128 bits vs 32/64 bits), resulting in slightly larger payloads, indexes, and join costs.
- Requires maintaining a mapping between internal integer PKs and public UUIDs if both are used in the same table.
- All teams must consistently use UUIDs in API contracts and never expose integer PKs — enforced by code review and DTO discipline.
