---
name: backend-ops
description: Run and operate the FindMy.py location backend (login, key export, server, troubleshooting)
---

# Backend-Betrieb

Lives unter `backend/`: `server.py` (FastAPI `/api/locations`), `login.py`
(interaktiver Apple-Login mit sauberen Fehlermeldungen), `requirements.txt`.

## Setup-Sequenz

```bash
cd backend
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python login.py              # einmalig: Apple-ID + 2FA -> account.json
cp airtag.json tags/                   # FindMyAccessory-Export
.venv/bin/uvicorn server:app --host 0.0.0.0 --port 8000
```

Der Server lädt `account.json` beim ersten `/api/locations`-Request —
**kein Neustart** nach dem Login nötig. Cache: `FINDMY_MIN_REFRESH` (default
300 s) gegen Apple-Rate-Limits.

## AirTag-Key-Export

Auf einem Mac mit derselben Apple-ID: plists unter
`~/Library/com.apple.icloud.searchpartyd/OwnedBeacons/`, dann:

```python
from findmy import FindMyAccessory
FindMyAccessory.from_plist("Beacon_XXXX.plist").to_json("airtag.json")
```

## Troubleshooting Login

- `ec -80045` („cannot be completed"): Ablehnung VOR Passwort/2FA —
  Apple-Risk-Engine oder temporäre Welle, kein Credential-Problem.
  Diagnose via `gsa_probe.py` (probt init alle 10 min, ntfy bei Änderung).
- Tracebacks sind in `login.py` abgefangen — bei neuen Fehlertypen
  `_explain_login_error` erweitern.
- Account-Check: `appleid.apple.com` im Browser.

## Produktions-Backend

Läuft aktuell unter `/root/workspace/airtag-backend/` auf dem Server
(`uvicorn` via `nohup`, Port 8000, Log `server.log`). Überlebt keinen
Reboot — für Dauerbetrieb systemd-Unit bauen (siehe dev/TODO.md).
