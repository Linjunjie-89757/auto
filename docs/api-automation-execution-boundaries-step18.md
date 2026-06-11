# 接口自动化执行职责边界补充（第十八步）

本轮不改接口、不改数据库、不改前端，只对接口自动化执行拆分后的职责边界做巡检和低风险整理。`ApiExecutionEngineSupport` 继续作为执行总编排 support，但不再保留已由 domain service 或 history service 接管的模块树、场景保存规范化、历史结果反序列化等残留私有方法。

| 类 | 当前职责 |
| --- | --- |
| `ApiExecutionDomainService` | 对外执行用例的 domain 编排入口，负责调试/执行 definition、case、draft、scenario 的整体流程串联，决定何时创建 run envelope、持久化步骤、收尾任务与报告。 |
| `ApiExecutionEngineSupport` | 执行引擎总编排 support，保留执行上下文构建、run envelope 创建、definition/case/custom request 执行、场景执行委托回调、场景级断言结果构造、资源读取校验等运行期能力。 |
| `ApiRequestExecutionSupport` | HTTP 请求执行边界，负责变量替换后的请求解析、URL/query/header/cookie/body 构建、Basic/Digest 认证、multipart/binary 请求发送与请求快照所需信息。 |
| `ApiProcessorExecutor` | 前置/后置处理器与提取器执行边界，负责脚本/SQL/提取器执行、变量写入、环境变量持久化、处理器结果与提取结果记录。 |
| `ApiScenarioExecutionSupport` | 场景步骤运行边界，负责 API/API_CASE/CUSTOM_REQUEST/API_SCENARIO 步骤调度，以及 IF/LOOP/ONCE_ONLY/CONSTANT_TIMER/SCRIPT 控制器语义。 |
| `ApiAssertionSupport` | 断言通用纯逻辑，负责断言类型归一化、条件归一化、单值/多值比较、首个失败消息提取。 |
| `ApiAssertionEvaluator` | 接口响应、变量、脚本断言执行器，负责把请求/响应/变量上下文转换为断言结果。 |
| `ApiRunResultPersistenceSupport` | 运行结果持久化边界，负责报告步骤结果、断言/提取/处理器 JSON、用例运行历史、响应体大小与环境/变量集名称写入。 |
| `ApiRunFinalizerSupport` | 执行收尾边界，负责 definition/case/scenario 的 lastRun 字段，以及 task/report 的最终状态和 failureSummary 写入。 |

## 本轮巡检结论

- `ApiExecutionEngineSupport` 当前定位应收敛为“执行编排 + 资源读取 + 上下文构建”。
- 已删除 `ApiExecutionEngineSupport` 中确认无调用的历史残留私有方法，包括模块树构建、场景保存规范化、历史结果反序列化、无用 module/report 查询等逻辑。
- 未迁移或改动执行入口、HTTP 请求发送、处理器执行、断言行为、报告持久化、任务收尾行为。
- `ResolvedEnvironment`、`RunStepComputation`、`MutableRequestConfig` 仍定义在 `ApiExecutionEngineSupport` 内并被多个 support 复用。后续若要进一步治理，建议单独做一轮共享运行模型迁移，避免和行为改动混在一起。
