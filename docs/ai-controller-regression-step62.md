# AI 模块 Controller 回归记录（第六十二步）

本记录用于收尾第 57 到 61 步新增的 AI 模块 Controller 集成测试。当前阶段不改接口路径、不改响应结构、不改数据库、不动前端，只确认已拆分后的 AI 模块主链路具备基础回归保护。

## 测试覆盖

| 测试类 | 覆盖范围 |
| --- | --- |
| `AiCaseControllerIntegrationTests` | AI 供应商连接、角色配置、需求文档导入、素材上传下载删除、同步生成、同步评审、图片能力校验 |
| `AiGenerationTaskControllerIntegrationTests` | AI 生成任务创建、查询、更新、取消、重试、删除、事件列表、`listAfter`、终态 SSE smoke |
| `AiProviderClientTests` | OpenAI compatible chat/responses 请求构建、图片输入结构、responses 输出提取、stream delta 提取、timeout fallback/override |

## 已覆盖接口清单

供应商连接与模型能力：
- `GET /api/cases/ai/providers`
- `POST /api/cases/ai/providers`
- `PUT /api/cases/ai/providers/{id}`
- `GET /api/cases/ai/providers/{id}/secret`
- `POST /api/cases/ai/providers/{id}/test`
- `POST /api/cases/ai/providers/{id}/fetch-models`
- `GET /api/cases/ai/providers/{id}/models`
- `POST /api/cases/ai/providers/{id}/models/probe`

AI 角色配置：
- `GET /api/cases/ai/config`
- `POST /api/cases/ai/config`
- `PUT /api/cases/ai/config/{id}`
- `GET /api/cases/ai/config/{id}/secret`
- `POST /api/cases/ai/config/test`

需求文档和素材：
- `POST /api/cases/ai/requirement-import`
- `POST /api/cases/ai/assets`
- `GET /api/cases/ai/assets/{id}/download`
- `DELETE /api/cases/ai/assets/{id}`

同步生成和评审：
- `POST /api/cases/ai/generate`
- `POST /api/cases/ai/review`
- `POST /api/cases/ai/tasks/image-support/validate`

AI 生成任务：
- `POST /api/cases/ai/tasks`
- `GET /api/cases/ai/tasks`
- `GET /api/cases/ai/tasks/{taskId}`
- `PUT /api/cases/ai/tasks/{taskId}`
- `POST /api/cases/ai/tasks/{taskId}/cancel`
- `POST /api/cases/ai/tasks/{taskId}/retry`
- `DELETE /api/cases/ai/tasks/{taskId}`
- `GET /api/cases/ai/tasks/{taskId}/events/stream`（终态 smoke）

## Mock 策略

- `AiProviderClient` 使用 `@MockitoBean`，避免真实外网模型请求。
- provider test、fetch models、probe model、config test、generate、review 均通过 mock 返回最小有效结果来保护 Controller 响应结构和服务编排行为。
- `AiGenerationTaskRunner` 使用 `@MockitoBean`，避免创建/重试任务后进入异步执行链路。
- 素材上传使用测试隔离目录 `app.ai.asset-storage-root=./target/test-ai-assets/ai-case-controller`，避免污染本地 `data/ai-assets`。
- 配置唯一约束通过测试内切换独立用户规避执行顺序影响，避免多个测试争用同一用户的 `CASE_GENERATOR` / `CASE_REVIEWER` 配置。

## 已验证行为

- 供应商连接 CRUD、secret 解密读取、连接测试成功/失败状态更新、模型拉取缓存、模型探测能力写入。
- 角色配置 CRUD、secret 读取、绑定 provider 后的 config test。
- txt 需求文档导入、图片素材上传、下载、删除主链路。
- 同步生成入口返回生成用例列表、覆盖摘要、缺口、warnings、rawContent 和 ignoredImages 字段。
- 同步评审入口返回 result、summary、issues、suggestions、caseDecisions、supplementCases、unresolvedCoverageGaps、rawContent 和 structured 字段。
- 图片能力校验在支持图片时通过，不支持图片时返回当前失败语义。
- 生成任务创建、列表、详情、更新、取消、失败任务重试、删除后查询失败。
- 任务事件在详情中按 seq 正序返回，`AiGenerationTaskEventService.listAfter(taskId, seq)` 只返回指定 seq 之后的事件。
- 终态任务的 SSE stream 可以输出已有事件并结束，不阻塞测试。

## 未覆盖范围

- 真实模型网络调用和真实 provider API 协议端到端。
- SSE 长连接实时增量推送过程中的并发写入、心跳持续输出和客户端断开场景。
- 异步任务完整执行链路，包括真实 `AiGenerationTaskRunner.runTask` 调用 `executeTask` 后的生成、评审和落库全过程。
- `streamGenerateCases` / `streamReviewGeneratedCases` 的逐行解析和流式 UI 更新细节。
- docx 需求文档图片提取复杂样例。
- provider preview models、bootstrap-from-legacy、task cancel/retry 在真实执行中的竞态边界。

## 后续测试批次建议

1. 补 AI 生成任务异步执行 service 级集成测试，使用 mock `AiProviderClient` 驱动 `executeTask` 完成 `COMPLETE` 模式生成和评审落库。
2. 补流式生成/评审 service 级测试，重点保护 `AiResponseParsingSupport` 的 NDJSON、fallback、补充用例和错误吞吐行为。
3. 补 provider preview models 和 bootstrap-from-legacy 的窄范围 Controller 回归。
4. 补 docx 导入图片提取样例，验证素材实体、物理文件和响应字段。
5. 如需要验证真实协议适配，单独使用本地 `HttpServer` 测 `AiProviderClient`，不要依赖真实外网。

## 验证命令

```text
.\mvnw.cmd "-Dtest=AiCaseControllerIntegrationTests,AiGenerationTaskControllerIntegrationTests,AiProviderClientTests" test
.\mvnw.cmd -DskipTests compile
git diff --check
```
