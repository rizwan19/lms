#!/usr/bin/env bash
# Restores a verified backup into an isolated, empty environment. Never restores over a live installation.
#
#   LEARNING_BACKUP_KEY_FILE=/root/backup.key PSQL=... PG_RESTORE="pg_restore -d learning_restore --no-owner" \
#   RESTORE_MEDIA_DIR=/srv/restore/media scripts/restore.sh ARCHIVE --target isolated
#
# PSQL and PG_RESTORE must point at the *target* database. The target must have no application tables and the media
# directory must be empty. Start the restored application with LEARNING_JOBS_ENABLED=false and without SMTP until it
# has been checked; then enable jobs and email deliberately.
source "$(dirname "$0")/backup-common.sh"

ARCHIVE=""
TARGET=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --target) TARGET="$2"; shift 2 ;;
        *) ARCHIVE="$1"; shift ;;
    esac
done
[[ -n "$ARCHIVE" && "$TARGET" == "isolated" ]] || die "usage: restore.sh ARCHIVE --target isolated"
require_key
[[ -n "${RESTORE_MEDIA_DIR:-}" ]] || die "RESTORE_MEDIA_DIR is not set"
[[ -n "${PG_RESTORE:-}" ]] || die "PG_RESTORE must name the target database explicitly"

existing=$(psql_value "select count(*) from information_schema.tables where table_schema = 'public'")
[[ "$existing" == "0" ]] || die "target database is not empty; refusing to restore over a live installation"
mkdir -p "$RESTORE_MEDIA_DIR"
[[ -z "$(ls -A "$RESTORE_MEDIA_DIR")" ]] || die "media directory $RESTORE_MEDIA_DIR is not empty"

DIR=$(mktemp -d)
trap 'rm -rf "$DIR"' EXIT
unpack_verified "$ARCHIVE" "$DIR"
$PG_RESTORE --no-owner --exit-on-error < "$DIR/database.dump"
tar -xf "$DIR/media.tar" -C "$DIR"
cp -R "$DIR/media/." "$RESTORE_MEDIA_DIR/"
${PSQL:-psql} -X -q -v ON_ERROR_STOP=1 -c "update system_state set maintenance = false, reason = null, updated_at = now() where id = 1" >/dev/null
echo "restored $ARCHIVE; start the application with LEARNING_JOBS_ENABLED=false and no SMTP, then check it"
