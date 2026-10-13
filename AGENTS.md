# Projekt-Regeln & Setup

## Was ist das?

Android-App (Kotlin + Jetpack Compose), die Apple-AirTags/FindMy-Geräte per
BLE findet (Radar) — plus Python-Backend (`backend/`), das via FindMy.py
Apples Find-My-Netzwerk abfragt (Karten-Tab). Siehe `dev/TODO.md` für
offene Aufgaben.

## Lokale Entwicklung

### Voraussetzungen

- **JDK 17+** (`java -version`)
- **Android SDK** mit `platforms;android-34` + `build-tools;34.0.0`
  - Bequemster Weg: **Android Studio** installieren → Projekt öffnen → fertig
  - Headless: `cmdline-tools` + `sdkmanager "platforms;android-34" "build-tools;34.0.0"`
- `local.properties` mit `sdk.dir=/pfad/zum/sdk` anlegen (nicht committen)

### Build & Test

```bash
./gradlew testDebugUnitTest   # alle Unit-Tests (Parser, Keys, Log, HTTP)
./gradlew assembleDebug       # APK -> app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Backend lokal

```bash
cd backend
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python login.py        # einmalig: Apple-ID + 2FA
cp dein_airtag.json tags/
.venv/bin/uvicorn server:app --host 0.0.0.0 --port 8000
```

## APK-Release-Prozess (immer befolgen)

Nach jeder Änderung am App-Code:

1. **Immer neu bauen + testen**, bevor committet wird:
   `./gradlew testDebugUnitTest assembleDebug` — erst bei Grün weiter.
2. **APK als eigenen Commit** — nicht mit Code-Änderungen vermischen:
   ```bash
   cp app/build/outputs/apk/debug/app-debug.apk download/airtag-finder-1.0-debug.apk
   git add download/ && git commit -m "release: rebuild debug APK"
   ```
3. **Beide Branches pushen** (`master` und `airtag-finder`, gleicher Stand).

## Projektstruktur

```
app/src/main/java/dev/findmy/radar/
  MainActivity.kt        Permissions, Tabs (Scanner/Karte), ID-Log, Namen
  ble/FindMyDevice.kt    Offline-Finding Parser (Port aus FindMy.py)
  ble/FindMyScanner.kt   BluetoothLeScanner (Apple 0x004C, Typ 0x12)
  ui/                    DeviceListScreen, RadarScreen, MapScreen
  util/                  KeyFormat, IdLog, LocationClient, ProximityMath
backend/
  login.py, server.py    FindMy.py-Backend (siehe backend/README.md)
app/src/test/            JVM-Unit-Tests (kein Gerät nötig)
```

## Konventionen

- UI-Strings auf **Deutsch**, Code-Kommentare auf Englisch.
- Pure Logik nach `util/` oder `ble/` auslagern → JVM-testbar halten
  (keine Android-Imports dort außer `org.json`/`java.*`).
- Neue Features: erst Unit-Test für die Logik, dann UI.
