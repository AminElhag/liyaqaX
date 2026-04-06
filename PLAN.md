# PLAN.md — Role Management UI

## Status
Ready for implementation

## Branch
feat/role-management

## Goal
Complete the RBAC system end-to-end by adding a UI for managing roles and
permissions. Platform admins (Super Admin) can create and edit platform-scoped
roles in web-nexus. Club owners can create and edit club-scoped roles in
web-pulse, then assign those roles to their staff members. All role and
permission data already exists in the database — this plan adds the management
API surface and wires it into both frontends.

## Context
- `Role`, `Permission`, `RolePermission`, `UserRole` entities already exist
  and are fully seeded.
- RBAC is fully dynamic — `roleId` in JWT, Redis-cached permission resolution.
  Changing a role's permissions takes effect within 5 minutes (Redis TTL).
- `role:create/read/update/delete` permission codes already exist and are
  assigned to Super Admin (platform) and Owner (club).
- Redis permission cache key: `role_permissions:{roleId}` — must be
  invalidated when a role's permissions change.
- `ddl-auto: create-drop` in dev — no Flyway migration needed (no new tables).
- web-nexus already has the sidebar, PermissionGate, and patterns needed for
  a new "Roles" section.
- web-pulse already has the member registration wizard and settings patterns
  to inform a staff role assignment UI.

---

## Scope — what this plan covers

### Backend
- [ ] `RoleNexusController.kt` — platform role CRUD (scope: platform roles only)
- [ ] `RolePermissionNexusController.kt` — assign/remove permissions on a role
- [ ] `RolePulseController.kt` — club role CRUD (scope: club roles only)
- [ ] `RolePermissionPulseController.kt` — assign/remove permissions on a club role
- [ ] `StaffRoleAssignmentPulseController.kt` — assign a role to a staff member
- [ ] `RoleManagementService.kt` — shared service: create/update/delete roles,
  assign/remove permissions, Redis cache invalidation on every write
- [ ] DTOs for all role management endpoints
- [ ] Unit tests: `RoleManagementServiceTest`
- [ ] Integration tests: `RoleNexusControllerTest`, `RolePulseControllerTest`
- [ ] Flyway V11 — no new tables; add NOT NULL constraint guard if needed
  (check if any schema fix is required — if not, skip V11 entirely)

### Frontend
- [ ] web-nexus: Roles section — list platform roles, view/edit permissions,
  create new platform role
- [ ] web-pulse: Roles section — list club roles, view/edit permissions,
  create new club role, assign role to staff member

---

## Out of scope — do not implement in this plan
- Deleting roles that have active staff assigned (guard exists — return 409)
- Role cloning / templates (future plan)
- Permission group labels / categories UI (permissions shown as flat list)
- Assigning roles to trainers or members via UI (trainer roles are fixed;
  member role is system-assigned on registration — not user-configurable)
- Audit logging for role changes — `AuditService` calls will be added
  following the same pattern established in the audit logging plan

---

## Decisions already made

- **Two separate controller pairs**: `RoleNexusController` handles platform
  roles (`scope = "platform"`, `roleScope = "platform"`). `RolePulseController`
  handles club roles (`scope = "club"`, `roleScope = "club"`). A club owner
  cannot see or touch platform roles and vice versa. The service layer enforces
  this via `role.scope` check.

- **Redis invalidation on every role permission write**: `RoleManagementService`
  calls `redisTemplate.delete("role_permissions:${roleId}")` immediately after
  any permission assignment or removal. Next request by any user with that role
  will re-fetch from DB and re-cache. TTL is 5 minutes but invalidation is
  immediate — no 5-minute lag for permission changes.

- **Cannot delete a role with active assignments**: `DELETE /roles/{id}` checks
  `UserRole` table for any active (non-deleted) assignment. Returns 409
  "Cannot delete role: X staff members are currently assigned to this role."
  Caller must reassign staff first.

