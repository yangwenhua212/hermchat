#!/usr/bin/env python3
"""Minimal HxSync WebSocket bridge for local demo (BRIDGE_PROTOCOL simple frames).

v0.2.0 Agent Bridge 演示：远程 Agent 调用手机工具。
- 收到「提醒/闹钟/开会」类话术 → 下发 tool_call 帧 → 手机弹确认卡
- 用户点允许 → 手机执行 → 回传 tool_result → Agent 把结果转述给用户

运行: pip install websockets && python3 demo_bridge.py
手机: 同一 Wi-Fi，App 配置 ws://<本机IP>:8765/ws（类型选 WebSocket）
"""

from __future__ import annotations

import argparse
import asyncio
import json
import re
import socket
import time
import uuid
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


def plan_tool_call(content: str) -> dict | None:
    """把自然语言话术映射成手机工具调用（demo 级规则）。

    返回 None 表示纯聊天，不需要动手机工具。
    """
    if any(k in content for k in ("提醒", "闹钟", "叫我", "喊我")):
        m = re.search(r"(\d+)\s*分钟", content)
        minutes = int(m.group(1)) if m else 5
        # 提取「提醒我 X」/「X 的时候叫我」后面的话作为提醒文案
        msg = re.sub(r"(提醒|麻烦|请|帮我|一下|好|，|,)", "", content).strip() or "时间到了"
        trigger_ms = int(time.time() * 1000) + minutes * 60_000
        return {
            "name": "alarm.create",
            "need_confirm": True,
            "arguments": {
                "message": msg,
                "triggerMs": str(trigger_ms),
            },
        }
    if any(k in content for k in ("开会", "会议", "日程", "安排", "约个")) or re.search(
        r"(开|约|排).{0,8}会", content,
    ):
        tomorrow = int(time.time() // 86_400 + 1) * 86_400
        begin_ms = (tomorrow + 10 * 3600) * 1000
        end_ms = (tomorrow + 11 * 3600) * 1000
        return {
            "name": "calendar.create",
            "need_confirm": True,
            "arguments": {
                "title": content.strip() or "日程",
                "beginMs": str(begin_ms),
                "endMs": str(end_ms),
                "description": "来自 demo bridge 的日程",
            },
        }
    return None


async def handle(websocket: Any) -> None:
    # tool_call_id -> Future（收到对应 tool_result 时 resolve）
    pending: dict[str, asyncio.Future] = {}
    # 同一连接内串行处理话术，避免 token 流交错
    process_lock = asyncio.Lock()

    async def process(msg: dict[str, Any]) -> None:
        async with process_lock:
            msg_id = str(msg.get("id", ""))
            content = str(msg.get("content", ""))
            attachment = msg.get("attachment")

            if isinstance(attachment, dict) and attachment.get("data"):
                mime = str(attachment.get("mime") or "image/*")
                await stream_reply(websocket, msg_id, f"已收到附件（{mime}）：{content or '请描述图片'}")
                return

            plan = plan_tool_call(content)
            if plan is None:
                if content in {"你好", "您好", "hi", "hello"}:
                    reply = "你好！我是电脑上的 demo Agent，可以对手机说「提醒我 10 分钟后喝水」试试。"
                else:
                    reply = f"好的：{content}"
                await stream_reply(websocket, msg_id, reply)
                return

            # ── 需要调用手机工具：下发 tool_call 帧，等确认与结果 ──
            call_id = uuid.uuid4().hex
            fut: asyncio.Future = asyncio.get_running_loop().create_future()
            pending[call_id] = fut
            print(f"  → 下发工具请求 {plan['name']} (id={call_id[:8]})，等待手机确认…")
            await websocket.send(
                json.dumps(
                    {
                        "type": "tool_call",
                        "id": call_id,
                        "name": plan["name"],
                        "need_confirm": plan.get("need_confirm", True),
                        "arguments": plan.get("arguments", {}),
                    },
                    ensure_ascii=False,
                ),
            )
            try:
                result = await asyncio.wait_for(fut, timeout=45)
                ok = bool(result.get("ok"))
                content_out = str(result.get("content", ""))
                if ok:
                    reply = f"✅ 手机已执行：{content_out}"
                else:
                    reply = f"⚠️ 手机端未执行：{content_out}"
            except asyncio.TimeoutError:
                pending.pop(call_id, None)
                reply = "⚠️ 手机 45 秒内没有回应（App 没在线？）"
            await stream_reply(websocket, msg_id, reply)

    async for raw in websocket:
        try:
            msg = json.loads(raw)
        except json.JSONDecodeError:
            await websocket.send(json.dumps({"type": "error", "message": "invalid json"}))
            continue

        msg_type = msg.get("type")
        if msg_type == "tool_result":
            # 手机回传的工具执行结果 → 唤醒等待中的 process
            call_id = str(msg.get("id", ""))
            fut = pending.pop(call_id, None)
            if fut is not None and not fut.done():
                fut.set_result(msg)
            continue
        if msg_type == "chat":
            # 并发调度：不阻塞 async for，才能收到后续 tool_result
            asyncio.create_task(process(msg))
            continue
        await websocket.send(
            json.dumps({"type": "error", "id": str(msg.get("id", "")), "message": f"unsupported type: {msg_type}"}),
        )


async def stream_reply(websocket: Any, msg_id: str, text: str) -> None:
    for ch in text:
        await websocket.send(
            json.dumps({"type": "token", "id": msg_id, "content": ch}, ensure_ascii=False),
        )
        await asyncio.sleep(0.02)
    await websocket.send(json.dumps({"type": "done", "id": msg_id}))


async def main() -> None:
    parser = argparse.ArgumentParser(description="HxSync demo WebSocket bridge (Agent Bridge v0.2.0)")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()

    ip = lan_ip()
    print("HxSync demo bridge running (Agent Bridge v0.2.0)")
    print(f"  phone (same Wi-Fi): ws://{ip}:{args.port}/ws")
    print(f"  emulator:           ws://10.0.2.2:{args.port}/ws")
    print()
    print("  试：给手机发「提醒我 10 分钟后喝水」→ 手机弹确认卡 → 允许 → 设闹钟")
    print("  试：「明天上午开会」→ 确认后写入系统日历")
    print("Press Ctrl+C to stop.")

    async with serve(handle, args.host, args.port, ping_interval=20, ping_timeout=20):
        await asyncio.Future()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nstopped")
