-- Institution: exactly one settings row per installation.
create table institution_settings (
    id         smallint     primary key check (id = 1),
    name       varchar(200) not null,
    timezone   varchar(64)  not null,
    updated_at timestamptz  not null,
    version    bigint       not null default 0
);

insert into institution_settings (id, name, timezone, updated_at) values (1, 'Learning Portal', 'UTC', now());

-- Accounts. Usernames and emails are stored normalized (trimmed, lower case).
create table users (
    id                       uuid         primary key,
    username                 varchar(64)  not null unique check (username ~ '^[a-z0-9._-]{3,64}$'),
    email                    varchar(254) unique,
    display_name             varchar(150) not null check (length(trim(display_name)) > 0),
    password_hash            varchar(255),
    status                   varchar(16)  not null check (status in ('ACTIVE', 'DISABLED')),
    password_change_required boolean      not null default false,
    password_expires_at      timestamptz,
    created_by               uuid         references users (id),
    created_at               timestamptz  not null,
    updated_at               timestamptz  not null,
    version                  bigint       not null default 0
);

create index users_created_by_idx on users (created_by);

create table user_roles (
    user_id uuid        not null references users (id),
    role    varchar(32) not null check (role in ('ADMINISTRATOR', 'TEACHER', 'STUDENT')),
    primary key (user_id, role)
);

create index user_roles_role_idx on user_roles (role, user_id);

-- One-time tokens are stored as SHA-256 hashes only.
create table invitations (
    id         uuid        primary key,
    user_id    uuid        not null references users (id),
    token_hash varchar(64) not null unique,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null
);

create index invitations_user_idx on invitations (user_id);

create table password_reset_tokens (
    id         uuid        primary key,
    user_id    uuid        not null references users (id),
    token_hash varchar(64) not null unique,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null
);

create index password_reset_tokens_user_idx on password_reset_tokens (user_id);

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
