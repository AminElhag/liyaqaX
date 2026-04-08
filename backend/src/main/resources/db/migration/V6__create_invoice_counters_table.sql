-- ============================================================
-- V6__create_invoice_counters_table.sql
-- Create invoice_counters table for atomic sequential invoice
-- numbering per club, required by ZATCA Phase 1 compliance.
-- ============================================================

-- ── Invoice counters ────────────────────────────────────────

CREATE TABLE invoice_counters (
    -- Standard columns
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ     NULL,

    -- Domain columns
    club_id         BIGINT          NOT NULL,
    last_value      BIGINT          NOT NULL DEFAULT 0,

    -- Constraints
    CONSTRAINT uq_invoice_counters_public_id
        UNIQUE (public_id),

    CONSTRAINT uq_invoice_counters_club_id
        UNIQUE (club_id),

    CONSTRAINT fk_invoice_counters_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE invoice_counters IS
    'Atomic sequential counter per club for ZATCA-compliant invoice numbering. '
    'Counter can only increment, never decrement or reset.';

COMMENT ON COLUMN invoice_counters.last_value IS
    'The last issued invoice counter value for this club. '
    'Incremented atomically via UPDATE ... RETURNING.';

-- updated_at trigger
CREATE TRIGGER trg_invoice_counters_set_updated_at
    BEFORE UPDATE ON invoice_counters
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
