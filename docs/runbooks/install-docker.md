# Install with Docker Compose

Verified with Docker Engine 29.5 and Docker Compose 5.1 in a Colima VM (2 vCPU / 2 GiB). The recommended production
host is 4 vCPU / 8 GiB; see [../verification-results.md](../verification-results.md) for measured load results.

1. Install Docker Engine with the Compose plugin and point a DNS name (e.g. `learn.example.org`) at the host.
   Open ports 80 and 443 only.
2. Get the source and create the environment file:

   ```bash
   git clone <repository> learning-portal && cd learning-portal
   cp .env.example .env
   # Set LEARNING_PUBLIC_ORIGIN=https://learn.example.org, LEARNING_SITE_ADDRESS=learn.example.org,
   # strong random POSTGRES_PASSWORD (e.g. `openssl rand -base64 32`).
   chmod 600 .env
   ```

3. Build and start: `docker compose -f deploy/compose.yml --env-file .env up -d --build`.
   Caddy obtains HTTPS certificates automatically for `LEARNING_SITE_ADDRESS`.
4. Check health: `docker compose -f deploy/compose.yml ps` shows `app` as `healthy` (readiness checks the database and
   the selected storage).
5. Open `https://learn.example.org` right away and create the first administrator. Until one exists, anyone who can
   reach the site can complete setup; it is disabled afterwards.
6. Configure backups before real use: [backup-restore.md](backup-restore.md).

Data lives in the named volumes `postgres-data` (database), `media-data` (private files) and `caddy-data`
(certificates); they survive container replacement and upgrades.

## S3 storage

Set `LEARNING_STORAGE_MODE=s3` and the `LEARNING_S3_*` variables, create a private bucket, add the CORS rule from
[configuration.md](configuration.md), and set `LEARNING_MEDIA_ORIGIN` to the bucket's origin.

## Verifying an installation

`deploy/compose.s3-smoke.yml` adds a local S3-compatible server for testing S3 mode. The installation smoke test
(`cd frontend && SMOKE_BASE_URL=http://localhost:8088 npm run test:smoke`) performs setup, sign-in, a private video
upload through the proxy and student playback with HTTP ranges; results are recorded in
[../verification-results.md](../verification-results.md).
