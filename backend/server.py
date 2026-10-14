"""Minimal location backend for the AirTag Finder app.

Uses FindMy.py to query Apple's Find My network and exposes the latest
location of each configured tag as JSON:

    GET /api/locations
    -> [{"name": ..., "latitude": ..., "longitude": ...,
         "timestamp": ..., "confidence": ..., "horizontal_accuracy": ...,
         "battery": ..., "status": ...}]

Setup:
    pip install -r requirements.txt
    python login.py                  # one-time Apple login -> account.json
    mkdir tags && cp <tag>.json tags/  # FindMyAccessory JSON exports
    uvicorn server:app --host 0.0.0.0 --port 8000
"""

from __future__ import annotations

import os
import time
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException
from findmy import AppleAccount, FindMyAccessory

BASE_DIR = Path(__file__).resolve().parent
ACCOUNT_PATH = Path(os.environ.get("FINDMY_ACCOUNT", BASE_DIR / "account.json"))
TAGS_DIR = Path(os.environ.get("FINDMY_TAGS_DIR", BASE_DIR / "tags"))
ANISETTE_LIBS = os.environ.get("FINDMY_ANISETTE_LIBS", str(BASE_DIR / "ani_libs.bin"))

# Minimum seconds between real queries to Apple's servers. Lower values risk
# rate-limiting / account flags; Find My reports only appear when an Apple
# device saw the tag anyway.
MIN_REFRESH_SECONDS = int(os.environ.get("FINDMY_MIN_REFRESH", "300"))

BATTERY_LEVEL = {0b00: "Full", 0b01: "Medium", 0b10: "Low", 0b11: "Very Low"}

app = FastAPI(title="AirTag Finder Backend")

_cache: dict[str, Any] = {"fetched_at": 0.0, "locations": []}
_account: AppleAccount | None = None
_tags: list[FindMyAccessory] | None = None


def get_account() -> AppleAccount:
    global _account
    if _account is None:
        if not ACCOUNT_PATH.exists():
            raise HTTPException(
                503,
                f"Kein Login gefunden ({ACCOUNT_PATH}). Einmalig `python login.py` ausführen.",
            )
        _account = AppleAccount.from_json(
            str(ACCOUNT_PATH), anisette_libs_path=ANISETTE_LIBS,
        )
    return _account


def get_tags() -> list[FindMyAccessory]:
    global _tags
    if _tags is None:
        paths = sorted(TAGS_DIR.glob("*.json"))
        if not paths:
            raise HTTPException(503, f"Keine Tag-JSONs in {TAGS_DIR} gefunden.")
        _tags = [FindMyAccessory.from_json(str(p)) for p in paths]
    return _tags


def report_to_json(name: str, report: Any) -> dict[str, Any]:
    return {
        "name": name,
        "latitude": report.latitude,
        "longitude": report.longitude,
        "timestamp": report.timestamp.isoformat(),
        "confidence": report.confidence,
        "horizontal_accuracy": report.horizontal_accuracy,
        "status": report.status,
        "battery": BATTERY_LEVEL.get((report.status >> 6) & 0b11, "Unknown"),
    }


@app.get("/api/locations")
def locations() -> dict[str, Any]:
    now = time.time()
    if now - _cache["fetched_at"] < MIN_REFRESH_SECONDS:
        return _cache

    acc = get_account()
    tags = get_tags()
    try:
        results = acc.fetch_location(tags)
    except Exception as exc:  # auth/session errors surface as 502
        raise HTTPException(502, f"FindMy-Abfrage fehlgeschlagen: {exc}") from exc
    finally:
        acc.to_json(str(ACCOUNT_PATH))  # persist refreshed session state

    out = []
    for tag in tags:
        report = results.get(tag)
        name = tag.name or tag.identifier or tag.hashed_adv_key_b64[:12]
        if report is not None:
            out.append(report_to_json(name, report))

    out.sort(key=lambda r: r["timestamp"], reverse=True)
    _cache.update({"fetched_at": now, "locations": out})
    return _cache


@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
