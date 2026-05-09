# 自动化测试工作平台

这是一个面向公司内部测试团队的自动化测试平台仓库，当前采用前后端分离架构：

- 前端：`Vue 3 + TypeScript + Element Plus`
- 后端：`Spring Boot 3 + Maven + Java 21`

## 当前进度

目前已经完成一轮从“演示原型”到“可持续开发底座”的推进：

- 后端从内存种子数据切换为持久化数据层
- 使用 `H2 File` 作为本地默认数据库，保留后续切换 `MySQL` 的入口
- 保留并落地了 `X-Workspace-Code` + `ALL` 全部空间视角规则
- 工作空间、成员、用例、执行任务、执行报告、缺陷、环境、参数集已接入数据库
- 缺陷模块已支持创建、编辑、指派、流转、评论、从用例创建、从报告创建
- 系统设置中的环境管理、参数管理已支持真实持久化 CRUD
- 已接入真实登录态、当前用户上下文、空间可见范围和基础读写权限控制

## 登录与权限

当前版本使用服务端 Session 维护登录态：

- 登录接口：`POST /api/auth/login`
- 当前用户接口：`GET /api/auth/me`
- 退出登录接口：`POST /api/auth/logout`

默认演示账号密码均为：

```text
123456
```

内置账号示例：

- `zhangli`：平台管理员，可查看全部空间并维护空间/成员
- `chennan`：普通成员，可访问 `account-open`
- `liping`：普通成员，可访问 `account-open`
- `zhaofeng`：普通成员，可访问 `trade-core`
- `wangxin`：只读成员，仅可查看 `risk-control`

权限规则：

- 所有接口默认都需要先登录
- `ALL` 是全局视角，不是数据库实体
- 平台管理员可查看全部空间
- 普通用户在 `ALL` 视角下，只会聚合自己有权限访问的空间
- `VIEWER` 角色只有读取权限，不能新增、编辑、删除、流转
- 工作空间成员维护默认要求平台管理员或该空间 `ADMIN`

## 空间规则

- `X-Workspace-Code` 是统一的空间上下文请求头
- 具体空间只查询该空间数据
- `ALL` 是全局视角，不是数据库实体
- 在 `ALL` 视角下新增业务数据时，必须显式指定目标空间
- 所有业务数据仍然只归属一个真实工作空间

## 后端启动

如果当前终端还没有识别 `java` / `mvn`，先注入环境变量：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$env:MAVEN_HOME='C:\Program Files\Apache\apache-maven-3.9.15'
$env:Path="$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;" + $env:Path
```

启动后端：

```powershell
cd server
mvn spring-boot:run
```

默认本地数据库：

```text
jdbc:h2:file:./data/auto-platform;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE
```

如果后续切换到 MySQL，可通过环境变量覆盖：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `DB_DRIVER`

## 前端启动

```powershell
cd web
npm.cmd install
npm.cmd run dev
```

## 本地访问地址

- 前端：[http://127.0.0.1:4173](http://127.0.0.1:4173)
- 后端：[http://127.0.0.1:8080](http://127.0.0.1:8080)
- H2 Console：[http://127.0.0.1:8080/h2-console](http://127.0.0.1:8080/h2-console)

## 当前建议的下一步

1. 把系统设置前端改成完整的真实 CRUD，而不是只展示列表。
2. 给用例、缺陷、设置模块补分页、筛选和接口测试。
3. 给权限模型继续细化到按钮级或操作级。
4. 在底座稳定后，再推进统一执行中心和 API / Web / APP 执行器接入。
## CI baseline

The repository keeps a minimal GitHub Actions pipeline in `.github/workflows/ci.yml`.

- Triggers: `push`, `pull_request`, `workflow_dispatch`
- Scheduled smoke patrol: every weekday at `02:00` China Standard Time (`18:00 UTC` on the previous day)
- Backend verification: `cd server && mvn test`
- Frontend build: `cd web && npm.cmd run build`
- Fixed smoke suite: `cd web && npm.cmd run smoke`
- Failure artifacts: `web/test-results` and `web/playwright-report`

Recommended local verification order:

```powershell
cd server
mvn test

cd ..\web
npm.cmd install
npm.cmd run build
npm.cmd run smoke
```

MySQL local integration remains a developer-side verification path and is not required by the first CI gate.

## MySQL local smoke

For local MySQL regression, make sure Docker MySQL is running and run the suites sequentially:

```powershell
cd web
npm.cmd run smoke:mysql:cases
npm.cmd run smoke:mysql:tasks
npm.cmd run smoke:mysql:reports
```

For a CRUD-only quick pass:

```powershell
cd web
npm.cmd run smoke:mysql:crud
```

For the full local MySQL smoke:

```powershell
cd web
npm.cmd run smoke:mysql
```

To verify only the report flow against MySQL:

```powershell
cd web
npm.cmd run smoke:mysql:reports
```
