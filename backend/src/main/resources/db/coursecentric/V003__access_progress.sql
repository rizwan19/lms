-- One enrollment per course and learner, pinned to the edition it was granted on. Suspension, withdrawal and
-- restoration keep the same row, so progress, attempts and grades are preserved.
create table course_enrollments (
    id                uuid        primary key,
    course_id         uuid        not null references courses (id),
    course_version_id uuid        not null,
    learner_id        uuid        not null references users (id),
    -- EnrollmentStatus code: 0 ACTIVE, 1 SUSPENDED, 2 WITHDRAWN.
    status            smallint    not null check (status between 0 and 2),
    -- GrantSource code: 0 REQUEST, 1 INVITATION.
    grant_source      smallint    not null check (grant_source between 0 and 1),
    granted_by        uuid        not null references users (id),
    learning_locked   boolean     not null default false,
    granted_at        timestamptz not null,
    status_changed_at timestamptz not null,
    version           bigint      not null default 0,
    foreign key (course_id, course_version_id) references course_versions (course_id, id),
    unique (course_id, learner_id),
    unique (id, course_id, course_version_id),
    unique (course_id, learner_id, id)
);

create index course_enrollments_learner_idx on course_enrollments (learner_id, status, id);
create index course_enrollments_roster_idx on course_enrollments (course_id, status, id);

-- Current request state per course and learner; every transition is appended to course_access_events.
create table course_access_requests (
    id            uuid          primary key,
    course_id     uuid          not null references courses (id),
    learner_id    uuid          not null references users (id),
    -- AccessRequestStatus code: 0 PENDING, 1 APPROVED, 2 REJECTED, 3 CANCELLED.
    status        smallint      not null check (status between 0 and 3),
    decided_by    uuid          references users (id),
    reason        varchar(1000),
    enrollment_id uuid          references course_enrollments (id),
    requested_at  timestamptz   not null,
    decided_at    timestamptz,
    version       bigint        not null default 0,
    unique (course_id, learner_id),
    foreign key (course_id, learner_id, enrollment_id) references course_enrollments (course_id, learner_id, id),
    check ((status = 0 and decided_by is null and decided_at is null)
        or (status <> 0 and decided_by is not null and decided_at is not null)),
    check (status <> 1 or enrollment_id is not null)
);

create index course_access_requests_author_queue_idx on course_access_requests (course_id, status, requested_at, id);

create table course_access_events (
    id          uuid          primary key,
    request_id  uuid          not null references course_access_requests (id),
    actor_id    uuid          not null references users (id),
    -- AccessRequestStatus codes, as on course_access_requests.status.
    from_status smallint      check (from_status between 0 and 3),
    to_status   smallint      not null check (to_status between 0 and 3),
    reason      varchar(1000),
    created_at  timestamptz   not null
);

create index course_access_events_request_idx on course_access_events (request_id, created_at, id);

-- Author invitations preauthorize enrollment for the verified account with the matching email.
create table course_invitations (
    id              uuid         primary key,
    course_id       uuid         not null references courses (id),
    invited_by      uuid         not null references users (id),
    recipient_email varchar(254) not null check (recipient_email = lower(trim(recipient_email))),
    token_hash      varchar(64)  not null unique,
    -- InvitationStatus code: 0 PENDING, 1 ACCEPTED, 2 REVOKED, 3 EXPIRED.
    status          smallint     not null check (status between 0 and 3),
    accepted_by     uuid         references users (id),
    enrollment_id   uuid         references course_enrollments (id),
    created_at      timestamptz  not null,
    expires_at      timestamptz  not null,
    accepted_at     timestamptz,
    revoked_at      timestamptz,
    version         bigint       not null default 0,
    foreign key (course_id, accepted_by, enrollment_id) references course_enrollments (course_id, learner_id, id),
    check (expires_at > created_at),
    check ((status = 1 and accepted_by is not null and enrollment_id is not null and accepted_at is not null)
        or (status <> 1 and accepted_by is null and accepted_at is null))
);

create unique index course_invitations_one_pending_idx on course_invitations (course_id, recipient_email) where status = 0;
create index course_invitations_recipient_idx on course_invitations (recipient_email, status);

-- Explicit lesson completion and monotonic resume position per enrollment, on the enrollment's own edition.
create table lesson_progress (
    id                uuid        primary key,
    enrollment_id     uuid        not null,
    course_id         uuid        not null,
    course_version_id uuid        not null,
    lesson_id         uuid        not null,
    completed_at      timestamptz,
    resume_seconds    bigint      not null default 0 check (resume_seconds >= 0),
    last_activity_at  timestamptz not null,
    revision          bigint      not null check (revision > 0),
    unique (enrollment_id, lesson_id),
    foreign key (enrollment_id, course_id, course_version_id) references course_enrollments (id, course_id, course_version_id),
    foreign key (course_version_id, lesson_id) references lessons (course_version_id, id)
);

create index lesson_progress_activity_idx on lesson_progress (enrollment_id, last_activity_at);
