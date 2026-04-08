-- ============================================================
-- V20__member_check_ins.sql
-- Create member_check_ins table for tracking attendance visits
-- ============================================================

-- ── Check-In Attendance ─────────────────────────────────────

CREATE TABLE member_check_ins (
    id                    BIGSERIAL PRIMARY KEY,
    public_id             UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    member_id             BIGINT NOT NULL REFERENCES members(id),
    branch_id             BIGINT NOT NULL REFERENCES branches(id),
    checked_in_by_user_id BIGINT NOT NULL REFERENCES users(id),
    method                VARCHAR(20) NOT NULL,
    checked_in_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE member_check_ins IS
    'Append-only record of member check-ins at a branch. '
    'One row per visit. Immutable — no soft delete.';

-- member_id: lookup by member (duplicate detection, member history)
CREATE INDEX idx_check_ins_member_id     ON member_check_ins(member_id);

-- branch_id + checked_in_at: today count and recent-by-branch queries
CREATE INDEX idx_check_ins_branch_date   ON member_check_ins(branch_id, checked_in_at);

-- member_id + branch_id + checked_in_at DESC: duplicate detection within time window
CREATE INDEX idx_check_ins_member_branch ON member_check_ins(member_id, branch_id, checked_in_at DESC);
