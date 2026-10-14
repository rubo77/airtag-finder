# AirTag Finder

![Status](https://img.shields.io/badge/Status-blocked-red)
![Plattform](https://img.shields.io/badge/Android-8.0%2B-green)
![Backend](https://img.shields.io/badge/Backend-FastAPI%20%2B%20FindMy.py-blue)

> [!WARNING]
> 🔴 **Remote-Ortung aktuell blockiert** (Stand: Oktober 2026): Apple lehnt
> den GSA-Login derzeit mit `ec=-80045` ab (Anisette-/Risk-Engine) — das
> Backend kann deshalb keine Positions-Reports aus dem Find-My-Netzwerk
> abrufen. Derselbe Fehler betrifft aktuell auch andere Projekte, z. B.
> [OpenTagViewer](https://github.com/parawanderer/OpenTagViewer).
> Ein Watcher ([`backend/gsa_probe.py`](backend/gsa_probe.py)) testet alle
> 10 Minuten, ob Apple den Login wieder durchlässt.
> **Alle BLE-Funktionen (Geräteliste, Radar, Benennung, Rolling-Key-Erkennung,
> Hintergrund-Scan) funktionieren uneingeschränkt — komplett lokal, ohne
> Apple-Account.**

Android-App zum Finden von AirTags und anderen Apple-FindMy-Geräten in
Bluetooth-Reichweite — plus optionale Remote-Ortung über Apples
Find-My-Netzwerk via dem mitgelieferten Backend.

Die App scannt BLE-Advertisements vom Typ *Offline Finding* (Apple Company-ID
`0x004C`, Typ `0x12`), dekodiert sie und zeigt alle FindMy-Geräte in der Nähe
an — mit Entfernungsschätzung und einem Radar-Modus zum Anpeilen.

> [!NOTE]
> Dieses Projekt ist in keiner Weise mit Apple Inc. oder Android/Google LLC
> verbunden.

## Features

- **Geräteliste**: alle gefundenen AirTags, AirPods, iPhones und FindMy-Zubehör mit MAC, Advertisement-Key-Präfix, RSSI, Batteriesymbol und Entfernungsschätzung
- **Radar**: große Entfernungsanzeige, Sweep-Animation, Pieptöne die schneller werden je näher du kommst, Vibration unter ~1 m
- **Rolling-Key-Identität**: FindMyAccessory-JSON (mit SKN/SKS) in der App importieren → eigene Tags werden **permanent über alle Schlüssel-Rotationen** erkannt (P-224-EC + KDF, bitgenau zu FindMy.py portiert)
- **Eigen-Filter**: `adv_key_b64` deines AirTags eintragen → dein Gerät wird markiert und die Liste gefiltert
- **Eigene Namen**: jedes gefundene Gerät lässt sich benennen, Namen bleiben gespeichert
- **Rotations-Heuristik** (Fallback für fremde Tags): neue IDs nach dem Verschwinden eines benannten Geräts werden dem Vorgänger vorgeschlagen (manuell bestätigbar)
- **Hintergrund-Scan**: Foreground-Service + Notification → Scan läuft bei gesperrtem Display weiter
- **Benachrichtigungen**: wenn ein benanntes Gerät auftaucht oder verschwindet
- **Filter-Chips**: Alle / nur AirTags / nur benannte / nur unbenannte Geräte
- **Log-Export**: ID-Log + Gerätenamen als JSON teilen
- **Karten-Tab**: letzte bekannte Position aus Apples Find-My-Netzwerk auf OpenStreetMap — inklusive Positionshistorie als Trail/Polyline pro Tag (via [`backend/`](backend/))
- **Zustände**: unterscheidet *separated* (voller 28-Byte-Key) und *nearby* (nur 6 Key-Bytes)
- **Permissions** korrekt je Android-Version: „Geräte in der Nähe" ab Android 12, Standort-Berechtigung auf älteren Versionen

## Was wird unterstützt

**Alles, was im Offline-Finding-Format advertised, sieht die App per BLE.**

| Was | Funktioniert? | |
| --- | --- | --- |
| **AirTag** (BLE: Liste, Radar, Benennung) | ✅ | sofort, ohne Account/Backend |
| **AirTag** (Remote-Ortung über Find My) | ⛔ | aktuell durch Apple blockiert (`ec=-80045`, siehe oben) |
| **Drittanbieter-Tracker mit Apple Find My** (Chipolo, Pebblebee, eufy, MiTag …) | ✅ | gleiches Advertisement-Format |
| **AirPods, iPhones, andere FindMy-Geräte** | ✅ | BLE-Sichtung (nearby/separated) |
| **Custom Tags** ([OpenHaystack](https://github.com/seemoo-lab/openhaystack)/[Macless Haystack](https://github.com/dchristl/macless-haystack)) | ✅ | eigene Keys bekannt → JSON-Import direkt möglich |
| **Ein Tag, den dir jemand per Apple-Sharing geteilt hat** | ❌ | nur der Besitzer kann den Schlüssel exportieren |
| **Samsung SmartTag, Tile** | ❌ | völlig anderes Netzwerk |

## Download

Fertiges Debug-APK liegt unter [`download/`](download/):

- [airtag-finder-1.0-debug.apk](download/airtag-finder-1.0-debug.apk) — direkt aufs Handy laden und installieren („Unbekannte Quellen" erlauben)

Mindestanforderung: **Android 8.0 (API 26)** mit Bluetooth LE.

## Voraussetzungen & Setup

### Nur BLE-Scan (funktioniert sofort)

1. APK installieren
2. Bluetooth- + Standort-Berechtigungen erteilen
3. Scannen — kein Account, kein Backend, kein Apple nötig

Optional für permanente Tag-Erkennung: `FindMyAccessory`-JSON des eigenen
Tags importieren (Dialog „Eigene Tags") — die App leitet daraus alle
rotierten Werbe-Keys selbst ab.

### Remote-Ortung (sobald der Apple-Login wieder durchlässt)

1. Ein **Android-Phone** mit installierter App
2. Ein **Apple-Account** mit 2FA (SMS oder Trusted Device)
3. Einen **Linux-Server** (oder Rechner im LAN) für [`backend/`](backend/)
4. **Einmalig** Zugriff auf einen Mac / eine macOS-VM, der deine Tags in
   „Wo ist?" sieht — für den Export der AirTag-Privatschlüssel
   (`~/Library/com.apple.icloud.searchpartyd/OwnedBeacons/`,
   Details: [`backend/README.md`](backend/README.md))

```bash
cd backend && pip install -r requirements.txt
python login.py          # einmalig: Apple-ID + 2FA → account.json
cp dein_airtag.json tags/ # FindMyAccessory-JSON aus dem Mac-Export
uvicorn server:app --host 0.0.0.0 --port 8000
```

In der App im Tab **Karte** die Backend-URL eintragen, z. B.
`http://192.168.1.10:8000`. Produktivbetrieb mit systemd: siehe
[`backend/`](backend/) bzw. `airtag-backend.service` /
`airtag-gsa-probe.service`.

## Wie es funktioniert

AirTags senden alle ~2 Sekunden BLE-Advertisements im Apple-Offline-Finding-Format:

| Payload-Länge | Zustand | Inhalt |
|---|---|---|
| 25 Byte | separated | Status, 28-Byte-Pubkey (über MAC + Payload rekonstruiert), Hint |
| 2 Byte | nearby | Status, 6 Key-Bytes (Rest über MAC rekonstruiert) |

Der Decoder (`FindMyDevice.parse`) ist aus
[FindMy.py](https://github.com/malmeloo/FindMy.py) portiert. Die Entfernung
wird aus dem RSSI über ein Log-Distance-Path-Loss-Modell geschätzt
(`d = 10^((txPower - rssi) / 25)`).

Der Advertisement-Key **rotiert ca. alle 15 Minuten**. Für eigene Tags löst
die App das kryptografisch: aus dem importierten FindMyAccessory (SKN/SKS)
leitet `crypto/RollingKey.kt` per X9.63-KDF und P-224-Skalararithmetik
(`crypto/P224.kt`) jeden zukünftigen Werbe-Key deterministisch ab — Namen
und Zuordnung gehen nie wieder verloren. Für fremde Tags greift weiterhin
die Zeit-/RSSI-Heuristik.

## Aktueller Blocker: Apple GSA `ec=-80045`

- **Symptom**: `backend/login.py` (bzw. jede GSA-Abfrage) wird von Apple mit
  `ec=-80045` abgelehnt — die Risk-Engine akzeptiert den Anisette-Kontext
  nicht. Kein Credential-Problem.
- **Monitoring**: `backend/gsa_probe.py` läuft als systemd-Service alle
  600 s und prüft, ob der Fehlercode sich ändert (Status in `.last_ec`,
  Log `gsa_probe.log`).
- **Sobald Apple wieder durchlässt**: `python login.py` + 2FA →
  `account.json` + `ani_libs.bin` ins Backend-Verzeichnis →
  `systemctl restart airtag-backend` — fertig.
- **Betroffen**: alle Projekte, die GSA-Server-seitig anmelden — u. a.
  FindMy.py-Nutzer und OpenTagViewer.

## Einschränkungen

- **BLE-Radar**: nur Bluetooth-Reichweite (~10–50 m); Entfernung ist eine grobe RSSI-Schätzung.
- **Karte**: zeigt nur Reports, die andere Apple-Geräte hochgeladen haben — kein Live-Tracking; aktuell komplett blockiert (s. oben).
- Das Backend hat **keine Auth** — nur im eigenen LAN/VPN betreiben.
- **AirTag-Key-Export** erfordert einmalig einen Mac (oder macOS-VM).

## Build

```bash
git clone https://github.com/rubo77/airtag-finder.git
cd airtag-finder
./gradlew assembleDebug        # APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # Unit-Tests (Parser, Rolling-Key-Krypto, Proximity)
```

Oder das Projekt in **Android Studio** öffnen und synchronisieren lassen.

## Credits

- Find-My-Abfrage, Key-Format & Decoder-Referenz: [FindMy.py](https://github.com/malmeloo/FindMy.py)
- Offline-Finding-Protokoll: [OpenHaystack](https://github.com/seemoo-lab/openhaystack)-Forschung
- Verwandtes Projekt: [OpenTagViewer](https://github.com/parawanderer/OpenTagViewer) (FindMy.py via Chaquopy direkt auf dem Gerät)
