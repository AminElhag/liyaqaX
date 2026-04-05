-- ============================================================
-- V7__add_zatca_phase1_columns.sql
-- Add ZATCA Phase 1 columns to invoices and clubs tables
-- for PIH chain, invoice counter, and VAT number support.
-- ============================================================

-- ── Invoices — add PIH and counter columns ──────────────────

ALTER TABLE invoices
    ADD COLUMN previous_invoice_hash TEXT NULL;

ALTER TABLE invoices
    ADD COLUMN invoice_counter_value BIGINT NULL;

COMMENT ON COLUMN invoices.previous_invoice_hash IS
    'Hash of the previous invoice in this club PIH chain. '
    'First invoice uses the ZATCA SDK initial PIH constant.';

COMMENT ON COLUMN invoices.invoice_counter_value IS
    'Sequential counter value per club, required by ZATCA. '
    'Cannot be decremented or reset.';

-- ── Invoices — update zatca_status CHECK to include generated ─

ALTER TABLE invoices
    DROP CONSTRAINT chk_invoices_zatca_status;

ALTER TABLE invoices
    ADD CONSTRAINT chk_invoices_zatca_status
    CHECK (zatca_status IN ('pending', 'generated', 'submitted', 'accepted', 'rejected'));

-- ── Clubs — add VAT number ──────────────────────────────────

ALTER TABLE clubs
    ADD COLUMN vat_number VARCHAR(50) NULL;

COMMENT ON COLUMN clubs.vat_number IS
    'ZATCA-registered VAT number for this club. '
    'Falls back to organization VAT number if null.';
