# AI 模块回归测试计划（第五十六步）

本计划用于保护第 50 到 55 步 AI 模块拆分后的行为边界。本步只梳理测试覆盖和落文档，不改接口路径、不改响应结构、不改数据库、不动前端。

## 现有测试覆盖

| 测试类 | 层级 | 当前覆盖 |
| --- | --- | --- |
| `AiProviderClientTests` | client/support 单元测试 | OpenAI compatible chat/responses endpoint 拼接、请求体构造、图片输入体构造、responses 输出提取、stream delta 提取、timeout fallback/override。 |
| `AiGenerationTaskEventServiceTests` | service 集成测试 | 任务事件追加、seq 自增、事件列表顺序。继承 `IntegrationTestSupport`，可复用 H2 + 当前用户上下文。 |

当前缺口：

- 还没有 `AiCaseController` 级集成测试。
- 还没有 `AiCaseService` 生成/评审入口 smoke 测试。
- 还没有供应商连接、角色配置、素材上传/下载/删除的接口级断言。
- 还没有覆盖拆分后的 `AiProviderDomainService`、`AiCaseConfigDomainService`、`AiRequirementAssetDomainService`、`AiPromptBuilderSupport`、`AiResponseParsingSupport` 的组合行为。

## 可复用测试基础

- `IntegrationTestSupport`
  - 使用 H2 初始化迁移脚本。
  - 默认设置当前用户 `id=11`，平台角色 `PLATFORM_ADMIN`。
  - 提供统一 `WORKSPACE_CODE = risk-ops`。

- `MockMvc` 集成测试模式
  - 可参考 `BugControllerIntegrationTests` 和 `ApiAutomationListControllerIntegrationTests`。
  - 适合覆盖 `AiCaseController` 的响应结构、HTTP 路径、multipart 上传/下载和 header 行为。

- 本地 `HttpServer` 模式
  - 可参考 `ApiExecutionEntryIntegrationTests`。
  - 如果要模拟 OpenAI compatible HTTP 响应，可使用本地 `/v1/chat/completions`、`/v1/models` 等端点。

- Mock 策略
  - 第一批 AI controller/service 测试建议优先 mock `AiProviderClient`，避免真实外网调用。
  - 供应商模型拉取如需验证 HTTP 协议适配，可另起 `AiProviderClient` 专项测试或使用本地 `HttpServer`。

## 第一批建议测试

### 1. 供应商连接接口回归

建议新增/扩展：`AiCaseControllerIntegrationTests`

覆盖接口：

- `POST /api/cases/ai/providers`
- `PUT /api/cases/ai/providers/{id}`
- `GET /api/cases/ai/providers`
- `GET /api/cases/ai/providers/{id}/secret`
- `POST /api/cases/ai/providers/{id}/test`
- `POST /api/cases/ai/providers/{id}/fetch-models`
- `GET /api/cases/ai/providers/{id}/models`

数据准备：

- 使用当前用户 `11`。
- 创建 provider request，协议建议用 `OPENAI_COMPATIBLE_CHAT`，baseUrl 用 `http://127.0.0.1:{port}/v1` 或 mock `AiProviderClient`。
- API Key 使用测试值，断言 secret 接口返回解密后的原值。

Mock 策略：

- 连接 CRUD、secret、列表不需要 mock 外部 HTTP。
- `testProvider` 和 `fetchProviderModels` 第一批建议 mock `AiProviderClient.testConnection` / `fetchModels`，验证 controller 响应和模型缓存写入语义。
- 如要验证真实协议适配，放到后续 `AiProviderClient` 测试，用本地 `HttpServer` 单独覆盖。

关键断言：

- 响应 `success=true`。
- 创建/更新后 provider 列表能看到连接。
- secret 不返回密文，返回原始 key。
- `fetchModels` 后 `getProviderModels` 能看到模型名。

### 2. 角色配置接口回归

建议新增/扩展：`AiCaseControllerIntegrationTests`

覆盖接口：

- `POST /api/cases/ai/config`
- `PUT /api/cases/ai/config/{id}`
- `GET /api/cases/ai/config`
- `GET /api/cases/ai/config/{id}/secret`
- `POST /api/cases/ai/config/test`

数据准备：

- 先创建 provider connection。
- 创建 `CASE_GENERATOR` 和 `CASE_REVIEWER` 两类配置。
- 使用 `providerConnectionId` 绑定供应商连接。

