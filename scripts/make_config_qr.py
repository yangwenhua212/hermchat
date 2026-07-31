#!/usr/bin/env python3
"""Generate HxSync agent config JSON / QR for phone import."""

from __future__ import annotations

import argparse
import json
import sys
import urllib.parse
import webbrowser
from pathlib import Path


def build_payload(endpoint: str, kind: str | None, name: str | None) -> dict:
    inferred = "WEBSOCKET"
    lower = endpoint.lower()
    if lower.startswith("http://") or lower.startswith("https://"):
        inferred = "HTTP_COMPAT"
    payload: dict = {
        "v": 1,
        "kind": (kind or inferred).upper(),
        "endpoint": endpoint.strip(),
    }
    if name:
        payload["name"] = name
    return payload


def main() -> int:
    parser = argparse.ArgumentParser(description="Make HxSync config JSON / QR")
    parser.add_argument("--endpoint", required=True, help="ws:// or http(s):// URL")
    parser.add_argument("--kind", default=None, help="WEBSOCKET | HTTP_COMPAT | CUSTOM")
    parser.add_argument("--name", default=None, help="Display name")
    parser.add_argument(
        "--out",
        default="config-qr.png",
        help="PNG path when qrcode package is available",
    )
    parser.add_argument(
        "--no-open",
        action="store_true",
        help="Do not open browser QR page",
    )
    args = parser.parse_args()

    payload = build_payload(args.endpoint, args.kind, args.name)
    text = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    print(text)

    wrote_png = False
    try:
        import qrcode  # type: ignore

        img = qrcode.make(text)
        out = Path(args.out)
        img.save(out)
        print(f"Wrote {out.resolve()}", file=sys.stderr)
        wrote_png = True
    except Exception as exc:  # noqa: BLE001
        print(f"Local QR skipped ({exc})", file=sys.stderr)

    if not args.no_open and not wrote_png:
        url = "https://api.qrserver.com/v1/create-qr-code/?size=280x280&data=" + urllib.parse.quote(
            text
        )
        print(f"Opening {url}", file=sys.stderr)
        webbrowser.open(url)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