- **Cannot remove a permission that would leave a role with zero permissions**:
  Return 422 "A role must have at least one permission." Prevents accidentally
  locking out all users of a role.

- **Seeded roles are protected from deletion**: Super Admin, Owner, Member,
  and other system-seeded roles have `isSystem = true` on the `Role` entity.
  `DELETE` returns 409 "System roles cannot be deleted." `isSystem` field
  added to `Role` entity (boolean, default false, set to true for seeded roles
  in DevDataLoader).

- **Permission list is read-only from frontend**: The set of available
  `Permission` records is fixed (seeded). No UI to create new permission codes.
  Frontend fetches `GET /permissions` to populate the checkboxes — all known
  permission codes in the system.

- **Staff role reassignment in web-pulse**: `PATCH /api/v1/staff/{id}/role`
  updates the `UserRole` record for that staff member. Only one active role
  per staff member at a time. Changing the role immediately invalidates their
  Redis cache entry.

- **Audit logging**: every role create/update/delete and permission
  assignment/removal writes an audit record via `AuditService`. New action
  codes: `ROLE_CREATED`, `ROLE_UPDATED`, `ROLE_DELETED`,
  `ROLE_PERMISSION_ADDED`, `ROLE_PERMISSION_REMOVED`, `STAFF_ROLE_ASSIGNED`.

---

## Entity design

### Role (modify existing)
Add one field:
```
is_system    BOOLEAN NOT NULL DEFAULT false
```
Set `isSystem = true` for all seeded roles in DevDataLoader.

No Flyway migration needed — `ddl-auto: create-drop` handles in dev.
For production: add to V11 if needed, otherwise the column will be added by
Hibernate on next deploy (depending on ddl-auto setting in prod — confirm before
shipping).

### No other entity changes.

---

## API endpoints

### RoleNexusController — `/api/v1/nexus/roles` (platform roles)

```
GET    /api/v1/nexus/roles              list all platform-scoped roles
GET    /api/v1/nexus/roles/{id}         role detail with permissions list
POST   /api/v1/nexus/roles              create platform role
PATCH  /api/v1/nexus/roles/{id}         update role name/description
DELETE /api/v1/nexus/roles/{id}         delete role (rule: no active assignments, not system)
```

### RolePermissionNexusController — `/api/v1/nexus/roles/{id}/permissions`

```
GET    /api/v1/nexus/roles/{id}/permissions         current permissions on role
PUT    /api/v1/nexus/roles/{id}/permissions         replace full permission set (idempotent)
POST   /api/v1/nexus/roles/{id}/permissions/{permId}    add single permission
DELETE /api/v1/nexus/roles/{id}/permissions/{permId}    remove single permission
```

### PermissionNexusController — `/api/v1/nexus/permissions`

```
GET    /api/v1/nexus/permissions        list all available permission codes (read-only)
```

### RolePulseController — `/api/v1/roles` (club roles)

```
GET    /api/v1/roles              list club-scoped roles for this club
GET    /api/v1/roles/{id}         role detail with permissions
POST   /api/v1/roles              create club role
PATCH  /api/v1/roles/{id}         update role name/description
DELETE /api/v1/roles/{id}         delete role (rule: no active assignments, not system)
```

### RolePermissionPulseController — `/api/v1/roles/{id}/permissions`

```
GET    /api/v1/roles/{id}/permissions
PUT    /api/v1/roles/{id}/permissions           replace full permission set
POST   /api/v1/roles/{id}/permissions/{permId}  add single permission
DELETE /api/v1/roles/{id}/permissions/{permId}  remove single permission
```

### PermissionPulseController — `/api/v1/permissions`

```
GET    /api/v1/permissions        list all permission codes (for checkbox UI)
```

### StaffRoleAssignmentPulseController — `/api/v1/staff/{id}/role`

```
PATCH  /api/v1/staff/{id}/role    reassign staff member to a different role
```

