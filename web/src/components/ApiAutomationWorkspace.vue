<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  Check,
  Delete,
  Plus,
  RefreshRight,
  VideoPlay,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { platformApi } from '../api/platform'
import { useWorkspace } from '../composables/useWorkspace'
import { useWorkspaceAccess } from '../composables/useWorkspaceAccess'
import type {
  ApiAssertionConfig,
  ApiDefinitionDetail,
  ApiDefinitionItem,
  ApiEnvironmentItem,
  ApiExtractorConfig,
  ApiKeyValue,
  ApiRunPayload,
  ApiRunStepResult,
  ApiScenarioDetail,
  ApiScenarioItem,
  ApiScenarioStep,
  ApiVariableItem,
  ApiVariableSetItem,
  CreateBugPayload,
  ReportDetail,
  ReportItem,
  TaskItem,
  UserItem,
  WorkspaceItem,
} from '../types/api'

const { workspaceCode, isAllScope } = useWorkspace()
const { canWriteWorkspace } = useWorkspaceAccess()

const loading = ref(false)
const saving = ref(false)
const reportDrawerVisible = ref(false)
const bugDialogVisible = ref(false)
const activeTab = ref<'definitions' | 'scenarios' | 'execution' | 'reports' | 'settings'>('definitions')

const definitions = ref<ApiDefinitionItem[]>([])
const scenarios = ref<ApiScenarioItem[]>([])
const environments = ref<ApiEnvironmentItem[]>([])
const variableSets = ref<ApiVariableSetItem[]>([])
const tasks = ref<TaskItem[]>([])
const reports = ref<ReportItem[]>([])
const users = ref<UserItem[]>([])
const workspaces = ref<WorkspaceItem[]>([])
const reportDetail = ref<ReportDetail | null>(null)
const reportStepResults = ref<ApiRunStepResult[]>([])

const selectedDefinitionId = ref<number | null>(null)
const selectedScenarioId = ref<number | null>(null)
const selectedEnvironmentId = ref<number | null>(null)
const selectedVariableSetId = ref<number | null>(null)
const selectedReportId = ref<number | null>(null)

const definitionFilters = reactive({
  keyword: '',
  directory: '',
})

const scenarioFilters = reactive({
  keyword: '',
  directory: '',
})

const definitionForm = reactive<ApiDefinitionDetail>({
  id: 0,
  workspaceCode: '',
  workspaceName: '',
  name: '',
  method: 'GET',
  path: '',
  directoryName: '',
  description: '',
  tags: [],
  lastRunResult: null,
  lastRunAt: null,
  updatedAt: null,
  createdAt: null,
  requestConfig: {
    method: 'GET',
    path: '',
    timeoutMs: 10000,
    queryParams: [],
    headers: [],
    cookies: [],
    body: {
      type: 'NONE',
      rawText: '',
      formItems: [],
    },
    authConfig: {
      type: 'INHERIT',
      token: '',
      username: '',
      password: '',
    },
  },
  assertions: [],
  extractors: [],
})

const scenarioForm = reactive<ApiScenarioDetail>({
  id: 0,
  workspaceCode: '',
  workspaceName: '',
  name: '',
  directoryName: '',
  description: '',
  tags: [],
  stepCount: 0,
  defaultEnvironmentId: null,
  variableSetId: null,
  continueOnFailure: false,
  lastRunResult: null,
  lastRunAt: null,
  updatedAt: null,
  createdAt: null,
  relatedCaseId: null,
  steps: [],
})

const environmentForm = reactive<ApiEnvironmentItem>({
  id: 0,
  workspaceCode: '',
  workspaceName: '',
  name: '',
  baseUrl: '',
  headers: [],
  authConfig: {
    type: 'NONE',
    token: '',
    username: '',
    password: '',
  },
  timeoutMs: 10000,
  status: 1,
})

const variableSetForm = reactive<ApiVariableSetItem>({
  id: 0,
  workspaceCode: '',
  workspaceName: '',
  name: '',
  variables: [],
  status: 1,
})

const runOptions = reactive<ApiRunPayload>({
  environmentId: null,
  variableSetId: null,
})

const bugForm = reactive<CreateBugPayload & { workspaceCode: string; reportId: number | null }>({
  workspaceCode: '',
  reportId: null,
  title: '',
  description: '',
  priority: 'P1',
  severity: 'HIGH',
  assigneeId: null,
  tags: [],
})

const writableWorkspaces = computed(() => workspaces.value.filter(item => !item.allScope && canWriteWorkspace(item.code)))
const canWriteCurrentWorkspace = computed(() => isAllScope.value
  ? !!(bugForm.workspaceCode || workspaceCode.value === 'ALL') && writableWorkspaces.value.length > 0
  : canWriteWorkspace(workspaceCode.value))

const definitionDirectoryOptions = computed(() => uniqueNonEmpty(definitions.value.map(item => item.directoryName)))
const scenarioDirectoryOptions = computed(() => uniqueNonEmpty(scenarios.value.map(item => item.directoryName)))
const activeOwnerOptions = computed(() => users.value.filter(item => item.status === 1))
const definitionOptions = computed(() => definitions.value.map(item => ({
  label: `${item.method} ${item.name}`,
  value: item.id,
})))

const filteredDefinitions = computed(() => definitions.value.filter((item) => {
  const keyword = definitionFilters.keyword.trim().toLowerCase()
  if (keyword) {
    const matched = item.name.toLowerCase().includes(keyword)
      || item.path.toLowerCase().includes(keyword)
      || (item.description || '').toLowerCase().includes(keyword)
    if (!matched) {
      return false
    }
  }
  if (definitionFilters.directory && (item.directoryName || '') !== definitionFilters.directory) {
    return false
  }
  return true
}))

const filteredScenarios = computed(() => scenarios.value.filter((item) => {
  const keyword = scenarioFilters.keyword.trim().toLowerCase()
  if (keyword) {
    const matched = item.name.toLowerCase().includes(keyword)
      || (item.description || '').toLowerCase().includes(keyword)
    if (!matched) {
      return false
    }
  }
  if (scenarioFilters.directory && (item.directoryName || '') !== scenarioFilters.directory) {
    return false
  }
  return true
}))

const apiTasks = computed(() => tasks.value.filter(item => item.engineType === 'API'))
const apiTaskMap = computed(() => new Map(apiTasks.value.map(item => [item.id, item])))
const apiReports = computed(() => reports.value.filter(item => apiTaskMap.value.has(item.taskId)))

