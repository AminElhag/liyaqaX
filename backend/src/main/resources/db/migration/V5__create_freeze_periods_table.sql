-- ============================================================
-- V5__create_freeze_periods_table.sql
-- Create freeze_periods table to track individual freeze
-- periods for audit trail and freeze days calculation.
-- ============================================================

-- ── Membership lifecycle: freeze tracking ───────────────────

CREATE TABLE freeze_periods (

    -- Standard columns
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Tenant scope
    organization_id BIGINT          NOT NULL,

    -- Domain columns
    membership_id   BIGINT          NOT NULL,
    member_id       BIGINT          NOT NULL,
    freeze_start_date DATE          NOT NULL,
    freeze_end_date   DATE          NOT NULL,
    -- actual_end_date: null if freeze not yet ended; set when unfreeze is called
    actual_end_date   DATE          NULL,
    -- duration_days: freeze_end_date - freeze_start_date
    duration_days     INTEGER       NOT NULL,
    reason            TEXT          NULL,
    requested_by_id   BIGINT        NOT NULL,

    -- Constraints
    CONSTRAINT uq_freeze_periods_public_id
        UNIQUE (public_id),

    CONSTRAINT fk_freeze_periods_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_freeze_periods_membership
        FOREIGN KEY (membership_id)
        REFERENCES memberships(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_freeze_periods_member
        FOREIGN KEY (member_id)
        REFERENCES members(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_freeze_periods_requested_by
        FOREIGN KEY (requested_by_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_freeze_periods_duration_positive
        CHECK (duration_days > 0),

    CONSTRAINT chk_freeze_periods_dates_valid
        CHECK (freeze_end_date > freeze_start_date)
);

COMMENT ON TABLE freeze_periods IS
    'Tracks individual freeze periods on a membership for audit trail '
    'and freeze days calculation. Each row represents one freeze request. '
    'actual_end_date is set when the freeze is ended early via unfreeze.';

-- Indexes
CREATE INDEX idx_freeze_periods_organization_id
    ON freeze_periods(organization_id);

CREATE INDEX idx_freeze_periods_membership_id
    ON freeze_periods(membership_id);

CREATE INDEX idx_freeze_periods_member_id
    ON freeze_periods(member_id);

-- updated_at trigger
CREATE TRIGGER trg_freeze_periods_set_updated_at
    BEFORE UPDATE ON freeze_periods
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
