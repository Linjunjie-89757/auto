<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import type { Directive } from 'vue'
import {
  Close,
  Fold,
  Folder,
  FolderOpened,
  MoreFilled,
  Plus,
  Setting,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { platformApi } from '../api/platform'
import { useTableSettings, type TableSettingsColumn } from '../composables/useTableSettings'
import { useWorkspace } from '../composables/useWorkspace'
import { useWorkspaceAccess } from '../composables/useWorkspaceAccess'
import ApiAssertionEditor from './ApiAssertionEditor.vue'
import ApiCaseDrawer from './ApiCaseDrawer.vue'
import ApiProcessorEditor from './ApiProcessorEditor.vue'
import MonacoCodeEditor from './MonacoCodeEditor.vue'
import TableSettingsDrawer from './TableSettingsDrawer.vue'
import type {
  ApiAssertionConfig,
  ApiAuthConfig,
  ApiAuthCredential,
  ApiDebugCasePayload,
  ApiDefinitionCaseDetail,
  ApiDefinitionCaseItem,
  ApiDefinitionDetail,
  ApiDefinitionItem,
  DbConnectionItem,
  ApiEnvironmentItem,
  ApiExtractorConfig,
  ApiKeyValue,
  ApiProcessorConfig,
  ApiProcessorExtractorConfig,
  ApiDebugDefinitionPayload,
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

type RequestEditorResourceType = 'definition' | 'case'
type ApiRequestEditorDetail = ApiDefinitionDetail & {
  resourceType: RequestEditorResourceType
  definitionId: number | null
  definitionName: string | null
  casePriority?: string | null
  caseStatus?: string | null
}

type RequestEditorTab = {
  key: string
  resourceType: RequestEditorResourceType
  resourceId: number | null
  definitionId: number | null
  title: string
  method: string
  activeTab: RequestContentTab
  draft: ApiRequestEditorDetail
  savedFingerprint: string
  isDirty: boolean
  debugReportId: number | null
  debugFailureSummary: string
  debugStepResults: ApiRunStepResult[]
}

const requestMethodOptions = ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS', 'HEAD', 'PATCH', 'TRACE'] as const
const queryParamTypeOptions = ['string', 'integer', 'number', 'boolean'] as const
const bodyParamTypeOptions = ['string', 'integer', 'number', 'boolean'] as const
const requestAuthTypeOptions = [
  { label: 'No Auth', value: 'NONE' },
  { label: 'Basic Auth', value: 'BASIC' },
  { label: 'Digest Auth', value: 'DIGEST' },
] as const
const casePriorityOptions = ['P0', 'P1', 'P2', 'P3'] as const
const caseStatusOptions = ['进行中', '已完成', '已废弃'] as const
const CASE_LIST_PAGE_SIZE_OPTIONS = [10, 20, 30, 40, 50] as const

type DefinitionDirectoryTreeNode = {
  key: string
  type: 'root' | 'workspace' | 'module' | 'unassigned' | 'request'
  label: string
  workspaceCode: string
  fullPath: string | null
  count: number
  definitionId: number | null
  method: string | null
  children: DefinitionDirectoryTreeNode[]
}

type BatchAddMode = 'query' | 'cookie' | 'header' | 'body-form' | 'assertion' | 'extractor'
type SortableParamGroup = 'query' | 'header' | 'body-form'
type RequestContentTab = 'params' | 'headers' | 'body' | 'auth' | 'pre' | 'post' | 'tests' | 'settings' | 'cases'

const DEFINITION_TREE_ROOT_KEY = 'definition-root'
const DEFINITION_TREE_UNASSIGNED_KEY = 'definition-unassigned'

const { workspaceCode, isAllScope } = useWorkspace()
const { canWriteWorkspace } = useWorkspaceAccess()

const vOverflowTitle: Directive<HTMLElement, string> = {
  mounted(element, binding) {
    setOverflowTitle(element, binding.value)
  },
  updated(element, binding) {
    setOverflowTitle(element, binding.value)
  },
}

function setOverflowTitle(element: HTMLElement, value?: string) {
  requestAnimationFrame(() => {
    const text = value || element.textContent?.trim() || ''
    if (text && element.scrollWidth > element.clientWidth) {
      element.setAttribute('title', text)
    }
    else {
      element.removeAttribute('title')
    }
  })
}

const loading = ref(false)
const saving = ref(false)
const reportDrawerVisible = ref(false)
const bugDialogVisible = ref(false)
const definitionSaveDialogVisible = ref(false)
const batchAddDrawerVisible = ref(false)
const activeTab = ref<'definitions' | 'scenarios' | 'execution' | 'reports' | 'settings'>('definitions')
const activeRequestTab = ref<RequestContentTab>('body')
const responsePreviewTab = ref<'body' | 'header' | 'console' | 'actualRequest'>('body')
const caseDrawerVisible = ref(false)
const caseDrawerEditorKey = ref('')
const caseDrawerRequestTab = ref<RequestContentTab>('body')
const caseDrawerResponsePreviewTab = ref<'body' | 'header' | 'console' | 'actualRequest'>('body')
const caseDrawerDebugReportId = ref<number | null>(null)
const caseDrawerDebugFailureSummary = ref('')
const caseDrawerDebugStepResults = ref<ApiRunStepResult[]>([])
const batchAddMode = ref<BatchAddMode>('query')
const batchAddInput = ref('')
const batchAddContext = ref<'main' | 'case'>('main')
const draggingParamGroup = ref<SortableParamGroup | null>(null)
const draggingParamIndex = ref<number | null>(null)
const dragOverParamGroup = ref<SortableParamGroup | null>(null)
const dragOverParamIndex = ref<number | null>(null)
const activePreProcessorId = ref<string | null>(null)
const activePostProcessorId = ref<string | null>(null)
const activeAssertionId = ref<string | null>(null)

const definitions = ref<ApiDefinitionItem[]>([])
const apiCases = ref<ApiDefinitionCaseItem[]>([])
const scenarios = ref<ApiScenarioItem[]>([])
const environments = ref<ApiEnvironmentItem[]>([])
const variableSets = ref<ApiVariableSetItem[]>([])
const dbConnections = ref<DbConnectionItem[]>([])
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
const caseDrawerSourceEditorKey = ref('')
const requestEditorSyncing = ref(false)
const selectedDefinitionTreeKey = ref(DEFINITION_TREE_ROOT_KEY)
const definitionTreeRenderKey = ref(0)
const expandedDefinitionTreeKeys = ref<string[]>([DEFINITION_TREE_ROOT_KEY])

const definitionFilters = reactive({
  keyword: '',
  directory: '',
})

const definitionSaveForm = reactive({
  workspaceCode: '',
  name: '',
  path: '',
  directoryName: '',
})

const scenarioFilters = reactive({
  keyword: '',
  directory: '',
})

const definitionForm = reactive<ApiRequestEditorDetail>({
  id: 0,
  resourceType: 'definition',
  definitionId: 0,
  definitionName: '',
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
  casePriority: 'P0',
  caseStatus: '进行中',
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
      contentType: '',
      fileName: '',
      binaryBase64: '',
      jsonText: '',
      xmlText: '',
      plainText: '',
    },
    authConfig: {
      authType: 'NONE',
      basicAuth: {
        userName: '',
        password: '',
      },
      digestAuth: {
        userName: '',
        password: '',
      },
    },
  },
  assertions: [],
  extractors: [],
  preProcessors: [],
  postProcessors: [],
})

const caseDrawerForm = reactive<ApiRequestEditorDetail>(cloneEditorDetail(definitionForm))

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
    authType: 'NONE',
    basicAuth: {
      userName: '',
      password: '',
    },
    digestAuth: {
      userName: '',
      password: '',
    },
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
const canDebugDefinition = computed(() => canWriteDefinition.value
  && !!definitionForm.requestConfig.method?.trim()
  && !!definitionForm.requestConfig.path?.trim())
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
const activeCaseDrawerEditorTab = computed(() => {
  if (!caseDrawerEditorKey.value) {
    return null
  }
  return requestEditorTabs.value.find(item => item.key === caseDrawerEditorKey.value && item.resourceType === 'case') ?? null
})
const currentDefinitionCases = computed(() => {
  const definitionId = activeRequestEditorTab.value?.definitionId
  if (!definitionId || activeRequestEditorTab.value?.resourceType !== 'definition') {
    return []
  }
  return apiCases.value.filter(item => item.definitionId === definitionId)
})
const caseListColumns = computed<TableSettingsColumn[]>(() => [
  { key: 'id', label: 'ID', required: true, defaultVisible: true },
  { key: 'name', label: '用例名称', required: true, defaultVisible: true },
  { key: 'protocol', label: '协议', defaultVisible: true },
  { key: 'priority', label: '用例等级', defaultVisible: true },
  { key: 'status', label: '状态', defaultVisible: true },
  { key: 'path', label: '路径', defaultVisible: true },
  { key: 'tags', label: '标签', defaultVisible: true },
  { key: 'creator', label: '创建人', defaultVisible: true },
])
const caseListSettings = useTableSettings({
  storageKey: 'api-automation-definition-case-list-settings',
  columns: caseListColumns,
  pageSizeEnabled: true,
  defaultPageSize: 10,
  pageSizeOptions: [...CASE_LIST_PAGE_SIZE_OPTIONS],
})
const visibleCaseListColumnKeys = computed(() => new Set(caseListSettings.visibleColumns.value.map(item => item.key)))
const caseListCurrentPage = ref(1)
const caseDrawerCreateSource = ref<'draft' | 'savedDefinition'>('draft')
const pagedDefinitionCases = computed(() => {
  const start = (caseListCurrentPage.value - 1) * caseListSettings.pageSize.value
  return currentDefinitionCases.value.slice(start, start + caseListSettings.pageSize.value)
})
const caseListTotalPages = computed(() => Math.max(1, Math.ceil(currentDefinitionCases.value.length / caseListSettings.pageSize.value)))
const caseDrawerTitle = computed(() => {
  if (caseDrawerForm.id) {
    return '编辑用例'
  }
  return caseDrawerCreateSource.value === 'savedDefinition' ? '保存为用例' : '创建用例'
})
const caseDrawerSubtitle = computed(() => caseDrawerForm.definitionName || caseDrawerForm.name || '未命名接口')
const caseDrawerMethod = computed(() => caseDrawerForm.requestConfig.method || caseDrawerForm.method || 'GET')
const caseDrawerPath = computed(() => caseDrawerForm.requestConfig.path || caseDrawerForm.path || '')
const canWriteCaseDrawer = computed(() => canWriteTarget(caseDrawerForm.workspaceCode))
const canDebugCaseDrawer = computed(() => canWriteCaseDrawer.value
  && !!caseDrawerForm.requestConfig.method?.trim()
  && !!caseDrawerForm.requestConfig.path?.trim())
const showCaseListContent = computed(() => activeRequestEditorTab.value?.resourceType === 'definition' && activeRequestTab.value === 'cases')
const visibleRequestEditorTabs = computed(() => requestEditorTabs.value.filter(item => item.resourceType === 'definition'))

const scenarioDirectoryOptions = computed(() => uniqueNonEmpty(scenarios.value.map(item => item.directoryName)))
const activeOwnerOptions = computed(() => users.value.filter(item => item.status === 1))
const definitionOptions = computed(() => definitions.value.map(item => ({
  label: `${item.method} ${item.name}`,
  value: item.id,
})))
const caseOptions = computed(() => apiCases.value.map(item => ({
  label: `${item.name} (${item.method} ${item.path})`,
  value: item.id,
})))
const draftDefinitionDirectories = computed(() => requestEditorTabs.value
  .map(item => ({
    workspaceCode: item.draft.workspaceCode,
    directoryName: (item.draft.directoryName || '').trim(),
  }))
  .filter(item => !!item.workspaceCode && !!item.directoryName))
const definitionKeywordFilteredItems = computed(() => definitions.value.filter((item) => {
  const keyword = definitionFilters.keyword.trim().toLowerCase()
  if (!keyword) {
    return true
  }
  return item.name.toLowerCase().includes(keyword)
    || item.path.toLowerCase().includes(keyword)
    || (item.description || '').toLowerCase().includes(keyword)
}))

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
  return selectedDefinitionTreeNode.value?.type === 'module' || selectedDefinitionTreeNode.value?.type === 'request'
    ? (selectedDefinitionTreeNode.value.fullPath ?? '')
    : ''
})
function getDefinitionWorkspaceName(targetWorkspaceCode: string) {
  return workspaces.value.find(item => item.code === targetWorkspaceCode)?.name ?? targetWorkspaceCode
}

function getDefinitionWorkspaceModuleOptions(targetWorkspaceCode: string) {
  const result: Array<{ label: string, value: string }> = []
  const walk = (nodes: DefinitionDirectoryTreeNode[]) => {
    for (const node of nodes) {
      if (node.type === 'module' && node.workspaceCode === targetWorkspaceCode && node.fullPath) {
        result.push({ label: node.fullPath, value: node.fullPath })
      }
      if (node.children.length) {
        walk(node.children)
      }
    }
  }
  walk(definitionDirectoryTree.value)
  return result
}

const selectedDefinitionWorkspaceModules = computed(() => {
  const targetWorkspaceCode = definitionSaveForm.workspaceCode || definitionForm.workspaceCode || selectedDefinitionWorkspaceCode.value
  return targetWorkspaceCode ? getDefinitionWorkspaceModuleOptions(targetWorkspaceCode) : []
})

const selectedDefinitionWorkspaceRootLabel = computed(() => {
  const targetWorkspaceCode = definitionSaveForm.workspaceCode || definitionForm.workspaceCode || selectedDefinitionWorkspaceCode.value
  return targetWorkspaceCode ? getDefinitionWorkspaceName(targetWorkspaceCode) : '空间根目录'
})

const filteredDefinitions = computed(() => definitionKeywordFilteredItems.value.filter((item) => {
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
  if (selectedNode?.type === 'request') {
    return item.id === selectedNode.definitionId
  }
  return true
}))

const definitionDirectoryTree = computed<DefinitionDirectoryTreeNode[]>(() => {
  type MutableNode = DefinitionDirectoryTreeNode & { childMap?: Map<string, MutableNode> }
  const directoryItems = [
    ...definitionKeywordFilteredItems.value.map(item => ({
      workspaceCode: item.workspaceCode,
      directoryName: (item.directoryName || '').trim(),
      countWeight: 1,
    })),
    ...draftDefinitionDirectories.value.map(item => ({
      workspaceCode: item.workspaceCode,
      directoryName: item.directoryName,
      countWeight: 0,
    })),
  ]
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
      definitionId: null,
      method: null,
      children: [],
      childMap: new Map<string, MutableNode>(),
    }
  })
  const workspaceMap = new Map(workspaceNodes.map(item => [item.workspaceCode, item]))
  const unassignedRequestMap = new Map<string, MutableNode[]>()
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
        definitionId: null,
        method: null,
        children: [],
        childMap: new Map(),
      }
      parentMap.set(fullPath, node)
      parentChildren.push(node)
    }
    return node
  }
  for (const item of directoryItems) {
    const workspaceNode = workspaceMap.get(item.workspaceCode)
    if (!workspaceNode) {
      continue
    }
    const path = (item.directoryName || '').trim()
    if (!path) {
      workspaceNode.count += item.countWeight
      continue
    }
    workspaceNode.count += item.countWeight
    const segments = path.split('/').map(part => part.trim()).filter(Boolean)
    let currentChildren = workspaceNode.children as MutableNode[]
    let currentMap = workspaceNode.childMap ?? new Map<string, MutableNode>()
    let assembled = ''
    for (const segment of segments) {
      assembled = assembled ? `${assembled}/${segment}` : segment
      const node = ensureNode(currentChildren, currentMap, item.workspaceCode, segment, assembled)
      node.count += item.countWeight
      currentChildren = node.children as MutableNode[]
      currentMap = node.childMap ?? new Map<string, MutableNode>()
    }
  }
  for (const item of definitionKeywordFilteredItems.value) {
    const workspaceNode = workspaceMap.get(item.workspaceCode)
    if (!workspaceNode) {
      continue
    }
    const path = (item.directoryName || '').trim()
    const requestNode: MutableNode = {
      key: `definition-request:${item.id}`,
      type: 'request',
      label: item.name,
      workspaceCode: item.workspaceCode,
      fullPath: path || null,
      count: 0,
      definitionId: item.id,
      method: item.method,
      children: [],
    }
    if (!path) {
      const current = unassignedRequestMap.get(item.workspaceCode) ?? []
      current.push(requestNode)
      unassignedRequestMap.set(item.workspaceCode, current)
      continue
    }
    let parentChildren = workspaceNode.children as MutableNode[]
    let parentMap = workspaceNode.childMap ?? new Map<string, MutableNode>()
    const segments = path.split('/').map(part => part.trim()).filter(Boolean)
    let assembled = ''
    for (const segment of segments) {
      assembled = assembled ? `${assembled}/${segment}` : segment
      const node = ensureNode(parentChildren, parentMap, item.workspaceCode, segment, assembled)
      parentChildren = node.children as MutableNode[]
      parentMap = node.childMap ?? new Map<string, MutableNode>()
    }
    parentChildren.push(requestNode)
  }
  const stripChildMap = (nodes: MutableNode[]): DefinitionDirectoryTreeNode[] => nodes
    .sort((left, right) => {
      const leftOrder = left.type === 'request' ? 1 : 0
      const rightOrder = right.type === 'request' ? 1 : 0
      if (leftOrder !== rightOrder) {
        return leftOrder - rightOrder
      }
      return left.label.localeCompare(right.label, 'zh-CN')
    })
    .map(node => ({
      key: node.key,
      type: node.type,
      label: node.label,
      workspaceCode: node.workspaceCode,
      fullPath: node.fullPath,
      count: node.count,
      definitionId: node.definitionId,
      method: node.method,
      children: stripChildMap(node.children as MutableNode[]),
    }))
  const workspaceTrees = workspaceNodes.map((workspaceNode) => {
    const unassignedRequests = unassignedRequestMap.get(workspaceNode.workspaceCode) ?? []
    const children = stripChildMap(workspaceNode.children as MutableNode[])
    if (unassignedRequests.length) {
      children.push({
        key: `${DEFINITION_TREE_UNASSIGNED_KEY}:${workspaceNode.workspaceCode}`,
        type: 'unassigned',
        label: '\u672a\u89c4\u5212\u8bf7\u6c42',
        workspaceCode: workspaceNode.workspaceCode,
        fullPath: null,
        count: unassignedRequests.length,
        definitionId: null,
        method: null,
        children: stripChildMap(unassignedRequests),
      })
    }
    return {
      key: workspaceNode.key,
      type: 'workspace' as const,
      label: workspaceNode.label,
      workspaceCode: workspaceNode.workspaceCode,
      fullPath: null,
      count: workspaceNode.count,
      definitionId: null,
      method: null,
      children,
    }
  })
  return [{
    key: DEFINITION_TREE_ROOT_KEY,
    type: 'root',
    label: '\u8bf7\u6c42\u76ee\u5f55',
    workspaceCode: isAllScope.value ? 'ALL' : workspaceCode.value,
    fullPath: null,
    count: definitionKeywordFilteredItems.value.length,
    definitionId: null,
    method: null,
    children: workspaceTrees,
  }]
})

function flattenDefinitionTreeNodes(nodes: DefinitionDirectoryTreeNode[]): DefinitionDirectoryTreeNode[] {
  const result: DefinitionDirectoryTreeNode[] = []
  const stack = [...nodes]
  while (stack.length) {
    const current = stack.shift()
    if (!current) {
      continue
    }
    result.push(current)
    if (current.children.length) {
      stack.unshift(...current.children)
    }
  }
  return result
}

const flatDefinitionTreeNodes = computed(() => flattenDefinitionTreeNodes(definitionDirectoryTree.value))

function findDefinitionTreeNode(targetKey: string) {
  return flatDefinitionTreeNodes.value.find(node => node.key === targetKey) ?? null
}

function collectDefinitionDescendantKeys(node: DefinitionDirectoryTreeNode) {
  const keys: string[] = []
  const stack = [...node.children]
  while (stack.length) {
    const current = stack.pop()
    if (!current) {
      continue
    }
    keys.push(current.key)
    stack.push(...current.children)
  }
  return keys
}

function collectDefinitionExpandableKeys(nodes: DefinitionDirectoryTreeNode[]) {
  const keys: string[] = []
  const stack = [...nodes]
  while (stack.length) {
    const current = stack.shift()
    if (!current) {
      continue
    }
    if (current.children.length) {
      keys.push(current.key)
      stack.unshift(...current.children)
    }
  }
  return keys
}

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
const currentResponseStep = computed(() => {
  const steps = activeRequestEditorTab.value?.debugStepResults ?? []
  if (!steps.length) {
    return null
  }
  return steps.find(item => !item.success) ?? steps[steps.length - 1]
})
const currentResponseStatusCode = computed(() => currentResponseStep.value?.response?.statusCode ?? null)
const currentResponseDuration = computed(() => currentResponseStep.value?.durationMs ?? null)
const currentResponseSize = computed(() => {
  const body = currentResponseStep.value?.response?.body
  return body ? `${new Blob([body]).size} B` : '0 B'
})
const currentResponseContentType = computed(() => currentResponseStep.value?.response?.contentType ?? '')
const currentResponseBody = computed(() => currentResponseStep.value?.response?.body ?? '')
const currentResponseHeaders = computed(() => currentResponseStep.value?.response?.headers ?? {})
const currentAssertionResults = computed(() => currentResponseStep.value?.assertionResults ?? [])
const currentExtractionResults = computed(() => currentResponseStep.value?.extractionResults ?? [])
const currentProcessorResults = computed(() => currentResponseStep.value?.processorResults ?? [])
const currentDebugError = computed(() => currentResponseStep.value?.errorMessage || activeRequestEditorTab.value?.debugFailureSummary || '')
const showResponseEmptyState = computed(() => !currentResponseStep.value && !currentDebugError.value)
const caseDrawerResponseStep = computed(() => {
  const steps = caseDrawerDebugStepResults.value
  if (!steps.length) {
    return null
  }
  return steps.find(item => !item.success) ?? steps[steps.length - 1]
})
const caseDrawerResponseStatusCode = computed(() => caseDrawerResponseStep.value?.response?.statusCode ?? null)
const caseDrawerResponseDuration = computed(() => caseDrawerResponseStep.value?.durationMs ?? null)
const caseDrawerResponseSize = computed(() => {
  const body = caseDrawerResponseStep.value?.response?.body
  return body ? `${new Blob([body]).size} B` : '0 B'
})
const caseDrawerResponseContentType = computed(() => caseDrawerResponseStep.value?.response?.contentType ?? '')
const caseDrawerResponseBody = computed(() => caseDrawerResponseStep.value?.response?.body ?? '')
const caseDrawerResponseHeaders = computed(() => caseDrawerResponseStep.value?.response?.headers ?? {})
const caseDrawerAssertionResults = computed(() => caseDrawerResponseStep.value?.assertionResults ?? [])
const caseDrawerExtractionResults = computed(() => caseDrawerResponseStep.value?.extractionResults ?? [])
const caseDrawerProcessorResults = computed(() => caseDrawerResponseStep.value?.processorResults ?? [])
const caseDrawerDebugError = computed(() => caseDrawerResponseStep.value?.errorMessage || caseDrawerDebugFailureSummary.value || '')
const caseDrawerShowResponseEmptyState = computed(() => !caseDrawerResponseStep.value && !caseDrawerDebugError.value)
const shouldShowResponsePanel = computed(() => {
  if (showCaseListContent.value) {
    return false
  }
  return true
})
const queryEnabledCount = computed(() =>
  definitionForm.requestConfig.queryParams.filter(item => !isKeyValueRowEmpty(item) && item.enabled !== false).length,
)

