# Repair request — client contact details upgrade

This builds on `repair_management_repair_frontend_complete.zip` and keeps the existing
Angular session, image upload feature, company workspace separation, and repair workflows.

## What changed

Each new repair request now stores two **required** contact fields:

- `clientName`: name of the person to contact (max 150 characters)
- `clientPhone`: telephone number (6 to 40 characters)

The values are **snapshots on the repair**, independent from the login user's profile.
The account which created the repair remains linked by `customer_id`, so authenticated
customers can still track only the repairs they submitted.

Angular (`/repairs`) pre-fills the fields from the signed-in account when possible.
They may be edited before submitting the repair, e.g. for a different contact person.
The customer detail view and staff's Repair Workspace display these values. Company
staff can search by client name or phone, and the staff view offers a clickable phone link.

The MySQL `repair_requests` table gets `client_name` and `client_phone` via Flyway V4.
The migration backfills historical repairs from registered customer profiles where available.
Legacy records without a saved phone remain valid; new requests require both fields.

## How to install

1. Back up your MySQL database and existing project.
2. Extract the **patch ZIP** into the current application project root (where `pom.xml` and
   `frontend/` are located), preserving the relative paths.
3. Restart the Spring Boot server (`http://localhost:8081`). Flyway should apply
   `V4__repair_client_contact.sql` automatically. Do **not** manually run V4 as well.
4. Start Angular (`http://localhost:4200`) and sign in.
5. Open My Repairs, select a public company and create a request with client name and phone.
6. Open Repair Workspace as a company member and confirm both fields are visible.

Backend:

```bash
mvn test
mvn spring-boot:run
```

Frontend, in a separate terminal:

```bash
cd frontend
npm install
npm run build
npm start
```

## Verify the migration

```sql
USE repair_management;
SHOW COLUMNS FROM repair_requests LIKE 'client_%';
SELECT id, customer_id, client_name, client_phone
FROM repair_requests ORDER BY id DESC LIMIT 10;
```

JWT-authenticated API example: see `http/repair-client-contact.http`.

## Data protection and access

- Contact data is returned only in repair endpoints already restricted to the creator
  or company staff. It is not included in the public product/shop catalog.
- The contact name and phone are not authentication credentials or proof of identity.
- These are personal data: avoid printing them in server logs, restrict database access,
  and define a retention/deletion policy for your deployment.
- This change does not yet support repairs for walk-in customers **without** a logged-in
  account. That is a separate workflow to add later if required.

## Verification limits

The updated source has been checked for syntactic problems where tooling was available.
A complete Maven build, browser test, and live MySQL migration remain to be run in your environment.
