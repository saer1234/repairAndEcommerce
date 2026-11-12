# V29 — Bilingual sign-in and registration

Based on V28.2 full project.

- English / Arabic switcher on login and registration pages; persists using the existing StoreLanguageService key `wolftech.store.language`.
- Translated field labels, buttons, standard browser-side validation errors, navigation links, and some common server response messages.
- RTL for Arabic forms; email, phone and password inputs stay LTR.
- Refreshed mobile-friendly authentication card styling.
- Existing registration/login endpoints, role protections, and redirect behavior remain unchanged.

## Installation
Copy the seven frontend files from this patch into the same project-relative paths, or extract the complete project ZIP.

## Verification
Translation keys checked for duplicates and template interpolations counted. Full Angular build unavailable: dependency installation failed (offline npm cache missing packages); run `npm ci && npm run build` in your normal development environment.

Note: Arbitrary backend validation messages not in the translation map are shown in their original language; translating all possible backend errors would require structured error codes from the API.
