<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, RefreshRight } from '@element-plus/icons-vue'
import { Activity, Bell, Check, ChevronLeft, Crown, Database, Edit2, Globe, Key, Layers, Package, Palette, Server, Settings, Shield, Target, Trash2, User, UserCog, Users, X } from '@lucide/vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { platformApi } from '../api/platform'
import AiConnectionSettingsPanel from '../components/AiConnectionSettingsPanel.vue'
import ListToolbar from '../components/ListToolbar.vue'
import { usePersistedFilters } from '../composables/usePersistedFilters'
import { useWorkspace } from '../composables/useWorkspace'
import { useWorkspaceAccess } from '../composables/useWorkspaceAccess'
import type {
  BatchCreateUserResult,
  BatchWorkspaceMemberPayload,
  CreateDbConnectionPayload,
  CreateEnvPayload,
  CreateParamPayload,
  CreateUserPayload,
  CreateWorkspacePayload,
  DbConnectionItem,
  EnvConfigItem,
  ParamSetItem,
  UpdateUserPayload,
  UserItem,
  WorkspaceItem,
  WorkspaceMemberItem,
} from '../types/api'

const { workspaceCode, isAllScope } = useWorkspace()
const { currentUser, isPlatformAdmin, isSuperAdmin } = useWorkspaceAccess()
const route = useRoute()
const router = useRouter()

const props = withDefaults(defineProps<{
  mode?: 'settings' | 'configCenter'
}>(), {
  mode: 'settings',
})

type SettingsTab = 'aiConnection' | 'env' | 'param' | 'dbConnection' | 'workspace' | 'member' | 'general' | 'team' | 'notify' | 'security' | 'theme'

const activeTab = ref<SettingsTab>('aiConnection')
const memberViewMode = ref<'user' | 'workspace'>('user')
const memberWorkspaceCode = ref('')
const managingWorkspaceCode = ref('')
const configParamCategoryFilter = ref<'' | 'global' | 'api' | 'business'>('')
const paramSensitive = ref(false)
const paramDescription = ref('')
const dbHost = ref('')
const dbPort = ref('3306')
const dbName = ref('')
const dbPasswordVisible = ref(false)

const pageLoading = ref(false)
const workspaceLoading = ref(false)
const userLoading = ref(false)
const memberLoading = ref(false)
const envLoading = ref(false)
const paramLoading = ref(false)
const dbConnectionLoading = ref(false)

const savingWorkspace = ref(false)
const savingUser = ref(false)
const savingBatchUser = ref(false)
const savingMember = ref(false)
const savingEnv = ref(false)
const savingParam = ref(false)
const savingDbConnection = ref(false)

const workspaces = ref<WorkspaceItem[]>([])
const users = ref<UserItem[]>([])
const members = ref<WorkspaceMemberItem[]>([])
const envs = ref<EnvConfigItem[]>([])
const params = ref<ParamSetItem[]>([])
const dbConnections = ref<DbConnectionItem[]>([])

const workspaceFilters = reactive({
  keyword: '',
})
const userFilters = reactive({
  keyword: '',
  roleCode: '',
  status: '',
})
const memberFilters = reactive({
  workspaceCode: '',
  keyword: '',
})
const envFilters = reactive({
  keyword: '',
  envType: '',
  status: '',
})
const paramFilters = reactive({
  keyword: '',
  paramType: '',
  status: '',
})
const dbConnectionFilters = reactive({
  keyword: '',
  dbType: '',
  status: '',
})

const workspaceFilterMemory = usePersistedFilters({
  storageKey: 'settings-workspace-filters-v1',
  filters: workspaceFilters,
  defaults: {
    keyword: '',
  },
})

const userFilterMemory = usePersistedFilters({
  storageKey: 'settings-user-filters-v1',
  filters: userFilters,
  defaults: {
    keyword: '',
    roleCode: '',
    status: '',
  },
})

const memberFilterMemory = usePersistedFilters({
  storageKey: 'settings-member-filters-v1',
  filters: memberFilters,
  defaults: {
    workspaceCode: '',
    keyword: '',
  },
})

const envFilterMemory = usePersistedFilters({
  storageKey: 'settings-env-filters-v1',
  filters: envFilters,
  defaults: {
    keyword: '',
    envType: '',
    status: '',
  },
})

const paramFilterMemory = usePersistedFilters({
  storageKey: 'settings-param-filters-v1',
  filters: paramFilters,
  defaults: {
    keyword: '',
    paramType: '',
    status: '',
  },
})
const dbConnectionFilterMemory = usePersistedFilters({
  storageKey: 'settings-db-connection-filters-v1',
  filters: dbConnectionFilters,
  defaults: {
    keyword: '',
    dbType: '',
    status: '',
  },
})

const workspaceDialogVisible = ref(false)
const workspaceDialogMode = ref<'create' | 'edit'>('create')
const userDialogVisible = ref(false)
const userDialogMode = ref<'create' | 'edit'>('create')
const batchUserDialogVisible = ref(false)
const batchMemberDialogVisible = ref(false)
const envDialogVisible = ref(false)
const envDialogMode = ref<'create' | 'edit'>('create')
const paramDialogVisible = ref(false)
const paramDialogMode = ref<'create' | 'edit'>('create')
const dbConnectionDialogVisible = ref(false)
const dbConnectionDialogMode = ref<'create' | 'edit'>('create')

const configEnvTypeOptions = [
  { value: 'TEST', label: '测试' },
  { value: 'STAGING', label: '预发布' },
  { value: 'PROD', label: '生产' },
]

const configEnvStatusOptions = [
  { value: 1, label: '启用' },
  { value: 0, label: '禁用' },
]

const configParamTypeOptions = [
  { value: 'GLOBAL', label: '全局参数' },
  { value: 'API', label: '接口参数' },
  { value: 'BUSINESS', label: '业务参数' },
]

const configDbTypeOptions = [
  { value: 'MYSQL', label: 'MySQL', port: '3306', driver: 'com.mysql.cj.jdbc.Driver' },
  { value: 'POSTGRESQL', label: 'PostgreSQL', port: '5432', driver: 'org.postgresql.Driver' },
  { value: 'REDIS', label: 'Redis', port: '6379', driver: '' },
  { value: 'MONGODB', label: 'MongoDB', port: '27017', driver: '' },
]

function notifyWorkspaceListChanged() {
  window.dispatchEvent(new CustomEvent('workspace-list-changed'))
}

const workspaceForm = reactive<Required<CreateWorkspacePayload>>({
  workspaceCode: '',
  workspaceName: '',
  description: '',
  workspaceType: 'PROJECT',
  ownerUserId: null,
  status: 1,
})

const userForm = reactive<CreateUserPayload & UpdateUserPayload & { id: number | null }>({
  id: null,
  username: '',
  email: '',
  displayName: '',
  roleCode: 'MEMBER',
  status: 1,
  workspaceCodes: [],
})

const batchUserForm = reactive({
  rawText: '',
  workspaceCodes: [] as string[],
})
const batchUserResults = ref<BatchCreateUserResult[]>([])

const batchMemberForm = reactive<BatchWorkspaceMemberPayload>({
  userIds: [],
  roleCode: 'MEMBER',
})

const envForm = reactive<CreateEnvPayload & { id: number | null; workspaceCode: string; status: number }>({
  id: null,
  workspaceCode: '',
  envType: 'API',
  envName: '',
  baseUrl: '',
  configJson: '',
  status: 1,
})

const paramForm = reactive<CreateParamPayload & { id: number | null; workspaceCode: string; status: number }>({
  id: null,
  workspaceCode: '',
  paramType: 'TOKEN',
  paramName: '',
  contentJson: '',
  status: 1,
})

const dbConnectionForm = reactive<CreateDbConnectionPayload & { id: number | null; workspaceCode: string; status: number }>({
  id: null,
  workspaceCode: '',
  connectionName: '',
  dbType: 'MYSQL',
  driverClassName: '',
  jdbcUrl: '',
  username: '',
  password: '',
  poolMax: 10,
  timeoutMs: 5000,
  description: '',
  status: 1,
})

const businessWorkspaces = computed(() => workspaces.value.filter(item => !item.allScope))
const managingWorkspace = computed(() => businessWorkspaces.value.find(item => item.code === managingWorkspaceCode.value) ?? null)
const canManageSettings = computed(() => isPlatformAdmin.value)
const canManageAdminUsers = computed(() => isSuperAdmin.value)
const isConfigCenter = computed(() => props.mode === 'configCenter')
const settingsNavItems = computed(() => {
  const base = [
    { id: 'aiConnection' as SettingsTab, label: 'AI 连接', desc: '配置 AI 大模型连接池', icon: Database },
  ]
  if (canManageSettings.value) {
    base.push(
      { id: 'workspace' as SettingsTab, label: '空间配置', desc: '管理测试空间', icon: Layers },
      { id: 'general' as SettingsTab, label: '通用设置', desc: '平台基础配置', icon: Settings },
      { id: 'team' as SettingsTab, label: '团队管理', desc: '成员与权限管理', icon: Users },
      { id: 'notify' as SettingsTab, label: '通知设置', desc: '消息推送与告警', icon: Bell },
      { id: 'security' as SettingsTab, label: '安全设置', desc: '密钥与访问控制', icon: Shield },
      { id: 'theme' as SettingsTab, label: '外观设置', desc: '主题与显示偏好', icon: Palette },
    )
  } else {
    base.push(
      { id: 'team' as SettingsTab, label: '团队管理', desc: '成员与权限管理', icon: Users },
    )
  }
  return base
})
const visibleTabs = computed<SettingsTab[]>(() => {
  if (isConfigCenter.value) {
    return canManageSettings.value ? ['env', 'param', 'dbConnection'] : []
  }
  return settingsNavItems.value.map(item => item.id)
})
const visibleWorkspaceCodes = computed(() => currentUser.value?.workspaceCodes ?? [])
const writableWorkspaceOptions = computed(() => {
  if (isPlatformAdmin.value) {
    return businessWorkspaces.value
  }
  return businessWorkspaces.value.filter(item => visibleWorkspaceCodes.value.includes(item.code))
})
const activeUsers = computed(() => users.value.filter(item => item.status === 1))
const workspaceStatCards = computed(() => [
  { label: '空间总数', value: businessWorkspaces.value.length, tone: 'blue', icon: Layers },
  { label: '启用空间', value: businessWorkspaces.value.filter(item => item.status !== 0).length, tone: 'green', icon: Check },
  { label: '总成员数', value: activeUsers.value.length, tone: 'purple', icon: Users },
  { label: '项目空间', value: businessWorkspaces.value.filter(item => inferWorkspaceType(item) === 'project').length, tone: 'orange', icon: Activity },
])
const teamStatCards = computed(() => [
  { label: '成员总数', value: users.value.length, tone: 'blue', icon: Users },
  { label: '启用成员', value: users.value.filter(item => item.status === 1).length, tone: 'green', icon: Check },
  { label: '平台管理员', value: users.value.filter(item => isPlatformAdminRole(item.roleCode)).length, tone: 'purple', icon: Shield },
  { label: '普通账号', value: users.value.filter(item => isPlatformMemberRole(item.roleCode)).length, tone: 'orange', icon: User },
])
const selectableMembersForWorkspace = computed(() => {
  const existingUserIds = new Set(members.value.map(item => item.userId))
  return activeUsers.value.filter(item => !isPlatformAdminRole(item.roleCode) && !existingUserIds.has(item.id))
})
const currentScopeText = computed(() => {
  if (!isConfigCenter.value) {
    return canManageSettings.value
      ? '管理个人 AI 连接、工作空间与成员权限。'
      : '管理个人 AI 连接配置。'
  }
  if (isAllScope.value) {
    return '当前为全部空间视角。环境配置、参数配置和数据库连接会跨空间展示；新建或编辑时需要明确目标空间。'
  }
  return `当前为 ${resolveWorkspaceName(workspaceCode.value)} 视角。配置中心只展示当前空间的公共配置。`
})

const filteredWorkspaces = computed(() => {
  const keyword = workspaceFilters.keyword.trim().toLowerCase()
  return businessWorkspaces.value.filter((item) => {
    if (!keyword) {
      return true
    }
    return item.name.toLowerCase().includes(keyword)
      || item.code.toLowerCase().includes(keyword)
      || (item.description ?? '').toLowerCase().includes(keyword)
  })
})

const filteredUsers = computed(() => {
  const keyword = userFilters.keyword.trim().toLowerCase()
  return users.value.filter((item) => {
    const matchKeyword = !keyword
      || item.displayName.toLowerCase().includes(keyword)
      || item.username.toLowerCase().includes(keyword)
      || item.email.toLowerCase().includes(keyword)
      || item.workspaceNames.join(' ').toLowerCase().includes(keyword)
    const matchRole = !userFilters.roleCode || userRoleFilterMatches(item.roleCode, userFilters.roleCode)
    const matchStatus = !userFilters.status || String(item.status) === userFilters.status
    return matchKeyword && matchRole && matchStatus
  })
})

const filteredMembers = computed(() => {
  const keyword = memberFilters.keyword.trim().toLowerCase()
  return members.value.filter((item) => {
    if (!keyword) {
      return true
    }
    return item.displayName.toLowerCase().includes(keyword)
      || item.username.toLowerCase().includes(keyword)
      || item.email.toLowerCase().includes(keyword)
  })
})
const workspaceAdminMembers = computed(() => {
  const ownerUserId = managingWorkspace.value?.ownerUserId ?? null
  return filteredMembers.value.filter(member =>
    member.roleCode === 'ADMIN' && member.id > 0 && member.userId !== ownerUserId)
})
const workspaceRegularMembers = computed(() => {
  const ownerUserId = managingWorkspace.value?.ownerUserId ?? null
  return filteredMembers.value.filter(member =>
    member.roleCode !== 'ADMIN' && member.roleCode !== 'SUPER_ADMIN' && member.userId !== ownerUserId)
})
const configCenterNavItems = computed(() => [
  { id: 'env' as SettingsTab, label: '环境配置', desc: '测试环境管理', icon: Globe },
  { id: 'param' as SettingsTab, label: '参数配置', desc: '全局参数设置', icon: Key },
  { id: 'dbConnection' as SettingsTab, label: '数据库连接', desc: '数据源配置', icon: Server },
])

const filteredEnvs = computed(() => {
  const keyword = envFilters.keyword.trim().toLowerCase()
  return envs.value.filter((item) => {
    const matchKeyword = !keyword
      || item.envName.toLowerCase().includes(keyword)
      || item.baseUrl.toLowerCase().includes(keyword)
      || item.workspaceName.toLowerCase().includes(keyword)
    const matchType = !envFilters.envType || item.envType === envFilters.envType
    const matchStatus = !envFilters.status || String(item.status) === envFilters.status
    return matchKeyword && matchType && matchStatus
  })
})

const configEnvStats = computed(() => [
  { label: '环境总数', value: envs.value.length, tone: 'blue' },
  { label: '启用环境', value: envs.value.filter(item => item.status === 1).length, tone: 'green' },
  { label: '生产环境', value: envs.value.filter(item => isProductionEnv(item)).length, tone: 'red' },
])

const configParamStats = computed(() => [
  { label: '全部参数', value: params.value.length, tone: 'gray' },
  { label: '全局参数', value: params.value.filter(item => configParamCategory(item) === 'global').length, tone: 'blue' },
  { label: '接口参数', value: params.value.filter(item => configParamCategory(item) === 'api').length, tone: 'purple' },
  { label: '业务参数', value: params.value.filter(item => configParamCategory(item) === 'business').length, tone: 'green' },
])

const configDbStats = computed(() => [
  { label: '连接总数', value: dbConnections.value.length, tone: 'blue' },
  { label: '正常连接', value: dbConnections.value.filter(item => item.status === 1).length, tone: 'green' },
  { label: '异常连接', value: dbConnections.value.filter(item => item.status !== 1).length, tone: 'red' },
  { label: 'MySQL', value: dbConnections.value.filter(item => item.dbType === 'MYSQL').length, tone: 'orange' },
])

const configEnvs = computed(() => envs.value)
const configDbConnections = computed(() => dbConnections.value)

const filteredParams = computed(() => {
  const keyword = paramFilters.keyword.trim().toLowerCase()
  return params.value.filter((item) => {
    const matchKeyword = !keyword
      || item.paramName.toLowerCase().includes(keyword)
      || item.workspaceName.toLowerCase().includes(keyword)
      || item.contentJson.toLowerCase().includes(keyword)
    const matchType = !paramFilters.paramType || item.paramType === paramFilters.paramType
    const matchStatus = !paramFilters.status || String(item.status) === paramFilters.status
    return matchKeyword && matchType && matchStatus
  })
})

