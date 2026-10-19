#!/usr/bin/env bash
# Consistent encrypted backup of the database and media.
#
#   LEARNING_BACKUP_KEY_FILE=/root/backup.key MEDIA_DIR=/var/lib/learning-portal/media scripts/backup.sh --output /var/backups/learning
#
# Steps: maintenance on (mutations refused, job claims paused) -> wait for running jobs -> check every referenced file
# exists -> pg_dump + media snapshot + checksum manifest -> AES-256 encrypted archive -> verify -> maintenance off ->
# retention (7 newest daily, newest per week for 4 weeks; the newest verified archive is never deleted).
# Media source: MEDIA_DIR (a local directory), or MEDIA_EXPORT_CMD, a command that copies all media into the directory
# given as its last argument, e.g. "docker cp learning-portal-app-1:/data/media/." for Docker volumes or
# "aws s3 sync s3://learning-media" for S3 storage (exports the immutable objects so the backup is portable).
source "$(dirname "$0")/backup-common.sh"

OUTPUT=""
KEEP_DAILY=7
KEEP_WEEKLY=4
while [[ $# -gt 0 ]]; do
    case "$1" in
        --output) OUTPUT="$2"; shift 2 ;;
        --keep-daily) KEEP_DAILY="$2"; shift 2 ;;
        --keep-weekly) KEEP_WEEKLY="$2"; shift 2 ;;
        *) die "unknown argument $1" ;;
    esac
done
[[ -n "$OUTPUT" ]] || die "usage: backup.sh --output DIR"
require_key
mkdir -p "$OUTPUT"
[[ -w "$OUTPUT" ]] || die "output directory $OUTPUT is not writable"
[[ -n "${MEDIA_DIR:-}" || -n "${MEDIA_EXPORT_CMD:-}" ]] || die "set MEDIA_DIR or MEDIA_EXPORT_CMD"

WORK=$(mktemp -d)
cleanup() {
    rm -rf "$WORK"
    ${PSQL:-psql} -X -q -c "update system_state set maintenance = false, reason = null, updated_at = now() where id = 1" >/dev/null \
        || echo "warning: could not end maintenance mode; end it from Settings or with SQL" >&2
}
trap cleanup EXIT

${PSQL:-psql} -X -q -v ON_ERROR_STOP=1 -c "update system_state set maintenance = true, reason = 'backup', updated_at = now() where id = 1" >/dev/null
for _ in $(seq 1 60); do
    [[ "$(psql_value "select count(*) from background_jobs where status = 'RUNNING' and lease_until > now()")" == "0" ]] && break
    sleep 2
done
[[ "$(psql_value "select count(*) from background_jobs where status = 'RUNNING' and lease_until > now()")" == "0" ]] \
    || die "background jobs are still running; try again later"

mkdir -p "$WORK/media"
if [[ -n "${MEDIA_EXPORT_CMD:-}" ]]; then
    $MEDIA_EXPORT_CMD "$WORK/media"
    rm -rf "$WORK/media/.staging"
else
    [[ -d "$MEDIA_DIR" ]] || die "media directory $MEDIA_DIR not found"
    tar -C "$MEDIA_DIR" --exclude=.staging -cf - . | tar -C "$WORK/media" -xf -
fi
missing=0
while read -r key; do
    [[ -z "$key" ]] && continue
    [[ -f "$WORK/media/$key" ]] || { echo "missing media file: $key" >&2; missing=1; }
done < <(${PSQL:-psql} -X -A -t -v ON_ERROR_STOP=1 -c \
    "select storage_key from media_assets where status = 'AVAILABLE' union select storage_key from certificates where rendered union select storage_key from report_exports where status = 'READY'")
[[ "$missing" == 0 ]] || die "referenced media is missing; backup aborted"

${PG_DUMP:-pg_dump} -Fc > "$WORK/database.dump"
[[ -s "$WORK/database.dump" ]] || die "database dump is empty"
tar -C "$WORK" -cf "$WORK/media.tar" media
rm -rf "$WORK/media"
SCHEMA=$(psql_value "select max(version) from flyway_schema_history where success")
{
    echo "# learning-portal backup $(date -u +%Y-%m-%dT%H:%M:%SZ) schema=$SCHEMA"
    echo "$(sha256 "$WORK/database.dump") database.dump"
    echo "$(sha256 "$WORK/media.tar") media.tar"
} > "$WORK/manifest.txt"

NAME="learning-backup-$(date -u +%Y%m%dT%H%M%SZ)"
tar -C "$WORK" -cf "$WORK/$NAME.tar" manifest.txt database.dump media.tar
encrypt "$WORK/$NAME.tar" "$OUTPUT/$NAME.tar.enc"
sha256 "$OUTPUT/$NAME.tar.enc" > "$OUTPUT/$NAME.tar.enc.sha256"
"$(dirname "$0")/verify-backup.sh" "$OUTPUT/$NAME.tar.enc" || { rm -f "$OUTPUT/$NAME.tar.enc"*; die "verification failed"; }

# Retention: keep the newest KEEP_DAILY archives plus the newest archive of each of the last KEEP_WEEKLY weeks.
# The archive just written is always kept (it is the newest verified backup).
kept_weeks=""
week_count=0
index=0
for archive in $(ls -1 "$OUTPUT"/learning-backup-*.tar.enc 2>/dev/null | sort -r); do
    stamp=$(basename "$archive" | sed -E 's/learning-backup-([0-9]{8})T.*/\1/')
    week=$(date -u -d "$stamp" +%G-%V 2>/dev/null || date -u -j -f %Y%m%d "$stamp" +%G-%V)
    keep=0
    (( index < KEEP_DAILY )) && keep=1
    if [[ " $kept_weeks " != *" $week "* && $week_count -lt $KEEP_WEEKLY ]]; then
        kept_weeks="$kept_weeks $week"
        week_count=$((week_count + 1))
        keep=1
    fi
    [[ "$archive" == "$OUTPUT/$NAME.tar.enc" ]] && keep=1
    [[ "$keep" == 1 ]] || rm -f "$archive" "$archive.sha256"
    index=$((index + 1))
done
echo "$OUTPUT/$NAME.tar.enc"
