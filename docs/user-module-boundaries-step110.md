# User 模块职责边界记录（第 110 步）

本记录用于收尾第 105-110 步对 `user` 模块的后端治理。本阶段不改接口路径、不改响应结构、不改数据库、不动前端，目标是让 `UserService` 从综合 Service 收敛为门面，并保留被其他模块依赖的 public 方法。

## 当前职责边界

| 类 | 当前职责 |
| --- | --- |
| `UserController` | 继续承接 `/api/users` 接口入口，只调用 `UserService`。 |
| `UserService` | user 模块门面，保留原 public 方法；用户主能力、密码、角色、空间授权分别委托 domain/support。仍保留批量创建、重置密码和移除管理员空间的轻量编排。 |
| `UserDomainService` | 用户主能力：列表、创建、更新、用户读取、响应转换、邮箱唯一校验。 |
| `UserCredentialSupport` | 默认密码和密码编码，保持默认密码值与 `PasswordEncoder` 编码行为不变。 |
| `UserRoleSupport` | 平台角色归一、当前/指定用户管理员判断、角色可分配校验、超级管理员保护规则。 |
| `UserWorkspaceGrantSupport` | 用户工作空间授权：成员关系替换、用户可见空间读取、用户列表空间映射、平台管理员列表、移除管理员时剩余空间计算。 |
| `PlatformUserDetailsService` | 登录认证的用户加载入口，本阶段不迁移，避免影响 Spring Security 认证链路。 |
| `SuperAdminBootstrap` | 超级管理员初始化，本阶段不迁移，避免改变启动初始化语义。 |

## 公共方法保留为门面的原因

以下 `UserService` public 方法被 workspace、casecenter、bug、AI 等模块调用，当前阶段继续保留在 `UserService`，只在内部委托 support/domain：

- `requireUser`
- `requireAnyUser`
- `findActiveUser`
- `isPlatformAdmin(Long userId)`
- `isCurrentPlatformAdmin`
- `isSuperAdmin(Long userId)`
- `isCurrentSuperAdmin`
- `listPlatformAdminUsers`
- `requirePlatformAdmin`
- `removeAdminFromWorkspace`

这样可以避免把 `UserRoleSupport`、`UserWorkspaceGrantSupport` 等内部 support 泄漏到其他业务模块，跨模块调用面保持不变。

## 保持不变的行为

- `/api/users` 路径、请求体、响应结构不变。
- 用户列表仍隐藏超级管理员。
- `ADMIN` 输入仍归一为 `PLATFORM_ADMIN` 存储，响应仍展示为 `ADMIN`。
- `VIEWER` 输入仍按当前实现归一为普通成员。
- 创建用户和重置密码仍使用 `zhyt@2025` 默认密码并通过 `PasswordEncoder` 编码。
- 非平台管理员不能执行用户管理写操作。
- 非超级管理员不能创建/调整平台管理员。
- 超级管理员仍不能在成员管理列表中维护。
- 平台管理员默认拥有全部 active workspace，不写入普通 workspace membership。
- workspace 成员列表中的平台管理员虚拟成员仍通过 `UserService.listPlatformAdminUsers` 获取。
- 移除平台管理员虚拟成员仍通过 `UserService.removeAdminFromWorkspace` 作为兼容入口。

## 剩余风险

- 当前 `UserController` 没有用户详情和删除接口，本阶段不新增接口；后续测试应以现有接口为准，不能凭目标描述扩接口。
- `UserService.batchCreateUsers` 仍保留批量创建编排，后续如继续治理可拆 `UserBatchDomainService`，但当前优先补回归测试。
- `resetPassword` 仍在 `UserService` 中做实体写入，当前只把密码编码抽到 `UserCredentialSupport`；后续若需要可再抽 `UserCredentialDomainService`。
- `removeAdminFromWorkspace` 仍由 `UserService` 编排角色变更和授权替换，原因是它同时承担 workspace 虚拟成员兼容入口；后续如要继续收敛，需要先补强专项测试。
- 登录认证和超级管理员启动初始化仍在 auth 模块，本阶段不动，避免扩大安全链路风险。

## 建议下一步

第 111 步优先补 `UserControllerIntegrationTests` 和必要的 `UserServiceTests`，覆盖现有接口：用户列表、创建、更新、批量创建、重置密码、替换空间角色，以及非管理员写操作失败。由于当前没有详情/删除接口，测试目标应记录为“现有接口无对应入口，暂不覆盖”。
