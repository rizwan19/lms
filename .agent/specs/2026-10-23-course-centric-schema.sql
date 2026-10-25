-- Course-centric LMS v2: proposed EMPTY-DATABASE blueprint, 23 October 2026.
-- Planning artifact only. NOT executed. Not a migration for an existing LMS database.
-- During implementation split into ordered Flyway migrations under db/coursecentric.
-- Supply UUIDs and application timestamps from the backend; now() is not domain Clock.

create table users (
    id uuid primary key,
    username varchar(64) not null unique check (username ~ '^[a-z0-9._-]{3,64}$'),
    email varchar(254) not null unique check (email = lower(trim(email))),
    display_name varchar(150) not null check (length(trim(display_name)) > 0),
    password_hash varchar(255) not null,
    role varchar(16) not null check (role in ('TEACHER','STUDENT')),
    status varchar(16) not null check (status in ('ACTIVE','SUSPENDED')),
    email_verified_at timestamptz,
    teacher_status varchar(16) check (teacher_status in ('PENDING','APPROVED','REJECTED')),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0,
    check ((role = 'STUDENT' and teacher_status is null)
        or (role = 'TEACHER' and teacher_status is not null)),
    check (teacher_status is distinct from 'APPROVED' or email_verified_at is not null)
);
create index users_approved_teachers_idx on users(id)
    where role='TEACHER' and teacher_status='APPROVED' and status='ACTIVE';

create table workspace (
    id smallint primary key check (id=1),
    name varchar(200) not null,
    timezone varchar(64) not null,
    state varchar(32) not null check (state in ('REQUIRED','OWNER_VERIFICATION_PENDING','ACTIVE')),
    created_by uuid references users(id),
    public_verification boolean not null default false,
    certificate_show_name boolean not null default false,
    branding jsonb not null default '{}',
    created_at timestamptz not null,
    updated_at timestamptz not null,
    version bigint not null default 0
);
-- The bootstrap migration creates the singleton REQUIRED row with fixed UTC provenance.
-- No hard-coded setup token, account, password or privileged session is seeded.

create table identity_tokens (
    id uuid primary key,
    user_id uuid not null references users(id),
    purpose varchar(32) not null check (purpose in ('VERIFY_EMAIL','OWNER_VERIFY_EMAIL','RESET_PASSWORD')),
    token_hash char(64) not null unique,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    revoked_at timestamptz,
    check (expires_at > created_at)
);
create index identity_tokens_user_purpose_idx on identity_tokens(user_id,purpose);

create table teacher_applications (
    id uuid primary key,
    applicant_id uuid not null references users(id),
    status varchar(16) not null check (status in ('PENDING','APPROVED','REJECTED')),
    decided_by uuid references users(id),
    reason varchar(1000),
    created_at timestamptz not null,
    decided_at timestamptz,
    version bigint not null default 0,
    check (decided_by is null or decided_by <> applicant_id),
    check ((status='PENDING' and decided_by is null and decided_at is null)
        or (status<>'PENDING' and decided_by is not null and decided_at is not null))
);
create unique index teacher_applications_one_pending on teacher_applications(applicant_id) where status='PENDING';
create index teacher_applications_queue_idx on teacher_applications(status,created_at,id);

create table auth_rate_limits (
    bucket varchar(400) primary key,
    attempts integer not null check (attempts>=0),
    window_started_at timestamptz not null
);
create table spring_session (
    primary_id char(36) primary key, session_id char(36) not null unique,
    creation_time bigint not null, last_access_time bigint not null,
    max_inactive_interval integer not null, expiry_time bigint not null,
    principal_name varchar(100)
);
create index spring_session_expiry_idx on spring_session(expiry_time);
create index spring_session_principal_idx on spring_session(principal_name);
create table spring_session_attributes (
    session_primary_id char(36) not null references spring_session(primary_id) on delete cascade,
    attribute_name varchar(200) not null, attribute_bytes bytea not null,
    primary key (session_primary_id,attribute_name)
);

create table media_assets (
    id uuid primary key, uploaded_by uuid not null references users(id),
    storage_key varchar(500) not null unique, original_filename varchar(255) not null,
    content_type varchar(100) not null, byte_size bigint not null check (byte_size>=0),
    sha256 char(64), purpose varchar(32) not null check (purpose in ('LESSON_VIDEO','LESSON_DOCUMENT','BRANDING_IMAGE','CERTIFICATE','REPORT_EXPORT')),
    status varchar(24) not null check (status in ('UPLOADING','VALIDATING','AVAILABLE','REJECTED')),
    metadata jsonb not null default '{}', created_at timestamptz not null, updated_at timestamptz not null
);
create index media_assets_uploader_idx on media_assets(uploaded_by,status);

