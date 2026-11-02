# Product photos: Item Image Upload (Stage 7 extension)

This extension builds on **Session & Company Access** and **Add Items to Store**.

## Overview

- Each item supports **one photo** (JPEG, PNG or WebP; at most **5 MB**).
- The **image filename and MIME type** are stored on the `items` row in MySQL.
- The **image bytes live in the configured file directory**, not in MySQL.
- Public stores display a photo or a placeholder; customer-facing item JSON contains only `imageUrl`, **never acquisition cost**.
- Only `OWNER` or `MANAGER` of the item's **own company** can upload, replace, or delete images. Server-side checks use JWT identity and company membership.
- Images are generated with random UUID filenames; file paths are never accepted from the client.

## Apply to an existing project

Unzip the additions-only ZIP over the root of your **existing Stage 7 project** (the folder containing `pom.xml`). Back up the code/database first.

**Database:** Start Spring Boot normally. Flyway will apply:

`src/main/resources/db/migration/V3__item_images.sql`

This adds nullable `image_filename` and `image_content_type` fields to the existing `items` table. Existing products remain valid and appear without photos.

**Storage:** Add an environment variable, or use the default under the backend working directory:

```properties
app.images.storage-dir=${ITEM_IMAGES_DIR:./data/item-images}
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=6MB
```

Use a persistent volume on your VPS, e.g. `ITEM_IMAGES_DIR=/srv/repair-management/item-images`. Ensure the Spring Boot OS user can write to it. **Back up this folder as well as MySQL**. Avoid storing images under `src/main/resources`, `target`, or a container's ephemeral filesystem.

**Angular:** Install dependencies if necessary, then start the frontend:

```bash
mvn test
mvn spring-boot:run
# in a second terminal:
cd frontend
npm install
npm start
```

### Use from Angular

1. Log in as a company `OWNER` or `MANAGER`.
2. Select **My workspace** for that company.
3. Open **Store inventory**, fill the item form, choose a photo and click **Add item to store**.
4. For an existing item, click **Edit**, choose a replacement image, and click **Save changes**.
5. **Remove current photo** removes the image without changing item details.
6. Visit **Customer Store** to view images in product cards.

The item form creates/updates the item first, then uploads the photo through a **separate multipart request**. If photo upload fails, the item remains saved and can be edited to retry.

### REST API

| Operation | Method | URL | Access |
|---|---|---|---|
| Upload / replace | `POST` | `/api/companies/{companyId}/items/{itemId}/image` | owner/manager of this company |
| Fetch | `GET` | `/api/companies/{companyId}/items/{itemId}/image` | public for active companies and items |
| Delete photo | `DELETE` | `/api/companies/{companyId}/items/{itemId}/image` | owner/manager of this company |

Upload field **must be named `file`** and the request must use `multipart/form-data`. Don't manually set `Content-Type` for Angular FormData; Angular/browser supplies the boundary.

Example (replace `TOKEN`, company, item, and filename):

```bash
curl -X POST 'http://localhost:8081/api/companies/1/items/1/image' \
  -H 'Authorization: Bearer TOKEN' \
  -F 'file=@/home/me/product.jpg'

curl 'http://localhost:8081/api/companies/1/items'
# returns item details including imageUrl, e.g.
# "imageUrl": "/api/companies/1/items/1/image"
```

### Important security details

- No SVG or arbitrary file extension uploads; incoming MIME/filename are not trusted.
- The server checks file signatures and provides `X-Content-Type-Options: nosniff`.
- Public image retrieval checks the item's company and active status, and never uses URL-provided file paths.
- `Cache-Control: no-store` ensures replacements become visible immediately.
- Photo files are deleted after DB commit on replace/remove; new files are deleted on transaction rollback.
- This implementation is suitable for one backend instance with a **persistent filesystem**. For multiple instances, switch file storage to a shared object store (e.g. S3-compatible storage), with access rules preserved.

## Verification

Run `mvn test`, run `npm run build`, check Flyway history (V3), and manually test upload, view, replace, delete, forbidden company changes, invalid file types, file size, and restarting Spring Boot to check persistence.
