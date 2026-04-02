# RBAC.md — Cross-System Role-Based Access Control Rules

This file defines the access control rules that apply across every
application in the monorepo. It is the single source of truth for
which roles exist, what they can do, and how enforcement works.

This file is read at the start of every development session that
touches authentication, authorization, or role-dependent behavior.

---

## 1. Core principles

- **Deny by default** — every endpoint, route, and UI element is restricted
  unless explicitly permitted.
- **Least privilege** — each role has the minimum permissions needed.
  No role gets "everything" as a shortcut.
- **Defense in depth** — frontend and backend both enforce RBAC independently.
  The frontend check is UX enforcement. The backend check is the real gate.
- **Explicit over implicit** — permissions are declared, never inferred.
  "Admin" does not implicitly mean "can do everything" — each permission
  is listed.
- **Tenant isolation is separate from role permission** — a valid role
  does not grant cross-tenant access. Tenant scoping and RBAC are
  two independent checks that both must pass.

---

## 2. System boundary

Each application serves a specific audience and rejects all others.

| App | Serves | Rejects |
|---|---|---|
| web-nexus | Internal platform team | Club roles, trainer roles, member role |
| web-pulse | Club owner + staff (not trainers) | Trainers, members, internal team |
| web-coach | PT trainers + GX instructors | Club staff, members, internal team |
| web-arena | Club members only | Staff, trainers, internal team |
| mobile-arena | Club members only | Staff, trainers, internal team |

A user who authenticates with the wrong role for the app they are
accessing receives `403 Forbidden` — not a redirect to the "correct" app.

---

## 3. Role registry

### Internal platform roles (web-nexus)

| Role | Description |
|---|---|
| `nexus:super-admin` | Full access to all platform operations. Can create organizations, manage integrations, impersonate users. |
| `nexus:support-agent` | Read all data, troubleshoot issues, reset passwords. Cannot modify platform configuration. |
| `nexus:integration-specialist` | Manage integration configurations (ZATCA, Qoyod) only. No access to user management or org creation. |
| `nexus:read-only-auditor` | Read-only access to all data. Cannot perform any write operation. |

### Club roles (web-pulse)

| Role | Description |
|---|---|
| `club:owner` | Full access to all branches within their club. Can manage staff, view financials, configure plans. |
| `club:branch-manager` | Full operations within assigned branches only. Cannot access other branches or club-level config. |
| `club:receptionist` | Check-in members, process registrations, collect payments, handle renewals. Branch-scoped. |
| `club:sales-agent` | Manage leads, onboard new members, process initial payments. Branch-scoped. |

### Trainer roles (web-coach)

| Role | Description |
|---|---|
| `trainer:pt` | View own PT sessions, assigned members, own schedule. Cannot see other trainers' data. |
| `trainer:gx` | View own GX classes, own schedule. Cannot see other instructors' data. |

A trainer can hold both `trainer:pt` and `trainer:gx` simultaneously.

### Member roles (web-arena, mobile-arena)

| Role | Description |
|---|---|
| `member` | View and manage own account, memberships, bookings, and session history only. |

---

## 4. Role code format

Pattern: `<context>:<role-name>`

Constants are defined in:
- **Backend**: `src/main/kotlin/com/liyaqa/security/Roles.kt`
- **Frontend**: `src/types/permissions.ts` in each app

Never use raw role strings in application code. Always reference
the constant. This ensures typos are caught at compile time and
role renames propagate automatically.

---

## 5. Permission structure

Format: `<resource>:<action>`

### Standard actions

| Action | Meaning |
|---|---|
| `create` | Create a new resource |
| `read` | View a resource or list |
| `update` | Modify an existing resource |
| `delete` | Soft-delete a resource |
| `export` | Export data to file (CSV, PDF) |
| `approve` | Approve a pending action (freeze, refund) |
| `reject` | Reject a pending action |
| `configure` | Modify system configuration |
| `impersonate` | Act as another user |
| `view-financial` | View financial data (revenue, commissions) |
| `collect-payment` | Process a payment from a member |
| `issue-refund` | Issue a refund to a member |

---

## 6. Scope constraints

Roles operate within a defined scope. A permission check passes
only if the user has the permission AND is within scope.

| Scope | Who | Data access |
|---|---|---|
| Global | Nexus roles only | All organizations, all data |
| Organization | `club:owner` | All clubs and branches within one organization |
| Club | `club:owner` | All branches within one club |
| Branch | `club:branch-manager`, `club:receptionist`, `club:sales-agent` | Specific branches only |
| Own | `trainer:pt`, `trainer:gx`, `member` | Own data only |

### JWT scope claims

```json
{
  "sub": "user-uuid",
  "roles": ["club:branch-manager"],
  "organizationId": "org-uuid",
  "clubId": "club-uuid",
  "branchIds": ["branch-uuid-1", "branch-uuid-2"]
}
```

- Nexus roles have no `organizationId`, `clubId`, or `branchIds` —
  their scope is global.
- `club:owner` has `organizationId` and `clubId` but no `branchIds` —
  they can access all branches.
- Branch-scoped roles have all three. `branchIds` is an array because
  a user can be assigned to multiple branches.
- `member` has `organizationId` and `clubId` only. Their data access
  is further restricted to their own records via `sub` claim.

---

## 7. Backend enforcement

Authorization is enforced at two layers in the backend:

### Layer 1 — Spring Security filter chain

- JWT is validated on every request (signature, expiry, claims).
- `@PreAuthorize` annotation on every controller method declares
  the required role(s).
