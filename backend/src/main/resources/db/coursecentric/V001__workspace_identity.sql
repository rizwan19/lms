-- Course-centric v2 baseline for an EMPTY database. Legacy (db/migration) databases are refused before Flyway runs.

-- Accounts have exactly one role. Teaching approval is account state (teacher_status), never a third role.
-- Usernames and emails are stored normalized (trimmed, lower case); every account has a unique email.
create table users (
    id                uuid         primary key,
    username          varchar(64)  not null unique check (username ~ '^[a-z0-9._-]{3,64}$'),
    email             varchar(254) not null unique check (email = lower(trim(email))),
    display_name      varchar(150) not null check (length(trim(display_name)) > 0),
    password_hash     varchar(255) not null,
    -- Role code: 0 STUDENT, 1 TEACHER.
    role              smallint     not null check (role between 0 and 1),
    -- AccountStatus code: 0 ACTIVE, 1 SUSPENDED.
    status            smallint     not null check (status between 0 and 1),
    email_verified_at timestamptz,
    -- TeacherStatus code: 0 PENDING, 1 APPROVED, 2 REJECTED.
    teacher_status    smallint     check (teacher_status between 0 and 2),
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    version           bigint       not null default 0,
    check ((role = 0 and teacher_status is null) or (role = 1 and teacher_status is not null)),
    check (teacher_status is distinct from 1 or email_verified_at is not null)
);

create index users_approved_teachers_idx on users (id)
    where role = 1 and teacher_status = 1 and status = 0;

-- One workspace per installation. created_by is provenance only, never a permanent superuser bypass.
create table workspace (
    id                  smallint     primary key check (id = 1),
    name                varchar(200) not null,
    timezone            varchar(64)  not null,
    -- WorkspaceState code: 0 REQUIRED, 1 OWNER_VERIFICATION_PENDING, 2 ACTIVE.
    state               smallint     not null check (state between 0 and 2),
    created_by          uuid         references users (id),
    public_verification boolean      not null default false,
    certificate_show_name boolean    not null default false,
    created_at          timestamptz  not null,
    updated_at          timestamptz  not null,
    version             bigint       not null default 0,
    check (state = 0 or created_by is not null)
);

-- The singleton row with fixed provenance; no account, password, setup secret or session is seeded.
insert into workspace (id, name, timezone, state, created_at, updated_at)
values (1, 'Learning Portal', 'UTC', 0, timestamptz '2026-10-23 00:00:00+00', timestamptz '2026-10-23 00:00:00+00');

-- One-time tokens are stored as SHA-256 hashes only and are bound to one purpose.
create table identity_tokens (
    id          uuid        primary key,
    user_id     uuid        not null references users (id),
    -- TokenPurpose code: 0 VERIFY_EMAIL, 1 OWNER_VERIFY_EMAIL, 2 RESET_PASSWORD.
    purpose     smallint    not null check (purpose between 0 and 2),
    token_hash  varchar(64) not null unique,
    created_at  timestamptz not null,
    expires_at  timestamptz not null,
    consumed_at timestamptz,
    revoked_at  timestamptz,
    check (expires_at > created_at)
);

create index identity_tokens_user_purpose_idx on identity_tokens (user_id, purpose);

-- Teacher applications: the first decision by another active approved teacher resolves the application.
create table teacher_applications (
    id           uuid          primary key,
    applicant_id uuid          not null references users (id),
    -- ApplicationStatus code: 0 PENDING, 1 APPROVED, 2 REJECTED.
    status       smallint      not null check (status between 0 and 2),
    decided_by   uuid          references users (id),
    reason       varchar(1000),
    created_at   timestamptz   not null,
    decided_at   timestamptz,
    version      bigint        not null default 0,
    check (decided_by is null or decided_by <> applicant_id),
    check ((status = 0 and decided_by is null and decided_at is null)
        or (status <> 0 and decided_by is not null and decided_at is not null))
);

create unique index teacher_applications_one_pending on teacher_applications (applicant_id) where status = 0;
create index teacher_applications_queue_idx on teacher_applications (status, created_at, id);

create table auth_rate_limits (
    bucket            varchar(400) primary key,
    attempts          integer      not null check (attempts >= 0),
    window_started_at timestamptz  not null
);

-- Spring Session JDBC (schema-postgresql.sql from spring-session-jdbc 4.1.1).
create table spring_session (
    primary_id            char(36)     not null,
    session_id            char(36)     not null,
    creation_time         bigint       not null,
    last_access_time      bigint       not null,
    max_inactive_interval int          not null,
    expiry_time           bigint       not null,
    principal_name        varchar(100),
    constraint spring_session_pk primary key (primary_id)
);

create unique index spring_session_ix1 on spring_session (session_id);
create index spring_session_ix2 on spring_session (expiry_time);
create index spring_session_ix3 on spring_session (principal_name);

create table spring_session_attributes (
    session_primary_id char(36)     not null,
    attribute_name     varchar(200) not null,
    attribute_bytes    bytea        not null,
    constraint spring_session_attributes_pk primary key (session_primary_id, attribute_name),
    constraint spring_session_attributes_fk foreign key (session_primary_id) references spring_session (primary_id) on delete cascade
);
