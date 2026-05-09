<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Plus, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { platformApi } from '../api/platform'
import ListToolbar from '../components/ListToolbar.vue'
import { usePersistedFilters } from '../composables/usePersistedFilters'
import { useWorkspace } from '../composables/useWorkspace'
import { useWorkspaceAccess } from '../composables/useWorkspaceAccess'
import type {
  BatchWorkspaceMemberPayload,
  CreateEnvPayload,
  CreateParamPayload,
  CreateUserPayload,
  CreateWorkspacePayload,
  EnvConfigItem,
  ParamSetItem,
  UpdateUserPayload,
  UserItem,
  WorkspaceItem,
  WorkspaceMemberItem,
} from '../types/api'

const { workspaceCode, isAllScope } = useWorkspace()
const { currentUser, isPlatformAdmin, isSuperAdmin } = useWorkspaceAccess()

const activeTab = ref<'env' | 'param' | 'workspace' | 'member'>('env')
const memberViewMode = ref<'user' | 'workspace'>('user')
const memberWorkspaceCode = ref('')

const pageLoading = ref(false)
const workspaceLoading = ref(false)
const userLoading = ref(false)
const memberLoading = ref(false)
const envLoading = ref(false)
const paramLoading = ref(false)

const savingWorkspace = ref(false)
const savingUser = ref(false)
const savingMember = ref(false)
const savingEnv = ref(false)
const savingParam = ref(false)

const workspaces = ref<WorkspaceItem[]>([])
const users = ref<UserItem[]>([])
const members = ref<WorkspaceMemberItem[]>([])
const envs = ref<EnvConfigItem[]>([])
const params = ref<ParamSetItem[]>([])

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

const workspaceDialogVisible = ref(false)
const workspaceDialogMode = ref<'create' | 'edit'>('create')
const userDialogVisible = ref(false)
const userDialogMode = ref<'create' | 'edit'>('create')
const batchMemberDialogVisible = ref(false)
const envDialogVisible = ref(false)
const envDialogMode = ref<'create' | 'edit'>('create')
const paramDialogVisible = ref(false)
const paramDialogMode = ref<'create' | 'edit'>('create')

