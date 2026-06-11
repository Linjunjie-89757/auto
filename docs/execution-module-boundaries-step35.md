# execution 模块职责边界验收（第三十五步）

本轮是 execution 模块任务、报告、附件拆分后的阶段收尾验收。不改接口、不改数据库、不动前端，不继续拆新服务。

## 当前职责边界

| 类 | 职责 |
| --- | --- |
| `ExecutionController` | 保持 HTTP 路由入口，接收请求参数，统一包装响应。Controller 仍只调用 `ExecutionService`。 |
| `ExecutionService` | execution 模块门面，保留原有 public 方法入口，按职责委托给任务、报告、附件服务。 |
| `ExecutionTaskDomainService` | 任务列表、详情、创建、更新、删除、状态流转、任务摘要和任务空间权限校验。 |
| `ExecutionReportDomainService` | 报告列表、详情、创建、更新、删除、内容更新、报告摘要/详情转换、报告空间权限校验，以及报告详情中的 legacy attachments 兼容展示。 |
| `ExecutionReportAttachmentSupport` | 报告附件上传、删除、下载、附件查询、物理文件保存/删除、上传异常回滚。 |
| `ReportAttachmentStorageService` | 附件物理存储读写和删除。 |

## 调用链检查

- `ExecutionController` 没有直接调用 domain service、support 或 mapper。
- `ExecutionService` 保持门面定位，任务方法委托 `ExecutionTaskDomainService`，报告方法委托 `ExecutionReportDomainService`，附件方法委托 `ExecutionReportAttachmentSupport`。
- `ExecutionReportAttachmentSupport` 通过 `ExecutionReportDomainService` 查询报告和校验报告可读空间，避免重复实现报告空间校验。
- 附件上传、删除后仍由附件 support 更新 `ReportEntity.updatedAt`。
- 删除报告时仍由 `ExecutionReportDomainService` 删除附件实体和物理文件，保持原行为不变。

## 回归覆盖

当前阶段最小回归命令：

```text
.\mvnw.cmd "-Dtest=ExecutionServiceTests,ExecutionControllerIntegrationTests" test
.\mvnw.cmd -DskipTests compile
git diff --check
```

覆盖点：

- `ExecutionControllerIntegrationTests` 覆盖任务接口列表、分页、筛选和空间可读范围。
- `ExecutionServiceTests` 覆盖任务分页、筛选和状态参数校验。
- Spring 上下文启动验证了 `ExecutionController -> ExecutionService -> ExecutionTaskDomainService / ExecutionReportDomainService / ExecutionReportAttachmentSupport` 的依赖注入链路。

## 剩余风险

- 报告 CRUD 和附件上传/下载目前主要依赖编译和上下文启动保护，缺少专门接口级断言。
- 附件上传异常回滚逻辑已经迁移到 `ExecutionReportAttachmentSupport`，但还没有窄范围单元测试覆盖回滚路径。
- 旧代码中仍有部分历史中文乱码文案，后续如治理文案，建议单独开一轮编码/文案修复，不混入职责拆分。

## 后续建议

下一阶段优先补 execution 模块报告和附件的接口级回归测试，而不是继续拆服务。建议先覆盖报告 CRUD，再覆盖附件上传、下载、删除和异常回滚。
