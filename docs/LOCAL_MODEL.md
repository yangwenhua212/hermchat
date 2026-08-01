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
| 模型按需下载（默认 Gemma 3 **270M** Q8 `.task`；可选 1B） | ✅ **不打进 APK**；配置页 / **资源库** 下载 |
| 资源库管理（已装列表、删除、HF 搜索 litert `.task`） | ✅ 设置「资源库」或顶栏 Agent 下拉 |
| 选用到 Agent | ✅ 资源库「选用到当前」；配置页目录选择（LOCAL=`model`，GATEWAY=`localModelId`） |
| 内存门槛 | ✅ 不足则拒绝加载，不硬崩（270M 门槛较低） |
| MediaPipe 本机推理 | ✅ 模型就绪且内存足够时加载 |
| 未下载模型时主操作 | ✅ 「下载模型」按钮 |

## 使用

1. 添加 Agent → 选 **本地**  
2. 填 **下载令牌**（Hugging Face，需接受 Gemma 许可）  
3. **下载模型**（默认约 300MB 的 270M；仅首次）→ **测试** → 命名 → 聊天  
4. 无模型也可先测「编排已就绪」，日程/闹钟仍可用  

## 技术

| 层 | 方案 |
|----|------|
| 推理 | MediaPipe `tasks-genai` + 默认 Gemma 3 **270M** Q8（可选手动填 `gemma3-1b-it-int4`） |
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
- [ ] 真机下载 Gemma 后无网完成一轮问答（依赖机型与令牌）
