# V27 — Public storefront with sign-in-required purchasing

Built from the provided V26 full-project ZIP; no V22–V26 chat/role code was removed.

## Behavior

- `/shop` is publicly accessible without signing in.
- Visitors can select an active store and browse published products, images, prices, descriptions, search, pagination, and verified-fit filters.
- The landing page's Browse accessories, Explore store, and View products links now lead directly to `/shop`.
- `Add to cart` becomes `Sign in to buy` for visitors and routes to `/login?returnUrl=/shop?companyId=N` (the URL is appropriately encoded by Angular).
- A client signing in with that return URL comes back to the same store.
- Anonymous visitors never request their own cart or attempt checkout; the checkout UI is hidden until they have an eligible client session.
- Non-customer accounts may browse products but cannot purchase.
- Public GET catalog + delivery settings APIs are allowed by Spring Security. Cart mutation and checkout remain protected by `ROLE_CLIENT` **and** existing service checks.
- Guest navigation shows Sign in and Create account rather than private workspace actions.

## Files modified

- `frontend/src/app/routes.ts`
- `frontend/src/app/core/services/company-context.ts`
- `frontend/src/app/pages/shell.page/shell.page.ts`
- `frontend/src/app/pages/shell.page/shell.page.html`
- `frontend/src/app/pages/shop.page/shop.page.ts`
- `frontend/src/app/pages/shop.page/shop.page.html`
- `frontend/src/app/pages/home.page/home.page.html`
- `frontend/src/app/pages/auth/login.page/login.page.ts`
- `src/main/java/com/repairmanagement/repairproject/security/SecurityConfig.java`

## Verification checklist

1. In an incognito window, open `/shop`, select a company, and confirm products/prices/images load without a token.
2. Try Search, brand/device filters, verified-fit filters, product details, and pagination as a guest.
3. Click `Sign in to buy` on a product. Verify `/login` opens and `returnUrl` retains the company ID.
4. Log in as a client; verify the selected storefront loads and checkout/cart still work.
5. Send an unauthenticated POST to `/api/companies/{id}/checkout` and a PUT to `/api/companies/{id}/cart/items/{itemId}`; both must fail (401/403).
6. Log in as an owner/manager/employee; verify store browsing is possible but the purchase actions are disabled.
7. Confirm `/dashboard`, `/orders`, `/inventory`, `/companies`, `/support` still require authentication.
8. Validate mobile and desktop navigation.

### Build status

The ZIP contents and patch scope were checked. Maven and Angular compilation were **not** performed because the relevant toolchain dependencies are unavailable in this environment. Validate with `mvn test` and `cd frontend && npm ci && npm run build` before deploying.
