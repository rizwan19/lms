#!/usr/bin/env bash
# Shared helpers for backup.sh, verify-backup.sh and restore.sh. Sourced, not executed.
#
# Database commands are configurable so the same scripts work on a VPS (local client tools) and with Docker:
#   PSQL       default: psql            e.g. "docker compose -f deploy/compose.yml exec -T postgres psql -U learning -d learning"
#   PG_DUMP    default: pg_dump         e.g. "docker compose -f deploy/compose.yml exec -T postgres pg_dump -U learning -d learning"
#   PG_RESTORE default: pg_restore
# Connection details for local tools come from the standard PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE variables.

set -euo pipefail

die() {
    echo "error: $*" >&2
    exit 1
}

require_key() {
    [[ -n "${LEARNING_BACKUP_KEY_FILE:-}" ]] || die "LEARNING_BACKUP_KEY_FILE is not set"
    [[ -s "$LEARNING_BACKUP_KEY_FILE" ]] || die "backup key file $LEARNING_BACKUP_KEY_FILE is missing or empty"
}

sha256() {
    if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

psql_value() {
    ${PSQL:-psql} -X -A -t -v ON_ERROR_STOP=1 -c "$1" | tr -d '[:space:]'
}

encrypt() {
    openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -in "$1" -out "$2" -pass "file:$LEARNING_BACKUP_KEY_FILE"
}

decrypt() {
    openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in "$1" -out "$2" -pass "file:$LEARNING_BACKUP_KEY_FILE" 2>/dev/null \
        || die "cannot decrypt $1 (wrong key or corrupt archive)"
}

# Decrypts and unpacks ARCHIVE into DIR after checking the archive checksum and every manifest entry.
unpack_verified() {
    local archive="$1" dir="$2"
    [[ -f "$archive" ]] || die "archive $archive not found"
    [[ -f "$archive.sha256" ]] || die "checksum file $archive.sha256 not found"
    [[ "$(sha256 "$archive")" == "$(cat "$archive.sha256")" ]] || die "archive checksum mismatch: $archive"
    decrypt "$archive" "$dir/archive.tar"
    tar -xf "$dir/archive.tar" -C "$dir" || die "archive is not a valid tar file"
    rm "$dir/archive.tar"
    [[ -f "$dir/manifest.txt" ]] || die "manifest missing"
    while read -r expected file; do
        [[ "$expected" == "#"* || -z "$expected" ]] && continue
        [[ -f "$dir/$file" ]] || die "manifest entry missing: $file"
        [[ "$(sha256 "$dir/$file")" == "$expected" ]] || die "checksum mismatch: $file"
    done < "$dir/manifest.txt"
}
