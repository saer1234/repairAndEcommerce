# Fix: item image selection unexpectedly resets the Angular page

## Root cause

In `frontend/src/app/inventory.page.ts`, the constructor's Angular `effect()`
called `clearForm()`. That indirectly read the `previewUrl` signal, so the effect
became dependent on changes to the chosen image. When the file-input handler
updated the preview, it triggered the effect again. The effect cleared the form
and reloaded items. This looked like a page refresh even when there was no
browser navigation.

## Changes

- Wrap form reset and inventory loading in Angular `untracked(...)`; the effect
  now tracks company ID and role only.
- Avoid clearing the file-input element inside its own `change` handler.
  Revoke the prior blob URL and update the image preview instead.
- The existing save flow remains: save the item, then upload the selected photo
  to the Spring Boot API, with the 5 MB and file-type checks unchanged.

## Installation

For an existing project, copy the corrected
`frontend/src/app/inventory.page.ts` into your project at the same path.
Restart `npm start` if the Angular dev server does not pick up the change.
There are no backend or MySQL changes.

## Regression checks

1. Log in as a company OWNER or MANAGER, navigate to **Manage My Store**.
2. Type SKU and product name, then select a JPEG/PNG/WebP photo.
3. Confirm all typed values stay in place, no inventory reload is triggered,
   and an image preview appears.
4. Select a different photo; confirm the preview updates without resetting
   any other field.
5. Save the item, then open **Customer Store** to confirm its photo displays.
6. Edit an existing item and replace its photo.
7. Change **My workspace** to a different managed company: the form should
   intentionally reset because the company context actually changed.

If the browser **really** performs a full reload (network navigation with
`document` type), check your browser's Console and Network tabs for JavaScript
errors or dev-server hot reload events; that would be a separate issue.
