export const navigationItems = [
  { label: '工作台', path: '/dashboard', icon: 'dashboard' },
  { label: '用例中心', path: '/cases', icon: 'cases' },
  { label: '缺陷管理', path: '/bugs', icon: 'bugs' },
  { label: '接口自动化', path: '/automation/api', icon: 'api' },
  { label: 'Web UI 自动化', path: '/automation/web', icon: 'web' },
  { label: 'APP 自动化', path: '/automation/app', icon: 'app' },
  { label: '系统设置', path: '/settings', icon: 'settings' },
] as const

export const workspaceOptions = [
  { label: '全部空间', value: 'ALL' },
  { label: '开户工作空间', value: 'account-open' },
  { label: '交易工作空间', value: 'trade-core' },
  { label: '风控工作空间', value: 'risk-control' },
]

export const quickActions = [
  { title: '新建用例', desc: '手工录入功能、边界和异常用例' },
  { title: 'AI 生成用例', desc: '从需求文档快速生成结构化用例' },
  { title: '发起接口任务', desc: '针对接口脚本或场景立刻执行' },
  { title: '发起 Web 任务', desc: '携带浏览器参数运行 Playwright' },
  { title: '发起 APP 任务', desc: '选择设备与安装包执行 Appium' },
  { title: '查看失败报告', desc: '集中定位近期失败和慢任务' },
]

export const recentActivities = [
  { title: 'AI 补充开户流程边界用例', meta: '开户工作空间 · 18 分钟前', status: '待评审', tone: 'warning' },
  { title: '回归场景 smoke-web-042 执行完成', meta: '交易工作空间 · 26 分钟前', status: '成功', tone: 'success' },
  { title: 'APP 登录场景在 iPhone 14 失败', meta: '风控工作空间 · 42 分钟前', status: '失败', tone: 'danger' },
  { title: '新增 UAT 环境 Token 参数集', meta: '开户工作空间 · 1 小时前', status: '已更新', tone: 'neutral' },
]

export const caseFolders = [
  '全部用例',
  '账户体系',
  '交易链路',
  '风控校验',
  '兼容性与回归',
]

export const aiStrategy = [
  { label: '功能用例', ratio: '40%' },
  { label: '边界用例', ratio: '20%' },
  { label: '异常用例', ratio: '20%' },
  { label: '性能/兼容性', ratio: '10%' },
  { label: '回归用例', ratio: '10%' },
]

export const automationModules = {
  api: {
    title: '接口自动化',
    description: '面向 Karate 资产、场景编排、执行任务与请求级报告。',
    stats: [
      { label: '接口脚本', value: '312', trend: '登录、下单、查询覆盖完整' },
      { label: '接口场景', value: '38', trend: '14 个串行业务场景' },
      { label: '今日执行', value: '96', trend: '平均耗时 3m 12s' },
      { label: '通过率', value: '97.1%', trend: '失败多集中在鉴权波动' },
    ],
    tasks: [
      ['单接口脚本管理', '按目录维护请求、断言、公共变量与标签', '已规划'],
      ['业务场景编排', '支持串行步骤、参数传递和前后置处理', '已规划'],
      ['统一执行任务', '环境、并发、重试、节点与调度配置统一入口', '优先'],
      ['请求级报告', '展示请求响应、断言结果、错误摘要和耗时', '优先'],
    ],
  },
  web: {
    title: 'Web UI 自动化',
    description: '面向 Playwright 的脚本治理、浏览器执行参数与失败定位。',
    stats: [
      { label: 'Web 脚本', value: '184', trend: '录制脚本占比 28%' },
      { label: '回归场景', value: '22', trend: '含 6 个跨浏览器套件' },
      { label: '今日执行', value: '53', trend: 'Trace 产物齐全' },
      { label: '通过率', value: '92.8%', trend: '近期失败集中在元素等待' },
    ],
    tasks: [
      ['脚本目录治理', '按系统和业务线组织脚本，查看最近结果', '已规划'],
      ['场景组合执行', '支持回归集、冒烟集和浏览器参数继承', '优先'],
      ['调试产物管理', '截图、视频、Trace、控制台日志统一归档', '优先'],
      ['失败定位抽屉', '从任务页右侧直接下钻失败详情', '已规划'],
    ],
  },
  app: {
    title: 'APP 自动化',
    description: '面向 Appium 的设备、安装包、脚本执行和基础报告。',
    stats: [
      { label: 'APP 脚本', value: '86', trend: 'Android / iOS 双平台' },
      { label: '在线设备', value: '12', trend: '当前 3 台占用中' },
      { label: '今日执行', value: '27', trend: '多来自登录与支付回归' },
      { label: '通过率', value: '89.3%', trend: '真机稳定性仍需优化' },
    ],
    tasks: [
      ['脚本资产管理', '按平台区分脚本、标签和目录结构', '已规划'],
      ['设备注册与心跳', '展示在线状态、占用状态和最近心跳', '优先'],
      ['执行任务配置', '选择设备、App 包、参数集和环境', '优先'],
      ['基础执行报告', '步骤日志、截图、设备信息和错误摘要', '已规划'],
    ],
  },
} as const

export const settingsSections = [
  {
    title: '工作空间与权限',
    desc: '维护空间、成员角色和默认 AI 配置。',
    items: ['工作空间编码与状态', '成员分配与轻量 RBAC', '默认用例生成/评审模型'],
  },
  {
    title: '环境与参数',
    desc: '统一管理 API / Web / App 执行依赖。',
    items: ['Base URL / Host', '鉴权信息与变量', '账号、Header、Token、测试数据'],
  },
  {
    title: '平台级系统配置',
    desc: '控制存储、执行和日志策略。',
    items: ['默认超时与并发数', '报告/附件保留时长', '文件上传与对象存储策略'],
  },
] as const