const filteredDbConnections = computed(() => {
  const keyword = dbConnectionFilters.keyword.trim().toLowerCase()
  return dbConnections.value.filter((item) => {
    const matchKeyword = !keyword
      || item.connectionName.toLowerCase().includes(keyword)
      || item.jdbcUrl.toLowerCase().includes(keyword)
      || item.workspaceName.toLowerCase().includes(keyword)
      || (item.username ?? '').toLowerCase().includes(keyword)
    const matchType = !dbConnectionFilters.dbType || item.dbType === dbConnectionFilters.dbType
    const matchStatus = !dbConnectionFilters.status || String(item.status) === dbConnectionFilters.status
    return matchKeyword && matchType && matchStatus
  })
})

const configFilteredParams = computed(() => {
  if (!configParamCategoryFilter.value) {
    return params.value
  }
  return params.value.filter(item => configParamCategory(item) === configParamCategoryFilter.value)
})

function normalizeSettingsTab(tab: unknown): SettingsTab {
  const candidate = typeof tab === 'string' ? tab : ''
  if (visibleTabs.value.includes(candidate as SettingsTab)) {
    return candidate as SettingsTab
  }
  return visibleTabs.value[0] ?? (isConfigCenter.value ? 'env' : 'aiConnection')
}

function syncSettingsTabFromRoute() {
  activeTab.value = normalizeSettingsTab(route.query.tab)
}

function resetWorkspaceFilters() {
  workspaceFilterMemory.reset()
}

function resetUserFilters() {
  userFilterMemory.reset()
}

function resetMemberFilters() {
  memberFilterMemory.reset()
  memberWorkspaceCode.value = memberFilters.workspaceCode || businessWorkspaces.value[0]?.code || ''
  memberFilters.workspaceCode = memberWorkspaceCode.value
}

function resetEnvFilters() {
  envFilterMemory.reset()
}

function resetParamFilters() {
  paramFilterMemory.reset()
}

function resetDbConnectionFilters() {
  dbConnectionFilterMemory.reset()
}

function resolveWorkspaceName(code: string) {
  if (code === 'ALL') {
    return '全部空间'
  }
  return businessWorkspaces.value.find(item => item.code === code)?.name ?? code
}

function isProductionEnv(item: EnvConfigItem) {
  const text = `${item.envType} ${item.envName}`.toLowerCase()
  return text.includes('prod') || text.includes('生产')
}

function envVisualMeta(item: EnvConfigItem) {
  const text = `${item.envType} ${item.envName} ${item.configJson ?? ''}`.toLowerCase()
  if (text.includes('prod') || text.includes('生产')) {
    return {
      typeLabel: '生产环境',
      typeClassName: 'is-red',
      description: envDescription(item, '正式生产环境（谨慎操作）'),
    }
  }
  if (text.includes('staging') || text.includes('stage') || text.includes('预发')) {
    return {
      typeLabel: '预发布环境',
      typeClassName: 'is-orange',
      description: envDescription(item, '上线前验证环境'),
    }
  }
  return {
    typeLabel: '测试环境',
    typeClassName: 'is-blue',
    description: envDescription(item, '开发和测试使用的环境'),
  }
}

function envDescription(item: EnvConfigItem, fallback: string) {
  const raw = item.configJson?.trim()
  if (!raw) {
    return fallback
  }
  try {
    const parsed = JSON.parse(raw) as { description?: unknown; desc?: unknown; remark?: unknown }
    const description = parsed.description ?? parsed.desc ?? parsed.remark
    if (typeof description === 'string' && description.trim()) {
      return description.trim()
    }
  }
  catch {
    if (!raw.startsWith('{') && !raw.startsWith('[')) {
      return raw
    }
  }
  return fallback
}

function envCreatedText(item: EnvConfigItem) {
  const row = item as EnvConfigItem & { createdAt?: string; createTime?: string; createdTime?: string }
  const value = row.createdAt ?? row.createTime ?? row.createdTime
  return value ? value.slice(0, 10) : '-'
}

function normalizeConfigEnvType(item: EnvConfigItem) {
  const text = `${item.envType} ${item.envName} ${item.configJson ?? ''}`.toLowerCase()
  if (text.includes('prod') || text.includes('生产')) {
    return 'PROD'
  }
  if (text.includes('staging') || text.includes('stage') || text.includes('预发')) {
    return 'STAGING'
  }
  return 'TEST'
}

function configParamCategory(item: Pick<ParamSetItem, 'paramType' | 'paramName'>) {
  const text = `${item.paramType} ${item.paramName}`.toLowerCase()
  if (item.paramType === 'BUSINESS' || ['business', '业务'].some(keyword => text.includes(keyword))) {
    return 'business'
  }
  if (item.paramType === 'API' || ['header', 'body', 'query', 'api'].some(keyword => text.includes(keyword))) {
    return 'api'
  }
  return 'global'
}

function configParamTypeMeta(item: Pick<ParamSetItem, 'paramType' | 'paramName'>) {
  const category = configParamCategory(item)
  if (category === 'api') {
    return { label: '接口参数', className: 'is-purple' }
  }
  if (category === 'business') {
    return { label: '业务参数', className: 'is-green' }
  }
  return { label: '全局参数', className: 'is-blue' }
}

function parseParamContent(contentJson: string) {
  const raw = contentJson?.trim() ?? ''
  if (!raw) {
    return { value: '', description: '', sensitive: false }
  }
  try {
    const parsed = JSON.parse(raw) as { value?: unknown; description?: unknown; desc?: unknown; sensitive?: unknown; isSecret?: unknown }
    const value = typeof parsed.value === 'string' ? parsed.value : raw
    const description = typeof parsed.description === 'string'
      ? parsed.description
      : typeof parsed.desc === 'string'
        ? parsed.desc
        : ''
    return {
      value,
      description,
      sensitive: parsed.sensitive === true || parsed.isSecret === true,
    }
  }
  catch {
    return { value: raw, description: '', sensitive: false }
  }
}

function buildParamContentJson() {
  return JSON.stringify({
    value: paramForm.contentJson.trim(),
    description: paramDescription.value.trim(),
    sensitive: paramSensitive.value,
  })
}

function configParamValueText(item: ParamSetItem) {
  const parsed = parseParamContent(item.contentJson)
  if (parsed.sensitive && parsed.value) {
    return '••••••••'
  }
  return parsed.value || '-'
}

function selectConfigParamFilter(value: '' | 'global' | 'api' | 'business') {
  configParamCategoryFilter.value = value
}

function dbHostSummary(jdbcUrl: string) {
  const match = jdbcUrl.match(/^jdbc:[^:]+:\/\/([^/?]+)(?:\/([^?]+))?/)
  if (!match) {
    return jdbcUrl
  }
  return match[1]
}

function dbNameSummary(row: DbConnectionItem) {
  const match = row.jdbcUrl.match(/^jdbc:[^:]+:\/\/[^/?]+\/([^?]+)/)
  return match?.[1] || row.description || '-'
}

function parseJdbcUrl(jdbcUrl: string) {
  const match = jdbcUrl.match(/^jdbc:([^:]+):\/\/([^:/?]+)(?::(\d+))?(?:\/([^?]+))?/)
  return {
    type: match?.[1]?.toUpperCase() ?? 'MYSQL',
    host: match?.[2] ?? '',
    port: match?.[3] ?? '',
    database: match?.[4] ?? '',
  }
}

function buildJdbcUrl() {
  const host = dbHost.value.trim()
  const port = dbPort.value.trim()
  const database = dbName.value.trim()
  if (dbConnectionForm.dbType === 'POSTGRESQL') {
    return `jdbc:postgresql://${host}${port ? `:${port}` : ''}/${database}`
  }
  if (dbConnectionForm.dbType === 'MONGODB') {
    return `jdbc:mongodb://${host}${port ? `:${port}` : ''}/${database}`
  }
  if (dbConnectionForm.dbType === 'REDIS') {
    return `jdbc:redis://${host}${port ? `:${port}` : ''}/${database || '0'}`
  }
  return `jdbc:mysql://${host}${port ? `:${port}` : ''}/${database}`
}

function applyDbTypeDefaults(type: string) {
  const meta = configDbTypeOptions.find(item => item.value === type) ?? configDbTypeOptions[0]
  dbConnectionForm.dbType = meta.value
  dbConnectionForm.driverClassName = meta.driver
  if (!dbPort.value || dbPort.value === '3306' || dbPort.value === '5432' || dbPort.value === '6379' || dbPort.value === '27017') {
    dbPort.value = meta.port
  }
}

function roleLabel(roleCode: string) {
  if (roleCode === 'SUPER_ADMIN') {
    return '超级管理员'
  }
  return isPlatformAdminRole(roleCode) ? '平台管理员' : '普通账号'
}

function isPlatformAdminRole(roleCode: string) {
  return roleCode === 'SUPER_ADMIN' || roleCode === 'ADMIN' || roleCode === 'PLATFORM_ADMIN'
}

function isPlatformMemberRole(roleCode: string) {
  return !isPlatformAdminRole(roleCode)
}

function userRoleFilterMatches(roleCode: string, filterRole: string) {
  if (filterRole === 'ADMIN') {
    return isPlatformAdminRole(roleCode)
  }
  if (filterRole === 'MEMBER') {
    return isPlatformMemberRole(roleCode)
  }
  return roleCode === filterRole
}

function teamRoleClass(roleCode: string) {
  if (roleCode === 'SUPER_ADMIN') {
    return 'is-super'
  }
  return isPlatformAdminRole(roleCode) ? 'is-admin' : 'is-member'
}

function teamStatusLabel(status: number) {
  return status === 1 ? '启用中' : '已停用'
}

function workspaceSummary(user: UserItem) {
  if (isPlatformAdminRole(user.roleCode)) {
    return '全部空间'
  }
  if (!user.workspaceNames.length) {
    return '未分配空间'
  }
  return user.workspaceNames.join('、')
}

type WorkspaceVisualType = 'project' | 'team' | 'product'

function inferWorkspaceType(workspace: WorkspaceItem): WorkspaceVisualType {
  if (workspace.workspaceType === 'TEAM') {
    return 'team'
  }
  if (workspace.workspaceType === 'PRODUCT') {
    return 'product'
  }
  if (workspace.workspaceType === 'PROJECT') {
    return 'project'
  }
  const text = `${workspace.name} ${workspace.code} ${workspace.description ?? ''}`.toLowerCase()
  if (text.includes('团队') || text.includes('team')) {
    return 'team'
  }
  if (text.includes('产品') || text.includes('product')) {
    return 'product'
  }
  return 'project'
}

function workspaceTypeMeta(workspace: WorkspaceItem) {
  const type = inferWorkspaceType(workspace)
  const map = {
    project: { label: '项目', className: 'is-project', icon: Package },
    team: { label: '团队', className: 'is-team', icon: Users },
    product: { label: '产品', className: 'is-product', icon: Target },
  }
  return map[type]
}

function workspaceMemberCount(row: WorkspaceItem) {
  return activeUsers.value.filter((user) => {
    if (user.roleCode === 'SUPER_ADMIN' || user.roleCode === 'ADMIN') {
      return true
    }
    return user.workspaceCodes.includes(row.code)
  }).length
}

function workspaceOwnerName(row: WorkspaceItem) {
  if (row.ownerName) {
    return row.ownerName
  }
  if (row.ownerUserId) {
    const owner = activeUsers.value.find(user => user.id === row.ownerUserId)
    if (owner) {
      return owner.displayName
    }
  }
  const directMember = activeUsers.value.find(user => user.workspaceCodes.includes(row.code))
  const admin = activeUsers.value.find(user => user.roleCode === 'ADMIN' || user.roleCode === 'SUPER_ADMIN')
  return directMember?.displayName || admin?.displayName || '组织管理员'
}

function openWorkspaceMembers(row: WorkspaceItem) {
  managingWorkspaceCode.value = row.code
  memberWorkspaceCode.value = row.code
  memberFilters.workspaceCode = row.code
}

function closeWorkspaceMembers() {
  managingWorkspaceCode.value = ''
}

function workspaceUserInitial(name: string) {
  return name.trim().charAt(0).toUpperCase() || 'U'
}

function memberRoleDescription(roleCode: string) {
  if (roleCode === 'ADMIN' || roleCode === 'SUPER_ADMIN') {
    return '具有空间管理权限，可编辑用例和配置'
  }
  return '可访问该空间，可查看和执行测试用例'
}

function normalizeWorkspaceCodes(codes: string[]) {
  return [...new Set(codes.filter(Boolean))]
}

function resetWorkspaceForm() {
  workspaceForm.workspaceCode = ''
  workspaceForm.workspaceName = ''
  workspaceForm.description = ''
  workspaceForm.workspaceType = 'PROJECT'
  workspaceForm.ownerUserId = activeUsers.value.find(item => item.roleCode === 'ADMIN' || item.roleCode === 'SUPER_ADMIN')?.id
    ?? activeUsers.value[0]?.id
    ?? null
  workspaceForm.status = 1
}

function resetUserForm() {
  userForm.id = null
  userForm.username = ''
  userForm.email = ''
  userForm.displayName = ''
  userForm.roleCode = 'MEMBER'
  userForm.status = 1
  userForm.workspaceCodes = []
}

function resetBatchUserForm() {
  batchUserForm.rawText = ''
  batchUserForm.workspaceCodes = []
  batchUserResults.value = []
}

function resetBatchMemberForm() {
  batchMemberForm.userIds = []
  batchMemberForm.roleCode = 'MEMBER'
}

function resetEnvForm() {
  envForm.id = null
  envForm.workspaceCode = isAllScope.value ? '' : workspaceCode.value
  envForm.envType = 'TEST'
  envForm.envName = ''
  envForm.baseUrl = ''
  envForm.configJson = ''
  envForm.status = 1
}

function resetParamForm() {
  paramForm.id = null
  paramForm.workspaceCode = isAllScope.value ? '' : workspaceCode.value
  paramForm.paramType = 'GLOBAL'
  paramForm.paramName = ''
  paramForm.contentJson = ''
  paramDescription.value = ''
  paramSensitive.value = false
  paramForm.status = 1
}

function resetDbConnectionForm() {
  dbConnectionForm.id = null
  dbConnectionForm.workspaceCode = isAllScope.value ? '' : workspaceCode.value
  dbConnectionForm.connectionName = ''
  dbConnectionForm.dbType = 'MYSQL'
  dbConnectionForm.driverClassName = 'com.mysql.cj.jdbc.Driver'
  dbConnectionForm.jdbcUrl = ''
  dbConnectionForm.username = ''
  dbConnectionForm.password = ''
  dbHost.value = ''
  dbPort.value = '3306'
  dbName.value = ''
  dbPasswordVisible.value = false
  dbConnectionForm.poolMax = 10
  dbConnectionForm.timeoutMs = 5000
  dbConnectionForm.description = ''
  dbConnectionForm.status = 1
}

async function loadBaseData() {
  pageLoading.value = true
  workspaceLoading.value = true
  userLoading.value = true
  try {
    const [workspaceList, userList] = await Promise.all([
      platformApi.getWorkspaces(),
      platformApi.getUsers(),
    ])
    workspaces.value = workspaceList
    users.value = userList
    if (!memberWorkspaceCode.value || !businessWorkspaces.value.some(item => item.code === memberWorkspaceCode.value)) {
      memberWorkspaceCode.value = businessWorkspaces.value[0]?.code ?? ''
    }
    if (!memberFilters.workspaceCode || !businessWorkspaces.value.some(item => item.code === memberFilters.workspaceCode)) {
      memberFilters.workspaceCode = memberWorkspaceCode.value
    }
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    workspaceLoading.value = false
    userLoading.value = false
    pageLoading.value = false
  }
}

async function loadMembers() {
  if (!memberWorkspaceCode.value) {
    members.value = []
    return
  }
  memberLoading.value = true
  try {
    members.value = await platformApi.getWorkspaceMembers(memberWorkspaceCode.value)
  }
  catch (error) {
    members.value = []
    ElMessage.error((error as Error).message)
  }
  finally {
    memberLoading.value = false
  }
}

async function loadScopedSettings() {
  envLoading.value = true
  paramLoading.value = true
  dbConnectionLoading.value = true
  try {
    const [envPage, paramPage, dbConnectionPage] = await Promise.all([
      platformApi.getSettingsEnvs(workspaceCode.value),
      platformApi.getSettingsParams(workspaceCode.value),
      platformApi.getSettingsDbConnections(workspaceCode.value),
    ])
    envs.value = envPage.items
    params.value = paramPage.items
    dbConnections.value = dbConnectionPage.items
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    envLoading.value = false
    paramLoading.value = false
    dbConnectionLoading.value = false
  }
}