function selectableKeyValueRows(items: ApiKeyValue[]) {
  return items
}

function tableSelectionState(items: ApiKeyValue[]) {
  const selectableRows = selectableKeyValueRows(items)
  const total = selectableRows.length
  const enabled = selectableRows.filter(item => item.enabled !== false).length
  return {
    checked: total > 0 && enabled === total,
    indeterminate: enabled > 0 && enabled < total,
  }
}

function toggleTableSelection(items: ApiKeyValue[], enabled: boolean) {
  selectableKeyValueRows(items).forEach((item) => {
    item.enabled = enabled
  })
}

const queryTableSelectionModel = computed({
  get: () => tableSelectionState(definitionForm.requestConfig.queryParams).checked,
  set: (enabled: boolean) => toggleTableSelection(definitionForm.requestConfig.queryParams, enabled),
})

const headerTableSelectionModel = computed({
  get: () => tableSelectionState(definitionForm.requestConfig.headers).checked,
  set: (enabled: boolean) => toggleTableSelection(definitionForm.requestConfig.headers, enabled),
})

const bodyFormTableSelectionModel = computed({
  get: () => tableSelectionState(definitionForm.requestConfig.body.formItems).checked,
  set: (enabled: boolean) => toggleTableSelection(definitionForm.requestConfig.body.formItems, enabled),
})

const caseDrawerQueryEnabledCount = computed(() =>
  caseDrawerForm.requestConfig.queryParams.filter(item => !isKeyValueRowEmpty(item) && item.enabled !== false).length,
)

const caseDrawerQueryTableSelectionModel = computed({
  get: () => tableSelectionState(caseDrawerForm.requestConfig.queryParams).checked,
  set: (enabled: boolean) => toggleTableSelection(caseDrawerForm.requestConfig.queryParams, enabled),
})

const caseDrawerHeaderTableSelectionModel = computed({
  get: () => tableSelectionState(caseDrawerForm.requestConfig.headers).checked,
  set: (enabled: boolean) => toggleTableSelection(caseDrawerForm.requestConfig.headers, enabled),
})

const caseDrawerBodyFormTableSelectionModel = computed({
  get: () => tableSelectionState(caseDrawerForm.requestConfig.body.formItems).checked,
  set: (enabled: boolean) => toggleTableSelection(caseDrawerForm.requestConfig.body.formItems, enabled),
})

function isBodyMode(mode: string, form: ApiRequestEditorDetail = definitionForm) {
  if (mode === 'json') return form.requestConfig.body.type === 'RAW_JSON'
  if (mode === 'xml') return form.requestConfig.body.type === 'RAW_XML'
  if (mode === 'raw') return form.requestConfig.body.type === 'RAW_TEXT'
  return form.requestConfig.body.type === mode
}

function getModeBodyText(type: string, form: ApiRequestEditorDetail = definitionForm) {
  if (type === 'RAW_JSON') return form.requestConfig.body.jsonText || ''
  if (type === 'RAW_XML') return form.requestConfig.body.xmlText || ''
  if (type === 'RAW_TEXT') return form.requestConfig.body.plainText || ''
  return form.requestConfig.body.rawText || ''
}

function setModeBodyText(type: string, value: string, form: ApiRequestEditorDetail = definitionForm) {
  if (type === 'RAW_JSON') {
    form.requestConfig.body.jsonText = value
  }
  else if (type === 'RAW_XML') {
    form.requestConfig.body.xmlText = value
  }
  else if (type === 'RAW_TEXT') {
    form.requestConfig.body.plainText = value
  }
  form.requestConfig.body.rawText = value
}

function syncActiveBodyText(form: ApiRequestEditorDetail = definitionForm) {
  setModeBodyText(form.requestConfig.body.type, getModeBodyText(form.requestConfig.body.type, form), form)
}

function setBodyMode(mode: 'NONE' | 'FORM_DATA' | 'FORM_URLENCODED' | 'RAW_JSON' | 'RAW_XML' | 'RAW_TEXT' | 'BINARY', form: ApiRequestEditorDetail = definitionForm) {
  form.requestConfig.body.type = mode
  if (mode === 'RAW_JSON') {
    form.requestConfig.body.contentType = 'application/json'
  }
  if (mode === 'RAW_XML') {
    form.requestConfig.body.contentType = 'application/xml'
  }
  if (mode === 'RAW_TEXT') {
    form.requestConfig.body.contentType = 'text/plain'
  }
  if (mode === 'BINARY') {
    form.requestConfig.body.contentType = 'application/octet-stream'
  }
  syncActiveBodyText(form)
}

function getSortableParamList(group: SortableParamGroup, form: ApiRequestEditorDetail = definitionForm) {
  switch (group) {
    case 'query':
      return form.requestConfig.queryParams
    case 'header':
      return form.requestConfig.headers
    case 'body-form':
      return form.requestConfig.body.formItems
  }
}

function clearParamDragState() {
  draggingParamGroup.value = null
  draggingParamIndex.value = null
  dragOverParamGroup.value = null
  dragOverParamIndex.value = null
}

function handleParamDragStart(group: SortableParamGroup, index: number, event: DragEvent) {
  draggingParamGroup.value = group
  draggingParamIndex.value = index
  dragOverParamGroup.value = group
  dragOverParamIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', `${group}:${index}`)
  }
}

function handleParamDragOver(group: SortableParamGroup, index: number, event: DragEvent) {
  if (draggingParamGroup.value !== group) {
    return
  }
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
  dragOverParamGroup.value = group
  dragOverParamIndex.value = index
}

function handleParamDrop(group: SortableParamGroup, index: number, event: DragEvent) {
  event.preventDefault()
  if (draggingParamGroup.value !== group || draggingParamIndex.value === null) {
    clearParamDragState()
    return
  }
  const fromIndex = draggingParamIndex.value
  const targetList = getSortableParamList(group)
  if (fromIndex === index || fromIndex < 0 || fromIndex >= targetList.length || index < 0 || index >= targetList.length) {
    clearParamDragState()
    return
  }
  const [moved] = targetList.splice(fromIndex, 1)
  targetList.splice(index, 0, moved)
  clearParamDragState()
}

function handleParamDragEnd() {
  clearParamDragState()
}

function isParamRowDragging(group: SortableParamGroup, index: number) {
  return draggingParamGroup.value === group && draggingParamIndex.value === index
}

function isParamRowDragOver(group: SortableParamGroup, index: number) {
  return dragOverParamGroup.value === group && dragOverParamIndex.value === index && !isParamRowDragging(group, index)
}

function requestMethodClass(method: string) {
  return `request-method-${method.toLowerCase()}`
}

const activeBodyRawText = computed({
  get: () => getModeBodyText(definitionForm.requestConfig.body.type),
  set: (value: string) => setModeBodyText(definitionForm.requestConfig.body.type, value),
})

const caseDrawerActiveBodyRawText = computed({
  get: () => getModeBodyText(caseDrawerForm.requestConfig.body.type, caseDrawerForm),
  set: (value: string) => setModeBodyText(caseDrawerForm.requestConfig.body.type, value, caseDrawerForm),
})

const activeBodyLanguage = computed<'json' | 'xml' | 'text'>(() => {
  const type = definitionForm.requestConfig.body.type
  if (type === 'RAW_JSON') return 'json'
  if (type === 'RAW_XML') return 'xml'
  return 'text'
})

const caseDrawerActiveBodyLanguage = computed<'json' | 'xml' | 'text'>(() => {
  const type = caseDrawerForm.requestConfig.body.type
  if (type === 'RAW_JSON') return 'json'
  if (type === 'RAW_XML') return 'xml'
  return 'text'
})

const responseBodyLanguage = computed<'json' | 'xml' | 'text'>(() => {
  const contentType = currentResponseContentType.value.toLowerCase()
  const body = currentResponseBody.value.trim()
  if (contentType.includes('json')) return 'json'
  if (contentType.includes('xml') || contentType.includes('html')) return 'xml'
  if (!body) return 'text'
  if ((body.startsWith('{') && body.endsWith('}')) || (body.startsWith('[') && body.endsWith(']'))) {
    try {
      JSON.parse(body)
      return 'json'
    }
    catch {
      return 'text'
    }
  }
  if (body.startsWith('<') && body.endsWith('>')) {
    return 'xml'
  }
  return 'text'
})
const caseDrawerResponseBodyLanguage = computed<'json' | 'xml' | 'text'>(() => {
  const contentType = caseDrawerResponseContentType.value.toLowerCase()
  const body = caseDrawerResponseBody.value.trim()
  if (contentType.includes('json')) return 'json'
  if (contentType.includes('xml') || contentType.includes('html')) return 'xml'
  if (!body) return 'text'
  if ((body.startsWith('{') && body.endsWith('}')) || (body.startsWith('[') && body.endsWith(']'))) {
    try {
      JSON.parse(body)
      return 'json'
    }
    catch {
      return 'text'
    }
  }
  if (body.startsWith('<') && body.endsWith('>')) {
    return 'xml'
  }
  return 'text'
})

const responseBodyPreview = computed(() => currentResponseBody.value || '')
const responseHeadersPreview = computed(() => JSON.stringify(currentResponseHeaders.value, null, 2))
const caseDrawerResponseBodyPreview = computed(() => caseDrawerResponseBody.value || '')
const caseDrawerResponseHeadersPreview = computed(() => JSON.stringify(caseDrawerResponseHeaders.value, null, 2))
const responseConsolePreview = computed(() => {
  const lines: string[] = []
  if (currentDebugError.value) {
    lines.push(`[Error] ${currentDebugError.value}`)
  }
  currentProcessorResults.value.forEach((item, index) => {
    lines.push(`[Processor ${index + 1}] ${item.stage} / ${item.name} / ${item.success ? 'PASS' : 'FAIL'} / ${item.durationMs} ms`)
    if (item.message) {
      lines.push(`  ${item.message}`)
    }
    if (Object.keys(item.outputVariables || {}).length) {
      lines.push(`  outputVariables: ${JSON.stringify(item.outputVariables)}`)
    }
    item.logs?.forEach(log => lines.push(`  ${log}`))
  })
  currentAssertionResults.value.forEach((item, index) => {
    lines.push(`[Assertion ${index + 1}] ${(item.name || item.type)} / ${item.success ? 'PASS' : 'FAIL'}`)
    if (item.message) {
      lines.push(`  ${item.message}`)
    }
    if (item.expectedValue !== undefined || item.actualValue !== undefined) {
      lines.push(`  expected: ${item.expectedValue ?? ''}`)
      lines.push(`  actual: ${item.actualValue ?? ''}`)
    }
  })
  currentExtractionResults.value.forEach((item, index) => {
    lines.push(`[Extraction ${index + 1}] ${item.name} / ${item.success ? 'OK' : 'FAIL'}`)
    lines.push(`  ${item.value || item.message || ''}`)
  })
  return lines.length ? lines.join('\n') : '暂无控制台内容'
})
const caseDrawerResponseConsolePreview = computed(() => {
  const lines: string[] = []
  if (caseDrawerDebugError.value) {
    lines.push(`[Error] ${caseDrawerDebugError.value}`)
  }
  caseDrawerProcessorResults.value.forEach((item, index) => {
    lines.push(`[Processor ${index + 1}] ${item.stage} / ${item.name} / ${item.success ? 'PASS' : 'FAIL'} / ${item.durationMs} ms`)
    if (item.message) {
      lines.push(`  ${item.message}`)
    }
    if (Object.keys(item.outputVariables || {}).length) {
      lines.push(`  outputVariables: ${JSON.stringify(item.outputVariables)}`)
    }
    item.logs?.forEach(log => lines.push(`  ${log}`))
  })
  caseDrawerAssertionResults.value.forEach((item, index) => {
    lines.push(`[Assertion ${index + 1}] ${(item.name || item.type)} / ${item.success ? 'PASS' : 'FAIL'}`)
    if (item.message) {
      lines.push(`  ${item.message}`)
    }
    if (item.expectedValue !== undefined || item.actualValue !== undefined) {
      lines.push(`  expected: ${item.expectedValue ?? ''}`)
      lines.push(`  actual: ${item.actualValue ?? ''}`)
    }
  })
  caseDrawerExtractionResults.value.forEach((item, index) => {
    lines.push(`[Extraction ${index + 1}] ${item.name} / ${item.success ? 'OK' : 'FAIL'}`)
    lines.push(`  ${item.value || item.message || ''}`)
  })
  return lines.length ? lines.join('\n') : '暂无控制台内容'
})
const actualRequestPreview = computed(() => {
  const snapshot = currentResponseStep.value?.request
  if (snapshot) {
    return JSON.stringify(snapshot, null, 2)
  }
  return JSON.stringify({
    method: definitionForm.requestConfig.method || definitionForm.method || 'GET',
    url: definitionForm.requestConfig.path || definitionForm.path || '',
    headers: Object.fromEntries(
      definitionForm.requestConfig.headers
        .filter(item => !isKeyValueRowEmpty(item) && item.enabled !== false)
        .map(item => [item.key, item.value]),
    ),
    body: getModeBodyText(definitionForm.requestConfig.body.type) || null,
  }, null, 2)
})
const caseDrawerActualRequestPreview = computed(() => {
  const snapshot = caseDrawerResponseStep.value?.request
  if (snapshot) {
    return JSON.stringify(snapshot, null, 2)
  }
  return JSON.stringify({
    method: caseDrawerForm.requestConfig.method || caseDrawerForm.method || 'GET',
    url: caseDrawerForm.requestConfig.path || caseDrawerForm.path || '',
    headers: Object.fromEntries(
      caseDrawerForm.requestConfig.headers
        .filter(item => !isKeyValueRowEmpty(item) && item.enabled !== false)
        .map(item => [item.key, item.value]),
    ),
    body: getModeBodyText(caseDrawerForm.requestConfig.body.type, caseDrawerForm) || null,
  }, null, 2)
})
const caseDrawerTagsInput = computed({
  get: () => readTagInput(caseDrawerForm.tags),
  set: (value: string) => updateTagInput(caseDrawerForm, value),
})

async function pickBinaryBodyFile() {
  await pickBinaryBodyFileFor(definitionForm)
}

async function pickCaseDrawerBinaryBodyFile() {
  await pickBinaryBodyFileFor(caseDrawerForm)
}

async function pickBinaryBodyFileFor(form: ApiRequestEditorDetail) {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '*/*'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) {
      return
    }
    const arrayBuffer = await file.arrayBuffer()
    const bytes = new Uint8Array(arrayBuffer)
    let binary = ''
    const chunkSize = 0x8000
    for (let index = 0; index < bytes.length; index += chunkSize) {
      const chunk = bytes.subarray(index, index + chunkSize)
      binary += String.fromCharCode(...chunk)
    }
    form.requestConfig.body.type = 'BINARY'
    form.requestConfig.body.fileName = file.name
    form.requestConfig.body.contentType = file.type || 'application/octet-stream'
    form.requestConfig.body.binaryBase64 = btoa(binary)
  }
  input.click()
}

function clearBinaryBodyFile() {
  clearBinaryBodyFileFor(definitionForm)
}

function clearCaseDrawerBinaryBodyFile() {
  clearBinaryBodyFileFor(caseDrawerForm)
}

function clearBinaryBodyFileFor(form: ApiRequestEditorDetail) {
  form.requestConfig.body.fileName = ''
  form.requestConfig.body.binaryBase64 = ''
  form.requestConfig.body.contentType = 'application/octet-stream'
}

const binaryBodySizeLabel = computed(() => {
  const base64 = definitionForm.requestConfig.body.binaryBase64 || ''
  if (!base64) {
    return ''
  }
  const padding = base64.endsWith('==') ? 2 : base64.endsWith('=') ? 1 : 0
  const bytes = Math.max(0, Math.floor(base64.length * 3 / 4) - padding)
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
})

const caseDrawerBinaryBodySizeLabel = computed(() => {
  const base64 = caseDrawerForm.requestConfig.body.binaryBase64 || ''
  if (!base64) {
    return ''
  }
  const padding = base64.endsWith('==') ? 2 : base64.endsWith('=') ? 1 : 0
  const bytes = Math.max(0, Math.floor(base64.length * 3 / 4) - padding)
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
})

watch(() => workspaceCode.value, () => {
  void bootstrap()
})

watch(definitionForm, () => {
  syncActiveRequestEditorTab()
}, { deep: true })

