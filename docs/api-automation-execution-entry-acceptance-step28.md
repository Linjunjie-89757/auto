# 接口自动化执行入口集成测试收尾（第二十八步）

本轮只做入口集成测试收尾巡检和文档同步，不改接口、不改数据库、不动前端，不拆新服务，不改变执行行为。

## 覆盖状态

`ApiExecutionEntryIntegrationTests` 当前已覆盖第二十四步计划中的 5 个执行入口：

| 执行入口 | 覆盖用例 | 当前验证点 |
| --- | --- | --- |
| `debugRunDefinitionDraft` | `debugRunDefinitionDraftReturnsSuccessfulRunWithStepResult` | 草稿 definition 调试返回 `SUCCESS`，task/report 非空，步骤响应 200。 |
| `debugRunDefinition` | `debugRunSavedDefinitionReturnsSuccessfulRunAndUpdatesLastRunResult` | 已保存 definition 调试返回 `SUCCESS`，definition `lastRunResult` / `lastRunAt` 更新。 |
| `runCase` | `runSavedCaseReturnsSuccessfulRunUpdatesLastRunResultAndWritesHistory` | 已保存 case 执行返回 `SUCCESS`，case `lastRunResult` / `lastRunAt` 更新，并写入运行历史。 |
| `debugRunCaseDraft` | `debugRunCaseDraftWithSavedCaseReturnsSuccessfulRunAndWritesHistory` | 草稿 case 调试返回 `SUCCESS`，传入已保存 caseId 时写入运行历史。 |
| `runScenario` | `runScenarioReturnsSuccessfulRunAndUpdatesLastRunResult` | 已保存 scenario 执行返回 `SUCCESS`，至少一个 HTTP step 响应 200，scenario `lastRunResult` / `lastRunAt` 更新。 |

## 测试策略确认

- 测试类继承 `IntegrationTestSupport`，复用 H2 + migration 初始化、当前用户上下文和 `WORKSPACE_CODE`。
- 使用 JDK `HttpServer` 提供本地 `/ok` JSON 响应，不依赖外部网络。
- 测试数据优先通过 `ApiAutomationService.createDefinition`、`createCase`、`createScenario` 创建，不直接写 mapper。
- 运行历史验证通过 `ApiAutomationService.listCaseRunHistory` 查询，不直接查 mapper。

## 剩余未覆盖范围

第一轮入口集成测试只做 smoke，不覆盖以下复杂场景：

- 场景控制器：IF、LOOP、ONCE_ONLY、CONSTANT_TIMER、SCRIPT。
- 嵌套场景和嵌套深度限制。
- 场景级断言。
- 失败链路：HTTP 非 2xx、断言失败、处理器失败、提取器失败。
- 环境变量集、默认环境、变量覆盖优先级。
- Basic/Digest、multipart、binary body 等请求细节入口级组合。

这些能力已有部分 support 或处理器核心测试覆盖。后续如要扩展入口测试，应一批只补一个复杂维度，避免入口测试过慢或过重。

## 验证命令

```powershell
.\mvnw.cmd "-Dtest=ApiExecutionEntryIntegrationTests" test
.\mvnw.cmd -DskipTests compile
git diff --check
```