function openWorkspaceCreate() {
  resetWorkspaceForm()
  workspaceDialogMode.value = 'create'
  workspaceDialogVisible.value = true
}

function openWorkspaceEdit(row: WorkspaceItem) {
  workspaceDialogMode.value = 'edit'
  workspaceForm.workspaceCode = row.code
  workspaceForm.workspaceName = row.name
  workspaceForm.description = row.description ?? ''
  workspaceForm.workspaceType = row.workspaceType ?? 'PROJECT'
  workspaceForm.ownerUserId = row.ownerUserId ?? activeUsers.value.find(item => item.displayName === row.ownerName)?.id ?? null
  workspaceForm.status = row.status ?? 1
  workspaceDialogVisible.value = true
}

async function submitWorkspace() {
  if (!workspaceForm.workspaceName.trim()) {
    ElMessage.error('请先填写工作空间名称')
    return
  }
  savingWorkspace.value = true
  try {
    const payload = {
      workspaceCode: workspaceDialogMode.value === 'edit' ? workspaceForm.workspaceCode.trim() : undefined,
      workspaceName: workspaceForm.workspaceName.trim(),
      description: workspaceForm.description.trim(),
      workspaceType: workspaceForm.workspaceType,
      ownerUserId: workspaceForm.ownerUserId,
      status: workspaceForm.status,
    }
    if (workspaceDialogMode.value === 'create') {
      await platformApi.createWorkspace(payload)
      ElMessage.success('工作空间创建成功')
    } else {
      await platformApi.updateWorkspace(workspaceForm.workspaceCode, payload)
      ElMessage.success('工作空间更新成功')
    }
    workspaceDialogVisible.value = false
    await loadBaseData()
    notifyWorkspaceListChanged()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    savingWorkspace.value = false
  }
}