watch(caseDrawerForm, () => {
  syncCaseDrawerEditorTab()
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

watch(currentDefinitionCases, () => {
  const maxPage = Math.max(1, Math.ceil(currentDefinitionCases.value.length / caseListSettings.pageSize.value))
  if (caseListCurrentPage.value > maxPage) {
    caseListCurrentPage.value = maxPage
  }
}, { deep: true })

watch(() => caseListSettings.pageSize.value, () => {
  caseListCurrentPage.value = 1
})

watch(
  () => definitionSaveForm.workspaceCode,
  (workspace) => {
    if (!workspace) {
      definitionSaveForm.directoryName = ''
      return
    }
    const validDirectories = new Set(getDefinitionWorkspaceModuleOptions(workspace).map(item => item.value))
    if (definitionSaveForm.directoryName && !validDirectories.has(definitionSaveForm.directoryName)) {
      definitionSaveForm.directoryName = ''
    }
  },
)

watch(definitionDirectoryTree, (tree) => {
  const available = new Set(collectDefinitionExpandableKeys(tree))
  const nextKeys = expandedDefinitionTreeKeys.value.filter(key => available.has(key))
  if (!nextKeys.length && tree.length) {
    syncDefinitionExpandedKeys()
    return
  }
  expandedDefinitionTreeKeys.value = nextKeys
}, { immediate: true })

onMounted(() => {
  openNewRequestTab()
  caseListSettings.load()
  void bootstrap()
})

function emptyKeyValue(overrides: Partial<ApiKeyValue> = {}): ApiKeyValue {
  return {
    key: '',
    value: '',
    description: '',
    enabled: true,
    paramType: 'string',
    required: false,
    encode: false,
    minLength: null,
    maxLength: null,
    ...overrides,
  }
}

function isKeyValueRowEmpty(row: ApiKeyValue | null | undefined) {
  if (!row) {
    return true
  }
  return !row.key?.trim()
    && !row.value?.trim()
    && !row.description?.trim()
}

function normalizeKeyValueRow(row: ApiKeyValue | null | undefined, defaults?: Partial<ApiKeyValue>) {
  return emptyKeyValue({
    ...(row ?? {}),
    ...defaults,
  })
}

function ensureTrailingKeyValueRow(target: ApiKeyValue[], defaults?: Partial<ApiKeyValue>) {
  if (!target.length || !isKeyValueRowEmpty(target[target.length - 1])) {
    target.push(emptyKeyValue(defaults))
  }
}

function syncKeyValueRows(target: ApiKeyValue[], defaults?: Partial<ApiKeyValue>) {
  const normalized = target
    .map(item => normalizeKeyValueRow(item, defaults))
    .filter((item, index, arr) => !isKeyValueRowEmpty(item) || index === arr.length - 1)
  target.splice(0, target.length, ...normalized)
  ensureTrailingKeyValueRow(target, defaults)
}

function removeKeyValueRow(target: ApiKeyValue[], index: number, defaults?: Partial<ApiKeyValue>) {
  target.splice(index, 1)
  syncKeyValueRows(target, defaults)
}

function handleKeyValueRowInput(target: ApiKeyValue[], defaults?: Partial<ApiKeyValue>) {
  ensureTrailingKeyValueRow(target, defaults)
}

function queryParamDefaults() {
  return { paramType: 'string', required: false, encode: false }
}

function headerParamDefaults() {
  return { paramType: '', required: false, encode: false }
}

function bodyFormParamDefaults() {
  return { paramType: 'string', required: false, encode: false }
}

function emptyAuthCredential(): ApiAuthCredential {
  return {
    userName: '',
    password: '',
  }
}

function emptyAuthConfig(): ApiAuthConfig {
  return {
    authType: 'NONE',
    basicAuth: emptyAuthCredential(),
    digestAuth: emptyAuthCredential(),
  }
}

function normalizeAuthConfig(authConfig?: Partial<ApiAuthConfig> | null): ApiAuthConfig {
  return {
    authType: authConfig?.authType === 'BASIC' || authConfig?.authType === 'DIGEST' ? authConfig.authType : 'NONE',
    basicAuth: {
      ...emptyAuthCredential(),
      ...(authConfig?.basicAuth ?? {}),
    },
    digestAuth: {
      ...emptyAuthCredential(),
      ...(authConfig?.digestAuth ?? {}),
    },
  }
}

function prepareKeyValueRowsForPayload(items: ApiKeyValue[]) {
  return items
    .map(item => normalizeKeyValueRow(item))
    .filter(item => !isKeyValueRowEmpty(item))
}

function hydrateDefinitionKeyValueRows(detail: ApiDefinitionDetail) {
  syncKeyValueRows(detail.requestConfig.queryParams, queryParamDefaults())
  syncKeyValueRows(detail.requestConfig.headers, headerParamDefaults())
  syncKeyValueRows(detail.requestConfig.body.formItems, bodyFormParamDefaults())
  detail.requestConfig.body.rawText = detail.requestConfig.body.rawText || ''
  detail.requestConfig.body.jsonText = detail.requestConfig.body.type === 'RAW_JSON'
    ? (detail.requestConfig.body.rawText || '')
    : (detail.requestConfig.body.jsonText || '')
  detail.requestConfig.body.xmlText = detail.requestConfig.body.type === 'RAW_XML'
    ? (detail.requestConfig.body.rawText || '')
    : (detail.requestConfig.body.xmlText || '')
  detail.requestConfig.body.plainText = detail.requestConfig.body.type === 'RAW_TEXT'
    ? (detail.requestConfig.body.rawText || '')
    : (detail.requestConfig.body.plainText || '')
  detail.requestConfig.authConfig = normalizeAuthConfig(detail.requestConfig.authConfig)
  detail.preProcessors = normalizeProcessorList(detail.preProcessors || [], 'pre')
  detail.postProcessors = normalizePostProcessorList(detail)
}

function normalizeProcessorList(processors: ApiProcessorConfig[], stage: 'pre' | 'post') {
  return processors.map((processor, index) => {
    const baseId = processor.id || `${stage}-${processor.processorType.toLowerCase()}-${index}`
    if (processor.processorType === 'SCRIPT') {
      return {
        ...emptyProcessor('SCRIPT', stage),
        ...processor,
        id: baseId,
        enabled: processor.enabled !== false,
        script: processor.script || '',
      } satisfies ApiProcessorConfig
    }
    if (processor.processorType === 'TIME_WAITING') {
      return {
        ...emptyProcessor('TIME_WAITING', stage),
        ...processor,
        id: baseId,
        enabled: processor.enabled !== false,
        delayMs: processor.delayMs || 1000,
      } satisfies ApiProcessorConfig
    }
    if (processor.processorType === 'SQL') {
      return {
        ...emptyProcessor('SQL', stage),
        ...processor,
        id: baseId,
        enabled: processor.enabled !== false,
        script: processor.script || '',
        queryTimeout: processor.queryTimeout || 30000,
        extractParams: processor.extractParams || [],
      } satisfies ApiProcessorConfig
    }
    return {
      ...emptyProcessor('EXTRACT', stage),
      ...processor,
      id: baseId,
      enabled: processor.enabled !== false,
      extractors: (processor.extractors || []).map(item => ({
        ...emptyProcessorExtractor(),
        ...item,
        enabled: item.enabled !== false,
      })),
    } satisfies ApiProcessorConfig
  })
}

function normalizePostProcessorList(detail: ApiDefinitionDetail) {
  const processors = normalizeProcessorList(detail.postProcessors || [], 'post')
  if (processors.length || !detail.extractors?.length) {
    return processors
  }
  return [
    ...processors,
    {
      ...emptyProcessor('EXTRACT', 'post'),
      name: 'Extract',
      extractors: detail.extractors.map(item => ({
        ...emptyProcessorExtractor(),
        name: item.name,
        sourceType: (item.sourceType as ApiProcessorExtractorConfig['sourceType']) || 'BODY_JSONPATH',
        expression: item.expression,
      })),
    },
  ]
}

function emptyProcessorExtractor(): ApiProcessorExtractorConfig {
  return {
    name: '',
    variableName: '',
    variableType: 'TEMPORARY',
    extractType: 'JSON_PATH',
    extractScope: 'BODY',
    expression: '$.data.id',
    expressionMatchingRule: 'EXPRESSION',
    resultMatchingRule: 'RANDOM',
    resultMatchingRuleNum: 1,
    responseFormat: 'JSON',
    enabled: true,
  }
}

function emptyProcessor(type: ApiProcessorConfig['processorType'], stage: 'pre' | 'post'): ApiProcessorConfig {
  const id = `${stage}-${type.toLowerCase()}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  if (type === 'SCRIPT') {
    return {
      id,
      processorType: 'SCRIPT',
      name: stage === 'pre' ? 'Pre Script' : 'Post Script',
      enabled: true,
      script: '',
    }
  }
  if (type === 'TIME_WAITING') {
    return {
      id,
      processorType: 'TIME_WAITING',
      name: 'Wait',
      enabled: true,
      delayMs: 1000,
    }
  }
  if (type === 'SQL') {
    return {
      id,
      processorType: 'SQL',
      name: 'SQL',
      enabled: true,
      script: '',
      dataSourceId: null,
      dataSourceName: '',
      queryTimeout: 30000,
      variableNames: '',
      extractParams: [],
      resultVariable: '',
    }
  }
  return {
    id,
    processorType: 'EXTRACT',
    name: 'Extract',
    enabled: true,
    extractors: [emptyProcessorExtractor()],
  }
}

function emptyScenarioStep(): ApiScenarioStep {
  return { stepName: '', resourceType: 'CASE', resourceId: 0, enabled: true }
}

function emptyVariable(): ApiVariableItem {
  return { name: '', value: '', sensitive: false }
}

const batchAddTitle = computed(() => {
  switch (batchAddMode.value) {
    case 'query':
      return '批量添加 Query 参数'
    case 'cookie':
      return '批量添加 Cookie 参数'
    case 'header':
      return '批量添加请求头'
    case 'body-form':
      return '批量添加 Body 参数'
    case 'assertion':
      return '批量添加断言'
    case 'extractor':
      return '批量添加提取参数'
  }
})

const batchAddPlaceholder = computed(() => {
  switch (batchAddMode.value) {
    case 'assertion':
      return '每行一条，支持 TAB 或连续空格分列'
    case 'extractor':
      return '每行一条，格式：变量名<TAB>来源<TAB>表达式'
    default:
      return '每行一条，格式：名称<TAB>值 或 名称:值；空行自动忽略，同名以最后一条为准'
  }
})

const batchAddExamples = computed(() => {
  switch (batchAddMode.value) {
    case 'query':
      return ['page\t1', 'pageSize\t20', 'keyword:test']
    case 'cookie':
      return ['SESSION\tabc123', 'token:{{authToken}}']
    case 'header':
      return ['Content-Type\tapplication/json', 'Authorization\tBearer {{token}}']
    case 'body-form':
      return ['username\tadmin', 'password\t123456']
    case 'assertion':
      return ['STATUS_CODE\t200', 'HEADER_EQUALS\tContent-Type\tapplication/json', 'BODY_JSONPATH_EQUALS\t$.code\t0']
    case 'extractor':
      return ['token\tHEADER\tAuthorization', 'userId\tBODY_JSONPATH\t$.data.id']
  }
})

function toEditorDetailFromDefinition(detail: ApiDefinitionDetail): ApiRequestEditorDetail {
  return {
    ...JSON.parse(JSON.stringify(detail)) as ApiDefinitionDetail,
    resourceType: 'definition',
    definitionId: detail.id || 0,
    definitionName: detail.name || '',
  }
}

function toEditorDetailFromCase(detail: ApiDefinitionCaseDetail): ApiRequestEditorDetail {
  const meta = detail as ApiDefinitionCaseDetail & { casePriority?: string | null, caseStatus?: string | null, priority?: string | null, status?: string | null }
  return {
    ...JSON.parse(JSON.stringify(detail)) as ApiDefinitionCaseDetail,
    directoryName: '',
    extractors: Array.isArray(detail.extractors) ? detail.extractors : [],
    resourceType: 'case',
    definitionId: detail.definitionId,
    definitionName: detail.definitionName,
    casePriority: meta.casePriority || meta.priority || 'P0',
    caseStatus: meta.caseStatus || meta.status || '进行中',
  }
}

function cloneEditorDetail(detail: ApiRequestEditorDetail): ApiRequestEditorDetail {
  return JSON.parse(JSON.stringify(detail)) as ApiRequestEditorDetail
}

function fingerprintDefinitionDetail(detail: ApiRequestEditorDetail) {
  return JSON.stringify(detail)
}

function hasEnabledKeyValueRows(rows: ApiKeyValue[]) {
  return rows.some(item => !isKeyValueRowEmpty(item) && item.enabled !== false)
}

function resolveDefaultRequestTab(detail: ApiRequestEditorDetail): RequestContentTab {
  if (detail.requestConfig.body.type !== 'NONE') {
    return 'body'
  }
  if (hasEnabledKeyValueRows(detail.requestConfig.queryParams)) {
    return 'params'
  }
  if (hasEnabledKeyValueRows(detail.requestConfig.headers)) {
    return 'headers'
  }
  return 'body'
}

function buildEmptyDefinitionDetail(): ApiRequestEditorDetail {
  return {
    id: 0,
    resourceType: 'definition',
    definitionId: 0,
    definitionName: '',
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
    casePriority: 'P0',
    caseStatus: '进行中',
    requestConfig: {
      method: 'GET',
      path: '',
      timeoutMs: 10000,
      queryParams: [],
      headers: [],
      cookies: [],
      body: { type: 'NONE', rawText: '', formItems: [], contentType: '', fileName: '', binaryBase64: '', jsonText: '', xmlText: '', plainText: '' },
      authConfig: emptyAuthConfig(),
    },
    assertions: [],
    extractors: [],
    preProcessors: [],
    postProcessors: [],
  }
}

function makeRequestEditorTab(detail?: ApiRequestEditorDetail) {
  const draft = cloneEditorDetail(detail ?? buildEmptyDefinitionDetail())
  hydrateDefinitionKeyValueRows(draft)
  if (draft.requestConfig.body.type === 'RAW_JSON') {
    draft.requestConfig.body.jsonText = draft.requestConfig.body.rawText || ''
  }
  else if (draft.requestConfig.body.type === 'RAW_XML') {
    draft.requestConfig.body.xmlText = draft.requestConfig.body.rawText || ''
  }
  else if (draft.requestConfig.body.type === 'RAW_TEXT') {
    draft.requestConfig.body.plainText = draft.requestConfig.body.rawText || ''
  }
  const savedFingerprint = fingerprintDefinitionDetail(draft)
  const key = `request-tab-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  return {
    key,
    resourceType: draft.resourceType,
    resourceId: draft.id || null,
    definitionId: draft.definitionId || draft.id || null,
    title: draft.name || '\u65b0\u5efa\u8bf7\u6c42',
    method: draft.requestConfig.method || draft.method || 'GET',
    activeTab: resolveDefaultRequestTab(draft),
    draft,
    savedFingerprint,
    isDirty: false,
    debugReportId: null,
    debugFailureSummary: '',
    debugStepResults: [],
  } satisfies RequestEditorTab
}

function syncActiveRequestEditorTab() {
  const current = activeRequestEditorTab.value
  if (!current || requestEditorSyncing.value) {
    return
  }
  const snapshot = cloneEditorDetail(definitionForm)
  current.draft = snapshot
  current.resourceType = definitionForm.resourceType
  current.resourceId = definitionForm.id || null
  current.definitionId = definitionForm.resourceType === 'case'
    ? (definitionForm.definitionId || null)
    : (definitionForm.id || null)
  current.title = definitionForm.name || (definitionForm.resourceType === 'case' ? '新建用例' : '\u65b0\u5efa\u8bf7\u6c42')
  current.method = definitionForm.requestConfig.method || definitionForm.method || 'GET'
  current.activeTab = activeRequestTab.value
  current.isDirty = current.savedFingerprint !== fingerprintDefinitionDetail(snapshot)
}

function syncCaseDrawerEditorTab() {
  const current = activeCaseDrawerEditorTab.value
  if (!current || requestEditorSyncing.value || !caseDrawerVisible.value) {
    return
  }
  const snapshot = cloneEditorDetail(caseDrawerForm)
  current.draft = snapshot
  current.resourceType = caseDrawerForm.resourceType
  current.resourceId = caseDrawerForm.id || null
  current.definitionId = caseDrawerForm.definitionId || null
  current.title = caseDrawerForm.name || '新建用例'
  current.method = caseDrawerForm.requestConfig.method || caseDrawerForm.method || 'GET'
  current.activeTab = caseDrawerRequestTab.value
  current.isDirty = current.savedFingerprint !== fingerprintDefinitionDetail(snapshot)
}

function setActiveRequestContentTab(tab: RequestContentTab) {
  activeRequestTab.value = tab
  const current = activeRequestEditorTab.value
  if (current) {
    current.activeTab = tab
  }
}

function setCaseDrawerRequestContentTab(tab: RequestContentTab) {
  caseDrawerRequestTab.value = tab
  const current = activeCaseDrawerEditorTab.value
  if (current) {
    current.activeTab = tab
  }
}

function applyEditorDetailToForm(detail: ApiRequestEditorDetail, options?: { markSaved?: boolean }) {
  requestEditorSyncing.value = true
  const cloned = cloneEditorDetail(detail)
  hydrateDefinitionKeyValueRows(cloned)
  Object.assign(definitionForm, cloned)
  if (cloned.resourceType === 'definition') {
    selectedDefinitionId.value = cloned.id || null
    syncDefinitionTreeSelection(cloned)
  }
  const current = activeRequestEditorTab.value
  if (current) {
    current.resourceType = cloned.resourceType
    current.resourceId = cloned.id || null
    current.draft = cloneEditorDetail(cloned)
    current.definitionId = cloned.resourceType === 'case'
      ? (cloned.definitionId || null)
      : (cloned.id || null)
    current.title = cloned.name || (cloned.resourceType === 'case' ? '新建用例' : '\u65b0\u5efa\u8bf7\u6c42')
    current.method = cloned.requestConfig.method || cloned.method || 'GET'
    current.activeTab = current.activeTab || resolveDefaultRequestTab(cloned)
    if (options?.markSaved) {
      current.savedFingerprint = fingerprintDefinitionDetail(cloned)
      current.isDirty = false
    }
  }
  void nextTick(() => {
    requestEditorSyncing.value = false
  })
}

function applyCaseDrawerDetailToForm(detail: ApiRequestEditorDetail, options?: { markSaved?: boolean }) {
  requestEditorSyncing.value = true
  const cloned = cloneEditorDetail(detail)
  hydrateDefinitionKeyValueRows(cloned)
  Object.assign(caseDrawerForm, cloned)
  const current = activeCaseDrawerEditorTab.value
  if (current) {
    current.resourceType = cloned.resourceType
    current.resourceId = cloned.id || null
    current.draft = cloneEditorDetail(cloned)
    current.definitionId = cloned.definitionId || null
    current.title = cloned.name || '新建用例'
    current.method = cloned.requestConfig.method || cloned.method || 'GET'
    current.activeTab = current.activeTab || resolveDefaultRequestTab(cloned)
    if (options?.markSaved) {
      current.savedFingerprint = fingerprintDefinitionDetail(cloned)
      current.isDirty = false
    }
  }
  void nextTick(() => {
    requestEditorSyncing.value = false
  })
}

function resetCaseDrawerDebugState() {
  caseDrawerDebugReportId.value = null
  caseDrawerDebugFailureSummary.value = ''
  caseDrawerDebugStepResults.value = []
}

function syncCaseDrawerDebugStateFromTab(tab?: RequestEditorTab | null) {
  if (!tab) {
    resetCaseDrawerDebugState()
    return
  }
  caseDrawerDebugReportId.value = tab.debugReportId
  caseDrawerDebugFailureSummary.value = tab.debugFailureSummary || ''
  caseDrawerDebugStepResults.value = [...(tab.debugStepResults || [])]
}

function updateCaseDrawerDebugState(reportId: number | null, failureSummary: string, stepResults: ApiRunStepResult[]) {
  caseDrawerDebugReportId.value = reportId
  caseDrawerDebugFailureSummary.value = failureSummary
  caseDrawerDebugStepResults.value = [...stepResults]
}

function activateRequestEditorTab(key: string) {
  const target = requestEditorTabs.value.find(item => item.key === key)
  if (!target) {
    return
  }
  activeRequestEditorKey.value = key
  activeRequestTab.value = target.activeTab || resolveDefaultRequestTab(target.draft)
  applyEditorDetailToForm(target.draft)
}

function openNewRequestTab(detail?: ApiRequestEditorDetail) {
  const tab = makeRequestEditorTab(detail)
  requestEditorTabs.value.push(tab)
  activateRequestEditorTab(tab.key)
}

function syncDefinitionTreeSelection(detail: Pick<ApiDefinitionDetail, 'id' | 'workspaceCode' | 'directoryName'>) {
  const directoryName = (detail.directoryName || '').trim()
  if (!detail.id) {
    if (directoryName) {
      const moduleKey = `definition-module:${detail.workspaceCode}:${directoryName}`
      expandDefinitionTreeToKey(moduleKey)
      selectedDefinitionTreeKey.value = moduleKey
      return
    }
    if (isAllScope.value && detail.workspaceCode) {
      selectedDefinitionTreeKey.value = `definition-workspace:${detail.workspaceCode}`
      return
    }
    selectedDefinitionTreeKey.value = DEFINITION_TREE_ROOT_KEY
    return
  }
  if (directoryName) {
    expandDefinitionTreeToKey(`definition-module:${detail.workspaceCode}:${directoryName}`)
  }
  selectedDefinitionTreeKey.value = `definition-request:${detail.id}`
}

function openOrReuseDraftRequest(detail: ApiRequestEditorDetail) {
  const current = activeRequestEditorTab.value
  if (current && current.resourceType === 'definition' && current.resourceId == null && !current.isDirty) {
    const draft = cloneEditorDetail(detail)
    hydrateDefinitionKeyValueRows(draft)
    current.draft = draft
    current.resourceType = draft.resourceType
    current.resourceId = draft.id || null
    current.definitionId = draft.resourceType === 'case'
      ? (draft.definitionId || null)
      : (draft.id || null)
    current.title = draft.name || (draft.resourceType === 'case' ? '新建用例' : '\u65b0\u5efa\u8bf7\u6c42')
    current.method = draft.requestConfig.method || draft.method || 'GET'
    current.activeTab = resolveDefaultRequestTab(draft)
    current.savedFingerprint = fingerprintDefinitionDetail(draft)
    current.isDirty = false
    current.debugReportId = null
    current.debugFailureSummary = ''
    current.debugStepResults = []
    activateRequestEditorTab(current.key)
    return
  }
  openNewRequestTab(detail)
}

async function closeRequestEditorTab(key: string, options?: { activateFallback?: boolean }) {
  const closing = requestEditorTabs.value.find(item => item.key === key)
  if (!closing) {
    return
  }
  if (closing.isDirty) {
    await ElMessageBox.confirm('\u5f53\u524d\u8bf7\u6c42\u6709\u672a\u4fdd\u5b58\u4fee\u6539\uff0c\u786e\u8ba4\u5173\u95ed\u8fd9\u4e2a\u8bf7\u6c42\u9875\u7b7e\u5417\uff1f', '\u5173\u95ed\u8bf7\u6c42', { type: 'warning' })
  }
  if (requestEditorTabs.value.length <= 1) {
    const current = requestEditorTabs.value[0]
    if (current) {
      const emptyDetail = buildEmptyDefinitionDetail()
      current.draft = emptyDetail
      current.resourceType = 'definition'
      current.resourceId = null
      current.definitionId = null
      current.title = '\u65b0\u5efa\u8bf7\u6c42'
      current.method = 'GET'
      current.activeTab = resolveDefaultRequestTab(emptyDetail)
      current.savedFingerprint = fingerprintDefinitionDetail(emptyDetail)
      current.isDirty = false
      current.debugReportId = null
      current.debugFailureSummary = ''
      current.debugStepResults = []
      if (options?.activateFallback !== false) {
        activateRequestEditorTab(current.key)
      }
    }
    return
  }
  const index = requestEditorTabs.value.findIndex(item => item.key === key)
  if (index < 0) {
    return
  }
  requestEditorTabs.value.splice(index, 1)
  if (caseDrawerSourceEditorKey.value === key) {
    caseDrawerSourceEditorKey.value = ''
  }
  const fallback = requestEditorTabs.value[Math.max(0, index - 1)] ?? requestEditorTabs.value[0]
  if (fallback && options?.activateFallback !== false) {
    activateRequestEditorTab(fallback.key)
  }
}

async function closeCaseDrawer() {
  const activeCaseTab = activeCaseDrawerEditorTab.value
  if (!activeCaseTab) {
    return
  }
  await closeRequestEditorTab(activeCaseTab.key, { activateFallback: false })
  caseDrawerVisible.value = false
  caseDrawerEditorKey.value = ''
  caseDrawerRequestTab.value = 'body'
  caseDrawerResponsePreviewTab.value = 'body'
  caseDrawerSourceEditorKey.value = ''
  resetCaseDrawerDebugState()
}

function buildCaseDraftFromCurrentDefinition(options?: { fromSavedDefinition?: boolean }) {
  const snapshot = cloneEditorDetail(definitionForm)
  snapshot.resourceType = 'case'
  snapshot.id = 0
  snapshot.lastRunResult = null
  snapshot.lastRunAt = null
  snapshot.createdAt = null
  snapshot.updatedAt = null
  snapshot.definitionId = definitionForm.resourceType === 'definition'
    ? (definitionForm.id || 0)
    : (definitionForm.definitionId || 0)
  snapshot.definitionName = definitionForm.resourceType === 'definition'
    ? (definitionForm.name || '')
    : (definitionForm.definitionName || '')
  snapshot.extractors = []
  snapshot.name = options?.fromSavedDefinition && snapshot.definitionName
    ? `${snapshot.definitionName} 用例`
    : (snapshot.name ? `${snapshot.name} 用例` : '新建用例')
  return snapshot
}

async function openCaseEditor(id: number) {
  const loadedTab = requestEditorTabs.value.find(item => item.resourceType === 'case' && item.resourceId === id)
  if (!loadedTab) {
    const detail = await platformApi.getApiDefinitionCaseDetail(workspaceCode.value, id)
    const tab = makeRequestEditorTab(toEditorDetailFromCase(detail))
    requestEditorTabs.value.push(tab)
  }
  caseDrawerSourceEditorKey.value = activeRequestEditorKey.value
  const target = requestEditorTabs.value.find(item => item.resourceType === 'case' && item.resourceId === id)
  if (target) {
    caseDrawerCreateSource.value = 'draft'
    caseDrawerEditorKey.value = target.key
    caseDrawerRequestTab.value = target.activeTab || resolveDefaultRequestTab(target.draft)
    caseDrawerResponsePreviewTab.value = 'body'
    applyCaseDrawerDetailToForm(target.draft)
    syncCaseDrawerDebugStateFromTab(target)
    caseDrawerVisible.value = true
  }
}

function openCaseDraftFromDefinition(options?: { fromSavedDefinition?: boolean }) {
  const detail = buildCaseDraftFromCurrentDefinition(options)
  const tab = makeRequestEditorTab(detail)
  requestEditorTabs.value.push(tab)
  caseDrawerSourceEditorKey.value = activeRequestEditorKey.value
  caseDrawerCreateSource.value = options?.fromSavedDefinition ? 'savedDefinition' : 'draft'
  caseDrawerEditorKey.value = tab.key
  caseDrawerRequestTab.value = tab.activeTab || resolveDefaultRequestTab(tab.draft)
  caseDrawerResponsePreviewTab.value = 'body'
  applyCaseDrawerDetailToForm(tab.draft)
  syncCaseDrawerDebugStateFromTab(tab)
  caseDrawerVisible.value = true
}

async function runCaseItem(id: number) {
  await openCaseEditor(id)
  await debugCaseDrawer()
}

async function duplicateCaseItem(id: number) {
  const detail = await platformApi.getApiDefinitionCaseDetail(workspaceCode.value, id)
  const duplicated = Object.assign(buildEmptyDefinitionDetail(), toEditorDetailFromCase(detail), {
    resourceType: 'case' as const,
    id: 0,
    name: detail.name ? `${detail.name} - 副本` : '新建用例',
    lastRunResult: null,
    lastRunAt: null,
    createdAt: null,
    updatedAt: null,
  })
  const tab = makeRequestEditorTab(duplicated)
  requestEditorTabs.value.push(tab)
  caseDrawerSourceEditorKey.value = activeRequestEditorKey.value
  caseDrawerCreateSource.value = 'draft'
  caseDrawerEditorKey.value = tab.key
  caseDrawerRequestTab.value = tab.activeTab || resolveDefaultRequestTab(tab.draft)
  caseDrawerResponsePreviewTab.value = 'body'
  applyCaseDrawerDetailToForm(tab.draft)
  syncCaseDrawerDebugStateFromTab(tab)
  caseDrawerVisible.value = true
}

async function removeCase(id?: number | null) {
  if (!id) {
    await closeRequestEditorTab(activeRequestEditorKey.value)
    return
  }
  await ElMessageBox.confirm('删除后不可恢复，确认删除当前用例吗？', '删除用例', { type: 'warning' })
  await platformApi.deleteApiDefinitionCase(workspaceCode.value, id)
  ElMessage.success('用例已删除')
  const closingKey = activeRequestEditorKey.value
  await refreshData()
  await closeRequestEditorTab(closingKey)
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
    authConfig: emptyAuthConfig(),
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
      casePage,
      scenarioPage,
      envPage,
      variablePage,
      dbConnectionPage,
      taskPage,
      reportPage,
      userList,
      workspaceList,
    ] = await Promise.all([
      platformApi.getApiDefinitions(workspaceCode.value),
      platformApi.getApiDefinitionCases(workspaceCode.value),
      platformApi.getApiScenarios(workspaceCode.value),
      platformApi.getApiEnvironments(workspaceCode.value),
      platformApi.getApiVariableSets(workspaceCode.value),
      platformApi.getSettingsDbConnections(workspaceCode.value),
      platformApi.getTasks(workspaceCode.value),
      platformApi.getReports(workspaceCode.value),
      platformApi.getUsers(),
      platformApi.getSwitchableWorkspaces(),
    ])
    definitions.value = definitionPage.items
    apiCases.value = casePage.items
    scenarios.value = scenarioPage.items
    environments.value = envPage.items
    variableSets.value = variablePage.items
    dbConnections.value = dbConnectionPage.items
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
  const loadedTab = requestEditorTabs.value.find(item => item.resourceType === 'definition' && item.resourceId === id)
  if (loadedTab) {
    activateRequestEditorTab(loadedTab.key)
    return
  }
  selectedDefinitionId.value = id
  const detail = await platformApi.getApiDefinitionDetail(workspaceCode.value, id)
  openNewRequestTab(toEditorDetailFromDefinition(detail))
}

async function selectScenario(id: number) {
  selectedScenarioId.value = id
  const detail = await platformApi.getApiScenarioDetail(workspaceCode.value, id)
  assignScenario(detail)
}

function assignScenario(detail: ApiScenarioDetail) {
  Object.assign(scenarioForm, JSON.parse(JSON.stringify(detail)))
  scenarioForm.steps = (scenarioForm.steps || []).map(step => ({
    stepName: step.stepName || '',
    resourceType: step.resourceType || 'DEFINITION',
    resourceId: step.resourceId || 0,
    enabled: step.enabled !== false,
  }))
}

function addDefinitionRow(target: ApiKeyValue[]) {
  const defaults = target === definitionForm.requestConfig.queryParams
    ? queryParamDefaults()
    : target === definitionForm.requestConfig.headers
      ? headerParamDefaults()
      : bodyFormParamDefaults()
  target.push(emptyKeyValue(defaults))
}

function openBatchAddDrawer(mode: BatchAddMode, context: 'main' | 'case' = 'main') {
  batchAddMode.value = mode
  batchAddInput.value = ''
  batchAddContext.value = context
  batchAddDrawerVisible.value = true
}

function splitBatchColumns(line: string) {
  if (line.includes('\t')) {
    return line.split('\t').map(item => item.trim())
  }
  if (line.includes('：')) {
    const index = line.indexOf('：')
    return [line.slice(0, index).trim(), line.slice(index + 1).trim()]
  }
  if (line.includes(':')) {
    const index = line.indexOf(':')
    return [line.slice(0, index).trim(), line.slice(index + 1).trim()]
  }
  if (line.includes('=')) {
    const index = line.indexOf('=')
    return [line.slice(0, index).trim(), line.slice(index + 1).trim()]
  }
  return line.split(/\s{2,}/).map(item => item.trim())
}

function splitBatchLine(line: string) {
  const columns = splitBatchColumns(line).filter(Boolean)
  return columns.length ? columns : [line.trim()]
}

function normalizeBooleanLike(value: string) {
  const normalized = value.trim().toLowerCase()
  if (['true', '1', 'yes', 'y', 'on', '启用', '开启'].includes(normalized)) {
    return true
  }
  if (['false', '0', 'no', 'n', 'off', '绂佺敤', '鍏抽棴'].includes(normalized)) {
    return false
  }
  return null
}

function dedupeByKey<T>(items: T[], readKey: (item: T) => string) {
  const latestIndex = new Map<string, number>()
  items.forEach((item, index) => {
    const key = readKey(item).trim()
    if (key) {
      latestIndex.set(key, index)
    }
  })
  return items.filter((item, index) => latestIndex.get(readKey(item).trim()) === index)
}

function isNonNull<T>(value: T | null): value is T {
  return value !== null
}

function parseBatchKeyValueInput() {
  const rows = batchAddInput.value
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
    .map<ApiKeyValue | null>((line) => {
      const columns = splitBatchLine(line).filter(Boolean)
      if (!columns.length) {
        return null
      }
      let enabled = true
      let offset = 0
      const firstBoolean = normalizeBooleanLike(columns[0])
      if (firstBoolean !== null && columns.length >= 2) {
        enabled = firstBoolean
        offset = 1
      }
      const key = columns[offset] || ''
      const value = columns[offset + 1] || ''
      return { key, value, enabled }
    })
    .filter(isNonNull)
    .filter(item => !!item.key)
  return dedupeByKey(rows, item => item.key)
}

function normalizeAssertionType(value: string) {
  const key = value.trim().toUpperCase()
  const aliases: Record<string, ApiAssertionConfig['type']> = {
    STATUS: 'STATUS_CODE',
    STATUS_CODE: 'STATUS_CODE',
    HEADER_EQUALS: 'HEADER_EQUALS',
    HEADER_CONTAINS: 'HEADER_CONTAINS',
    BODY_JSONPATH_EQUALS: 'BODY_JSONPATH_EQUALS',
    BODY_JSONPATH_CONTAINS: 'BODY_JSONPATH_CONTAINS',
    RESPONSE_TIME_LE: 'RESPONSE_TIME_LE',
  }
  return aliases[key] ?? null
}

function parseBatchAssertions() {
  const rows = batchAddInput.value
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
    .map<ApiAssertionConfig | null>((line) => {
      const parts = splitBatchColumns(line).filter(Boolean)
      const type = normalizeAssertionType(parts[0] || '')
      if (!type) {
        return null
      }
      if (type === 'STATUS_CODE' || type === 'RESPONSE_TIME_LE') {
        return {
          type,
          subject: '',
          operator: 'EQUALS',
          expectedValue: parts[1] || '',
        } satisfies ApiAssertionConfig
      }
      return {
        type,
        subject: parts[1] || '',
        operator: 'EQUALS',
        expectedValue: parts.slice(2).join('\t'),
      } satisfies ApiAssertionConfig
    })
    .filter((item): item is ApiAssertionConfig => !!item && !!item.expectedValue)
  return dedupeByKey(rows, item => `${item.type ?? ''}|${item.subject ?? ''}`)
}

function normalizeExtractorType(value: string) {
  const key = value.trim().toUpperCase()
  if (key === 'HEADER' || key === 'BODY_JSONPATH') {
    return key as ApiExtractorConfig['sourceType']
  }
  return null
}

function parseBatchExtractors() {
  const rows = batchAddInput.value
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
    .map((line) => {
      const parts = splitBatchColumns(line).filter(Boolean)
      const name = parts[0] || ''
      const maybeSourceType = normalizeExtractorType(parts[1] || '')
      const sourceType = maybeSourceType ?? 'BODY_JSONPATH'
      const expression = maybeSourceType
        ? parts.slice(2).join('\t')
        : parts.slice(1).join('\t')
      if (!name || !expression) {
        return null
      }
      return { name, sourceType, expression } satisfies ApiExtractorConfig
    })
    .filter((item): item is ApiExtractorConfig => !!item)
  return dedupeByKey(rows, item => item.name)
}

function confirmBatchAdd() {
  const targetForm = batchAddContext.value === 'case' ? caseDrawerForm : definitionForm
  let count = 0
  if (batchAddMode.value === 'query') {
    const rows = parseBatchKeyValueInput()
    targetForm.requestConfig.queryParams.push(...rows.map(row => normalizeKeyValueRow(row, queryParamDefaults())))
    syncKeyValueRows(targetForm.requestConfig.queryParams, queryParamDefaults())
    count = rows.length
  }
  else if (batchAddMode.value === 'cookie') {
    const rows = parseBatchKeyValueInput()
    targetForm.requestConfig.cookies.push(...rows)
    count = rows.length
  }
  else if (batchAddMode.value === 'header') {
    const rows = parseBatchKeyValueInput()
    targetForm.requestConfig.headers.push(...rows.map(row => normalizeKeyValueRow(row, headerParamDefaults())))
    syncKeyValueRows(targetForm.requestConfig.headers, headerParamDefaults())
    count = rows.length
  }
  else if (batchAddMode.value === 'body-form') {
    const rows = parseBatchKeyValueInput()
    targetForm.requestConfig.body.formItems.push(...rows.map(row => normalizeKeyValueRow(row, bodyFormParamDefaults())))
    syncKeyValueRows(targetForm.requestConfig.body.formItems, bodyFormParamDefaults())
    count = rows.length
  }
  else if (batchAddMode.value === 'assertion') {
    const rows = parseBatchAssertions()
    targetForm.assertions.push(...rows)
    count = rows.length
  }
  else if (batchAddMode.value === 'extractor') {
    const rows = parseBatchExtractors()
    targetForm.extractors.push(...rows)
    count = rows.length
  }

  if (!count) {
    ElMessage.warning('未解析出可添加的数据')
    return
  }
  batchAddDrawerVisible.value = false
  batchAddInput.value = ''
  batchAddContext.value = 'main'
  ElMessage.success(`已批量添加 ${count} 条`)
}

function handleDefinitionTreeSelect(data: DefinitionDirectoryTreeNode | null) {
  selectedDefinitionTreeKey.value = data?.key ?? DEFINITION_TREE_ROOT_KEY
}

async function handleDefinitionTreeClick(data: DefinitionDirectoryTreeNode) {
  if (data.type === 'request' && data.definitionId) {
    await selectDefinition(data.definitionId)
  }
}

function isDefinitionTreeExpanded(nodeKey: string) {
  return expandedDefinitionTreeKeys.value.includes(nodeKey)
}

function syncDefinitionExpandedKeys(keys?: string[]) {
  if (keys) {
    expandedDefinitionTreeKeys.value = keys
  }
  else {
    expandedDefinitionTreeKeys.value = collectDefinitionExpandableKeys(definitionDirectoryTree.value)
  }
  definitionTreeRenderKey.value += 1
}

function collapseAllDefinitionTreeChildren() {
  syncDefinitionExpandedKeys([DEFINITION_TREE_ROOT_KEY])
}

function expandDefinitionTreeToKey(nodeKey: string) {
  const parts = nodeKey.split(':')
  if (parts.length < 3) {
    expandedDefinitionTreeKeys.value = Array.from(new Set([
      ...expandedDefinitionTreeKeys.value,
      DEFINITION_TREE_ROOT_KEY,
      nodeKey,
    ]))
    return
  }
  const workspaceCode = parts[1]
  const workspaceKey = `definition-workspace:${workspaceCode}`
  const fullPath = parts.slice(2).join(':')
  const segments = fullPath.split('/').filter(Boolean)
  const keys = new Set<string>([DEFINITION_TREE_ROOT_KEY, workspaceKey])
  let assembled = ''
  for (const segment of segments) {
    assembled = assembled ? `${assembled}/${segment}` : segment
    keys.add(`definition-module:${workspaceCode}:${assembled}`)
  }
  expandedDefinitionTreeKeys.value = Array.from(new Set([
    ...expandedDefinitionTreeKeys.value,
    ...keys,
  ]))
}

function handleDefinitionTreeExpand(_: DefinitionDirectoryTreeNode, treeNode: { key: string }) {
  const keys = new Set(expandedDefinitionTreeKeys.value)
  keys.add(String(treeNode.key))
  expandedDefinitionTreeKeys.value = [...keys]
}

function handleDefinitionTreeCollapse(_: DefinitionDirectoryTreeNode, treeNode: { key: string }) {
  const collapsedKey = String(treeNode.key)
  const currentNode = findDefinitionTreeNode(collapsedKey)
  if (!currentNode) {
    expandedDefinitionTreeKeys.value = expandedDefinitionTreeKeys.value.filter(key => key !== collapsedKey)
    return
  }
  const blockedKeys = new Set([collapsedKey, ...collectDefinitionDescendantKeys(currentNode)])
  expandedDefinitionTreeKeys.value = expandedDefinitionTreeKeys.value.filter(key => !blockedKeys.has(key))
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
    const nodeKey = `definition-module:${targetWorkspaceCode}:${fullPath}`
    selectedDefinitionTreeKey.value = nodeKey
    expandDefinitionTreeToKey(nodeKey)
    openOrReuseDraftRequest({
      ...buildEmptyDefinitionDetail(),
      workspaceCode: targetWorkspaceCode,
      directoryName: fullPath,
    })
    ElMessage.success(`已创建子模块：${fullPath}`)
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
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

function syncDefinitionSaveForm() {
  definitionSaveForm.workspaceCode = definitionForm.workspaceCode || selectedDefinitionWorkspaceCode.value || defaultEditableWorkspaceCode()
  definitionSaveForm.name = definitionForm.name.trim()
  definitionSaveForm.path = definitionForm.requestConfig.path.trim()
  definitionSaveForm.directoryName = resolveDefinitionDirectoryName(definitionSaveForm.workspaceCode)
}

function resolveDefinitionDirectoryName(targetWorkspaceCode?: string) {
  const workspace = (targetWorkspaceCode || definitionForm.workspaceCode || selectedDefinitionWorkspaceCode.value || '').trim()
  const selectedPath = selectedDefinitionWorkspaceCode.value === workspace
    ? (selectedDefinitionModulePath.value || '').trim()
    : ''
  const currentPath = (definitionForm.directoryName || '').trim()
  if (!definitionForm.id && selectedPath) {
    return selectedPath
  }
  return currentPath || selectedPath
}

function buildDefinitionMutationPayload() {
  const targetWorkspaceCode = isAllScope.value ? definitionForm.workspaceCode || writableWorkspaces.value[0]?.code : workspaceCode.value
  return {
    workspaceCode: targetWorkspaceCode,
    name: definitionForm.name.trim(),
    directoryName: (definitionForm.directoryName || '').trim() || null,
    description: definitionForm.description,
    tags: definitionForm.tags,
    requestConfig: {
      ...definitionForm.requestConfig,
      path: definitionForm.requestConfig.path.trim(),
      queryParams: prepareKeyValueRowsForPayload(definitionForm.requestConfig.queryParams),
      headers: prepareKeyValueRowsForPayload(definitionForm.requestConfig.headers),
      body: {
        ...definitionForm.requestConfig.body,
        formItems: prepareKeyValueRowsForPayload(definitionForm.requestConfig.body.formItems),
      },
    },
    assertions: definitionForm.assertions,
    extractors: definitionForm.extractors,
    preProcessors: definitionForm.preProcessors,
    postProcessors: definitionForm.postProcessors,
  }
}

function buildCaseMutationPayload(definitionId: number) {
  const targetWorkspaceCode = isAllScope.value ? definitionForm.workspaceCode || writableWorkspaces.value[0]?.code : workspaceCode.value
  return {
    workspaceCode: targetWorkspaceCode,
    definitionId,
    name: definitionForm.name.trim(),
    description: definitionForm.description,
    tags: definitionForm.tags,
    requestConfig: {
      ...definitionForm.requestConfig,
      path: definitionForm.requestConfig.path.trim(),
      queryParams: prepareKeyValueRowsForPayload(definitionForm.requestConfig.queryParams),
      headers: prepareKeyValueRowsForPayload(definitionForm.requestConfig.headers),
      body: {
        ...definitionForm.requestConfig.body,
        formItems: prepareKeyValueRowsForPayload(definitionForm.requestConfig.body.formItems),
      },
    },
    assertions: definitionForm.assertions,
    preProcessors: definitionForm.preProcessors,
    postProcessors: definitionForm.postProcessors,
  }
}

async function ensureCaseDefinitionSaved() {
  if (definitionForm.definitionId) {
    return definitionForm.definitionId
  }
  const payload = buildDefinitionMutationPayload()
  payload.name = (definitionForm.definitionName || definitionForm.name || '').trim()
    || `${definitionForm.requestConfig.method || 'GET'} ${definitionForm.requestConfig.path.trim()}`
  const detail = await platformApi.createApiDefinition(workspaceCode.value, payload)
  definitionForm.definitionId = detail.id
  definitionForm.definitionName = detail.name
  return detail.id
}

async function persistDefinition(options?: { debugAfterSave?: boolean }) {
  const isUpdate = !!definitionForm.id
  saving.value = true
  try {
    definitionForm.name = definitionForm.name.trim()
    definitionForm.requestConfig.path = definitionForm.requestConfig.path.trim()
    syncActiveBodyText()
    definitionForm.directoryName = resolveDefinitionDirectoryName(
      isAllScope.value ? definitionForm.workspaceCode || writableWorkspaces.value[0]?.code : workspaceCode.value,
    )
    const payload = buildDefinitionMutationPayload()
    const detail = isUpdate
      ? await platformApi.updateApiDefinition(workspaceCode.value, definitionForm.id, payload)
      : await platformApi.createApiDefinition(workspaceCode.value, payload)
    ElMessage.success(isUpdate ? '更新成功' : '保存成功')
    definitionSaveDialogVisible.value = false
    await refreshData()
    const latestDetail = toEditorDetailFromDefinition(await platformApi.getApiDefinitionDetail(workspaceCode.value, detail.id))
    applyEditorDetailToForm(latestDetail, { markSaved: true })
    if (options?.debugAfterSave) {
      await debugDefinition()
    }
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function persistCase(options?: { debugAfterSave?: boolean }) {
  const isUpdate = !!definitionForm.id
  saving.value = true
  try {
    definitionForm.name = definitionForm.name.trim()
    definitionForm.requestConfig.path = definitionForm.requestConfig.path.trim()
    syncActiveBodyText()
    definitionForm.directoryName = resolveDefinitionDirectoryName(
      isAllScope.value ? definitionForm.workspaceCode || writableWorkspaces.value[0]?.code : workspaceCode.value,
    )
    const definitionId = await ensureCaseDefinitionSaved()
    const payload = buildCaseMutationPayload(definitionId)
    const detail = isUpdate
      ? await platformApi.updateApiDefinitionCase(workspaceCode.value, definitionForm.id, payload)
      : await platformApi.createApiDefinitionCase(workspaceCode.value, payload)
    ElMessage.success(isUpdate ? '用例已更新' : '用例已保存')
    await refreshData()
    const latestDetail = toEditorDetailFromCase(await platformApi.getApiDefinitionCaseDetail(workspaceCode.value, detail.id))
    applyEditorDetailToForm(latestDetail, { markSaved: true })
    if (options?.debugAfterSave) {
      await debugCase()
    }
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

function openDefinitionSaveDialog() {
  syncDefinitionSaveForm()
  definitionSaveDialogVisible.value = true
}

async function confirmDefinitionSaveDialog() {
  definitionSaveForm.workspaceCode = definitionSaveForm.workspaceCode.trim()
  definitionSaveForm.name = definitionSaveForm.name.trim()
  definitionSaveForm.path = definitionSaveForm.path.trim()
  definitionSaveForm.directoryName = definitionSaveForm.directoryName.trim()
  if (!definitionSaveForm.workspaceCode) {
    ElMessage.warning('请先选择所属空间')
    return
  }
  if (!definitionSaveForm.name || !definitionSaveForm.path) {
    ElMessage.warning('请补全请求名称和请求 URL')
    return
  }
  definitionForm.name = definitionSaveForm.name
  definitionForm.requestConfig.path = definitionSaveForm.path
  definitionForm.path = definitionSaveForm.path
  definitionForm.workspaceCode = definitionSaveForm.workspaceCode
  definitionForm.directoryName = definitionSaveForm.directoryName
  await persistDefinition()
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
  if (definitionForm.id) {
    if (!definitionForm.name.trim() || !definitionForm.requestConfig.path.trim()) {
      ElMessage.warning('\u8bf7\u8865\u5168\u8bf7\u6c42\u540d\u79f0\u548c\u8bf7\u6c42 URL')
      return
    }
    definitionForm.path = definitionForm.requestConfig.path.trim()
    await persistDefinition()
    return
  }
  openDefinitionSaveDialog()
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
  if (!canDebugDefinition.value) {
    ElMessage.warning('请补全请求方法和请求 URL')
    return
  }
  saving.value = true
  try {
    syncActiveBodyText()
    const response = await platformApi.debugApiDefinitionDraft(workspaceCode.value, {
      ...buildDefinitionMutationPayload(),
      definitionId: definitionForm.id || null,
      environmentId: runOptions.environmentId,
      variableSetId: runOptions.variableSetId,
    } satisfies ApiDebugDefinitionPayload)
    const current = activeRequestEditorTab.value
    if (current) {
      current.debugReportId = response.reportId
      current.debugFailureSummary = response.failureSummary || ''
      current.debugStepResults = response.stepResults || []
    }
    if (caseDrawerVisible.value && activeRequestEditorKey.value === caseDrawerEditorKey.value) {
      updateCaseDrawerDebugState(
        response.reportId,
        response.failureSummary || '',
        response.stepResults || [],
      )
      caseDrawerResponsePreviewTab.value = 'body'
    }
    ElMessage.success(response.result === 'SUCCESS' ? '发送成功' : '发送完成')
    await refreshData()
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

function saveDefinitionAsCase() {
  if (activeRequestEditorTab.value?.resourceType !== 'definition' || !definitionForm.id) {
    ElMessage.warning('请先保存接口定义')
    return
  }
  openCaseDraftFromDefinition({ fromSavedDefinition: true })
}

async function saveCase() {
  try {
    ensureScopedTargetWorkspace(definitionForm.workspaceCode)
  }
  catch (error) {
    ElMessage.warning((error as Error).message)
    return
  }
  if (!definitionForm.name.trim() || !definitionForm.requestConfig.path.trim()) {
    ElMessage.warning('请补全用例名称和请求 URL')
    return
  }
  definitionForm.path = definitionForm.requestConfig.path.trim()
  await persistCase()
}

async function debugCase() {
  if (!canDebugDefinition.value) {
    ElMessage.warning('请补全请求方法和请求 URL')
    return
  }
  saving.value = true
  try {
    syncActiveBodyText()
    const response = definitionForm.definitionId
      ? await platformApi.debugApiDefinitionCaseDraft(workspaceCode.value, {
          ...buildCaseMutationPayload(definitionForm.definitionId),
          caseId: definitionForm.id || null,
          environmentId: runOptions.environmentId,
          variableSetId: runOptions.variableSetId,
        } satisfies ApiDebugCasePayload)
      : await platformApi.debugApiDefinitionDraft(workspaceCode.value, {
          ...buildDefinitionMutationPayload(),
          definitionId: null,
          environmentId: runOptions.environmentId,
          variableSetId: runOptions.variableSetId,
        } satisfies ApiDebugDefinitionPayload)
    const current = activeRequestEditorTab.value
    if (current) {
      current.debugReportId = response.reportId
      current.debugFailureSummary = response.failureSummary || ''
      current.debugStepResults = response.stepResults || []
    }
    if (caseDrawerVisible.value && activeRequestEditorKey.value === caseDrawerEditorKey.value) {
      updateCaseDrawerDebugState(
        response.reportId,
        response.failureSummary || '',
        response.stepResults || [],
      )
      caseDrawerResponsePreviewTab.value = 'body'
    }
    ElMessage.success(response.result === 'SUCCESS' ? '发送成功' : '发送完成')
    await refreshData()
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

async function runInCaseDrawerContext(action: () => Promise<void>) {
  const drawerTab = activeCaseDrawerEditorTab.value
  if (!drawerTab) {
    return
  }
  const mainSnapshot = cloneEditorDetail(definitionForm)
  const mainActiveEditorKey = activeRequestEditorKey.value
  const mainRequestTab = activeRequestTab.value
  const mainSelectedDefinitionId = selectedDefinitionId.value

  requestEditorSyncing.value = true
  activeRequestEditorKey.value = drawerTab.key
  activeRequestTab.value = caseDrawerRequestTab.value
  Object.assign(definitionForm, cloneEditorDetail(caseDrawerForm))
  requestEditorSyncing.value = false

  try {
    await action()
    applyCaseDrawerDetailToForm(cloneEditorDetail(definitionForm), { markSaved: true })
  }
  finally {
    requestEditorSyncing.value = true
    Object.assign(definitionForm, mainSnapshot)
    activeRequestEditorKey.value = mainActiveEditorKey
    activeRequestTab.value = mainRequestTab
    selectedDefinitionId.value = mainSelectedDefinitionId
    requestEditorSyncing.value = false
  }
}

async function saveCaseDrawer() {
  await runInCaseDrawerContext(async () => {
    await saveCase()
  })
}

async function debugCaseDrawer() {
  await runInCaseDrawerContext(async () => {
    await debugCase()
  })
}

async function saveActiveEditor() {
  if (activeRequestEditorTab.value?.resourceType === 'case') {
    await saveCase()
    return
  }
  await saveDefinition()
}

async function debugActiveEditor() {
  if (activeRequestEditorTab.value?.resourceType === 'case') {
    await debugCase()
    return
  }
  await debugDefinition()
}

async function removeActiveEditor() {
  if (activeRequestEditorTab.value?.resourceType === 'case') {
    await removeCase(definitionForm.id || null)
    return
  }
  await removeDefinition()
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
  if (scenarioForm.steps.some(step => !step.resourceId)) {
    ElMessage.warning('请为每个步骤选择接口或用例')
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
      steps: scenarioForm.steps.map(step => ({
        stepName: step.stepName,
        resourceType: step.resourceType,
        resourceId: step.resourceId,
        enabled: step.enabled !== false,
      })),
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
    environmentForm.authConfig = normalizeAuthConfig(environmentForm.authConfig)
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
  responsePreviewTab.value = 'body'
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
  environmentForm.authConfig = normalizeAuthConfig(environmentForm.authConfig)
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
  await debugActiveEditor()
}

function duplicateDefinition() {
  const snapshot = cloneEditorDetail(definitionForm)
  const duplicated = Object.assign(buildEmptyDefinitionDetail(), snapshot, {
    resourceType: 'definition',
    id: 0,
    definitionId: 0,
    definitionName: '',
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
  ElMessage.info('下一步可以继续接入 Curl 导入，这一版先把工作台主体落稳')
}

function updateTagInput(target: { tags: string[] }, value: string) {
  target.tags = value.split(',').map(item => item.trim()).filter(Boolean)
}

function readTagInput(tags: string[]) {
  return tags.join(', ')
}

function formatCaseTags(tags?: string[] | null) {
  return tags?.length ? tags.join(', ') : '-'
}

function caseProtocolLabel() {
  return 'HTTP'
}

function casePriorityLabel(item: ApiDefinitionCaseItem) {
  const meta = item as ApiDefinitionCaseItem & { casePriority?: string | null, priority?: string | null }
  return meta.casePriority || meta.priority || '-'
}

function caseStatusLabel(item: ApiDefinitionCaseItem) {
  const meta = item as ApiDefinitionCaseItem & { caseStatus?: string | null, status?: string | null }
  return meta.caseStatus || meta.status || '-'
}

function updateCaseListPageSize(size: number) {
  caseListSettings.updatePageSize(size)
  caseListCurrentPage.value = 1
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
              <el-input v-model="definitionFilters.keyword" placeholder="请输入模块或请求名称" clearable />
              <el-button v-if="canCreateInCurrentScope" type="primary" @click="openNewRequestTab()">新建请求</el-button>
            </div>
            <div class="ms-like-directory-shell">
              <el-tree
                :key="definitionTreeRenderKey"
                :data="definitionDirectoryTree"
                node-key="key"
                :default-expanded-keys="expandedDefinitionTreeKeys"
                highlight-current
                :expand-on-click-node="false"
                :current-node-key="selectedDefinitionTreeKey"
                class="ms-like-directory-tree"
                @current-change="handleDefinitionTreeSelect"
                @node-click="handleDefinitionTreeClick"
                @node-expand="handleDefinitionTreeExpand"
                @node-collapse="handleDefinitionTreeCollapse"
              >
                <template #default="{ data }">
                  <div :class="['ms-like-directory-node', { 'is-root': data.type === 'root', 'is-request': data.type === 'request' }]">
                    <div class="ms-like-directory-main">
                      <span
                        v-if="data.type === 'workspace'"
                        :class="['tree-node-folder-svg', { 'is-open': isDefinitionTreeExpanded(data.key) }]"
                        aria-hidden="true"
                      >
                        <el-icon class="tree-node-folder-icon">
                          <FolderOpened v-if="isDefinitionTreeExpanded(data.key)" />
                          <Folder v-else />
                        </el-icon>
                      </span>
                      <template v-if="data.type === 'request'">
                        <span :class="['ms-like-method', `method-${(data.method || 'GET').toLowerCase()}`]">{{ data.method }}</span>
                        <span v-overflow-title="data.label" class="ms-like-tree-item-name">{{ data.label }}</span>
                      </template>
                      <template v-else>
                        <span v-overflow-title="data.label" class="ms-like-directory-label">{{ data.label }}</span>
                        <span class="ms-like-directory-count">{{ data.count }}</span>
                      </template>
                    </div>
                    <div v-if="data.type !== 'request'" class="ms-like-directory-actions" @click.stop>
                      <el-button
                        v-if="data.type === 'root'"
                        text
                        class="tree-icon-button"
                        title="\u6536\u8d77\u5168\u90e8\u5b50\u6a21\u5757"
                        @click.stop="collapseAllDefinitionTreeChildren"
                      >
                        <el-icon class="tree-collapse-icon"><Fold /></el-icon>
                      </el-button>
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
          </aside>

          <section class="ms-like-main">
            <div class="ms-like-tab-strip">
              <button
                v-for="item in visibleRequestEditorTabs"
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
                <span v-if="requestEditorTabs.length > 1" class="ms-like-editor-tab-close" @click.stop="closeRequestEditorTab(item.key)">
                  <el-icon><Close /></el-icon>
                </span>
              </button>
              <button type="button" class="ms-like-tab-add" @click="openNewRequestTab()">+</button>
            </div>

            <div class="ms-like-editor-shell">
              <div v-if="isAllScope && !definitionForm.workspaceCode" class="scope-hint">
                &#24403;&#21069;&#22788;&#20110; ALL &#35270;&#35282;&#65292;&#35831;&#20808;&#22312;&#39030;&#37096;&#36873;&#25321;&#30446;&#26631;&#31354;&#38388;&#21518;&#20877;&#20445;&#23384;&#25110;&#35843;&#35797;&#12290;
              </div>

              <div class="ms-like-request-row">
                <el-select
                  v-model="definitionForm.requestConfig.method"
                  :class="['request-method-select', requestMethodClass(definitionForm.requestConfig.method)]"
                  popper-class="request-method-popper"
                >
                  <el-option
                    v-for="method in requestMethodOptions"
                    :key="method"
                    :label="method"
                    :value="method"
                  >
                    <span :class="['request-method-option', requestMethodClass(method)]">{{ method }}</span>
                  </el-option>
                </el-select>
                <el-input v-model="definitionForm.requestConfig.path" class="request-url-input" placeholder="&#35831;&#36755;&#20837;&#21253;&#21547; http/https &#30340;&#23436;&#25972; URL &#25110;&#25509;&#21475;&#36335;&#24452;" data-testid="definition-url-input" />
                <el-button @click="promptImportCurl">Curl</el-button>
                <el-button type="primary" :disabled="!canDebugDefinition" :loading="saving" @click="debugActiveEditor">
                  &#21457;&#36865;
                </el-button>
                <el-dropdown split-button :disabled="!canWriteDefinition" :loading="saving" data-testid="definition-save-button" @click="saveActiveEditor">
                  &#20445;&#23384;
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-if="activeRequestEditorTab?.resourceType === 'definition' && !!definitionForm.id" @click="saveDefinitionAsCase">保存为用例</el-dropdown-item>
                      <el-dropdown-item v-if="activeRequestEditorTab?.resourceType === 'definition'" @click="duplicateDefinition">&#22797;&#21046;&#25509;&#21475;</el-dropdown-item>
                      <el-dropdown-item @click="removeActiveEditor">{{ activeRequestEditorTab?.resourceType === 'case' ? '删除用例' : '删除接口' }}</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>

              <div class="ms-like-top-tabs">
                <button :class="['ms-like-top-tab', { active: activeRequestTab === 'headers' }]" @click="setActiveRequestContentTab('headers')">&#35831;&#27714;&#22836;</button>
                <button :class="['ms-like-top-tab', { active: activeRequestTab === 'body' }]" @click="setActiveRequestContentTab('body')">&#35831;&#27714;&#20307;</button>
                <button :class="['ms-like-top-tab', { active: activeRequestTab === 'params' }]" @click="setActiveRequestContentTab('params')">
                  Params
                  <span v-if="queryEnabledCount" class="ms-like-tab-badge">{{ queryEnabledCount }}</span>
                </button>
                <button :class="['ms-like-top-tab', { active: activeRequestTab === 'auth' }]" @click="setActiveRequestContentTab('auth')">Auth</button>
                <button data-testid="request-tab-pre" :class="['ms-like-top-tab', { active: activeRequestTab === 'pre' }]" @click="setActiveRequestContentTab('pre')">前置处理</button>
                <button data-testid="request-tab-post" :class="['ms-like-top-tab', { active: activeRequestTab === 'post' }]" @click="setActiveRequestContentTab('post')">后置处理</button>
                <button data-testid="request-tab-tests" :class="['ms-like-top-tab', { active: activeRequestTab === 'tests' }]" @click="setActiveRequestContentTab('tests')">断言</button>
                <button :class="['ms-like-top-tab', { active: activeRequestTab === 'settings' }]" @click="setActiveRequestContentTab('settings')">&#35774;&#32622;</button>
                <button v-if="activeRequestEditorTab?.resourceType === 'definition'" :class="['ms-like-top-tab', { active: activeRequestTab === 'cases' }]" @click="setActiveRequestContentTab('cases')">用例</button>
              </div>

              <div v-if="showCaseListContent" class="ms-like-request-body">
                <div class="request-section case-list-panel">
                  <div class="editor-actions left">
                    <el-button type="primary" @click="openCaseDraftFromDefinition()">新建用例</el-button>
                  </div>
                  <div v-if="!currentDefinitionCases.length" class="empty-hint">当前接口下还没有用例</div>
                  <div v-else class="case-list-table-wrap">
                  <el-table :data="pagedDefinitionCases" size="small" class="case-list-table">
                    <el-table-column v-if="visibleCaseListColumnKeys.has('id')" prop="id" label="ID" width="92" />
                    <el-table-column v-if="visibleCaseListColumnKeys.has('name')" prop="name" label="用例名称" min-width="200" show-overflow-tooltip />
                    <el-table-column v-if="visibleCaseListColumnKeys.has('protocol')" label="协议" width="90">
                      <template #default>
                        {{ caseProtocolLabel() }}
                      </template>
                    </el-table-column>
                    <el-table-column v-if="visibleCaseListColumnKeys.has('priority')" label="用例等级" width="100">
                      <template #default="{ row }">
                        {{ casePriorityLabel(row) }}
                      </template>
                    </el-table-column>
                    <el-table-column v-if="visibleCaseListColumnKeys.has('status')" label="状态" width="110">
                      <template #default="{ row }">
                        {{ caseStatusLabel(row) }}
                      </template>
                    </el-table-column>
                    <el-table-column v-if="visibleCaseListColumnKeys.has('path')" prop="path" label="路径" min-width="240" show-overflow-tooltip />
                    <el-table-column v-if="visibleCaseListColumnKeys.has('tags')" label="标签" min-width="160" show-overflow-tooltip>
                      <template #default="{ row }">
                        {{ formatCaseTags(row.tags) }}
                      </template>
                    </el-table-column>
                    <el-table-column v-if="visibleCaseListColumnKeys.has('creator')" label="创建人" width="110">
                      <template #default>-</template>
                    </el-table-column>
                    <el-table-column width="148" fixed="right" align="center" header-align="center">
                      <template #header>
                        <div class="case-list-operation-header">
                          <span>操作</span>
                          <el-button text class="table-settings-trigger case-list-settings-trigger" @click="caseListSettings.settingsVisible.value = true">
                            <el-icon><Setting /></el-icon>
                          </el-button>
                        </div>
                      </template>
                      <template #default="{ row }">
                        <div class="case-list-actions">
                          <el-button text type="primary" size="small" class="case-list-action-button" @click="openCaseEditor(row.id)">编辑</el-button>
                          <el-button text size="small" type="primary" @click="runCaseItem(row.id)">执行</el-button>
                          <el-dropdown trigger="click" placement="bottom-end">
                            <el-button text type="primary" size="small" class="case-list-more-button">
                              <el-icon><MoreFilled /></el-icon>
                            </el-button>
                            <template #dropdown>
                              <el-dropdown-menu class="case-list-more-menu">
                                <el-dropdown-item class="case-list-menu-item" @click="duplicateCaseItem(row.id)">复制</el-dropdown-item>
                                <el-dropdown-item class="case-list-menu-item is-danger" @click="removeCase(row.id)">删除</el-dropdown-item>
                              </el-dropdown-menu>
                            </template>
                          </el-dropdown>
                        </div>
                      </template>
                    </el-table-column>
                  </el-table>
                  <div class="case-list-pagination">
                    <div class="case-list-pagination-summary">共 {{ currentDefinitionCases.length }} 条 / {{ caseListTotalPages }} 页</div>
                    <el-pagination
                      v-model:current-page="caseListCurrentPage"
                      :page-size="caseListSettings.pageSize.value"
                      :page-sizes="[...CASE_LIST_PAGE_SIZE_OPTIONS]"
                      size="small"
                      layout="sizes, prev, pager, next"
                      :total="currentDefinitionCases.length"
                      @size-change="updateCaseListPageSize"
                    />
                  </div>
                  </div>
                </div>
              </div>

              <div v-else class="ms-like-request-body">
                <template v-if="activeRequestTab === 'params'">
                  <div class="request-section ms-like-table-surface ms-like-param-table ms-like-param-table--query">
                    <div class="ms-like-table-header ms-like-param-table-grid ms-like-param-table-grid--query">
                      <div class="ms-like-drag-cell"></div>
                      <div class="ms-like-checkbox-cell ms-like-checkbox-cell--header">
                        <el-checkbox
                          v-model="queryTableSelectionModel"
                          :indeterminate="tableSelectionState(definitionForm.requestConfig.queryParams).indeterminate"
                        />
                      </div>
                      <span class="ms-like-header-input-title">Query 参数</span>
                      <span>类型</span>
                      <span>参数值</span>
                      <span>编码</span>
                      <span>描述</span>
                      <button type="button" class="ms-like-link-button" @click="openBatchAddDrawer('query')">批量添加</button>
                    </div>
                    <div
                      v-for="(row, index) in definitionForm.requestConfig.queryParams"
                      :key="`query-${index}`"
                      :class="['ms-like-table-row', 'ms-like-param-table-grid', 'ms-like-param-table-grid--query', { 'is-dragging': isParamRowDragging('query', index), 'is-drag-over': isParamRowDragOver('query', index) }]"
                      @dragover="handleParamDragOver('query', index, $event)"
                      @drop="handleParamDrop('query', index, $event)"
                    >
                      <div class="ms-like-drag-cell">
                        <button
                          type="button"
                          class="ms-like-drag-handle"
                          draggable="true"
                          aria-label="拖拽排序"
                          @dragstart="handleParamDragStart('query', index, $event)"
                          @dragend="handleParamDragEnd"
                        >
                          <span v-for="dotIndex in 6" :key="`query-dot-${index}-${dotIndex}`" class="ms-like-drag-dot"></span>
                        </button>
                      </div>
                      <div class="ms-like-checkbox-cell">
                        <el-checkbox v-model="row.enabled" />
                      </div>
                      <div class="ms-like-name-field">
                        <button
                          type="button"
                          :class="['ms-like-required-button', { active: row.required }]"
                          @click="row.required = !row.required"
                        >
                          *
                        </button>
                        <el-input
                          v-model="row.key"
                          placeholder="参数名称"
                          @input="handleKeyValueRowInput(definitionForm.requestConfig.queryParams, queryParamDefaults())"
                        />
                      </div>
                      <el-select v-model="row.paramType" @change="handleKeyValueRowInput(definitionForm.requestConfig.queryParams, queryParamDefaults())">
                        <el-option v-for="option in queryParamTypeOptions" :key="option" :label="option" :value="option" />
                      </el-select>
                      <el-input
                        v-model="row.value"
                        placeholder="参数值 / {{variable}}"
                        @input="handleKeyValueRowInput(definitionForm.requestConfig.queryParams, queryParamDefaults())"
                      />
                      <div class="ms-like-switch-cell ms-like-switch-cell--query">
                        <el-switch v-model="row.encode" size="small" />
                      </div>
                      <el-input
                        v-model="row.description"
                        placeholder="描述"
                        @input="handleKeyValueRowInput(definitionForm.requestConfig.queryParams, queryParamDefaults())"
                      />
                      <button type="button" class="ms-like-row-remove" @click="removeKeyValueRow(definitionForm.requestConfig.queryParams, index, queryParamDefaults())">删除</button>
                    </div>
                      <button type="button" class="ms-like-add-row" @click="addDefinitionRow(definitionForm.requestConfig.queryParams)">+ 添加一行</button>
                  </div>
                </template>

                <template v-else-if="activeRequestTab === 'headers'">
                  <div class="request-section ms-like-table-surface ms-like-param-table ms-like-param-table--header">
                    <div class="ms-like-table-header ms-like-param-table-grid ms-like-param-table-grid--header">
                      <div class="ms-like-drag-cell"></div>
                      <div class="ms-like-checkbox-cell ms-like-checkbox-cell--header">
                        <el-checkbox
                          v-model="headerTableSelectionModel"
                          :indeterminate="tableSelectionState(definitionForm.requestConfig.headers).indeterminate"
                        />
                      </div>
                      <span class="ms-like-header-input-title">参数名称</span>
                      <span>参数值</span>
                      <span>描述</span>
                      <button type="button" class="ms-like-link-button" @click="openBatchAddDrawer('header')">批量添加</button>
                    </div>
                    <div
                      v-for="(row, index) in definitionForm.requestConfig.headers"
                      :key="`header-${index}`"
                      :class="['ms-like-table-row', 'ms-like-param-table-grid', 'ms-like-param-table-grid--header', { 'is-dragging': isParamRowDragging('header', index), 'is-drag-over': isParamRowDragOver('header', index) }]"
                      @dragover="handleParamDragOver('header', index, $event)"
                      @drop="handleParamDrop('header', index, $event)"
                    >
                      <div class="ms-like-drag-cell">
                        <button
                          type="button"
                          class="ms-like-drag-handle"
                          draggable="true"
                          aria-label="拖拽排序"
                          @dragstart="handleParamDragStart('header', index, $event)"
                          @dragend="handleParamDragEnd"
                      >
                          <span v-for="dotIndex in 6" :key="`header-dot-${index}-${dotIndex}`" class="ms-like-drag-dot"></span>
                        </button>
                      </div>
                      <div class="ms-like-checkbox-cell">
                        <el-checkbox v-model="row.enabled" />
                      </div>
                      <div class="ms-like-header-input-cell">
                        <el-input
                          v-model="row.key"
                          placeholder="参数名称"
                          @input="handleKeyValueRowInput(definitionForm.requestConfig.headers, headerParamDefaults())"
                        />
                      </div>
                      <el-input
                        v-model="row.value"
                        placeholder="参数值"
                        @input="handleKeyValueRowInput(definitionForm.requestConfig.headers, headerParamDefaults())"
                      />
                      <el-input
                        v-model="row.description"
                        placeholder="描述"
                        @input="handleKeyValueRowInput(definitionForm.requestConfig.headers, headerParamDefaults())"
                      />
                      <button type="button" class="ms-like-row-remove" @click="removeKeyValueRow(definitionForm.requestConfig.headers, index, headerParamDefaults())">删除</button>
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
                      <button :class="['ms-like-body-chip', { active: isBodyMode('RAW_XML') }]" @click="setBodyMode('RAW_XML')">xml</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('RAW_TEXT') }]" @click="setBodyMode('RAW_TEXT')">raw</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('BINARY') }]" @click="setBodyMode('BINARY')">binary</button>
                    </div>
                    <div class="ms-like-body-mode-shell">
                    <MonacoCodeEditor
                      v-if="['RAW_JSON', 'RAW_XML', 'RAW_TEXT'].includes(definitionForm.requestConfig.body.type)"
                      v-model="activeBodyRawText"
                      :language="activeBodyLanguage"
                      height="300px"
                    />
                    <div v-else-if="definitionForm.requestConfig.body.type === 'BINARY'" class="request-section ms-like-form-panel">
                      <div class="ms-like-form-row">
                        <div class="ms-like-form-label">File</div>
                        <div class="ms-like-form-control ms-like-binary-actions">
                          <el-button @click="pickBinaryBodyFile">
                            {{ definitionForm.requestConfig.body.fileName ? '&#37325;&#26032;&#36873;&#25321;' : '&#36873;&#25321;&#25991;&#20214;' }}
                          </el-button>
                          <el-button :disabled="!definitionForm.requestConfig.body.binaryBase64" @click="clearBinaryBodyFile">&#28165;&#31354;</el-button>
                        </div>
                      </div>
                      <div class="ms-like-form-row">
                        <div class="ms-like-form-label">&#24050;&#36873;&#25991;&#20214;</div>
                        <div class="empty-hint">
                          <template v-if="definitionForm.requestConfig.body.fileName">
                            <span class="binary-file-name">{{ definitionForm.requestConfig.body.fileName }}</span>
                            <span v-if="binaryBodySizeLabel" class="binary-file-size">{{ binaryBodySizeLabel }}</span>
                          </template>
                          <template v-else>
                            &#23578;&#26410;&#36873;&#25321;&#20108;&#36827;&#21046;&#25991;&#20214;
                          </template>
                        </div>
                      </div>
                    </div>
                    <div
                      v-else-if="['FORM_URLENCODED', 'FORM_DATA'].includes(definitionForm.requestConfig.body.type)"
                      class="body-form-grid ms-like-table-surface ms-like-param-table ms-like-param-table--body-form"
                    >
                      <div class="ms-like-table-header ms-like-param-table-grid ms-like-param-table-grid--body-form">
                        <div class="ms-like-drag-cell"></div>
                        <div class="ms-like-checkbox-cell ms-like-checkbox-cell--header">
                          <el-checkbox
                            v-model="bodyFormTableSelectionModel"
                            :indeterminate="tableSelectionState(definitionForm.requestConfig.body.formItems).indeterminate"
                          />
                        </div>
                        <span class="ms-like-header-input-title">参数名称</span>
                        <span>类型</span>
                        <span>参数值</span>
                        <span>描述</span>
                        <button type="button" class="ms-like-link-button" @click="openBatchAddDrawer('body-form')">批量添加</button>
                      </div>
                      <div
                        v-for="(row, index) in definitionForm.requestConfig.body.formItems"
                        :key="`body-${index}`"
                        :class="['ms-like-table-row', 'ms-like-param-table-grid', 'ms-like-param-table-grid--body-form', { 'is-dragging': isParamRowDragging('body-form', index), 'is-drag-over': isParamRowDragOver('body-form', index) }]"
                        @dragover="handleParamDragOver('body-form', index, $event)"
                        @drop="handleParamDrop('body-form', index, $event)"
                      >
                        <div class="ms-like-drag-cell">
                          <button
                            type="button"
                            class="ms-like-drag-handle"
                            draggable="true"
                            aria-label="拖拽排序"
                            @dragstart="handleParamDragStart('body-form', index, $event)"
                            @dragend="handleParamDragEnd"
                          >
                            <span v-for="dotIndex in 6" :key="`body-dot-${index}-${dotIndex}`" class="ms-like-drag-dot"></span>
                          </button>
                        </div>
                        <div class="ms-like-checkbox-cell">
                          <el-checkbox v-model="row.enabled" />
                        </div>
                        <div class="ms-like-name-field">
                          <button
                            type="button"
                            :class="['ms-like-required-button', { active: row.required }]"
                            @click="row.required = !row.required"
                          >
                            *
                          </button>
                          <el-input
                            v-model="row.key"
                            placeholder="参数名称"
                            @input="handleKeyValueRowInput(definitionForm.requestConfig.body.formItems, bodyFormParamDefaults())"
                          />
                        </div>
                        <el-select v-model="row.paramType" @change="handleKeyValueRowInput(definitionForm.requestConfig.body.formItems, bodyFormParamDefaults())">
                          <el-option v-for="option in bodyParamTypeOptions" :key="option" :label="option" :value="option" />
                        </el-select>
                        <el-input
                          v-model="row.value"
                          placeholder="参数值"
                          @input="handleKeyValueRowInput(definitionForm.requestConfig.body.formItems, bodyFormParamDefaults())"
                        />
                        <el-input
                          v-model="row.description"
                          placeholder="描述"
                          @input="handleKeyValueRowInput(definitionForm.requestConfig.body.formItems, bodyFormParamDefaults())"
                        />
                        <button type="button" class="ms-like-row-remove" @click="removeKeyValueRow(definitionForm.requestConfig.body.formItems, index, bodyFormParamDefaults())">删除</button>
                      </div>
                      <button type="button" class="ms-like-add-row" @click="definitionForm.requestConfig.body.formItems.push(emptyKeyValue(bodyFormParamDefaults()))">+ 添加一行</button>
                    </div>
                    <div v-else class="ms-like-empty-body">&#35831;&#27714;&#27809;&#26377; Body</div>
                    </div>
                  </div>
                </template>

                <template v-else-if="activeRequestTab === 'pre'">
                  <div class="request-section" data-testid="pre-processors-section">
                    <ApiProcessorEditor
                      v-model="definitionForm.preProcessors"
                      v-model:active-id="activePreProcessorId"
                      stage="pre"
                      :db-connections="dbConnections"
                      :latest-response="currentResponseStep?.response ?? null"
                    />
                  </div>
                </template>

                <template v-else-if="activeRequestTab === 'post'">
                  <div class="request-section" data-testid="post-processors-section">
                    <ApiProcessorEditor
                      v-model="definitionForm.postProcessors"
                      v-model:active-id="activePostProcessorId"
                      stage="post"
                      :db-connections="dbConnections"
                      :latest-response="currentResponseStep?.response ?? null"
                    />
                  </div>
                </template>

                <template v-else-if="activeRequestTab === 'tests'">
                  <div class="request-section" data-testid="assertions-section">
                    <ApiAssertionEditor
                      v-model="definitionForm.assertions"
                      v-model:active-id="activeAssertionId"
                      :latest-response="currentResponseStep?.response ?? null"
                    />
                    <div class="editor-actions left">
                      <el-button text type="primary" @click="openBatchAddDrawer('assertion')">&#25209;&#37327;&#28155;&#21152;</el-button>
                    </div>
                  </div>
                </template>

                <template v-else-if="activeRequestTab === 'auth'">
                  <div class="request-section">
                    <div class="ms-auth-panel">
                      <div class="ms-auth-panel-title">&#35748;&#35777;&#26041;&#24335;</div>
                      <el-radio-group v-model="definitionForm.requestConfig.authConfig.authType" class="ms-auth-radio-group">
                        <el-radio-button
                          v-for="option in requestAuthTypeOptions"
                          :key="option.value"
                          :value="option.value"
                        >
                          {{ option.label }}
                        </el-radio-button>
                      </el-radio-group>
                      <div
                        v-if="definitionForm.requestConfig.authConfig.authType === 'BASIC'"
                        class="ms-auth-form"
                      >
                        <div class="ms-auth-form-item">
                          <label class="ms-auth-form-label">Username</label>
                          <el-input
                            v-model="definitionForm.requestConfig.authConfig.basicAuth.userName"
                            placeholder="username"
                            class="ms-auth-form-control"
                          />
                        </div>
                        <div class="ms-auth-form-item">
                          <label class="ms-auth-form-label">Password</label>
                          <el-input
                            v-model="definitionForm.requestConfig.authConfig.basicAuth.password"
                            placeholder="password"
                            class="ms-auth-form-control"
                            show-password
                          />
                        </div>
                      </div>
                      <div
                        v-else-if="definitionForm.requestConfig.authConfig.authType === 'DIGEST'"
                        class="ms-auth-form"
                      >
                        <div class="ms-auth-form-item">
                          <label class="ms-auth-form-label">Username</label>
                          <el-input
                            v-model="definitionForm.requestConfig.authConfig.digestAuth.userName"
                            placeholder="username"
                            class="ms-auth-form-control"
                          />
                        </div>
                        <div class="ms-auth-form-item">
                          <label class="ms-auth-form-label">Password</label>
                          <el-input
                            v-model="definitionForm.requestConfig.authConfig.digestAuth.password"
                            placeholder="password"
                            class="ms-auth-form-control"
                            show-password
                          />
                        </div>
                      </div>
                    </div>
                  </div>
                </template>

                <template v-else>
                  <div class="request-section ms-like-form-panel">
                    <div class="ms-like-form-row" data-testid="definition-name-input">
                      <div class="ms-like-form-label">&#25509;&#21475;&#21517;&#31216;</div>
                      <el-input v-model="definitionForm.name" class="ms-like-form-control" placeholder="&#25509;&#21475;&#21517;&#31216;" />
                    </div>
                    <div class="ms-like-form-row">
                      <div class="ms-like-form-label">&#27169;&#22359; / &#30446;&#24405;</div>
                      <el-input v-model="definitionForm.directoryName" class="ms-like-form-control" placeholder="&#27169;&#22359; / &#30446;&#24405;" />
                    </div>
                    <div class="ms-like-form-row">
                      <div class="ms-like-form-label">&#26631;&#31614;</div>
                      <el-input
                        :model-value="readTagInput(definitionForm.tags)"
                        class="ms-like-form-control"
                        placeholder="&#26631;&#31614;&#65292;&#36887;&#21495;&#20998;&#38548;"
                        @update:model-value="(value: string | number) => updateTagInput(definitionForm, String(value))"
                      />
                    </div>
                    <div class="ms-like-form-row">
                      <div class="ms-like-form-label">&#36229;&#26102;&#26102;&#38388;</div>
                      <el-input-number v-model="definitionForm.requestConfig.timeoutMs" :min="1000" :step="1000" class="ms-like-form-control full-width" />
                    </div>
                    <div class="ms-like-form-row align-start">
                      <div class="ms-like-form-label">&#25551;&#36848;</div>
                      <el-input v-model="definitionForm.description" class="ms-like-form-control" type="textarea" :rows="4" placeholder="&#25509;&#21475;&#25551;&#36848;&#12289;&#35843;&#29992;&#32422;&#26463;&#25110;&#22791;&#27880;" />
                    </div>
                    <div class="ms-like-settings-hint">
                      <span>&#20889;&#20837;&#31354;&#38388; {{ currentDefinitionWorkspaceLabel }}</span>
                      <span>&#35843;&#35797;&#19978;&#19979;&#25991; {{ currentEnvironmentName }} / {{ currentVariableSetName }}</span>
                      <span>&#26368;&#21518;&#36816;&#34892; {{ formatTimeLabel(definitionForm.lastRunAt) }}</span>
                    </div>
                  </div>
                </template>
              </div>

            <div v-if="shouldShowResponsePanel" class="ms-like-response-shell">
              <div class="ms-like-response-header">
                <div class="ms-like-response-title">响应内容</div>
                <div v-if="!showResponseEmptyState" class="ms-like-response-metrics">
                  <span>状态 {{ currentResponseStatusCode ?? '-' }}</span>
                  <span>耗时 {{ currentResponseDuration ?? '-' }}<template v-if="currentResponseDuration !== null"> ms</template></span>
                  <span>大小 {{ currentResponseSize }}</span>
                </div>
              </div>

              <div v-if="currentDebugError" class="response-error-banner">
                {{ currentDebugError }}
              </div>

              <div v-if="showResponseEmptyState" class="ms-like-response-empty">
                <div class="ms-like-response-empty-card">
                  <div class="ms-like-response-empty-visual">
                    <div class="ms-like-response-empty-window">
                      <span></span>
                      <span></span>
                      <span></span>
                    </div>
                  </div>
                  <div class="ms-like-response-empty-text">点击 <span>发送</span> 获取响应内容</div>
                </div>
              </div>
              <template v-else>
                <div class="ms-like-response-tabs">
                  <button :class="['ms-like-top-tab', { active: responsePreviewTab === 'body' }]" @click="responsePreviewTab = 'body'">Body</button>
                  <button :class="['ms-like-top-tab', { active: responsePreviewTab === 'header' }]" @click="responsePreviewTab = 'header'">Header</button>
                  <button :class="['ms-like-top-tab', { active: responsePreviewTab === 'console' }]" @click="responsePreviewTab = 'console'">控制台</button>
                  <button :class="['ms-like-top-tab', { active: responsePreviewTab === 'actualRequest' }]" @click="responsePreviewTab = 'actualRequest'">实际请求</button>
                </div>

                <div class="ms-like-response-body">
                    <MonacoCodeEditor
                      v-if="responsePreviewTab === 'body'"
                      :model-value="responseBodyPreview"
                      :language="responseBodyLanguage"
                      :read-only="true"
                      :show-format-button="false"
                      :fit-content="true"
                      :max-fit-content-height="1000"
                      height="100%"
                    />
                    <MonacoCodeEditor
                      v-else-if="responsePreviewTab === 'header'"
                      :model-value="responseHeadersPreview"
                      language="json"
                      :read-only="true"
                      :show-format-button="false"
                      :fit-content="true"
                      :max-fit-content-height="1000"
                      height="100%"
                    />
                    <MonacoCodeEditor
                      v-else-if="responsePreviewTab === 'console'"
                      :model-value="responseConsolePreview"
                      language="text"
                      :read-only="true"
                      :show-format-button="false"
                      :fit-content="true"
                      :max-fit-content-height="1000"
                      height="100%"
                    />
                    <MonacoCodeEditor
                      v-else-if="responsePreviewTab === 'actualRequest'"
                      :model-value="actualRequestPreview"
                      language="json"
                      :read-only="true"
                      :show-format-button="false"
                      :fit-content="true"
                      :max-fit-content-height="1000"
                      height="100%"
                    />
                </div>
              </template>
            </div>

            </div>
          </section>

          <ApiCaseDrawer
            v-if="caseDrawerVisible"
            :model-value="caseDrawerVisible"
            :title="caseDrawerTitle"
            :subtitle="caseDrawerSubtitle"
            :summary-name="caseDrawerSubtitle"
            :method="caseDrawerMethod"
            :path="caseDrawerPath"
            :case-name="caseDrawerForm.name"
            :priority="caseDrawerForm.casePriority || 'P0'"
            :priority-options="casePriorityOptions"
            :status="caseDrawerForm.caseStatus || '进行中'"
            :status-options="caseStatusOptions"
            :tags-input="caseDrawerTagsInput"
            :can-debug="canDebugCaseDrawer"
            :can-write="canWriteCaseDrawer"
            :saving="saving"
            :is-edit="!!caseDrawerForm.id"
            @request-close="closeCaseDrawer"
            @update:case-name="value => caseDrawerForm.name = value"
            @update:priority="value => caseDrawerForm.casePriority = value"
            @update:status="value => caseDrawerForm.caseStatus = value"
            @update:tags-input="value => caseDrawerTagsInput = value"
            @debug="debugCaseDrawer"
            @create="saveCaseDrawer"
            @save="saveCaseDrawer"
          >
            <template #tabs>
              <div class="ms-like-top-tabs case-drawer-top-tabs">
                <button :class="['ms-like-top-tab', { active: caseDrawerRequestTab === 'headers' }]" @click="setCaseDrawerRequestContentTab('headers')">请求头</button>
                <button :class="['ms-like-top-tab', { active: caseDrawerRequestTab === 'body' }]" @click="setCaseDrawerRequestContentTab('body')">请求体</button>
                <button :class="['ms-like-top-tab', { active: caseDrawerRequestTab === 'params' }]" @click="setCaseDrawerRequestContentTab('params')">
                  Params
                  <span v-if="caseDrawerQueryEnabledCount" class="ms-like-tab-badge">{{ caseDrawerQueryEnabledCount }}</span>
                </button>
                <button :class="['ms-like-top-tab', { active: caseDrawerRequestTab === 'auth' }]" @click="setCaseDrawerRequestContentTab('auth')">Auth</button>
                <button :class="['ms-like-top-tab', { active: caseDrawerRequestTab === 'pre' }]" @click="setCaseDrawerRequestContentTab('pre')">前置处理</button>
                <button :class="['ms-like-top-tab', { active: caseDrawerRequestTab === 'post' }]" @click="setCaseDrawerRequestContentTab('post')">后置处理</button>
                <button :class="['ms-like-top-tab', { active: caseDrawerRequestTab === 'tests' }]" @click="setCaseDrawerRequestContentTab('tests')">断言</button>
                <button :class="['ms-like-top-tab', { active: caseDrawerRequestTab === 'settings' }]" @click="setCaseDrawerRequestContentTab('settings')">设置</button>
              </div>
            </template>

            <template #body>
              <div class="ms-like-request-body">
                <template v-if="caseDrawerRequestTab === 'params'">
                  <div class="request-section ms-like-table-surface ms-like-param-table ms-like-param-table--query">
                    <div class="ms-like-table-header ms-like-param-table-grid ms-like-param-table-grid--query">
                      <div class="ms-like-drag-cell"></div>
                      <div class="ms-like-checkbox-cell ms-like-checkbox-cell--header">
                        <el-checkbox
                          v-model="caseDrawerQueryTableSelectionModel"
                          :indeterminate="tableSelectionState(caseDrawerForm.requestConfig.queryParams).indeterminate"
                        />
                      </div>
                      <span class="ms-like-header-input-title">Query 参数</span>
                      <span>类型</span>
                      <span>参数值</span>
                      <span>编码</span>
                      <span>描述</span>
                      <button type="button" class="ms-like-link-button" @click="openBatchAddDrawer('query', 'case')">批量添加</button>
                    </div>
                    <div
                      v-for="(row, index) in caseDrawerForm.requestConfig.queryParams"
                      :key="`case-query-${index}`"
                      :class="['ms-like-table-row', 'ms-like-param-table-grid', 'ms-like-param-table-grid--query', { 'is-dragging': isParamRowDragging('query', index), 'is-drag-over': isParamRowDragOver('query', index) }]"
                      @dragover="handleParamDragOver('query', index, $event)"
                      @drop="handleParamDrop('query', index, $event)"
                    >
                      <div class="ms-like-drag-cell">
                        <button
                          type="button"
                          class="ms-like-drag-handle"
                          draggable="true"
                          aria-label="拖拽排序"
                          @dragstart="handleParamDragStart('query', index, $event)"
                          @dragend="handleParamDragEnd"
                        >
                          <span v-for="dotIndex in 6" :key="`case-query-dot-${index}-${dotIndex}`" class="ms-like-drag-dot"></span>
                        </button>
                      </div>
                      <div class="ms-like-checkbox-cell">
                        <el-checkbox v-model="row.enabled" />
                      </div>
                      <div class="ms-like-name-field">
                        <button
                          type="button"
                          :class="['ms-like-required-button', { active: row.required }]"
                          @click="row.required = !row.required"
                        >
                          *
                        </button>
                        <el-input
                          v-model="row.key"
                          placeholder="参数名称"
                          @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.queryParams, queryParamDefaults())"
                        />
                      </div>
                      <el-select v-model="row.paramType" @change="handleKeyValueRowInput(caseDrawerForm.requestConfig.queryParams, queryParamDefaults())">
                        <el-option v-for="option in queryParamTypeOptions" :key="option" :label="option" :value="option" />
                      </el-select>
                      <el-input
                        v-model="row.value"
                        placeholder="参数值 / {{variable}}"
                        @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.queryParams, queryParamDefaults())"
                      />
                      <div class="ms-like-switch-cell ms-like-switch-cell--query">
                        <el-switch v-model="row.encode" size="small" />
                      </div>
                      <el-input
                        v-model="row.description"
                        placeholder="描述"
                        @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.queryParams, queryParamDefaults())"
                      />
                      <button type="button" class="ms-like-row-remove" @click="removeKeyValueRow(caseDrawerForm.requestConfig.queryParams, index, queryParamDefaults())">删除</button>
                    </div>
                    <button type="button" class="ms-like-add-row" @click="addDefinitionRow(caseDrawerForm.requestConfig.queryParams)">+ 添加一行</button>
                  </div>
                </template>

                <template v-else-if="caseDrawerRequestTab === 'headers'">
                  <div class="request-section ms-like-table-surface ms-like-param-table ms-like-param-table--header">
                    <div class="ms-like-table-header ms-like-param-table-grid ms-like-param-table-grid--header">
                      <div class="ms-like-drag-cell"></div>
                      <div class="ms-like-checkbox-cell ms-like-checkbox-cell--header">
                        <el-checkbox
                          v-model="caseDrawerHeaderTableSelectionModel"
                          :indeterminate="tableSelectionState(caseDrawerForm.requestConfig.headers).indeterminate"
                        />
                      </div>
                      <span class="ms-like-header-input-title">参数名称</span>
                      <span>参数值</span>
                      <span>描述</span>
                      <button type="button" class="ms-like-link-button" @click="openBatchAddDrawer('header', 'case')">批量添加</button>
                    </div>
                    <div
                      v-for="(row, index) in caseDrawerForm.requestConfig.headers"
                      :key="`case-header-${index}`"
                      :class="['ms-like-table-row', 'ms-like-param-table-grid', 'ms-like-param-table-grid--header', { 'is-dragging': isParamRowDragging('header', index), 'is-drag-over': isParamRowDragOver('header', index) }]"
                      @dragover="handleParamDragOver('header', index, $event)"
                      @drop="handleParamDrop('header', index, $event)"
                    >
                      <div class="ms-like-drag-cell">
                        <button
                          type="button"
                          class="ms-like-drag-handle"
                          draggable="true"
                          aria-label="拖拽排序"
                          @dragstart="handleParamDragStart('header', index, $event)"
                          @dragend="handleParamDragEnd"
                        >
                          <span v-for="dotIndex in 6" :key="`case-header-dot-${index}-${dotIndex}`" class="ms-like-drag-dot"></span>
                        </button>
                      </div>
                      <div class="ms-like-checkbox-cell">
                        <el-checkbox v-model="row.enabled" />
                      </div>
                      <div class="ms-like-header-input-cell">
                        <el-input
                          v-model="row.key"
                          placeholder="参数名称"
                          @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.headers, headerParamDefaults())"
                        />
                      </div>
                      <el-input
                        v-model="row.value"
                        placeholder="参数值"
                        @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.headers, headerParamDefaults())"
                      />
                      <el-input
                        v-model="row.description"
                        placeholder="描述"
                        @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.headers, headerParamDefaults())"
                      />
                      <button type="button" class="ms-like-row-remove" @click="removeKeyValueRow(caseDrawerForm.requestConfig.headers, index, headerParamDefaults())">删除</button>
                    </div>
                    <button type="button" class="ms-like-add-row" @click="addDefinitionRow(caseDrawerForm.requestConfig.headers)">+ 添加一行</button>
                  </div>
                </template>

                <template v-else-if="caseDrawerRequestTab === 'body'">
                  <div class="request-section">
                    <div class="ms-like-body-type-row">
                      <button :class="['ms-like-body-chip', { active: isBodyMode('NONE', caseDrawerForm) }]" @click="setBodyMode('NONE', caseDrawerForm)">none</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('FORM_DATA', caseDrawerForm) }]" @click="setBodyMode('FORM_DATA', caseDrawerForm)">form-data</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('FORM_URLENCODED', caseDrawerForm) }]" @click="setBodyMode('FORM_URLENCODED', caseDrawerForm)">x-www-form-urlencoded</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('RAW_JSON', caseDrawerForm) }]" @click="setBodyMode('RAW_JSON', caseDrawerForm)">json</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('RAW_XML', caseDrawerForm) }]" @click="setBodyMode('RAW_XML', caseDrawerForm)">xml</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('RAW_TEXT', caseDrawerForm) }]" @click="setBodyMode('RAW_TEXT', caseDrawerForm)">raw</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('BINARY', caseDrawerForm) }]" @click="setBodyMode('BINARY', caseDrawerForm)">binary</button>
                    </div>
                    <div class="ms-like-body-mode-shell">
                      <MonacoCodeEditor
                        v-if="['RAW_JSON', 'RAW_XML', 'RAW_TEXT'].includes(caseDrawerForm.requestConfig.body.type)"
                        v-model="caseDrawerActiveBodyRawText"
                        :language="caseDrawerActiveBodyLanguage"
                        class="case-drawer-body-editor"
                        height="300px"
                      />
                      <div v-else-if="caseDrawerForm.requestConfig.body.type === 'BINARY'" class="request-section ms-like-form-panel">
                        <div class="ms-like-form-row">
                          <div class="ms-like-form-label">File</div>
                          <div class="ms-like-form-control ms-like-binary-actions">
                            <el-button @click="pickCaseDrawerBinaryBodyFile">
                              {{ caseDrawerForm.requestConfig.body.fileName ? '重新选择' : '选择文件' }}
                            </el-button>
                            <el-button :disabled="!caseDrawerForm.requestConfig.body.binaryBase64" @click="clearCaseDrawerBinaryBodyFile">清空</el-button>
                          </div>
                        </div>
                        <div class="ms-like-form-row">
                          <div class="ms-like-form-label">已选文件</div>
                          <div class="empty-hint">
                            <template v-if="caseDrawerForm.requestConfig.body.fileName">
                              <span class="binary-file-name">{{ caseDrawerForm.requestConfig.body.fileName }}</span>
                              <span v-if="caseDrawerBinaryBodySizeLabel" class="binary-file-size">{{ caseDrawerBinaryBodySizeLabel }}</span>
                            </template>
                            <template v-else>
                              尚未选择二进制文件
                            </template>
                          </div>
                        </div>
                      </div>
                      <div
                        v-else-if="['FORM_URLENCODED', 'FORM_DATA'].includes(caseDrawerForm.requestConfig.body.type)"
                        class="body-form-grid ms-like-table-surface ms-like-param-table ms-like-param-table--body-form"
                      >
                        <div class="ms-like-table-header ms-like-param-table-grid ms-like-param-table-grid--body-form">
                          <div class="ms-like-drag-cell"></div>
                          <div class="ms-like-checkbox-cell ms-like-checkbox-cell--header">
                            <el-checkbox
                              v-model="caseDrawerBodyFormTableSelectionModel"
                              :indeterminate="tableSelectionState(caseDrawerForm.requestConfig.body.formItems).indeterminate"
                            />
                          </div>
                          <span class="ms-like-header-input-title">参数名称</span>
                          <span>类型</span>
                          <span>参数值</span>
                          <span>描述</span>
                          <button type="button" class="ms-like-link-button" @click="openBatchAddDrawer('body-form', 'case')">批量添加</button>
                        </div>
                        <div
                          v-for="(row, index) in caseDrawerForm.requestConfig.body.formItems"
                          :key="`case-body-${index}`"
                          :class="['ms-like-table-row', 'ms-like-param-table-grid', 'ms-like-param-table-grid--body-form', { 'is-dragging': isParamRowDragging('body-form', index), 'is-drag-over': isParamRowDragOver('body-form', index) }]"
                          @dragover="handleParamDragOver('body-form', index, $event)"
                          @drop="handleParamDrop('body-form', index, $event)"
                        >
                          <div class="ms-like-drag-cell">
                            <button
                              type="button"
                              class="ms-like-drag-handle"
                              draggable="true"
                              aria-label="拖拽排序"
                              @dragstart="handleParamDragStart('body-form', index, $event)"
                              @dragend="handleParamDragEnd"
                            >
                              <span v-for="dotIndex in 6" :key="`case-body-dot-${index}-${dotIndex}`" class="ms-like-drag-dot"></span>
                            </button>
                          </div>
                          <div class="ms-like-checkbox-cell">
                            <el-checkbox v-model="row.enabled" />
                          </div>
                          <div class="ms-like-name-field">
                            <button
                              type="button"
                              :class="['ms-like-required-button', { active: row.required }]"
                              @click="row.required = !row.required"
                            >
                              *
                            </button>
                            <el-input
                              v-model="row.key"
                              placeholder="参数名称"
                              @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.body.formItems, bodyFormParamDefaults())"
                            />
                          </div>
                          <el-select v-model="row.paramType" @change="handleKeyValueRowInput(caseDrawerForm.requestConfig.body.formItems, bodyFormParamDefaults())">
                            <el-option v-for="option in bodyParamTypeOptions" :key="option" :label="option" :value="option" />
                          </el-select>
                          <el-input
                            v-model="row.value"
                            placeholder="参数值"
                            @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.body.formItems, bodyFormParamDefaults())"
                          />
                          <el-input
                            v-model="row.description"
                            placeholder="描述"
                            @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.body.formItems, bodyFormParamDefaults())"
                          />
                          <button type="button" class="ms-like-row-remove" @click="removeKeyValueRow(caseDrawerForm.requestConfig.body.formItems, index, bodyFormParamDefaults())">删除</button>
                        </div>
                        <button type="button" class="ms-like-add-row" @click="caseDrawerForm.requestConfig.body.formItems.push(emptyKeyValue(bodyFormParamDefaults()))">+ 添加一行</button>
                      </div>
                      <div v-else class="ms-like-empty-body">请求没有 Body</div>
                    </div>
                  </div>
                </template>

                <template v-else-if="caseDrawerRequestTab === 'pre'">
                  <div class="request-section" data-testid="pre-processors-section">
                    <ApiProcessorEditor
                      v-model="caseDrawerForm.preProcessors"
                      v-model:active-id="activePreProcessorId"
                      stage="pre"
                      :db-connections="dbConnections"
                      :latest-response="caseDrawerResponseStep?.response ?? null"
                    />
                  </div>
                </template>

                <template v-else-if="caseDrawerRequestTab === 'post'">
                  <div class="request-section" data-testid="post-processors-section">
                    <ApiProcessorEditor
                      v-model="caseDrawerForm.postProcessors"
                      v-model:active-id="activePostProcessorId"
                      stage="post"
                      :db-connections="dbConnections"
                      :latest-response="caseDrawerResponseStep?.response ?? null"
                    />
                  </div>
                </template>

                <template v-else-if="caseDrawerRequestTab === 'tests'">
                  <div class="request-section" data-testid="assertions-section">
                    <ApiAssertionEditor
                      v-model="caseDrawerForm.assertions"
                      v-model:active-id="activeAssertionId"
                      :latest-response="caseDrawerResponseStep?.response ?? null"
                    />
                    <div class="editor-actions left">
                      <el-button text type="primary" @click="openBatchAddDrawer('assertion', 'case')">批量添加</el-button>
                    </div>
                  </div>
                </template>

                <template v-else-if="caseDrawerRequestTab === 'auth'">
                  <div class="request-section">
                    <div class="ms-auth-panel">
                      <div class="ms-auth-panel-title">认证方式</div>
                      <el-radio-group v-model="caseDrawerForm.requestConfig.authConfig.authType" class="ms-auth-radio-group">
                        <el-radio-button
                          v-for="option in requestAuthTypeOptions"
                          :key="option.value"
                          :value="option.value"
                        >
                          {{ option.label }}
                        </el-radio-button>
                      </el-radio-group>
                      <div
                        v-if="caseDrawerForm.requestConfig.authConfig.authType === 'BASIC'"
                        class="ms-auth-form"
                      >
                        <div class="ms-auth-form-item">
                          <label class="ms-auth-form-label">Username</label>
                          <el-input
                            v-model="caseDrawerForm.requestConfig.authConfig.basicAuth.userName"
                            placeholder="username"
                            class="ms-auth-form-control"
                          />
                        </div>
                        <div class="ms-auth-form-item">
                          <label class="ms-auth-form-label">Password</label>
                          <el-input
                            v-model="caseDrawerForm.requestConfig.authConfig.basicAuth.password"
                            placeholder="password"
                            class="ms-auth-form-control"
                            show-password
                          />
                        </div>
                      </div>
                      <div
                        v-else-if="caseDrawerForm.requestConfig.authConfig.authType === 'DIGEST'"
                        class="ms-auth-form"
                      >
                        <div class="ms-auth-form-item">
                          <label class="ms-auth-form-label">Username</label>
                          <el-input
                            v-model="caseDrawerForm.requestConfig.authConfig.digestAuth.userName"
                            placeholder="username"
                            class="ms-auth-form-control"
                          />
                        </div>
                        <div class="ms-auth-form-item">
                          <label class="ms-auth-form-label">Password</label>
                          <el-input
                            v-model="caseDrawerForm.requestConfig.authConfig.digestAuth.password"
                            placeholder="password"
                            class="ms-auth-form-control"
                            show-password
                          />
                        </div>
                      </div>
                    </div>
                  </div>
                </template>

                <template v-else>
                  <div class="request-section ms-like-form-panel">
                    <div class="ms-like-form-row" data-testid="definition-name-input">
                      <div class="ms-like-form-label">接口名称</div>
                      <el-input v-model="caseDrawerForm.name" class="ms-like-form-control" placeholder="接口名称" />
                    </div>
                    <div class="ms-like-form-row">
                      <div class="ms-like-form-label">模块 / 目录</div>
                      <el-input v-model="caseDrawerForm.directoryName" class="ms-like-form-control" placeholder="模块 / 目录" />
                    </div>
                    <div class="ms-like-form-row">
                      <div class="ms-like-form-label">标签</div>
                      <el-input
                        :model-value="readTagInput(caseDrawerForm.tags)"
                        class="ms-like-form-control"
                        placeholder="标签，逗号分隔"
                        @update:model-value="(value: string | number) => updateTagInput(caseDrawerForm, String(value))"
                      />
                    </div>
                    <div class="ms-like-form-row">
                      <div class="ms-like-form-label">超时时间</div>
                      <el-input-number v-model="caseDrawerForm.requestConfig.timeoutMs" :min="1000" :step="1000" class="ms-like-form-control full-width" />
                    </div>
                    <div class="ms-like-form-row align-start">
                      <div class="ms-like-form-label">描述</div>
                      <el-input v-model="caseDrawerForm.description" class="ms-like-form-control" type="textarea" :rows="4" placeholder="接口描述、调用约束或备注" />
                    </div>
                    <div class="ms-like-settings-hint">
                      <span>写入空间 {{ currentDefinitionWorkspaceLabel }}</span>
                      <span>调试上下文 {{ currentEnvironmentName }} / {{ currentVariableSetName }}</span>
                      <span>最后运行 {{ formatTimeLabel(caseDrawerForm.lastRunAt) }}</span>
                    </div>
                  </div>
                </template>
              </div>
            </template>

            <template #response>
              <div class="ms-like-response-shell case-drawer-response-shell">
                <div class="ms-like-response-header">
                  <div class="ms-like-response-title">响应内容</div>
                  <div v-if="!caseDrawerShowResponseEmptyState" class="ms-like-response-metrics">
                    <span>状态 {{ caseDrawerResponseStatusCode ?? '-' }}</span>
                    <span>耗时 {{ caseDrawerResponseDuration ?? '-' }}<template v-if="caseDrawerResponseDuration !== null"> ms</template></span>
                    <span>大小 {{ caseDrawerResponseSize }}</span>
                  </div>
                </div>

                <div v-if="caseDrawerDebugError" class="response-error-banner">
                  {{ caseDrawerDebugError }}
                </div>

                <div v-if="caseDrawerShowResponseEmptyState" class="ms-like-response-empty">
                  <div class="ms-like-response-empty-card">
                    <div class="ms-like-response-empty-visual">
                      <div class="ms-like-response-empty-window">
                        <span></span>
                        <span></span>
                        <span></span>
                      </div>
                    </div>
                    <div class="ms-like-response-empty-text">点击 <span>发送</span> 获取响应内容</div>
                  </div>
                </div>
                <template v-else>
                  <div class="ms-like-response-tabs">
                    <button :class="['ms-like-top-tab', { active: caseDrawerResponsePreviewTab === 'body' }]" @click="caseDrawerResponsePreviewTab = 'body'">Body</button>
                    <button :class="['ms-like-top-tab', { active: caseDrawerResponsePreviewTab === 'header' }]" @click="caseDrawerResponsePreviewTab = 'header'">Header</button>
                    <button :class="['ms-like-top-tab', { active: caseDrawerResponsePreviewTab === 'console' }]" @click="caseDrawerResponsePreviewTab = 'console'">控制台</button>
                    <button :class="['ms-like-top-tab', { active: caseDrawerResponsePreviewTab === 'actualRequest' }]" @click="caseDrawerResponsePreviewTab = 'actualRequest'">实际请求</button>
                  </div>

                  <div class="ms-like-response-body">
                    <MonacoCodeEditor
                      v-if="caseDrawerResponsePreviewTab === 'body'"
                      :model-value="caseDrawerResponseBodyPreview"
                      :language="caseDrawerResponseBodyLanguage"
                      :read-only="true"
                      :show-format-button="false"
                      :fit-content="true"
                      :max-fit-content-height="1000"
                      height="100%"
                    />
                    <MonacoCodeEditor
                      v-else-if="caseDrawerResponsePreviewTab === 'header'"
                      :model-value="caseDrawerResponseHeadersPreview"
                      language="json"
                      :read-only="true"
                      :show-format-button="false"
                      :fit-content="true"
                      :max-fit-content-height="1000"
                      height="100%"
                    />
                    <MonacoCodeEditor
                      v-else-if="caseDrawerResponsePreviewTab === 'console'"
                      :model-value="caseDrawerResponseConsolePreview"
                      language="text"
                      :read-only="true"
                      :show-format-button="false"
                      :fit-content="true"
                      :max-fit-content-height="1000"
                      height="100%"
                    />
                    <MonacoCodeEditor
                      v-else-if="caseDrawerResponsePreviewTab === 'actualRequest'"
                      :model-value="caseDrawerActualRequestPreview"
                      language="json"
                      :read-only="true"
                      :show-format-button="false"
                      :fit-content="true"
                      :max-fit-content-height="1000"
                      height="100%"
                    />
                  </div>
                </template>
              </div>
            </template>
          </ApiCaseDrawer>
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
                <div class="editor-subtitle">按顺序编排接口链路，可选择默认环境与变量集。</div>
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
                <el-input v-model="scenarioForm.directoryName" placeholder="鐩綍" />
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
                  <el-select v-model="step.resourceType" placeholder="资源类型">
                    <el-option label="用例" value="CASE" />
                    <el-option label="接口" value="DEFINITION" />
                  </el-select>
                  <el-select v-model="step.resourceId" :placeholder="step.resourceType === 'CASE' ? '选择用例' : '选择接口'">
                    <el-option
                      v-for="item in step.resourceType === 'CASE' ? caseOptions : definitionOptions"
                      :key="`${step.resourceType}-${item.value}`"
                      :label="item.label"
                      :value="item.value"
                    />
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
                <div class="ms-auth-panel ms-auth-panel--compact">
                  <div class="ms-auth-panel-title">认证方式</div>
                  <el-radio-group v-model="environmentForm.authConfig.authType" class="ms-auth-radio-group">
                    <el-radio-button
                      v-for="option in requestAuthTypeOptions"
                      :key="`env-${option.value}`"
                      :value="option.value"
                    >
                      {{ option.label }}
                    </el-radio-button>
                  </el-radio-group>
                  <div v-if="environmentForm.authConfig.authType === 'BASIC'" class="ms-auth-form ms-auth-form--compact">
                    <div class="ms-auth-form-item">
                      <label class="ms-auth-form-label">Username</label>
                      <el-input v-model="environmentForm.authConfig.basicAuth.userName" placeholder="username" class="ms-auth-form-control" />
                    </div>
                    <div class="ms-auth-form-item">
                      <label class="ms-auth-form-label">Password</label>
                      <el-input v-model="environmentForm.authConfig.basicAuth.password" placeholder="password" class="ms-auth-form-control" show-password />
                    </div>
                  </div>
                  <div v-else-if="environmentForm.authConfig.authType === 'DIGEST'" class="ms-auth-form ms-auth-form--compact">
                    <div class="ms-auth-form-item">
                      <label class="ms-auth-form-label">Username</label>
                      <el-input v-model="environmentForm.authConfig.digestAuth.userName" placeholder="username" class="ms-auth-form-control" />
                    </div>
                    <div class="ms-auth-form-item">
                      <label class="ms-auth-form-label">Password</label>
                      <el-input v-model="environmentForm.authConfig.digestAuth.password" placeholder="password" class="ms-auth-form-control" show-password />
                    </div>
                  </div>
                </div>
                <el-select v-model="environmentForm.status">
                  <el-option label="启用" :value="1" />
                  <el-option label="停用" :value="0" />
                </el-select>
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

      <el-drawer v-model="batchAddDrawerVisible" :title="batchAddTitle" size="560px" destroy-on-close>
      <div class="batch-drawer">
        <div class="batch-drawer-hint">
          <div class="batch-drawer-label">格式示例</div>
          <div v-for="item in batchAddExamples" :key="item" class="batch-drawer-example">{{ item }}</div>
          <div class="batch-drawer-note">空行会自动忽略；同名重复时以最后一条为准。</div>
        </div>
        <el-input
          v-model="batchAddInput"
          type="textarea"
          :rows="18"
          class="batch-drawer-textarea"
          :placeholder="batchAddPlaceholder"
        />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="batchAddDrawerVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmBatchAdd">确认添加</el-button>
        </div>
      </template>
      </el-drawer>

      <el-dialog v-model="definitionSaveDialogVisible" title="保存" width="520px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item v-if="isAllScope" label="所属空间" required>
          <el-select v-model="definitionSaveForm.workspaceCode" class="full-width" placeholder="请选择空间">
            <el-option
              v-for="item in writableWorkspaces"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="请求名称" required>
          <el-input v-model="definitionSaveForm.name" placeholder="请输入请求名称" />
        </el-form-item>
        <el-form-item label="请求 URL" required>
          <el-input v-model="definitionSaveForm.path" placeholder="请输入请求 URL" />
        </el-form-item>
        <el-form-item label="请求所属模块">
          <el-select v-model="definitionSaveForm.directoryName" clearable placeholder="请选择请求所属模块" class="full-width">
            <el-option :label="selectedDefinitionWorkspaceRootLabel" value="" />
            <el-option
              v-for="item in selectedDefinitionWorkspaceModules"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="definitionSaveDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="confirmDefinitionSaveDialog()">确定</el-button>
        </div>
      </template>
    </el-dialog>

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
            <div class="snapshot-grid">
              <div>
                <div class="snapshot-title">处理器结果</div>
                <pre>{{ JSON.stringify(item.processorResults, null, 2) }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>

    <TableSettingsDrawer
      v-model="caseListSettings.settingsVisible.value"
      :columns="caseListSettings.settingsColumns.value.map(column => ({
        key: column.key,
        label: column.label,
        required: column.required,
        visible: column.required ? true : visibleCaseListColumnKeys.has(column.key),
        draggable: caseListSettings.canDragColumn(column.key),
      }))"
      :page-size-enabled="true"
      :page-size="caseListSettings.pageSizeDisplay.value"
      :page-size-options="[...CASE_LIST_PAGE_SIZE_OPTIONS]"
      :dragging-key="caseListSettings.draggingColumnKey.value"
      @page-size-change="updateCaseListPageSize"
      @toggle-column="caseListSettings.toggleColumnVisibility"
      @drag-start="caseListSettings.handleDragStart"
      @drag-end="caseListSettings.handleDragEnd"
      @drop-column="caseListSettings.moveColumnToTarget"
      @reset="caseListSettings.reset"
    />
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