Required permissions:
- Nexus role endpoints: `role:create`, `role:read`, `role:update`, `role:delete`
- Pulse role endpoints: same codes, scoped to club JWT
- Staff role assignment: `staff:update`

---

## Request / Response shapes

### RoleListItemResponse
```json
{
  "id": "uuid",
  "name": "string",
  "description": "string | null",
  "scope": "platform | club",
  "isSystem": false,
  "permissionCount": 12,
  "staffCount": 3
}
```

### RoleDetailResponse
```json
{
  "id": "uuid",
  "name": "string",
  "description": "string | null",
  "scope": "platform | club",
  "isSystem": false,
  "permissions": [
    { "id": "uuid", "code": "member:create", "description": "string | null" }
  ],
  "staffCount": 3
}
```

### CreateRoleRequest
```json
{
  "name": "string (required, unique within scope+club)",
  "description": "string (optional)"
}
```

### UpdateRoleRequest
```json
{
  "name": "string (optional)",
  "description": "string (optional)"
}
```

### PermissionResponse
```json
{
  "id": "uuid",
  "code": "member:create",
  "description": "string | null"
}
```

### UpdateRolePermissionsRequest (PUT — full replace)
```json
{
  "permissionIds": ["uuid", "uuid", "uuid"]
}
```

### AssignStaffRoleRequest
```json
{
  "roleId": "uuid"
}
```

---

## Business rules — enforce in service layer

1. **Scope isolation**: platform roles are only visible/editable via nexus
   endpoints by platform-scope JWT. Club roles are only visible/editable via
   pulse endpoints by club-scope JWT. A club owner cannot see platform roles
   and vice versa. Service checks `role.scope` matches caller scope.

2. **Cannot delete a role with active staff**: check `UserRole` for any
   assignment where `role.id = this role` and staff member is not deleted.
   Return 409 with count of assigned staff.

3. **Cannot delete a system role**: `role.isSystem = true` → return 409
   "System roles cannot be deleted."

4. **Cannot remove last permission**: a role must always have at least one
   permission. Return 422 if removing a permission would leave zero.

5. **Role name unique within scope + club**: platform role names must be
   unique across all platform roles. Club role names must be unique within
   the same club. Return 409 "A role with this name already exists."

6. **Redis cache invalidation on permission change**: after any
   `PUT/POST/DELETE /roles/{id}/permissions`, call
   `redisTemplate.delete("role_permissions:${role.publicId}")` before
   returning. This is mandatory — not optional.

7. **Staff role reassignment invalidates cache**: `PATCH /staff/{id}/role`
   updates `UserRole`, then invalidates Redis for the old role AND the new
   role: `redisTemplate.delete("role_permissions:${oldRoleId}")` and
   `redisTemplate.delete("role_permissions:${newRoleId}")`.

8. **Club role must belong to same club**: when a pulse endpoint modifies
   a role, verify `role.clubId == JWT clubId`. Return 403 if not.

9. **PUT permissions is idempotent**: full replace — compute the diff
   (add new, remove old), persist, invalidate cache once. Returns the
   updated permissions list.

10. **Audit every write**: every create/update/delete/permission-change
    calls `AuditService.log()` with the appropriate new action code.
    New codes: `ROLE_CREATED`, `ROLE_UPDATED`, `ROLE_DELETED`,
    `ROLE_PERMISSION_ADDED`, `ROLE_PERMISSION_REMOVED`, `STAFF_ROLE_ASSIGNED`.

---

## Seed data updates

Update `DevDataLoader.kt`:
- Set `isSystem = true` on all seeded roles:
  Super Admin, Support Agent, Integration Specialist, Read-Only Auditor,
  Owner, Branch Manager, Receptionist, Sales Agent, PT Trainer, GX Instructor,
  Member.

No new roles or permissions are seeded.

---

## Frontend additions

### web-nexus — Roles section

**Nav item**: "Roles" added to sidebar (gated by `role:read`).

