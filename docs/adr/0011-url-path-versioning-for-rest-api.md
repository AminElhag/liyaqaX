# ADR-0011 — URL path versioning for the REST API (/api/v1/)

## Status
Accepted

## Context
The backend API serves five clients that are deployed and updated independently. Breaking changes (removing a field, changing a type, altering required parameters) must not silently break existing clients. Three versioning strategies were considered: URL path versioning (`/api/v1/`), header-based versioning (`Accept: application/vnd.arena.v1+json`), and query parameter versioning (`?version=1`). Header-based versioning keeps URLs clean but is invisible in logs, browser address bars, and documentation — making debugging and API exploration harder. Query parameter versioning is non-standard and easily omitted. URL path versioning is the most explicit, discoverable, and widely understood approach. The mobile app in particular benefits from explicit URL versions since older app versions in the wild may call older API versions for extended periods.

## Decision
Use URL path versioning for all API endpoints: `/api/v1/`, `/api/v2/`, etc. Every endpoint includes the version prefix from day one. A new version is introduced only for breaking changes — additive changes (new optional fields) do not require a version bump. Deprecated versions communicate their sunset date via a `Deprecation` response header.

## Consequences
- API versions are immediately visible in URLs, logs, documentation, and monitoring dashboards — no ambiguity about which version a client is calling.
- Multiple API versions can coexist in production, allowing clients to migrate at their own pace — critical for the mobile app where users may not update immediately.
- The `Deprecation` header provides a machine-readable signal for clients to detect and act on upcoming version sunsets.
- Additive, non-breaking changes are deployed without a version bump, keeping the version count manageable.
- Maintaining multiple active versions increases backend code surface — old controllers and DTOs must be kept alive until all clients have migrated and the version is sunset.
- Route definitions become slightly more verbose with the `/api/v1/` prefix on every endpoint, but this is a minor cost for the clarity gained.
