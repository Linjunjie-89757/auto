# 接口自动化运行模型边界补充（第二十一步）

本轮只补充第十九步、第二十步之后的职责边界说明，不改接口、不改数据库、不动前端，也不改变执行行为。

## 当前边界

| 类 | 职责 |
| --- | --- |
| `ApiExecutionRuntimeModels` | 接口自动化执行链路的包内运行模型容器，只供 `apiautomation` 包内 support 之间共享。 |
| `EnvironmentConfigPayload` | API 环境配置 JSON 的运行期读取模型，承接 headers、authConfig、timeoutMs、variables。 |
| `ResolvedEnvironment` | 执行前解析后的环境快照，包含环境 id、baseUrl、环境请求头、认证配置、超时和变量。 |
| `ExecutionContext` | 单次执行上下文，聚合已解析环境和变量 Map。 |
| `RunEnvelope` | 单次执行创建出的 task/report 包装对象。 |
| `RunStepComputation` | 单个执行步骤的计算结果，包含 success 和步骤响应快照。 |
| `MutableRequestConfig` | 处理器脚本可修改的请求配置副本，只用于执行期请求配置变更，不代表持久化模型。 |

## 可见性约定

- `ApiExecutionRuntimeModels` 保持 package-private，避免被 controller、domain 外层或其他模块当成公共 API 使用。
- 内部 record/class 当前也保持 package-private，只允许 `com.company.autoplatform.apiautomation` 包内访问。
- Spring support 类继续保持现有 `public class` 形式，避免改变组件扫描和测试构造方式。
- 执行入口仍由 `ApiExecutionDomainService` 触发，`ApiExecutionEngineSupport` 继续作为执行总编排 support。

## 巡检结论

- 第十九步已经把原本嵌在 `ApiExecutionEngineSupport` 内的运行模型迁移到独立容器，降低了其他 support 对执行引擎内部类型的耦合。
- 第二十步只收紧了运行模型容器的可见性，没有修改 HTTP 请求、处理器、断言、场景、持久化或收尾行为。
- 当前测试仍可直接构造运行模型，这是为了验证 support 的窄范围行为；后续如果测试变重，再考虑增加 test fixture，而不是扩大生产代码可见性。

## 后续建议

- 后续继续治理时，优先保持“一步只处理一个边界”的节奏。
- 不建议为了可见性洁癖修改 Spring 组件类级别可见性；收益低，容易引入扫描或代理兼容风险。
- 如果继续拆执行链路，应优先从只读查询、纯转换、测试夹具整理这类低风险区域开始。
