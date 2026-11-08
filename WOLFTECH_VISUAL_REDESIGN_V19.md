# Wolf Tech Visual Redesign — V19 (frontend only)

This update implements the visual direction of the supplied Wolf Tech Repair image in your Angular app. No Java, MySQL schema, payment logic, or Docker configuration is changed.

## What's included

- A new public homepage at `/`, using the provided banner `frontend/public/assets/wolftech-showcase.jpg` exactly as supplied.
- A responsive electric-blue and midnight-navy Wolf Tech landing page, with repair, accessory, About Us, login, and support navigation.
- New matching brand styles for the app sidebar, mobile header, sign-in/registration and public dynamic About Us page.
- A matching banner at the top of the customer store, without changing cart or checkout logic.
- Homepage links keep login requirements, company permissions, and existing client/shop authorization intact.
- Corrects the existing mobile keyboard-focus listener's inverted type guard.

**Routing change:** `/` now opens the public branded homepage. Existing authenticated users still reach their workspace at `/dashboard`. The login page stays at `/login`; all existing application routes remain.

## Install locally from the PATCH archive

This patch is based on the V18 complete package from this conversation, which might differ from your latest GitHub working tree. Review changes before overwriting files.

Windows Git Bash:

```bash
cd /d/techApp/repairAndEcommerce
# Recommended: save current work first; do not overwrite unsaved edits
git switch -c feature/wolftech-visual-redesign
unzip -o /path/to/wolftech_visual_redesign_v19_patch.zip
cd frontend
npm ci
npm run build
```

Check on desktop and mobile:

- `/` — hero/banner and service links
- `/login`, `/register` — legible forms on branded background
- `/about` — per-company dynamic information retained
- `/shop` — store banner only for eligible logged-in clients
- `/dashboard` — workspace, company selector, logout, and navigation unchanged functionally

If your local machine cannot use Node 24.15+ or a supported Angular CLI runtime, build in your existing Docker setup on your VPS after code review.

Push only after testing:

```bash
cd /d/techApp/repairAndEcommerce
git add frontend/src frontend/public/assets/wolftech-showcase.jpg WOLFTECH_VISUAL_REDESIGN_V19.md
git commit -m "Apply Wolf Tech branded website design"
git push -u origin feature/wolftech-visual-redesign
```

Merge the branch to `main` after review. Then deploy on VPS:

```bash
cd /home/techApp
git pull --ff-only origin main
docker compose up -d --no-deps --build frontend
docker compose logs --tail=60 frontend
```

The backend and MySQL remain unchanged. Do not use `docker compose down -v`.

## Asset and performance

The source banner is approximately 167 KB and 1600 × 700 px. It is embedded as a normal local image, served from `frontend/public/assets/` without external network requests. Image text is part of the artwork. On small screens the entire image remains visible instead of cropping out the wolf logo.

## Verification note

Changed TypeScript files were checked with the TypeScript syntax parser; CSS stylesheets passed a CSS parser check. Full Angular compilation, automated browser testing, and live VPS deployment were not performed here.
