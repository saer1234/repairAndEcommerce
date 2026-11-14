# Wolf Tech Repair Mobile — Flutter Android / iOS

A native Flutter companion application for the **existing Spring Boot + MySQL**
`repairAndEcommerce` project. Designed against the source archive
`repairAndEcommerce_V30_1_FULL_PROJECT.zip` (backend API V30.1, including V26 chat events).

**Status: source implementation, not a compiled APK or IPA.** This environment did not
have Flutter/Dart or a mobile SDK installed, so `flutter analyze`, `flutter test`,
and native app builds could not be executed here. Use the setup steps below to
resolve dependencies, generate platform runners, and run the app. This is not a
replacement backend: it uses your already deployed Spring Boot services and MySQL.

## Features included

| Feature | Customer / guest | Staff / owner |
|---|---|---|
| Public shop/catalog with images, categories, search, offers | Yes, no login needed | Browse |
| Sign in / register (`CLIENT`, `OWNER`) | Yes | Yes |
| Secure JWT storage and session expiry | Yes | Yes |
| English + Arabic, right-to-left navigation | Yes | Yes |
| Cart, item quantity, pickup/delivery checkout | `CLIENT` only | No purchase |
| Delivery GPS position or manually entered coordinates | Yes | — |
| My orders, order details, Whish transfer reference | Yes | Orders and status changes |
| Repair request & customer repair tracking timeline | Yes | Repair queue, updates/status |
| Invoice list and details | Yes | Yes |
| Live WebSocket support and offline inquiries | Yes | Yes |
| Real-time staff online/offline and customer departure events | Yes | Yes |
| Company creation, staff role editing | — | Authorized owner/admin |
| Inventory add/edit, restock, images via camera/gallery | — | Authorized staff |
| Finance overview, Whish transfer review | — | Authorized owner/manager |
| Platform user list/role changes | — | Super admin only |

All authorization decisions remain on the Spring Boot backend. The mobile app
hides unavailable actions but never grants access by itself.

### Functionality that still needs expansion for full web parity

The web app contains more detailed inventory movement history, repair parts,
repair labor/assignment/report workflows, expense creation, invoice creation,
full manager reports, storefront compatibility search, and advanced delivery/map
interactions. The Flutter app already shows core flows, but these advanced
forms and dashboards are **not yet fully ported**. No push notification service
or automatic background chat notification has been added: live chat is active
while the support page is open and the app is in the foreground.

## Requirements

- Flutter stable SDK with Dart 3.5+, Android Studio/Android SDK (Android builds).
- On macOS: Xcode and CocoaPods for iOS builds/signing.
- Working HTTPS Spring Boot API at `https://wolftech.space/api` or your own domain.
- Real server account credentials. No test credentials or secrets are bundled.

## Windows setup

Unzip this folder and run in PowerShell / Command Prompt:

```powershell
cd wolftech_mobile_flutter
scripts\setup_windows.bat
flutter devices
flutter run --dart-define=API_BASE_URL=https://wolftech.space/api
```

If the automated script fails, run the underlying commands individually:

```powershell
flutter create --org space.wolftech --project-name wolftech_mobile --platforms=android .
dart scripts/configure_platforms.dart
flutter pub get
flutter analyze --no-fatal-infos
flutter test
flutter run --dart-define=API_BASE_URL=https://wolftech.space/api
```

`flutter create` generates the `android/` runner on Windows, and the
macOS setup script also generates `ios/`; it does not need to replace the existing `lib/`
implementation. The permission script adds Android network/GPS and iOS
camera, library and GPS descriptions. Run the setup script again if you
regenerate a platform folder. If `flutter create` overwrites any custom file,
restore it from this ZIP. **A release iOS app requires macOS and Apple signing.**

### Build Android APK

```powershell
flutter build apk --release --dart-define=API_BASE_URL=https://wolftech.space/api
```

APK: `build/app/outputs/flutter-apk/app-release.apk` after a successful build.
Configure a proper app signing key before distributing a production APK.

### iOS (on macOS)

```bash
./scripts/setup_macos_linux.sh
flutter build ipa --release --dart-define=API_BASE_URL=https://wolftech.space/api
```

You need an Apple Developer account, suitable provisioning profiles and signing.

## Backend integration

The Flutter app does **not** change your Spring Boot API or database. Key routes:

- `POST /api/auth/login`, `POST /api/auth/register`, `GET /api/me`
- `GET /api/catalog/companies`, `GET /api/companies/{id}/items`
- `GET|PUT|DELETE /api/companies/{id}/cart...`
- `POST /api/companies/{id}/checkout`, `GET /api/companies/{id}/orders/mine`
- `GET /api/repairs/tracking`, `POST /api/companies/{id}/repairs`
- `GET /api/companies/{id}/support/availability`, `POST .../ws-ticket`
- `wss://host/api/support/ws?ticket=...` — single-use, short-lived ticket (not JWT)
- `POST /api/companies/{id}/support/presence/heartbeat`
- Company membership roles, inventory, invoices, Whish transfers and finance routes.

