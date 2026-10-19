-- One completion record per enrollment. ACHIEVED when every required lesson is completed and every required exam has
-- a published pass; INVALIDATED if a published correction removes a pass (restored to ACHIEVED on renewed eligibility).
create table course_completions (
    id                uuid        primary key,
    enrollment_id     uuid        not null unique references enrollments (id),
    student_id        uuid        not null references users (id),
    class_id          uuid        not null references classes (id),
    course_version_id uuid        not null references course_versions (id),
    status            varchar(16) not null check (status in ('ACHIEVED', 'INVALIDATED')),
    achieved_at       timestamptz not null,
    status_changed_at timestamptz not null,
    version           bigint      not null default 0
);

create index course_completions_class_idx on course_completions (class_id, status);

-- Once a completion exists the student can no longer undo lesson completion.
alter table enrollments add column learning_locked boolean not null default false;

-- Backfill: reconcile every existing enrollment once so no earlier progress or grade is missed.
insert into background_jobs (id, type, aggregate_id, deduplication_key, payload, status, attempts, available_at, created_at, updated_at)
select gen_random_uuid(), 'completion.reconcile', id, 'completion-backfill:' || id, '{}', 'PENDING', 0, now(), now(), now()
from enrollments;
