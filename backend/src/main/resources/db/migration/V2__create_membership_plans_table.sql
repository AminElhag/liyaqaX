-- ============================================================
-- V2__create_membership_plans_table.sql
-- Create the membership_plans table for the plan catalog.
-- Plans define the price, duration, and rules for memberships
-- that a club offers to members.
-- ============================================================

-- ── Membership plans ────────────────────────────────────────

CREATE TABLE membership_plans (

    -- Standard columns
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ     NULL,

    -- Tenant scope (plan belongs to a club, not a branch)
    organization_id BIGINT          NOT NULL,
    club_id         BIGINT          NOT NULL,

    -- Domain columns
    name_ar              VARCHAR(255) NOT NULL,
    name_en              VARCHAR(255) NOT NULL,
    description_ar       TEXT         NULL,
    description_en       TEXT         NULL,
    price_halalas        BIGINT       NOT NULL,
    duration_days        INTEGER      NOT NULL,
    grace_period_days    INTEGER      NOT NULL DEFAULT 0,
    freeze_allowed       BOOLEAN      NOT NULL DEFAULT true,
    max_freeze_days      INTEGER      NOT NULL DEFAULT 30,
    gx_classes_included  BOOLEAN      NOT NULL DEFAULT true,
    pt_sessions_included BOOLEAN      NOT NULL DEFAULT false,
    is_active            BOOLEAN      NOT NULL DEFAULT true,
    sort_order           INTEGER      NOT NULL DEFAULT 0,

    -- Constraints
    CONSTRAINT uq_membership_plans_public_id
        UNIQUE (public_id),

    CONSTRAINT fk_membership_plans_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_membership_plans_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_membership_plans_price_positive
        CHECK (price_halalas > 0),

    CONSTRAINT chk_membership_plans_duration_positive
        CHECK (duration_days > 0),

    CONSTRAINT chk_membership_plans_grace_period
        CHECK (grace_period_days >= 0),

    CONSTRAINT chk_membership_plans_max_freeze_days
        CHECK (max_freeze_days >= 0)
);

COMMENT ON TABLE membership_plans IS
    'Catalog of membership plan templates offered by a club. '
    'Plans define price, duration, freeze limits, and included features. '
    'A plan belongs to a club — members from any branch can be assigned '
    'any of the club''s plans.';

COMMENT ON COLUMN membership_plans.price_halalas IS
    'Plan price in halalas (1 SAR = 100 halalas). Must be positive.';

COMMENT ON COLUMN membership_plans.grace_period_days IS
    'Number of days after expiry during which the member retains access. '
    'Zero means no grace period.';

COMMENT ON COLUMN membership_plans.max_freeze_days IS
    'Maximum number of freeze days allowed per year. '
    'Must be zero when freeze_allowed is false.';

COMMENT ON COLUMN membership_plans.is_active IS
    'Inactive plans cannot be assigned to new members but existing '
    'memberships continue until they expire.';

-- Indexes
CREATE UNIQUE INDEX idx_membership_plans_public_id_active
    ON membership_plans(public_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_membership_plans_organization_id_active
    ON membership_plans(organization_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_membership_plans_club_id_active
    ON membership_plans(club_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_membership_plans_club_sort
    ON membership_plans(club_id, sort_order)
    WHERE deleted_at IS NULL;

-- Name uniqueness within a club (active, non-deleted plans only)
CREATE UNIQUE INDEX idx_membership_plans_club_name_en_active
    ON membership_plans(club_id, name_en)
    WHERE deleted_at IS NULL;

-- updated_at trigger
CREATE TRIGGER trg_membership_plans_set_updated_at
    BEFORE UPDATE ON membership_plans
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
