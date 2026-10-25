# Notes for contributors and coding agents

Read [README.md](README.md) first, then the [specification](.agent/specs/2026-10-18-learning-portal-architecture.md)
for product rules. This file records the conventions the code relies on.

## Architecture

- `backend/` is a Spring Boot modular monolith under `com.saasresearcher.learning`. Each module
  (`identity`, `workspace`, `courses`, `enrollment`, `learning`, `assessments`, `grading`, `completion`,
  `certificates`, `reporting`, `media`, `notifications`, `operations`, `audit`) has an `api` package (public records and
  interfaces) and layer packages for everything else. `common` holds cross-cutting infrastructure only.
- Layer packages inside a module:
  - `controller`: REST controllers. Request/response records local to one endpoint may stay nested in the controller.
  - `service`: business logic, authorization helpers, `*Rules`, `*Errors`, and implementations of `api` interfaces.
  - `repository`: `JdbcClient` repositories and Spring Data interfaces.
  - `dto`: view and input records shared between controller and service. A DTO that is a class rather than a record
    uses Lombok `@Getter` instead of hand-written accessors.
  - `entity`: JPA entities only, with no records: use a surrogate `id` instead of a composite key, and plain columns
    for frozen values. Persistence always goes through JPA entities and Spring Data repositories. Annotate every
    entity with class-level Lombok `@Getter` and never hand-write a plain field accessor; callers use the standard
    `getX()`/`isX()` names. Keep only derived methods (`isActive()`, `toView()`, `schedule()`) and state-changing
    methods; no Lombok `@Setter`/`@Data` on entities.
  - Never declare an enum inside an entity class or the `entity` package; put it in the module's `api` package
    (`common` only for cross-cutting ones such as `Role`).
  - Never store an enum as a string: no `@Enumerated` and no `varchar` enum columns with `check (x in ('A', 'B'))`.
    The enum carries a permanent `short` code (`code()`/`fromCode()`, never reuse or renumber one), a `converter`
    maps it to a `smallint` column (null-safe only when the column is nullable), and the migration documents the
    codes in a comment (see `QuestionType`). Native SQL compares codes, never names.
  - `config`: `@ConfigurationProperties`, Spring configuration, schedules, filters and argument resolvers.
  - `infra`: job handlers, mail, storage, rendering and other adapters.
  - `converter`: JPA `AttributeConverter`s, declared `@Converter(autoApply = true)` so entities never reference them.
- Layers depend downwards only. Controllers never touch repositories: add a service method instead. Repositories may
  be used by `service`, `infra` and `config`; `infra` only by `service` and `config`.
- Modules depend on each other only through `api`; every other package of a module is internal even though its classes
  are `public`. `ModuleBoundariesTest` (ArchUnit) enforces both the module and the layer rules; keep it green.
- When two modules need each other, the lower module owns a listener/policy interface in its `api` and the higher one
  implements it (`CourseEditionListener`, `GrantPolicy`, `ProgressObserver`, `AttemptFinalizedListener`,
  `PublishedResultListener`, `CompletionListener`, `AssetAccessPolicy`). Do not add a direct call back.
- Background work goes through durable jobs (`DurableJobs.enqueue` + a `JobHandler` by type), never `@Async`. Jobs
  pause while maintenance mode is on.
- Two roles only (`TEACHER`, `STUDENT`); workspace powers come from current approval (`Actor.requireApprovedTeacher`).
  Course content, access, exams, grades and reports belong to the course's single author (`CourseOwnership`); there is
  no workspace-wide bypass.
- Lock orders: global approval/suspension: workspace → accounts in UUID order → application. Grants: accounts
  `FOR SHARE` in UUID order → course `FOR UPDATE` → enrollment → request/invitation. Learning and exam writes:
  account and course `FOR SHARE` → enrollment (`EnrollmentAccess.lockActive`/`lock`) → attempt → answers/grades.
  Starting an attempt takes no enrollment lock; concurrent starts are settled by the attempt unique indexes (409).
- Lock through `RowLocks` when the entity was already read in the transaction: a lock query returns the cached,
  possibly stale instance; `RowLocks` refreshes it under the lock.
- Schema lives in Flyway migrations (`backend/src/main/resources/db/coursecentric/V0NN__*.sql`). Until the first
  production release, change the migration that owns the table in place (reset local databases); after release, only
  add new migrations and never edit an applied one. `db/migration` holds the retired v1 schema for reference only. Hibernate runs in `validate` mode.
- Errors are RFC 9457 problem responses via `ApiException` with a stable `code`. Optimistic concurrency uses
  `expectedRevision` in requests and a `409` with a `*REVISION_CONFLICT` code.
- The REST contract lives in `docs/api/openapi.yaml`; `OpenApiContractIT` checks it against the running controllers.
- `frontend/` is React 19 + TanStack Query; features live in `frontend/src/features/<area>`, shared UI and the API
  client in `frontend/src/shared`.

## Conventions

- Keep it simple: no speculative abstractions, no defensive checks for states the domain already rules out.
- Use the injected `Clock`, never `Instant.now()`; tests control time through `MutableClock`.
- Integration tests extend `PostgresIntegrationTest` (fresh database per test) and use the fixtures in
  `backend/src/test/java/.../support`. Name unit tests `*Test` and integration tests `*IT`.
- Never log or commit secrets. Demo credentials exist only in the `demo` profile, which refuses secure cookies.
- Commit one task per commit with a conventional message (`feat:`, `fix:`, `test:`, `docs:`).

## Commands

```bash
cd backend && ./gradlew check                                 # all backend tests
cd backend && ./gradlew integrationTest --tests CourseAccessRequestIT   # one IT
cd frontend && npm run lint && npm run typecheck && npm test -- --run && npm run build
cd frontend && npm run test:e2e                               # Playwright, boots backend with Testcontainers
```

Colima users: export `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` and
`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.
