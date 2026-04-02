# RBAC.md — Cross-System Role-Based Access Control Rules

This file defines the rules, structure, and standards for access control
across every application in this system.

No permission matrix is defined speculatively.
The matrix in section 9 grows alongside features — one row is added
per action as the feature that introduces it is built.

This file is permanent. It is read at the start of every development
session for any subproject alongside the relevant CLAUDE.md files.
It does not replace the per-app CLAUDE.md RBAC sections — it sits
above them as the system-wide authority.

---

## 1. Core principles

**Deny by default.**
A user has no access to anything until a permission is explicitly granted.
There is no "catch-all" permission. No role inherits access it was not given.

**Least privilege.**
Every role has the minimum permissions required to do its job.
When in doubt, grant less. Expanding permissions is easier than recovering
from a data breach caused by over-permissioning.

**Defense in depth.**
Permission checks happen at multiple layers — never at just one:
- Backend: JWT claim validation on every request
- Backend: Service layer authorization check before business logic executes
- Frontend: Route guard before the page renders
- Frontend: UI element gating (PermissionGate) — controls absent from DOM

A permission check at the frontend alone is never sufficient.
A permission check at the controller alone is never sufficient.
Both are required, always.

**Explicit over implicit.**
Every endpoint, every route, every UI element that requires a permission
must declare that requirement explicitly in code.
Implicit "authenticated means authorized" is never acceptable.

**Tenant isolation is not a permission — it is a constraint.**
A user having permission to "view members" means they can view members
in their own tenant scope only. Tenant isolation is enforced independently
of and in addition to role permissions. These are two separate checks.

---

## 2. System boundary — which app serves which users

Every user in the system belongs to exactly one app boundary.
Crossing boundaries is never permitted — it is rejected at the auth guard.

| App | Serves | Rejects |
|---|---|---|
| `web-nexus` | Internal platform team only | Anyone with a club role or member role |
| `web-pulse` | Club owner + club staff (except trainers) | Trainers, members, internal team |
| `web-coach` | PT trainers + GX instructors | Club staff, members, internal team |
| `web-arena` | Club members only | Staff, trainers, internal team |
| `mobile-arena` | Club members only | Staff, trainers, internal team |

When a user with the wrong role attempts to access an app:
- Return `403 Forbidden` from the backend
- Show a clear message at the frontend auth guard:
  "This app is not for your role. You should use [correct app name]."
- Never redirect silently — always explain why access was denied

---

## 3. Role registry — every role in the system

All roles across all apps. A user has exactly one role per app context.
A trainer who also manages their own club is two separate user accounts
in two separate app contexts — never a single account with combined roles.

### Internal platform roles (web-nexus)

| Role | Code | Description |
|---|---|---|
| Super Admin | `nexus:super-admin` | Full access to everything in Nexus |
| Support Agent | `nexus:support-agent` | Read all org data, use troubleshooting tools, no config writes |
| Integration Specialist | `nexus:integration-specialist` | Read + write integration configs, no org lifecycle actions |
| Read-Only Auditor | `nexus:read-only-auditor` | Read-only access to all data and logs, no writes |

### Club roles (web-pulse)

| Role | Code | Description |
|---|---|---|
| Club Owner | `club:owner` | Full access across all branches of their club |
| Branch Manager | `club:branch-manager` | Full operations within assigned branches only |
| Receptionist | `club:receptionist` | Member check-in, registration, payment collection, renewals |
| Sales Agent | `club:sales-agent` | Lead management, member onboarding, payment collection |

### Trainer roles (web-coach)

| Role | Code | Description |
|---|---|---|
| PT Trainer | `trainer:pt` | Own PT sessions, assigned members, own schedule, own earnings |
| GX Instructor | `trainer:gx` | Own GX classes, own schedule, own earnings |

A trainer can hold both `trainer:pt` and `trainer:gx` simultaneously.
In this case they see all features of both roles.

### Member roles (web-arena, mobile-arena)

