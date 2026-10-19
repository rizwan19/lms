#!/usr/bin/env bash
# Verifies an encrypted backup: archive checksum, decryption with the key, every manifest checksum, and that the
# database dump is readable by pg_restore (when available).
#
#   LEARNING_BACKUP_KEY_FILE=/root/backup.key scripts/verify-backup.sh ARCHIVE
source "$(dirname "$0")/backup-common.sh"

[[ $# -eq 1 ]] || die "usage: verify-backup.sh ARCHIVE"
require_key
DIR=$(mktemp -d)
trap 'rm -rf "$DIR"' EXIT
unpack_verified "$1" "$DIR"
if [[ -n "${PG_RESTORE:-}" ]] || command -v pg_restore >/dev/null; then
    ${PG_RESTORE:-pg_restore} --list < "$DIR/database.dump" >/dev/null || die "database dump is not readable"
fi
tar -tf "$DIR/media.tar" >/dev/null || die "media snapshot is not readable"
echo "verified $1"
