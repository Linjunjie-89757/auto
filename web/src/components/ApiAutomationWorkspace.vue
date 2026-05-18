<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  Plus,
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

type RequestEditorTab = {
  key: string
  definitionId: number | null
  title: string
  method: string
  draft: ApiDefinitionDetail
  savedFingerprint: string
  isDirty: boolean
}

type DefinitionDirectoryTreeNode = {
  key: string
  type: 'root' | 'workspace' | 'module' | 'unassigned'
  label: string
  workspaceCode: string
  fullPath: string | null
  count: number
  children: DefinitionDirectoryTreeNode[]
}

const DEFINITION_TREE_ROOT_KEY = 'definition-root'
const DEFINITION_TREE_UNASSIGNED_KEY = 'definition-unassigned'

const { workspaceCode, isAllScope } = useWorkspace()
const { canWriteWorkspace } = useWorkspaceAccess()

const loading = ref(false)
const saving = ref(false)
const reportDrawerVisible = ref(false)
const bugDialogVisible = ref(false)
const activeTab = ref<'definitions' | 'scenarios' | 'execution' | 'reports' | 'settings'>('definitions')
const activeRequestTab = ref<'params' | 'headers' | 'body' | 'auth' | 'tests' | 'extract' | 'settings' | 'rest'>('params')
const responsePreviewTab = ref<'body' | 'headers' | 'assertions' | 'extractions' | 'history'>('body')

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
const requestEditorTabs = ref<RequestEditorTab[]>([])
const activeRequestEditorKey = ref('')
const requestEditorSyncing = ref(false)
const selectedDefinitionTreeKey = ref(DEFINITION_TREE_ROOT_KEY)

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
const canWriteDefinition = computed(() => canWriteTarget(definitionForm.workspaceCode))
const canWriteScenario = computed(() => canWriteTarget(scenarioForm.workspaceCode))
const canWriteEnvironment = computed(() => canWriteTarget(environmentForm.workspaceCode))
const canWriteVariableSet = computed(() => canWriteTarget(variableSetForm.workspaceCode))
const canCreateInCurrentScope = computed(() => (
  isAllScope.value ? writableWorkspaces.value.length > 0 : canWriteWorkspace(workspaceCode.value)
))
const currentDefinitionWorkspaceLabel = computed(() => {
  if (!definitionForm.workspaceCode) {
    return isAllScope.value ? '未选择空间' : '当前空间'
  }
  return workspaces.value.find(item => item.code === definitionForm.workspaceCode)?.name ?? definitionForm.workspaceCode
})
const currentEnvironmentName = computed(() => environments.value.find(item => item.id === runOptions.environmentId)?.name ?? '未选择环境')
const currentVariableSetName = computed(() => variableSets.value.find(item => item.id === runOptions.variableSetId)?.name ?? '未选择变量集')
const activeRequestEditorTab = computed(() => requestEditorTabs.value.find(item => item.key === activeRequestEditorKey.value) ?? null)

const scenarioDirectoryOptions = computed(() => uniqueNonEmpty(scenarios.value.map(item => item.directoryName)))
const activeOwnerOptions = computed(() => users.value.filter(item => item.status === 1))
const definitionOptions = computed(() => definitions.value.map(item => ({
  label: `${item.method} ${item.name}`,
  value: item.id,
})))

const selectedDefinitionTreeNode = computed(() => {
  const walk = (nodes: DefinitionDirectoryTreeNode[]): DefinitionDirectoryTreeNode | null => {
    for (const node of nodes) {
      if (node.key === selectedDefinitionTreeKey.value) {
        return node
      }
      const child = walk(node.children)
      if (child) {
        return child
      }
    }
    return null
  }
  return walk(definitionDirectoryTree.value)
})
const selectedDefinitionWorkspaceCode = computed(() => {
  const code = selectedDefinitionTreeNode.value?.workspaceCode || ''
  return code || defaultEditableWorkspaceCode()
})
const selectedDefinitionModulePath = computed(() => {
  return selectedDefinitionTreeNode.value?.type === 'module' ? (selectedDefinitionTreeNode.value.fullPath ?? '') : ''
})

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
  const selectedNode = selectedDefinitionTreeNode.value
  if (selectedNode?.type === 'workspace' && item.workspaceCode !== selectedNode.workspaceCode) {
    return false
  }
  if (selectedNode?.type === 'unassigned') {
    if (item.workspaceCode !== selectedNode.workspaceCode) {
      return false
    }
    return !(item.directoryName || '').trim()
  }
  if (selectedNode?.type === 'module') {
    if (item.workspaceCode !== selectedNode.workspaceCode) {
      return false
    }
    const currentPath = (item.directoryName || '').trim()
    if (!(currentPath === selectedDefinitionModulePath.value || currentPath.startsWith(`${selectedDefinitionModulePath.value}/`))) {
      return false
    }
  }
  return true
}))

const definitionDirectoryTree = computed<DefinitionDirectoryTreeNode[]>(() => {
  type MutableNode = DefinitionDirectoryTreeNode & { childMap: Map<string, MutableNode> }
  const workspaceCodes = isAllScope.value
    ? workspaces.value.map(item => item.code)
    : [workspaceCode.value]
  const workspaceNodes = workspaceCodes.map((code) => {
    const workspaceName = workspaces.value.find(item => item.code === code)?.name ?? code
    return {
      key: `definition-workspace:${code}`,
      type: 'workspace' as const,
      label: workspaceName,
      workspaceCode: code,
      fullPath: null,
      count: 0,
      children: [],
      childMap: new Map<string, MutableNode>(),
    }
  })
  const workspaceMap = new Map(workspaceNodes.map(item => [item.workspaceCode, item]))
  const ensureNode = (
    parentChildren: MutableNode[],
    parentMap: Map<string, MutableNode>,
    workspaceCode: string,
    label: string,
    fullPath: string,
  ) => {
    let node = parentMap.get(fullPath)
    if (!node) {
      node = {
        key: `definition-module:${workspaceCode}:${fullPath}`,
        type: 'module',
        label,
        workspaceCode,
        fullPath,
        count: 0,
        children: [],
        childMap: new Map(),
      }
      parentMap.set(fullPath, node)
      parentChildren.push(node)
    }
    return node
  }
  for (const item of definitions.value) {
    const workspaceNode = workspaceMap.get(item.workspaceCode)
    if (!workspaceNode) {
      continue
    }
    workspaceNode.count += 1
    const path = (item.directoryName || '').trim()
    if (!path) {
      continue
    }
    const segments = path.split('/').map(part => part.trim()).filter(Boolean)
    let currentChildren = workspaceNode.children as MutableNode[]
    let currentMap = workspaceNode.childMap
    let assembled = ''
    for (const segment of segments) {
      assembled = assembled ? `${assembled}/${segment}` : segment
      const node = ensureNode(currentChildren, currentMap, item.workspaceCode, segment, assembled)
      node.count += 1
      currentChildren = node.children as MutableNode[]
      currentMap = node.childMap
    }
  }
  const stripChildMap = (nodes: MutableNode[]): DefinitionDirectoryTreeNode[] => nodes.map(node => ({
    key: node.key,
    type: node.type,
    label: node.label,
    workspaceCode: node.workspaceCode,
    fullPath: node.fullPath,
    count: node.count,
    children: stripChildMap(node.children as MutableNode[]),
  }))
  const workspaceTrees = workspaceNodes.map((workspaceNode) => {
    const unassignedCount = definitions.value.filter(item => item.workspaceCode === workspaceNode.workspaceCode && !(item.directoryName || '').trim()).length
    const children = stripChildMap(workspaceNode.children as MutableNode[])
    if (unassignedCount) {
      children.push({
        key: `${DEFINITION_TREE_UNASSIGNED_KEY}:${workspaceNode.workspaceCode}`,
        type: 'unassigned',
        label: '未规划请求',
        workspaceCode: workspaceNode.workspaceCode,
        fullPath: null,
        count: unassignedCount,
        children: [],
      })
    }
    return {
      key: workspaceNode.key,
      type: 'workspace' as const,
      label: workspaceNode.label,
      workspaceCode: workspaceNode.workspaceCode,
      fullPath: null,
      count: workspaceNode.count,
      children,
    }
  })
  return [{
    key: DEFINITION_TREE_ROOT_KEY,
    type: 'root',
    label: '请求目录',
    workspaceCode: isAllScope.value ? 'ALL' : workspaceCode.value,
    fullPath: null,
    count: definitions.value.length,
    children: workspaceTrees,
  }]
})

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
const reportHistory = computed(() => [...apiReports.value].sort((left, right) => right.id - left.id).slice(0, 8))
const currentResponseStep = computed(() => {
  if (!reportStepResults.value.length) {
    return null
  }
  return reportStepResults.value.find(item => !item.success) ?? reportStepResults.value[reportStepResults.value.length - 1]
})
const currentResponseStatusCode = computed(() => currentResponseStep.value?.response?.statusCode ?? null)
const currentResponseDuration = computed(() => currentResponseStep.value?.durationMs ?? null)
const currentResponseSize = computed(() => {
  const body = currentResponseStep.value?.response?.body
  return body ? `${new Blob([body]).size} B` : '0 B'
})
const currentResponseBody = computed(() => currentResponseStep.value?.response?.body ?? '')
const currentResponseHeaders = computed(() => currentResponseStep.value?.response?.headers ?? {})
const currentAssertionResults = computed(() => currentResponseStep.value?.assertionResults ?? [])
const currentExtractionResults = computed(() => currentResponseStep.value?.extractionResults ?? [])
const currentDebugError = computed(() => currentResponseStep.value?.errorMessage || reportDetail.value?.failureSummary || '')
const currentAssertionSummary = computed(() => {
  const total = currentAssertionResults.value.length
  const passed = currentAssertionResults.value.filter(item => item.success).length
  return { total, passed }
})
const currentExtractionSummary = computed(() => {
  const total = currentExtractionResults.value.length
  const passed = currentExtractionResults.value.filter(item => item.success).length
  return { total, passed }
})
const queryEnabledCount = computed(() => definitionForm.requestConfig.queryParams.filter(item => item.enabled !== false).length)
const headerEnabledCount = computed(() => definitionForm.requestConfig.headers.filter(item => item.enabled !== false).length)

