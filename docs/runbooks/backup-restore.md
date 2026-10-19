# Backup and restore

Goals (acceptance targets, not hosting promises): nightly backups so at most 24 hours of data can be lost (RPO), and a
rehearsed restore within four hours (RTO).

## What a backup contains

`scripts/backup.sh` produces `learning-backup-<UTC timestamp>.tar.enc` and a `.sha256` file:

- a `pg_dump` custom-format dump of the database;
- a snapshot of all private media (local directory, Docker volume, or an export of the S3 bucket);
- a manifest with SHA-256 checksums and the schema version;

encrypted with AES-256 (OpenSSL, PBKDF2 200,000 iterations) using an operator-managed key file. Keep the key outside
the server and outside the backup location; without it backups cannot be restored.

During the backup the installation is in **maintenance mode** for a few seconds to minutes: reads continue, changes
return "maintenance" (HTTP 503) and background jobs are not claimed. The script waits for running jobs, checks that
every referenced file exists (and aborts otherwise), verifies the archive it wrote, ends maintenance and applies
retention: the 7 newest archives plus the newest of each of the last 4 weeks; the newest verified archive is never
deleted. Copy archives off the server (e.g. `rclone`/`rsync` to another provider) after each run.

## Nightly backup

```bash
# Docker Compose
cat > /etc/learning-backup.env <<ENV
LEARNING_BACKUP_KEY_FILE=/root/learning-backup.key
PSQL="docker compose -f /opt/learning-portal/deploy/compose.yml exec -T postgres psql -U learning -d learning"
PG_DUMP="docker compose -f /opt/learning-portal/deploy/compose.yml exec -T postgres pg_dump -U learning -d learning"
PG_RESTORE="docker compose -f /opt/learning-portal/deploy/compose.yml exec -T postgres pg_restore"
MEDIA_EXPORT_CMD="docker cp learning-portal-app-1:/data/media/."
ENV
# VPS: PGHOST=localhost PGUSER=learning PGDATABASE=learning PGPASSWORD=... MEDIA_DIR=/var/lib/learning-portal/media
# S3:  MEDIA_EXPORT_CMD="aws s3 sync s3://learning-media"

openssl rand -base64 48 > /root/learning-backup.key && chmod 600 /root/learning-backup.key
echo '30 2 * * * root set -a; . /etc/learning-backup.env; /opt/learning-portal/scripts/backup.sh --output /var/backups/learning' \
  > /etc/cron.d/learning-backup
```

Check a backup at any time with `scripts/verify-backup.sh ARCHIVE` (same key variable).

## Restore (into an isolated environment first)

1. Prepare an **empty** environment: a new Compose project (`docker compose -p learning-restore ... up -d postgres`)
   or an empty database and media directory. Do not point the restore at the live installation; the script refuses
   non-empty targets.
2. Restore:

   ```bash
   LEARNING_BACKUP_KEY_FILE=/root/learning-backup.key \
   PSQL="docker exec -i learning-restore-postgres-1 psql -U learning -d learning" \
   PG_RESTORE="docker exec -i learning-restore-postgres-1 pg_restore -U learning -d learning" \
   RESTORE_MEDIA_DIR=/srv/restore/media \
   scripts/restore.sh /var/backups/learning/learning-backup-<stamp>.tar.enc --target isolated
   ```

   Wrong keys, checksum mismatches and corrupt archives stop the restore before anything is written.
3. Start the application with `LEARNING_JOBS_ENABLED=false` and without SMTP settings so no emails or jobs run, and
   copy the media into place (`docker cp /srv/restore/media/. learning-restore-app-1:/data/media/` then
   `docker exec -u 0 learning-restore-app-1 chown -R learning:learning /data/media`; on a VPS copy into
   `/var/lib/learning-portal/media` owned by `learning`). In S3 mode upload the media directory to the bucket.
4. Validate: administrator sign-in, a student's course with a private video and document, a published grade, and a
   certificate download. Then enable jobs and SMTP, switch DNS or volumes over, and keep the old environment until
   the next verified backup.

Measured drill results are in [../evidence/2026-10-20-backup-restore-drill.txt](../evidence/2026-10-20-backup-restore-drill.txt).
