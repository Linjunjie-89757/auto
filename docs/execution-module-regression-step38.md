# execution 模块阶段回归记录（第三十八步）

本轮是 execution 模块任务、报告、附件治理后的阶段回归记录。不改接口、不改数据库、不动前端，不继续拆分服务。

## 已完成拆分

| 阶段 | 内容 |
| --- | --- |
| 第三十二步 | 抽出 `ExecutionTaskDomainService`，承接任务 CRUD、筛选分页、状态流转和任务空间权限校验。 |
| 第三十三步 | 抽出 `ExecutionReportDomainService`，承接报告 CRUD、内容更新、详情转换、报告空间校验和 legacy attachments 兼容。 |
| 第三十四步 | 抽出 `ExecutionReportAttachmentSupport`，承接附件上传、下载、删除、附件查询和物理文件处理。 |
| 第三十五步 | 记录 execution 模块职责边界。 |
| 第三十六步 | 补报告接口最小集成回归。 |
| 第三十七步 | 补附件接口主链路集成回归。 |

## 当前回归覆盖

`ExecutionControllerIntegrationTests` 当前覆盖：

- 任务列表不传分页参数时返回全部匹配任务。
- 任务列表分页参数返回分页元信息。
- 任务列表支持 keyword/status/engineType 组合筛选。
- 普通成员只能看到可读空间任务。
- 报告创建、列表、详情和内容更新的响应结构。
- 报告创建时校验报告空间必须与关联任务空间一致。
- 报告附件上传、报告详情附件展示、下载和删除主链路。

`ExecutionServiceTests` 当前覆盖：

- 任务列表无分页时保持 `selectList` 行为。
- 任务列表有分页时使用数据库分页。
- 非法任务状态仍抛业务异常。
- 非法分页参数会先归一化再查询。

## 本阶段验证命令

```text
.\mvnw.cmd "-Dtest=ExecutionServiceTests,ExecutionControllerIntegrationTests" test
.\mvnw.cmd -DskipTests compile
git diff --check
```

## 验收结论

- `ExecutionController` 仍只依赖 `ExecutionService`。
- `ExecutionService` 已接近门面：任务、报告、附件分别委托给独立服务。
- 当前拆分没有改变接口路径、请求结构、响应结构或数据库结构。
- 任务筛选分页、报告 CRUD、附件主链路均有接口级或服务级回归保护。

## 剩余风险

- 附件上传异常回滚路径还没有单独测试。
- 报告删除时级联删除附件实体和物理文件还没有专门接口级断言。
- 报告 legacy attachments 兼容展示还没有专门测试。
- execution 模块部分历史中文文案仍存在编码显示问题，建议单独治理，不混入职责拆分。

## 下一步建议

下一阶段建议暂停 execution 模块拆分，转入缺陷管理后端模块职责巡检。若仍继续 execution，优先补附件异常回滚和报告删除级联行为的窄范围测试，而不是继续拆新类。