create table courses (
    id uuid primary key, author_id uuid not null references users(id),
    slug varchar(160) not null unique, title varchar(200) not null,
    summary varchar(2000) not null default '', thumbnail_asset_id uuid references media_assets(id),
    status varchar(16) not null check (status in ('ACTIVE','ARCHIVED')),
    created_at timestamptz not null, updated_at timestamptz not null, archived_at timestamptz,
    version bigint not null default 0
);
create index courses_author_idx on courses(author_id,updated_at desc,id);
create table course_versions (
    id uuid primary key, course_id uuid not null references courses(id),
    edition integer check (edition>0), status smallint not null check (status between 0 and 1), -- 0 DRAFT, 1 PUBLISHED
    title varchar(200) not null, description text not null default '',
    based_on_version_id uuid references course_versions(id),
    created_at timestamptz not null, updated_at timestamptz not null, published_at timestamptz,
    version bigint not null default 0,
    unique (course_id,edition), unique (course_id,id),
    check ((status=0 and edition is null and published_at is null)
        or (status=1 and edition is not null and published_at is not null))
);
create unique index course_versions_draft_idx on course_versions(course_id) where status=0;
create index course_versions_catalog_idx on course_versions(course_id,edition desc) where status='PUBLISHED';
create table sections (
    id uuid primary key, course_version_id uuid not null references course_versions(id),
    title varchar(200) not null, position integer not null check (position>=0),
    unique (course_version_id,position), unique (course_version_id,id)
);
create table lessons (
    id uuid primary key, course_version_id uuid not null references course_versions(id), section_id uuid not null,
    title varchar(200) not null, position integer not null check (position>=0), body_html text not null default '',
    video_asset_id uuid references media_assets(id), video_provider varchar(16) check (video_provider in ('YOUTUBE','VIMEO')),
    video_ref varchar(64), required boolean not null default true,
    foreign key (course_version_id,section_id) references sections(course_version_id,id),
    unique (section_id,position), unique (course_version_id,id),
    check ((video_provider is null) = (video_ref is null)),
    check (video_asset_id is null or video_provider is null)
);
create table lesson_resources (
    lesson_id uuid not null references lessons(id), asset_id uuid not null references media_assets(id),
    position integer not null check (position>=0), primary key (lesson_id,position)
);
create index lesson_resources_asset_idx on lesson_resources(asset_id);

create table course_enrollments (
    id uuid primary key, course_id uuid not null references courses(id),
    course_version_id uuid not null, learner_id uuid not null references users(id),
    status varchar(16) not null check (status in ('ACTIVE','SUSPENDED','WITHDRAWN')),
    grant_source varchar(16) not null check (grant_source in ('REQUEST','INVITATION')),
    granted_by uuid not null references users(id), learning_locked boolean not null default false,
    granted_at timestamptz not null, status_changed_at timestamptz not null, version bigint not null default 0,
    foreign key (course_id,course_version_id) references course_versions(course_id,id),
    unique (course_id,learner_id), unique (id,course_id,course_version_id),
    unique (course_id,learner_id,id)
);
create index course_enrollments_learner_idx on course_enrollments(learner_id,status,id);
create index course_enrollments_roster_idx on course_enrollments(course_id,status,id);
create table course_access_requests (
    id uuid primary key, course_id uuid not null references courses(id), learner_id uuid not null references users(id),
    status varchar(16) not null check (status in ('PENDING','APPROVED','REJECTED','CANCELLED')),
    decided_by uuid references users(id), reason varchar(1000), enrollment_id uuid references course_enrollments(id),
    requested_at timestamptz not null, decided_at timestamptz, version bigint not null default 0,
    unique (course_id,learner_id),
    foreign key (course_id,learner_id,enrollment_id) references course_enrollments(course_id,learner_id,id),
    check ((status='PENDING' and decided_by is null and decided_at is null)
        or (status<>'PENDING' and decided_by is not null and decided_at is not null)),
    check (status<>'APPROVED' or enrollment_id is not null)
);
create index course_access_requests_author_queue_idx on course_access_requests(course_id,status,requested_at,id);
create table course_access_events (
    id uuid primary key, request_id uuid not null references course_access_requests(id),
    actor_id uuid not null references users(id), from_status varchar(16), to_status varchar(16) not null,
    reason varchar(1000), created_at timestamptz not null
);
create index course_access_events_request_idx on course_access_events(request_id,created_at,id);
create table course_invitations (
    id uuid primary key, course_id uuid not null references courses(id), invited_by uuid not null references users(id),
    recipient_email varchar(254) not null check (recipient_email=lower(trim(recipient_email))),
    token_hash char(64) not null unique,
    status varchar(16) not null check (status in ('PENDING','ACCEPTED','REVOKED','EXPIRED')),
    accepted_by uuid references users(id), enrollment_id uuid references course_enrollments(id),
    created_at timestamptz not null, expires_at timestamptz not null, accepted_at timestamptz,
    revoked_at timestamptz, version bigint not null default 0,
    foreign key (course_id,accepted_by,enrollment_id) references course_enrollments(course_id,learner_id,id),
    check (expires_at>created_at),
    check ((status='ACCEPTED' and accepted_by is not null and enrollment_id is not null and accepted_at is not null)
        or (status<>'ACCEPTED' and accepted_by is null and accepted_at is null))
);
create unique index course_invitations_one_pending_idx on course_invitations(course_id,recipient_email) where status='PENDING';
create index course_invitations_recipient_idx on course_invitations(recipient_email,status);

