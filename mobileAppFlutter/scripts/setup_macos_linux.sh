#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v flutter >/dev/null || { echo 'Flutter SDK not on PATH'; exit 1; }
if [[ "$(uname -s)" == "Darwin" ]]; then
  PLATFORMS=android,ios
else
  PLATFORMS=android
fi
flutter create --org space.wolftech --project-name wolftech_mobile --platforms="$PLATFORMS" .
dart scripts/configure_platforms.dart
flutter pub get
flutter analyze --no-fatal-infos
flutter test
echo 'Ready: flutter run --dart-define=API_BASE_URL=https://wolftech.space/api'
