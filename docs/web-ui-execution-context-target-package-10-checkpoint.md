# Web UI 自动化目标包 10：公共执行上下文设计检查点

> 记录时间：2026-06-20  
> 目的：把“环境配置 + 变量参数”这块的设计决策固化下来，避免目标包 10 分阶段开发时丢失上下文。

## 一、核心结论

Web UI 自动化不再单独做一套私有变量/环境体系，而是接入平台已有“配置中心”：

- 环境配置复用 `tb_env_config`
- 参数/变量集复用 `tb_param_set`
- API 自动化、Web UI 自动化、后续 APP 自动化共享同一个配置中心能力
- 执行时统一抽象为“执行上下文”，包括环境、变量集、运行时参数、内置变量、步骤提取变量

这样做的原因：

- 不重复造表，不把 Web UI 做成孤岛
- 符合平台已有“配置中心”的方向
- 后续 API / Web UI / APP / CI 执行可以共用变量语法和配置选择方式
- 便于报告里追溯本次执行到底用了哪套环境和变量

## 二、目标包 10 推荐名称

推荐名称：

**目标包 10：配置中心公共执行上下文接入 Web UI 自动化**

也可以简称：

**目标包 10：公共环境/变量接入**

## 三、第一期必须落地的范围

第一期目标不是把配置中心全部重做，而是让 Web UI 自动化真正用上公共环境和变量。

### 目标 1：定义 Web UI 公共环境结构

在 `tb_env_config` 中使用：

- `envType = WEB_UI`
- `baseUrl` 继续作为环境基础地址
- Web UI 专项配置放在 `config_json`

建议 `config_json` 结构：

```json
{
  "browserType": "CHROMIUM",
  "headless": true,
  "defaultTimeoutMs": 10000,
  "viewport": {
    "width": 1440,
    "height": 900
  },
  "ignoreHttpsErrors": false,
  "defaultVariableSetId": 12,
  "variables": []
}
```

第一期可以先支持这些字段：

- `browserType`
- `headless`
- `defaultTimeoutMs`
- `viewport.width`
- `viewport.height`
- `ignoreHttpsErrors`
- `defaultVariableSetId`

`variables` 可以先预留，是否启用看现有配置中心表单复杂度。

### 目标 2：定义 Web UI 公共变量集结构

在 `tb_param_set` 中使用：

- `paramType = WEB_UI_VARIABLE_SET`
- 变量明细放在 `content_json`

建议 `content_json` 结构：

```json
[
  {
    "name": "USERNAME",
    "value": "admin",
    "description": "登录账号",
    "sensitive": false
  },
  {
    "name": "PASSWORD",
    "value": "123456",
    "description": "登录密码",
    "sensitive": true
  }
]
```

变量语法统一使用：

```text
{{VARIABLE_NAME}}
```

暂定转义规则：

```text
\{{VARIABLE_NAME}}
```

表示输出字面量 `{{VARIABLE_NAME}}`，不做变量替换。

### 目标 3：Web UI 执行支持公共环境

Web UI 执行、调试执行、批量执行、CI 触发都应能读取 `tb_env_config` 中 `envType = WEB_UI` 的环境。

执行时至少使用：

- `baseUrl`
- `browserType`
- `headless`
- `defaultTimeoutMs`
- `viewport`
- `ignoreHttpsErrors`

兼容策略：

- 旧的 Web UI 私有环境表/接口短期保留，不立刻删除
- 新执行链优先接公共配置中心环境
- 前端环境选择逐步切到公共配置中心

### 目标 4：Web UI 执行支持公共变量集

Web UI 运行请求需要逐步支持：

- `environmentId`
- `variableSetId`
- `runtimeVariables`

变量替换范围：

- `OPEN` 的 URL
- `locatorValue`
- `inputValue`
- 文件上传路径
- 断言期望值
- 后续步骤提取结果

第一期内置变量建议：

- `TIMESTAMP`
- `RANDOM_STRING`
- `TODAY`

### 目标 5：报告保存执行上下文快照

执行报告不能只保存环境 ID 和变量集 ID，否则变量集后续被修改后，历史报告会失真。

第一期至少保存脱敏后的快照：

```json
{
  "environment": {
    "id": 1,
    "name": "测试环境",
    "baseUrl": "https://test.example.com",
    "browserType": "CHROMIUM",
    "headless": true
  },
  "variables": {
    "USERNAME": "admin",
    "PASSWORD": "******"
  }
}
```

敏感值要求：

- 报告里不展示真实敏感值
- 后端日志不打印真实敏感值
- 前端详情页只展示掩码

## 四、变量优先级

最终变量合并优先级：

```text
内置变量
< 项目默认变量
< 环境变量
< 变量集变量
< 运行时变量
< 步骤提取变量
```

说明：

- 同名变量由后面的层级覆盖前面的层级
- 第一期可以先实现：内置变量 < 环境变量 < 变量集变量 < 运行时变量
- 项目默认变量、步骤提取变量可以作为第二期补齐

## 五、敏感变量更新语义

必须避免“空值陷阱”。

错误做法：

- 敏感变量保存空字符串就默认沿用旧值

问题：

- 用户无法表达“我要把这个敏感变量设为空字符串”
- 也无法区分“不修改”和“清空”

推荐做法：

```json
{
  "name": "PASSWORD",
  "value": "new-value",
  "sensitive": true,
  "valueChanged": true
}
```

语义：

