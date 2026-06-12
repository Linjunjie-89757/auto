# Workspace 模块职责边界记录（第一百零一步）

本记录用于收尾第 97-101 步对 `workspace` 模块的后端治理。当前阶段不改接口路径、不改响应结构、不改数据库、不动前端，目标是让 `WorkspaceService` 从大 Service 收敛为门面，同时保留它作为全后端空间权限入口。

## 当前职责边界

| 类 | 当前职责 |
| --- | --- |
| `WorkspaceController` | 保持 `/api/workspaces` 对外接口入口，继续调用 `WorkspaceService`。 |
| `WorkspaceService` | workspace 模块门面，保留所有原 public 方法，委托空间主能力、成员能力和权限范围能力。 |
| `WorkspaceDomainService` | 承接空间主能力：空间读取、创建、更新、删除、响应转换、owner 成员保障、删除前资源依赖校验。 |
| `WorkspaceMemberDomainService` | 承接成员管理：成员列表、单个/批量添加、角色更新、删除、owner 保护和成员响应转换。 |
| `WorkspaceAccessSupport` | 承接空间权限与范围：可读/可写空间校验、目标空间解析、可读空间列表、平台管理员判断。 |
| `WorkspaceScope` | 保持 header、ALL 和默认空间 code 的轻量规范化工具。 |

## 公共权限方法为什么仍保留在 WorkspaceService

`WorkspaceService` 的以下方法被接口自动化、用例中心、execution、bug、settings、AI 等模块广泛依赖：

- `requireReadableWorkspace`
- `requireWritableWorkspace`
- `resolveTargetWorkspace`
- `listReadableWorkspaceEntities`
- `listReadableWorkspaceIds`
- `listReadableWorkspaceCodes`
- `isSuperAdmin`
- `isPlatformAdmin`
- `requirePlatformAdmin`
- `requireWorkspace`
- `requireWorkspaceById`

为避免跨模块调用面变化，本阶段只让这些 public 方法在 `WorkspaceService` 中保留门面委托，不要求其他模块改为直接依赖 `WorkspaceAccessSupport` 或 `WorkspaceDomainService`。

## 保持不变的行为

- `/api/workspaces` 路径、请求体和响应结构不变。
- 空间创建、更新、删除仍要求平台管理员。
- 删除空间前仍校验成员、用例、任务、报告、缺陷、环境配置、参数集等关联数据。
- 成员列表仍合并平台管理员虚拟成员和空间成员。
- 批量新增成员仍复用单个新增语义。
- owner 成员不能被删除或降级的保护逻辑不变。
- `ALL` 视角下仍要求显式目标空间。
- 其他模块仍通过 `WorkspaceService` 获取空间权限能力。

## 剩余风险

- 当前 workspace 专属 Controller/Service 回归测试尚未补齐，后续第 102-103 步需要补空间 CRUD、成员管理、权限范围和删除保护。
- 部分历史响应文案存在编码显示问题，本阶段未治理，避免扩大响应文本变更范围。
- `WorkspaceDomainService` 内仍保留平台管理员判断用于空间 CRUD；这是为避免 `WorkspaceAccessSupport -> WorkspaceDomainService` 与反向依赖形成 Spring 循环。后续若要进一步收敛，可引入更底层的 `WorkspacePrincipalSupport`，但当前阶段不建议继续拆。

## 验证命令

```text
.\mvnw.cmd "-Dtest=WorkspaceServiceTests,WorkspaceControllerIntegrationTests,DbConnectionServiceTests" test
.\mvnw.cmd -DskipTests compile
git diff --check
```

## 下一步

下一步执行第 102 步：补 workspace 第一批接口/集成回归测试，优先覆盖空间列表、可切换空间、创建、更新、删除、删除保护和非平台管理员禁止写操作。