.ms-like-layout .ms-like-main {
  overflow: auto;
}

.ms-like-sidebar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
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
  flex: 1;
  min-height: 0;
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

.ms-like-directory-tree :deep(.el-tree-node__expand-icon.is-leaf) {
  color: transparent;
}

.ms-like-directory-tree :deep(.el-tree-node > .el-tree-node__content:has(.ms-like-directory-node.is-root) .el-tree-node__expand-icon) {
  visibility: hidden;
  pointer-events: none;
}

.ms-like-directory-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: #f3e8ff;
}

.ms-like-directory-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-width: 0;
  gap: 8px;
}

.ms-like-directory-main {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.tree-node-folder-svg {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 17px;
  height: 17px;
}

.tree-node-folder-icon {
  font-size: 16px;
  color: #d7a12b;
}

.tree-node-folder-svg.is-open .tree-node-folder-icon {
  color: #c98312;
}

.ms-like-directory-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex: 0 0 auto;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.ms-like-directory-node:hover .ms-like-directory-actions,
.ms-like-directory-node.is-root .ms-like-directory-actions {
  opacity: 1;
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

.ms-like-directory-node.is-root .ms-like-directory-label {
  font-weight: 700;
  color: #101828;
}

.ms-like-directory-count {
  color: var(--el-text-color-secondary);
}

.ms-like-directory-node.is-root .ms-like-directory-count {
  color: var(--text-subtle, #667085);
}

.tree-icon-button {
  width: 24px;
  height: 24px;
  padding: 0;
}

.tree-collapse-icon {
  font-size: 14px;
  color: var(--text-subtle, #667085);
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
  color: #f97316;
}

.ms-like-method.method-put,
.ms-like-method.method-options,
.ms-like-method.method-head {
  color: #3b82f6;
}

.ms-like-method.method-patch {
  color: #ec4899;
}

.ms-like-method.method-delete {
  color: #dc2626;
}

.ms-like-method.method-trace {
  color: #8b5cf6;
}

.request-method-select {
  width: 112px;
  min-width: 112px;
  flex: 0 0 112px;
}

.request-method-option {
  font-weight: 600;
}

.request-method-get {
  color: #16a34a;
}

.request-method-post {
  color: #f97316;
}

.request-method-put,
.request-method-options,
.request-method-head {
  color: #3b82f6;
}

.request-method-patch {
  color: #ec4899;
}

.request-method-delete {
  color: #dc2626;
}

.request-method-trace {
  color: #8b5cf6;
}

.request-method-select :deep(.el-select__wrapper) {
  min-height: 36px;
}

.request-method-select :deep(.el-select__selected-item) {
  font-weight: 600;
}

.request-method-select.request-method-get :deep(.el-select__selected-item) {
  color: #16a34a;
}

.request-method-select.request-method-post :deep(.el-select__selected-item) {
  color: #f97316;
}

.request-method-select.request-method-put :deep(.el-select__selected-item),
.request-method-select.request-method-options :deep(.el-select__selected-item),
.request-method-select.request-method-head :deep(.el-select__selected-item) {
  color: #3b82f6;
}

.request-method-select.request-method-delete :deep(.el-select__selected-item) {
  color: #dc2626;
}

.request-method-select.request-method-patch :deep(.el-select__selected-item) {
  color: #ec4899;
}

.request-method-select.request-method-trace :deep(.el-select__selected-item) {
  color: #8b5cf6;
}

:deep(.request-method-popper .el-select-dropdown__item) {
  font-weight: 600;
}

:deep(.request-method-popper .request-method-get) {
  color: #16a34a;
}

:deep(.request-method-popper .request-method-post) {
  color: #f97316;
}

:deep(.request-method-popper .request-method-put),
:deep(.request-method-popper .request-method-options),
:deep(.request-method-popper .request-method-head) {
  color: #3b82f6;
}

:deep(.request-method-popper .request-method-delete) {
  color: #dc2626;
}

:deep(.request-method-popper .request-method-patch) {
  color: #ec4899;
}

:deep(.request-method-popper .request-method-trace) {
  color: #8b5cf6;
}

.case-drawer-method-tag.request-method-get {
  color: #16a34a;
}

.case-drawer-method-tag.request-method-post {
  color: #f97316;
}

.case-drawer-method-tag.request-method-put,
.case-drawer-method-tag.request-method-options,
.case-drawer-method-tag.request-method-head {
  color: #3b82f6;
}

.case-drawer-method-tag.request-method-delete {
  color: #dc2626;
}

.case-drawer-method-tag.request-method-patch {
  color: #ec4899;
}

.case-drawer-method-tag.request-method-trace {
  color: #8b5cf6;
}

.ms-like-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: #fff;
}

.ms-like-main > * {
  min-height: 0;
}

.case-drawer-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 20px 14px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: #fff;
}

.case-drawer-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.case-drawer-title {
  font-size: 17px;
  font-weight: 600;
  color: #101828;
}

.case-drawer-subtitle {
  font-size: 13px;
  color: #667085;
}

.case-drawer-close {
  flex: 0 0 auto;
}

.case-drawer-summary-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 0 2px;
}

.case-drawer-summary-main {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.case-drawer-summary-name {
  font-size: 15px;
  font-weight: 600;
  color: #101828;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.case-drawer-summary-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.case-drawer-method-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 52px;
  height: 28px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid currentColor;
  background: #fff;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
}

.case-drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 14px 20px 18px;
  border-top: 1px solid var(--el-border-color-light);
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
  background: #ffffff;
  color: #4b5563;
  padding: 10px 14px;
  font-size: 13px;
}

.ms-like-editor-tab.active,
.ms-like-editor-tab:hover {
  background: #f3e8ff;
  color: #7c3aed;
}

.ms-like-editor-tab.active .ms-like-editor-tab-close {
  opacity: 0.42;
  background: transparent;
  color: #667085;
}

.ms-like-editor-tab:hover .ms-like-editor-tab-close {
  opacity: 1;
  background: rgba(15, 23, 42, 0.08);
  color: #344054;
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
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-left: 6px;
  border-radius: 999px;
  color: #667085;
  opacity: 0;
  flex: 0 0 auto;
  transition: opacity 0.16s ease, background-color 0.16s ease, color 0.16s ease;
}

.ms-like-editor-tab-close:hover {
  background: rgba(124, 58, 237, 0.14);
  color: #7c3aed;
  opacity: 1;
}

.ms-like-editor-tab-close :deep(.el-icon) {
  font-size: 14px;
  font-weight: 700;
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
  padding: 12px 16px 16px;
  flex: 0 0 auto;
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
  overflow: visible;
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
  color: #165dff;
}

.ms-like-top-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: #165dff;
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
  overflow: visible;
  background: #fff;
  padding: 0;
}

