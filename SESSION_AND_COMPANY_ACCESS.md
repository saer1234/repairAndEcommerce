# Session persistence and company access update

## Behavior

1. The Angular app stores a **short-lived JWT** and public profile in `sessionStorage`, validates expiry at startup, and restores auth state after refreshing the same tab. A 401 on an authenticated API call triggers logout. No automatic token refresh exists.
2. **Browse customer store** is the public-company selector. It lists all ACTIVE companies and lets a logged-in user browse another company's public product catalog.
3. **My workspace** is the private-company selector. It contains only the signed-in user's company memberships. The existing backend checks OWNER/MANAGER roles for modifying inventory, finance, and company membership. Employees access only the company repair queue and authorized stock data.
4. Company browsing never changes management company, and changing a managed company never restricts the public store directory.

## Manual verification

- Sign in as the owner of company A and refresh at `/dashboard`: the signed-in state remains.
- Browse company B in **Browse customer store**, navigate to Customer Store: see B's public items.
- Open **Manage My Store**: edit inventory ONLY of A (not B). In Network tab verify the URL points to `/api/companies/A_ID/...`.
- Go to **Companies & teams**: public directory includes A and B, while the private manager selector includes only A (unless user is a member of B).
- As company A manager, attempt `POST /api/companies/B_ID/items` using the token: API must return forbidden/not found.
- Sign out and refresh: login is required.
- Set a previously saved session expiry into the past and refresh: login is required.
- Log in as different user after logout: previous user's company memberships are absent.

## Security notes

This change deliberately avoids letting OWNER/MANAGER membership in company A grant modification or visibility rights in B. Public company directory and product catalog are different from protected internal company data. JavaScript-readable token storage is suitable only for a development prototype with careful XSS defenses. For production, move to an HttpOnly Secure SameSite refresh-cookie design with an in-memory access token and server-side refresh rotation/revocation.
