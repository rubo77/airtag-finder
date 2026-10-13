---
name: android-build
description: Build, test and release the AirTag Finder Android app (Gradle, APK download folder, branch sync)
---

# Android Build & Release

## Schnellstart

```bash
./gradlew testDebugUnitTest assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Voraussetzung: `local.properties` mit `sdk.dir=...` (Android SDK 34,
Build-Tools 34.0.0, JDK 17+).

## Release-Checkliste (nach jeder Code-Änderung)

1. `./gradlew testDebugUnitTest assembleDebug` — muss grün sein.
2. APK kopieren:
   `cp app/build/outputs/apk/debug/app-debug.apk download/airtag-finder-1.0-debug.apk`
3. **Separater** Commit nur für das APK: `release: rebuild debug APK`.
4. `master` **und** `airtag-finder` pushen (gleicher Stand).

## Testinfrastruktur

- JUnit4 JVM-Tests unter `app/src/test/` — kein Gerät nötig.
- `org.json` ist als `testImplementation` eingebunden → JSON-Parsing
  in `util/` ist auf der JVM testbar.
- HTTP-Roundtrips mit eingebettetem `java.net.ServerSocket` testen
  (siehe `LocationClientTest`), nicht `com.sun.net.httpserver`
  (nicht auf dem Android-Test-Classpath).

## Häufige Stolpersteine

- Kotlin-Compile-Daemon stirbt gelegentlich („daemon has terminated"):
  Build einfach erneut starten, transiente Warnung.
- `Stroke(...)` hat kein `alpha`-Param — Alpha in `Color.copy(alpha=)`.
- Scoped-Modifier wie `Modifier.weight` sind kein Top-Level-Import.