| Role | Code | Description |
|---|---|---|
| Member | `member` | Access to their own account data only |

---

## 4. Role code format

Role codes follow this pattern: `<context>:<role-name>`

- `nexus:` — internal platform team roles
- `club:` — club staff roles
- `trainer:` — trainer roles
- `member` — member role (no context prefix — members exist at club level)

Role codes are:
- Stored in the JWT claims as a string array: `"roles": ["club:owner"]`
- Used in backend `@PreAuthorize` or equivalent security annotations
- Used in frontend `PermissionGate` and route guard checks
- Never hardcoded as raw strings outside of the permissions constants file

In every app, role codes are defined in one place:

```
backend:    src/main/kotlin/com/arena/security/Roles.kt
web-nexus:  src/types/permissions.ts
web-pulse:  src/types/permissions.ts
web-coach:  src/types/permissions.ts
web-arena:  src/types/permissions.ts
```

Never use the string `"club:owner"` directly in application code.
Always use the constant: `Roles.CLUB_OWNER` (backend), `ROLES.CLUB_OWNER` (frontend).

---

## 5. Permission structure

A permission is a named action on a named resource.

### Format

```
<resource>:<action>
```

Examples:
```
member:create
member:read
member:update
member:delete
membership:freeze
payment:collect
report:view-financial
integration:configure-zatca
```

### Resource naming

Resources match the domain glossary and the API resource names:
- singular noun
- kebab-case for multi-word: `membership-plan`, `pt-session`, `gx-class`
- Never abbreviate: `membership` not `mem`, `organization` not `org`

### Action naming

Standard actions:
| Action | Meaning |
|---|---|
| `create` | Create a new record |
| `read` | View a record or list |
| `update` | Modify an existing record |
| `delete` | Soft-delete a record |
| `export` | Export data to file |
| `approve` | Approve a pending request |
| `reject` | Reject a pending request |
| `configure` | Change system or integration configuration |
| `impersonate` | Act as another entity for support purposes |
| `view-financial` | Access financial reports and amounts |
| `collect-payment` | Record a payment transaction |
| `issue-refund` | Process a refund |

Non-standard actions (domain-specific verbs) are allowed when none of the
standard actions accurately describe the operation:
```
membership:freeze
membership:unfreeze
membership:transfer
pt-session:mark-attendance
invoice:submit-zatca
```

---

## 6. Scope constraints

Permission alone is not enough for most resources.
Scope constrains what data a permission applies to.

### Scope types

**Global** — applies across all tenants (Nexus roles only)
**Organization** — applies within one organization
**Club** — applies within one club within an organization
**Branch** — applies within specific branches only
**Own** — applies only to the user's own data (trainer's own sessions, member's own account)

### Scope in the JWT

The JWT carries scope claims alongside the role:
```json
{
  "sub": "user-uuid",
  "roles": ["club:branch-manager"],
  "organizationId": "org-uuid",
  "clubId": "club-uuid",
  "branchIds": ["branch-uuid-1", "branch-uuid-2"]
}
```

### Scope enforcement rules

- `club:owner` — scope is the entire club. `branchIds` contains all branch IDs.
- `club:branch-manager` — scope is assigned branches only. `branchIds` lists them.
- `club:receptionist` and `club:sales-agent` — scope is assigned branches only.
- `trainer:pt` and `trainer:gx` — scope is own data only. No branch-level access.
- `member` — scope is own account only. `memberId` claim in JWT.

Scope is enforced on the backend by extracting claims from the JWT.
A `branch-manager` passing a different `branchId` in their request
receives the data for their assigned branches only — the request parameter
can narrow scope but never broaden it beyond what the JWT permits.

---

## 7. Backend enforcement

### JWT validation

Every protected endpoint:
1. Validates the JWT signature and expiry
2. Extracts the role and scope claims
3. Checks the required role for the endpoint
4. Enforces tenant isolation using the `organizationId` claim

This happens in a Spring Security filter — before the controller executes.

### Service layer authorization

