# 接口自动化后端治理说明

## 当前职责边界

接口自动化对外 HTTP 入口仍集中在 `ApiAutomationController`，常规接口定义、用例、场景、环境变量集、执行和报告查询都通过 `ApiAutomationService` 门面转发。`ApiAiCaseGenerationService` 保持独立，继续负责接口用例 AI 生成流式接口。

| 类 | 职责 |
| --- | --- |
| `ApiAutomationController` | HTTP 路由、请求参数接收、统一响应包装 |
| `ApiAutomationService` | 薄门面，保持原服务入口不变，委托到各 domain service |
| `ApiDefinitionDomainService` | 接口定义 CRUD、接口定义模块树、定义模块移动/删除 |
| `ApiCaseDomainService` | 接口用例 CRUD、用例变更历史写入 |
| `ApiScenarioDomainService` | 接口场景 CRUD、场景模块树、场景步骤与断言保存规范化 |
| `ApiConfigDomainService` | 接口自动化环境配置、变量集配置 CRUD |
| `ApiExecutionDomainService` | 执行入口编排，包括接口调试、用例执行、草稿调试、场景执行 |
| `ApiExecutionEngineSupport` | 执行上下文、请求执行、处理器执行、断言评估、报告/步骤持久化 |
| `ApiRunHistoryDomainService` | 用例变更历史、用例运行历史、报告步骤查询 |
| `ApiWorkspaceScopeSupport` | 工作空间范围解析和读权限校验复用 |
| `ApiAutomationFormatSupport` | 空值处理、列表默认值、JSON 读取、处理器归一化等纯格式工具 |
| `ApiAssertionEvaluator` | 接口断言计算 |

## 调用链巡检结论

- `ApiAutomationController` 只注入 `ApiAutomationService` 和 `ApiAiCaseGenerationService`。
- 普通接口自动化能力没有从 Controller 直接调用 domain service、mapper 或执行 support。
- `ApiAutomationService` 公开方法与 Controller 中调用的方法一一对应。
- `ApiAutomationService` 保留原入口，内部按职责委托到定义、用例、场景、配置、执行、历史查询 domain service。
- 本轮治理不改接口路径、不改请求/响应模型、不改数据库结构、不动前端。

## 最小回归清单

后续每次继续拆分接口自动化后端，至少回归以下链路：

1. 接口定义
   - 查询定义列表。
   - 新增、编辑、删除接口定义。
   - 查询接口定义详情。
   - 新增、编辑、移动、删除接口定义模块。

2. 接口用例
   - 查询用例列表。
   - 新增、编辑、删除接口用例。
   - 查询用例详情。
   - 查询用例变更历史。

3. 接口场景
   - 查询场景列表。
   - 新增、编辑、删除接口场景。
   - 查询场景详情。
   - 新增、编辑、移动、删除场景模块。
   - 保存包含 API、API_CASE、API_SCENARIO、控制器、定时器、脚本步骤的场景。

4. 环境与变量集
   - 查询、新增、编辑、删除接口环境。
   - 查询、新增、编辑、删除变量集。
   - 校验工作空间范围和只读空间限制仍生效。

5. 执行链路
   - 已保存接口定义调试。
   - 接口定义草稿调试。
   - 已保存用例执行。
   - 用例草稿调试。
   - 场景执行。
   - 前置处理器、后置处理器、变量提取、断言失败摘要仍能写入运行结果。

6. 报告与历史
   - 查询用例运行历史。
   - 查询用例运行历史详情。
   - 查询报告步骤。
   - 场景断言步骤可正常持久化和展示。

## 继续治理注意事项

- `ApiAutomationService` 应继续保持门面角色，不重新堆业务逻辑。
- domain service 之间不要互相随意调用；确需共享的纯函数放到 support，涉及业务状态的流程保留在对应 domain service。
- `ApiExecutionEngineSupport` 涉及 HTTP 请求、断言、处理器、变量、报告持久化，继续拆分时应一次只移动一个稳定职责。
- 工作空间校验必须留在每条业务入口链路内，不得只依赖前端传参。
- 若继续修复中文文案或编码问题，应先用 Git 历史或 UTF-8 文件内容确认真实源码，不以控制台乱码显示为依据。

## 执行引擎风险清单

