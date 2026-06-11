# AI 生成任务服务职责边界记录（第八十步更新）

本记录用于收尾第 72-79 步对 AI 生成任务服务的拆分治理。当前阶段不改接口路径、不改响应结构、不改数据库、不动前端；重点是确认拆分后的职责边界闭环，并记录后续可继续治理的最小目标。

## 当前职责边界

| 类 | 当前职责 |
| --- | --- |
| `AiGenerationTaskService` | 保持 Controller / Runner 依赖的门面入口；负责 `executeTask`、`executeCompleteTask`、`executeStreamTask` 的执行编排，调用 AI 生成/评审能力，更新任务状态，汇总生成/评审结果，追加执行事件。 |
| `AiGenerationTaskDomainService` | 负责任务 CRUD、列表/详情查询、取消、重试、更新、删除，以及这些能力所需的 workspace 权限校验、目录校验和任务读取。 |
| `AiGenerationTaskResponseSupport` | 负责任务响应 DTO 转换、用户名称补充、JSON 字段读写、索引列表归一化；保持 JSON 序列化失败和解析 fallback 行为不变。 |
| `AiGenerationTaskResultMergeSupport` | 负责生成用例和评审结果的合并，包括完整评审结果合并、流式评审单条更新、补充用例 metadata、`GeneratedAiCaseItem` 到 `AiExistingCaseItem` 的转换。 |
| `AiGenerationTaskEventMessageSupport` | 负责生成/评审/补充用例事件文案构建、评审状态标签、内部 caseIndex 文本清洗；不负责事件落库。 |
| `AiGenerationTaskSseSupport` | 负责任务事件 SSE 输出、历史事件补发、增量事件轮询、ping 心跳和终态退出；不负责事件创建和任务执行。 |
| `AiGenerationTaskExecutionStateSupport` | 负责任务执行状态更新和执行快照持久化，包括进入生成态、取消收尾、失败收尾、流式生成快照写入、raw output 截断和取消标记判断。 |
| `AiGenerationTaskEventService` | 继续保持事件追加、按 seq 查询、`listAfter` 查询和事件响应转换。 |
| `AiGenerationTaskRunner` | 继续保持异步调度薄封装，只调用 `AiGenerationTaskService.executeTask(...)`。 |

## 调用边界

- `AiCaseController` 和 `AiGenerationTaskRunner` 仍调用 `AiGenerationTaskService`，对外调用入口未变化。
- 任务管理类入口由 `AiGenerationTaskService` 委托到 `AiGenerationTaskDomainService`，响应结构由 `AiGenerationTaskResponseSupport` 统一生成。
- 执行链路仍留在 `AiGenerationTaskService`，但执行过程中涉及的 JSON、结果合并、事件文案和 SSE 已下沉到独立 support。
- 执行链路中的状态更新、取消/失败收尾和流式生成快照持久化已下沉到 `AiGenerationTaskExecutionStateSupport`，但 `AiGenerationTaskService` 仍负责决定何时进入这些状态。
- `AiGenerationTaskSseSupport` 自己完成 SSE 所需的任务读取和 workspace 读权限校验，避免为了 SSE 继续暴露主执行服务的内部 helper。
- `AiGenerationTaskEventService` 未被拆分，事件 seq 递增和事件查询语义保持原位。

## 本阶段已完成

- 第 72 步：抽出 `AiGenerationTaskDomainService`，收敛任务 CRUD/查询/取消/重试。
- 第 73 步：抽出 `AiGenerationTaskResponseSupport`，收敛响应转换和 JSON 读写。
- 第 74 步：抽出 `AiGenerationTaskResultMergeSupport`，收敛生成/评审结果合并逻辑。
- 第 75 步：抽出 `AiGenerationTaskEventMessageSupport`，收敛事件文案构建。
- 第 77 步：抽出 `AiGenerationTaskSseSupport`，收敛 SSE 输出逻辑。
- 第 79 步：抽出 `AiGenerationTaskExecutionStateSupport`，收敛执行状态更新、取消/失败收尾、raw output 截断和流式生成快照持久化。

## 保持不变的行为

- 任务创建初始字段、取消语义、重试规则、更新字段、删除行为保持不变。
- COMPLETE / STREAM 执行入口、成功/失败/fallback/取消链路保持不变。
- 任务响应字段、事件字段、JSON 字段解析 fallback、JSON 序列化错误信息保持不变。
- SSE 历史事件补发、增量轮询、终态退出和 ping 心跳行为保持不变。
- `TASK_CANCELED` / `TASK_FAILED` 事件写入语义保持不变。
- raw output 超长时仍保留尾部内容，截断长度保持不变。

## 剩余边界和风险

- `AiGenerationTaskService` 仍保留 COMPLETE / STREAM 执行主体，这是当前阶段刻意保留的核心编排逻辑。
- 执行主体仍直接编排 AI 生成/评审调用、fallback 分支、事件追加时机和部分完成态字段写入。后续如继续拆，应先拆极窄的事件追加 support 或执行上下文构建，不建议一次性搬空 `executeCompleteTask` / `executeStreamTask`。
- SSE 目前已有 service/controller 级 smoke 覆盖，但没有稳定覆盖长连接实时增量、客户端断开和并发事件写入。
- 真实外部模型网络调用仍由 mock 测试替代，不在本阶段验证范围内。

## 第八十步巡检结论

- 未发现 support 反向依赖 `AiGenerationTaskService`。
- `AiGenerationTaskService` 中剩余私有 helper 仍有调用：
  - `appendCompleteReviewEvents`：完整输出评审完成事件汇总。
  - `appendEvent`：执行编排内统一追加事件。
  - `requireTask`：执行入口和流式回调中刷新任务实体。
  - `normalizeOutputMode`：执行入口判断 COMPLETE / STREAM。
  - `blankToNull`：fallback reason payload 仍在使用。
- 暂未发现需要低风险删除的无调用私有方法。
- 当前更适合先补执行链路边界文档和回归记录，不继续大拆 `executeCompleteTask` / `executeStreamTask` 主体。

## 验证命令

```text
.\mvnw.cmd "-Dtest=AiGenerationTaskControllerIntegrationTests,AiGenerationTaskExecutionServiceTests,AiProviderClientTests" test
.\mvnw.cmd -DskipTests compile
git diff --check
```

## 下一阶段建议

下一步建议做第八十一步：补 AI 生成任务拆分后的 service/support 边界回归记录或轻量测试巡检。重点确认 controller、runner、domain/support、execution state、SSE 的调用链仍闭环；如新增测试，优先覆盖取消/失败事件语义和 SSE smoke，不继续拆执行主体。
