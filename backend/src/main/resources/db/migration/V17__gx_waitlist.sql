-- ============================================================
-- V17__gx_waitlist.sql
-- Adds gx_waitlist_entries table for GX class waitlist feature
-- ============================================================

CREATE TABLE gx_waitlist_entries (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    class_instance_id   BIGINT NOT NULL REFERENCES gx_class_instances(id),
    member_id           BIGINT NOT NULL REFERENCES members(id),
    position            INTEGER NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    notified_at         TIMESTAMPTZ,
    accepted_booking_id BIGINT REFERENCES gx_bookings(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_waitlist_member_class UNIQUE (class_instance_id, member_id)
);

CREATE INDEX idx_gx_waitlist_class_status ON gx_waitlist_entries(class_instance_id, status);
CREATE INDEX idx_gx_waitlist_member ON gx_waitlist_entries(member_id);
