-- ============================================================
-- V1__create_auth_tables.sql
-- Create the users and refresh_tokens tables for JWT auth
-- ============================================================

-- ── Shared function ─────────────────────────────────────────

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ── Users ───────────────────────────────────────────────────

CREATE TABLE users (

    -- Standard columns (required on every table)
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ     NULL,

    -- Domain columns
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    -- role: nexus:super-admin | nexus:support-agent | nexus:integration-specialist
    --       | nexus:read-only-auditor | club:owner | club:branch-manager
    --       | club:receptionist | club:sales-agent | trainer:pt | trainer:gx | member
    role            VARCHAR(50)     NOT NULL
        CONSTRAINT chk_users_role
        CHECK (role IN (
            'nexus:super-admin', 'nexus:support-agent',
            'nexus:integration-specialist', 'nexus:read-only-auditor',
            'club:owner', 'club:branch-manager',
            'club:receptionist', 'club:sales-agent',
            'trainer:pt', 'trainer:gx',
            'member'
        )),
    is_active       BOOLEAN         NOT NULL DEFAULT true,
    last_login_at   TIMESTAMPTZ     NULL,

    -- Constraints
    CONSTRAINT uq_users_public_id
        UNIQUE (public_id)
);

COMMENT ON TABLE users IS
    'Platform-wide user accounts. Each user has exactly one role. '
    'Users exist at platform level — tenant scope is on the entities they relate to '
    '(staff_members, trainers, members — added in later plans).';

-- Unique email among active (non-deleted) users
CREATE UNIQUE INDEX idx_users_email
    ON users(email)
    WHERE deleted_at IS NULL;

-- updated_at trigger
CREATE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ── Refresh tokens ──────────────────────────────────────────

CREATE TABLE refresh_tokens (

    -- Standard columns (required on every table)
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ     NULL,

    -- Domain columns
    user_id         BIGINT          NOT NULL,
    token_hash      VARCHAR(255)    NOT NULL,
    expires_at      TIMESTAMPTZ     NOT NULL,
    revoked_at      TIMESTAMPTZ     NULL,
    device_info     VARCHAR(255)    NULL,

    -- Constraints
    CONSTRAINT uq_refresh_tokens_public_id
        UNIQUE (public_id),

    CONSTRAINT uq_refresh_tokens_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

COMMENT ON TABLE refresh_tokens IS
    'Stores hashed refresh tokens for JWT authentication. '
    'Each row represents one device/session. Tokens are rotated on every use '
    'and invalidated via revoked_at.';

COMMENT ON COLUMN refresh_tokens.token_hash IS
    'BCrypt hash of the raw refresh token. The raw token is returned to the client once and never stored.';

COMMENT ON COLUMN refresh_tokens.revoked_at IS
    'Set when the token is explicitly revoked (logout) or rotated. NULL means still valid.';

-- Indexes
CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens(user_id);

CREATE INDEX idx_refresh_tokens_expires_at
    ON refresh_tokens(expires_at);

-- updated_at trigger
CREATE TRIGGER trg_refresh_tokens_set_updated_at
    BEFORE UPDATE ON refresh_tokens
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
