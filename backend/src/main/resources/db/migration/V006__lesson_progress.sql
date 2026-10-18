-- Explicit lesson completion and monotonic resume position per enrollment.
create table lesson_progress (
    id               uuid        primary key,
    enrollment_id    uuid        not null references enrollments (id),
    lesson_id        uuid        not null references lessons (id),
    completed_at     timestamptz,
    resume_seconds   integer     not null default 0 check (resume_seconds >= 0),
    last_activity_at timestamptz not null,
    revision         bigint      not null check (revision > 0),
    unique (enrollment_id, lesson_id)
);
