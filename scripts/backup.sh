#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
umask 077
mkdir -p backups
stamp=$(date -u +%Y%m%dT%H%M%SZ)
# MySQL environment variables are read inside the DB container.
docker compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --single-transaction --routines --triggers --events -u "$MYSQL_USER" "$MYSQL_DATABASE"' | gzip > "backups/mysql-$stamp.sql.gz"
# Save user-uploaded photos from their persistent Docker volume.
backend_id=$(docker compose ps -q backend)
if [ -z "$backend_id" ]; then echo "Backend is not running; could not back up uploaded images" >&2; exit 1; fi
docker run --rm --volumes-from "$backend_id" -v "$PWD/backups:/backup" alpine:3.21 \
  tar -czf "/backup/item-images-$stamp.tar.gz" -C /data item-images
printf 'Created:\n  backups/mysql-%s.sql.gz\n  backups/item-images-%s.tar.gz\n' "$stamp" "$stamp"
