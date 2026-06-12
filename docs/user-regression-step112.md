# User 模块回归记录（第 112 步）

本记录用于收尾第 105-112 步 user 模块后端治理。本阶段不改接口路径、不改响应结构、不改数据库、不动前端，重点是在拆分 `UserService` 后补充接口和服务级回归保护。

## 已覆盖范围

### 用户管理接口

- 用户列表响应结构保持不变，并继续隐藏超级管理员。
- 用户创建后返回 `id`、`username`、`email`、`displayName`、`roleCode`、`status`、`workspaceCodes` 等关键字段。
- 用户更新后返回字段保持不变，并能更新邮箱、展示名、角色、状态和空间授权。
- 批量创建返回成功/失败计数及失败详情。
- 重置密码接口仍按当前默认密码逻辑返回成功。
- 非平台管理员执行用户管理写操作仍失败。
- 超级管理员不能通过普通用户管理接口维护或重置密码。

### 角色与空间授权

- `UserService.isPlatformAdmin(Long userId)` 仍能识别平台管理员。
- `UserService.isSuperAdmin(Long userId)` 仍能识别超级管理员。
- `UserService.listPlatformAdminUsers()` 仍返回平台管理员用户。
- workspace 成员列表中仍展示平台管理员虚拟成员，虚拟成员 `id` 保持负数语义，角色保持 `ADMIN`。
- `UserService.removeAdminFromWorkspace` 做了 service 级最小覆盖：从指定空间移除平台管理员虚拟成员后，当前实现会将该用户降级为普通成员并保留剩余空间授权。

## Mock 与测试策略

- Controller 级测试继续继承 `IntegrationTestSupport` 并使用 `MockMvc`。
- 通过 `SecurityContextHolder` 设置当前用户身份，避免引入真实登录流程。
- 对平台管理员虚拟成员使用临时 workspace 验证，避免依赖带有历史成员角色的演示空间数据。
- 对会改变种子用户角色的测试，在测试内显式恢复 `zhangli` 的平台管理员状态，降低 H2 共享测试库污染风险。

## 未覆盖范围

- 当前 `UserController` 没有用户详情接口和删除接口，本阶段不新增接口，因此未覆盖用户详情和删除普通用户的接口级回归。
- 登录认证、token、Spring Security 用户加载链路仍属于 auth 模块，未在 user 阶段扩展。
- `removeAdminFromWorkspace` 的完整 Controller 触发链路通过 workspace 虚拟成员删除间接发生，当前只做 user service 级稳定覆盖。
- 超级管理员启动初始化仍由 `SuperAdminBootstrap` 负责，本阶段未迁移也未新增专项测试。

## 后续建议

下一阶段建议切换到 auth 模块治理，先巡检再决定是否拆分：

- `AuthController`
- `AuthService`
- `PlatformUserDetailsService`
- `SuperAdminBootstrap`
- `CurrentUserContext` / `CurrentUserPrincipal` / 当前用户响应 DTO

优先梳理登录、登出、当前用户信息、认证用户加载、超级管理员初始化、当前用户 workspace 范围，以及 auth 与 `UserService` / `WorkspaceService` 的权限耦合点。
