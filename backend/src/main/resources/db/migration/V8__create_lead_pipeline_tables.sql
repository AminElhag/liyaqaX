-- ============================================================
-- V8__create_lead_pipeline_tables.sql
-- Create tables for the lead pipeline: configurable lead sources,
-- leads with stage tracking, and append-only lead notes.
-- ============================================================

-- ── Lead Sources (club-configurable) ────────────────────────

CREATE TABLE lead_sources (

    -- Standard columns
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ     NULL,

    -- Tenant scope (club-level)
    organization_id BIGINT          NOT NULL,
    club_id         BIGINT          NOT NULL,

    -- Domain columns
    name            VARCHAR(100)    NOT NULL,
    name_ar         VARCHAR(100)    NOT NULL,
    -- color: hex color for badge display (e.g. "#10B981")
    color           VARCHAR(7)      NOT NULL DEFAULT '#6B7280',
    is_active       BOOLEAN         NOT NULL DEFAULT true,
    display_order   INTEGER         NOT NULL DEFAULT 0,

    -- Constraints
    CONSTRAINT uq_lead_sources_public_id
        UNIQUE (public_id),

    CONSTRAINT uq_lead_sources_club_name
        UNIQUE (club_id, name),

    CONSTRAINT fk_lead_sources_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_lead_sources_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE lead_sources IS
    'Club-configurable lead source definitions (e.g. Walk-in, Referral, '
    'Social Media). Each club manages its own set of sources. Sources are '
    'deactivated, never deleted.';

-- Indexes
CREATE UNIQUE INDEX idx_lead_sources_public_id_active
    ON lead_sources(public_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_lead_sources_organization_id_active
    ON lead_sources(organization_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_lead_sources_club_id_active
    ON lead_sources(club_id)
    WHERE deleted_at IS NULL;

-- updated_at trigger
CREATE TRIGGER trg_lead_sources_set_updated_at
    BEFORE UPDATE ON lead_sources
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ── Leads ───────────────────────────────────────────────────

CREATE TABLE leads (

    -- Standard columns
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ     NULL,

    -- Tenant scope
    organization_id     BIGINT      NOT NULL,
    club_id             BIGINT      NOT NULL,
    branch_id           BIGINT      NULL,

    -- References
    lead_source_id      BIGINT      NULL,
    assigned_staff_id   BIGINT      NULL,
    converted_member_id BIGINT      NULL,

    -- Contact information
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    first_name_ar       VARCHAR(100) NULL,
    last_name_ar        VARCHAR(100) NULL,
    phone               VARCHAR(20)  NULL,
    email               VARCHAR(255) NULL,
    -- gender: male | female
    gender              VARCHAR(10)  NULL
        CONSTRAINT chk_leads_gender
        CHECK (gender IN ('male', 'female')),
    notes               TEXT         NULL,

    -- Stage tracking
    -- stage: new | contacted | interested | converted | lost
    stage               VARCHAR(20)  NOT NULL DEFAULT 'new'
        CONSTRAINT chk_leads_stage
        CHECK (stage IN ('new', 'contacted', 'interested', 'converted', 'lost')),
    lost_reason         TEXT         NULL,

    -- Stage timestamps
    contacted_at        TIMESTAMPTZ  NULL,
    interested_at       TIMESTAMPTZ  NULL,
    converted_at        TIMESTAMPTZ  NULL,
    lost_at             TIMESTAMPTZ  NULL,

    -- Constraints
    CONSTRAINT uq_leads_public_id
        UNIQUE (public_id),

    CONSTRAINT fk_leads_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_leads_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_leads_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_leads_source
        FOREIGN KEY (lead_source_id)
        REFERENCES lead_sources(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_leads_assigned_staff
        FOREIGN KEY (assigned_staff_id)
        REFERENCES staff_members(id)
        ON DELETE SET NULL
        ON UPDATE CASCADE,

    CONSTRAINT fk_leads_converted_member
        FOREIGN KEY (converted_member_id)
        REFERENCES members(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE leads IS
    'A prospective gym member moving through the sales pipeline. '
    'Stages: new → contacted → interested → converted/lost. '
    'Converting a lead creates a Member record and links it via '
    'converted_member_id.';

-- Indexes
CREATE UNIQUE INDEX idx_leads_public_id_active
    ON leads(public_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_leads_organization_id_active
    ON leads(organization_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_leads_club_id_active
    ON leads(club_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_leads_branch_id_active
    ON leads(branch_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_leads_source_id_active
    ON leads(lead_source_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_leads_assigned_staff_id_active
    ON leads(assigned_staff_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_leads_stage_active
    ON leads(club_id, stage)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_leads_created_at_active
    ON leads(club_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- updated_at trigger
CREATE TRIGGER trg_leads_set_updated_at
    BEFORE UPDATE ON leads
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ── Lead Notes (append-only) ────────────────────────────────

CREATE TABLE lead_notes (

    -- Standard columns (no deleted_at — notes are append-only, immutable)
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- References
    lead_id         BIGINT          NOT NULL,
    staff_id        BIGINT          NOT NULL,

    -- Domain columns
    body            TEXT            NOT NULL,

    -- Constraints
    CONSTRAINT uq_lead_notes_public_id
        UNIQUE (public_id),

    CONSTRAINT fk_lead_notes_lead
        FOREIGN KEY (lead_id)
        REFERENCES leads(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_lead_notes_staff
        FOREIGN KEY (staff_id)
        REFERENCES staff_members(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE lead_notes IS
    'Append-only notes on a lead. Notes are never edited or deleted — '
    'they serve as a permanent audit trail of all interactions.';

-- Indexes
CREATE INDEX idx_lead_notes_lead_id
    ON lead_notes(lead_id);

CREATE INDEX idx_lead_notes_staff_id
    ON lead_notes(staff_id);

-- updated_at trigger
CREATE TRIGGER trg_lead_notes_set_updated_at
    BEFORE UPDATE ON lead_notes
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