async function confirmDeleteWorkspace(row: WorkspaceItem) {
  const deletingCurrentWorkspace = row.code === workspaceCode.value
  try {
    await ElMessageBox.confirm(
      `删除后将无法恢复工作空间“${row.name}”。只有无依赖数据的空间允许删除，是否继续？`,
      '删除工作空间',
      { type: 'warning' },
    )
    await platformApi.deleteWorkspace(row.code)
    ElMessage.success('工作空间删除成功')
    if (managingWorkspaceCode.value === row.code) {
      managingWorkspaceCode.value = ''
    }
    if (memberWorkspaceCode.value === row.code) {
      memberWorkspaceCode.value = ''
    }
    notifyWorkspaceListChanged()
    if (deletingCurrentWorkspace) {
      await router.replace({
        path: route.path,
        query: {
          ...route.query,
          workspace: 'ALL',
        },
      })
      await loadBaseData()
      await loadScopedSettings()
    } else {
      await Promise.all([loadBaseData(), loadScopedSettings()])
    }
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

function openUserCreate() {
  resetUserForm()
  if (!isSuperAdmin.value) {
    userForm.roleCode = 'MEMBER'
  }
  userDialogMode.value = 'create'
  userDialogVisible.value = true
}

function openBatchUserCreate() {
  resetBatchUserForm()
  batchUserDialogVisible.value = true
}

function openUserEdit(row: UserItem) {
  userDialogMode.value = 'edit'
  userForm.id = row.id
  userForm.username = row.username
  userForm.email = row.email
  userForm.displayName = row.displayName
  userForm.roleCode = row.roleCode
  userForm.status = row.status
  userForm.workspaceCodes = [...row.workspaceCodes]
  userDialogVisible.value = true
}

function canEditUser(row: UserItem) {
  return row.roleCode !== 'ADMIN' || isSuperAdmin.value
}

function canToggleUser(row: UserItem) {
  return row.roleCode !== 'ADMIN' || isSuperAdmin.value
}

function canResetPassword(row: UserItem) {
  return row.roleCode !== 'ADMIN' || isSuperAdmin.value
}

function splitBatchUserLine(line: string) {
  if (line.includes(',')) {
    return line.split(',').map(item => item.trim())
  }
  if (line.includes('\t')) {
    return line.split('\t').map(item => item.trim())
  }
  return line.trim().split(/\s+/).map(item => item.trim())
}

function parseBatchUsers() {
  const existingUsernames = new Set(users.value.map(item => item.username.trim().toLowerCase()))
  const existingEmails = new Set(users.value.map(item => item.email.trim().toLowerCase()))
  const batchUsernames = new Set<string>()
  const batchEmails = new Set<string>()
  const workspaceCodes = normalizeWorkspaceCodes(batchUserForm.workspaceCodes)
  const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  const parsed: CreateUserPayload[] = []
  const errors: string[] = []

  batchUserForm.rawText
    .split(/\r?\n/)
    .map((line, index) => ({ line: line.trim(), index: index + 1 }))
    .filter(item => item.line)
    .forEach(({ line, index }) => {
      const [username = '', displayName = '', email = ''] = splitBatchUserLine(line)
      const normalizedUsername = username.trim().toLowerCase()
      const normalizedEmail = email.trim().toLowerCase()

      if (!username || !displayName || !email) {
        errors.push(`第 ${index} 行：请按“账号,姓名,邮箱”填写`)
        return
      }
      if (!emailPattern.test(email)) {
        errors.push(`第 ${index} 行：邮箱格式不正确`)
        return
      }
      if (existingUsernames.has(normalizedUsername)) {
        errors.push(`第 ${index} 行：账号已存在`)
        return
      }
      if (existingEmails.has(normalizedEmail)) {
        errors.push(`第 ${index} 行：邮箱已存在`)
        return
      }
      if (batchUsernames.has(normalizedUsername)) {
        errors.push(`第 ${index} 行：账号在本次批量中重复`)
        return
      }
      if (batchEmails.has(normalizedEmail)) {
        errors.push(`第 ${index} 行：邮箱在本次批量中重复`)
        return
      }

      batchUsernames.add(normalizedUsername)
      batchEmails.add(normalizedEmail)
      parsed.push({
        username: username.trim(),
        displayName: displayName.trim(),
        email: email.trim(),
        roleCode: 'MEMBER',
        workspaceCodes,
      })
    })

  return { parsed, errors }
}

async function submitBatchUsers() {
  const { parsed, errors } = parseBatchUsers()
  batchUserResults.value = []
  if (!batchUserForm.rawText.trim()) {
    ElMessage.error('请先粘贴账号数据')
    return
  }
  if (errors.length) {
    ElMessage.error(errors[0])
    batchUserResults.value = errors.map((message, index) => ({
      index: index + 1,
      username: '',
      email: '',
      displayName: '',
      success: false,
      message,
      user: null,
    }))
    return
  }
  if (!parsed.length) {
    ElMessage.error('没有可新增的账号')
    return
  }

  savingBatchUser.value = true
  try {
    const response = await platformApi.batchCreateUsers({ users: parsed })
    batchUserResults.value = response.results
    ElMessage.success(`批量新增完成：成功 ${response.successCount} 个，失败 ${response.failureCount} 个`)
    await Promise.all([loadBaseData(), loadMembers()])
    if (response.failureCount === 0) {
      batchUserDialogVisible.value = false
    }
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    savingBatchUser.value = false
  }
}

async function submitUser() {
  if (!userForm.username.trim() && userDialogMode.value === 'create') {
    ElMessage.error('请先填写成员账号')
    return
  }
  if (!userForm.email.trim()) {
    ElMessage.error('请先填写邮箱')
    return
  }
  if (!userForm.displayName.trim()) {
    ElMessage.error('请先填写成员姓名')
    return
  }

  if (userForm.roleCode === 'ADMIN' && !isSuperAdmin.value) {
    ElMessage.error('只有超级管理员可以创建或调整管理员')
    return
  }

  const workspaceCodes = normalizeWorkspaceCodes(userForm.workspaceCodes ?? [])

  savingUser.value = true
  try {
    if (userDialogMode.value === 'create') {
      await platformApi.createUser({
        username: userForm.username.trim(),
        email: userForm.email.trim(),
        displayName: userForm.displayName.trim(),
        roleCode: userForm.roleCode,
        workspaceCodes,
      })
      ElMessage.success('成员创建成功，默认密码为 zhyt@2025')
    } else if (userForm.id !== null) {
      await platformApi.updateUser(userForm.id, {
        email: userForm.email.trim(),
        displayName: userForm.displayName.trim(),
        roleCode: userForm.roleCode,
        status: userForm.status,
        workspaceCodes,
      })
      ElMessage.success('成员信息更新成功')
    }
    userDialogVisible.value = false
    await Promise.all([loadBaseData(), loadMembers()])
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    savingUser.value = false
  }
}

async function toggleUserStatus(row: UserItem) {
  const nextStatus = row.status === 1 ? 0 : 1
  const actionText = nextStatus === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${actionText}成员“${row.displayName}”吗？`, `${actionText}成员`, { type: 'warning' })
    await platformApi.updateUser(row.id, {
      email: row.email,
      displayName: row.displayName,
      roleCode: row.roleCode,
      status: nextStatus,
      workspaceCodes: row.workspaceCodes,
    })
    ElMessage.success(`成员已${actionText}`)
    await Promise.all([loadBaseData(), loadMembers()])
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

async function confirmResetPassword(row: UserItem) {
  try {
    await ElMessageBox.confirm(
      `确认将成员“${row.displayName}”的密码重置为默认密码 zhyt@2025 吗？`,
      '重置密码',
      { type: 'warning' },
    )
    const response = await platformApi.resetUserPassword(row.id)
    ElMessage.success(`成员 ${response.username} 的密码已重置为 ${response.defaultPassword}`)
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

function openBatchMemberCreate(roleCode: 'ADMIN' | 'MEMBER' = 'MEMBER') {
  if (!memberWorkspaceCode.value) {
    ElMessage.error('请先选择工作空间')
    return
  }
  resetBatchMemberForm()
  batchMemberForm.roleCode = roleCode
  batchMemberDialogVisible.value = true
}

async function submitBatchMembers() {
  if (!memberWorkspaceCode.value) {
    ElMessage.error('请先选择工作空间')
    return
  }
  if (!batchMemberForm.userIds.length) {
    ElMessage.error('请至少选择一个成员')
    return
  }
  savingMember.value = true
  try {
    await platformApi.createWorkspaceMembers(memberWorkspaceCode.value, {
      userIds: batchMemberForm.userIds,
      roleCode: batchMemberForm.roleCode,
    })
    ElMessage.success('空间成员添加成功')
    batchMemberDialogVisible.value = false
    notifyWorkspaceListChanged()
    await Promise.all([loadBaseData(), loadMembers()])
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    savingMember.value = false
  }
}

async function confirmChangeMemberRole(row: WorkspaceMemberItem, roleCode: 'ADMIN' | 'MEMBER') {
  const actionText = roleCode === 'ADMIN' ? '设为空间管理员' : '降为普通成员'
  try {
    await ElMessageBox.confirm(
      `确认将成员“${row.displayName}”${actionText}吗？`,
      actionText,
      { type: 'warning' },
    )
    await platformApi.updateWorkspaceMember(memberWorkspaceCode.value, row.id, { roleCode })
    ElMessage.success(`成员已${actionText}`)
    notifyWorkspaceListChanged()
    await Promise.all([loadBaseData(), loadMembers()])
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

async function confirmDeleteMember(row: WorkspaceMemberItem) {
  try {
    await ElMessageBox.confirm(
      `移除后，成员“${row.displayName}”将失去 ${resolveWorkspaceName(memberWorkspaceCode.value)} 的访问权限，是否继续？`,
      '移除空间成员',
      { type: 'warning' },
    )
    await platformApi.deleteWorkspaceMember(memberWorkspaceCode.value, row.id)
    ElMessage.success('空间成员移除成功')
    notifyWorkspaceListChanged()
    await Promise.all([loadBaseData(), loadMembers()])
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

function openEnvCreate() {
  resetEnvForm()
  envDialogMode.value = 'create'
  envDialogVisible.value = true
}

function openEnvEdit(row: EnvConfigItem) {
  envDialogMode.value = 'edit'
  envForm.id = row.id
  envForm.workspaceCode = row.workspaceCode
  envForm.envType = normalizeConfigEnvType(row)
  envForm.envName = row.envName
  envForm.baseUrl = row.baseUrl
  envForm.configJson = envVisualMeta(row).description
  envForm.status = row.status
  envDialogVisible.value = true
}

async function submitEnv() {
  if (!envForm.envName.trim() || !envForm.baseUrl.trim()) {
    ElMessage.error('请先填写环境名称和基础地址')
    return
  }
  if (isAllScope.value && !envForm.workspaceCode) {
    ElMessage.error('全部空间视角下必须选择目标空间')
    return
  }
  savingEnv.value = true
  try {
    const payload = {
      workspaceCode: envForm.workspaceCode,
      envType: envForm.envType,
      envName: envForm.envName.trim(),
      baseUrl: envForm.baseUrl.trim(),
      configJson: envForm.configJson.trim(),
      status: envForm.status,
    }
    if (envDialogMode.value === 'create') {
      await platformApi.createSettingsEnv(workspaceCode.value, payload)
      ElMessage.success('环境创建成功')
    } else if (envForm.id !== null) {
      await platformApi.updateSettingsEnv(workspaceCode.value, envForm.id, payload)
      ElMessage.success('环境更新成功')
    }
    envDialogVisible.value = false
    await loadScopedSettings()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    savingEnv.value = false
  }
}

async function confirmDeleteEnv(row: EnvConfigItem) {
  try {
    await ElMessageBox.confirm(`确认删除环境“${row.envName}”吗？`, '删除环境', { type: 'warning' })
    await platformApi.deleteSettingsEnv(workspaceCode.value, row.id)
    ElMessage.success('环境删除成功')
    await loadScopedSettings()
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

async function toggleEnvStatus(row: EnvConfigItem) {
  const nextStatus = row.status === 1 ? 0 : 1
  const actionText = nextStatus === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${actionText}环境“${row.envName}”吗？`, `${actionText}环境`, { type: 'warning' })
    await platformApi.updateSettingsEnvStatus(workspaceCode.value, row.id, nextStatus)
    ElMessage.success(`环境已${actionText}`)
    await loadScopedSettings()
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

function openParamCreate() {
  resetParamForm()
  paramDialogMode.value = 'create'
  paramDialogVisible.value = true
}

function openParamEdit(row: ParamSetItem) {
  paramDialogMode.value = 'edit'
  paramForm.id = row.id
  paramForm.workspaceCode = row.workspaceCode
  paramForm.paramType = configParamTypeMeta(row).label === '接口参数'
    ? 'API'
    : configParamTypeMeta(row).label === '业务参数'
      ? 'BUSINESS'
      : 'GLOBAL'
  paramForm.paramName = row.paramName
  const parsed = parseParamContent(row.contentJson)
  paramForm.contentJson = parsed.value
  paramDescription.value = parsed.description
  paramSensitive.value = parsed.sensitive
  paramForm.status = row.status
  paramDialogVisible.value = true
}

async function submitParam() {
  if (!paramForm.paramName.trim()) {
    ElMessage.error('请先填写参数集名称')
    return
  }
  if (isAllScope.value && !paramForm.workspaceCode) {
    ElMessage.error('全部空间视角下必须选择目标空间')
    return
  }
  savingParam.value = true
  try {
    const payload = {
      workspaceCode: paramForm.workspaceCode,
      paramType: paramForm.paramType,
      paramName: paramForm.paramName.trim(),
      contentJson: buildParamContentJson(),
      status: paramForm.status,
    }
    if (paramDialogMode.value === 'create') {
      await platformApi.createSettingsParam(workspaceCode.value, payload)
      ElMessage.success('参数集创建成功')
    } else if (paramForm.id !== null) {
      await platformApi.updateSettingsParam(workspaceCode.value, paramForm.id, payload)
      ElMessage.success('参数集更新成功')
    }
    paramDialogVisible.value = false
    await loadScopedSettings()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    savingParam.value = false
  }
}

async function confirmDeleteParam(row: ParamSetItem) {
  try {
    await ElMessageBox.confirm(`确认删除参数集“${row.paramName}”吗？`, '删除参数集', { type: 'warning' })
    await platformApi.deleteSettingsParam(workspaceCode.value, row.id)
    ElMessage.success('参数集删除成功')
    await loadScopedSettings()
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

async function toggleParamStatus(row: ParamSetItem) {
  const nextStatus = row.status === 1 ? 0 : 1
  const actionText = nextStatus === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${actionText}参数集“${row.paramName}”吗？`, `${actionText}参数集`, { type: 'warning' })
    await platformApi.updateSettingsParamStatus(workspaceCode.value, row.id, nextStatus)
    ElMessage.success(`参数集已${actionText}`)
    await loadScopedSettings()
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

function openDbConnectionCreate() {
  resetDbConnectionForm()
  dbConnectionDialogMode.value = 'create'
  dbConnectionDialogVisible.value = true
}

function openDbConnectionEdit(row: DbConnectionItem) {
  dbConnectionDialogMode.value = 'edit'
  dbConnectionForm.id = row.id
  dbConnectionForm.workspaceCode = row.workspaceCode
  dbConnectionForm.connectionName = row.connectionName
  dbConnectionForm.dbType = row.dbType
  dbConnectionForm.driverClassName = row.driverClassName ?? ''
  dbConnectionForm.jdbcUrl = row.jdbcUrl
  const parsed = parseJdbcUrl(row.jdbcUrl)
  dbHost.value = parsed.host
  dbPort.value = parsed.port || (configDbTypeOptions.find(item => item.value === row.dbType)?.port ?? '3306')
  dbName.value = parsed.database
  dbConnectionForm.username = row.username ?? ''
  dbConnectionForm.password = ''
  dbPasswordVisible.value = false
  dbConnectionForm.poolMax = row.poolMax
  dbConnectionForm.timeoutMs = row.timeoutMs
  dbConnectionForm.description = row.description ?? ''
  dbConnectionForm.status = row.status
  dbConnectionDialogVisible.value = true
}

function dbConnectionPayload() {
  dbConnectionForm.jdbcUrl = buildJdbcUrl()
  return {
    workspaceCode: dbConnectionForm.workspaceCode,
    connectionName: dbConnectionForm.connectionName.trim(),
    dbType: dbConnectionForm.dbType,
    driverClassName: dbConnectionForm.driverClassName?.trim() || null,
    jdbcUrl: dbConnectionForm.jdbcUrl.trim(),
    username: dbConnectionForm.username?.trim() || null,
    password: dbConnectionForm.password || null,
    poolMax: dbConnectionForm.poolMax,
    timeoutMs: dbConnectionForm.timeoutMs,
    description: dbConnectionForm.description?.trim() || null,
    status: dbConnectionForm.status,
  }
}

async function submitDbConnection() {
  if (!dbConnectionForm.connectionName.trim() || !dbHost.value.trim() || !dbName.value.trim()) {
    ElMessage.error('请先填写连接名称、主机地址和数据库名')
    return
  }
  if (isAllScope.value && !dbConnectionForm.workspaceCode) {
    ElMessage.error('全部空间视角下必须选择目标空间')
    return
  }
  savingDbConnection.value = true
  try {
    const payload = dbConnectionPayload()
    if (dbConnectionDialogMode.value === 'create') {
      await platformApi.createSettingsDbConnection(workspaceCode.value, payload)
      ElMessage.success('数据库连接已创建')
    } else if (dbConnectionForm.id !== null) {
      await platformApi.updateSettingsDbConnection(workspaceCode.value, dbConnectionForm.id, payload)
      ElMessage.success('数据库连接已更新')
    }
    dbConnectionDialogVisible.value = false
    await loadScopedSettings()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    savingDbConnection.value = false
  }
}

async function testDbConnection(row?: DbConnectionItem) {
  try {
    const payload = row ? { id: row.id } : { id: dbConnectionForm.id, ...dbConnectionPayload() }
    const result = await platformApi.testSettingsDbConnection(workspaceCode.value, payload)
    ElMessage.success(result.message || '连接测试成功')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function confirmDeleteDbConnection(row: DbConnectionItem) {
  try {
    await ElMessageBox.confirm(`确认删除数据库连接“${row.connectionName}”吗？`, '删除数据库连接', { type: 'warning' })
    await platformApi.deleteSettingsDbConnection(workspaceCode.value, row.id)
    ElMessage.success('数据库连接已删除')
    await loadScopedSettings()
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

async function toggleDbConnectionStatus(row: DbConnectionItem) {
  const nextStatus = row.status === 1 ? 0 : 1
  const actionText = nextStatus === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${actionText}数据库连接“${row.connectionName}”吗？`, `${actionText}数据库连接`, { type: 'warning' })
    await platformApi.updateSettingsDbConnectionStatus(workspaceCode.value, row.id, nextStatus)
    ElMessage.success(`数据库连接已${actionText}`)
    await loadScopedSettings()
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

watch(() => userForm.roleCode, (value) => {
  if (value === 'ADMIN') {
    userForm.workspaceCodes = []
  }
})

watch(memberWorkspaceCode, () => {
  memberFilters.workspaceCode = memberWorkspaceCode.value
  void loadMembers()
})

watch(() => workspaceCode.value, () => {
  if (!isConfigCenter.value) {
    return
  }
  resetEnvForm()
  resetParamForm()
  resetDbConnectionForm()
  void loadScopedSettings()
})

watch(() => route.query.tab, () => {
  syncSettingsTabFromRoute()
})

watch(visibleTabs, () => {
  syncSettingsTabFromRoute()
})

watch(activeTab, (tab) => {
  const nextTab = normalizeSettingsTab(tab)
  if (nextTab !== tab) {
    activeTab.value = nextTab
    return
  }
  const currentQueryTab = typeof route.query.tab === 'string' ? route.query.tab : ''
  if (currentQueryTab === nextTab) {
    return
  }
  void router.replace({
    query: {
      ...route.query,
      tab: nextTab,
    },
  })
})

onMounted(async () => {
  syncSettingsTabFromRoute()
  if (isConfigCenter.value) {
    envFilterMemory.load()
    paramFilterMemory.load()
    dbConnectionFilterMemory.load()
  } else {
    workspaceFilterMemory.load()
    userFilterMemory.load()
    memberFilterMemory.load()
    memberWorkspaceCode.value = memberFilters.workspaceCode
  }
  await loadBaseData()
  if (isConfigCenter.value) {
    await loadScopedSettings()
  } else {
    await loadMembers()
  }
})
</script>

<template>
  <section :class="isConfigCenter ? 'config-center-page-shell' : 'settings-page-shell'">
    <div v-if="!isConfigCenter" class="settings-figma-shell" v-loading="pageLoading">
      <aside class="settings-category-sidebar">
        <button
          v-for="item in settingsNavItems"
          :key="item.id"
          type="button"
          class="settings-category-item"
          :class="{ 'is-active': activeTab === item.id }"
          @click="activeTab = item.id"
        >
          <component :is="item.icon" :size="17" />
          <span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.desc }}</small>
          </span>
        </button>
      </aside>

      <main class="settings-figma-content">
        <AiConnectionSettingsPanel v-if="activeTab === 'aiConnection'" />
        <section v-else-if="activeTab === 'workspace'" class="workspace-config-page" :class="{ 'is-managing': managingWorkspace }">
          <template v-if="!managingWorkspace">
            <header class="workspace-config-header">
              <div>
                <h2>工作空间配置</h2>
                <p>管理测试空间，控制不同项目和团队的测试资源隔离</p>
              </div>
              <button v-if="canManageSettings" type="button" class="workspace-create-button" @click="openWorkspaceCreate">
                <el-icon><Plus /></el-icon>
                <span>新增空间</span>
              </button>
            </header>

            <div class="workspace-stat-grid">
              <article
                v-for="stat in workspaceStatCards"
                :key="stat.label"
                class="workspace-stat-card"
                :class="`is-${stat.tone}`"
              >
                <div>
                  <span>{{ stat.label }}</span>
                  <component :is="stat.icon" :size="16" />
                </div>
                <strong>{{ stat.value }}</strong>
              </article>
            </div>

            <div v-if="filteredWorkspaces.length" class="workspace-card-grid">
              <article
              v-for="item in filteredWorkspaces"
              :key="item.code"
              class="workspace-config-card"
              :class="{ 'is-disabled': item.status === 0 }"
            >
                <div class="workspace-card-main">
                  <div class="workspace-card-head">
                    <div class="workspace-card-icon" :class="workspaceTypeMeta(item).className">
                      <component :is="workspaceTypeMeta(item).icon" :size="20" />
                    </div>
                    <div class="workspace-card-title">
                      <div class="workspace-card-name-row">
                        <h3>{{ item.name }}</h3>
                      <span class="workspace-status-badge" :class="{ 'is-disabled': item.status === 0 }">
                        {{ item.status === 0 ? '已禁用' : '启用中' }}
                      </span>
                      </div>
                      <p>{{ item.description || '暂无空间说明' }}</p>
                    </div>
                  </div>

                  <div v-if="canManageSettings" class="workspace-card-actions">
                    <button type="button" title="成员管理" @click="openWorkspaceMembers(item)">
                      <UserCog :size="16" />
                    </button>
                    <button type="button" title="编辑" @click="openWorkspaceEdit(item)">
                      <Edit2 :size="16" />
                    </button>
                    <button type="button" title="删除" class="is-danger" @click="confirmDeleteWorkspace(item)">
                      <Trash2 :size="16" />
                    </button>
                  </div>
                </div>

                <footer class="workspace-card-meta">
                  <span class="workspace-type-badge" :class="workspaceTypeMeta(item).className">
                    <component :is="workspaceTypeMeta(item).icon" :size="13" />
                    {{ workspaceTypeMeta(item).label }}空间
                  </span>
                  <span>
                    <User :size="13" />
                    {{ workspaceOwnerName(item) }} (负责人)
                  </span>
                  <span>
                    <Users :size="13" />
                    {{ workspaceMemberCount(item) }} 名成员
                  </span>
                </footer>
              </article>
            </div>

            <div v-else class="workspace-empty-state">
              <div>
                <Layers :size="32" />
              </div>
              <strong>暂无工作空间</strong>
              <p>创建第一个工作空间开始管理测试资源</p>
              <button v-if="canManageSettings" type="button" class="workspace-create-button" @click="openWorkspaceCreate">
                <el-icon><Plus /></el-icon>
                <span>创建工作空间</span>
              </button>
            </div>
          </template>

          <template v-else>
            <div class="workspace-manage-head">
              <div class="workspace-manage-top">
                <button type="button" class="workspace-back-button" @click="closeWorkspaceMembers">
                  <ChevronLeft :size="16" />
                  返回工作空间列表
                </button>
                <div class="workspace-manage-summary">
                  <div class="workspace-manage-icon" :class="workspaceTypeMeta(managingWorkspace).className">
                    <component :is="workspaceTypeMeta(managingWorkspace).icon" :size="26" />
                  </div>
                  <div>
                    <h1>{{ managingWorkspace.name }}</h1>
                    <p>{{ managingWorkspace.description || '暂无空间说明' }}</p>
                    <div class="workspace-manage-meta">
                      <span class="workspace-type-badge" :class="workspaceTypeMeta(managingWorkspace).className">
                        <component :is="workspaceTypeMeta(managingWorkspace).icon" :size="13" />
                        {{ workspaceTypeMeta(managingWorkspace).label }}空间
                      </span>
                      <span>创建于当前系统空间</span>
                    </div>
                  </div>
                </div>
              </div>
              <button v-if="canManageSettings" type="button" class="workspace-save-button" @click="openWorkspaceEdit(managingWorkspace)">
                编辑空间
              </button>
            </div>

            <nav class="workspace-manage-tabs">
              <button type="button" class="is-active">成员管理</button>
              <button type="button">权限设置</button>
              <button type="button">操作日志</button>
            </nav>

            <div class="workspace-member-page" v-loading="memberLoading">
              <div class="workspace-member-title">
                <h2>成员管理</h2>
                <span>共 {{ filteredMembers.length + 1 }} 名成员</span>
              </div>

              <section class="workspace-member-section is-owner">
                <div class="workspace-member-section-title">
                  <Crown :size="16" />
                  <h3>负责人 (1人)</h3>
                </div>
                <div class="workspace-member-row is-owner">
                  <div class="workspace-member-profile">
                    <div class="workspace-avatar is-owner">{{ workspaceUserInitial(workspaceOwnerName(managingWorkspace)) }}</div>
                    <div>
                      <strong>{{ workspaceOwnerName(managingWorkspace) }}</strong>
                      <p>拥有空间最高权限</p>
                    </div>
                  </div>
                  <span class="workspace-role-badge is-owner">Owner</span>
                </div>
              </section>

              <section class="workspace-member-section">
                <div class="workspace-member-section-head">
                  <div class="workspace-member-section-title">
                    <Shield :size="16" />
                    <h3>空间管理员 ({{ workspaceAdminMembers.length }}人)</h3>
                  </div>
                  <button
                    v-if="canManageSettings"
                    type="button"
                    class="workspace-member-add is-admin"
                    :disabled="!selectableMembersForWorkspace.length"
                    @click="openBatchMemberCreate('ADMIN')"
                  >
                    <el-icon><Plus /></el-icon>
                    添加空间管理员
                  </button>
                </div>
                <div v-if="workspaceAdminMembers.length" class="workspace-member-list">
                  <div
                    v-for="item in workspaceAdminMembers"
                    :key="item.id"
                    class="workspace-member-row"
                  >
                    <div class="workspace-member-profile">
                      <div class="workspace-avatar is-admin">{{ workspaceUserInitial(item.displayName) }}</div>
                      <div>
                        <strong>{{ item.displayName }}</strong>
                        <p>{{ memberRoleDescription(item.roleCode) }}</p>
                      </div>
                    </div>
                    <div class="workspace-member-actions">
                      <span class="workspace-role-badge is-admin">空间管理员</span>
                      <button
                        v-if="canManageSettings"
                        type="button"
                        class="workspace-member-text-action"
                        @click="confirmChangeMemberRole(item, 'MEMBER')"
                      >
                        降为普通
                      </button>
                      <button v-if="canManageSettings" type="button" @click="confirmDeleteMember(item)">
                        <Trash2 :size="16" />
                      </button>
                    </div>
                  </div>
                </div>
                <div v-else class="workspace-member-empty">暂无管理员，点击右上角添加</div>
              </section>

              <section class="workspace-member-section">
                <div class="workspace-member-section-head">
                  <div class="workspace-member-section-title">
                    <Users :size="16" />
                    <h3>普通成员 ({{ workspaceRegularMembers.length }}人)</h3>
                  </div>
                  <button
                    v-if="canManageSettings"
                    type="button"
                    class="workspace-member-add"
                    :disabled="!selectableMembersForWorkspace.length"
                    @click="openBatchMemberCreate('MEMBER')"
                  >
                    <el-icon><Plus /></el-icon>
                    添加普通成员
                  </button>
                </div>
                <div v-if="workspaceRegularMembers.length" class="workspace-member-list">
                  <div
                    v-for="item in workspaceRegularMembers"
                    :key="item.id"
                    class="workspace-member-row"
                  >
                    <div class="workspace-member-profile">
                      <div class="workspace-avatar">{{ workspaceUserInitial(item.displayName) }}</div>
                      <div>
                        <strong>{{ item.displayName }}</strong>
                        <p>{{ memberRoleDescription(item.roleCode) }}</p>
                      </div>
                    </div>
                    <div class="workspace-member-actions">
                      <span class="workspace-role-badge">普通成员</span>
                      <button
                        v-if="canManageSettings"
                        type="button"
                        class="workspace-member-text-action"
                        @click="confirmChangeMemberRole(item, 'ADMIN')"
                      >
                        设为管理员
                      </button>
                      <button v-if="canManageSettings" type="button" @click="confirmDeleteMember(item)">
                        <Trash2 :size="16" />
                      </button>
                    </div>
                  </div>
                </div>
                <div v-else class="workspace-member-empty">暂无成员，点击右上角添加</div>
              </section>
            </div>
          </template>
        </section>
        <section v-else-if="activeTab === 'team'" class="team-management-page">
          <template v-if="canManageSettings">
            <header class="team-management-header">
              <div>
                <h2>团队管理</h2>
                <p>管理平台账号、平台角色与可访问空间。空间管理员身份请在「空间配置 → 成员管理」中维护。</p>
              </div>
              <div class="team-header-actions">
                <button type="button" class="team-secondary-button" @click="openBatchUserCreate">
                  批量新增
                </button>
                <button type="button" class="team-primary-button" @click="openUserCreate">
                  <el-icon><Plus /></el-icon>
                  <span>新增账号</span>
                </button>
              </div>
            </header>

            <div class="team-stat-grid">
              <article
                v-for="stat in teamStatCards"
                :key="stat.label"
                class="team-stat-card"
                :class="`is-${stat.tone}`"
              >
                <div>
                  <span>{{ stat.label }}</span>
                  <component :is="stat.icon" :size="16" />
                </div>
                <strong>{{ stat.value }}</strong>
              </article>
            </div>

            <section class="team-filter-card">
              <label class="team-filter-field is-keyword">
                <span>关键词</span>
                <input v-model="userFilters.keyword" placeholder="搜索姓名、账号、邮箱或空间">
              </label>
              <label class="team-filter-field">
                <span>平台角色</span>
                <select v-model="userFilters.roleCode">
                  <option value="">全部角色</option>
                  <option value="ADMIN">平台管理员</option>
                  <option value="MEMBER">普通账号</option>
                </select>
              </label>
              <label class="team-filter-field">
                <span>状态</span>
                <select v-model="userFilters.status">
                  <option value="">全部状态</option>
                  <option value="1">启用中</option>
                  <option value="0">已停用</option>
                </select>
              </label>
              <button type="button" class="team-reset-button" @click="resetUserFilters">重置</button>
            </section>

            <section class="team-table-card" v-loading="userLoading">
              <table v-if="filteredUsers.length">
                <thead>
                  <tr>
                    <th>成员</th>
                    <th>平台角色</th>
                    <th>可访问空间</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="user in filteredUsers" :key="user.id">
                    <td>
                      <div class="team-member-cell">
                        <div class="team-avatar">{{ workspaceUserInitial(user.displayName) }}</div>
                        <div>
                          <strong>{{ user.displayName }}</strong>
                          <p>{{ user.username }} · {{ user.email }}</p>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span class="team-role-badge" :class="teamRoleClass(user.roleCode)">
                        {{ roleLabel(user.roleCode) }}
                      </span>
                    </td>
                    <td>
                      <span class="team-workspace-text">{{ workspaceSummary(user) }}</span>
                    </td>
                    <td>
                      <span class="team-status-badge" :class="{ 'is-disabled': user.status !== 1 }">
                        {{ teamStatusLabel(user.status) }}
                      </span>
                    </td>
                    <td>
                      <div class="team-row-actions">
                        <button
                          type="button"
                          :disabled="!canEditUser(user)"
                          @click="openUserEdit(user)"
                        >
                          编辑
                        </button>
                        <button
                          type="button"
                          :disabled="!canToggleUser(user)"
                          @click="toggleUserStatus(user)"
                        >
                          {{ user.status === 1 ? '停用' : '启用' }}
                        </button>
                        <button
                          type="button"
                          :disabled="!canResetPassword(user)"
                          @click="confirmResetPassword(user)"
                        >
                          重置密码
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
              <div v-else class="team-empty-state">
                <Users :size="32" />
                <strong>暂无匹配账号</strong>
                <p>调整筛选条件，或新增一个普通账号。</p>
              </div>
            </section>
          </template>

          <div v-else class="settings-placeholder">
            <div>
              <Users :size="34" />
              <p>暂无权限访问团队管理。</p>
            </div>
          </div>
        </section>
        <div v-else class="settings-placeholder">
          <div>
            <component :is="settingsNavItems.find(item => item.id === activeTab)?.icon ?? Settings" :size="34" />
            <p>{{ settingsNavItems.find(item => item.id === activeTab)?.label ?? '设置' }} 页面建设中...</p>
          </div>
        </div>
      </main>
    </div>

    <div v-else class="config-center-shell" v-loading="pageLoading">
      <aside class="config-center-sidebar">
        <button
          v-for="item in configCenterNavItems"
          :key="item.id"
          type="button"
          class="config-nav-item"
          :class="{ 'is-active': activeTab === item.id }"
          @click="activeTab = item.id"
        >
          <component :is="item.icon" :size="17" />
          <span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.desc }}</small>
          </span>
        </button>
      </aside>

      <main class="config-center-content">
        <section v-if="canManageSettings && activeTab === 'env'" class="config-content-page">
          <header class="config-page-header">
            <div>
              <h2>环境配置</h2>
              <p>管理不同测试环境的配置信息</p>
            </div>
            <button type="button" class="config-primary-button" @click="openEnvCreate">
              <el-icon><Plus /></el-icon>
              新增环境
            </button>
          </header>

          <div class="config-stat-grid is-three">
            <div v-for="stat in configEnvStats" :key="stat.label" class="config-stat-card" :class="`is-${stat.tone}`">
              <span>{{ stat.label }}</span>
              <strong>{{ stat.value }}</strong>
            </div>
          </div>

          <div v-loading="envLoading" class="config-env-grid">
            <article v-for="env in configEnvs" :key="env.id" class="config-env-card">
              <div class="config-card-main">
                <div class="config-card-title">
                  <div class="config-card-name-row">
                    <h3>{{ env.envName }}</h3>
                    <span class="config-status-pill" :class="env.status === 1 ? 'is-success' : 'is-neutral'">
                      {{ env.status === 1 ? '启用中' : '已停用' }}
                    </span>
                  </div>
                  <p>{{ envVisualMeta(env).description }}</p>
                </div>
                <div class="config-card-actions">
                  <button type="button" aria-label="编辑环境" @click="openEnvEdit(env)">
                    <Edit2 :size="16" />
                  </button>
                  <button type="button" class="is-danger" aria-label="删除环境" @click="confirmDeleteEnv(env)">
                    <Trash2 :size="16" />
                  </button>
                </div>
              </div>
              <div class="config-card-body">
                <span class="config-type-badge" :class="envVisualMeta(env).typeClassName">{{ envVisualMeta(env).typeLabel }}</span>
                <div class="config-code-box">{{ env.baseUrl }}</div>
                <div class="config-card-meta">创建于 {{ envCreatedText(env) }}</div>
              </div>
            </article>
            <div v-if="!configEnvs.length" class="config-empty-state">暂无环境配置</div>
          </div>
        </section>

        <section v-else-if="canManageSettings && activeTab === 'param'" class="config-content-page">
          <header class="config-page-header">
            <div>
              <h2>参数配置</h2>
              <p>管理全局配置参数和业务参数</p>
            </div>
            <button type="button" class="config-primary-button" @click="openParamCreate">
              <el-icon><Plus /></el-icon>
              新增参数
            </button>
          </header>

          <div class="config-stat-grid is-four">
            <div v-for="stat in configParamStats" :key="stat.label" class="config-stat-card" :class="`is-${stat.tone}`">
              <span>{{ stat.label }}</span>
              <strong>{{ stat.value }}</strong>
            </div>
          </div>

          <div class="config-segmented-tabs">
            <button type="button" :class="{ 'is-active': configParamCategoryFilter === '' }" @click="selectConfigParamFilter('')">全部</button>
            <button type="button" :class="{ 'is-active': configParamCategoryFilter === 'global' }" @click="selectConfigParamFilter('global')">全局参数</button>
            <button type="button" :class="{ 'is-active': configParamCategoryFilter === 'api' }" @click="selectConfigParamFilter('api')">接口参数</button>
            <button type="button" :class="{ 'is-active': configParamCategoryFilter === 'business' }" @click="selectConfigParamFilter('business')">业务参数</button>
          </div>

          <div v-loading="paramLoading" class="config-param-table-card">
            <table>
              <thead>
                <tr>
                  <th>参数名</th>
                  <th>参数值</th>
                  <th>类型</th>
                  <th>说明</th>
                  <th v-if="canManageSettings">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="param in configFilteredParams" :key="param.id">
                  <td>
                    <div class="config-table-title">{{ param.paramName }}</div>
                    <div v-if="isAllScope" class="config-table-subtitle">{{ param.workspaceName }}</div>
                  </td>
                  <td><span class="config-value-chip">{{ configParamValueText(param) }}</span></td>
                  <td><span class="config-type-badge" :class="configParamTypeMeta(param).className">{{ configParamTypeMeta(param).label }}</span></td>
                  <td><span class="config-table-muted">{{ param.paramType }}</span></td>
                  <td v-if="canManageSettings">
                    <button type="button" class="config-text-action" @click="openParamEdit(param)">编辑</button>
                    <button type="button" class="config-text-action is-danger" @click="confirmDeleteParam(param)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!configFilteredParams.length" class="config-empty-state">暂无参数配置</div>
          </div>
        </section>

        <section v-else-if="canManageSettings && activeTab === 'dbConnection'" class="config-content-page" data-testid="db-connection-tab-pane">
          <header class="config-page-header">
            <div>
              <h2>数据库连接</h2>
              <p>管理测试数据库连接配置</p>
            </div>
            <button type="button" class="config-primary-button" data-testid="db-connection-create" @click="openDbConnectionCreate">
              <el-icon><Plus /></el-icon>
              新增连接
            </button>
          </header>

          <div class="config-stat-grid is-four">
            <div v-for="stat in configDbStats" :key="stat.label" class="config-stat-card" :class="`is-${stat.tone}`">
              <span>{{ stat.label }}</span>
              <strong>{{ stat.value }}</strong>
            </div>
          </div>

          <div v-loading="dbConnectionLoading" class="config-db-list">
            <article v-for="db in configDbConnections" :key="db.id" class="config-db-card">
              <div class="config-db-main">
                <div class="config-db-title">
                  <div class="config-card-name-row">
                    <h3>{{ db.connectionName }}</h3>
                    <span class="config-status-pill" :class="db.status === 1 ? 'is-success' : 'is-danger'">
                      {{ db.status === 1 ? '已连接' : '连接异常' }}
                    </span>
                  </div>
                  <div class="config-db-meta-row">
                    <span class="config-type-badge is-blue">{{ db.dbType }}</span>
                    <span>{{ dbHostSummary(db.jdbcUrl) }}</span>
                    <span>数据库：{{ dbNameSummary(db) }}</span>
                  </div>
                  <p v-if="isAllScope">所属空间：{{ db.workspaceName || resolveWorkspaceName(db.workspaceCode) }}</p>
                </div>
                <div class="config-card-actions is-visible">
                  <button type="button" aria-label="测试连接" @click="testDbConnection(db)">
                    <Activity :size="16" />
                  </button>
                  <button type="button" aria-label="编辑连接" @click="openDbConnectionEdit(db)">
                    <Edit2 :size="16" />
                  </button>
                  <button type="button" class="is-danger" aria-label="删除连接" @click="confirmDeleteDbConnection(db)">
                    <Trash2 :size="16" />
                  </button>
                </div>
              </div>
            </article>
            <div v-if="!configDbConnections.length" class="config-empty-state">暂无数据库连接</div>
          </div>
        </section>

        <div v-else class="settings-single-panel">暂无权限访问配置中心。</div>
      </main>
    </div>

    <div v-if="false" class="legacy-settings-bindings">
      <ListToolbar :title="currentScopeText">
        <template #filters>
          <el-icon><RefreshRight /></el-icon>
        </template>
      </ListToolbar>
      <span>{{ memberViewMode }}</span>
      <span>{{ filteredUsers.length }}{{ filteredEnvs.length }}{{ filteredParams.length }}{{ filteredDbConnections.length }}</span>
      <button type="button" @click="resetWorkspaceFilters(); resetUserFilters(); resetMemberFilters(); resetEnvFilters(); resetParamFilters(); resetDbConnectionFilters()">
        reset
      </button>
      <button type="button" @click="openUserCreate">user</button>
      <button
        v-for="user in filteredUsers"
        :key="`legacy-user-${user.id}`"
        type="button"
        @click="openUserEdit(user); canEditUser(user); canToggleUser(user); canResetPassword(user); toggleUserStatus(user); confirmResetPassword(user); workspaceSummary(user)"
      >
        user
      </button>
      <button
        v-for="env in filteredEnvs"
        :key="`legacy-env-${env.id}`"
        type="button"
        @click="toggleEnvStatus(env)"
      >
        env
      </button>
      <button
        v-for="param in filteredParams"
        :key="`legacy-param-${param.id}`"
        type="button"
        @click="toggleParamStatus(param)"
      >
        param
      </button>
      <button
        v-for="db in filteredDbConnections"
        :key="`legacy-db-${db.id}`"
        type="button"
        @click="toggleDbConnectionStatus(db)"
      >
        db
      </button>
    </div>

    <Teleport to="body">
      <div v-if="workspaceDialogVisible" class="workspace-modal-overlay">
        <div class="workspace-modal-panel">
          <header class="workspace-modal-header">
            <h2>{{ workspaceDialogMode === 'create' ? '新增工作空间' : '编辑工作空间' }}</h2>
            <button type="button" aria-label="关闭" @click="workspaceDialogVisible = false">
              <X :size="16" />
            </button>
          </header>

          <div class="workspace-modal-form">
            <label class="workspace-modal-field">
              <span>空间名称 *</span>
              <input v-model="workspaceForm.workspaceName" placeholder="例如：开户工作空间">
            </label>

            <label class="workspace-modal-field">
              <span>空间描述</span>
              <textarea v-model="workspaceForm.description" rows="3" placeholder="描述该工作空间的用途和范围" />
            </label>

            <div class="workspace-modal-field">
              <span>空间类型</span>
              <div class="workspace-modal-type-grid">
                <button type="button" :class="{ 'is-active': workspaceForm.workspaceType === 'PROJECT' }" @click="workspaceForm.workspaceType = 'PROJECT'">
                  <span class="workspace-modal-type-icon">📦</span>
                  <strong>项目空间</strong>
                  <small>项目专用</small>
                </button>
                <button type="button" :class="{ 'is-active': workspaceForm.workspaceType === 'TEAM' }" @click="workspaceForm.workspaceType = 'TEAM'">
                  <span class="workspace-modal-type-icon">👥</span>
                  <strong>团队空间</strong>
                  <small>团队协作</small>
                </button>
                <button type="button" :class="{ 'is-active': workspaceForm.workspaceType === 'PRODUCT' }" @click="workspaceForm.workspaceType = 'PRODUCT'">
                  <span class="workspace-modal-type-icon">🎯</span>
                  <strong>产品空间</strong>
                  <small>产品测试</small>
                </button>
              </div>
            </div>

            <label class="workspace-modal-field">
              <span>负责人（Owner）</span>
              <select v-model.number="workspaceForm.ownerUserId">
                <option :value="null">组织管理员</option>
                <option v-for="user in activeUsers" :key="user.id" :value="user.id">
                  {{ user.displayName }}
                </option>
              </select>
              <small>负责人拥有空间最高权限，创建后可通过成员管理添加管理员和成员</small>
            </label>

            <div class="workspace-modal-field">
              <span>状态</span>
              <div class="workspace-modal-status">
                <button type="button" :class="{ 'is-active': workspaceForm.status === 1 }" @click="workspaceForm.status = 1">启用</button>
                <button type="button" :class="{ 'is-active': workspaceForm.status === 0 }" @click="workspaceForm.status = 0">禁用</button>
              </div>
            </div>
          </div>

          <footer class="workspace-modal-footer">
            <button type="button" class="workspace-modal-cancel" @click="workspaceDialogVisible = false">取消</button>
            <button type="button" class="workspace-modal-submit" :disabled="savingWorkspace" @click="submitWorkspace">
              {{ workspaceDialogMode === 'create' ? '创建空间' : '保存修改' }}
            </button>
          </footer>
        </div>
      </div>
    </Teleport>

    <el-dialog v-model="userDialogVisible" :title="userDialogMode === 'create' ? '新增平台账号' : '编辑平台账号'" width="620px">
      <el-form label-width="100px">
        <el-form-item label="账号" required>
          <el-input v-model="userForm.username" :disabled="userDialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="邮箱" required>
          <el-input v-model="userForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="userForm.displayName" />
        </el-form-item>
        <el-form-item label="平台角色" required>
          <el-radio-group v-model="userForm.roleCode">
            <el-radio v-if="canManageAdminUsers" value="ADMIN">平台管理员</el-radio>
            <el-radio value="MEMBER">普通账号</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="可访问空间">
          <template v-if="userForm.roleCode === 'ADMIN'">
            <div class="block-note">平台管理员默认拥有全部空间，无需单独分配。空间管理员请到「空间配置 → 成员管理」中设置。</div>
          </template>
          <template v-else>
            <el-select
              v-model="userForm.workspaceCodes"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              placeholder="可选，未分配时登录后会提示联系管理员"
            >
              <el-option
                v-for="item in businessWorkspaces"
                :key="item.code"
                :label="item.name"
                :value="item.code"
              />
            </el-select>
            <div class="block-note">未分配空间的普通账号可以登录平台，但暂时无法进入业务空间。</div>
          </template>
        </el-form-item>
        <div v-if="userDialogMode === 'create'" class="block-note">
          平台账号创建后默认密码为 <span class="mono">zhyt@2025</span>，后续可在列表中重置密码。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingUser" @click="submitUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchUserDialogVisible" title="批量新增普通账号" width="760px">
      <div class="batch-user-dialog">
        <div class="block-note">
          每行一个账号，格式为 <span class="mono">账号,姓名,邮箱</span>。也支持从 Excel 复制三列后直接粘贴。批量新增账号默认角色为普通账号，默认密码为 <span class="mono">zhyt@2025</span>。
        </div>
        <el-form label-width="100px">
          <el-form-item label="账号数据" required>
            <el-input
              v-model="batchUserForm.rawText"
              type="textarea"
              :rows="9"
              placeholder="zhangsan,张三,zhangsan@example.com&#10;lisi,李四,lisi@example.com"
            />
          </el-form-item>
          <el-form-item label="可访问空间">
            <el-select
              v-model="batchUserForm.workspaceCodes"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              placeholder="可选，未分配时登录后会提示联系管理员"
            >
              <el-option
                v-for="item in businessWorkspaces"
                :key="item.code"
                :label="item.name"
                :value="item.code"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <div v-if="batchUserResults.length" class="batch-user-results">
          <div
            v-for="item in batchUserResults"
            :key="`${item.index}-${item.username}-${item.message}`"
            class="batch-user-result-row"
            :class="{ 'is-success': item.success }"
          >
            <span>第 {{ item.index }} 行</span>
            <strong>{{ item.displayName || item.username || '未解析' }}</strong>
            <em>{{ item.message }}</em>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="batchUserDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="savingBatchUser" @click="submitBatchUsers">开始新增</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchMemberDialogVisible" :title="batchMemberForm.roleCode === 'ADMIN' ? '添加空间管理员' : '批量添加空间普通成员'" width="620px">
      <el-form label-width="100px">
        <el-form-item label="目标空间">
          <el-input :model-value="resolveWorkspaceName(memberWorkspaceCode)" disabled />
        </el-form-item>
        <el-form-item :label="batchMemberForm.roleCode === 'ADMIN' ? '空间管理员' : '普通成员'" required>
          <el-select
            v-model="batchMemberForm.userIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="请选择成员"
          >
            <el-option
              v-for="item in selectableMembersForWorkspace"
              :key="item.id"
              :label="`${item.displayName} (${item.username}) / ${item.email} / ${roleLabel(item.roleCode)}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <div class="block-note">
          这里设置的是当前空间内角色，不会改变团队管理中的平台角色。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="batchMemberDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingMember" @click="submitBatchMembers">保存</el-button>
      </template>
    </el-dialog>

    <Teleport to="body">
      <div v-if="envDialogVisible" class="config-modal-overlay">
        <div class="config-env-modal">
          <header class="config-modal-header">
            <h2>{{ envDialogMode === 'create' ? '新增环境' : '编辑环境' }}</h2>
            <button type="button" aria-label="关闭" @click="envDialogVisible = false">
              <X :size="18" />
            </button>
          </header>

          <div class="config-modal-body">
            <label v-if="isAllScope" class="config-modal-field">
              <span>目标空间</span>
              <select v-model="envForm.workspaceCode">
                <option value="">请选择目标空间</option>
                <option v-for="item in writableWorkspaceOptions" :key="item.code" :value="item.code">
                  {{ item.name }}
                </option>
              </select>
            </label>

            <label class="config-modal-field">
              <span>环境名称</span>
              <input v-model="envForm.envName" placeholder="例如：测试环境">
            </label>

            <div class="config-modal-field">
              <span>环境类型</span>
              <div class="config-modal-segment is-three">
                <button
                  v-for="item in configEnvTypeOptions"
                  :key="item.value"
                  type="button"
                  :class="{ 'is-active': envForm.envType === item.value }"
                  @click="envForm.envType = item.value"
                >
                  {{ item.label }}
                </button>
              </div>
            </div>

            <label class="config-modal-field">
              <span>Base URL</span>
              <input v-model="envForm.baseUrl" class="is-mono" placeholder="https://api.example.com">
            </label>

            <label class="config-modal-field">
              <span>描述</span>
              <textarea v-model="envForm.configJson" rows="2" />
            </label>

            <div class="config-modal-field">
              <span>状态</span>
              <div class="config-modal-segment is-two">
                <button
                  v-for="item in configEnvStatusOptions"
                  :key="item.value"
                  type="button"
                  :class="{ 'is-active': envForm.status === item.value }"
                  @click="envForm.status = item.value"
                >
                  {{ item.label }}
                </button>
              </div>
            </div>
          </div>

          <footer class="config-modal-footer">
            <button type="button" class="config-modal-cancel" @click="envDialogVisible = false">取消</button>
            <button type="button" class="config-modal-submit" :disabled="savingEnv" @click="submitEnv">保存</button>
          </footer>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="paramDialogVisible" class="config-modal-overlay">
        <div class="config-env-modal">
          <header class="config-modal-header">
            <h2>{{ paramDialogMode === 'create' ? '新增参数' : '编辑参数' }}</h2>
            <button type="button" aria-label="关闭" @click="paramDialogVisible = false">
              <X :size="18" />
            </button>
          </header>

          <div class="config-modal-body">
            <label v-if="isAllScope" class="config-modal-field">
              <span>目标空间</span>
              <select v-model="paramForm.workspaceCode">
                <option value="">请选择目标空间</option>
                <option v-for="item in writableWorkspaceOptions" :key="item.code" :value="item.code">
                  {{ item.name }}
                </option>
              </select>
            </label>

            <label class="config-modal-field">
              <span>参数名</span>
              <input v-model="paramForm.paramName" class="is-mono" placeholder="例如：REQUEST_TIMEOUT">
            </label>

            <label class="config-modal-field">
              <span>参数值</span>
              <input v-model="paramForm.contentJson" :type="paramSensitive ? 'password' : 'text'" placeholder="参数值">
            </label>

            <div class="config-modal-field">
              <span>参数类型</span>
              <div class="config-modal-segment is-three">
                <button
                  v-for="item in configParamTypeOptions"
                  :key="item.value"
                  type="button"
                  :class="{ 'is-active': paramForm.paramType === item.value }"
                  @click="paramForm.paramType = item.value"
                >
                  {{ item.label }}
                </button>
              </div>
            </div>

            <label class="config-modal-field">
              <span>说明</span>
              <textarea v-model="paramDescription" rows="2" />
            </label>

            <label class="config-modal-checkbox">
              <input v-model="paramSensitive" type="checkbox">
              <span>敏感参数（密码、密钥等）</span>
            </label>
          </div>

          <footer class="config-modal-footer">
            <button type="button" class="config-modal-cancel" @click="paramDialogVisible = false">取消</button>
            <button type="button" class="config-modal-submit" :disabled="savingParam" @click="submitParam">保存</button>
          </footer>
        </div>
      </div>
    </Teleport>
    <Teleport to="body">
      <div v-if="dbConnectionDialogVisible" class="config-modal-overlay">
        <div class="config-db-modal" data-testid="db-connection-dialog">
          <header class="config-modal-header">
            <h2>{{ dbConnectionDialogMode === 'create' ? '新增数据库连接' : '编辑数据库连接' }}</h2>
            <button type="button" aria-label="关闭" @click="dbConnectionDialogVisible = false">
              <X :size="18" />
            </button>
          </header>

          <div class="config-modal-body">
            <label v-if="isAllScope" class="config-modal-field">
              <span>目标空间</span>
              <select v-model="dbConnectionForm.workspaceCode" data-testid="db-connection-workspace">
                <option value="">请选择目标空间</option>
                <option v-for="item in writableWorkspaceOptions" :key="item.code" :value="item.code">
                  {{ item.name }}
                </option>
              </select>
            </label>

            <label class="config-modal-field">
              <span>连接名称</span>
              <input v-model="dbConnectionForm.connectionName" placeholder="例如：主数据库（测试）" data-testid="db-connection-name">
            </label>

            <div class="config-modal-field">
              <span>数据库类型</span>
              <div class="config-modal-segment is-four">
                <button
                  v-for="item in configDbTypeOptions"
                  :key="item.value"
                  type="button"
                  :class="{ 'is-active': dbConnectionForm.dbType === item.value }"
                  @click="applyDbTypeDefaults(item.value)"
                >
                  {{ item.label }}
                </button>
              </div>
            </div>

            <div class="config-modal-grid is-two">
              <label class="config-modal-field">
                <span>主机地址</span>
                <input v-model="dbHost" class="is-mono" placeholder="localhost 或 IP">
              </label>
              <label class="config-modal-field">
                <span>端口</span>
                <input v-model="dbPort" class="is-mono" placeholder="3306">
              </label>
            </div>

            <label class="config-modal-field">
              <span>数据库名</span>
              <input v-model="dbName" placeholder="数据库名称">
            </label>

            <div class="config-modal-grid is-two">
              <label class="config-modal-field">
                <span>用户名</span>
                <input v-model="dbConnectionForm.username" class="is-soft" autocomplete="username" placeholder="用户名">
              </label>
              <label class="config-modal-field">
                <span>密码</span>
                <div class="config-password-input">
                  <input
                    v-model="dbConnectionForm.password"
                    :type="dbPasswordVisible ? 'text' : 'password'"
                    autocomplete="current-password"
                    :placeholder="dbConnectionDialogMode === 'edit' ? '留空沿用旧密码' : '密码'"
                  >
                  <button type="button" aria-label="显示密码" @click="dbPasswordVisible = !dbPasswordVisible">
                    <span>{{ dbPasswordVisible ? '○' : '◉' }}</span>
                  </button>
                </div>
              </label>
            </div>
          </div>

          <footer class="config-modal-footer">
            <button type="button" class="config-modal-cancel" @click="dbConnectionDialogVisible = false">取消</button>
            <button type="button" class="config-modal-submit" :disabled="savingDbConnection" @click="submitDbConnection">保存</button>
          </footer>
        </div>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.settings-page-shell {
  height: 100%;
  min-height: 0;
}

