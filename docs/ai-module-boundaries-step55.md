# AI 模块职责边界验收（第五十五步）

本轮是 AI 模块在第 50 到 54 步拆分后的阶段收尾验收。不改接口路径、不改响应结构、不改数据库、不动前端，不继续拆新类。

## 当前职责边界

| 类 | 职责 |
| --- | --- |
| `AiCaseController` | 保持 HTTP 路由入口，接收请求参数，统一包装响应。Controller 仍只调用 `AiCaseService`、任务相关入口仍调用既有任务服务。 |
| `AiCaseService` | AI 用例能力门面，保留原 public 方法入口。负责生成/评审主流程编排、工作空间写权限校验、调用模型客户端、处理图片降级重试、组装流式返回对象，并把供应商、配置、素材、Prompt、响应解析委托给独立服务/support。 |
| `AiProviderDomainService` | 供应商连接和模型管理：连接 CRUD、连接测试、模型预览/拉取/列表/探测、模型能力缓存、密钥读取、协议归一化和 provider profile 构建。 |
| `AiCaseConfigDomainService` | AI 用例角色配置：生成器/评审器配置 CRUD、配置测试、配置密钥读取、legacy 配置迁移、角色配置解析、能力覆盖读写、角色/status/topP/maxCases 归一化。 |
| `AiRequirementAssetDomainService` | 需求文档和素材附件：txt/md/docx 导入、DOCX 文本和图片提取、素材上传/下载/删除、素材所有权校验、图片输入转换、上传异常回滚。 |
| `AiPromptBuilderSupport` | Prompt 纯拼装：生成 Prompt、生成结果评审 Prompt、已保存用例评审 Prompt。 |
| `AiResponseParsingSupport` | AI 响应解析：流式行缓冲、生成用例逐行解析、评审 NDJSON 解析、完整评审结果 fallback 转 update、生成覆盖摘要/缺口解析、图片输入不支持错误判断。 |

## 调用链检查

- `AiCaseController` 没有直接调用 `AiProviderDomainService`、`AiCaseConfigDomainService`、`AiRequirementAssetDomainService`、`AiPromptBuilderSupport` 或 `AiResponseParsingSupport`。
- `AiCaseService` 保持对外门面定位：配置、供应商、素材入口继续保留原方法签名，但内部只做委托。
- `AiCaseService` 仍保留 AI 生成/评审编排，这是当前阶段刻意保留的边界，避免把模型调用、流式回调、图片降级重试和任务服务依赖一次性拆散。
- `AiCaseConfigDomainService` 依赖 `AiProviderDomainService` 获取连接、密钥、provider profile 和模型能力，配置服务没有反向依赖门面。
- `AiRequirementAssetDomainService` 只依赖素材 mapper 和存储服务，不依赖配置、供应商、Prompt 或响应解析。
- `AiPromptBuilderSupport` 是纯 Prompt 拼装，不依赖 mapper、模型客户端或门面。
- `AiResponseParsingSupport` 依赖 `AiProviderClient` 复用完整内容解析能力，并暂时引用 `AiCaseService` 中的流式 update record，属于兼容现有返回类型的最小耦合点。

## 巡检结论

- `AiCaseService` 已从大服务收敛到约 469 行，当前主要承担门面委托和生成/评审编排。
- 供应商连接、角色配置、需求素材、Prompt 拼装、响应解析已经分离，职责边界基本闭环。
- 本阶段没有改变 Controller 调用链，没有新增接口，没有改数据库字段，没有触碰前端。
- 本轮低风险清理了 `AiCaseService` 中 Prompt 拆分后遗留的无调用 `nullSafe` helper。

## 已知边界

- `AiCaseService` 仍直接依赖 `AiProviderClient`，因为生成/评审模型调用主流程尚未拆出。
- `AiResponseParsingSupport` 暂时引用 `AiCaseService.GeneratedCaseStreamUpdate` 和 `AiCaseService.ReviewCaseStreamUpdate`，后续如果继续降低耦合，可把这些 runtime record 挪到独立模型文件。
- `AiGenerationTaskService` 仍直接调用 `AiCaseService` 的生成/评审和流式方法，这是任务流对门面的合理依赖，暂不调整。
- 当前 AI 模块专项测试仍偏少，本轮主要依赖 `AiProviderClientTests` 和编译验证 Bean 装配与基础行为。

## 回归命令

```text
.\mvnw.cmd "-Dtest=AiProviderClientTests" test
.\mvnw.cmd -DskipTests compile
git diff --check
```

## 后续建议

下一阶段建议先补 AI 模块接口/集成回归测试，而不是继续拆服务。优先覆盖供应商连接、角色配置、素材导入/上传、生成入口和评审入口，保护当前拆分后的行为边界。