create table lesson_progress (
    enrollment_id uuid not null, course_id uuid not null, course_version_id uuid not null, lesson_id uuid not null,
    completed_at timestamptz, resume_seconds bigint not null default 0 check (resume_seconds>=0),
    last_activity_at timestamptz not null, version bigint not null default 0,
    primary key (enrollment_id,lesson_id),
    foreign key (enrollment_id,course_id,course_version_id) references course_enrollments(id,course_id,course_version_id),
    foreign key (course_version_id,lesson_id) references lessons(course_version_id,id)
);

-- Exams belong to a course edition like its lessons: edited in the draft, frozen on publication, copied into the next draft.
create table exams (
    id uuid primary key, course_id uuid not null, course_version_id uuid not null,
    title varchar(200) not null,
    instructions varchar(10000) not null default '',
    pass_percent numeric(5,2) not null check (pass_percent between 0 and 100), required boolean not null,
    mode varchar(16) not null check (mode in ('SELF_PACED','SCHEDULED')),
    opens_at timestamptz, closes_at timestamptz,
    duration_minutes integer not null check (duration_minutes between 1 and 240),
    max_attempts integer not null check (max_attempts between 1 and 10),
    result_policy varchar(20) not null check (result_policy in ('AUTO_PUBLISH','TEACHER_PUBLISH')),
    feedback_policy varchar(24) not null check (feedback_policy in ('SCORE_AND_COMMENTS','AFTER_CLOSE','AFTER_SUBMISSION')),
    created_at timestamptz not null, updated_at timestamptz not null, version bigint not null default 0,
    foreign key (course_id,course_version_id) references course_versions(course_id,id),
    unique (course_id,course_version_id,id),
    check ((mode='SELF_PACED' and opens_at is null and closes_at is null)
        or (mode='SCHEDULED' and opens_at is not null and closes_at is not null and closes_at>opens_at)),
    check (feedback_policy<>'AFTER_CLOSE' or closes_at is not null),
    check (feedback_policy<>'AFTER_SUBMISSION' or not required)
);
create table exam_questions (
    id uuid primary key, exam_id uuid not null references exams(id),
    position integer not null check (position>=0),
    type smallint not null check (type between 0 and 3), -- QuestionType code
    prompt varchar(10000) not null, points numeric(7,2) not null check (points>0 and points<=1000),
    explanation varchar(10000) not null default '', grading_notes varchar(10000) not null default '',
    options jsonb not null default '[]' check (jsonb_typeof(options)='array'), -- [{id, label, correct}] in display order
    unique (exam_id,position)
);
create table exam_attempts (
    id uuid primary key, enrollment_id uuid not null, course_id uuid not null, course_version_id uuid not null, exam_id uuid not null,
    attempt_number integer not null check (attempt_number>0),
    status varchar(24) not null check (status in ('IN_PROGRESS','SUBMITTED','AWAITING_GRADING','GRADED')),
    started_at timestamptz not null, expires_at timestamptz not null, submitted_at timestamptz,
    pass_percent numeric(5,2) not null check (pass_percent between 0 and 100),
    provisional_earned numeric(10,2), maximum_points numeric(10,2) not null check (maximum_points>0),
    published_grade_revision_id uuid, published_earned numeric(10,2), published_at timestamptz,
    version bigint not null default 0,
    foreign key (enrollment_id,course_id,course_version_id) references course_enrollments(id,course_id,course_version_id),
    foreign key (course_id,course_version_id,exam_id) references exams(course_id,course_version_id,id),
    unique (enrollment_id,exam_id,attempt_number),
    check (expires_at>started_at),
    check (published_earned is null or published_earned between 0 and maximum_points)
);
create unique index exam_attempts_one_active_idx on exam_attempts(enrollment_id,exam_id) where status='IN_PROGRESS';
create index exam_attempts_expiry_idx on exam_attempts(expires_at,id) where status='IN_PROGRESS';
create index exam_attempts_course_queue_idx on exam_attempts(course_id,status,id);
create table attempt_answers (
    attempt_id uuid not null, question_id uuid not null, answer jsonb not null,
    saved_at timestamptz not null, version bigint not null default 0,
    primary key (attempt_id,question_id),
    foreign key (attempt_id) references exam_attempts(id),
    foreign key (question_id) references exam_questions(id) -- the attempt's exam, frozen with its published course edition
);
create table grade_revisions (
    id uuid primary key, attempt_id uuid not null references exam_attempts(id),
    revision integer not null check (revision>0),
    status varchar(16) not null check (status in ('DRAFT','FINALIZED','PUBLISHED')),
    reason varchar(1000), earned numeric(10,2), maximum_points numeric(10,2) not null check (maximum_points>0),
    created_by uuid references users(id), published_by uuid references users(id),
    created_at timestamptz not null, published_at timestamptz, version bigint not null default 0,
    unique (attempt_id,revision), unique (attempt_id,id),
    check (earned is null or earned between 0 and maximum_points)
);
create unique index grade_revisions_one_draft on grade_revisions(attempt_id) where status='DRAFT';
alter table exam_attempts add constraint exam_attempts_published_revision_fk
    foreign key (id,published_grade_revision_id) references grade_revisions(attempt_id,id);
