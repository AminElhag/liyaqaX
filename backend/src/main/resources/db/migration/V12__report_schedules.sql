-- ============================================================
-- V12__report_schedules.sql
-- Create report_schedules table for scheduled report email
-- delivery with PDF attachment.
-- ============================================================

-- ── Report Scheduling ──────────────────────────────────────

CREATE TABLE report_schedules (
    -- Standard columns
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ     NULL,

    -- Relationships
    template_id     BIGINT          NOT NULL,
    club_id         BIGINT          NOT NULL,

    -- Domain columns
    -- frequency: daily | weekly | monthly
    frequency       VARCHAR(20)     NOT NULL
        CONSTRAINT chk_report_schedules_frequency
        CHECK (frequency IN ('daily', 'weekly', 'monthly')),
    -- recipients_json: JSON array of email strings, max 10
    recipients_json VARCHAR(2000)   NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT true,
    last_run_at     TIMESTAMPTZ,
    -- last_run_status: success | failed
    last_run_status VARCHAR(20)
        CONSTRAINT chk_report_schedules_last_run_status
        CHECK (last_run_status IN ('success', 'failed')),
    last_error      TEXT,

    -- Constraints
    CONSTRAINT uq_report_schedules_public_id
        UNIQUE (public_id),

    CONSTRAINT uq_report_schedules_template_id
        UNIQUE (template_id),

    CONSTRAINT fk_report_schedules_template
        FOREIGN KEY (template_id)
        REFERENCES report_templates(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_report_schedules_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE report_schedules IS
    'Scheduled report email delivery configuration. One schedule per report template. '
    'Controls frequency (daily/weekly/monthly) and recipient list for automated report emails.';

COMMENT ON COLUMN report_schedules.frequency IS 'Delivery frequency: daily, weekly (Monday), or monthly (1st of month). All run at 07:00 Asia/Riyadh.';
COMMENT ON COLUMN report_schedules.recipients_json IS 'JSON array of email addresses. Maximum 10 entries.';
COMMENT ON COLUMN report_schedules.last_run_status IS 'Status of the most recent scheduled run: success or failed.';
COMMENT ON COLUMN report_schedules.last_error IS 'Error message from the most recent failed run. Truncated to 500 characters.';

-- Indexes
CREATE INDEX idx_report_schedules_club_id
    ON report_schedules(club_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_report_schedules_template_id
    ON report_schedules(template_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_report_schedules_is_active
    ON report_schedules(is_active)
    WHERE deleted_at IS NULL;

-- updated_at trigger
CREATE TRIGGER trg_report_schedules_set_updated_at
    BEFORE UPDATE ON report_schedules
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
