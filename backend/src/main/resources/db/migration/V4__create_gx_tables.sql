-- ============================================================
-- V4__create_gx_tables.sql
-- Create tables for the GX (Group Exercise) domain: class types,
-- class instances, bookings, waitlist, and attendance.
-- ============================================================

-- ── GX Class Types (templates) ─────────────────────────────

CREATE TABLE gx_class_types (

    -- Standard columns
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ     NULL,

    -- Tenant scope (club-level — shared across branches)
    organization_id BIGINT          NOT NULL,
    club_id         BIGINT          NOT NULL,

    -- Domain columns
    name_ar              VARCHAR(255) NOT NULL,
    name_en              VARCHAR(255) NOT NULL,
    description_ar       TEXT         NULL,
    description_en       TEXT         NULL,
    default_duration_minutes INTEGER NOT NULL DEFAULT 60,
    default_capacity     INTEGER      NOT NULL DEFAULT 20,
    -- color: hex color for calendar display (e.g. "#8B5CF6")
    color                VARCHAR(7)   NULL,
    is_active            BOOLEAN      NOT NULL DEFAULT true,

    -- Constraints
    CONSTRAINT uq_gx_class_types_public_id
        UNIQUE (public_id),

    CONSTRAINT fk_gx_class_types_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_gx_class_types_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_gx_class_types_duration_positive
        CHECK (default_duration_minutes > 0),

    CONSTRAINT chk_gx_class_types_capacity_positive
        CHECK (default_capacity > 0)
);

COMMENT ON TABLE gx_class_types IS
    'Template for a group exercise class (e.g. Yoga, HIIT, Spinning). '
    'Club-level — shared across all branches. Instances are scheduled '
    'per branch from these templates.';

