# Domain Glossary

This glossary defines the precise meaning of every business term used across the Liyaqa system.
It is not a technical glossary — it defines business concepts, their boundaries,
and how they relate to each other.

---

## 1. How to use this glossary

When a term appears in code, API design, database schema, migration files,
or a PLAN.md file, its meaning must match this glossary exactly.
If you encounter a business term that is not defined here,
add it to this glossary — with a precise definition agreed upon by the team —
before using it in any implementation artifact.

This glossary is the single source of truth for business language.
The goal is to eliminate ambiguity: when someone says "membership" in a PR review,
a Slack thread, or a code comment, everyone understands the same thing.
If two people can reasonably interpret a term differently, the term needs
a sharper definition here.

---

## 2. People & roles

> **Role inheritance model:** The system defines base roles (e.g., Trainer, Receptionist)
> with a fixed set of permissions. New roles can be created that **inherit** from an
> existing base role and extend it with additional permissions. For example,
> `trainer-manager` inherits all permissions from `pt-trainer` and adds
> trainer management capabilities. The base role's permissions are never reduced
> by inheritance — only extended.

### Member

**Definition:** A person who has registered with a club and holds (or has held) a membership plan.

**In the system:** Represented as a row in the `members` table. Exposed via `/api/v1/members`. Has a dedicated self-service app (`web-arena`) and mobile app (`mobile-arena`).

**Related terms:** Membership, Lead, Prospect, User.

**Do not confuse with:** Lead (a lead has not yet taken any meaningful action), Prospect (a prospect has shown interest or purchased a non-membership product but has not bought a membership), User (a system-level identity — a member is a specific kind of user), Customer (ambiguous — use Member).

---

### Lead

**Definition:** A potential member who has been identified or captured by the club but has not yet taken any meaningful action toward joining. A lead is the initial status — no engagement, no purchase, no expressed commitment.

**In the system:** The entry point of the lead pipeline in `web-pulse`. A lead becomes a Prospect once they demonstrate interest or make any non-membership purchase.

**Related terms:** Prospect, Member, Sales Agent.

**Lead source values:** `walk-in`, `referral`, `social-media`, `website`, `call`, `other`. Source is required on lead creation.

**Do not confuse with:** Prospect (a prospect has shown interest or purchased something; a lead has not).

---

### Prospect

**Definition:** A person who has shown interest in the club or has purchased a non-membership product — such as at-home personal training, open classes, or any other product the club sells — but has not yet purchased a membership.

**In the system:** A Prospect is a Lead that has progressed. The transition from Lead to Prospect happens when the person demonstrates interest (e.g., schedules a trial, visits the club) or purchases any club product other than a membership. Prospects are managed in the lead pipeline in `web-pulse` and displayed on a Kanban board. When a prospect purchases a membership, they are converted to a Member.

**Related terms:** Lead, Member, Sales Agent.

**Valid values (pipeline status):**
| Value | Meaning |
|---|---|
| `new` | Lead captured, no contact made yet (Lead stage) |
| `contacted` | Staff has made first contact (Lead stage) |
| `trial-scheduled` | A trial visit or tour has been booked (Prospect stage) |
| `trial-done` | Trial visit completed, awaiting decision (Prospect stage) |
| `converted` | Prospect became a Member (terminal state) |
| `lost` | Prospect decided not to join or became unreachable (terminal state) |

**Do not confuse with:** Lead (a lead has taken no meaningful action), Member (a member has purchased a membership).

---

### Staff

**Definition:** A person employed by the club in an operational capacity who is not a trainer. Staff handle club administration, member services, finance, and management.

**In the system:** Staff members are managed in `web-pulse` under the staff section. Each staff member has exactly one role that determines their access. Staff roles: `club-owner`, `branch-manager`, `receptionist`, `sales-agent`, `read-only`.

**Related terms:** Club Owner, Branch Manager, Receptionist, Sales Agent, Trainer.

**Important distinction — staff vs. trainer:** A trainer is **not** a staff member by default. Trainers may be outsourced contractors who are paid per session and have no staff relationship with the club. However, a person can be **both** staff and trainer simultaneously — for example, a salaried employee who works a club shift (staff role) and also conducts PT/GX sessions (trainer role). In this case, the person holds a staff record and a trainer record, receives a salary (as staff) and session-based pay (as trainer). See also: Trainer Manager.

**Do not confuse with:** Trainer (trainers use `web-coach`; staff use `web-pulse` — a person who is both will use both apps under their respective roles).

---

### Trainer (PT Trainer)

**Definition:** A person who delivers one-on-one personal training sessions to members. A trainer may be a direct employee of the club, an outsourced contractor, or a staff member who also trains.

**In the system:** Has the `pt-trainer` role. Uses `web-coach` exclusively for trainer functions. Can view and manage only their own PT sessions, their assigned members' scoped profiles, and their own earnings. Cannot access `web-pulse` through the trainer role.

**Employment models:**
- **Outsourced trainer:** Not a staff member. Paid per PT/GX session delivered. No salary, no staff duties. Example: Omer works with FitnessX Gym as an outsourced trainer — he gets paid for sessions but is not part of the club's staff.
- **Staff + trainer (dual role):** A salaried staff member who also conducts PT/GX sessions. Receives both a salary and session-based pay. Example: Amin works as both staff and trainer — he has a scheduled shift at the club and also runs PT/GX sessions.
- **Trainer Manager:** A staff member who, in addition to conducting PT/GX sessions, manages other trainers and assists members on the gym floor. This role requires a staff relationship with the club. See: Trainer Manager.

**Related terms:** PT Package, PT Session, GX Instructor, Trainer Assignment, Trainer Manager, Staff.

**Do not confuse with:** GX Instructor (different session type — though one person can hold both roles simultaneously). Staff (a trainer is not staff unless they also hold a staff role).

---

### GX Instructor

**Definition:** A person who leads group fitness classes at the club. Like PT Trainers, a GX Instructor may be outsourced or may also be a staff member.

**In the system:** Has the `gx-instructor` role. Uses `web-coach` exclusively for instructor functions. Can view and manage only their own GX classes, take attendance, and broadcast messages to enrolled members. Cannot access `web-pulse` through the instructor role.

**Related terms:** GX Class Type, GX Class Instance, Attendance, Trainer, Trainer Manager.

**Do not confuse with:** PT Trainer (GX instructors teach classes, not one-on-one sessions — though one person can hold both roles).

---

### Trainer Manager

**Definition:** A trainer who also manages other trainers. In addition to conducting their own PT/GX sessions, they manage trainers' schedules, track trainers' progress, and assign PT/GX sessions to trainers. A Trainer Manager must be a staff member of the club.

**In the system:** The `trainer-manager` role **inherits from** the `pt-trainer` role (and/or `gx-instructor` if applicable). It has all trainer permissions plus: view and manage other trainers' schedules, assign PT/GX sessions to trainers, track trainer progress and performance. Because the Trainer Manager is also a staff member, they access `web-pulse` for management functions and `web-coach` for their own training delivery.

**Related terms:** Trainer, Staff, Branch Manager, PT Session, GX Class Instance.

**Do not confuse with:** Branch Manager (a branch manager oversees the entire branch operation; a trainer manager specifically oversees the training team). PT Trainer (a PT trainer without the manager extension cannot manage other trainers).

---

### Club Owner

**Definition:** The owner or designated top-level administrator of a club within an organization.

**In the system:** Has the `club-owner` role in `web-pulse`. Full access to all features, all branches, all financial data, and all staff management. Can view the "All branches" aggregate mode. This is the highest-privilege role within a club's operational scope.

**Related terms:** Branch Manager, Organization, Club.

**Do not confuse with:** Branch Manager (scoped to specific branches, not the whole club).

---

### Branch Manager

**Definition:** The person responsible for day-to-day operations of one or more specific branches within a club.

**In the system:** Has the `branch-manager` role in `web-pulse`. Full operations access within their assigned branches. Cannot manage other branches or view club-wide financials. Can approve refunds, manage staff schedules, approve trainer leave requests and certifications.

**Related terms:** Club Owner, Branch, Receptionist.

