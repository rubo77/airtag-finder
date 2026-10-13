# TODO & Ideen

## Offen / Blockiert

- [ ] **Apple-GSA-Login** (`ec -80045`): wartet auf Apple — `gsa_probe.py`
      monitort; bei Statuswechsel `login.py` ausführen + 2FA eingeben.
      Danach `account.json` + `ani_libs.bin` ins Backend-Verzeichnis.
- [ ] **AirTag-Key-Export vom Mac**: plist aus `OwnedBeacons/` →
      `FindMyAccessory.from_plist(...).to_json(...)` → `tags/` (siehe
      backend/README.md). Ohne Key zeigt die Karte nichts.
- [ ] **systemd-Unit** für `server.py` (+ ggf. `gsa_probe.py`), damit das
      Backend einen Reboot überlebt.
- [ ] **Signiertes Release-APK**: Keystore anlegen, `assembleRelease`,
      Versionierung hochziehen (aktuell debug-signiert, versionName 1.0).

## App — Features

- [ ] **Scan im Hintergrund**: Foreground-Service + Notification, damit
      Radar/Liste bei gesperrtem Display weiterläuft (Permission
      `FOREGROUND_SERVICE` + `POST_NOTIFICATIONS`).
- [ ] **„Unbekanntes Gerät folgt dir"**: wie Apples Tracker-Warnung —
      ID persistiert über Orte/Zeit → Notification. IdLog liefert die Basis.
- [ ] **Play Sound**: AirTags lassen sich per BLE-GATT zum Piepen bringen
      (Write auf AirTag-Charakteristik) — UUIDs verifizieren, dann
      „Piepen"-Button im Radar.
- [ ] **Filter-Chips**: nur AirTags / nur unbenannte / nur benannte anzeigen.
- [ ] **Rotations-Heuristik verbessern**: neben Zeitnähe auch Signalstärke
      und Übergang nearby↔separated einbeziehen; ggf. Auto-Confirm nach 1× OK.
- [ ] **Rolling-Key-Ableitung** mit AirTag-Privatschlüssel → permanente
      Identität über Rotationen hinweg (P-224-ECDH + KDF portieren,
      Referenz: `RollingKeyPairSource` in FindMy.py) — damit Namen nie
      mehr verloren gehen.
- [ ] **Radar-Feinschliff**: TX-Power kalibrierbar (Knopf „auf 1 m
      kalibrieren"), Beep-Vol/Frequenz-Einstellungen.
- [ ] **Benachrichtigung**, wenn benanntes Gerät auftaucht/verschwindet.
- [ ] **Log-Export**: ID-Log + Reports als JSON/CSV teilen.
- [ ] **Karte**: Positionshistorie des Tags als Trail/Polyline; Badge für
      Report-Alter; Offline-Tiles.
- [ ] **Settings-Screen**: Scan-Modus (Latenz vs. Akku), Sprache,
      Backend-URL, Log-Verwaltung.
- [ ] **i18n**: strings.xml auslagern, EN+DE.

## Backend

- [ ] **Auth**: Token/Passwort vor `/api/locations` (LAN ist ok, WAN nicht).
- [ ] **History-Endpunkt**: `fetch_location_history` → Trail für die Karte.
- [ ] **Dockerfile + Compose** für einfachen Betrieb.
- [ ] **Push an App** (ntfy/WebSocket), wenn Tag bewegt wird.
- [ ] **Mehrere Tags**: UI-Gruppierung, eigene Marker-Farben (API liefert
      schon alle — nur App-Darstellung fehlt).
- [ ] **Health-Monitoring**: Alert, wenn letzter erfolgreicher Fetch alt
      oder Session abgelaufen (re-login nötig).

## Qualität

- [ ] UI-Tests (Compose) oder mind. Screenshot-Tests für die drei Screens.
- [ ] CI (GitHub Actions): `./gradlew testDebugUnitTest assembleDebug`
      + APK-Artefakt; backend `pytest`/Syntax-Check.
- [ ] Release-Pipeline: Tag → signiertes APK → GitHub Release statt
      `download/`-Ordner.
- [ ] Lint (ktlint/detekt) + pre-commit.

## Ideen (unpriorisiert)

- Quick-Settings-Tile „AirTag-Radar starten"
- Wear-OS-/Home-Assistant-Anbindung
- Widget: Distanz zum Lieblings-Tag
- „Crowd-Modus": mehrere befreundete Scanner teilen Sichtungen (Privacy
  klären!)
