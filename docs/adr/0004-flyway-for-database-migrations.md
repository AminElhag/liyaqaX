# ADR-0004 — Flyway for all database schema migrations

## Status
Accepted

## Context
The backend uses PostgreSQL and requires a disciplined approach to schema evolution. Hibernate's `ddl-auto=update` is explicitly prohibited in production because it silently alters schemas without review, cannot handle destructive changes safely, and produces no audit trail. The project needs versioned, reviewable, and repeatable migration scripts that integrate with the CI/CD pipeline. Liquibase was considered as an alternative but Flyway was chosen for its simplicity and convention-over-configuration approach — the team does not need Liquibase's XML/YAML changeset format or its rollback abstraction. Migrations must be backward-compatible with the previous running application version to allow safe rollback during deployments.

## Decision
Use Flyway for all database schema changes. Every schema change is a versioned SQL migration script, reviewed in PRs like any other code. Migrations run as a pre-deployment step in CI/CD, before the new application version starts. No schema changes are made outside of Flyway — not via `ddl-auto`, not via manual DDL, not via ad hoc scripts.

## Consequences
- Every schema change is versioned, auditable, and reproducible across all environments (local, staging, production).
- Migrations are reviewed in pull requests, catching issues like missing indexes, incorrect types, or breaking changes before they reach production.
- Backward-compatible migrations enable safe blue-green deployments and rollback without data loss.
- Developers must write raw SQL for migrations, which requires familiarity with PostgreSQL DDL — there is no ORM abstraction layer for schema changes.
- Migration ordering is strictly sequential — concurrent feature branches that both add migrations require coordination to avoid version conflicts.
- The `flywayMigrate` and `flywayInfo` Gradle tasks provide a clear workflow for applying and inspecting migration status locally.