**Do not confuse with:** Club Owner (a branch manager's scope is limited to assigned branches).

---

### Receptionist

**Definition:** Front-desk staff responsible for member-facing operations at a branch.

**In the system:** Has the `receptionist` role in `web-pulse`. Can perform: member check-in, new member registration, payment collection, membership renewal, PT session scheduling on behalf of trainers, and **lead management** (view, create, update status, reassign leads in the pipeline). Cannot view financial reports, cannot manage staff, cannot terminate memberships.

**Related terms:** Branch Manager, Sales Agent, Member, Lead.

**Do not confuse with:** Sales Agent (both can manage leads and collect payments; the distinction is operational focus — receptionists handle the front desk, sales agents focus on acquisition).

---

### Sales Agent

**Definition:** A staff member focused on acquiring new members through lead management, tours, and plan consultation.

**In the system:** Has the `sales-agent` role in `web-pulse`. Can manage leads (full pipeline access), onboard new members, upsell plans, and collect payments. Cannot access scheduling, staff management, or financial reports.

**Related terms:** Lead, Prospect, Receptionist, Member.

**Do not confuse with:** Receptionist (both can manage leads — the distinction is operational focus, not pipeline access).

---

### User

**Definition:** A system-level identity that can authenticate with the platform. Every person who logs in — member, staff, trainer — is a User at the technical level.

**In the system:** Represented in the `users` table (authentication record). The JWT `sub` claim contains the `userId`. A User has exactly one role per application context. The User record owns the authentication credentials (email, password hash); the domain-specific record (Member, Staff, Trainer) holds the business data.

**Related terms:** Member, Staff, Trainer, RBAC.

**Do not confuse with:** Member. "User" is a system concept. In code, APIs, and database schema, prefer the specific term (Member, StaffMember, Trainer) over "User" unless referring to the authentication identity itself. See Section 10 (Terms to avoid).

---

### Customer

**Definition:** This term is deliberately avoided in the system. It is ambiguous — it could mean a Member, a Lead, a Prospect, or an Organization (in a SaaS context where the club is the customer of the platform).

**In the system:** Not used. No table, no API resource, no type named "Customer."

**When acceptable:** Only in external-facing marketing or billing contexts where "customer" refers to the Organization (the club paying for the SaaS platform). Never in code, API design, or internal documentation.

**Use instead:** Member (for a person with a membership), Prospect (for someone who has shown interest or bought a non-membership product), Lead (for an initial contact with no action), Organization (for the business entity subscribing to the platform).

---

## 3. Organisation hierarchy

### Organization

**Definition:** The top-level business entity that subscribes to the platform. An organization represents the legal or commercial entity that owns one or more clubs.

**In the system:** Every tenant-scoped entity carries `organizationId` at minimum. The `organizations` table is the root of the tenant hierarchy. Tenant isolation is enforced at this level — an organization can never see another organization's data. The `organizationId` is embedded in the JWT claims and extracted on every request; it is never supplied by the client in the request body.

**Related terms:** Club, Branch, Tenant.

**What it owns:** Everything. All clubs, branches, members, staff, trainers, financial records, plans, and configuration belong to an organization. There is no data in the system that exists outside of an organization's scope (except platform-level system data managed by the internal team via `web-nexus`).

**Do not confuse with:** Club (an organization may own multiple clubs; a club is one level below). The word "tenant" in technical contexts is synonymous with Organization.

---

### Club

**Definition:** A fitness business operated under a single brand or identity, owned by an Organization. A club may have one or more physical locations (branches).

**In the system:** Represented in the `clubs` table. Scoped to an organization (`organization_id`). The `clubId` appears in JWT claims and is used to scope data in `web-pulse` and `web-coach`. The `club-owner` role operates at the club level — they see all branches within their club.

**Related terms:** Organization, Branch, Club Owner.

**What it owns:** All branches within it, along with club-wide configuration: membership plans, pricing, PT package templates, GX class types, cancellation policies, waiver text, branding, and the staff/trainer roster.

**Club independence rule:** Each club within an organization is **fully independent**. There is no shared configuration, shared staff, or shared membership across clubs. If a staff member works at two clubs, they must be added as a separate staff record in each club. If a person is a member of two clubs, they exist as two separate member records. Clubs under the same organization do not share plans, pricing, trainers, or members.

**Do not confuse with:** Branch (a club is the brand/business; a branch is a physical location), Organization (an organization may own multiple clubs operating under different brands — e.g., "FitnessX" and "FitnessX Ladies" — but each club is independent).

---

### Branch

**Definition:** A single physical location where a club operates. A branch is where members check in, attend classes, and train.

**In the system:** Represented in the `branches` table. Scoped to both a club and an organization (`organization_id`, `club_id`). Branch-scoped entities also carry all three IDs (`organization_id`, `club_id`, `branch_id`) to allow filtering at any level without joins (per DATABASE.md section 3). Staff roles like `branch-manager`, `receptionist`, and `sales-agent` are scoped to specific branches via the JWT `branchIds` claim.

**Related terms:** Club, Organization, Branch Manager, Member.

**What it owns:** Branch-specific operational data: member check-ins, the cash drawer, GX class instances (scheduled at a specific branch), PT sessions (conducted at a specific branch), staff shift assignments, and branch-specific schedules and announcements.

**Do not confuse with:** Club (a branch is a location; a club is the business that operates one or more locations).

---

### The relationship between them

```
Organization (tenant root)
 └── Club (brand / business — fully independent)
      └── Branch (physical location)
```

- **Organization → Club:** one-to-many. An organization owns one or more clubs. Each club is fully independent — no shared data between clubs.
- **Club → Branch:** one-to-many. A club operates one or more branches.
- **Organization → Branch:** one-to-many (transitive). Every branch belongs to exactly one club, which belongs to exactly one organization.

### Where entities live in the hierarchy

| Entity | Scoped to | Notes |
|---|---|---|
| Member | **Branch** (home branch) | A member is linked to one branch as their home branch. Moving to another branch requires updating the home branch. A person joining two clubs is two separate member records. |
| Trainer | **Club** | A trainer exists at the club level and can serve multiple branches within that club. An outsourced trainer working at two clubs is two separate trainer records. |
| Staff | **Club** (with branch assignments) | Staff belong to a club. Roles like `branch-manager`, `receptionist`, `sales-agent` are scoped to specific branches via JWT claims. A person working at two clubs is two separate staff records. |
| Membership Plan | **Club** | Plan catalog is defined at the club level, available across all branches. |
| PT Package Template | **Club** | Defined at the club level. |
| GX Class Type | **Club** | The template (e.g., "Yoga") is club-level. |
| GX Class Instance | **Branch** | A specific occurrence with a date, time, and room is branch-level. |
| PT Session | **Branch** | Conducted at a specific branch. |
| Cash Drawer | **Branch** | Daily reconciliation per branch. |
| Invoice / Payment | **Branch** | Financial transactions occur at a branch. |
| Lead / Prospect | **Branch** | Captured and managed at a branch. |

### Which level owns configuration

| Concern | Owned at |
|---|---|
| Tenant isolation boundary | Organization |
| Platform billing / subscription | Organization |
| Membership plan catalog and pricing | Club |
| PT package templates and pricing | Club |
| GX class type definitions | Club |
| Cancellation and freeze policies | Club |
| Waiver text | Club |
| Staff and trainer roster | Club |
| Club-wide financial reports | Club |
| Daily operations (check-in, cash drawer) | Branch |
| GX class scheduling (instances) | Branch |
| PT session scheduling | Branch |
| Staff shift scheduling | Branch |
| Branch-specific announcements | Branch |

---

## 4. Membership concepts

### Membership

**Definition:** A time-bound contractual relationship between a member and a club that grants the member access to the club's facilities and services at a specific branch. A membership covers facility access only — it does not include PT sessions, GX class packs, or other products unless combined through a Bundle.

**In the system:** Represented in the `memberships` table. Scoped to organization, club, and branch. A member may have multiple membership records over time (history). The following constraints are enforced:

- **At most one active membership** at any point in time (status is `active`, `frozen`, or `pending`).
- **Future memberships** are allowed — a membership with a start date in the future can be created while a current membership is still active.
- **No overlapping memberships under any circumstances.** The system must validate that no two membership date ranges overlap for the same member. This validation applies to every operation that affects membership dates: creation, renewal, freeze (which extends the end date), unfreeze, upgrade, and manual date adjustment. If an action would cause two memberships to overlap, the system raises a `422 Business Rule Violation` error.

**Related terms:** Membership Plan, Membership Status, Member, Branch, Renewal, Bundle.

**Do not confuse with:** Membership Plan (the plan is the template/catalog entry; the membership is the instance assigned to a specific member with specific dates). Bundle (a bundle may include a membership alongside other products, but the membership component is still a separate record). PT Package (a separate product — never part of a membership).

---

### Membership Plan

**Definition:** A predefined package that defines what facility access a member gets, for how long, and at what price. Plans are configured by the club and available across all branches. A membership plan covers facility access only — it does not include PT sessions, GX class packs, or other products.

**In the system:** Represented in the `membership_plans` table. Scoped to organization and club (club-level — not branch-specific). Defines: name (Arabic + English), duration, price in halalas, what facilities/services are included, freeze policy (whether freezing is allowed, whether it requires staff approval or is self-service, maximum freeze count, maximum freeze duration), grace period duration, and renewal rules.

**Related terms:** Membership, Renewal, Freeze, Grace Period, Bundle.

**Do not confuse with:** Membership (the plan is the catalog template; the membership is the specific instance). Bundle (a bundle groups multiple products including a membership plan; the plan itself is a standalone product). PT Package (always a separate product).

---

### Bundle

**Definition:** A combined offer that groups multiple independent products together and sells them as a single purchasable unit. A bundle may include a membership plan, PT sessions, GX classes, physical goods (e.g., sportswear), or any other product the club sells.

**In the system:** Each content item in a bundle (membership, PT package, GX class pack, sportswear, etc.) is created independently first, then grouped into a bundle. When a bundle is purchased, the system creates separate records for each component: a membership record, a PT package record, etc. This ensures that in reporting, revenue is attributed accurately to each product category (Membership revenue, PT revenue, GX revenue, etc.) without mixing.

**Example:** A "Monthly Premium Bundle" might contain:
- 1 Monthly Membership Plan
- 3 PT Sessions (as a PT Package)
- 3 GX Class Credits
- 1 Sportswear item

When purchased, the member receives a membership record, a PT package with 3 session credits, GX class credits, and the sportswear is fulfilled separately. Revenue reporting shows each component's contribution independently.

**Related terms:** Membership Plan, PT Package, GX Class Instance, Payment, Invoice.

**Do not confuse with:** Membership Plan (a plan is one possible component of a bundle; a bundle is the grouping mechanism). Upgrade (changing a membership plan, not purchasing a bundle).

---

### Membership Status

**Definition:** The current state of a member's membership record. Determines what the member can and cannot do in the system.

**In the system:** Stored as `membership_status` on the `memberships` table. `VARCHAR(50)` with a CHECK constraint (per DATABASE.md section 6). The membership status directly controls UI gates in `web-arena` (see web-arena CLAUDE.md section 8).

**Valid values:**

| Status | Meaning | Entered when | Exited when |
|---|---|---|---|
| `pending` | Membership created but onboarding not complete (e.g., waiver not signed, payment not confirmed) | Member record created during registration | Member completes onboarding (signs waiver, payment confirmed) → `active` |
| `active` | Member has a current, paid membership and full access to club facilities and services | Payment confirmed and onboarding complete; or renewed after expiry; or unfrozen | End date passes → `expired` or `expired-grace`; member requests freeze → `frozen`; staff terminates → `terminated` |
| `frozen` | Membership is temporarily paused. The freeze duration extends the membership end date by the number of frozen days. Member cannot access facilities or book classes. | Member requests freeze and it is approved (by staff or auto-approved per plan config) | Freeze period ends or member requests unfreeze → `active` |
| `expired` | Membership end date has passed without renewal. No grace period configured, or grace period has not yet been evaluated. | Membership end date passes without renewal (on plans with no grace period) | Member renews (pays) → `active`; staff terminates → `terminated` |
| `expired-grace` | Membership end date has passed, but the member is within the plan's grace period. Member cannot access facilities but can pay to renew. | Membership end date passes on a plan that has a grace period configured | Member renews (pays) → `active`; grace period elapses without renewal → `terminated`; staff terminates → `terminated` |
| `terminated` | Membership permanently closed. Terminal state. | Staff manually terminates (requires `branch-manager` or `club-owner`, confirmation dialog, mandatory reason); or system auto-terminates after grace period elapses | Does not exit — terminal state. Member must create a new membership to rejoin (new record). |

**State transition diagram:**

```
pending → active → frozen → active (unfreeze)
                 → expired → active (renew)
                           → terminated
                 → expired-grace → active (renew)
                                 → terminated
                 → terminated
```

**Related terms:** Active, Freeze, Grace Period, Renewal, Termination.

---

### Active (membership context)

**Definition:** A membership with status `active` — meaning the member has a current, paid membership that has not expired, is not frozen, and has not been terminated. The member has full access to club facilities and services according to their plan.

**In the system:** The `active` status is the only status that grants full access. In `web-arena`, an active membership enables class booking, PT session viewing, trainer messaging, and all self-service features. In `web-pulse`, active members appear in the standard member list without any warning indicators.

**Related terms:** Membership Status, Expired, Frozen.

**Do not confuse with:** "Active" used loosely to mean "not deleted" (soft delete context) or "active PT session" (session status context). Always qualify: "active membership," "active session," "active record." See Section 10 (Terms to avoid).

---

### Freeze / Frozen

**Definition:** A temporary pause of a membership, requested by the member and approved (by staff or automatically, depending on configuration). During a freeze, the member retains their membership record but cannot access club facilities or book classes. The freeze duration extends the membership end date by the equivalent number of frozen days.

**In the system:** Transitions the membership status from `active` to `frozen`. The freeze has a start date and an end date. When the freeze period ends (or the member requests an early unfreeze), the status returns to `active`.

**Freeze eligibility** is governed by the Membership Plan's freeze policy:
- Whether freezing is allowed at all
- Maximum number of freezes per membership term
- Maximum total freeze duration in days
- **Approval mode:** staff-approved (request goes to `web-pulse` for manual approval), self-service (member freezes directly in `web-arena`), or auto-approved (system approves immediately based on plan rules)

**Overlap validation:** When a freeze extends a membership's end date, the system must validate that the new end date does not cause the membership to overlap with any future scheduled membership for the same member. If it would, the freeze is rejected with a `422` error.

**Related terms:** Membership Status, Membership Plan, Active.

**UI behavior:** In `web-arena`, a frozen membership shows a blue status badge with the unfreeze date. Booking buttons are disabled with a tooltip explaining the freeze. In `web-pulse`, frozen members appear with a blue status indicator; the "Unfreeze" action is available to `receptionist` and above.

---

### Grace Period

**Definition:** A configurable window of time after a membership expires during which the member can still renew without creating a new membership. During the grace period, the member cannot access club facilities but can pay to reactivate.

**In the system:** The grace period duration is defined on the Membership Plan. When a membership expires on a plan with a grace period, the status transitions to `expired-grace`. During this period, `web-arena` shows a prominent renewal prompt with the days remaining. In `web-pulse`, these members appear in the "Overdue" section of the renewals queue with an amber highlight. When the grace period elapses without renewal, the status transitions to `terminated`.

**Related terms:** Membership Status, Expired, Expired-Grace, Renewal, Termination.

**Do not confuse with:** Freeze (a freeze is a voluntary pause while the membership is active; a grace period is an involuntary window after expiry).

---

### Renewal

**Definition:** The act of extending an active or expired membership for a new term by making a payment. Renewal may keep the same plan or switch to a different plan (upgrade).

**In the system:** In `web-pulse`, the renewals queue shows members expiring within 30 days. The "Renew now" action opens a pre-filled payment form. Staff can change the plan before confirming. In `web-arena`, members can self-serve renewal via the home page "Renew now" CTA or the membership detail page. After successful payment, the membership status updates to `active` and the end date is extended.

**Overlap validation:** Renewal must not create an overlapping membership. If the member has a future scheduled membership, the renewed end date must not overlap with the future membership's start date.

**Related terms:** Membership, Membership Plan, Grace Period, Upgrade, Payment.

**Do not confuse with:** Upgrade (an upgrade changes the plan; a renewal may or may not change the plan).

---

### Upgrade

**Definition:** Changing a member's membership to a higher-tier plan, typically with additional benefits and a higher price.

**In the system:** The remaining value of the current plan is **prorated and credited** toward the new plan. The member pays only the difference between the prorated credit and the new plan's price for the remaining or new term.

- In `web-arena`, members can request a membership upgrade — this triggers a staff review in `web-pulse` and is not instant.
- In `web-pulse`, staff can process the upgrade directly during a renewal.

**Related terms:** Membership Plan, Renewal, Member, Payment.

**Do not confuse with:** Renewal (a renewal extends the same plan; an upgrade changes the plan). Transfer (a transfer changes the branch, not the plan).

---

### Transfer (membership transfer between branches)

**Definition:** Moving a member's active membership from one branch to another within the same club. The member's home branch is updated to the new branch.

**In the system:** A transfer updates the member's `branch_id` (home branch). The membership record is updated to reflect the new branch. Transfer is an operational action performed by staff in `web-pulse` — members cannot self-serve a transfer in `web-arena`.

**Side effects of a transfer:**
- All previously scheduled PT sessions at the old branch are **cancelled**.
- All GX bookings at the old branch are **cancelled**.
- The remaining PT package at the old branch is **not usable** at the new branch.
- The club decides how to handle the remaining PT value — this is a per-operator business decision:
  - **Option A:** Refund the remaining PT value to the member.
  - **Option B:** Grant the member equivalent new PT sessions at the new branch.
- The system must support both options. The staff member processing the transfer selects the resolution in `web-pulse`.

**Related terms:** Member, Branch, Membership, PT Package.

**Do not confuse with:** A member joining a different club (that requires a new member record and a new membership — clubs are fully independent).

---

### Termination

**Definition:** The permanent closure of a membership. Termination is a terminal state — the membership cannot be reactivated. If the member wishes to rejoin, a new membership record is created.

**In the system:** Membership status transitions to `terminated`. This can happen in two ways: (1) staff manually terminates the membership in `web-pulse` — requires `branch-manager` or `club-owner` role, a confirmation dialog, and a mandatory reason field; (2) the system automatically terminates after the grace period elapses without renewal (for plans with a grace period), or after a configurable period post-expiry (for plans without a grace period).

**Related terms:** Membership Status, Grace Period, Expired.

**UI behavior:** In `web-arena`, a terminated member can still log in but sees a read-only view of their history and a prompt to contact the club to rejoin. In `web-pulse`, terminated members appear with a red status badge. The termination reason and date are recorded for audit purposes.

**Do not confuse with:** Cancellation (of a booking or session — different concept). Soft delete (termination is a business status change; soft delete is a technical data pattern). Expiry (expiry may lead to termination, but they are distinct states).

---

## 5. PT concepts

### PT (Personal Training)

**Definition:** One-on-one training sessions delivered by a PT Trainer to a member. PT is a paid service, separate from the membership plan, purchased as a PT Package containing a fixed number of session credits.

**In the system:** PT is a domain scope used across the system: `pt` as a commit scope, `/pt-sessions` and `/pt-packages` as API resources, `pt/` as a route group in `web-pulse` and `web-coach`. The abbreviation "PT" is used everywhere — in code, APIs, UI labels, and documentation.

**Related terms:** PT Package, PT Session, Session Credit, Trainer, Trainer Assignment.

**Do not confuse with:** GX (group exercise — one-to-many, not one-to-one). Membership (PT is a separate product; a membership does not include PT sessions unless combined through a Bundle).

---

### PT Package

**Definition:** A purchasable product that grants a member a fixed number of PT session credits with a specific trainer, valid for a defined period. A PT Package is the unit of sale for personal training.

**In the system:** Represented in the `pt_packages` table. Scoped to organization, club, and branch. Links a Member to a Trainer with: total session count, sessions used, sessions remaining, start date, expiry date, and price in halalas. PT Package templates are defined at the club level; instances are created at the branch level when a member purchases or is assigned a package.

**Related terms:** PT Session, Session Credit, Trainer Assignment, Member, Bundle.

**Lifecycle:**
- Created when a member purchases (via `web-arena`, `web-pulse`, or as part of a Bundle) or when staff assigns one in `web-pulse`.
- Active while the expiry date has not passed and sessions remain.
- Expired when the expiry date passes, regardless of remaining credits.
- Exhausted when all session credits are consumed before the expiry date.

**Valid statuses:**

| Status | Meaning | Entered when | Exited when | Unused credits |
|---|---|---|---|---|
| `active` | Package is valid and sessions can be booked | Member purchases or is assigned a package | Expiry date passes → `expired`; all credits consumed → `exhausted`; staff cancels → `cancelled` | N/A — still in use |
| `expired` | Expiry date has passed regardless of remaining credits | Expiry date passes with credits still remaining | Does not exit — terminal state | Forfeited. Unused credits are lost. |
| `exhausted` | All session credits have been consumed before the expiry date | Last credit consumed (session completed, no-show, or late-cancelled) | Does not exit — terminal state | N/A — no credits remain |
| `cancelled` | Package manually cancelled by staff before natural completion | Staff cancels in `web-pulse` | Does not exit — terminal state | Handling depends on business context: staff may issue a refund for unused credits or grant replacement sessions. This is an operational decision, not an automatic system behavior. |

**Do not confuse with:** Membership Plan (membership covers facility access; PT Package covers training sessions). GX class credits (if applicable — GX booking is typically included with an active membership, not credit-based).

---

### PT Session

**Definition:** A single scheduled one-on-one training appointment between a trainer and a member. Each session consumes one credit from the member's PT Package.

**In the system:** Represented in the `pt_sessions` table. Scoped to organization, club, and branch. Links to a PT Package, a Member, and a Trainer. Each session has a scheduled date/time, duration, location/room, and a status. Sessions are created by staff in `web-pulse` (receptionists or branch managers schedule on behalf of trainers). Trainers view and manage their sessions in `web-coach`. Members view their sessions in `web-arena`.

**Related terms:** Session Credit, Session Status, PT Package, Trainer, Member.

**Do not confuse with:** GX Class Instance (a GX class is one-to-many; a PT session is one-to-one).

---

### Session Credit

**Definition:** A single unit of entitlement within a PT Package. One session credit is consumed each time a PT session is completed, marked as no-show, or late-cancelled. A trainer-cancelled session does not consume a credit.

**In the system:** Tracked as a balance on the PT Package: `session_count` (total), `sessions_used`, and the derived `sessions_remaining = session_count - sessions_used`. The balance is always shown prominently on the member's profile in `web-pulse`, on the session booking form, and on the member's PT page in `web-arena`.

**Credit consumption rules:**
| Session outcome | Credit consumed? |
|---|---|
| `completed` | Yes |
| `no-show` | Yes — member did not attend; credit is forfeited |
| `late-cancelled` | Yes — cancelled within the club-configured cutoff window (e.g., 2 hours before) |
| `cancelled` | No — cancelled outside the cutoff window; credit is restored |
| `trainer-cancelled` | No — trainer cancelled; credit is not consumed (affects trainer's performance metrics) |

**Related terms:** PT Package, PT Session, Session Status.

**Do not confuse with:** GX booking (GX classes are typically not credit-based — members with active memberships can book classes subject to capacity).

---

### Session Status

**Definition:** The current state of an individual PT session.

**In the system:** Stored as `session_status` on the `pt_sessions` table. `VARCHAR(50)` with a CHECK constraint.

**Valid values:**

| Status | Meaning | Set by | Credit consumed? |
|---|---|---|---|
| `scheduled` | Session is booked and upcoming | System (on creation) | No (not yet) |
| `completed` | Session took place as planned | Trainer (marks attendance as `present` in `web-coach`) | Yes |
| `cancelled` | Session cancelled outside the cutoff window | Staff in `web-pulse` or trainer in `web-coach` (with mandatory reason) | No — credit restored |
| `late-cancelled` | Session cancelled within the cutoff window | Staff in `web-pulse` (business rule enforced on backend) | Yes — credit forfeited |
| `no-show` | Member did not attend the scheduled session | Staff manually in `web-pulse` or trainer in `web-coach` | Yes — credit forfeited |
| `trainer-cancelled` | Trainer cancelled the session | Trainer in `web-coach` (with mandatory reason; notifies member via backend) | No — credit not consumed |

**State transitions:**
```
scheduled → completed
scheduled → cancelled
scheduled → late-cancelled
scheduled → no-show
scheduled → trainer-cancelled
```

All transitions from `scheduled` are one-way. A session that has been marked `completed`, `cancelled`, `late-cancelled`, `no-show`, or `trainer-cancelled` cannot be changed without staff intervention in `web-pulse`.

**Attendance marking window:** Trainers can mark attendance in `web-coach` from 30 minutes before the session start time until 2 hours after. Outside this window, attendance marking is locked and requires staff correction in `web-pulse`.

**Related terms:** PT Session, Session Credit, Trainer.

**Do not confuse with:** Membership Status (a completely different status domain). GX Attendance (GX uses a separate attendance model — see Section 6).

---

### Trainer Assignment

**Definition:** The relationship between a trainer and a member within the context of a PT Package. When a PT Package is created, a trainer is assigned to deliver the sessions.

**In the system:** The PT Package record links to a specific trainer via `trainer_id`. This assignment determines: which trainer delivers the sessions, which trainer can view the member's scoped profile in `web-coach`, and which trainer can message the member. A member may have different trainers for different packages over time, but each package has exactly one assigned trainer at any given time.

**Reassignment:** A PT Package's trainer can be changed mid-package (e.g., if a trainer leaves the club or a member requests a different trainer). The assignment is updated on the existing package — a new package is not required. The constraint is that the package must always have exactly one assigned trainer; it cannot be unassigned or assigned to multiple trainers simultaneously. When reassigned, the new trainer gains access to the member's scoped profile and message thread; the previous trainer loses access.

**Related terms:** PT Package, Trainer, Member.

**Do not confuse with:** GX Instructor (GX instructors are assigned to classes, not to individual members). Trainer Manager (a management role, not a session delivery assignment).

---

## 6. GX concepts

### GX (Group Exercise)

**Definition:** Instructor-led fitness classes with multiple members participating simultaneously. GX is the one-to-many counterpart to PT's one-to-one model. Members with active memberships can book GX classes subject to capacity.

**In the system:** GX is a domain scope used across the system: `gx` as a commit scope, `/gx-classes` as an API resource, `gx/` as a route group in `web-pulse` and `web-coach`. The abbreviation "GX" is used everywhere — in code, APIs, UI labels, and documentation.

**Related terms:** GX Class Type, GX Class Instance, Booking, Attendance, GX Instructor, Capacity, Waitlist.

**Do not confuse with:** PT (one-to-one sessions with a dedicated trainer). GX is not credit-based by default — members with active memberships can book classes subject to capacity.

---

### GX Class Type

**Definition:** A template that defines a category of group exercise class. A class type describes what the class is (e.g., "Yoga," "HIIT," "Spinning," "Pilates") — not when or where it happens.

**In the system:** Defined at the **club level**. A class type has: name (Arabic + English), description (Arabic + English), default duration, default capacity, and category. Class types are used to create recurring and one-off class instances. They are managed by staff in `web-pulse`. In `web-arena`, members browse and filter by class type when viewing the schedule.

**Related terms:** GX Class Instance, GX Instructor, Capacity.

**Do not confuse with:** GX Class Instance (the class type is the template — "Yoga"; the class instance is the specific occurrence — "Yoga, Sunday 9am, Studio A"). A class type has no date, time, or instructor — those belong to the instance.

---

### GX Class Instance

**Definition:** A specific, scheduled occurrence of a GX Class Type at a particular date, time, location, and branch, led by a specific GX Instructor. This is what members actually book and attend.

**In the system:** Represented in the `gx_class_instances` table (or similar). Scoped to organization, club, and **branch** (branch-level — a class happens at a physical location). Links to a GX Class Type and a GX Instructor. Has: date, start time, end time, room/location, capacity (may override the class type default), and status.

**Recurring classes:** Instances can be generated from a recurring template (e.g., "Yoga every Sunday at 9am"). Changes to a recurring class can apply to: this instance only, this and all future instances, or all instances in the series. This is managed by staff in `web-pulse`.

**Cancelled classes:** When a class instance is cancelled by staff in `web-pulse`, all booked members are notified via the backend notification service. The frontend triggers the cancellation; the backend handles notifications.

**Valid statuses:**

| Status | Meaning | Entered when | Exited when |
|---|---|---|---|
| `active` | Class is scheduled and upcoming, or currently in progress. Spots are still available. | Class instance is created | Capacity reached → `exhausted`; class time passes and attendance submitted → `completed`; class time passes without attendance → `expired`; staff cancels → `cancelled` |
| `exhausted` | Class has reached full capacity. No more direct bookings — only waitlist. This is a live state, not terminal. | Last available spot is booked | A member cancels and a spot opens (and no waitlist promotion fills it) → `active`; class time passes and attendance submitted → `completed`; class time passes without attendance → `expired`; staff cancels → `cancelled` |
| `completed` | Class has taken place and attendance has been recorded. | Attendance is submitted by the instructor or staff | Does not exit — terminal state |
| `expired` | Class time has passed without attendance being submitted. | The class's scheduled time passes and no attendance record is submitted within the attendance marking window (3 hours after class start) | Does not exit — terminal state. Attendance was not recorded; staff can investigate in `web-pulse`. |
| `cancelled` | Class was cancelled before it took place. | Staff cancels in `web-pulse` (all booked members are notified) | Does not exit — terminal state |

**State transitions:**
```
active → exhausted (capacity reached)
active → completed (attendance submitted)
active → expired (time passed, no attendance)
active → cancelled (staff cancels)

exhausted → active (spot opens without waitlist backfill)
exhausted → completed (attendance submitted)
exhausted → expired (time passed, no attendance)
exhausted → cancelled (staff cancels)

completed (terminal)
expired (terminal)
cancelled (terminal)
```

**Related terms:** GX Class Type, Booking, Attendance, Capacity, GX Instructor, Waitlist.

**Do not confuse with:** GX Class Type (the template, not the occurrence). PT Session (one-to-one, not group).

---

### Booking

**Definition:** A reservation record that confirms a member's spot in a specific GX Class Instance. A booking is the act of claiming a place in a class before it happens.

**In the system:** Represented in a bookings table. Links a Member to a GX Class Instance. Created when a member books a class in `web-arena` or when staff books on behalf of a member in `web-pulse`. A booking requires an active membership. Members cannot book classes if their membership is `frozen`, `expired`, `terminated`, or `pending`.

**Booking flow in `web-arena`:**
1. Member browses the class schedule and selects a class.
2. A confirmation dialog shows: class name, instructor, date/time, room, and cancellation policy summary.
3. On confirm, the booking is created. If the class filled between page load and submit (race condition), the system returns an error and offers the waitlist option.

**Cancellation:** Members can cancel a booking from `web-arena`. If cancellation occurs within the club-configured penalty window, a cancellation fee may apply (per the plan's cancellation policy). The cancellation confirmation dialog always shows the policy and any applicable fee.

**Valid statuses:**

| Status | Meaning | Entered when | Exited when |
|---|---|---|---|
| `confirmed` | Member has a reserved spot in the class | Member books and capacity is available; or member is promoted from waitlist | Member cancels → `cancelled` |
| `waitlisted` | Member is in the waitlist queue for a full class | Member joins waitlist for a class at full capacity | A spot opens and member is promoted → `promoted`; member cancels → `cancelled` |
| `promoted` | Member was auto-promoted from the waitlist to a confirmed spot | A booked member cancels, freeing a spot for the first waitlisted member | Member cancels → `cancelled` |
| `cancelled` | Booking has been cancelled by the member or by the system | Member cancels; or class is cancelled by staff (all bookings cancelled) | Does not exit — terminal state |

Waitlist and booking are managed within the same entity — a booking record transitions between these statuses. The `promoted` status is distinct from `confirmed` to preserve the audit trail that this member was originally waitlisted and auto-promoted.

**Related terms:** GX Class Instance, Attendance, Waitlist, Member, Capacity.

**Do not confuse with:** Attendance (a booking is the reservation; attendance is the record of whether the member actually showed up). PT Session (PT sessions are not "booked" by members — they are scheduled by staff or trainers).

---

### Attendance

**Definition:** The record of whether a member actually attended a GX Class Instance they booked. Attendance is taken after the class by the GX Instructor or staff.

**In the system:** Attendance is recorded per class instance as a batch operation. In `web-coach`, the GX instructor sees all enrolled members listed with a default status of `present`, then marks exceptions (`absent` or `late`). Attendance is submitted as a single batch — not saved incrementally. A "Submit attendance" button sends the whole list at once, with a confirmation step before submission.

**Valid values:**

| Value | Meaning | Set by |
|---|---|---|
| `present` | Member attended the class | GX Instructor in `web-coach` (default) or staff in `web-pulse` |
| `absent` | Member had a booking but did not attend | GX Instructor marks as exception |
| `late` | Member arrived late but attended | GX Instructor marks as exception |

**Attendance marking window:** From class start time until 3 hours after. After that, attendance marking locks and requires staff intervention in `web-pulse`.

**Related terms:** Booking, GX Class Instance, GX Instructor.

**Do not confuse with:** PT Session attendance (PT uses Session Status with values like `completed`, `no-show`; GX uses a separate Attendance record with values like `present`, `absent`, `late`). These are two different attendance models for two different contexts.

---

### Waitlist

**Definition:** An ordered queue of members who want to attend a GX Class Instance that has reached full capacity. When a booked member cancels, the first member on the waitlist is automatically promoted to a confirmed booking and notified.

**In the system:** When a member attempts to book a full class in `web-arena`, they are offered the option to join the waitlist. Their position in the queue is shown (e.g., "On waitlist (#3)"). Promotion from the waitlist is automatic — handled by the backend when a cancellation creates an open spot. The promoted member receives a notification. In `web-coach`, the instructor can view the waitlist in read-only mode.

**Cancellation after promotion:** When a waitlisted member is auto-promoted, the standard cancellation policy applies — including penalties if the class is within the penalty window. There is no grace period for promoted members. The member accepted the possibility of promotion when they joined the waitlist.

**Related terms:** Booking, GX Class Instance, Capacity.

**Do not confuse with:** Lead pipeline stages (both are queues, but they are entirely different domains).

---

### Capacity

**Definition:** The maximum number of members who can attend a specific GX Class Instance. Capacity is set when the class instance is created and determines when the class is full and the waitlist activates.

**In the system:** Defined on the GX Class Instance. May default from the GX Class Type's default capacity but can be overridden per instance (e.g., a smaller room on a specific date). In `web-arena`, remaining spots are shown on each class card. When capacity reaches 80% or more, the class is flagged as "nearly full." When capacity is reached, the booking button changes to "Join waitlist."

**Related terms:** GX Class Instance, GX Class Type, Booking, Waitlist.

**Do not confuse with:** PT Package session count (a different kind of limit — credits in a package vs. physical space in a room).

---

## 7. Financial concepts

### Payment

**Definition:** A record of money received from a member in exchange for a product or service — membership, PT package, GX class pack, bundle, or any other club offering.

**In the system:** Represented in the `payments` table. Payments are **immutable** — once a payment record is saved, it cannot be edited. Corrections are made via a Refund + re-payment flow, never by modifying the original record. Payments are append-only records; soft delete does not apply (per DATABASE.md section 10).

**Payment methods:** `cash`, `card`, `bank-transfer`, `other`. The selected method determines which additional fields are required (e.g., card requires a terminal/reference ID, bank transfer requires a reference number).

**Who can collect payments:**
- `receptionist` and `sales-agent` can collect payments in `web-pulse`.
- Members can pay online via card in `web-arena` (through the payment gateway's hosted fields — raw card data never touches the system).
- `club-owner` and `branch-manager` can also collect payments.

**Related terms:** Invoice, Receipt, Refund, Balance, Halala / SAR.

**Do not confuse with:** Invoice (the payment is the money received; the invoice is the document requesting or confirming the amount owed). Balance (an outstanding balance is the sum of unpaid invoice amounts, not a payment).

---

### Invoice

**Definition:** A financial document that records the amount owed or paid for a specific transaction. Invoices are the formal record of what was sold, to whom, at what price, with VAT breakdown.

**In the system:** Represented in the `invoices` table. Invoices are **immutable** — once generated, they cannot be edited (financial records are never modified; corrections use credit notes or refund records). Invoices are append-only; soft delete does not apply. Invoice generation is always performed server-side — the frontend never computes invoice totals.

**ZATCA compliance:** For Saudi regulatory compliance, invoices must be generated in a ZATCA-compliant format. ZATCA-compliant invoices are generated server-side and can be submitted to ZATCA via `POST /api/v1/invoices/{id}/submit-zatca`. The frontend triggers generation and polls for the download URL. Per ADR-0009, invoice generation is server-side only.

**VAT breakdown:** Every invoice includes: `subtotal_halalas`, `vat_amount_halalas`, `total_halalas`. The invariant `total_halalas = subtotal_halalas + vat_amount_halalas` is always enforced (per DATABASE.md section 11).

**Related terms:** Payment, Receipt, Halala / SAR, ZATCA.

**Do not confuse with:** Receipt (an invoice is the request or record of the charge; a receipt confirms that payment was received). Payment (the payment is the money; the invoice is the document).

---

### Receipt

**Definition:** A confirmation document issued to a member after a payment has been successfully processed. A receipt proves that money was received.

**In the system:** Generated after a successful payment. Members can download receipts per transaction in `web-arena`. Receipts are generated server-side.

**Related terms:** Payment, Invoice.

**Do not confuse with:** Invoice (an invoice records what is owed; a receipt records what was paid).

---

### Balance

**Definition:** The term "balance" has two distinct meanings in this system. Always qualify which one.

**Outstanding balance:** The total amount a member currently owes the club — the sum of unpaid or partially paid invoice amounts. A positive outstanding balance means the member owes money.

**Credit balance:** An amount the club owes the member — for example, from an overpayment or a prorated credit from a membership upgrade. A credit balance can be applied toward future purchases.

**In the system:** Outstanding balance is computed from unpaid invoices — it is a derived value, not a stored field. Credit balance is **derived** from refund and overpayment records — it is not a stored value on the member record. The system computes available credit by summing refund and overpayment transaction records that have not yet been applied to a purchase. When a member makes a new payment, available credit can be applied to reduce the amount owed. In `web-arena`, the outstanding balance is shown prominently on the home page with a "Pay now" CTA when greater than zero. In `web-pulse`, outstanding balances appear in the debts view with aging buckets (0-7d, 8-30d, 31-90d, 90d+).

**Related terms:** Payment, Invoice, Refund.

**Do not confuse with:** Each other. Never say "balance" without qualifying it as "outstanding balance" or "credit balance." See Section 10 (Terms to avoid).

---

### Refund

**Definition:** A full or partial reversal of a previous payment, returning money to the member. Refunds are the only way to correct a payment — the original payment record is never edited.

**In the system:** Refunds require `branch-manager` or `club-owner` approval in `web-pulse`. A refund without an approver's action is never processed. The refund record links to the original payment it reverses. Refund records are immutable and append-only, like payments.

**Partial refunds:** Supported. The refund amount is entered manually by staff, but the system enforces that the refund amount **cannot exceed the remaining refundable balance** of the associated package or payment. For example, if a PT package with 5 sessions at 100 SAR each has 3 unused sessions, the maximum refundable amount is 30,000 halalas (3 x 10,000 halalas). Multiple partial refunds can be issued against the same payment until the remaining balance reaches zero.

**Related terms:** Payment, Invoice, Balance, Credit Balance.

**Do not confuse with:** Cancellation (cancelling a booking or session is not a refund — it may or may not trigger a refund depending on the cancellation policy). Credit balance (a refund creates a credit balance record; the credit balance is the derived sum of unapplied refunds and overpayments).

---

### Commission

**Definition:** The amount owed to a trainer for PT sessions or GX classes they have delivered. Commission is calculated as a percentage of the session price, determined by two factors: the session type's base rate and an optional per-trainer adjustment.

**Commission model:**

1. **Base rate per session type:** Each session type (e.g., EMS, Strength, Yoga) has a base commission percentage. Example: EMS sessions have a 45% base rate.
2. **Per-trainer adjustment:** Each trainer can have a percentage adjustment (positive or negative) that modifies the base rate. This is configured per trainer in `web-pulse`.
3. **Effective rate = base rate + trainer adjustment.**

**Example:**
| Trainer | Session type | Base rate | Trainer adjustment | Effective rate | Session price | Commission per session |
|---|---|---|---|---|---|---|
| Ahmed | EMS PT | 45% | 0% | 45% | 20,000 halalas | 9,000 halalas |
| Nora | EMS PT | 45% | +5% | 50% | 20,000 halalas | 10,000 halalas |
| Reem | EMS PT | 45% | -3% | 42% | 20,000 halalas | 8,400 halalas |

**In the system:** Commission configuration (base rates per session type and per-trainer adjustments) is set by club staff in `web-pulse`. Trainers do not see the commission rate formula or the adjustment — only the computed results. In `web-coach`, trainers see their earnings: the total commission amount per period and a breakdown by session. Commission is computed by the backend based on sessions with status `completed`.

**Related terms:** Trainer, PT Session, GX Class Instance, Payment.

**Do not confuse with:** Salary (a staff member's salary is separate from commission; a dual-role staff+trainer may receive both). Payment (a payment is money from a member to the club; commission is money from the club to a trainer).

---

### Expense

**Definition:** A record of money spent by the club on operational costs — not payments received from members. Expenses track outgoing money and must be detailed enough to support the club's full operational and financial reporting needs.

**In the system:** Managed in `web-pulse` under the finance section (`/finance/expenses`). Expenses are scoped to a branch. The expense tracking system supports:

- **Categorization:** Expenses are classified by category (e.g., rent, utilities, supplies, equipment, maintenance, marketing, trainer payments, salaries).
- **Approval workflows:** Expenses above a configurable threshold require approval from `branch-manager` or `club-owner` before being recorded as confirmed.
- **Recurring expenses:** Support for recurring expenses (e.g., monthly rent, weekly cleaning service) that auto-generate expense records on a schedule.
- **Vendor management:** Track which vendor or supplier the expense is associated with, enabling per-vendor spend analysis.
- **Receipt upload:** Attach receipt images or PDFs as supporting documentation for each expense.
- **Budget tracking:** Define budgets per category per period (monthly/quarterly). Track actual spend against budget with variance reporting.

**Related terms:** Cash Drawer, Payment, Commission, Invoice.

**Do not confuse with:** Payment (a payment is money in from a member; an expense is money out from the club). Refund (a refund returns money to a member for a previous payment; an expense is a general outgoing cost). Commission (trainer commission is a specific type of expense — the club paying a trainer for sessions delivered).

---

### Cash Drawer

**Definition:** A daily cash reconciliation record for a branch. The cash drawer tracks the physical cash on hand at the branch throughout the day, ensuring that the cash count matches the expected balance from cash transactions.

**In the system:** Managed in `web-pulse` under `/finance/cash-drawer`. The daily workflow:
1. **Open** at the start of the day with a declared opening balance.
2. Cash payments received throughout the day are added automatically.
3. Cash refunds and expenses paid in cash are deducted.
4. **Close** at the end of the day with a declared closing count.
5. The system computes the expected balance (`opening + cash in - cash out`) and compares it to the declared closing count. A discrepancy flag appears if they do not match.

**Access control:** Only `club-owner` and `branch-manager` can view the cash drawer reconciliation. `receptionist` and `sales-agent` collect cash payments that feed into the drawer but cannot view the reconciliation itself.

**Data freshness:** The cash drawer uses `staleTime: 0` in `web-pulse` — always fetched fresh.

**Related terms:** Payment, Expense, Branch.

**Do not confuse with:** Balance (the cash drawer is a physical cash count; balance refers to a member's outstanding or credit balance).

---

### Halala / SAR

**Definition:** The currency units used throughout the system. SAR (Saudi Riyal) is the currency. A halala is the smallest unit of SAR: 1 SAR = 100 halalas.

**In the system:** Per ADR-0002, all monetary amounts are stored and transmitted as **integers in halalas**. Never as floats, decimals, or SAR amounts. The database uses `BIGINT` columns with a `_halalas` suffix (e.g., `price_halalas`, `total_halalas`). Application code and API fields use `Halalas` suffix in camelCase (e.g., `priceHalalas`, `totalHalalas`). Conversion to SAR for display happens only at the presentation layer, using `formatCurrency()` in each frontend app.

**Why halalas:** Floating-point arithmetic introduces rounding errors in financial calculations. Integer arithmetic on the smallest currency unit eliminates this class of bug entirely.

**Display conventions:**
- Arabic locale: ر.س (Arabic currency symbol)
- English locale: SAR

**Related terms:** Payment, Invoice, Balance.

**Do not confuse with:** Any other currency representation. Never store SAR amounts — always halalas. Never use `FLOAT`, `DOUBLE`, `DECIMAL`, or `NUMERIC` for monetary values (per DATABASE.md section 11).

---

## 8. Status values — master list

This table lists every status value used anywhere in the system.
When adding a new status value, add it here first.

| Term | Entity | Meaning | Entered when | Exited when |
|---|---|---|---|---|
| `pending` | Membership | Onboarding not complete (waiver unsigned, payment unconfirmed) | Member record created during registration | Onboarding complete → `active` |
| `active` | Membership | Current, paid membership with full facility access | Payment confirmed and onboarding complete; renewed; unfrozen | End date passes → `expired` or `expired-grace`; freeze → `frozen`; staff terminates → `terminated` |
| `frozen` | Membership | Membership temporarily paused; freeze duration extends end date | Freeze approved (by staff or auto-approved per plan config) | Freeze ends or member unfreezes → `active` |
| `expired` | Membership | End date passed, no grace period configured (or grace period not applicable) | End date passes without renewal on a plan with no grace period | Renews → `active`; staff terminates → `terminated` |
| `expired-grace` | Membership | End date passed, within the plan's grace period | End date passes on a plan with a grace period | Renews → `active`; grace period elapses → `terminated`; staff terminates → `terminated` |
| `terminated` | Membership | Permanently closed. Terminal state. | Staff manually terminates; or system auto-terminates after grace period | Does not exit — terminal |
| | | | | |
| `active` | PT Package | Package is valid, sessions can be booked | Member purchases or is assigned a package | Expiry date passes → `expired`; all credits consumed → `exhausted`; staff cancels → `cancelled` |
| `expired` | PT Package | Expiry date passed with unused credits remaining | Expiry date passes | Does not exit — terminal. Unused credits forfeited. |
| `exhausted` | PT Package | All session credits consumed before expiry | Last credit consumed | Does not exit — terminal |
| `cancelled` | PT Package | Manually cancelled by staff before natural completion | Staff cancels in `web-pulse` | Does not exit — terminal. Unused credits handled per business decision. |
| | | | | |
| `scheduled` | PT Session | Session is booked and upcoming | Session created by staff or system | → `completed`, `cancelled`, `late-cancelled`, `no-show`, or `trainer-cancelled` |
| `completed` | PT Session | Session took place as planned | Trainer marks attendance as present | Does not exit — terminal |
| `cancelled` | PT Session | Cancelled outside the cutoff window; credit restored | Staff or trainer cancels | Does not exit — terminal |
| `late-cancelled` | PT Session | Cancelled within the cutoff window; credit forfeited | Staff applies late-cancellation rule | Does not exit — terminal |
| `no-show` | PT Session | Member did not attend; credit forfeited | Staff or trainer marks no-show | Does not exit — terminal |
| `trainer-cancelled` | PT Session | Trainer cancelled; credit not consumed | Trainer cancels with mandatory reason | Does not exit — terminal |
| | | | | |
| `active` | GX Class Instance | Class is scheduled/upcoming with spots available | Class instance created | Capacity reached → `exhausted`; attendance submitted → `completed`; time passes without attendance → `expired`; staff cancels → `cancelled` |
| `exhausted` | GX Class Instance | Full capacity reached; waitlist only (live state) | Last spot booked | Spot opens (no waitlist backfill) → `active`; attendance submitted → `completed`; time passes without attendance → `expired`; staff cancels → `cancelled` |
| `completed` | GX Class Instance | Class took place, attendance recorded | Attendance submitted by instructor or staff | Does not exit — terminal |
| `expired` | GX Class Instance | Class time passed without attendance being submitted | Attendance window closes with no submission | Does not exit — terminal |
| `cancelled` | GX Class Instance | Class cancelled before it took place | Staff cancels in `web-pulse`; booked members notified | Does not exit — terminal |
| | | | | |
| `confirmed` | GX Booking | Member has a reserved spot | Member books (capacity available); or promoted from waitlist | Member cancels → `cancelled` |
| `waitlisted` | GX Booking | Member is in the waitlist queue for a full class | Member joins waitlist | Spot opens → `promoted`; member cancels → `cancelled` |
| `promoted` | GX Booking | Auto-promoted from waitlist to a confirmed spot | Booked member cancels, freeing a spot | Member cancels → `cancelled` |
| `cancelled` | GX Booking | Booking cancelled | Member cancels; or class cancelled by staff | Does not exit — terminal |
| | | | | |
| `present` | GX Attendance | Member attended the class | Instructor marks (default for all enrolled) | Does not exit — final after submission |
| `absent` | GX Attendance | Member had a booking but did not attend | Instructor marks as exception | Does not exit — final after submission |
| `late` | GX Attendance | Member arrived late but attended | Instructor marks as exception | Does not exit — final after submission |
| | | | | |
| `new` | Lead / Prospect Pipeline | Lead captured, no contact made | Lead created in `web-pulse` | Staff contacts → `contacted` |
| `contacted` | Lead / Prospect Pipeline | Staff has made first contact | Staff logs contact | Interest shown → `trial-scheduled`; lost → `lost` |
| `trial-scheduled` | Lead / Prospect Pipeline | Trial visit or tour booked (prospect stage) | Staff schedules trial | Trial completed → `trial-done`; lost → `lost` |
| `trial-done` | Lead / Prospect Pipeline | Trial completed, awaiting decision (prospect stage) | Trial visit occurs | Purchases membership → `converted`; lost → `lost` |
| `converted` | Lead / Prospect Pipeline | Prospect became a Member | Membership purchased | Does not exit — terminal |
| `lost` | Lead / Prospect Pipeline | Decided not to join or unreachable | Staff marks as lost at any stage | Does not exit — terminal |
| | | | | |
| `pending-review` | Trainer Certification | Certification uploaded, awaiting manager approval | Trainer uploads in `web-coach` | Manager approves → `approved`; manager rejects → `rejected` |
| `approved` | Trainer Certification | Certification verified by branch manager | Manager approves in `web-pulse` | Does not exit (until expiry triggers a new upload cycle) |
| `rejected` | Trainer Certification | Certification rejected by branch manager | Manager rejects in `web-pulse` with reason | Does not exit — trainer must upload a new certification |
| | | | | |
| `pending` | Trainer Leave Request | Leave request submitted, awaiting approval | Trainer submits in `web-coach` | Manager approves → `approved`; manager rejects → `rejected` |
| `approved` | Trainer Leave Request | Leave approved by branch manager | Manager approves in `web-pulse` | Does not exit — leave is taken as scheduled |
| `rejected` | Trainer Leave Request | Leave rejected by branch manager | Manager rejects in `web-pulse` with reason | Does not exit — trainer must submit a new request |
| | | | | |
| `sent` | Message | Message sent by sender | Sender submits message | Delivered to recipient → `delivered` |
| `delivered` | Message | Message received by recipient's app | Backend confirms delivery | Recipient opens → `read` |
| `read` | Message | Message opened by recipient | Recipient views the message | Does not exit — terminal |
| | | | | |
| `active` | Goal (Progress) | Goal is current and being tracked | Trainer sets goal in `web-coach` | Trainer marks achieved → `achieved`; trainer marks abandoned → `abandoned` |
| `achieved` | Goal (Progress) | Goal target reached | Trainer marks with a note | Does not exit — terminal |
| `abandoned` | Goal (Progress) | Goal no longer being pursued | Trainer marks with a note | Does not exit — terminal |

---

## 9. Abbreviations

Every abbreviation used in code, APIs, database schema, UI labels, or documentation.
If an abbreviation is not in this table, spell it out or add it here before using it.

| Abbreviation | Full term | Where used | Notes |
|---|---|---|---|
| PT | Personal Training | Everywhere: code, APIs, DB tables, UI, docs | One-on-one trainer sessions. Used in: `pt_sessions`, `pt_packages`, `/api/v1/pt-sessions`, commit scope `pt` |
| GX | Group Exercise | Everywhere: code, APIs, DB tables, UI, docs | Instructor-led group classes. Used in: `gx_class_instances`, `/api/v1/gx-classes`, commit scope `gx` |
| SAR | Saudi Riyal | Financial code, APIs, UI display | The currency. Never stored as SAR amounts — always converted to halalas for storage and transmission |
| ZATCA | Zakat, Tax and Customs Authority | Backend, invoicing, integration code | Saudi tax authority. Invoice compliance is handled server-side only (per ADR-0009). Used in: `/api/v1/invoices/{id}/submit-zatca` |
| KMP | Kotlin Multiplatform | Mobile app (`mobile-arena`) | Framework for sharing code between Android and iOS. Used in: `mobile-arena/shared/` |
| CMP | Compose Multiplatform | Mobile app (`mobile-arena`) | UI framework for KMP — shared UI across Android and iOS |
| JWT | JSON Web Token | Backend auth, all frontends | Stateless authentication token. Contains: `sub`, `role`, `organizationId`, `clubId`, `branchIds`, `exp`, `iat`. Per ADR-0003 |
| RBAC | Role-Based Access Control | All apps, backend | Every app enforces role-based permissions. Defined in `src/types/permissions.ts` per frontend app. Backend enforces independently |
| ADR | Architecture Decision Record | Documentation (`docs/adr/`) | Format: `docs/adr/0001-use-uuid-for-public-ids.md`. Records significant architectural decisions with context, decision, and consequences |
| SaaS | Software as a Service | Architecture, multi-tenancy docs | The platform is a multi-tenant SaaS product. Each Organization is a tenant |
| UUID | Universally Unique Identifier | Everywhere: DB, APIs, frontend | Version 4. All public-facing IDs. Generated by PostgreSQL (`gen_random_uuid()`). Per ADR-0001 |
| VAT | Value Added Tax | Financial code, invoicing | Tax component on invoices. Stored separately: `subtotal_halalas`, `vat_amount_halalas`, `total_halalas` |
| RTL | Right-to-Left | Frontend, localization | Arabic text direction. When locale is `ar`, root has `dir="rtl"` with logical CSS properties |
| DTO | Data Transfer Object | Backend | Request and response objects. Domain entities are never exposed directly from API endpoints |
| API | Application Programming Interface | Everywhere | REST API at `/api/v{n}/`. Versioned from day one. Per ADR-0011 |
| HSTS | HTTP Strict Transport Security | Backend, security | Enforces HTTPS. Set via `Strict-Transport-Security` header |
| OWASP | Open Worldwide Application Security Project | Backend, security | Top 10 used as baseline security checklist |
| RFC 7807 | Problem Details for HTTP APIs | Backend, error handling | Standard error response format used for all API errors. Per ADR-0007 |
| MSW | Mock Service Worker | Frontend testing | API mocking at the network layer for development and tests |
| CSP | Content Security Policy | Frontend, security (`web-arena`) | HTTP header controlling allowed resource origins. Critical for payment gateway iframe security |
| TTL | Time to Live | Backend (caching, JWT), frontend (TanStack Query `staleTime`) | Expiry duration for cached data, tokens, and idempotency keys |
| PII | Personally Identifiable Information | Security, logging | Never logged to browser console or included in error responses. Stripped from URLs in `web-arena` |
| BMI | Body Mass Index | Progress tracking (`web-coach`, `web-arena`) | Derived metric computed by the backend — never computed on the frontend |
| EMS | Electrical Muscle Stimulation | PT session types | A type of PT session. Used in commission calculation examples |

---

## 10. Terms to avoid

These terms are ambiguous and must NOT be used in code, APIs, database schema,
or documentation without qualification. Each entry explains why the term is
problematic and what to use instead.

---

### User (alone)

**Why it's ambiguous:** "User" could mean a Member, a StaffMember, a Trainer, a Club Owner, or any person who logs in. In a system with distinct roles, apps, and data scopes, "User" erases critical distinctions.

**Acceptable usage:** Only when referring to the system-level authentication identity — the `users` table, the `userId` in a JWT, or the abstract concept of "someone who can log in." In these contexts, "User" is the correct technical term.

**Never acceptable:** As a substitute for a specific role in business logic, API naming, or UI copy. `getUserProfile` is wrong if it returns a member's profile — call it `getMemberProfile`.

**Use instead:**
- `Member` — a person with a membership
- `StaffMember` — a club employee in an operational role
- `Trainer` — a PT trainer or GX instructor
- `Lead` / `Prospect` — a potential member in the pipeline

---

### Customer

**Why it's ambiguous:** "Customer" could mean a Member (the person using the gym), an Organization (the business paying for the SaaS platform), or a Lead/Prospect (someone who might pay). Each has completely different data, permissions, and lifecycle.

**Acceptable usage:** Only in external-facing marketing or billing contexts where "customer" refers to the Organization subscribing to the platform. Never in code.

**Use instead:**
- `Member` — a person with a club membership
- `Lead` — an initial contact with no meaningful action
- `Prospect` — someone who has shown interest or purchased a non-membership product
- `Organization` — the business entity subscribing to the platform

---

### Active (alone)

**Why it's ambiguous:** "Active" is used across multiple domains with different meanings:
- Active membership — status `active`, member has full access
- Active PT package — not expired, not exhausted, not cancelled
- Active GX class — class instance with status `active`, upcoming with spots available
- Active record — not soft-deleted (`deleted_at IS NULL`)
- Active goal — goal with status `active`, currently being tracked
- Active trainer — unclear: currently employed? Currently logged in? Has sessions this week?

**Acceptable usage:** Never alone. Always qualify with the entity.

**Use instead:**
- "active membership" — membership with status `active`
- "active PT package" — package with status `active`
- "active class" — GX class instance with status `active`
- "active record" or "non-deleted record" — `deleted_at IS NULL`
- "active goal" — goal with status `active`

---

### Inactive

**Why it's problematic:** "Inactive" is not a valid status on any entity in this system. It is a vague negation that could mean expired, frozen, terminated, cancelled, exhausted, soft-deleted, or simply "not currently doing something."

**Acceptable usage:** Never. There is no `inactive` status value anywhere in the system. Do not introduce one.

**Use instead:** The specific status that applies:
- Membership: `expired`, `expired-grace`, `frozen`, `terminated`
- PT Package: `expired`, `exhausted`, `cancelled`
- GX Class Instance: `expired`, `cancelled`
- Record: soft-deleted (`deleted_at IS NOT NULL`)

---

### Cancel / Cancelled (alone)

**Why it's ambiguous:** "Cancelled" applies to at least five different entities, each with different consequences:
- Cancelled GX booking — member's spot released, possible waitlist promotion, possible penalty fee
- Cancelled PT session — credit restored (if outside cutoff) or forfeited (if late/no-show)
- Cancelled PT package — terminal state, unused credits handled per business decision
- Cancelled GX class instance — all bookings cancelled, members notified
- Terminated membership — "cancelled membership" is not the correct term

**Acceptable usage:** Always qualify with the entity and, where relevant, the consequence.

**Use instead:**
- "cancelled booking" — a GX class booking that was cancelled
- "cancelled session" / "late-cancelled session" / "trainer-cancelled session" — a PT session cancellation with specific credit impact
- "cancelled package" — a PT package cancelled by staff
- "cancelled class" — a GX class instance cancelled by staff
- "terminated membership" — never say "cancelled membership"; the correct term is "terminated"

---

### Delete / Deleted (alone)

**Why it's ambiguous:** The system uses soft deletes (`deleted_at` timestamp) for all business entities. "Delete" could mean:
- Soft delete — set `deleted_at = NOW()`, record remains in database
- Hard delete — physically remove the row (never done on business entities)
- Terminate — end a membership permanently (business action, not a data operation)
- Cancel — cancel a booking, session, or package (status change, not a delete)

**Acceptable usage:** Only in technical contexts referring to the soft delete mechanism itself ("soft-delete this record," "filter out deleted records").

**Use instead:**
- "soft-delete" — when referring to setting `deleted_at`
- "terminated" — when ending a membership
- "cancelled" — when cancelling a booking, session, or package
- Never say "delete a member" or "delete a membership" — use "terminate" or "soft-delete" depending on whether you mean the business action or the data operation

---

### Session (alone)

**Why it's ambiguous:** "Session" could mean:
- A PT session — a one-on-one training appointment
- A GX class instance — sometimes informally called a "session"
- A user session — the authenticated login session (JWT lifetime)
- A browser session — `sessionStorage`, tab lifecycle

**Acceptable usage:** Only when the context makes the meaning unambiguous (e.g., inside the `pt/sessions/` route, "session" clearly means PT session).

**Use instead:**
- "PT session" — a personal training appointment
- "GX class" or "class instance" — a group exercise occurrence
- "auth session" or "login session" — the JWT-based authenticated session
- "browser session" — client-side session storage

---

### Class (alone)

**Why it's ambiguous:** "Class" could mean:
- A GX Class Type — the template (e.g., "Yoga")
- A GX Class Instance — a specific scheduled occurrence
- A code class — a Kotlin/Java/TypeScript class definition

**Acceptable usage:** Only when context is unambiguous (e.g., inside the `gx/` route group).

**Use instead:**
- "class type" — the GX template definition
- "class instance" — a specific scheduled occurrence with a date, time, and instructor
- Spell out the programming language construct when discussing both code and domain (e.g., "the `GXClassInstance` entity class")

---

### Plan (alone)

**Why it's ambiguous:** "Plan" could mean:
- A Membership Plan — the catalog template defining facility access
- A PT session plan — the trainer's pre-session workout plan/notes
- A Bundle — sometimes informally called a "plan" or "package"

**Acceptable usage:** Only when context is unambiguous (e.g., inside the membership plans management view).

**Use instead:**
- "membership plan" — the facility access template
- "session plan" or "workout plan" — the trainer's pre-session notes
- "bundle" — the grouped product offer

---

### Package (alone)

**Why it's ambiguous:** "Package" could mean:
- A PT Package — the purchasable unit of PT session credits
- A Bundle — sometimes informally called a "package"
- A software package — a dependency in `package.json` or Gradle

**Acceptable usage:** Only when context is unambiguous (e.g., inside the PT domain).

**Use instead:**
- "PT package" — the personal training session credits product
- "bundle" — the grouped product offer
- "dependency" or "library" — a software package
