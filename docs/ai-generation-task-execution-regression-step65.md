# AI 生成任务执行回归记录（第六十五步）

本记录用于收尾第 63-69 步新增的 AI 生成任务执行 service 级集成测试。当前阶段不改接口路径、不改响应结构、不改数据库、不动前端，只确认 `AiGenerationTaskService.executeTask(...)` 的核心成功、失败、fallback 和取消链路已经具备最小回归保护，并梳理后续剩余测试计划。

## 已覆盖范围

| 测试类 | 覆盖链路 | 关键断言 |
| --- | --- | --- |
| `AiGenerationTaskExecutionServiceTests#executeCompleteTaskPersistsGenerationReviewAndEvents` | `COMPLETE` 输出模式：创建 provider、配置 `CASE_GENERATOR` / `CASE_REVIEWER`、创建任务、直接执行 `executeTask`、完成生成和评审落库 | `status=COMPLETED`、`currentStep=4`、`finishedAt` 非空、`provider/model/generatedCount/generatedCases/reviewResult/generationRawOutput/reviewRawOutput` 写入、事件包含 `TASK_STARTED` / `GENERATION_COMPLETED` / `REVIEW_STARTED` / `REVIEW_COMPLETED` / `FINAL_CASES_READY` / `TASK_COMPLETED` |
| `AiGenerationTaskExecutionServiceTests#executeStreamTaskPersistsGenerationReviewAndEvents` | `STREAM` 输出模式：创建 provider、配置 `CASE_GENERATOR` / `CASE_REVIEWER`、创建任务、mock 流式生成和流式评审 delta、直接执行 `executeTask` | `status=COMPLETED`、`currentStep=4`、`finishedAt` 非空、`provider/model/generatedCount/generatedCases/reviewResult/generationRawOutput/reviewRawOutput` 写入、事件包含 `CASE_GENERATED` / `GENERATION_COMPLETED` / `REVIEW_STARTED` / `CASE_REVIEWED` / `REVIEW_COMPLETED` / `TASK_COMPLETED` |
| `AiGenerationTaskExecutionServiceTests#executeCompleteTaskMarksFailedWhenGenerationFails` | `COMPLETE` 生成阶段失败：mock `generate(...)` 抛异常，直接执行 `executeTask` | `status=FAILED`、`finishedAt` 非空、`errorMessage` 写入、无成功生成结果、无 `generationRawOutput` / `reviewResult` / `reviewRawOutput`，事件包含 `TASK_STARTED` / `TASK_FAILED` 且不包含 `GENERATION_COMPLETED` / `REVIEW_STARTED` |
| `AiGenerationTaskExecutionServiceTests#executeCompleteTaskKeepsGenerationResultWhenReviewFails` | `COMPLETE` 评审阶段失败：mock `generate(...)` 成功、`review(...)` 抛异常 | `status=FAILED`、`finishedAt` 非空、`errorMessage` 写入、已生成的 `generatedCases` / `generationRawOutput` 保留，事件包含 `GENERATION_COMPLETED` / `REVIEW_STARTED` / `TASK_FAILED` |
| `AiGenerationTaskExecutionServiceTests#executeStreamTaskMarksFailedWhenGenerationStreamFails` | `STREAM` 生成阶段失败：mock 第一次 `streamStructuredContentWithResult(...)` 抛异常 | `status=FAILED`、`finishedAt` 非空、`errorMessage` 写入、无生成结果和评审结果，事件包含 `TASK_STARTED` / `GENERATION_MODEL_READY` / `TASK_FAILED` |
| `AiGenerationTaskExecutionServiceTests#executeStreamTaskKeepsGenerationResultWhenReviewStreamFails` | `STREAM` 评审阶段失败：流式生成成功、第二次流式评审调用抛异常 | `status=FAILED`、`finishedAt` 非空、`errorMessage` 写入、已生成的 `generatedCases` / `generationRawOutput` 保留，事件包含 `CASE_GENERATED` / `GENERATION_COMPLETED` / `REVIEW_STARTED` / `TASK_FAILED` |
| `AiGenerationTaskExecutionServiceTests#executeStreamTaskCompletesWhenGenerationFallsBackToCompleteOutput` | `STREAM` 生成阶段 fallback 到完整输出：mock `StreamContentResult(fallbackToComplete=true)` 后继续完成评审 | `status=COMPLETED`、`currentStep=4`、`finishedAt` 非空、生成和评审结果写入，事件包含 `GENERATION_STREAM_FALLBACK` / `GENERATION_COMPLETED` / `REVIEW_STARTED` / `REVIEW_COMPLETED` / `TASK_COMPLETED` |
| `AiGenerationTaskExecutionServiceTests#executeTaskKeepsCanceledWhenCanceledBeforeStart` | 执行前取消：创建任务后先调用 `cancelTask(...)`，再直接执行 `executeTask` | 任务保持 `status=CANCELED`、`cancelRequested=true`、`finishedAt` 非空，事件包含 `TASK_CANCELED`，且不进入 `TASK_STARTED` / `TASK_FAILED` / `GENERATION_COMPLETED` / `REVIEW_STARTED` |
| `AiGenerationTaskExecutionServiceTests#executeCompleteTaskKeepsCanceledWhenCanceledAfterGenerationReturns` | `COMPLETE` 生成后取消：mock `generate(...)` 返回前调用 `cancelTask(...)` | 任务最终 `status=CANCELED`、`cancelRequested=true`、`finishedAt` 非空，不写成功生成结果、不写评审结果，事件包含 `TASK_STARTED` / `TASK_CANCELED`，且不写 `TASK_FAILED` |

