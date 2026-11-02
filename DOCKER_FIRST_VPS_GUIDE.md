# Repair Management — Docker-first VPS deployment (October 2026)

This package builds **all** application code inside Docker. You need Docker Engine
and **Docker Compose v2** on your Ubuntu VPS, **not** host Node.js, Maven, or Java.

## What runs

- `db`: MySQL 8.4, internal-only network, named persistent volume.
- `backend`: Spring Boot compiled/tested in `maven:3.9-eclipse-temurin-21`,
  running as non-root with `eclipse-temurin:21-jre-jammy`, internal port 8081.
- `frontend`: Angular 22 built in `node:24.15.0-bookworm-slim`, then served
  by Caddy with `/api/*` proxying to backend. Caddy handles TLS for a domain.

**Important:** This includes updated tests from the previous VPS fixes:
RegistrationTypeValidationTest, OrderServiceTest, WhishTransferServiceTest,
and DeviceCompatibilityTest. All 106 tests need to pass during the backend image build.

## 1. Extract files on VPS

Upload `repair_management_docker_first_complete.zip`, extract to `/home/techApp`.
If you already edited your application on VPS, prefer applying the smaller
deployment patch and the individual test patches. Back up your existing code first.

```bash
cd /home/techApp
cp .env.example .env
openssl rand -base64 48
nano .env
chmod 600 .env
```

Set unique MYSQL_PASSWORD, MYSQL_ROOT_PASSWORD, JWT_SECRET_BASE64.

For INITIAL tests by IP you may keep `SITE_ADDRESS=:80` and browse
`http://YOUR_VPS_IP` (the public IP is not automatically encrypted).
**Never accept real customer data, credentials, or payments on plain HTTP.**
For real deployment set `SITE_ADDRESS=repair.yourdomain.com`, point DNS to VPS,
keep port 80/443 accessible, and browse `https://repair.yourdomain.com`.
`CORS_ALLOWED_ORIGINS` is not needed for same-origin SPA API calls but can be set
to your browser origin. Java/backend and MySQL are not published on host ports.

## 2. Verify Docker and check port conflicts

```bash
docker --version
docker compose version
docker ps --format 'table {{.Names}}\t{{.Ports}}'
ss -ltnp | grep -E ':(80|443) ' || true
```

If ports 80/443 are used by your existing service, do **not** stop it blindly.
Use a dedicated hostname with an existing reverse proxy or adjust this Caddy
routing and host port mapping; direct public ACME challenges normally require
standard TCP 80/443.

## 3. Deploy — one command

```bash
cd /home/techApp
./scripts/deploy.sh
```

The script validates the config, builds and runs the Java 21 tests, builds Angular
with Node 24, then starts the containers. It builds sequentially to save RAM.
With ~2 GB memory, building both applications may exhaust RAM; 4 GB+ or
build on a stronger machine/CI and push images to a registry is recommended.

Or execute manually:

```bash
docker compose build backend
docker compose build frontend
docker compose up -d --no-build
docker compose ps
```

## 4. Diagnose

```bash
docker compose logs -f --tail=100 backend
docker compose logs -f --tail=100 frontend
docker compose logs -f --tail=100 db
```

If backend Java tests fail, Docker **intentionally** blocks deployment.
Inspect the first failing test and fix the code; do not use -DskipTests to conceal
defects. Your preceding test log reported 105/106 passing, and the included
DeviceCompatibilityTest patch corrects the last expected category mismatch.

## 5. Persistence and upgrades

- Persistent volumes: `mysql_data`, `item_images`, `caddy_data`, `caddy_config`.
- Flyway applies V1-V12 when a brand-new database is created.
- Do **not** run `docker compose down -v`: `-v` deletes all named volumes,
  including the database and uploaded images.
- To deploy future code updates: `./scripts/backup.sh`, then
  `./scripts/deploy.sh`.
- Keep an off-site encrypted backup of data and images; periodically test restores.
- Whish transfers are **manually verified**; no automatic Visa gateway exists.

## 6. Production checklist

- HTTPS on real domain; do not use the IP/HTTP testing mode for real users.
- Ensure host firewall allows SSH and TCP 80/443; do not expose 8081 or 3306.
- Configure backups, login throttling, monitoring, safe JWT secret and
  shop-owner verification before accepting real customers.
- Check Web UI: register owner/client, product images, order checkout,
  repair invoice, Whish sender name/phone, manual verification, manager reports.

## Applying only the patch to your CURRENT project

Use this if you have already edited `/home/techApp` or applied previous fixes:

```bash
cd /home/techApp
cp -a src/test src/test.backup
unzip -o repair_management_docker_first_patch.zip -d /home/techApp
./scripts/init-env.sh
# If you have an old .env, make sure SITE_ADDRESS exists and secrets are filled.
./scripts/deploy.sh
```

Only Docker deployment files, Angular `environment.ts`, and the four corrected
test classes are included in the patch. Other source files are left unchanged.
The complete package is a snapshot of V12 and should not overwrite newer work.
