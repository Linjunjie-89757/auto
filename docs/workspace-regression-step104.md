# Workspace 模块阶段回归记录（第 104 步）

本记录用于收尾第 97-104 步 workspace 模块后端治理。本阶段不改接口路径、不改响应结构、不改数据库、不动前端，目标是让 `WorkspaceService` 保持对外门面，同时把空间主能力、成员能力和权限范围能力拆到更清晰的领域类中。

## 已完成拆分

| 类 | 当前职责 |
| --- | --- |
| `WorkspaceController` | 继续承接 `/api/workspaces` 接口入口，只调用 `WorkspaceService`。 |
| `WorkspaceService` | 保留原 public 方法作为门面，委托空间、成员和权限 support/domain service。 |
| `WorkspaceDomainService` | 空间主能力：列表项转换、创建、更新、删除、读取、owner 成员保障、删除前资源依赖校验。 |
| `WorkspaceMemberDomainService` | 成员管理：成员列表、单个/批量新增、角色更新、成员删除、owner 成员保护。 |
| `WorkspaceAccessSupport` | 空间权限与范围：可读/可写校验、目标空间解析、可读空间 id/code/entity 列表、平台管理员判断。 |

`WorkspaceService` 继续保留 `requireReadableWorkspace`、`requireWritableWorkspace`、`resolveTargetWorkspace`、`listReadableWorkspaceIds`、`listReadableWorkspaceCodes` 等 public 方法，是为了兼容接口自动化、用例中心、execution、bug、settings、AI 等模块的既有调用，不要求其他模块在本阶段直接依赖 workspace 内部 support。

## 已覆盖回归

`WorkspaceControllerIntegrationTests` 当前覆盖：

- `GET /api/workspaces` 空间列表响应字段。
- `GET /api/workspaces/switchable` 可切换空间列表和 `ALL` 视角字段。
- 创建、更新、删除无关联资源空间。
- 删除有关联资源空间失败。
- 非平台管理员不能创建、更新、删除空间。
- 成员列表包含平台管理员虚拟成员和真实空间成员。
- 新增成员、更新成员角色、删除成员。
- 批量新增成员。
- owner 成员不能降级、不能删除。
- 成员可读空间，非成员不可读空间。
- 成员通过 `WorkspaceService.requireWritableWorkspace` 保持当前可写校验语义。
- `listReadableWorkspaceIds`、`listReadableWorkspaceCodes` 返回成员可读空间。

`DbConnectionServiceTests` 继续作为 settings 模块对 workspace 权限门面的间接回归，覆盖数据库连接相关服务调用 workspace 可写校验的主链路。

## 未覆盖和风险

- 尚未单独新增 `WorkspaceServiceTests`，当前以 Controller 集成测试和跨模块 service 测试保护主要行为。
- `WorkspaceAccessSupport.requireWritableWorkspace` 当前语义等同可读校验；本阶段只记录和保护现状，不改变权限模型。
- 平台管理员虚拟成员删除会调用用户侧空间授权逻辑，本阶段未新增专门测试，避免扩大到 user 模块行为。
- 历史响应 message 文案和部分历史文档存在编码显示问题，本阶段不治理，避免改变响应文本或扩大文档 churn。
- 删除空间依赖校验已覆盖环境配置依赖，未逐一覆盖 case/task/report/bug/param 的所有依赖类型；这些仍由 mapper count 组合逻辑承担。

## 验证命令

```text
.\mvnw.cmd "-Dtest=WorkspaceControllerIntegrationTests,WorkspaceServiceTests,DbConnectionServiceTests" test
.\mvnw.cmd -DskipTests compile
git diff --check
```

## 本阶段提交

- `8e211d32` `refactor(workspace): extract workspace domain service`
- `bb21dc5b` `refactor(workspace): extract member domain service`
- `1ca5fa4b` `refactor(workspace): extract access support`
- `2519097a` `docs(workspace): record service boundaries`
- `da5c92c5` `test(workspace): cover workspace crud regression`
- `44886785` `test(workspace): cover member access regression`

## 后续建议

下一阶段建议切换到 `user` 模块治理。原因是 workspace 阶段已经暴露出用户模块与空间权限、平台管理员、虚拟成员、用户状态之间存在明显耦合；先巡检 `UserController`、`UserService`、用户实体/DTO/Mapper 和平台角色逻辑，再决定是否拆出用户主档、角色权限、空间授权等 domain/support。
