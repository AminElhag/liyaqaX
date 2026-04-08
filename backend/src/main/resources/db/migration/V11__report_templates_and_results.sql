-- ============================================================
-- V11__report_templates_and_results.sql
-- Create tables for the custom report builder: saved report
-- templates per club, and their last-run result snapshots.
-- ============================================================

-- ── Report Templates ────────────────────────────────────────

CREATE TABLE report_templates (
    -- Standard columns
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ     NULL,

    -- Tenant scope
    club_id         BIGINT          NOT NULL,

    -- Domain columns
    name            VARCHAR(200)    NOT NULL,
    description     VARCHAR(500)    NULL,
    -- metrics: JSON array of metric codes e.g. ["revenue","new_members"]
    metrics         TEXT            NOT NULL,
    -- dimensions: JSON array of dimension codes e.g. ["month","branch"]
    dimensions      TEXT            NOT NULL,
    -- filters: JSON object of filter config e.g. {"branch_id": null, "plan_id": null}
    filters         TEXT            NULL,
    -- metric_scope: revenue | leads | members | gx | pt | cash — gates role access
    metric_scope    VARCHAR(50)     NULL,
    is_system       BOOLEAN         NOT NULL DEFAULT false,

    -- Constraints
    CONSTRAINT uq_report_templates_public_id
        UNIQUE (public_id),

    CONSTRAINT fk_report_templates_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE report_templates IS
    'Saved custom report definitions created by club owners and managers. '
    'Each template stores the selected metrics, dimensions, and filters. '
    'Scoped to a single club via club_id.';

COMMENT ON COLUMN report_templates.metrics IS 'JSON array of metric codes from MetricCatalogue.';
COMMENT ON COLUMN report_templates.dimensions IS 'JSON array of dimension codes from DimensionCatalogue.';
COMMENT ON COLUMN report_templates.filters IS 'JSON object of filter configuration. Null values mean no filter applied.';
COMMENT ON COLUMN report_templates.metric_scope IS 'Primary scope of metrics in this template. Used to gate access for restricted roles.';

-- Indexes
CREATE INDEX idx_report_templates_club_id
    ON report_templates(club_id)
    WHERE deleted_at IS NULL;

-- updated_at trigger
CREATE TRIGGER trg_report_templates_set_updated_at
    BEFORE UPDATE ON report_templates
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ── Report Results ──────────────────────────────────────────

CREATE TABLE report_results (
    -- Standard columns
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ     NULL,

    -- Domain columns
    template_id     BIGINT          NOT NULL,
    run_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    run_by_user_id  VARCHAR(100)    NOT NULL,
    date_from       DATE            NOT NULL,
    date_to         DATE            NOT NULL,
    -- result_json: JSON array of row objects
    result_json     TEXT            NOT NULL,
    row_count       INTEGER         NOT NULL,
    truncated       BOOLEAN         NOT NULL DEFAULT false,
    -- run_params_hash: SHA-256 of run-time parameters for cache key
    run_params_hash VARCHAR(64)     NULL,

    -- Constraints
    CONSTRAINT uq_report_results_public_id
        UNIQUE (public_id),

    CONSTRAINT fk_report_results_template
        FOREIGN KEY (template_id)
        REFERENCES report_templates(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE report_results IS
    'Last-run snapshot for each report template. Only the most recent active '
    'result per template is kept; older results are soft-deleted when a new run completes.';

COMMENT ON COLUMN report_results.run_by_user_id IS 'User public ID (UUID from JWT) who triggered the run.';
COMMENT ON COLUMN report_results.result_json IS 'JSON array of row objects representing the report output.';
COMMENT ON COLUMN report_results.run_params_hash IS 'SHA-256 of templateId + dateFrom + dateTo + filters. Used as part of the Redis cache key.';

-- Indexes
CREATE INDEX idx_report_results_template_id
    ON report_results(template_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_report_results_run_at
    ON report_results(run_at DESC);

-- updated_at trigger
CREATE TRIGGER trg_report_results_set_updated_at
    BEFORE UPDATE ON report_results
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