Every service method that performs a sensitive operation checks authorization
explicitly — not just "is the user authenticated" but "does this user's role
and scope allow this specific action on this specific resource":

```kotlin
// Example pattern — not implementation
fun freezeMembership(membershipId: UUID, requesterId: UUID) {
    val requester = userRepository.findById(requesterId)
    val membership = membershipRepository.findByPublicId(membershipId)

    // Check role
    check(requester.hasRole(Roles.RECEPTIONIST) || requester.hasRole(Roles.BRANCH_MANAGER)) {
        throw ForbiddenException("freeze-membership", requester.role)
    }

    // Check scope — requester must be in the same branch as the membership
    check(requester.branchIds.contains(membership.branchId)) {
        throw ForbiddenException("branch-scope-violation")
    }

    // Business logic follows...
}
```

### Annotation convention

Every controller method is annotated with its required role:

```kotlin
@PreAuthorize("hasRole('CLUB_RECEPTIONIST') or hasRole('CLUB_BRANCH_MANAGER')")
@PostMapping("/memberships/{id}/freeze")
fun freezeMembership(@PathVariable id: UUID): ResponseEntity<MembershipResponse>
```

The annotation and the service-layer check are both required.
The annotation is fast rejection. The service check is the real guard.

---

## 8. Frontend enforcement

### Route guards

Every route declares its required role in the router configuration.
Unauthorized navigation is caught before the component renders:

```typescript
// Pattern — not implementation
{
  path: '/finance',
  component: FinancePage,
  beforeLoad: ({ context }) => {
    if (!hasRole(context.auth, [ROLES.CLUB_OWNER, ROLES.BRANCH_MANAGER])) {
      throw redirect({ to: '/403' })
    }
  }
}
```

### PermissionGate component

UI elements that require a specific role are wrapped in `<PermissionGate>`.
The element is absent from the DOM for unauthorized users — not hidden with CSS:

```tsx
// Pattern — not implementation
<PermissionGate roles={[ROLES.CLUB_OWNER, ROLES.BRANCH_MANAGER]}>
  <RefundButton />
</PermissionGate>
```

`display: none` is never used to hide privileged UI.
`visibility: hidden` is never used to hide privileged UI.
If the user does not have the role, the element does not exist in the DOM.

### Auth store

The current user's role and permissions are loaded once at app init
and stored in the auth store. They are derived from the JWT claims.

Permission checks in components use the auth store — never re-fetch
the user's role from the API on every render.

When a JWT expires and a new one is issued (via refresh token),
the auth store is updated with the new claims.

---

## 9. The permission matrix

This matrix grows alongside features.
A row is added when a feature introduces a new action.
Never add rows speculatively — only when the feature is being built.

### How to read this matrix

- ✅ = permitted
- ❌ = not permitted
- 🔒 = permitted for own data only (trainer's own sessions, member's own account)
- 🏢 = permitted within assigned branch scope only
- ⚠️ = permitted with additional approval step

### Matrix format

When a new action is added, use this format:

| Action | nexus:super-admin | nexus:support-agent | nexus:integration-specialist | nexus:read-only-auditor | club:owner | club:branch-manager | club:receptionist | club:sales-agent | trainer:pt | trainer:gx | member |
|---|---|---|---|---|---|---|---|---|---|---|---|
| _(rows added per feature)_ | | | | | | | | | | | |

### Current entries

No rows exist yet. The first row will be added when the first
feature PLAN.md is written and implemented. Each PLAN.md must
include a "Permissions" section that specifies which roles can
perform each new action, and the matrix is updated as part of
that feature's implementation PR.

---

## 10. Adding a new permission — the process

When a feature introduces a new action that requires access control,
follow this process before writing any code:

**Step 1 — Name the permission**
Use the `<resource>:<action>` format. Check that the resource name
matches the domain glossary and the API resource name.
Check that no existing permission already covers this action.

**Step 2 — Define which roles can perform it**
Answer for every role in section 3:
- Can this role perform this action? (yes / no / own-data-only / branch-scope-only)
- If yes, are there conditions? (requires approval, requires specific status, etc.)

