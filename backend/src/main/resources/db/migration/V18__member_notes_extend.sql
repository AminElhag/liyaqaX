-- ============================================================
-- V18__member_notes_extend.sql
-- Create the member_notes table for general-purpose member notes
-- and activity timeline (Plan 32 — Member Notes & Activity Timeline)
-- ============================================================

-- ── Member Notes ────────────────────────────────────────────

CREATE TABLE member_notes (
    -- Standard columns
    id                  BIGSERIAL       PRIMARY KEY,
    public_id           UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ     NULL,

    -- Tenant scope
    organization_id     BIGINT          NOT NULL,
    club_id             BIGINT          NOT NULL,

    -- Domain columns
    member_id           BIGINT          NOT NULL,
    created_by_user_id  BIGINT          NOT NULL,
    -- note_type: GENERAL | HEALTH | COMPLAINT | FOLLOW_UP | REJECTION
    note_type           VARCHAR(20)     NOT NULL DEFAULT 'GENERAL',
    content             TEXT            NOT NULL,
    follow_up_at        TIMESTAMPTZ     NULL,

    -- Constraints
    CONSTRAINT uq_member_notes_public_id
        UNIQUE (public_id),

    CONSTRAINT fk_member_notes_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_member_notes_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_member_notes_member
        FOREIGN KEY (member_id)
        REFERENCES members(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_member_notes_created_by
        FOREIGN KEY (created_by_user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_member_notes_note_type
        CHECK (note_type IN ('GENERAL', 'HEALTH', 'COMPLAINT', 'FOLLOW_UP', 'REJECTION'))
);

COMMENT ON TABLE member_notes IS
    'Staff and trainer notes attached to a member. Append-only (no edit), soft-deleted. '
    'Used for general observations, health notes, complaints, follow-ups, and registration rejection reasons.';

-- Indexes
CREATE INDEX idx_member_notes_member_id ON member_notes(member_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_member_notes_organization_id ON member_notes(organization_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_member_notes_follow_up_at ON member_notes(follow_up_at)
    WHERE follow_up_at IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_member_notes_member_type ON member_notes(member_id, note_type)
    WHERE deleted_at IS NULL;

-- updated_at trigger
CREATE TRIGGER trg_member_notes_set_updated_at
    BEFORE UPDATE ON member_notes
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