- `valueChanged = false`：不修改旧值
- `valueChanged = true` 且 `value = ""`：设置为空字符串
- `valueChanged = true` 且 `value = null`：清空为 null
- 字段缺失：按 PATCH 语义，不更新该字段

## 六、环境与变量集绑定

环境应支持绑定默认变量集。

第一期不建议立刻加数据库字段，先放在 `tb_env_config.config_json.defaultVariableSetId`：

```json
{
  "defaultVariableSetId": 12
}
```

执行时规则：

- 如果运行请求传了 `variableSetId`，优先使用请求里的变量集
- 如果没有传，则使用环境绑定的 `defaultVariableSetId`
- 如果两者都没有，则只使用内置变量、环境变量、运行时变量

后续如果这个字段使用稳定，再考虑迁移为正式列。

## 七、第二期增强，不放进第一期硬做

这些点有价值，但目标包 10 第一期不建议全部塞进去。

### 变量集继承

可引入：

```json
{
  "parentSetId": 10
}
```

规则：

- 父变量集先合并
- 子变量集覆盖父变量集

第一期替代方案：

- 支持“复制变量集”
- 暂不做递归继承，降低复杂度

### 项目默认变量

用于减少不同环境里的重复变量。

变量优先级位置：

```text
内置变量 < 项目默认变量 < 环境变量
```

第一期可以先在设计中保留，不强行实现。

### APP 自动化配置

后续 APP 自动化可以复用同一个 `tb_env_config`，使用：

- `envType = APP`

建议 `config_json`：

```json
{
  "platformName": "iOS",
  "deviceName": "iPhone 15",
  "udid": "auto",
  "appPath": "/path/to/app.ipa",
  "automationName": "XCUITest",
  "noReset": true,
  "wdaLocalPort": 8100
}
```

当前目标包 10 只预留方向，不实现 APP。

### 变量版本和完整快照

第一期保存脱敏快照即可。

第二期可以增强：

- 保存变量集版本号
- 保存完整执行上下文 JSON
- 支持报告按执行时快照回放
- 支持变量集变更审计

## 八、配置中心前端改造方向

配置中心不需要推翻重做，但现有“变量参数”和“环境配置”需要从单点配置升级为更适合自动化执行的结构。

### 参数配置页面

建议改成变量集编辑体验：

- 表格维护变量
- 字段包括：变量名、变量值、是否敏感、描述
- 支持批量新增/删除
- 支持 JSON 导入/导出
- 敏感变量编辑时使用 `valueChanged` 语义

### 环境配置页面

建议按 `envType` 展示不同高级字段：

- API：保留现有 API 环境字段
- WEB_UI：浏览器、无头模式、超时、窗口大小、忽略 HTTPS、默认变量集
- APP：后续再加设备和应用字段

### Web UI 自动化页面

Web UI 用例运行、调试、批量运行、CI Token 配置中应逐步出现：

- 环境选择
- 变量集选择
- 运行时变量入口
- 当前环境绑定的默认变量集提示

## 九、目标包 10 建议拆分

### 10.1 后端执行上下文模型和变量替换

内容：

- 新增/整理 Web UI 执行上下文模型
- 支持 `{{VAR}}` 替换
- 支持 `\{{VAR}}` 转义
- 支持变量合并优先级
- 支持敏感变量脱敏

验证：

- 单元测试覆盖变量替换、转义、优先级、脱敏

### 10.2 Web UI 执行链读取公共环境

内容：

- 读取 `tb_env_config envType=WEB_UI`
- 转成 Web UI 执行浏览器配置
- 保留旧私有环境兼容逻辑

验证：

- 执行接口集成测试
- 调试执行集成测试

### 10.3 Web UI 执行链读取公共变量集

内容：

- 读取 `tb_param_set paramType=WEB_UI_VARIABLE_SET`
- 支持请求传 `variableSetId`
- 未传时读取环境 `defaultVariableSetId`
- 替换步骤字段

验证：

- 用例步骤中的 URL、输入值、断言值能被变量替换
- 敏感变量不会出现在报告和日志中

### 10.4 前端配置中心最小增强

内容：

- 参数配置支持 WEB_UI 变量集
- 环境配置支持 WEB_UI 高级字段
- Web UI 页面环境选择切到公共环境来源

验证：

- 类型检查
- 构建
- 手工验证新增环境、变量集、执行用例

### 10.5 报告上下文快照

内容：

- 执行记录保存环境和变量脱敏快照
- 报告抽屉展示本次执行上下文

验证：

- 修改变量集后，历史报告仍展示执行时快照
- 敏感变量显示为 `******`

## 十、明确不做的事

目标包 10 第一期不做：

- 不删除旧 Web UI 私有环境表
- 不强制迁移历史数据
- 不做 APP 自动化
- 不做变量集递归继承
- 不做复杂权限模型
- 不做完整配置中心 UI 大改版

这些可以后续作为目标包 11 或配置中心专项改造。

## 十一、当前判断

用户朋友/AI 给出的补充意见有较高参考价值，尤其是：

- 敏感变量空值陷阱
- 环境默认绑定变量集
- APP 配置预留
- 项目默认变量
- 变量集继承
- `{{}}` 转义
- JSON 导入/导出
- 报告保存执行时快照

但落地时需要分期，不能在目标包 10 一次性全做。目标包 10 第一优先级是：

```text
Web UI 执行真正接入公共环境 + 公共变量集 + 变量替换 + 敏感脱敏 + 报告快照
```

这条链路跑通后，再做变量集继承、项目默认变量、APP 配置和更完整的配置中心体验。