## Mock 策略

- 使用 `@MockitoBean AiProviderClient`，避免真实调用外网模型。
- `COMPLETE` 链路直接 mock `generate(...)` 和 `review(...)`，返回最小有效 `AiGeneratedCasesResult` 与 `AiReviewResult`。
- `COMPLETE` 失败链路通过 mock `generate(...)` 或 `review(...)` 抛异常，保护任务失败状态、错误信息、事件和已生成数据保留语义。
- `STREAM` 链路 mock `streamStructuredContentWithResult(...)`，在 answer 中主动调用传入的 `deltaConsumer`，让生成和评审事件由当前 service 链路真实触发。
- `STREAM` 失败和 fallback 链路通过连续 stub `streamStructuredContentWithResult(...)` 的第一次/第二次调用，分别模拟生成失败、评审失败和 `fallbackToComplete=true`。
- 取消中断链路通过 service 自身的 `cancelTask(...)` 入口触发，不直接改 mapper 或任务实体，保护当前取消状态和事件语义。
- 因 `AiResponseParsingSupport` 复用 `AiProviderClient.parseGeneratedCasesContent(...)` 解析流式生成行，`STREAM` 测试额外 mock 该解析方法，避免 mock client 破坏解析 support 的组合行为。
- 测试通过 `upsertConfig(...)` 创建或更新当前用户的 `CASE_GENERATOR` / `CASE_REVIEWER` 配置，避免角色配置唯一约束影响测试稳定性。

## 为什么直接调用 executeTask

- `AiGenerationTaskRunner` 负责异步线程调度，不是本阶段要验证的核心业务行为。
- 直接调用 `AiGenerationTaskService.executeTask(taskId, workspaceCode)` 可以稳定覆盖任务执行编排、生成结果落库、评审结果落库、raw output 写入和事件写入。
- 这样可以避免线程调度、测试时序、SSE 长连接等非核心因素导致测试不稳定，同时仍然保护任务执行主链路。

## 未覆盖范围

- 取消中断：当前已覆盖执行前取消和 `COMPLETE` 生成后取消；`STREAM` 生成/评审过程中取消、`COMPLETE` 评审前/评审后取消尚未单独覆盖。
- `fallbackToComplete`：当前已覆盖流式生成 fallback 成功链路；流式评审 fallback 到完整输出尚未单独覆盖。
- 图片素材忽略：模型不支持图片时忽略图片继续生成的 `IMAGE_ASSETS_IGNORED` 行为。
- 真实 SSE 增量：`GET /api/cases/ai/tasks/{taskId}/events/stream` 的长连接实时推送、心跳、客户端断开和并发写入。
- 真实外部模型网络调用：仍只依赖 mock，不验证真实 provider API。

## 后续失败链路测试计划

1. 新增 `STREAM` 评审 fallback service 级测试：mock 第二次 `StreamContentResult(fallbackToComplete=true)`，断言 `REVIEW_STREAM_FALLBACK`、最终结果合并和完成状态。
2. 新增更细粒度取消中断测试：覆盖 `STREAM` 生成/评审中取消，以及 `COMPLETE` 评审前/评审后取消。
3. 图片素材忽略建议放到独立批次，先复用已有素材上传能力准备 asset，再验证任务执行事件和 raw output 语义。

## 验证命令

```text
.\mvnw.cmd "-Dtest=AiGenerationTaskExecutionServiceTests,AiProviderClientTests" test
.\mvnw.cmd -DskipTests compile
git diff --check
```