**`/roles` — Platform Roles List**
Table: role name, description, permission count, staff count, system badge.
"New Role" button (gated by `role:create`).
System roles have a lock icon — edit is allowed (name/description), delete is not.
Click row → role detail.

**`/roles/$roleId` — Role Detail**
Role name + description (inline edit gated by `role:update`).
Permissions section: full list of all permissions as checkboxes, grouped by
domain prefix (member:*, gx:*, pt:*, etc.).
Currently assigned = checked. Toggle to add/remove (calls POST/DELETE per
permission, or PUT on "Save All").
"Save All" button sends `PUT /nexus/roles/{id}/permissions` with full set.
Immediate feedback: toast "Permissions updated. Changes take effect within
5 minutes for active sessions."
Delete button (gated by `role:delete`, hidden for system roles).

### web-pulse — Roles section

**Nav item**: "Roles" added to Settings section of sidebar
(gated by `role:read`, visible to Owner and Branch Manager).

**`/settings/roles` — Club Roles List**
Same table as nexus roles list but club-scoped.
"New Role" button (gated by `role:create`).

**`/settings/roles/$roleId` — Role Detail**
Same pattern as nexus: inline edit name/description, permission checkboxes,
Save All + Delete.

**Staff role assignment** (existing `/staff/$staffId` detail page):
Add a "Role" section to the staff detail page showing the current role name.
"Change Role" button (gated by `staff:update`) → dropdown of available club
roles → confirm → calls `PATCH /staff/{id}/role`.
Toast: "Role updated. Staff member will see the new permissions on their next
login."

---

## Files to generate

### Backend — new files
```
role/
  RoleManagementService.kt
  RoleNexusController.kt
  RolePermissionNexusController.kt
  PermissionNexusController.kt
  RolePulseController.kt
  RolePermissionPulseController.kt
  PermissionPulseController.kt
  StaffRoleAssignmentPulseController.kt
  dto/
    RoleListItemResponse.kt
    RoleDetailResponse.kt
    CreateRoleRequest.kt
    UpdateRoleRequest.kt
    PermissionResponse.kt
    UpdateRolePermissionsRequest.kt
    AssignStaffRoleRequest.kt
```

### Backend — modified files
```
rbac/Role.kt                  add isSystem: Boolean = false field
audit/AuditAction.kt          add 6 new action codes
config/DevDataLoader.kt       set isSystem = true on all seeded roles
```

### Frontend — web-nexus additions
```
src/api/roles.ts
src/routes/roles/
  index.tsx               (role list)
  $roleId.tsx             (role detail + permissions)
src/components/roles/
  RoleTable.tsx
  RoleForm.tsx            (create/edit modal)
  PermissionCheckboxGroup.tsx   (grouped by domain prefix)
```

### Frontend — web-pulse additions
```
src/api/roles.ts
src/routes/settings/roles/
  index.tsx
  $roleId.tsx
src/components/roles/
  RoleTable.tsx
  RoleForm.tsx
  PermissionCheckboxGroup.tsx
src/routes/staff/$staffId.tsx   (modify: add role assignment section)
src/components/staff/
  StaffRoleAssignment.tsx
```

---

## Implementation order

