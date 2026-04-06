-- ============================================================
-- V9__create_cash_drawer_tables.sql
-- Create tables for shift-based cash drawer reconciliation:
-- sessions (open/close/reconcile workflow) and entries
-- (individual cash movements within a session).
-- ============================================================

-- ── Cash Drawer Sessions ───────────────────────────────────

CREATE TABLE cash_drawer_sessions (

    -- Standard columns
    id                      BIGSERIAL       PRIMARY KEY,
    public_id               UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ     NULL,

    -- Tenant scope
    organization_id         BIGINT          NOT NULL,
    club_id                 BIGINT          NOT NULL,
    branch_id               BIGINT          NOT NULL,

    -- Staff references
    opened_by_staff_id      BIGINT          NOT NULL,
    closed_by_staff_id      BIGINT          NULL,
    reconciled_by_staff_id  BIGINT          NULL,

    -- Session state
    -- status: open | closed | reconciled
    status                  VARCHAR(20)     NOT NULL DEFAULT 'open'
        CONSTRAINT chk_cash_drawer_sessions_status
        CHECK (status IN ('open', 'closed', 'reconciled')),

    -- Monetary values (halalas)
    opening_float_halalas       BIGINT      NOT NULL DEFAULT 0,
    counted_closing_halalas     BIGINT      NULL,
    expected_closing_halalas    BIGINT      NULL,
    difference_halalas          BIGINT      NULL,

    -- Reconciliation
    -- reconciliation_status: approved | flagged
    reconciliation_status   VARCHAR(20)     NULL
        CONSTRAINT chk_cash_drawer_sessions_reconciliation_status
        CHECK (reconciliation_status IN ('approved', 'flagged')),
    reconciliation_notes    TEXT            NULL,

    -- Timestamps
    opened_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    closed_at               TIMESTAMPTZ     NULL,
    reconciled_at           TIMESTAMPTZ     NULL,

    -- Constraints
    CONSTRAINT uq_cash_drawer_sessions_public_id
        UNIQUE (public_id),

    CONSTRAINT fk_cash_drawer_sessions_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_cash_drawer_sessions_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_cash_drawer_sessions_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_cash_drawer_sessions_opened_by
        FOREIGN KEY (opened_by_staff_id)
        REFERENCES staff_members(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_cash_drawer_sessions_closed_by
        FOREIGN KEY (closed_by_staff_id)
        REFERENCES staff_members(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_cash_drawer_sessions_reconciled_by
        FOREIGN KEY (reconciled_by_staff_id)
        REFERENCES staff_members(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE cash_drawer_sessions IS
    'A shift-based cash drawer session for a branch. One open session per '
    'branch at a time. Workflow: open → closed → reconciled. Entries are '
    'frozen after close. Reconciliation sets approved or flagged status.';

-- Indexes
CREATE UNIQUE INDEX idx_cash_drawer_sessions_public_id_active
    ON cash_drawer_sessions(public_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_cash_drawer_sessions_organization_id_active
    ON cash_drawer_sessions(organization_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_cash_drawer_sessions_club_id_active
    ON cash_drawer_sessions(club_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_cash_drawer_sessions_branch_id_active
    ON cash_drawer_sessions(branch_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_cash_drawer_sessions_branch_status_active
    ON cash_drawer_sessions(branch_id, status)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_cash_drawer_sessions_opened_at_active
    ON cash_drawer_sessions(club_id, opened_at DESC)
    WHERE deleted_at IS NULL;

-- updated_at trigger
CREATE TRIGGER trg_cash_drawer_sessions_set_updated_at
    BEFORE UPDATE ON cash_drawer_sessions
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ── Cash Drawer Entries (append-only) ──────────────────────

CREATE TABLE cash_drawer_entries (

    -- Standard columns (no deleted_at — entries are append-only, immutable)
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- References
    session_id      BIGINT          NOT NULL,
    staff_id        BIGINT          NOT NULL,
    payment_id      BIGINT          NULL,

    -- Domain columns
    -- entry_type: cash_in | cash_out | float_adjustment
    entry_type      VARCHAR(20)     NOT NULL
        CONSTRAINT chk_cash_drawer_entries_entry_type
        CHECK (entry_type IN ('cash_in', 'cash_out', 'float_adjustment')),
    amount_halalas  BIGINT          NOT NULL
        CONSTRAINT chk_cash_drawer_entries_amount_positive
        CHECK (amount_halalas > 0),
    description     VARCHAR(255)    NOT NULL,
    recorded_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT uq_cash_drawer_entries_public_id
        UNIQUE (public_id),

    CONSTRAINT fk_cash_drawer_entries_session
        FOREIGN KEY (session_id)
        REFERENCES cash_drawer_sessions(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_cash_drawer_entries_staff
        FOREIGN KEY (staff_id)
        REFERENCES staff_members(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_cash_drawer_entries_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE cash_drawer_entries IS
    'Individual cash movements within a drawer session. Append-only — '
    'entries are never edited or deleted. Immutable after session close.';

-- Indexes
CREATE INDEX idx_cash_drawer_entries_session_id
    ON cash_drawer_entries(session_id);

CREATE INDEX idx_cash_drawer_entries_staff_id
    ON cash_drawer_entries(staff_id);

CREATE INDEX idx_cash_drawer_entries_session_type
    ON cash_drawer_entries(session_id, entry_type);

-- updated_at trigger
CREATE TRIGGER trg_cash_drawer_entries_set_updated_at
    BEFORE UPDATE ON cash_drawer_entries
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
