# Verification results

Release qualification for milestones A–E, run on 2026-10-20 at commit `test: qualify learning portal release and
delivery documentation`. Raw evidence is in [evidence/](evidence/).

## Environment

| Item | Value |
| --- | --- |
| Host | Apple M3 Pro (11 cores, 18 GiB), macOS 26.1 |
| Containers | Colima 0.10.3 VM, **2 vCPU / 2 GiB**, Docker Engine 29.5.2, Docker Compose 5.1.4 |
| Toolchain | OpenJDK 21.0.7, Node.js 22.23.3, Playwright 1.64.1, k6 2.3.0, osv-scanner 2.7.0 |
| Database | PostgreSQL 17 (Testcontainers for tests, `postgres:17` image in Compose) |

The specification's reference host is 4 vCPU / 8 GiB. All Compose measurements below ran on the smaller VM.

## Required gates

| Command | Outcome |
| --- | --- |
| `cd backend && ./mvnw verify` | Pass: 6 unit tests, 126 integration tests, 0 failures (81 s) |
| `cd frontend && npm run lint` | Pass |
| `cd frontend && npm run typecheck` | Pass |
| `cd frontend && npm test -- --run` | Pass: 12 files, 32 tests (includes axe-core accessibility checks) |
| `cd frontend && npm run build` | Pass (Vite warns that the main chunk exceeds 500 kB) |
| `cd frontend && npm run test:e2e` | Pass: 14 tests (1.1 min), including the complete learning journey and exam recovery |

Release-specific coverage: `ReleaseJourneyIT` (publish, enroll, study, mixed exam, application restart mid-attempt,
reconnect, teacher reassignment, grade correction, certificate revoke/reinstate, withdrawal, no cross-student leaks),
`DemoModeIT` (demo accounts exist only in the `demo` profile; refused with secure cookies),
`complete-learning-journey.spec.ts` and `exam-recovery.spec.ts` (browser reload and offline/online during an attempt).

## Security checks

| Check | Outcome |
| --- | --- |
| `npm audit` (frontend, all dependencies) | 0 vulnerabilities |
| `osv-scanner scan source --lockfile backend/pom.xml --lockfile frontend/package-lock.json` | Initially 10 findings: `tomcat-embed-core` 11.0.24 (3 critical) and Jackson 3.1.5 (5 high, 2 medium), both managed by Spring Boot 4.1.1, the latest 4.1 release. Fixed by overriding `tomcat.version` 11.0.27 and `jackson-bom.version` 3.1.8 in `backend/pom.xml`; re-scan: no issues. All gates above ran with the overrides. |
| Secret scan of tracked files (regex for cloud keys, private keys, tokens and hard-coded passwords; no gitleaks installed) | No findings. `.env` is ignored; `.env.example` holds placeholders. The demo password is intentionally public and only usable in demo mode. |
| CSP and limits | Restrictive CSP and request-size limits in `deploy/Caddyfile` / `Caddyfile.vps` (Task 16), exercised by the installation smoke test. |

## Installation, upgrade and recovery

| Check | Outcome |
| --- | --- |
| Clean Compose install, local storage | Smoke test passed ([evidence](evidence/2026-10-20-installation-smoke.txt)); re-run after the dependency overrides: passed (6.9 s) |
| Clean Compose install, S3 storage (SeaweedFS 3.97 stand-in) | Smoke test passed |
| In-place upgrade applying V013 to existing volumes | Passed ([evidence](evidence/2026-10-20-backup-restore-drill.txt)) |
| Backup, verify, restore to a fresh stack | Backup ~1 s, restore + start 11 s + ~15 s app start; row counts matched; smoke passed on the restored instance |
| Ubuntu 24.04 VPS steps (packages, PostgreSQL 17, systemd unit, readiness) | Rehearsed in an Ubuntu container up to readiness ([evidence](evidence/2026-10-20-vps-rehearsal.txt)) |

## Load test

`k6 run scripts/load/learning-portal.js` (5 minutes) against the Compose stack on the 2 vCPU / 2 GiB VM
([evidence](evidence/2026-10-20-load-test.txt)). Synthetic data: 1,000 students enrolled in one class with a 20-lesson
course and an 11-question mixed exam; 100 concurrent sessions, 70 studying (dashboard, player, progress saves) and 30
taking the exam (answer saves, attempt reads).

| Metric | Result |
| --- | --- |
| API requests (excluding sign-in and setup) p95 | **8.5 ms** (median 3.8 ms, p90 6.8 ms, max 664 ms); target < 500 ms |
| Failed requests | 0 of 35,948; all checks passed |
| Iterations | 12,888 in 5 minutes (~43 per second) |
| Container memory after the run | app 438 MiB, postgres 80 MiB, web 25 MiB |

Sign-in is excluded from the API figure on purpose: 100 simultaneous first sign-ins took up to 13.8 s on 2 vCPUs because
password hashing is deliberately expensive. Real sessions sign in spread out over time. The load run used the image
built before the Tomcat/Jackson patch overrides (patch-level change; smoke re-run afterwards).

## Known limitations

- **Certificates for names in complex scripts (e.g. Bengali) fail to render.** The bundled Noto Sans fonts are used
  without complex-script shaping, so such certificates are marked `RENDER_FAILED` with an explicit message instead of
  producing a wrong PDF (covered by `CertificateLifecycleIT`). Latin names are tested; Noto Sans also contains Cyrillic
  and Greek glyphs, which were not tested. A fix needs a shaping engine (e.g. Apache FOP or
  HarfBuzz) and matching Noto fonts.
- VPS installation was rehearsed in a container; Caddy with a real domain and Let's Encrypt TLS was not exercised.
- S3 mode was verified against SeaweedFS (the MinIO image could not be pulled), not AWS S3.
- Load and recovery timings come from a small VM and small media sets; large media libraries and S3 export timings
  were not measured.
- No OWASP dependency-check (NVD) run; dependency risk was assessed with osv-scanner and npm audit.
