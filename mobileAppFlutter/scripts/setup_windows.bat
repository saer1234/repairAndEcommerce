@echo off
setlocal
cd /d "%~dp0.."
where flutter >nul 2>nul
if errorlevel 1 (
  echo Flutter SDK is not installed or is not on your PATH.
  echo Install Flutter from https://docs.flutter.dev/get-started/install/windows/mobile
  exit /b 1
)
flutter create --org space.wolftech --project-name wolftech_mobile --platforms=android .
if errorlevel 1 exit /b 1
dart scripts/configure_platforms.dart
if errorlevel 1 exit /b 1
flutter pub get
if errorlevel 1 exit /b 1
flutter analyze --no-fatal-infos
if errorlevel 1 exit /b 1
flutter test
if errorlevel 1 exit /b 1
echo Setup complete. Run: flutter run --dart-define=API_BASE_URL=https://wolftech.space/api
endlocal
