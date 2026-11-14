# Flutter / Spring Boot API mapping (based on V30.1 source)

This table names existing backend endpoints, not new backend functionality.

| Mobile view | Spring endpoint | Notes |
|---|---|---|
| Guest store companies | `GET /api/catalog/companies` | `permitAll` |
| Public inventory | `GET /api/companies/{companyId}/items` | `permitAll`; hides private stock/cost |
| Product image | `GET /api/companies/{companyId}/items/{itemId}/image` | Public, backend-generated path |
| Register / Login | `POST /api/auth/register`, `/api/auth/login` | Public types OWNER / CLIENT only |
| Companies I manage | `GET /api/companies` | Uses JWT; roles from membership DB |
| Customer cart | `GET /api/companies/{id}/cart` | CLIENT only |
| Cart update | `PUT /api/companies/{id}/cart/items/{itemId}` | Body `{ "quantity": 2 }` |
| Checkout | `POST /api/companies/{id}/checkout` | Expected total plus pickup/delivery fields |
| My orders | `GET /api/companies/{id}/orders/mine` | JWT |
| Manager orders | `GET /api/companies/{id}/orders` | Membership checked |
| Whish payment instructions | `GET /api/companies/{id}/orders/{orderId}/whish/instructions` | Optional configured wallet |
| Whish reference | `POST /api/companies/{id}/orders/{orderId}/whish/transfers` | Sender name/phone/reference |
| Customer repair tracking | `GET /api/repairs/tracking` | Only customer's own records |
| New repair request | `POST /api/companies/{id}/repairs` | Device, phone, issue |
| Staff repairs | `GET /api/companies/{id}/repairs` | Staff membership |
| Staff status | `POST /api/companies/{id}/repairs/{repairId}/status` | Validate transition server-side |
| Invoices | `GET /api/companies/{id}/invoices/mine` / `/invoices` | Client/staff access |
| Support availability | `GET /api/companies/{id}/support/availability` | Authenticated |
| Support one-use WS ticket | `POST /api/companies/{id}/support/ws-ticket` | JWT REST; ticket for handshake |
| Support WS | `GET /api/support/ws?ticket=...` | V26 `message`, `refresh`, `customer_presence`, `staff_availability` |
| Staff presence heartbeat | `POST /api/companies/{id}/support/presence/heartbeat` | `{ "sessionId": "UUID" }` |
| Staff presence exit | `DELETE /api/companies/{id}/support/presence/{sessionId}` | Also tied to bound WS on disconnect |
| Customer conversation | `GET /api/companies/{id}/support/mine` | Null if no conversation yet |
| Start chat | `POST /api/companies/{id}/support/conversations` | Existing thread reused/reopened |
| Staff chat inbox | `GET /api/companies/{id}/support/conversations` | Membership checks |
| Chat history | `GET /api/companies/{id}/support/conversations/{conversationId}/messages` | Latest 100 |
| Offline customer message | `POST /api/companies/{id}/support/offline-messages` | Staff offline fallback |
| Member roles | `GET/PATCH /api/companies/{id}/members...` | Owner/admin restrictions |
| Inventory | `GET /api/companies/{id}/inventory/items` | Staff only |
| Inventory product | `POST /api/companies/{id}/items`, `PUT /api/companies/{id}/items/{itemId}` | Server validates |
| Product image upload | `POST /api/companies/{id}/items/{itemId}/image` | Multipart `file` |
| Restock | `POST /api/companies/{id}/inventory/items/{itemId}/movements` | Audited stock transaction |
| Finance | `GET /api/companies/{id}/finance/overview` | Authorized staff |
| Admin users | `GET/PATCH /api/platform/users` | Super admin only |

If your VPS runs a version older than V26, staff-availability push and the
customer-presence events may not work. Deploy the V30.1 backend before testing.