watch(() => workspaceCode.value, () => {
  void bootstrap()
})

watch(filteredDefinitions, (items) => {
  if (!items.length) {
    resetDefinitionForm()
    selectedDefinitionId.value = null
    return
  }
  if (selectedDefinitionId.value == null || !items.some(item => item.id === selectedDefinitionId.value)) {
    void selectDefinition(items[0].id)
  }
})

watch(filteredScenarios, (items) => {
  if (!items.length) {
    resetScenarioForm()
    selectedScenarioId.value = null
    return
  }
  if (selectedScenarioId.value == null || !items.some(item => item.id === selectedScenarioId.value)) {
    void selectScenario(items[0].id)
  }
})

onMounted(() => {
  void bootstrap()
})

function emptyKeyValue(): ApiKeyValue {
  return { key: '', value: '', enabled: true }
}

function emptyAssertion(): ApiAssertionConfig {
  return { type: 'STATUS_CODE', subject: '', operator: 'EQUALS', expectedValue: '200' }
}

function emptyExtractor(): ApiExtractorConfig {
  return { name: '', sourceType: 'BODY_JSONPATH', expression: '$.data.id' }
}

function emptyScenarioStep(): ApiScenarioStep {
  return { stepName: '', definitionId: 0, enabled: true }
}

function emptyVariable(): ApiVariableItem {
  return { name: '', value: '', sensitive: false }
}

function defaultEditableWorkspaceCode() {
  return isAllScope.value ? (writableWorkspaces.value[0]?.code || '') : workspaceCode.value
}

function resetDefinitionForm() {
  Object.assign(definitionForm, {
    id: 0,
    workspaceCode: defaultEditableWorkspaceCode(),
    workspaceName: '',
    name: '',
    method: 'GET',
    path: '',
    directoryName: '',
    description: '',
    tags: [],
    lastRunResult: null,
    lastRunAt: null,
    updatedAt: null,
    createdAt: null,
    requestConfig: {
      method: 'GET',
      path: '',
      timeoutMs: 10000,
      queryParams: [],
      headers: [],
      cookies: [],
      body: { type: 'NONE', rawText: '', formItems: [] },
      authConfig: { type: 'INHERIT', token: '', username: '', password: '' },
    },
    assertions: [],
    extractors: [],
  })
}

function resetScenarioForm() {
  Object.assign(scenarioForm, {
    id: 0,
    workspaceCode: defaultEditableWorkspaceCode(),
    workspaceName: '',
    name: '',
    directoryName: '',
    description: '',
    tags: [],
    stepCount: 0,
    defaultEnvironmentId: runOptions.environmentId ?? null,
    variableSetId: runOptions.variableSetId ?? null,
    continueOnFailure: false,
    lastRunResult: null,
    lastRunAt: null,
    updatedAt: null,
    createdAt: null,
    relatedCaseId: null,
    steps: [],
  })
}

function resetEnvironmentForm() {
  Object.assign(environmentForm, {
    id: 0,
    workspaceCode: defaultEditableWorkspaceCode(),
    workspaceName: '',
    name: '',
    baseUrl: '',
    headers: [],
    authConfig: { type: 'NONE', token: '', username: '', password: '' },
    timeoutMs: 10000,
    status: 1,
  })
}

function resetVariableSetForm() {
  Object.assign(variableSetForm, {
    id: 0,
    workspaceCode: defaultEditableWorkspaceCode(),
    workspaceName: '',
    name: '',
    variables: [],
    status: 1,
  })
}

async function bootstrap() {
  loading.value = true
  try {
    const [
      definitionPage,
      scenarioPage,
      envPage,
      variablePage,
      taskPage,
      reportPage,
      userList,
      workspaceList,
    ] = await Promise.all([
      platformApi.getApiDefinitions(workspaceCode.value),
      platformApi.getApiScenarios(workspaceCode.value),
      platformApi.getApiEnvironments(workspaceCode.value),
      platformApi.getApiVariableSets(workspaceCode.value),
      platformApi.getTasks(workspaceCode.value),
      platformApi.getReports(workspaceCode.value),
      platformApi.getUsers(),
      platformApi.getSwitchableWorkspaces(),
    ])
    definitions.value = definitionPage.items
    scenarios.value = scenarioPage.items
    environments.value = envPage.items
    variableSets.value = variablePage.items
    tasks.value = taskPage.items
    reports.value = reportPage.items
    users.value = userList
    workspaces.value = workspaceList.filter(item => !item.allScope)
    if (!selectedEnvironmentId.value && environments.value.length > 0) {
      selectedEnvironmentId.value = environments.value[0].id
      runOptions.environmentId = selectedEnvironmentId.value
    }
    if (!selectedVariableSetId.value && variableSets.value.length > 0) {
      selectedVariableSetId.value = variableSets.value[0].id
      runOptions.variableSetId = selectedVariableSetId.value
    }
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    loading.value = false
  }
}

async function refreshData() {
  await bootstrap()
}

async function selectDefinition(id: number) {
  selectedDefinitionId.value = id
  const detail = await platformApi.getApiDefinitionDetail(workspaceCode.value, id)
  assignDefinition(detail)
}

async function selectScenario(id: number) {
  selectedScenarioId.value = id
  const detail = await platformApi.getApiScenarioDetail(workspaceCode.value, id)
  assignScenario(detail)
}

function assignDefinition(detail: ApiDefinitionDetail) {
  Object.assign(definitionForm, JSON.parse(JSON.stringify(detail)))
}

function assignScenario(detail: ApiScenarioDetail) {
  Object.assign(scenarioForm, JSON.parse(JSON.stringify(detail)))
}

function addDefinitionRow(target: ApiKeyValue[]) {
  target.push(emptyKeyValue())
}

function addAssertion() {
  definitionForm.assertions.push(emptyAssertion())
}

function addExtractor() {
  definitionForm.extractors.push(emptyExtractor())
}

function addScenarioStep() {
  scenarioForm.steps.push(emptyScenarioStep())
}

function addEnvironmentHeader() {
  environmentForm.headers.push(emptyKeyValue())
}

function addVariableRow() {
  variableSetForm.variables.push(emptyVariable())
}

