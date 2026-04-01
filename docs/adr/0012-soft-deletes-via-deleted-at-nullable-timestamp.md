# ADR-0012 — Soft deletes via deleted_at nullable timestamp

## Status
Accepted

## Context
The platform handles sensitive financial, membership, and operational data that is subject to audit requirements. Hard deletes permanently remove records from the database, making it impossible to reconstruct historical state, investigate disputes, or satisfy audit trails. The system needs to support scenarios like: a terminated member's payment history must remain accessible to staff; a cancelled invoice must still appear in financial reports; a deleted PT session must still count in trainer performance history. Two soft delete approaches were considered: a boolean `is_deleted` flag, or a nullable `deleted_at` timestamp. A boolean flag indicates whether a record is deleted but not when — losing valuable audit information. A nullable timestamp serves both purposes: `NULL` means active, a non-null value means deleted and records the exact moment of deletion.

## Decision
Implement soft deletes on critical entities using a nullable `deleted_at` timestamp column. A record is active when `deleted_at IS NULL` and soft-deleted when `deleted_at` is populated. The `deleted_at` field follows the project's naming conventions (`deletedAt` in application code, `deleted_at` in the database). Queries filter on `deleted_at IS NULL` by default to exclude soft-deleted records from normal operations.

## Consequences
- Audit history is preserved — deleted records remain in the database and can be queried for reporting, dispute resolution, and compliance.
- The timestamp captures when the deletion occurred, providing a complete audit trail without a separate audit log table for delete events.
- Consistent with the project's naming conventions: `deletedAt` follows the `At` suffix pattern for timestamps alongside `createdAt` and `updatedAt`.
- Every query on a soft-deletable table must include a `deleted_at IS NULL` filter — the JPA interceptor or a repository base class should apply this automatically to prevent accidentally returning deleted records.
- Soft-deleted records still occupy storage and affect index sizes — periodic archival of old soft-deleted records may be needed as the dataset grows.
- Unique constraints must account for soft deletes — a unique index on `(email)` would prevent re-registration after soft delete unless scoped to `WHERE deleted_at IS NULL`.
- Hard deletes may still be appropriate for non-critical, high-volume data (e.g., expired session tokens, temporary cache entries) where audit preservation is unnecessary.
