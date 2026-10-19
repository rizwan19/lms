-- One attempt of one enrollment at one assignment. Questions are snapshotted at start, including the private key,
-- so later exam editions can never change an attempt. Deadlines are server-side (expires_at).
create table exam_attempts (
    id                    uuid          primary key,
    assignment_id         uuid          not null references exam_assignments (id),
    enrollment_id         uuid          not null references enrollments (id),
    student_id            uuid          not null references users (id),
    attempt_number        integer       not null check (attempt_number between 1 and 10),
    status                varchar(20)   not null check (status in ('IN_PROGRESS', 'SUBMITTED', 'AWAITING_GRADING', 'GRADED')),
    started_at            timestamptz   not null,
    expires_at            timestamptz   not null check (expires_at > started_at),
    finalized_at          timestamptz,
    finalized_reason      varchar(16)   check (finalized_reason in ('SUBMITTED', 'EXPIRED')),
    objective_score       numeric(9, 2) check (objective_score >= 0),
    max_score             numeric(9, 2) not null check (max_score > 0),
    version               bigint        not null default 0,
    unique (enrollment_id, assignment_id, attempt_number),
    check ((status = 'IN_PROGRESS') = (finalized_at is null))
);

create unique index exam_attempts_single_active on exam_attempts (enrollment_id, assignment_id) where status = 'IN_PROGRESS';
create index exam_attempts_assignment_student_idx on exam_attempts (assignment_id, student_id);
create index exam_attempts_status_expiry_idx on exam_attempts (status, expires_at);

-- options: [{"id","label","correct"}] in the order shown to this student.
create table attempt_questions (
    attempt_id    uuid           not null references exam_attempts (id),
    question_id   uuid           not null references exam_questions (id),
    position      integer        not null check (position >= 0),
    type          varchar(20)    not null check (type in ('SINGLE_CHOICE', 'MULTIPLE_SELECT', 'TRUE_FALSE', 'WRITTEN')),
    prompt        varchar(10000) not null,
    points        numeric(7, 2)  not null check (points > 0),
    explanation   varchar(10000) not null,
    grading_notes varchar(10000) not null,
    options       jsonb          not null,
    auto_points   numeric(7, 2)  check (auto_points >= 0 and auto_points <= points),
    primary key (attempt_id, question_id),
    unique (attempt_id, position)
);

create table attempt_answers (
    attempt_id          uuid           not null,
    question_id         uuid           not null,
    selected_option_ids jsonb          not null default '[]',
    written_text        varchar(20000),
    revision            bigint         not null check (revision > 0),
    saved_at            timestamptz    not null,
    primary key (attempt_id, question_id),
    foreign key (attempt_id, question_id) references attempt_questions (attempt_id, question_id)
);
