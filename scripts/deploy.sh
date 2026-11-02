#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./scripts/check-config.sh
# Build one image at a time to reduce VPS peak memory use.
echo 'Building and unit-testing Java 21 backend inside Docker...'
docker compose build backend
echo 'Building Angular 22 with Node 24.15.0 inside Docker...'
docker compose build frontend
echo 'Starting MySQL, Spring Boot and Caddy...'
docker compose up -d --no-build
docker compose ps
echo 'If any container is restarting, check: docker compose logs --tail=120 backend'
