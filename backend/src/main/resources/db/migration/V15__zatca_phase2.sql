-- ============================================================
-- V15__zatca_phase2.sql
-- Add per-club ZATCA certificates table and extend invoices
-- for Phase 2 FATOORA API integration.
-- ============================================================

-- ── ZATCA certificates per club ──────────────────────────────

CREATE TABLE club_zatca_certificates (
    id                      BIGSERIAL       PRIMARY KEY,
    public_id               UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ     NULL,

    -- One certificate record per club (UNIQUE)
    club_id                 BIGINT          NOT NULL UNIQUE,

    -- sandbox | production
    environment             VARCHAR(20)     NOT NULL DEFAULT 'sandbox',

    -- CSR and encrypted private key
    csr_pem                 TEXT,
    private_key_encrypted   TEXT            NOT NULL DEFAULT '',

    -- Compliance CSID response fields
    compliance_request_id   VARCHAR(255),
    compliance_binary_token TEXT,
    compliance_secret       VARCHAR(255),

    -- Production CSID response fields
    production_request_id   VARCHAR(255),
    production_binary_token TEXT,
    production_secret       VARCHAR(255),

    -- Decoded certificate info
    certificate_pem         TEXT,
    serial_number           VARCHAR(255),

    -- onboarding_status: pending | compliance_issued | compliance_checked | active | expired | revoked
    onboarding_status       VARCHAR(50)     NOT NULL DEFAULT 'pending',
    csid_expires_at         TIMESTAMPTZ,

    CONSTRAINT fk_club_zatca_certificates_club
        FOREIGN KEY (club_id) REFERENCES clubs(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
);

COMMENT ON TABLE club_zatca_certificates IS
    'Per-club ZATCA CSID certificates for Phase 2 e-invoicing. '
    'Each club has at most one active certificate record.';

CREATE INDEX idx_club_zatca_certificates_club_id
    ON club_zatca_certificates(club_id)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_club_zatca_certificates_set_updated_at
    BEFORE UPDATE ON club_zatca_certificates
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ── Extend invoices for Phase 2 reporting ────────────────────

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS zatca_signed_xml       TEXT,
    ADD COLUMN IF NOT EXISTS zatca_reported_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS zatca_report_response   TEXT,
    ADD COLUMN IF NOT EXISTS zatca_retry_count       INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS zatca_last_error        TEXT;

-- Index for the scheduler query (find unreported invoices)
-- Invoices are immutable (no deleted_at), so no partial index needed
CREATE INDEX idx_invoices_zatca_status ON invoices(zatca_status);
