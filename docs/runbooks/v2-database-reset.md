# Course-centric (v2) database reset

The course-centric release replaces classes with direct course enrollment. Its schema is a new baseline
(`backend/src/main/resources/db/coursecentric`, Flyway location `classpath:db/coursecentric`). It is **not** a
migration of the class-based (v1) schema, and no v1 data is carried over.

## What the application does

- Before Flyway runs, the application inspects the configured schema only. If it finds any table that exists only in
  the v1 schema (`classes`, `class_teachers`, `enrollments`, `user_roles`, `course_teachers`, `institution_settings`,
  `exam_assignments`, `invitations`, `password_reset_tokens`) it refuses to start with `LEGACY_SCHEMA_UNSUPPORTED`.
- It never drops, cleans or rewrites a database or its Flyway history. `flyway clean` is not used.
- The v1 migrations stay in `db/migration` for reference; they are not on the active Flyway location.

## Default path: create a fresh database

Keep the old installation running on the old release until the new one is verified.

```bash
# PostgreSQL 17, as the database superuser. Pick a new name; never reuse the v1 database.
createdb -O learning learning_v2
# Point the new release at it, e.g. in .env / the service environment:
LEARNING_DB_URL=jdbc:postgresql://localhost:5432/learning_v2
```

Start the new release. Flyway creates the v2 schema in the empty database; the workspace is in setup state
`REQUIRED`. Complete owner setup with the deployment setup secret (`LEARNING_SETUP_SECRET`) and verify the owner's
email (SMTP must be configured first, see [configuration](configuration.md)).

Media storage: use a new, empty media directory or bucket for v2. v1 media objects are not referenced by the v2
schema.

## Optional: delete the old database (explicit operator action)

Only after the v2 installation works and you no longer need the old data:

1. Take and verify a final v1 backup with the **old** release's `scripts/backup.sh` and keep it with its key. v1
   backups can be restored only by the old release; the v2 release refuses them.
2. Name and check the target before deleting anything:

   ```bash
   psql -d postgres -c "select datname from pg_database where datname = 'learning'"
   psql -d learning -c "select count(*) from information_schema.tables where table_name = 'classes'"  # 1 = v1 database
   ```

3. Delete it deliberately: `dropdb learning`. Never run this against the database named in the v2 `LEARNING_DB_URL`.

The application never performs these steps for you.

## Frontend

The existing frontend still calls class-based routes and is not compatible with the v2 backend. See
[course-centric breaking changes](../api/course-centric-breaking-changes.md) for the replacement routes.