**Step 3 — Add a row to the matrix in section 9**
One row per action. Fill every column — no blanks.

**Step 4 — Add the permission constant**
Add to `Roles.kt` (backend) and `permissions.ts` (relevant frontend app).
Never use the string directly in application code.

**Step 5 — Implement the check at both layers**
Backend: service method check + controller annotation.
Frontend: route guard (if route-level) + PermissionGate (if element-level).

**Step 6 — Write a test for the permission**
Every permission has at least two tests:
- Authorized role can perform the action
- Unauthorized role receives 403

---

## 11. Special cases

### Impersonation (web-nexus only)

Impersonation is not a regular permission — it is a privileged support operation.

- Only `nexus:super-admin` and `nexus:support-agent` can initiate impersonation.
- Impersonation does not grant admin rights within the impersonated org.
  It grants the ability to see what the org's `club:owner` would see.
- Every action taken during impersonation is audit-logged against the
  Nexus user who initiated it, not the impersonated org.
- Impersonation sessions expire after 30 minutes.
- Impersonation is always explicit — there is no silent or automatic impersonation.

### Cross-branch access (club:owner only)

`club:owner` is the only club role that can access data across all branches.
All other club roles are branch-scoped.

If a `club:owner` delegates management to a person who needs multi-branch
access, the correct solution is to assign them `club:owner` role —
not to widen the `club:branch-manager` role definition.

### Trainer data visibility

Trainers can only see:
- Their own schedule and sessions
- Members who have an active PT package assigned to them
- Their own GX classes and enrolled members
- Their own earnings

A trainer cannot see:
- Other trainers' schedules, members, or earnings
- Any financial data beyond their own commission
- Member payment history or membership financial details
- Any club staff data

This is enforced at the backend by filtering all trainer queries
by `trainerId = currentUserId` — not just by role check.

### Member data isolation

A member can only see their own data.
The backend derives the member's identity from the JWT `sub` claim.
There is no API parameter that allows a member to request another member's data.
Any request that attempts to do so returns `403`, not `404`.

---

## 12. Audit logging for permission events

Every permission-sensitive action is audit-logged regardless of outcome.

Logged on success:
- Who performed the action (userId, role)
- What action was performed (permission code)
- On which resource (resourceType, resourceId)
- When (timestamp UTC)
- From which app (X-Client-App header)
- Tenant context (organizationId, branchId)

Logged on failure (403):
- Who attempted the action
- What they attempted
- Why it was denied (role insufficient / scope violation / tenant mismatch)
- When and from which app

Audit logs are append-only and immutable.
No role — including `nexus:super-admin` — can delete or modify audit logs.

---

## 13. Session and token rules

- Access token lifetime: 15 minutes (short — forces regular refresh)
- Refresh token lifetime: 7 days
- Refresh tokens are stored in platform secure storage
  (EncryptedSharedPreferences on Android, Keychain on iOS,
  memory-only on web apps — no localStorage)
- When a user's role changes, the change takes effect on their next login.
  The current session retains the role it was issued with.
- When a user is suspended or terminated, their refresh tokens are
  invalidated server-side immediately. The access token remains valid
  until it expires (maximum 15 minutes) — this is an acceptable window
  given the short access token lifetime.
- Logging out invalidates the refresh token server-side and clears
  all token storage client-side.

---

## 14. What never bypasses RBAC

No code path, no matter how "internal" or "temporary", bypasses RBAC.

- Background jobs and scheduled tasks authenticate with a service account
  that has a specific role. They do not run as a super-user with no role.
- Database migrations run as a migration user with schema-only permissions.
  The migration user cannot read or write application data.
- Admin scripts run by the internal team authenticate through Nexus,
  not through a backdoor database connection.
- "Debug mode" or "dev environment" does not disable permission checks.
  RBAC is enforced in all environments including local development.
- There is no master password, no God-mode endpoint, and no
  "skip auth for testing" flag in production code.
