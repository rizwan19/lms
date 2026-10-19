# Upgrades

Schema changes are Flyway migrations applied automatically at start-up (`V001`...); applied migrations are never
edited. There is no automatic down-migration: rolling back across a migration means restoring the pre-upgrade backup.

1. Read the release notes for configuration changes.
2. Take and verify a backup: `scripts/backup.sh --output ...` (see [backup-restore.md](backup-restore.md)).
   Copy it off the server.
3. Upgrade:
   - Docker: `git pull && docker compose -f deploy/compose.yml --env-file .env up -d --build`
   - VPS: replace `/opt/learning-portal/app.jar` and `/var/www/learning-portal`, then `systemctl restart learning-portal`.
4. Watch `docker compose logs -f app` (or `journalctl -u learning-portal -f`) until readiness reports UP, then sign in
   and open a course, an exam result and a certificate.
5. If the upgrade fails **before** a migration is applied, start the previous version again. If a migration was applied,
   roll back by restoring the pre-upgrade backup into an empty environment and running the previous version on it;
   changes made after the backup are lost.

Rehearsed on 2026-10-20: an in-place Compose upgrade applied migration V013 to existing data (see
[../evidence/2026-10-20-backup-restore-drill.txt](../evidence/2026-10-20-backup-restore-drill.txt)).