See `docs/API_MAPPING.md` for the integration map. A secured Spring Boot server
with the **V26+ WebSocket messages** is required for live chat event features.

## Security / operational notes

- Only customer accounts without a company membership can add to cart/checkout.
- JWT is stored in platform secure storage (Keychain / Android encrypted storage);
  it expires at the server-provided time. No refresh-token flow exists in V30.1.
- JWT is sent in `Authorization: Bearer` on REST requests, **never** in WebSocket URLs.
  The support socket uses the backend's 30-second, one-time ticket.
- Browsers may enforce CORS; native Flutter Android/iOS HTTP clients do not. You
  still need a valid TLS certificate and a public API address.
- Staff presence heartbeat runs while the support view is foreground and available.
  Leaving the view closes the WebSocket and sends best-effort presence deletion.
- Client sockets close on view exit/background, letting your V23–V26 server
  detect offline customers and shut down live chat.
- Product images are loaded only from server-generated `/api/companies/.../image` paths.
- The client never calculates authoritative catalog prices or stock. Checkout
  asks the server to validate totals and current stock.
- Delivery positioning requests location only on demand; no background tracking.

## Suggested testing checklist

1. Guest can see products but tapping Buy opens Sign in.
2. `CLIENT` login can add/remove items; an `OWNER` can't checkout.
3. Pickup order works; delivery validates address and GPS coordinates.
4. Arabic switches direction and persists after app restart.
5. My orders, invoice details, repair tracking and new repair requests work.
6. With two devices, staff availability and client exit update each other's chat.
7. Closing the staff support page releases its availability; other staff stay online.
8. Staff can change permitted company roles but never self-promote to super admin.
9. Build with `flutter analyze`, `flutter test`, then test on actual Android/iOS devices.

## Project organization

```
lib/
  main.dart                 # App shell, language and navigation
  core/
    api.dart                # REST transport, image upload, error handling
    app_state.dart          # Auth, secure token storage, company selection
    i18n.dart               # English/Arabic UI text and inherited app state
  screens/
    auth.dart               # Login and registration
    store.dart              # Public shop, cart, checkout, GPS
    orders.dart             # Orders and Whish reference submission
    repairs.dart            # Customer tracking and staff repair status
    support.dart            # Native WebSocket chat and staff availability
    staff.dart              # Company, inventory, roles, finance and payments
    invoices.dart           # Customer/staff invoice list and details
  widgets/ui.dart           # Shared mobile design components
scripts/                     # Runners, permissions, setup commands
```

Version: **Wolf Tech Repair Mobile 1.1.0+2 (Flutter V2 branded source)**.

## V2 — Official Wolf Tech Repair branding

The Flutter V2 app now uses the **same actual brand assets** as the V30.1 Angular website:

- `assets/images/wolflog-removebg-preview.png` — original WolfTech wolf-head logo
- `assets/images/wolftech-showcase.jpg` — original store/showroom banner
- `lib/widgets/brand.dart` — reusable wordmark and brand showcase widgets
- `assets/icons/android/...` and `assets/icons/ios/...` — platform launcher icons using the wolf logo
- `assets/icons/wolftech_splash.png` — Android launch mark

The app bar, shop landing banner, login and registration views, and account page
now display Wolf Tech Repair rather than generic tool icons. The published Wolf
Tech Repair company is preferred for the first public store selection when it
exists. A customer's saved store choice is preserved, and all catalogs still
load from the backend. **No backend or MySQL migration is required.**

### Apply V2 to an existing Android project (Windows)

**Do not delete your local `android/` directory or rerun `flutter create` when
it already exists.** Back up the current project and overlay the V2 files,
including `assets/`, `lib/`, `pubspec.yaml`, and `scripts/`. Then:

```powershell
cd D:\techApp\mobileAppFlutter

# Repair the custom Pub cache path if necessary (no leading space):
$env:PUB_CACHE = 'D:\techApp\pub-cache'

# Apply Android launcher name, original wolf icon, splash and permissions:
dart scripts/configure_platforms.dart

flutter clean
flutter pub get
flutter analyze
flutter test
flutter run --dart-define=API_BASE_URL=https://wolftech.space/api
```

After verifying the UI on an Android device, create the release APK:

```powershell
flutter build apk --release --dart-define=API_BASE_URL=https://wolftech.space/api
```

The expected output is `build/app/outputs/flutter-apk/app-release.apk`.
An application update normally retains the existing Android application ID;
launcher text/icon can take a moment to refresh on some devices.
For iOS, run `dart scripts/configure_platforms.dart` on macOS after the
`ios/` runner has been generated.

**Build status:** Branding source and images are included, but this ZIP is
not a compiled APK. Flutter, Dart, Android SDK, and iOS SDK are unavailable
in this workspace, so app compilation and emulator tests have not been run.