.config-center-page-shell {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.settings-figma-shell {
  display: flex;
  min-height: 100%;
  height: 100%;
  overflow: hidden;
  background: var(--ath-bg-page);
}

.settings-category-sidebar {
  width: 224px;
  flex: 0 0 224px;
  padding: 16px 12px 0;
  overflow-y: auto;
  border-right: 1px solid var(--ath-border);
  background: var(--ath-bg-panel);
  scrollbar-width: none;
}

.settings-category-sidebar::-webkit-scrollbar {
  display: none;
}

.settings-category-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 12px;
  min-height: 56px;
  margin-bottom: 2px;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: var(--ath-text-main);
  text-align: left;
  cursor: pointer;
  transition: background-color 0.18s ease, border-color 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
}

.settings-category-item:hover {
  background: var(--ath-bg-page);
  color: var(--ath-text-strong);
}

.settings-category-item.is-active {
  border-color: transparent;
  background: var(--ath-blue-soft);
  color: var(--ath-primary);
  box-shadow: none;
}

.settings-category-item svg {
  flex: 0 0 auto;
  color: var(--ath-text-subtle);
}

.settings-category-item.is-active svg {
  color: var(--ath-primary);
}

.settings-category-item span {
  min-width: 0;
}

.settings-category-item strong,
.settings-category-item small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.settings-category-item strong {
  color: inherit;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.35;
}

