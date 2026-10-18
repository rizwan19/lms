-- A course is stable identity and ownership; content lives in editions (course_versions).
-- One mutable DRAFT per course; PUBLISHED editions are immutable and numbered per course.
create table courses (
    id          uuid         primary key,
    title       varchar(200) not null,
    status      varchar(16)  not null check (status in ('ACTIVE', 'ARCHIVED')),
    created_by  uuid         not null references users (id),
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    archived_at timestamptz,
    version     bigint       not null default 0
);

create table course_teachers (
    course_id  uuid not null references courses (id),
    teacher_id uuid not null references users (id),
    primary key (course_id, teacher_id)
);

create index course_teachers_teacher_idx on course_teachers (teacher_id);

create table course_versions (
    id                  uuid          primary key,
    course_id           uuid          not null references courses (id),
    edition             integer       check (edition > 0),
    status              varchar(16)   not null check (status in ('DRAFT', 'PUBLISHED')),
    title               varchar(200)  not null,
    description         varchar(5000) not null default '',
    based_on_version_id uuid          references course_versions (id),
    published_at        timestamptz,
    published_by        uuid          references users (id),
    created_at          timestamptz   not null,
    updated_at          timestamptz   not null,
    version             bigint        not null default 0,
    unique (course_id, edition),
    check ((status = 'DRAFT' and edition is null) or (status = 'PUBLISHED' and edition is not null and published_at is not null))
);

create unique index course_versions_single_draft on course_versions (course_id) where status = 'DRAFT';

create table sections (
    id                uuid         primary key,
    course_version_id uuid         not null references course_versions (id),
    title             varchar(200) not null,
    position          integer      not null check (position >= 0),
    unique (course_version_id, position)
);

create table lessons (
    id                uuid         primary key,
    section_id        uuid         not null references sections (id),
    course_version_id uuid         not null references course_versions (id),
    title             varchar(200) not null,
    position          integer      not null check (position >= 0),
    body_html         text         not null default '',
    video_asset_id    uuid         references media_assets (id),
    video_provider    varchar(16)  check (video_provider in ('YOUTUBE', 'VIMEO')),
    video_ref         varchar(64),
    required          boolean      not null default true,
    unique (section_id, position),
    check ((video_provider is null) = (video_ref is null)),
    check (video_asset_id is null or video_provider is null)
);

create index lessons_version_idx on lessons (course_version_id);
create index lessons_video_asset_idx on lessons (video_asset_id);

create table lesson_resources (
    lesson_id uuid    not null references lessons (id),
    position  integer not null check (position >= 0),
    asset_id  uuid    not null references media_assets (id),
    primary key (lesson_id, position)
);

create index lesson_resources_asset_idx on lesson_resources (asset_id);
