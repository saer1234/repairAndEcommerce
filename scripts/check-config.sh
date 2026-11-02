#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f .env ]]; then echo 'No .env. Run cp .env.example .env and set passwords.' >&2; exit 1; fi
if grep -Eq 'CHANGE_ME' .env; then echo 'Please replace all CHANGE_ME secrets in .env.' >&2; exit 1; fi
if ! command -v docker >/dev/null || ! docker compose version >/dev/null 2>&1; then
    echo 'Docker Engine + docker compose v2 required on VPS. No host Java/Node/Maven needed.' >&2;exit 1
fi
if ! docker info >/dev/null 2>&1; then
    echo 'Docker daemon unavailable or this user lacks Docker access.' >&2;exit 1
fi
docker compose config --quiet
echo 'Docker configuration valid. Images and application tests still need to build.'
