-- One certificate per completion. The snapshot freezes everything printed (institution, student name, course, date,
-- template revision, verification policy) and holds the private verification token; lookups use only its hash.
create table certificates (
    id                uuid          primary key,
    completion_id     uuid          not null unique references course_completions (id),
    student_id        uuid          not null references users (id),
    class_id          uuid          not null references classes (id),
    status            varchar(20)   not null check (status in ('PENDING_RENDER', 'ISSUED', 'RENDER_FAILED', 'REVOKED')),
    snapshot          jsonb         not null,
    verification_hash varchar(64)   unique,
    storage_key       varchar(200)  not null unique,
    rendered          boolean       not null default false,
    render_error      varchar(1000),
    revoked_reason    varchar(1000),
    revoked_by        uuid          references users (id),
    revoked_at        timestamptz,
    issued_at         timestamptz,
    created_at        timestamptz   not null,
    updated_at        timestamptz   not null,
    version           bigint        not null default 0
);

create index certificates_student_idx on certificates (student_id);
create index certificates_class_idx on certificates (class_id, status);

-- Public verification is off by default for school privacy; student names are shown only when separately enabled.
alter table institution_settings
    add column certificate_verification_enabled boolean not null default false,
    add column verification_shows_student      boolean not null default false;