`ApiExecutionEngineSupport` 当前约 2900 行，承担了执行上下文、场景步骤递归、HTTP 请求构建、认证、变量替换、前后置处理器、SQL 处理器、提取器、断言、报告步骤持久化等职责。第十步只做风险收敛和边界记录，不拆执行主流程。

| 风险点 | 当前位置 | 风险说明 | 建议处理 |
| --- | --- | --- | --- |
| 执行入口和执行细节耦合 | `executeDefinition`、`executeCase`、`executeScenarioSteps` | 单步执行、场景递归、异常结果封装、处理器执行和断言都在同一类里，后续修改容易影响多条执行链路 | 暂不拆主流程；先补接口级回归，再逐个抽 support |
| HTTP 请求构建职责偏重 | `resolveRequest`、`buildHttpRequest`、`sendRequest`、认证相关方法 | URL 拼接、query、header、cookie、body、Basic/Digest 认证在一起，任何改动都可能影响真实请求行为 | 优先拆成 `ApiRequestExecutionSupport`，但保持入参/返回快照不变 |
| 变量替换是全链路关键点 | `replaceVariables`、`toEnabledMap`、`buildQueryString` | 路径、参数、header、body、认证、SQL、场景条件都依赖同一替换规则，缺变量会抛业务异常 | 后续若抽出 `ApiVariableResolver`，必须保持缺变量即失败的语义 |
| 处理器执行链路复杂 | `executeProcessors`、`executeScriptProcessor`、`executeSqlProcessor`、`applyProcessorExtractors` | 处理器会修改请求、写变量、持久化环境变量、记录结果，属于执行副作用集中区 | 优先补处理器回归用例；再考虑抽 `ApiProcessorExecutor` |
| 提取器容错影响结果语义 | `applyProcessorExtractors` 中捕获提取异常并写失败结果 | 这里不是普通异常吞掉，而是把单个提取项失败写入处理器结果；贸然改异常处理会改变用户看到的执行结果 | 暂不改；如需调整，先明确提取失败是否应中断步骤 |
| 断言逻辑已经部分下沉 | `evaluateAssertions` 委托 `ApiAssertionEvaluator`，类内仍保留旧断言辅助方法 | 存在历史方法残留，继续清理前要确认没有旧兼容链路依赖 | 后续做一次只读引用巡检，确认无用后再清理 |
| 报告和历史持久化与执行状态绑定 | `persistStep`、`finalizeRunDefinition`、`finalizeRunCase`、`finalizeRunScenario`、`persistCaseRunHistory` | 任务、报告、步骤、用例/场景 lastRun 字段同时更新，事务边界和失败状态需要稳定 | 可抽 `ApiRunReportSupport`，但不要改变调用时机和字段写入顺序 |
| 场景步骤递归和控制器语义集中 | `executeScenarioSteps` 及各类 controller step 方法 | IF、LOOP、ONCE_ONLY、SCENARIO 引用都共享变量上下文和 stepOrder，拆分不当会改变执行顺序 | 场景执行拆分应最后做，且先覆盖嵌套场景和循环场景回归 |

## 执行引擎后续拆分建议

建议后续按低风险到高风险顺序推进：

1. `ApiRequestExecutionSupport`
   - 迁移 `resolveRequest`、`buildHttpRequest`、`sendRequest`、Basic/Digest 认证、multipart/body 构建。
   - 保持 `ResolvedRequest`、`SentRequestResult` 或等价内部模型的字段语义不变。

2. `ApiVariableResolver`
   - 迁移 `replaceVariables`、`toEnabledMap`、`buildQueryString` 这类纯变量/参数处理。
   - 明确保留缺失变量抛 `BadRequestException` 的行为。

3. `ApiProcessorExecutor`
   - 迁移前后置处理器执行、脚本处理器、SQL 处理器、提取器执行。
   - 迁移前先补处理器场景的接口级回归清单。

4. `ApiRunReportSupport`
   - 迁移步骤结果、任务、报告、用例运行历史持久化。
   - 保持 `lastRunResult`、`lastRunAt`、`failureSummary`、步骤快照 JSON 的写入行为。

5. 场景执行拆分
   - 最后再拆 `executeScenarioSteps` 和 controller step。
   - 拆分前至少准备嵌套场景、循环、条件、一次性控制器、脚本步骤的回归数据。
