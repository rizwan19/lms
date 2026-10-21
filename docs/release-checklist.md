# Release checklist

Run before tagging a release. Record results in [verification-results.md](verification-results.md).

## Build gates (all must pass)

- [ ] `cd backend && ./gradlew check` — unit, integration, module-boundary and OpenAPI contract tests.
- [ ] `cd frontend && npm run lint && npm run typecheck && npm test -- --run && npm run build`
- [ ] `cd frontend && npm run test:e2e` — includes the complete learning journey and exam recovery specs.
- [ ] `docs/api/openapi.yaml` updated for any route change (`OpenApiContractIT` fails otherwise).

## Security

- [ ] `cd frontend && npm audit` reports no vulnerabilities.
- [ ] `osv-scanner scan source --lockfile backend/gradle.lockfile --lockfile frontend/package-lock.json` reports no issues,
      or each finding is patched or documented with a reason.
- [ ] Secret scan of tracked files finds nothing (`gitleaks detect` or the pattern scan in verification-results).
- [ ] `.env` and backups are not tracked; `.env.example` holds placeholders only.
- [ ] Caddy CSP and request-size limits unchanged or reviewed (`deploy/Caddyfile`, `deploy/Caddyfile.vps`).
- [ ] New third-party code or fonts listed in [third-party-notices.md](third-party-notices.md) and
      [dependencies.md](dependencies.md).

## Installation and operations (when packaging, storage or migrations changed)

- [ ] Clean Compose install, local storage: `npm run test:smoke` against the stack.
- [ ] Clean Compose install, S3 storage (`deploy/compose.s3-smoke.yml`): `npm run test:smoke`.
- [ ] Upgrade from the previous release's volumes applies new migrations and the smoke test passes.
- [ ] Backup, `verify-backup.sh` and restore drill onto a fresh stack; smoke test passes on the restored instance.
- [ ] Load test (`scripts/load/learning-portal.js`) at the planning workload; record p95 and environment.

## Package

- [ ] Version set in `backend/build.gradle.kts` and `frontend/package.json`.
- [ ] Source package: `git archive --format=tar.gz --prefix=learning-portal-<version>/ -o learning-portal-<version>.tar.gz <tag>`.
- [ ] Release notes list migrations, configuration changes and known limitations.
