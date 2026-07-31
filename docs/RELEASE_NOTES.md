# HxSync 0.1.5

开源内测包：把你自己的 AI Agent 装进口袋。

## 下载

安装包：`HxSync-0.1.5.apk`（debug 签名，可直接安装；需允许未知来源）。

## 本版包含

- 三种模式：远程 WebSocket / 直连 API / 本地运行时
- 多 Agent 切换、扫码与粘贴配置
- 日历、闹钟（确认后执行）
- 离线唤醒 + 说指令自动发送
- Agent 配置加密存储、WebSocket 断线重连

## 五分钟上手

1. 安装本 APK  
2. 电脑与手机同一 Wi‑Fi，运行：

```bash
pip install websockets
python scripts/demo_bridge.py
```

3. App 里选 WebSocket，填终端打印的 `ws://局域网IP:8765/ws` → 测试 → 聊天  

更多：[README](https://github.com/yangwenhua212/hermchat#readme) · [CONNECT_AGENTS](https://github.com/yangwenhua212/hermchat/blob/main/docs/CONNECT_AGENTS.md)

## 许可

AGPL-3.0。商用见仓库 `COMMERCIAL.md`。

不上架应用商店；仅 GitHub 开源分发。
