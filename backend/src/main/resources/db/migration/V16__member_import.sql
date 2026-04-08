-- ============================================================
-- V16__member_import.sql
-- Add member_import_jobs table and member_import_job_id FK on members
-- to support bulk CSV member import with rollback capability.
-- ============================================================

CREATE TABLE member_import_jobs (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    club_id             BIGINT NOT NULL REFERENCES clubs(id),
    created_by_user_id  BIGINT NOT NULL REFERENCES users(id),
    file_name           VARCHAR(255) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    total_rows          INTEGER,
    imported_count      INTEGER,
    skipped_count       INTEGER,
    error_count         INTEGER,
    error_detail        TEXT,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE member_import_jobs IS
    'Tracks bulk CSV member import jobs. Each job creates members linked via member_import_job_id '
    'on the members table, enabling full rollback (soft-delete) of all members created by a job.';

ALTER TABLE members
    ADD COLUMN member_import_job_id BIGINT REFERENCES member_import_jobs(id);

CREATE INDEX idx_member_import_jobs_club_id ON member_import_jobs(club_id);
CREATE INDEX idx_members_import_job_id ON members(member_import_job_id);
