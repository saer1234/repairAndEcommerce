#!/usr/bin/env bash
set -euo pipefail
# Docker's cumulative network I/O since container startup (not a daily accounting report).
# Use on the VPS; no elevated container privileges are needed.
docker stats --no-stream --format 'table {{.Name}}\t{{.NetIO}}\t{{.CPUPerc}}\t{{.MemUsage}}'