.settings-category-item small {
  margin-top: 2px;
  color: var(--ath-text-subtle);
  font-size: 12px;
  line-height: 1.3;
}

.settings-figma-content {
  min-width: 0;
  flex: 1;
  overflow: hidden;
}

.settings-placeholder {
  display: flex;
  min-height: 100%;
  align-items: center;
  justify-content: center;
  color: var(--ath-text-subtle);
  font-size: var(--ath-font-sm);
  text-align: center;
}

.settings-placeholder div {
  display: grid;
  justify-items: center;
  gap: 12px;
  min-width: min(520px, calc(100vw - 64px));
  border: 1px solid var(--ath-border);
  border-radius: var(--ath-radius-lg);
  background: linear-gradient(180deg, #ffffff 0%, var(--ath-bg-subtle) 100%);
  padding: var(--ath-space-8);
  box-shadow: var(--ath-shadow-xs);
}

.settings-placeholder svg {
  width: 42px;
  height: 42px;
  border-radius: var(--ath-radius-lg);
  background: var(--ath-blue-soft);
  padding: 10px;
  color: var(--ath-primary);
}

.settings-placeholder p {
  margin: 0;
}

.workspace-config-page {
  height: 100%;
  overflow-y: auto;
  padding: 28px 32px;
  background: var(--ath-bg-page);
  scrollbar-width: none;
}

.workspace-config-page.is-managing {
  padding: 0;
}

.workspace-config-page::-webkit-scrollbar {
  display: none;
}

.workspace-config-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 28px;
}

.workspace-config-header h2 {
  margin: 0;
  color: var(--ath-text-strong);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
  letter-spacing: 0;
}

.workspace-config-header p {
  margin: 2px 0 0;
  color: var(--ath-text-muted);
  font-size: 14px;
  line-height: 1.45;
}

.workspace-create-button {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 42px;
  padding: 0 16px;
  border: 0;
  border-radius: 12px;
  background: var(--ath-primary);
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.18s ease;
}

.workspace-create-button:hover {
  background: var(--ath-primary-hover);
}

.workspace-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 28px;
}

.workspace-stat-card {
  min-width: 0;
  padding: 16px 20px;
  border: 1px solid var(--ath-border);
  border-radius: 16px;
  background: var(--ath-bg-panel);
}

.workspace-stat-card div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.workspace-stat-card span {
  color: var(--ath-text-muted);
  font-size: 12px;
  line-height: 1.35;
}

.workspace-stat-card strong {
  display: block;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}

.workspace-stat-card.is-blue strong,
.workspace-stat-card.is-blue svg {
  color: var(--ath-primary);
}

.workspace-stat-card.is-green strong,
.workspace-stat-card.is-green svg {
  color: var(--ath-green);
}

.workspace-stat-card.is-purple strong,
.workspace-stat-card.is-purple svg {
  color: var(--ath-purple);
}

.workspace-stat-card.is-orange strong,
.workspace-stat-card.is-orange svg {
  color: var(--ath-orange);
}

.workspace-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.team-management-page {
  height: 100%;
  overflow-y: auto;
  padding: 28px 32px;
  background: var(--ath-bg-page);
  scrollbar-width: none;
}

.team-management-page::-webkit-scrollbar {
  display: none;
}

.team-management-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 28px;
}

.team-management-header h2 {
  margin: 0;
  color: var(--ath-text-strong);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
}

.team-management-header p {
  max-width: 640px;
  margin: 2px 0 0;
  color: var(--ath-text-muted);
  font-size: 14px;
  line-height: 1.45;
}

.team-header-actions {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 10px;
}

.team-primary-button {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 42px;
  padding: 0 16px;
  border: 0;
  border-radius: 12px;
  background: var(--ath-primary);
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.18s ease;
}

.team-secondary-button {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  height: 42px;
  padding: 0 16px;
  border: 1px solid var(--ath-border);
  border-radius: 12px;
  background: var(--ath-bg-panel);
  color: var(--ath-text-main);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.18s ease, border-color 0.18s ease;
}

.team-primary-button:hover {
  background: var(--ath-primary-hover);
}

.team-secondary-button:hover {
  border-color: var(--ath-border-strong);
  background: var(--ath-bg-page);
}

.batch-user-dialog {
  display: grid;
  gap: 16px;
}

.batch-user-results {
  display: grid;
  max-height: 220px;
  overflow-y: auto;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--ath-border);
  border-radius: 12px;
  background: var(--ath-bg-subtle);
}

.batch-user-result-row {
  display: grid;
  grid-template-columns: 72px minmax(120px, 1fr) minmax(160px, 2fr);
  align-items: center;
  gap: 10px;
  min-height: 34px;
  border-radius: 8px;
  background: var(--ath-red-soft);
  padding: 8px 10px;
  color: var(--ath-red);
  font-size: 12px;
}

.batch-user-result-row.is-success {
  background: var(--ath-green-soft);
  color: var(--ath-green);
}

.batch-user-result-row span,
.batch-user-result-row em {
  font-style: normal;
}

.batch-user-result-row strong {
  overflow: hidden;
  color: var(--ath-text-strong);
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.team-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}

.team-stat-card {
  min-width: 0;
  padding: 16px 20px;
  border: 1px solid var(--ath-border);
  border-radius: 16px;
  background: var(--ath-bg-panel);
}

.team-stat-card div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.team-stat-card span {
  color: var(--ath-text-muted);
  font-size: 12px;
  line-height: 1.35;
}

.team-stat-card strong {
  display: block;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}

.team-stat-card.is-blue strong,
.team-stat-card.is-blue svg {
  color: var(--ath-primary);
}

.team-stat-card.is-green strong,
.team-stat-card.is-green svg {
  color: var(--ath-green);
}

.team-stat-card.is-purple strong,
.team-stat-card.is-purple svg {
  color: var(--ath-purple);
}

.team-stat-card.is-orange strong,
.team-stat-card.is-orange svg {
  color: var(--ath-orange);
}