.ms-like-body-type-row {
  display: flex;
  align-items: center;
  gap: 0;
  margin-bottom: 4px;
}

.ms-like-body-mode-shell {
  min-height: 300px;
}

.ms-like-body-mode-shell > .ms-monaco-editor,
.ms-like-body-mode-shell > .ms-like-table-surface,
.ms-like-body-mode-shell > .ms-like-form-panel,
.ms-like-body-mode-shell > .ms-like-empty-body {
  width: 100%;
}

.ms-like-body-mode-shell > .ms-like-table-surface,
.ms-like-body-mode-shell > .ms-like-form-panel {
  min-height: 300px;
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
  min-height: 300px;
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

.request-section.ms-like-table-surface,
.request-section.ms-like-form-panel,
.request-section.body-form-grid {
  gap: 0;
}

.ms-like-table-header,
.ms-like-table-row,
.ms-like-param-table-grid {
  display: grid;
  align-items: center;
}

.ms-like-param-table-grid--query {
  grid-template-columns: 24px 32px minmax(220px, 1.25fr) 130px minmax(260px, 1.2fr) 80px minmax(220px, 1fr) 80px;
}

.ms-like-param-table-grid--header {
  grid-template-columns: 24px 32px repeat(3, minmax(0, 1fr)) 80px;
}

.ms-like-param-table-grid--body-form {
  grid-template-columns: 24px 32px minmax(220px, 1.15fr) 130px minmax(220px, 1.05fr) minmax(180px, 1fr) 80px;
}

.ms-like-table-header {
  min-height: 30px;
  border-bottom: 1px solid #ebeef5;
  color: #606266;
  font-size: 12px;
  padding-right: 10px;
}

.ms-like-param-table .ms-like-table-header {
  color: #7d7d7f;
  font-size: 14px;
  font-weight: 500;
  line-height: 22px;
}

.ms-like-check-cell,
.ms-like-checkbox-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ms-like-name-field,
.ms-like-header-name-cell,
.ms-like-checkbox-cell {
  display: flex;
  align-items: center;
  min-width: 0;
}

.ms-like-name-field {
  position: relative;
}

.ms-like-checkbox-cell {
  justify-content: center;
}

.ms-like-checkbox-cell--header {
  justify-content: center;
  padding-left: 0;
}

.ms-like-check-cell {
  grid-column: 1 / 3;
  padding-left: 6px;
}

.ms-like-header-input-title {
  display: inline-flex;
  align-items: center;
  padding-left: 0;
}

.ms-like-param-table--query .ms-like-header-input-title,
.ms-like-param-table--body-form .ms-like-header-input-title {
  padding-left: 28px;
}

.ms-like-param-table .ms-like-header-input-title,
.ms-like-param-table .ms-like-table-header > span {
  color: #7d7d7f;
  font-size: 14px;
  font-weight: 500;
  line-height: 22px;
}

.ms-like-header-input-cell {
  min-width: 0;
}

.ms-like-table-row {
  min-height: 28px;
  border-bottom: 1px solid #f2f3f5;
  padding: 1px 10px 1px 0;
  transition: background-color 0.15s ease;
}

.ms-like-table-row:last-of-type {
  border-bottom: 0;
}

.ms-like-table-row:hover {
  background: #fafbfc;
}

.ms-like-table-row.is-dragging {
  opacity: 0.6;
}

.ms-like-table-row.is-drag-over {
  background: #f5f3ff;
}

.ms-like-drag-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  user-select: none;
}

