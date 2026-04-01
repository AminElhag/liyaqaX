# ADR-0003 — Stateless JWT authentication (no server-side sessions)

## Status
Accepted

## Context
The platform serves five distinct client applications (four web apps and one mobile app) from a single backend API. A session-based authentication model would require server-side session storage, sticky sessions or a shared session store, and would complicate horizontal scaling of the backend. The system enforces RBAC with role-specific claims that differ per app (Nexus has internal roles, Pulse has club roles, Coach has trainer roles, Arena has a single member role). OAuth2 with an external identity provider was considered but deferred — the initial architecture uses self-issued JWTs with the option to migrate to OAuth2 later. The public-facing Arena app stores the JWT in memory only (not localStorage) to mitigate XSS-based token theft.

## Decision
Use stateless JWT authentication for all API access. No server-side sessions are maintained. The JWT contains the user's identity, role, tenant scope (organizationId, clubId, branchId where applicable), and expiry. The backend extracts the role from JWT claims on every request — never from a session or request body. Token signature and expiry are validated on every request. All endpoints are secured by default (deny-by-default); public paths are explicitly permitted.

## Consequences
- The backend is fully stateless and horizontally scalable — no shared session store needed.
- A single authentication mechanism serves all five client apps with role claims tailored to each app's RBAC model.
- Role changes take effect on next login, not mid-session — acceptable for this domain but means a revoked user retains access until token expiry.
- Token revocation before expiry is not natively supported by stateless JWTs — a token blacklist or short expiry with refresh tokens is needed for forced logout scenarios.
- JWT secret rotation requires coordinated deployment — all running instances must accept both old and new secrets during rotation.
- The mobile app stores the refresh token in platform secure storage (Android Keystore / iOS Keychain); web-arena stores the JWT in memory only, requiring re-login on page refresh.
