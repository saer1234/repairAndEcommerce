# Registered client repair flow (employee and manager)

This patch builds on **repair_management_client_picker_inventory_complete.zip** and retains inventory search and all earlier modules.

## Permissions and client identity

- A logged-in company **Employee**, **Manager**, or **Owner** may register a repair in their **private company workspace**.
- They must search for an **active, registered Client account** and select it from the results. Searching shows both the account name and email for identification.
- Only `platformRole=CLIENT` accounts with **no active company membership** appear in search. Neither an unregistered customer nor a manually typed name is accepted.
- The API accepts `clientId`, **not** `clientName`. The backend looks up the client, verifies eligibility again, associates the repair with that account (`customer_id`), and snapshots the account's full name (`client_name`). The contact phone for that repair may be edited.
- The Client account does **not** need to be online at the same time; it only needs to exist and be active.
- A client may submit their own repair using their login. The self-service `CreateRepairRequest` no longer contains an unused `clientName` property.
- The associated client sees the repair in **Track My Repairs** after signing in.

## Staff APIs

Authenticated with JWT as a company member (employee/manager/owner):

```http
GET http://localhost:8081/api/companies/1/repairs/eligible-clients?q=ali
Authorization: Bearer <staff_jwt>
```

Sample response:

```json
[{"id":15,"fullName":"Alice Example","email":"alice@example.com","phone":"+96170123456"}]
```

Create request for a selected client (no arbitrary name):

```http
POST http://localhost:8081/api/companies/1/repairs/for-client
Authorization: Bearer <staff_jwt>
Content-Type: application/json
```

```json
{
  "clientId": 15,
  "clientPhone": "+96170123456",
  "deviceType": "Phone",
  "brand": "Samsung",
  "model": "S24",
  "issueDescription": "Cracked screen"
}
```

The server stores `customer_id=15` and `client_name` from the database. An owner, employee, manager, inactive user, or client with an active staff membership is rejected as the selected client.

## How to install and test

1. Back up your application, then extract the patch ZIP into the project folder and overwrite the matching files. **No schema migration is required.**
2. Start Spring Boot on 8081 with `mvn test` then `mvn spring-boot:run`.
3. Start Angular: `cd frontend && npm start`.
4. Log in as an employee or manager of Company A and open **Repair Workspace**.
5. Search by part of a client's name/email, select their verified account, then enter device information and submit.
6. Verify `customer_id` and `client_name` in `repair_requests`; then sign in as that client and open **Track My Repairs**.
7. Verify arbitrary names cannot be entered on the staff form and that company staff accounts do not appear as client options.

The API continues to enforce permissions independently of the Angular UI. Full Maven and live MySQL execution must be verified in your own environment.