.ms-like-drag-handle {
  display: grid;
  grid-template-columns: repeat(2, 3px);
  grid-template-rows: repeat(3, 3px);
  gap: 2px;
  width: 14px;
  height: 19px;
  align-content: center;
  justify-content: center;
  border: 0;
  padding: 0;
  background: transparent;
  cursor: grab;
}

.ms-like-drag-handle:active {
  cursor: grabbing;
}

.ms-like-drag-dot {
  width: 3px;
  height: 3px;
  border-radius: 999px;
  background: #c0c4cc;
}

.ms-like-table-row:hover .ms-like-drag-dot,
.ms-like-table-row.is-drag-over .ms-like-drag-dot {
  background: #9ca3af;
}

.ms-like-checkbox-field {
  min-width: 0;
}

.ms-like-name-field :deep(.el-input),
.ms-like-header-input-cell :deep(.el-input) {
  width: 100%;
}

.ms-like-required-button {
  position: absolute;
  top: 50%;
  left: 6px;
  z-index: 1;
  transform: translateY(-50%);
  width: 18px;
  height: 18px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #98a2b3;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0;
}

.ms-like-required-button.active {
  color: #f04438;
  background: #fff1f3;
}

.ms-like-checkbox-field :deep(.el-input),
.ms-like-name-field :deep(.el-input),
.ms-like-table-row :deep(.el-input),
.ms-like-table-row :deep(.el-select) {
  width: 100%;
}

