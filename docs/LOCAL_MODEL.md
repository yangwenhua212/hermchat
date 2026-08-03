# 本地运行时 / 本地模型（Phase B）

> 状态：**Step 13 已落地主干**（编排 + MediaPipe 推理路径 + 按需下载）。  
> 产品总述见 [PRODUCT.md](PRODUCT.md)。品牌表述 **B**：通用口袋客户端，不排他绑定 Hermes。

## 目标

第三种模式：**本地运行时**——推理与轻量编排在手机内执行。

## 当前实现

| 能力 | 状态 |
|------|------|
| Agent 类型「本地」 | ✅ 与 WebSocket / HTTP 并列切换 |
| `LocalRuntimeClient` | ✅ 统一流式出口 |
| 本机工具（日历/闹钟） | ✅ 经现有确认卡 |
| 模型按需下载 | ✅ **不打进 APK**；默认 **Qwen2.5 0.5B（免 HF 令牌）**；可选 TinyLlama / Gemma |
| 暂停 / 断点续传 | ✅ 保留 `.part` + HTTP `Range`；资源库与配置页可「暂停 / 继续」 |
| 资源库管理（已装列表、删除、HF 搜索 litert `.task`） | ✅ 设置「资源库」或顶栏 Agent 下拉 |
| 选用到 Agent | ✅ 资源库「选用到当前」；配置页目录选择（LOCAL=`model`，GATEWAY=`localModelId`） |
| 内存门槛 | ✅ 不足则拒绝加载，不硬崩（约 ≥3GB 总内存） |
| MediaPipe 本机推理 | ✅ 模型就绪且内存足够时加载 |
| 未下载模型时主操作 | ✅ 「下载模型」按钮 |

## 内置目录

| 模型 | 约大小 | HF 令牌 |
|------|--------|---------|
| **Qwen2.5 0.5B**（默认） | ~547MB | **不需要** |
| TinyLlama 1.1B | ~1.1GB | **不需要** |
| Gemma 3 270M / 1B | ~318 / 550MB | **需要**（网页 Agree + Token） |

## 使用

1. 添加 Agent → 选 **本地**（或在资源库管理权重后再选用）  
2. 资源库选 **Qwen2.5 0.5B（免令牌）** → 直接点 **下载**（不必填令牌）  
3. 若要 Gemma：填 **Hugging Face 令牌**，并在模型页 Agree  
   - https://huggingface.co/settings/tokens  
4. 下载中可 **暂停**；再点 **继续** 从断点接着拉  
5. **测试** → 命名 → 聊天；无模型也可先测「编排已就绪」，日程/闹钟仍可用  

### 下载为什么慢？

- 权重约 **500MB～1GB+**，单连接从 Hugging Face 拉；国内到 HF CDN 常偏慢  
- Wi‑Fi 明显快于蜂窝；已支持暂停续传  

### 下完手机能不能用？

**能，但有门槛：** 文件为 MediaPipe `.task`；机型建议总内存 ≥ **约 3GB**；Agent 选 **本地** 或 **端侧网关**（网关作本地兜底）并「选用」该模型。RAM 不够时拒绝加载、不硬崩。任意 HF `.task` 不保证都能被 MediaPipe 加载。

四档总览见 [PRODUCT.md](PRODUCT.md)；④ 本地兜底见 [REMOTE_BRAIN_LOCAL_TOOLS.md](REMOTE_BRAIN_LOCAL_TOOLS.md)。

## 技术

| 层 | 方案 |
|----|------|
| 推理 | MediaPipe `tasks-genai` + 默认 Qwen2.5 0.5B Q8 `.task`（可选 TinyLlama / Gemma） |
| 编排 | `LocalRuntimeClient` + `LocalToolPlanner` |
| 分发 | `LocalModelStore` → `filesDir/local_llm/`（资源库可见路径） |
| 开源搜索 | `HfModelSearch` → Hugging Face `litert-community` 的 `.task` |

## 非目标（仍后置）

- 完整桌面 Hermes 插件生态  
- 替代 eraherm-memory  
- 强制某一品牌模型  

## 验收

- [x] 可保存/切换本地配置  
- [x] 本地模式下工具确认流可用  
- [x] 未下载时有「下载模型」主操作  
- [x] 默认目录免令牌可直下（Qwen2.5 0.5B）  
- [ ] 真机下载默认模型后无网完成一轮问答  
