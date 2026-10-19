-- Exams belong to a course; questions live in immutable published editions (exam_versions).
create table exams (
    id         uuid         primary key,
    course_id  uuid         not null references courses (id),
    title      varchar(200) not null,
    created_by uuid         not null references users (id),
    created_at timestamptz  not null,
    updated_at timestamptz  not null
);

create index exams_course_idx on exams (course_id);

create table exam_versions (
    id                  uuid           primary key,
    exam_id             uuid           not null references exams (id),
    edition             integer        check (edition > 0),
    status              varchar(16)    not null check (status in ('DRAFT', 'PUBLISHED')),
    title               varchar(200)   not null,
    instructions        varchar(10000) not null default '',
    pass_percent        numeric(5, 2)  not null default 50 check (pass_percent >= 0 and pass_percent <= 100),
    based_on_version_id uuid           references exam_versions (id),
    published_at        timestamptz,
    published_by        uuid           references users (id),
    created_at          timestamptz    not null,
    updated_at          timestamptz    not null,
    version             bigint         not null default 0,
    unique (exam_id, edition),
    check ((status = 'DRAFT' and edition is null) or (status = 'PUBLISHED' and edition is not null and published_at is not null))
);

create unique index exam_versions_single_draft on exam_versions (exam_id) where status = 'DRAFT';

-- grading_notes and option.correct form the private grading key; never sent to students.
create table exam_questions (
    id              uuid           primary key,
    exam_version_id uuid           not null references exam_versions (id),
    position        integer        not null check (position >= 0),
    type            varchar(20)    not null check (type in ('SINGLE_CHOICE', 'MULTIPLE_SELECT', 'TRUE_FALSE', 'WRITTEN')),
    prompt          varchar(10000) not null,
    points          numeric(7, 2)  not null check (points > 0 and points <= 1000),
    explanation     varchar(10000) not null default '',
    grading_notes   varchar(10000) not null default '',
    unique (exam_version_id, position)
);

create table question_options (
    id          uuid          primary key,
    question_id uuid          not null references exam_questions (id),
    position    integer       not null check (position >= 0),
    label       varchar(1000) not null,
    correct     boolean       not null,
    unique (question_id, position)
);

create table course_required_exams (
    course_version_id uuid    not null references course_versions (id),
    exam_version_id   uuid    not null references exam_versions (id),
    position          integer not null check (position >= 0),
    primary key (course_version_id, exam_version_id),
    unique (course_version_id, position)
);

-- A class schedule for one exam edition. At least one deadline bound is mandatory.
create table exam_assignments (
    id               uuid        primary key,
    class_id         uuid        not null references classes (id),
    exam_version_id  uuid        not null references exam_versions (id),
    required         boolean     not null,
    opens_at         timestamptz,
    closes_at        timestamptz,
    duration_minutes integer     check (duration_minutes between 1 and 240),
    max_attempts     integer     not null default 1 check (max_attempts between 1 and 10),
    result_policy    varchar(20) not null check (result_policy in ('AUTO_PUBLISH', 'TEACHER_PUBLISH')),
    feedback_policy  varchar(24) not null check (feedback_policy in ('SCORE_AND_COMMENTS', 'AFTER_CLOSE', 'AFTER_SUBMISSION')),
    created_at       timestamptz not null,
    updated_at       timestamptz not null,
    version          bigint      not null default 0,
    unique (class_id, exam_version_id),
    check (duration_minutes is not null or closes_at is not null),
    check (opens_at is null or closes_at is null or closes_at > opens_at),
    check (feedback_policy <> 'AFTER_CLOSE' or closes_at is not null),
    check (feedback_policy <> 'AFTER_SUBMISSION' or not required)
);

create index exam_assignments_class_idx on exam_assignments (class_id);