.ms-like-checkbox-field :deep(.el-input__wrapper),
.ms-like-name-field :deep(.el-input__wrapper),
.ms-like-table-row :deep(.el-input__wrapper),
.ms-like-table-row :deep(.el-select__wrapper) {
  box-shadow: inset 0 0 0 1px transparent;
  background: transparent;
  border-radius: 4px;
  min-height: 24px;
  padding: 0 6px;
  transition: box-shadow 0.15s ease, background-color 0.15s ease;
}

.ms-like-name-field :deep(.el-input__wrapper) {
  padding-left: 28px;
}

.ms-like-checkbox-field :deep(.el-input__inner),
.ms-like-name-field :deep(.el-input__inner),
.ms-like-table-row :deep(.el-input__inner),
.ms-like-table-row :deep(.el-select__placeholder),
.ms-like-table-row :deep(.el-select__selected-item) {
  font-size: 12px;
}

.ms-like-checkbox-field :deep(.el-input__wrapper:hover),
.ms-like-name-field :deep(.el-input__wrapper:hover),
.ms-like-table-row :deep(.el-input__wrapper:hover),
.ms-like-table-row :deep(.el-select__wrapper:hover) {
  box-shadow: inset 0 0 0 1px #d0d5dd;
  background: #fff;
}

.ms-like-checkbox-field :deep(.el-input.is-focus .el-input__wrapper),
.ms-like-name-field :deep(.el-input.is-focus .el-input__wrapper),
.ms-like-table-row :deep(.el-input.is-focus .el-input__wrapper),
.ms-like-table-row :deep(.el-select.is-focus .el-select__wrapper),
.ms-like-table-row :deep(.el-select__wrapper.is-focused) {
  box-shadow: inset 0 0 0 1px #7c3aed;
  background: #fff;
}

