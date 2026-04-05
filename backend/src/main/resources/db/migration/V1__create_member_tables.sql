-- ============================================================
-- V1__create_member_tables.sql
-- Create tables for the member domain: members, emergency
-- contacts, health waivers, and waiver signatures.
-- ============================================================

-- ── Reusable trigger function ───────────────────────────────

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ── Members ─────────────────────────────────────────────────

CREATE TABLE members (

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

    -- User reference
    user_id         BIGINT          NOT NULL,

    -- Domain columns
    first_name_ar   VARCHAR(100)    NOT NULL,
    first_name_en   VARCHAR(100)    NOT NULL,
    last_name_ar    VARCHAR(100)    NOT NULL,
    last_name_en    VARCHAR(100)    NOT NULL,
    phone           VARCHAR(50)     NOT NULL,
    national_id     VARCHAR(50)     NULL,
    date_of_birth   DATE            NULL,
    -- gender: male | female | unspecified
    gender          VARCHAR(20)     NULL
        CONSTRAINT chk_members_gender
        CHECK (gender IN ('male', 'female', 'unspecified')),
    -- membership_status: pending | active | frozen | expired | terminated
    membership_status VARCHAR(50)   NOT NULL DEFAULT 'pending'
        CONSTRAINT chk_members_membership_status
        CHECK (membership_status IN ('pending', 'active', 'frozen', 'expired', 'terminated')),
    notes           TEXT            NULL,
    joined_at       DATE            NOT NULL DEFAULT CURRENT_DATE,

    -- Constraints
    CONSTRAINT uq_members_public_id
        UNIQUE (public_id),

    CONSTRAINT uq_members_user_id
        UNIQUE (user_id),

    CONSTRAINT fk_members_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_members_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_members_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_members_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE members IS
    'A person registered as a club member. One user has at most one member '
    'profile. Member status starts as pending and transitions via payments '
    'and staff actions.';

-- Indexes
CREATE UNIQUE INDEX idx_members_public_id_active
    ON members(public_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_members_organization_id_active
    ON members(organization_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_members_club_id_active
    ON members(club_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_members_branch_id_active
    ON members(branch_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_members_user_id
    ON members(user_id);

CREATE INDEX idx_members_membership_status_active
    ON members(organization_id, club_id, membership_status)
    WHERE deleted_at IS NULL;

-- updated_at trigger
CREATE TRIGGER trg_members_set_updated_at
    BEFORE UPDATE ON members
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ── Emergency contacts ──────────────────────────────────────

CREATE TABLE emergency_contacts (

    -- Standard columns (no deleted_at — append-only)
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- References
    member_id       BIGINT          NOT NULL,
    organization_id BIGINT          NOT NULL,

    -- Domain columns
    name_ar         VARCHAR(255)    NOT NULL,
    name_en         VARCHAR(255)    NOT NULL,
    phone           VARCHAR(50)     NOT NULL,
    relationship    VARCHAR(100)    NULL,

    -- Constraints
    CONSTRAINT uq_emergency_contacts_public_id
        UNIQUE (public_id),

    CONSTRAINT fk_emergency_contacts_member
        FOREIGN KEY (member_id)
        REFERENCES members(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_emergency_contacts_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE emergency_contacts IS
    'Emergency contact for a member. At least one is required at '
    'registration. No soft delete — contacts are removed via hard delete.';

-- Indexes
CREATE INDEX idx_emergency_contacts_member_id
    ON emergency_contacts(member_id);

CREATE INDEX idx_emergency_contacts_organization_id
    ON emergency_contacts(organization_id);

-- updated_at trigger
CREATE TRIGGER trg_emergency_contacts_set_updated_at
    BEFORE UPDATE ON emergency_contacts
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ── Health waivers ──────────────────────────────────────────

CREATE TABLE health_waivers (

    -- Standard columns
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ     NULL,

    -- Tenant scope
    organization_id BIGINT          NOT NULL,
    club_id         BIGINT          NOT NULL,

    -- Domain columns
    content_ar      TEXT            NOT NULL,
    content_en      TEXT            NOT NULL,
    version         INTEGER         NOT NULL DEFAULT 1,
    is_active       BOOLEAN         NOT NULL DEFAULT true,

    -- Constraints
    CONSTRAINT uq_health_waivers_public_id
        UNIQUE (public_id),

    CONSTRAINT fk_health_waivers_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_health_waivers_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE health_waivers IS
    'Club-level health waiver document. Versioned — when text is updated, '
    'a new version is created and all members must re-sign. Only one active '
    'waiver per club at a time.';

-- Indexes
CREATE INDEX idx_health_waivers_organization_id_active
    ON health_waivers(organization_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_health_waivers_club_id_active
    ON health_waivers(club_id, is_active)
    WHERE deleted_at IS NULL;

-- updated_at trigger
CREATE TRIGGER trg_health_waivers_set_updated_at
    BEFORE UPDATE ON health_waivers
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ── Waiver signatures ───────────────────────────────────────

CREATE TABLE waiver_signatures (

    -- Standard columns (no deleted_at — immutable records)
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- References
    member_id       BIGINT          NOT NULL,
    waiver_id       BIGINT          NOT NULL,
    organization_id BIGINT          NOT NULL,

    -- Domain columns
    signed_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    ip_address      VARCHAR(50)     NULL,

    -- Constraints
    CONSTRAINT uq_waiver_signatures_public_id
        UNIQUE (public_id),

    CONSTRAINT uq_waiver_signatures_member_waiver
        UNIQUE (member_id, waiver_id),

    CONSTRAINT fk_waiver_signatures_member
        FOREIGN KEY (member_id)
        REFERENCES members(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_waiver_signatures_waiver
        FOREIGN KEY (waiver_id)
        REFERENCES health_waivers(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_waiver_signatures_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE waiver_signatures IS
    'Immutable record that a member signed a specific waiver version. '
    'Never deleted — signatures are permanent audit records.';

-- Indexes
CREATE INDEX idx_waiver_signatures_member_id
    ON waiver_signatures(member_id);

CREATE INDEX idx_waiver_signatures_waiver_id
    ON waiver_signatures(waiver_id);

CREATE INDEX idx_waiver_signatures_organization_id
    ON waiver_signatures(organization_id);