```
Step 1 — Role.isSystem + RoleManagementService (core)
  rbac/Role.kt: add isSystem: Boolean = false
  config/DevDataLoader.kt: set isSystem = true on all 11 seeded roles
  role/RoleManagementService.kt:
    listRoles(scope, clubId?) → List<RoleListItemResponse>
    getRoleDetail(roleId) → RoleDetailResponse with permissions
    createRole(request, scope, clubId?) → RoleDetailResponse (rule 5)
    updateRole(roleId, request) → RoleDetailResponse
    deleteRole(roleId) → Unit (rules 2, 3)
    addPermission(roleId, permissionId) → invalidate Redis (rule 6)
    removePermission(roleId, permissionId) → rule 4, invalidate Redis
    replacePermissions(roleId, permissionIds) → diff + save + invalidate (rule 9)
    assignStaffRole(staffId, roleId, clubId) → update UserRole + invalidate both (rule 7)
    auditService.log() for every write (rule 10)
  audit/AuditAction.kt: add ROLE_CREATED, ROLE_UPDATED, ROLE_DELETED,
    ROLE_PERMISSION_ADDED, ROLE_PERMISSION_REMOVED, STAFF_ROLE_ASSIGNED
  Verify: ./gradlew build -x test

Step 2 — Nexus role controllers + permission list
  role/RoleNexusController.kt — GET/POST/PATCH/DELETE /nexus/roles
  role/RolePermissionNexusController.kt — GET/PUT/POST/DELETE /nexus/roles/{id}/permissions
  role/PermissionNexusController.kt — GET /nexus/permissions
  All scope checks: rule 1 (platform scope only)
  DTOs: RoleListItemResponse, RoleDetailResponse, CreateRoleRequest,
    UpdateRoleRequest, PermissionResponse, UpdateRolePermissionsRequest
  Verify: ./gradlew build -x test

Step 3 — Pulse role controllers + staff role assignment
  role/RolePulseController.kt — GET/POST/PATCH/DELETE /roles
  role/RolePermissionPulseController.kt — GET/PUT/POST/DELETE /roles/{id}/permissions
  role/PermissionPulseController.kt — GET /permissions
  role/StaffRoleAssignmentPulseController.kt — PATCH /staff/{id}/role
  All scope checks: rule 1 (club scope only), rule 8 (role belongs to club)
  DTOs: AssignStaffRoleRequest
  Verify: ./gradlew build -x test

Step 4 — Backend tests
  RoleManagementServiceTest.kt (unit):
    - createRole: happy path, duplicate name (409)
    - deleteRole: with active staff (409), system role (409), clean role (success)
    - removePermission: last permission (422), normal removal + cache invalidated
    - replacePermissions: idempotent diff, cache invalidated once
    - assignStaffRole: old + new role cache both invalidated
  RoleNexusControllerTest.kt (integration):
    - list platform roles, create, update name, delete clean role
    - delete role with staff → 409, delete system role → 409
    - add/remove permission, PUT full replace
    - club-scope JWT calling nexus endpoint → 403 (rule 1)
  RolePulseControllerTest.kt (integration):
    - list club roles, create, update, delete
    - role from different club → 403 (rule 8)
    - assign staff role, verify UserRole updated
  Verify: ./gradlew test --no-daemon

Step 5 — Backend final checks
  ./gradlew ktlintFormat --no-daemon
  ./gradlew ktlintCheck --no-daemon
  ./gradlew build --no-daemon

Step 6 — web-nexus: Roles section
  src/api/roles.ts — listRoles, getRoleDetail, createRole, updateRole,
    deleteRole, listPermissions, addPermission, removePermission, replacePermissions
  src/routes/roles/index.tsx:
    RoleTable (name, description, permission count, staff count, system badge)
    "New Role" button → RoleForm modal (gated by role:create)
  src/routes/roles/$roleId.tsx:
    Role header: name + description (inline edit, gated by role:update)
    PermissionCheckboxGroup: permissions grouped by domain prefix
      (e.g., "Member" group: member:create, member:read, member:update, member:delete)
    Checkboxes: checked = currently assigned
    "Save All" → PUT /nexus/roles/{id}/permissions
    Toast: "Permissions updated. Changes take effect within 5 minutes."
    "Delete Role" button (gated by role:delete, hidden if isSystem = true)
  Sidebar: add "Roles" nav item under Organizations (gated by role:read)
  Verify: npm run dev → login as admin → see Roles in sidebar →
    view Owner role → toggle a permission → Save All → toast appears

Step 7 — web-pulse: Roles section + staff role assignment
  src/api/roles.ts — same pattern as nexus but calling /api/v1/roles
  src/routes/settings/roles/index.tsx — club role list
  src/routes/settings/roles/$roleId.tsx — same permission editor pattern
  Sidebar: add "Roles" under Settings section (gated by role:read)
  src/routes/staff/$staffId.tsx (modify existing):
    Add "Role" card section: current role name badge
    "Change Role" button (gated by staff:update) →
      StaffRoleAssignment component: dropdown of club roles → confirm modal
      → PATCH /staff/{id}/role → invalidate staff query → toast
  Verify: npm run dev → login as owner → Settings → Roles →
    create "Senior Trainer" role → assign permissions →
    go to a staff member → change their role → toast confirms

Step 8 — Frontend tests
  web-nexus:
    RoleTable.test.tsx — renders role list, system badge visible
    PermissionCheckboxGroup.test.tsx — groups by domain prefix, checked state
    RoleForm.test.tsx — submit creates role, duplicate name shows error
  web-pulse:
    RoleTable.test.tsx — club roles only
    StaffRoleAssignment.test.tsx — dropdown shows club roles, confirm updates
  Verify: npm test (both apps)

Step 9 — Frontend final checks (both apps)
  web-nexus: npm run typecheck && npm run lint && npm run build
  web-pulse: npm run typecheck && npm run lint && npm run build
```

