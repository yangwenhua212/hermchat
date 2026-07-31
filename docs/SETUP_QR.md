# 电脑出配置码（一页）

手机在 Agent 配置页点 **扫码导入** 或 **粘贴配置**。电脑这边只要生成一段配置文本（或对应二维码）。

本地演示可先跑 `python scripts/demo_bridge.py`，再用下面脚本把打印出的地址做成二维码。五分钟上手见 [README.md](../README.md#五分钟上手真机--演示-bridge)。

## 推荐 JSON

把下面里的地址改成你电脑的局域网 IP 与端口：

```json
{"v":1,"kind":"WEBSOCKET","endpoint":"ws://192.168.1.8:8765/ws","name":"家里的助手"}
```

| `kind` | 适用 |
|--------|------|
| `WEBSOCKET` | `ws://` / `wss://` |
| `HTTP_COMPAT` | OpenAI 兼容 `http(s)://` |
| `CUSTOM` | 其它 |

深链等价：

```
hxsync://agent?kind=WEBSOCKET&endpoint=ws%3A%2F%2F192.168.1.8%3A8765%2Fws&name=%E5%AE%B6%E9%87%8C
```

也可直接把纯地址做成二维码，例如 `ws://192.168.1.8:8765/ws`。

## 一键脚本

在仓库根目录：

```bash
# 生成 JSON，并尝试打开浏览器展示二维码
python scripts/make_config_qr.py --endpoint ws://192.168.1.8:8765/ws --name 家里的助手

# Windows PowerShell
pwsh scripts/make_config_qr.ps1 -Endpoint ws://192.168.1.8:8765/ws -Name 家里的助手
```

脚本会：

1. 打印可复制的 JSON（可直接「粘贴配置」）
2. 若本机有 `qrcode` Python 包则写出 `config-qr.png`
3. 否则打开在线二维码页（需联网；内网环境请改用粘贴或本地 `qrcode`）

安装本地出图（可选）：

```bash
pip install qrcode[pil]
```

## 查本机局域网 IP

- Windows：`ipconfig` → 看「无线局域网适配器」IPv4  
- macOS / Linux：`ip addr` 或 `ifconfig`

手机与电脑须同一 Wi‑Fi；模拟器访问宿主机用 `10.0.2.2`，一般不必扫码。

完整协议见 [BRIDGE_PROTOCOL.md](BRIDGE_PROTOCOL.md)。