.ms-like-switch-cell {
  color: #667085;
  font-size: 12px;
}

.ms-like-switch-cell {
  display: flex;
  justify-content: center;
}

.ms-like-param-table--query .ms-like-switch-cell--query {
  justify-content: flex-start;
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
  justify-self: center;
  color: #c75450;
}

.ms-like-param-table--header .ms-like-link-button,
.ms-like-param-table--header .ms-like-row-remove {
  justify-self: center;
  text-align: center;
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

.ms-like-binary-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.binary-file-name {
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.binary-file-size {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
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
  min-height: 290px;
  flex: 0 0 auto;
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
  overflow: visible;
  display: flex;
  flex-direction: column;
}

.ms-like-response-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  padding: 12px 0 4px;
}

.ms-like-response-empty-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.ms-like-response-empty-visual {
  display: flex;
  align-items: center;
  justify-content: center;
}

.ms-like-response-empty-window {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}

.ms-like-response-empty-window span {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--el-text-color-secondary);
  opacity: 0.6;
}

.ms-like-response-empty-text {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
  text-align: center;
}

.ms-like-response-empty-text span {
  color: #165dff;
  font-weight: 500;
}

.case-drawer-response-shell {
  min-height: 0;
  padding: 8px 0 0;
  border-top: 1px solid var(--el-border-color-light);
}

.case-drawer-response-shell .ms-like-response-header {
  min-height: 36px;
  padding: 0 16px;
}

.case-drawer-response-shell .ms-like-response-empty {
  padding: 6px 16px 12px;
}

.case-drawer-response-shell .ms-like-response-tabs {
  margin: 0 16px;
}

.case-drawer-response-shell .ms-like-response-body {
  padding: 0 16px 12px;
}

.case-drawer-response-shell .response-error-banner {
  margin: 0 16px;
}

.case-drawer-body-editor {
  min-height: 324px;
}

.case-drawer-body-editor :deep(.ms-monaco-editor),
.case-drawer-body-editor :deep(.ms-monaco-editor__body),
.case-drawer-body-editor :deep(.monaco-editor),
.case-drawer-body-editor :deep(.overflow-guard) {
  min-height: 300px !important;
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
  grid-template-columns: 44px minmax(180px, 1fr) 140px minmax(220px, 1fr) auto auto auto auto;
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
  gap: 0;
}

.ms-auth-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.ms-auth-panel--compact {
  min-width: 0;
}

.ms-auth-panel-title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 500;
}

.ms-auth-radio-group {
  align-self: flex-start;
}

.ms-auth-form {
  display: grid;
  gap: 16px;
}

.ms-auth-form--compact {
  gap: 12px;
}

.ms-auth-form-item {
  display: grid;
  gap: 8px;
}

.ms-auth-form-label {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.ms-auth-form-control {
  width: min(100%, 450px);
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

.processor-result-copy {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.processor-variable-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.processor-variable-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 999px;
  background: var(--el-fill-color-blank);
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.processor-log-preview {
  margin: 0;
  padding: 10px;
  border-radius: 8px;
  background: #111827;
  color: #e5e7eb;
  font-size: 12px;
  overflow: auto;
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

.batch-drawer {
  display: grid;
  gap: 16px;
}

.batch-drawer-hint {
  padding: 12px 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

.batch-drawer-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.batch-drawer-example {
  font-family: var(--el-font-family-monospace, Consolas, monospace);
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-regular);
}

.batch-drawer-note {
  margin-top: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
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

.case-list-table {
  width: 100%;
}

.case-list-table-wrap {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.case-list-operation-header {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
}

.case-list-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  white-space: nowrap;
}

.case-list-action-button,
.case-list-more-button {
  color: var(--el-color-primary);
}

.case-list-more-button {
  width: 26px;
  min-width: 26px;
  padding-inline: 0;
}

.case-list-settings-trigger {
  width: 22px;
  height: 22px;
  min-height: 22px;
  padding: 0;
  color: var(--el-text-color-secondary);
}

.case-list-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.case-list-pagination-summary {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

:deep(.case-list-more-menu .case-list-menu-item) {
  color: var(--el-color-primary);
}

:deep(.case-list-more-menu .case-list-menu-item.is-danger) {
  color: var(--el-color-danger);
}

@media (max-width: 1480px) {
  .ms-like-param-table-grid--query {
      grid-template-columns: 24px 28px minmax(190px, 1.15fr) 112px minmax(220px, 1fr) 72px minmax(170px, 0.95fr) 64px;
  }

  .ms-like-param-table-grid--header {
      grid-template-columns: 24px 28px repeat(3, minmax(0, 1fr)) 64px;
  }

  .ms-like-param-table-grid--body-form {
      grid-template-columns: 24px 28px minmax(190px, 1.1fr) 112px minmax(180px, 1fr) minmax(150px, 0.9fr) 64px;
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
    flex: 1;
  }

  .ms-like-request-row,
  .ms-like-meta-row,
  .ms-like-form-row {
    grid-template-columns: 1fr;
  }

  .ms-like-param-table-grid--query,
  .ms-like-param-table-grid--header,
  .ms-like-param-table-grid--body-form {
    grid-template-columns: 28px minmax(0, 1fr);
    gap: 8px;
  }

  .ms-like-check-cell {
    grid-column: 1 / -1;
  }

  .ms-like-table-header > .ms-like-checkbox-cell,
  .ms-like-table-row > .ms-like-checkbox-cell {
    grid-column: 1 / -1;
    justify-content: flex-start;
    padding-left: 6px;
  }

  .ms-like-table-header > span,
  .ms-like-table-header > .ms-like-header-input-title,
  .ms-like-table-header > .ms-like-link-button,
  .ms-like-table-row > :not(.ms-like-drag-cell):not(.ms-like-checkbox-field):not(.ms-like-checkbox-cell) {
    grid-column: 2 / -1;
  }

  .ms-like-table-row > .ms-like-switch-cell {
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


