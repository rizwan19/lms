-- Installation-wide maintenance switch used by backups and restores: ordinary mutations are refused and background
-- jobs are not claimed while it is on. Backup scripts toggle it with SQL so they need no application credentials.
create table system_state (
    id          smallint     primary key check (id = 1),
    maintenance boolean      not null default false,
    reason      varchar(200),
    updated_at  timestamptz  not null
);

insert into system_state (id, maintenance, updated_at) values (1, false, now());
