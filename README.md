# Learning Portal

Self-hosted learning application for independent tutors and small schools: one institution per
installation, private lesson materials, classes, mixed exams, teacher grading and certificates.

- Architecture: [specification](.agent/specs/2026-10-18-learning-portal-architecture.md)
- Plan: [implementation plan](.agent/plans/2026-10-18-learning-portal.md)
- API: [docs/api/openapi.yaml](docs/api/openapi.yaml)
- Dependencies: [docs/dependencies.md](docs/dependencies.md)

## Layout

```text
backend/   Spring Boot 4.1 modular monolith (Java 21, Maven Wrapper)
frontend/  React 19 + TypeScript + Vite single-page app
deploy/    Docker Compose and container definitions
docs/      API contract, runbooks and dependency records
```

## Development

Requirements: Java 21, Node.js 22 (>= 22.22), Docker, ffprobe (FFmpeg).

```bash
# Database
docker compose -f deploy/compose.yml up -d postgres

# Backend (http://localhost:8080, management/health on :8081)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Or: backend with a throwaway Testcontainers database
cd backend && ./mvnw spring-boot:test-run

# Frontend (http://localhost:5173, proxies /api to :8080)
cd frontend && npm ci && npm run dev
```

## Verification

```bash
cd backend && ./mvnw verify          # unit tests (*Test) and integration tests (*IT, Testcontainers)
cd frontend && npm run lint && npm run typecheck && npm test -- --run && npm run build
cd frontend && npm run test:e2e      # Playwright; boots the backend via spring-boot:test-run
```

With Colima, export `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` and
`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` before running tests.
