-- ============================================================
-- V22__online_payment_transactions.sql
-- Create online_payment_transactions table for Moyasar payment tracking.
-- Payment records are append-only — no deleted_at, no updated_at.
-- ============================================================

CREATE TABLE online_payment_transactions (
    id                   BIGSERIAL       PRIMARY KEY,
    public_id            UUID            NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    moyasar_id           VARCHAR(100)    NOT NULL UNIQUE,
    membership_id        BIGINT          NOT NULL REFERENCES memberships(id),
    member_id            BIGINT          NOT NULL REFERENCES members(id),
    club_id              BIGINT          NOT NULL REFERENCES clubs(id),
    amount_halalas       BIGINT          NOT NULL,
    -- status: INITIATED | PAID | FAILED | CANCELLED
    status               VARCHAR(20)     NOT NULL DEFAULT 'INITIATED',
    -- payment_method: mada | creditcard | applepay — populated from webhook payload
    payment_method       VARCHAR(20),
    moyasar_hosted_url   VARCHAR(500)    NOT NULL,
    callback_received_at TIMESTAMPTZ,
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE online_payment_transactions IS
    'Tracks Moyasar online payment transactions for membership purchases. '
    'Append-only — no soft delete, no updated_at. Status transitions only.';

-- Member profile Online Payments tab query
CREATE INDEX idx_otp_member_id     ON online_payment_transactions(member_id);

-- Link to membership for activation
CREATE INDEX idx_otp_membership_id ON online_payment_transactions(membership_id);

-- Webhook lookup — most critical, called on every payment event
CREATE INDEX idx_otp_moyasar_id    ON online_payment_transactions(moyasar_id);

-- web-pulse per-club transaction list with status filter
CREATE INDEX idx_otp_club_status   ON online_payment_transactions(club_id, status, created_at DESC);
