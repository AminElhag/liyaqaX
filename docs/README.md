# docs/

Index of all documentation files in this directory.

## Files

| File | Description |
|---|---|
| `domain-glossary.md` | Business term definitions used across the entire project. |
| `RBAC.md` | Cross-system role-based access control rules. |
| `adr/` | Architecture Decision Records. |
| `api/openapi.yaml` | Root OpenAPI 3.1.0 spec that imports all domain spec files. |
| `api/shared-schemas.yaml` | Shared reusable schemas and responses referenced by all domain specs. |
| `api/auth.yaml` | Authentication endpoint definitions. |
| `api/members.yaml` | Member management endpoint definitions. |
| `api/memberships.yaml` | Membership management endpoint definitions. |
| `api/finance.yaml` | Finance and billing endpoint definitions. |
| `api/pt.yaml` | Personal training endpoint definitions. |
| `api/gx.yaml` | Group exercise endpoint definitions. |
| `api/staff.yaml` | Staff management endpoint definitions. |
| `api/leads.yaml` | Lead management endpoint definitions. |
| `api/integrations.yaml` | External integration endpoint definitions (ZATCA, Qoyod, etc.). |

---

## How this directory grows

These documents are not written speculatively.
They grow alongside the system:
- A new ADR is added when a significant architectural decision is made
- A new API spec entry is added when a feature introduces a new endpoint
- The domain glossary is updated when a new business term is introduced
- The RBAC matrix gains a new row when a feature introduces a new permission

No document in this directory is ever complete — they are living
references that reflect the current state of the system.
