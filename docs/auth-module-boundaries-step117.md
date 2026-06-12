# Auth 模块职责边界记录（第 117 步）

本记录用于收尾第 113-117 步对 auth 模块的阶段性治理。本阶段不改接口路径、不改响应结构、不改数据库、不动前端，也不改变 Spring Security 配置行为。

## 当前职责边界

| 类 | 当前职责 |
| --- | --- |
| `AuthController` | 继续承接 `/api/auth/login`、`/api/auth/me`、`/api/auth/logout` 入口。登录时仍通过 `AuthenticationManager` 认证并写入 session security context；登出仍清理 session 和 `SecurityContextHolder`。 |
| `AuthService` | auth 门面服务，目前只保留 `currentUser()` public 入口，委托 `AuthCurrentUserSupport` 构建当前用户响应。 |
| `AuthCurrentUserSupport` | 构建 `/api/auth/me` 和登录成功后的当前用户响应，负责角色展示值和 readable workspace code 读取。 |
| `PlatformUserDetailsService` | Spring Security `UserDetailsService` 入口，保留框架需要的 public contract，内部委托 `PlatformUserDetailsSupport`。 |
| `PlatformUserDetailsSupport` | 认证用户加载细节：按 username/email 查用户、状态校验、密码存在校验、构建 `CurrentUserPrincipal`。 |
| `SuperAdminBootstrap` | 应用启动时的 `ApplicationRunner` 入口，保留配置注入和启动调用。 |
| `SuperAdminBootstrapSupport` | 超级管理员初始化细节：解析默认密码、创建或更新超级管理员、清理超级管理员普通 workspace membership。 |
| `SuperAdminProperties` | 启动配置值的轻量载体。 |
| `CurrentUserContext` | 从 Spring Security context 读取当前登录用户，保持静态工具入口不变。 |
| `CurrentUserPrincipal` | 当前登录用户 principal，同时实现 `UserDetails`。 |
| `SecurityConfig` | Spring Security 过滤链、异常响应、CORS、`PasswordEncoder`、`AuthenticationManager` 配置。本阶段不迁移。 |

## 保持不变的行为

- `/api/auth/login` 路径、请求体、响应结构不变。
- `/api/auth/me` 响应字段不变：`id`、`username`、`displayName`、`roleCode`、`workspaceCodes`。
- `/api/auth/logout` 仍清理 session 和当前 security context。
- 登录认证仍由 Spring Security `AuthenticationManager` 驱动。
- 用户加载仍支持 username 或 email。
- 禁用用户或不存在用户仍不能登录。
- 未设置密码用户仍按当前异常语义失败。
- `CurrentUserPrincipal` authorities 仍按 `ROLE_` + `platformRole` 构建。
- 超级管理员启动初始化仍保证唯一超级管理员配置值、active 状态和密码兜底。
- 超级管理员仍不保留普通 workspace membership。
- 平台管理员和超级管理员的 current user workspace 范围仍通过 `WorkspaceService.listReadableWorkspaceCodes()` 读取。

## 暂不继续拆分的原因

- `AuthController.login` 里写 session security context 是 Spring Security 登录入口语义，继续保留在 controller 可降低行为漂移风险。
- `SecurityConfig` 是安全过滤链配置，不在本阶段拆分，避免扩大认证和授权面风险。
- `CurrentUserContext` 是全局静态读取入口，已被多个模块使用；本阶段不改调用方式。
- `CurrentUserPrincipal` 是 Spring Security contract 对象，不拆成业务 DTO。

## 剩余风险

- 当前 auth 还缺少专项 Controller 集成测试，下一步需要覆盖登录成功/失败、`/api/auth/me`、禁用用户、超级管理员初始化和平台管理员 workspace 范围。
- 登录失败的具体 HTTP 状态和响应体由 Spring Security 异常处理链决定，测试应按当前实现断言，不额外改安全配置。
- 超级管理员初始化依赖应用启动 runner，专项测试需要避免污染共享 H2 测试库。

## 下一步

第 118 步补 `AuthControllerIntegrationTests` 或等价 auth 回归测试，覆盖第一批登录和当前用户主链路，并补 `docs/auth-regression-step118.md` 记录已覆盖范围和剩余风险。
