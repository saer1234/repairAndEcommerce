# V22 — Company roles and support access revocation

## Implemented
- Company OWNER can update another member's company role, remove them, and restore an inactive membership using protected REST endpoints.
- SUPER_ADMIN can change ordinary users' platform roles (USER / OWNER / CLIENT), but cannot grant or modify SUPER_ADMIN or self-edit.
- Company membership modifications broadcast an AFTER_COMMIT event; WebSocket tickets and connected sessions are revoked and the removed staff member's presence cleared.
- Support REST endpoints already recheck current live membership, and existing WebSocket access checks continue to enforce it. Added connection-time verification and proactive disconnect.
- Angular team and platform administration screens now have working role selectors and member removal.

## New REST endpoints
- PATCH /api/companies/{companyId}/members/{userId}/role `{ "role": "MANAGER" }`
- DELETE /api/companies/{companyId}/members/{userId}
- POST /api/companies/{companyId}/members/{userId}/restore `{ "role": "EMPLOYEE" }`
- PATCH /api/platform/users/{userId}/role `{ "role": "OWNER" }`

## Schema
No Flyway migration required: `company_members.active` and role columns already exist in V1.

## Security and deployment
- A current company OWNER is needed for company role changes. The last active owner is protected. Owners cannot remove/demote themselves.
- Only platform SUPER_ADMIN can edit platform roles, and this endpoint cannot create/change a SUPER_ADMIN account.
- JWT authentication loads live platform roles from the database; chat REST and socket requests revalidate tenant membership.
- Reconnection must obtain a fresh one-use WebSocket ticket. Membership changes revoke pending tickets and active sockets after transaction commit.
- On single-JVM deployments revocation is immediate. Multi-instance deployments need shared ticket/session invalidation or a distributed event bus; existing socket hub is JVM-local.
- The restore API is available but the Angular UI currently lists active members only. Restoration may be done by the company owner via API.
- Existing historical conversation records are retained after removing staff.
- Verify Spring Boot and Angular builds and carry out the security test cases before production deployment.
