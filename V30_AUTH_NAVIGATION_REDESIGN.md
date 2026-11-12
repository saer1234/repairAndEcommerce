# V30 — Login / registration footer redesign

## Changes
- Replaces the old inline account links with a full-width account action inside a pale-blue panel.
- Replaces About Wolf Tech Repair and Back to home links with equally spaced, touch-friendly navigation tiles.
- Reuses existing English/Arabic keys, so no translation service changes were needed.
- Supports right-to-left navigation in Arabic; uses inline SVG icons, focus-visible outlines and reduced-motion support.
- Preserves the original routes `/login`, `/register`, `/about` and `/` (store-first).

## Modified files
- `frontend/src/app/pages/auth/login.page/login.page.html`
- `frontend/src/app/pages/auth/login.page/login.page.css`
- `frontend/src/app/pages/auth/register.page/register.page.html`
- `frontend/src/app/pages/auth/register.page/register.page.css`

## Validation
- Checked navigation targets, SVG element balance, Angular interpolation balance and ZIP CRC.
- Full Angular compilation was not executed. Run `npm ci && npm run build` in `frontend`.
