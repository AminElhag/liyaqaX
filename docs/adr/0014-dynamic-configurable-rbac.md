# ADR-0014 — Dynamic Configurable RBAC with Role-ID JWT Claims

## Status
Accepted

## Context

The initial auth implementation used fixed system-defined role strings
embedded in the JWT (e.g., `club:receptionist`, `club:owner`). This
approach was discovered to be insufficient for the following reasons:

1. **Platform-level roles must be configurable by Nexus super-admins.**
   The internal team needs to define and modify what Nexus roles can do
   without a code deployment.

2. **Club-level roles are defined per club, not per system.**
   One club may have "Owner", "Manager", "HR". Another may have
   "Director", "Operations Lead", "Front Desk". The system cannot
   pre-define these — each club configures its own role structure.

3. **Permissions are fine-grained and configurable.**
   Permissions are specific action codes (`staff:create`,
   `payment:collect`, `report:revenue:view`). Which permissions
   belong to which role is configured by the club or platform admin —
   not hardcoded in the application.

4. **Staff creation and management follows permission flow.**
   Not all staff can create other staff. The ability to perform any
   action depends on the permissions assigned to the user's role
   within their specific club.

The system needs a fully dynamic RBAC model that supports:
- Custom role names per club
- Custom permission assignments per role
- Immediate effect of permission changes
- Minimal performance overhead on every request

Three JWT payload options were evaluated:
- **Option A**: Carry `roleId` only — lookup permissions per request
- **Option B**: Carry full permission list in token
- **Option C**: Carry both `roleId` + cached permissions (hybrid)

---

## Decision

### 1. Role model

Roles are fully dynamic and stored in the database.

**Platform roles** (scoped to Nexus):
- Defined and managed by `nexus:super-admin`
- Apply to internal team members only
- Stored in the `roles` table with `scope = 'platform'`

**Club roles** (scoped per club):
- Defined and managed by the club owner or permitted staff
- Each club has its own independent set of roles
- Stored in the `roles` table with `scope = 'club'`
  and scoped to `organization_id` + `club_id`

When a new club is created, a default set of roles is
pre-configured automatically by the system:
- `Owner` — all permissions
- `Branch Manager` — branch-level operations
- `Receptionist` — member management, payments
- `Sales Agent` — leads, onboarding, payments

These defaults are a starting point. The club owner can
rename, modify, or delete them (except Owner).

### 2. Permission model

Permissions are fine-grained action codes in the format:
`<resource>:<action>`

Examples:
```
staff:create
staff:read
staff:update
staff:delete
member:create
member:read
payment:collect
payment:refund
report:revenue:view
branch:read
gx-class:manage
pt-session:manage
```

The full set of permission codes is defined by the system
(developers add new codes when new features are built).
Which permissions are assigned to which role is configured
by admins — not hardcoded.

### 3. JWT payload

Use **Option A — roleId only**:

```json
{
  "sub": "user-public-id-uuid",
  "roleId": "role-public-id-uuid",
  "scope": "club",
  "organizationId": "org-uuid",
  "clubId": "club-uuid",
  "branchIds": ["branch-uuid-1", "branch-uuid-2"],
  "iat": 1704067200,
  "exp": 1704068100
}
```

For platform users (Nexus):
```json
{
  "sub": "user-public-id-uuid",
  "roleId": "role-public-id-uuid",
  "scope": "platform",
  "iat": 1704067200,
  "exp": 1704068100
}
```

For members:
```json
{
  "sub": "user-public-id-uuid",
  "roleId": "role-public-id-uuid",
  "scope": "member",
  "memberId": "member-uuid",
  "organizationId": "org-uuid",
  "clubId": "club-uuid",
  "branchId": "branch-uuid",
  "iat": 1704067200,
  "exp": 1704068100
}
```

Rationale for Option A over Option B:
- Fine-grained permissions result in large token sizes if embedded
- Permission changes must take effect immediately — embedded
  permissions require waiting for token expiry (up to 15 minutes)
- Redis caching eliminates the DB-hit concern

Rationale for Option A over Option C:
- Hybrid model has two sources of truth that can diverge
- Harder to reason about during debugging and auditing
- Adds complexity without meaningful benefit given Redis is available

### 4. Permission resolution with Redis caching

Every authenticated request resolves permissions via:

```
1. Extract roleId from JWT
2. Check Redis: GET "role_permissions:{roleId}"
3. Cache hit  → use cached permission set (TTL: 5 minutes)
4. Cache miss → SELECT permissions WHERE role_id = ?
               → SET "role_permissions:{roleId}" EX 300
               → use fetched permission set
5. Authorize the request against the resolved permissions
```

