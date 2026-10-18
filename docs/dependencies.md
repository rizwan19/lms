# Resolved dependencies

Recorded per task. Framework-managed backend libraries come from the Spring Boot BOM; only
explicitly versioned libraries are pinned in `backend/pom.xml`. Frontend versions are exact
(`.npmrc` sets `save-exact`) and locked in `frontend/package-lock.json`.

## Toolchain

| Tool | Version | Notes |
| --- | --- | --- |
| Java | 21 (Temurin 21.0.7 locally) | `java.version=21` |
| Maven | 3.9.16 via Maven Wrapper 3.3.4 | `backend/.mvn/wrapper/maven-wrapper.properties` |
| Node.js | 22.x, >= 22.22.0 | Several current toolchain packages (react-router 8, jsdom 30) require >= 22.22; still within the major-22 baseline |
| PostgreSQL | 17 (`postgres:17-alpine`) | Tests and development compose file |
| ffprobe | FFmpeg 9.x | Required at runtime for video metadata validation |

## Backend (resolved by Spring Boot 4.1.1 BOM unless pinned)

| Library | Version | Licence |
| --- | --- | --- |
| Spring Boot | 4.1.1 | Apache-2.0 |
| Spring Framework (webmvc) | 7.0.9 | Apache-2.0 |
| Spring Security | 7.1.1 | Apache-2.0 |
| Spring Session JDBC | 4.1.1 | Apache-2.0 |
| Spring Boot Mail (Jakarta Mail via BOM) | 4.1.1 | Apache-2.0 / EPL-2.0 |
| Hibernate ORM | 7.4.5.Final | Apache-2.0 |
| Flyway (core, database-postgresql) | 12.4.0 | Apache-2.0 |
| Jackson databind | 3.1.5 | Apache-2.0 |
| Tomcat embed | 11.0.24 | Apache-2.0 |
| PostgreSQL JDBC | 42.7.13 | BSD-2-Clause |
| Testcontainers | BOM-managed 2.x | MIT |
| OWASP Java HTML Sanitizer (pinned) | 20261011.1 | Apache-2.0 |
| Apache Commons CSV (pinned) | 1.14.1 | Apache-2.0 |
| ArchUnit (pinned) | 1.5.1 | Apache-2.0 |

## Frontend (exact)

| Package | Version | Licence |
| --- | --- | --- |
| react / react-dom | 19.3.1 | MIT |
| react-router | 8.4.0 | MIT |
| @tanstack/react-query | 5.104.4 | MIT |
| @tiptap/react / starter-kit / pm | 3.32.0 | MIT |
| typescript | 5.9.3 | Apache-2.0 |
| vite | 8.3.4 | MIT |
| @vitejs/plugin-react | 6.1.2 | MIT |
| vitest | 5.0.5 | MIT |
| jsdom | 30.1.3 | MIT |
| @testing-library/react / jest-dom / user-event | 16.3.3 / 6.9.1 / 14.6.8 | MIT |
| eslint / @eslint/js / typescript-eslint / eslint-plugin-react-hooks | 10.13.0 / 10.0.1 / 8.71.2 / 7.1.1 | MIT |
| @playwright/test | 1.64.1 | Apache-2.0 |
| @types/node | 22.x (dev) | MIT |
