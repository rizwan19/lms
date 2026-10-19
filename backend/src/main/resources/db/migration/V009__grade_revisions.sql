-- Grade revisions per attempt: DRAFT -> FINALIZED -> PUBLISHED, later SUPERSEDED by a regrade's publication.
-- Students only ever read PUBLISHED revisions and their immutable per-question copies.
create table grade_revisions (
    id              uuid          primary key,
    attempt_id      uuid          not null references exam_attempts (id),
    revision_number integer       not null check (revision_number > 0),
    status          varchar(16)   not null check (status in ('DRAFT', 'FINALIZED', 'PUBLISHED', 'SUPERSEDED')),
    earned          numeric(9, 2) check (earned >= 0),
    maximum         numeric(9, 2) not null check (maximum > 0),
    reason          varchar(1000),
    created_by      uuid          references users (id),
    created_at      timestamptz   not null,
    finalized_at    timestamptz,
    published_at    timestamptz,
    published_by    uuid          references users (id),
    version         bigint        not null default 0,
    unique (attempt_id, revision_number),
    check (earned is null or earned <= maximum)
);

create unique index grade_revisions_single_open on grade_revisions (attempt_id) where status in ('DRAFT', 'FINALIZED');
create index grade_revisions_status_idx on grade_revisions (status, attempt_id);

-- Private per-question grades of a revision. points null = not graded yet (written answers need a teacher).
create table answer_grades (
    grade_revision_id uuid          not null references grade_revisions (id),
    question_id       uuid          not null references exam_questions (id),
    points            numeric(7, 2) check (points >= 0),
    feedback          varchar(5000) not null default '',
    graded_by         uuid          references users (id),
    graded_at         timestamptz,
    primary key (grade_revision_id, question_id)
);

create table published_question_grades (
    grade_revision_id uuid          not null references grade_revisions (id),
    question_id       uuid          not null references exam_questions (id),
    points            numeric(7, 2) not null check (points >= 0),
    feedback          varchar(5000) not null,
    primary key (grade_revision_id, question_id)
);

-- Published result projection owned by assessments; written only through AttemptResults.recordPublished.
alter table exam_attempts
    add column published_revision_id uuid references grade_revisions (id),
    add column published_earned      numeric(9, 2),
    add column published_maximum     numeric(9, 2),
    add column published_passed      boolean,
    add column published_at          timestamptz;

create index exam_attempts_published_idx on exam_attempts (enrollment_id, assignment_id, published_passed);
