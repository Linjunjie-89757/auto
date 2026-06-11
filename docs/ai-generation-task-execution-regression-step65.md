# AI 生成任务执行回归记录（第六十五步）

本记录用于收尾第 63-64 步新增的 AI 生成任务执行 service 级集成测试。当前阶段不改接口路径、不改响应结构、不改数据库、不动前端，只确认 `AiGenerationTaskService.executeTask(...)` 的核心成功链路已经具备最小回归保护，并梳理后续失败链路测试计划。

## 已覆盖范围

| 测试类 | 覆盖链路 | 关键断言 |
| --- | --- | --- |
| `AiGenerationTaskExecutionServiceTests#executeCompleteTaskPersistsGenerationReviewAndEvents` | `COMPLETE` 输出模式：创建 provider、配置 `CASE_GENERATOR` / `CASE_REVIEWER`、创建任务、直接执行 `executeTask`、完成生成和评审落库 | `status=COMPLETED`、`currentStep=4`、`finishedAt` 非空、`provider/model/generatedCount/generatedCases/reviewResult/generationRawOutput/reviewRawOutput` 写入、事件包含 `TASK_STARTED` / `GENERATION_COMPLETED` / `REVIEW_STARTED` / `REVIEW_COMPLETED` / `FINAL_CASES_READY` / `TASK_COMPLETED` |
| `AiGenerationTaskExecutionServiceTests#executeStreamTaskPersistsGenerationReviewAndEvents` | `STREAM` 输出模式：创建 provider、配置 `CASE_GENERATOR` / `CASE_REVIEWER`、创建任务、mock 流式生成和流式评审 delta、直接执行 `executeTask` | `status=COMPLETED`、`currentStep=4`、`finishedAt` 非空、`provider/model/generatedCount/generatedCases/reviewResult/generationRawOutput/reviewRawOutput` 写入、事件包含 `CASE_GENERATED` / `GENERATION_COMPLETED` / `REVIEW_STARTED` / `CASE_REVIEWED` / `REVIEW_COMPLETED` / `TASK_COMPLETED` |

## Mock 策略

- 使用 `@MockitoBean AiProviderClient`，避免真实调用外网模型。
- `COMPLETE` 链路直接 mock `generate(...)` 和 `review(...)`，返回最小有效 `AiGeneratedCasesResult` 与 `AiReviewResult`。
- `STREAM` 链路 mock `streamStructuredContentWithResult(...)`，在 answer 中主动调用传入的 `deltaConsumer`，让生成和评审事件由当前 service 链路真实触发。
- 因 `AiResponseParsingSupport` 复用 `AiProviderClient.parseGeneratedCasesContent(...)` 解析流式生成行，`STREAM` 测试额外 mock 该解析方法，避免 mock client 破坏解析 support 的组合行为。
- 测试通过 `upsertConfig(...)` 创建或更新当前用户的 `CASE_GENERATOR` / `CASE_REVIEWER` 配置，避免角色配置唯一约束影响测试稳定性。

## 为什么直接调用 executeTask

- `AiGenerationTaskRunner` 负责异步线程调度，不是本阶段要验证的核心业务行为。
- 直接调用 `AiGenerationTaskService.executeTask(taskId, workspaceCode)` 可以稳定覆盖任务执行编排、生成结果落库、评审结果落库、raw output 写入和事件写入。
- 这样可以避免线程调度、测试时序、SSE 长连接等非核心因素导致测试不稳定，同时仍然保护任务执行主链路。

## 未覆盖范围

- 生成阶段失败：`AiProviderClient.generate(...)` 或流式生成抛异常后，任务是否进入 `FAILED`，错误信息和 `TASK_FAILED` 事件是否正确写入。
- 评审阶段失败：生成已成功但评审抛异常时，任务失败状态、已生成数据保留和错误事件是否符合当前实现。
- 取消中断：任务在生成前、生成后、评审前/评审后被标记取消时的 `CANCELED` 状态和事件语义。
- `fallbackToComplete`：流式生成或流式评审降级为完整输出时，fallback 事件、raw output、最终结果合并是否正确。
- 图片素材忽略：模型不支持图片时忽略图片继续生成的 `IMAGE_ASSETS_IGNORED` 行为。
- 真实 SSE 增量：`GET /api/cases/ai/tasks/{taskId}/events/stream` 的长连接实时推送、心跳、客户端断开和并发写入。
- 真实外部模型网络调用：仍只依赖 mock，不验证真实 provider API。

## 后续失败链路测试计划

1. 新增 `COMPLETE` 生成失败 service 级测试：mock `generate(...)` 抛异常，断言任务 `FAILED`、`errorMessage`、`finishedAt`、`TASK_FAILED`。
2. 新增 `COMPLETE` 评审失败 service 级测试：mock 生成成功、评审失败，断言已生成结果保留、任务 `FAILED`、错误事件写入。
3. 新增 `STREAM` fallback service 级测试：mock `StreamContentResult(fallbackToComplete=true)`，断言 fallback 事件、结果解析和最终完成状态。
4. 新增取消中断 service 级测试：直接设置任务取消标记或通过 service cancel 入口触发，覆盖执行前取消和生成后取消的最小场景。
5. 图片素材忽略建议放到独立批次，先复用已有素材上传能力准备 asset，再验证任务执行事件和 raw output 语义。

## 验证命令

```text
.\mvnw.cmd "-Dtest=AiGenerationTaskExecutionServiceTests,AiProviderClientTests" test
.\mvnw.cmd -DskipTests compile
git diff --check
```
