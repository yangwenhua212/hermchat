# HxMV 内容生产接入

HxSync 的「HxMV 内容生产」页：把 HxMV（自主内容生产闭环 Agent）当远端服务或手机本机服务来用。
入口：设置 → **HxMV 内容生产**。

## 两种跑法（同一套接口，只有地址不同）

| 模式 | 地址 | 说明 |
|---|---|---|
| 远端 | `https://hxmv.eraherm.com`（默认） | 服务器出片；手机只当遥控器 |
| 手机本机 | `http://127.0.0.1:8668` | 手机里用 Termux 跑 HxMV；页面上会自动发现并提示「切换到手机本机」 |

## 手机本机怎么装（Termux）

1. 装 Termux（F-Droid 或 GitHub Releases；小米应用商店没有）。
2. 页面右下「复制安装命令」→ 粘到 Termux 执行（脚本：装 python/ffmpeg、取代码、自检、配 Key、起面板）。
3. 回到页面点「切换到手机本机」。

装好后建议：`termux-wake-lock` 保住后台；设置 → 应用 → Termux → 省电策略 → 无限制。
产物默认写在 `~/storage/shared/HxMV/`（相册可见）。

## 接口（客户端用）

见 HxMV 仓库 `docs/CLIENT.md`。本页用到：

- `GET /api/health` — 发现实例 + 能力自检（版本/ffmpeg/编码器/各后端 Key 状态/项目）
- `POST /api/run {goal,provider,project}` — 提交 → `run_id`
- `GET /api/stream?run_id=&token=` — SSE 实时事件（任务 · 分数 · 实测指标）
- `GET /api/artifact?run_id=&name=` — 取成品

认证：`X-Hxmv-Token` 头（SSE 用 `?token=`）。令牌存在加密 prefs（`hermchat_hxmv`）。

服务端可选配 `HXMV_NOTIFY_URL`，跑完主动推一份含成品链接的负载（客户端不在线也不丢成品）。

## 成品

- **播放**：下载到应用缓存 → 交给系统播放器（FileProvider，`app/src/main/res/xml/hxmv_paths.xml`）。
- **保存**：写入系统相册 `Movies/HxSync`（Android 10+ 走 MediaStore，免存储权限）。

## 排查

| 现象 | 处理 |
|---|---|
| 状态显示「连不上 HxMV」 | 检查地址/令牌；远端确认 `hxmv.eraherm.com` 可访问 |
| 「令牌不对或未设置」 | 服务端设了 `HXMV_WEB_TOKEN` 时，页面「服务」里要填同值 |
| 提交后一直「生产中」但无进度 | 服务端可能重启过（run 记录仍在磁盘）；页面重开即可看到历史 |
| 播放没反应 | 系统里没有能播 mp4 的 App；改用「保存」到相册播放 |
