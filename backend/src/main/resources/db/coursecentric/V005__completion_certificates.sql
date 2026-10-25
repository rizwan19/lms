-- One completion per enrollment. ACHIEVED when every required lesson of the pinned edition is completed and every
-- required course exam has a published pass; INVALIDATED if a published correction removes a pass.
create table course_completions (
    id                uuid        primary key,
    enrollment_id     uuid        not null unique,
    learner_id        uuid        not null references users (id),
    course_id         uuid        not null,
    course_version_id uuid        not null,
    -- CompletionStatus code: 1 ACHIEVED, 2 INVALIDATED (0 NOT_ELIGIBLE is never stored).
    status            smallint    not null check (status between 1 and 2),
    achieved_at       timestamptz not null,
    status_changed_at timestamptz not null,
    version           bigint      not null default 0,
    foreign key (enrollment_id, course_id, course_version_id) references course_enrollments (id, course_id, course_version_id)
);

create index course_completions_course_idx on course_completions (course_id, status);

-- One certificate per completion. The printed columns (template_revision through verification_token) freeze everything
-- printed (workspace, learner, course, author, date, template revision, verification policy) and are never updated.
-- verification_token is private; lookups use only its hash.
create table certificates (
    id                uuid          primary key,
    completion_id     uuid          not null unique references course_completions (id),
    learner_id        uuid          not null references users (id),
    course_id         uuid          not null references courses (id),
    -- CertificateStatus code: 0 PENDING_RENDER, 1 ISSUED, 2 RENDER_FAILED, 3 REVOKED.
    status            smallint      not null check (status between 0 and 3),
    template_revision integer       not null check (template_revision > 0),
    workspace_name    varchar(200)  not null,
    logo_asset_id     uuid,
    learner_name      varchar(200)  not null,
    course_title      varchar(200)  not null,
    author_name       varchar(200)  not null,
    edition           integer       not null check (edition > 0),
    completed_on      date          not null,
    verification_enabled boolean    not null,
    show_learner_name boolean       not null,
    verification_token varchar(100),
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

create index certificates_learner_idx on certificates (learner_id);
create index certificates_course_idx on certificates (course_id, status);
