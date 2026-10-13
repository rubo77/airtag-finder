"""One-time interactive Apple login for the AirTag Finder backend.

Run this once on the machine that will host the backend:

    python login.py

It asks for your Apple ID credentials and (usually) a 2FA code, then stores
the session in account.json so server.py can reuse it without interaction.
Anisette data is generated locally via the bundled `anisette` package —
no Mac and no public anisette server needed.
"""

from __future__ import annotations

import getpass
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

STORE_PATH = Path(__file__).with_name("account.json")
ANISETTE_LIBS_PATH = Path(__file__).with_name("ani_libs.bin")
ENV_PATH = Path(__file__).with_name(".env")


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


def main() -> int:
    load_env(ENV_PATH)
    if STORE_PATH.exists():
        print(f"{STORE_PATH} exists already - delete it first to re-login.")
        return 1

    ani = LocalAnisetteProvider(libs_path=str(ANISETTE_LIBS_PATH))
    acc = AppleAccount(ani)

    email = os.environ.get("APPLE_ID_EMAIL") or input("Apple-ID E-Mail: ")
    password = os.environ.get("APPLE_ID_PASSWORD") or getpass.getpass("Passwort: ")

    state = acc.login(email, password)

    if state == LoginState.REQUIRE_2FA:
        methods = acc.get_2fa_methods()
        for i, method in enumerate(methods):
            if isinstance(method, TrustedDeviceSecondFactorMethod):
                print(f"{i} - Trusted Device")
            elif isinstance(method, SmsSecondFactorMethod):
                print(f"{i} - SMS ({method.phone_number})")

        ind = int(input("Methode? > "))
        method = methods[ind]
        method.request()
        code = input("Code? > ")
        method.submit(code)
    elif state != LoginState.LOGGED_IN:
        print(f"Login fehlgeschlagen: {state}")
        return 1

    acc.to_json(str(STORE_PATH))
    print(f"Eingeloggt als {acc.account_name} - Session gespeichert in {STORE_PATH}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
