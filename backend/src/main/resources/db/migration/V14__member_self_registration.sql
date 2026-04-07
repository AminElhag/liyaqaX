-- Add self_registration_enabled to club_portal_settings
ALTER TABLE club_portal_settings
    ADD COLUMN self_registration_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Update members check constraint to include pending_activation
ALTER TABLE members
    DROP CONSTRAINT IF EXISTS members_membership_status_check;
ALTER TABLE members
    ADD CONSTRAINT members_membership_status_check
        CHECK (membership_status IN ('pending', 'active', 'frozen', 'expired', 'terminated', 'pending_activation'));

-- Member registration intents
CREATE TABLE member_registration_intents (
    id                              BIGSERIAL       PRIMARY KEY,
    public_id                       UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    member_id                       BIGINT          NOT NULL REFERENCES members(id),
    member_public_id                UUID            NOT NULL,
    membership_plan_id              BIGINT          REFERENCES membership_plans(id),
    membership_plan_public_id       UUID,
    membership_plan_name_en         VARCHAR(200),
    membership_plan_name_ar         VARCHAR(200),
    membership_plan_price_halalas   BIGINT,
    club_id                         BIGINT          NOT NULL,
    resolved_at                     TIMESTAMPTZ,
    resolved_by                     BIGINT          REFERENCES users(id),
    created_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at                      TIMESTAMPTZ
);

CREATE INDEX idx_mri_member_id   ON member_registration_intents(member_id);
CREATE INDEX idx_mri_club_id     ON member_registration_intents(club_id);
CREATE INDEX idx_mri_resolved_at ON member_registration_intents(resolved_at)
    WHERE resolved_at IS NULL;
