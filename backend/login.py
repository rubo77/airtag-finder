"""One-time interactive Apple login for the AirTag Finder backend.

Run this once on the machine that will host the backend:

    python login.py

It asks for your Apple ID credentials and (usually) a 2FA code, then stores
the session in account.json so server.py can reuse it without interaction.
Anisette data is generated locally via the bundled `anisette` package —
no Mac and no public anisette server needed.

Credentials can also come from a .env file next to this script:

    APPLE_ID_EMAIL=you@example.com
    APPLE_ID_PASSWORD=secret
"""

from __future__ import annotations

import getpass
import logging
import os
import sys
from pathlib import Path

from findmy import (
    AppleAccount,
    LocalAnisetteProvider,
    LoginState,
    SmsSecondFactorMethod,
    TrustedDeviceSecondFactorMethod,
)
from findmy.errors import InvalidCredentialsError

STORE_PATH = Path(__file__).with_name("account.json")
ANISETTE_LIBS_PATH = Path(__file__).with_name("ani_libs.bin")
ENV_PATH = Path(__file__).with_name(".env")

logging.basicConfig(level=logging.WARNING)


def load_env(path: Path) -> None:
    """Load KEY=VALUE pairs from a .env file into os.environ (no expansion)."""
    if not path.exists():
        return
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        os.environ.setdefault(key.strip(), value.strip())


def _explain_login_error(exc: Exception) -> str:
    """Translate a login failure into an actionable message."""
    msg = str(exc)
    if isinstance(exc, InvalidCredentialsError):
        if "cannot be completed" in msg or "-80045" in msg:
            return (
                "Apple lehnt den Login-Request ab (ec -80045). Das passiert VOR der\n"
                "Passwortprüfung - 2FA wird also nicht erreicht.\n"
                "Mögliche Ursachen:\n"
                "  - Temporäre Apple-Störung/Filterwelle (in der Vergangenheit hat\n"
                "    sich das nach Stunden bis Tagen von selbst gelöst)\n"
                "  - Apple-ID ist gesperrt -> teste https://appleid.apple.com\n"
                "  - Künstliches Anisette-Device wird abgelehnt\n"
                "Empfehlung: appleid.apple.com im Browser prüfen; später erneut\n"
                "probieren."
            )
        return (
            f"Apple hat die Anmeldung abgelehnt: {msg}\n"
            "Prüfe E-Mail/Passwort und ob die Apple-ID bei appleid.apple.com\n"
            "einwandfrei funktioniert."
        )
    return f"Unerwarteter Fehler beim Login: {type(exc).__name__}: {msg}"


def _do_2fa(acc: AppleAccount) -> int:
    """Interactive second-factor flow. Returns process exit code."""
    methods = acc.get_2fa_methods()
    if not methods:
        print("Keine 2FA-Methoden verfügbar - Account ohne 2FA?")
        return 1
    for i, method in enumerate(methods):
        if isinstance(method, TrustedDeviceSecondFactorMethod):
            print(f"{i} - Trusted Device (Code erscheint auf deinem Apple-Gerät)")
        elif isinstance(method, SmsSecondFactorMethod):
            print(f"{i} - SMS an {method.phone_number}")
    try:
        ind = int(input("Methode? > "))
        method = methods[ind]
    except (ValueError, IndexError):
        print("Ungültige Auswahl.")
        return 1

    method.request()
    code = input("Code? > ")
    try:
        method.submit(code)
    except InvalidCredentialsError:
        print("2FA-Code wurde abgelehnt - falsch oder abgelaufen.")
        return 1
    return 0


def main() -> int:
    load_env(ENV_PATH)

    if STORE_PATH.exists():
        print(f"{STORE_PATH} existiert bereits - zum Neu-Einloggen erst löschen.")
        return 1

    ani = LocalAnisetteProvider(libs_path=str(ANISETTE_LIBS_PATH))
    acc = AppleAccount(ani)

    email = os.environ.get("APPLE_ID_EMAIL") or input("Apple-ID E-Mail: ")
    password = os.environ.get("APPLE_ID_PASSWORD") or getpass.getpass("Passwort: ")

    try:
        state = acc.login(email, password)
    except Exception as exc:
        print(_explain_login_error(exc))
        return 1

    if state == LoginState.REQUIRE_2FA:
        rc = _do_2fa(acc)
        if rc != 0:
            return rc
    elif state != LoginState.LOGGED_IN:
        print(f"Unerwarteter Login-Zustand: {state}")
        return 1

    acc.to_json(str(STORE_PATH))
    print(f"Eingeloggt als {acc.account_name} - Session in {STORE_PATH} gespeichert.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
