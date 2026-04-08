-- ============================================================
-- V21__staff_shifts.sql
-- Create staff_shifts and shift_swap_requests tables for
-- the staff scheduling & shift swap feature (Plan 26).
-- ============================================================

-- ── Staff Shifts ────────────────────────────────────────────

CREATE TABLE staff_shifts (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    staff_member_id     BIGINT NOT NULL REFERENCES staff_members(id),
    branch_id           BIGINT NOT NULL REFERENCES branches(id),
    start_at            TIMESTAMPTZ NOT NULL,
    end_at              TIMESTAMPTZ NOT NULL,
    notes               VARCHAR(500),
    created_by_user_id  BIGINT NOT NULL REFERENCES users(id),
    deleted_at          TIMESTAMPTZ
);

COMMENT ON TABLE staff_shifts IS
    'A scheduled work shift for a staff member at a specific branch. '
    'Soft-deleted via deleted_at. staffMemberId is mutable to support swap transfers.';

CREATE INDEX idx_shifts_staff_member  ON staff_shifts(staff_member_id);
CREATE INDEX idx_shifts_branch_start  ON staff_shifts(branch_id, start_at);
CREATE INDEX idx_shifts_staff_range   ON staff_shifts(staff_member_id, start_at, end_at)
    WHERE deleted_at IS NULL;

CREATE TRIGGER trg_staff_shifts_set_updated_at
    BEFORE UPDATE ON staff_shifts
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ── Shift Swap Requests ─────────────────────────────────────

CREATE TABLE shift_swap_requests (
    id                    BIGSERIAL PRIMARY KEY,
    public_id             UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shift_id              BIGINT NOT NULL REFERENCES staff_shifts(id),
    requester_staff_id    BIGINT NOT NULL REFERENCES staff_members(id),
    target_staff_id       BIGINT NOT NULL REFERENCES staff_members(id),
    -- status: PENDING_ACCEPTANCE | PENDING_APPROVAL | APPROVED | REJECTED | DECLINED | CANCELLED
    status                VARCHAR(30) NOT NULL DEFAULT 'PENDING_ACCEPTANCE',
    requester_note        VARCHAR(300),
    resolved_by_user_id   BIGINT REFERENCES users(id),
    resolved_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE shift_swap_requests IS
    'A request to swap a shift between two staff members. '
    'Operational record — no soft delete. Terminal states: APPROVED, REJECTED, DECLINED, CANCELLED.';

CREATE INDEX idx_swap_shift          ON shift_swap_requests(shift_id);
CREATE INDEX idx_swap_requester      ON shift_swap_requests(requester_staff_id);
CREATE INDEX idx_swap_target         ON shift_swap_requests(target_staff_id);
CREATE INDEX idx_swap_status         ON shift_swap_requests(status)
    WHERE status IN ('PENDING_ACCEPTANCE', 'PENDING_APPROVAL');