---

## Acceptance criteria

### Backend
- [ ] `GET /nexus/roles` returns platform roles with isSystem flag
- [ ] `POST /nexus/roles` creates a new platform role
- [ ] `DELETE /nexus/roles/{id}` on a system role returns 409
- [ ] `DELETE /nexus/roles/{id}` on a role with active staff returns 409 with count
- [ ] `DELETE /nexus/roles/{id}` on empty custom role succeeds
- [ ] `PUT /nexus/roles/{id}/permissions` replaces full permission set
- [ ] Redis key `role_permissions:{roleId}` is deleted after any permission change
- [ ] `DELETE /nexus/roles/{id}/permissions/{permId}` with last permission returns 422
- [ ] Club-scope JWT calling nexus role endpoint returns 403
- [ ] `GET /roles` in pulse returns only this club's roles
- [ ] `PATCH /staff/{id}/role` updates UserRole + invalidates both old and new role cache
- [ ] Role name duplicate within same scope/club returns 409
- [ ] All 6 new audit action codes are written on corresponding operations
- [ ] All 356+ existing tests still pass

### Frontend
- [ ] web-nexus Roles nav item visible for Super Admin, hidden for Support Agent
- [ ] System roles show lock icon, Delete button absent
- [ ] Permission checkboxes grouped by domain prefix (member:*, gx:*, pt:*, etc.)
- [ ] "Save All" shows toast with 5-minute cache warning
- [ ] web-pulse Settings → Roles creates custom club role with selected permissions
- [ ] Staff detail page shows current role and "Change Role" button (Owner only)
- [ ] Changing staff role shows confirmation modal, toast on success
- [ ] `npm run typecheck`, `lint`, `test`, `build` pass for both web-nexus and web-pulse

---

## RBAC matrix rows added by this plan

No new permission codes. Existing `role:create/read/update/delete` already on
Super Admin (platform) and Owner (club). `staff:update` already on Owner and
Branch Manager.

New audit action codes added to `AuditAction.kt`:
`ROLE_CREATED`, `ROLE_UPDATED`, `ROLE_DELETED`,
`ROLE_PERMISSION_ADDED`, `ROLE_PERMISSION_REMOVED`, `STAFF_ROLE_ASSIGNED`

---

## Definition of done

- All acceptance criteria checked
- All 10 business rules covered by tests
- Redis cache invalidation tested: permission change → cache key deleted
- System role protection tested: delete → 409, isSystem badge on frontend
- Staff role reassignment tested end-to-end (pulse)
- Both web-nexus and web-pulse CI checks pass
- PLAN.md deleted before merging
- PR title: `feat(rbac): add role management UI to web-nexus and web-pulse`
- Target branch: `develop`

