-- Exams are part of a course edition, like its lessons: edited in the draft, frozen when the edition is published and
-- copied into the next draft. After publication only the schedule and policies change; never the questions, the pass
-- mark or the required flag.
create table exams (
    id                uuid           primary key,
    course_id         uuid           not null,
    course_version_id uuid           not null,
    title             varchar(200)   not null,
    instructions      varchar(10000) not null default '',
    pass_percent      numeric(5, 2)  not null check (pass_percent between 0 and 100),
    required          boolean        not null,
    -- ExamMode code: 0 SELF_PACED, 1 SCHEDULED.
    mode              smallint       not null check (mode between 0 and 1),
    opens_at          timestamptz,
    closes_at         timestamptz,
    duration_minutes  integer        not null check (duration_minutes between 1 and 240),
    max_attempts      integer        not null check (max_attempts between 1 and 10),
    -- ResultPolicy code: 0 AUTO_PUBLISH, 1 TEACHER_PUBLISH.
    result_policy     smallint       not null check (result_policy between 0 and 1),
    -- FeedbackPolicy code: 0 SCORE_AND_COMMENTS, 1 AFTER_CLOSE, 2 AFTER_SUBMISSION.
    feedback_policy   smallint       not null check (feedback_policy between 0 and 2),
    created_at        timestamptz    not null,
    updated_at        timestamptz    not null,
    version           bigint         not null default 0,
    foreign key (course_id, course_version_id) references course_versions (course_id, id),
    unique (course_id, course_version_id, id),
    check ((mode = 0 and opens_at is null and closes_at is null)
        or (mode = 1 and opens_at is not null and closes_at is not null and closes_at > opens_at)),
    check (feedback_policy <> 1 or closes_at is not null),
    check (feedback_policy <> 2 or not required)
);

-- grading_notes and option.correct form the private grading key; never sent to learners.
create table exam_questions (
    id            uuid           primary key,
    exam_id       uuid           not null references exams (id),
    position      integer        not null check (position >= 0),
    -- QuestionType code: 0 SINGLE_CHOICE, 1 MULTIPLE_SELECT, 2 TRUE_FALSE, 3 WRITTEN.
    type          smallint       not null check (type between 0 and 3),
    prompt        varchar(10000) not null,
    points        numeric(7, 2)  not null check (points > 0 and points <= 1000),
    explanation   varchar(10000) not null default '',
    grading_notes varchar(10000) not null default '',
    unique (exam_id, position)
);

create table question_options (
    id          uuid          primary key,
    question_id uuid          not null references exam_questions (id),
    position    integer       not null check (position >= 0),
    label       varchar(1000) not null,
    correct     boolean       not null,
    unique (question_id, position)
);

-- One attempt of one enrollment at one exam of its pinned edition. The questions belong to that published edition and
-- never change; schedule edits never move an attempt's deadline. Deadlines are server-side (expires_at).
create table exam_attempts (
    id                uuid          primary key,
    enrollment_id     uuid          not null,
    course_id         uuid          not null,
    course_version_id uuid          not null,
    exam_id           uuid          not null,
    learner_id        uuid          not null references users (id),
    attempt_number    integer       not null check (attempt_number between 1 and 10),
    -- AttemptStatus code: 0 IN_PROGRESS, 1 SUBMITTED, 2 AWAITING_GRADING, 3 GRADED, 4 EXPIRED.
    status            smallint      not null check (status between 0 and 4),
    started_at        timestamptz   not null,
    expires_at        timestamptz   not null,
    finalized_at      timestamptz,
    objective_score   numeric(9, 2) check (objective_score >= 0),
    max_score         numeric(9, 2) not null check (max_score > 0),
    published_revision_id uuid,
    published_earned  numeric(9, 2),
    published_maximum numeric(9, 2),
    published_passed  boolean,
    published_at      timestamptz,
    version           bigint        not null default 0,
    foreign key (enrollment_id, course_id, course_version_id) references course_enrollments (id, course_id, course_version_id),
    foreign key (course_id, course_version_id, exam_id) references exams (course_id, course_version_id, id),
    unique (enrollment_id, exam_id, attempt_number),
    check (expires_at > started_at),
    check ((status = 0) = (finalized_at is null)),
    check (published_earned is null or published_earned between 0 and published_maximum)
);

create unique index exam_attempts_one_active_idx on exam_attempts (enrollment_id, exam_id) where status = 0;
create index exam_attempts_expiry_idx on exam_attempts (expires_at, id) where status = 0;
create index exam_attempts_course_queue_idx on exam_attempts (course_id, status, id);
create index exam_attempts_learner_idx on exam_attempts (learner_id, finalized_at);
create index exam_attempts_published_idx on exam_attempts (enrollment_id, exam_id, published_passed);

-- question_id belongs to the attempt's exam, which never changes once published; the service checks the pairing.
create table attempt_answers (
    id                  uuid           primary key,
    attempt_id          uuid           not null references exam_attempts (id),
    question_id         uuid           not null references exam_questions (id),
    selected_option_ids jsonb          not null default '[]',
    written_text        varchar(20000),
    saved_at            timestamptz    not null,
    unique (attempt_id, question_id)
);

-- Grade revisions per attempt: DRAFT -> FINALIZED -> PUBLISHED, later SUPERSEDED by a regrade's publication.
-- Learners only ever read PUBLISHED revisions and their immutable per-question copies.
create table grade_revisions (
    id              uuid          primary key,
    attempt_id      uuid          not null references exam_attempts (id),
    revision_number integer       not null check (revision_number > 0),
    -- RevisionStatus code: 0 DRAFT, 1 FINALIZED, 2 PUBLISHED, 3 SUPERSEDED.
    status          smallint      not null check (status between 0 and 3),
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
    unique (attempt_id, id),
    check (earned is null or earned <= maximum)
);

create unique index grade_revisions_single_open on grade_revisions (attempt_id) where status in (0, 1);
create index grade_revisions_status_idx on grade_revisions (status, attempt_id);

alter table exam_attempts add constraint exam_attempts_published_revision_fk
    foreign key (id, published_revision_id) references grade_revisions (attempt_id, id);

-- Private per-question grades of a revision. points null = not graded yet (written answers need the author).
create table answer_grades (
    id                uuid          primary key,
    grade_revision_id uuid          not null references grade_revisions (id),
    question_id       uuid          not null references exam_questions (id),
    points            numeric(7, 2) check (points >= 0),
    feedback          varchar(5000) not null default '',
    graded_by         uuid          references users (id),
    graded_at         timestamptz,
    unique (grade_revision_id, question_id)
);

create table published_question_grades (
    id                uuid          primary key,
    grade_revision_id uuid          not null references grade_revisions (id),
    question_id       uuid          not null references exam_questions (id),
    points            numeric(7, 2) not null check (points >= 0),
    feedback          varchar(5000) not null,
    unique (grade_revision_id, question_id)
);
