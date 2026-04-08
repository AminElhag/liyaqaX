-- ============================================================
-- V3__create_membership_payment_invoice_tables.sql
-- Create tables for membership instances, payments, and invoices
-- to support the membership assignment flow.
-- ============================================================

-- ── Memberships ─────────────────────────────────────────────

CREATE TABLE memberships (
    -- Standard columns
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ     NULL,

    -- Tenant scope
    organization_id BIGINT          NOT NULL,
    club_id         BIGINT          NOT NULL,
    branch_id       BIGINT          NOT NULL,

    -- Domain columns
    member_id       BIGINT          NOT NULL,
    plan_id         BIGINT          NOT NULL,
    -- membership_status: pending | active | frozen | expired | terminated
    membership_status VARCHAR(50)   NOT NULL DEFAULT 'pending'
        CONSTRAINT chk_memberships_status
        CHECK (membership_status IN ('pending', 'active', 'frozen', 'expired', 'terminated')),
    start_date      DATE            NOT NULL,
    end_date        DATE            NOT NULL,
    grace_end_date  DATE            NULL,
    freeze_days_used INTEGER        NOT NULL DEFAULT 0,
    notes           TEXT            NULL,

    -- Constraints
    CONSTRAINT uq_memberships_public_id
        UNIQUE (public_id),

    CONSTRAINT fk_memberships_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_memberships_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_memberships_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_memberships_member
        FOREIGN KEY (member_id)
        REFERENCES members(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_memberships_plan
        FOREIGN KEY (plan_id)
        REFERENCES membership_plans(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE memberships IS
    'An active or historical membership plan instance assigned to a member. '
    'One member may have multiple memberships over time but only one active '
    '(non-deleted, non-expired) membership at any point.';

-- Indexes
CREATE INDEX idx_memberships_organization_id_active
    ON memberships(organization_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_memberships_club_id_active
    ON memberships(club_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_memberships_branch_id_active
    ON memberships(branch_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_memberships_member_id_active
    ON memberships(member_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_memberships_plan_id
    ON memberships(plan_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_memberships_status_active
    ON memberships(organization_id, membership_status)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_memberships_end_date
    ON memberships(end_date)
    WHERE deleted_at IS NULL;

-- updated_at trigger
CREATE TRIGGER trg_memberships_set_updated_at
    BEFORE UPDATE ON memberships
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ── Payments ────────────────────────────────────────────────

CREATE TABLE payments (
    -- Standard columns (no deleted_at — payments are immutable)
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Tenant scope
    organization_id BIGINT          NOT NULL,
    club_id         BIGINT          NOT NULL,
    branch_id       BIGINT          NOT NULL,

    -- Domain columns
    member_id       BIGINT          NOT NULL,
    membership_id   BIGINT          NULL,
    amount_halalas  BIGINT          NOT NULL,
    -- payment_method: cash | card | bank-transfer | other
    payment_method  VARCHAR(50)     NOT NULL
        CONSTRAINT chk_payments_method
        CHECK (payment_method IN ('cash', 'card', 'bank-transfer', 'other')),
    reference_number VARCHAR(255)   NULL,
    collected_by_id BIGINT          NOT NULL,
    paid_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    notes           TEXT            NULL,

    -- Constraints
    CONSTRAINT uq_payments_public_id
        UNIQUE (public_id),

    CONSTRAINT fk_payments_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_payments_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_payments_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_payments_member
        FOREIGN KEY (member_id)
        REFERENCES members(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_payments_membership
        FOREIGN KEY (membership_id)
        REFERENCES memberships(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_payments_collected_by
        FOREIGN KEY (collected_by_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_payments_amount_positive
        CHECK (amount_halalas > 0)
);

COMMENT ON TABLE payments IS
    'Immutable record of money received from a member. '
    'Corrections are handled via refund + re-payment, never by editing. '
    'Payments are never soft-deleted or hard-deleted.';

-- Indexes
CREATE INDEX idx_payments_organization_id
    ON payments(organization_id);

CREATE INDEX idx_payments_club_id
    ON payments(club_id);

CREATE INDEX idx_payments_branch_id
    ON payments(branch_id);

CREATE INDEX idx_payments_member_id
    ON payments(member_id);

CREATE INDEX idx_payments_membership_id
    ON payments(membership_id);

CREATE INDEX idx_payments_paid_at
    ON payments(paid_at);

-- updated_at trigger
CREATE TRIGGER trg_payments_set_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ── Invoices ────────────────────────────────────────────────

CREATE TABLE invoices (
    -- Standard columns (no deleted_at — invoices are immutable)
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Tenant scope
    organization_id BIGINT          NOT NULL,
    club_id         BIGINT          NOT NULL,
    branch_id       BIGINT          NOT NULL,

    -- Domain columns
    member_id       BIGINT          NOT NULL,
    payment_id      BIGINT          NOT NULL,
    invoice_number  VARCHAR(100)    NOT NULL,
    subtotal_halalas BIGINT         NOT NULL,
    vat_rate        NUMERIC(5,4)    NOT NULL DEFAULT 0.1500,
    vat_amount_halalas BIGINT       NOT NULL,
    total_halalas   BIGINT          NOT NULL,
    issued_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- ZATCA fields (present but empty in this plan)
    -- zatca_status: pending | submitted | accepted | rejected
    zatca_status    VARCHAR(50)     NOT NULL DEFAULT 'pending'
        CONSTRAINT chk_invoices_zatca_status
        CHECK (zatca_status IN ('pending', 'submitted', 'accepted', 'rejected')),
    zatca_uuid      VARCHAR(255)    NULL,
    zatca_hash      VARCHAR(255)    NULL,
    zatca_qr_code   TEXT            NULL,

    -- Constraints
    CONSTRAINT uq_invoices_public_id
        UNIQUE (public_id),

    CONSTRAINT uq_invoices_invoice_number
        UNIQUE (invoice_number),

    CONSTRAINT uq_invoices_payment_id
        UNIQUE (payment_id),

    CONSTRAINT fk_invoices_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_invoices_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_invoices_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_invoices_member
        FOREIGN KEY (member_id)
        REFERENCES members(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_invoices_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_invoices_subtotal_positive
        CHECK (subtotal_halalas > 0),

    CONSTRAINT chk_invoices_vat_non_negative
        CHECK (vat_amount_halalas >= 0),

    CONSTRAINT chk_invoices_total_positive
        CHECK (total_halalas > 0)
);

COMMENT ON TABLE invoices IS
    'Immutable invoice record generated for each payment. '
    'ZATCA fields are present but not populated until Plan 9. '
    'Invoices are never soft-deleted or hard-deleted.';

-- Indexes
CREATE INDEX idx_invoices_organization_id
    ON invoices(organization_id);

CREATE INDEX idx_invoices_club_id
    ON invoices(club_id);

CREATE INDEX idx_invoices_branch_id
    ON invoices(branch_id);

CREATE INDEX idx_invoices_member_id
    ON invoices(member_id);

CREATE INDEX idx_invoices_issued_at
    ON invoices(issued_at);

CREATE INDEX idx_invoices_club_year
    ON invoices(club_id, issued_at);

-- updated_at trigger
CREATE TRIGGER trg_invoices_set_updated_at
    BEFORE UPDATE ON invoices
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
