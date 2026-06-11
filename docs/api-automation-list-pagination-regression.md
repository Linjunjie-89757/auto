# 接口自动化列表分页回归记录

本轮记录接口自动化列表分页的前后端联调结果。范围限定为接口定义列表和接口用例列表分页接入，不改后端接口、不改数据库。

## 参数边界

| 列表 | 接口 | 分页/筛选参数 | 说明 |
| --- | --- | --- | --- |
| 接口定义 | `GET /api/automation/api/definitions` | `keyword`、`moduleId`、`pageNo`、`pageSize` | `keyword` 用于名称/路径等模糊搜索；`moduleId` 用于目录模块过滤；不传参数时保持旧加载行为。 |
| 接口用例 | `GET /api/automation/api/cases` | `definitionId`、`keyword`、`pageNo`、`pageSize` | 用例 tab 按当前接口 `definitionId` 加载；切页和每页条数变化走后端分页。 |

## 前端触发点

- 进入接口自动化页面时，仍会加载 definitions 和 cases 的兼容默认数据。
- 搜索接口定义时，definitions 请求会带 `keyword/pageNo/pageSize`。
- 点击接口目录模块时，definitions 请求会带 `moduleId/pageNo/pageSize`。
- 打开某个接口的“用例”tab 时，cases 请求会带 `definitionId/pageNo/pageSize`。
- 切换用例页码时，cases 请求会更新 `pageNo`。
- 切换每页条数时，cases 请求会更新 `pageSize`，并回到第一页。

## 已验证场景

| 场景 | 验证结果 |
| --- | --- |
| definitions 首次加载 | `GET /api/automation/api/definitions` 返回 200。 |
| definitions keyword 搜索 | 请求带 `keyword/pageNo/pageSize`，返回 200。 |
| definitions moduleId 过滤 | 使用临时模块验证 `moduleId` 可命中对应接口。 |
| cases 打开用例 tab | 请求带 `definitionId=...&pageNo=1&pageSize=10`，返回 200。 |
| 当前接口无用例 | 页面显示“当前接口下还没有用例”，不显示分页器。 |
| 当前接口超过 10 条用例 | 12 条用例时分页器显示 2 页，第一页展示 10 条。 |
| 下一页 | 请求带 `pageNo=2&pageSize=10`，第二页展示剩余 2 条。 |
| 切换 20 条/页 | 请求带 `pageNo=1&pageSize=20`，只发 1 次请求，展示 12 条。 |
| 保存/复制/删除刷新语义 | 临时用例总数按 `12 -> 13 -> 14 -> 12` 正确变化。 |

## 验证命令

```powershell
npm.cmd run build
git diff --check
```

## 剩余限制

- 接口定义左侧目录树仍保留兼容加载方式，避免只加载当前页导致目录树、场景导入、AI 生成已有用例判断缺失全量数据。
- 每页条数下拉依赖 Element Plus 默认下拉层，当前可点击，但后续如果做更强的自动化 UI 测试，建议增加更稳定的 `data-testid`。
- 本轮没有新增自动化测试文件，验证以真实浏览器联调和临时数据回归为主。