- This is the first line of defense.

### Layer 2 — Service layer

- Every service method that performs a write or reads sensitive data
  verifies the caller's role and scope before executing business logic.
- This is the second line of defense. If a controller annotation
  is misconfigured, the service layer catches it.
- Both checks are required. Neither alone is sufficient.

### Enforcement rules

- Every controller method has `@PreAuthorize`. No exceptions.
- The service layer extracts `organizationId` from the JWT —
  never from the request body.
- A query that touches tenant-scoped data always includes the
  tenant filter derived from the JWT.
- `403 Forbidden` is returned when the user is authenticated
  but lacks the required role. `401 Unauthorized` is returned
  when no valid token is present.

---

## 8. Frontend enforcement

Frontend RBAC is UX enforcement only. It improves the user experience
by hiding actions the user cannot perform, but it does NOT protect data.

### Implementation per app

- **Route guards**: enforced in the router's `beforeLoad` hook.
  An unauthorized navigation redirects to a dedicated 403 page.
- **PermissionGate component**: wraps UI elements that require
  a specific permission. Unauthorized elements are removed from
  the DOM entirely — never hidden with CSS, never disabled.
- **Auth store**: holds the user's role and derived permissions,
  populated from JWT claims on login.
- **permissions.ts**: the role-to-permission map is the single
  source of truth for each app's RBAC decisions.

### Rules

- Never show a disabled button for an action the user cannot perform.
  Remove it from the DOM.
- Never show an error message after the user clicks something they
  shouldn't have been able to click. Prevent the click.
- The frontend must gracefully handle a `403` response from the backend
  (in case the frontend check was bypassed or stale).

---

## 9. Permission matrix

Rows are added per feature alongside implementation.
No speculative rows — a row is added only when the feature
that requires the permission is built.

| Action | nexus:super-admin | nexus:support-agent | nexus:integration-specialist | nexus:read-only-auditor | club:owner | club:branch-manager | club:receptionist | club:sales-agent | trainer:pt | trainer:gx | member |
|---|---|---|---|---|---|---|---|---|---|---|---|
| auth:login | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| auth:refresh | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| auth:logout | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

---

## 10. Adding a new permission — the process

Every new permission follows this checklist:

1. **Name the permission** — `<resource>:<action>` format.
2. **Define which roles can perform it** — fill in the matrix row.
3. **Add the row to the matrix** in section 9 of this file.
4. **Add the constant** to `Roles.kt` (backend) and `permissions.ts`
   (relevant frontend app).
5. **Implement the backend check** — `@PreAuthorize` on the controller,
   role verification in the service layer.
6. **Implement the frontend check** — route guard and/or `PermissionGate`.
7. **Write tests** — at minimum: one test for an authorized role succeeding,
   one test for an unauthorized role receiving `403`.

---

## 11. Special cases

### Impersonation

- Only `nexus:super-admin` and `nexus:support-agent` can impersonate.
- Impersonation creates a temporary token with the target user's role
  and scope, plus an `impersonatedBy` claim.
- Full audit trail is required: who impersonated whom, when, and what
  actions were taken during impersonation.
- Impersonation tokens expire after 30 minutes. Not renewable.
- Impersonation is never silent — the UI displays a banner.

### Cross-branch access

- `club:owner` can access all branches within their club.
- `club:branch-manager` can only access their assigned branches.
  A query for data in an unassigned branch returns `403`.

### Trainer data isolation

- Trainers see only their own sessions, members, and schedule.
- Enforced by filtering on `trainerId` derived from the JWT `sub` claim.
- A trainer cannot see another trainer's data even within the same branch.

### Member data isolation

- Members see only their own account, memberships, bookings, and history.
- Enforced by filtering on `memberId` derived from the JWT `sub` claim.
- A member cannot see another member's data.

---

## 12. Audit logging

Every permission-sensitive action is logged with the following fields:

| Field | Source |
|---|---|
| userId | JWT `sub` claim |
| role | JWT `roles` claim |
| permission | The permission code checked |
| resourceType | The type of resource acted upon |
| resourceId | The public ID of the resource |
| timestamp | UTC server time |
| clientApp | `X-Client-App` request header |
| organizationId | JWT claim or resource scope |
| branchId | JWT claim or resource scope |

Audit logs are append-only and immutable. They are never soft-deleted,
hard-deleted, or modified after creation.

---

## 13. Session and token rules

| Token | Lifetime | Storage | Revocation |
|---|---|---|---|
| Access token | 15 minutes | Memory (frontend) | Expires naturally |
| Refresh token | 7 days | Secure storage | Server-side invalidation on logout |

### Rules

- Role changes take effect on the user's next login. The current
  session reflects the role it was issued with.
- Logout invalidates the refresh token server-side immediately.
  The access token remains valid until it expires (max 15 minutes).
- A stolen refresh token can be invalidated by the user logging out
  from any device, or by an admin revoking the user's sessions.

---

## 14. What never bypasses RBAC

- **Background jobs** use service account roles with explicit, minimal
  permissions. They do not run as "system" with unlimited access.
- **Migration scripts** have schema-only database permissions.
  They cannot read or write application data.
- **Admin scripts** authenticate through Nexus with a named user.
  No anonymous admin access.
- **Dev environment** enforces RBAC identically to production.
  There is no debug bypass, no dev-only God mode.
- **No master password.** No God-mode endpoint. No backdoor.
  Every action is authenticated and authorized through the
  standard RBAC pipeline.
