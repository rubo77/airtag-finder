---
name: findmy-protocol
description: FindMy/Offline-Finding protocol notes and Apple GSA debugging knowledge gathered for this project
---

# FindMy-Protokoll & GSA-Debugging

## BLE-Advertisement-Format (Offline Finding)

- Manufacturer data, Company-ID `0x004C`; Payload-Byte 0 = Typ `0x12`,
  Byte 1 = Länge.
- `len=25` (separated): status, 22B key-end, top-2-bits byte, hint.
  Pubkey (28B) = `((p23&3)<<6 | mac0&0x3f)` + mac[1..5] + p[1..22].
- `len=2` (nearby): status, top-2-bits byte. Partial-Key (6B) =
  `((p1&3)<<6 | mac0&0x3f)` + mac[1..5].
- Status: bits 7-6 = Batterie, bits 5-4 = Gerätetyp
  (0=Apple-Gerät, 1=AirTag, 2=FindMy-Zubehör, 3=AirPods).
- **6-Byte-Key-Präfix** ist in beiden Zuständen identisch → `nameKey`,
  Identität pro Key-Periode. Rotation ~15 min (echte AirTags).
- Parser-Port: `ble/FindMyDevice.kt` ↔ `findmy/scanner/scanner.py`.
- Hinweis aus Upstream-Issues: AirTag Gen 2 sendet in Owner-Nähe teils
  DULT- statt OF-Payloads (Issue #272) — möglicher zukünftiger Parser-Fall.

## Apple-GSA-Login — Lessons Learned (-80045)

Symptom: `Email verification failed: This action cannot be completed
at this time` / `Status.ec = -80045` beim GSA `init`.

Bereits geprüft/ausgeschlossen (Stand 2026-10-13):

- IP: identisch via Hetzner + 4 Cloud-Proxies
- Credentials: identisch mit Fake-E-Mail → Fehler VOR Account-Check
- Anisette: identisch lokal/remote/fresh-provisioned
- TLS: identisch aiohttp vs. curl_cffi `impersonate=safari18_0`
- Header: identisch minimal vs. voller X-Apple-I-*-Satz
- Request wird ausgewertet: kaputter Body → `ec -80009` statt `-80045`

Fazit: Apple-Risk-Engine lehnt den Request-Kontext (Anisette-Device)
ab. Historisch transient (Issue #225, löste sich selbst).

Diagnose-Werkzeug: `backend/gsa_probe.py` — probt `init` alle 10 min,
ntfy-Push bei Statuswechsel.

Reproduzierbarer Probe-Request: siehe `gsa_probe.py`
(`o:"init"`, `ps:["s2k","s2k_fo"]`, UA `akd/1.0`, plist an
`https://gsa.apple.com/grandslam/GsService2`).

## Referenzen

- FindMy.py: https://github.com/malmeloo/FindMy.py (Scanner/Account-Code)
- grandslam: https://github.com/nythepegasus/grandslam (GSA-Referenz)
- Public Anisette: https://ani.sidestore.io/ (liefert echte Header)