Cache invalidation:
- When role permissions are updated → DEL "role_permissions:{roleId}"
- When a role is deleted → DEL "role_permissions:{roleId}"
- TTL of 5 minutes is a safety net — explicit invalidation is primary

### 5. Migration from fixed-role auth

The existing fixed-role implementation is removed entirely.
The following changes are made:

**JWT changes:**
- Remove `"role": "club:receptionist"` claim
- Add `"roleId": "uuid"` claim
- Add `"scope": "club" | "platform" | "member"` claim

**Backend changes:**
- Remove `Roles.kt` fixed role constants
- Add `roles` table, `permissions` table,
  `role_permissions` join table
- Add `PermissionService` — resolves permissions from roleId via Redis
- Add `PermissionEvaluator` — replaces `@PreAuthorize` role checks
  with permission-based checks
- Update `JwtAuthFilter` — extract roleId and scope instead of role string
- Update `SecurityConfig` — remove role-based rules,
  use permission evaluator instead

**Seed data changes:**
- Remove hardcoded role strings from seeded users
- Create default roles in seed data
- Assign users to roles by roleId

### 6. Permission check pattern in code

Replace role-based annotations:
```kotlin
// BEFORE — removed
@PreAuthorize("hasRole('CLUB_RECEPTIONIST')")

// AFTER — permission-based
@PreAuthorize("hasPermission(null, 'staff:create')")
```

Or via programmatic check in service layer:
```kotlin
permissionService.requirePermission(currentUser, "staff:create")
```

---

## Database schema additions

```
roles
  id, public_id, created_at, updated_at, deleted_at
  name_ar VARCHAR(100) NOT NULL
  name_en VARCHAR(100) NOT NULL
  scope   VARCHAR(20)  NOT NULL  -- 'platform' | 'club' | 'member'
  organization_id BIGINT NULL FK → organizations(id)
  club_id         BIGINT NULL FK → clubs(id)
  is_system       BOOLEAN NOT NULL DEFAULT false
  -- is_system = true means created by platform, not deletable by club

permissions
  id, public_id, created_at, updated_at
  code        VARCHAR(100) NOT NULL UNIQUE
  -- e.g. 'staff:create', 'payment:collect'
  resource    VARCHAR(50)  NOT NULL
  -- e.g. 'staff', 'payment', 'report'
  action      VARCHAR(50)  NOT NULL
  -- e.g. 'create', 'read', 'collect'
  description_ar TEXT NULL
  description_en TEXT NULL
  -- No deleted_at — permissions are never soft deleted
  -- they are removed from roles instead

role_permissions
  id, created_at
  role_id       BIGINT NOT NULL FK → roles(id)
  permission_id BIGINT NOT NULL FK → permissions(id)
  UNIQUE (role_id, permission_id)

user_roles
  id, created_at
  user_id BIGINT NOT NULL FK → users(id)
  role_id BIGINT NOT NULL FK → roles(id)
  -- A user has exactly one active role per club context
  -- enforced at application layer
```

---

## Consequences

### Benefits
- Fully flexible role and permission model per club
- Permission changes take effect within 5 minutes (Redis TTL)
  or immediately on cache invalidation
- Fine-grained control: `staff:create` can be granted
  independently of `staff:delete`
- Clubs can model their own org structure without system changes
- Platform team can reconfigure Nexus roles without deployment

### Tradeoffs
- More complex implementation than fixed roles
- Every request requires a Redis lookup (fast, but adds a dependency)
- Default role setup must be seeded correctly for each new club
- Testing is more complex — must seed roles and permissions in tests
- The permission code list must be maintained by developers
  when new features are added

### Constraints
- The `Owner` system role for each club cannot be deleted
  (enforced at application layer — `is_system = true`)
- Permission codes are defined by developers — clubs configure
  which permissions belong to which role, but cannot invent
  new permission codes
- A user must always have exactly one role per context
  (one platform role OR one club role — never both)
- Redis is now a required dependency — the application
  will not start without a Redis connection

---

## Impact on existing plans

### Auth foundation (already merged)
- JWT payload changes: remove `role`, add `roleId` + `scope`
- JwtService: update token generation and parsing
- JwtAuthFilter: extract `roleId` and `scope` instead of `role`
- Update seed data: create roles, assign users to roles by ID

### All future plans
- Replace `@PreAuthorize("hasRole(...)")` with
  `@PreAuthorize("hasPermission(null, 'resource:action')")`
- Every new feature adds its permission codes to the
  `permissions` seed table
- Every new feature adds its permission codes to the
  RBAC matrix in `docs/rbac.md`
