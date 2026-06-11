# Settings 模块职责边界记录（第九十六步）

本记录用于收尾第 91-96 步对 `settings` 模块的后端治理。当前阶段不改接口路径、不改响应结构、不改数据库、不动前端，目标是让 `SettingsService` 从大 Service 收敛为门面。

## 当前职责边界

| 类 | 当前职责 |
| --- | --- |
| `SettingsController` | 保持 `/api/settings` 对外接口入口，继续调用 `SettingsService`。 |
| `SettingsService` | settings 模块门面，委托环境配置、参数集、数据库连接和连接测试能力。 |
| `SettingsEnvironmentDomainService` | 承接环境配置列表筛选、创建、更新、状态更新、删除、workspace 权限校验和响应转换。 |
| `SettingsParamSetDomainService` | 承接参数集列表筛选、创建、更新、状态更新、删除、workspace 权限校验和响应转换。 |
| `DbConnectionDomainService` | 承接数据库连接列表筛选、创建、更新、状态更新、删除、密码加密保存、workspace 权限校验和响应转换。 |
| `DbConnectionTestSupport` | 承接数据库连接测试、驱动加载、`DriverManager` 超时设置、已保存连接密钥解密和测试失败异常转换。 |
| `DbConnectionCrypto` | 保持数据库连接密码 AES/GCM 加密和解密能力。 |

## 保持不变的行为

- `/api/settings/envs`、`/api/settings/params`、`/api/settings/db-connections` 和 `/api/settings/db-connections/test` 路径与响应结构不变。
- 环境配置、参数集、数据库连接仍按 workspace 可读/可写权限校验。
- 列表 keyword/type/status 筛选语义不变。
- 数据库连接密码仍只返回 `passwordConfigured`，不暴露明文或密文。
- 数据库连接创建/更新仍保留原加密行为，空密码更新仍保留旧密码。
- 连接测试仍只支持当前已有的 JDBC 行为，不扩展数据库类型或协议。
- 连接测试失败仍抛 `BadRequestException`，错误信息沿用原语义。

## 回归覆盖

当前 `DbConnectionServiceTests` 覆盖：

- 环境配置、参数集、数据库连接列表筛选。
- 环境配置创建、更新、状态更新、删除。
- 参数集创建、更新、状态更新、删除。
- 数据库连接创建、更新、状态更新、删除。
- 数据库连接密码加密、响应不暴露密码、空密码更新保留旧密码。
- H2 连接测试成功。
- 连接测试缺失驱动和空 JDBC URL 失败分支。

## 剩余风险

- 当前 settings 测试以 Service 级集成为主，尚未补 Controller 级 MockMvc 响应消息断言。
- 错误响应文案中历史编码问题未在本阶段治理，避免扩大响应文本变更范围。
- 连接测试仍会实际打开本地 JDBC 连接；本阶段未引入 mock driver 或测试专用连接工厂。

## 下一阶段建议

settings 阶段完成后，建议切换到 `workspace` 模块治理。该模块承接空间、成员、权限范围等核心能力，优先先做只读职责巡检，再决定是否拆出 workspace 基础信息、成员管理和权限校验 support。
