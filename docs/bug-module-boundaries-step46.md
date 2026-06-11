# 缺陷管理模块职责边界巡检（Step 46）

## 范围

本轮巡检覆盖 `com.company.autoplatform.bug` 模块在第 40 到 45 步后的职责边界。目标是确认拆分后接口路径、响应结构、数据库和前端调用保持不变，同时让 `BugService` 回到门面定位。

## 当前职责边界

- `BugController`
  - 保持 HTTP 入口职责。
  - 继续只调用 `BugService`，没有直接依赖 domain/support/assembler。

- `BugService`
  - 保持门面职责。
  - 对外方法名和返回类型保持不变。
  - 负责调用 domain/support，并把实体交给 `BugResponseAssembler` 组装响应。

- `BugDomainService`
  - 缺陷主能力：列表、详情实体读取、创建、更新、统计。
  - 空间范围解析、可读校验、状态/优先级/严重程度归一化。
  - 缺陷编号生成和创建时初始流转记录。

- `BugWorkflowDomainService`
  - 缺陷工作流能力：分配处理人、状态流转、流转记录写入。
  - 保留原有可读/可写空间校验和同状态流转拦截行为。

- `BugCommentDomainService`
  - 评论列表和新增评论。
  - 保留评论可读/可写空间校验和评论人记录行为。

- `BugAttachmentSupport`
  - 附件上传、删除、下载。
  - 负责物理文件保存/删除、异常回滚、附件响应转换。

- `BugSourceContextSupport`
  - 从用例/报告创建缺陷时合并来源字段。
  - 缺陷详情中的来源上下文摘要：用例摘要、报告摘要、任务摘要。
  - 统一隔离缺陷模块对 `casecenter` 和 `execution` 的查询依赖。

- `BugResponseAssembler`
  - 缺陷列表、详情、评论、流转、活动时间线响应组装。
  - 保留原响应字段和活动标题文案。

## 巡检结论

- `BugService` 已接近纯门面，文件体积从原来的大服务降到约 4.9KB。
- 缺陷主 CRUD、附件、评论、工作流、来源上下文、响应组装已分离。
- 本阶段没有改变 Controller 调用链，没有引入新接口或数据库字段。
- 当前未继续拆 `BugResponseAssembler`，因为它主要是响应聚合逻辑，继续拆分收益小于风险。

## 已知边界

- `BugDomainService` 仍包含创建时的初始流转记录，这是创建缺陷的领域副作用，暂时保留。
- `BugResponseAssembler` 依赖多个 mapper 和外部服务用于详情聚合，这是展示层聚合职责，可以后续按测试覆盖情况再细拆。
- 当前阶段没有新增 bug 模块专项测试，主要依赖编译和现有集成测试验证 Bean 装配与基础回归。

## 下一阶段建议

下一步进入缺陷模块测试补强：新增最小接口/集成测试覆盖缺陷创建、列表筛选、详情来源上下文、评论、附件和状态流转，先保护已拆分后的行为，再考虑更细的内部整理。
