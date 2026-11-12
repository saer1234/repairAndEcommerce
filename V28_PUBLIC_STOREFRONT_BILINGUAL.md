# V28 — Store-first and bilingual storefront

- `/` now redirects to `/shop`. The existing `/dashboard` workspace stays available for signed-in users.
- Store remains public: browsing product listings requires no account; cart and checkout continue to use existing backend access control.
- Redesigned storefront hero, product grid, visual hierarchy and responsive layout. Uses existing local showcase asset; no external image dependencies.
- Added English/Arabic toggle in the shared header and a lightweight store translation service with persistent language preference. Arabic enables RTL layout within the shop. Product names/descriptions remain as supplied by sellers and are not machine-translated.
- Existing company selection, filtering, pricing, compatibility, checkout and sign-in returnUrl behavior retained.

## Verification steps
1. Load `/` in a signed-out browser and confirm `/shop` opens.
2. Browse products, switch company and filter without signing in.
3. Switch to Arabic, refresh, verify remembered language and RTL shop layout.
4. Sign in from a product and verify return to company shop.
5. Test customer cart/checkout, including delivery confirmation and ordering.
6. Test staff dashboard/management routes; no additional public API is granted.

## Note
Translation covers store chrome and checkout labels. Seller-entered item text and other management pages are not translated.
