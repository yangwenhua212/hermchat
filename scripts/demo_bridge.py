#!/usr/bin/env python3
"""Minimal HxSync WebSocket bridge for local demo (BRIDGE_PROTOCOL simple frames)."""

from __future__ import annotations

import argparse
import asyncio
import json
import socket
from typing import Any

try:
    import websockets
    from websockets.asyncio.server import serve
except ImportError as exc:  # pragma: no cover
    raise SystemExit(
        "缺少依赖，请先执行: pip install websockets\n" + str(exc),
    ) from exc


def lan_ip() -> str:
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.connect(("8.8.8.8", 80))
        ip = sock.getsockname()[0]
        sock.close()
        return ip
    except OSError:
        return "127.0.0.1"


async def handle(websocket: Any) -> None:
    async for raw in websocket:
        try:
            msg = json.loads(raw)
        except json.JSONDecodeError:
            await websocket.send(json.dumps({"type": "error", "message": "invalid json"}))
            continue

        msg_type = msg.get("type")
        msg_id = msg.get("id", "")
        content = str(msg.get("content", ""))
        attachment = msg.get("attachment")

        if msg_type != "chat":
            await websocket.send(
                json.dumps({"type": "error", "id": msg_id, "message": f"unsupported type: {msg_type}"}),
            )
            continue

        if isinstance(attachment, dict) and attachment.get("data"):
            mime = str(attachment.get("mime") or "image/*")
            reply = f"已收到附件（{mime}）：{content or '请描述图片'}"
        elif content in {"你好", "您好", "hi", "hello"}:
            reply = "你好！"
        elif any(k in content for k in ("提醒", "开会", "日程")):
            reply = "好的，已记下。"
        else:
            reply = f"好的：{content}"
        for ch in reply:
            await websocket.send(
                json.dumps({"type": "token", "id": msg_id, "content": ch}, ensure_ascii=False),
            )
            await asyncio.sleep(0.032)
        await websocket.send(json.dumps({"type": "done", "id": msg_id}))


async def main() -> None:
    parser = argparse.ArgumentParser(description="HxSync demo WebSocket bridge")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()

    ip = lan_ip()
    print("HxSync demo bridge running")
    print(f"  phone (same Wi-Fi): ws://{ip}:{args.port}/ws")
    print(f"  emulator:           ws://10.0.2.2:{args.port}/ws")
    print("Press Ctrl+C to stop.")

    async with serve(handle, args.host, args.port, ping_interval=20, ping_timeout=20):
        await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nstopped")
