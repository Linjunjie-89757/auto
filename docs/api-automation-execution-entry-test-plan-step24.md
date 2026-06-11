# 接口自动化执行入口集成回归测试准备（第二十四步）

本轮只做测试准备和现状梳理，不改接口、不改数据库、不动前端，也不继续拆执行引擎主链路。

## 现有覆盖

| 执行入口 | 当前覆盖情况 | 说明 |
| --- | --- | --- |
| 已保存接口定义调试 `debugRunDefinition` | 未发现直接覆盖 | 现有测试覆盖了请求执行 support，但没有通过已保存 definition id 走完整入口。 |
| 已保存接口用例执行 `runCase` | 未发现直接覆盖 | 现有测试覆盖了步骤持久化、收尾和历史实体转换，但没有通过已保存 case id 走完整入口。 |
| 草稿接口定义调试 `debugRunDefinitionDraft` | 已有基础覆盖 | `ApiProcessorCoreTests` 通过 `ApiAutomationService.debugRunDefinitionDraft` 走 Spring/H2/本地 HTTP 服务链路，覆盖处理器、提取器和断言。 |
| 草稿接口用例调试 `debugRunCaseDraft` | 未发现直接覆盖 | 需要先准备已保存 definition，再用草稿 case request 走入口。 |
| 场景执行 `runScenario` | 未发现直接覆盖 | `ApiScenarioExecutionSupportTests` 覆盖了场景 support 语义，但没有通过已保存 scenario id 走完整入口。 |

## 推荐测试边界

优先新增一个轻量集成测试类，例如 `ApiExecutionEntryIntegrationTests`，继承现有 `IntegrationTestSupport`：

- 复用 H2 迁移初始化和 `WORKSPACE_CODE = risk-ops`。
- 复用本地 `HttpServer`，提供稳定的 `/ok`、`/fail`、`/json` 响应。
- 优先通过 `ApiAutomationService` 调用入口，而不是直接调用 controller，确保 service 门面和 domain/service/support 链路都被覆盖。
- 每个入口只验证最关键的业务结果：`run.result()`、`stepResults`、状态码断言、report/task id 非空、必要时验证 lastRun 或运行历史。

## 最小用例建议

1. 已保存接口定义调试入口
   - 通过现有定义创建接口保存 `GET {baseUrl}/ok`。
   - 调用 `debugRunDefinition(definitionId, WORKSPACE_CODE, new ApiRunRequest(null, null))`。
   - 断言结果为 `SUCCESS`，步骤状态码为 200，definition 的 `lastRunResult` 被更新。

2. 已保存接口用例执行入口
   - 准备已保存 definition 和 case。
   - 调用 `runCase(caseId, WORKSPACE_CODE, new ApiRunRequest(null, null))`。
   - 断言结果为 `SUCCESS`，case `lastRunResult` 被更新，并写入 case run history。

3. 草稿接口定义调试入口
   - 现有 `ApiProcessorCoreTests` 已覆盖复杂处理器和断言链路。
   - 可追加一个更轻的 smoke 用例，只验证最基础 GET 请求成功，作为入口层回归。

4. 草稿接口用例调试入口
   - 先保存 definition。
   - 调用 `debugRunCaseDraft`，传入 definitionId 和草稿 request/assertions。
   - 断言结果为 `SUCCESS`；若传入已保存 caseId，再断言该 case 的运行历史被写入。

5. 场景执行入口
   - 准备一个包含 API 或 API_CASE 步骤的已保存 scenario。
   - 调用 `runScenario(scenarioId, WORKSPACE_CODE, new ApiRunRequest(null, null))`。
   - 断言每个步骤有结果，scenario `lastRunResult` 被更新。
   - 后续再追加控制器/嵌套场景/场景级断言，不在第一轮一次性补完。

## 数据与 Mock 策略

- 数据库：继续使用 `IntegrationTestSupport` 的 H2 + migration 初始化，不新增数据库结构。
- HTTP：使用 JDK `HttpServer`，避免依赖外部网络。
- 用户：复用 `IntegrationTestSupport` 中的当前用户上下文。
- 工作空间：复用 `WORKSPACE_CODE`，避免每个测试重复造空间。
- Mock：第一轮尽量不 Mock 执行链路 support；入口集成测试的价值在于验证真实 service/support 串联。

## 风险与注意事项

- 已保存 definition/case/scenario 的创建最好优先走 `ApiAutomationService` 现有方法，避免直接写 mapper 造成测试数据与真实业务保存逻辑不一致。
- 场景执行入口牵涉步骤 JSON、资源 id、continueOnFailure、场景断言和嵌套深度，第一轮只做 smoke 覆盖。
- 如果入口测试运行时间明显变长，可以把复杂处理器链路继续留在 `ApiProcessorCoreTests`，入口测试只覆盖“能串起来”的最小路径。
- 后续补测试时，应避免为了测试方便扩大生产代码可见性。