Mock 策略：

- `config/test` 建议 mock `AiProviderClient.testConnection`。
- 不直接查 mapper，优先通过 controller 的 config/secret 接口断言。

关键断言：

- `GET /config` 返回 generator/reviewer 字段形状不变。
- `providerConnectionId`、`model`、`supportsImageInput` 等关键字段存在。
- 创建重复角色配置仍失败。
- secret 返回当前绑定 provider 的明文 key。

### 3. 需求文档和素材接口回归

建议新增/扩展：`AiCaseControllerIntegrationTests`

覆盖接口：

- `POST /api/cases/ai/requirement-import`
- `POST /api/cases/ai/assets`
- `GET /api/cases/ai/assets/{id}/download`
- `DELETE /api/cases/ai/assets/{id}`

数据准备：

- 使用 `MockMultipartFile`。
- 文档导入第一批先覆盖 txt/md；docx 图片提取可作为第二批。
- 素材上传使用小 png/webp 测试字节。

Mock 策略：

- 不需要 mock `AiProviderClient`。
- 建议为测试配置独立 `app.ai.asset-storage-root` 临时目录，避免污染本地 `data/ai-assets`。

关键断言：

- txt/md 导入返回 title/content/contentLength。
- 图片上传返回 id、downloadUrl、contentType、fileSize。
- 下载返回 200、contentLength、contentType。
- 删除后再次下载返回错误。

### 4. AI 生成入口最小 smoke

建议新增：`AiCaseServiceIntegrationTests` 或放入第一版 `AiCaseControllerIntegrationTests`

覆盖入口：

- `AiCaseService.generateCases`
- 后续再覆盖 `AiCaseService.streamGenerateCases`

数据准备：

- 创建 provider connection。
- 创建 active `CASE_GENERATOR` 配置。
- 构造最小 `GenerateAiCasesRequest`。

Mock 策略：

- 第一批建议 mock `AiProviderClient.generate`，返回包含 1 条 case 的 `AiGeneratedCasesResult`。
- 暂不走真实 HTTP，不依赖外部模型。

关键断言：

- 返回 `workspaceCode/workspaceName/provider/model`。
- `actualGeneratedCount=1`。
- `generatedCases[0]` 字段完整。
- 无图片时 `ignoredImages=false`。

### 5. AI 评审入口最小 smoke

建议新增：`AiCaseServiceIntegrationTests` 或放入第一版 `AiCaseControllerIntegrationTests`

覆盖入口：

- `AiCaseService.reviewGeneratedCases`
- 后续再覆盖 `AiCaseService.streamReviewGeneratedCases`

数据准备：

- 创建 provider connection。
- 创建 active `CASE_REVIEWER` 配置。
- 构造包含 1 条 generated case 的 `ReviewAiGeneratedCasesRequest`。

Mock 策略：

- 第一批建议 mock `AiProviderClient.review`，返回 `AiReviewResult("APPROVE", ...)`。

关键断言：

- 返回 `result`、`summary`、`issues`、`suggestions` 结构不变。
- 不真实调用外部模型。

## 分批实施顺序

1. 第一批：新增 `AiCaseControllerIntegrationTests`，覆盖 provider CRUD/list/secret、config CRUD/list/secret、txt 素材导入和图片上传/下载/删除。
2. 第二批：新增 `AiCaseServiceIntegrationTests`，mock `AiProviderClient`，覆盖生成和评审最小 smoke。
3. 第三批：补 provider test/fetch models 的 mock 覆盖，并验证模型缓存返回。
4. 第四批：补 streamGenerateCases / streamReviewGeneratedCases 的流式解析 smoke，重点保护 `AiResponseParsingSupport`。
5. 暂缓：真实 OpenAI compatible HTTP 端到端、docx 图片提取复杂样例、异常回滚路径。这些风险更高，等基础接口测试稳定后再补。

## 推荐验证命令

第一批计划阶段仍运行：

```text
.\mvnw.cmd "-Dtest=AiProviderClientTests" test
.\mvnw.cmd -DskipTests compile
git diff --check
```

后续真正补测试时建议新增命令：

```text
.\mvnw.cmd "-Dtest=AiCaseControllerIntegrationTests,AiCaseServiceIntegrationTests,AiProviderClientTests" test
```

