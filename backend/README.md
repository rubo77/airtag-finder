# AirTag Finder Backend

Mini-Backend, das über [FindMy.py](https://github.com/malmeloo/FindMy.py) Apples
Find-My-Netzwerk abfragt und die letzte bekannte Position deiner Tags als JSON
bereitstellt — die Android-App zeigt sie auf einer Karte an.

## Setup

```bash
cd backend
pip install -r requirements.txt

# 1) Einmalig einloggen (fragt Apple-ID + 2FA ab, speichert account.json)
python login.py

# 2) Tag-Schlüssel ablegen: FindMyAccessory-JSONs nach tags/ kopieren
mkdir -p tags && cp /pfad/zu/deinem_airtag.json tags/

# 3) Server starten
uvicorn server:app --host 0.0.0.0 --port 8000
```

Test: `curl http://localhost:8000/api/locations`

In der App im Tab **Karte** die Backend-URL eintragen, z. B.
`http://192.168.1.10:8000`.

## Sicherheit

- `account.json`, `tags/*.json` und `ani_libs.bin` enthalten Geheimnisse —
  **nicht committen** (stehen in `.gitignore`).
- Der Server hat keine Authentifizierung — nur im eigenen LAN/VPN betreiben
  oder selbst einen Schutz davorschalten.
- Apple rate-limited die Reports-API: `FINDMY_MIN_REFRESH` (default 300 s)
  nicht zu niedrig setzen.

## Konfiguration (Umgebungsvariablen)

| Variable | Default | Zweck |
|---|---|---|
| `FINDMY_ACCOUNT` | `account.json` | Pfad zur gespeicherten Session |
| `FINDMY_TAGS_DIR` | `tags/` | Ordner mit Tag-JSONs |
| `FINDMY_ANISETTE_LIBS` | `ani_libs.bin` | Anisette-Library-Cache |
| `FINDMY_MIN_REFRESH` | `300` | Mindestabstand Apple-Abfragen (s) |