.team-filter-card {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 180px 160px auto;
  align-items: end;
  gap: 12px;
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid var(--ath-border);
  border-radius: 16px;
  background: var(--ath-bg-panel);
}

.team-filter-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.team-filter-field span {
  color: var(--ath-text-muted);
  font-size: 12px;
  line-height: 1.35;
}

.team-filter-field input,
.team-filter-field select {
  width: 100%;
  height: 38px;
  border: 1px solid var(--ath-input-border);
  border-radius: 10px;
  background: #ffffff;
  padding: 0 12px;
  color: var(--ath-text-strong);
  font-size: 14px;
  outline: none;
}

.team-filter-field input:focus,
.team-filter-field select:focus {
  border-color: var(--ath-primary);
  box-shadow: var(--ath-focus-ring);
}

.team-reset-button {
  height: 38px;
  border: 1px solid var(--ath-border);
  border-radius: 10px;
  background: var(--ath-bg-panel);
  color: var(--ath-text-main);
  padding: 0 14px;
  font-size: 14px;
  cursor: pointer;
}

.team-reset-button:hover {
  border-color: var(--ath-border-strong);
  background: var(--ath-bg-page);
}

.team-table-card {
  overflow: hidden;
  border: 1px solid var(--ath-border);
  border-radius: 16px;
  background: var(--ath-bg-panel);
}

.team-table-card table {
  width: 100%;
  border-collapse: collapse;
}

.team-table-card th {
  height: 44px;
  border-bottom: 1px solid var(--ath-border-soft);
  background: var(--ath-bg-subtle);
  color: var(--ath-text-muted);
  padding: 0 18px;
  font-size: 12px;
  font-weight: 600;
  text-align: left;
  white-space: nowrap;
}

.team-table-card td {
  min-height: 54px;
  border-bottom: 1px solid var(--ath-border-soft);
  padding: 14px 18px;
  color: var(--ath-text-main);
  font-size: 14px;
  vertical-align: middle;
}

.team-table-card tbody tr:last-child td {
  border-bottom: 0;
}

.team-table-card tbody tr:hover {
  background: var(--ath-bg-subtle);
}

.team-member-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.team-member-cell strong {
  display: block;
  color: var(--ath-text-strong);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.35;
}

.team-member-cell p {
  margin: 3px 0 0;
  color: var(--ath-text-muted);
  font-size: 12px;
  line-height: 1.35;
}

.team-avatar {
  display: inline-flex;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--ath-blue-soft);
  color: var(--ath-primary);
  font-size: 14px;
  font-weight: 700;
}

.team-role-badge,
.team-status-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 24px;
  border-radius: 999px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.team-role-badge.is-super,
.team-role-badge.is-admin {
  background: var(--ath-purple-soft);
  color: var(--ath-purple);
}

.team-role-badge.is-member {
  background: var(--ath-blue-soft);
  color: var(--ath-primary);
}

.team-status-badge {
  background: var(--ath-green-soft);
  color: var(--ath-green);
}

.team-status-badge.is-disabled {
  background: var(--ath-bg-muted);
  color: var(--ath-text-muted);
}

.team-workspace-text {
  display: inline-block;
  max-width: 260px;
  overflow: hidden;
  color: var(--ath-text-main);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.team-row-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
}

.team-row-actions button {
  border: 0;
  background: transparent;
  color: var(--ath-primary);
  padding: 0;
  font-size: 13px;
  cursor: pointer;
}

.team-row-actions button:hover:not(:disabled) {
  color: var(--ath-primary-hover);
}

.team-row-actions button:disabled {
  color: var(--ath-text-disabled);
  cursor: not-allowed;
}

.team-empty-state {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 48px 20px;
  color: var(--ath-text-muted);
  text-align: center;
}

.team-empty-state svg {
  color: var(--ath-primary);
}

.team-empty-state strong {
  color: var(--ath-text-strong);
  font-size: 14px;
}

.team-empty-state p {
  margin: 0;
  font-size: 13px;
}

.workspace-config-card {
  min-width: 0;
  padding: 20px;
  border: 1px solid var(--ath-border);
  border-radius: 16px;
  background: var(--ath-bg-panel);
  transition: box-shadow 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.workspace-config-card:hover {
  border-color: var(--ath-border);
  box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1);
}

.workspace-config-card.is-disabled {
  opacity: 0.64;
}

.workspace-card-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.workspace-card-head {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: flex-start;
  gap: 12px;
}

.workspace-card-icon {
  display: inline-flex;
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--ath-blue), var(--ath-primary));
  color: #ffffff;
}

.workspace-card-icon.is-team {
  background: linear-gradient(135deg, #22c55e, var(--ath-green));
}

.workspace-card-icon.is-product {
  background: linear-gradient(135deg, #a855f7, #7c3aed);
}

.workspace-card-title {
  min-width: 0;
  flex: 1;
}

.workspace-card-name-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.workspace-card-name-row h3 {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--ath-text-strong);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-card-title p {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--ath-text-muted);
  font-size: 12px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.workspace-status-badge {
  flex: 0 0 auto;
  padding: 2px 8px;
  border-radius: 999px;
  background: #dcfce7;
  color: #15803d;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.35;
}

.workspace-status-badge.is-disabled {
  background: var(--ath-border-soft);
  color: var(--ath-text-main);
}

.workspace-card-actions {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.18s ease;
}

.workspace-config-card:hover .workspace-card-actions {
  opacity: 1;
}

.workspace-card-actions button {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ath-text-subtle);
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.workspace-card-actions button:hover {
  background: var(--ath-border-soft);
  color: var(--ath-text-main);
}

.workspace-card-actions button:first-child:hover {
  background: var(--ath-blue-soft);
  color: var(--ath-blue);
}

.workspace-card-actions button.is-danger:hover {
  background: var(--ath-red-soft);
  color: var(--ath-red);
}

.workspace-card-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px 16px;
}

.workspace-card-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--ath-text-muted);
  font-size: 12px;
  line-height: 1.35;
}

.workspace-type-badge {
  padding: 4px 8px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: var(--ath-blue-soft);
  color: var(--ath-primary-hover) !important;
}

.workspace-type-badge.is-team {
  border-color: #bbf7d0;
  background: var(--ath-green-soft);
  color: #15803d !important;
}

.workspace-type-badge.is-product {
  border-color: #e9d5ff;
  background: var(--ath-purple-soft);
  color: #7e22ce !important;
}

.workspace-empty-state {
  display: flex;
  min-height: 320px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--ath-text-subtle);
  text-align: center;
}

.workspace-empty-state > div {
  display: inline-flex;
  width: 64px;
  height: 64px;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  border-radius: 16px;
  background: var(--ath-border-soft);
  color: var(--ath-text-subtle);
}

.workspace-empty-state strong {
  margin-bottom: 4px;
  color: var(--ath-text-main);
  font-size: 14px;
  font-weight: 500;
}

.workspace-empty-state p {
  margin: 0 0 16px;
  color: var(--ath-text-subtle);
  font-size: 12px;
}

.workspace-manage-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 16px 32px;
  border-bottom: 1px solid var(--ath-border);
  background: var(--ath-bg-panel);
}

.workspace-manage-top {
  min-width: 0;
}

.workspace-back-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 16px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ath-text-main);
  font-size: 14px;
  cursor: pointer;
  transition: color 0.18s ease;
}

.workspace-back-button:hover {
  color: var(--ath-text-strong);
}

.workspace-manage-summary {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 16px;
}

.workspace-manage-icon {
  display: inline-flex;
  width: 56px;
  height: 56px;
  flex: 0 0 56px;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--ath-blue), var(--ath-primary));
  color: #ffffff;
}

.workspace-manage-icon.is-team {
  background: linear-gradient(135deg, #22c55e, var(--ath-green));
}

.workspace-manage-icon.is-product {
  background: linear-gradient(135deg, #a855f7, #7c3aed);
}

.workspace-manage-summary h1 {
  margin: 0 0 4px;
  color: var(--ath-text-strong);
  font-size: 20px;
  font-weight: 600;
  line-height: 1.35;
}

.workspace-manage-summary p {
  margin: 0;
  color: var(--ath-text-muted);
  font-size: 14px;
  line-height: 1.45;
}

.workspace-manage-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}

.workspace-manage-meta > span:not(.workspace-type-badge) {
  color: var(--ath-text-subtle);
  font-size: 12px;
}

.workspace-save-button {
  flex: 0 0 auto;
  margin-top: 32px;
  padding: 8px 16px;
  border: 0;
  border-radius: 8px;
  background: var(--ath-primary);
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.18s ease;
}

.workspace-save-button:hover {
  background: var(--ath-primary-hover);
}

.workspace-manage-tabs {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 32px;
  border-bottom: 1px solid var(--ath-border);
  background: var(--ath-bg-panel);
}

.workspace-manage-tabs button {
  height: 44px;
  padding: 0 4px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--ath-text-muted);
  font-size: 14px;
  cursor: pointer;
}

.workspace-manage-tabs button.is-active {
  border-bottom-color: var(--ath-primary);
  color: var(--ath-primary);
  font-weight: 500;
}

.workspace-member-page {
  max-width: 896px;
  padding: 28px 32px;
}

.workspace-member-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.workspace-member-title h2 {
  margin: 0;
  color: var(--ath-text-strong);
  font-size: 16px;
  font-weight: 600;
}

.workspace-member-title span {
  color: var(--ath-text-muted);
  font-size: 14px;
}

.workspace-member-section {
  margin-bottom: 16px;
  padding: 24px;
  border: 1px solid var(--ath-border);
  border-radius: 12px;
  background: var(--ath-bg-panel);
}

.workspace-member-section:last-child {
  margin-bottom: 0;
}

.workspace-member-section-head,
.workspace-member-section-title {
  display: flex;
  align-items: center;
}

.workspace-member-section-head {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.workspace-member-section-title {
  gap: 8px;
  margin-bottom: 16px;
}

.workspace-member-section-head .workspace-member-section-title {
  margin-bottom: 0;
}

.workspace-member-section-title svg {
  color: #f59e0b;
}

.workspace-member-section:not(.is-owner) .workspace-member-section-title svg {
  color: var(--ath-blue);
}

.workspace-member-section:last-child .workspace-member-section-title svg {
  color: var(--ath-text-muted);
}

.workspace-member-section-title h3 {
  margin: 0;
  color: var(--ath-text-strong);
  font-size: 14px;
  font-weight: 500;
}

.workspace-member-add {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 34px;
  padding: 6px 12px;
  border: 1px solid var(--ath-border-strong);
  border-radius: 8px;
  background: var(--ath-bg-panel);
  color: var(--ath-text-main);
  font-size: 14px;
  cursor: pointer;
  transition: border-color 0.18s ease, color 0.18s ease, background-color 0.18s ease;
}

.workspace-member-add:hover {
  border-color: var(--ath-text-subtle);
  color: var(--ath-text-main);
}

.workspace-member-add.is-admin {
  border-color: #bfdbfe;
  color: var(--ath-primary);
}

.workspace-member-add.is-admin:hover {
  border-color: #93c5fd;
  color: var(--ath-primary-hover);
}

.workspace-member-add:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.workspace-member-list {
  display: grid;
  gap: 8px;
}

.workspace-member-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border-radius: 8px;
  background: var(--ath-bg-page);
  transition: background-color 0.18s ease;
}

.workspace-member-row:hover {
  background: var(--ath-border-soft);
}

.workspace-member-row.is-owner {
  border: 1px solid #fde68a;
  background: linear-gradient(90deg, #fffbeb, #fff7ed);
}

.workspace-member-profile {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.workspace-avatar {
  display: inline-flex;
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--ath-text-subtle), var(--ath-text-muted));
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
}

.workspace-avatar.is-owner {
  background: linear-gradient(135deg, #f59e0b, #f97316);
}

.workspace-avatar.is-admin {
  background: linear-gradient(135deg, var(--ath-blue), var(--ath-primary));
}

.workspace-member-profile strong {
  display: block;
  color: var(--ath-text-strong);
  font-size: 14px;
  font-weight: 500;
  line-height: 1.35;
}

.workspace-member-profile p {
  margin: 2px 0 0;
  color: var(--ath-text-muted);
  font-size: 12px;
  line-height: 1.35;
}

.workspace-member-actions {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.workspace-member-actions button {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ath-red);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.18s ease, background-color 0.18s ease;
}

.workspace-member-actions .workspace-member-text-action {
  width: auto;
  min-width: 0;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--ath-blue-soft);
  color: var(--ath-primary);
  font-size: 12px;
  font-weight: 500;
}

.workspace-member-row:hover .workspace-member-actions button {
  opacity: 1;
}

.workspace-member-actions button:hover {
  background: var(--ath-red-soft);
}

.workspace-member-actions .workspace-member-text-action:hover {
  background: #dbeafe;
}

.workspace-role-badge {
  padding: 4px 8px;
  border-radius: 999px;
  background: var(--ath-border);
  color: var(--ath-text-main);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.2;
}

.workspace-role-badge.is-owner {
  padding: 4px 12px;
  background: #fef3c7;
  color: #b45309;
}

.workspace-role-badge.is-admin {
  background: #dbeafe;
  color: var(--ath-primary-hover);
}

.workspace-member-empty {
  padding: 32px;
  color: var(--ath-text-subtle);
  font-size: 14px;
  text-align: center;
}

.workspace-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 4000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgb(0 0 0 / 0.3);
  backdrop-filter: blur(8px);
}

.workspace-modal-panel {
  display: flex;
  width: 100%;
  max-width: 512px;
  max-height: 90vh;
  flex-direction: column;
  overflow: hidden;
  border-radius: 16px;
  background: var(--ath-bg-panel);
  box-shadow: 0 25px 50px -12px rgb(0 0 0 / 0.25);
}

.workspace-modal-header {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 16px;
  border-bottom: 1px solid var(--ath-border-soft);
}

.workspace-modal-header h2 {
  margin: 0;
  color: var(--ath-text-strong);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.45;
}

.workspace-modal-header button {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ath-text-muted);
  cursor: pointer;
  transition: background-color 0.18s ease;
}

.workspace-modal-header button:hover {
  background: var(--ath-border-soft);
}

.workspace-modal-form {
  display: grid;
  gap: 20px;
  max-height: calc(90vh - 132px);
  overflow-y: auto;
  padding: 24px;
  scrollbar-width: none;
}

.workspace-modal-form::-webkit-scrollbar {
  display: none;
}

.workspace-modal-field {
  display: grid;
  gap: 6px;
}

.workspace-modal-field > span {
  color: var(--ath-text-main);
  font-size: 14px;
  font-weight: 500;
}

.workspace-modal-field > small {
  color: var(--ath-text-subtle);
  font-size: 12px;
  line-height: 1.4;
}

