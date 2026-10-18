-- A class delivers one published course edition to its own roster, teachers and dates.
create table classes (
    id                uuid         primary key,
    course_id         uuid         not null references courses (id),
    course_version_id uuid         not null references course_versions (id),
    name              varchar(200) not null check (length(trim(name)) > 0),
    status            varchar(16)  not null check (status in ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    starts_at         timestamptz,
    ends_at           timestamptz,
    created_by        uuid         not null references users (id),
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    activated_at      timestamptz,
    archived_at       timestamptz,
    version           bigint       not null default 0,
    check (starts_at is null or ends_at is null or ends_at > starts_at)
);

create index classes_course_idx on classes (course_id);
create index classes_course_version_idx on classes (course_version_id);

create table class_teachers (
    class_id   uuid not null references classes (id),
    teacher_id uuid not null references users (id),
    primary key (class_id, teacher_id)
);

create index class_teachers_teacher_idx on class_teachers (teacher_id);

create table enrollments (
    id                uuid        primary key,
    class_id          uuid        not null references classes (id),
    student_id        uuid        not null references users (id),
    status            varchar(16) not null check (status in ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    enrolled_at       timestamptz not null,
    status_changed_at timestamptz not null,
    version           bigint      not null default 0,
    unique (class_id, student_id)
);

create index enrollments_class_status_idx on enrollments (class_id, status);
create index enrollments_student_idx on enrollments (student_id);
