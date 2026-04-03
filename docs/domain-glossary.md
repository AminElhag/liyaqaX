# Domain Glossary

This glossary defines the precise meaning of every business term used
across the system. Code, APIs, database schemas, and PLAN.md files
must use terms consistently as defined here.

---

## How to use this glossary

When a term appears in code, API design, database schema, or a PLAN.md
file, its meaning must match this glossary exactly.

If a term is not here, add it before using it in any implementation.
Ambiguous terms lead to ambiguous code.

---

## People & roles

### Member

**Definition:** A person registered in the system regardless of their
current membership status.

**In the system:** A row in the `members` table. Can have status:
active, frozen, expired, terminated, or pending.

**Do not confuse with:** An "active member" — which specifically means
a member with an active membership status. A member whose membership
has expired is still a member.

### Lead / Prospect

**Definition:** A person who has expressed interest in joining but has
not yet become a paying member.

**In the system:** A row in the `leads` table. Not a member yet.
Converted to a member when they sign up and pay.

### Staff

**Definition:** An employee of the club who is NOT a trainer.

**Includes:** club owner, branch manager, receptionist, sales agent.

**Do not confuse with:** Trainers, who have their own roles and their
own app (web-coach). A trainer is not staff.

### Trainer (PT Trainer)

**Definition:** A personal training professional assigned to deliver
one-on-one PT sessions to members.

**In the system:** `trainer:pt` role. Uses the web-coach app.

**Do not confuse with:** GX Instructor (group classes), Staff
(club employees).

### GX Instructor

**Definition:** A group exercise class instructor who leads classes
for multiple members simultaneously.

**In the system:** `trainer:gx` role. Uses the web-coach app.

**Note:** A person can hold both `trainer:pt` and `trainer:gx`
roles simultaneously.

### Club Owner

**Definition:** The person with full administrative control over a club
and all its branches.

**In the system:** `club:owner` role in web-pulse.

**Do not confuse with:** The Organization — a club owner manages
one club, not the entire platform.

### User (system concept)

**Definition:** Any authenticated entity in the system regardless
of role.

**In the system:** A row in the `users` table with a role column.

**Do not use alone:** Always qualify — Member, StaffMember, Trainer.
"User" alone is ambiguous and must not appear in domain discussions
without qualification.

### Customer

**Definition:** AMBIGUOUS — do not use in code or APIs.

**Use instead:** The specific role name — Member, ClubOwner,
or whatever applies.

---

## Organisation hierarchy

### Organization

**Definition:** The top-level tenant entity. Represents the company
that owns one or more gym clubs.

**In the system:** `organizations` table. Every tenant-scoped entity
carries `organization_id`.

**Example:** "Liyaqa Demo Org" is an organization that might own
multiple gym brands.

### Club

**Definition:** A specific gym brand under an organization. One
organization may have multiple clubs, each with its own brand identity.

**In the system:** `clubs` table. References `organization_id`.

**Example:** "Elixir Gym" is a club under the "Liyaqa Demo Org"
organization.

### Branch

**Definition:** A physical gym location belonging to a club.

**In the system:** `branches` table. References `club_id` and
`organization_id`.

**Example:** "Elixir Gym — Al Olaya Branch" is a branch of the
"Elixir Gym" club.

### Hierarchy

```
Organization → Club → Branch
```

This order is strict. A branch cannot exist without a club.
A club cannot exist without an organization.

---

## Membership concepts

### Membership

**Definition:** An instance of a plan assigned to a specific member
for a specific period.

**In the system:** `memberships` table.

**Valid statuses:** active, frozen, expired, expired-grace, terminated,
pending.

**Do not confuse with:** Membership Plan (the catalog item).

### Membership Plan

**Definition:** The catalog item that defines the type, duration,
price, and rules of a membership offering.

**In the system:** `membership_plans` table.

**Do not confuse with:** Membership (the instance assigned to a member).
A plan is the template; a membership is the instance.

### Active (membership)

**Definition:** A membership where the current date is between
`start_date` and `expires_at` AND `deleted_at IS NULL`.

**Do not use alone:** Always qualify — "active membership",
"active session". The word "active" alone means nothing.

### Freeze / Frozen

**Definition:** A paused membership at the member's request. The freeze
duration extends the expiry date by the same amount.

**Entered when:** Member requests a freeze and staff approves it.

**Exited when:** The freeze period ends automatically, or the member
requests an early unfreeze.

### Grace Period

**Definition:** A configurable period after expiry during which the
member retains access to the gym.

**Configuration:** Defined per membership plan. Can be zero
(no grace period).

**Purpose:** Gives the member time to renew without losing access
immediately on expiry.

### Renewal

**Definition:** Creating a new membership instance for a member after
(or before) their current one expires.

**Important:** Renewal does not extend the existing membership —
it creates a new membership instance.

### Termination

**Definition:** Permanently closing a membership before its natural
expiry date.

**Irreversible** without explicit staff action to create a new
membership (rejoin).

**Requires:** `club:branch-manager` or `club:owner` role.

---

## PT concepts

