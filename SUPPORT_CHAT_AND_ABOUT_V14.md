# Wolf Tech Repair — About Us and Support Chat (V14)

Based on the user's uploaded `repairAndEcommerce-main.zip` (7 November 2026).

## Added

- `/about` — a public, mobile-friendly About Us page based on the platform features currently present. No invented address, opening hours, or contact details.
- `/support` — authenticated support chat. Client accounts open one conversation per shop; owners and managers respond from their selected company workspace. The shared desktop/mobile navigation includes both pages, and the sign-in and registration pages link to About Us.
- Company-scoped conversation and message tables, JPA entities/repositories, service/controller, and **Flyway V14**.
- 12-second polling while the support page is visible; no WebSocket, external chat service, email, or push notification is required.
- Server-side access checks; text-only messages; maximum 2,000 characters; last 100 messages displayed. A new message reopens a closed conversation.

## Patch installation on Windows

1. Commit or back up your working source. Do **not** overwrite your production `.env`.
2. Extract `repair_management_support_about_v14_patch.zip` into the repository root (where `pom.xml`, `frontend/`, and `src/` exist). Preserve the directory structure.
3. The patch includes a small correction to two existing Angular service decorators: `@Service()` → `@Injectable({providedIn: 'root'})`, since the uploaded snapshot uses an invalid Angular decorator.
4. Test the backend with Java 21 and Maven: `mvn clean test`.
5. Test Angular with Node 24.15+ (or an Angular-supported Node 22.22+): `cd frontend`, `npm ci`, `npm run build`.
6. Start the local app and test as a client and as an owner/manager in the **same company**.
7. Push your tested code to GitHub.

## VPS deployment

1. Back up MySQL before applying any migration. Verify there is no existing `V14` migration in production; never edit an already applied Flyway migration.
2. `cd /home/techApp && git pull --ff-only origin main`
3. `docker compose up -d --no-deps --build backend frontend`
4. `docker compose logs --tail=100 backend` to check Flyway V14, then `docker compose ps`.
5. Test About Us (logged out), client start/reply, manager inbox/reply, and another company's access isolation.

**Do not** run `docker compose down -v`; that deletes local volumes. Keep your database and JWT credentials unchanged.

## API reference (JWT required except About Us frontend page)

- `GET  /api/companies/{companyId}/support/mine` — client’s current thread, or null
- `POST /api/companies/{companyId}/support/conversations` — client starts/opens their thread
- `GET  /api/companies/{companyId}/support/conversations` — owner/manager inbox
- `GET  /api/companies/{companyId}/support/conversations/{conversationId}/messages` — authorized messages (last 100)
- `POST /api/companies/{companyId}/support/conversations/{conversationId}/messages` — `{ "body": "Hello" }`
- `PATCH /api/companies/{companyId}/support/conversations/{conversationId}/status` — owner/manager `{ "status": "CLOSED" }` or `"OPEN"`

## Security and limitations

- Conversations are private to the specific customer and that shop’s owner/manager, checked **server-side**, not just in the Angular interface. Employees cannot access the inbox.
- Support is **not** a WhatsApp integration, automated chatbot, or instant WebSocket chat. Messages refresh within roughly 12 seconds while the page is open. No outgoing email or notifications are implemented.
- Use HTTPS in production. Before wide public launch, add per-account/IP rate limiting, retention and deletion policies, and optional notifications, moderation, and audit reporting.
- The About Us text is deliberately generic; provide verified company details (address, contact email, working hours, policy) before publishing customized claims.

## Validation status

- New and existing Angular TypeScript files passed a **syntax-only** parse using TypeScript; no full Angular template compilation was possible here.
- The backend service has JUnit/Mockito permission tests, but Maven and database integration tests were not executable here.
- The current environment's Node runtime is too old for your Angular CLI and its offline NPM cache is incomplete. **You must run a real Angular build and backend tests** before deploying.
