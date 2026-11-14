import 'dart:io';

// Run from the project root after `flutter create --platforms=android,ios .`.
// Re-run this script after replacing files in an existing Flutter Android/iOS app.
// The launcher images are generated from WolfTech's real website wolf-head logo.
void main() {
  final manifest = File('android/app/src/main/AndroidManifest.xml');
  if (manifest.existsSync()) {
    var xml = manifest.readAsStringSync();
    for (final permission in [
      'android.permission.INTERNET',
      'android.permission.ACCESS_COARSE_LOCATION',
      'android.permission.ACCESS_FINE_LOCATION',
    ]) {
      if (!xml.contains(permission)) {
        xml = xml.replaceFirst('<application',
          '<uses-permission android:name="$permission" />\n    <application');
      }
    }
    // The package/applicationId stays unchanged to avoid affecting installed apps.
    xml = xml.replaceAll(RegExp(r'android:label="[^"]*"'),
      'android:label="Wolf Tech Repair"');
    manifest.writeAsStringSync(xml);

    const iconSizes = <String>[
      'mipmap-mdpi', 'mipmap-hdpi', 'mipmap-xhdpi',
      'mipmap-xxhdpi', 'mipmap-xxxhdpi',
    ];
    final res = Directory('android/app/src/main/res');
    for (final density in iconSizes) {
      final source = File('assets/icons/android/$density/ic_launcher.png');
      if (!source.existsSync()) {
        throw StateError('Missing WolfTech icon asset: ${source.path}');
      }
      final targetFolder = Directory('${res.path}/$density');
      targetFolder.createSync(recursive: true);
      source.copySync('${targetFolder.path}/ic_launcher.png');
      // If the generated manifest references a round icon, keep it branded too.
      source.copySync('${targetFolder.path}/ic_launcher_round.png');
    }

    // The Android 12 splash icon inherits the launcher; the pre-12 screen
    // displays the official wolf mark on a navy background.
    final drawable = Directory('${res.path}/drawable');
    drawable.createSync(recursive: true);
    File('assets/icons/wolftech_splash.png')
      .copySync('${drawable.path}/wolftech_splash.png');
    final values = Directory('${res.path}/values');
    values.createSync(recursive: true);
    File('${values.path}/wolftech_colors.xml').writeAsStringSync('''
<?xml version="1.0" encoding="utf-8"?>
<resources>
  <color name="wolftech_navy">#091A36</color>
</resources>
'''.trimLeft());
    const launchBackground = '''
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
  <item android:drawable="@color/wolftech_navy" />
  <item>
    <bitmap android:gravity="center" android:src="@drawable/wolftech_splash" />
  </item>
</layer-list>
''';
    for (final folder in ['drawable', 'drawable-v21']) {
      final directory = Directory('${res.path}/$folder');
      directory.createSync(recursive: true);
      File('${directory.path}/launch_background.xml')
        .writeAsStringSync(launchBackground.trimLeft());
    }
    stdout.writeln('Android: Wolf Tech Repair display name, icons, splash and permissions configured.');
  }

  final kotlinGradle = File('android/app/build.gradle.kts');
  if (kotlinGradle.existsSync()) {
    var gradle = kotlinGradle.readAsStringSync();
    gradle = gradle.replaceFirst('minSdk = flutter.minSdkVersion', 'minSdk = 24');
    kotlinGradle.writeAsStringSync(gradle);
  }
  final groovyGradle = File('android/app/build.gradle');
  if (groovyGradle.existsSync()) {
    var gradle = groovyGradle.readAsStringSync();
    gradle = gradle.replaceFirst('minSdkVersion flutter.minSdkVersion', 'minSdkVersion 24');
    groovyGradle.writeAsStringSync(gradle);
  }

  final ios = File('ios/Runner/Info.plist');
  if (ios.existsSync()) {
    var xml = ios.readAsStringSync();
    final permissions = <String, String>{
      'NSLocationWhenInUseUsageDescription': 'Wolf Tech Repair uses your location only for a delivery address you select.',
      'NSPhotoLibraryUsageDescription': 'Allow Wolf Tech Repair to choose item photos from your library.',
      'NSCameraUsageDescription': 'Allow Wolf Tech Repair to photograph product items.',
    };
    for (final entry in permissions.entries) {
      if (!xml.contains('<key>${entry.key}</key>')) {
        final idx = xml.lastIndexOf('</dict>');
        if (idx < 0) throw const FormatException('Invalid iOS Info.plist');
        xml = xml.replaceRange(idx, idx,
          '    <key>${entry.key}</key>\n    <string>${entry.value}</string>\n');
      }
    }
    final displayName = RegExp(r'(<key>CFBundleDisplayName</key>\s*<string>)[^<]*(</string>)');
    if (displayName.hasMatch(xml)) {
      xml = xml.replaceFirstMapped(displayName, (m) =>
        '${m.group(1)}Wolf Tech Repair${m.group(2)}');
    } else {
      final idx = xml.lastIndexOf('</dict>');
      if (idx < 0) throw const FormatException('Invalid iOS Info.plist');
      xml = xml.replaceRange(idx, idx,
        '    <key>CFBundleDisplayName</key>\n    <string>Wolf Tech Repair</string>\n');
    }
    ios.writeAsStringSync(xml);

    final iconFolder = Directory('ios/Runner/Assets.xcassets/AppIcon.appiconset');
    if (iconFolder.existsSync()) {
      final sourceFolder = Directory('assets/icons/ios');
      for (final source in sourceFolder.listSync().whereType<File>()) {
        if (source.path.endsWith('.png')) {
          final name = source.uri.pathSegments.last;
          source.copySync('${iconFolder.path}/$name');
        }
      }
      stdout.writeln('iOS: Wolf Tech Repair display name and app icons configured.');
    }
  }
}
