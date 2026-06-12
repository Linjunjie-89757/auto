# 接口自动化残余大类巡检回归记录（第 124 步）

本记录用于收尾第 119-124 步接口自动化残余大类巡检阶段。本阶段没有修改生产代码，不改接口路径、不改响应结构、不改数据库、不动前端。

## 阶段结论

- `ApiAiCaseGenerationService` 是后续最值得治理的残余大类，但当前缺少专项测试保护，本阶段不拆。
- `ApiScenarioDomainService` 和 `ApiDefinitionDomainService` 虽然偏长，但仍处于清晰领域聚合内，本阶段不拆。
- `ApiAutomationController` 是多入口 controller 聚合，基本只做参数接收和门面委托，本阶段不拆。
- 已通过文档记录边界、风险和后续触发条件，避免为了行数继续制造低收益抽象。

## 已运行验证

第 124 步运行接口自动化相关回归集合：

- `ApiAutomationListControllerIntegrationTests`
- `ApiExecutionEntryIntegrationTests`
- `ApiProcessorCoreTests`
- `ApiVariableResolverTests`
- `ApiRequestExecutionSupportTests`
- `ApiProcessorExecutorTests`
- `ApiScenarioExecutionSupportTests`
- `ApiAssertionSupportTests`
- `ApiRunResultPersistenceSupportTests`
- `ApiRunFinalizerSupportTests`

结果：40 tests，全部通过。

## 覆盖范围

- definitions / cases / scenarios 列表筛选分页。
- 已保存 definition / case / scenario 执行入口 smoke。
- 草稿 definition / case debug 执行入口 smoke。
- 变量解析、请求组装、处理器执行、场景执行、断言、结果持久化、最终状态更新。

## 仍未覆盖范围

- `ApiAiCaseGenerationService` 的 AI 生成 SSE 主链路。
- Prompt 构建文案稳定性。
- 流式 NDJSON 解析和 fallback 解析。
- AI provider 缺失、模型缺失、外部调用失败时的事件输出。
- 场景目录管理的完整 CRUD 异常分支。
- 定义目录路径同步和删除前场景引用保护的窄范围专项测试。

## 下一阶段建议

建议下一阶段先做接口自动化 AI 生成链路测试准备，而不是直接拆 `ApiAiCaseGenerationService`：

1. 梳理当前 `/api/automation/api/ai-case-generation/stream` 的测试可行性。
2. 明确 `AiProviderClient` mock 策略和 provider 连接测试数据准备方式。
3. 先补最小 service 级或 controller 级 smoke，覆盖成功 SSE 事件和失败 SSE 事件。
4. 测试稳定后再拆 `ApiAiCaseGenerationPromptSupport`、`ApiAiCaseGenerationParsingSupport` 或事件写出 support。
