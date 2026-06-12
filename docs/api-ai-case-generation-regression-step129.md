# 接口自动化 AI 生成链路回归记录（第 129 步）

本文档记录第 125-129 步对 `ApiAiCaseGenerationService` 的第一批测试保护。阶段目标是先保护 AI 生成 SSE 入口行为，不改接口路径、不改响应结构、不真实调用外部 AI。

## 测试边界

- 测试类：`ApiAiCaseGenerationServiceTests`
- 测试层级：service 级集成测试，继承 `IntegrationTestSupport`
- 调用入口：直接调用 `ApiAiCaseGenerationService.streamGenerate(...)`
- SSE 捕获：使用 `StringWriter` 捕获 `event:` / `data:` 输出，再解析为事件列表断言顺序和关键字段
- Provider 数据：通过 `AiProviderConnectionMapper` 插入测试连接，`AiSecretCodec.encrypt(...)` 准备 API Key 密文
- 外部 AI：使用 `@MockitoBean AiProviderClient` mock，不触发真实网络请求

## 已覆盖事件

- 成功链路：`started -> item_outline -> item_completed -> completed`
- provider 前置失败：`failed`
- 大纲生成失败：`started -> item_failed -> completed`
- 多条大纲 NDJSON：多个 `item_outline` 按目标顺序输出
- 大纲返回内容兜底解析：当流式回调未产出事件时，通过最终 content 解析并补发 `item_outline`
- 详情单 case JSON fallback：详情响应无 `case` 包裹时仍可解析为 `item_completed`

## 已覆盖场景

- providerConnectionId 缺失
- provider 不存在或无权访问
- provider 已停用
- AI outline 调用抛异常
- outline NDJSON 多行解析
- detail `{"case": ...}` 包裹解析
- detail 单 case 裸 JSON 解析

## 暂未覆盖范围

- 真实外部 AI 网络调用
- Controller 层长连接输出和前端 `EventSource`
- 长连接中断、客户端断开、writer 写入异常
- 真实模型返回 Markdown / 代码块 / 非法 JSON 的完整容错矩阵
- Prompt 文案快照测试
- `ApiAiCaseGenerationService` 内部 private 解析方法的直接单元测试

## 后续建议

当前测试已经覆盖进入拆分前最关键的 SSE 成功事件、前置失败、AI 调用失败和主要 JSON 解析兼容路径。下一阶段可以考虑低风险拆分：

1. 先抽 `ApiAiCaseGenerationPromptSupport`，只迁移 prompt 构建纯逻辑。
2. 再抽 `ApiAiCaseGenerationParsingSupport`，只迁移 NDJSON / JSON 解析和 fallback 解析纯逻辑。
3. 暂时不拆 `streamGenerate(...)` 主编排和事件写出，避免改变 SSE 事件顺序。

## 第 131-134 步拆分后状态

- `ApiAiCaseGenerationService`：继续作为 `streamGenerate(...)` 主编排入口，保留 workspace/provider 解析、slot 构建、生成流程、draft/outline normalize 和 SSE 事件顺序控制。
- `ApiAiCaseGenerationPromptSupport`：承接 outline、单用例详情、批量、流式批量 prompt 构建，以及 prompt payload JSON 序列化。
- `ApiAiCaseGenerationParsingSupport`：承接单行 NDJSON、outline NDJSON、`case` 包裹、`cases` 数组、裸 case JSON fallback 解析。
- `ApiAiCaseGenerationEventSupport`：承接 SSE `event/data` 写出、flush、unchecked 写出包装和换行定位辅助。

本阶段没有强行迁移 `emitDraftLine`、`emitOutlineLine`、`emitRemainingDrafts`、`emitRemainingOutlines` 的完整主体，因为这些方法会直接调用 `normalizeDraft` / `normalizeOutline` 并维护 `completedIds` / `outlines` 状态。继续迁移会把用例归一化和主流程状态管理拖入 event support，职责边界反而变糊。当前只抽出事件写出和换行辅助，保持 SSE 事件名称、顺序、data 字段和 flush 行为不变。

拆分后暂未发现 support 之间反向依赖：Prompt / Parsing / Event support 均只依赖 `ApiAiCaseGenerationService` 的数据 record 和通用基础设施，不互相调用。