-- Indexes
CREATE UNIQUE INDEX idx_gx_class_types_public_id_active
    ON gx_class_types(public_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_gx_class_types_organization_id_active
    ON gx_class_types(organization_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_gx_class_types_club_id_active
    ON gx_class_types(club_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX idx_gx_class_types_club_name_en_active
    ON gx_class_types(club_id, name_en)
    WHERE deleted_at IS NULL;

-- updated_at trigger
CREATE TRIGGER trg_gx_class_types_set_updated_at
    BEFORE UPDATE ON gx_class_types
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ── GX Class Instances (scheduled occurrences) ─────────────

CREATE TABLE gx_class_instances (

    -- Standard columns
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ     NULL,

    -- Tenant scope (branch-level)
    organization_id BIGINT          NOT NULL,
    club_id         BIGINT          NOT NULL,
    branch_id       BIGINT          NOT NULL,

    -- Domain columns
    class_type_id   BIGINT          NOT NULL,
    instructor_id   BIGINT          NOT NULL,
    scheduled_at    TIMESTAMPTZ     NOT NULL,
    duration_minutes INTEGER        NOT NULL DEFAULT 60,
    capacity        INTEGER         NOT NULL DEFAULT 20,
    bookings_count  INTEGER         NOT NULL DEFAULT 0,
    waitlist_count  INTEGER         NOT NULL DEFAULT 0,
    room            VARCHAR(100)    NULL,
    -- instance_status: scheduled | in-progress | completed | cancelled
    instance_status VARCHAR(50)     NOT NULL DEFAULT 'scheduled'
        CONSTRAINT chk_gx_class_instances_status
        CHECK (instance_status IN ('scheduled', 'in-progress', 'completed', 'cancelled')),
    notes           TEXT            NULL,

    -- Constraints
    CONSTRAINT uq_gx_class_instances_public_id
        UNIQUE (public_id),

    CONSTRAINT fk_gx_class_instances_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_gx_class_instances_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_gx_class_instances_branch
        FOREIGN KEY (branch_id)
        REFERENCES branches(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_gx_class_instances_class_type
        FOREIGN KEY (class_type_id)
        REFERENCES gx_class_types(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_gx_class_instances_instructor
        FOREIGN KEY (instructor_id)
        REFERENCES trainers(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_gx_class_instances_duration_positive
        CHECK (duration_minutes > 0),

    CONSTRAINT chk_gx_class_instances_capacity_positive
        CHECK (capacity > 0),

    CONSTRAINT chk_gx_class_instances_bookings_non_negative
        CHECK (bookings_count >= 0),

    CONSTRAINT chk_gx_class_instances_waitlist_non_negative
        CHECK (waitlist_count >= 0)
);

COMMENT ON TABLE gx_class_instances IS
    'A specific scheduled occurrence of a GX class type at a branch. '
    'E.g. "Yoga with Noura, Monday 7am, Room 2, 20 spots". '
    'Bookings and attendance are tracked per instance.';

-- Indexes
CREATE UNIQUE INDEX idx_gx_class_instances_public_id_active
    ON gx_class_instances(public_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_gx_class_instances_organization_id_active
    ON gx_class_instances(organization_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_gx_class_instances_club_id_active
    ON gx_class_instances(club_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_gx_class_instances_branch_id_active
    ON gx_class_instances(branch_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_gx_class_instances_class_type_id
    ON gx_class_instances(class_type_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_gx_class_instances_instructor_id
    ON gx_class_instances(instructor_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_gx_class_instances_scheduled_at
    ON gx_class_instances(branch_id, scheduled_at)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_gx_class_instances_instructor_schedule
    ON gx_class_instances(instructor_id, scheduled_at, duration_minutes)
    WHERE deleted_at IS NULL AND instance_status != 'cancelled';

-- updated_at trigger
CREATE TRIGGER trg_gx_class_instances_set_updated_at
    BEFORE UPDATE ON gx_class_instances
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ── GX Bookings (member reservations) ──────────────────────

CREATE TABLE gx_bookings (

    -- Standard columns (no deleted_at — bookings are immutable records)
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Tenant scope
    organization_id BIGINT          NOT NULL,
    club_id         BIGINT          NOT NULL,

    -- Domain columns
    instance_id     BIGINT          NOT NULL,
    member_id       BIGINT          NOT NULL,
    -- booking_status: confirmed | cancelled | waitlist | promoted
    booking_status  VARCHAR(50)     NOT NULL DEFAULT 'confirmed'
        CONSTRAINT chk_gx_bookings_status
        CHECK (booking_status IN ('confirmed', 'cancelled', 'waitlist', 'promoted')),
    booked_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    waitlist_position INTEGER       NULL,
    cancelled_at    TIMESTAMPTZ     NULL,
    cancellation_reason TEXT        NULL,

    -- Constraints
    CONSTRAINT uq_gx_bookings_public_id
        UNIQUE (public_id),

    CONSTRAINT uq_gx_bookings_instance_member
        UNIQUE (instance_id, member_id),

    CONSTRAINT fk_gx_bookings_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_gx_bookings_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_gx_bookings_instance
        FOREIGN KEY (instance_id)
        REFERENCES gx_class_instances(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_gx_bookings_member
        FOREIGN KEY (member_id)
        REFERENCES members(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE gx_bookings IS
    'A member reservation for a GX class instance. Immutable record — '
    'status changes from confirmed to cancelled but the row is never deleted. '
    'One booking per member per instance (enforced by unique constraint).';

-- Indexes
CREATE INDEX idx_gx_bookings_organization_id
    ON gx_bookings(organization_id);

CREATE INDEX idx_gx_bookings_instance_id
    ON gx_bookings(instance_id);

CREATE INDEX idx_gx_bookings_member_id
    ON gx_bookings(member_id);

CREATE INDEX idx_gx_bookings_instance_status
    ON gx_bookings(instance_id, booking_status);

-- updated_at trigger
CREATE TRIGGER trg_gx_bookings_set_updated_at
    BEFORE UPDATE ON gx_bookings
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- ── GX Attendance ──────────────────────────────────────────

CREATE TABLE gx_attendance (

    -- Standard columns (no deleted_at — attendance records are immutable)
    id              BIGSERIAL       PRIMARY KEY,
    public_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    -- Tenant scope
    organization_id BIGINT          NOT NULL,

    -- Domain columns
    instance_id     BIGINT          NOT NULL,
    member_id       BIGINT          NOT NULL,
    -- attendance_status: present | absent | late
    attendance_status VARCHAR(50)   NOT NULL
        CONSTRAINT chk_gx_attendance_status
        CHECK (attendance_status IN ('present', 'absent', 'late')),
    marked_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    marked_by_id    BIGINT          NOT NULL,

    -- Constraints
    CONSTRAINT uq_gx_attendance_public_id
        UNIQUE (public_id),

    CONSTRAINT uq_gx_attendance_instance_member
        UNIQUE (instance_id, member_id),

    CONSTRAINT fk_gx_attendance_organization
        FOREIGN KEY (organization_id)
        REFERENCES organizations(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_gx_attendance_instance
        FOREIGN KEY (instance_id)
        REFERENCES gx_class_instances(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_gx_attendance_member
        FOREIGN KEY (member_id)
        REFERENCES members(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_gx_attendance_marked_by
        FOREIGN KEY (marked_by_id)
        REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

COMMENT ON TABLE gx_attendance IS
    'Attendance record for a member at a GX class instance. '
    'One record per member per instance (upsert on re-submission). '
    'Never deleted — attendance records are permanent.';

-- Indexes
CREATE INDEX idx_gx_attendance_organization_id
    ON gx_attendance(organization_id);

CREATE INDEX idx_gx_attendance_instance_id
    ON gx_attendance(instance_id);

CREATE INDEX idx_gx_attendance_member_id
    ON gx_attendance(member_id);

-- updated_at trigger
CREATE TRIGGER trg_gx_attendance_set_updated_at
    BEFORE UPDATE ON gx_attendance
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
