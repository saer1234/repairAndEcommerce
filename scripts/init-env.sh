#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -e .env ]]; then
  echo 'Existing .env found; leaving it unchanged.'
  if ! grep -q '^SITE_ADDRESS=' .env; then
    echo 'NOTE: Add SITE_ADDRESS=:80 (testing) or SITE_ADDRESS=your.domain.com (HTTPS) to existing .env.'
  fi
  exit 0
fi
if ! command -v openssl >/dev/null; then
  echo 'Install openssl first (sudo apt-get install -y openssl)' >&2
  exit 1
fi
umask 077
app_pwd="$(openssl rand -hex 32)"
root_pwd="$(openssl rand -hex 32)"
jwt_secret="$(openssl rand -base64 48 | tr -d '\n')"
cat > .env <<EOF
# Use :80 ONLY for initial testing by IP (not for real customer data).
# For production change to your hostname and use HTTPS.
SITE_ADDRESS=:80
HTTP_PORT=80
HTTPS_PORT=443
MYSQL_DATABASE=repair_management
MYSQL_USER=repair_app
MYSQL_PASSWORD=${app_pwd}
MYSQL_ROOT_PASSWORD=${root_pwd}
JWT_SECRET_BASE64=${jwt_secret}
CORS_ALLOWED_ORIGINS=http://localhost:4200
EOF
chmod 600 .env
echo 'Created .env with random passwords and a new JWT signing secret.'
echo 'Initial SITE_ADDRESS=:80 is HTTP-only: use an HTTPS domain before real accounts/payments.'
