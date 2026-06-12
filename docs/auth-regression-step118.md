# Auth 模块回归记录（第 118 步）

本记录用于收尾第 113-118 步 auth 模块后端治理。本阶段不改接口路径、不改响应结构、不改数据库、不动前端，不改变 Spring Security 配置行为。

## 已覆盖范围

- `POST /api/auth/login` 登录成功主链路。
- 登录成功响应结构保持不变，包含 `success`、`message`、`data.id`、`data.username`、`data.displayName`、`data.roleCode`、`data.workspaceCodes`。
- 平台管理员登录后 `roleCode` 仍返回 `ADMIN`，`workspaceCodes` 仍包含可读空间。
- `POST /api/auth/login` 密码错误仍返回 401，并保持失败响应结构。
- `GET /api/auth/me` 在已认证上下文下返回当前用户响应字段。
- 禁用用户不能登录，仍返回 401。
- 应用启动后存在 active 超级管理员，角色为 `SUPER_ADMIN`，密码非空。

## 测试策略

- 新增 `AuthControllerIntegrationTests`，继承 `IntegrationTestSupport` 并使用 `MockMvc`。
- 登录成功和失败走真实 `AuthenticationManager`，不 mock Spring Security。
- 禁用用户测试使用临时用户数据，避免修改演示用户状态。
- `/api/auth/me` 复用 `IntegrationTestSupport` 默认认证上下文，保护当前用户响应构建行为。
- 超级管理员初始化通过当前测试上下文启动后的数据库状态断言，不直接重复调用 runner。

## 未覆盖范围

- 未覆盖真实浏览器 session 的跨请求持久化；当前只验证登录接口响应和 `/api/auth/me` 当前认证上下文。
- 未覆盖 logout 后同一 session 再访问 `/api/auth/me` 的端到端断言。
- 未覆盖空用户名、空密码等 Bean Validation 分支。
- 未覆盖 `CurrentUserContext` 在匿名/无效 principal 下的所有异常分支。
- 未覆盖超级管理员配置变更后的覆盖更新细节，避免在共享 H2 测试库中反复改动全局管理员。

## 后续建议

auth 阶段已经完成第一批边界拆分和主链路回归。下一阶段建议切换到后端剩余大类巡检，优先评估接口自动化残余大类：

- `ApiAiCaseGenerationService`
- `ApiScenarioDomainService`
- `ApiDefinitionDomainService`
- `ApiAutomationController`

先做只读巡检，判断这些类是否确实还存在可低风险拆分的职责块；如果拆分收益不明显，优先补边界文档和回归测试，而不是继续按行数硬拆。
