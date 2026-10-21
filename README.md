# Learning Portal

Self-hosted learning application for independent tutors and small schools: one institution per
installation, private lesson materials, classes, mixed exams, teacher grading and certificates.

- Architecture: [specification](.agent/specs/2026-10-18-learning-portal-architecture.md)
- Plan: [implementation plan](.agent/plans/2026-10-18-learning-portal.md)
- API: [docs/api/openapi.yaml](docs/api/openapi.yaml)
- Dependencies: [docs/dependencies.md](docs/dependencies.md)
- Setup guide: [HowToSetup.md](HowToSetup.md) (demo mode, Docker Compose, VPS)
- Contributor and agent notes: [AGENTS.md](AGENTS.md)
- Release: [docs/release-checklist.md](docs/release-checklist.md), [docs/verification-results.md](docs/verification-results.md)

## Layout

```text
backend/   Spring Boot 4.1 modular monolith (Java 21, Gradle Wrapper)
frontend/  React 19 + TypeScript + Vite single-page app
deploy/    Docker Compose and container definitions
docs/      API contract, runbooks, dependency records and verification evidence
scripts/   Backup/restore scripts and the k6 load test
```

## Development

Requirements: Java 21, Node.js 22 (>= 22.22), PostgreSQL 17, ffprobe (FFmpeg). Docker is needed only for the tests
and `bootTestRun`.

```bash
# Database (development, once): PostgreSQL 17 as a background service, database/user/password learning
brew install postgresql@17 && brew services start postgresql@17
PG="$(brew --prefix postgresql@17)/bin"; "$PG/createuser" learning --pwprompt && "$PG/createdb" -O learning learning

# Backend (http://localhost:8080, management/health on :8081)
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'

# Or: backend with a throwaway Testcontainers database
cd backend && ./gradlew bootTestRun

# Frontend (http://localhost:5173, proxies /api to :8080)
cd frontend && npm ci && npm run dev
```

Add the `demo` profile (`--args='--spring.profiles.active=dev,demo'`) to create `demo-admin`, `demo-teacher` and
`demo-student` (password `demo-password-not-secret`) on an empty database. Demo mode refuses to start with secure
cookies, i.e. on any production configuration.

## Installation

- Docker Compose: [docs/runbooks/install-docker.md](docs/runbooks/install-docker.md)
- Manual Ubuntu VPS: [docs/runbooks/install-vps.md](docs/runbooks/install-vps.md)
- Settings: [docs/runbooks/configuration.md](docs/runbooks/configuration.md)

## Verification

```bash
cd backend && ./gradlew check        # unit tests (*Test) and integration tests (*IT, Testcontainers)
cd frontend && npm run lint && npm run typecheck && npm test -- --run && npm run build
cd frontend && npm run test:e2e      # Playwright; boots the backend via gradle bootTestRun
k6 run scripts/load/learning-portal.js   # load test against a running stack (see the script header)
```

With Colima, export `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` and
`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` before running tests.
