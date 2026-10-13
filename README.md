# AirTag Finder

Android-App zum Finden von AirTags und anderen Apple-FindMy-Geräten in Bluetooth-Reichweite.

Die App scannt BLE-Advertisements vom Typ *Offline Finding* (Apple Company-ID `0x004C`, Typ `0x12`), dekodiert sie und zeigt alle FindMy-Geräte in der Nähe an — mit Entfernungsschätzung und einem Radar-Modus zum Anpeilen.

## Features

- **Geräteliste**: alle gefundenen AirTags, AirPods, iPhones und FindMy-Zubehör mit MAC, Advertisement-Key-Präfix, RSSI, Batteriesymbol und Entfernungsschätzung
- **Radar**: große Entfernungsanzeige, Sweep-Animation, Pieptöne die schneller werden je näher du kommst, Vibration unter ~1 m
- **Eigen-Filter**: `adv_key_b64` deines AirTags (z. B. aus [FindMy.py](https://github.com/malmeloo/FindMy.py)) eintragen → dein Gerät wird markiert und die Liste gefiltert
- **Zustände**: unterscheidet *separated* (voller 28-Byte-Key wird gesendet) und *nearby* (nur 6 Key-Bytes)
- **Permissions** korrekt je Android-Version: „Geräte in der Nähe" ab Android 12, Standort-Berechtigung auf älteren Versionen

## Download

Fertiges Debug-APK liegt unter [`download/`](download/):

- [airtag-finder-1.0-debug.apk](download/airtag-finder-1.0-debug.apk) — direkt aufs Handy laden und installieren („Unbekannte Quellen" erlauben)

Mindestanforderung: **Android 8.0 (API 26)** mit Bluetooth LE.

## Build

```bash
git clone https://github.com/rubo77/airtag-finder.git
cd airtag-finder
./gradlew assembleDebug        # APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # Unit-Tests (Parser, Key-Format, Proximity)
```

Oder das Projekt in **Android Studio** öffnen und synchronisieren lassen.

## Wie es funktioniert

AirTags senden alle ~2 Sekunden BLE-Advertisements im Apple-Offline-Finding-Format:

| Payload-Länge | Zustand | Inhalt |
|---|---|---|
| 25 Byte | separated | Status, 28-Byte-Pubkey (über MAC + Payload rekonstruiert), Hint |
| 2 Byte | nearby | Status, 6 Key-Bytes (Rest über MAC rekonstruiert) |

Der Decoder (`FindMyDevice.parse`) ist aus [FindMy.py](https://github.com/malmeloo/FindMy.py) portiert. Die Entfernung wird aus dem RSSI über ein Log-Distance-Path-Loss-Modell geschätzt (`d = 10^((txPower - rssi) / 25)`).

## Einschränkungen

- **Nur Bluetooth-Reichweite** (~10–50 m). Die Ortung über Apples Find-My-Server ist nicht implementiert — dafür braucht es die AirTag-Privatschlüssel + Apple-Login, was FindMy.py am PC leistet.
- Der Advertisement-Key **rotiert ca. alle 15 Minuten** — danach muss das Ziel ggf. neu angepeilt werden.
- Die Entfernungsanzeige ist eine grobe Schätzung; Wände und Ausrichtung verfälschen RSSI.

## Credits

- Payload-Format & Decoder-Logik: [FindMy.py](https://github.com/malmeloo/FindMy.py)
- Basis: OpenHaystack-Forschung zum Offline-Finding-Protokoll
