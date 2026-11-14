# Wolf Tech Repair Flutter V2 — Branding changes

This update corrects V1's generic app identity. The website's **real** wolf-head
logo and showroom banner are bundled into Flutter and displayed on the actual
store, authentication pages, and navigation bar.

### New files
- `assets/images/wolflog-removebg-preview.png`
- `assets/images/wolftech-showcase.jpg`
- `assets/icons/android/mipmap-*/ic_launcher.png` (5 sizes)
- `assets/icons/ios/*.png` (iOS icon sizes)
- `assets/icons/wolftech_splash.png`
- `lib/widgets/brand.dart`

### Updated files
- `lib/main.dart`: official brand in header/account and application title
- `lib/screens/store.dart`: official store showcase and logo
- `lib/screens/auth.dart`: logo, wordmark and brand slogan on login/register
- `lib/core/app_state.dart`: prefer WolfTech shop on first launch, preserve saved company
- `lib/core/i18n.dart`: bilingual tagline
- `pubspec.yaml`: register image assets
- `scripts/configure_platforms.dart`: set Android/iOS display name, launcher icon,
  Android splash and permissions after native runners exist
- `README.md`: integration instructions

### Notes
- No Spring Boot backend, MySQL schema, authentication flow, support chat, or
  customer/staff roles are changed.
- No separate WolfTech company ID is hardcoded: first launch selects the
  published company whose name contains WolfTech, otherwise follows existing
  catalog behavior. Existing company selection remains intact.
- If you only overlay the Dart files and omit `assets/`, Flutter will report
  missing images. Copy the entire `assets/` folder and `pubspec.yaml`.
- Run `dart scripts/configure_platforms.dart` from the Flutter project root
  to apply launcher icons and app name to existing Android/iOS platform files.
- Compilation and device tests must be completed on a Flutter-equipped machine.
