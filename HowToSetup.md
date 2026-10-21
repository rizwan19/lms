# How to set up Learning Portal

Three paths, from quickest to production. All commands run from the repository root unless stated.

## 1. Try it locally (demo mode)

Demo mode creates three accounts on an empty database so you can click through every role.
It only starts with insecure (HTTP) cookies, so it can never run on a production configuration.

Requirements: Java 21, Node.js 22 (>= 22.22), PostgreSQL 17, ffprobe (FFmpeg).

```bash
# Once: PostgreSQL 17 as a background service (macOS/Homebrew), database/user/password learning
brew install postgresql@17 && brew services start postgresql@17
PG="$(brew --prefix postgresql@17)/bin"; "$PG/createuser" learning --pwprompt && "$PG/createdb" -O learning learning

cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev,demo'  # terminal 1
cd frontend && npm ci && npm run dev                                         # terminal 2
```

Open http://localhost:5173 and sign in as `demo-admin`, `demo-teacher` or `demo-student` with the password
`demo-password-not-secret`. Demo accounts are created only when no administrator exists yet.

PostgreSQL keeps running in the background (`brew services stop postgresql@17` to stop it). With Docker instead,
`docker compose -f deploy/compose.yml -f deploy/compose.dev.yml up -d postgres` provides the same database, and
`cd backend && ./gradlew bootTestRun` starts the backend against a throwaway Testcontainers database (data is lost on exit).

## 2. Install on one machine with Docker Compose

Follow [docs/runbooks/install-docker.md](docs/runbooks/install-docker.md). In short:

```bash
cp .env.example .env            # set POSTGRES_PASSWORD, LEARNING_PUBLIC_ORIGIN, LEARNING_SITE_ADDRESS
docker compose -f deploy/compose.yml --env-file .env up -d --build
```

Then open the public URL, choose **Set up institution** and create the first administrator.
Every setting is described in [docs/runbooks/configuration.md](docs/runbooks/configuration.md).

To run the same stack on your own machine over plain HTTP, set `LEARNING_PUBLIC_ORIGIN=http://localhost:<port>`,
`LEARNING_SITE_ADDRESS=:80`, `LEARNING_SECURE_COOKIES=false` and `LEARNING_HTTP_PORT=<port>` in `.env`, then use the
Gradle shortcuts (they fall back to `docker-compose` when the `docker compose` plugin is unavailable):

```bash
cd backend && ./gradlew dockerUp      # build and start, wait until healthy
cd backend && ./gradlew dockerDown    # stop; data stays in the Docker volumes
```

## 3. Install on an Ubuntu VPS without containers

Follow [docs/runbooks/install-vps.md](docs/runbooks/install-vps.md): PostgreSQL 17 from the OS packages, the
application JAR as a systemd service ([deploy/learning-portal.service](deploy/learning-portal.service)) and Caddy for
TLS ([deploy/Caddyfile.vps](deploy/Caddyfile.vps)).

## After installing

- Email: [docs/runbooks/email-delivery.md](docs/runbooks/email-delivery.md) (optional; without SMTP, administrators
  hand out temporary passwords).
- Media storage: local disk by default; S3-compatible object storage via `LEARNING_STORAGE_MODE=s3`.
- Backups: [docs/runbooks/backup-restore.md](docs/runbooks/backup-restore.md). Schedule `scripts/backup.sh` and run
  `scripts/verify-backup.sh` on every archive.
- Upgrades: [docs/runbooks/upgrades.md](docs/runbooks/upgrades.md).
- Health: `curl http://127.0.0.1:8081/actuator/health` on the host (management port is not exposed publicly).
