# Notes for contributors and coding agents

Read [README.md](README.md) first, then the [specification](.agent/specs/2026-10-18-learning-portal-architecture.md)
for product rules. This file records the conventions the code relies on.

## Architecture

- `backend/` is a Spring Boot modular monolith under `com.saasresearcher.learning`. Each module
  (`identity`, `institution`, `courses`, `classrooms`, `learning`, `assessments`, `grading`, `completion`,
  `certificates`, `reporting`, `media`, `notifications`, `operations`, `audit`) has an `api` package (public records and
  interfaces) and layer packages for everything else. `common` holds cross-cutting infrastructure only.
- Layer packages inside a module:
  - `controller`: REST controllers. Request/response records local to one endpoint may stay nested in the controller.
  - `service`: business logic, authorization helpers, `*Rules`, `*Errors`, and implementations of `api` interfaces.
  - `repository`: `JdbcClient` repositories and Spring Data interfaces.
  - `dto`: view and input records shared between controller and service.
  - `entity`: JPA entities and JDBC row records.
  - `config`: `@ConfigurationProperties`, Spring configuration, schedules, filters and argument resolvers.
  - `infra`: job handlers, mail, storage, rendering and other adapters.
- Layers depend downwards only. Controllers never touch repositories: add a service method instead. Repositories may
  be used by `service`, `infra` and `config`; `infra` only by `service` and `config`.
- Modules depend on each other only through `api`; every other package of a module is internal even though its classes
  are `public`. `ModuleBoundariesTest` (ArchUnit) enforces both the module and the layer rules; keep it green.
- When two modules need each other, the lower module owns a listener/policy interface in its `api` and the higher one
  implements it (`ClassLifecycleListener`, `ProgressObserver`, `AttemptFinalizedListener`, `PublishedResultListener`,
  `CompletionListener`, `AssetAccessPolicy`, `RequiredExamValidator`). Do not add a direct call back.
- Background work goes through durable jobs (`DurableJobs.enqueue` + a `JobHandler` by type), never `@Async`. Jobs
  pause while maintenance mode is on.
- Lock order for exam work: enrollment (`EnrollmentAccess.lock`) before attempt.
- Schema changes are new Flyway migrations (`backend/src/main/resources/db/migration/V0NN__*.sql`); never edit an
  applied one. Hibernate runs in `validate` mode.
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
cd backend && ./gradlew integrationTest --tests ReleaseJourneyIT   # one IT
cd frontend && npm run lint && npm run typecheck && npm test -- --run && npm run build
cd frontend && npm run test:e2e                               # Playwright, boots backend with Testcontainers
```

Colima users: export `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` and
`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.
