# Wolf Tech Repair Flutter V2 — QA status

## Completed checks

- Extracted the actual wolf-head PNG and official store image from the
  `repairAndEcommerce_V30_1_FULL_PROJECT.zip` Angular website source.
- Verified all referenced Flutter `Image.asset` paths exist and are declared
  in `pubspec.yaml`.
- Generated branded Android launcher icons in five densities (48–192 px)
  and the iOS AppIcon set in expected dimensions (including 1024 px).
- Prepared Android native launch background resource and application label
  configuration, and iOS display-name/icon propagation.
- Left authentication, API paths, authorization and payment functions untouched.
- Preserved the saved company selection; selected Wolf Tech Repair by name
  only when the previous selection is missing or invalid.
- Validated release ZIP integrity and XML resource syntax.

## Not executed here

- `flutter pub get`, `flutter analyze`, `flutter test`, Android Gradle build,
  iOS Xcode build, emulator and real-device QA: Flutter/Dart/Android/iOS SDKs
  are not installed in this container.
- App signing / final Play Store or App Store release.

## Developer checklist

1. Copy source files + `assets/` into the existing Flutter project.
2. `dart scripts/configure_platforms.dart`
3. `flutter clean && flutter pub get && flutter analyze && flutter test`
4. `flutter run --dart-define=API_BASE_URL=https://wolftech.space/api`
5. Confirm launcher icon, login, store, language, cart, roles, support chat.
6. `flutter build apk --release --dart-define=API_BASE_URL=https://wolftech.space/api`