function moveScenarioStep(index: number, delta: number) {
  const target = index + delta
  if (target < 0 || target >= scenarioForm.steps.length) {
    return
  }
  const next = [...scenarioForm.steps]
  const current = next[index]
  next[index] = next[target]
  next[target] = current
  scenarioForm.steps.splice(0, scenarioForm.steps.length, ...next)
}

async function saveDefinition() {
  if (!definitionForm.name.trim() || !definitionForm.requestConfig.path.trim()) {
    ElMessage.warning('请补全接口名称和路径')
    return
  }
  saving.value = true
  try {
    const payload = {
      workspaceCode: isAllScope.value ? definitionForm.workspaceCode || writableWorkspaces.value[0]?.code : workspaceCode.value,
      name: definitionForm.name,
      directoryName: definitionForm.directoryName,
      description: definitionForm.description,
      tags: definitionForm.tags,
      requestConfig: definitionForm.requestConfig,
      assertions: definitionForm.assertions,
      extractors: definitionForm.extractors,
    }
    const detail = definitionForm.id
      ? await platformApi.updateApiDefinition(workspaceCode.value, definitionForm.id, payload)
      : await platformApi.createApiDefinition(workspaceCode.value, payload)
    ElMessage.success(definitionForm.id ? '接口已更新' : '接口已创建')
    await refreshData()
    await selectDefinition(detail.id)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function removeDefinition() {
  if (!definitionForm.id) {
    resetDefinitionForm()
    return
  }
  await ElMessageBox.confirm('删除后不可恢复，确认删除当前接口吗？', '删除接口', { type: 'warning' })
  await platformApi.deleteApiDefinition(workspaceCode.value, definitionForm.id)
  ElMessage.success('接口已删除')
  selectedDefinitionId.value = null
  await refreshData()
}

async function debugDefinition() {
  if (!definitionForm.id) {
    ElMessage.warning('请先保存接口')
    return
  }
  saving.value = true
  try {
    const response = await platformApi.debugApiDefinition(workspaceCode.value, definitionForm.id, runOptions)
    ElMessage.success(response.result === 'SUCCESS' ? '调试成功' : '调试失败')
    await refreshData()
    await openReportDetail(response.reportId)
    activeTab.value = 'reports'
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function saveScenario() {
  if (!scenarioForm.name.trim() || scenarioForm.steps.length === 0) {
    ElMessage.warning('请补全场景名称并至少添加一个步骤')
    return
  }
  if (scenarioForm.steps.some(step => !step.definitionId)) {
    ElMessage.warning('请为每个步骤选择接口')
    return
  }
  saving.value = true
  try {
    const payload = {
      workspaceCode: isAllScope.value ? scenarioForm.workspaceCode || writableWorkspaces.value[0]?.code : workspaceCode.value,
      name: scenarioForm.name,
      directoryName: scenarioForm.directoryName,
      description: scenarioForm.description,
      tags: scenarioForm.tags,
      defaultEnvironmentId: scenarioForm.defaultEnvironmentId,
      variableSetId: scenarioForm.variableSetId,
      continueOnFailure: scenarioForm.continueOnFailure,
      relatedCaseId: scenarioForm.relatedCaseId,
      steps: scenarioForm.steps,
    }
    const detail = scenarioForm.id
      ? await platformApi.updateApiScenario(workspaceCode.value, scenarioForm.id, payload)
      : await platformApi.createApiScenario(workspaceCode.value, payload)
    ElMessage.success(scenarioForm.id ? '场景已更新' : '场景已创建')
    await refreshData()
    await selectScenario(detail.id)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function removeScenario() {
  if (!scenarioForm.id) {
    resetScenarioForm()
    return
  }
  await ElMessageBox.confirm('删除后不可恢复，确认删除当前场景吗？', '删除场景', { type: 'warning' })
  await platformApi.deleteApiScenario(workspaceCode.value, scenarioForm.id)
  ElMessage.success('场景已删除')
  selectedScenarioId.value = null
  await refreshData()
}

async function runScenario() {
  if (!scenarioForm.id) {
    ElMessage.warning('请先保存场景')
    return
  }
  saving.value = true
  try {
    const response = await platformApi.runApiScenario(workspaceCode.value, scenarioForm.id, {
      environmentId: runOptions.environmentId ?? scenarioForm.defaultEnvironmentId,
      variableSetId: runOptions.variableSetId ?? scenarioForm.variableSetId,
    })
    ElMessage.success(response.result === 'SUCCESS' ? '场景执行成功' : '场景执行失败')
    await refreshData()
    await openReportDetail(response.reportId)
    activeTab.value = 'reports'
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function saveEnvironment() {
  if (!environmentForm.name.trim() || !environmentForm.baseUrl.trim()) {
    ElMessage.warning('请补全环境名称和 Base URL')
    return
  }
  saving.value = true
  try {
    const payload = {
      workspaceCode: isAllScope.value ? environmentForm.workspaceCode || writableWorkspaces.value[0]?.code : workspaceCode.value,
      name: environmentForm.name,
      baseUrl: environmentForm.baseUrl,
      headers: environmentForm.headers,
      authConfig: environmentForm.authConfig,
      timeoutMs: environmentForm.timeoutMs,
      status: environmentForm.status,
    }
    const item = environmentForm.id
      ? await platformApi.updateApiEnvironment(workspaceCode.value, environmentForm.id, payload)
      : await platformApi.createApiEnvironment(workspaceCode.value, payload)
    ElMessage.success(environmentForm.id ? '环境已更新' : '环境已创建')
    await refreshData()
    Object.assign(environmentForm, JSON.parse(JSON.stringify(item)))
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function removeEnvironment(id: number) {
  await ElMessageBox.confirm('确认删除这个环境吗？', '删除环境', { type: 'warning' })
  await platformApi.deleteApiEnvironment(workspaceCode.value, id)
  ElMessage.success('环境已删除')
  await refreshData()
  resetEnvironmentForm()
}

async function saveVariableSet() {
  if (!variableSetForm.name.trim()) {
    ElMessage.warning('请填写变量集名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      workspaceCode: isAllScope.value ? variableSetForm.workspaceCode || writableWorkspaces.value[0]?.code : workspaceCode.value,
      name: variableSetForm.name,
      variables: variableSetForm.variables,
      status: variableSetForm.status,
    }
    const item = variableSetForm.id
      ? await platformApi.updateApiVariableSet(workspaceCode.value, variableSetForm.id, payload)
      : await platformApi.createApiVariableSet(workspaceCode.value, payload)
    ElMessage.success(variableSetForm.id ? '变量集已更新' : '变量集已创建')
    await refreshData()
    Object.assign(variableSetForm, JSON.parse(JSON.stringify(item)))
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function removeVariableSet(id: number) {
  await ElMessageBox.confirm('确认删除这个变量集吗？', '删除变量集', { type: 'warning' })
  await platformApi.deleteApiVariableSet(workspaceCode.value, id)
  ElMessage.success('变量集已删除')
  await refreshData()
  resetVariableSetForm()
}

async function openReportDetail(id: number) {
  selectedReportId.value = id
  reportDrawerVisible.value = true
  try {
    const [detail, steps] = await Promise.all([
      platformApi.getReportDetail(workspaceCode.value, id),
      platformApi.getApiRunStepResults(workspaceCode.value, id),
    ])
    reportDetail.value = detail
    reportStepResults.value = steps
  }
  catch (error) {
    reportDrawerVisible.value = false
    ElMessage.error((error as Error).message)
  }
}

function openReportBug() {
  if (!reportDetail.value) {
    return
  }
  const failedStep = reportStepResults.value.find(item => !item.success)
  bugForm.workspaceCode = reportDetail.value.workspaceCode
  bugForm.reportId = reportDetail.value.id
  bugForm.title = `[API] ${reportDetail.value.reportName} 执行失败`
  bugForm.description = [
    `报告：${reportDetail.value.reportName}`,
    `任务：${reportDetail.value.taskName}`,
    reportDetail.value.failureSummary ? `失败摘要：${reportDetail.value.failureSummary}` : '',
    failedStep ? `失败步骤：${failedStep.stepName}` : '',
    failedStep?.request?.url ? `请求地址：${failedStep.request.url}` : '',
    failedStep?.errorMessage ? `错误信息：${failedStep.errorMessage}` : '',
  ].filter(Boolean).join('\n')
  bugForm.priority = 'P1'
  bugForm.severity = 'HIGH'
  bugForm.assigneeId = activeOwnerOptions.value[0]?.id ?? null
  bugForm.tags = ['API_AUTOMATION']
  bugDialogVisible.value = true
}

async function submitReportBug() {
  if (!bugForm.reportId || !bugForm.title.trim() || !bugForm.description.trim() || !bugForm.assigneeId) {
    ElMessage.warning('请补全缺陷标题、描述和处理人')
    return
  }
  saving.value = true
  try {
    await platformApi.createBugFromReport(bugForm.workspaceCode || workspaceCode.value, bugForm.reportId, {
      workspaceCode: bugForm.workspaceCode,
      title: bugForm.title,
      description: bugForm.description,
      priority: bugForm.priority,
      severity: bugForm.severity,
      assigneeId: bugForm.assigneeId,
      tags: bugForm.tags,
    })
    bugDialogVisible.value = false
    ElMessage.success('已从报告创建缺陷')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

function selectEnvironment(item: ApiEnvironmentItem) {
  Object.assign(environmentForm, JSON.parse(JSON.stringify(item)))
}

function selectVariableSet(item: ApiVariableSetItem) {
  Object.assign(variableSetForm, JSON.parse(JSON.stringify(item)))
}

function fillFromDefinition(item: ApiDefinitionItem) {
  void selectDefinition(item.id)
  activeTab.value = 'definitions'
}

function fillFromScenario(item: ApiScenarioItem) {
  void selectScenario(item.id)
  activeTab.value = 'scenarios'
}

async function runDefinitionItem(id: number) {
  await selectDefinition(id)
  await debugDefinition()
}

async function runScenarioItem(id: number) {
  await selectScenario(id)
  await runScenario()
}

function updateTagInput(target: { tags: string[] }, value: string) {
  target.tags = value.split(',').map(item => item.trim()).filter(Boolean)
}

function readTagInput(tags: string[]) {
  return tags.join(', ')
}

function uniqueNonEmpty(values: Array<string | null | undefined>) {
  return Array.from(new Set(values.filter((item): item is string => !!item && item.trim().length > 0)))
}
</script>

<template>
  <section v-loading="loading" class="api-automation-page">
    <div class="page-header">
      <div>
        <h2>接口自动化工作台</h2>
        <p>围绕接口资产、场景编排、执行报告和缺陷闭环展开。</p>
      </div>
      <div class="page-actions">
        <el-select v-model="runOptions.environmentId" clearable placeholder="执行环境" class="header-select">
          <el-option v-for="item in environments" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-select v-model="runOptions.variableSetId" clearable placeholder="变量集" class="header-select">
          <el-option v-for="item in variableSets" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-button @click="refreshData">
          <el-icon><RefreshRight /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="api-tabs">
      <el-tab-pane label="接口" name="definitions">
        <div class="workspace-grid">
          <section class="workspace-sidebar">
            <div class="toolbar-row">
              <el-input v-model="definitionFilters.keyword" placeholder="搜索接口名称或路径" clearable />
              <el-button type="primary" @click="resetDefinitionForm">
                <el-icon><Plus /></el-icon>
                新建
              </el-button>
            </div>
            <el-select v-model="definitionFilters.directory" clearable placeholder="目录筛选" class="full-width">
              <el-option v-for="item in definitionDirectoryOptions" :key="item" :label="item" :value="item" />
            </el-select>
            <div class="asset-list">
              <button
                v-for="item in filteredDefinitions"
                :key="item.id"
                type="button"
                :class="['asset-item', { active: item.id === selectedDefinitionId }]"
                @click="selectDefinition(item.id)"
              >
                <div class="asset-item-top">
                  <el-tag size="small" effect="plain">{{ item.method }}</el-tag>
                  <span class="asset-result">{{ item.lastRunResult || '-' }}</span>
                </div>
                <div class="asset-item-title">{{ item.name }}</div>
                <div class="asset-item-meta">{{ item.path }}</div>
              </button>
            </div>
          </section>

          <section class="workspace-main">
            <div class="editor-header">
              <div>
                <div class="editor-title">{{ definitionForm.id ? '接口详情' : '新建接口' }}</div>
                <div class="editor-subtitle">支持结构化请求、基础断言、变量提取和单次调试。</div>
              </div>
              <div class="editor-actions">
                <el-button :disabled="!canWriteCurrentWorkspace" :loading="saving" @click="saveDefinition">
                  <el-icon><Check /></el-icon>
                  保存
                </el-button>
                <el-button type="primary" :disabled="!definitionForm.id || !canWriteCurrentWorkspace" :loading="saving" @click="debugDefinition">
                  <el-icon><VideoPlay /></el-icon>
                  发送调试
                </el-button>
                <el-button :disabled="!definitionForm.id || !canWriteCurrentWorkspace" @click="removeDefinition">
                  <el-icon><Delete /></el-icon>
                  删除
                </el-button>
              </div>
            </div>

            <div class="editor-form">
              <el-select v-if="isAllScope" v-model="definitionForm.workspaceCode" placeholder="目标空间">
                <el-option v-for="item in writableWorkspaces" :key="item.code" :label="item.name" :value="item.code" />
              </el-select>
              <div class="form-grid">
                <el-input v-model="definitionForm.name" placeholder="接口名称" />
                <el-input v-model="definitionForm.directoryName" placeholder="目录" />
                <el-select v-model="definitionForm.requestConfig.method">
                  <el-option label="GET" value="GET" />
                  <el-option label="POST" value="POST" />
                  <el-option label="PUT" value="PUT" />
                  <el-option label="DELETE" value="DELETE" />
                  <el-option label="PATCH" value="PATCH" />
                </el-select>
                <el-input v-model="definitionForm.requestConfig.path" placeholder="/api/example 或完整 URL" />
              </div>
              <el-input
                :model-value="readTagInput(definitionForm.tags)"
                placeholder="标签，逗号分隔"
                @update:model-value="(value: string | number) => updateTagInput(definitionForm, String(value))"
              />
              <el-input v-model="definitionForm.description" type="textarea" :rows="2" placeholder="接口描述" />

              <div class="editor-section">
                <div class="section-title">请求配置</div>
                <div class="form-grid">
                  <el-input-number v-model="definitionForm.requestConfig.timeoutMs" :min="1000" :step="1000" />
                  <el-select v-model="definitionForm.requestConfig.body.type">
                    <el-option label="NONE" value="NONE" />
                    <el-option label="RAW_JSON" value="RAW_JSON" />
                    <el-option label="RAW_TEXT" value="RAW_TEXT" />
                    <el-option label="FORM_URLENCODED" value="FORM_URLENCODED" />
                    <el-option label="FORM_DATA" value="FORM_DATA" />
                  </el-select>
                </div>

                <div class="kv-block">
                  <div class="section-subtitle">
                    Query
                    <el-button text @click="addDefinitionRow(definitionForm.requestConfig.queryParams)">新增</el-button>
                  </div>
                  <div v-for="(row, index) in definitionForm.requestConfig.queryParams" :key="`query-${index}`" class="kv-row">
                    <el-input v-model="row.key" placeholder="key" />
                    <el-input v-model="row.value" placeholder="value" />
                    <el-switch v-model="row.enabled" />
                    <el-button text @click="definitionForm.requestConfig.queryParams.splice(index, 1)">删除</el-button>
                  </div>
                </div>

                <div class="kv-block">
                  <div class="section-subtitle">
                    Headers
                    <el-button text @click="addDefinitionRow(definitionForm.requestConfig.headers)">新增</el-button>
                  </div>
                  <div v-for="(row, index) in definitionForm.requestConfig.headers" :key="`header-${index}`" class="kv-row">
                    <el-input v-model="row.key" placeholder="key" />
                    <el-input v-model="row.value" placeholder="value" />
                    <el-switch v-model="row.enabled" />
                    <el-button text @click="definitionForm.requestConfig.headers.splice(index, 1)">删除</el-button>
                  </div>
                </div>

                <div class="kv-block">
                  <div class="section-subtitle">
                    Cookies
                    <el-button text @click="addDefinitionRow(definitionForm.requestConfig.cookies)">新增</el-button>
                  </div>
                  <div v-for="(row, index) in definitionForm.requestConfig.cookies" :key="`cookie-${index}`" class="kv-row">
                    <el-input v-model="row.key" placeholder="key" />
                    <el-input v-model="row.value" placeholder="value" />
                    <el-switch v-model="row.enabled" />
                    <el-button text @click="definitionForm.requestConfig.cookies.splice(index, 1)">删除</el-button>
                  </div>
                </div>

                <div class="kv-block">
                  <div class="section-subtitle">
                    Body
                    <el-button
                      v-if="['FORM_URLENCODED', 'FORM_DATA'].includes(definitionForm.requestConfig.body.type)"
                      text
                      @click="definitionForm.requestConfig.body.formItems.push(emptyKeyValue())"
                    >
                      新增
                    </el-button>
                  </div>
                  <el-input
                    v-if="['RAW_JSON', 'RAW_TEXT'].includes(definitionForm.requestConfig.body.type)"
                    v-model="definitionForm.requestConfig.body.rawText"
                    type="textarea"
                    :rows="6"
                    placeholder="支持 {{variable}} 占位符"
                  />
                  <div
                    v-for="(row, index) in definitionForm.requestConfig.body.formItems"
                    v-else-if="['FORM_URLENCODED', 'FORM_DATA'].includes(definitionForm.requestConfig.body.type)"
                    :key="`body-${index}`"
                    class="kv-row"
                  >
                    <el-input v-model="row.key" placeholder="key" />
                    <el-input v-model="row.value" placeholder="value" />
                    <el-switch v-model="row.enabled" />
                    <el-button text @click="definitionForm.requestConfig.body.formItems.splice(index, 1)">删除</el-button>
                  </div>
                </div>

                <div class="form-grid">
                  <el-select v-model="definitionForm.requestConfig.authConfig.type">
                    <el-option label="INHERIT" value="INHERIT" />
                    <el-option label="NONE" value="NONE" />
                    <el-option label="BEARER" value="BEARER" />
                    <el-option label="BASIC" value="BASIC" />
                  </el-select>
                  <template v-if="definitionForm.requestConfig.authConfig.type === 'BEARER'">
                    <el-input v-model="definitionForm.requestConfig.authConfig.token" placeholder="Bearer Token" />
                  </template>
                  <template v-else-if="definitionForm.requestConfig.authConfig.type === 'BASIC'">
                    <el-input v-model="definitionForm.requestConfig.authConfig.username" placeholder="username" />
                    <el-input v-model="definitionForm.requestConfig.authConfig.password" placeholder="password" show-password />
                  </template>
                </div>
              </div>

              <div class="editor-section">
                <div class="section-title">
                  断言
                  <el-button text @click="addAssertion">新增</el-button>
                </div>
                <div v-for="(item, index) in definitionForm.assertions" :key="`assert-${index}`" class="assert-row">
                  <el-select v-model="item.type">
                    <el-option label="STATUS_CODE" value="STATUS_CODE" />
                    <el-option label="HEADER_EQUALS" value="HEADER_EQUALS" />
                    <el-option label="HEADER_CONTAINS" value="HEADER_CONTAINS" />
                    <el-option label="BODY_JSONPATH_EQUALS" value="BODY_JSONPATH_EQUALS" />
                    <el-option label="BODY_JSONPATH_CONTAINS" value="BODY_JSONPATH_CONTAINS" />
                    <el-option label="RESPONSE_TIME_LE" value="RESPONSE_TIME_LE" />
                  </el-select>
                  <el-input v-model="item.subject" placeholder="subject / 表达式" />
                  <el-input v-model="item.expectedValue" placeholder="期望值" />
                  <el-button text @click="definitionForm.assertions.splice(index, 1)">删除</el-button>
                </div>
              </div>

              <div class="editor-section">
                <div class="section-title">
                  变量提取
                  <el-button text @click="addExtractor">新增</el-button>
                </div>
                <div v-for="(item, index) in definitionForm.extractors" :key="`extract-${index}`" class="assert-row">
                  <el-input v-model="item.name" placeholder="变量名" />
                  <el-select v-model="item.sourceType">
                    <el-option label="BODY_JSONPATH" value="BODY_JSONPATH" />
                    <el-option label="HEADER" value="HEADER" />
                  </el-select>
                  <el-input v-model="item.expression" placeholder="JSONPath 或 Header 名" />
                  <el-button text @click="definitionForm.extractors.splice(index, 1)">删除</el-button>
                </div>
              </div>
            </div>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="场景" name="scenarios">
        <div class="workspace-grid">
          <section class="workspace-sidebar">
            <div class="toolbar-row">
              <el-input v-model="scenarioFilters.keyword" placeholder="搜索场景名称" clearable />
              <el-button type="primary" @click="resetScenarioForm">
                <el-icon><Plus /></el-icon>
                新建
              </el-button>
            </div>
            <el-select v-model="scenarioFilters.directory" clearable placeholder="目录筛选" class="full-width">
              <el-option v-for="item in scenarioDirectoryOptions" :key="item" :label="item" :value="item" />
            </el-select>
            <div class="asset-list">
              <button
                v-for="item in filteredScenarios"
                :key="item.id"
                type="button"
                :class="['asset-item', { active: item.id === selectedScenarioId }]"
                @click="selectScenario(item.id)"
              >
                <div class="asset-item-top">
                  <el-tag size="small" effect="plain">{{ item.stepCount }} 步</el-tag>
                  <span class="asset-result">{{ item.lastRunResult || '-' }}</span>
                </div>
                <div class="asset-item-title">{{ item.name }}</div>
                <div class="asset-item-meta">{{ item.directoryName || '未分组' }}</div>
              </button>
            </div>
          </section>

          <section class="workspace-main">
            <div class="editor-header">
              <div>
                <div class="editor-title">{{ scenarioForm.id ? '场景详情' : '新建场景' }}</div>
                <div class="editor-subtitle">V1 采用顺序步骤编排，支持变量传递和失败短路。</div>
              </div>
              <div class="editor-actions">
                <el-button :disabled="!canWriteCurrentWorkspace" :loading="saving" @click="saveScenario">
                  <el-icon><Check /></el-icon>
                  保存
                </el-button>
                <el-button type="primary" :disabled="!scenarioForm.id || !canWriteCurrentWorkspace" :loading="saving" @click="runScenario">
                  <el-icon><VideoPlay /></el-icon>
                  立即执行
                </el-button>
                <el-button :disabled="!scenarioForm.id || !canWriteCurrentWorkspace" @click="removeScenario">
                  <el-icon><Delete /></el-icon>
                  删除
                </el-button>
              </div>
            </div>

            <div class="editor-form">
              <el-select v-if="isAllScope" v-model="scenarioForm.workspaceCode" placeholder="目标空间">
                <el-option v-for="item in writableWorkspaces" :key="item.code" :label="item.name" :value="item.code" />
              </el-select>
              <div class="form-grid">
                <el-input v-model="scenarioForm.name" placeholder="场景名称" />
                <el-input v-model="scenarioForm.directoryName" placeholder="目录" />
                <el-select v-model="scenarioForm.defaultEnvironmentId" clearable placeholder="默认环境">
                  <el-option v-for="item in environments" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
                <el-select v-model="scenarioForm.variableSetId" clearable placeholder="变量集">
                  <el-option v-for="item in variableSets" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </div>
              <el-input
                :model-value="readTagInput(scenarioForm.tags)"
                placeholder="标签，逗号分隔"
                @update:model-value="(value: string | number) => updateTagInput(scenarioForm, String(value))"
              />
              <el-input v-model="scenarioForm.description" type="textarea" :rows="2" placeholder="场景描述" />
              <el-switch v-model="scenarioForm.continueOnFailure" active-text="失败后继续执行" inactive-text="失败即停止" />

              <div class="editor-section">
                <div class="section-title">
                  步骤编排
                  <el-button text @click="addScenarioStep">新增步骤</el-button>
                </div>
                <div v-for="(step, index) in scenarioForm.steps" :key="`step-${index}`" class="scenario-step-row">
                  <div class="step-index">{{ index + 1 }}</div>
                  <el-input v-model="step.stepName" placeholder="步骤名称（可选）" />
                  <el-select v-model="step.definitionId" placeholder="选择接口">
                    <el-option v-for="item in definitionOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                  <el-switch v-model="step.enabled" />
                  <el-button text @click="moveScenarioStep(index, -1)">上移</el-button>
                  <el-button text @click="moveScenarioStep(index, 1)">下移</el-button>
                  <el-button text @click="scenarioForm.steps.splice(index, 1)">删除</el-button>
                </div>
              </div>
            </div>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="执行" name="execution">
        <div class="execution-grid">
          <section class="panel">
            <div class="panel-header">
              <div class="panel-title">可运行接口</div>
              <div class="panel-subtitle">挑选接口直接调试，使用顶部环境和变量集。</div>
            </div>
            <div class="execution-list">
              <div v-for="item in filteredDefinitions" :key="`run-def-${item.id}`" class="execution-item">
                <div>
                  <div class="execution-name">{{ item.name }}</div>
                  <div class="execution-meta">{{ item.method }} {{ item.path }}</div>
                </div>
                <div class="execution-actions">
                  <el-button size="small" @click="fillFromDefinition(item)">查看</el-button>
                  <el-button size="small" type="primary" @click="runDefinitionItem(item.id)">运行</el-button>
                </div>
              </div>
            </div>
          </section>

          <section class="panel">
            <div class="panel-header">
              <div class="panel-title">可运行场景</div>
              <div class="panel-subtitle">顺序场景会生成任务和报告记录。</div>
            </div>
            <div class="execution-list">
              <div v-for="item in filteredScenarios" :key="`run-scene-${item.id}`" class="execution-item">
                <div>
                  <div class="execution-name">{{ item.name }}</div>
                  <div class="execution-meta">{{ item.stepCount }} 步 / {{ item.directoryName || '未分组' }}</div>
                </div>
                <div class="execution-actions">
                  <el-button size="small" @click="fillFromScenario(item)">查看</el-button>
                  <el-button size="small" type="primary" @click="runScenarioItem(item.id)">运行</el-button>
                </div>
              </div>
            </div>
          </section>
        </div>

        <section class="panel table-panel">
          <div class="panel-header">
            <div class="panel-title">最近执行任务</div>
            <div class="panel-subtitle">复用现有任务体系，API 自动化运行会写入这里。</div>
          </div>
          <el-table :data="apiTasks" size="small">
            <el-table-column prop="taskName" label="任务名称" min-width="220" />
            <el-table-column prop="status" label="状态" width="120" />
            <el-table-column prop="summary" label="摘要" min-width="220" />
            <el-table-column prop="workspaceName" label="空间" width="160" />
          </el-table>
        </section>
      </el-tab-pane>

      <el-tab-pane label="报告" name="reports">
        <section class="panel table-panel">
          <div class="panel-header">
            <div class="panel-title">执行报告</div>
            <div class="panel-subtitle">保留现有报告体系，并补充步骤级请求和断言明细。</div>
          </div>
          <el-table :data="apiReports" size="small">
            <el-table-column prop="reportName" label="报告名称" min-width="220" />
            <el-table-column prop="result" label="结果" width="120" />
            <el-table-column prop="failureSummary" label="失败摘要" min-width="240" />
            <el-table-column prop="workspaceName" label="空间" width="160" />
            <el-table-column width="120" label="操作" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" @click="openReportDetail(row.id)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </el-tab-pane>

      <el-tab-pane label="设置" name="settings">
        <div class="settings-grid">
          <section class="panel">
            <div class="panel-header">
              <div class="panel-title">执行环境</div>
              <div class="panel-subtitle">环境继承公共 Header、鉴权和默认超时。</div>
            </div>
            <div class="asset-list compact">
              <button v-for="item in environments" :key="item.id" type="button" class="asset-item" @click="selectEnvironment(item)">
                <div class="asset-item-title">{{ item.name }}</div>
                <div class="asset-item-meta">{{ item.baseUrl }}</div>
              </button>
            </div>
            <div class="editor-section">
              <div class="section-title">环境编辑</div>
              <el-select v-if="isAllScope" v-model="environmentForm.workspaceCode" placeholder="目标空间">
                <el-option v-for="item in writableWorkspaces" :key="item.code" :label="item.name" :value="item.code" />
              </el-select>
              <el-input v-model="environmentForm.name" placeholder="环境名称" />
              <el-input v-model="environmentForm.baseUrl" placeholder="Base URL" />
              <el-input-number v-model="environmentForm.timeoutMs" :min="1000" :step="1000" class="full-width" />
              <div class="section-subtitle">
                公共 Header
                <el-button text @click="addEnvironmentHeader">新增</el-button>
              </div>
              <div v-for="(row, index) in environmentForm.headers" :key="`env-header-${index}`" class="kv-row">
                <el-input v-model="row.key" placeholder="key" />
                <el-input v-model="row.value" placeholder="value" />
                <el-switch v-model="row.enabled" />
                <el-button text @click="environmentForm.headers.splice(index, 1)">删除</el-button>
              </div>
              <div class="form-grid">
                <el-select v-model="environmentForm.authConfig.type">
                  <el-option label="NONE" value="NONE" />
                  <el-option label="BEARER" value="BEARER" />
                  <el-option label="BASIC" value="BASIC" />
                </el-select>
                <el-select v-model="environmentForm.status">
                  <el-option label="启用" :value="1" />
                  <el-option label="停用" :value="0" />
                </el-select>
              </div>
              <el-input
                v-if="environmentForm.authConfig.type === 'BEARER'"
                v-model="environmentForm.authConfig.token"
                placeholder="Bearer Token"
              />
              <div v-else-if="environmentForm.authConfig.type === 'BASIC'" class="form-grid">
                <el-input v-model="environmentForm.authConfig.username" placeholder="username" />
                <el-input v-model="environmentForm.authConfig.password" placeholder="password" show-password />
              </div>
              <div class="editor-actions left">
                <el-button :loading="saving" :disabled="!canWriteCurrentWorkspace" @click="saveEnvironment">保存环境</el-button>
                <el-button :disabled="!environmentForm.id || !canWriteCurrentWorkspace" @click="removeEnvironment(environmentForm.id)">删除</el-button>
                <el-button @click="resetEnvironmentForm">重置</el-button>
              </div>
            </div>
          </section>

          <section class="panel">
            <div class="panel-header">
              <div class="panel-title">变量集</div>
              <div class="panel-subtitle">变量值支持在请求里通过 <code v-pre>{{ variable }}</code> 被替换。</div>
            </div>
            <div class="asset-list compact">
              <button v-for="item in variableSets" :key="item.id" type="button" class="asset-item" @click="selectVariableSet(item)">
                <div class="asset-item-title">{{ item.name }}</div>
                <div class="asset-item-meta">{{ item.variables.length }} 个变量</div>
              </button>
            </div>
            <div class="editor-section">
              <div class="section-title">变量集编辑</div>
              <el-select v-if="isAllScope" v-model="variableSetForm.workspaceCode" placeholder="目标空间">
                <el-option v-for="item in writableWorkspaces" :key="item.code" :label="item.name" :value="item.code" />
              </el-select>
              <el-input v-model="variableSetForm.name" placeholder="变量集名称" />
              <el-select v-model="variableSetForm.status">
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
              <div class="section-subtitle">
                变量列表
                <el-button text @click="addVariableRow">新增</el-button>
              </div>
              <div v-for="(row, index) in variableSetForm.variables" :key="`var-${index}`" class="kv-row">
                <el-input v-model="row.name" placeholder="变量名" />
                <el-input v-model="row.value" placeholder="变量值" />
                <el-switch v-model="row.sensitive" active-text="敏感" />
                <el-button text @click="variableSetForm.variables.splice(index, 1)">删除</el-button>
              </div>
              <div class="editor-actions left">
                <el-button :loading="saving" :disabled="!canWriteCurrentWorkspace" @click="saveVariableSet">保存变量集</el-button>
                <el-button :disabled="!variableSetForm.id || !canWriteCurrentWorkspace" @click="removeVariableSet(variableSetForm.id)">删除</el-button>
                <el-button @click="resetVariableSetForm">重置</el-button>
              </div>
            </div>
          </section>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="bugDialogVisible" title="从报告创建缺陷" width="640px">
      <el-form label-width="90px">
        <el-form-item v-if="isAllScope" label="目标空间" required>
          <el-select v-model="bugForm.workspaceCode">
            <el-option v-for="item in writableWorkspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="bugForm.title" />
        </el-form-item>
        <el-form-item label="描述" required>
          <el-input v-model="bugForm.description" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="处理人" required>
          <el-select v-model="bugForm.assigneeId">
            <el-option v-for="item in activeOwnerOptions" :key="item.id" :label="item.displayName" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bugDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReportBug">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="reportDrawerVisible" title="API 执行报告" size="860px">
      <div v-if="reportDetail" class="report-drawer">
        <div class="detail-grid">
          <div><span>报告名称</span><strong>{{ reportDetail.reportName }}</strong></div>
          <div><span>任务名称</span><strong>{{ reportDetail.taskName }}</strong></div>
          <div><span>执行结果</span><strong>{{ reportDetail.result }}</strong></div>
          <div><span>空间</span><strong>{{ reportDetail.workspaceName }}</strong></div>
        </div>
        <el-input v-model="reportDetail.failureSummary" type="textarea" :rows="2" readonly />
        <div class="drawer-actions">
          <el-button type="primary" @click="openReportBug">创建缺陷</el-button>
        </div>
        <div class="step-results">
          <div v-for="item in reportStepResults" :key="`${item.stepOrder}-${item.definitionId}`" class="step-card">
            <div class="step-card-header">
              <div class="step-title">{{ item.stepOrder }}. {{ item.stepName }}</div>
              <el-tag :type="item.success ? 'success' : 'danger'">{{ item.success ? '成功' : '失败' }}</el-tag>
            </div>
            <div class="step-meta">耗时 {{ item.durationMs }} ms</div>
            <div v-if="item.errorMessage" class="step-error">{{ item.errorMessage }}</div>
            <div class="snapshot-grid">
              <div>
                <div class="snapshot-title">最终请求</div>
                <pre>{{ JSON.stringify(item.request, null, 2) }}</pre>
              </div>
              <div>
                <div class="snapshot-title">响应快照</div>
                <pre>{{ JSON.stringify(item.response, null, 2) }}</pre>
              </div>
            </div>
            <div class="snapshot-grid">
              <div>
                <div class="snapshot-title">断言结果</div>
                <pre>{{ JSON.stringify(item.assertionResults, null, 2) }}</pre>
              </div>
              <div>
                <div class="snapshot-title">变量提取</div>
                <pre>{{ JSON.stringify(item.extractionResults, null, 2) }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.api-automation-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header,
.toolbar-row,
.editor-header,
.editor-actions,
.section-title,
.section-subtitle,
.drawer-actions,
.step-card-header,
.execution-item,
.execution-actions,
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-header h2 {
  margin: 0 0 4px;
  font-size: 22px;
}

.page-header p,
.editor-subtitle,
.panel-subtitle,
.asset-item-meta,
.step-meta {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.page-actions,
.form-grid,
.settings-grid,
.execution-grid,
.snapshot-grid,
.detail-grid {
  display: grid;
  gap: 12px;
}

.page-actions {
  grid-auto-flow: column;
  align-items: center;
}

.header-select {
  width: 200px;
}

.workspace-grid {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
}

.workspace-sidebar,
.workspace-main,
.panel {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.workspace-sidebar,
.panel {
  padding: 16px;
}

.workspace-main {
  padding: 20px;
}

.full-width {
  width: 100%;
}

.asset-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.asset-list.compact {
  max-height: 260px;
  overflow: auto;
}

.asset-item {
  width: 100%;
  text-align: left;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  padding: 12px;
  cursor: pointer;
}

.asset-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.asset-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.asset-item-title,
.editor-title,
.panel-title,
.execution-name,
.step-title {
  font-weight: 600;
}

.editor-form,
.step-results {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.editor-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.kv-block,
.assert-row,
.scenario-step-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.kv-row,
.assert-row,
.scenario-step-row,
.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  align-items: center;
}

.assert-row {
  grid-template-columns: 180px 1fr 1fr auto;
}

.scenario-step-row {
  grid-template-columns: 44px 1fr 1fr auto auto auto auto;
}

.step-index {
  text-align: center;
  color: var(--el-text-color-secondary);
}

.execution-grid,
.settings-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.execution-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}

.execution-item {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 12px;
}

.table-panel {
  margin-top: 16px;
}

.detail-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 16px;
}

.detail-grid span {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-bottom: 4px;
}

.report-drawer {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.step-card {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 16px;
}

.step-error {
  color: var(--el-color-danger);
}

.snapshot-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 12px;
}

.snapshot-title {
  font-weight: 600;
  margin-bottom: 6px;
}

pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.5;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 10px;
  background: var(--el-fill-color-light);
}

.editor-actions.left {
  justify-content: flex-start;
}

@media (max-width: 1200px) {
  .workspace-grid,
  .execution-grid,
  .settings-grid,
  .form-grid,
  .snapshot-grid {
    grid-template-columns: 1fr;
  }

  .scenario-step-row,
  .assert-row,
  .kv-row {
    grid-template-columns: 1fr;
  }
}
</style>