const workspaceForm = reactive<CreateWorkspacePayload>({
  workspaceCode: '',
  workspaceName: '',
  description: '',
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

const batchMemberForm = reactive<BatchWorkspaceMemberPayload>({
  userIds: [],
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

const businessWorkspaces = computed(() => workspaces.value.filter(item => !item.allScope))
const canManageSettings = computed(() => isPlatformAdmin.value)
const canManageAdminUsers = computed(() => isSuperAdmin.value)
const visibleWorkspaceCodes = computed(() => currentUser.value?.workspaceCodes ?? [])
const writableWorkspaceOptions = computed(() => {
  if (isPlatformAdmin.value) {
    return businessWorkspaces.value
  }
  return businessWorkspaces.value.filter(item => visibleWorkspaceCodes.value.includes(item.code))
})
const activeUsers = computed(() => users.value.filter(item => item.status === 1))
const selectableMembersForWorkspace = computed(() => {
  const existingUserIds = new Set(members.value.map(item => item.userId))
  return activeUsers.value.filter(item => item.roleCode !== 'ADMIN' && !existingUserIds.has(item.id))
})
const currentScopeText = computed(() => {
  if (isAllScope.value) {
    return '当前为全部空间视角。环境配置和参数配置会跨空间展示；新建或编辑时需要明确目标空间。'
  }
  return `当前为 ${resolveWorkspaceName(workspaceCode.value)} 视角。环境配置和参数配置只展示当前空间数据。`
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
    const matchRole = !userFilters.roleCode || item.roleCode === userFilters.roleCode
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

function resolveWorkspaceName(code: string) {
  if (code === 'ALL') {
    return '全部空间'
  }
  return businessWorkspaces.value.find(item => item.code === code)?.name ?? code
}

function roleLabel(roleCode: string) {
  if (roleCode === 'SUPER_ADMIN') {
    return '超级管理员'
  }
  return roleCode === 'ADMIN' ? '管理员' : '普通成员'
}

function workspaceSummary(user: UserItem) {
  if (user.roleCode === 'SUPER_ADMIN' || user.roleCode === 'ADMIN') {
    return '全部空间'
  }
  if (!user.workspaceNames.length) {
    return '未分配空间'
  }
  return user.workspaceNames.join('、')
}

function normalizeWorkspaceCodes(codes: string[]) {
  return [...new Set(codes.filter(Boolean))]
}

function resetWorkspaceForm() {
  workspaceForm.workspaceCode = ''
  workspaceForm.workspaceName = ''
  workspaceForm.description = ''
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

function resetBatchMemberForm() {
  batchMemberForm.userIds = []
}

function resetEnvForm() {
  envForm.id = null
  envForm.workspaceCode = isAllScope.value ? '' : workspaceCode.value
  envForm.envType = 'API'
  envForm.envName = ''
  envForm.baseUrl = ''
  envForm.configJson = ''
  envForm.status = 1
}

function resetParamForm() {
  paramForm.id = null
  paramForm.workspaceCode = isAllScope.value ? '' : workspaceCode.value
  paramForm.paramType = 'TOKEN'
  paramForm.paramName = ''
  paramForm.contentJson = ''
  paramForm.status = 1
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
  try {
    const [envPage, paramPage] = await Promise.all([
      platformApi.getSettingsEnvs(workspaceCode.value),
      platformApi.getSettingsParams(workspaceCode.value),
    ])
    envs.value = envPage.items
    params.value = paramPage.items
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    envLoading.value = false
    paramLoading.value = false
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
  workspaceDialogVisible.value = true
}

async function submitWorkspace() {
  if (!workspaceForm.workspaceCode.trim() || !workspaceForm.workspaceName.trim()) {
    ElMessage.error('请先填写工作空间编码和名称')
    return
  }
  savingWorkspace.value = true
  try {
    const payload = {
      workspaceCode: workspaceForm.workspaceCode.trim(),
      workspaceName: workspaceForm.workspaceName.trim(),
      description: workspaceForm.description.trim(),
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
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    savingWorkspace.value = false
  }
}

async function confirmDeleteWorkspace(row: WorkspaceItem) {
  try {
    await ElMessageBox.confirm(
      `删除后将无法恢复工作空间“${row.name}”。只有无依赖数据的空间允许删除，是否继续？`,
      '删除工作空间',
      { type: 'warning' },
    )
    await platformApi.deleteWorkspace(row.code)
    ElMessage.success('工作空间删除成功')
    await Promise.all([loadBaseData(), loadScopedSettings()])
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
  if (userForm.roleCode !== 'ADMIN' && workspaceCodes.length === 0) {
    ElMessage.error('普通成员至少需要选择一个所属空间')
    return
  }

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

function openBatchMemberCreate() {
  if (!memberWorkspaceCode.value) {
    ElMessage.error('请先选择工作空间')
    return
  }
  resetBatchMemberForm()
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
    })
    ElMessage.success('空间成员添加成功')
    batchMemberDialogVisible.value = false
    await Promise.all([loadBaseData(), loadMembers()])
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    savingMember.value = false
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
  envForm.envType = row.envType
  envForm.envName = row.envName
  envForm.baseUrl = row.baseUrl
  envForm.configJson = row.configJson
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
  paramForm.paramType = row.paramType
  paramForm.paramName = row.paramName
  paramForm.contentJson = row.contentJson
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
      contentJson: paramForm.contentJson.trim(),
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
  resetEnvForm()
  resetParamForm()
  void loadScopedSettings()
})

onMounted(async () => {
  workspaceFilterMemory.load()
  userFilterMemory.load()
  memberFilterMemory.load()
  envFilterMemory.load()
  paramFilterMemory.load()
  memberWorkspaceCode.value = memberFilters.workspaceCode
  await loadBaseData()
  await Promise.all([loadMembers(), loadScopedSettings()])
})
</script>

<template>
  <section class="page-shell">
    <article class="page-card" v-loading="pageLoading">
      <header class="page-header">
        <div>
          <h1 class="page-title">系统设置</h1>
          <p class="page-subtitle">{{ currentScopeText }}</p>
        </div>
      </header>

      <el-tabs v-model="activeTab" class="settings-tabs">
        <el-tab-pane label="环境配置" name="env">
          <ListToolbar title="环境配置">
            <template #filters>
              <el-input v-model="envFilters.keyword" placeholder="搜索环境名称 / 地址 / 所属空间" clearable class="toolbar-filter-input" />
              <el-select v-model="envFilters.envType" placeholder="环境类型" clearable class="toolbar-filter-select">
                <el-option label="API" value="API" />
                <el-option label="WEB" value="WEB" />
                <el-option label="APP" value="APP" />
              </el-select>
              <el-select v-model="envFilters.status" placeholder="状态" clearable class="toolbar-filter-select">
                <el-option label="启用" value="1" />
                <el-option label="停用" value="0" />
              </el-select>
              <el-button text @click="resetEnvFilters()">
                <el-icon><RefreshRight /></el-icon>
                重置
              </el-button>
            </template>
            <template #actions>
              <el-button v-if="canManageSettings" type="primary" @click="openEnvCreate">
                <el-icon><Plus /></el-icon>
                新增环境
              </el-button>
            </template>
          </ListToolbar>

          <el-table v-loading="envLoading" :data="filteredEnvs" size="large">
            <el-table-column v-if="isAllScope" prop="workspaceName" label="所属空间" min-width="160" />
            <el-table-column prop="envName" label="环境名称" min-width="180" />
            <el-table-column prop="envType" label="环境类型" width="120" />
            <el-table-column prop="baseUrl" label="基础地址" min-width="280" />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <span class="status-pill" :class="row.status === 1 ? 'status-success' : 'status-neutral'">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="configJson" label="扩展配置" min-width="240">
              <template #default="{ row }">
                <span class="cell-ellipsis">{{ row.configJson || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="canManageSettings" label="操作" width="240">
              <template #default="{ row }">
                <el-button text type="primary" @click="openEnvEdit(row)">编辑</el-button>
                <el-button text type="warning" @click="toggleEnvStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
                <el-button text type="danger" @click="confirmDeleteEnv(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="参数配置" name="param">
          <ListToolbar title="参数配置">
            <template #filters>
              <el-input v-model="paramFilters.keyword" placeholder="搜索参数集 / 内容 / 所属空间" clearable class="toolbar-filter-input" />
              <el-select v-model="paramFilters.paramType" placeholder="参数类型" clearable class="toolbar-filter-select">
                <el-option label="TOKEN" value="TOKEN" />
                <el-option label="HEADER" value="HEADER" />
                <el-option label="BODY" value="BODY" />
                <el-option label="QUERY" value="QUERY" />
              </el-select>
              <el-select v-model="paramFilters.status" placeholder="状态" clearable class="toolbar-filter-select">
                <el-option label="启用" value="1" />
                <el-option label="停用" value="0" />
              </el-select>
              <el-button text @click="resetParamFilters()">
                <el-icon><RefreshRight /></el-icon>
                重置
              </el-button>
            </template>
            <template #actions>
              <el-button v-if="canManageSettings" type="primary" @click="openParamCreate">
                <el-icon><Plus /></el-icon>
                新增参数集
              </el-button>
            </template>
          </ListToolbar>

          <el-table v-loading="paramLoading" :data="filteredParams" size="large">
            <el-table-column v-if="isAllScope" prop="workspaceName" label="所属空间" min-width="160" />
            <el-table-column prop="paramName" label="参数集名称" min-width="180" />
            <el-table-column prop="paramType" label="参数类型" width="140" />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <span class="status-pill" :class="row.status === 1 ? 'status-success' : 'status-neutral'">
                  {{ row.status === 1 ? '启用' : '停用' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="contentJson" label="内容" min-width="280">
              <template #default="{ row }">
                <span class="cell-ellipsis">{{ row.contentJson || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="canManageSettings" label="操作" width="240">
              <template #default="{ row }">
                <el-button text type="primary" @click="openParamEdit(row)">编辑</el-button>
                <el-button text type="warning" @click="toggleParamStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
                <el-button text type="danger" @click="confirmDeleteParam(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="工作空间" name="workspace">
          <ListToolbar title="工作空间">
            <template #filters>
              <el-input v-model="workspaceFilters.keyword" placeholder="搜索空间名称 / 编码 / 描述" clearable class="toolbar-filter-input" />
              <el-button text @click="resetWorkspaceFilters()">
                <el-icon><RefreshRight /></el-icon>
                重置
              </el-button>
            </template>
            <template #actions>
              <el-button v-if="canManageSettings" type="primary" @click="openWorkspaceCreate">
                <el-icon><Plus /></el-icon>
                新增工作空间
              </el-button>
            </template>
          </ListToolbar>

          <el-table v-loading="workspaceLoading" :data="filteredWorkspaces" size="large">
            <el-table-column prop="name" label="空间名称" min-width="180" />
            <el-table-column prop="code" label="空间编码" min-width="160" />
            <el-table-column prop="description" label="说明" min-width="300" />
            <el-table-column label="我的范围" width="160">
              <template #default="{ row }">
                <span class="list-meta">
                  {{ isPlatformAdmin ? '全部空间' : (visibleWorkspaceCodes.includes(row.code) ? '可访问' : '不可访问') }}
                </span>
              </template>
            </el-table-column>
            <el-table-column v-if="canManageSettings" label="操作" width="220">
              <template #default="{ row }">
                <el-button text type="primary" @click="openWorkspaceEdit(row)">编辑</el-button>
                <el-button text type="danger" @click="confirmDeleteWorkspace(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="成员权限" name="member">
          <div class="mode-toolbar">
            <el-radio-group v-model="memberViewMode">
              <el-radio-button value="user">按成员看</el-radio-button>
              <el-radio-button value="workspace">按空间看</el-radio-button>
            </el-radio-group>
          </div>

          <template v-if="memberViewMode === 'user'">
            <ListToolbar title="成员管理">
              <template #filters>
                <el-input v-model="userFilters.keyword" placeholder="搜索姓名 / 账号 / 邮箱 / 空间" clearable class="toolbar-filter-input" />
                <el-select v-model="userFilters.roleCode" placeholder="成员角色" clearable class="toolbar-filter-select">
                  <el-option label="管理员" value="ADMIN" />
                  <el-option label="普通成员" value="MEMBER" />
                </el-select>
                <el-select v-model="userFilters.status" placeholder="状态" clearable class="toolbar-filter-select">
                  <el-option label="启用" value="1" />
                  <el-option label="停用" value="0" />
                </el-select>
                <el-button text @click="resetUserFilters()">
                  <el-icon><RefreshRight /></el-icon>
                  重置
                </el-button>
              </template>
              <template #actions>
                <el-button v-if="canManageSettings" type="primary" @click="openUserCreate">
                  <el-icon><Plus /></el-icon>
                  新增成员
                </el-button>
              </template>
            </ListToolbar>

            <el-table v-loading="userLoading" :data="filteredUsers" size="large">
              <el-table-column prop="displayName" label="姓名" min-width="140" />
              <el-table-column prop="username" label="账号" min-width="160" />
              <el-table-column prop="email" label="邮箱" min-width="220" />
              <el-table-column label="成员角色" width="120">
                <template #default="{ row }">
                  {{ roleLabel(row.roleCode) }}
                </template>
              </el-table-column>
              <el-table-column label="所属空间" min-width="220">
                <template #default="{ row }">
                  <span class="list-meta">{{ workspaceSummary(row) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <span class="status-pill" :class="row.status === 1 ? 'status-success' : 'status-neutral'">
                    {{ row.status === 1 ? '启用' : '停用' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column v-if="canManageSettings" label="操作" width="320">
                <template #default="{ row }">
                  <el-button text type="primary" :disabled="!canEditUser(row)" @click="openUserEdit(row)">编辑</el-button>
                  <el-button text type="warning" :disabled="!canToggleUser(row)" @click="toggleUserStatus(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
                  <el-button text type="warning" :disabled="!canResetPassword(row)" @click="confirmResetPassword(row)">重置密码</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>

          <template v-else>
            <ListToolbar title="空间成员">
              <template #filters>
                <el-select v-model="memberWorkspaceCode" placeholder="选择工作空间" class="toolbar-filter-select workspace-select">
                  <el-option
                    v-for="item in businessWorkspaces"
                    :key="item.code"
                    :label="item.name"
                    :value="item.code"
                  />
                </el-select>
                <el-input v-model="memberFilters.keyword" placeholder="搜索姓名 / 账号 / 邮箱" clearable class="toolbar-filter-input" />
                <el-button text @click="resetMemberFilters()">
                  <el-icon><RefreshRight /></el-icon>
                  重置
                </el-button>
              </template>
              <template #actions>
                <el-button v-if="canManageSettings" type="primary" @click="openBatchMemberCreate">
                  <el-icon><Plus /></el-icon>
                  添加成员
                </el-button>
              </template>
            </ListToolbar>

            <el-table v-loading="memberLoading" :data="filteredMembers" size="large">
              <el-table-column prop="displayName" label="姓名" min-width="140" />
              <el-table-column prop="username" label="账号" min-width="160" />
              <el-table-column prop="email" label="邮箱" min-width="220" />
              <el-table-column label="成员角色" width="120">
                <template #default="{ row }">
                  {{ roleLabel(row.roleCode) }}
                </template>
              </el-table-column>
              <el-table-column v-if="canManageSettings" label="操作" width="160">
                <template #default="{ row }">
                  <el-button text type="danger" :disabled="row.roleCode === 'ADMIN' && !isSuperAdmin" @click="confirmDeleteMember(row)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </el-tab-pane>
      </el-tabs>
    </article>

    <el-dialog v-model="workspaceDialogVisible" :title="workspaceDialogMode === 'create' ? '新增工作空间' : '编辑工作空间'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="空间编码" required>
          <el-input v-model="workspaceForm.workspaceCode" :disabled="workspaceDialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="空间名称" required>
          <el-input v-model="workspaceForm.workspaceName" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="workspaceForm.description" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="workspaceDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingWorkspace" @click="submitWorkspace">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="userDialogVisible" :title="userDialogMode === 'create' ? '新增成员' : '编辑成员'" width="620px">
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
        <el-form-item label="成员角色" required>
          <el-radio-group v-model="userForm.roleCode">
            <el-radio v-if="canManageAdminUsers" value="ADMIN">管理员</el-radio>
            <el-radio value="MEMBER">普通成员</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="所属空间" :required="userForm.roleCode !== 'ADMIN'">
          <template v-if="userForm.roleCode === 'ADMIN'">
            <div class="block-note">管理员默认拥有全部空间，无需单独分配。</div>
          </template>
          <template v-else>
            <el-select
              v-model="userForm.workspaceCodes"
              multiple
              filterable
              collapse-tags
              collapse-tags-tooltip
              placeholder="请选择所属空间"
            >
              <el-option
                v-for="item in businessWorkspaces"
                :key="item.code"
                :label="item.name"
                :value="item.code"
              />
            </el-select>
          </template>
        </el-form-item>
        <div v-if="userDialogMode === 'create'" class="block-note">
          成员创建后默认密码为 <span class="mono">zhyt@2025</span>，后续可在列表中重置密码。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingUser" @click="submitUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchMemberDialogVisible" title="批量添加空间成员" width="620px">
      <el-form label-width="100px">
        <el-form-item label="目标空间">
          <el-input :model-value="resolveWorkspaceName(memberWorkspaceCode)" disabled />
        </el-form-item>
        <el-form-item label="成员" required>
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
      </el-form>
      <template #footer>
        <el-button @click="batchMemberDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingMember" @click="submitBatchMembers">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="envDialogVisible" :title="envDialogMode === 'create' ? '新增环境配置' : '编辑环境配置'" width="640px">
      <el-form label-width="100px">
        <el-form-item v-if="isAllScope" label="目标空间" required>
          <el-select v-model="envForm.workspaceCode" placeholder="请选择目标空间">
            <el-option
              v-for="item in writableWorkspaceOptions"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="环境类型" required>
          <el-select v-model="envForm.envType">
            <el-option label="API" value="API" />
            <el-option label="WEB" value="WEB" />
            <el-option label="APP" value="APP" />
          </el-select>
        </el-form-item>
        <el-form-item label="环境名称" required>
          <el-input v-model="envForm.envName" />
        </el-form-item>
        <el-form-item label="基础地址" required>
          <el-input v-model="envForm.baseUrl" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="envForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
        <el-form-item label="扩展配置">
          <el-input v-model="envForm.configJson" type="textarea" :rows="5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="envDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingEnv" @click="submitEnv">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="paramDialogVisible" :title="paramDialogMode === 'create' ? '新增参数集' : '编辑参数集'" width="640px">
      <el-form label-width="100px">
        <el-form-item v-if="isAllScope" label="目标空间" required>
          <el-select v-model="paramForm.workspaceCode" placeholder="请选择目标空间">
            <el-option
              v-for="item in writableWorkspaceOptions"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="参数类型" required>
          <el-select v-model="paramForm.paramType">
            <el-option label="TOKEN" value="TOKEN" />
            <el-option label="HEADER" value="HEADER" />
            <el-option label="BODY" value="BODY" />
            <el-option label="QUERY" value="QUERY" />
          </el-select>
        </el-form-item>
        <el-form-item label="参数集名称" required>
          <el-input v-model="paramForm.paramName" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="paramForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
        <el-form-item label="内容 JSON">
          <el-input v-model="paramForm.contentJson" type="textarea" :rows="5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="paramDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingParam" @click="submitParam">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.settings-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
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
  background: #f3f4f6;
  color: #4b5563;
}

.list-meta {
  color: #4b5563;
}

.cell-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.block-note {
  color: #6b7280;
  line-height: 1.6;
}

.mono {
  font-family: Consolas, Monaco, monospace;
}

@media (max-width: 900px) {
  .table-toolbar {
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    justify-content: flex-start;
  }
}
</style>
