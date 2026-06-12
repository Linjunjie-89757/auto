# 接口自动化残余大类边界记录（第 123 步）

本记录用于收尾第 119-123 步对接口自动化残余大类的巡检。本阶段不改接口路径、不改响应结构、不改数据库、不动前端，不按行数硬拆，只记录当前职责、风险和后续触发条件。

## 巡检结论

| 类 | 当前判断 | 本阶段处理 |
| --- | --- | --- |
| `ApiAiCaseGenerationService` | 真实存在职责混合：SSE 编排、AI provider 解析、Prompt 构建、流式 NDJSON 解析、草稿归一化、事件输出都在一个类中。 | 暂不拆代码。缺少专项测试保护，直接抽 Prompt/解析 support 容易改变事件顺序、文案、fallback 和流式吞吐行为。 |
| `ApiScenarioDomainService` | 类偏长，但职责仍集中在场景聚合：场景 CRUD、场景目录树、步骤归一化、引用校验、筛选分页和响应转换。 | 暂不拆代码。当前已有列表分页和执行入口测试，但目录管理、步骤异常、环境/变量集引用异常覆盖不足。 |
| `ApiDefinitionDomainService` | 类偏长，主要覆盖接口定义 CRUD、目录树、目录路径同步、按目录筛选分页、删除前场景引用检查。 | 暂不拆代码。目录路径同步和 legacy `directoryName` 兼容耦合较深，继续拆分需要更细测试保护。 |
| `ApiAutomationController` | 行数偏高但基本是接口入口聚合，绝大多数方法只透传 `workspaceCode`、路径参数、查询参数和 request 到 `ApiAutomationService`。 | 暂不拆代码。拆 controller support 只会增加间接层，不降低行为风险。 |

## 为什么不继续硬拆

- `ApiAiCaseGenerationService` 是本阶段最像“大服务”的类，但 AI 生成链路目前没有专项集成/服务测试；Prompt 文案、NDJSON 解析、SSE event 顺序和错误吞吐都容易被拆分影响。
- `ApiScenarioDomainService` 和 `ApiDefinitionDomainService` 虽然长，但多数 helper 是同一领域聚合内的私有协作，抽出后会把 mapper、workspace 校验和 JSON 转换分散到更多类。
- `ApiAutomationController` 是多入口 controller，不是业务逻辑堆积；保持入口聚合比拆多个小 controller 更能维持接口路径和调用稳定。
- 本阶段目标是残余大类巡检，结论是先补测试和文档，再决定下一轮是否拆分。

## 已有测试保护

- `ApiAutomationListControllerIntegrationTests` 覆盖 definitions / cases / scenarios 列表筛选分页。
- `ApiExecutionEntryIntegrationTests` 覆盖定义、用例、场景执行入口 smoke。
- `ApiProcessorCoreTests`、`ApiVariableResolverTests`、`ApiRequestExecutionSupportTests`、`ApiProcessorExecutorTests`、`ApiScenarioExecutionSupportTests`、`ApiAssertionSupportTests`、`ApiRunResultPersistenceSupportTests`、`ApiRunFinalizerSupportTests` 覆盖执行链路核心 support。

## 主要缺口

- AI 生成用例接口缺少专项测试，尤其是：
  - Prompt 构建输出稳定性。
  - 单条生成、批量生成、流式生成的事件顺序。
  - NDJSON / JSON fallback 解析。
  - provider 缺失、模型缺失、AI 调用失败时的 SSE 失败事件。
- 场景目录管理测试不足：
  - 创建 / 重命名 / 移动 / 删除目录。
  - 删除非空目录失败。
  - 子目录递归筛选。
- 场景步骤归一化异常分支不足：
  - API step 引用不存在 definition。
  - CASE step 引用不存在 case。
  - 跨 workspace 引用。
  - 环境 / 变量集不存在或跨空间。
- 定义目录路径同步和场景引用删除保护还需要更窄测试。

## 后续触发条件

满足以下条件之一，再进入拆分：

- 新增 `ApiAiCaseGenerationServiceTests` 或 controller 集成测试，能 mock `AiProviderClient` 并稳定覆盖 SSE 输出。
- 场景目录和步骤引用异常测试补齐。
- 定义目录路径同步和删除保护测试补齐。
- 出现明确改动需求，需要在这些类上继续叠加逻辑。

## 推荐下一步

下一阶段优先做接口自动化 AI 生成链路测试准备，而不是直接拆服务：

- 先新增测试计划文档，明确如何 mock `AiProviderClient` 和 provider 连接数据。
- 再补 `ApiAiCaseGenerationServiceTests` 第一批，只覆盖非真实外网的最小成功和失败事件。
- 测试稳定后，再考虑抽 `ApiAiCaseGenerationPromptSupport` 和 `ApiAiCaseGenerationParsingSupport`。
