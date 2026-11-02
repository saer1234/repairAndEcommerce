# RepairFlow V13 — Responsive Navigation Redesign

This release redesigns the Angular application shell for desktop, tablet, and mobile.

## What changed

- Desktop: modern dark sidebar with grouped, role-aware navigation, active-route highlight, account summary, and a visible Log out button.
- Mobile: sticky white top bar with an always-visible Log out button and a large menu button.
- Mobile menu: scrollable slide-out drawer with a backdrop, close control, account card, and second Log out button.
- Accessibility: readable text labels, 43–46 px touch targets, visible keyboard-focus outlines, close on Escape, keyboard-focus trapping inside the drawer, and close on navigation.
- Workspace selectors (private managed company and public customer store) remain functional and wrap on narrow screens.
- Sidebar and mobile drawer use one shared navigation template to keep permission-based links synchronized.
- `AuthService.logout()` remains the original action, so it clears the current session and returns to login.

The frontend changes do **not** change backend authorization. Spring Boot remains responsible for permissions.

## Changed files

```text
frontend/src/app/shell.page.ts
frontend/src/styles.css
```

## Patch installation on an existing Docker VPS

From your local PC, upload `repairflow_nav_redesign_v13_patch.zip` to `/home/techApp` via SFTP.
On the VPS:

```bash
cd /home/techApp
cp frontend/src/app/shell.page.ts frontend/src/app/shell.page.ts.bak
cp frontend/src/styles.css frontend/src/styles.css.bak
unzip -o repairflow_nav_redesign_v13_patch.zip -d /home/techApp

docker compose build frontend
docker compose up -d --no-deps frontend
docker compose ps
docker compose logs --tail=60 frontend
```

After deployment, hard-refresh the website (`Ctrl+Shift+R` or a private browser tab), then test on a phone and desktop browser.

You do **not** need to rebuild the backend or restart MySQL for this update.
Avoid `docker compose down -v` because that removes persistent volumes.

## Important deployment notes

- The frontend files belong in the *host project directory*, not inside the running Docker container.
- Docker rebuilds the Angular app using the Node version in `deploy/Dockerfile.frontend` and Caddy serves the built website.
- The mobile header has its own Log out action; the drawer also includes Log out at the bottom.
- The generated static visual previews were checked at 320, 390, 800, 1024, and 1440 px without document-level horizontal overflow.
- The TypeScript source passed syntax transpilation, and the CSS parsed successfully.
- Full Angular compilation and real-device testing were not possible in the authoring environment because npm packages were unavailable. `docker compose build frontend` is the production compilation check on your VPS.
