# Projekt-Regeln

## APK-Release-Prozess

Nach jeder Änderung am App-Code:

1. **Immer neu bauen + testen**, bevor committet wird:
   ```bash
   export ANDROID_HOME=/opt/android-sdk
   gradle testDebugUnitTest assembleDebug
   ```
   Erst weitermachen, wenn Build und alle Unit-Tests grün sind.

2. **APK als eigenen Commit veröffentlichen** — nicht mit Code-Änderungen
   vermischen:
   ```bash
   cp app/build/outputs/apk/debug/app-debug.apk download/airtag-finder-1.0-debug.apk
   git add download/ && git commit -m "release: rebuild debug APK"
   ```

3. Danach **beide Branches pushen** (`master` und `airtag-finder`, gleicher Stand).

## Build-Umgebung

- JDK 21, Gradle 8.9 (`/opt/gradle-8.9`), Android SDK `/opt/android-sdk`
- SDK-Pakete: `platforms;android-34`, `build-tools;34.0.0`, `platform-tools`
- `local.properties` enthält `sdk.dir` (nicht committen)

## Backend

- Python-Server unter `backend/` — Secrets (`account.json`, `tags/`,
  `ani_libs.bin`) niemals committen, sind in `.gitignore`.
