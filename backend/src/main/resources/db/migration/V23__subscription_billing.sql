-- ============================================================
-- V23__subscription_billing.sql
-- Add subscription plans and club subscriptions for SaaS billing
-- ============================================================

-- ── Subscription Plans ──────────────────────────────────────

CREATE TABLE subscription_plans (
    id                      BIGSERIAL PRIMARY KEY,
    public_id               UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name                    VARCHAR(100) NOT NULL,
    monthly_price_halalas   BIGINT NOT NULL,
    max_branches            INT NOT NULL DEFAULT 0,
    max_staff               INT NOT NULL DEFAULT 0,
    features                JSONB,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at              TIMESTAMPTZ
);

COMMENT ON TABLE subscription_plans IS
    'Platform-level subscription plans that gate club access. '
    'Each plan defines monthly price, branch/staff limits, and feature flags.';

COMMENT ON COLUMN subscription_plans.max_branches IS '0 = unlimited';
COMMENT ON COLUMN subscription_plans.max_staff IS '0 = unlimited';

CREATE INDEX idx_subscription_plans_active ON subscription_plans(is_active) WHERE deleted_at IS NULL;

-- ── Club Subscriptions ──────────────────────────────────────

CREATE TABLE club_subscriptions (
    id                      BIGSERIAL PRIMARY KEY,
    public_id               UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    club_id                 BIGINT NOT NULL REFERENCES clubs(id),
    plan_id                 BIGINT NOT NULL REFERENCES subscription_plans(id),
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    current_period_start    TIMESTAMPTZ NOT NULL,
    current_period_end      TIMESTAMPTZ NOT NULL,
    grace_period_ends_at    TIMESTAMPTZ NOT NULL,
    cancelled_at            TIMESTAMPTZ,
    assigned_by_user_id     BIGINT NOT NULL REFERENCES users(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE club_subscriptions IS
    'Tracks the active subscription for each club. '
    'Status transitions: ACTIVE -> GRACE -> EXPIRED. '
    'No soft delete — uses status transitions only.';

-- status: ACTIVE | GRACE | EXPIRED | CANCELLED
COMMENT ON COLUMN club_subscriptions.status IS 'ACTIVE | GRACE | EXPIRED | CANCELLED';

-- Enforces one active subscription per club at DB level
CREATE UNIQUE INDEX idx_club_subscription_active
    ON club_subscriptions(club_id)
    WHERE status NOT IN ('CANCELLED', 'EXPIRED');

-- Look up a club subscription (enforcement interceptor — must be fast)
CREATE INDEX idx_club_subscription_club_id ON club_subscriptions(club_id);

-- Scheduler queries for expiring/expired subscriptions
CREATE INDEX idx_club_subscription_status ON club_subscriptions(status, current_period_end);

-- Expiry notification scheduler query
CREATE INDEX idx_club_subscription_expiry ON club_subscriptions(current_period_end)
    WHERE status = 'ACTIVE';