### PT (Personal Training)

**Definition:** One-on-one training sessions between a trainer and
a member.

### PT Package

**Definition:** A bundle of PT sessions purchased by or for a member.

**In the system:** `pt_packages` table. Has a session count and
expiry date.

### PT Session

**Definition:** A single scheduled PT appointment between a trainer
and a member.

**In the system:** `pt_sessions` table.

**Valid statuses:** scheduled, completed, cancelled, late-cancelled,
no-show, trainer-cancelled.

### Session Credit

**Definition:** One unit from a PT package. Consumed when a session
is marked as completed, no-show, or late-cancelled.

**Not consumed when:** Session is cancelled (within the allowed
cancellation window) or trainer-cancelled.

---

## GX concepts

### GX (Group Exercise)

**Definition:** Instructor-led fitness classes open to multiple
members simultaneously.

### GX Class Type

**Definition:** The template for a class — "Yoga", "HIIT", "Spinning".
Not a specific occurrence.

**In the system:** `gx_class_types` table.

**Do not confuse with:** GX Class Instance (a specific scheduled
occurrence).

### GX Class Instance

**Definition:** A specific scheduled occurrence of a class type with
a date, time, room, and instructor.

**In the system:** `gx_class_instances` table.

**Do not confuse with:** GX Class Type (the template). The type is
"Yoga". The instance is "Yoga on Tuesday at 7pm with Sarah in Room A".

### Booking

**Definition:** A member's reservation for a specific GX class instance.

**Do not confuse with:** Attendance (whether they actually showed up).

### Attendance

**Definition:** The record of whether a member was physically present
at a class or session they were booked for.

### Waitlist

**Definition:** A queue of members waiting for a spot in a full GX
class instance. When a spot opens, the next person on the waitlist
is automatically offered the spot.

---

## Financial concepts

### Payment

**Definition:** A financial transaction recording money received from
a member.

**Immutable** once saved — corrections are handled via refund +
re-payment, never by editing the original payment.

### Invoice

**Definition:** A formal document issued for a payment. Must be
ZATCA-compliant for Saudi Arabia.

**Immutable** once issued — corrections via credit note.

### Balance (outstanding)

**Definition:** The amount owed by a member to the club.

**Convention:** Positive means the member owes money. Zero means
paid in full. Negative is not used.

### Halala / SAR

**Definition:** Saudi Riyal (SAR) is the currency. 1 SAR = 100 halalas.

**In the system:** All monetary values are stored as integers in
halalas. Per ADR-0002.

**Column naming:** `price_halalas`, `total_halalas`, `balance_halalas`.

### Commission

**Definition:** The portion of revenue attributed to a trainer or
sales agent for their role in generating the revenue.

**Configuration:** Set in web-pulse by club owner or branch manager.

**Visibility:** Trainers see the computed commission amount only —
never the formula or percentage used to calculate it.

---

## Status values — master list

| Term | Entity | Meaning | Entered when | Exited when |
|---|---|---|---|---|
| active | Membership | Current, paid, within dates | Plan assigned and paid | Expires, frozen, or terminated |
| frozen | Membership | Paused at member request | Staff approves freeze | Unfreeze or freeze period ends |
| expired | Membership | Past expiry date | `expires_at` passes | Renewed |
| expired-grace | Membership | Past expiry, within grace period | `expires_at` passes, grace > 0 | Grace period ends or renewed |
| terminated | Membership | Permanently closed | Staff terminates | Rejoin creates new membership |
| pending | Membership | Registered, not yet paid | Member created, no payment | First payment made |
| scheduled | PT Session | Booked, not yet occurred | Session booked | Session time passes |
| completed | PT Session | Session delivered | Staff or trainer marks complete | N/A — terminal |
| cancelled | PT Session | Session cancelled within window | Member or staff cancels | N/A — terminal |
| late-cancelled | PT Session | Cancelled within penalty window | Cancellation within cutoff | N/A — terminal |
| no-show | PT Session | Member did not attend | Staff or trainer marks | N/A — terminal |
| trainer-cancelled | PT Session | Trainer cancelled the session | Trainer marks cancelled | N/A — terminal |

---

## Abbreviations

| Abbreviation | Meaning |
|---|---|
| PT | Personal Training |
| GX | Group Exercise |
| SAR | Saudi Riyal |
| ZATCA | Zakat, Tax and Customs Authority (Saudi Arabia) |
| KMP | Kotlin Multiplatform |
| CMP | Compose Multiplatform |
| JWT | JSON Web Token |
| RBAC | Role-Based Access Control |
| ADR | Architecture Decision Record |
| SaaS | Software as a Service |

---

## Terms to avoid

These terms must NOT be used alone in code, APIs, or database schema.

| Avoid | Use instead |
|---|---|
| User (alone) | Member, StaffMember, or Trainer |
| Customer | The specific role name |
| Active (alone) | active membership, active session |
| Inactive | The specific status: expired, terminated, frozen |
| Cancel | cancelled booking, cancelled session, terminated membership |
| Delete | soft delete, terminated, or cancelled depending on context |