.workspace-modal-field input,
.workspace-modal-field textarea,
.workspace-modal-field select {
  width: 100%;
  border: 1px solid var(--ath-border);
  border-radius: 12px;
  background: var(--ath-bg-panel);
  color: var(--ath-text-strong);
  font-size: 14px;
  outline: none;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.workspace-modal-field input,
.workspace-modal-field select {
  height: 42px;
  padding: 0 12px;
}

.workspace-modal-field textarea {
  min-height: 82px;
  padding: 10px 12px;
  resize: none;
}

.workspace-modal-field input::placeholder,
.workspace-modal-field textarea::placeholder {
  color: var(--ath-text-subtle);
}

.workspace-modal-field input:focus,
.workspace-modal-field textarea:focus,
.workspace-modal-field select:focus {
  border-color: var(--ath-blue);
  box-shadow: 0 0 0 2px rgb(59 130 246 / 0.16);
}

.workspace-modal-type-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.workspace-modal-type-grid button {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 4px;
  min-height: 94px;
  padding: 12px;
  border: 2px solid var(--ath-border);
  border-radius: 12px;
  background: var(--ath-bg-panel);
  color: var(--ath-text-muted);
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease;
}

.workspace-modal-type-grid button:hover {
  border-color: var(--ath-border-strong);
}

.workspace-modal-type-grid button.is-active {
  border-color: var(--ath-blue);
  background: var(--ath-blue-soft);
  color: var(--ath-primary);
}

.workspace-modal-type-grid strong {
  color: var(--ath-text-strong);
  font-size: 12px;
  font-weight: 500;
}

.workspace-modal-type-grid small {
  color: var(--ath-text-subtle);
  font-size: 12px;
}

.workspace-modal-type-icon {
  margin-bottom: 4px;
  font-size: 24px;
  line-height: 1;
}

.workspace-modal-status {
  display: flex;
  gap: 12px;
}

.workspace-modal-status button {
  flex: 1;
  height: 42px;
  border: 2px solid var(--ath-border);
  border-radius: 12px;
  background: var(--ath-bg-panel);
  color: var(--ath-text-muted);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.workspace-modal-status button:hover {
  border-color: var(--ath-border-strong);
}

.workspace-modal-status button.is-active {
  border-color: #bbf7d0;
  background: var(--ath-green-soft);
  color: #15803d;
}

.workspace-modal-status button:nth-child(2).is-active {
  border-color: var(--ath-border);
  background: var(--ath-border-soft);
  color: var(--ath-text-main);
}

.workspace-modal-footer {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 24px;
  border-top: 1px solid var(--ath-border-soft);
}

.workspace-modal-cancel,
.workspace-modal-submit {
  height: 38px;
  padding: 0 18px;
  border-radius: 12px;
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.18s ease, border-color 0.18s ease;
}

.workspace-modal-cancel {
  border: 1px solid var(--ath-border);
  background: var(--ath-bg-panel);
  color: var(--ath-text-main);
}

.workspace-modal-cancel:hover {
  background: var(--ath-bg-page);
}

.workspace-modal-submit {
  border: 0;
  background: var(--ath-primary);
  color: #ffffff;
  font-weight: 500;
}

.workspace-modal-submit:hover {
  background: var(--ath-primary-hover);
}

.workspace-modal-submit:disabled {
  cursor: wait;
  opacity: 0.65;
}

.settings-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.settings-single-panel {
  padding-top: 8px;
}

.page-header {
  margin-bottom: 0;
}

.page-subtitle {
  display: none;
}

.mode-toolbar {
  margin-bottom: 16px;
}

.table-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: start;
  margin-bottom: 16px;
}

.toolbar-filter-input {
  width: 320px;
}

.toolbar-filter-select {
  width: 220px;
}

.toolbar-filters {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  min-height: 40px;
}

.toolbar-filters :deep(.el-input),
.toolbar-filters :deep(.el-select) {
  width: 220px;
}

.toolbar-filters :deep(.el-input:first-child) {
  width: 320px;
}

.toolbar-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  min-height: 40px;
}

.workspace-select {
  width: 240px !important;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 64px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1.2;
}

.status-success {
  background: #ecfdf3;
  color: #047857;
}

.status-neutral {
  background: var(--ath-border-soft);
  color: var(--ath-text-main);
}

.list-meta {
  color: var(--ath-text-main);
}

.cell-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.block-note {
  color: var(--ath-text-muted);
  line-height: 1.6;
}

.mono {
  font-family: Consolas, Monaco, monospace;
}

.config-center-shell {
  display: flex;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: var(--ath-bg-page);
}

.config-center-sidebar {
  width: 224px;
  flex: 0 0 224px;
  padding: 16px 12px;
  overflow-y: auto;
  border-right: 1px solid var(--ath-border);
  background: var(--ath-bg-panel);
  scrollbar-width: none;
}

.config-center-sidebar::-webkit-scrollbar {
  display: none;
}

.config-nav-item {
  display: flex;
  width: 100%;
  min-height: 56px;
  align-items: center;
  gap: 12px;
  margin-bottom: 2px;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  color: var(--ath-text-main);
  text-align: left;
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.config-nav-item:hover {
  background: var(--ath-bg-page);
  color: var(--ath-text-strong);
}

.config-nav-item.is-active {
  background: var(--ath-blue-soft);
  color: var(--ath-primary);
}

.config-nav-item svg {
  flex: 0 0 auto;
  color: var(--ath-text-subtle);
}

.config-nav-item.is-active svg {
  color: var(--ath-primary);
}

.config-nav-item span {
  min-width: 0;
}

.config-nav-item strong,
.config-nav-item small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.config-nav-item strong {
  color: inherit;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.35;
}

.config-nav-item small {
  margin-top: 2px;
  color: var(--ath-text-subtle);
  font-size: 12px;
  line-height: 1.3;
}

.config-center-content {
  min-width: 0;
  flex: 1;
  overflow: hidden;
}

.config-content-page {
  height: 100%;
  overflow-y: auto;
  padding: 28px 32px;
  scrollbar-width: none;
}

.config-content-page::-webkit-scrollbar {
  display: none;
}

.config-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 28px;
}

.config-page-header h2 {
  margin: 0;
  color: var(--ath-text-strong);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
  letter-spacing: 0;
}

.config-page-header p {
  margin: 2px 0 0;
  color: var(--ath-text-muted);
  font-size: 14px;
  line-height: 1.5;
}

.config-primary-button {
  display: inline-flex;
  height: 40px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 18px;
  border: 0;
  border-radius: 12px;
  background: var(--ath-primary);
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.18s ease;
}

.config-primary-button:hover {
  background: var(--ath-primary-hover);
}

.config-stat-grid {
  display: grid;
  gap: 16px;
  margin-bottom: 28px;
}

.config-stat-grid.is-three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.config-stat-grid.is-four {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.config-stat-card {
  min-width: 0;
  min-height: 86px;
  padding: 16px 20px;
  border: 1px solid var(--ath-border);
  border-radius: 16px;
  background: var(--ath-bg-panel);
}

.config-stat-card span {
  display: block;
  margin-bottom: 4px;
  color: var(--ath-text-muted);
  font-size: 12px;
  line-height: 1.35;
}

.config-stat-card strong {
  display: block;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.1;
}

.config-stat-card.is-blue strong {
  color: var(--ath-primary);
}

.config-stat-card.is-green strong {
  color: var(--ath-green);
}

.config-stat-card.is-red strong {
  color: #e60012;
}

.config-stat-card.is-orange strong {
  color: var(--ath-orange);
}

.config-stat-card.is-purple strong {
  color: var(--ath-purple);
}

.config-stat-card.is-gray strong {
  color: var(--ath-text-main);
}

.config-env-grid {
  display: grid;
  min-height: 120px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.config-env-card,
.config-db-card {
  min-width: 0;
  border: 1px solid var(--ath-border);
  border-radius: 16px;
  background: var(--ath-bg-panel);
  transition: box-shadow 0.2s ease;
}

.config-env-card {
  min-height: 188px;
  padding: 20px;
}

.config-env-card:hover,
.config-db-card:hover {
  box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1), 0 2px 4px -2px rgb(0 0 0 / 0.1);
}

.config-card-main,
.config-db-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.config-card-main {
  margin-bottom: 18px;
}

.config-card-title,
.config-db-title {
  min-width: 0;
  flex: 1;
}

.config-card-name-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  margin-bottom: 3px;
}

.config-card-name-row h3 {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--ath-text-strong);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.config-card-title p,
.config-db-title p {
  margin: 0;
  color: var(--ath-text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.config-card-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.18s ease;
}

.config-env-card:hover .config-card-actions,
.config-db-card:hover .config-card-actions,
.config-card-actions.is-visible {
  opacity: 1;
}

.config-card-actions button {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ath-text-subtle);
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.config-card-actions button:hover {
  background: var(--ath-border-soft);
  color: var(--ath-text-main);
}

.config-card-actions button.is-danger:hover {
  background: var(--ath-red-soft);
  color: var(--ath-red);
}

.config-card-body {
  display: grid;
  gap: 10px;
}

.config-status-pill {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1.25;
}

.config-status-pill.is-success {
  background: #dcfce7;
  color: var(--ath-green);
}

.config-status-pill.is-neutral {
  background: var(--ath-border-soft);
  color: var(--ath-text-main);
}

.config-status-pill.is-danger {
  background: #fee2e2;
  color: #b91c1c;
}

.config-type-badge {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  justify-content: center;
  padding: 4px 8px;
  border: 1px solid;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.25;
}

.config-type-badge.is-blue {
  border-color: #bfdbfe;
  background: var(--ath-blue-soft);
  color: var(--ath-primary);
}

.config-type-badge.is-orange {
  border-color: #fed7aa;
  background: #fff7ed;
  color: var(--ath-orange);
}

.config-type-badge.is-purple {
  border-color: #e9d5ff;
  background: var(--ath-purple-soft);
  color: #7e22ce;
}

.config-type-badge.is-green {
  border-color: #bbf7d0;
  background: var(--ath-green-soft);
  color: #15803d;
}

.config-type-badge.is-red {
  border-color: #fecaca;
  background: var(--ath-red-soft);
  color: var(--ath-red);
}

.config-code-box,
.config-value-chip {
  max-width: 100%;
  overflow: hidden;
  border-radius: 8px;
  background: var(--ath-bg-page);
  color: var(--ath-text-main);
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.config-code-box {
  padding: 8px 12px;
}

.config-value-chip {
  display: inline-block;
  padding: 4px 8px;
}

.config-card-meta {
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.35;
}

.config-segmented-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.config-segmented-tabs button {
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--ath-border);
  border-radius: 8px;
  background: var(--ath-bg-panel);
  color: var(--ath-text-main);
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.18s ease, border-color 0.18s ease, color 0.18s ease;
}

.config-segmented-tabs button:hover {
  background: var(--ath-bg-page);
}

.config-segmented-tabs button.is-active {
  border-color: var(--ath-primary);
  background: var(--ath-blue);
  color: #ffffff;
}

.config-param-table-card {
  min-height: 120px;
  overflow: hidden;
  border: 1px solid var(--ath-border);
  border-radius: 12px;
  background: var(--ath-bg-panel);
}

.config-param-table-card table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.config-param-table-card thead {
  border-bottom: 1px solid var(--ath-border);
  background: var(--ath-bg-page);
}

.config-param-table-card th {
  padding: 12px 24px;
  color: var(--ath-text-muted);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.4;
  text-align: left;
}

.config-param-table-card td {
  padding: 16px 24px;
  border-bottom: 1px solid var(--ath-border-soft);
  color: var(--ath-text-main);
  font-size: 14px;
  line-height: 1.45;
  vertical-align: middle;
}

.config-param-table-card tr:last-child td {
  border-bottom: 0;
}

.config-table-title {
  overflow: hidden;
  color: var(--ath-text-strong);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.config-table-subtitle,
.config-table-muted {
  margin-top: 2px;
  color: var(--ath-text-subtle);
  font-size: 12px;
}

.config-text-action {
  margin-right: 12px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ath-primary);
  font-size: 14px;
  cursor: pointer;
}

.config-text-action.is-danger {
  color: var(--ath-red);
}

.config-db-list {
  display: grid;
  min-height: 120px;
  gap: 16px;
}

.config-db-card {
  padding: 20px;
}

.config-db-meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  color: var(--ath-text-muted);
  font-size: 13px;
  line-height: 1.45;
}

.config-empty-state {
  display: flex;
  min-height: 120px;
  grid-column: 1 / -1;
  align-items: center;
  justify-content: center;
  color: var(--ath-text-subtle);
  font-size: 14px;
}

.config-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 4100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgb(0 0 0 / 0.32);
  backdrop-filter: blur(10px);
}

.config-env-modal {
  display: flex;
  width: min(512px, calc(100vw - 48px));
  max-height: calc(100vh - 72px);
  flex-direction: column;
  overflow: hidden;
  border-radius: 14px;
  background: var(--ath-bg-panel);
  box-shadow: 0 24px 60px rgb(15 23 42 / 0.22);
}

.config-db-modal {
  display: flex;
  width: min(672px, calc(100vw - 48px));
  max-height: calc(100vh - 72px);
  flex-direction: column;
  overflow: hidden;
  border-radius: 14px;
  background: var(--ath-bg-panel);
  box-shadow: 0 24px 60px rgb(15 23 42 / 0.22);
}

.config-modal-header {
  display: flex;
  min-height: 64px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  border-bottom: 1px solid #f1f5f9;
}

.config-modal-header h2 {
  margin: 0;
  color: var(--ath-text-strong);
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
}

.config-modal-header button {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ath-text-muted);
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.config-modal-header button:hover {
  background: var(--ath-border-soft);
  color: var(--ath-text-main);
}

.config-modal-body {
  display: grid;
  gap: 20px;
  overflow-y: auto;
  padding: 30px 24px 24px;
  scrollbar-width: none;
}

.config-modal-body::-webkit-scrollbar {
  display: none;
}

.config-modal-field {
  display: grid;
  gap: 8px;
}

.config-modal-field > span {
  color: var(--ath-text-main);
  font-size: 14px;
  font-weight: 400;
  line-height: 1.4;
}

.config-modal-field input,
.config-modal-field textarea,
.config-modal-field select {
  width: 100%;
  border: 1px solid #dfe3ea;
  border-radius: 12px;
  background: var(--ath-bg-panel);
  color: var(--ath-text-strong);
  font-size: 14px;
  outline: none;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.config-modal-field input,
.config-modal-field select {
  height: 42px;
  padding: 0 12px;
}

.config-modal-field textarea {
  min-height: 62px;
  padding: 10px 12px;
  resize: none;
}

.config-modal-field input::placeholder,
.config-modal-field textarea::placeholder {
  color: var(--ath-text-subtle);
}

.config-modal-field .is-mono {
  font-family: Consolas, Monaco, monospace;
}

.config-modal-field .is-soft {
  background: var(--ath-blue-soft);
}

.config-modal-field input:focus,
.config-modal-field textarea:focus,
.config-modal-field select:focus {
  border-color: var(--ath-blue);
  box-shadow: 0 0 0 2px rgb(59 130 246 / 0.12);
}

.config-modal-segment {
  display: grid;
  gap: 8px;
}

.config-modal-segment.is-three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.config-modal-segment.is-four {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.config-modal-segment.is-two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.config-modal-segment button {
  height: 40px;
  border: 2px solid var(--ath-border);
  border-radius: 10px;
  background: var(--ath-bg-panel);
  color: var(--ath-text-strong);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease, color 0.18s ease;
}

.config-modal-segment button:hover {
  border-color: #bfdbfe;
  color: var(--ath-primary);
}

.config-modal-segment button.is-active {
  border-color: #2f7cff;
  background: var(--ath-blue-soft);
  color: #1763ff;
}

.config-modal-checkbox {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 8px;
  color: var(--ath-text-main);
  font-size: 14px;
  line-height: 1.4;
  cursor: pointer;
}

.config-modal-checkbox input {
  width: 16px;
  height: 16px;
  margin: 0;
  border: 1px solid var(--ath-text-subtle);
  border-radius: 2px;
  accent-color: var(--ath-primary);
}

.config-modal-grid {
  display: grid;
  gap: 16px;
}

.config-modal-grid.is-two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.config-password-input {
  position: relative;
}

.config-password-input input {
  padding-right: 42px;
  background: var(--ath-blue-soft);
}

.config-password-input button {
  position: absolute;
  top: 50%;
  right: 10px;
  display: inline-flex;
  width: 26px;
  height: 26px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  transform: translateY(-50%);
}

.config-password-input button:hover {
  background: rgb(37 99 235 / 0.08);
  color: #64748b;
}

.config-modal-footer {
  display: flex;
  min-height: 68px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 24px;
  border-top: 1px solid #f1f5f9;
}

.config-modal-cancel,
.config-modal-submit {
  height: 38px;
  padding: 0 17px;
  border-radius: 12px;
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.18s ease, border-color 0.18s ease;
}

.config-modal-cancel {
  border: 1px solid var(--ath-border);
  background: var(--ath-bg-panel);
  color: var(--ath-text-strong);
}

.config-modal-cancel:hover {
  background: var(--ath-bg-page);
}

.config-modal-submit {
  min-width: 68px;
  border: 0;
  background: var(--ath-primary);
  color: #ffffff;
  font-weight: 500;
}

.config-modal-submit:hover {
  background: var(--ath-primary-hover);
}

.config-modal-submit:disabled {
  cursor: wait;
  opacity: 0.68;
}

@media (max-width: 900px) {
  .settings-figma-shell {
    min-height: auto;
    flex-direction: column;
  }

  .settings-category-sidebar {
    width: auto;
    flex-basis: auto;
    border-right: 0;
    border-bottom: 1px solid var(--ath-border);
  }

  .workspace-config-page {
    padding: 24px 20px;
  }

  .workspace-config-header {
    flex-direction: column;
  }

  .workspace-stat-grid,
  .workspace-card-grid {
    grid-template-columns: 1fr;
  }

  .config-center-shell {
    min-height: auto;
    flex-direction: column;
  }

  .config-center-sidebar {
    width: auto;
    flex-basis: auto;
    border-right: 0;
    border-bottom: 1px solid var(--ath-border);
  }

  .config-content-page {
    padding: 24px 20px;
  }

  .config-page-header {
    flex-direction: column;
  }

  .config-stat-grid.is-three,
  .config-stat-grid.is-four,
  .config-env-grid {
    grid-template-columns: 1fr;
  }

  .table-toolbar {
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    justify-content: flex-start;
  }
}
</style>
