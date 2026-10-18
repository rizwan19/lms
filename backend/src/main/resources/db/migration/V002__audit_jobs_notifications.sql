-- Append-only business and security history. Never stores credentials or raw exam answers.
create table audit_events (
    id             uuid         primary key,
    occurred_at    timestamptz  not null,
    actor_id       uuid         references users (id),
    action         varchar(100) not null,
    aggregate_type varchar(50)  not null,
    aggregate_id   uuid,
    outcome        varchar(16)  not null check (outcome in ('SUCCESS', 'DENIED', 'FAILURE')),
    metadata       jsonb        not null default '{}'
);

create index audit_events_aggregate_idx on audit_events (aggregate_type, aggregate_id, occurred_at);
create index audit_events_time_idx on audit_events (occurred_at);

-- Durable work inserted in the caller's transaction and claimed with FOR UPDATE SKIP LOCKED leases.
create table background_jobs (
    id                uuid          primary key,
    type              varchar(100)  not null,
    aggregate_id      uuid,
    deduplication_key varchar(200)  unique,
    payload           jsonb         not null default '{}',
    status            varchar(16)   not null check (status in ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    attempts          integer       not null default 0 check (attempts >= 0),
    available_at      timestamptz   not null,
    lease_until       timestamptz,
    lease_token       uuid,
    last_error        varchar(2000),
    created_at        timestamptz   not null,
    updated_at        timestamptz   not null,
    completed_at      timestamptz
);

create index background_jobs_claim_idx on background_jobs (status, available_at);

create table notifications (
    id                uuid          primary key,
    recipient_id      uuid          not null references users (id),
    type              varchar(100)  not null,
    title             varchar(200)  not null,
    body              varchar(2000) not null default '',
    link              varchar(500),
    deduplication_key varchar(200)  unique,
    created_at        timestamptz   not null,
    read_at           timestamptz
);

create index notifications_recipient_idx on notifications (recipient_id, read_at, created_at desc);
