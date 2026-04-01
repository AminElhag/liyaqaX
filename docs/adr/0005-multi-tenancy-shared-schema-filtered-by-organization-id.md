# ADR-0005 — Multi-tenancy via shared schema filtered by organization_id

## Status
Accepted

## Context
The platform is a SaaS product serving multiple organizations, each with their own clubs and branches. Three multi-tenancy strategies were considered: database-per-tenant (strongest isolation but high operational overhead for provisioning and migrations), schema-per-tenant (moderate isolation but complicates connection pooling and migration orchestration), and shared schema with row-level filtering (simplest operationally but requires rigorous filtering discipline). The tenant hierarchy follows Organization > Club > Branch, and every tenant-scoped entity must carry `organizationId` at minimum. A missing tenant filter must cause a query to fail — not silently return cross-tenant data.

## Decision
Use a shared database schema with row-level tenant isolation filtered by `organizationId`. Tenant context is propagated via a request-scoped context object, populated once at the filter/interceptor layer from JWT claims. Tenant filtering is applied automatically via a JPA interceptor or Hibernate Filter so developers cannot accidentally omit it. All cross-tenant operations require explicit elevated privileges and are audit-logged.

## Consequences
- Single database simplifies operations: one connection pool, one migration pipeline, one backup strategy.
- Adding a new organization requires no infrastructure changes — just a new data row.
- Automatic tenant filtering via JPA interceptor eliminates the risk of developers forgetting a WHERE clause.
- Tenant isolation must be explicitly tested in integration tests — assert that tenant A cannot read or write tenant B's data.
- Shared schema means a noisy-neighbor risk: one organization's heavy queries can affect others. Monitoring and query optimization are essential.
- Cross-tenant reporting (for the internal Nexus dashboard) requires an explicit bypass of the tenant filter, which must be logged and restricted to elevated roles.
- Tenant context is always explicit — never inferred from session state or assumed from the request. Field names are consistent everywhere: `organizationId`, `clubId`, `branchId` with no abbreviations.
