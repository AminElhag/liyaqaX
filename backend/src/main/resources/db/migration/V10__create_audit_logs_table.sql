-- ============================================================
-- V10__create_audit_logs_table.sql
-- Create the audit_logs table for persistent audit trail of all
-- significant write operations across all domains.
-- ============================================================

-- ── Audit ────────────────────────────────────────────────────

CREATE TABLE audit_logs (
    id                BIGSERIAL       PRIMARY KEY,
    public_id         UUID            NOT NULL DEFAULT gen_random_uuid(),
    actor_id          VARCHAR(100)    NOT NULL,
    actor_scope       VARCHAR(20)     NOT NULL,
    action            VARCHAR(100)    NOT NULL,
    entity_type       VARCHAR(100)    NOT NULL,
    entity_id         VARCHAR(100)    NOT NULL,
    organization_id   VARCHAR(100),
    club_id           VARCHAR(100),
    changes_json      TEXT,
    ip_address        VARCHAR(45),
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_audit_logs_public_id
        UNIQUE (public_id)
);

COMMENT ON TABLE audit_logs IS
    'Append-only audit trail recording every significant write operation. '
    'No updates, no deletes. Immutable after insert.';

COMMENT ON COLUMN audit_logs.actor_id IS 'User public ID (UUID) or "system" for automated operations.';
COMMENT ON COLUMN audit_logs.actor_scope IS 'platform | club | trainer | member | system';
COMMENT ON COLUMN audit_logs.changes_json IS 'Compact JSON of changed fields: {"field": ["old", "new"]}. Truncated at 4000 chars.';

-- Indexes
CREATE INDEX idx_audit_logs_actor_id    ON audit_logs(actor_id);
CREATE INDEX idx_audit_logs_action      ON audit_logs(action);
CREATE INDEX idx_audit_logs_entity      ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_org_id      ON audit_logs(organization_id);
CREATE INDEX idx_audit_logs_created_at  ON audit_logs(created_at DESC);