create table question_grades (
    grade_revision_id uuid not null, attempt_id uuid not null, question_id uuid not null,
    points numeric(7,2) not null check (points>=0), feedback varchar(5000) not null default '',
    graded_by uuid references users(id), graded_at timestamptz not null,
    primary key (grade_revision_id,question_id),
    foreign key (attempt_id,grade_revision_id) references grade_revisions(attempt_id,id),
    foreign key (question_id) references exam_questions(id)
);

create table course_completions (
    id uuid primary key, enrollment_id uuid not null unique references course_enrollments(id),
    status varchar(16) not null check (status in ('ACHIEVED','INVALIDATED')),
    achieved_at timestamptz not null, invalidated_at timestamptz, updated_at timestamptz not null,
    version bigint not null default 0
);
create table certificates (
    id uuid primary key, completion_id uuid not null unique references course_completions(id),
    status varchar(24) not null check (status in ('PENDING_RENDER','ISSUED','RENDER_FAILED','REVOKED')),
    snapshot jsonb not null, verification_hash char(64) unique,
    storage_key varchar(500), render_error varchar(2000), revoked_reason varchar(1000),
    issued_at timestamptz, revoked_at timestamptz, created_at timestamptz not null, updated_at timestamptz not null,
    version bigint not null default 0
);

create table background_jobs (
    id uuid primary key, type varchar(100) not null, aggregate_id uuid not null,
    deduplication_key varchar(255) not null unique, payload jsonb not null default '{}',
    status varchar(16) not null check (status in ('PENDING','RUNNING','SUCCEEDED','FAILED')),
    attempts integer not null default 0 check (attempts>=0),
    available_at timestamptz not null, lease_until timestamptz, locked_by varchar(100), last_error varchar(2000),
    created_at timestamptz not null, updated_at timestamptz not null
);
create index background_jobs_due_idx on background_jobs(status,available_at,id);
create table notifications (
    id uuid primary key, recipient_id uuid not null references users(id), type varchar(100) not null,
    title varchar(200) not null, body varchar(2000) not null, link varchar(500),
    deduplication_key varchar(255) not null unique, read_at timestamptz, created_at timestamptz not null
);
create index notifications_recipient_idx on notifications(recipient_id,created_at desc,id);
create table audit_events (
    id uuid primary key, actor_id uuid references users(id), action varchar(100) not null,
    aggregate_type varchar(100) not null, aggregate_id uuid, outcome varchar(16) not null,
    metadata jsonb not null default '{}', created_at timestamptz not null
);
create index audit_events_aggregate_idx on audit_events(aggregate_type,aggregate_id,created_at,id);
create table report_exports (
    id uuid primary key, course_id uuid not null references courses(id), requested_by uuid not null references users(id),
    type varchar(32) not null, status varchar(16) not null check (status in ('PENDING','READY','FAILED')),
    storage_key varchar(500), created_at timestamptz not null, updated_at timestamptz not null
);
create index report_exports_requester_idx on report_exports(requested_by,course_id,created_at);
create table maintenance_state (
    id smallint primary key check (id=1), enabled boolean not null default false,
    changed_by uuid references users(id), changed_at timestamptz not null
);
