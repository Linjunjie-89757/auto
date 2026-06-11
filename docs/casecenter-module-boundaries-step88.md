# 用例中心模块职责边界记录（第八十八步）

本记录用于收尾第 82-88 步对用例中心 `casecenter` 模块的拆分治理。当前阶段不改接口路径、不改响应结构、不改数据库、不动前端；重点是让 `CaseService` 从大 Service 收敛为门面 + 少量执行/人工评审编排。

## 当前职责边界

| 类 | 当前职责 |
| --- | --- |
| `CaseController` | 保持 `/api/cases` 对外接口入口，继续调用 `CaseService`；AI 评审入口委托 `CaseReviewDomainService`。 |
| `CaseService` | 作为用例中心门面，委托用例 CRUD、目录、批量、附件能力；暂时保留人工评审 `reviewCase` 和执行结果 `executeCase` 的最小状态更新逻辑。 |
| `CaseDomainService` | 承接用例主 CRUD、列表筛选分页、详情/摘要响应转换、用例读取和用例可读权限校验。 |
| `CaseDirectoryDomainService` | 承接目录列表、目录树构建、目录创建/重命名/移动/删除、目录归属校验和后代目录收集。 |
| `CaseBatchDomainService` | 承接批量移动、批量更新、批量删除，以及批量操作所需的用例可写校验和批量摘要响应转换。 |
| `CaseExecutionAttachmentSupport` | 承接执行附件上传、删除、下载、附件实体读取、物理文件保存和异常回滚。 |
| `CaseReviewDomainService` | 承接 `/api/cases/{id}/ai-review` 的最小编排：读取用例详情并调用 `AiCaseService.reviewSavedCase(...)`。 |
| `CaseExecutionAttachmentStorageService` | 保持执行附件物理文件存储、读取、删除、文件大小和扩展名校验。 |

## 已完成拆分

- 第 83 步：抽出 `CaseDomainService`，迁移用例主 CRUD、列表、详情和响应转换。
- 第 84 步：抽出 `CaseDirectoryDomainService`，迁移目录树和目录管理逻辑。
- 第 85 步：抽出 `CaseBatchDomainService`，迁移批量移动、批量更新、批量删除。
- 第 86 步：抽出 `CaseExecutionAttachmentSupport`，迁移执行附件上传、删除、下载和回滚逻辑。
- 第 87 步：抽出 `CaseReviewDomainService`，收敛 AI 评审入口编排。
- 第 88 步：清理 `CaseService` 中已经无调用的目录、批量、附件、主 CRUD 残留 helper。

## 保持不变的行为

- `/api/cases` 路径、请求参数、响应结构保持不变。
- 用例列表分页、筛选、目录过滤和空间可读范围保持不变。
- 创建/更新用例时空间归属、owner 校验、目录归属校验保持不变。
- 目录删除仍校验子目录和已绑定用例。
- 批量操作仍不支持跨空间混合提交。
- 执行附件上传失败时仍回滚已插入附件实体和已保存文件。
- AI 评审仍使用 `AiCaseService.reviewSavedCase(...)`，不改 AI 模块行为。

## 剩余边界和风险

- `CaseService` 仍保留 `reviewCase` 和 `executeCase`，这两个属于人工评审状态更新和执行结果状态更新，后续可拆为 `CaseExecutionDomainService` 或 `CaseManualReviewDomainService`，但本阶段先不扩大范围。
- `CaseDomainService` 和 `CaseBatchDomainService` 都有摘要响应转换逻辑，后续可以抽轻量 `CaseResponseAssembler` 收敛重复。
- 当前用例中心缺少专门的 Controller 集成测试；第 89-90 步应优先补主链路回归，而不是继续拆服务。
- `CaseController` 中部分历史响应消息存在编码显示问题，本阶段未作为治理目标处理，避免改动响应文案范围。

## 验证命令

```text
.\mvnw.cmd "-Dtest=AutoPlatformApplicationTests,BugControllerIntegrationTests" test
.\mvnw.cmd -DskipTests compile
git diff --check
```

## 下一阶段建议

下一步建议执行第 89 步：新增用例中心第一批接口/集成回归测试，优先覆盖创建、列表分页/筛选、详情、更新、删除和目录创建/重命名/移动/删除。测试保护到位后，再进入附件、批量和 AI 评审入口的第二批回归。
