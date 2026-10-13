"""Periodically probe Apple GSA 'init' and report the returned Status ec.

Used to detect when Apple's temporary -80045 rejection window ends.
Logs every attempt; sends an ntfy push when the status changes.
"""

import asyncio
import plistlib
import sys
import time
import warnings
from pathlib import Path

warnings.filterwarnings("ignore")

import srp  # noqa: E402
from curl_cffi.requests import AsyncSession  # noqa: E402
from findmy import LocalAnisetteProvider  # noqa: E402
from findmy.reports.account import AsyncAppleAccount  # noqa: E402

BASE = Path(__file__).resolve().parent
NTFY_TOPIC = "https://ntfy.sh/upfTOPVY5qzE7f"
LAST_EC = BASE / ".last_ec"

EMAIL = "probe-nonexistent@example.com"


async def probe_once() -> int | None:
    ani = LocalAnisetteProvider(libs_path=str(BASE / "ani_libs.bin"))
    a = AsyncAppleAccount(ani)
    cpd = await a._anisette.get_cpd(a._uid, a._devid)
    usr = srp.User(EMAIL, b"", hash_alg=srp.SHA256, ng_type=srp.NG_2048)
    _, a2k = usr.start_authentication()
    body = {
        "Header": {"Version": "1.0.1"},
        "Request": {
            "cpd": cpd, "A2k": a2k, "u": EMAIL,
            "ps": ["s2k", "s2k_fo"], "o": "init",
        },
    }
    headers = {
        "Content-Type": "text/x-xml-plist", "Accept": "*/*",
        "User-Agent": "akd/1.0 CFNetwork/978.0.7 Darwin/18.7.0",
        "X-MMe-Client-Info": a._anisette.client,
    }
    async with AsyncSession(impersonate="safari18_0", verify=False) as s:
        r = await s.post(
            "https://gsa.apple.com/grandslam/GsService2",
            data=plistlib.dumps(body), headers=headers, timeout=30,
        )
        st = plistlib.loads(r.content).get("Response", {}).get("Status", {})
        return st.get("ec")


def notify(msg: str) -> None:
    import urllib.request
    try:
        urllib.request.urlopen(NTFY_TOPIC, data=msg.encode(), timeout=10)
    except Exception:
        pass


async def main() -> int:
    last = int(LAST_EC.read_text().strip()) if LAST_EC.exists() else None
    while True:
        try:
            ec = await probe_once()
        except Exception as exc:
            print(f"{time.strftime('%H:%M:%S')} probe failed: {exc}", flush=True)
            ec = None
        print(f"{time.strftime('%H:%M:%S')} ec={ec} (last={last})", flush=True)
        if ec != last and ec not in (-80045, None):
            notify(f"GSA init Status geändert: ec={ec} - Login könnte jetzt klappen!")
        if ec != last and ec is not None:
            LAST_EC.write_text(str(ec))
            last = ec
        await asyncio.sleep(600)


if __name__ == "__main__":
    sys.exit(asyncio.run(main()) or 0)
