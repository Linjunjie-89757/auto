# 用例中心回归记录（第九十步）

本记录用于收尾第 82-90 步用例中心后端治理阶段。当前阶段不改接口路径、不改响应结构、不改数据库、不动前端。

## 已完成治理

- `CaseDomainService`：用例 CRUD、列表筛选分页、详情/摘要响应转换。
- `CaseDirectoryDomainService`：目录树、目录创建/重命名/移动/删除。
- `CaseBatchDomainService`：批量移动、批量更新、批量删除。
- `CaseExecutionAttachmentSupport`：执行附件上传、下载、删除和异常回滚。
- `CaseReviewDomainService`：AI 评审入口的最小编排。
- `CaseService`：保留门面委托，并暂存人工评审状态更新、执行结果状态更新。

## 已覆盖测试

测试类：`CaseControllerIntegrationTests`

已覆盖：
- 创建用例、查看详情、更新用例、删除用例。
- 列表分页和筛选：`keyword`、`priority`、`reviewStatus`、`executionStatus`。
- 目录创建、重命名、移动、删除。
- 目录下存在子目录时删除父目录失败。
- 按父目录筛选时包含子目录用例。
- 批量移动、批量更新、批量删除。
- 执行附件上传、详情展示、下载、删除。
- AI 评审入口 smoke，使用 `@MockitoBean AiProviderClient` 避免真实外网调用。

## 验证命令

```text
.\mvnw.cmd "-Dtest=CaseControllerIntegrationTests" test
.\mvnw.cmd -DskipTests compile
git diff --check
```

## 剩余未覆盖范围

- 非平台管理员只能访问可读空间用例的接口级断言。
- 人工评审 `reviewCase` 和执行结果 `executeCase` 的独立接口回归。
- 附件上传异常回滚的失败分支。
- AI 评审真实模型网络调用。
- 目录跨空间移动/绑定校验失败分支。

## 下一阶段建议

下一阶段建议切换到 `settings` 模块治理。`SettingsService` 当前体量较大，混合了环境配置、参数集、数据库连接和连接测试能力；适合先做职责巡检，再按配置类别拆分 domain/support。