function isBodyMode(mode: string) {
  if (mode === 'json') return definitionForm.requestConfig.body.type === 'RAW_JSON'
  if (mode === 'raw') return definitionForm.requestConfig.body.type === 'RAW_TEXT'
  return definitionForm.requestConfig.body.type === mode
}

function setBodyMode(mode: 'NONE' | 'FORM_DATA' | 'FORM_URLENCODED' | 'RAW_JSON' | 'RAW_TEXT') {
  definitionForm.requestConfig.body.type = mode
}

watch(() => workspaceCode.value, () => {
  void bootstrap()
})

watch(definitionForm, () => {
  syncActiveRequestEditorTab()
}, { deep: true })

watch(filteredDefinitions, (items) => {
  if (activeRequestEditorTab.value?.draft.id === 0) {
    return
  }
  if (!items.length) {
    if (!requestEditorTabs.value.length) {
      openNewRequestTab()
    }
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
  openNewRequestTab()
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

function cloneDefinitionDetail(detail: ApiDefinitionDetail): ApiDefinitionDetail {
  return JSON.parse(JSON.stringify(detail)) as ApiDefinitionDetail
}

function fingerprintDefinitionDetail(detail: ApiDefinitionDetail) {
  return JSON.stringify(detail)
}

function buildEmptyDefinitionDetail(): ApiDefinitionDetail {
  return {
    id: 0,
    workspaceCode: selectedDefinitionWorkspaceCode.value,
    workspaceName: '',
    name: '',
    method: 'GET',
    path: '',
    directoryName: selectedDefinitionModulePath.value,
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
  }
}

function makeRequestEditorTab(detail?: ApiDefinitionDetail) {
  const draft = cloneDefinitionDetail(detail ?? buildEmptyDefinitionDetail())
  const savedFingerprint = fingerprintDefinitionDetail(draft)
  const key = `request-tab-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  return {
    key,
    definitionId: draft.id || null,
    title: draft.name || '新建请求',
    method: draft.requestConfig.method || draft.method || 'GET',
    draft,
    savedFingerprint,
    isDirty: false,
  } satisfies RequestEditorTab
}

function syncActiveRequestEditorTab() {
  const current = activeRequestEditorTab.value
  if (!current || requestEditorSyncing.value) {
    return
  }
  const snapshot = cloneDefinitionDetail(definitionForm)
  current.draft = snapshot
  current.definitionId = definitionForm.id || null
  current.title = definitionForm.name || '新建请求'
  current.method = definitionForm.requestConfig.method || definitionForm.method || 'GET'
  current.isDirty = current.savedFingerprint !== fingerprintDefinitionDetail(snapshot)
}

function applyDefinitionToEditor(detail: ApiDefinitionDetail, options?: { markSaved?: boolean }) {
  requestEditorSyncing.value = true
  Object.assign(definitionForm, cloneDefinitionDetail(detail))
  selectedDefinitionId.value = detail.id || null
  const current = activeRequestEditorTab.value
  if (current) {
    current.draft = cloneDefinitionDetail(detail)
    current.definitionId = detail.id || null
    current.title = detail.name || '新建请求'
    current.method = detail.requestConfig.method || detail.method || 'GET'
    if (options?.markSaved) {
      current.savedFingerprint = fingerprintDefinitionDetail(detail)
      current.isDirty = false
    }
  }
  requestEditorSyncing.value = false
}

function activateRequestEditorTab(key: string) {
  const target = requestEditorTabs.value.find(item => item.key === key)
  if (!target) {
    return
  }
  activeRequestEditorKey.value = key
  applyDefinitionToEditor(target.draft)
}

function openNewRequestTab(detail?: ApiDefinitionDetail) {
  const tab = makeRequestEditorTab(detail)
  requestEditorTabs.value.push(tab)
  activateRequestEditorTab(tab.key)
}

async function closeRequestEditorTab(key: string) {
  const closing = requestEditorTabs.value.find(item => item.key === key)
  if (!closing) {
    return
  }
  if (closing.isDirty) {
    await ElMessageBox.confirm('当前请求有未保存修改，确认关闭这个请求页签吗？', '关闭请求', { type: 'warning' })
  }
  if (requestEditorTabs.value.length <= 1) {
    const current = requestEditorTabs.value[0]
    if (current) {
      const emptyDetail = buildEmptyDefinitionDetail()
      current.draft = emptyDetail
      current.definitionId = null
      current.title = '新建请求'
      current.method = 'GET'
      current.savedFingerprint = fingerprintDefinitionDetail(emptyDetail)
      current.isDirty = false
      activateRequestEditorTab(current.key)
    }
    return
  }
  const index = requestEditorTabs.value.findIndex(item => item.key === key)
  if (index < 0) {
    return
  }
  requestEditorTabs.value.splice(index, 1)
  const fallback = requestEditorTabs.value[Math.max(0, index - 1)] ?? requestEditorTabs.value[0]
  if (fallback) {
    activateRequestEditorTab(fallback.key)
  }
}

function canWriteTarget(targetWorkspaceCode?: string | null) {
  return isAllScope.value
    ? !!targetWorkspaceCode && canWriteWorkspace(targetWorkspaceCode)
    : canWriteWorkspace(workspaceCode.value)
}

function defaultEditableWorkspaceCode() {
  return isAllScope.value ? (writableWorkspaces.value[0]?.code || '') : workspaceCode.value
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

function ensureScopedTargetWorkspace(targetWorkspaceCode?: string) {
  if (isAllScope.value && !targetWorkspaceCode) {
    throw new Error('ALL 视角下请先选择目标空间')
  }
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
    if (!selectedEnvironmentId.value || !environments.value.some(item => item.id === selectedEnvironmentId.value)) {
      selectedEnvironmentId.value = environments.value[0]?.id ?? null
      runOptions.environmentId = selectedEnvironmentId.value
    }
    if (!selectedVariableSetId.value || !variableSets.value.some(item => item.id === selectedVariableSetId.value)) {
      selectedVariableSetId.value = variableSets.value[0]?.id ?? null
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
  const loadedTab = requestEditorTabs.value.find(item => item.definitionId === id)
  if (loadedTab) {
    activateRequestEditorTab(loadedTab.key)
    return
  }
  selectedDefinitionId.value = id
  const detail = await platformApi.getApiDefinitionDetail(workspaceCode.value, id)
  openNewRequestTab(detail)
}

async function selectScenario(id: number) {
  selectedScenarioId.value = id
  const detail = await platformApi.getApiScenarioDetail(workspaceCode.value, id)
  assignScenario(detail)
}

function assignScenario(detail: ApiScenarioDetail) {
  Object.assign(scenarioForm, JSON.parse(JSON.stringify(detail)))
}

function addDefinitionRow(target: ApiKeyValue[]) {
  target.push(emptyKeyValue())
}

function handleDefinitionTreeSelect(data: DefinitionDirectoryTreeNode | null) {
  selectedDefinitionTreeKey.value = data?.key ?? DEFINITION_TREE_ROOT_KEY
}

async function createDefinitionModule(node: DefinitionDirectoryTreeNode) {
  const targetWorkspaceCode = node.type === 'root'
    ? defaultEditableWorkspaceCode()
    : node.workspaceCode
  if (!targetWorkspaceCode || !canWriteWorkspace(targetWorkspaceCode)) {
    ElMessage.warning('请先选择可写空间后再新建子模块')
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('请输入子模块名称', '新建子模块', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '子模块名称不能为空',
    })
    const moduleName = value.trim()
    const parentPath = node.type === 'module' ? (node.fullPath ?? '') : ''
    const fullPath = parentPath ? `${parentPath}/${moduleName}` : moduleName
    selectedDefinitionTreeKey.value = `definition-module:${targetWorkspaceCode}:${fullPath}`
    openNewRequestTab({
      ...buildEmptyDefinitionDetail(),
      workspaceCode: targetWorkspaceCode,
      directoryName: fullPath,
    })
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
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
  try {
    ensureScopedTargetWorkspace(definitionForm.workspaceCode)
  }
  catch (error) {
    ElMessage.warning((error as Error).message)
    return
  }
  if (!definitionForm.name.trim() || !definitionForm.requestConfig.path.trim()) {
    ElMessage.warning('请补全接口名称和路径')
    return
  }
  saving.value = true
  try {
    const payload = {
      workspaceCode: isAllScope.value ? definitionForm.workspaceCode || writableWorkspaces.value[0]?.code : workspaceCode.value,
      name: definitionForm.name,
      directoryName: (definitionForm.directoryName || selectedDefinitionModulePath.value || '').trim() || null,
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
    const latestDetail = await platformApi.getApiDefinitionDetail(workspaceCode.value, detail.id)
    applyDefinitionToEditor(latestDetail, { markSaved: true })
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
    await closeRequestEditorTab(activeRequestEditorKey.value)
    return
  }
  await ElMessageBox.confirm('删除后不可恢复，确认删除当前接口吗？', '删除接口', { type: 'warning' })
  await platformApi.deleteApiDefinition(workspaceCode.value, definitionForm.id)
  ElMessage.success('接口已删除')
  const closingKey = activeRequestEditorKey.value
  selectedDefinitionId.value = null
  await refreshData()
  await closeRequestEditorTab(closingKey)
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
    await loadReportPreview(response.reportId)
    activeTab.value = 'definitions'
    responsePreviewTab.value = 'body'
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function saveAndDebugDefinition() {
  await saveDefinition()
  if (definitionForm.id) {
    await debugDefinition()
  }
}

async function saveScenario() {
  try {
    ensureScopedTargetWorkspace(scenarioForm.workspaceCode)
  }
  catch (error) {
    ElMessage.warning((error as Error).message)
    return
  }
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
    await loadReportPreview(response.reportId)
    activeTab.value = 'scenarios'
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function saveEnvironment() {
  try {
    ensureScopedTargetWorkspace(environmentForm.workspaceCode)
  }
  catch (error) {
    ElMessage.warning((error as Error).message)
    return
  }
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
  try {
    ensureScopedTargetWorkspace(variableSetForm.workspaceCode)
  }
  catch (error) {
    ElMessage.warning((error as Error).message)
    return
  }
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

async function loadReportPreview(id: number) {
  selectedReportId.value = id
  const [detail, steps] = await Promise.all([
    platformApi.getReportDetail(workspaceCode.value, id),
    platformApi.getApiRunStepResults(workspaceCode.value, id),
  ])
  reportDetail.value = detail
  reportStepResults.value = steps
}

async function openReportDetail(id: number) {
  reportDrawerVisible.value = true
  try {
    await loadReportPreview(id)
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
  selectedEnvironmentId.value = item.id
  runOptions.environmentId = item.id
}

function selectVariableSet(item: ApiVariableSetItem) {
  Object.assign(variableSetForm, JSON.parse(JSON.stringify(item)))
  selectedVariableSetId.value = item.id
  runOptions.variableSetId = item.id
}

function fillFromDefinition(item: ApiDefinitionItem) {
  void selectDefinition(item.id)
  activeTab.value = 'definitions'
}

async function runDefinitionItem(id: number) {
  await selectDefinition(id)
  await debugDefinition()
}

function duplicateDefinition() {
  const snapshot = JSON.parse(JSON.stringify(definitionForm)) as ApiDefinitionDetail
  const duplicated = Object.assign(buildEmptyDefinitionDetail(), snapshot, {
    id: 0,
    name: snapshot.name ? `${snapshot.name} - 副本` : '',
    lastRunResult: null,
    lastRunAt: null,
    createdAt: null,
    updatedAt: null,
    workspaceName: '',
  })
  openNewRequestTab(duplicated)
}

function promptImportCurl() {
  ElMessage.info('下一步可以继续接入 Curl 导入，这版先把专业工作台形态落地')
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

function resultTagType(result?: string | null) {
  switch (result) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    default:
      return 'info'
  }
}

function formatTimeLabel(value?: string | null) {
  if (!value) {
    return '未运行'
  }
  return value.replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <section v-loading="loading" class="api-automation-page">
    <el-tabs v-model="activeTab" class="api-tabs">
      <el-tab-pane label="接口" name="definitions">
        <div class="ms-like-layout shell-card">
          <aside class="ms-like-sidebar">
            <div class="ms-like-sidebar-tools">
              <el-input v-model="definitionFilters.keyword" placeholder="请输入模块请求名称" clearable />
              <el-button v-if="canCreateInCurrentScope" type="primary" @click="openNewRequestTab()">新建请求</el-button>
            </div>
            <div class="ms-like-tree-toolbar">
              <div class="ms-like-tree-title">全部请求 ({{ filteredDefinitions.length }})</div>
              <el-button circle size="small" @click="openNewRequestTab()">
                <el-icon><Plus /></el-icon>
              </el-button>
            </div>
            <div class="ms-like-directory-shell">
              <el-tree
                :data="definitionDirectoryTree"
                node-key="key"
                highlight-current
                default-expand-all
                :expand-on-click-node="false"
                :current-node-key="selectedDefinitionTreeKey"
                class="ms-like-directory-tree"
                @current-change="handleDefinitionTreeSelect"
              >
                <template #default="{ data }">
                  <div class="ms-like-directory-node">
                    <div class="ms-like-directory-main">
                      <span class="ms-like-directory-label">{{ data.label }}</span>
                      <span class="ms-like-directory-count">{{ data.count }}</span>
                    </div>
                    <div class="ms-like-directory-actions" @click.stop>
                      <el-button
                        v-if="(data.type === 'workspace' || data.type === 'module' || (!isAllScope && data.type === 'root')) && canWriteWorkspace(data.type === 'root' ? workspaceCode : data.workspaceCode)"
                        text
                        class="tree-icon-button"
                        @click.stop="createDefinitionModule(data)"
                      >
                        <el-icon><Plus /></el-icon>
                      </el-button>
                    </div>
                  </div>
                </template>
              </el-tree>
            </div>
            <div class="ms-like-tree request-list">
              <button
                v-for="item in filteredDefinitions"
                :key="item.id"
                type="button"
                :class="['ms-like-tree-item', { active: item.id === selectedDefinitionId }]"
                @click="selectDefinition(item.id)"
              >
                <span :class="['ms-like-method', `method-${item.method.toLowerCase()}`]">{{ item.method }}</span>
                <span class="ms-like-tree-item-name">{{ item.name }}</span>
              </button>
            </div>
          </aside>

          <section class="ms-like-main">
            <div class="ms-like-tab-strip">
              <button
                v-for="item in requestEditorTabs"
                :key="item.key"
                type="button"
                :class="['ms-like-editor-tab', { active: item.key === activeRequestEditorKey }]"
                @click="activateRequestEditorTab(item.key)"
              >
                <span :class="['ms-like-method', `method-${item.method.toLowerCase()}`]">
                  {{ item.method }}
                </span>
                <span class="ms-like-editor-tab-label">{{ item.title }}</span>
                <span v-if="item.isDirty" class="ms-like-editor-tab-dot"></span>
                <span v-if="requestEditorTabs.length > 1" class="ms-like-editor-tab-close" @click.stop="closeRequestEditorTab(item.key)">×</span>
              </button>
              <button type="button" class="ms-like-tab-add" @click="openNewRequestTab()">+</button>
            </div>

            <div class="ms-like-editor-shell">
              <div v-if="isAllScope && !definitionForm.workspaceCode" class="scope-hint">
                当前处于 ALL 视角，请先在顶部选择目标空间后再保存或调试。
              </div>

              <div class="ms-like-request-row">
                <el-select v-model="definitionForm.requestConfig.method" class="request-method-select">
                  <el-option label="GET" value="GET" />
                  <el-option label="POST" value="POST" />
                  <el-option label="PUT" value="PUT" />
                  <el-option label="DELETE" value="DELETE" />
                  <el-option label="PATCH" value="PATCH" />
                </el-select>
                <el-input v-model="definitionForm.requestConfig.path" class="request-url-input" placeholder="请输入包含 http/https 的完整 URL 或接口路径" />
                <el-button @click="promptImportCurl">Curl</el-button>
                <el-button type="primary" :disabled="!definitionForm.id || !canWriteDefinition" :loading="saving" @click="debugDefinition">
                  服务端执行
                </el-button>
                <el-dropdown split-button :disabled="!canWriteDefinition" :loading="saving" @click="saveDefinition">
                  保存
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="saveAndDebugDefinition">保存并执行</el-dropdown-item>
                      <el-dropdown-item @click="duplicateDefinition">复制接口</el-dropdown-item>
                      <el-dropdown-item :disabled="!definitionForm.id" @click="removeDefinition">删除接口</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>

              <div class="ms-like-top-tabs">
                <button :class="['ms-like-top-tab', { active: activeRequestTab === 'headers' }]" @click="activeRequestTab = 'headers'">请求头</button>
                <button :class="['ms-like-top-tab', { active: activeRequestTab === 'body' }]" @click="activeRequestTab = 'body'">请求体</button>
                <button :class="['ms-like-top-tab', { active: activeRequestTab === 'params' }]" @click="activeRequestTab = 'params'">
                  Query
                  <span v-if="queryEnabledCount" class="ms-like-tab-badge">{{ queryEnabledCount }}</span>
                </button>
                <button :class="['ms-like-top-tab', { active: activeRequestTab === 'rest' }]" @click="activeRequestTab = 'rest'">REST</button>
                <button :class="['ms-like-top-tab', { active: activeRequestTab === 'tests' }]" @click="activeRequestTab = 'tests'">前置</button>
                <button :class="['ms-like-top-tab', { active: activeRequestTab === 'extract' }]" @click="activeRequestTab = 'extract'">后置</button>
                <button :class="['ms-like-top-tab', { active: activeRequestTab === 'auth' }]" @click="activeRequestTab = 'auth'">认证</button>
                <button :class="['ms-like-top-tab', { active: activeRequestTab === 'settings' }]" @click="activeRequestTab = 'settings'">设置</button>
              </div>

              <div class="ms-like-request-body">
                <template v-if="activeRequestTab === 'params'">
                  <div class="request-section ms-like-table-surface">
                    <div class="ms-like-table-header">
                      <label class="ms-like-check-cell">
                        <el-checkbox :model-value="queryEnabledCount > 0" />
                        <span>参数名称</span>
                      </label>
                      <span>参数值</span>
                      <span>描述</span>
                      <button type="button" class="ms-like-link-button" @click="addDefinitionRow(definitionForm.requestConfig.queryParams)">批量添加</button>
                    </div>
                    <div v-for="(row, index) in definitionForm.requestConfig.queryParams" :key="`query-${index}`" class="ms-like-table-row">
                      <div class="ms-like-drag-cell">⋮</div>
                      <div class="ms-like-checkbox-field">
                        <el-checkbox v-model="row.enabled" />
                        <el-input v-model="row.key" placeholder="参数名称" />
                      </div>
                      <el-input v-model="row.value" placeholder="参数值 / {{variable}}" />
                      <el-input placeholder="描述" />
                      <button type="button" class="ms-like-row-remove" @click="definitionForm.requestConfig.queryParams.splice(index, 1)">删除</button>
                    </div>
                    <button type="button" class="ms-like-add-row" @click="addDefinitionRow(definitionForm.requestConfig.queryParams)">+ 添加一行</button>
                  </div>
                </template>

                <template v-else-if="activeRequestTab === 'headers'">
                  <div class="request-section ms-like-table-surface">
                    <div class="ms-like-table-header">
                      <label class="ms-like-check-cell">
                        <el-checkbox :model-value="headerEnabledCount > 0" />
                        <span>参数名称</span>
                      </label>
                      <span>参数值</span>
                      <span>描述</span>
                      <button type="button" class="ms-like-link-button" @click="addDefinitionRow(definitionForm.requestConfig.headers)">批量添加</button>
                    </div>
                    <div v-for="(row, index) in definitionForm.requestConfig.headers" :key="`header-${index}`" class="ms-like-table-row">
                      <div class="ms-like-drag-cell">⋮</div>
                      <div class="ms-like-checkbox-field">
                        <el-checkbox v-model="row.enabled" />
                        <el-input v-model="row.key" placeholder="参数名称" />
                      </div>
                      <el-input v-model="row.value" placeholder="参数值 / {{variable}}" />
                      <el-input placeholder="描述" />
                      <button type="button" class="ms-like-row-remove" @click="definitionForm.requestConfig.headers.splice(index, 1)">删除</button>
                    </div>
                    <button type="button" class="ms-like-add-row" @click="addDefinitionRow(definitionForm.requestConfig.headers)">+ 添加一行</button>
                  </div>
                </template>

                <template v-else-if="activeRequestTab === 'body'">
                  <div class="request-section">
                    <div class="ms-like-body-type-row">
                      <button :class="['ms-like-body-chip', { active: isBodyMode('NONE') }]" @click="setBodyMode('NONE')">none</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('FORM_DATA') }]" @click="setBodyMode('FORM_DATA')">form-data</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('FORM_URLENCODED') }]" @click="setBodyMode('FORM_URLENCODED')">x-www-form-urlencoded</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('RAW_JSON') }]" @click="setBodyMode('RAW_JSON')">json</button>
                      <button class="ms-like-body-chip ghost" type="button">xml</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('RAW_TEXT') }]" @click="setBodyMode('RAW_TEXT')">raw</button>
                      <button class="ms-like-body-chip ghost" type="button">binary</button>
                    </div>
                    <el-input
                      v-if="['RAW_JSON', 'RAW_TEXT'].includes(definitionForm.requestConfig.body.type)"
                      v-model="definitionForm.requestConfig.body.rawText"
                      type="textarea"
                      :rows="16"
                      class="code-textarea ms-like-code-editor"
                      placeholder="支持 {{variable}} 占位符"
                    />
                    <div
                      v-else-if="['FORM_URLENCODED', 'FORM_DATA'].includes(definitionForm.requestConfig.body.type)"
                      class="body-form-grid ms-like-table-surface"
                    >
                      <div class="ms-like-table-header">
                        <label class="ms-like-check-cell">
                          <el-checkbox model-value />
                          <span>参数名称</span>
                        </label>
                        <span>参数值</span>
                        <span>描述</span>
                        <button type="button" class="ms-like-link-button" @click="definitionForm.requestConfig.body.formItems.push(emptyKeyValue())">批量添加</button>
                      </div>
                      <div v-for="(row, index) in definitionForm.requestConfig.body.formItems" :key="`body-${index}`" class="ms-like-table-row">
                        <div class="ms-like-drag-cell">⋮</div>
                        <div class="ms-like-checkbox-field">
                          <el-checkbox v-model="row.enabled" />
                          <el-input v-model="row.key" placeholder="参数名称" />
                        </div>
                        <el-input v-model="row.value" placeholder="参数值" />
                        <el-input placeholder="描述" />
                        <button type="button" class="ms-like-row-remove" @click="definitionForm.requestConfig.body.formItems.splice(index, 1)">删除</button>
                      </div>
                      <button type="button" class="ms-like-add-row" @click="definitionForm.requestConfig.body.formItems.push(emptyKeyValue())">+ 添加一行</button>
                    </div>
                    <div v-else class="ms-like-empty-body">请求没有 Body</div>
                  </div>
                </template>

                <template v-else-if="activeRequestTab === 'rest'">
                  <div class="request-section ms-like-table-surface">
                    <div class="ms-like-table-header">
                      <label class="ms-like-check-cell">
                        <el-checkbox model-value />
                        <span>参数名称</span>
                      </label>
                      <span>参数值</span>
                      <span>描述</span>
                      <button type="button" class="ms-like-link-button" @click="addDefinitionRow(definitionForm.requestConfig.cookies)">批量添加</button>
                    </div>
                    <div v-for="(row, index) in definitionForm.requestConfig.cookies" :key="`cookie-${index}`" class="ms-like-table-row">
                      <div class="ms-like-drag-cell">⋮</div>
                      <div class="ms-like-checkbox-field">
                        <el-checkbox v-model="row.enabled" />
                        <el-input v-model="row.key" placeholder="参数名称" />
                      </div>
                      <el-input v-model="row.value" placeholder="参数值" />
                      <el-input placeholder="描述" />
                      <button type="button" class="ms-like-row-remove" @click="definitionForm.requestConfig.cookies.splice(index, 1)">删除</button>
                    </div>
                    <button type="button" class="ms-like-add-row" @click="addDefinitionRow(definitionForm.requestConfig.cookies)">+ 添加一行</button>
                  </div>
                </template>

                <template v-else-if="activeRequestTab === 'extract'">
                  <div class="request-section ms-like-table-surface">
                    <div class="ms-like-table-header">
                      <label class="ms-like-check-cell">
                        <el-checkbox model-value />
                        <span>参数名称</span>
                      </label>
                      <span>参数值</span>
                      <span>描述</span>
                      <button type="button" class="ms-like-link-button" @click="addExtractor">批量添加</button>
                    </div>
                    <div v-for="(item, index) in definitionForm.extractors" :key="`extract-${index}`" class="ms-like-table-row">
                      <div class="ms-like-drag-cell">⋮</div>
                      <div class="ms-like-checkbox-field">
                        <el-checkbox model-value />
                        <el-input v-model="item.name" placeholder="参数名称" />
                      </div>
                      <el-input v-model="item.expression" placeholder="JSONPath / Header" />
                      <el-select v-model="item.sourceType">
                        <el-option label="BODY_JSONPATH" value="BODY_JSONPATH" />
                        <el-option label="HEADER" value="HEADER" />
                      </el-select>
                      <button type="button" class="ms-like-row-remove" @click="definitionForm.extractors.splice(index, 1)">删除</button>
                    </div>
                    <button type="button" class="ms-like-add-row" @click="addExtractor">+ 添加一行</button>
                  </div>
                </template>

                <template v-else-if="activeRequestTab === 'tests'">
                  <div class="request-section ms-like-table-surface">
                    <div class="ms-like-table-header">
                      <label class="ms-like-check-cell">
                        <el-checkbox model-value />
                        <span>参数名称</span>
                      </label>
                      <span>参数值</span>
                      <span>描述</span>
                      <button type="button" class="ms-like-link-button" @click="addAssertion">批量添加</button>
                    </div>
                    <div v-for="(item, index) in definitionForm.assertions" :key="`assert-${index}`" class="ms-like-table-row">
                      <div class="ms-like-drag-cell">⋮</div>
                      <div class="ms-like-checkbox-field">
                        <el-checkbox model-value />
                        <el-select v-model="item.type">
                          <el-option label="STATUS_CODE" value="STATUS_CODE" />
                          <el-option label="HEADER_EQUALS" value="HEADER_EQUALS" />
                          <el-option label="HEADER_CONTAINS" value="HEADER_CONTAINS" />
                          <el-option label="BODY_JSONPATH_EQUALS" value="BODY_JSONPATH_EQUALS" />
                          <el-option label="BODY_JSONPATH_CONTAINS" value="BODY_JSONPATH_CONTAINS" />
                          <el-option label="RESPONSE_TIME_LE" value="RESPONSE_TIME_LE" />
                        </el-select>
                      </div>
                      <el-input v-model="item.subject" placeholder="subject / 表达式" />
                      <el-input v-model="item.expectedValue" placeholder="期望值" />
                      <button type="button" class="ms-like-row-remove" @click="definitionForm.assertions.splice(index, 1)">删除</button>
                    </div>
                    <button type="button" class="ms-like-add-row" @click="addAssertion">+ 添加一行</button>
                  </div>
                </template>

                <template v-else-if="activeRequestTab === 'auth'">
                  <div class="request-section ms-like-form-panel">
                    <div class="ms-like-form-row">
                      <div class="ms-like-form-label">认证类型</div>
                      <el-select v-model="definitionForm.requestConfig.authConfig.type" class="ms-like-form-control">
                        <el-option label="INHERIT" value="INHERIT" />
                        <el-option label="NONE" value="NONE" />
                        <el-option label="BEARER" value="BEARER" />
                        <el-option label="BASIC" value="BASIC" />
                      </el-select>
                    </div>
                    <div v-if="definitionForm.requestConfig.authConfig.type === 'BEARER'" class="ms-like-form-row">
                      <div class="ms-like-form-label">Token</div>
                      <el-input v-model="definitionForm.requestConfig.authConfig.token" class="ms-like-form-control" placeholder="Bearer Token" />
                    </div>
                    <template v-else-if="definitionForm.requestConfig.authConfig.type === 'BASIC'">
                      <div class="ms-like-form-row">
                        <div class="ms-like-form-label">Username</div>
                        <el-input v-model="definitionForm.requestConfig.authConfig.username" class="ms-like-form-control" placeholder="username" />
                      </div>
                      <div class="ms-like-form-row">
                        <div class="ms-like-form-label">Password</div>
                        <el-input v-model="definitionForm.requestConfig.authConfig.password" class="ms-like-form-control" placeholder="password" show-password />
                      </div>
                    </template>
                    <div v-else class="empty-hint">认证将继承环境配置，或按 NONE 发送匿名请求。</div>
                  </div>
                </template>

                <template v-else>
                  <div class="request-section ms-like-form-panel">
                    <div class="ms-like-form-row">
                      <div class="ms-like-form-label">接口名称</div>
                      <el-input v-model="definitionForm.name" class="ms-like-form-control" placeholder="接口名称" />
                    </div>
                    <div class="ms-like-form-row">
                      <div class="ms-like-form-label">模块 / 目录</div>
                      <el-input v-model="definitionForm.directoryName" class="ms-like-form-control" placeholder="模块 / 目录" />
                    </div>
                    <div class="ms-like-form-row">
                      <div class="ms-like-form-label">标签</div>
                      <el-input
                        :model-value="readTagInput(definitionForm.tags)"
                        class="ms-like-form-control"
                        placeholder="标签，逗号分隔"
                        @update:model-value="(value: string | number) => updateTagInput(definitionForm, String(value))"
                      />
                    </div>
                    <div class="ms-like-form-row">
                      <div class="ms-like-form-label">超时时间</div>
                      <el-input-number v-model="definitionForm.requestConfig.timeoutMs" :min="1000" :step="1000" class="ms-like-form-control full-width" />
                    </div>
                    <div class="ms-like-form-row align-start">
                      <div class="ms-like-form-label">描述</div>
                      <el-input v-model="definitionForm.description" class="ms-like-form-control" type="textarea" :rows="4" placeholder="接口描述、调用约束或备注" />
                    </div>
                    <div class="ms-like-settings-hint">
                      <span>写入空间 {{ currentDefinitionWorkspaceLabel }}</span>
                      <span>调试上下文 {{ currentEnvironmentName }} / {{ currentVariableSetName }}</span>
                      <span>最后运行 {{ formatTimeLabel(definitionForm.lastRunAt) }}</span>
                    </div>
                  </div>
                </template>

              </div>
            </div>

            <div class="ms-like-response-shell">
              <div class="ms-like-response-header">
                <div class="ms-like-response-title">响应内容</div>
                <div class="ms-like-response-metrics">
                  <span>状态 {{ currentResponseStatusCode ?? '-' }}</span>
                  <span>耗时 {{ currentResponseDuration ?? '-' }}<template v-if="currentResponseDuration !== null"> ms</template></span>
                  <span>大小 {{ currentResponseSize }}</span>
                </div>
                <el-button v-if="selectedReportId" text type="primary" @click="openReportDetail(selectedReportId)">完整报告</el-button>
              </div>

              <div v-if="currentDebugError" class="response-error-banner">
                {{ currentDebugError }}
              </div>

              <div class="ms-like-response-tabs">
                <button :class="['ms-like-top-tab', { active: responsePreviewTab === 'body' }]" @click="responsePreviewTab = 'body'">Body</button>
                <button :class="['ms-like-top-tab', { active: responsePreviewTab === 'headers' }]" @click="responsePreviewTab = 'headers'">Headers</button>
                <button :class="['ms-like-top-tab', { active: responsePreviewTab === 'assertions' }]" @click="responsePreviewTab = 'assertions'">Assertions</button>
                <button :class="['ms-like-top-tab', { active: responsePreviewTab === 'extractions' }]" @click="responsePreviewTab = 'extractions'">Extract</button>
                <button :class="['ms-like-top-tab', { active: responsePreviewTab === 'history' }]" @click="responsePreviewTab = 'history'">History</button>
              </div>

              <div class="ms-like-response-body">
                <pre v-if="responsePreviewTab === 'body'">{{ currentResponseBody || '点击“服务端执行”获取响应内容' }}</pre>
                <pre v-else-if="responsePreviewTab === 'headers'">{{ JSON.stringify(currentResponseHeaders, null, 2) }}</pre>
                <div v-else-if="responsePreviewTab === 'assertions'" class="result-list">
                  <div class="result-summary">通过 {{ currentAssertionSummary.passed }} / {{ currentAssertionSummary.total }}</div>
                  <div v-for="(item, index) in currentAssertionResults" :key="`assertion-preview-${index}`" class="result-item">
                    <el-tag size="small" :type="item.success ? 'success' : 'danger'">{{ item.success ? 'PASS' : 'FAIL' }}</el-tag>
                    <div>
                      <div class="result-title">{{ item.type }} · {{ item.subject || '默认项' }}</div>
                      <div class="result-meta">{{ item.message }}</div>
                    </div>
                  </div>
                </div>
                <div v-else-if="responsePreviewTab === 'extractions'" class="result-list">
                  <div class="result-summary">成功提取 {{ currentExtractionSummary.passed }} / {{ currentExtractionSummary.total }}</div>
                  <div v-for="(item, index) in currentExtractionResults" :key="`extract-preview-${index}`" class="result-item">
                    <el-tag size="small" :type="item.success ? 'success' : 'danger'">{{ item.success ? 'OK' : 'FAIL' }}</el-tag>
                    <div>
                      <div class="result-title">{{ item.name }}</div>
                      <div class="result-meta">{{ item.value || item.message }}</div>
                    </div>
                  </div>
                </div>
                <div v-else class="result-list">
                  <div class="result-summary">最近 8 条 API 报告</div>
                  <button
                    v-for="item in reportHistory"
                    :key="`history-${item.id}`"
                    type="button"
                    class="history-row"
                    @click="loadReportPreview(item.id)"
                  >
                    <div>
                      <div class="result-title">{{ item.reportName }}</div>
                      <div class="result-meta">{{ item.failureSummary || item.workspaceName }}</div>
                    </div>
                    <el-tag size="small" :type="resultTagType(item.result)">{{ item.result }}</el-tag>
                  </button>
                </div>
              </div>
            </div>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="场景" name="scenarios">
        <div class="workspace-grid">
          <section class="workspace-sidebar shell-card">
            <div class="toolbar-row">
              <el-input v-model="scenarioFilters.keyword" placeholder="搜索场景名称" clearable />
              <el-button v-if="canCreateInCurrentScope" type="primary" @click="resetScenarioForm">
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
                  <el-tag size="small" :type="resultTagType(item.lastRunResult)">{{ item.lastRunResult || '未运行' }}</el-tag>
                </div>
                <div class="asset-item-title">{{ item.name }}</div>
                <div class="asset-item-meta">{{ item.directoryName || '未分组' }}</div>
              </button>
            </div>
          </section>

          <section class="workspace-main shell-card">
            <div class="editor-header">
              <div>
                <div class="editor-title">{{ scenarioForm.id ? '场景详情' : '新建场景' }}</div>
                <div class="editor-subtitle">用顺序步骤编排接口链路，可选择默认环境与变量集。</div>
              </div>
              <div class="editor-actions">
                <el-button :disabled="!canWriteScenario" :loading="saving" @click="saveScenario">保存</el-button>
                <el-button type="primary" :disabled="!scenarioForm.id || !canWriteScenario" :loading="saving" @click="runScenario">立即执行</el-button>
                <el-button :disabled="!scenarioForm.id || !canWriteScenario" @click="removeScenario">删除</el-button>
              </div>
            </div>

            <div class="editor-form">
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
                  <span>步骤编排</span>
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
          <section class="panel shell-card">
            <div class="panel-header">
              <div class="panel-title">可运行接口</div>
              <div class="panel-subtitle">用当前环境和变量集直接调试接口。</div>
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

          <section class="panel shell-card">
            <div class="panel-header">
              <div class="panel-title">最近执行任务</div>
              <div class="panel-subtitle">API 自动化任务继续复用原有任务中心。</div>
            </div>
            <el-table :data="apiTasks" size="small">
              <el-table-column prop="taskName" label="任务名称" min-width="220" />
              <el-table-column prop="status" label="状态" width="120" />
              <el-table-column prop="summary" label="摘要" min-width="220" />
              <el-table-column prop="workspaceName" label="空间" width="160" />
            </el-table>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="报告" name="reports">
        <section class="panel shell-card table-panel">
          <div class="panel-header">
            <div class="panel-title">执行报告</div>
            <div class="panel-subtitle">保留原有报告体系，同时增加步骤级请求 / 响应 / 断言明细。</div>
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
          <section class="panel shell-card">
            <div class="panel-header">
              <div class="panel-title">执行环境</div>
              <div class="panel-subtitle">集中维护 Base URL、公共 Header 与鉴权方案。</div>
            </div>
            <div class="asset-list compact">
              <button v-for="item in environments" :key="item.id" type="button" class="asset-item" @click="selectEnvironment(item)">
                <div class="asset-item-title">{{ item.name }}</div>
                <div class="asset-item-meta">{{ item.baseUrl }}</div>
              </button>
            </div>
            <div class="editor-section">
              <div class="section-title">环境编辑</div>
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
              <el-input v-if="environmentForm.authConfig.type === 'BEARER'" v-model="environmentForm.authConfig.token" placeholder="Bearer Token" />
              <div v-else-if="environmentForm.authConfig.type === 'BASIC'" class="form-grid">
                <el-input v-model="environmentForm.authConfig.username" placeholder="username" />
                <el-input v-model="environmentForm.authConfig.password" placeholder="password" show-password />
              </div>
              <div class="editor-actions left">
                <el-button :loading="saving" :disabled="!canWriteEnvironment" @click="saveEnvironment">保存环境</el-button>
                <el-button :disabled="!environmentForm.id || !canWriteEnvironment" @click="removeEnvironment(environmentForm.id)">删除</el-button>
                <el-button @click="resetEnvironmentForm">重置</el-button>
              </div>
            </div>
          </section>

          <section class="panel shell-card">
            <div class="panel-header">
              <div class="panel-title">变量集</div>
              <div class="panel-subtitle">变量值支持通过 <code v-pre>{{ variable }}</code> 注入请求和鉴权。</div>
            </div>
            <div class="asset-list compact">
              <button v-for="item in variableSets" :key="item.id" type="button" class="asset-item" @click="selectVariableSet(item)">
                <div class="asset-item-title">{{ item.name }}</div>
                <div class="asset-item-meta">{{ item.variables.length }} 个变量</div>
              </button>
            </div>
            <div class="editor-section">
              <div class="section-title">变量集编辑</div>
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
                <el-button :loading="saving" :disabled="!canWriteVariableSet" @click="saveVariableSet">保存变量集</el-button>
                <el-button :disabled="!variableSetForm.id || !canWriteVariableSet" @click="removeVariableSet(variableSetForm.id)">删除</el-button>
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

.shell-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-bg-color);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.page-header,
.browser-toolbar,
.editor-header,
.editor-actions,
.section-title,
.section-subtitle,
.panel-header,
.drawer-actions,
.execution-item,
.execution-actions,
.response-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.page-header {
  padding: 18px 20px;
}

.page-heading h2 {
  margin: 0 0 4px;
  font-size: 22px;
}

.page-heading p,
.editor-subtitle,
.panel-subtitle,
.asset-item-meta,
.result-meta,
.step-meta,
.browser-summary,
.scope-hint {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.page-actions,
.form-grid,
.settings-grid,
.execution-grid,
.snapshot-grid,
.detail-grid,
.request-meta-grid,
.response-summary-grid,
.settings-inline-grid {
  display: grid;
  gap: 12px;
}

.page-actions {
  grid-auto-flow: column;
  align-items: center;
}

.header-select {
  width: 180px;
}

.header-select.wide {
  width: 220px;
}

.ms-like-layout,
.workspace-sidebar,
.workspace-main,
.panel {
  overflow: hidden;
}

.ms-like-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  min-height: 820px;
}

.ms-like-sidebar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-right: 1px solid var(--el-border-color-light);
  background: #fff;
  padding: 12px;
}

.ms-like-sidebar-tools,
.ms-like-tree-toolbar,
.ms-like-request-row,
.ms-like-response-header,
.ms-like-response-metrics {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ms-like-sidebar-tools {
  justify-content: space-between;
}

.ms-like-sidebar-tools .el-input {
  flex: 1;
}

.ms-like-tree-toolbar,
.ms-like-response-header {
  justify-content: space-between;
}

.ms-like-tree-title,
.ms-like-response-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.ms-like-tree {
  display: flex;
  flex-direction: column;
  gap: 10px;
  overflow: auto;
  min-height: 0;
  padding-right: 4px;
}

.ms-like-directory-shell {
  min-height: 180px;
  max-height: 280px;
  overflow: auto;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 8px;
  background: #fff;
}

.ms-like-directory-tree :deep(.el-tree-node__content) {
  height: 32px;
  border-radius: 6px;
}

.ms-like-directory-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: #f3e8ff;
}

.ms-like-directory-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
}

.ms-like-directory-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.ms-like-directory-actions {
  flex: 0 0 auto;
}

.ms-like-directory-label,
.ms-like-directory-count {
  font-size: 13px;
}

.ms-like-directory-label {
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ms-like-directory-count {
  color: var(--el-text-color-secondary);
}

.tree-icon-button {
  padding: 4px;
}

.request-list {
  border-top: 1px solid #f2f3f5;
  padding-top: 10px;
}

.ms-like-tree-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: 0;
  background: transparent;
  border-radius: 6px;
  padding: 8px 10px;
  cursor: pointer;
  text-align: left;
}

.ms-like-tree-item:hover {
  background: var(--el-fill-color-light);
}

.ms-like-tree-item.active {
  background: #f3e8ff;
}

.ms-like-tree-item-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ms-like-method {
  font-size: 12px;
  font-weight: 600;
}

.ms-like-method.method-get {
  color: #16a34a;
}

.ms-like-method.method-post {
  color: #2563eb;
}

.ms-like-method.method-put,
.ms-like-method.method-patch {
  color: #d97706;
}

.ms-like-method.method-delete {
  color: #dc2626;
}

.ms-like-main {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) 290px;
  min-width: 0;
  background: #fff;
}

.ms-like-tab-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--el-border-color-light);
  padding: 10px 16px 0;
}

.ms-like-editor-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid #e9d5ff;
  border-bottom: 0;
  border-radius: 8px 8px 0 0;
  background: #f5ecff;
  color: var(--el-text-color-primary);
  padding: 10px 14px;
  font-size: 13px;
}

.ms-like-editor-tab.active {
  background: #f3e8ff;
}

.ms-like-editor-tab-label {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ms-like-editor-tab-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #7c3aed;
  flex: 0 0 auto;
}

.ms-like-editor-tab-close {
  color: #9ca3af;
  font-size: 14px;
  line-height: 1;
}

.ms-like-tab-add {
  border: 0;
  background: transparent;
  color: var(--el-text-color-secondary);
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}

.ms-like-editor-shell {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
  padding: 12px 16px 16px;
}

.ms-like-request-row {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr) auto auto auto;
  align-items: center;
}

.ms-like-top-tabs,
.ms-like-response-tabs {
  display: flex;
  align-items: center;
  gap: 22px;
  overflow: auto;
  border-bottom: 1px solid var(--el-border-color-light);
}

.ms-like-top-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: 14px;
  padding: 10px 0 12px;
  cursor: pointer;
  white-space: nowrap;
}

.ms-like-top-tab.active {
  color: #7c3aed;
  font-weight: 600;
}

.ms-like-top-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: #7c3aed;
}

.ms-like-tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  border-radius: 999px;
  background: #d4d4d8;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  padding: 0 5px;
}

.ms-like-meta-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.ms-like-request-body {
  min-height: 0;
  overflow: auto;
  background: #fff;
  padding: 8px 0 0;
}

.ms-like-body-type-row {
  display: flex;
  align-items: center;
  gap: 0;
  margin-bottom: 8px;
}

.ms-like-body-chip {
  border: 1px solid #e5e7eb;
  border-right-width: 0;
  background: #f5f3ff;
  color: #6b7280;
  font-size: 12px;
  line-height: 1;
  padding: 7px 12px;
  cursor: pointer;
}

.ms-like-body-chip:first-child {
  border-top-left-radius: 6px;
  border-bottom-left-radius: 6px;
}

.ms-like-body-chip:last-child {
  border-right-width: 1px;
  border-top-right-radius: 6px;
  border-bottom-right-radius: 6px;
}

.ms-like-body-chip.active {
  background: #ffffff;
  color: #7c3aed;
}

.ms-like-body-chip.ghost {
  background: #fafafa;
  color: #9ca3af;
}

.ms-like-empty-body {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  border-radius: 8px;
  background: #faf8ff;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.ms-like-table-surface {
  gap: 0;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fff;
}

.ms-like-table-header,
.ms-like-table-row {
  display: grid;
  grid-template-columns: 44px minmax(0, 1.25fr) minmax(0, 1.2fr) minmax(0, 1fr) 88px;
  align-items: center;
}

.ms-like-table-header {
  min-height: 32px;
  border-bottom: 1px solid #ebeef5;
  color: #606266;
  font-size: 13px;
  padding-right: 10px;
}

.ms-like-check-cell,
.ms-like-checkbox-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ms-like-check-cell {
  grid-column: 1 / 3;
  padding-left: 10px;
}

.ms-like-table-row {
  min-height: 32px;
  border-bottom: 1px solid #f2f3f5;
  padding: 4px 10px 4px 0;
}

.ms-like-table-row:last-of-type {
  border-bottom: 0;
}

.ms-like-drag-cell {
  color: #c0c4cc;
  text-align: center;
  font-size: 14px;
  user-select: none;
}

.ms-like-checkbox-field {
  min-width: 0;
}

.ms-like-checkbox-field :deep(.el-input),
.ms-like-table-row :deep(.el-input),
.ms-like-table-row :deep(.el-select) {
  width: 100%;
}

.ms-like-link-button,
.ms-like-row-remove,
.ms-like-add-row {
  border: 0;
  background: transparent;
  color: #7c3aed;
  cursor: pointer;
  font-size: 12px;
  padding: 0;
}

.ms-like-row-remove {
  justify-self: end;
}

.ms-like-add-row {
  align-self: flex-start;
  padding: 8px 10px 10px;
}

.ms-like-form-panel {
  gap: 0;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fff;
}

.ms-like-form-row {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  border-bottom: 1px solid #f2f3f5;
  padding: 12px 16px;
}

.ms-like-form-row:last-of-type {
  border-bottom: 0;
}

.ms-like-form-row.align-start {
  align-items: start;
}

.ms-like-form-label {
  color: #606266;
  font-size: 13px;
}

.ms-like-form-control {
  width: 100%;
}

.ms-like-settings-hint {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #909399;
  font-size: 12px;
  padding: 12px 16px;
}

.ms-like-code-editor :deep(textarea) {
  border-radius: 4px;
  background: #fbfbfd;
}

.ms-like-response-shell {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  border-top: 1px solid var(--el-border-color-light);
  background: #fff;
  padding: 10px 16px 16px;
}

.ms-like-response-metrics {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.ms-like-response-body {
  min-height: 0;
  overflow: auto;
}

.workspace-sidebar,
.panel {
  padding: 16px;
}

.workspace-main {
  padding: 18px;
}

.workspace-grid {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
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
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.asset-item:hover,
.history-row:hover {
  border-color: var(--el-color-primary-light-5);
}

.asset-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  box-shadow: inset 0 0 0 1px var(--el-color-primary-light-5);
}

.asset-item-top,
.asset-item-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.asset-item-title,
.editor-title,
.panel-title,
.execution-name,
.step-title,
.result-title {
  font-weight: 600;
}

.asset-item-title {
  margin: 10px 0 4px;
}

.asset-item-foot {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.editor-form,
.step-results,
.result-list,
.editor-section,
.request-section,
.report-drawer {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.scope-hint {
  padding: 10px 12px;
  border: 1px dashed var(--el-color-warning-light-5);
  border-radius: 8px;
  background: var(--el-color-warning-light-9);
}

.request-method-select,
.request-url-input {
  width: 100%;
}

.form-grid,
.settings-grid,
.execution-grid,
.settings-inline-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.kv-row,
.assert-row,
.scenario-step-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  align-items: center;
}

.assert-row {
  grid-template-columns: 190px 1fr 1fr auto;
}

.scenario-step-row {
  grid-template-columns: 44px 1fr 1fr auto auto auto auto;
}

.step-index {
  text-align: center;
  color: var(--el-text-color-secondary);
}

.code-textarea :deep(textarea) {
  font-family: Consolas, 'SFMono-Regular', Menlo, monospace;
  line-height: 1.6;
}

.auth-grid,
.body-form-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty-hint {
  padding: 14px;
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-lighter);
  font-size: 13px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  padding: 12px;
}

.metric-card span,
.detail-grid span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.response-error-banner {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--el-color-danger-light-5);
  border-radius: 8px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  font-size: 13px;
}

.history-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  padding: 10px 12px;
  cursor: pointer;
}

.result-summary {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.result-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  padding: 10px 12px;
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
  line-height: 1.6;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 12px;
  background: var(--el-fill-color-light);
}

.editor-actions.left {
  justify-content: flex-start;
}

@media (max-width: 1480px) {
  .ms-like-table-header,
  .ms-like-table-row {
    grid-template-columns: 36px minmax(0, 1.1fr) minmax(0, 1fr) minmax(0, 0.9fr) 72px;
  }
}

@media (max-width: 1200px) {
  .ms-like-layout,
  .workspace-grid,
  .execution-grid,
  .settings-grid,
  .form-grid,
  .snapshot-grid,
  .settings-inline-grid {
    grid-template-columns: 1fr;
  }

  .ms-like-main {
    grid-template-rows: auto minmax(0, 1fr) 320px;
  }

  .ms-like-request-row,
  .ms-like-meta-row,
  .ms-like-form-row {
    grid-template-columns: 1fr;
  }

  .ms-like-table-header,
  .ms-like-table-row {
    grid-template-columns: 28px minmax(0, 1fr);
    gap: 8px;
  }

  .ms-like-check-cell {
    grid-column: 1 / -1;
  }

  .ms-like-table-header > span,
  .ms-like-table-header > .ms-like-link-button,
  .ms-like-table-row > :not(.ms-like-drag-cell):not(.ms-like-checkbox-field) {
    grid-column: 2 / -1;
  }

  .page-actions {
    grid-auto-flow: row;
    grid-template-columns: 1fr 1fr;
  }

  .scenario-step-row,
  .assert-row,
  .kv-row {
    grid-template-columns: 1fr;
  }
}
</style>
