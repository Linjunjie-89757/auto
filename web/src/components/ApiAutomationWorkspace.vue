<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import type { Component, Directive } from 'vue'
import {
  ArrowLeft,
  ArrowRight,
  ArrowDown,
  ArrowUp,
  CaretRight,
  Close,
  Delete,
  EditPen,
  Fold,
  MagicStick,
  MoreFilled,
  Plus,
  Setting,
  RefreshRight,
  Search,
} from '@element-plus/icons-vue'
import {
  Check,
  Bell,
  ChevronRight,
  Clock,
  FileJson,
  FileText,
  Folder as LucideFolder,
  FolderOpen as LucideFolderOpen,
  GripVertical,
  Link,
  Layers,
  MoreHorizontal,
  Play,
  Plus as LucidePlus,
  Save,
  Search as LucideSearch,
  Settings2,
  Upload,
  X,
  Zap,
} from '@lucide/vue'
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
  ApiAiCaseGenerationEvent,
  ApiAiCaseGenerationPayload,
  ApiAiGeneratedCaseDraft,
  ApiAiGeneratedCaseOutline,
  ApiAssertionConfig,
  ApiAssertionResult,
  ApiAuthConfig,
  ApiAuthCredential,
  ApiDebugCasePayload,
  ApiRunResponse,
  AiProviderConnection,
  ApiDefinitionCaseDetail,
  ApiDefinitionCaseChangeHistoryItem,
  ApiDefinitionCaseItem,
  ApiDefinitionCaseRunHistoryDetail,
  ApiDefinitionCaseRunHistoryItem,
  ApiDefinitionDetail,
  ApiDefinitionItem,
  ApiDefinitionModuleItem,
  DbConnectionItem,
  ApiEnvironmentItem,
  ApiExtractorConfig,
  ApiKeyValue,
  ApiProcessorConfig,
  ApiProcessorExtractorConfig,
  ApiRequestConfig,
  ApiDebugDefinitionPayload,
  ApiRunPayload,
  ApiRunStepResult,
  ApiScenarioDetail,
  ApiScenarioItem,
  ApiScenarioModuleItem,
  ApiScenarioAssertionConfig,
  ApiScenarioStep,
  ApiScenarioStepType,
  ApiVariableItem,
  ApiVariableSetItem,
  CreateBugPayload,
  ReportDetail,
  ReportItem,
  TaskItem,
  UserItem,
  WorkspaceItem,
} from '../types/api'

type RequestEditorResourceType = 'definition' | 'case' | 'ai-case-generation'
type CaseDrawerMode = 'create' | 'edit' | 'run'
type CaseDrawerViewTab = 'detail' | 'runHistory' | 'changeHistory'
type CaseDrawerHistoryView = 'list' | 'detail'
type ScenarioDetailTab = 'basic' | 'steps' | 'params' | 'assertions' | 'history' | 'settings'
type ResponsePreviewTab = 'body' | 'header' | 'console' | 'actualRequest' | 'assertions'
type HistoryRequestPreviewTab = 'header' | 'body'
type ScenarioAddStepAction =
  | 'IMPORT_SYSTEM_API'
  | 'CUSTOM_REQUEST'
  | 'LOOP_CONTROLLER'
  | 'IF_CONTROLLER'
  | 'ONCE_ONLY_CONTROLLER'
  | 'SCRIPT'
  | 'CONSTANT_TIMER'
type ScenarioImportTab = 'api' | 'case' | 'scenario'
type ScenarioStepDrawerMode = 'create' | 'edit'
type ScenarioScriptDrawerTab = 'script' | 'assertions'
type ScenarioScriptInputMode = 'manual' | 'common'
type ScenarioScriptResultTab = 'console' | 'assertions'
type DefinitionImportFormat = 'swagger' | 'postman' | 'har'
type DefinitionImportMode = 'url' | 'file'
type AiCaseGenerateGroup = 'positive' | 'negative' | 'boundary' | 'security'
type AiCaseGenerateResultStatus = 'generating' | 'pending' | 'failed' | 'accepted' | 'discarded'
type AiCaseGenerationStatusFilter = 'pending' | 'accepted' | 'discarded'
type AiCaseGenerateOption = {
  key: string
  group: AiCaseGenerateGroup
  label: string
}
type AiCaseGenerateResult = {
  id: string
  name: string
  directoryName: string
  tags: string[]
  description: string
  group: string
  type: string
  expected: string
  status: AiCaseGenerateResultStatus
  errorMessage?: string
  runResult?: string
  activeTab: Exclude<RequestContentTab, 'cases'>
  responseTab: ResponsePreviewTab
  requestConfig: ApiRequestConfig
  assertions: ApiAssertionConfig[]
  preProcessors: ApiProcessorConfig[]
  postProcessors: ApiProcessorConfig[]
  debugReportId?: number | null
  debugFailureSummary?: string
  debugStepResults?: ApiRunStepResult[]
  responseBody?: string
  responseStatus?: string
  responseHeaders?: Record<string, string>
  responseConsole?: string
}
type AiCaseGenerateTabState = {
  definitionId: number
  definitionName: string
  method: string
  path: string
  results: AiCaseGenerateResult[]
  generating: boolean
  abortController?: AbortController | null
}
type RequestConfigHost = {
  requestConfig: ApiRequestConfig
}
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
  aiGeneration?: AiCaseGenerateTabState
  aiGeneratedCaseId?: string
}

type ScenarioEditorTab = {
  key: string
  id: number | null
  title: string
  draft: ApiScenarioDetail | null
  lastRunStepResults: ApiRunStepResult[]
  savedFingerprint: string
  isDirty: boolean
}

type ScenarioModuleTreeNode = {
  key: string
  type: 'root' | 'workspace' | 'module'
  id: number | null
  name: string
  scenarioCount: number
  workspaceCode: string
  children: ScenarioModuleTreeNode[]
}

type FlatScenarioStep = {
  step: ApiScenarioStep
  path: number[]
  level: number
}

type ScenarioImportTreeNode = {
  key: string
  type: 'root' | 'workspace' | 'module'
  label: string
  workspaceCode: string
  modulePath: string | null
  moduleId: number | null
  count: number
  children: ScenarioImportTreeNode[]
}

type TabStripOverflowState = {
  overflow: boolean
  arrivedLeft: boolean
  arrivedRight: boolean
}

const requestMethodOptions = ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS', 'HEAD', 'PATCH', 'TRACE'] as const
const queryParamTypeOptions = ['string', 'integer', 'number', 'boolean', 'array'] as const
const bodyParamTypeOptions = ['string', 'integer', 'number', 'boolean', 'array', 'json', 'file'] as const
const requestAuthTypeOptions = [
  { label: 'No Auth', value: 'NONE' },
  { label: 'Basic Auth', value: 'BASIC' },
  { label: 'Digest Auth', value: 'DIGEST' },
] as const
const casePriorityOptions = ['P0', 'P1', 'P2', 'P3'] as const
const caseStatusOptions = ['进行中', '已完成', '已废弃'] as const
const scenarioPriorityOptions = ['P0', 'P1', 'P2', 'P3'] as const
const scenarioStatusOptions = [
  { label: '未开始', value: 'NOT_STARTED' },
  { label: '进行中', value: 'IN_PROGRESS' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已归档', value: 'ARCHIVED' },
] as const
const scenarioStepTypeOptions: Array<{ label: string; value: ApiScenarioStepType }> = [
  { label: '接口', value: 'API' },
  { label: '接口用例', value: 'API_CASE' },
  { label: '自定义请求', value: 'CUSTOM_REQUEST' },
  { label: '引用场景', value: 'API_SCENARIO' },
  { label: '条件控制器', value: 'IF_CONTROLLER' },
  { label: '循环控制器', value: 'LOOP_CONTROLLER' },
  { label: '仅一次控制器', value: 'ONCE_ONLY_CONTROLLER' },
  { label: '固定等待', value: 'CONSTANT_TIMER' },
  { label: '脚本', value: 'SCRIPT' },
]
const scenarioAssertionTypeOptions: Array<{ label: string; value: ApiScenarioAssertionConfig['assertionType'] }> = [
  { label: '全部步骤通过', value: 'ALL_STEPS_PASSED' },
  { label: '失败数等于', value: 'FAILED_COUNT_EQUALS' },
  { label: '失败数小于等于', value: 'FAILED_COUNT_LTE' },
  { label: '总耗时小于', value: 'TOTAL_DURATION_LT' },
  { label: '执行步骤数等于', value: 'STEP_COUNT_EQUALS' },
]
const CASE_LIST_PAGE_SIZE_OPTIONS = [10, 20, 30, 40, 50] as const
const definitionImportFormats: Array<{
  value: DefinitionImportFormat
  label: string
  description: string
  icon: Component
  tone: string
  accept: string
}> = [
  {
    value: 'swagger',
    label: 'Swagger / OpenAPI',
    description: '支持 Swagger 2.0 / OpenAPI 3.x',
    icon: Link,
    tone: 'green',
    accept: '.json,.yaml,.yml',
  },
  {
    value: 'postman',
    label: 'Postman Collection',
    description: '支持 Postman v2.0 / v2.1 格式',
    icon: FileJson,
    tone: 'orange',
    accept: '.json',
  },
  {
    value: 'har',
    label: 'HAR 文件',
    description: '浏览器导出的 HTTP 存档文件',
    icon: FileText,
    tone: 'purple',
    accept: '.har',
  },
]
const aiCaseGenerateGroups: Array<{ key: AiCaseGenerateGroup, label: string }> = [
  { key: 'positive', label: '正向' },
  { key: 'negative', label: '负向' },
  { key: 'boundary', label: '边界值' },
  { key: 'security', label: '安全性' },
]
const aiCaseGenerateOptions: AiCaseGenerateOption[] = [
  { key: 'required-only', group: 'positive', label: '仅传必要字段' },
  { key: 'valid-semantics', group: 'positive', label: '语义合法' },
  { key: 'sample-combination', group: 'positive', label: '覆盖枚举组合' },
  { key: 'other-positive', group: 'positive', label: '其他正向' },
  { key: 'empty-value', group: 'negative', label: '无效值' },
  { key: 'missing-required', group: 'negative', label: '缺失必填字段' },
  { key: 'format-error', group: 'negative', label: '格式错误' },
  { key: 'type-error', group: 'negative', label: '类型错误' },
  { key: 'semantic-invalid', group: 'negative', label: '语义非法' },
  { key: 'other-negative', group: 'negative', label: '其他负向' },
  { key: 'max-min', group: 'boundary', label: '极大值/极小值' },
  { key: 'over-boundary', group: 'boundary', label: '超出最大、最小边界值' },
  { key: 'null-empty', group: 'boundary', label: 'Null/零值/空值' },
  { key: 'string-length', group: 'boundary', label: '字符串过长、过短' },
  { key: 'auth-control', group: 'security', label: '鉴权控制' },
  { key: 'sql-injection', group: 'security', label: 'SQL注入' },
  { key: 'fuzzy-input', group: 'security', label: '模糊输入' },
  { key: 'xss-injection', group: 'security', label: 'XSS注入' },
  { key: 'command-injection', group: 'security', label: '命令行注入' },
  { key: 'json-injection', group: 'security', label: 'JSON注入' },
  { key: 'nosql-injection', group: 'security', label: 'NoSQL注入' },
]
const executionSuiteTree = [
  {
    id: 'workspace-account',
    label: '开户工作空间',
    count: 4,
    children: [
      { id: 'suite-smoke', label: '冒烟测试', count: 2, children: [
        { id: 'suite-order-smoke', label: '订单冒烟', count: 0 },
        { id: 'suite-account-smoke', label: '开户冒烟', count: 0 },
      ] },
      { id: 'suite-regression', label: '回归测试', count: 2, children: [
        { id: 'suite-login-regression', label: '登录回归', count: 0 },
        { id: 'suite-full-regression', label: '全量回归', count: 0 },
      ] },
    ],
  },
  {
    id: 'workspace-trade',
    label: '交易工作空间',
    count: 0,
    children: [],
  },
] as const
const executionSuiteCases = [
  { id: 'api-login', type: 'api', name: '用户登录接口用例', method: 'POST', path: '/api/auth/login', description: '' },
  { id: 'api-profile', type: 'api', name: '获取用户信息', method: 'GET', path: '/api/user/profile', description: '' },
  { id: 'scene-account', type: 'scene', name: '开户完整流程', method: '', path: '', description: '包含登录、开户注册、状态校验' },
] as const
const executionVisualEnvironment = ref('uat')
const executionVisualRunMode = ref('parallel')
const executionVisualRunOn = ref('local')
const executionVisualNotify = ref(true)
const executionSubTabs = [
  { key: 'arrange', label: '编排', icon: Layers },
  { key: 'schedule', label: '定时', icon: Clock },
  { key: 'branch', label: '分支', icon: Zap },
  { key: 'report', label: '报告', icon: FileText },
] as const

type DefinitionDirectoryTreeNode = {
  key: string
  type: 'root' | 'workspace' | 'module' | 'unassigned' | 'request'
  label: string
  workspaceCode: string
  fullPath: string | null
  count: number
  definitionId: number | null
  moduleId: number | null
  method: string | null
  children: DefinitionDirectoryTreeNode[]
}

type BatchAddMode = 'query' | 'cookie' | 'header' | 'body-form' | 'assertion' | 'extractor'
type SortableParamGroup = 'query' | 'header' | 'body-form'
type RequestContentTab = 'params' | 'headers' | 'body' | 'auth' | 'pre' | 'post' | 'tests' | 'settings' | 'cases'

const DEFINITION_TREE_ROOT_KEY = 'definition-root'
const DEFINITION_TREE_UNASSIGNED_KEY = 'definition-unassigned'
const SCENARIO_MODULE_ROOT_KEY = 'scenario-module-all'

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

function updateTabStripOverflowState(element: HTMLElement | null, state: TabStripOverflowState) {
  if (!element) {
    state.overflow = false
    state.arrivedLeft = true
    state.arrivedRight = true
    return
  }
  const maxScrollLeft = Math.max(0, element.scrollWidth - element.clientWidth)
  state.overflow = maxScrollLeft > 1
  state.arrivedLeft = element.scrollLeft <= 1
  state.arrivedRight = element.scrollLeft >= maxScrollLeft - 1
}

function scrollTabStrip(element: HTMLElement | null, state: TabStripOverflowState, direction: 'left' | 'right') {
  if (!element || !state.overflow) {
    return
  }
  const delta = Math.max(160, element.clientWidth - 80)
  const nextLeft = direction === 'left'
    ? Math.max(0, element.scrollLeft - delta)
    : Math.min(element.scrollWidth - element.clientWidth, element.scrollLeft + delta)
  element.scrollTo({ left: nextLeft, behavior: 'smooth' })
}

function scrollActiveTabIntoView(element: HTMLElement | null) {
  if (!element) {
    return
  }
  const activeItem = element.querySelector<HTMLElement>('.ms-like-editor-tab.active')
  if (!activeItem) {
    return
  }
  const itemLeft = activeItem.offsetLeft
  const itemRight = itemLeft + activeItem.offsetWidth
  const viewLeft = element.scrollLeft
  const viewRight = viewLeft + element.clientWidth
  if (itemLeft < viewLeft) {
    element.scrollTo({
      left: Math.max(0, itemLeft - 24),
      behavior: 'smooth',
    })
    return
  }
  if (itemRight > viewRight) {
    element.scrollTo({
      left: Math.min(element.scrollWidth - element.clientWidth, itemRight - element.clientWidth + 24),
      behavior: 'smooth',
    })
  }
}

function updateRequestTabOverflowState() {
  updateTabStripOverflowState(requestTabNavRef.value, requestTabOverflow)
}

function updateScenarioTabOverflowState() {
  updateTabStripOverflowState(scenarioTabNavRef.value, scenarioTabOverflow)
}

function syncRequestTabStripState() {
  scrollActiveTabIntoView(requestTabNavRef.value)
  updateRequestTabOverflowState()
}

function syncScenarioTabStripState() {
  scrollActiveTabIntoView(scenarioTabNavRef.value)
  updateScenarioTabOverflowState()
}

const loading = ref(false)
const saving = ref(false)
const reportDrawerVisible = ref(false)
const bugDialogVisible = ref(false)
const definitionSaveDialogVisible = ref(false)
const batchAddDrawerVisible = ref(false)
const aiCaseGenerateDrawerVisible = ref(false)
const activeTab = ref<'definitions' | 'scenarios' | 'execution' | 'reports' | 'settings'>('definitions')
const activeRequestTab = ref<RequestContentTab>('body')
const responsePreviewTab = ref<ResponsePreviewTab>('body')
const caseDrawerVisible = ref(false)
const caseDrawerEditorKey = ref('')
const caseDrawerRequestTab = ref<RequestContentTab>('body')
const caseDrawerViewTab = ref<CaseDrawerViewTab>('detail')
const caseDrawerResponsePreviewTab = ref<ResponsePreviewTab>('body')
const caseDrawerHistoryPreviewTab = ref<ResponsePreviewTab>('body')
const caseDrawerHistoryRequestPreviewTab = ref<HistoryRequestPreviewTab>('body')
const caseDrawerHistoryView = ref<CaseDrawerHistoryView>('list')
const caseDrawerDebugReportId = ref<number | null>(null)
const caseDrawerDebugFailureSummary = ref('')
const caseDrawerDebugStepResults = ref<ApiRunStepResult[]>([])
const caseDrawerRunHistoryLoading = ref(false)
const caseDrawerRunHistoryItems = ref<ApiDefinitionCaseRunHistoryItem[]>([])
const caseDrawerRunHistoryDetailLoading = ref(false)
const caseDrawerRunHistoryDetail = ref<ApiDefinitionCaseRunHistoryDetail | null>(null)
const selectedCaseDrawerRunHistoryId = ref<number | null>(null)
const caseDrawerChangeHistoryLoading = ref(false)
const caseDrawerChangeHistoryItems = ref<ApiDefinitionCaseChangeHistoryItem[]>([])
const batchAddMode = ref<BatchAddMode>('query')
const batchAddInput = ref('')
const batchAddContext = ref<'main' | 'case'>('main')
const batchAddParamStyle = ref<'quick' | 'standard'>('standard')
const aiCaseGenerateSelectedOptions = ref<string[]>(
  aiCaseGenerateOptions
    .filter(item => item.group !== 'security' || item.key === 'auth-control')
    .map(item => item.key),
)
const aiCaseGeneratePrompt = ref('')
const aiCaseGenerateCount = ref<'AUTO' | 10 | 20 | 40 | 80>('AUTO')
const aiCaseGenerateModel = ref('')
const aiCaseGenerateProviderConnections = ref<AiProviderConnection[]>([])
const aiCaseGenerateProviderLoading = ref(false)
const aiCaseGenerateNoDuplicate = ref(true)
const aiCaseGenerateResults = ref<AiCaseGenerateResult[]>([])
const aiCaseGenerationFinishedIcon = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTQiIGhlaWdodD0iMTQiIHZpZXdCb3g9IjAgMCAxNCAxNCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMu b3JnLzIwMDAvc3ZnIj4KICA8ZyBjbGlwLXBhdGg9InVybCgjY2xpcDBfMTA2NV8yMTk5MDQpIj4KICAgIDxwYXRoCiAgICAgIGQ9Ik01LjI0MTIxIDMuMDYxNTJDNS40MjgyOCAyLjE0NjI4IDYuNzM1NzggMi4xNDYyOCA2LjkyMjg1IDMuMDYxNTJDNy4zMzYxIDUuMDgyODEgOC45MTYxNyA2LjY2MzAzIDEwLjkzNzUgNy4wNzYxN0MxMS44NTI3IDcuMjYzMjcgMTEuODUyNyA4LjU3MDc1IDEwLjkzNzUgOC43NTc4MUM4LjkxNjI0IDkuMTcxMDMgNy4zMzYwNiAxMC43NTAyIDYuOTIyODUgMTIuNzcxNUM2LjczNTY4IDEzLjY4NjUgNS40MjgzOCAxMy42ODY1IDUuMjQxMjEgMTIuNzcxNUM0LjgyNzk4IDEwLjc1MDQgMy4yNDg2MiA5LjE3MTEgMS4yMjc1NCA4Ljc1NzgxQzAuMzEyMzA1IDguNTcwNzUgMC4zMTIzMzQgNy4yNjMyNyAxLjIyNzU0IDcuMDc2MTdDMy4yNDg2OSA2LjY2Mjk1IDQuODI3OTQgNS4wODI2NSA1LjI0MTIxIDMuMDYxNTJaTTEwLjY2MTEgMS4yMzkyNkMxMC43MzAxIDAuOTAyMDYxIDExLjIxMjMgMC45MDIwNjEgMTEuMjgxMiAxLjIzOTI2QzExLjQzMzYgMS45ODM3MSAxMi4wMTUzIDIuNTY1NDcgMTIuNzU5OCAyLjcxNzc3QzEzLjA5NyAyLjc4NjY5IDEzLjA5NyAzLjI2ODk3IDEyLjc1OTggMy4zMzc4OUMxMi4wMTUzIDMuNDkwMjQgMTEuNDMzNSA0LjA3MTg0IDExLjI4MTIgNC44MTY0MUMxMS4yMTIzIDUuMTUzNiAxMC43MzAxIDUuMTUzNiAxMC42NjExIDQuODE2NDFDMTAuNTA4OSA0LjA3MTggOS45MjcyMSAzLjQ5MDE2IDkuMTgyNjIgMy4zMzc4OUM4Ljg0NTU0IDMuMjY4OTEgOC44NDU1NCAyLjc4Njc1IDkuMTgyNjIgMi43MTc3N0M5LjkyNzE1IDIuNTY1NTQgMTAuNTA4OCAxLjk4Mzc1IDEwLjY2MTEgMS4yMzkyNloiCiAgICAgIGZpbGw9InVybCgjcGFpbnQwX2xpbmVhcl8xMDY1XzIxOTkwNCkiIC8+CiAgPC9nPgogIDxkZWZzPgogICAgPGxpbmVhckdyYWRpZW50IGlkPSJwYWludDBfbGluZWFyXzEwNjVfMjE5OTA0IiB4MT0iMC41NDEwMTYiIHkxPSIzLjUzMTQ2IiB4Mj0iMTMuMTEwNSIgeTI9IjMuNzAxNzUiCiAgICAgIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIj4KICAgICAgPHN0b3Agc3RvcC1jb2xvcj0iI0U5NTZFOSIgLz4KICAgICAgPHN0b3Agb2Zmc2V0PSIwLjcyIiBzdG9wLWNvbG9yPSIjRkY1ODVFIiAvPgogICAgICA8c3RvcCBvZmZzZXQ9IjEiIHN0b3AtY29sb3I9IiNGRkEzMDAiIC8+CiAgICA8L2xpbmVhckdyYWRpZW50PgogICAgPGNsaXBQYXRoIGlkPSJjbGlwMF8xMDY1XzIxOTkwNCI+CiAgICAgIDxyZWN0IHdpZHRoPSIxNCIgaGVpZ2h0PSIxNCIgZmlsbD0id2hpdGUiIC8+CiAgICA8L2NsaXBQYXRoPgogIDwvZGVmcz4KPC9zdmc+Cg=='.replace(/\s/g, '')
const aiGenerationStatusFilter = ref<AiCaseGenerationStatusFilter>('pending')
const selectedAiCaseGenerateResultIds = ref<string[]>([])
const aiGenerationDetailKeyword = ref('')
const aiGenerationDetailGroupFilter = ref<AiCaseGenerateGroup | ''>('')
const aiGenerationDetailTypeFilter = ref('')
const activeAiGeneratedCaseId = ref('')
const aiCaseDrawerResultId = ref('')
const draggingParamGroup = ref<SortableParamGroup | null>(null)
const draggingParamIndex = ref<number | null>(null)
const dragOverParamGroup = ref<SortableParamGroup | null>(null)
const dragOverParamIndex = ref<number | null>(null)
const activePreProcessorId = ref<string | null>(null)
const activePostProcessorId = ref<string | null>(null)
const activeAssertionId = ref<string | null>(null)

const definitions = ref<ApiDefinitionItem[]>([])
const definitionModules = ref<ApiDefinitionModuleItem[]>([])
const apiCases = ref<ApiDefinitionCaseItem[]>([])
const scenarios = ref<ApiScenarioItem[]>([])
const scenarioModules = ref<ApiScenarioModuleItem[]>([])
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
const selectedScenarioModuleId = ref<number | null>(null)
const selectedScenarioWorkspaceCode = ref<string | null>(null)
const selectedEnvironmentId = ref<number | null>(null)
const selectedVariableSetId = ref<number | null>(null)
const selectedReportId = ref<number | null>(null)
const requestEditorTabs = ref<RequestEditorTab[]>([])
const scenarioEditorTabs = ref<ScenarioEditorTab[]>([{
  key: 'scenario-list',
  id: null,
  title: '全部场景',
  draft: null,
  lastRunStepResults: [],
  savedFingerprint: '',
  isDirty: false,
}])
const activeRequestEditorKey = ref('')
const activeScenarioEditorKey = ref('scenario-list')
const activeScenarioDetailTab = ref<ScenarioDetailTab>('steps')
const scenarioLastRunStepResults = ref<ApiRunStepResult[]>([])
const caseDrawerSourceEditorKey = ref('')
const requestEditorSyncing = ref(false)
const selectedDefinitionTreeKey = ref(DEFINITION_TREE_ROOT_KEY)
const definitionTreeRenderKey = ref(0)
const expandedDefinitionTreeKeys = ref<string[]>([DEFINITION_TREE_ROOT_KEY])
const scenarioModuleTreeRenderKey = ref(0)
const expandedScenarioModuleTreeKeys = ref<string[]>([])

const definitionFilters = reactive({
  keyword: '',
  directory: '',
})
const definitionImportDialogVisible = ref(false)
const definitionImportFormat = ref<DefinitionImportFormat>('swagger')
const definitionImportMode = ref<DefinitionImportMode>('url')
const definitionImportUrl = ref('')
const definitionImportFileName = ref('')
const definitionEditorShellRef = ref<HTMLElement | null>(null)

const definitionSaveForm = reactive({
  workspaceCode: '',
  name: '',
  path: '',
  directoryName: '',
})

const scenarioFilters = reactive({
  keyword: '',
  status: '',
})
const scenarioModuleKeyword = ref('')
const scenarioViewMode = ref('ALL')
const scenarioImportDrawerVisible = ref(false)
const scenarioImportActiveTab = ref<ScenarioImportTab>('api')
const scenarioImportTreeKeyword = ref('')
const scenarioImportKeyword = ref('')
const scenarioImportWorkspaceCode = ref('')
const scenarioImportProtocol = ref('HTTP')
const selectedScenarioImportTreeKey = ref('scenario-import-all')
const scenarioImportSelectedDefinitionIds = ref<number[]>([])
const scenarioImportSelectedCaseIds = ref<number[]>([])
const scenarioImportSelectedScenarioIds = ref<number[]>([])
const scenarioImportLoading = ref(false)
const scenarioCustomRequestDrawerVisible = ref(false)
const scenarioCustomRequestDrawerMode = ref<ScenarioStepDrawerMode>('create')
const scenarioCustomRequestEditingPath = ref<number[]>([])
const scenarioCustomRequestActiveTab = ref<RequestContentTab>('headers')
const scenarioCustomRequestTitleEditing = ref(false)
const scenarioCustomRequestHasCustomStepName = ref(false)
const scenarioCustomRequestActivePreProcessorId = ref<string | null>(null)
const scenarioCustomRequestActivePostProcessorId = ref<string | null>(null)
const scenarioCustomRequestActiveAssertionId = ref<string | null>(null)
const scenarioCustomRequestDebugLoading = ref(false)
const scenarioCustomRequestDebugFailureSummary = ref('')
const scenarioCustomRequestDebugStepResults = ref<ApiRunStepResult[]>([])
const scenarioCustomRequestResponsePreviewTab = ref<ResponsePreviewTab>('body')
const scenarioScriptDrawerVisible = ref(false)
const scenarioScriptDrawerMode = ref<ScenarioStepDrawerMode>('create')
const scenarioScriptEditingPath = ref<number[]>([])
const scenarioScriptActiveTab = ref<ScenarioScriptDrawerTab>('script')
const scenarioScriptInputMode = ref<ScenarioScriptInputMode>('manual')
const scenarioScriptActiveAssertionId = ref<string | null>(null)
const scenarioScriptEditorRef = ref<{ formatDocument: () => Promise<void> | void } | null>(null)
const scenarioScriptResultTab = ref<ScenarioScriptResultTab>('console')
const scenarioSystemRequestDrawerVisible = ref(false)
const scenarioSystemRequestDrawerLoading = ref(false)
const scenarioSystemRequestDetail = ref<ApiRequestEditorDetail | null>(null)
const scenarioSystemRequestActiveTab = ref<RequestContentTab>('headers')
const scenarioSystemRequestEditingStep = ref<ApiScenarioStep | null>(null)
const scenarioSystemRequestTitleEditing = ref(false)
const scenarioSystemRequestTitleDraft = ref('')
const scenarioSystemRequestHasCustomStepName = ref(false)
const scenarioSystemRequestDebugLoading = ref(false)
const scenarioSystemRequestDebugFailureSummary = ref('')
const scenarioSystemRequestDebugStepResults = ref<ApiRunStepResult[]>([])
const scenarioSystemRequestResponsePreviewTab = ref<ResponsePreviewTab>('body')
const scenarioStepNameEditingId = ref('')
const scenarioStepNameDraft = ref('')

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
  moduleId: null,
  moduleName: null,
  priority: 'P1',
  status: 'IN_PROGRESS',
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
  scenarioVariables: [],
  scenarioAssertions: [],
  steps: [],
})

function cloneScenarioDetail(detail: ApiScenarioDetail): ApiScenarioDetail {
  return JSON.parse(JSON.stringify(detail)) as ApiScenarioDetail
}

function fingerprintScenarioDetail(detail: ApiScenarioDetail) {
  return JSON.stringify({
    workspaceCode: detail.workspaceCode || '',
    name: detail.name || '',
    directoryName: detail.directoryName || '',
    moduleId: detail.moduleId ?? null,
    priority: detail.priority || 'P1',
    status: detail.status || 'IN_PROGRESS',
    description: detail.description || '',
    tags: [...(detail.tags || [])],
    defaultEnvironmentId: detail.defaultEnvironmentId ?? null,
    variableSetId: detail.variableSetId ?? null,
    continueOnFailure: !!detail.continueOnFailure,
    relatedCaseId: detail.relatedCaseId ?? null,
    scenarioVariables: detail.scenarioVariables || [],
    scenarioAssertions: detail.scenarioAssertions || [],
    steps: normalizeScenarioStepPayload(detail.steps || []),
  })
}

const scenarioCustomRequestForm = reactive<ApiScenarioStep>({
  id: '',
  stepName: '',
  stepType: 'CUSTOM_REQUEST',
  resourceType: null,
  resourceId: null,
  enabled: true,
  requestConfig: emptyApiRequestConfig(),
  assertions: [],
  preProcessors: [],
  postProcessors: [],
  delayMs: null,
  conditionType: 'EXPRESSION',
  conditionExpression: '',
  loopType: 'FIXED',
  loopCount: null,
  foreachExpression: '',
  script: '',
  children: [],
})

const scenarioScriptForm = reactive<ApiScenarioStep>({
  id: '',
  stepName: '',
  stepType: 'SCRIPT',
  resourceType: null,
  resourceId: null,
  enabled: true,
  requestConfig: null,
  assertions: [],
  preProcessors: [],
  postProcessors: [],
  delayMs: null,
  conditionType: 'EXPRESSION',
  conditionExpression: '',
  loopType: 'FIXED',
  loopCount: null,
  foreachExpression: '',
  script: '',
  children: [],
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
const hasDefinitionRequestUrl = computed(() => !!definitionForm.requestConfig.path?.trim())
const canSaveActiveEditor = computed(() => canWriteDefinition.value && hasDefinitionRequestUrl.value)
const canDebugDefinition = computed(() => canWriteDefinition.value
  && !!definitionForm.requestConfig.method?.trim()
  && hasDefinitionRequestUrl.value)
const canWriteScenario = computed(() => canWriteTarget(scenarioForm.workspaceCode))
const canWriteEnvironment = computed(() => canWriteTarget(environmentForm.workspaceCode))
const canWriteVariableSet = computed(() => canWriteTarget(variableSetForm.workspaceCode))
const canCreateInCurrentScope = computed(() => (
  isAllScope.value ? writableWorkspaces.value.length > 0 : canWriteWorkspace(workspaceCode.value)
))
const scenarioClosableEditorTabs = computed(() => scenarioEditorTabs.value.filter(item => item.key !== 'scenario-list'))
const showScenarioEditorMoreAction = computed(() => scenarioClosableEditorTabs.value.length > 0)
const activeScenarioEditorTab = computed(() => scenarioEditorTabs.value.find(item => item.key === activeScenarioEditorKey.value) ?? null)
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
const currentDefinitionCaseCount = computed(() => currentDefinitionCases.value.length)
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
const caseListTableRef = ref<{ doLayout?: () => void } | null>(null)
const caseDrawerCreateSource = ref<'draft' | 'savedDefinition'>('draft')
const caseDrawerMode = ref<CaseDrawerMode>('create')
const pagedDefinitionCases = computed(() => {
  const start = (caseListCurrentPage.value - 1) * caseListSettings.pageSize.value
  return currentDefinitionCases.value.slice(start, start + caseListSettings.pageSize.value)
})
const caseListTotalPages = computed(() => Math.max(1, Math.ceil(currentDefinitionCases.value.length / caseListSettings.pageSize.value)))
const caseDrawerTitle = computed(() => {
  if (aiCaseDrawerResultId.value) {
    return 'AI 生成接口用例详情'
  }
  if (caseDrawerMode.value === 'run') {
    return '用例详情'
  }
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
const caseDrawerReadOnly = computed(() => caseDrawerMode.value === 'run')
const caseDrawerPrimaryActionLabel = computed(() => (caseDrawerReadOnly.value ? '执行' : '发送'))
const caseDrawerShowFooter = computed(() => !caseDrawerReadOnly.value)
const canDebugScenarioCustomRequest = computed(() => canWriteScenario.value
  && !!scenarioCustomRequestConfig.value.method?.trim()
  && !!scenarioCustomRequestConfig.value.path?.trim())
const canDebugScenarioSystemRequest = computed(() => {
  const detail = scenarioSystemRequestDetail.value
  return !!detail
    && canWriteTarget(detail.workspaceCode)
    && !!detail.requestConfig.method?.trim()
    && !!detail.requestConfig.path?.trim()
})
const showCaseListContent = computed(() => activeRequestEditorTab.value?.resourceType === 'definition' && activeRequestTab.value === 'cases')
const isAiCaseGenerationTabActive = computed(() => activeRequestEditorTab.value?.resourceType === 'ai-case-generation')
const activeAiCaseGenerationState = computed(() => activeRequestEditorTab.value?.aiGeneration ?? null)
const visibleRequestEditorTabs = computed(() => requestEditorTabs.value.filter(item => item.resourceType === 'definition' || item.resourceType === 'ai-case-generation'))
const showRequestEditorMoreAction = computed(() => visibleRequestEditorTabs.value.length > 0)
const canCreateCaseForCurrentDefinition = computed(() => activeRequestEditorTab.value?.resourceType === 'definition' && !!definitionForm.id)
const aiCaseGenerateSelectedCount = computed(() => aiCaseGenerateSelectedOptions.value.length)
const activeAiCaseGenerationResults = computed(() => {
  if (!activeAiCaseGenerationState.value) {
    return []
  }
  return aiCaseGenerateResults.value
})
const aiGenerationDetailTypeOptions = computed(() => aiCaseGenerateOptions
  .filter(item => !aiGenerationDetailGroupFilter.value || item.group === aiGenerationDetailGroupFilter.value)
  .map(item => ({ value: item.label, label: item.label })))
const filteredAiCaseGenerationResults = computed(() => {
  const keyword = aiGenerationDetailKeyword.value.trim().toLowerCase()
  return activeAiCaseGenerationResults.value.filter((item) => {
    if (aiGenerationStatusFilter.value === 'accepted' && item.status !== 'accepted') {
      return false
    }
    if (aiGenerationStatusFilter.value === 'discarded' && item.status !== 'discarded') {
      return false
    }
    if (aiGenerationStatusFilter.value === 'pending' && (item.status === 'accepted' || item.status === 'discarded')) {
      return false
    }
    const groupKey = aiCaseGenerateGroups.find(group => group.label === item.group)?.key ?? ''
    if (aiGenerationDetailGroupFilter.value && groupKey !== aiGenerationDetailGroupFilter.value) {
      return false
    }
    if (aiGenerationDetailTypeFilter.value && item.type !== aiGenerationDetailTypeFilter.value) {
      return false
    }
    if (!keyword) {
      return true
    }
    return [item.name, item.type, item.group].some(value => value.toLowerCase().includes(keyword))
  })
})
const aiGenerationEmptyStateText = computed(() => {
  if (activeAiCaseGenerationState.value?.generating) {
    return 'AI 正在生成用例大纲，生成后的用例会逐条显示'
  }
  if (activeAiCaseGenerationResults.value.length > 0) {
    return '当前筛选条件下没有可展示的用例'
  }
  return '本次没有生成可展示的用例，请检查模型是否按要求返回结构化内容'
})
const aiCaseGenerationPendingCount = computed(() =>
  activeAiCaseGenerationResults.value.filter(item => item.status !== 'accepted' && item.status !== 'discarded').length)
const aiCaseGenerationAcceptedCount = computed(() =>
  activeAiCaseGenerationResults.value.filter(item => item.status === 'accepted').length)
const aiCaseGenerationDiscardedCount = computed(() =>
  activeAiCaseGenerationResults.value.filter(item => item.status === 'discarded').length)
const selectableAiCaseGenerationResults = computed(() =>
  filteredAiCaseGenerationResults.value.filter(item => item.status !== 'generating' && item.status !== 'failed'))
const selectedAiCaseGenerationResults = computed(() =>
  selectableAiCaseGenerationResults.value.filter(item => selectedAiCaseGenerateResultIds.value.includes(item.id)))
const isAiCaseGenerationAllSelected = computed(() =>
  selectableAiCaseGenerationResults.value.length > 0
  && selectableAiCaseGenerationResults.value.every(item => selectedAiCaseGenerateResultIds.value.includes(item.id)))
const isAiCaseGenerationSelectionIndeterminate = computed(() =>
  selectedAiCaseGenerationResults.value.length > 0 && !isAiCaseGenerationAllSelected.value)
const aiCaseGenerateModelOptions = computed(() =>
  aiCaseGenerateProviderConnections.value
    .filter(item => item.status !== 0 && !!item.modelName?.trim())
    .map(item => ({
      value: `${item.id}:${item.modelName}`,
      label: `${item.connectionName} / ${item.modelName}`,
    })),
)

function displayRequestPath(rawPath?: string | null) {
  if (!rawPath) {
    return ''
  }
  try {
    const url = new URL(rawPath)
    return `${url.pathname}${url.search}${url.hash}` || rawPath
  } catch {
    return rawPath
  }
}

const requestTabNavRef = ref<HTMLElement | null>(null)
const scenarioTabNavRef = ref<HTMLElement | null>(null)
const requestTabOverflow = reactive<TabStripOverflowState>({
  overflow: false,
  arrivedLeft: true,
  arrivedRight: true,
})
const scenarioTabOverflow = reactive<TabStripOverflowState>({
  overflow: false,
  arrivedLeft: true,
  arrivedRight: true,
})
const resizeObservers: ResizeObserver[] = []
const tabStripCleanupFns: Array<() => void> = []

const scenarioModuleTree = computed<ScenarioModuleTreeNode[]>(() => {
  const workspaceCodes = isAllScope.value
    ? Array.from(new Set([
      ...workspaces.value.map(item => item.code),
      ...scenarios.value.map(item => item.workspaceCode),
      ...scenarioModules.value.map(item => item.workspaceCode),
    ])).filter(Boolean)
    : [workspaceCode.value]
  const keyword = scenarioModuleKeyword.value.trim().toLowerCase()
  const workspaceNodes = workspaceCodes.map((code) => {
    const workspaceName = getScenarioWorkspaceName(code)
    const allModuleChildren = scenarioModules.value
      .filter(item => item.workspaceCode === code)
      .map(toScenarioModuleTreeNode)
    const children = keyword && !workspaceName.toLowerCase().includes(keyword)
      ? allModuleChildren.filter(node => matchesScenarioModuleKeyword(node, scenarioModuleKeyword.value))
      : allModuleChildren
    return {
      key: `scenario-workspace:${code}`,
      type: 'workspace' as const,
      id: null,
      name: workspaceName,
      scenarioCount: scenarios.value.filter(item => item.workspaceCode === code).length,
      workspaceCode: code,
      children,
    }
  }).filter(node => !keyword || node.name.toLowerCase().includes(keyword) || node.children.length)
  return workspaceNodes
})
const selectedScenarioModuleTreeKey = computed(() => {
  if (selectedScenarioModuleId.value != null) {
    return `scenario-module-${selectedScenarioModuleId.value}`
  }
  return selectedScenarioWorkspaceCode.value
    ? `scenario-workspace:${selectedScenarioWorkspaceCode.value}`
    : SCENARIO_MODULE_ROOT_KEY
})
const flatScenarioModules = computed(() => flattenScenarioModules(scenarioModules.value))
const selectedScenarioModuleItem = computed(() => (
  selectedScenarioModuleId.value == null
    ? null
    : flatScenarioModules.value.find(item => item.id === selectedScenarioModuleId.value) ?? null
))
const selectedScenarioModuleFormName = computed(() => {
  if (selectedScenarioModuleItem.value) {
    return selectedScenarioModuleItem.value.name
  }
  return ''
})
const scenarioModuleOptions = computed(() => flatScenarioModules.value.map(item => ({
  label: `${'  '.repeat(item.level)}${item.name}`,
  value: item.id,
})))
const scenarioRunHistorySteps = computed(() => scenarioLastRunStepResults.value)
const scenarioFlatSteps = computed(() => flattenScenarioSteps(scenarioForm.steps || []))
const scenarioSystemRequestStepOrder = computed(() => {
  const step = scenarioSystemRequestEditingStep.value
  if (!step) {
    return 1
  }
  const index = scenarioFlatSteps.value.findIndex(item => item.step === step || item.step.id === step.id)
  return index >= 0 ? index + 1 : 1
})
const scenarioCustomRequestStepOrder = computed(() => {
  if (scenarioCustomRequestDrawerMode.value === 'edit') {
    const step = getScenarioStepByPath(scenarioCustomRequestEditingPath.value)
    const index = scenarioFlatSteps.value.findIndex(item => item.step === step || (!!step?.id && item.step.id === step.id))
    return index >= 0 ? index + 1 : scenarioFlatSteps.value.length + 1
  }
  return scenarioFlatSteps.value.length + 1
})
const scenarioScriptStepOrder = computed(() => {
  if (scenarioScriptDrawerMode.value === 'edit') {
    const step = getScenarioStepByPath(scenarioScriptEditingPath.value)
    const index = scenarioFlatSteps.value.findIndex(item => item.step === step || (!!step?.id && item.step.id === step.id))
    return index >= 0 ? index + 1 : scenarioFlatSteps.value.length + 1
  }
  return scenarioFlatSteps.value.length + 1
})
const scenarioImportWorkspaceOptions = computed(() => {
  const codes = Array.from(new Set([
    ...workspaces.value.map(item => item.code),
    ...definitions.value.map(item => item.workspaceCode),
    ...apiCases.value.map(item => item.workspaceCode),
    ...scenarios.value.map(item => item.workspaceCode),
  ])).filter(Boolean)
  const scopedCodes = isAllScope.value ? codes : [workspaceCode.value]
  return scopedCodes.map(code => ({ label: getScenarioWorkspaceName(code), value: code }))
})
const activeScenarioImportTreeNode = computed(() => findScenarioImportTreeNode(scenarioImportTree.value, selectedScenarioImportTreeKey.value))
const scenarioImportTree = computed<ScenarioImportTreeNode[]>(() => buildScenarioImportTree(scenarioImportActiveTab.value))
const scenarioImportDefinitions = computed(() => {
  const keyword = scenarioImportKeyword.value.trim().toLowerCase()
  const node = activeScenarioImportTreeNode.value
  return definitions.value.filter((item) => {
    if (scenarioImportWorkspaceCode.value && item.workspaceCode !== scenarioImportWorkspaceCode.value) {
      return false
    }
    if (!matchesScenarioImportDefinitionScope(item, node)) {
      return false
    }
    if (!keyword) {
      return true
    }
    return item.name.toLowerCase().includes(keyword) || item.path.toLowerCase().includes(keyword)
  })
})
const scenarioImportCases = computed(() => {
  const keyword = scenarioImportKeyword.value.trim().toLowerCase()
  const node = activeScenarioImportTreeNode.value
  return apiCases.value.filter((item) => {
    if (scenarioImportWorkspaceCode.value && item.workspaceCode !== scenarioImportWorkspaceCode.value) {
      return false
    }
    if (!matchesScenarioImportCaseScope(item, node)) {
      return false
    }
    if (!keyword) {
      return true
    }
    return item.name.toLowerCase().includes(keyword) || item.path.toLowerCase().includes(keyword) || item.definitionName.toLowerCase().includes(keyword)
  })
})
const scenarioImportScenarios = computed(() => {
  const keyword = scenarioImportKeyword.value.trim().toLowerCase()
  const node = activeScenarioImportTreeNode.value
  return scenarios.value.filter((item) => {
    if (item.id === scenarioForm.id) {
      return false
    }
    if (scenarioImportWorkspaceCode.value && item.workspaceCode !== scenarioImportWorkspaceCode.value) {
      return false
    }
    if (!matchesScenarioImportScenarioScope(item, node)) {
      return false
    }
    if (!keyword) {
      return true
    }
    return item.name.toLowerCase().includes(keyword) || (item.moduleName || '').toLowerCase().includes(keyword)
  })
})
const scenarioImportSelectedDefinitionRows = computed(() => definitions.value.filter(item => scenarioImportSelectedDefinitionIds.value.includes(item.id)))
const scenarioImportSelectedCaseRows = computed(() => apiCases.value.filter(item => scenarioImportSelectedCaseIds.value.includes(item.id)))
const scenarioImportSelectedScenarioRows = computed(() => scenarios.value.filter(item => scenarioImportSelectedScenarioIds.value.includes(item.id)))
const scenarioImportSelectedTotal = computed(() =>
  scenarioImportSelectedDefinitionIds.value.length
  + scenarioImportSelectedCaseIds.value.length
  + scenarioImportSelectedScenarioIds.value.length)
const activeOwnerOptions = computed(() => users.value.filter(item => item.status === 1))
const definitionOptions = computed(() => definitions.value.map(item => ({
  label: `${item.method} ${item.name}`,
  value: item.id,
})))
const caseOptions = computed(() => apiCases.value.map(item => ({
  label: `${item.name} (${item.method} ${item.path})`,
  value: item.id,
})))
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
  const workspaceCodes = isAllScope.value
    ? Array.from(new Set([
      ...workspaces.value.map(item => item.code),
      ...definitionModules.value.map(item => item.workspaceCode),
      ...definitions.value.map(item => item.workspaceCode),
    ])).filter(Boolean)
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
      moduleId: null,
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
    moduleId: number | null,
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
        moduleId,
        method: null,
        children: [],
        childMap: new Map(),
      }
      parentMap.set(fullPath, node)
      parentChildren.push(node)
    }
    if (moduleId != null) {
      node.moduleId = moduleId
    }
    return node
  }

  const addModuleNode = (module: ApiDefinitionModuleItem) => {
    const workspaceNode = workspaceMap.get(module.workspaceCode)
    if (!workspaceNode) {
      return
    }
    const path = (module.fullPath || module.name || '').trim()
    if (!path) {
      return
    }
    const segments = path.split('/').map(part => part.trim()).filter(Boolean)
    let currentChildren = workspaceNode.children as MutableNode[]
    let currentMap = workspaceNode.childMap ?? new Map<string, MutableNode>()
    let assembled = ''
    segments.forEach((segment, index) => {
      assembled = assembled ? `${assembled}/${segment}` : segment
      const node = ensureNode(
        currentChildren,
        currentMap,
        module.workspaceCode,
        segment,
        assembled,
        index === segments.length - 1 ? module.id : null,
      )
      currentChildren = node.children as MutableNode[]
      currentMap = node.childMap ?? new Map<string, MutableNode>()
    })
  }

  const flattenModules = (items: ApiDefinitionModuleItem[]) => {
    const result: ApiDefinitionModuleItem[] = []
    const walk = (modules: ApiDefinitionModuleItem[]) => {
      for (const module of modules) {
        result.push(module)
        walk(module.children || [])
      }
    }
    walk(items)
    return result
  }
  flattenModules(definitionModules.value).forEach(addModuleNode)

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
      moduleId: null,
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
      const node = ensureNode(parentChildren, parentMap, item.workspaceCode, segment, assembled, null)
      node.count += 1
      parentChildren = node.children as MutableNode[]
      parentMap = node.childMap ?? new Map<string, MutableNode>()
    }
    workspaceNode.count += 1
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
      moduleId: node.moduleId,
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
        moduleId: null,
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
      moduleId: null,
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
    moduleId: null,
    method: null,
    children: workspaceTrees,
  }]
})

const visibleDefinitionDirectoryTree = computed<DefinitionDirectoryTreeNode[]>(() =>
  definitionDirectoryTree.value[0]?.children ?? [],
)

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
  if (selectedScenarioWorkspaceCode.value && item.workspaceCode !== selectedScenarioWorkspaceCode.value) {
    return false
  }
  if (selectedScenarioModuleId.value != null && item.moduleId !== selectedScenarioModuleId.value) {
    return false
  }
  if (scenarioFilters.status && item.status !== scenarioFilters.status) {
    return false
  }
  return true
}))

const apiTasks = computed(() => tasks.value.filter(item => item.engineType === 'API'))
const apiTaskMap = computed(() => new Map(apiTasks.value.map(item => [item.id, item])))
const apiReports = computed(() => reports.value.filter(item => apiTaskMap.value.has(item.taskId)))
function pickPreferredRunStep(steps: ApiRunStepResult[]) {
  if (!steps.length) {
    return null
  }
  return steps.find(item => !item.success) ?? steps[steps.length - 1]
}

function formatResponseSize(body?: string | null) {
  return body ? `${new Blob([body]).size} B` : '0 B'
}

function getStatusTone(statusCode: number | null | undefined) {
  if (statusCode == null) return 'neutral'
  if (statusCode >= 200 && statusCode < 300) return 'success'
  if (statusCode >= 400) return 'failed'
  return 'neutral'
}

function getDurationTone(durationMs: number | null | undefined) {
  return durationMs != null && durationMs >= 1000 ? 'slow' : 'neutral'
}

function getCaseRunResultPresentation(result?: string | null) {
  const normalized = (result || '').toUpperCase()
  if (normalized === 'PASSED') {
    return { label: '通过', tone: 'passed' }
  }
  if (normalized === 'NOT_PASSED') {
    return { label: '不通过', tone: 'not-passed' }
  }
  if (normalized === 'NO_ASSERTION') {
    return { label: '无断言', tone: 'no-assertion' }
  }
  if (normalized.includes('FAIL') || normalized.includes('ERROR')) {
    return { label: '失败', tone: 'failed' }
  }
  return { label: '无断言', tone: 'no-assertion' }
}

function getAssertionRunResultPresentation(assertionResults: ApiRunStepResult['assertionResults'], errorMessage?: string | null) {
  if (!assertionResults.length) {
    if (errorMessage) {
      return { label: '失败', tone: 'failed', visible: true }
    }
    return { label: '', tone: 'no-assertion', visible: false }
  }
  if (assertionResults.some(item => !item.success)) {
    return { label: '不通过', tone: 'not-passed', visible: true }
  }
  return { label: '通过', tone: 'passed', visible: true }
}

function assertionTypeLabel(type?: string | null) {
  const normalized = (type || '').toUpperCase()
  const labels: Record<string, string> = {
    RESPONSE_CODE: '状态码',
    STATUS_CODE: '状态码',
    RESPONSE_HEADER: '响应头',
    RESPONSE_BODY: '响应体',
    RESPONSE_TIME: '响应时间',
    VARIABLE: '变量',
    SCRIPT: '脚本',
  }
  return labels[normalized] || type || '-'
}

function assertionConditionLabel(condition?: string | null) {
  const normalized = (condition || '').toUpperCase()
  const labels: Record<string, string> = {
    EQUALS: '等于',
    NOT_EQUALS: '不等于',
    CONTAINS: '包含',
    NOT_CONTAINS: '不包含',
    STARTS_WITH: '开头是',
    ENDS_WITH: '结尾是',
    GREATER_THAN: '大于',
    GREATER_THAN_OR_EQUALS: '大于等于',
    LESS_THAN: '小于',
    LT_OR_EQUALS: '小于等于',
    REGEX: '正则匹配',
    EMPTY: '为空',
    NOT_EMPTY: '不为空',
    EXISTS: '存在',
    NOT_EXISTS: '不存在',
    UNCHECKED: '不校验',
  }
  return labels[normalized] || condition || '-'
}

function assertionResultTone(assertion: ApiAssertionResult) {
  return assertion.success ? 'passed' : 'not-passed'
}

function assertionResultLabel(assertion: ApiAssertionResult) {
  return assertion.success ? '通过' : '不通过'
}

function formatStoredResponseSize(size?: number | null) {
  if (size == null) {
    return '-'
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

const currentResponseStep = computed(() => {
  return pickPreferredRunStep(activeRequestEditorTab.value?.debugStepResults ?? [])
})
const currentResponseStatusCode = computed(() => currentResponseStep.value?.response?.statusCode ?? null)
const currentResponseDuration = computed(() => currentResponseStep.value?.durationMs ?? null)
const currentResponseSize = computed(() => formatResponseSize(currentResponseStep.value?.response?.body))
const currentResponseStatusTone = computed(() => getStatusTone(currentResponseStatusCode.value))
const currentResponseDurationTone = computed(() => getDurationTone(currentResponseDuration.value))
const currentResponseContentType = computed(() => currentResponseStep.value?.response?.contentType ?? '')
const currentResponseBody = computed(() => currentResponseStep.value?.response?.body ?? '')
const currentResponseHeaders = computed(() => currentResponseStep.value?.response?.headers ?? {})
const currentAssertionResults = computed(() => currentResponseStep.value?.assertionResults ?? [])
const currentAssertionResultPresentation = computed(() =>
  getAssertionRunResultPresentation(currentAssertionResults.value, currentDebugError.value),
)
const currentExtractionResults = computed(() => currentResponseStep.value?.extractionResults ?? [])
const currentProcessorResults = computed(() => currentResponseStep.value?.processorResults ?? [])
const currentDebugError = computed(() => currentResponseStep.value?.errorMessage || activeRequestEditorTab.value?.debugFailureSummary || '')
const showResponseEmptyState = computed(() => !currentResponseStep.value && !currentDebugError.value)
const caseDrawerResponseStep = computed(() => pickPreferredRunStep(caseDrawerDebugStepResults.value))
const caseDrawerResponseStatusCode = computed(() => caseDrawerResponseStep.value?.response?.statusCode ?? null)
const caseDrawerResponseDuration = computed(() => caseDrawerResponseStep.value?.durationMs ?? null)
const caseDrawerResponseSize = computed(() => formatResponseSize(caseDrawerResponseStep.value?.response?.body))
const caseDrawerResponseStatusTone = computed(() => getStatusTone(caseDrawerResponseStatusCode.value))
const caseDrawerResponseDurationTone = computed(() => getDurationTone(caseDrawerResponseDuration.value))
const caseDrawerResponseContentType = computed(() => caseDrawerResponseStep.value?.response?.contentType ?? '')
const caseDrawerResponseBody = computed(() => caseDrawerResponseStep.value?.response?.body ?? '')
const caseDrawerResponseHeaders = computed(() => caseDrawerResponseStep.value?.response?.headers ?? {})
const caseDrawerAssertionResults = computed(() => caseDrawerResponseStep.value?.assertionResults ?? [])
const caseDrawerAssertionResultPresentation = computed(() =>
  getAssertionRunResultPresentation(caseDrawerAssertionResults.value, caseDrawerDebugError.value),
)
const caseDrawerExtractionResults = computed(() => caseDrawerResponseStep.value?.extractionResults ?? [])
const caseDrawerProcessorResults = computed(() => caseDrawerResponseStep.value?.processorResults ?? [])
const caseDrawerDebugError = computed(() => caseDrawerResponseStep.value?.errorMessage || caseDrawerDebugFailureSummary.value || '')
const caseDrawerShowResponseEmptyState = computed(() => !caseDrawerResponseStep.value && !caseDrawerDebugError.value)
const scenarioCustomRequestResponseStep = computed(() => pickPreferredRunStep(scenarioCustomRequestDebugStepResults.value))
const scenarioCustomRequestResponseStatusCode = computed(() => scenarioCustomRequestResponseStep.value?.response?.statusCode ?? null)
const scenarioCustomRequestResponseDuration = computed(() => scenarioCustomRequestResponseStep.value?.durationMs ?? null)
const scenarioCustomRequestResponseSize = computed(() => formatResponseSize(scenarioCustomRequestResponseStep.value?.response?.body))
const scenarioCustomRequestResponseStatusTone = computed(() => getStatusTone(scenarioCustomRequestResponseStatusCode.value))
const scenarioCustomRequestResponseDurationTone = computed(() => getDurationTone(scenarioCustomRequestResponseDuration.value))
const scenarioCustomRequestResponseContentType = computed(() => scenarioCustomRequestResponseStep.value?.response?.contentType ?? '')
const scenarioCustomRequestResponseBody = computed(() => scenarioCustomRequestResponseStep.value?.response?.body ?? '')
const scenarioCustomRequestResponseHeaders = computed(() => scenarioCustomRequestResponseStep.value?.response?.headers ?? {})
const scenarioCustomRequestAssertionResults = computed(() => scenarioCustomRequestResponseStep.value?.assertionResults ?? [])
const scenarioCustomRequestDebugError = computed(() => scenarioCustomRequestResponseStep.value?.errorMessage || scenarioCustomRequestDebugFailureSummary.value || '')
const scenarioCustomRequestAssertionResultPresentation = computed(() =>
  getAssertionRunResultPresentation(scenarioCustomRequestAssertionResults.value, scenarioCustomRequestDebugError.value),
)
const scenarioCustomRequestExtractionResults = computed(() => scenarioCustomRequestResponseStep.value?.extractionResults ?? [])
const scenarioCustomRequestProcessorResults = computed(() => scenarioCustomRequestResponseStep.value?.processorResults ?? [])
const scenarioCustomRequestShowResponseEmptyState = computed(() => !scenarioCustomRequestResponseStep.value && !scenarioCustomRequestDebugError.value)
const scenarioSystemRequestResponseStep = computed(() => pickPreferredRunStep(scenarioSystemRequestDebugStepResults.value))
const scenarioSystemRequestResponseStatusCode = computed(() => scenarioSystemRequestResponseStep.value?.response?.statusCode ?? null)
const scenarioSystemRequestResponseDuration = computed(() => scenarioSystemRequestResponseStep.value?.durationMs ?? null)
const scenarioSystemRequestResponseSize = computed(() => formatResponseSize(scenarioSystemRequestResponseStep.value?.response?.body))
const scenarioSystemRequestResponseStatusTone = computed(() => getStatusTone(scenarioSystemRequestResponseStatusCode.value))
const scenarioSystemRequestResponseDurationTone = computed(() => getDurationTone(scenarioSystemRequestResponseDuration.value))
const scenarioSystemRequestResponseContentType = computed(() => scenarioSystemRequestResponseStep.value?.response?.contentType ?? '')
const scenarioSystemRequestResponseBody = computed(() => scenarioSystemRequestResponseStep.value?.response?.body ?? '')
const scenarioSystemRequestResponseHeaders = computed(() => scenarioSystemRequestResponseStep.value?.response?.headers ?? {})
const scenarioSystemRequestAssertionResults = computed(() => scenarioSystemRequestResponseStep.value?.assertionResults ?? [])
const scenarioSystemRequestDebugError = computed(() => scenarioSystemRequestResponseStep.value?.errorMessage || scenarioSystemRequestDebugFailureSummary.value || '')
const scenarioSystemRequestAssertionResultPresentation = computed(() =>
  getAssertionRunResultPresentation(scenarioSystemRequestAssertionResults.value, scenarioSystemRequestDebugError.value),
)
const scenarioSystemRequestExtractionResults = computed(() => scenarioSystemRequestResponseStep.value?.extractionResults ?? [])
const scenarioSystemRequestProcessorResults = computed(() => scenarioSystemRequestResponseStep.value?.processorResults ?? [])
const scenarioSystemRequestShowResponseEmptyState = computed(() => !scenarioSystemRequestResponseStep.value && !scenarioSystemRequestDebugError.value)
const scenarioScriptRunSteps = computed(() => {
  if (scenarioScriptDrawerMode.value !== 'edit') {
    return []
  }
  return scenarioLastRunStepResults.value.filter(item => item.stepOrder === scenarioScriptStepOrder.value)
})
const scenarioScriptResponseStep = computed(() => pickPreferredRunStep(scenarioScriptRunSteps.value))
const scenarioScriptAssertionResults = computed(() => scenarioScriptResponseStep.value?.assertionResults ?? [])
const scenarioScriptProcessorResults = computed(() => scenarioScriptResponseStep.value?.processorResults ?? [])
const scenarioScriptDebugError = computed(() => scenarioScriptResponseStep.value?.errorMessage || '')
const scenarioScriptAssertionResultPresentation = computed(() =>
  getAssertionRunResultPresentation(scenarioScriptAssertionResults.value, scenarioScriptDebugError.value),
)
const scenarioScriptResponseConsolePreview = computed(() => buildRunConsolePreview(
  scenarioScriptDebugError.value,
  scenarioScriptProcessorResults.value,
  scenarioScriptAssertionResults.value,
  scenarioScriptResponseStep.value?.extractionResults ?? [],
))
const scenarioScriptShowResponseEmptyState = computed(() => !scenarioScriptResponseStep.value && !scenarioScriptDebugError.value)
const scenarioScriptShouldShowResultPanel = computed(() => scenarioScriptDrawerMode.value === 'edit')
const caseDrawerSelectedHistoryStep = computed(() => pickPreferredRunStep(caseDrawerRunHistoryDetail.value?.stepResults ?? []))
const caseDrawerSelectedHistoryStatusCode = computed(() => caseDrawerSelectedHistoryStep.value?.response?.statusCode ?? caseDrawerRunHistoryDetail.value?.statusCode ?? null)
const caseDrawerSelectedHistoryDuration = computed(() => caseDrawerSelectedHistoryStep.value?.durationMs ?? caseDrawerRunHistoryDetail.value?.durationMs ?? null)
const caseDrawerSelectedHistorySize = computed(() => formatResponseSize(
  caseDrawerSelectedHistoryStep.value?.response?.body ?? caseDrawerRunHistoryDetail.value?.stepResults?.[0]?.response?.body,
))
const caseDrawerSelectedHistoryContentType = computed(() => caseDrawerSelectedHistoryStep.value?.response?.contentType ?? '')
const caseDrawerSelectedHistoryBody = computed(() => caseDrawerSelectedHistoryStep.value?.response?.body ?? '')
const caseDrawerSelectedHistoryHeaders = computed(() => caseDrawerSelectedHistoryStep.value?.response?.headers ?? {})
const caseDrawerSelectedHistoryError = computed(() => caseDrawerSelectedHistoryStep.value?.errorMessage || caseDrawerRunHistoryDetail.value?.failureSummary || '')
const caseDrawerSelectedHistoryResultPresentation = computed(() => getCaseRunResultPresentation(caseDrawerRunHistoryDetail.value?.result))
const caseDrawerSelectedHistoryResult = computed(() => caseDrawerSelectedHistoryResultPresentation.value.tone)
const caseDrawerSelectedHistoryResultLabel = computed(() => caseDrawerSelectedHistoryResultPresentation.value.label)
const caseDrawerSelectedHistoryStatusTone = computed(() => getStatusTone(caseDrawerSelectedHistoryStatusCode.value))
const caseDrawerSelectedHistoryDurationTone = computed(() => getDurationTone(caseDrawerSelectedHistoryDuration.value))
const caseDrawerSelectedHistoryAssertionResults = computed(() => caseDrawerSelectedHistoryStep.value?.assertionResults ?? [])
const caseDrawerSelectedHistoryRequestHeaders = computed(() => caseDrawerSelectedHistoryStep.value?.request?.headers ?? {})
const caseDrawerSelectedHistoryRequestBody = computed(() => caseDrawerSelectedHistoryStep.value?.request?.body ?? '')
const caseDrawerSelectedHistoryRequestQueryParams = computed(() => caseDrawerSelectedHistoryStep.value?.request?.queryParams ?? [])
const caseDrawerSelectedHistoryRequestCookies = computed(() => caseDrawerSelectedHistoryStep.value?.request?.cookies ?? [])
const caseDrawerSelectedHistoryRequestBodyType = computed(() => caseDrawerSelectedHistoryStep.value?.request?.bodyType ?? '')
const caseDrawerSelectedHistoryRequestBodyFormItems = computed(() => caseDrawerSelectedHistoryStep.value?.request?.bodyFormItems ?? [])
const caseDrawerSelectedHistoryRequestBodyFileName = computed(() => caseDrawerSelectedHistoryStep.value?.request?.bodyFileName ?? '')
const caseDrawerSelectedHistoryRequestBodyFileContentType = computed(() => caseDrawerSelectedHistoryStep.value?.request?.bodyFileContentType ?? '')
const shouldShowResponsePanel = computed(() => {
  if (showCaseListContent.value) {
    return false
  }
  return true
})
const queryEnabledCount = computed(() =>
  definitionForm.requestConfig.queryParams.filter(item => !isKeyValueRowEmpty(item) && item.enabled !== false).length,
)
const assertionEnabledCount = computed(() =>
  definitionForm.assertions.filter(item => item.enabled !== false).length,
)

function scheduleCaseListTableLayout() {
  void nextTick(() => {
    caseListTableRef.value?.doLayout?.()
  })
}

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
const caseDrawerAssertionEnabledCount = computed(() =>
  caseDrawerForm.assertions.filter(item => item.enabled !== false).length,
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

function getScenarioCustomRequestConfig() {
  if (!scenarioCustomRequestForm.requestConfig) {
    scenarioCustomRequestForm.requestConfig = emptyApiRequestConfig()
  }
  return scenarioCustomRequestForm.requestConfig
}

const scenarioCustomRequestQueryEnabledCount = computed(() =>
  getScenarioCustomRequestConfig().queryParams.filter(item => !isKeyValueRowEmpty(item) && item.enabled !== false).length,
)
const scenarioCustomRequestAssertionEnabledCount = computed(() =>
  (scenarioCustomRequestForm.assertions || []).filter(item => item.enabled !== false).length,
)

const scenarioCustomRequestQueryTableSelectionModel = computed({
  get: () => tableSelectionState(getScenarioCustomRequestConfig().queryParams).checked,
  set: (enabled: boolean) => toggleTableSelection(getScenarioCustomRequestConfig().queryParams, enabled),
})

const scenarioCustomRequestHeaderTableSelectionModel = computed({
  get: () => tableSelectionState(getScenarioCustomRequestConfig().headers).checked,
  set: (enabled: boolean) => toggleTableSelection(getScenarioCustomRequestConfig().headers, enabled),
})

const scenarioCustomRequestBodyFormTableSelectionModel = computed({
  get: () => tableSelectionState(getScenarioCustomRequestConfig().body.formItems).checked,
  set: (enabled: boolean) => toggleTableSelection(getScenarioCustomRequestConfig().body.formItems, enabled),
})

function isBodyMode(mode: string, form: RequestConfigHost = definitionForm) {
  if (mode === 'json') return form.requestConfig.body.type === 'RAW_JSON'
  if (mode === 'xml') return form.requestConfig.body.type === 'RAW_XML'
  if (mode === 'raw') return form.requestConfig.body.type === 'RAW_TEXT'
  return form.requestConfig.body.type === mode
}

function getModeBodyText(type: string, form: RequestConfigHost = definitionForm) {
  if (type === 'RAW_JSON') return form.requestConfig.body.jsonText || ''
  if (type === 'RAW_XML') return form.requestConfig.body.xmlText || ''
  if (type === 'RAW_TEXT') return form.requestConfig.body.plainText || ''
  return form.requestConfig.body.rawText || ''
}

function setModeBodyText(type: string, value: string, form: RequestConfigHost = definitionForm) {
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

function syncActiveBodyText(form: RequestConfigHost = definitionForm) {
  setModeBodyText(form.requestConfig.body.type, getModeBodyText(form.requestConfig.body.type, form), form)
}

function setBodyMode(mode: 'NONE' | 'FORM_DATA' | 'FORM_URLENCODED' | 'RAW_JSON' | 'RAW_XML' | 'RAW_TEXT' | 'BINARY', form: RequestConfigHost = definitionForm) {
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

const scenarioCustomRequestHost = computed<RequestConfigHost>(() => ({
  requestConfig: getScenarioCustomRequestConfig(),
}))
const scenarioCustomRequestConfig = computed(() => getScenarioCustomRequestConfig())
const scenarioCustomRequestPreProcessors = computed({
  get: () => scenarioCustomRequestForm.preProcessors || [],
  set: (value: ApiProcessorConfig[]) => {
    scenarioCustomRequestForm.preProcessors = value
  },
})
const scenarioCustomRequestPostProcessors = computed({
  get: () => scenarioCustomRequestForm.postProcessors || [],
  set: (value: ApiProcessorConfig[]) => {
    scenarioCustomRequestForm.postProcessors = value
  },
})
const scenarioCustomRequestAssertions = computed({
  get: () => scenarioCustomRequestForm.assertions || [],
  set: (value: ApiAssertionConfig[]) => {
    scenarioCustomRequestForm.assertions = value
  },
})
const scenarioScriptContent = computed({
  get: () => scenarioScriptForm.script || '',
  set: (value: string) => {
    scenarioScriptForm.script = value
  },
})
const scenarioScriptAssertions = computed({
  get: () => scenarioScriptForm.assertions || [],
  set: (value: ApiAssertionConfig[]) => {
    scenarioScriptForm.assertions = value
  },
})
const scenarioScriptAssertionEnabledCount = computed(() =>
  scenarioScriptAssertions.value.filter(item => item.enabled !== false).length,
)
const scenarioSystemRequestConfig = computed(() => scenarioSystemRequestDetail.value?.requestConfig || emptyApiRequestConfig())
const scenarioSystemRequestQueryEnabledCount = computed(() =>
  scenarioSystemRequestConfig.value.queryParams.filter(item => !isKeyValueRowEmpty(item) && item.enabled !== false).length,
)
const scenarioSystemRequestAssertionEnabledCount = computed(() =>
  (scenarioSystemRequestDetail.value?.assertions || []).filter(item => item.enabled !== false).length,
)
const scenarioSystemRequestBodyLanguage = computed<'json' | 'xml' | 'text'>(() => {
  const type = scenarioSystemRequestConfig.value.body.type
  if (type === 'RAW_JSON') return 'json'
  if (type === 'RAW_XML') return 'xml'
  return 'text'
})
const scenarioSystemRequestBodyText = computed(() =>
  getModeBodyText(scenarioSystemRequestConfig.value.body.type, { requestConfig: scenarioSystemRequestConfig.value }),
)

function isScenarioCustomRequestBodyMode(mode: string) {
  return isBodyMode(mode, scenarioCustomRequestHost.value)
}

function setScenarioCustomRequestBodyMode(mode: 'NONE' | 'FORM_DATA' | 'FORM_URLENCODED' | 'RAW_JSON' | 'RAW_XML' | 'RAW_TEXT' | 'BINARY') {
  setBodyMode(mode, scenarioCustomRequestHost.value)
}

function getSortableParamList(group: SortableParamGroup, form: RequestConfigHost = definitionForm) {
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

const scenarioCustomRequestActiveBodyRawText = computed({
  get: () => getModeBodyText(getScenarioCustomRequestConfig().body.type, scenarioCustomRequestHost.value),
  set: (value: string) => setModeBodyText(getScenarioCustomRequestConfig().body.type, value, scenarioCustomRequestHost.value),
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

const scenarioCustomRequestActiveBodyLanguage = computed<'json' | 'xml' | 'text'>(() => {
  const type = getScenarioCustomRequestConfig().body.type
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
const scenarioCustomRequestResponseBodyLanguage = computed<'json' | 'xml' | 'text'>(() => {
  const contentType = scenarioCustomRequestResponseContentType.value.toLowerCase()
  const body = scenarioCustomRequestResponseBody.value.trim()
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
const scenarioSystemRequestResponseBodyLanguage = computed<'json' | 'xml' | 'text'>(() => {
  const contentType = scenarioSystemRequestResponseContentType.value.toLowerCase()
  const body = scenarioSystemRequestResponseBody.value.trim()
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
const caseDrawerHistoryBodyLanguage = computed<'json' | 'xml' | 'text'>(() => {
  const contentType = caseDrawerSelectedHistoryContentType.value.toLowerCase()
  const body = caseDrawerSelectedHistoryBody.value.trim()
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
const caseDrawerHistoryRequestBodyLanguage = computed<'json' | 'xml' | 'text'>(() => {
  const headers = caseDrawerSelectedHistoryRequestHeaders.value
  const contentType = Object.entries(headers)
    .find(([key]) => key.toLowerCase() === 'content-type')?.[1]
    ?.toLowerCase() ?? ''
  const body = caseDrawerSelectedHistoryRequestBody.value.trim()
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
const scenarioCustomRequestResponseBodyPreview = computed(() => scenarioCustomRequestResponseBody.value || '')
const scenarioCustomRequestResponseHeadersPreview = computed(() => JSON.stringify(scenarioCustomRequestResponseHeaders.value, null, 2))
const scenarioSystemRequestResponseBodyPreview = computed(() => scenarioSystemRequestResponseBody.value || '')
const scenarioSystemRequestResponseHeadersPreview = computed(() => JSON.stringify(scenarioSystemRequestResponseHeaders.value, null, 2))
const caseDrawerHistoryBodyPreview = computed(() => caseDrawerSelectedHistoryBody.value || '')
const caseDrawerHistoryHeadersPreview = computed(() => JSON.stringify(caseDrawerSelectedHistoryHeaders.value, null, 2))
const caseDrawerHistoryRequestHeadersPreview = computed(() => JSON.stringify(caseDrawerSelectedHistoryRequestHeaders.value, null, 2))
const caseDrawerHistoryRequestBodyPreview = computed(() => caseDrawerSelectedHistoryRequestBody.value || '')
const caseDrawerHistoryRequestQueryParamsPreview = computed(() => JSON.stringify(caseDrawerSelectedHistoryRequestQueryParams.value, null, 2))
const caseDrawerHistoryRequestCookiesPreview = computed(() => JSON.stringify(caseDrawerSelectedHistoryRequestCookies.value, null, 2))
const caseDrawerHistoryRequestBodyFormItemsPreview = computed(() => JSON.stringify(caseDrawerSelectedHistoryRequestBodyFormItems.value, null, 2))
function buildRunConsolePreview(
  debugError: string,
  processorResults: ApiRunStepResult['processorResults'],
  assertionResults: ApiRunStepResult['assertionResults'],
  extractionResults: ApiRunStepResult['extractionResults'],
) {
  const lines: string[] = []
  if (debugError) {
    lines.push(`[Error] ${debugError}`)
  }
  processorResults.forEach((item, index) => {
    lines.push(`[Processor ${index + 1}] ${item.stage} / ${item.name} / ${item.success ? 'PASS' : 'FAIL'} / ${item.durationMs} ms`)
    if (item.message) {
      lines.push(`  ${item.message}`)
    }
    if (Object.keys(item.outputVariables || {}).length) {
      lines.push(`  outputVariables: ${JSON.stringify(item.outputVariables)}`)
    }
    item.logs?.forEach(log => lines.push(`  ${log}`))
  })
  assertionResults.forEach((item, index) => {
    lines.push(`[Assertion ${index + 1}] ${(item.name || item.type)} / ${item.success ? 'PASS' : 'FAIL'}`)
    if (item.message) {
      lines.push(`  ${item.message}`)
    }
    if (item.expectedValue !== undefined || item.actualValue !== undefined) {
      lines.push(`  expected: ${item.expectedValue ?? ''}`)
      lines.push(`  actual: ${item.actualValue ?? ''}`)
    }
  })
  extractionResults.forEach((item, index) => {
    lines.push(`[Extraction ${index + 1}] ${item.name} / ${item.success ? 'OK' : 'FAIL'}`)
    lines.push(`  ${item.value || item.message || ''}`)
  })
  return lines.length ? lines.join('\n') : '暂无控制台内容'
}
const responseConsolePreview = computed(() => {
  return buildRunConsolePreview(
    currentDebugError.value,
    currentProcessorResults.value,
    currentAssertionResults.value,
    currentExtractionResults.value,
  )
})
const caseDrawerResponseConsolePreview = computed(() => {
  return buildRunConsolePreview(
    caseDrawerDebugError.value,
    caseDrawerProcessorResults.value,
    caseDrawerAssertionResults.value,
    caseDrawerExtractionResults.value,
  )
})
const scenarioCustomRequestResponseConsolePreview = computed(() => {
  return buildRunConsolePreview(
    scenarioCustomRequestDebugError.value,
    scenarioCustomRequestProcessorResults.value,
    scenarioCustomRequestAssertionResults.value,
    scenarioCustomRequestExtractionResults.value,
  )
})
const scenarioSystemRequestResponseConsolePreview = computed(() => {
  return buildRunConsolePreview(
    scenarioSystemRequestDebugError.value,
    scenarioSystemRequestProcessorResults.value,
    scenarioSystemRequestAssertionResults.value,
    scenarioSystemRequestExtractionResults.value,
  )
})
const caseDrawerHistoryConsolePreview = computed(() => buildRunConsolePreview(
  caseDrawerSelectedHistoryError.value,
  caseDrawerSelectedHistoryStep.value?.processorResults ?? [],
  caseDrawerSelectedHistoryStep.value?.assertionResults ?? [],
  caseDrawerSelectedHistoryStep.value?.extractionResults ?? [],
))
function buildActualRequestPreview(
  snapshot: ApiRunStepResult['request'] | null | undefined,
  fallback: { method: string, url: string, headers: Record<string, string>, body: string | null },
) {
  if (snapshot) {
    return JSON.stringify(snapshot, null, 2)
  }
  return JSON.stringify(fallback, null, 2)
}
const actualRequestPreview = computed(() => buildActualRequestPreview(currentResponseStep.value?.request, {
    method: definitionForm.requestConfig.method || definitionForm.method || 'GET',
    url: definitionForm.requestConfig.path || definitionForm.path || '',
    headers: Object.fromEntries(
      definitionForm.requestConfig.headers
        .filter(item => !isKeyValueRowEmpty(item) && item.enabled !== false)
        .map(item => [item.key, item.value]),
    ),
    body: getModeBodyText(definitionForm.requestConfig.body.type) || null,
  }))
const caseDrawerActualRequestPreview = computed(() => buildActualRequestPreview(caseDrawerResponseStep.value?.request, {
    method: caseDrawerForm.requestConfig.method || caseDrawerForm.method || 'GET',
    url: caseDrawerForm.requestConfig.path || caseDrawerForm.path || '',
    headers: Object.fromEntries(
      caseDrawerForm.requestConfig.headers
        .filter(item => !isKeyValueRowEmpty(item) && item.enabled !== false)
        .map(item => [item.key, item.value]),
    ),
    body: getModeBodyText(caseDrawerForm.requestConfig.body.type, caseDrawerForm) || null,
  }))
const scenarioCustomRequestActualRequestPreview = computed(() => buildActualRequestPreview(scenarioCustomRequestResponseStep.value?.request, {
    method: scenarioCustomRequestConfig.value.method || 'GET',
    url: scenarioCustomRequestConfig.value.path || '',
    headers: Object.fromEntries(
      scenarioCustomRequestConfig.value.headers
        .filter(item => !isKeyValueRowEmpty(item) && item.enabled !== false)
        .map(item => [item.key, item.value]),
    ),
    body: getModeBodyText(scenarioCustomRequestConfig.value.body.type, scenarioCustomRequestHost.value) || null,
  }))
const scenarioSystemRequestActualRequestPreview = computed(() => buildActualRequestPreview(scenarioSystemRequestResponseStep.value?.request, {
    method: scenarioSystemRequestConfig.value.method || scenarioSystemRequestDetail.value?.method || 'GET',
    url: scenarioSystemRequestConfig.value.path || scenarioSystemRequestDetail.value?.path || '',
    headers: Object.fromEntries(
      scenarioSystemRequestConfig.value.headers
        .filter(item => !isKeyValueRowEmpty(item) && item.enabled !== false)
        .map(item => [item.key, item.value]),
    ),
    body: getModeBodyText(scenarioSystemRequestConfig.value.body.type, { requestConfig: scenarioSystemRequestConfig.value }) || null,
  }))
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

async function readFileAsBase64(file: File) {
  const buffer = await file.arrayBuffer()
  let binary = ''
  const bytes = new Uint8Array(buffer)
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return btoa(binary)
}

async function pickBodyFormRowFile(row: ApiKeyValue, target: ApiKeyValue[], defaults?: Partial<ApiKeyValue>) {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '*/*'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) {
      return
    }
    row.paramType = 'file'
    row.value = file.name
    row.fileName = file.name
    row.contentType = file.type || 'application/octet-stream'
    row.fileBase64 = await readFileAsBase64(file)
    handleKeyValueRowInput(target, defaults)
  }
  input.click()
}

function clearBodyFormRowFile(row: ApiKeyValue) {
  row.value = ''
  row.fileName = ''
  row.contentType = ''
  row.fileBase64 = ''
}

function formatBodyFormFileSize(row: ApiKeyValue) {
  const base64 = row.fileBase64 || ''
  if (!base64) {
    return ''
  }
  const padding = base64.endsWith('==') ? 2 : base64.endsWith('=') ? 1 : 0
  const bytes = Math.max(0, Math.floor(base64.length * 3 / 4) - padding)
  if (bytes >= 1024 * 1024) {
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  }
  if (bytes >= 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }
  return `${bytes} B`
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

watch(aiGenerationDetailGroupFilter, () => {
  if (aiGenerationDetailTypeFilter.value
    && !aiGenerationDetailTypeOptions.value.some(item => item.value === aiGenerationDetailTypeFilter.value)) {
    aiGenerationDetailTypeFilter.value = ''
  }
})

watch([filteredAiCaseGenerationResults, aiGenerationStatusFilter], () => {
  const visibleIds = new Set(filteredAiCaseGenerationResults.value.map(item => item.id))
  selectedAiCaseGenerateResultIds.value = selectedAiCaseGenerateResultIds.value.filter(id => visibleIds.has(id))
})

watch(scenarioForm, () => {
  syncActiveScenarioEditorTab()
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
  scheduleCaseListTableLayout()
}, { deep: true })

watch(
  () => [
    activeRequestEditorKey.value,
    visibleRequestEditorTabs.value.map(item => `${item.key}:${item.title}:${item.method}:${item.isDirty}`).join('|'),
  ],
  () => {
    void nextTick(() => {
      syncRequestTabStripState()
    })
  },
  { immediate: true },
)

watch(
  () => [
    activeScenarioEditorKey.value,
    scenarioEditorTabs.value.map(item => `${item.key}:${item.title}:${item.isDirty}`).join('|'),
  ],
  () => {
    void nextTick(() => {
      syncScenarioTabStripState()
    })
  },
  { immediate: true },
)

watch(() => caseListSettings.pageSize.value, () => {
  caseListCurrentPage.value = 1
  scheduleCaseListTableLayout()
})

watch(
  () => [
    showCaseListContent.value,
    activeRequestEditorKey.value,
    caseListCurrentPage.value,
    caseListSettings.visibleColumns.value.map(item => item.key).join('|'),
  ],
  () => {
    scheduleCaseListTableLayout()
  },
)

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
  document.addEventListener('mousedown', handleScenarioStepNameOutsidePointerDown, true)
  void nextTick(() => {
    const registerTabStrip = (element: HTMLElement | null, handler: () => void) => {
      if (!element) {
        return
      }
      const onScroll = () => handler()
      element.addEventListener('scroll', onScroll, { passive: true })
      tabStripCleanupFns.push(() => {
        element.removeEventListener('scroll', onScroll)
      })
      if (typeof ResizeObserver !== 'undefined') {
        const observer = new ResizeObserver(() => {
          handler()
        })
        observer.observe(element)
        resizeObservers.push(observer)
      }
    }
    registerTabStrip(requestTabNavRef.value, updateRequestTabOverflowState)
    registerTabStrip(scenarioTabNavRef.value, updateScenarioTabOverflowState)
    syncRequestTabStripState()
    syncScenarioTabStripState()
  })
  void bootstrap()
})

onBeforeUnmount(() => {
  requestEditorTabs.value.forEach(tab => tab.aiGeneration?.abortController?.abort())
  document.removeEventListener('mousedown', handleScenarioStepNameOutsidePointerDown, true)
  tabStripCleanupFns.forEach(cleanup => cleanup())
  resizeObservers.forEach(observer => observer.disconnect())
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
    fileName: '',
    contentType: '',
    fileBase64: '',
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
    && !row.fileName?.trim()
}

function normalizeKeyValueRow(row: ApiKeyValue | null | undefined, defaults?: Partial<ApiKeyValue>) {
  return emptyKeyValue({
    ...defaults,
    ...(row ?? {}),
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

function bodyParamTypeOptionsFor(form: RequestConfigHost = definitionForm) {
  if (form.requestConfig.body.type === 'FORM_DATA') {
    return bodyParamTypeOptions
  }
  return bodyParamTypeOptions.filter(option => option !== 'file' && option !== 'json')
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
    case 'query':
    case 'body-form':
      return batchAddParamStyle.value === 'standard'
        ? '每行一条，格式：参数名,类型,必填,参数值；例如 username,string,true,admin'
        : '每行一条，格式：名称<TAB>值 或 名称:值；空行自动忽略，同名以最后一条为准'
    default:
      return '每行一条，格式：名称<TAB>值 或 名称:值；空行自动忽略，同名以最后一条为准'
  }
})

const batchAddExamples = computed(() => {
  switch (batchAddMode.value) {
    case 'query':
      return batchAddParamStyle.value === 'standard'
        ? ['page,integer,true,1', 'pageSize,integer,false,20', 'keyword,string,false,test']
        : ['page\t1', 'pageSize\t20', 'keyword:test']
    case 'cookie':
      return ['SESSION\tabc123', 'token:{{authToken}}']
    case 'header':
      return ['Content-Type\tapplication/json', 'Authorization\tBearer {{token}}']
    case 'body-form':
      return batchAddParamStyle.value === 'standard'
        ? ['username,string,true,admin', 'avatar,file,false,avatar.png', 'profile,json,false,{"name":"admin"}']
        : ['username\tadmin', 'password\t123456']
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

function makeRequestEditorTab(detail?: ApiRequestEditorDetail): RequestEditorTab {
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

function makeAiCaseGenerationTab(state: AiCaseGenerateTabState): RequestEditorTab {
  const draft = buildEmptyDefinitionDetail()
  draft.resourceType = 'ai-case-generation'
  draft.id = state.definitionId
  draft.definitionId = state.definitionId
  draft.definitionName = state.definitionName
  draft.name = 'AI 生成单接口用例'
  draft.method = state.method
  draft.path = state.path
  draft.requestConfig.method = state.method
  draft.requestConfig.path = state.path
  return {
    key: `ai-case-generation-${state.definitionId}-${Date.now()}`,
    resourceType: 'ai-case-generation',
    resourceId: state.definitionId,
    definitionId: state.definitionId,
    title: 'AI 生成单接口用例',
    method: 'AI',
    activeTab: 'body',
    draft,
    savedFingerprint: '',
    isDirty: false,
    debugReportId: null,
    debugFailureSummary: '',
    debugStepResults: [],
    aiGeneration: state,
  }
}

function syncActiveRequestEditorTab() {
  const current = activeRequestEditorTab.value
  if (!current || requestEditorSyncing.value) {
    return
  }
  if (current.resourceType === 'ai-case-generation') {
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
  current.isDirty = true
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
  current.isDirty = true
}

function setActiveRequestContentTab(tab: RequestContentTab) {
  activeRequestTab.value = tab
  const current = activeRequestEditorTab.value
  if (current) {
    current.activeTab = tab
  }
  definitionEditorShellRef.value?.scrollTo({
    top: 0,
    behavior: 'smooth',
  })
}

function emptyApiRequestConfig(): ApiRequestConfig {
  return {
    method: 'GET',
    path: '',
    timeoutMs: 10000,
    queryParams: [],
    headers: [],
    cookies: [],
    body: { type: 'NONE', rawText: '', formItems: [], contentType: '', fileName: '', binaryBase64: '', jsonText: '', xmlText: '', plainText: '' },
    authConfig: emptyAuthConfig(),
  }
}

function toScenarioModuleTreeNode(module: ApiScenarioModuleItem): ScenarioModuleTreeNode {
  return {
    key: `scenario-module-${module.id}`,
    type: 'module',
    id: module.id,
    name: module.name,
    scenarioCount: module.scenarioCount || 0,
    workspaceCode: module.workspaceCode,
    children: (module.children || []).map(toScenarioModuleTreeNode),
  }
}

function matchesScenarioModuleKeyword(node: ScenarioModuleTreeNode, keyword: string): boolean {
  const trimmed = keyword.trim().toLowerCase()
  if (!trimmed) {
    return true
  }
  return node.name.toLowerCase().includes(trimmed)
    || node.children.some(child => matchesScenarioModuleKeyword(child, keyword))
}

function getScenarioWorkspaceName(targetWorkspaceCode: string) {
  return workspaces.value.find(item => item.code === targetWorkspaceCode)?.name
    ?? scenarios.value.find(item => item.workspaceCode === targetWorkspaceCode)?.workspaceName
    ?? scenarioModules.value.find(item => item.workspaceCode === targetWorkspaceCode)?.workspaceName
    ?? definitions.value.find(item => item.workspaceCode === targetWorkspaceCode)?.workspaceName
    ?? apiCases.value.find(item => item.workspaceCode === targetWorkspaceCode)?.workspaceName
    ?? targetWorkspaceCode
}

function getScenarioImportWorkspaceCodes() {
  const codes = scenarioImportWorkspaceCode.value
    ? [scenarioImportWorkspaceCode.value]
    : (isAllScope.value
        ? Array.from(new Set([
          ...workspaces.value.map(item => item.code),
          ...definitions.value.map(item => item.workspaceCode),
          ...apiCases.value.map(item => item.workspaceCode),
          ...scenarios.value.map(item => item.workspaceCode),
        ])).filter(Boolean)
        : [workspaceCode.value])
  return codes
}

function buildScenarioImportTree(type: ScenarioImportTab): ScenarioImportTreeNode[] {
  const keyword = scenarioImportTreeKeyword.value.trim().toLowerCase()
  const workspaceNodes = getScenarioImportWorkspaceCodes().map((code) => {
    const children = type === 'scenario'
      ? buildScenarioImportScenarioModuleNodes(code)
      : buildScenarioImportDefinitionModuleNodes(code, type)
    const workspaceName = getScenarioWorkspaceName(code)
    return {
      key: `scenario-import-workspace:${code}`,
      type: 'workspace' as const,
      label: workspaceName,
      workspaceCode: code,
      modulePath: null,
      moduleId: null,
      count: type === 'api'
        ? definitions.value.filter(item => item.workspaceCode === code).length
        : type === 'case'
          ? apiCases.value.filter(item => item.workspaceCode === code).length
          : scenarios.value.filter(item => item.workspaceCode === code && item.id !== scenarioForm.id).length,
      children,
    }
  }).filter(node => !keyword || node.label.toLowerCase().includes(keyword) || node.children.length)
  return [{
    key: 'scenario-import-all',
    type: 'root',
    label: type === 'scenario' ? '全部场景' : '全部接口',
    workspaceCode: '',
    modulePath: null,
    moduleId: null,
    count: type === 'api'
      ? definitions.value.length
      : type === 'case'
        ? apiCases.value.length
        : scenarios.value.filter(item => item.id !== scenarioForm.id).length,
    children: workspaceNodes,
  }]
}

function buildScenarioImportDefinitionModuleNodes(targetWorkspaceCode: string, type: 'api' | 'case'): ScenarioImportTreeNode[] {
  type MutableNode = ScenarioImportTreeNode & { childMap?: Map<string, MutableNode> }
  const sourceDirectories = type === 'api'
    ? definitions.value
      .filter(item => item.workspaceCode === targetWorkspaceCode)
      .map(item => item.directoryName || '')
    : apiCases.value
      .filter(item => item.workspaceCode === targetWorkspaceCode)
      .map((item) => definitions.value.find(definition => definition.id === item.definitionId)?.directoryName || '')
  const rootChildren: MutableNode[] = []
  const rootMap = new Map<string, MutableNode>()
  const ensureNode = (parentChildren: MutableNode[], parentMap: Map<string, MutableNode>, label: string, fullPath: string) => {
    let node = parentMap.get(fullPath)
    if (!node) {
      node = {
        key: `scenario-import-definition-module:${targetWorkspaceCode}:${fullPath}`,
        type: 'module',
        label,
        workspaceCode: targetWorkspaceCode,
        modulePath: fullPath,
        moduleId: null,
        count: 0,
        children: [],
        childMap: new Map(),
      }
      parentMap.set(fullPath, node)
      parentChildren.push(node)
    }
    return node
  }
  for (const directory of sourceDirectories) {
    const path = directory.trim()
    if (!path) {
      continue
    }
    const segments = path.split('/').map(item => item.trim()).filter(Boolean)
    let currentChildren = rootChildren
    let currentMap = rootMap
    let assembled = ''
    for (const segment of segments) {
      assembled = assembled ? `${assembled}/${segment}` : segment
      const node = ensureNode(currentChildren, currentMap, segment, assembled)
      node.count += 1
      currentChildren = node.children as MutableNode[]
      currentMap = node.childMap ?? new Map<string, MutableNode>()
    }
  }
  return stripScenarioImportChildMap(rootChildren)
}

function buildScenarioImportScenarioModuleNodes(targetWorkspaceCode: string): ScenarioImportTreeNode[] {
  const toNode = (module: ApiScenarioModuleItem): ScenarioImportTreeNode => ({
    key: `scenario-import-scenario-module:${module.id}`,
    type: 'module',
    label: module.name,
    workspaceCode: module.workspaceCode,
    modulePath: null,
    moduleId: module.id,
    count: module.scenarioCount || 0,
    children: (module.children || []).map(toNode),
  })
  const keyword = scenarioImportTreeKeyword.value.trim().toLowerCase()
  return scenarioModules.value
    .filter(item => item.workspaceCode === targetWorkspaceCode)
    .map(toNode)
    .filter(node => !keyword || matchesScenarioImportTreeKeyword(node, keyword))
}

function stripScenarioImportChildMap(nodes: Array<ScenarioImportTreeNode & { childMap?: Map<string, ScenarioImportTreeNode> }>): ScenarioImportTreeNode[] {
  const keyword = scenarioImportTreeKeyword.value.trim().toLowerCase()
  return nodes
    .sort((left, right) => left.label.localeCompare(right.label, 'zh-CN'))
    .map(node => ({
      key: node.key,
      type: node.type,
      label: node.label,
      workspaceCode: node.workspaceCode,
      modulePath: node.modulePath,
      moduleId: node.moduleId,
      count: node.count,
      children: stripScenarioImportChildMap(node.children as Array<ScenarioImportTreeNode & { childMap?: Map<string, ScenarioImportTreeNode> }>),
    }))
    .filter(node => !keyword || matchesScenarioImportTreeKeyword(node, keyword))
}

function matchesScenarioImportTreeKeyword(node: ScenarioImportTreeNode, keyword: string): boolean {
  return node.label.toLowerCase().includes(keyword) || node.children.some(child => matchesScenarioImportTreeKeyword(child, keyword))
}

function findScenarioImportTreeNode(nodes: ScenarioImportTreeNode[], key: string): ScenarioImportTreeNode | null {
  for (const node of nodes) {
    if (node.key === key) {
      return node
    }
    const child = findScenarioImportTreeNode(node.children, key)
    if (child) {
      return child
    }
  }
  return null
}

function matchesScenarioImportDefinitionScope(item: ApiDefinitionItem, node: ScenarioImportTreeNode | null) {
  if (!node || node.type === 'root') {
    return true
  }
  if (item.workspaceCode !== node.workspaceCode) {
    return false
  }
  if (node.type === 'workspace') {
    return true
  }
  const path = item.directoryName || ''
  return !!node.modulePath && (path === node.modulePath || path.startsWith(`${node.modulePath}/`))
}

function matchesScenarioImportCaseScope(item: ApiDefinitionCaseItem, node: ScenarioImportTreeNode | null) {
  if (!node || node.type === 'root') {
    return true
  }
  if (item.workspaceCode !== node.workspaceCode) {
    return false
  }
  if (node.type === 'workspace') {
    return true
  }
  const definition = definitions.value.find(definitionItem => definitionItem.id === item.definitionId)
  const path = definition?.directoryName || ''
  return !!node.modulePath && (path === node.modulePath || path.startsWith(`${node.modulePath}/`))
}

function matchesScenarioImportScenarioScope(item: ApiScenarioItem, node: ScenarioImportTreeNode | null) {
  if (!node || node.type === 'root') {
    return true
  }
  if (item.workspaceCode !== node.workspaceCode) {
    return false
  }
  if (node.type === 'workspace') {
    return true
  }
  return item.moduleId === node.moduleId
}

function flattenScenarioModules(modules: ApiScenarioModuleItem[], level = 0): Array<ApiScenarioModuleItem & { level: number }> {
  return modules.flatMap(module => [
    { ...module, level },
    ...flattenScenarioModules(module.children || [], level + 1),
  ])
}

function scenarioStepTypeLabel(type?: string | null) {
  return scenarioStepTypeOptions.find(item => item.value === type)?.label || '接口'
}

function scenarioStepTypeBadgeLabel(type?: string | null) {
  const map: Record<string, string> = {
    API: '引用 API',
    API_CASE: '引用 API',
    CUSTOM_REQUEST: '自定义请求',
    API_SCENARIO: '引用场景',
    IF_CONTROLLER: '条件控制器',
    LOOP_CONTROLLER: '循环控制器',
    ONCE_ONLY_CONTROLLER: '仅一次控制器',
    CONSTANT_TIMER: '等待时间',
    SCRIPT: '脚本操作',
  }
  return map[type || ''] || scenarioStepTypeLabel(type)
}

function scenarioStepDisplayName(step: ApiScenarioStep) {
  if (step.stepName?.trim()) {
    return step.stepName
  }
  if (step.stepType === 'API') {
    return definitions.value.find(item => item.id === step.resourceId)?.name || '接口'
  }
  if (step.stepType === 'API_CASE') {
    return apiCases.value.find(item => item.id === step.resourceId)?.name || '接口用例'
  }
  if (step.stepType === 'API_SCENARIO') {
    return scenarios.value.find(item => item.id === step.resourceId)?.name || '场景'
  }
  if (step.stepType === 'CUSTOM_REQUEST') {
    return step.requestConfig?.path || '自定义请求'
  }
  if (step.stepType === 'SCRIPT') {
    return '脚本操作'
  }
  return scenarioStepTypeLabel(step.stepType)
}

function scenarioStatusLabel(status?: string | null) {
  return scenarioStatusOptions.find(item => item.value === status)?.label || '进行中'
}

function setCaseDrawerRequestContentTab(tab: RequestContentTab) {
  caseDrawerRequestTab.value = tab
  const current = activeCaseDrawerEditorTab.value
  if (current) {
    current.activeTab = tab
  }
}

async function setCaseDrawerViewTab(tab: CaseDrawerViewTab) {
  caseDrawerViewTab.value = tab
  if (tab === 'runHistory' && caseDrawerForm.id && !caseDrawerRunHistoryItems.value.length && !caseDrawerRunHistoryLoading.value) {
    await loadCaseDrawerRunHistory(caseDrawerForm.id)
  }
  if (tab === 'changeHistory' && caseDrawerForm.id && !caseDrawerChangeHistoryItems.value.length && !caseDrawerChangeHistoryLoading.value) {
    await loadCaseDrawerChangeHistory(caseDrawerForm.id)
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

function resetCaseDrawerRunHistoryState() {
  caseDrawerRunHistoryLoading.value = false
  caseDrawerRunHistoryDetailLoading.value = false
  caseDrawerRunHistoryItems.value = []
  caseDrawerRunHistoryDetail.value = null
  selectedCaseDrawerRunHistoryId.value = null
  caseDrawerHistoryPreviewTab.value = 'body'
  caseDrawerHistoryRequestPreviewTab.value = 'body'
  caseDrawerHistoryView.value = 'list'
}

function resetCaseDrawerChangeHistoryState() {
  caseDrawerChangeHistoryLoading.value = false
  caseDrawerChangeHistoryItems.value = []
}

function getPreferredHistoryRequestPreviewTab() {
  return caseDrawerSelectedHistoryRequestBody.value.trim() ? 'body' : 'header'
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

async function selectCaseDrawerRunHistory(historyId: number | null) {
  selectedCaseDrawerRunHistoryId.value = historyId
  caseDrawerHistoryView.value = historyId ? 'detail' : 'list'
  if (!historyId) {
    caseDrawerRunHistoryDetail.value = null
    return
  }
  caseDrawerRunHistoryDetailLoading.value = true
  try {
    caseDrawerRunHistoryDetail.value = await platformApi.getApiDefinitionCaseRunHistoryDetail(workspaceCode.value, historyId)
    caseDrawerHistoryPreviewTab.value = 'body'
    caseDrawerHistoryRequestPreviewTab.value = getPreferredHistoryRequestPreviewTab()
  }
  catch (error) {
    caseDrawerRunHistoryDetail.value = null
    ElMessage.error((error as Error).message)
  }
  finally {
    caseDrawerRunHistoryDetailLoading.value = false
  }
}

function handleCaseDrawerHistoryRowClick(row: ApiDefinitionCaseRunHistoryItem) {
  void selectCaseDrawerRunHistory(row.id)
}

function backToCaseDrawerHistoryList() {
  caseDrawerHistoryView.value = 'list'
}

async function loadCaseDrawerRunHistory(caseId?: number | null, preferredHistoryId?: number | null) {
  if (!caseId) {
    resetCaseDrawerRunHistoryState()
    return
  }
  caseDrawerRunHistoryLoading.value = true
  try {
    const response = await platformApi.getApiDefinitionCaseRunHistory(workspaceCode.value, caseId)
    caseDrawerRunHistoryItems.value = response.items || []
    if (preferredHistoryId) {
      await selectCaseDrawerRunHistory(preferredHistoryId)
      return
    }
    if (caseDrawerHistoryView.value === 'detail' && selectedCaseDrawerRunHistoryId.value) {
      const selectedStillExists = caseDrawerRunHistoryItems.value.some(item => item.id === selectedCaseDrawerRunHistoryId.value)
      if (selectedStillExists) {
        await selectCaseDrawerRunHistory(selectedCaseDrawerRunHistoryId.value)
        return
      }
    }
    selectedCaseDrawerRunHistoryId.value = null
    caseDrawerRunHistoryDetail.value = null
    caseDrawerHistoryPreviewTab.value = 'body'
    caseDrawerHistoryRequestPreviewTab.value = 'body'
    caseDrawerHistoryView.value = 'list'
  }
  catch (error) {
    resetCaseDrawerRunHistoryState()
    ElMessage.error((error as Error).message)
  }
  finally {
    caseDrawerRunHistoryLoading.value = false
  }
}

async function loadCaseDrawerChangeHistory(caseId?: number | null) {
  if (!caseId) {
    resetCaseDrawerChangeHistoryState()
    return
  }
  caseDrawerChangeHistoryLoading.value = true
  try {
    const response = await platformApi.getApiDefinitionCaseChangeHistory(workspaceCode.value, caseId)
    caseDrawerChangeHistoryItems.value = response.items || []
  }
  catch (error) {
    resetCaseDrawerChangeHistoryState()
    ElMessage.error((error as Error).message)
  }
  finally {
    caseDrawerChangeHistoryLoading.value = false
  }
}

function applyDebugResponseToEditorTab(tab: RequestEditorTab | null | undefined, response: {
  reportId: number | null
  failureSummary?: string | null
  stepResults?: ApiRunStepResult[] | null
}) {
  if (!tab) {
    return
  }
  tab.debugReportId = response.reportId
  tab.debugFailureSummary = response.failureSummary || ''
  tab.debugStepResults = response.stepResults || []
}

function activateRequestEditorTab(key: string) {
  const target = requestEditorTabs.value.find(item => item.key === key)
  if (!target) {
    return
  }
  activeRequestEditorKey.value = key
  if (target.resourceType === 'ai-case-generation') {
    aiCaseGenerateResults.value = [...(target.aiGeneration?.results ?? [])]
    activeRequestTab.value = 'body'
    return
  }
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

async function closeRequestEditorTab(key: string, options?: { activateFallback?: boolean }) {
  const closing = requestEditorTabs.value.find(item => item.key === key)
  if (!closing) {
    return
  }
  if (closing.resourceType === 'ai-case-generation' && hasUnacceptedAiGeneratedCases(closing)) {
    await ElMessageBox.confirm('关闭后未采纳的 AI 生成用例将会废弃', '关闭窗口？', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
  }
  closing.aiGeneration?.abortController?.abort()
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

async function closeOtherRequestEditorTabs() {
  const hasDirty = visibleRequestEditorTabs.value.some(item => item.key !== activeRequestEditorKey.value && item.isDirty)
  if (hasDirty) {
    await ElMessageBox.confirm('其他请求页签存在未保存修改，确认关闭其他页签吗？', '关闭其他请求', { type: 'warning' })
  }
  const activeDefinitionTab = activeRequestEditorTab.value
  const keepKeys = new Set<string>()
  if (activeDefinitionTab?.resourceType === 'definition') {
    keepKeys.add(activeDefinitionTab.key)
  }
  requestEditorTabs.value = requestEditorTabs.value.filter(item => item.resourceType !== 'definition' || keepKeys.has(item.key))
  if (!activeDefinitionTab || activeDefinitionTab.resourceType !== 'definition') {
    const fallback = visibleRequestEditorTabs.value[0]
    if (fallback) {
      activateRequestEditorTab(fallback.key)
    }
  }
}

async function closeCaseDrawer() {
  const activeCaseTab = activeCaseDrawerEditorTab.value
  if (!activeCaseTab) {
    aiCaseDrawerResultId.value = ''
    return
  }
  if (aiCaseDrawerResultId.value) {
    const item = findAiCaseGenerateResult(aiCaseDrawerResultId.value)
    if (item) {
      syncAiGeneratedCaseFromCaseDrawer(item)
    }
    activeCaseTab.isDirty = false
  }
  await closeRequestEditorTab(activeCaseTab.key, { activateFallback: false })
  caseDrawerVisible.value = false
  caseDrawerEditorKey.value = ''
  caseDrawerViewTab.value = 'detail'
  caseDrawerRequestTab.value = 'body'
  caseDrawerResponsePreviewTab.value = 'body'
  caseDrawerSourceEditorKey.value = ''
  caseDrawerMode.value = 'create'
  aiCaseDrawerResultId.value = ''
  resetCaseDrawerDebugState()
  resetCaseDrawerRunHistoryState()
}

function createAiCaseGenerationPlaceholder(option: AiCaseGenerateOption, index: number): AiCaseGenerateResult {
  const group = aiCaseGenerateGroups.find(groupItem => groupItem.key === option.group)?.label ?? '\u5176\u4ed6'
  return {
    id: createAiCaseGenerationItemId(index),
    name: option.label,
    directoryName: definitionForm.directoryName || '',
    tags: [group, option.label],
    description: 'AI \u6b63\u5728\u751f\u6210\u63a5\u53e3\u7528\u4f8b',
    group,
    type: option.label,
    expected: '\u751f\u6210\u5b8c\u6210\u540e\u5c55\u793a\u9884\u671f\u7ed3\u679c',
    status: 'generating',
    activeTab: 'body',
    responseTab: 'body',
    requestConfig: cloneScenarioRequestConfig(definitionForm.requestConfig),
    assertions: [],
    preProcessors: [],
    postProcessors: [],
  }
}

function createAiCaseGenerationItemId(index: number) {
  return `ai-case-${Date.now()}-${index}`
}

function buildAiCaseGenerationRequestSlots(): AiCaseGenerateResult[] {
  const selected = aiCaseGenerateOptions.filter(item => aiCaseGenerateSelectedOptions.value.includes(item.key))
  const targetCount = resolveAiCaseGenerateTargetCount(selected.length)
  return Array.from({ length: targetCount }, (_, index) =>
    createAiCaseGenerationPlaceholder(selected[index % selected.length], index))
}

function resolveAiCaseGenerateTargetCount(selectedCount: number) {
  if (aiCaseGenerateCount.value === 'AUTO') {
    return Math.min(Math.max(selectedCount, 1), 12)
  }
  return Math.max(1, Math.min(80, Number(aiCaseGenerateCount.value)))
}

function hasUnacceptedAiGeneratedCases(tab: RequestEditorTab) {
  return (tab.aiGeneration?.results || []).some(item =>
    item.status !== 'generating'
    && item.status !== 'failed'
    && item.status !== 'accepted'
    && item.status !== 'discarded')
}

function createAiCaseGenerationPlaceholderFromEvent(event: ApiAiCaseGenerationEvent, index: number): AiCaseGenerateResult {
  const fallbackOption = aiCaseGenerateOptions.find(item => item.label === event.type)
  return createAiCaseGenerationPlaceholder({
    key: event.itemId || `stream-${index}`,
    group: fallbackOption?.group ?? 'positive',
    label: event.type || `\u7528\u4f8b ${index + 1}`,
  }, index)
}

function buildCaseDraftFromCurrentDefinition(options?: { fromSavedDefinition?: boolean }) {
  if (definitionForm.resourceType !== 'definition' || !definitionForm.id) {
    throw new Error('请先保存接口，再创建用例')
  }
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

async function openCaseEditor(id: number, options?: { mode?: Extract<CaseDrawerMode, 'edit' | 'run'> }) {
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
    caseDrawerMode.value = options?.mode ?? 'edit'
    caseDrawerViewTab.value = 'detail'
    caseDrawerEditorKey.value = target.key
    caseDrawerRequestTab.value = target.activeTab || resolveDefaultRequestTab(target.draft)
    caseDrawerResponsePreviewTab.value = 'body'
    caseDrawerHistoryPreviewTab.value = 'body'
    applyCaseDrawerDetailToForm(target.draft)
    syncCaseDrawerDebugStateFromTab(target)
    if (caseDrawerMode.value === 'run') {
      await loadCaseDrawerRunHistory(target.resourceId)
    }
    else {
      resetCaseDrawerRunHistoryState()
    }
    caseDrawerVisible.value = true
  }
}

function openCaseDraftFromDefinition(options?: { fromSavedDefinition?: boolean }) {
  let detail: ApiRequestEditorDetail
  try {
    detail = buildCaseDraftFromCurrentDefinition(options)
  }
  catch (error) {
    ElMessage.warning((error as Error).message)
    return
  }
  const tab = makeRequestEditorTab(detail)
  requestEditorTabs.value.push(tab)
  caseDrawerSourceEditorKey.value = activeRequestEditorKey.value
  caseDrawerCreateSource.value = options?.fromSavedDefinition ? 'savedDefinition' : 'draft'
  caseDrawerMode.value = 'create'
  caseDrawerViewTab.value = 'detail'
  caseDrawerEditorKey.value = tab.key
  caseDrawerRequestTab.value = tab.activeTab || resolveDefaultRequestTab(tab.draft)
  caseDrawerResponsePreviewTab.value = 'body'
  caseDrawerHistoryPreviewTab.value = 'body'
  applyCaseDrawerDetailToForm(tab.draft)
  syncCaseDrawerDebugStateFromTab(tab)
  resetCaseDrawerRunHistoryState()
  caseDrawerVisible.value = true
}

function openAiCaseGenerateDrawer() {
  if (!canCreateCaseForCurrentDefinition.value) {
    ElMessage.warning('请先保存接口，再使用 AI 生成用例')
    return
  }
  aiCaseGenerateDrawerVisible.value = true
  void loadAiCaseGenerateModels()
}

async function loadAiCaseGenerateModels() {
  if (aiCaseGenerateProviderLoading.value || aiCaseGenerateProviderConnections.value.length) {
    return
  }
  aiCaseGenerateProviderLoading.value = true
  try {
    aiCaseGenerateProviderConnections.value = await platformApi.getAiProviderConnections(workspaceCode.value)
    if (!aiCaseGenerateModel.value && aiCaseGenerateModelOptions.value.length) {
      aiCaseGenerateModel.value = aiCaseGenerateModelOptions.value[0].value
    }
  } catch (error) {
    ElMessage.error((error as Error).message || 'AI 模型配置加载失败')
  } finally {
    aiCaseGenerateProviderLoading.value = false
  }
}

function toggleAiCaseGroup(group: AiCaseGenerateGroup, checked: boolean) {
  const groupKeys = aiCaseGenerateOptions.filter(item => item.group === group).map(item => item.key)
  const next = new Set(aiCaseGenerateSelectedOptions.value)
  groupKeys.forEach((key) => {
    if (checked) {
      next.add(key)
    } else {
      next.delete(key)
    }
  })
  aiCaseGenerateSelectedOptions.value = Array.from(next)
}

function isAiCaseGroupAllSelected(group: AiCaseGenerateGroup) {
  const groupKeys = aiCaseGenerateOptions.filter(item => item.group === group).map(item => item.key)
  return groupKeys.length > 0 && groupKeys.every(key => aiCaseGenerateSelectedOptions.value.includes(key))
}

function openAiCaseGenerationResultTab(results: AiCaseGenerateResult[]) {
  if (!definitionForm.id) {
    ElMessage.warning('请先保存接口，再生成用例')
    return
  }
  const state = reactive<AiCaseGenerateTabState>({
    definitionId: definitionForm.id,
    definitionName: definitionForm.name || '未命名接口',
    method: definitionForm.requestConfig.method || 'GET',
    path: definitionForm.requestConfig.path || definitionForm.path || '',
    results,
    generating: true,
    abortController: null,
  })
  aiGenerationStatusFilter.value = 'pending'
  aiGenerationDetailGroupFilter.value = ''
  aiGenerationDetailTypeFilter.value = ''
  aiGenerationDetailKeyword.value = ''
  const tab = makeAiCaseGenerationTab(state)
  tab.aiGeneration = state
  tab.title = state.definitionName ? `AI 用例 - ${state.definitionName}` : 'AI 生成接口用例'
  tab.isDirty = false
  aiCaseGenerateResults.value = state.results
  requestEditorTabs.value.push(tab)
  aiCaseGenerateDrawerVisible.value = false
  activateRequestEditorTab(tab.key)
  activeAiGeneratedCaseId.value = results[0]?.id ?? ''
  return state
}

async function submitAiCaseGeneratePreview() {
  if (!aiCaseGenerateSelectedOptions.value.length) {
    ElMessage.warning('请至少选择一种生成类型')
    return
  }
  if (!definitionForm.id) {
    ElMessage.warning('请先保存接口，再生成用例')
    return
  }
  const model = parseAiCaseGenerateModelSelection()
  if (!model) {
    ElMessage.warning('请选择 AI 连接池配置的模型')
    return
  }
  const requestSlots = buildAiCaseGenerationRequestSlots()
  aiCaseGenerateResults.value = []
  const state = openAiCaseGenerationResultTab([])
  if (!state) {
    return
  }
  const abortController = new AbortController()
  state.abortController = abortController
  try {
    await platformApi.streamGenerateApiDefinitionCases(workspaceCode.value, buildAiCaseGenerationPayload(model, requestSlots), {
      signal: abortController.signal,
      onEvent: event => handleAiCaseGenerationEvent(state, event),
    })
  }
  catch (error) {
    if ((error as Error).name === 'AbortError') {
      markRemainingAiCaseGenerationRowsFailed(state, '已停止生成')
      return
    }
    markRemainingAiCaseGenerationRowsFailed(state, (error as Error).message || 'AI 生成失败')
    ElMessage.error((error as Error).message || 'AI 生成失败')
  }
  finally {
    state.generating = false
    state.abortController = null
  }
}

function parseAiCaseGenerateModelSelection() {
  const [connectionIdText, ...modelParts] = aiCaseGenerateModel.value.split(':')
  const connectionId = Number(connectionIdText)
  const modelName = modelParts.join(':')
  if (!Number.isFinite(connectionId) || connectionId <= 0 || !modelName.trim()) {
    return null
  }
  return { connectionId, modelName: modelName.trim() }
}

function buildAiCaseGenerationPayload(model: { connectionId: number, modelName: string }, placeholders: AiCaseGenerateResult[]): ApiAiCaseGenerationPayload {
  const selectedOptions = aiCaseGenerateOptions.filter(item => aiCaseGenerateSelectedOptions.value.includes(item.key))
  return {
    workspaceCode: definitionForm.workspaceCode || workspaceCode.value,
    definitionId: definitionForm.id || 0,
    definitionName: definitionForm.name || '',
    name: definitionForm.name || '',
    method: definitionForm.requestConfig.method || definitionForm.method || 'GET',
    path: definitionForm.requestConfig.path || definitionForm.path || '',
    description: definitionForm.description || '',
    providerConnectionId: model.connectionId,
    modelName: model.modelName,
    caseCount: String(aiCaseGenerateCount.value),
    noDuplicate: aiCaseGenerateNoDuplicate.value,
    prompt: aiCaseGeneratePrompt.value,
    options: placeholders.map((item, index) => ({
      id: item.id,
      key: selectedOptions[index % selectedOptions.length]?.key || item.type,
      group: aiCaseGenerateGroups.find(group => group.label === item.group)?.key || 'positive',
      label: item.type,
      groupLabel: item.group,
    })),
    requestConfig: cloneScenarioRequestConfig(definitionForm.requestConfig),
    assertions: JSON.parse(JSON.stringify(definitionForm.assertions || [])) as ApiAssertionConfig[],
    preProcessors: JSON.parse(JSON.stringify(definitionForm.preProcessors || [])) as ApiProcessorConfig[],
    postProcessors: JSON.parse(JSON.stringify(definitionForm.postProcessors || [])) as ApiProcessorConfig[],
    existingCases: currentDefinitionCases.value.map(item => ({
      id: item.id,
      name: item.name,
      tags: item.tags || [],
    })),
  }
}

function isActiveAiGenerationState(state: AiCaseGenerateTabState) {
  return activeRequestEditorTab.value?.resourceType === 'ai-case-generation'
    && activeRequestEditorTab.value.aiGeneration === state
}

function handleAiCaseGenerationEvent(state: AiCaseGenerateTabState, event: ApiAiCaseGenerationEvent) {
  const refreshResults = () => {
    state.results = [...state.results]
    if (isActiveAiGenerationState(state)) {
      aiCaseGenerateResults.value = state.results
    }
  }
  if (event.event === 'completed') {
    state.generating = false
    refreshResults()
    return
  }
  if (event.event === 'failed') {
    markRemainingAiCaseGenerationRowsFailed(state, event.message || 'AI \u751f\u6210\u5931\u8d25')
    state.generating = false
    refreshResults()
    return
  }
  if (event.event === 'item_generating') {
    return
  }
  if (event.event === 'item_outline' && event.outline) {
    const target = findOrCreateOutlineAiCaseGenerationResult(state, event)
    applyAiGeneratedOutlineToResult(target, event.outline)
    refreshResults()
    return
  }
  if (event.event === 'item_failed') {
    const target = findOrCreateFailedAiCaseGenerationResult(state, event)
    if (event.outline) {
      applyAiGeneratedOutlineToResult(target, event.outline)
    }
    target.status = 'failed'
    target.errorMessage = event.message || '\u751f\u6210\u5931\u8d25'
    target.group = event.group || target.group
    target.type = event.type || target.type
    refreshResults()
    return
  }
  if (event.event !== 'item_completed' || !event.item) {
    return
  }
  const target = findOrCreateCompletedAiCaseGenerationResult(state, event)
  if (!target) {
    return
  }
  applyAiGeneratedDraftToResult(target, event.item)
  refreshResults()
}

function findOrCreateCompletedAiCaseGenerationResult(state: AiCaseGenerateTabState, event: ApiAiCaseGenerationEvent) {
  const existing = findFirstAiCaseGenerationPlaceholder(state, event.itemId || undefined)
  if (existing) {
    return existing
  }
  const result = createAiCaseGenerationPlaceholderFromEvent(event, state.results.length)
  if (event.itemId) {
    result.id = event.itemId
  }
  result.status = 'pending'
  state.results = [...state.results, result]
  return result
}

function findOrCreateOutlineAiCaseGenerationResult(state: AiCaseGenerateTabState, event: ApiAiCaseGenerationEvent) {
  const existing = findFirstAiCaseGenerationPlaceholder(state, event.itemId || undefined)
  if (existing) {
    return existing
  }
  const result = createAiCaseGenerationPlaceholderFromEvent(event, state.results.length)
  if (event.itemId) {
    result.id = event.itemId
  }
  result.status = 'generating'
  state.results = [...state.results, result]
  return result
}

function findOrCreateFailedAiCaseGenerationResult(state: AiCaseGenerateTabState, event: ApiAiCaseGenerationEvent) {
  const existing = findFirstAiCaseGenerationPlaceholder(state, event.itemId || undefined)
  if (existing) {
    return existing
  }
  const result = createAiCaseGenerationPlaceholderFromEvent(event, state.results.length)
  if (event.itemId) {
    result.id = event.itemId
  }
  result.status = 'failed'
  state.results = [...state.results, result]
  return result
}

function findFirstAiCaseGenerationPlaceholder(state: AiCaseGenerateTabState, itemId?: string) {
  if (itemId) {
    const byId = state.results.find(item => item.id === itemId)
    if (byId) {
      return byId
    }
    return undefined
  }
  return state.results.find(item => item.status === 'generating')
}

function applyAiGeneratedOutlineToResult(target: AiCaseGenerateResult, outline: ApiAiGeneratedCaseOutline) {
  target.name = normalizeAiGeneratedCaseName(outline.name || target.name, outline.type || target.type, outline.expected || target.expected)
  target.description = outline.description || target.description
  target.tags = Array.isArray(outline.tags) ? [...outline.tags] : target.tags
  target.group = outline.group || target.group
  target.type = outline.type || target.type
  target.expected = outline.expected || target.expected
  target.status = 'generating'
  target.errorMessage = ''
}

function applyAiGeneratedDraftToResult(target: AiCaseGenerateResult, draft: ApiAiGeneratedCaseDraft) {
  target.name = normalizeAiGeneratedCaseName(draft.name || target.name, draft.type || target.type, draft.expected || target.expected)
  target.description = draft.description || target.description
  target.tags = Array.isArray(draft.tags) ? [...draft.tags] : target.tags
  target.group = draft.group || target.group
  target.type = draft.type || target.type
  target.expected = draft.expected || target.expected
  target.requestConfig = cloneScenarioRequestConfig(draft.requestConfig || target.requestConfig)
  target.assertions = JSON.parse(JSON.stringify(draft.assertions || [])) as ApiAssertionConfig[]
  target.preProcessors = JSON.parse(JSON.stringify(draft.preProcessors || [])) as ApiProcessorConfig[]
  target.postProcessors = JSON.parse(JSON.stringify(draft.postProcessors || [])) as ApiProcessorConfig[]
  target.status = 'pending'
  target.errorMessage = ''
}

function normalizeAiGeneratedCaseName(name: string, type: string, expected?: string | null) {
  const cleanName = (name || type || 'AI \u751f\u6210\u7528\u4f8b')
    .replace(/^\u3010[^\u3011]+\u3011\s*/, '')
    .replace(/^\[[^\]]+]\s*/, '')
    .replace(/^(\u6b63\u5411|\u53cd\u5411|\u8d1f\u5411|\u8fb9\u754c|\u5b89\u5168\u6027|\u5b89\u5168)\s*[-\u2013\u2014:\uff1a]\s*/, '')
    .trim()
  const cleanType = (type || '').trim()
  if (!cleanType || cleanName.startsWith(`${cleanType} \u2013 `) || cleanName.startsWith(`${cleanType} - `)) {
    return cleanName
  }
  const cleanExpected = (expected || '').trim()
  return cleanExpected
    ? `${cleanType} \u2013 ${cleanName} \u2013 ${cleanExpected}`
    : `${cleanType} \u2013 ${cleanName}`
}

function markRemainingAiCaseGenerationRowsFailed(state: AiCaseGenerateTabState, message: string) {
  state.results
    .filter(item => item.status === 'generating')
    .forEach((item) => {
      item.status = 'failed'
      item.errorMessage = message
    })
}

function updateAiCaseGenerateResultStatus(id: string, status: AiCaseGenerateResultStatus) {
  const target = findAiCaseGenerateResult(id)
  if (target) {
    target.status = status
    selectedAiCaseGenerateResultIds.value = selectedAiCaseGenerateResultIds.value.filter(itemId => itemId !== id)
    refreshAiCaseGenerationResults()
  }
}

function refreshAiCaseGenerationResults() {
  const state = activeAiCaseGenerationState.value
  if (state) {
    state.results = [...state.results]
    aiCaseGenerateResults.value = state.results
  }
  else {
    aiCaseGenerateResults.value = [...aiCaseGenerateResults.value]
  }
}

function setAiGenerationStatusFilter(status: AiCaseGenerationStatusFilter) {
  aiGenerationStatusFilter.value = status
  selectedAiCaseGenerateResultIds.value = selectedAiCaseGenerateResultIds.value
    .filter(id => filteredAiCaseGenerationResults.value.some(item => item.id === id))
}

function toggleAiCaseGenerationSelection(id: string, checked: boolean) {
  const next = new Set(selectedAiCaseGenerateResultIds.value)
  if (checked) {
    next.add(id)
  }
  else {
    next.delete(id)
  }
  selectedAiCaseGenerateResultIds.value = Array.from(next)
}

function toggleAllAiCaseGenerationSelection(checked: boolean) {
  if (!checked) {
    const visibleIds = new Set(selectableAiCaseGenerationResults.value.map(item => item.id))
    selectedAiCaseGenerateResultIds.value = selectedAiCaseGenerateResultIds.value.filter(id => !visibleIds.has(id))
    return
  }
  const next = new Set(selectedAiCaseGenerateResultIds.value)
  selectableAiCaseGenerationResults.value.forEach(item => next.add(item.id))
  selectedAiCaseGenerateResultIds.value = Array.from(next)
}

function discardSelectedAiCaseGenerateResults() {
  if (!selectedAiCaseGenerationResults.value.length) {
    ElMessage.info('请先选择用例')
    return
  }
  selectedAiCaseGenerationResults.value.forEach((item) => {
    item.status = 'discarded'
  })
  selectedAiCaseGenerateResultIds.value = []
  refreshAiCaseGenerationResults()
}

async function acceptSelectedAiCaseGenerateResults() {
  if (!selectedAiCaseGenerationResults.value.length) {
    ElMessage.info('请先选择用例')
    return
  }
  for (const item of [...selectedAiCaseGenerationResults.value]) {
    await acceptAiCaseGenerateResult(item)
  }
}

function runSelectedAiCaseGenerateResults() {
  const target = selectedAiCaseGenerationResults.value[0]
  if (!target) {
    ElMessage.info('请先选择用例')
    return
  }
  runAiCaseGenerateResult(target)
}

function runAiCaseGenerateResult(item: AiCaseGenerateResult) {
  if (!ensureAiGeneratedCaseReady(item)) {
    return
  }
  openAiGeneratedCaseInCaseDrawer(item)
  void debugCaseDrawer()
}

function findAiCaseGenerateResult(id: string) {
  return activeAiCaseGenerationResults.value.find(item => item.id === id)
    ?? aiCaseGenerateResults.value.find(item => item.id === id)
}

function toggleAiGeneratedCaseDetail(item: AiCaseGenerateResult) {
  if (!ensureAiGeneratedCaseReady(item)) {
    return
  }
  openAiGeneratedCaseInCaseDrawer(item)
}

function ensureAiGeneratedCaseReady(item: AiCaseGenerateResult) {
  if (item.status === 'generating') {
    ElMessage.info('生成中，请稍后')
    return false
  }
  if (item.status === 'failed') {
    ElMessage.warning(item.errorMessage || '该用例生成失败')
    return false
  }
  return true
}

function buildCaseDraftFromAiGeneratedCase(item: AiCaseGenerateResult): ApiRequestEditorDetail {
  const state = activeAiCaseGenerationState.value
  return {
    id: 0,
    resourceType: 'case',
    definitionId: state?.definitionId || definitionForm.id || 0,
    definitionName: state?.definitionName || definitionForm.name || '',
    workspaceCode: definitionForm.workspaceCode || workspaceCode.value,
    workspaceName: definitionForm.workspaceName || '',
    name: normalizeAiGeneratedCaseName(item.name, item.type, item.expected),
    method: item.requestConfig.method || state?.method || 'GET',
    path: item.requestConfig.path || state?.path || '',
    directoryName: item.directoryName || definitionForm.directoryName || '',
    description: item.description || item.expected || '',
    tags: [...(item.tags || [])],
    lastRunResult: item.runResult || null,
    lastRunAt: null,
    updatedAt: null,
    createdAt: null,
    casePriority: 'P0',
    caseStatus: item.status === 'discarded' ? '已废弃' : '进行中',
    requestConfig: cloneScenarioRequestConfig(item.requestConfig),
    assertions: JSON.parse(JSON.stringify(item.assertions || [])) as ApiAssertionConfig[],
    extractors: [],
    preProcessors: JSON.parse(JSON.stringify(item.preProcessors || [])) as ApiProcessorConfig[],
    postProcessors: JSON.parse(JSON.stringify(item.postProcessors || [])) as ApiProcessorConfig[],
  }
}

function syncAiGeneratedCaseFromCaseDrawer(item: AiCaseGenerateResult) {
  item.name = normalizeAiGeneratedCaseName(caseDrawerForm.name, item.type, item.expected)
  item.directoryName = caseDrawerForm.directoryName || ''
  item.description = caseDrawerForm.description || ''
  item.tags = [...(caseDrawerForm.tags || [])]
  item.requestConfig = cloneScenarioRequestConfig(caseDrawerForm.requestConfig)
  item.assertions = JSON.parse(JSON.stringify(caseDrawerForm.assertions || [])) as ApiAssertionConfig[]
  item.preProcessors = JSON.parse(JSON.stringify(caseDrawerForm.preProcessors || [])) as ApiProcessorConfig[]
  item.postProcessors = JSON.parse(JSON.stringify(caseDrawerForm.postProcessors || [])) as ApiProcessorConfig[]
}

function applyAiGeneratedCaseRunResponse(item: AiCaseGenerateResult, response: Pick<ApiRunResponse, 'reportId' | 'failureSummary' | 'stepResults' | 'result'>) {
  const step = pickPreferredRunStep(response.stepResults || [])
  item.debugReportId = response.reportId
  item.debugFailureSummary = response.failureSummary || ''
  item.debugStepResults = response.stepResults || []
  item.responseTab = 'body'
  item.responseBody = step?.response?.body || ''
  item.responseHeaders = step?.response?.headers || {}
  item.responseStatus = step?.response ? `状态 ${step.response.statusCode}` : ''
  item.responseConsole = buildRunConsolePreview(
    step?.errorMessage || response.failureSummary || '',
    step?.processorResults ?? [],
    step?.assertionResults ?? [],
    step?.extractionResults ?? [],
  )
  item.runResult = response.result === 'SUCCESS' || step?.success
    ? '通过'
    : '失败'
}

function openAiGeneratedCaseInCaseDrawer(item: AiCaseGenerateResult) {
  activeAiGeneratedCaseId.value = item.id
  aiCaseDrawerResultId.value = item.id
  const existingTab = requestEditorTabs.value.find(tab => tab.aiGeneratedCaseId === item.id)
  const tab = existingTab ?? makeRequestEditorTab(buildCaseDraftFromAiGeneratedCase(item))
  tab.aiGeneratedCaseId = item.id
  tab.aiGeneration = activeAiCaseGenerationState.value ?? undefined
  tab.resourceType = 'case'
  tab.resourceId = null
  tab.definitionId = tab.draft.definitionId || activeAiCaseGenerationState.value?.definitionId || definitionForm.id || null
  tab.title = normalizeAiGeneratedCaseName(item.name || 'AI 生成用例', item.type, item.expected)
  tab.method = item.requestConfig.method || activeAiCaseGenerationState.value?.method || 'GET'
  tab.debugReportId = item.debugReportId ?? null
  tab.debugFailureSummary = item.debugFailureSummary || ''
  tab.debugStepResults = [...(item.debugStepResults || [])]
  tab.isDirty = true
  if (!existingTab) {
    requestEditorTabs.value.push(tab)
  }
  caseDrawerSourceEditorKey.value = activeRequestEditorKey.value
  caseDrawerCreateSource.value = 'draft'
  caseDrawerMode.value = 'create'
  caseDrawerViewTab.value = 'detail'
  caseDrawerEditorKey.value = tab.key
  caseDrawerRequestTab.value = tab.activeTab || resolveDefaultRequestTab(tab.draft)
  caseDrawerResponsePreviewTab.value = item.responseTab || 'body'
  caseDrawerHistoryPreviewTab.value = 'body'
  applyCaseDrawerDetailToForm(tab.draft)
  syncCaseDrawerDebugStateFromTab(tab)
  resetCaseDrawerRunHistoryState()
  caseDrawerVisible.value = true
}

function setAiGeneratedCaseTab(item: AiCaseGenerateResult, tab: Exclude<RequestContentTab, 'cases'>) {
  item.activeTab = tab
}

function setAiGeneratedCaseResponseTab(item: AiCaseGenerateResult, tab: ResponsePreviewTab) {
  item.responseTab = tab
}

function aiGeneratedCaseBodyText(item: AiCaseGenerateResult) {
  const body = item.requestConfig.body
  if (body.type === 'RAW_JSON') return body.jsonText || body.rawText || ''
  if (body.type === 'RAW_XML') return body.xmlText || body.rawText || ''
  if (body.type === 'RAW_TEXT') return body.plainText || body.rawText || ''
  return body.rawText || ''
}

function setAiGeneratedCaseBodyText(item: AiCaseGenerateResult, value: string) {
  setModeBodyText(item.requestConfig.body.type, value, { requestConfig: item.requestConfig })
}

async function acceptAiCaseGenerateResult(item: AiCaseGenerateResult) {
  if (!ensureAiGeneratedCaseReady(item)) {
    return
  }
  if (!definitionForm.id) {
    ElMessage.warning('请先保存接口')
    return
  }
  try {
    const payload = {
      workspaceCode: definitionForm.workspaceCode || workspaceCode.value,
      definitionId: definitionForm.id,
      name: normalizeAiGeneratedCaseName(item.name, item.type, item.expected),
      description: item.description || '',
      tags: item.tags || [],
      requestConfig: cloneScenarioRequestConfig(item.requestConfig),
      assertions: JSON.parse(JSON.stringify(item.assertions || [])) as ApiAssertionConfig[],
      preProcessors: JSON.parse(JSON.stringify(item.preProcessors || [])) as ApiProcessorConfig[],
      postProcessors: JSON.parse(JSON.stringify(item.postProcessors || [])) as ApiProcessorConfig[],
    }
    await platformApi.createApiDefinitionCase(workspaceCode.value, payload)
    item.status = 'accepted'
    selectedAiCaseGenerateResultIds.value = selectedAiCaseGenerateResultIds.value.filter(id => id !== item.id)
    refreshAiCaseGenerationResults()
    ElMessage.success('AI 生成用例已采纳并保存')
    await refreshData()
  }
  catch (error) {
    ElMessage.error((error as Error).message || '采纳失败')
  }
}

function stopAiCaseGeneration() {
  const state = activeAiCaseGenerationState.value
  state?.abortController?.abort()
  if (state) {
    state.generating = false
    markRemainingAiCaseGenerationRowsFailed(state, '已停止生成')
  }
}

function aiGeneratedCaseBodyLanguage(item: AiCaseGenerateResult): 'json' | 'xml' | 'text' {
  const type = item.requestConfig.body.type
  if (type === 'RAW_JSON') return 'json'
  if (type === 'RAW_XML') return 'xml'
  return 'text'
}

function aiGeneratedCaseResponsePreview(item: AiCaseGenerateResult) {
  if (item.responseTab === 'header') {
    return JSON.stringify(item.responseHeaders || {}, null, 2)
  }
  if (item.responseTab === 'console') {
    return item.responseConsole || ''
  }
  if (item.responseTab === 'actualRequest') {
    return JSON.stringify({
      method: item.requestConfig.method,
      path: item.requestConfig.path,
      queryParams: item.requestConfig.queryParams.filter(row => !isKeyValueRowEmpty(row)),
      headers: item.requestConfig.headers.filter(row => !isKeyValueRowEmpty(row)),
      body: item.requestConfig.body,
    }, null, 2)
  }
  if (item.responseTab === 'assertions') {
    return JSON.stringify(item.assertions.map(assertion => ({
      name: assertion.name || '断言',
      expectedValue: assertion.expectedValue || '',
      result: item.runResult || '未运行',
    })), null, 2)
  }
  return item.responseBody || ''
}

function aiGeneratedCaseResponseLanguage(item: AiCaseGenerateResult): 'json' | 'text' {
  return item.responseTab === 'console' ? 'text' : 'json'
}

function formatAiGeneratedBodyType(type: string) {
  switch (type) {
    case 'NONE':
      return 'none'
    case 'FORM_DATA':
      return 'form-data'
    case 'FORM_URLENCODED':
      return 'x-www-form-urlencoded'
    case 'RAW_JSON':
      return 'JSON'
    case 'RAW_XML':
      return 'XML'
    case 'RAW_TEXT':
      return 'Text'
    case 'BINARY':
      return 'Binary'
    default:
      return type || '-'
  }
}

async function runCaseItem(id: number) {
  await openCaseEditor(id, { mode: 'run' })
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
  caseDrawerMode.value = 'create'
  caseDrawerViewTab.value = 'detail'
  caseDrawerEditorKey.value = tab.key
  caseDrawerRequestTab.value = tab.activeTab || resolveDefaultRequestTab(tab.draft)
  caseDrawerResponsePreviewTab.value = 'body'
  caseDrawerHistoryPreviewTab.value = 'body'
  applyCaseDrawerDetailToForm(tab.draft)
  syncCaseDrawerDebugStateFromTab(tab)
  resetCaseDrawerRunHistoryState()
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

function applyScenarioDetailToForm(detail: ApiScenarioDetail) {
  Object.assign(scenarioForm, cloneScenarioDetail(detail))
  scenarioForm.moduleId = detail.moduleId ?? null
  scenarioForm.priority = detail.priority || 'P1'
  scenarioForm.status = detail.status || 'IN_PROGRESS'
  scenarioForm.scenarioVariables = detail.scenarioVariables || []
  scenarioForm.scenarioAssertions = detail.scenarioAssertions || []
  scenarioForm.steps = normalizeScenarioStepPayload(detail.steps || [])
}

function buildEmptyScenarioDetail() {
  const targetWorkspaceCode = selectedScenarioModuleItem.value?.workspaceCode
    || selectedScenarioWorkspaceCode.value
    || defaultEditableWorkspaceCode()
  return {
    id: 0,
    workspaceCode: targetWorkspaceCode,
    workspaceName: '',
    name: '',
    directoryName: '',
    moduleId: selectedScenarioModuleId.value,
    moduleName: selectedScenarioModuleFormName.value,
    priority: 'P1',
    status: 'IN_PROGRESS',
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
    scenarioVariables: [],
    scenarioAssertions: [],
    steps: [],
  } satisfies ApiScenarioDetail
}

function syncActiveScenarioEditorTab() {
  const current = activeScenarioEditorTab.value
  if (!current || current.key === 'scenario-list') {
    return
  }
  current.id = scenarioForm.id || null
  current.title = scenarioForm.name?.trim() || current.title || '新建场景'
  current.draft = cloneScenarioDetail({
    ...scenarioForm,
    steps: normalizeScenarioStepPayload(scenarioForm.steps),
  })
  current.lastRunStepResults = JSON.parse(JSON.stringify(scenarioLastRunStepResults.value || [])) as ApiRunStepResult[]
  current.isDirty = current.savedFingerprint !== fingerprintScenarioDetail(current.draft)
}

function resetScenarioForm() {
  applyScenarioDetailToForm(buildEmptyScenarioDetail())
  scenarioLastRunStepResults.value = []
  openScenarioEditorTab(null)
}

function nextScenarioDraftTabTitle() {
  const maxIndex = scenarioEditorTabs.value.reduce((max, item) => {
    if (item.id != null) {
      return max
    }
    if (item.title === '新建场景') {
      return Math.max(max, 1)
    }
    const matched = item.title.match(/^新建场景(\d+)$/)
    if (!matched) {
      return max
    }
    return Math.max(max, Number(matched[1]) || 0)
  }, 0)
  return `新建场景${Math.max(1, maxIndex + 1)}`
}

function openScenarioEditorTab(id: number | null) {
  const key = id ? `scenario-${id}` : `scenario-new-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  const title = id
    ? scenarios.value.find(item => item.id === id)?.name || scenarioForm.name || '场景详情'
    : nextScenarioDraftTabTitle()
  if (!scenarioEditorTabs.value.some(item => item.key === key)) {
    scenarioEditorTabs.value.push({
      key,
      id,
      title,
      draft: id ? null : cloneScenarioDetail(scenarioForm),
      lastRunStepResults: [],
      savedFingerprint: id ? '' : fingerprintScenarioDetail(scenarioForm),
      isDirty: false,
    })
  }
  activeScenarioEditorKey.value = key
  activeScenarioDetailTab.value = 'steps'
}

function activateScenarioEditorTab(key: string) {
  if (activeScenarioEditorKey.value === key) {
    return
  }
  void handleScenarioTabChange(key)
}

async function closeScenarioEditorTab(key: string) {
  if (key === 'scenario-list') {
    return
  }
  const closing = scenarioEditorTabs.value.find(item => item.key === key)
  if (!closing) {
    return
  }
  if (closing.isDirty) {
    await ElMessageBox.confirm('当前场景有未保存修改，确认关闭这个场景页签吗？', '关闭场景', { type: 'warning' })
  }
  const index = scenarioEditorTabs.value.findIndex(item => item.key === key)
  if (index < 0) {
    return
  }
  scenarioEditorTabs.value.splice(index, 1)
  if (activeScenarioEditorKey.value === key) {
    activeScenarioEditorKey.value = 'scenario-list'
    selectedScenarioId.value = null
    scenarioLastRunStepResults.value = []
  }
}

async function closeOtherScenarioEditorTabs() {
  const hasDirty = scenarioEditorTabs.value.some(item => item.key !== 'scenario-list' && item.key !== activeScenarioEditorKey.value && item.isDirty)
  if (hasDirty) {
    await ElMessageBox.confirm('其他场景页签存在未保存修改，确认关闭其他页签吗？', '关闭其他场景', { type: 'warning' })
  }
  scenarioEditorTabs.value = scenarioEditorTabs.value.filter(item => item.key === 'scenario-list' || item.key === activeScenarioEditorKey.value)
  if (activeScenarioEditorTab.value?.draft) {
    selectedScenarioId.value = activeScenarioEditorTab.value.id
  }
}

async function closeAllScenarioEditorTabs() {
  const hasDirty = scenarioEditorTabs.value.some(item => item.key !== 'scenario-list' && item.isDirty)
  if (hasDirty) {
    await ElMessageBox.confirm('场景页签存在未保存修改，确认关闭全部页签吗？', '关闭全部场景', { type: 'warning' })
  }
  scenarioEditorTabs.value = scenarioEditorTabs.value.filter(item => item.key === 'scenario-list')
  activeScenarioEditorKey.value = 'scenario-list'
  selectedScenarioId.value = null
  scenarioLastRunStepResults.value = []
}

async function handleScenarioEditorMoreAction(command: string | number | object) {
  if (String(command) === 'closeOthers') {
    await closeOtherScenarioEditorTabs()
    return
  }
  await closeAllScenarioEditorTabs()
}

async function handleScenarioTabChange(name: string | number) {
  const key = String(name)
  activeScenarioEditorKey.value = key
  const tab = scenarioEditorTabs.value.find(item => item.key === key)
  if (tab?.draft) {
    applyScenarioDetailToForm(tab.draft)
    scenarioLastRunStepResults.value = JSON.parse(JSON.stringify(tab.lastRunStepResults || [])) as ApiRunStepResult[]
    selectedScenarioId.value = tab.id
    return
  }
  if (tab?.id) {
    await selectScenario(tab.id)
    return
  }
  selectedScenarioId.value = null
}

function flattenScenarioSteps(steps: ApiScenarioStep[], basePath: number[] = [], level = 0): FlatScenarioStep[] {
  return steps.flatMap((step, index) => {
    const path = [...basePath, index]
    return [
      { step, path, level },
      ...flattenScenarioSteps(step.children || [], path, level + 1),
    ]
  })
}

function getScenarioStepByPath(path: number[]) {
  let current: ApiScenarioStep | null = null
  let children = scenarioForm.steps
  for (const index of path) {
    current = children[index] ?? null
    if (!current) {
      return null
    }
    children = current.children || []
  }
  return current
}

function getScenarioStepListByParentPath(parentPath: number[]) {
  if (!parentPath.length) {
    return scenarioForm.steps
  }
  const parent = getScenarioStepByPath(parentPath)
  if (!parent) {
    return scenarioForm.steps
  }
  if (!parent.children) {
    parent.children = []
  }
  return parent.children
}

function emptyScenarioStep(type: ApiScenarioStepType = 'API_CASE'): ApiScenarioStep {
  return {
    id: `scenario-step-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    stepName: '',
    stepType: type,
    resourceType: type === 'API' ? 'DEFINITION' : type === 'API_CASE' ? 'CASE' : null,
    resourceId: null,
    enabled: true,
    requestConfig: type === 'CUSTOM_REQUEST' ? emptyApiRequestConfig() : null,
    assertions: [],
    preProcessors: [],
    postProcessors: [],
    delayMs: type === 'CONSTANT_TIMER' ? 1000 : type === 'LOOP_CONTROLLER' ? 0 : null,
    conditionType: type === 'IF_CONTROLLER' || type === 'LOOP_CONTROLLER' ? 'EXPRESSION' : 'EXPRESSION',
    conditionExpression: '',
    loopType: type === 'LOOP_CONTROLLER' ? 'FIXED' : 'FIXED',
    loopCount: type === 'LOOP_CONTROLLER' ? 1 : null,
    foreachExpression: '',
    script: type === 'SCRIPT' ? '' : '',
    children: isScenarioControllerStep(type) ? [] : [],
  }
}

function isScenarioControllerStep(type?: string | null) {
  return type === 'IF_CONTROLLER' || type === 'LOOP_CONTROLLER' || type === 'ONCE_ONLY_CONTROLLER'
}

function addScenarioStep(parentPath: number[] = [], type: ApiScenarioStepType = 'API_CASE') {
  getScenarioStepListByParentPath(parentPath).push(emptyScenarioStep(type))
}

function cloneScenarioStep(step: ApiScenarioStep): ApiScenarioStep {
  return JSON.parse(JSON.stringify(step)) as ApiScenarioStep
}

async function openScenarioSystemRequestDrawer(step: ApiScenarioStep) {
  if (!step.resourceId || (step.stepType !== 'API' && step.stepType !== 'API_CASE')) {
    return
  }
  scenarioSystemRequestDrawerVisible.value = true
  scenarioSystemRequestDrawerLoading.value = true
  scenarioSystemRequestActiveTab.value = 'headers'
  scenarioSystemRequestDetail.value = null
  scenarioSystemRequestEditingStep.value = step
  syncScenarioSystemRequestTitleState(step)
  resetScenarioSystemRequestDebugState()
  try {
    const detail = step.stepType === 'API'
      ? toEditorDetailFromDefinition(await platformApi.getApiDefinitionDetail(workspaceCode.value, step.resourceId))
      : toEditorDetailFromCase(await platformApi.getApiDefinitionCaseDetail(workspaceCode.value, step.resourceId))
    hydrateDefinitionKeyValueRows(detail)
    scenarioSystemRequestDetail.value = detail
  }
  catch (error) {
    scenarioSystemRequestDrawerVisible.value = false
    ElMessage.error((error as Error).message)
  }
  finally {
    scenarioSystemRequestDrawerLoading.value = false
  }
}

function handleScenarioAddStepAction(command: string | number | object) {
  const action = String(command) as ScenarioAddStepAction
  if (action === 'IMPORT_SYSTEM_API') {
    openScenarioImportDrawer()
    return
  }
  if (action === 'CUSTOM_REQUEST') {
    openScenarioCustomRequestDrawer()
    return
  }
  if (action === 'SCRIPT') {
    openScenarioScriptDrawer()
    return
  }
  const typeMap: Partial<Record<ScenarioAddStepAction, ApiScenarioStepType>> = {
    LOOP_CONTROLLER: 'LOOP_CONTROLLER',
    IF_CONTROLLER: 'IF_CONTROLLER',
    ONCE_ONLY_CONTROLLER: 'ONCE_ONLY_CONTROLLER',
    CONSTANT_TIMER: 'CONSTANT_TIMER',
  }
  const type = typeMap[action]
  if (type) {
    addScenarioStep([], type)
  }
}

function openScenarioImportDrawer() {
  scenarioImportDrawerVisible.value = true
  scenarioImportActiveTab.value = 'api'
  scenarioImportWorkspaceCode.value = scenarioForm.workspaceCode || defaultEditableWorkspaceCode()
  resetScenarioImportSelection(false)
}

function resetScenarioCustomRequestForm() {
  const next = emptyScenarioStep('CUSTOM_REQUEST')
  next.stepName = '自定义请求'
  next.requestConfig = emptyApiRequestConfig()
  Object.assign(scenarioCustomRequestForm, next)
  syncKeyValueRows(getScenarioCustomRequestConfig().queryParams, queryParamDefaults())
  syncKeyValueRows(getScenarioCustomRequestConfig().headers, headerParamDefaults())
  syncKeyValueRows(getScenarioCustomRequestConfig().body.formItems, bodyFormParamDefaults())
  scenarioCustomRequestActiveTab.value = 'headers'
  scenarioCustomRequestTitleEditing.value = false
  scenarioCustomRequestHasCustomStepName.value = false
  scenarioCustomRequestActivePreProcessorId.value = null
  scenarioCustomRequestActivePostProcessorId.value = null
  scenarioCustomRequestActiveAssertionId.value = null
  scenarioCustomRequestDebugLoading.value = false
  scenarioCustomRequestDebugFailureSummary.value = ''
  scenarioCustomRequestDebugStepResults.value = []
  scenarioCustomRequestResponsePreviewTab.value = 'body'
}

function syncScenarioSystemRequestTitleState(step?: ApiScenarioStep | null) {
  const name = step?.stepName?.trim() || ''
  scenarioSystemRequestHasCustomStepName.value = !!name && !['', '引用 API', '引用用例'].includes(name)
  scenarioSystemRequestTitleDraft.value = name || ''
  scenarioSystemRequestTitleEditing.value = false
}

function openScenarioCustomRequestDrawer(path?: number[]) {
  scenarioCustomRequestDrawerMode.value = path ? 'edit' : 'create'
  scenarioCustomRequestEditingPath.value = path ? [...path] : []
  if (path) {
    const step = getScenarioStepByPath(path)
    if (step) {
      Object.assign(scenarioCustomRequestForm, cloneScenarioStep({
        ...emptyScenarioStep('CUSTOM_REQUEST'),
        ...step,
        requestConfig: step.requestConfig || emptyApiRequestConfig(),
      }))
      scenarioCustomRequestHasCustomStepName.value = !!step.stepName?.trim() && step.stepName.trim() !== '自定义请求'
    }
  }
  else {
    resetScenarioCustomRequestForm()
  }
  syncKeyValueRows(getScenarioCustomRequestConfig().queryParams, queryParamDefaults())
  syncKeyValueRows(getScenarioCustomRequestConfig().headers, headerParamDefaults())
  syncKeyValueRows(getScenarioCustomRequestConfig().body.formItems, bodyFormParamDefaults())
  scenarioCustomRequestTitleEditing.value = false
  scenarioCustomRequestDrawerVisible.value = true
}

function resetScenarioSystemRequestDebugState() {
  scenarioSystemRequestDebugLoading.value = false
  scenarioSystemRequestDebugFailureSummary.value = ''
  scenarioSystemRequestDebugStepResults.value = []
  scenarioSystemRequestResponsePreviewTab.value = 'body'
}

function startScenarioSystemRequestTitleEdit() {
  if (!scenarioSystemRequestHasCustomStepName.value) {
    scenarioSystemRequestTitleDraft.value = ''
  }
  scenarioSystemRequestTitleEditing.value = true
}

function finishScenarioSystemRequestTitleEdit() {
  const name = scenarioSystemRequestTitleDraft.value.trim()
  scenarioSystemRequestHasCustomStepName.value = !!name
  if (scenarioSystemRequestEditingStep.value) {
    scenarioSystemRequestEditingStep.value.stepName = name || scenarioSystemRequestEditingStep.value.stepName || ''
  }
  scenarioSystemRequestTitleEditing.value = false
}

function startScenarioCustomRequestTitleEdit() {
  if (!scenarioCustomRequestHasCustomStepName.value) {
    scenarioCustomRequestForm.stepName = ''
  }
  scenarioCustomRequestTitleEditing.value = true
}

function finishScenarioCustomRequestTitleEdit() {
  const name = scenarioCustomRequestForm.stepName?.trim() || ''
  scenarioCustomRequestHasCustomStepName.value = !!name
  if (!name) {
    scenarioCustomRequestForm.stepName = '自定义请求'
  }
  else {
    scenarioCustomRequestForm.stepName = name
  }
  scenarioCustomRequestTitleEditing.value = false
}

function closeScenarioCustomRequestDrawer() {
  scenarioCustomRequestDrawerVisible.value = false
}

function closeScenarioSystemRequestDrawer() {
  scenarioSystemRequestDrawerVisible.value = false
  scenarioSystemRequestEditingStep.value = null
  scenarioSystemRequestTitleEditing.value = false
  scenarioSystemRequestTitleDraft.value = ''
  scenarioSystemRequestHasCustomStepName.value = false
}

function saveScenarioCustomRequestStep(keepOpen = false) {
  const path = getScenarioCustomRequestConfig().path?.trim()
  if (!path) {
    ElMessage.warning('请输入请求 URL')
    return
  }
  const next = cloneScenarioStep(scenarioCustomRequestForm)
  next.stepType = 'CUSTOM_REQUEST'
  next.resourceType = null
  next.resourceId = null
  next.stepName = next.stepName?.trim() || path
  scenarioCustomRequestHasCustomStepName.value = next.stepName !== '自定义请求'
  next.requestConfig = cloneScenarioRequestConfig(getScenarioCustomRequestConfig())
  if (scenarioCustomRequestDrawerMode.value === 'edit') {
    const current = getScenarioStepByPath(scenarioCustomRequestEditingPath.value)
    if (current) {
      Object.assign(current, next)
    }
  }
  else {
    scenarioForm.steps.push(next)
  }
  ElMessage.success(scenarioCustomRequestDrawerMode.value === 'edit' ? '自定义请求已保存' : '自定义请求已添加')
  if (keepOpen && scenarioCustomRequestDrawerMode.value === 'create') {
    resetScenarioCustomRequestForm()
    return
  }
  scenarioCustomRequestDrawerVisible.value = false
}

function cloneScenarioRequestConfig(config: ApiRequestConfig): ApiRequestConfig {
  return JSON.parse(JSON.stringify(config)) as ApiRequestConfig
}

function resetScenarioScriptForm() {
  const next = emptyScenarioStep('SCRIPT')
  next.stepName = '脚本操作'
  next.script = ''
  Object.assign(scenarioScriptForm, next)
  scenarioScriptActiveTab.value = 'script'
  scenarioScriptInputMode.value = 'manual'
  scenarioScriptActiveAssertionId.value = null
  scenarioScriptResultTab.value = 'console'
}

function openScenarioScriptDrawer(path?: number[]) {
  scenarioScriptDrawerMode.value = path ? 'edit' : 'create'
  scenarioScriptEditingPath.value = path ? [...path] : []
  if (path) {
    const step = getScenarioStepByPath(path)
    if (step) {
      Object.assign(scenarioScriptForm, cloneScenarioStep({
        ...emptyScenarioStep('SCRIPT'),
        ...step,
      }))
    }
  }
  else {
    resetScenarioScriptForm()
  }
  scenarioScriptDrawerVisible.value = true
}

function closeScenarioScriptDrawer() {
  scenarioScriptDrawerVisible.value = false
}

async function formatScenarioScriptContent() {
  if (scenarioScriptEditorRef.value) {
    await scenarioScriptEditorRef.value.formatDocument()
    return
  }
  scenarioScriptForm.script = (scenarioScriptForm.script || '').trim()
}

function clearScenarioScriptContent() {
  scenarioScriptForm.script = ''
}

function saveScenarioScriptStep(keepOpen = false) {
  if (!scenarioScriptForm.stepName?.trim()) {
    ElMessage.warning('请输入脚本名称')
    return
  }
  const next = cloneScenarioStep(scenarioScriptForm)
  next.stepType = 'SCRIPT'
  next.resourceType = null
  next.resourceId = null
  next.stepName = next.stepName.trim()
  next.script = next.script || ''
  if (scenarioScriptDrawerMode.value === 'edit') {
    const current = getScenarioStepByPath(scenarioScriptEditingPath.value)
    if (current) {
      Object.assign(current, next)
    }
  }
  else {
    scenarioForm.steps.push(next)
  }
  ElMessage.success(scenarioScriptDrawerMode.value === 'edit' ? '脚本操作已保存' : '脚本操作已添加')
  if (keepOpen && scenarioScriptDrawerMode.value === 'create') {
    resetScenarioScriptForm()
    return
  }
  scenarioScriptDrawerVisible.value = false
}

function resetScenarioImportSelection(resetTabSelection = true) {
  scenarioImportTreeKeyword.value = ''
  scenarioImportKeyword.value = ''
  selectedScenarioImportTreeKey.value = 'scenario-import-all'
  if (resetTabSelection) {
    scenarioImportSelectedDefinitionIds.value = []
    scenarioImportSelectedCaseIds.value = []
    scenarioImportSelectedScenarioIds.value = []
  }
}

function handleScenarioImportTabChange() {
  scenarioImportKeyword.value = ''
  selectedScenarioImportTreeKey.value = 'scenario-import-all'
}

function handleScenarioImportWorkspaceChange() {
  selectedScenarioImportTreeKey.value = 'scenario-import-all'
}

function handleScenarioImportDefinitionSelection(rows: ApiDefinitionItem[]) {
  scenarioImportSelectedDefinitionIds.value = rows.map(item => item.id)
}

function handleScenarioImportCaseSelection(rows: ApiDefinitionCaseItem[]) {
  scenarioImportSelectedCaseIds.value = rows.map(item => item.id)
}

function handleScenarioImportScenarioSelection(rows: ApiScenarioItem[]) {
  scenarioImportSelectedScenarioIds.value = rows.map(item => item.id)
}

function appendScenarioSteps(steps: ApiScenarioStep[]) {
  scenarioForm.steps.push(...steps)
}

function createReferenceStepFromDefinition(item: ApiDefinitionItem): ApiScenarioStep {
  return {
    ...emptyScenarioStep('API'),
    stepName: item.name,
    resourceType: 'DEFINITION',
    resourceId: item.id,
  }
}

function createReferenceStepFromCase(item: ApiDefinitionCaseItem): ApiScenarioStep {
  return {
    ...emptyScenarioStep('API_CASE'),
    stepName: item.name,
    resourceType: 'CASE',
    resourceId: item.id,
  }
}

function createReferenceStepFromScenario(item: ApiScenarioItem): ApiScenarioStep {
  return {
    ...emptyScenarioStep('API_SCENARIO'),
    stepName: item.name,
    resourceType: null,
    resourceId: item.id,
  }
}

function createCustomStepFromDefinition(detail: ApiDefinitionDetail): ApiScenarioStep {
  return {
    ...emptyScenarioStep('CUSTOM_REQUEST'),
    stepName: detail.name,
    requestConfig: JSON.parse(JSON.stringify(detail.requestConfig)),
    assertions: JSON.parse(JSON.stringify(detail.assertions || [])),
    preProcessors: JSON.parse(JSON.stringify(detail.preProcessors || [])),
    postProcessors: JSON.parse(JSON.stringify(detail.postProcessors || [])),
  }
}

function createCustomStepFromCase(detail: ApiDefinitionCaseDetail): ApiScenarioStep {
  return {
    ...emptyScenarioStep('CUSTOM_REQUEST'),
    stepName: detail.name,
    requestConfig: JSON.parse(JSON.stringify(detail.requestConfig)),
    assertions: JSON.parse(JSON.stringify(detail.assertions || [])),
    preProcessors: JSON.parse(JSON.stringify(detail.preProcessors || [])),
    postProcessors: JSON.parse(JSON.stringify(detail.postProcessors || [])),
  }
}

function cloneScenarioStepsForImport(steps: ApiScenarioStep[]): ApiScenarioStep[] {
  return steps.map(step => ({
    ...JSON.parse(JSON.stringify(step)),
    id: `scenario-step-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    children: cloneScenarioStepsForImport(step.children || []),
  }))
}

async function handleScenarioImport(mode: 'copy' | 'quote') {
  if (!scenarioImportSelectedTotal.value) {
    return
  }
  scenarioImportLoading.value = true
  try {
    if (mode === 'quote') {
      appendScenarioSteps([
        ...scenarioImportSelectedDefinitionRows.value.map(createReferenceStepFromDefinition),
        ...scenarioImportSelectedCaseRows.value.map(createReferenceStepFromCase),
        ...scenarioImportSelectedScenarioRows.value.map(createReferenceStepFromScenario),
      ])
    }
    else {
      const copiedDefinitionSteps = await Promise.all(scenarioImportSelectedDefinitionRows.value.map(async (item) => {
        const detail = await platformApi.getApiDefinitionDetail(workspaceCode.value, item.id)
        return createCustomStepFromDefinition(detail)
      }))
      const copiedCaseSteps = await Promise.all(scenarioImportSelectedCaseRows.value.map(async (item) => {
        const detail = await platformApi.getApiDefinitionCaseDetail(workspaceCode.value, item.id)
        return createCustomStepFromCase(detail)
      }))
      const copiedScenarioStepGroups = await Promise.all(scenarioImportSelectedScenarioRows.value.map(async (item) => {
        const detail = await platformApi.getApiScenarioDetail(workspaceCode.value, item.id)
        return cloneScenarioStepsForImport(detail.steps || [])
      }))
      appendScenarioSteps([
        ...copiedDefinitionSteps,
        ...copiedCaseSteps,
        ...copiedScenarioStepGroups.flat(),
      ])
    }
    ElMessage.success(mode === 'quote' ? '已引用到场景步骤' : '已复制到场景步骤')
    scenarioImportDrawerVisible.value = false
    resetScenarioImportSelection()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    scenarioImportLoading.value = false
  }
}

function removeScenarioStep(path: number[]) {
  const list = getScenarioStepListByParentPath(path.slice(0, -1))
  list.splice(path[path.length - 1], 1)
}

function moveScenarioStep(path: number[], delta: number) {
  const list = getScenarioStepListByParentPath(path.slice(0, -1))
  const index = path[path.length - 1]
  const target = index + delta
  if (target < 0 || target >= list.length) {
    return
  }
  const current = list[index]
  list[index] = list[target]
  list[target] = current
}

function startScenarioStepNameEdit(step: ApiScenarioStep) {
  scenarioStepNameEditingId.value = step.id || ''
  scenarioStepNameDraft.value = step.stepName || scenarioStepDisplayName(step)
}

function finishScenarioStepNameEdit(step: ApiScenarioStep) {
  const name = scenarioStepNameDraft.value.trim()
  if (name) {
    step.stepName = name
  }
  scenarioStepNameEditingId.value = ''
  scenarioStepNameDraft.value = ''
}

function findScenarioStepById(steps: ApiScenarioStep[], id: string): ApiScenarioStep | null {
  for (const step of steps) {
    if (step.id === id) {
      return step
    }
    const child = findScenarioStepById(step.children || [], id)
    if (child) {
      return child
    }
  }
  return null
}

function handleScenarioStepNameOutsidePointerDown(event: MouseEvent) {
  if (!scenarioStepNameEditingId.value) {
    return
  }
  const target = event.target
  if (!(target instanceof Element)) {
    return
  }
  if (target.closest('.scenario-step-name-inline-input') || target.closest('.scenario-step-name-edit-button')) {
    return
  }
  const step = findScenarioStepById(scenarioForm.steps, scenarioStepNameEditingId.value)
  if (step) {
    finishScenarioStepNameEdit(step)
    return
  }
  scenarioStepNameEditingId.value = ''
  scenarioStepNameDraft.value = ''
}

function normalizeScenarioStepPayload(steps: ApiScenarioStep[]): ApiScenarioStep[] {
  return steps.map((step) => ({
    ...step,
    stepName: step.stepName || scenarioStepTypeLabel(step.stepType),
    enabled: step.enabled !== false,
    children: normalizeScenarioStepPayload(step.children || []),
  }))
}

function scenarioStepNeedsResource(step: ApiScenarioStep) {
  return step.stepType === 'API' || step.stepType === 'API_CASE' || step.stepType === 'API_SCENARIO' || !step.stepType
}

function hasInvalidScenarioStep(steps: ApiScenarioStep[]): boolean {
  return steps.some((step) => {
    if (scenarioStepNeedsResource(step) && !step.resourceId) {
      return true
    }
    if (step.stepType === 'CUSTOM_REQUEST' && !step.requestConfig?.path?.trim()) {
      return true
    }
    if (step.stepType === 'SCRIPT' && !step.script?.trim()) {
      return true
    }
    return hasInvalidScenarioStep(step.children || [])
  })
}

function addScenarioVariable() {
  scenarioForm.scenarioVariables.push(emptyVariable())
}

function addScenarioAssertion() {
  scenarioForm.scenarioAssertions.push({
    id: `scenario-assertion-${Date.now()}`,
    name: '全部步骤通过',
    assertionType: 'ALL_STEPS_PASSED',
    operator: 'EQUALS',
    expectedValue: 'true',
    enabled: true,
  })
}

async function handleScenarioModuleSelect(data: ScenarioModuleTreeNode) {
  if (data.type === 'root') {
    selectedScenarioWorkspaceCode.value = null
    selectedScenarioModuleId.value = null
  }
  else if (data.type === 'workspace') {
    selectedScenarioWorkspaceCode.value = data.workspaceCode
    selectedScenarioModuleId.value = null
  }
  else {
    selectedScenarioWorkspaceCode.value = data.workspaceCode
    selectedScenarioModuleId.value = data.id
  }
  activeScenarioEditorKey.value = 'scenario-list'
}

function isScenarioModuleTreeExpanded(nodeKey: string) {
  return expandedScenarioModuleTreeKeys.value.includes(nodeKey)
}

function collectScenarioModuleExpandableKeys(nodes: ScenarioModuleTreeNode[]) {
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

function syncScenarioModuleExpandedKeys(keys?: string[]) {
  expandedScenarioModuleTreeKeys.value = keys ?? collectScenarioModuleExpandableKeys(scenarioModuleTree.value)
  scenarioModuleTreeRenderKey.value += 1
}

function collapseAllScenarioModuleTreeChildren() {
  syncScenarioModuleExpandedKeys(scenarioModuleTree.value.map(node => node.key))
}

function handleScenarioModuleTreeExpand(_: ScenarioModuleTreeNode, treeNode: { key: string }) {
  const keys = new Set(expandedScenarioModuleTreeKeys.value)
  keys.add(String(treeNode.key))
  expandedScenarioModuleTreeKeys.value = [...keys]
}

function handleScenarioModuleTreeCollapse(_: ScenarioModuleTreeNode, treeNode: { key: string }) {
  const collapsedKey = String(treeNode.key)
  const blockedKeys = new Set([collapsedKey])
  const stack = [...scenarioModuleTree.value]
  while (stack.length) {
    const current = stack.pop()
    if (!current) {
      continue
    }
    if (blockedKeys.has(current.key)) {
      for (const child of current.children) {
        blockedKeys.add(child.key)
        stack.push(child)
      }
      continue
    }
    stack.push(...current.children)
  }
  expandedScenarioModuleTreeKeys.value = expandedScenarioModuleTreeKeys.value.filter(key => !blockedKeys.has(key))
}

async function createScenarioModule(parentId: number | null = null, targetWorkspaceCode?: string | null) {
  const moduleWorkspaceCode = targetWorkspaceCode
    || selectedScenarioModuleItem.value?.workspaceCode
    || selectedScenarioWorkspaceCode.value
    || defaultEditableWorkspaceCode()
  if (!moduleWorkspaceCode || !canWriteWorkspace(moduleWorkspaceCode)) {
    ElMessage.warning('请选择可写空间后再新建模块')
    return
  }
  const { value } = await ElMessageBox.prompt('请输入模块名称', '新建模块', {
    inputPattern: /\S+/,
    inputErrorMessage: '模块名称不能为空',
  })
  await platformApi.createApiScenarioModule(workspaceCode.value, {
    workspaceCode: moduleWorkspaceCode,
    parentId,
    name: value,
  })
  ElMessage.success('模块已创建')
  await refreshData()
}

async function renameScenarioModule(module: ApiScenarioModuleItem | ScenarioModuleTreeNode) {
  if (!module.id) {
    return
  }
  const { value } = await ElMessageBox.prompt('请输入模块名称', '重命名模块', {
    inputValue: module.name,
    inputPattern: /\S+/,
    inputErrorMessage: '模块名称不能为空',
  })
  await platformApi.updateApiScenarioModule(workspaceCode.value, module.id, { name: value })
  ElMessage.success('模块已更新')
  await refreshData()
}

async function deleteScenarioModule(module: ApiScenarioModuleItem | ScenarioModuleTreeNode) {
  if (!module.id) {
    return
  }
  await ElMessageBox.confirm('只能删除空模块，确认删除吗？', '删除模块', { type: 'warning' })
  await platformApi.deleteApiScenarioModule(workspaceCode.value, module.id)
  if (selectedScenarioModuleId.value === module.id) {
    selectedScenarioModuleId.value = null
  }
  ElMessage.success('模块已删除')
  await refreshData()
}

async function copyScenario(row: ApiScenarioItem) {
  const detail = await platformApi.getApiScenarioDetail(workspaceCode.value, row.id)
  Object.assign(scenarioForm, JSON.parse(JSON.stringify(detail)))
  scenarioForm.id = 0
  scenarioForm.name = `${detail.name} 副本`
  scenarioForm.scenarioVariables = detail.scenarioVariables || []
  scenarioForm.scenarioAssertions = detail.scenarioAssertions || []
  scenarioForm.steps = normalizeScenarioStepPayload(detail.steps || [])
  scenarioLastRunStepResults.value = []
  openScenarioEditorTab(null)
}

async function removeScenarioFromList(id: number) {
  await selectScenario(id)
  await removeScenario()
  activeScenarioEditorKey.value = 'scenario-list'
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
      definitionModuleList,
      casePage,
      scenarioPage,
      scenarioModuleList,
      envPage,
      variablePage,
      dbConnectionPage,
      taskPage,
      reportPage,
      userList,
      workspaceList,
    ] = await Promise.all([
      platformApi.getApiDefinitions(workspaceCode.value),
      platformApi.getApiDefinitionModules(workspaceCode.value),
      platformApi.getApiDefinitionCases(workspaceCode.value),
      platformApi.getApiScenarios(workspaceCode.value),
      platformApi.getApiScenarioModules(workspaceCode.value),
      platformApi.getApiEnvironments(workspaceCode.value),
      platformApi.getApiVariableSets(workspaceCode.value),
      platformApi.getSettingsDbConnections(workspaceCode.value),
      platformApi.getTasks(workspaceCode.value),
      platformApi.getReports(workspaceCode.value),
      platformApi.getUsers(),
      platformApi.getSwitchableWorkspaces(),
    ])
    definitions.value = definitionPage.items
    definitionModules.value = definitionModuleList
    apiCases.value = casePage.items
    scenarios.value = scenarioPage.items
    scenarioModules.value = scenarioModuleList
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
  const loadedTab = scenarioEditorTabs.value.find(item => item.id === id)
  if (loadedTab) {
    await handleScenarioTabChange(loadedTab.key)
    return
  }
  selectedScenarioId.value = id
  const detail = await platformApi.getApiScenarioDetail(workspaceCode.value, id)
  assignScenario(detail)
  openScenarioEditorTab(id)
}

function assignScenario(detail: ApiScenarioDetail) {
  applyScenarioDetailToForm(detail)
  scenarioLastRunStepResults.value = []
  const tab = scenarioEditorTabs.value.find(item => item.id === detail.id)
  if (tab) {
    tab.title = detail.name || '场景详情'
    tab.draft = cloneScenarioDetail({
      ...detail,
      steps: normalizeScenarioStepPayload(detail.steps || []),
    })
    tab.lastRunStepResults = []
    tab.savedFingerprint = fingerprintScenarioDetail(tab.draft)
    tab.isDirty = false
  }
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
  batchAddParamStyle.value = mode === 'query' || mode === 'body-form' ? 'standard' : 'quick'
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

function normalizeParamType(value: string, mode: BatchAddMode) {
  const normalized = value.trim().toLowerCase()
  const options = mode === 'body-form'
    ? new Set<string>(bodyParamTypeOptions)
    : new Set<string>(queryParamTypeOptions)
  return options.has(normalized) ? normalized : 'string'
}

function parseBatchStandardParamInput(mode: Extract<BatchAddMode, 'query' | 'body-form'>) {
  const rows = batchAddInput.value
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
    .map<ApiKeyValue | null>((line) => {
      const columns = line.split(',').map(item => item.trim())
      const key = columns[0] || ''
      if (!key) {
        return null
      }
      const paramType = normalizeParamType(columns[1] || 'string', mode)
      const required = normalizeBooleanLike(columns[2] || '') ?? false
      return {
        key,
        paramType,
        required,
        value: columns.slice(3).join(',').trim(),
        enabled: true,
      }
    })
    .filter(isNonNull)
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
    const rows = batchAddParamStyle.value === 'standard' ? parseBatchStandardParamInput('query') : parseBatchKeyValueInput()
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
    const rows = batchAddParamStyle.value === 'standard' ? parseBatchStandardParamInput('body-form') : parseBatchKeyValueInput()
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
    const created = await platformApi.createApiDefinitionModule(workspaceCode.value, {
      workspaceCode: targetWorkspaceCode,
      parentId: node.type === 'module' ? node.moduleId : null,
      name: moduleName,
    })
    await refreshData()
    const fullPath = created.fullPath || moduleName
    const nodeKey = `definition-module:${created.workspaceCode}:${fullPath}`
    selectedDefinitionTreeKey.value = nodeKey
    expandDefinitionTreeToKey(nodeKey)
    ElMessage.success(`已创建子模块：${fullPath}`)
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

function replaceDefinitionModulePath(currentPath: string, sourcePath: string, targetPath: string) {
  if (currentPath === sourcePath) {
    return targetPath
  }
  const suffix = currentPath.slice(sourcePath.length + 1)
  return targetPath ? `${targetPath}/${suffix}` : ''
}

function syncOpenDraftDefinitionModulePath(sourcePath: string, targetPath: string) {
  for (const tab of requestEditorTabs.value) {
    if (tab.resourceType !== 'definition' || tab.resourceId) {
      continue
    }
    const currentPath = (tab.draft.directoryName || '').trim()
    if (!(currentPath === sourcePath || currentPath.startsWith(`${sourcePath}/`))) {
      continue
    }
    tab.draft.directoryName = replaceDefinitionModulePath(currentPath, sourcePath, targetPath)
    if (tab.key === activeRequestEditorKey.value) {
      definitionForm.directoryName = tab.draft.directoryName
    }
  }
}

async function renameDefinitionModule(node: DefinitionDirectoryTreeNode) {
  if (node.type !== 'module' || !node.moduleId || !node.fullPath || !canWriteWorkspace(node.workspaceCode)) {
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('请输入目录名称', '重命名', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputValue: node.label,
      inputPattern: /\S+/,
      inputErrorMessage: '目录名称不能为空',
    })
    const nextName = value.trim()
    if (nextName.includes('/')) {
      ElMessage.error('目录名称不能包含 /')
      return
    }
    if (nextName === node.label) {
      return
    }
    const sourcePath = node.fullPath
    const updated = await platformApi.updateApiDefinitionModule(workspaceCode.value, node.moduleId, { name: nextName })
    await refreshData()
    const nextPath = updated.fullPath || sourcePath
    const nextKey = `definition-module:${updated.workspaceCode}:${nextPath}`
    syncOpenDraftDefinitionModulePath(sourcePath, nextPath)
    selectedDefinitionTreeKey.value = nextKey
    expandDefinitionTreeToKey(nextKey)
    ElMessage.success('目录已重命名')
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

async function deleteDefinitionModule(node: DefinitionDirectoryTreeNode) {
  if (node.type !== 'module' || !node.moduleId || !node.fullPath || !canWriteWorkspace(node.workspaceCode)) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除目录“${node.label}”吗？目录下的请求会移动到根目录，请求本身不会删除。`,
      '删除目录',
      { type: 'warning' },
    )
    await platformApi.deleteApiDefinitionModule(workspaceCode.value, node.moduleId)
    await refreshData()
    syncOpenDraftDefinitionModulePath(node.fullPath, '')
    selectedDefinitionTreeKey.value = `definition-workspace:${node.workspaceCode}`
    ElMessage.success('目录已删除')
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

function handleDefinitionModuleAction(command: string, node: DefinitionDirectoryTreeNode) {
  if (command === 'rename') {
    void renameDefinitionModule(node)
    return
  }
  void deleteDefinitionModule(node)
}

async function renameDefinitionRequest(node: DefinitionDirectoryTreeNode) {
  if (node.type !== 'request' || !node.definitionId || !canWriteWorkspace(node.workspaceCode)) {
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('请输入请求名称', '重命名', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputValue: node.label,
      inputPattern: /\S+/,
      inputErrorMessage: '请求名称不能为空',
    })
    const nextName = value.trim()
    if (nextName === node.label) {
      return
    }
    const detail = await platformApi.getApiDefinitionDetail(workspaceCode.value, node.definitionId)
    const editorDetail = toEditorDetailFromDefinition(detail)
    editorDetail.name = nextName
    const updated = await platformApi.updateApiDefinition(
      workspaceCode.value,
      detail.id,
      buildDefinitionMutationPayloadFor(editorDetail),
    )
    await refreshData()
    const updatedEditorDetail = toEditorDetailFromDefinition(updated)
    const openedTab = requestEditorTabs.value.find(item => item.resourceType === 'definition' && item.resourceId === updated.id)
    if (openedTab) {
      openedTab.title = updated.name
      openedTab.draft.name = updated.name
      if (!openedTab.isDirty) {
        openedTab.draft = cloneEditorDetail(updatedEditorDetail)
        openedTab.savedFingerprint = fingerprintDefinitionDetail(updatedEditorDetail)
      }
    }
    if (openedTab?.key === activeRequestEditorKey.value) {
      definitionForm.name = updated.name
      if (!openedTab.isDirty) {
        applyEditorDetailToForm(updatedEditorDetail, { markSaved: true })
      }
    }
    selectedDefinitionTreeKey.value = `definition-request:${updated.id}`
    ElMessage.success('请求已重命名')
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

async function deleteDefinitionRequest(node: DefinitionDirectoryTreeNode) {
  if (node.type !== 'request' || !node.definitionId || !canWriteWorkspace(node.workspaceCode)) {
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除接口“${node.label}”吗？删除后不可恢复。`, '删除接口', { type: 'warning' })
    await platformApi.deleteApiDefinition(workspaceCode.value, node.definitionId)
    const closingTab = requestEditorTabs.value.find(item => item.resourceType === 'definition' && item.resourceId === node.definitionId)
    selectedDefinitionId.value = null
    await refreshData()
    if (closingTab) {
      closingTab.isDirty = false
      await closeRequestEditorTab(closingTab.key)
    }
    if (selectedDefinitionTreeKey.value === node.key) {
      selectedDefinitionTreeKey.value = node.fullPath
        ? `definition-module:${node.workspaceCode}:${node.fullPath}`
        : `definition-workspace:${node.workspaceCode}`
    }
    ElMessage.success('接口已删除')
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

function handleDefinitionRequestAction(command: string, node: DefinitionDirectoryTreeNode) {
  if (command === 'rename') {
    void renameDefinitionRequest(node)
    return
  }
  void deleteDefinitionRequest(node)
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

function resolveDefinitionDirectoryNameFor(form: ApiRequestEditorDetail, targetWorkspaceCode?: string) {
  const workspace = (targetWorkspaceCode || form.workspaceCode || selectedDefinitionWorkspaceCode.value || '').trim()
  const selectedPath = selectedDefinitionWorkspaceCode.value === workspace
    ? (selectedDefinitionModulePath.value || '').trim()
    : ''
  const currentPath = (form.directoryName || '').trim()
  if (!form.id && selectedPath) {
    return selectedPath
  }
  return currentPath || selectedPath
}

function resolveDefinitionDirectoryName(targetWorkspaceCode?: string) {
  return resolveDefinitionDirectoryNameFor(definitionForm, targetWorkspaceCode)
}

function buildDefinitionMutationPayloadFor(form: ApiRequestEditorDetail) {
  const targetWorkspaceCode = isAllScope.value ? form.workspaceCode || writableWorkspaces.value[0]?.code : workspaceCode.value
  return {
    workspaceCode: targetWorkspaceCode,
    name: form.name.trim(),
    directoryName: (form.directoryName || '').trim() || null,
    description: form.description,
    tags: form.tags,
    requestConfig: {
      ...form.requestConfig,
      path: form.requestConfig.path.trim(),
      queryParams: prepareKeyValueRowsForPayload(form.requestConfig.queryParams),
      headers: prepareKeyValueRowsForPayload(form.requestConfig.headers),
      body: {
        ...form.requestConfig.body,
        formItems: prepareKeyValueRowsForPayload(form.requestConfig.body.formItems),
      },
    },
    assertions: form.assertions,
    extractors: form.extractors,
    preProcessors: form.preProcessors,
    postProcessors: form.postProcessors,
  }
}

function buildDefinitionMutationPayload() {
  return buildDefinitionMutationPayloadFor(definitionForm)
}

function buildCaseMutationPayloadFor(form: ApiRequestEditorDetail, definitionId: number) {
  const targetWorkspaceCode = isAllScope.value ? form.workspaceCode || writableWorkspaces.value[0]?.code : workspaceCode.value
  return {
    workspaceCode: targetWorkspaceCode,
    definitionId,
    name: form.name.trim(),
    description: form.description,
    tags: form.tags,
    requestConfig: {
      ...form.requestConfig,
      path: form.requestConfig.path.trim(),
      queryParams: prepareKeyValueRowsForPayload(form.requestConfig.queryParams),
      headers: prepareKeyValueRowsForPayload(form.requestConfig.headers),
      body: {
        ...form.requestConfig.body,
        formItems: prepareKeyValueRowsForPayload(form.requestConfig.body.formItems),
      },
    },
    assertions: form.assertions,
    preProcessors: form.preProcessors,
    postProcessors: form.postProcessors,
  }
}

function buildCaseMutationPayload(definitionId: number) {
  return buildCaseMutationPayloadFor(definitionForm, definitionId)
}

function buildScenarioCustomRequestDebugPayload() {
  const config = getScenarioCustomRequestConfig()
  return {
    workspaceCode: scenarioForm.workspaceCode || defaultEditableWorkspaceCode(),
    definitionId: null,
    name: scenarioCustomRequestForm.stepName?.trim() || '自定义请求',
    requestConfig: {
      ...cloneScenarioRequestConfig(config),
      path: config.path.trim(),
      queryParams: prepareKeyValueRowsForPayload(config.queryParams),
      headers: prepareKeyValueRowsForPayload(config.headers),
      body: {
        ...config.body,
        formItems: prepareKeyValueRowsForPayload(config.body.formItems),
      },
    },
    assertions: scenarioCustomRequestForm.assertions || [],
    extractors: [],
    preProcessors: scenarioCustomRequestForm.preProcessors || [],
    postProcessors: scenarioCustomRequestForm.postProcessors || [],
    environmentId: runOptions.environmentId,
    variableSetId: runOptions.variableSetId,
  } satisfies ApiDebugDefinitionPayload
}

async function ensureCaseDefinitionSavedFor(form: ApiRequestEditorDetail) {
  if (form.definitionId) {
    return form.definitionId
  }
  throw new Error('请先保存接口，再创建用例')
}

async function ensureCaseDefinitionSaved() {
  return ensureCaseDefinitionSavedFor(definitionForm)
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

async function saveCaseDrawer() {
  try {
    ensureScopedTargetWorkspace(caseDrawerForm.workspaceCode)
  }
  catch (error) {
    ElMessage.warning((error as Error).message)
    return
  }
  if (!caseDrawerForm.name.trim() || !caseDrawerForm.requestConfig.path.trim()) {
    ElMessage.warning('请补全用例名称和请求 URL')
    return
  }
  const isUpdate = !!caseDrawerForm.id
  saving.value = true
  try {
    caseDrawerForm.name = caseDrawerForm.name.trim()
    caseDrawerForm.requestConfig.path = caseDrawerForm.requestConfig.path.trim()
    caseDrawerForm.path = caseDrawerForm.requestConfig.path
    syncActiveBodyText(caseDrawerForm)
    caseDrawerForm.directoryName = resolveDefinitionDirectoryNameFor(
      caseDrawerForm,
      isAllScope.value ? caseDrawerForm.workspaceCode || writableWorkspaces.value[0]?.code : workspaceCode.value,
    )
    const definitionId = await ensureCaseDefinitionSavedFor(caseDrawerForm)
    const payload = buildCaseMutationPayloadFor(caseDrawerForm, definitionId)
    const detail = isUpdate
      ? await platformApi.updateApiDefinitionCase(workspaceCode.value, caseDrawerForm.id, payload)
      : await platformApi.createApiDefinitionCase(workspaceCode.value, payload)
    ElMessage.success(isUpdate ? '用例已更新' : '用例已保存')
    await refreshData()
    const latestDetail = toEditorDetailFromCase(await platformApi.getApiDefinitionCaseDetail(workspaceCode.value, detail.id))
    applyCaseDrawerDetailToForm(latestDetail, { markSaved: true })
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function debugCaseDrawer() {
  if (!canDebugCaseDrawer.value) {
    ElMessage.warning('请补全请求方法和请求 URL')
    return
  }
  saving.value = true
  try {
    syncActiveBodyText(caseDrawerForm)
    const response = caseDrawerForm.definitionId
      ? await platformApi.debugApiDefinitionCaseDraft(workspaceCode.value, {
          ...buildCaseMutationPayloadFor(caseDrawerForm, caseDrawerForm.definitionId),
          caseId: caseDrawerForm.id || null,
          environmentId: runOptions.environmentId,
          variableSetId: runOptions.variableSetId,
        } satisfies ApiDebugCasePayload)
      : await platformApi.debugApiDefinitionDraft(workspaceCode.value, {
          ...buildDefinitionMutationPayloadFor(caseDrawerForm),
          definitionId: null,
          environmentId: runOptions.environmentId,
          variableSetId: runOptions.variableSetId,
        } satisfies ApiDebugDefinitionPayload)
    applyDebugResponseToEditorTab(activeCaseDrawerEditorTab.value, response)
    updateCaseDrawerDebugState(
      response.reportId,
      response.failureSummary || '',
      response.stepResults || [],
    )
    if (aiCaseDrawerResultId.value) {
      const item = findAiCaseGenerateResult(aiCaseDrawerResultId.value)
      if (item) {
        syncAiGeneratedCaseFromCaseDrawer(item)
        applyAiGeneratedCaseRunResponse(item, response)
      }
    }
    caseDrawerResponsePreviewTab.value = 'body'
    if (caseDrawerForm.id) {
      await loadCaseDrawerRunHistory(caseDrawerForm.id)
    }
    ElMessage.success(response.result === 'SUCCESS' ? '发送成功' : '发送完成')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function debugScenarioCustomRequest() {
  if (!canDebugScenarioCustomRequest.value) {
    ElMessage.warning('请补全请求方法和请求 URL')
    return
  }
  scenarioCustomRequestDebugLoading.value = true
  try {
    syncActiveBodyText(scenarioCustomRequestHost.value)
    const response = await platformApi.debugApiDefinitionDraft(
      workspaceCode.value,
      buildScenarioCustomRequestDebugPayload(),
    )
    scenarioCustomRequestDebugFailureSummary.value = response.failureSummary || ''
    scenarioCustomRequestDebugStepResults.value = response.stepResults || []
    scenarioCustomRequestResponsePreviewTab.value = 'body'
    ElMessage.success(response.result === 'SUCCESS' ? '发送成功' : '发送完成')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    scenarioCustomRequestDebugLoading.value = false
  }
}

async function debugScenarioSystemRequest() {
  const detail = scenarioSystemRequestDetail.value
  if (!detail || !canDebugScenarioSystemRequest.value) {
    ElMessage.warning('请补全请求方法和请求 URL')
    return
  }
  scenarioSystemRequestDebugLoading.value = true
  try {
    syncActiveBodyText(detail)
    const response = detail.resourceType === 'case' && detail.definitionId
      ? await platformApi.debugApiDefinitionCaseDraft(workspaceCode.value, {
          ...buildCaseMutationPayloadFor(detail, detail.definitionId),
          caseId: detail.id || null,
          environmentId: runOptions.environmentId,
          variableSetId: runOptions.variableSetId,
        } satisfies ApiDebugCasePayload)
      : await platformApi.debugApiDefinitionDraft(workspaceCode.value, {
          ...buildDefinitionMutationPayloadFor(detail),
          definitionId: detail.resourceType === 'definition' ? detail.id || null : null,
          environmentId: runOptions.environmentId,
          variableSetId: runOptions.variableSetId,
        } satisfies ApiDebugDefinitionPayload)
    scenarioSystemRequestDebugFailureSummary.value = response.failureSummary || ''
    scenarioSystemRequestDebugStepResults.value = response.stepResults || []
    scenarioSystemRequestResponsePreviewTab.value = 'body'
    ElMessage.success(response.result === 'SUCCESS' ? '发送成功' : '发送完成')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    scenarioSystemRequestDebugLoading.value = false
  }
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
  if (!scenarioForm.moduleId) {
    ElMessage.warning('请选择场景模块')
    return
  }
  if (hasInvalidScenarioStep(scenarioForm.steps)) {
    ElMessage.warning('请补全步骤引用、请求 URL 或脚本内容')
    return
  }
  saving.value = true
  try {
    const wasNewScenario = !scenarioForm.id
    const previousScenarioKey = activeScenarioEditorKey.value
    const payload = {
      workspaceCode: isAllScope.value ? scenarioForm.workspaceCode || writableWorkspaces.value[0]?.code : workspaceCode.value,
      name: scenarioForm.name,
      directoryName: scenarioForm.directoryName,
      moduleId: scenarioForm.moduleId,
      priority: scenarioForm.priority,
      status: scenarioForm.status,
      description: scenarioForm.description,
      tags: scenarioForm.tags,
      defaultEnvironmentId: scenarioForm.defaultEnvironmentId,
      variableSetId: scenarioForm.variableSetId,
      continueOnFailure: scenarioForm.continueOnFailure,
      relatedCaseId: scenarioForm.relatedCaseId,
      scenarioVariables: scenarioForm.scenarioVariables,
      scenarioAssertions: scenarioForm.scenarioAssertions,
      steps: normalizeScenarioStepPayload(scenarioForm.steps),
    }
    const detail = scenarioForm.id
      ? await platformApi.updateApiScenario(workspaceCode.value, scenarioForm.id, payload)
      : await platformApi.createApiScenario(workspaceCode.value, payload)
    ElMessage.success(scenarioForm.id ? '场景已更新' : '场景已创建')
    const currentTab = activeScenarioEditorTab.value
    if (currentTab?.draft) {
      currentTab.savedFingerprint = fingerprintScenarioDetail(currentTab.draft)
      currentTab.isDirty = false
    }
    await refreshData()
    if (wasNewScenario) {
      scenarioEditorTabs.value = scenarioEditorTabs.value.filter(item => item.key !== previousScenarioKey)
      if (activeScenarioEditorKey.value === previousScenarioKey) {
        activeScenarioEditorKey.value = 'scenario-list'
      }
    }
    await selectScenario(detail.id)
    openScenarioEditorTab(detail.id)
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
    scenarioLastRunStepResults.value = response.stepResults || []
    ElMessage.success(response.result === 'SUCCESS' ? '场景执行成功' : '场景执行失败')
    await refreshData()
    await loadReportPreview(response.reportId)
    activeScenarioDetailTab.value = 'history'
    activeTab.value = 'scenarios'
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function runScenarioFromList(id: number) {
  await selectScenario(id)
  await runScenario()
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

const selectedDefinitionImportFormat = computed(() =>
  definitionImportFormats.find(item => item.value === definitionImportFormat.value) ?? definitionImportFormats[0],
)

function openDefinitionImportDialog() {
  definitionImportDialogVisible.value = true
}

function handleDefinitionImportFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  definitionImportFileName.value = input.files?.[0]?.name ?? ''
}

function submitDefinitionImportPreview() {
  ElMessage.info('导入能力待接入')
  definitionImportDialogVisible.value = false
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
            <div class="ms-like-sidebar-actions">
              <button
                v-if="canCreateInCurrentScope"
                type="button"
                class="ms-like-sidebar-primary"
                @click="openNewRequestTab()"
              >
                <LucidePlus class="ms-like-button-icon" />
                新建请求
              </button>
              <button type="button" class="ms-like-sidebar-secondary" @click="openDefinitionImportDialog">
                <Upload class="ms-like-button-icon" />
                导入
              </button>
            </div>
            <div class="ms-like-sidebar-search">
              <LucideSearch class="ms-like-search-icon" />
              <el-input v-model="definitionFilters.keyword" placeholder="搜索模块或请求" clearable />
            </div>
            <div class="ms-like-directory-title-row">
              <div class="ms-like-directory-title-main">
                <span>请求目录</span>
                <span>{{ filteredDefinitions.length }}</span>
              </div>
              <button
                type="button"
                class="ms-like-directory-collapse"
                title="收起全部子模块"
                aria-label="收起全部子模块"
                @click="collapseAllDefinitionTreeChildren"
              >
                <el-icon class="tree-collapse-icon"><Fold /></el-icon>
              </button>
            </div>
            <div class="ms-like-directory-shell">
              <el-tree
                :key="definitionTreeRenderKey"
                :data="visibleDefinitionDirectoryTree"
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
                        v-if="data.type === 'workspace' || data.type === 'module'"
                        :class="['tree-node-folder-svg', { 'is-open': isDefinitionTreeExpanded(data.key) }]"
                        aria-hidden="true"
                      >
                        <LucideFolderOpen v-if="isDefinitionTreeExpanded(data.key)" class="tree-node-folder-icon" />
                        <LucideFolder v-else class="tree-node-folder-icon" />
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
                    <div class="ms-like-directory-actions" @click.stop>
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
                        v-if="(data.type === 'workspace' || data.type === 'module') && canWriteWorkspace(data.workspaceCode)"
                        text
                        class="tree-icon-button"
                        @click.stop="createDefinitionModule(data)"
                      >
                          <LucidePlus class="tree-lucide-action-icon" />
                      </el-button>
                      <el-dropdown
                        v-if="data.type === 'module' && canWriteWorkspace(data.workspaceCode)"
                        trigger="click"
                        popper-class="definition-tree-action-menu"
                        @command="(command: string | number | object) => handleDefinitionModuleAction(String(command), data)"
                      >
                        <el-button
                          text
                          class="tree-icon-button definition-tree-more-button"
                          title="更多操作"
                          aria-label="更多操作"
                          @click.stop
                        >
                          <MoreHorizontal class="tree-lucide-action-icon" />
                        </el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item command="rename" class="definition-tree-action-item">
                              重命名
                            </el-dropdown-item>
                            <el-dropdown-item command="delete" class="definition-tree-action-item definition-tree-action-danger">
                              删除
                            </el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                      <el-dropdown
                        v-if="data.type === 'request' && data.definitionId && canWriteWorkspace(data.workspaceCode)"
                        trigger="click"
                        popper-class="definition-tree-action-menu"
                        @command="(command: string | number | object) => handleDefinitionRequestAction(String(command), data)"
                      >
                        <el-button
                          text
                          class="tree-icon-button definition-tree-more-button"
                          title="更多操作"
                          aria-label="更多操作"
                          @click.stop
                        >
                          <MoreHorizontal class="tree-lucide-action-icon" />
                        </el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item command="rename" class="definition-tree-action-item">
                              重命名
                            </el-dropdown-item>
                            <el-dropdown-item command="delete" class="definition-tree-action-item definition-tree-action-danger">
                              删除
                            </el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </div>
                  </div>
                </template>
              </el-tree>
            </div>
          </aside>

          <section class="ms-like-main">
            <div class="ms-like-tab-strip">
              <div class="ms-like-tab-strip-main">
                <button
                  v-if="requestTabOverflow.overflow"
                  type="button"
                  class="ms-like-tab-scroll-button"
                  :disabled="requestTabOverflow.arrivedLeft"
                  aria-label="向左滚动标签"
                  @click="scrollTabStrip(requestTabNavRef, requestTabOverflow, 'left')"
                >
                  <el-icon><ArrowLeft /></el-icon>
                </button>
                <div ref="requestTabNavRef" class="ms-like-tab-nav">
                  <button
                    v-for="item in visibleRequestEditorTabs"
                    :key="item.key"
                    type="button"
                    :class="['ms-like-editor-tab', { active: item.key === activeRequestEditorKey }]"
                    @click="activateRequestEditorTab(item.key)"
                  >
                    <span
                      v-if="item.resourceType === 'ai-case-generation' && item.aiGeneration?.generating"
                      :class="['ai-generation-tab-spinner', { spinning: item.aiGeneration?.generating }]"
                      aria-hidden="true"
                    ></span>
                    <img
                      v-else-if="item.resourceType === 'ai-case-generation'"
                      :src="aiCaseGenerationFinishedIcon"
                      alt="ai"
                      class="ai-generation-tab-finished-icon"
                    >
                    <span v-else :class="['ms-like-method', `method-${item.method.toLowerCase()}`]">
                      {{ item.method }}
                    </span>
                    <span class="ms-like-editor-tab-label">{{ item.title }}</span>
                    <span v-if="item.isDirty" class="ms-like-editor-tab-dot"></span>
                    <span v-if="requestEditorTabs.length > 1" class="ms-like-editor-tab-close" @click.stop="closeRequestEditorTab(item.key)">
                      <el-icon><Close /></el-icon>
                    </span>
                  </button>
                </div>
                <button
                  v-if="requestTabOverflow.overflow"
                  type="button"
                  class="ms-like-tab-scroll-button"
                  :disabled="requestTabOverflow.arrivedRight"
                  aria-label="向右滚动标签"
                  @click="scrollTabStrip(requestTabNavRef, requestTabOverflow, 'right')"
                >
                  <el-icon><ArrowRight /></el-icon>
                </button>
                <button type="button" class="ms-like-tab-add" @click="openNewRequestTab()">
                  <LucidePlus class="ms-like-tab-action-icon" />
                </button>
                <el-dropdown
                  v-if="showRequestEditorMoreAction"
                  trigger="click"
                  placement="bottom-start"
                  @command="closeOtherRequestEditorTabs"
                >
                  <button type="button" class="scenario-editor-more-button" aria-label="更多标签操作" @click.stop>
                    <MoreHorizontal class="ms-like-tab-action-icon" />
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="closeOthers">关闭其他标签</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>

            <div v-if="isAiCaseGenerationTabActive" class="ai-generation-workspace">
              <div class="ai-generation-page-header">
                <div>
                  <h3 class="ai-generation-title-line">
                    <span>AI 生成单接口用例</span>
                    <span class="ai-generation-title-source">
                      <span class="ai-generation-source-name">{{ activeAiCaseGenerationState?.definitionName }}</span>
                      <span :class="['ms-like-method', `method-${(activeAiCaseGenerationState?.method || 'GET').toLowerCase()}`]">
                        {{ activeAiCaseGenerationState?.method }}
                      </span>
                      <span class="ai-generation-source-path">{{ displayRequestPath(activeAiCaseGenerationState?.path) }}</span>
                    </span>
                  </h3>
                </div>
                <button
                  type="button"
                  :class="['ai-generation-header-action', { 'is-stop': activeAiCaseGenerationState?.generating }]"
                  @click="activeAiCaseGenerationState?.generating ? stopAiCaseGeneration() : openAiCaseGenerateDrawer()"
                >
                  <MagicStick v-if="!activeAiCaseGenerationState?.generating" />
                  {{ activeAiCaseGenerationState?.generating ? '停止' : '生成新用例' }}
                </button>
              </div>

              <div class="ai-generation-detail-workspace">
                <div class="ai-generation-detail-status-row">
                  <div class="ai-generation-detail-status-tabs">
                    <button
                      type="button"
                      :class="{ active: aiGenerationStatusFilter === 'pending' }"
                      @click="setAiGenerationStatusFilter('pending')"
                    >
                      待处理 ({{ aiCaseGenerationPendingCount }})
                    </button>
                    <button
                      type="button"
                      :class="{ active: aiGenerationStatusFilter === 'accepted' }"
                      @click="setAiGenerationStatusFilter('accepted')"
                    >
                      已采纳 ({{ aiCaseGenerationAcceptedCount }})
                    </button>
                    <button
                      type="button"
                      :class="{ active: aiGenerationStatusFilter === 'discarded' }"
                      @click="setAiGenerationStatusFilter('discarded')"
                    >
                      废弃 ({{ aiCaseGenerationDiscardedCount }})
                    </button>
                  </div>
                </div>

                <div class="ai-generation-detail-toolbar">
                  <div class="ai-generation-detail-search">
                    <LucideSearch />
                    <input v-model="aiGenerationDetailKeyword" type="text" placeholder="搜索" />
                  </div>
                  <el-select v-model="aiGenerationDetailGroupFilter" class="ai-generation-detail-filter" placeholder="分组" clearable>
                    <el-option
                      v-for="group in aiCaseGenerateGroups"
                      :key="group.key"
                      :label="group.label"
                      :value="group.key"
                    />
                  </el-select>
                  <el-select v-model="aiGenerationDetailTypeFilter" class="ai-generation-detail-filter is-wide" placeholder="类型" clearable>
                    <el-option
                      v-for="option in aiGenerationDetailTypeOptions"
                      :key="option.value"
                      :label="option.label"
                      :value="option.value"
                    />
                  </el-select>
                  <div class="ai-generation-detail-actions">
                    <button type="button" class="ai-generation-run-selected" @click="runSelectedAiCaseGenerateResults">
                      <Play />
                      运行选中
                    </button>
                    <button type="button" class="ai-generation-accept-selected" @click="acceptSelectedAiCaseGenerateResults">采纳选中</button>
                    <button type="button" class="ai-generation-discard-selected" @click="discardSelectedAiCaseGenerateResults">废弃选中</button>
                  </div>
                </div>

                <div class="ai-generation-detail-table">
                  <div class="ai-generation-detail-head">
                    <div class="ai-generation-row-selector">
                      <el-checkbox
                        :model-value="isAiCaseGenerationAllSelected"
                        :indeterminate="isAiCaseGenerationSelectionIndeterminate"
                        @update:model-value="toggleAllAiCaseGenerationSelection(Boolean($event))"
                      />
                    </div>
                    <span>名称</span>
                    <span>类型</span>
                    <span>分组</span>
                    <span>运行结果</span>
                    <span></span>
                  </div>
                  <div class="ai-generation-detail-body">
                    <div v-if="!filteredAiCaseGenerationResults.length" class="ai-generation-empty-state">
                      <MagicStick />
                      <span>{{ aiGenerationEmptyStateText }}</span>
                    </div>
                    <template
                      v-for="(item, index) in filteredAiCaseGenerationResults"
                      :key="item.id"
                    >
                      <div
                        :class="['ai-generation-detail-row', { active: false }]"
                        @click="toggleAiGeneratedCaseDetail(item)"
                      >
                        <div class="ai-generation-row-selector" @click.stop>
                          <svg
                            v-if="item.status === 'generating'"
                            class="ai-generation-row-loading"
                            version="1.1"
                            xmlns="http://www.w3.org/2000/svg"
                            viewBox="0 40 60 16"
                            aria-hidden="true"
                            focusable="false"
                          >
                            <circle fill="currentColor" stroke="none" cx="6" cy="50" r="6">
                              <animate attributeName="opacity" dur="1s" values="0;1;0" repeatCount="indefinite" begin="0.1s" />
                            </circle>
                            <circle fill="currentColor" stroke="none" cx="26" cy="50" r="6">
                              <animate attributeName="opacity" dur="1s" values="0;1;0" repeatCount="indefinite" begin="0.2s" />
                            </circle>
                            <circle fill="currentColor" stroke="none" cx="46" cy="50" r="6">
                              <animate attributeName="opacity" dur="1s" values="0;1;0" repeatCount="indefinite" begin="0.3s" />
                            </circle>
                          </svg>
                          <template v-else>
                            <span class="ai-generation-row-index">{{ index + 1 }}</span>
                            <el-checkbox
                              class="ai-generation-row-checkbox"
                              :model-value="selectedAiCaseGenerateResultIds.includes(item.id)"
                              :disabled="item.status === 'failed'"
                              @update:model-value="toggleAiCaseGenerationSelection(item.id, Boolean($event))"
                            />
                          </template>
                        </div>
                        <div class="ai-generation-detail-name">
                          <span>{{ item.name }}</span>
                        </div>
                        <div class="ai-generation-detail-group-cell">
                          <span class="ai-generation-case-tag">{{ item.type }}</span>
                        </div>
                        <span :class="['ai-generation-detail-group-type', item.group === '正向' ? 'is-positive' : 'is-negative']">{{ item.group }}</span>
                        <span :class="['ai-generation-run-result', { 'is-success': item.runResult === '通过', 'is-failed': item.runResult === '失败' || item.status === 'failed' }]">
                          {{ item.runResult || '-' }}
                        </span>
                        <div class="ai-generation-row-actions">
                          <button type="button" class="ai-generation-row-run" :disabled="item.status === 'generating' || item.status === 'failed'" @click.stop="runAiCaseGenerateResult(item)">
                            <Play />
                            运行
                          </button>
                          <button type="button" class="ai-generation-row-accept" :disabled="item.status === 'generating' || item.status === 'failed'" @click.stop="acceptAiCaseGenerateResult(item)">采纳</button>
                          <button type="button" class="ai-generation-row-discard" :disabled="item.status === 'generating'" @click.stop="updateAiCaseGenerateResultStatus(item.id, 'discarded')">废弃</button>
                        </div>
                      </div>

                      <div v-if="false" class="ai-generation-case-expand">
                        <div class="ai-generation-case-expand-request">
                          <div class="ai-generation-case-url-line">
                            <span :class="['ms-like-method', `method-${item.requestConfig.method.toLowerCase()}`]">{{ item.requestConfig.method }}</span>
                            <span class="ai-generation-case-path">{{ item.requestConfig.path || activeAiCaseGenerationState?.path || '-' }}</span>
                            <span class="ai-generation-case-type-tag">{{ item.type }}</span>
                          </div>

                          <div class="ms-like-top-tabs ai-generation-case-tabs">
                            <button :class="['ms-like-top-tab', { active: item.activeTab === 'headers' }]" type="button" @click="setAiGeneratedCaseTab(item, 'headers')">请求头</button>
                            <button :class="['ms-like-top-tab', { active: item.activeTab === 'body' }]" type="button" @click="setAiGeneratedCaseTab(item, 'body')">请求体</button>
                            <button :class="['ms-like-top-tab', { active: item.activeTab === 'params' }]" type="button" @click="setAiGeneratedCaseTab(item, 'params')">Params</button>
                            <button :class="['ms-like-top-tab', { active: item.activeTab === 'auth' }]" type="button" @click="setAiGeneratedCaseTab(item, 'auth')">Auth</button>
                            <button :class="['ms-like-top-tab', { active: item.activeTab === 'pre' }]" type="button" @click="setAiGeneratedCaseTab(item, 'pre')">前置操作</button>
                            <button :class="['ms-like-top-tab', { active: item.activeTab === 'post' }]" type="button" @click="setAiGeneratedCaseTab(item, 'post')">后置操作</button>
                            <button :class="['ms-like-top-tab', { active: item.activeTab === 'tests' }]" type="button" @click="setAiGeneratedCaseTab(item, 'tests')">断言</button>
                            <button :class="['ms-like-top-tab', { active: item.activeTab === 'settings' }]" type="button" @click="setAiGeneratedCaseTab(item, 'settings')">设置</button>
                          </div>

                          <div class="ms-like-request-body ai-generation-case-config-body">
                            <template v-if="item.activeTab === 'body'">
                              <div class="ai-generation-case-body-mode">
                                <span :class="['ms-like-body-chip', 'active']">{{ formatAiGeneratedBodyType(item.requestConfig.body.type) }}</span>
                              </div>
                              <div :class="['ms-like-body-mode-shell', { 'is-none': item.requestConfig.body.type === 'NONE' }]">
                                <MonacoCodeEditor
                                  v-if="['RAW_JSON', 'RAW_XML', 'RAW_TEXT'].includes(item.requestConfig.body.type)"
                                  :model-value="aiGeneratedCaseBodyText(item)"
                                  :language="aiGeneratedCaseBodyLanguage(item)"
                                  height="100%"
                                  adaptive
                                  :min-adaptive-height="260"
                                  :max-adaptive-height="720"
                                  @update:model-value="setAiGeneratedCaseBodyText(item, $event)"
                                />
                                <div v-else-if="['FORM_URLENCODED', 'FORM_DATA'].includes(item.requestConfig.body.type)" class="request-section ms-like-table-surface ms-like-param-table ms-like-param-table--body-form">
                                  <div class="ms-like-table-header ms-like-param-table-grid ms-like-param-table-grid--body-form">
                                    <div class="ms-like-drag-cell"></div>
                                    <div class="ms-like-checkbox-cell ms-like-checkbox-cell--header"><el-checkbox :model-value="true" /></div>
                                    <span class="ms-like-header-input-title">参数名称</span>
                                    <span class="ms-like-type-header">类型</span>
                                    <span>参数值</span>
                                    <span class="ms-like-length-header">长度范围</span>
                                    <span>描述</span>
                                    <span></span>
                                  </div>
                                  <div v-for="(row, rowIndex) in item.requestConfig.body.formItems" :key="`${item.id}-body-${rowIndex}`" class="ms-like-table-row ms-like-param-table-grid ms-like-param-table-grid--body-form">
                                    <div class="ms-like-drag-cell"></div>
                                    <div class="ms-like-checkbox-cell"><el-checkbox v-model="row.enabled" /></div>
                                    <div class="ms-like-name-field"><el-input v-model="row.key" placeholder="参数名称" @input="handleKeyValueRowInput(item.requestConfig.body.formItems, bodyFormParamDefaults())" /></div>
                                    <div class="ms-like-type-field">
                                      <button type="button" :class="['ms-like-required-button', { active: row.required }]" :title="row.required ? '必填' : '非必填'" @click="row.required = !row.required">*</button>
                                      <el-select v-model="row.paramType" @change="handleKeyValueRowInput(item.requestConfig.body.formItems, bodyFormParamDefaults())">
                                        <el-option v-for="option in bodyParamTypeOptionsFor({ requestConfig: item.requestConfig })" :key="option" :label="option" :value="option" />
                                      </el-select>
                                    </div>
                                    <el-input v-model="row.value" placeholder="参数值" @input="handleKeyValueRowInput(item.requestConfig.body.formItems, bodyFormParamDefaults())" />
                                    <div class="ms-like-length-range-cell">
                                      <el-input-number v-model="row.minLength" :min="0" :controls="false" placeholder="最小" @change="handleKeyValueRowInput(item.requestConfig.body.formItems, bodyFormParamDefaults())" />
                                      <span>至</span>
                                      <el-input-number v-model="row.maxLength" :min="0" :controls="false" placeholder="最大" @change="handleKeyValueRowInput(item.requestConfig.body.formItems, bodyFormParamDefaults())" />
                                    </div>
                                    <el-input v-model="row.description" placeholder="描述" @input="handleKeyValueRowInput(item.requestConfig.body.formItems, bodyFormParamDefaults())" />
                                    <button type="button" class="ms-like-row-remove" @click="removeKeyValueRow(item.requestConfig.body.formItems, rowIndex, bodyFormParamDefaults())">删除</button>
                                  </div>
                                  <button type="button" class="ms-like-add-row" @click="item.requestConfig.body.formItems.push(emptyKeyValue(bodyFormParamDefaults()))">+ 添加一行</button>
                                </div>
                                <div v-else-if="item.requestConfig.body.type === 'BINARY'" class="request-section ms-like-form-panel">
                                  <div class="ms-like-form-row">
                                    <div class="ms-like-form-label">File</div>
                                    <el-input v-model="item.requestConfig.body.fileName" class="ms-like-form-control" placeholder="文件名" />
                                  </div>
                                </div>
                                <div v-else class="ms-like-empty-body">请求没有 Body</div>
                              </div>
                            </template>

                            <template v-else-if="item.activeTab === 'headers'">
                              <div class="request-section ms-like-table-surface ms-like-param-table ms-like-param-table--header">
                                <div class="ms-like-table-header ms-like-param-table-grid ms-like-param-table-grid--header">
                                  <div class="ms-like-drag-cell"></div>
                                  <div class="ms-like-checkbox-cell ms-like-checkbox-cell--header"><el-checkbox :model-value="true" /></div>
                                  <span class="ms-like-header-input-title">Header 名称</span>
                                  <span>Header 值</span>
                                  <span>描述</span>
                                  <button type="button" class="ms-like-link-button">批量添加</button>
                                </div>
                                <div v-for="(row, rowIndex) in item.requestConfig.headers" :key="`${item.id}-header-${rowIndex}`" class="ms-like-table-row ms-like-param-table-grid ms-like-param-table-grid--header">
                                  <div class="ms-like-drag-cell"></div>
                                  <div class="ms-like-checkbox-cell"><el-checkbox v-model="row.enabled" /></div>
                                  <div class="ms-like-name-field"><el-input v-model="row.key" placeholder="Header 名称" @input="handleKeyValueRowInput(item.requestConfig.headers, headerParamDefaults())" /></div>
                                  <el-input v-model="row.value" placeholder="Header 值" @input="handleKeyValueRowInput(item.requestConfig.headers, headerParamDefaults())" />
                                  <el-input v-model="row.description" placeholder="描述" @input="handleKeyValueRowInput(item.requestConfig.headers, headerParamDefaults())" />
                                  <button type="button" class="ms-like-row-remove" @click="removeKeyValueRow(item.requestConfig.headers, rowIndex, headerParamDefaults())">删除</button>
                                </div>
                                <button type="button" class="ms-like-add-row" @click="item.requestConfig.headers.push(emptyKeyValue(headerParamDefaults()))">+ 添加一行</button>
                              </div>
                            </template>

                            <template v-else-if="item.activeTab === 'params'">
                              <div class="request-section ms-like-table-surface ms-like-param-table ms-like-param-table--query">
                                <div class="ms-like-table-header ms-like-param-table-grid ms-like-param-table-grid--query">
                                  <div class="ms-like-drag-cell"></div>
                                  <div class="ms-like-checkbox-cell ms-like-checkbox-cell--header"><el-checkbox :model-value="true" /></div>
                                  <span class="ms-like-header-input-title">Query 参数</span>
                                  <span class="ms-like-type-header">类型</span>
                                  <span>参数值</span>
                                  <span class="ms-like-length-header">长度范围</span>
                                  <span>编码</span>
                                  <span>描述</span>
                                  <button type="button" class="ms-like-link-button">批量添加</button>
                                </div>
                                <div v-for="(row, rowIndex) in item.requestConfig.queryParams" :key="`${item.id}-query-${rowIndex}`" class="ms-like-table-row ms-like-param-table-grid ms-like-param-table-grid--query">
                                  <div class="ms-like-drag-cell"></div>
                                  <div class="ms-like-checkbox-cell"><el-checkbox v-model="row.enabled" /></div>
                                  <div class="ms-like-name-field"><el-input v-model="row.key" placeholder="参数名称" @input="handleKeyValueRowInput(item.requestConfig.queryParams, queryParamDefaults())" /></div>
                                  <div class="ms-like-type-field">
                                    <button type="button" :class="['ms-like-required-button', { active: row.required }]" :title="row.required ? '必填' : '非必填'" @click="row.required = !row.required">*</button>
                                    <el-select v-model="row.paramType" @change="handleKeyValueRowInput(item.requestConfig.queryParams, queryParamDefaults())">
                                      <el-option v-for="option in queryParamTypeOptions" :key="option" :label="option" :value="option" />
                                    </el-select>
                                  </div>
                                  <el-input v-model="row.value" placeholder="参数值" @input="handleKeyValueRowInput(item.requestConfig.queryParams, queryParamDefaults())" />
                                  <div class="ms-like-length-range-cell">
                                    <el-input-number v-model="row.minLength" :min="0" :controls="false" placeholder="最小" @change="handleKeyValueRowInput(item.requestConfig.queryParams, queryParamDefaults())" />
                                    <span>至</span>
                                    <el-input-number v-model="row.maxLength" :min="0" :controls="false" placeholder="最大" @change="handleKeyValueRowInput(item.requestConfig.queryParams, queryParamDefaults())" />
                                  </div>
                                  <div class="ms-like-switch-cell ms-like-switch-cell--query"><el-switch v-model="row.encode" size="small" /></div>
                                  <el-input v-model="row.description" placeholder="描述" @input="handleKeyValueRowInput(item.requestConfig.queryParams, queryParamDefaults())" />
                                  <button type="button" class="ms-like-row-remove" @click="removeKeyValueRow(item.requestConfig.queryParams, rowIndex, queryParamDefaults())">删除</button>
                                </div>
                                <button type="button" class="ms-like-add-row" @click="item.requestConfig.queryParams.push(emptyKeyValue(queryParamDefaults()))">+ 添加一行</button>
                              </div>
                            </template>

                            <template v-else-if="item.activeTab === 'auth'">
                              <div class="ai-generation-case-summary-grid">
                                <span>认证方式</span><strong>{{ item.requestConfig.authConfig.authType }}</strong>
                                <span>用户名</span><strong>{{ item.requestConfig.authConfig.basicAuth.userName || item.requestConfig.authConfig.digestAuth.userName || '-' }}</strong>
                              </div>
                            </template>

                            <template v-else-if="item.activeTab === 'pre'">
                              <div class="request-section">
                                <ApiProcessorEditor
                                  v-model="item.preProcessors"
                                  v-model:active-id="activePreProcessorId"
                                  stage="pre"
                                  :db-connections="dbConnections"
                                />
                              </div>
                            </template>

                            <template v-else-if="item.activeTab === 'post'">
                              <div class="request-section">
                                <ApiProcessorEditor
                                  v-model="item.postProcessors"
                                  v-model:active-id="activePostProcessorId"
                                  stage="post"
                                  :db-connections="dbConnections"
                                />
                              </div>
                            </template>

                            <template v-else-if="item.activeTab === 'tests'">
                              <div class="request-section">
                                <ApiAssertionEditor
                                  v-model="item.assertions"
                                  v-model:active-id="activeAssertionId"
                                />
                              </div>
                            </template>

                            <template v-else>
                              <div class="ai-generation-case-summary-grid">
                                <span>用例类型</span><strong>{{ item.type }}</strong>
                                <span>所属分组</span><strong>{{ item.group }}</strong>
                                <span>预期结果</span><strong>{{ item.expected }}</strong>
                              </div>
                            </template>
                          </div>
                        </div>

                        <div class="ms-like-response-shell ai-generation-case-expand-response">
                          <div class="ms-like-response-header">
                            <div class="ms-like-response-title">返回响应</div>
                            <div v-if="item.responseBody" class="ms-like-response-metrics">
                              <span :class="['ms-like-response-metric', item.runResult === '通过' ? 'is-success' : 'is-failed']">{{ item.responseStatus || '-' }}</span>
                            </div>
                          </div>
                          <div class="ms-like-response-content-panel">
                            <div v-if="!item.responseBody" class="ms-like-response-empty">
                              <div class="ms-like-response-empty-card">
                                <div class="ms-like-response-empty-visual">
                                  <div class="ms-like-response-empty-window">
                                    <span></span>
                                    <span></span>
                                    <span></span>
                                  </div>
                                </div>
                                <div class="ms-like-response-empty-text">点击 <span>运行</span> 获取返回结果</div>
                              </div>
                            </div>
                            <template v-else>
                              <div class="ms-like-response-tabs">
                                <button :class="['ms-like-top-tab', { active: item.responseTab === 'body' }]" @click="setAiGeneratedCaseResponseTab(item, 'body')">Body</button>
                                <button :class="['ms-like-top-tab', { active: item.responseTab === 'header' }]" @click="setAiGeneratedCaseResponseTab(item, 'header')">Header</button>
                                <button :class="['ms-like-top-tab', { active: item.responseTab === 'console' }]" @click="setAiGeneratedCaseResponseTab(item, 'console')">控制台</button>
                                <button :class="['ms-like-top-tab', { active: item.responseTab === 'actualRequest' }]" @click="setAiGeneratedCaseResponseTab(item, 'actualRequest')">实际请求</button>
                                <button :class="['ms-like-top-tab', { active: item.responseTab === 'assertions' }]" @click="setAiGeneratedCaseResponseTab(item, 'assertions')">断言</button>
                              </div>
                              <div class="ms-like-response-body">
                                <MonacoCodeEditor
                                  :model-value="aiGeneratedCaseResponsePreview(item)"
                                  :language="aiGeneratedCaseResponseLanguage(item)"
                                  :read-only="true"
                                  :show-format-button="false"
                                  :fit-content="true"
                                  :max-fit-content-height="720"
                                  height="100%"
                                />
                              </div>
                            </template>
                          </div>
                        </div>
                      </div>
                    </template>
                  </div>
                </div>
              </div>
            </div>

              <div v-else :class="['ms-like-editor-shell', { 'without-response': !shouldShowResponsePanel }]">
              <div class="ms-like-request-shell">
                <div v-if="isAllScope && !definitionForm.workspaceCode" class="scope-hint">
                  &#24403;&#21069;&#22788;&#20110; ALL &#35270;&#35282;&#65292;&#35831;&#20808;&#22312;&#39030;&#37096;&#36873;&#25321;&#30446;&#26631;&#31354;&#38388;&#21518;&#20877;&#20445;&#23384;&#25110;&#35843;&#35797;&#12290;
                </div>

                <div class="ms-like-request-row">
                  <div class="ms-like-url-compose">
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
                    <el-input
                      v-model="definitionForm.requestConfig.path"
                      class="request-url-input"
                      placeholder="请输入包含 http/https 的完整 URL 或接口路径"
                      data-testid="definition-url-input"
                    />
                    <button type="button" class="ms-like-curl-button" @click="promptImportCurl">Curl</button>
                  </div>
                  <button
                    type="button"
                    class="ms-like-send-button"
                    :disabled="!canDebugDefinition || saving"
                    @click="debugActiveEditor"
                  >
                    <Play class="ms-like-button-icon" />
                    发送
                  </button>
                  <el-dropdown
                    split-button
                    class="ms-like-save-dropdown"
                    :disabled="!canSaveActiveEditor"
                    :loading="saving"
                    data-testid="definition-save-button"
                    @click="saveActiveEditor"
                  >
                    <span class="ms-like-save-label">
                      <Save class="ms-like-button-icon" />
                      保存
                    </span>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item v-if="activeRequestEditorTab?.resourceType === 'definition' && !!definitionForm.id" @click="saveDefinitionAsCase">保存为用例</el-dropdown-item>
                        <el-dropdown-item v-if="activeRequestEditorTab?.resourceType === 'definition'" @click="duplicateDefinition">复制接口</el-dropdown-item>
                        <el-dropdown-item @click="removeActiveEditor">{{ activeRequestEditorTab?.resourceType === 'case' ? '删除用例' : '删除接口' }}</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>

              </div>

              <div ref="definitionEditorShellRef" class="ms-like-editor-scroll">
                <div class="ms-like-request-content-panel">
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
                    <button data-testid="request-tab-tests" :class="['ms-like-top-tab', { active: activeRequestTab === 'tests' }]" @click="setActiveRequestContentTab('tests')">
                      断言
                      <span v-if="assertionEnabledCount" class="ms-like-tab-badge">{{ assertionEnabledCount }}</span>
                    </button>
                    <button :class="['ms-like-top-tab', { active: activeRequestTab === 'settings' }]" @click="setActiveRequestContentTab('settings')">&#35774;&#32622;</button>
                    <button v-if="activeRequestEditorTab?.resourceType === 'definition'" :class="['ms-like-top-tab', { active: activeRequestTab === 'cases' }]" @click="setActiveRequestContentTab('cases')">
                      用例
                      <span v-if="currentDefinitionCaseCount" class="ms-like-tab-badge">{{ currentDefinitionCaseCount }}</span>
                    </button>
                  </div>
                </div>
                  <div v-if="showCaseListContent" class="ms-like-request-body case-list-request-body">
                <div class="request-section case-list-panel">
                  <div class="editor-actions left">
                    <el-button
                      type="primary"
                      :title="canCreateCaseForCurrentDefinition ? '新建用例' : '请先保存接口，再创建用例'"
                      @click="openCaseDraftFromDefinition()"
                    >
                      新建用例
                    </el-button>
                    <button
                      type="button"
                      class="case-ai-generate-button"
                      :disabled="!canCreateCaseForCurrentDefinition"
                      :title="canCreateCaseForCurrentDefinition ? 'AI 生成接口用例' : '请先保存接口，再使用 AI 生成用例'"
                      @click="openAiCaseGenerateDrawer"
                    >
                      <MagicStick />
                      <span>AI生成用例</span>
                    </button>
                  </div>
                  <div v-if="!currentDefinitionCases.length" class="empty-hint">当前接口下还没有用例</div>
                  <div v-else class="case-list-table-wrap">
                  <el-table ref="caseListTableRef" :data="pagedDefinitionCases" size="small" class="case-list-table">
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
                      <span class="ms-like-type-header">类型</span>
                      <span>参数值</span>
                      <span class="ms-like-length-header">长度范围</span>
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
                        <el-input
                          v-model="row.key"
                          placeholder="参数名称"
                          @input="handleKeyValueRowInput(definitionForm.requestConfig.queryParams, queryParamDefaults())"
                        />
                      </div>
                      <div class="ms-like-type-field">
                        <button
                          type="button"
                          :class="['ms-like-required-button', { active: row.required }]"
                          :title="row.required ? '必填' : '非必填'"
                          @click="row.required = !row.required"
                        >
                          *
                        </button>
                        <el-select v-model="row.paramType" @change="handleKeyValueRowInput(definitionForm.requestConfig.queryParams, queryParamDefaults())">
                          <el-option v-for="option in queryParamTypeOptions" :key="option" :label="option" :value="option" />
                        </el-select>
                      </div>
                      <el-input
                        v-model="row.value"
                        placeholder="参数值 / {{variable}}"
                        @input="handleKeyValueRowInput(definitionForm.requestConfig.queryParams, queryParamDefaults())"
                      />
                      <div class="ms-like-length-range-cell">
                        <el-input-number
                          v-model="row.minLength"
                          :min="0"
                          :controls="false"
                          placeholder="最小"
                          @change="handleKeyValueRowInput(definitionForm.requestConfig.queryParams, queryParamDefaults())"
                        />
                        <span>至</span>
                        <el-input-number
                          v-model="row.maxLength"
                          :min="0"
                          :controls="false"
                          placeholder="最大"
                          @change="handleKeyValueRowInput(definitionForm.requestConfig.queryParams, queryParamDefaults())"
                        />
                      </div>
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
                  <div class="request-section ms-like-body-section">
                    <div class="ms-like-body-type-row">
                      <button :class="['ms-like-body-chip', { active: isBodyMode('NONE') }]" @click="setBodyMode('NONE')">none</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('FORM_DATA') }]" @click="setBodyMode('FORM_DATA')">form-data</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('FORM_URLENCODED') }]" @click="setBodyMode('FORM_URLENCODED')">x-www-form-urlencoded</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('RAW_JSON') }]" @click="setBodyMode('RAW_JSON')">json</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('RAW_XML') }]" @click="setBodyMode('RAW_XML')">xml</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('RAW_TEXT') }]" @click="setBodyMode('RAW_TEXT')">raw</button>
                      <button :class="['ms-like-body-chip', { active: isBodyMode('BINARY') }]" @click="setBodyMode('BINARY')">binary</button>
                    </div>
                    <div :class="['ms-like-body-mode-shell', { 'is-none': definitionForm.requestConfig.body.type === 'NONE' }]">
                    <MonacoCodeEditor
                      v-if="['RAW_JSON', 'RAW_XML', 'RAW_TEXT'].includes(definitionForm.requestConfig.body.type)"
                      v-model="activeBodyRawText"
                      :language="activeBodyLanguage"
                      height="100%"
                      adaptive
                      :min-adaptive-height="300"
                      :max-adaptive-height="1000"
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
                        <span class="ms-like-type-header">类型</span>
                        <span>参数值</span>
                        <span class="ms-like-length-header">长度范围</span>
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
                          <el-input
                            v-model="row.key"
                            placeholder="参数名称"
                            @input="handleKeyValueRowInput(definitionForm.requestConfig.body.formItems, bodyFormParamDefaults())"
                          />
                        </div>
                        <div class="ms-like-type-field">
                          <button
                            type="button"
                            :class="['ms-like-required-button', { active: row.required }]"
                            :title="row.required ? '必填' : '非必填'"
                            @click="row.required = !row.required"
                          >
                            *
                          </button>
                          <el-select v-model="row.paramType" @change="handleKeyValueRowInput(definitionForm.requestConfig.body.formItems, bodyFormParamDefaults())">
                            <el-option v-for="option in bodyParamTypeOptionsFor(definitionForm)" :key="option" :label="option" :value="option" />
                          </el-select>
                        </div>
                        <div v-if="row.paramType === 'file'" class="ms-like-file-param-cell">
                          <button type="button" class="ms-like-file-pick" @click="pickBodyFormRowFile(row, definitionForm.requestConfig.body.formItems, bodyFormParamDefaults())">
                            {{ row.fileName || '选择文件' }}
                          </button>
                          <button v-if="row.fileBase64" type="button" class="ms-like-file-clear" @click="clearBodyFormRowFile(row)">清空</button>
                          <small v-if="row.fileBase64">{{ formatBodyFormFileSize(row) }}</small>
                        </div>
                        <el-input
                          v-else
                          v-model="row.value"
                          placeholder="参数值"
                          @input="handleKeyValueRowInput(definitionForm.requestConfig.body.formItems, bodyFormParamDefaults())"
                        />
                        <div class="ms-like-length-range-cell">
                          <el-input-number
                            v-model="row.minLength"
                            :min="0"
                            :controls="false"
                            placeholder="最小"
                            @change="handleKeyValueRowInput(definitionForm.requestConfig.body.formItems, bodyFormParamDefaults())"
                          />
                          <span>至</span>
                          <el-input-number
                            v-model="row.maxLength"
                            :min="0"
                            :controls="false"
                            placeholder="最大"
                            @change="handleKeyValueRowInput(definitionForm.requestConfig.body.formItems, bodyFormParamDefaults())"
                          />
                        </div>
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
                  <span v-if="currentAssertionResultPresentation.visible" :class="['ms-like-result-pill', `is-${currentAssertionResultPresentation.tone}`]">
                    {{ currentAssertionResultPresentation.label }}
                  </span>
                  <span :class="['ms-like-response-metric', `is-${currentResponseStatusTone}`]">状态 {{ currentResponseStatusCode ?? '-' }}</span>
                  <span :class="['ms-like-response-metric', `is-${currentResponseDurationTone}`]">耗时 {{ currentResponseDuration ?? '-' }}<template v-if="currentResponseDuration !== null"> ms</template></span>
                  <span>大小 {{ currentResponseSize }}</span>
                </div>
              </div>

              <div v-if="currentDebugError" class="response-error-banner">
                {{ currentDebugError }}
              </div>

              <div class="ms-like-response-content-panel">
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
                    <button :class="['ms-like-top-tab', { active: responsePreviewTab === 'assertions' }]" @click="responsePreviewTab = 'assertions'">断言</button>
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
                    <div v-else class="assertion-result-panel">
                      <div v-if="!currentAssertionResults.length" class="assertion-result-empty">当前请求未配置断言</div>
                      <el-table v-else :data="currentAssertionResults" size="small" class="assertion-result-table">
                        <el-table-column label="断言名称" min-width="140" show-overflow-tooltip>
                          <template #default="{ row }">{{ row.name || assertionTypeLabel(row.type) }}</template>
                        </el-table-column>
                        <el-table-column label="断言对象" width="96">
                          <template #default="{ row }">{{ assertionTypeLabel(row.type) }}</template>
                        </el-table-column>
                        <el-table-column label="条件" width="92">
                          <template #default="{ row }">{{ assertionConditionLabel(row.condition) }}</template>
                        </el-table-column>
                        <el-table-column label="期望值" min-width="120" show-overflow-tooltip>
                          <template #default="{ row }">{{ row.expectedValue || '-' }}</template>
                        </el-table-column>
                        <el-table-column label="实际值" min-width="120" show-overflow-tooltip>
                          <template #default="{ row }">{{ row.actualValue || '-' }}</template>
                        </el-table-column>
                        <el-table-column label="结果" width="78">
                          <template #default="{ row }">
                            <span :class="['case-drawer-history-result', `is-${assertionResultTone(row)}`]">{{ assertionResultLabel(row) }}</span>
                          </template>
                        </el-table-column>
                        <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
                          <template #default="{ row }">{{ row.success ? '-' : row.message || '-' }}</template>
                        </el-table-column>
                      </el-table>
                    </div>
                  </div>
                </template>
              </div>
            </div>
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
            :tags="caseDrawerForm.tags"
            :can-debug="canDebugCaseDrawer"
            :can-write="canWriteCaseDrawer"
            :saving="saving"
            :is-edit="!!caseDrawerForm.id"
            :read-only="caseDrawerReadOnly"
            :primary-action-label="caseDrawerPrimaryActionLabel"
            :show-footer="caseDrawerShowFooter"
            @request-close="closeCaseDrawer"
            @update:case-name="value => caseDrawerForm.name = value"
            @update:priority="value => caseDrawerForm.casePriority = value"
            @update:status="value => caseDrawerForm.caseStatus = value"
            @update:tags="value => caseDrawerForm.tags = value"
            @debug="debugCaseDrawer"
            @create="saveCaseDrawer"
            @save="saveCaseDrawer"
          >
            <template #tabs>
              <div v-if="caseDrawerReadOnly" class="ms-like-top-tabs case-drawer-view-tabs">
                <button :class="['ms-like-top-tab', { active: caseDrawerViewTab === 'detail' }]" @click="void setCaseDrawerViewTab('detail')">详情</button>
                <button :class="['ms-like-top-tab', { active: caseDrawerViewTab === 'runHistory' }]" @click="void setCaseDrawerViewTab('runHistory')">执行历史</button>
                <button :class="['ms-like-top-tab', { active: caseDrawerViewTab === 'changeHistory' }]" @click="void setCaseDrawerViewTab('changeHistory')">变更历史</button>
              </div>
              <div v-if="caseDrawerViewTab === 'detail'" class="ms-like-top-tabs case-drawer-top-tabs">
                <button :class="['ms-like-top-tab', { active: caseDrawerRequestTab === 'headers' }]" @click="setCaseDrawerRequestContentTab('headers')">请求头</button>
                <button :class="['ms-like-top-tab', { active: caseDrawerRequestTab === 'body' }]" @click="setCaseDrawerRequestContentTab('body')">请求体</button>
                <button :class="['ms-like-top-tab', { active: caseDrawerRequestTab === 'params' }]" @click="setCaseDrawerRequestContentTab('params')">
                  Params
                  <span v-if="caseDrawerQueryEnabledCount" class="ms-like-tab-badge">{{ caseDrawerQueryEnabledCount }}</span>
                </button>
                <button :class="['ms-like-top-tab', { active: caseDrawerRequestTab === 'auth' }]" @click="setCaseDrawerRequestContentTab('auth')">Auth</button>
                <button :class="['ms-like-top-tab', { active: caseDrawerRequestTab === 'pre' }]" @click="setCaseDrawerRequestContentTab('pre')">前置处理</button>
                <button :class="['ms-like-top-tab', { active: caseDrawerRequestTab === 'post' }]" @click="setCaseDrawerRequestContentTab('post')">后置处理</button>
                <button :class="['ms-like-top-tab', { active: caseDrawerRequestTab === 'tests' }]" @click="setCaseDrawerRequestContentTab('tests')">
                  断言
                  <span v-if="caseDrawerAssertionEnabledCount" class="ms-like-tab-badge">{{ caseDrawerAssertionEnabledCount }}</span>
                </button>
                <button :class="['ms-like-top-tab', { active: caseDrawerRequestTab === 'settings' }]" @click="setCaseDrawerRequestContentTab('settings')">设置</button>
              </div>
            </template>

            <template #body>
              <div v-if="caseDrawerViewTab === 'detail'" :class="['ms-like-request-body', { 'case-drawer-readonly-body': caseDrawerReadOnly }]">
                <template v-if="caseDrawerRequestTab === 'params'">
                  <div class="request-section ms-like-table-surface ms-like-param-table ms-like-param-table--query">
                    <div class="ms-like-table-header ms-like-param-table-grid ms-like-param-table-grid--query">
                      <div class="ms-like-drag-cell"></div>
                      <div class="ms-like-checkbox-cell ms-like-checkbox-cell--header">
                        <el-checkbox
                          v-model="caseDrawerQueryTableSelectionModel"
                          :disabled="caseDrawerReadOnly"
                          :indeterminate="tableSelectionState(caseDrawerForm.requestConfig.queryParams).indeterminate"
                        />
                      </div>
                      <span class="ms-like-header-input-title">Query 参数</span>
                      <span class="ms-like-type-header">类型</span>
                      <span>参数值</span>
                      <span class="ms-like-length-header">长度范围</span>
                      <span>编码</span>
                      <span>描述</span>
                      <button v-if="!caseDrawerReadOnly" type="button" class="ms-like-link-button" @click="openBatchAddDrawer('query', 'case')">批量添加</button>
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
                          :disabled="caseDrawerReadOnly"
                          :draggable="!caseDrawerReadOnly"
                          aria-label="拖拽排序"
                          @dragstart="handleParamDragStart('query', index, $event)"
                          @dragend="handleParamDragEnd"
                        >
                          <span v-for="dotIndex in 6" :key="`case-query-dot-${index}-${dotIndex}`" class="ms-like-drag-dot"></span>
                        </button>
                      </div>
                      <div class="ms-like-checkbox-cell">
                        <el-checkbox v-model="row.enabled" :disabled="caseDrawerReadOnly" />
                      </div>
                      <div class="ms-like-name-field">
                        <el-input
                          v-model="row.key"
                          :disabled="caseDrawerReadOnly"
                          placeholder="参数名称"
                          @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.queryParams, queryParamDefaults())"
                        />
                      </div>
                      <div class="ms-like-type-field">
                        <button
                          type="button"
                          :class="['ms-like-required-button', { active: row.required }]"
                          :title="row.required ? '必填' : '非必填'"
                          :disabled="caseDrawerReadOnly"
                          @click="row.required = !row.required"
                        >
                          *
                        </button>
                        <el-select v-model="row.paramType" :disabled="caseDrawerReadOnly" @change="handleKeyValueRowInput(caseDrawerForm.requestConfig.queryParams, queryParamDefaults())">
                          <el-option v-for="option in queryParamTypeOptions" :key="option" :label="option" :value="option" />
                        </el-select>
                      </div>
                      <el-input
                        v-model="row.value"
                        :disabled="caseDrawerReadOnly"
                        placeholder="参数值 / {{variable}}"
                        @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.queryParams, queryParamDefaults())"
                      />
                      <div class="ms-like-length-range-cell">
                        <el-input-number
                          v-model="row.minLength"
                          :disabled="caseDrawerReadOnly"
                          :min="0"
                          :controls="false"
                          placeholder="最小"
                          @change="handleKeyValueRowInput(caseDrawerForm.requestConfig.queryParams, queryParamDefaults())"
                        />
                        <span>至</span>
                        <el-input-number
                          v-model="row.maxLength"
                          :disabled="caseDrawerReadOnly"
                          :min="0"
                          :controls="false"
                          placeholder="最大"
                          @change="handleKeyValueRowInput(caseDrawerForm.requestConfig.queryParams, queryParamDefaults())"
                        />
                      </div>
                      <div class="ms-like-switch-cell ms-like-switch-cell--query">
                        <el-switch v-model="row.encode" :disabled="caseDrawerReadOnly" size="small" />
                      </div>
                      <el-input
                        v-model="row.description"
                        :disabled="caseDrawerReadOnly"
                        placeholder="描述"
                        @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.queryParams, queryParamDefaults())"
                      />
                      <button v-if="!caseDrawerReadOnly" type="button" class="ms-like-row-remove" @click="removeKeyValueRow(caseDrawerForm.requestConfig.queryParams, index, queryParamDefaults())">删除</button>
                    </div>
                    <button v-if="!caseDrawerReadOnly" type="button" class="ms-like-add-row" @click="addDefinitionRow(caseDrawerForm.requestConfig.queryParams)">+ 添加一行</button>
                  </div>
                </template>

                <template v-else-if="caseDrawerRequestTab === 'headers'">
                  <div class="request-section ms-like-table-surface ms-like-param-table ms-like-param-table--header">
                    <div class="ms-like-table-header ms-like-param-table-grid ms-like-param-table-grid--header">
                      <div class="ms-like-drag-cell"></div>
                      <div class="ms-like-checkbox-cell ms-like-checkbox-cell--header">
                        <el-checkbox
                          v-model="caseDrawerHeaderTableSelectionModel"
                          :disabled="caseDrawerReadOnly"
                          :indeterminate="tableSelectionState(caseDrawerForm.requestConfig.headers).indeterminate"
                        />
                      </div>
                      <span class="ms-like-header-input-title">参数名称</span>
                      <span>参数值</span>
                      <span>描述</span>
                      <button v-if="!caseDrawerReadOnly" type="button" class="ms-like-link-button" @click="openBatchAddDrawer('header', 'case')">批量添加</button>
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
                          :disabled="caseDrawerReadOnly"
                          :draggable="!caseDrawerReadOnly"
                          aria-label="拖拽排序"
                          @dragstart="handleParamDragStart('header', index, $event)"
                          @dragend="handleParamDragEnd"
                        >
                          <span v-for="dotIndex in 6" :key="`case-header-dot-${index}-${dotIndex}`" class="ms-like-drag-dot"></span>
                        </button>
                      </div>
                      <div class="ms-like-checkbox-cell">
                        <el-checkbox v-model="row.enabled" :disabled="caseDrawerReadOnly" />
                      </div>
                      <div class="ms-like-header-input-cell">
                        <el-input
                          v-model="row.key"
                          :disabled="caseDrawerReadOnly"
                          placeholder="参数名称"
                          @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.headers, headerParamDefaults())"
                        />
                      </div>
                      <el-input
                        v-model="row.value"
                        :disabled="caseDrawerReadOnly"
                        placeholder="参数值"
                        @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.headers, headerParamDefaults())"
                      />
                      <el-input
                        v-model="row.description"
                        :disabled="caseDrawerReadOnly"
                        placeholder="描述"
                        @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.headers, headerParamDefaults())"
                      />
                      <button v-if="!caseDrawerReadOnly" type="button" class="ms-like-row-remove" @click="removeKeyValueRow(caseDrawerForm.requestConfig.headers, index, headerParamDefaults())">删除</button>
                    </div>
                    <button v-if="!caseDrawerReadOnly" type="button" class="ms-like-add-row" @click="addDefinitionRow(caseDrawerForm.requestConfig.headers)">+ 添加一行</button>
                  </div>
                </template>

                <template v-else-if="caseDrawerRequestTab === 'body'">
                  <div class="request-section">
                    <div class="ms-like-body-type-row">
                      <button :disabled="caseDrawerReadOnly" :class="['ms-like-body-chip', { active: isBodyMode('NONE', caseDrawerForm) }]" @click="setBodyMode('NONE', caseDrawerForm)">none</button>
                      <button :disabled="caseDrawerReadOnly" :class="['ms-like-body-chip', { active: isBodyMode('FORM_DATA', caseDrawerForm) }]" @click="setBodyMode('FORM_DATA', caseDrawerForm)">form-data</button>
                      <button :disabled="caseDrawerReadOnly" :class="['ms-like-body-chip', { active: isBodyMode('FORM_URLENCODED', caseDrawerForm) }]" @click="setBodyMode('FORM_URLENCODED', caseDrawerForm)">x-www-form-urlencoded</button>
                      <button :disabled="caseDrawerReadOnly" :class="['ms-like-body-chip', { active: isBodyMode('RAW_JSON', caseDrawerForm) }]" @click="setBodyMode('RAW_JSON', caseDrawerForm)">json</button>
                      <button :disabled="caseDrawerReadOnly" :class="['ms-like-body-chip', { active: isBodyMode('RAW_XML', caseDrawerForm) }]" @click="setBodyMode('RAW_XML', caseDrawerForm)">xml</button>
                      <button :disabled="caseDrawerReadOnly" :class="['ms-like-body-chip', { active: isBodyMode('RAW_TEXT', caseDrawerForm) }]" @click="setBodyMode('RAW_TEXT', caseDrawerForm)">raw</button>
                      <button :disabled="caseDrawerReadOnly" :class="['ms-like-body-chip', { active: isBodyMode('BINARY', caseDrawerForm) }]" @click="setBodyMode('BINARY', caseDrawerForm)">binary</button>
                    </div>
                    <div class="ms-like-body-mode-shell">
                      <MonacoCodeEditor
                        v-if="['RAW_JSON', 'RAW_XML', 'RAW_TEXT'].includes(caseDrawerForm.requestConfig.body.type)"
                        v-model="caseDrawerActiveBodyRawText"
                        :language="caseDrawerActiveBodyLanguage"
                        :read-only="caseDrawerReadOnly"
                        class="case-drawer-body-editor"
                        height="300px"
                        adaptive
                        :min-adaptive-height="300"
                        :max-adaptive-height="1000"
                      />
                      <div v-else-if="caseDrawerForm.requestConfig.body.type === 'BINARY'" class="request-section ms-like-form-panel">
                        <div class="ms-like-form-row">
                          <div class="ms-like-form-label">File</div>
                          <div class="ms-like-form-control ms-like-binary-actions">
                            <el-button v-if="!caseDrawerReadOnly" @click="pickCaseDrawerBinaryBodyFile">
                              {{ caseDrawerForm.requestConfig.body.fileName ? '重新选择' : '选择文件' }}
                            </el-button>
                            <el-button v-if="!caseDrawerReadOnly" :disabled="!caseDrawerForm.requestConfig.body.binaryBase64" @click="clearCaseDrawerBinaryBodyFile">清空</el-button>
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
                                :disabled="caseDrawerReadOnly"
                                :indeterminate="tableSelectionState(caseDrawerForm.requestConfig.body.formItems).indeterminate"
                              />
                          </div>
                          <span class="ms-like-header-input-title">参数名称</span>
                          <span class="ms-like-type-header">类型</span>
                          <span>参数值</span>
                          <span class="ms-like-length-header">长度范围</span>
                          <span>描述</span>
                          <button v-if="!caseDrawerReadOnly" type="button" class="ms-like-link-button" @click="openBatchAddDrawer('body-form', 'case')">批量添加</button>
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
                              :disabled="caseDrawerReadOnly"
                              :draggable="!caseDrawerReadOnly"
                              aria-label="拖拽排序"
                              @dragstart="handleParamDragStart('body-form', index, $event)"
                              @dragend="handleParamDragEnd"
                            >
                              <span v-for="dotIndex in 6" :key="`case-body-dot-${index}-${dotIndex}`" class="ms-like-drag-dot"></span>
                            </button>
                          </div>
                          <div class="ms-like-checkbox-cell">
                            <el-checkbox v-model="row.enabled" :disabled="caseDrawerReadOnly" />
                          </div>
                          <div class="ms-like-name-field">
                            <el-input
                              v-model="row.key"
                              :disabled="caseDrawerReadOnly"
                              placeholder="参数名称"
                              @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.body.formItems, bodyFormParamDefaults())"
                            />
                          </div>
                          <div class="ms-like-type-field">
                            <button
                              type="button"
                              :class="['ms-like-required-button', { active: row.required }]"
                              :title="row.required ? '必填' : '非必填'"
                              :disabled="caseDrawerReadOnly"
                              @click="row.required = !row.required"
                            >
                              *
                            </button>
                            <el-select v-model="row.paramType" :disabled="caseDrawerReadOnly" @change="handleKeyValueRowInput(caseDrawerForm.requestConfig.body.formItems, bodyFormParamDefaults())">
                              <el-option v-for="option in bodyParamTypeOptionsFor(caseDrawerForm)" :key="option" :label="option" :value="option" />
                            </el-select>
                          </div>
                          <div v-if="row.paramType === 'file'" class="ms-like-file-param-cell">
                            <button v-if="!caseDrawerReadOnly" type="button" class="ms-like-file-pick" @click="pickBodyFormRowFile(row, caseDrawerForm.requestConfig.body.formItems, bodyFormParamDefaults())">
                              {{ row.fileName || '选择文件' }}
                            </button>
                            <span v-else>{{ row.fileName || '-' }}</span>
                            <button v-if="row.fileBase64 && !caseDrawerReadOnly" type="button" class="ms-like-file-clear" @click="clearBodyFormRowFile(row)">清空</button>
                            <small v-if="row.fileBase64">{{ formatBodyFormFileSize(row) }}</small>
                          </div>
                          <el-input
                            v-else
                            v-model="row.value"
                            :disabled="caseDrawerReadOnly"
                            placeholder="参数值"
                            @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.body.formItems, bodyFormParamDefaults())"
                          />
                          <div class="ms-like-length-range-cell">
                            <el-input-number
                              v-model="row.minLength"
                              :disabled="caseDrawerReadOnly"
                              :min="0"
                              :controls="false"
                              placeholder="最小"
                              @change="handleKeyValueRowInput(caseDrawerForm.requestConfig.body.formItems, bodyFormParamDefaults())"
                            />
                            <span>至</span>
                            <el-input-number
                              v-model="row.maxLength"
                              :disabled="caseDrawerReadOnly"
                              :min="0"
                              :controls="false"
                              placeholder="最大"
                              @change="handleKeyValueRowInput(caseDrawerForm.requestConfig.body.formItems, bodyFormParamDefaults())"
                            />
                          </div>
                          <el-input
                            v-model="row.description"
                            :disabled="caseDrawerReadOnly"
                            placeholder="描述"
                            @input="handleKeyValueRowInput(caseDrawerForm.requestConfig.body.formItems, bodyFormParamDefaults())"
                          />
                          <button v-if="!caseDrawerReadOnly" type="button" class="ms-like-row-remove" @click="removeKeyValueRow(caseDrawerForm.requestConfig.body.formItems, index, bodyFormParamDefaults())">删除</button>
                        </div>
                        <button v-if="!caseDrawerReadOnly" type="button" class="ms-like-add-row" @click="caseDrawerForm.requestConfig.body.formItems.push(emptyKeyValue(bodyFormParamDefaults()))">+ 添加一行</button>
                      </div>
                      <div v-else class="ms-like-empty-body">请求没有 Body</div>
                    </div>
                  </div>
                </template>

                <template v-else-if="caseDrawerRequestTab === 'pre'">
                  <div :class="['request-section', { 'case-drawer-readonly-section': caseDrawerReadOnly }]" data-testid="pre-processors-section">
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
                  <div :class="['request-section', { 'case-drawer-readonly-section': caseDrawerReadOnly }]" data-testid="post-processors-section">
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
                  <div :class="['request-section', { 'case-drawer-readonly-section': caseDrawerReadOnly }]" data-testid="assertions-section">
                    <ApiAssertionEditor
                      v-model="caseDrawerForm.assertions"
                      v-model:active-id="activeAssertionId"
                      :latest-response="caseDrawerResponseStep?.response ?? null"
                    />
                    <div v-if="!caseDrawerReadOnly" class="editor-actions left">
                      <el-button text type="primary" @click="openBatchAddDrawer('assertion', 'case')">批量添加</el-button>
                    </div>
                  </div>
                </template>

                <template v-else-if="caseDrawerRequestTab === 'auth'">
                  <div class="request-section">
                    <div class="ms-auth-panel">
                      <div class="ms-auth-panel-title">认证方式</div>
                      <el-radio-group v-model="caseDrawerForm.requestConfig.authConfig.authType" :disabled="caseDrawerReadOnly" class="ms-auth-radio-group">
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
                              :disabled="caseDrawerReadOnly"
                              placeholder="username"
                              class="ms-auth-form-control"
                            />
                        </div>
                        <div class="ms-auth-form-item">
                          <label class="ms-auth-form-label">Password</label>
                            <el-input
                              v-model="caseDrawerForm.requestConfig.authConfig.basicAuth.password"
                              :disabled="caseDrawerReadOnly"
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
                              :disabled="caseDrawerReadOnly"
                              placeholder="username"
                              class="ms-auth-form-control"
                            />
                        </div>
                        <div class="ms-auth-form-item">
                          <label class="ms-auth-form-label">Password</label>
                            <el-input
                              v-model="caseDrawerForm.requestConfig.authConfig.digestAuth.password"
                              :disabled="caseDrawerReadOnly"
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
                      <el-input v-model="caseDrawerForm.name" :disabled="caseDrawerReadOnly" class="ms-like-form-control" placeholder="接口名称" />
                    </div>
                    <div class="ms-like-form-row">
                      <div class="ms-like-form-label">模块 / 目录</div>
                      <el-input v-model="caseDrawerForm.directoryName" :disabled="caseDrawerReadOnly" class="ms-like-form-control" placeholder="模块 / 目录" />
                    </div>
                    <div class="ms-like-form-row">
                      <div class="ms-like-form-label">标签</div>
                      <el-input
                        :model-value="readTagInput(caseDrawerForm.tags)"
                        :disabled="caseDrawerReadOnly"
                        class="ms-like-form-control"
                        placeholder="标签，逗号分隔"
                        @update:model-value="(value: string | number) => updateTagInput(caseDrawerForm, String(value))"
                      />
                    </div>
                    <div class="ms-like-form-row">
                      <div class="ms-like-form-label">超时时间</div>
                      <el-input-number v-model="caseDrawerForm.requestConfig.timeoutMs" :disabled="caseDrawerReadOnly" :min="1000" :step="1000" class="ms-like-form-control full-width" />
                    </div>
                    <div class="ms-like-form-row align-start">
                      <div class="ms-like-form-label">描述</div>
                      <el-input v-model="caseDrawerForm.description" :disabled="caseDrawerReadOnly" class="ms-like-form-control" type="textarea" :rows="4" placeholder="接口描述、调用约束或备注" />
                    </div>
                    <div class="ms-like-settings-hint">
                      <span>写入空间 {{ currentDefinitionWorkspaceLabel }}</span>
                      <span>调试上下文 {{ currentEnvironmentName }} / {{ currentVariableSetName }}</span>
                      <span>最后运行 {{ formatTimeLabel(caseDrawerForm.lastRunAt) }}</span>
                    </div>
                  </div>
                </template>
              </div>
              <div v-else-if="caseDrawerViewTab === 'runHistory'" class="case-drawer-history-panel">
                <div v-if="caseDrawerHistoryView === 'list'" class="case-drawer-history-list-shell">
                  <div v-if="caseDrawerRunHistoryLoading" class="case-drawer-history-loading">加载中...</div>
                  <el-table
                    v-else
                    :data="caseDrawerRunHistoryItems"
                    size="small"
                    class="case-drawer-history-table"
                    row-key="id"
                    highlight-current-row
                    @row-click="handleCaseDrawerHistoryRowClick"
                  >
                    <el-table-column label="执行时间" min-width="162" show-overflow-tooltip>
                      <template #default="{ row }">
                        {{ formatTimeLabel(row.createdAt) }}
                      </template>
                    </el-table-column>
                    <el-table-column label="结果" width="78">
                      <template #default="{ row }">
                        <span :class="['case-drawer-history-result', `is-${getCaseRunResultPresentation(row.result).tone}`]">
                          {{ getCaseRunResultPresentation(row.result).label }}
                        </span>
                      </template>
                    </el-table-column>
                    <el-table-column label="状态码" width="78" align="center">
                      <template #default="{ row }">
                        <span :class="['ms-like-response-metric', `is-${getStatusTone(row.statusCode)}`]">
                          {{ row.statusCode ?? '-' }}
                        </span>
                      </template>
                    </el-table-column>
                    <el-table-column label="耗时" width="92">
                      <template #default="{ row }">
                        {{ row.durationMs ?? '-' }}<template v-if="row.durationMs != null"> ms</template>
                      </template>
                    </el-table-column>
                    <el-table-column label="大小" width="92">
                      <template #default="{ row }">
                        {{ formatStoredResponseSize(row.responseSize) }}
                      </template>
                    </el-table-column>
                    <el-table-column label="环境" min-width="108" show-overflow-tooltip>
                      <template #default="{ row }">
                        {{ row.environmentName || '默认' }}
                      </template>
                    </el-table-column>
                    <el-table-column label="变量集" min-width="108" show-overflow-tooltip>
                      <template #default="{ row }">
                        {{ row.variableSetName || '未选择' }}
                      </template>
                    </el-table-column>
                    <el-table-column label="执行人" min-width="96" show-overflow-tooltip>
                      <template #default="{ row }">
                        {{ row.operator || '-' }}
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="88" align="center" header-align="center">
                      <template #default="{ row }">
                        <el-button text type="primary" size="small" class="case-drawer-history-detail-button" @click.stop="void selectCaseDrawerRunHistory(row.id)">
                          查看详情
                        </el-button>
                      </template>
                    </el-table-column>
                    <template #empty>
                      <div class="case-drawer-history-table-empty">暂无执行历史</div>
                    </template>
                  </el-table>
                </div>

                <div v-else class="ms-like-response-shell case-drawer-history-detail-shell">
                  <div class="ms-like-response-header">
                    <div class="case-drawer-history-detail-title">
                      <el-button text size="small" class="case-drawer-history-back-button" @click="backToCaseDrawerHistoryList">← 执行历史</el-button>
                    </div>
                    <div class="case-drawer-history-detail-head-right">
                      <div v-if="caseDrawerRunHistoryDetail" class="ms-like-response-metrics">
                        <span :class="['ms-like-result-pill', `is-${caseDrawerSelectedHistoryResult}`]">
                          {{ caseDrawerSelectedHistoryResultLabel }}
                        </span>
                        <span :class="['ms-like-response-metric', `is-${caseDrawerSelectedHistoryStatusTone}`]">状态 {{ caseDrawerSelectedHistoryStatusCode ?? '-' }}</span>
                        <span :class="['ms-like-response-metric', `is-${caseDrawerSelectedHistoryDurationTone}`]">耗时 {{ caseDrawerSelectedHistoryDuration ?? '-' }}<template v-if="caseDrawerSelectedHistoryDuration !== null"> ms</template></span>
                        <span>大小 {{ caseDrawerSelectedHistorySize }}</span>
                      </div>
                    </div>
                  </div>

                  <div v-if="caseDrawerRunHistoryDetail" class="case-drawer-history-meta">
                    <span>执行环境 {{ caseDrawerRunHistoryDetail.environmentName || '默认' }}</span>
                    <span>变量集 {{ caseDrawerRunHistoryDetail.variableSetName || '未选择' }}</span>
                    <span>执行人 {{ caseDrawerRunHistoryDetail.operator || '-' }}</span>
                  </div>

                  <div v-if="caseDrawerRunHistoryDetailLoading" class="case-drawer-history-loading">加载中...</div>
                  <div v-else-if="!caseDrawerRunHistoryDetail" class="case-drawer-history-empty">选择一条执行记录查看详情</div>
                  <template v-else>
                    <div class="case-drawer-history-section">
                      <div class="case-drawer-history-section-title">实际请求</div>
                      <div class="ms-like-response-tabs case-drawer-history-request-tabs">
                        <button
                          v-if="caseDrawerSelectedHistoryRequestHeaders && Object.keys(caseDrawerSelectedHistoryRequestHeaders).length"
                          :class="['ms-like-top-tab', { active: caseDrawerHistoryRequestPreviewTab === 'header' }]"
                          @click="caseDrawerHistoryRequestPreviewTab = 'header'"
                        >
                          Header
                        </button>
                        <button
                          v-if="caseDrawerSelectedHistoryRequestBody || caseDrawerSelectedHistoryRequestQueryParams.length || caseDrawerSelectedHistoryRequestCookies.length || caseDrawerSelectedHistoryRequestBodyFormItems.length || caseDrawerSelectedHistoryRequestBodyType"
                          :class="['ms-like-top-tab', { active: caseDrawerHistoryRequestPreviewTab === 'body' }]"
                          @click="caseDrawerHistoryRequestPreviewTab = 'body'"
                        >
                          Body
                        </button>
                      </div>
                      <div class="ms-like-response-body case-drawer-history-request-body">
                        <template v-if="caseDrawerHistoryRequestPreviewTab === 'header'">
                          <MonacoCodeEditor
                            :model-value="caseDrawerHistoryRequestHeadersPreview"
                            language="json"
                            :read-only="true"
                            :show-format-button="false"
                            :fit-content="true"
                            :max-fit-content-height="1000"
                            height="100%"
                          />
                        </template>
                        <template v-else>
                          <div v-if="caseDrawerSelectedHistoryRequestQueryParams.length" class="case-drawer-history-request-block">
                            <div class="case-drawer-history-request-label">Query</div>
                            <MonacoCodeEditor
                              :model-value="caseDrawerHistoryRequestQueryParamsPreview"
                              language="json"
                              :read-only="true"
                              :show-format-button="false"
                              :fit-content="true"
                              :max-fit-content-height="1000"
                              height="100%"
                            />
                          </div>
                          <div v-if="caseDrawerSelectedHistoryRequestCookies.length" class="case-drawer-history-request-block">
                            <div class="case-drawer-history-request-label">Cookies</div>
                            <MonacoCodeEditor
                              :model-value="caseDrawerHistoryRequestCookiesPreview"
                              language="json"
                              :read-only="true"
                              :show-format-button="false"
                              :fit-content="true"
                              :max-fit-content-height="1000"
                              height="100%"
                            />
                          </div>
                          <div v-if="caseDrawerSelectedHistoryRequestBodyFormItems.length" class="case-drawer-history-request-block">
                            <div class="case-drawer-history-request-label">Body Form</div>
                            <MonacoCodeEditor
                              :model-value="caseDrawerHistoryRequestBodyFormItemsPreview"
                              language="json"
                              :read-only="true"
                              :show-format-button="false"
                              :fit-content="true"
                              :max-fit-content-height="1000"
                              height="100%"
                            />
                          </div>
                          <div v-if="caseDrawerSelectedHistoryRequestBodyFileName" class="case-drawer-history-request-block">
                            <div class="case-drawer-history-request-label">Body File</div>
                            <div class="case-drawer-history-request-value">
                              {{ caseDrawerSelectedHistoryRequestBodyFileName }}
                              <template v-if="caseDrawerSelectedHistoryRequestBodyFileContentType">
                                ({{ caseDrawerSelectedHistoryRequestBodyFileContentType }})
                              </template>
                            </div>
                          </div>
                          <div class="case-drawer-history-request-block">
                            <div v-if="!caseDrawerHistoryRequestBodyPreview" class="case-drawer-history-empty case-drawer-history-request-empty">本次请求未发送请求体</div>
                            <MonacoCodeEditor
                              v-else
                              :model-value="caseDrawerHistoryRequestBodyPreview"
                              :language="caseDrawerHistoryRequestBodyLanguage"
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

                    <div class="case-drawer-history-section">
                      <div class="case-drawer-history-section-title">响应结果</div>
                      <div v-if="caseDrawerSelectedHistoryError" class="response-error-banner">
                        {{ caseDrawerSelectedHistoryError }}
                      </div>
                      <div class="ms-like-response-tabs">
                        <button :class="['ms-like-top-tab', { active: caseDrawerHistoryPreviewTab === 'body' }]" @click="caseDrawerHistoryPreviewTab = 'body'">Body</button>
                        <button :class="['ms-like-top-tab', { active: caseDrawerHistoryPreviewTab === 'header' }]" @click="caseDrawerHistoryPreviewTab = 'header'">Header</button>
                        <button :class="['ms-like-top-tab', { active: caseDrawerHistoryPreviewTab === 'console' }]" @click="caseDrawerHistoryPreviewTab = 'console'">控制台</button>
                        <button :class="['ms-like-top-tab', { active: caseDrawerHistoryPreviewTab === 'assertions' }]" @click="caseDrawerHistoryPreviewTab = 'assertions'">断言</button>
                      </div>

                      <div class="ms-like-response-body">
                        <MonacoCodeEditor
                          v-if="caseDrawerHistoryPreviewTab === 'body'"
                          :model-value="caseDrawerHistoryBodyPreview"
                          :language="caseDrawerHistoryBodyLanguage"
                          :read-only="true"
                          :show-format-button="false"
                          :fit-content="true"
                          :max-fit-content-height="1000"
                          height="100%"
                        />
                        <MonacoCodeEditor
                          v-else-if="caseDrawerHistoryPreviewTab === 'header'"
                          :model-value="caseDrawerHistoryHeadersPreview"
                          language="json"
                          :read-only="true"
                          :show-format-button="false"
                          :fit-content="true"
                          :max-fit-content-height="1000"
                          height="100%"
                        />
                        <MonacoCodeEditor
                          v-else-if="caseDrawerHistoryPreviewTab === 'console'"
                          :model-value="caseDrawerHistoryConsolePreview"
                          language="text"
                          :read-only="true"
                          :show-format-button="false"
                          :fit-content="true"
                          :max-fit-content-height="1000"
                          height="100%"
                        />
                        <div v-else class="assertion-result-panel">
                          <div v-if="!caseDrawerSelectedHistoryAssertionResults.length" class="assertion-result-empty">当前请求未配置断言</div>
                          <el-table v-else :data="caseDrawerSelectedHistoryAssertionResults" size="small" class="assertion-result-table">
                            <el-table-column label="断言名称" min-width="140" show-overflow-tooltip>
                              <template #default="{ row }">{{ row.name || assertionTypeLabel(row.type) }}</template>
                            </el-table-column>
                            <el-table-column label="断言对象" width="96">
                              <template #default="{ row }">{{ assertionTypeLabel(row.type) }}</template>
                            </el-table-column>
                            <el-table-column label="条件" width="92">
                              <template #default="{ row }">{{ assertionConditionLabel(row.condition) }}</template>
                            </el-table-column>
                            <el-table-column label="期望值" min-width="120" show-overflow-tooltip>
                              <template #default="{ row }">{{ row.expectedValue || '-' }}</template>
                            </el-table-column>
                            <el-table-column label="实际值" min-width="120" show-overflow-tooltip>
                              <template #default="{ row }">{{ row.actualValue || '-' }}</template>
                            </el-table-column>
                            <el-table-column label="结果" width="78">
                              <template #default="{ row }">
                                <span :class="['case-drawer-history-result', `is-${assertionResultTone(row)}`]">{{ assertionResultLabel(row) }}</span>
                              </template>
                            </el-table-column>
                            <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
                              <template #default="{ row }">{{ row.success ? '-' : row.message || '-' }}</template>
                            </el-table-column>
                          </el-table>
                        </div>
                      </div>
                    </div>
                  </template>
                </div>
              </div>
              <div v-else class="case-drawer-history-panel">
                <div v-if="caseDrawerChangeHistoryLoading" class="case-drawer-history-loading">加载中...</div>
                <el-table
                  v-else
                  :data="caseDrawerChangeHistoryItems"
                  size="small"
                  class="case-drawer-history-table case-drawer-change-history-table"
                  row-key="id"
                >
                  <el-table-column label="时间" min-width="162" show-overflow-tooltip>
                    <template #default="{ row }">
                      {{ formatTimeLabel(row.createdAt) }}
                    </template>
                  </el-table-column>
                  <el-table-column label="动作" width="92" align="center">
                    <template #default="{ row }">
                      <span class="case-drawer-change-history-action">{{ row.changeType }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作人" min-width="110" show-overflow-tooltip>
                    <template #default="{ row }">
                      {{ row.operatorName || '-' }}
                    </template>
                  </el-table-column>
                  <el-table-column label="摘要" min-width="240" show-overflow-tooltip>
                    <template #default="{ row }">
                      {{ row.changeSummary || '-' }}
                    </template>
                  </el-table-column>
                  <template #empty>
                    <div class="case-drawer-history-table-empty">暂无变更历史</div>
                  </template>
                </el-table>
              </div>
            </template>

            <template #response>
              <div v-if="caseDrawerViewTab === 'detail'" class="ms-like-response-shell case-drawer-response-shell">
                <div class="ms-like-response-header">
                  <div class="ms-like-response-title">响应内容</div>
                  <div v-if="!caseDrawerShowResponseEmptyState" class="ms-like-response-metrics">
                    <span v-if="caseDrawerAssertionResultPresentation.visible" :class="['ms-like-result-pill', `is-${caseDrawerAssertionResultPresentation.tone}`]">
                      {{ caseDrawerAssertionResultPresentation.label }}
                    </span>
                    <span :class="['ms-like-response-metric', `is-${caseDrawerResponseStatusTone}`]">状态 {{ caseDrawerResponseStatusCode ?? '-' }}</span>
                    <span :class="['ms-like-response-metric', `is-${caseDrawerResponseDurationTone}`]">耗时 {{ caseDrawerResponseDuration ?? '-' }}<template v-if="caseDrawerResponseDuration !== null"> ms</template></span>
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
                    <div class="ms-like-response-empty-text">点击 <span>{{ caseDrawerPrimaryActionLabel }}</span> 获取响应内容</div>
                  </div>
                </div>
                <template v-else>
                  <div class="ms-like-response-tabs">
                    <button :class="['ms-like-top-tab', { active: caseDrawerResponsePreviewTab === 'body' }]" @click="caseDrawerResponsePreviewTab = 'body'">Body</button>
                    <button :class="['ms-like-top-tab', { active: caseDrawerResponsePreviewTab === 'header' }]" @click="caseDrawerResponsePreviewTab = 'header'">Header</button>
                    <button :class="['ms-like-top-tab', { active: caseDrawerResponsePreviewTab === 'console' }]" @click="caseDrawerResponsePreviewTab = 'console'">控制台</button>
                    <button :class="['ms-like-top-tab', { active: caseDrawerResponsePreviewTab === 'actualRequest' }]" @click="caseDrawerResponsePreviewTab = 'actualRequest'">实际请求</button>
                    <button :class="['ms-like-top-tab', { active: caseDrawerResponsePreviewTab === 'assertions' }]" @click="caseDrawerResponsePreviewTab = 'assertions'">断言</button>
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
                    <div v-else class="assertion-result-panel">
                      <div v-if="!caseDrawerAssertionResults.length" class="assertion-result-empty">当前请求未配置断言</div>
                      <el-table v-else :data="caseDrawerAssertionResults" size="small" class="assertion-result-table">
                        <el-table-column label="断言名称" min-width="140" show-overflow-tooltip>
                          <template #default="{ row }">{{ row.name || assertionTypeLabel(row.type) }}</template>
                        </el-table-column>
                        <el-table-column label="断言对象" width="96">
                          <template #default="{ row }">{{ assertionTypeLabel(row.type) }}</template>
                        </el-table-column>
                        <el-table-column label="条件" width="92">
                          <template #default="{ row }">{{ assertionConditionLabel(row.condition) }}</template>
                        </el-table-column>
                        <el-table-column label="期望值" min-width="120" show-overflow-tooltip>
                          <template #default="{ row }">{{ row.expectedValue || '-' }}</template>
                        </el-table-column>
                        <el-table-column label="实际值" min-width="120" show-overflow-tooltip>
                          <template #default="{ row }">{{ row.actualValue || '-' }}</template>
                        </el-table-column>
                        <el-table-column label="结果" width="78">
                          <template #default="{ row }">
                            <span :class="['case-drawer-history-result', `is-${assertionResultTone(row)}`]">{{ assertionResultLabel(row) }}</span>
                          </template>
                        </el-table-column>
                        <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
                          <template #default="{ row }">{{ row.success ? '-' : row.message || '-' }}</template>
                        </el-table-column>
                      </el-table>
                    </div>
                  </div>
                </template>
              </div>
            </template>
          </ApiCaseDrawer>
        </div>
      </el-tab-pane>

      <el-tab-pane label="场景" name="scenarios">
        <div class="scenario-workbench ms-scenario-workbench">
          <aside class="scenario-module-pane">
            <div class="ms-like-sidebar-tools">
              <el-button v-if="canCreateInCurrentScope" type="primary" class="scenario-sidebar-primary" @click="resetScenarioForm">
                <LucidePlus class="scenario-sidebar-primary-icon" />
                新建场景
              </el-button>
              <el-input v-model="scenarioModuleKeyword" class="scenario-sidebar-search" placeholder="搜索模块或场景名称" clearable>
                <template #prefix>
                  <LucideSearch class="scenario-sidebar-search-icon" />
                </template>
              </el-input>
            </div>
            <div class="ms-like-directory-shell">
              <div class="scenario-directory-title-row">
                <div class="scenario-directory-title-main">
                  <span>场景目录</span>
                  <small>{{ scenarios.length }}</small>
                </div>
                <div class="scenario-directory-title-actions">
                  <button
                    type="button"
                    class="scenario-directory-collapse-button"
                    title="收起全部子模块"
                    @click.stop="collapseAllScenarioModuleTreeChildren"
                  >
                    <el-icon class="tree-collapse-icon"><Fold /></el-icon>
                  </button>
                </div>
              </div>
              <el-tree
                :key="scenarioModuleTreeRenderKey"
                :data="scenarioModuleTree"
                node-key="key"
                :default-expanded-keys="expandedScenarioModuleTreeKeys"
                highlight-current
                :expand-on-click-node="false"
                :current-node-key="selectedScenarioModuleTreeKey"
                class="ms-like-directory-tree scenario-module-tree"
                @current-change="handleScenarioModuleSelect"
                @node-expand="handleScenarioModuleTreeExpand"
                @node-collapse="handleScenarioModuleTreeCollapse"
              >
                <template #default="{ data }">
                  <div :class="['ms-like-directory-node', { 'is-root': data.type === 'root' }]">
                    <div class="ms-like-directory-main">
                      <span
                        v-if="data.type === 'workspace' || data.type === 'module'"
                        :class="['tree-node-folder-svg', { 'is-open': isScenarioModuleTreeExpanded(data.key) }]"
                        aria-hidden="true"
                      >
                        <LucideFolderOpen v-if="isScenarioModuleTreeExpanded(data.key)" class="tree-node-folder-icon" />
                        <LucideFolder v-else class="tree-node-folder-icon" />
                      </span>
                      <span v-overflow-title="data.name" class="ms-like-directory-label">{{ data.name }}</span>
                      <span class="ms-like-directory-count">{{ data.scenarioCount }}</span>
                    </div>
                    <div class="ms-like-directory-actions" @click.stop>
                      <el-button
                        v-if="(data.type === 'workspace' || data.type === 'module') && canWriteWorkspace(data.workspaceCode)"
                        text
                        class="tree-icon-button"
                        title="新建子模块"
                        @click.stop="createScenarioModule(data.id, data.workspaceCode)"
                      >
                        <el-icon><Plus /></el-icon>
                      </el-button>
                      <el-dropdown
                        v-if="data.type === 'module' && canWriteWorkspace(data.workspaceCode)"
                        trigger="click"
                        popper-class="definition-tree-action-menu"
                        @command="(command: string | number | object) => command === 'rename' ? renameScenarioModule(data) : deleteScenarioModule(data)"
                      >
                        <el-button
                          text
                          class="tree-icon-button definition-tree-more-button"
                          title="更多操作"
                          aria-label="更多操作"
                          @click.stop
                        >
                          <el-icon><MoreFilled /></el-icon>
                        </el-button>
                        <template #dropdown>
                          <el-dropdown-menu>
                            <el-dropdown-item command="rename" class="definition-tree-action-item">重命名</el-dropdown-item>
                            <el-dropdown-item command="delete" class="definition-tree-action-item definition-tree-action-danger">删除</el-dropdown-item>
                          </el-dropdown-menu>
                        </template>
                      </el-dropdown>
                    </div>
                  </div>
                </template>
              </el-tree>
            </div>
          </aside>

          <main class="scenario-main-pane">
            <div class="ms-like-tab-strip scenario-editor-tab-strip">
              <div class="ms-like-tab-strip-main">
                <button
                  v-if="scenarioTabOverflow.overflow"
                  type="button"
                  class="ms-like-tab-scroll-button"
                  :disabled="scenarioTabOverflow.arrivedLeft"
                  aria-label="向左滚动标签"
                  @click="scrollTabStrip(scenarioTabNavRef, scenarioTabOverflow, 'left')"
                >
                  <el-icon><ArrowLeft /></el-icon>
                </button>
                <div ref="scenarioTabNavRef" class="ms-like-tab-nav">
                  <button
                    v-for="tab in scenarioEditorTabs"
                    :key="tab.key"
                    type="button"
                    :class="['ms-like-editor-tab', { active: tab.key === activeScenarioEditorKey }]"
                    @click="activateScenarioEditorTab(tab.key)"
                  >
                    <span class="ms-like-editor-tab-label">{{ tab.title }}</span>
                    <span v-if="tab.isDirty" class="ms-like-editor-tab-dot"></span>
                    <span
                      v-if="tab.key !== 'scenario-list'"
                      class="ms-like-editor-tab-close"
                      @click.stop="closeScenarioEditorTab(tab.key)"
                    >
                      <el-icon><Close /></el-icon>
                    </span>
                  </button>
                </div>
                <button
                  v-if="scenarioTabOverflow.overflow"
                  type="button"
                  class="ms-like-tab-scroll-button"
                  :disabled="scenarioTabOverflow.arrivedRight"
                  aria-label="向右滚动标签"
                  @click="scrollTabStrip(scenarioTabNavRef, scenarioTabOverflow, 'right')"
                >
                  <el-icon><ArrowRight /></el-icon>
                </button>
                <button type="button" class="ms-like-tab-add" @click="resetScenarioForm()">+</button>
                <el-dropdown
                  v-if="showScenarioEditorMoreAction"
                  trigger="click"
                  placement="bottom-start"
                  @command="handleScenarioEditorMoreAction"
                >
                  <button type="button" class="scenario-editor-more-button" aria-label="更多标签操作" @click.stop>
                    <el-icon><MoreFilled /></el-icon>
                  </button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="closeOthers">关闭其他标签</el-dropdown-item>
                      <el-dropdown-item command="closeAll">关闭全部标签</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
            <el-tabs
              v-model="activeScenarioEditorKey"
              class="scenario-editor-tabs"
              @tab-change="handleScenarioTabChange"
              @tab-remove="closeScenarioEditorTab"
            >
              <el-tab-pane
                v-for="tab in scenarioEditorTabs"
                :key="tab.key"
                :label="tab.title"
                :name="tab.key"
                :closable="tab.key !== 'scenario-list'"
              >
                <template v-if="tab.key === 'scenario-list'">
                  <div class="ms-scenario-list-shell">
                    <div class="ms-scenario-list-toolbar">
                      <div class="ms-scenario-search">
                        <el-input v-model="scenarioFilters.keyword" placeholder="通过 ID/名称/标签搜索" clearable>
                          <template #suffix>
                            <el-icon><Search /></el-icon>
                          </template>
                        </el-input>
                      </div>
                      <el-select v-model="scenarioViewMode" class="ms-scenario-view-select">
                        <el-option label="全部数据" value="ALL" />
                      </el-select>
                      <el-button class="ms-scenario-tool-button" @click="ElMessage.info('高级筛选后续接入')">筛选</el-button>
                      <el-button class="ms-scenario-icon-button" @click="refreshData">
                        <el-icon><RefreshRight /></el-icon>
                      </el-button>
                    </div>
                    <el-table :data="filteredScenarios" size="small" class="scenario-table ms-scenario-table">
                    <el-table-column type="selection" width="44" />
                    <el-table-column width="34">
                      <template #default>??</template>
                    </el-table-column>
                    <el-table-column label="ID" width="120" sortable>
                      <template #default="{ row }">
                        <button type="button" class="scenario-link" @click="selectScenario(row.id)">{{ 100000 + row.id }}</button>
                      </template>
                    </el-table-column>
                    <el-table-column label="场景名称" min-width="180" sortable show-overflow-tooltip>
                      <template #default="{ row }">
                        <button type="button" class="ms-scenario-name-link" @click="selectScenario(row.id)">{{ row.name }}</button>
                      </template>
                    </el-table-column>
                    <el-table-column label="场景等级" width="120" sortable>
                      <template #default="{ row }">
                        <span class="ms-scenario-priority"><i></i>{{ row.priority || 'P1' }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="状态" width="120">
                      <template #header>
                        <span>状态 <span class="ms-scenario-filter-icon">⊥</span></span>
                      </template>
                      <template #default="{ row }">
                        <span class="ms-scenario-status">{{ scenarioStatusLabel(row.status) }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="执行结果" width="140">
                      <template #header>
                        <span>执行结果 <span class="ms-scenario-filter-icon">⊥</span></span>
                      </template>
                      <template #default="{ row }">
                        {{ row.lastRunResult || '-' }}
                      </template>
                    </el-table-column>
                    <el-table-column label="标签" min-width="140">
                      <template #default="{ row }">{{ row.tags?.length ? row.tags.join(', ') : '-' }}</template>
                    </el-table-column>
                    <el-table-column label="场景环境" min-width="140">
                      <template #default="{ row }">
                        {{ environments.find(item => item.id === row.defaultEnvironmentId)?.name || '-' }}
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="210" fixed="right">
                      <template #header>
                        <span>操作 <el-icon><Setting /></el-icon></span>
                      </template>
                      <template #default="{ row }">
                        <button type="button" class="ms-scenario-action" @click="selectScenario(row.id)">编辑</button>
                        <button type="button" class="ms-scenario-action" @click="runScenarioFromList(row.id)">执行</button>
                        <button type="button" class="ms-scenario-action" @click="copyScenario(row)">复制</button>
                        <el-dropdown trigger="click">
                          <button type="button" class="ms-scenario-action">...</button>
                          <template #dropdown>
                            <el-dropdown-menu>
                              <el-dropdown-item @click="removeScenarioFromList(row.id)">删除</el-dropdown-item>
                            </el-dropdown-menu>
                          </template>
                        </el-dropdown>
                      </template>
                    </el-table-column>
                  </el-table>
                    <div class="ms-scenario-pagination">
                      <span>共 {{ filteredScenarios.length }} 条</span>
                      <button type="button">?</button>
                      <span class="ms-scenario-page-current">1</span>
                      <button type="button">?</button>
                    </div>
                  </div>
                </template>

                <template v-else>
                  <div class="scenario-edit-workspace">
                    <section class="scenario-edit-main">
                      <div class="scenario-edit-toolbar">
                        <el-tabs v-model="activeScenarioDetailTab" class="scenario-detail-tabs">
                          <el-tab-pane label="步骤" name="steps">
                            <div class="scenario-step-toolbar">
                              <span>共 {{ scenarioFlatSteps.length }} 个步骤</span>
                              <el-dropdown trigger="click" popper-class="scenario-add-step-menu" @command="handleScenarioAddStepAction">
                                <el-button type="primary">
                                  <el-icon><Plus /></el-icon>
                                  添加步骤
                                </el-button>
                                <template #dropdown>
                                  <el-dropdown-menu>
                                    <div class="scenario-add-step-group-title">请求/场景</div>
                                    <el-dropdown-item command="IMPORT_SYSTEM_API">导入系统请求</el-dropdown-item>
                                    <el-dropdown-item command="CUSTOM_REQUEST">自定义请求</el-dropdown-item>
                                    <div class="scenario-add-step-group-title">逻辑控制</div>
                                    <el-dropdown-item command="LOOP_CONTROLLER">循环控制器</el-dropdown-item>
                                    <el-dropdown-item command="IF_CONTROLLER">条件控制器</el-dropdown-item>
                                    <el-dropdown-item command="ONCE_ONLY_CONTROLLER">仅一次控制器</el-dropdown-item>
                                    <div class="scenario-add-step-group-title">其他</div>
                                    <el-dropdown-item command="SCRIPT">脚本操作</el-dropdown-item>
                                    <el-dropdown-item command="CONSTANT_TIMER">等待时间</el-dropdown-item>
                                  </el-dropdown-menu>
                                </template>
                              </el-dropdown>
                            </div>
                            <div v-if="scenarioFlatSteps.length" class="scenario-step-tree">
                              <div
                                v-for="(item, index) in scenarioFlatSteps"
                                :key="item.step.id"
                                :class="['scenario-step-node', { 'is-nested': item.level > 0 }]"
                                :style="{ marginLeft: `${item.level * 32}px` }"
                              >
                                <div class="scenario-step-node-left">
                                  <el-checkbox />
                                  <span class="scenario-step-order">{{ index + 1 }}</span>
                                  <el-switch v-model="item.step.enabled" size="small" />
                                  <button type="button" class="scenario-step-run-button" title="执行步骤">
                                    <el-icon><CaretRight /></el-icon>
                                  </button>
                                  <span :class="['scenario-step-type-badge', `is-${String(item.step.stepType || 'API').toLowerCase().replaceAll('_', '-')}`]">
                                    {{ scenarioStepTypeBadgeLabel(item.step.stepType) }}
                                  </span>
                                </div>
                                <div class="scenario-step-node-main">
                                  <template v-if="item.step.stepType === 'API'">
                                    <el-select v-if="!item.step.resourceId" v-model="item.step.resourceId" class="scenario-step-resource-select" placeholder="选择接口">
                                      <el-option v-for="option in definitionOptions" :key="option.value" :label="option.label" :value="option.value" />
                                    </el-select>
                                    <span :class="['scenario-step-method', requestMethodClass(definitions.find(definition => definition.id === item.step.resourceId)?.method || 'GET')]">{{ definitions.find(definition => definition.id === item.step.resourceId)?.method || 'HTTP' }}</span>
                                  </template>
                                  <template v-else-if="item.step.stepType === 'API_CASE'">
                                    <el-select v-if="!item.step.resourceId" v-model="item.step.resourceId" class="scenario-step-resource-select" placeholder="选择用例">
                                      <el-option v-for="option in caseOptions" :key="option.value" :label="option.label" :value="option.value" />
                                    </el-select>
                                    <span :class="['scenario-step-method', requestMethodClass(apiCases.find(apiCase => apiCase.id === item.step.resourceId)?.method || 'GET')]">{{ apiCases.find(apiCase => apiCase.id === item.step.resourceId)?.method || 'HTTP' }}</span>
                                  </template>
                                  <template v-else-if="item.step.stepType === 'API_SCENARIO'">
                                    <el-select v-if="!item.step.resourceId" v-model="item.step.resourceId" class="scenario-step-resource-select" placeholder="选择场景">
                                      <el-option v-for="option in scenarios.filter(scene => scene.id !== scenarioForm.id)" :key="option.id" :label="option.name" :value="option.id" />
                                    </el-select>
                                  </template>
                                  <template v-else-if="item.step.stepType === 'CUSTOM_REQUEST' && item.step.requestConfig">
                                    <span :class="['scenario-step-method', requestMethodClass(item.step.requestConfig.method || 'GET')]">{{ item.step.requestConfig.method || 'GET' }}</span>
                                  </template>
                                  <template v-else-if="item.step.stepType === 'LOOP_CONTROLLER'">
                                    <el-select v-model="item.step.loopType" class="scenario-step-method-select">
                                      <el-option label="固定次数" value="FIXED" />
                                      <el-option label="While 条件" value="WHILE" />
                                      <el-option label="Foreach" value="FOREACH" />
                                    </el-select>
                                    <el-input-number v-if="item.step.loopType === 'FIXED'" v-model="item.step.loopCount" :min="0" :max="50" size="small" />
                                    <el-input v-else-if="item.step.loopType === 'FOREACH'" v-model="item.step.foreachExpression" class="scenario-step-path-input" placeholder="a,b,c 或 {{items}}" />
                                    <el-input v-else v-model="item.step.conditionExpression" class="scenario-step-path-input" placeholder="{{flag}} == true" />
                                    <span class="scenario-step-inline-label">间隔(ms):</span>
                                    <el-input-number v-model="item.step.delayMs" :min="0" :max="60000" size="small" />
                                  </template>
                                  <template v-else-if="item.step.stepType === 'IF_CONTROLLER'">
                                    <el-input v-model="item.step.conditionExpression" class="scenario-step-condition-input" placeholder="变量名称${var}" />
                                    <el-select v-model="item.step.conditionType" class="scenario-step-operator-select">
                                      <el-option label="等于" value="EXPRESSION" />
                                      <el-option label="脚本" value="SCRIPT" />
                                    </el-select>
                                    <el-input class="scenario-step-condition-input" placeholder="变量值" />
                                  </template>
                                  <template v-else-if="item.step.stepType === 'CONSTANT_TIMER'">
                                    <span class="scenario-step-inline-label">等待(ms):</span>
                                    <el-input-number v-model="item.step.delayMs" :min="1" :max="60000" size="small" />
                                  </template>
                                  <button
                                    v-if="['API', 'API_CASE', 'CUSTOM_REQUEST', 'SCRIPT'].includes(String(item.step.stepType)) && scenarioStepNameEditingId !== item.step.id"
                                    type="button"
                                    class="scenario-step-name-text scenario-step-name-button is-strong"
                                    @click="item.step.stepType === 'API' || item.step.stepType === 'API_CASE' ? openScenarioSystemRequestDrawer(item.step) : item.step.stepType === 'CUSTOM_REQUEST' ? openScenarioCustomRequestDrawer(item.path) : openScenarioScriptDrawer(item.path)"
                                  >
                                    {{ scenarioStepDisplayName(item.step) }}
                                  </button>
                                  <el-input
                                    v-else-if="['API', 'API_CASE', 'CUSTOM_REQUEST', 'SCRIPT'].includes(String(item.step.stepType)) && scenarioStepNameEditingId === item.step.id"
                                    v-model="scenarioStepNameDraft"
                                    class="scenario-step-name-inline-input"
                                    maxlength="255"
                                    @blur="finishScenarioStepNameEdit(item.step)"
                                    @keyup.enter="finishScenarioStepNameEdit(item.step)"
                                  />
                                  <button
                                    v-if="['API', 'API_CASE', 'CUSTOM_REQUEST', 'SCRIPT'].includes(String(item.step.stepType)) && scenarioStepNameEditingId !== item.step.id"
                                    type="button"
                                    class="scenario-step-name-edit-button"
                                    title="编辑名称"
                                    @click.stop="startScenarioStepNameEdit(item.step)"
                                  >
                                    <el-icon><EditPen /></el-icon>
                                  </button>
                                  <span
                                    v-if="item.step.stepType !== 'CONSTANT_TIMER' && !['API', 'API_CASE', 'CUSTOM_REQUEST', 'SCRIPT'].includes(String(item.step.stepType))"
                                    :class="['scenario-step-name-text', { 'is-strong': item.step.stepType === 'API' || item.step.stepType === 'API_CASE' }]"
                                  >
                                    {{ scenarioStepDisplayName(item.step) }}
                                  </span>
                                </div>
                                <div class="scenario-step-node-actions">
                                  <button v-if="isScenarioControllerStep(item.step.stepType)" type="button" class="scenario-step-icon-action is-text" title="添加子步骤" @click="addScenarioStep(item.path, 'API_CASE')">
                                    <el-icon><Plus /></el-icon>
                                  </button>
                                  <button type="button" class="scenario-step-icon-action" title="上移" @click="moveScenarioStep(item.path, -1)">
                                    <el-icon><ArrowUp /></el-icon>
                                  </button>
                                  <button type="button" class="scenario-step-icon-action" title="下移" @click="moveScenarioStep(item.path, 1)">
                                    <el-icon><ArrowDown /></el-icon>
                                  </button>
                                  <button type="button" class="scenario-step-icon-action is-danger" title="删除" @click="removeScenarioStep(item.path)">
                                    <el-icon><Delete /></el-icon>
                                  </button>
                                </div>
                              </div>
                            </div>
                          </el-tab-pane>
                          <el-tab-pane label="参数" name="params">
                            <div class="section-title">场景变量 <el-button text @click="addScenarioVariable">新增</el-button></div>
                            <div v-for="(row, index) in scenarioForm.scenarioVariables" :key="`scenario-var-${index}`" class="kv-row">
                              <el-input v-model="row.name" placeholder="变量名" />
                              <el-input v-model="row.value" placeholder="变量值" />
                              <el-switch v-model="row.sensitive" active-text="敏感" />
                              <el-button text @click="scenarioForm.scenarioVariables.splice(index, 1)">删除</el-button>
                            </div>
                          </el-tab-pane>
                          <el-tab-pane label="断言" name="assertions">
                            <div class="section-title">场景级断言 <el-button text @click="addScenarioAssertion">新增</el-button></div>
                            <div v-for="(row, index) in scenarioForm.scenarioAssertions" :key="row.id || index" class="scenario-assertion-row">
                              <el-input v-model="row.name" placeholder="断言名称" />
                              <el-select v-model="row.assertionType">
                                <el-option v-for="option in scenarioAssertionTypeOptions" :key="option.value" :label="option.label" :value="option.value" />
                              </el-select>
                              <el-input v-model="row.expectedValue" placeholder="期望值" />
                              <el-switch v-model="row.enabled" />
                              <el-button text @click="scenarioForm.scenarioAssertions.splice(index, 1)">删除</el-button>
                            </div>
                          </el-tab-pane>
                          <el-tab-pane label="执行历史" name="history">
                            <el-table :data="scenarioRunHistorySteps" size="small">
                              <el-table-column prop="stepOrder" label="#" width="64" />
                              <el-table-column prop="stepName" label="步骤" min-width="180" />
                              <el-table-column label="结果" width="90">
                                <template #default="{ row }">
                                  <el-tag size="small" :type="row.success ? 'success' : 'danger'">{{ row.success ? '通过' : '失败' }}</el-tag>
                                </template>
                              </el-table-column>
                              <el-table-column prop="durationMs" label="耗时 ms" width="100" />
                              <el-table-column prop="errorMessage" label="错误信息" min-width="220" show-overflow-tooltip />
                            </el-table>
                          </el-tab-pane>
                          <el-tab-pane label="设置" name="settings">
                            <el-switch v-model="scenarioForm.continueOnFailure" active-text="失败后继续执行" inactive-text="失败即停止" />
                          </el-tab-pane>
                        </el-tabs>
                      </div>
                    </section>
                    <aside class="scenario-property-panel">
                      <div class="scenario-property-card">
                      <div class="scenario-property-header">
                        <el-select
                          v-model="runOptions.environmentId"
                          class="scenario-property-environment-select"
                          placeholder="选择环境"
                          clearable
                        >
                          <el-option v-for="item in environments" :key="item.id" :label="item.name" :value="item.id" />
                        </el-select>
                        <div class="scenario-property-run-actions">
                          <el-button type="primary" class="scenario-property-run-button" :disabled="!scenarioForm.id || !canWriteScenario" :loading="saving" @click="runScenario">
                            <el-icon><CaretRight /></el-icon>
                            执行
                          </el-button>
                          <el-button class="scenario-property-save-button" :disabled="!canWriteScenario" :loading="saving" @click="saveScenario">
                            <el-icon><Check /></el-icon>
                            保存
                          </el-button>
                        </div>
                        <el-button v-if="scenarioForm.id" class="scenario-property-delete-button" :disabled="!canWriteScenario" @click="removeScenario">删除场景</el-button>
                      </div>
                      <div class="scenario-property-body">
                        <label class="scenario-property-field">
                          <span><b>*</b> 场景名称</span>
                          <el-input v-model="scenarioForm.name" placeholder="请输入场景名称" />
                        </label>
                        <label class="scenario-property-field">
                          <span>所属模块</span>
                          <el-select v-model="scenarioForm.moduleId" placeholder="请选择所属模块">
                            <el-option v-for="item in scenarioModuleOptions" :key="item.value" :label="item.label" :value="item.value" />
                          </el-select>
                        </label>
                        <label class="scenario-property-field">
                          <span>场景等级</span>
                          <el-select v-model="scenarioForm.priority" placeholder="请选择场景等级">
                            <el-option v-for="item in scenarioPriorityOptions" :key="item" :label="item" :value="item" />
                          </el-select>
                        </label>
                        <label class="scenario-property-field">
                          <span>场景状态</span>
                          <el-select v-model="scenarioForm.status" placeholder="请选择场景状态">
                            <el-option v-for="item in scenarioStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
                          </el-select>
                        </label>
                        <label class="scenario-property-field">
                          <span>默认环境</span>
                          <el-select v-model="scenarioForm.defaultEnvironmentId" clearable placeholder="请选择默认环境">
                            <el-option v-for="item in environments" :key="item.id" :label="item.name" :value="item.id" />
                          </el-select>
                        </label>
                        <label class="scenario-property-field">
                          <span>变量集</span>
                          <el-select v-model="scenarioForm.variableSetId" clearable placeholder="请选择变量集">
                            <el-option v-for="item in variableSets" :key="item.id" :label="item.name" :value="item.id" />
                          </el-select>
                        </label>
                        <label class="scenario-property-field">
                          <span>标签</span>
                          <el-input
                            :model-value="readTagInput(scenarioForm.tags)"
                            placeholder="添加标签，回车结束"
                            @update:model-value="(value: string | number) => updateTagInput(scenarioForm, String(value))"
                          />
                        </label>
                        <label class="scenario-property-field">
                          <span>描述</span>
                          <el-input v-model="scenarioForm.description" type="textarea" :rows="3" placeholder="请对该场景进行描述" />
                        </label>
                      </div>
                      </div>
                    </aside>
                  </div>
                </template>
              </el-tab-pane>
            </el-tabs>
          </main>
        </div>
      </el-tab-pane>

      <el-tab-pane label="执行" name="execution">
        <div class="execution-workbench">
          <aside class="execution-suite-pane">
            <div class="execution-suite-tools">
              <button type="button" class="execution-primary-button" @click="ElMessage.info('执行套件功能后续接入')">
                <LucidePlus />
                新建套件
              </button>
              <div class="execution-search-box">
                <LucideSearch />
                <input type="text" placeholder="搜索模块或套件名称">
              </div>
            </div>
            <div class="execution-suite-tree">
              <div class="execution-suite-title-row">
                <span>测试套件</span>
                <small>4</small>
              </div>
              <div v-for="workspace in executionSuiteTree" :key="workspace.id" class="execution-suite-group">
                <div class="execution-suite-node is-workspace">
                  <ChevronRight class="execution-suite-chevron is-open" />
                  <LucideFolderOpen class="execution-suite-folder is-open" />
                  <span>{{ workspace.label }}</span>
                  <small>{{ workspace.count }}</small>
                </div>
                <div v-for="group in workspace.children" :key="group.id" class="execution-suite-branch">
                  <div class="execution-suite-node">
                    <ChevronRight class="execution-suite-chevron is-open" />
                    <LucideFolderOpen class="execution-suite-folder is-open" />
                    <span>{{ group.label }}</span>
                    <small>{{ group.count }}</small>
                  </div>
                  <div v-for="suite in group.children" :key="suite.id" class="execution-suite-node is-leaf" :class="{ active: suite.id === 'suite-order-smoke' }">
                    <ChevronRight class="execution-suite-chevron is-placeholder" />
                    <LucideFolder class="execution-suite-folder" />
                    <span>{{ suite.label }}</span>
                    <small>{{ suite.count }}</small>
                  </div>
                </div>
              </div>
            </div>
          </aside>

          <main class="execution-main-pane">
            <div class="execution-suite-tab-strip">
              <button type="button" class="execution-suite-tab active">
                订单冒烟
                <X />
              </button>
              <button type="button" class="execution-tab-icon" @click="ElMessage.info('新增执行套件后续接入')">
                <LucidePlus />
              </button>
              <button type="button" class="execution-tab-icon">
                <MoreHorizontal />
              </button>
            </div>

            <div class="execution-content-row">
              <section class="execution-suite-content">
                <div class="execution-sub-tab-row">
                  <button
                    v-for="(tab, index) in executionSubTabs"
                    :key="tab.key"
                    type="button"
                    :class="['execution-sub-tab', { active: index === 0 }]"
                  >
                    <component :is="tab.icon" />
                    {{ tab.label }}
                  </button>
                </div>

                <div class="execution-suite-header">
                  <div class="execution-suite-name-row">
                    <span class="execution-priority-badge">P1</span>
                    <strong>订单冒烟</strong>
                  </div>
                  <p>覆盖登录、下单、支付回调的核心链路。</p>
                  <div class="execution-suite-meta">
                    <Clock />
                    <span>组织管理部</span>
                    <span>更新于 2 分钟前</span>
                    <span>·</span>
                    <span>更新于 2 分钟前</span>
                  </div>
                </div>

                <div class="execution-case-list">
                  <div class="execution-case-toolbar">
                    <span>共 {{ executionSuiteCases.length }} 个内容</span>
                    <div>
                      <button type="button" @click="ElMessage.info('添加接口用例后续接入')">
                        <LucidePlus />
                        接口用例
                      </button>
                      <button type="button" @click="ElMessage.info('添加场景用例后续接入')">
                        <LucidePlus />
                        场景用例
                      </button>
                    </div>
                  </div>
                  <div
                    v-for="item in executionSuiteCases"
                    :key="item.id"
                    class="execution-case-row"
                  >
                    <GripVertical />
                    <span :class="['execution-case-type', item.type === 'scene' ? 'is-scene' : 'is-api']">
                      <component :is="item.type === 'scene' ? Zap : Link" />
                      {{ item.type === 'scene' ? '场景' : '接口' }}
                    </span>
                    <strong>{{ item.name }}</strong>
                    <template v-if="item.type === 'api'">
                      <span :class="['scenario-step-method', requestMethodClass(item.method)]">{{ item.method }}</span>
                      <code>{{ item.path }}</code>
                    </template>
                    <span v-else class="execution-case-desc">{{ item.description }}</span>
                    <button type="button" class="execution-case-more">
                      <MoreHorizontal />
                    </button>
                  </div>
                </div>

                <div v-if="false" class="execution-recent-panel">
                  <div class="execution-recent-title">最近执行任务</div>
                  <el-table :data="apiTasks.slice(0, 4)" size="small" class="execution-task-table">
                    <el-table-column prop="taskName" label="任务名称" min-width="180" />
                    <el-table-column prop="status" label="状态" width="110" />
                    <el-table-column prop="summary" label="摘要" min-width="180" />
                  </el-table>
                </div>
              </section>

              <aside class="execution-config-panel">
                <div class="execution-config-card">
                  <div class="execution-config-head">
                    <el-select v-model="executionVisualEnvironment" placeholder="开户-UAT" class="execution-config-select">
                      <el-option label="开户-UAT" value="uat" />
                      <el-option label="开户-PRD" value="prd" />
                      <el-option label="个人-DEV" value="dev" />
                    </el-select>
                    <button type="button" class="execution-config-icon">
                      <Settings2 />
                    </button>
                    <div class="execution-run-buttons">
                      <button type="button" class="execution-run-button" @click="ElMessage.info('套件运行功能后续接入')">
                        <Play />
                        运行
                      </button>
                      <button type="button" class="execution-save-button" @click="ElMessage.info('保存套件配置后续接入')">
                        <Save />
                        保存
                      </button>
                    </div>
                  </div>
                  <div class="execution-config-body">
                    <label>
                      <span>运行模式</span>
                      <el-select v-model="executionVisualRunMode">
                        <el-option label="并行运行" value="parallel" />
                        <el-option label="串行运行" value="serial" />
                      </el-select>
                    </label>
                    <label>
                      <span>运行于</span>
                      <el-select v-model="executionVisualRunOn">
                        <el-option label="本地执行机" value="local" />
                        <el-option label="远程执行机" value="remote" />
                      </el-select>
                    </label>
                    <div class="execution-config-switch">
                      <div>
                        <Bell />
                        <span>通知</span>
                      </div>
                      <el-switch v-model="executionVisualNotify" />
                    </div>
                    <div class="execution-config-stats">
                      <div><span>上次运行</span><strong class="is-success">全部通过</strong></div>
                      <div><span>运行时长</span><strong>1m 23s</strong></div>
                      <div><span>用例数</span><strong>{{ executionSuiteCases.length }} 个</strong></div>
                    </div>
                  </div>
                </div>
              </aside>
            </div>
          </main>
        </div>
      </el-tab-pane>

      <el-tab-pane label="报告" name="reports">
        <div class="api-tab-placeholder">
          <div>
            <FileText :size="34" />
            <p>报告 页面建设中...</p>
          </div>
        </div>
        <section v-if="false" class="panel shell-card table-panel">
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
        <div class="api-tab-placeholder">
          <div>
            <Settings2 :size="34" />
            <p>设置 页面建设中...</p>
          </div>
        </div>
        <div v-if="false" class="settings-grid">
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

    <el-drawer
      v-model="scenarioImportDrawerVisible"
      title="导入系统请求"
      size="1200px"
      destroy-on-close
      class="api-soft-drawer scenario-import-drawer"
      @closed="resetScenarioImportSelection"
    >
      <div class="scenario-import-shell">
        <el-tabs v-model="scenarioImportActiveTab" class="scenario-import-tabs" @tab-change="handleScenarioImportTabChange">
          <el-tab-pane label="接口" name="api" />
          <el-tab-pane label="用例" name="case" />
          <el-tab-pane label="场景" name="scenario" />
        </el-tabs>
        <div class="scenario-import-content">
          <aside class="scenario-import-tree-pane">
            <div class="scenario-import-tree-controls">
              <el-select v-model="scenarioImportWorkspaceCode" placeholder="空间" @change="handleScenarioImportWorkspaceChange">
                <el-option v-for="item in scenarioImportWorkspaceOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-if="scenarioImportActiveTab !== 'scenario'" v-model="scenarioImportProtocol" class="scenario-import-protocol">
                <el-option label="HTTP" value="HTTP" />
              </el-select>
            </div>
            <el-input v-model="scenarioImportTreeKeyword" placeholder="输入模块名称搜索" clearable />
            <el-tree
              :data="scenarioImportTree"
              node-key="key"
              highlight-current
              :expand-on-click-node="false"
              :current-node-key="selectedScenarioImportTreeKey"
              class="scenario-import-tree"
              default-expand-all
              @current-change="(data: ScenarioImportTreeNode) => selectedScenarioImportTreeKey = data.key"
            >
              <template #default="{ data }">
                <div class="scenario-import-tree-node">
                  <span class="scenario-import-tree-label">{{ data.label }}</span>
                  <span class="scenario-import-tree-count">{{ data.count }}</span>
                </div>
              </template>
            </el-tree>
          </aside>
          <section class="scenario-import-table-pane">
            <div class="scenario-import-table-toolbar">
              <div class="scenario-import-table-title">
                {{ scenarioImportActiveTab === 'api' ? '全部接口' : scenarioImportActiveTab === 'case' ? '全部用例' : '全部场景' }}
                <span>({{ scenarioImportActiveTab === 'api' ? scenarioImportDefinitions.length : scenarioImportActiveTab === 'case' ? scenarioImportCases.length : scenarioImportScenarios.length }})</span>
              </div>
              <el-input v-model="scenarioImportKeyword" class="scenario-import-search" placeholder="通过路径或名称搜索" clearable>
                <template #suffix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </div>
            <el-table
              v-if="scenarioImportActiveTab === 'api'"
              :data="scenarioImportDefinitions"
              row-key="id"
              height="560"
              size="small"
              @selection-change="handleScenarioImportDefinitionSelection"
            >
              <el-table-column type="selection" width="44" />
              <el-table-column label="ID" width="110">
                <template #default="{ row }">{{ 100000 + row.id }}</template>
              </el-table-column>
              <el-table-column prop="name" label="接口名称" min-width="180" show-overflow-tooltip />
              <el-table-column label="请求类型" width="110">
                <template #default="{ row }">
                  <span :class="['case-drawer-method-tag', `request-method-${String(row.method).toLowerCase()}`]">{{ row.method }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="path" label="路径" min-width="220" show-overflow-tooltip />
              <el-table-column label="状态" width="110">
                <template #default>进行中</template>
              </el-table-column>
            </el-table>
            <el-table
              v-else-if="scenarioImportActiveTab === 'case'"
              :data="scenarioImportCases"
              row-key="id"
              height="560"
              size="small"
              @selection-change="handleScenarioImportCaseSelection"
            >
              <el-table-column type="selection" width="44" />
              <el-table-column label="ID" width="110">
                <template #default="{ row }">{{ 100000 + row.id }}</template>
              </el-table-column>
              <el-table-column prop="name" label="用例名称" min-width="180" show-overflow-tooltip />
              <el-table-column label="请求类型" width="110">
                <template #default="{ row }">
                  <span :class="['case-drawer-method-tag', `request-method-${String(row.method).toLowerCase()}`]">{{ row.method }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="path" label="路径" min-width="220" show-overflow-tooltip />
              <el-table-column label="状态" width="110">
                <template #default>进行中</template>
              </el-table-column>
            </el-table>
            <el-table
              v-else
              :data="scenarioImportScenarios"
              row-key="id"
              height="560"
              size="small"
              @selection-change="handleScenarioImportScenarioSelection"
            >
              <el-table-column type="selection" width="44" />
              <el-table-column label="ID" width="110">
                <template #default="{ row }">{{ 100000 + row.id }}</template>
              </el-table-column>
              <el-table-column prop="name" label="场景名称" min-width="180" show-overflow-tooltip />
              <el-table-column prop="moduleName" label="所属模块" min-width="140" show-overflow-tooltip />
              <el-table-column prop="stepCount" label="步骤数" width="100" />
              <el-table-column label="状态" width="110">
                <template #default="{ row }">{{ scenarioStatusLabel(row.status) }}</template>
              </el-table-column>
            </el-table>
          </section>
        </div>
      </div>
      <template #footer>
        <div class="scenario-import-footer">
          <div class="scenario-import-summary">
            <span>共选择 <strong>{{ scenarioImportSelectedTotal }}</strong></span>
            <span>接口 <strong>{{ scenarioImportSelectedDefinitionIds.length }}</strong></span>
            <span>用例 <strong>{{ scenarioImportSelectedCaseIds.length }}</strong></span>
            <span>场景 <strong>{{ scenarioImportSelectedScenarioIds.length }}</strong></span>
          </div>
          <div class="scenario-import-actions">
            <el-button :disabled="scenarioImportLoading" @click="scenarioImportDrawerVisible = false">取消</el-button>
            <el-button type="primary" :loading="scenarioImportLoading" :disabled="!scenarioImportSelectedTotal" @click="handleScenarioImport('copy')">复制</el-button>
            <el-button type="primary" :loading="scenarioImportLoading" :disabled="!scenarioImportSelectedTotal" @click="handleScenarioImport('quote')">引用</el-button>
          </div>
        </div>
      </template>
    </el-drawer>

    <el-drawer
      v-model="scenarioSystemRequestDrawerVisible"
      size="960px"
      destroy-on-close
      class="api-soft-drawer scenario-step-config-drawer"
      @closed="closeScenarioSystemRequestDrawer"
    >
      <template #header>
        <div class="scenario-drawer-title-row">
          <span class="scenario-drawer-step-order">{{ scenarioSystemRequestStepOrder }}</span>
          <span :class="['scenario-step-type-badge', scenarioSystemRequestDetail?.resourceType === 'case' ? 'is-api-case' : 'is-api']">
            {{ scenarioSystemRequestDetail?.resourceType === 'case' ? '引用用例' : '引用 API' }}
          </span>
          <el-input
            v-if="scenarioSystemRequestTitleEditing"
            v-model="scenarioSystemRequestTitleDraft"
            class="scenario-drawer-title-input"
            maxlength="255"
            placeholder="请输入步骤名称"
            @blur="finishScenarioSystemRequestTitleEdit"
            @keyup.enter="finishScenarioSystemRequestTitleEdit"
          />
          <span v-else class="scenario-drawer-step-title">
            {{ scenarioSystemRequestEditingStep ? scenarioStepDisplayName(scenarioSystemRequestEditingStep) : scenarioSystemRequestDetail?.name || '系统请求' }}
          </span>
          <button v-if="!scenarioSystemRequestTitleEditing" type="button" class="scenario-custom-title-edit" title="编辑名称" @click="startScenarioSystemRequestTitleEdit">
            <el-icon><EditPen /></el-icon>
          </button>
        </div>
      </template>
      <div v-loading="scenarioSystemRequestDrawerLoading" class="scenario-step-config-shell">
        <template v-if="scenarioSystemRequestDetail">
          <div class="scenario-step-config-request-row">
            <el-select model-value="HTTP" class="scenario-step-protocol-select" disabled>
              <el-option label="HTTP" value="HTTP" />
            </el-select>
            <span :class="['scenario-step-method scenario-system-request-method', requestMethodClass(scenarioSystemRequestConfig.method)]">{{ scenarioSystemRequestConfig.method }}</span>
            <el-input :model-value="scenarioSystemRequestConfig.path" class="request-url-input" readonly />
            <el-button
              type="primary"
              :loading="scenarioSystemRequestDebugLoading"
              :disabled="!canDebugScenarioSystemRequest"
              @click="debugScenarioSystemRequest"
            >
              发送
            </el-button>
          </div>
          <div class="ms-like-top-tabs scenario-step-config-tabs">
            <button :class="['ms-like-top-tab', { active: scenarioSystemRequestActiveTab === 'headers' }]" @click="scenarioSystemRequestActiveTab = 'headers'">请求头</button>
            <button :class="['ms-like-top-tab', { active: scenarioSystemRequestActiveTab === 'body' }]" @click="scenarioSystemRequestActiveTab = 'body'">请求体</button>
            <button :class="['ms-like-top-tab', { active: scenarioSystemRequestActiveTab === 'params' }]" @click="scenarioSystemRequestActiveTab = 'params'">
              Params
              <span v-if="scenarioSystemRequestQueryEnabledCount" class="ms-like-tab-badge">{{ scenarioSystemRequestQueryEnabledCount }}</span>
            </button>
            <button :class="['ms-like-top-tab', { active: scenarioSystemRequestActiveTab === 'auth' }]" @click="scenarioSystemRequestActiveTab = 'auth'">Auth</button>
            <button :class="['ms-like-top-tab', { active: scenarioSystemRequestActiveTab === 'pre' }]" @click="scenarioSystemRequestActiveTab = 'pre'">前置处理</button>
            <button :class="['ms-like-top-tab', { active: scenarioSystemRequestActiveTab === 'post' }]" @click="scenarioSystemRequestActiveTab = 'post'">后置处理</button>
            <button :class="['ms-like-top-tab', { active: scenarioSystemRequestActiveTab === 'tests' }]" @click="scenarioSystemRequestActiveTab = 'tests'">
              断言
              <span v-if="scenarioSystemRequestAssertionEnabledCount" class="ms-like-tab-badge">{{ scenarioSystemRequestAssertionEnabledCount }}</span>
            </button>
            <button :class="['ms-like-top-tab', { active: scenarioSystemRequestActiveTab === 'settings' }]" @click="scenarioSystemRequestActiveTab = 'settings'">设置</button>
          </div>
          <div class="scenario-step-config-body scenario-system-request-body">
            <template v-if="scenarioSystemRequestActiveTab === 'params'">
              <el-table :data="scenarioSystemRequestConfig.queryParams.filter(item => !isKeyValueRowEmpty(item))" size="small">
                <el-table-column prop="key" label="参数名称" min-width="180" />
                <el-table-column prop="value" label="参数值" min-width="220" show-overflow-tooltip />
                <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
                <el-table-column label="启用" width="80">
                  <template #default="{ row }">{{ row.enabled === false ? '否' : '是' }}</template>
                </el-table-column>
              </el-table>
            </template>
            <template v-else-if="scenarioSystemRequestActiveTab === 'headers'">
              <el-table :data="scenarioSystemRequestConfig.headers.filter(item => !isKeyValueRowEmpty(item))" size="small">
                <el-table-column prop="key" label="参数名称" min-width="180" />
                <el-table-column prop="value" label="参数值" min-width="220" show-overflow-tooltip />
                <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
                <el-table-column label="启用" width="80">
                  <template #default="{ row }">{{ row.enabled === false ? '否' : '是' }}</template>
                </el-table-column>
              </el-table>
            </template>
            <template v-else-if="scenarioSystemRequestActiveTab === 'body'">
              <div class="scenario-system-body-type">Body Type: {{ scenarioSystemRequestConfig.body.type }}</div>
              <MonacoCodeEditor
                v-if="scenarioSystemRequestBodyText"
                :model-value="scenarioSystemRequestBodyText"
                :language="scenarioSystemRequestBodyLanguage"
                :read-only="true"
                :show-format-button="false"
                height="360px"
              />
              <el-table v-else-if="scenarioSystemRequestConfig.body.formItems.filter(item => !isKeyValueRowEmpty(item)).length" :data="scenarioSystemRequestConfig.body.formItems.filter(item => !isKeyValueRowEmpty(item))" size="small">
                <el-table-column prop="key" label="参数名称" min-width="180" />
                <el-table-column prop="value" label="参数值" min-width="220" show-overflow-tooltip />
                <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip />
              </el-table>
              <div v-else class="ms-like-empty-body">请求没有 Body</div>
            </template>
            <template v-else-if="scenarioSystemRequestActiveTab === 'auth'">
              <div class="request-section ms-like-form-panel">
                <div class="ms-like-form-row">
                  <div class="ms-like-form-label">认证方式</div>
                  <div class="ms-like-form-control">{{ scenarioSystemRequestConfig.authConfig.authType }}</div>
                </div>
              </div>
            </template>
            <template v-else-if="scenarioSystemRequestActiveTab === 'pre'">
              <div class="scenario-system-list">
                <div v-for="item in scenarioSystemRequestDetail.preProcessors" :key="item.id" class="scenario-system-list-item">
                  <span>{{ item.name }}</span>
                  <span>{{ item.processorType }}</span>
                </div>
                <div v-if="!scenarioSystemRequestDetail.preProcessors.length" class="empty-hint">未配置前置处理</div>
              </div>
            </template>
            <template v-else-if="scenarioSystemRequestActiveTab === 'post'">
              <div class="scenario-system-list">
                <div v-for="item in scenarioSystemRequestDetail.postProcessors" :key="item.id" class="scenario-system-list-item">
                  <span>{{ item.name }}</span>
                  <span>{{ item.processorType }}</span>
                </div>
                <div v-if="!scenarioSystemRequestDetail.postProcessors.length" class="empty-hint">未配置后置处理</div>
              </div>
            </template>
            <template v-else-if="scenarioSystemRequestActiveTab === 'tests'">
              <el-table v-if="scenarioSystemRequestDetail.assertions.length" :data="scenarioSystemRequestDetail.assertions" size="small">
                <el-table-column label="断言名称" min-width="160">
                  <template #default="{ row }">{{ row.name || assertionTypeLabel(row.assertionType || row.type) }}</template>
                </el-table-column>
                <el-table-column label="断言对象" min-width="120">
                  <template #default="{ row }">{{ assertionTypeLabel(row.assertionType || row.type) }}</template>
                </el-table-column>
                <el-table-column prop="expectedValue" label="期望值" min-width="160" show-overflow-tooltip />
              </el-table>
              <div v-else class="empty-hint">未配置断言</div>
            </template>
            <template v-else>
              <div class="request-section ms-like-form-panel">
                <div class="ms-like-form-row">
                  <div class="ms-like-form-label">超时时间</div>
                  <div class="ms-like-form-control">{{ scenarioSystemRequestConfig.timeoutMs || 10000 }} ms</div>
                </div>
                <div class="ms-like-settings-hint">
                  <span>来源 {{ scenarioSystemRequestDetail.resourceType === 'case' ? '接口用例' : '接口定义' }}</span>
                  <span>最后更新 {{ formatTimeLabel(scenarioSystemRequestDetail.updatedAt) }}</span>
                </div>
              </div>
            </template>
          </div>
          <div class="ms-like-response-shell scenario-step-response-shell">
            <div class="ms-like-response-header">
              <div class="ms-like-response-title">响应内容</div>
              <div v-if="!scenarioSystemRequestShowResponseEmptyState" class="ms-like-response-metrics">
                <span v-if="scenarioSystemRequestAssertionResultPresentation.visible" :class="['ms-like-result-pill', `is-${scenarioSystemRequestAssertionResultPresentation.tone}`]">
                  {{ scenarioSystemRequestAssertionResultPresentation.label }}
                </span>
                <span :class="['ms-like-response-metric', `is-${scenarioSystemRequestResponseStatusTone}`]">状态 {{ scenarioSystemRequestResponseStatusCode ?? '-' }}</span>
                <span :class="['ms-like-response-metric', `is-${scenarioSystemRequestResponseDurationTone}`]">耗时 {{ scenarioSystemRequestResponseDuration ?? '-' }}<template v-if="scenarioSystemRequestResponseDuration !== null"> ms</template></span>
                <span>大小 {{ scenarioSystemRequestResponseSize }}</span>
              </div>
            </div>
            <div v-if="scenarioSystemRequestDebugError" class="response-error-banner">
              {{ scenarioSystemRequestDebugError }}
            </div>
            <div v-if="scenarioSystemRequestShowResponseEmptyState" class="ms-like-response-empty">
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
                <button :class="['ms-like-top-tab', { active: scenarioSystemRequestResponsePreviewTab === 'body' }]" @click="scenarioSystemRequestResponsePreviewTab = 'body'">Body</button>
                <button :class="['ms-like-top-tab', { active: scenarioSystemRequestResponsePreviewTab === 'header' }]" @click="scenarioSystemRequestResponsePreviewTab = 'header'">Header</button>
                <button :class="['ms-like-top-tab', { active: scenarioSystemRequestResponsePreviewTab === 'console' }]" @click="scenarioSystemRequestResponsePreviewTab = 'console'">控制台</button>
                <button :class="['ms-like-top-tab', { active: scenarioSystemRequestResponsePreviewTab === 'actualRequest' }]" @click="scenarioSystemRequestResponsePreviewTab = 'actualRequest'">实际请求</button>
                <button :class="['ms-like-top-tab', { active: scenarioSystemRequestResponsePreviewTab === 'assertions' }]" @click="scenarioSystemRequestResponsePreviewTab = 'assertions'">断言</button>
              </div>
              <div class="ms-like-response-body">
                <MonacoCodeEditor
                  v-if="scenarioSystemRequestResponsePreviewTab === 'body'"
                  :model-value="scenarioSystemRequestResponseBodyPreview"
                  :language="scenarioSystemRequestResponseBodyLanguage"
                  :read-only="true"
                  :show-format-button="false"
                  :fit-content="true"
                  :max-fit-content-height="1000"
                  height="100%"
                />
                <MonacoCodeEditor
                  v-else-if="scenarioSystemRequestResponsePreviewTab === 'header'"
                  :model-value="scenarioSystemRequestResponseHeadersPreview"
                  language="json"
                  :read-only="true"
                  :show-format-button="false"
                  :fit-content="true"
                  :max-fit-content-height="1000"
                  height="100%"
                />
                <MonacoCodeEditor
                  v-else-if="scenarioSystemRequestResponsePreviewTab === 'console'"
                  :model-value="scenarioSystemRequestResponseConsolePreview"
                  language="text"
                  :read-only="true"
                  :show-format-button="false"
                  :fit-content="true"
                  :max-fit-content-height="1000"
                  height="100%"
                />
                <MonacoCodeEditor
                  v-else-if="scenarioSystemRequestResponsePreviewTab === 'actualRequest'"
                  :model-value="scenarioSystemRequestActualRequestPreview"
                  language="json"
                  :read-only="true"
                  :show-format-button="false"
                  :fit-content="true"
                  :max-fit-content-height="1000"
                  height="100%"
                />
                <div v-else class="assertion-result-panel">
                  <div v-if="!scenarioSystemRequestAssertionResults.length" class="assertion-result-empty">当前请求未配置断言</div>
                  <el-table v-else :data="scenarioSystemRequestAssertionResults" size="small" class="assertion-result-table">
                    <el-table-column label="断言名称" min-width="140" show-overflow-tooltip>
                      <template #default="{ row }">{{ row.name || assertionTypeLabel(row.type) }}</template>
                    </el-table-column>
                    <el-table-column label="断言对象" width="96">
                      <template #default="{ row }">{{ assertionTypeLabel(row.type) }}</template>
                    </el-table-column>
                    <el-table-column label="条件" width="92">
                      <template #default="{ row }">{{ assertionConditionLabel(row.condition) }}</template>
                    </el-table-column>
                    <el-table-column label="期望值" min-width="120" show-overflow-tooltip>
                      <template #default="{ row }">{{ row.expectedValue || '-' }}</template>
                    </el-table-column>
                    <el-table-column label="实际值" min-width="120" show-overflow-tooltip>
                      <template #default="{ row }">{{ row.actualValue || '-' }}</template>
                    </el-table-column>
                    <el-table-column label="结果" width="78">
                      <template #default="{ row }">
                        <span :class="['case-drawer-history-result', `is-${assertionResultTone(row)}`]">{{ assertionResultLabel(row) }}</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
                      <template #default="{ row }">{{ row.success ? '-' : row.message || '-' }}</template>
                    </el-table-column>
                  </el-table>
                </div>
              </div>
            </template>
          </div>
        </template>
      </div>
    </el-drawer>

    <el-drawer
      v-model="scenarioCustomRequestDrawerVisible"
      size="960px"
      destroy-on-close
      class="api-soft-drawer scenario-step-config-drawer"
      @closed="resetScenarioCustomRequestForm"
    >
      <template #header>
        <div class="scenario-drawer-title-row">
          <span class="scenario-drawer-step-order">{{ scenarioCustomRequestStepOrder }}</span>
          <span class="scenario-step-type-badge is-custom-request">自定义请求</span>
          <el-input
            v-if="scenarioCustomRequestTitleEditing"
            v-model="scenarioCustomRequestForm.stepName"
            class="scenario-drawer-title-input"
            maxlength="255"
            placeholder="请输入步骤名称"
            @blur="finishScenarioCustomRequestTitleEdit"
            @keyup.enter="finishScenarioCustomRequestTitleEdit"
          />
          <span v-else class="scenario-drawer-step-title">{{ scenarioCustomRequestForm.stepName || '自定义请求' }}</span>
          <button v-if="!scenarioCustomRequestTitleEditing" type="button" class="scenario-custom-title-edit" title="编辑名称" @click="startScenarioCustomRequestTitleEdit">
            <el-icon><EditPen /></el-icon>
          </button>
        </div>
      </template>
      <div class="scenario-step-config-shell">
        <div class="scenario-step-config-request-row">
          <el-select model-value="HTTP" class="scenario-step-protocol-select" disabled>
            <el-option label="HTTP" value="HTTP" />
          </el-select>
          <el-select v-model="scenarioCustomRequestConfig.method" :class="['request-method-select', requestMethodClass(scenarioCustomRequestConfig.method)]" popper-class="request-method-popper">
            <el-option v-for="method in requestMethodOptions" :key="method" :label="method" :value="method">
              <span :class="['request-method-option', requestMethodClass(method)]">{{ method }}</span>
            </el-option>
          </el-select>
          <el-input v-model="scenarioCustomRequestConfig.path" class="request-url-input" placeholder="请输入包含 http/https 的完整 URL 或接口路径" />
          <el-button
            type="primary"
            :loading="scenarioCustomRequestDebugLoading"
            :disabled="!canDebugScenarioCustomRequest"
            @click="debugScenarioCustomRequest"
          >
            发送
          </el-button>
        </div>
        <div class="ms-like-top-tabs scenario-step-config-tabs">
          <button :class="['ms-like-top-tab', { active: scenarioCustomRequestActiveTab === 'headers' }]" @click="scenarioCustomRequestActiveTab = 'headers'">请求头</button>
          <button :class="['ms-like-top-tab', { active: scenarioCustomRequestActiveTab === 'body' }]" @click="scenarioCustomRequestActiveTab = 'body'">请求体</button>
          <button :class="['ms-like-top-tab', { active: scenarioCustomRequestActiveTab === 'params' }]" @click="scenarioCustomRequestActiveTab = 'params'">
            Params
            <span v-if="scenarioCustomRequestQueryEnabledCount" class="ms-like-tab-badge">{{ scenarioCustomRequestQueryEnabledCount }}</span>
          </button>
          <button :class="['ms-like-top-tab', { active: scenarioCustomRequestActiveTab === 'auth' }]" @click="scenarioCustomRequestActiveTab = 'auth'">Auth</button>
          <button :class="['ms-like-top-tab', { active: scenarioCustomRequestActiveTab === 'pre' }]" @click="scenarioCustomRequestActiveTab = 'pre'">前置处理</button>
          <button :class="['ms-like-top-tab', { active: scenarioCustomRequestActiveTab === 'post' }]" @click="scenarioCustomRequestActiveTab = 'post'">后置处理</button>
          <button :class="['ms-like-top-tab', { active: scenarioCustomRequestActiveTab === 'tests' }]" @click="scenarioCustomRequestActiveTab = 'tests'">
            断言
            <span v-if="scenarioCustomRequestAssertionEnabledCount" class="ms-like-tab-badge">{{ scenarioCustomRequestAssertionEnabledCount }}</span>
          </button>
          <button :class="['ms-like-top-tab', { active: scenarioCustomRequestActiveTab === 'settings' }]" @click="scenarioCustomRequestActiveTab = 'settings'">设置</button>
        </div>
        <div class="scenario-step-config-body">
          <template v-if="scenarioCustomRequestActiveTab === 'params'">
            <div class="request-section ms-like-table-surface ms-like-param-table ms-like-param-table--query">
              <div class="ms-like-table-header ms-like-param-table-grid ms-like-param-table-grid--query">
                <div class="ms-like-drag-cell"></div>
                <div class="ms-like-checkbox-cell ms-like-checkbox-cell--header">
                  <el-checkbox v-model="scenarioCustomRequestQueryTableSelectionModel" :indeterminate="tableSelectionState(scenarioCustomRequestConfig.queryParams).indeterminate" />
                </div>
                <span class="ms-like-header-input-title">Query 参数</span>
                <span class="ms-like-type-header">类型</span>
                <span>参数值</span>
                <span class="ms-like-length-header">长度范围</span>
                <span>编码</span>
                <span>描述</span>
                <span></span>
              </div>
              <div v-for="(row, index) in scenarioCustomRequestConfig.queryParams" :key="`scenario-custom-query-${index}`" class="ms-like-table-row ms-like-param-table-grid ms-like-param-table-grid--query">
                <div class="ms-like-drag-cell"></div>
                <div class="ms-like-checkbox-cell"><el-checkbox v-model="row.enabled" /></div>
                <div class="ms-like-name-field">
                  <el-input v-model="row.key" placeholder="参数名称" @input="handleKeyValueRowInput(scenarioCustomRequestConfig.queryParams, queryParamDefaults())" />
                </div>
                <div class="ms-like-type-field">
                  <button type="button" :class="['ms-like-required-button', { active: row.required }]" :title="row.required ? '必填' : '非必填'" @click="row.required = !row.required">*</button>
                  <el-select v-model="row.paramType" @change="handleKeyValueRowInput(scenarioCustomRequestConfig.queryParams, queryParamDefaults())">
                    <el-option v-for="option in queryParamTypeOptions" :key="option" :label="option" :value="option" />
                  </el-select>
                </div>
                <el-input v-model="row.value" placeholder="参数值 / {{variable}}" @input="handleKeyValueRowInput(scenarioCustomRequestConfig.queryParams, queryParamDefaults())" />
                <div class="ms-like-length-range-cell">
                  <el-input-number v-model="row.minLength" :min="0" :controls="false" placeholder="最小" @change="handleKeyValueRowInput(scenarioCustomRequestConfig.queryParams, queryParamDefaults())" />
                  <span>至</span>
                  <el-input-number v-model="row.maxLength" :min="0" :controls="false" placeholder="最大" @change="handleKeyValueRowInput(scenarioCustomRequestConfig.queryParams, queryParamDefaults())" />
                </div>
                <div class="ms-like-switch-cell ms-like-switch-cell--query"><el-switch v-model="row.encode" size="small" /></div>
                <el-input v-model="row.description" placeholder="描述" @input="handleKeyValueRowInput(scenarioCustomRequestConfig.queryParams, queryParamDefaults())" />
                <button type="button" class="ms-like-row-remove" @click="removeKeyValueRow(scenarioCustomRequestConfig.queryParams, index, queryParamDefaults())">删除</button>
              </div>
              <button type="button" class="ms-like-add-row" @click="scenarioCustomRequestConfig.queryParams.push(emptyKeyValue(queryParamDefaults()))">+ 添加一行</button>
            </div>
          </template>
          <template v-else-if="scenarioCustomRequestActiveTab === 'headers'">
            <div class="request-section ms-like-table-surface ms-like-param-table ms-like-param-table--header">
              <div class="ms-like-table-header ms-like-param-table-grid ms-like-param-table-grid--header">
                <div class="ms-like-drag-cell"></div>
                <div class="ms-like-checkbox-cell ms-like-checkbox-cell--header">
                  <el-checkbox v-model="scenarioCustomRequestHeaderTableSelectionModel" :indeterminate="tableSelectionState(scenarioCustomRequestConfig.headers).indeterminate" />
                </div>
                <span class="ms-like-header-input-title">参数名称</span>
                <span>参数值</span>
                <span>描述</span>
                <span></span>
              </div>
              <div v-for="(row, index) in scenarioCustomRequestConfig.headers" :key="`scenario-custom-header-${index}`" class="ms-like-table-row ms-like-param-table-grid ms-like-param-table-grid--header">
                <div class="ms-like-drag-cell"></div>
                <div class="ms-like-checkbox-cell"><el-checkbox v-model="row.enabled" /></div>
                <div class="ms-like-header-input-cell">
                  <el-input v-model="row.key" placeholder="参数名称" @input="handleKeyValueRowInput(scenarioCustomRequestConfig.headers, headerParamDefaults())" />
                </div>
                <el-input v-model="row.value" placeholder="参数值" @input="handleKeyValueRowInput(scenarioCustomRequestConfig.headers, headerParamDefaults())" />
                <el-input v-model="row.description" placeholder="描述" @input="handleKeyValueRowInput(scenarioCustomRequestConfig.headers, headerParamDefaults())" />
                <button type="button" class="ms-like-row-remove" @click="removeKeyValueRow(scenarioCustomRequestConfig.headers, index, headerParamDefaults())">删除</button>
              </div>
              <button type="button" class="ms-like-add-row" @click="scenarioCustomRequestConfig.headers.push(emptyKeyValue(headerParamDefaults()))">+ 添加一行</button>
            </div>
          </template>
          <template v-else-if="scenarioCustomRequestActiveTab === 'body'">
            <div class="request-section">
              <div class="ms-like-body-type-row">
                <button :class="['ms-like-body-chip', { active: isScenarioCustomRequestBodyMode('NONE') }]" @click="setScenarioCustomRequestBodyMode('NONE')">none</button>
                <button :class="['ms-like-body-chip', { active: isScenarioCustomRequestBodyMode('FORM_DATA') }]" @click="setScenarioCustomRequestBodyMode('FORM_DATA')">form-data</button>
                <button :class="['ms-like-body-chip', { active: isScenarioCustomRequestBodyMode('FORM_URLENCODED') }]" @click="setScenarioCustomRequestBodyMode('FORM_URLENCODED')">x-www-form-urlencoded</button>
                <button :class="['ms-like-body-chip', { active: isScenarioCustomRequestBodyMode('RAW_JSON') }]" @click="setScenarioCustomRequestBodyMode('RAW_JSON')">json</button>
                <button :class="['ms-like-body-chip', { active: isScenarioCustomRequestBodyMode('RAW_XML') }]" @click="setScenarioCustomRequestBodyMode('RAW_XML')">xml</button>
                <button :class="['ms-like-body-chip', { active: isScenarioCustomRequestBodyMode('RAW_TEXT') }]" @click="setScenarioCustomRequestBodyMode('RAW_TEXT')">raw</button>
                <button :class="['ms-like-body-chip', { active: isScenarioCustomRequestBodyMode('BINARY') }]" @click="setScenarioCustomRequestBodyMode('BINARY')">binary</button>
              </div>
              <div class="ms-like-body-mode-shell">
                <MonacoCodeEditor v-if="['RAW_JSON', 'RAW_XML', 'RAW_TEXT'].includes(scenarioCustomRequestConfig.body.type)" v-model="scenarioCustomRequestActiveBodyRawText" :language="scenarioCustomRequestActiveBodyLanguage" height="300px" />
                <div v-else-if="scenarioCustomRequestConfig.body.type === 'BINARY'" class="request-section ms-like-form-panel">
                  <div class="ms-like-form-row">
                    <div class="ms-like-form-label">File</div>
                    <el-input v-model="scenarioCustomRequestConfig.body.fileName" class="ms-like-form-control" placeholder="文件名" />
                  </div>
                </div>
                <div v-else-if="['FORM_URLENCODED', 'FORM_DATA'].includes(scenarioCustomRequestConfig.body.type)" class="body-form-grid ms-like-table-surface ms-like-param-table ms-like-param-table--body-form">
                  <div class="ms-like-table-header ms-like-param-table-grid ms-like-param-table-grid--body-form">
                    <div class="ms-like-drag-cell"></div>
                    <div class="ms-like-checkbox-cell ms-like-checkbox-cell--header">
                      <el-checkbox v-model="scenarioCustomRequestBodyFormTableSelectionModel" :indeterminate="tableSelectionState(scenarioCustomRequestConfig.body.formItems).indeterminate" />
                    </div>
                    <span class="ms-like-header-input-title">参数名称</span>
                    <span class="ms-like-type-header">类型</span>
                    <span>参数值</span>
                    <span class="ms-like-length-header">长度范围</span>
                    <span>描述</span>
                    <span></span>
                  </div>
                  <div v-for="(row, index) in scenarioCustomRequestConfig.body.formItems" :key="`scenario-custom-body-${index}`" class="ms-like-table-row ms-like-param-table-grid ms-like-param-table-grid--body-form">
                    <div class="ms-like-drag-cell"></div>
                    <div class="ms-like-checkbox-cell"><el-checkbox v-model="row.enabled" /></div>
                    <div class="ms-like-name-field">
                      <el-input v-model="row.key" placeholder="参数名称" @input="handleKeyValueRowInput(scenarioCustomRequestConfig.body.formItems, bodyFormParamDefaults())" />
                    </div>
                    <div class="ms-like-type-field">
                      <button type="button" :class="['ms-like-required-button', { active: row.required }]" :title="row.required ? '必填' : '非必填'" @click="row.required = !row.required">*</button>
                      <el-select v-model="row.paramType" @change="handleKeyValueRowInput(scenarioCustomRequestConfig.body.formItems, bodyFormParamDefaults())">
                        <el-option v-for="option in bodyParamTypeOptionsFor({ requestConfig: scenarioCustomRequestConfig } as RequestConfigHost)" :key="option" :label="option" :value="option" />
                      </el-select>
                    </div>
                    <div v-if="row.paramType === 'file'" class="ms-like-file-param-cell">
                      <button type="button" class="ms-like-file-pick" @click="pickBodyFormRowFile(row, scenarioCustomRequestConfig.body.formItems, bodyFormParamDefaults())">{{ row.fileName || '选择文件' }}</button>
                      <button v-if="row.fileBase64" type="button" class="ms-like-file-clear" @click="clearBodyFormRowFile(row)">清空</button>
                      <small v-if="row.fileBase64">{{ formatBodyFormFileSize(row) }}</small>
                    </div>
                    <el-input v-else v-model="row.value" placeholder="参数值" @input="handleKeyValueRowInput(scenarioCustomRequestConfig.body.formItems, bodyFormParamDefaults())" />
                    <div class="ms-like-length-range-cell">
                      <el-input-number v-model="row.minLength" :min="0" :controls="false" placeholder="最小" @change="handleKeyValueRowInput(scenarioCustomRequestConfig.body.formItems, bodyFormParamDefaults())" />
                      <span>至</span>
                      <el-input-number v-model="row.maxLength" :min="0" :controls="false" placeholder="最大" @change="handleKeyValueRowInput(scenarioCustomRequestConfig.body.formItems, bodyFormParamDefaults())" />
                    </div>
                    <el-input v-model="row.description" placeholder="描述" @input="handleKeyValueRowInput(scenarioCustomRequestConfig.body.formItems, bodyFormParamDefaults())" />
                    <button type="button" class="ms-like-row-remove" @click="removeKeyValueRow(scenarioCustomRequestConfig.body.formItems, index, bodyFormParamDefaults())">删除</button>
                  </div>
                  <button type="button" class="ms-like-add-row" @click="scenarioCustomRequestConfig.body.formItems.push(emptyKeyValue(bodyFormParamDefaults()))">+ 添加一行</button>
                </div>
                <div v-else class="ms-like-empty-body">请求没有 Body</div>
              </div>
            </div>
          </template>
          <template v-else-if="scenarioCustomRequestActiveTab === 'pre'">
            <div class="request-section">
              <ApiProcessorEditor v-model="scenarioCustomRequestPreProcessors" v-model:active-id="scenarioCustomRequestActivePreProcessorId" stage="pre" :db-connections="dbConnections" />
            </div>
          </template>
          <template v-else-if="scenarioCustomRequestActiveTab === 'post'">
            <div class="request-section">
              <ApiProcessorEditor v-model="scenarioCustomRequestPostProcessors" v-model:active-id="scenarioCustomRequestActivePostProcessorId" stage="post" :db-connections="dbConnections" />
            </div>
          </template>
          <template v-else-if="scenarioCustomRequestActiveTab === 'tests'">
            <div class="request-section">
              <ApiAssertionEditor v-model="scenarioCustomRequestAssertions" v-model:active-id="scenarioCustomRequestActiveAssertionId" />
            </div>
          </template>
          <template v-else-if="scenarioCustomRequestActiveTab === 'auth'">
            <div class="request-section">
              <div class="ms-auth-panel">
                <div class="ms-auth-panel-title">认证方式</div>
                <el-radio-group v-model="scenarioCustomRequestConfig.authConfig.authType" class="ms-auth-radio-group">
                  <el-radio-button v-for="option in requestAuthTypeOptions" :key="option.value" :value="option.value">{{ option.label }}</el-radio-button>
                </el-radio-group>
                <div v-if="scenarioCustomRequestConfig.authConfig.authType === 'BASIC'" class="ms-auth-form">
                  <div class="ms-auth-form-item"><label class="ms-auth-form-label">Username</label><el-input v-model="scenarioCustomRequestConfig.authConfig.basicAuth.userName" placeholder="username" class="ms-auth-form-control" /></div>
                  <div class="ms-auth-form-item"><label class="ms-auth-form-label">Password</label><el-input v-model="scenarioCustomRequestConfig.authConfig.basicAuth.password" placeholder="password" class="ms-auth-form-control" show-password /></div>
                </div>
                <div v-else-if="scenarioCustomRequestConfig.authConfig.authType === 'DIGEST'" class="ms-auth-form">
                  <div class="ms-auth-form-item"><label class="ms-auth-form-label">Username</label><el-input v-model="scenarioCustomRequestConfig.authConfig.digestAuth.userName" placeholder="username" class="ms-auth-form-control" /></div>
                  <div class="ms-auth-form-item"><label class="ms-auth-form-label">Password</label><el-input v-model="scenarioCustomRequestConfig.authConfig.digestAuth.password" placeholder="password" class="ms-auth-form-control" show-password /></div>
                </div>
              </div>
            </div>
          </template>
          <template v-else>
            <div class="request-section ms-like-form-panel">
              <div class="ms-like-form-row">
                <div class="ms-like-form-label">超时时间</div>
                <el-input-number v-model="scenarioCustomRequestConfig.timeoutMs" :min="1000" :step="1000" class="ms-like-form-control full-width" />
              </div>
              <div class="ms-like-settings-hint">
                <span>场景 {{ scenarioForm.name || '新建场景' }}</span>
                <span>调试上下文 {{ currentEnvironmentName }} / {{ currentVariableSetName }}</span>
              </div>
            </div>
          </template>
        </div>
        <div class="ms-like-response-shell scenario-step-response-shell">
          <div class="ms-like-response-header">
            <div class="ms-like-response-title">响应内容</div>
            <div v-if="!scenarioCustomRequestShowResponseEmptyState" class="ms-like-response-metrics">
              <span v-if="scenarioCustomRequestAssertionResultPresentation.visible" :class="['ms-like-result-pill', `is-${scenarioCustomRequestAssertionResultPresentation.tone}`]">
                {{ scenarioCustomRequestAssertionResultPresentation.label }}
              </span>
              <span :class="['ms-like-response-metric', `is-${scenarioCustomRequestResponseStatusTone}`]">状态 {{ scenarioCustomRequestResponseStatusCode ?? '-' }}</span>
              <span :class="['ms-like-response-metric', `is-${scenarioCustomRequestResponseDurationTone}`]">耗时 {{ scenarioCustomRequestResponseDuration ?? '-' }}<template v-if="scenarioCustomRequestResponseDuration !== null"> ms</template></span>
              <span>大小 {{ scenarioCustomRequestResponseSize }}</span>
            </div>
          </div>
          <div v-if="scenarioCustomRequestDebugError" class="response-error-banner">
            {{ scenarioCustomRequestDebugError }}
          </div>
          <div v-if="scenarioCustomRequestShowResponseEmptyState" class="ms-like-response-empty">
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
              <button :class="['ms-like-top-tab', { active: scenarioCustomRequestResponsePreviewTab === 'body' }]" @click="scenarioCustomRequestResponsePreviewTab = 'body'">Body</button>
              <button :class="['ms-like-top-tab', { active: scenarioCustomRequestResponsePreviewTab === 'header' }]" @click="scenarioCustomRequestResponsePreviewTab = 'header'">Header</button>
              <button :class="['ms-like-top-tab', { active: scenarioCustomRequestResponsePreviewTab === 'console' }]" @click="scenarioCustomRequestResponsePreviewTab = 'console'">控制台</button>
              <button :class="['ms-like-top-tab', { active: scenarioCustomRequestResponsePreviewTab === 'actualRequest' }]" @click="scenarioCustomRequestResponsePreviewTab = 'actualRequest'">实际请求</button>
              <button :class="['ms-like-top-tab', { active: scenarioCustomRequestResponsePreviewTab === 'assertions' }]" @click="scenarioCustomRequestResponsePreviewTab = 'assertions'">断言</button>
            </div>
            <div class="ms-like-response-body">
              <MonacoCodeEditor
                v-if="scenarioCustomRequestResponsePreviewTab === 'body'"
                :model-value="scenarioCustomRequestResponseBodyPreview"
                :language="scenarioCustomRequestResponseBodyLanguage"
                :read-only="true"
                :show-format-button="false"
                :fit-content="true"
                :max-fit-content-height="1000"
                height="100%"
              />
              <MonacoCodeEditor
                v-else-if="scenarioCustomRequestResponsePreviewTab === 'header'"
                :model-value="scenarioCustomRequestResponseHeadersPreview"
                language="json"
                :read-only="true"
                :show-format-button="false"
                :fit-content="true"
                :max-fit-content-height="1000"
                height="100%"
              />
              <MonacoCodeEditor
                v-else-if="scenarioCustomRequestResponsePreviewTab === 'console'"
                :model-value="scenarioCustomRequestResponseConsolePreview"
                language="text"
                :read-only="true"
                :show-format-button="false"
                :fit-content="true"
                :max-fit-content-height="1000"
                height="100%"
              />
              <MonacoCodeEditor
                v-else-if="scenarioCustomRequestResponsePreviewTab === 'actualRequest'"
                :model-value="scenarioCustomRequestActualRequestPreview"
                language="json"
                :read-only="true"
                :show-format-button="false"
                :fit-content="true"
                :max-fit-content-height="1000"
                height="100%"
              />
              <div v-else class="assertion-result-panel">
                <div v-if="!scenarioCustomRequestAssertionResults.length" class="assertion-result-empty">当前请求未配置断言</div>
                <el-table v-else :data="scenarioCustomRequestAssertionResults" size="small" class="assertion-result-table">
                  <el-table-column label="断言名称" min-width="140" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.name || assertionTypeLabel(row.type) }}</template>
                  </el-table-column>
                  <el-table-column label="断言对象" width="96">
                    <template #default="{ row }">{{ assertionTypeLabel(row.type) }}</template>
                  </el-table-column>
                  <el-table-column label="条件" width="92">
                    <template #default="{ row }">{{ assertionConditionLabel(row.condition) }}</template>
                  </el-table-column>
                  <el-table-column label="期望值" min-width="120" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.expectedValue || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="实际值" min-width="120" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.actualValue || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="结果" width="78">
                    <template #default="{ row }">
                      <span :class="['case-drawer-history-result', `is-${assertionResultTone(row)}`]">{{ assertionResultLabel(row) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.success ? '-' : row.message || '-' }}</template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </template>
        </div>
      </div>
      <template #footer>
        <div class="scenario-step-config-footer">
          <el-button @click="closeScenarioCustomRequestDrawer">取消</el-button>
          <el-button v-if="scenarioCustomRequestDrawerMode === 'create'" :disabled="!scenarioCustomRequestConfig.path?.trim()" @click="saveScenarioCustomRequestStep(true)">保存并继续</el-button>
          <el-button type="primary" :disabled="!scenarioCustomRequestConfig.path?.trim()" @click="saveScenarioCustomRequestStep(false)">
            {{ scenarioCustomRequestDrawerMode === 'edit' ? '保存' : '添加' }}
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-drawer
      v-model="scenarioScriptDrawerVisible"
      size="960px"
      destroy-on-close
      class="api-soft-drawer scenario-step-config-drawer scenario-script-operation-drawer"
      @closed="resetScenarioScriptForm"
    >
      <template #header>
        <div class="scenario-script-drawer-title">脚本操作</div>
      </template>
      <div class="scenario-step-config-shell">
        <div class="ms-like-top-tabs scenario-step-config-tabs">
          <button :class="['ms-like-top-tab', { active: scenarioScriptActiveTab === 'script' }]" @click="scenarioScriptActiveTab = 'script'">脚本</button>
          <button :class="['ms-like-top-tab', { active: scenarioScriptActiveTab === 'assertions' }]" @click="scenarioScriptActiveTab = 'assertions'">
            断言
            <span v-if="scenarioScriptAssertionEnabledCount" class="ms-like-tab-badge">{{ scenarioScriptAssertionEnabledCount }}</span>
          </button>
        </div>
        <div v-if="scenarioScriptActiveTab === 'script'" class="scenario-script-editor-pane">
          <label class="scenario-script-field">
            <span class="scenario-script-field-label">名称</span>
            <el-input v-model="scenarioScriptForm.stepName" maxlength="255" placeholder="请输入脚本操作名称" class="scenario-script-name-input" />
          </label>
          <div class="scenario-script-mode-tabs">
            <button :class="['scenario-script-mode-tab', { active: scenarioScriptInputMode === 'manual' }]" @click="scenarioScriptInputMode = 'manual'">手动录入</button>
            <el-tooltip content="公共脚本功能开发中" placement="top">
              <span class="scenario-script-mode-tab-tooltip">
                <button
                  type="button"
                  class="scenario-script-mode-tab is-disabled"
                  disabled
                >
                  引用公共脚本
                </button>
              </span>
            </el-tooltip>
          </div>
          <template v-if="scenarioScriptInputMode === 'manual'">
            <div class="scenario-script-editor-header">
              <span>脚本案例</span>
              <div class="scenario-script-editor-actions">
                <el-button size="small" :icon="MagicStick" @click="formatScenarioScriptContent">格式化</el-button>
                <el-button size="small" :icon="Delete" @click="clearScenarioScriptContent">清空</el-button>
              </div>
            </div>
            <div class="scenario-script-code-shell">
              <MonacoCodeEditor
                ref="scenarioScriptEditorRef"
                v-model="scenarioScriptContent"
                language="javascript"
                height="100%"
                :show-format-button="false"
              />
            </div>
          </template>
        </div>
        <div v-else class="scenario-script-assertion-pane request-section">
          <ApiAssertionEditor
            v-model="scenarioScriptAssertions"
            v-model:active-id="scenarioScriptActiveAssertionId"
            :allowed-types="['VARIABLE', 'SCRIPT']"
          />
        </div>
        <div v-if="scenarioScriptShouldShowResultPanel" class="ms-like-response-shell scenario-script-result-shell">
          <div class="ms-like-response-header">
            <div class="ms-like-response-title">执行结果</div>
            <div v-if="!scenarioScriptShowResponseEmptyState" class="ms-like-response-metrics">
              <span v-if="scenarioScriptAssertionResultPresentation.visible" :class="['ms-like-result-pill', `is-${scenarioScriptAssertionResultPresentation.tone}`]">
                {{ scenarioScriptAssertionResultPresentation.label }}
              </span>
              <span>步骤 {{ scenarioScriptStepOrder }}</span>
            </div>
          </div>
          <div v-if="scenarioScriptDebugError" class="response-error-banner">
            {{ scenarioScriptDebugError }}
          </div>
          <div v-if="scenarioScriptShowResponseEmptyState" class="ms-like-response-empty">
            <div class="ms-like-response-empty-card">
              <div class="ms-like-response-empty-visual">
                <div class="ms-like-response-empty-window">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
              </div>
              <div class="ms-like-response-empty-text">执行场景后查看当前步骤结果</div>
            </div>
          </div>
          <template v-else>
            <div class="ms-like-response-tabs">
              <button :class="['ms-like-top-tab', { active: scenarioScriptResultTab === 'console' }]" @click="scenarioScriptResultTab = 'console'">控制台</button>
              <button :class="['ms-like-top-tab', { active: scenarioScriptResultTab === 'assertions' }]" @click="scenarioScriptResultTab = 'assertions'">断言</button>
            </div>
            <div class="ms-like-response-body">
              <MonacoCodeEditor
                v-if="scenarioScriptResultTab === 'console'"
                :model-value="scenarioScriptResponseConsolePreview"
                language="text"
                :read-only="true"
                :show-format-button="false"
                :fit-content="true"
                :max-fit-content-height="1000"
                height="100%"
              />
              <div v-else class="assertion-result-panel">
                <div v-if="!scenarioScriptAssertionResults.length" class="assertion-result-empty">当前脚本未配置断言</div>
                <el-table v-else :data="scenarioScriptAssertionResults" size="small" class="assertion-result-table">
                  <el-table-column label="断言名称" min-width="140" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.name || assertionTypeLabel(row.type) }}</template>
                  </el-table-column>
                  <el-table-column label="断言对象" width="96">
                    <template #default="{ row }">{{ assertionTypeLabel(row.type) }}</template>
                  </el-table-column>
                  <el-table-column label="条件" width="92">
                    <template #default="{ row }">{{ assertionConditionLabel(row.condition) }}</template>
                  </el-table-column>
                  <el-table-column label="期望值" min-width="120" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.expectedValue || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="实际值" min-width="120" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.actualValue || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="结果" width="78">
                    <template #default="{ row }">
                      <span :class="['case-drawer-history-result', `is-${assertionResultTone(row)}`]">{{ assertionResultLabel(row) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.success ? '-' : row.message || '-' }}</template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </template>
        </div>
      </div>
      <template #footer>
        <div class="scenario-step-config-footer">
          <el-button @click="closeScenarioScriptDrawer">取消</el-button>
          <el-button v-if="scenarioScriptDrawerMode === 'create'" :disabled="!scenarioScriptForm.stepName?.trim()" @click="saveScenarioScriptStep(true)">保存并继续添加</el-button>
          <el-button type="primary" :disabled="!scenarioScriptForm.stepName?.trim()" @click="saveScenarioScriptStep(false)">
            {{ scenarioScriptDrawerMode === 'edit' ? '保存' : '添加' }}
          </el-button>
        </div>
      </template>
    </el-drawer>

      <el-drawer
        v-model="batchAddDrawerVisible"
        :title="batchAddTitle"
        size="560px"
        destroy-on-close
        class="api-soft-drawer batch-add-soft-drawer"
        >
        <div class="batch-drawer">
          <div v-if="batchAddMode === 'query' || batchAddMode === 'body-form'" class="batch-drawer-mode-row">
            <button
              type="button"
              :class="{ active: batchAddParamStyle === 'standard' }"
              @click="batchAddParamStyle = 'standard'"
            >
              标准添加
            </button>
            <button
              type="button"
              :class="{ active: batchAddParamStyle === 'quick' }"
              @click="batchAddParamStyle = 'quick'"
            >
              快捷添加
            </button>
          </div>
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

      <el-dialog
        v-model="definitionSaveDialogVisible"
        title="保存"
        width="520px"
        destroy-on-close
        class="api-soft-dialog"
      >
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

    <el-dialog v-model="bugDialogVisible" title="从报告创建缺陷" width="640px" class="api-soft-dialog">
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
        <div class="dialog-footer">
          <el-button @click="bugDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submitReportBug">提交</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="reportDrawerVisible" title="API 执行报告" size="860px" class="api-soft-drawer">
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

    <el-dialog
      v-model="definitionImportDialogVisible"
      width="520px"
      class="definition-import-dialog"
      destroy-on-close
      :show-close="false"
    >
      <div class="definition-import-modal">
        <div class="definition-import-header">
          <div class="definition-import-title">
            <Upload class="definition-import-title-icon" />
            <span>导入接口</span>
          </div>
          <button type="button" class="definition-import-close" @click="definitionImportDialogVisible = false">
            <X />
          </button>
        </div>

        <div class="definition-import-body">
          <section class="definition-import-section">
            <div class="definition-import-section-title">选择导入格式</div>
            <div class="definition-import-format-list">
              <button
                v-for="format in definitionImportFormats"
                :key="format.value"
                type="button"
                :class="['definition-import-format', `is-${format.tone}`, { active: definitionImportFormat === format.value }]"
                @click="definitionImportFormat = format.value"
              >
                <span class="definition-import-format-icon">
                  <component :is="format.icon" />
                </span>
                <span class="definition-import-format-copy">
                  <span>{{ format.label }}</span>
                  <small>{{ format.description }}</small>
                </span>
                <span class="definition-import-check">
                  <Check v-if="definitionImportFormat === format.value" />
                </span>
              </button>
            </div>
          </section>

          <section class="definition-import-section">
            <div class="definition-import-section-title">导入方式</div>
            <div class="definition-import-mode-switch">
              <button
                type="button"
                :class="{ active: definitionImportMode === 'url' }"
                @click="definitionImportMode = 'url'"
              >
                URL 导入
              </button>
              <button
                type="button"
                :class="{ active: definitionImportMode === 'file' }"
                @click="definitionImportMode = 'file'"
              >
                文件上传
              </button>
            </div>
            <el-input
              v-if="definitionImportMode === 'url'"
              v-model="definitionImportUrl"
              class="definition-import-url"
              :placeholder="definitionImportFormat === 'swagger' ? 'https://api.example.com/v3/api-docs' : '输入文件远程地址'"
            />
            <label v-else class="definition-import-upload">
              <Upload class="definition-import-upload-icon" />
              <span>{{ definitionImportFileName || '点击或拖拽文件到此处' }}</span>
              <small>支持 {{ selectedDefinitionImportFormat.accept }}</small>
              <input
                type="file"
                :accept="selectedDefinitionImportFormat.accept"
                @change="handleDefinitionImportFileChange"
              >
            </label>
          </section>
        </div>

        <div class="definition-import-footer">
          <button type="button" class="definition-import-cancel" @click="definitionImportDialogVisible = false">取消</button>
          <button type="button" class="definition-import-submit" @click="submitDefinitionImportPreview">开始导入</button>
        </div>
      </div>
    </el-dialog>

    <el-drawer
      v-model="aiCaseGenerateDrawerVisible"
      size="640px"
      class="api-ai-case-drawer"
      :show-close="false"
      destroy-on-close
    >
      <template #header>
        <div class="ai-case-drawer-header">
          <div>
            <div class="ai-case-drawer-title">
              <MagicStick />
              <span>AI 生成接口用例</span>
            </div>
          </div>
          <button type="button" class="definition-import-close" @click="aiCaseGenerateDrawerVisible = false">
            <X />
          </button>
        </div>
      </template>

      <div class="ai-case-drawer-body">
        <section class="ai-case-section">
          <div class="ai-case-section-title">
            <span>选择生成的用例类型</span>
            <small>已选 {{ aiCaseGenerateSelectedCount }} 项</small>
          </div>
          <div class="ai-case-option-groups">
            <div v-for="group in aiCaseGenerateGroups" :key="group.key" class="ai-case-option-group">
              <div class="ai-case-option-group-title">
                <strong>{{ group.label }}</strong>
                <button
                  type="button"
                  class="ai-case-select-all-link"
                  @click="toggleAiCaseGroup(group.key, !isAiCaseGroupAllSelected(group.key))"
                >
                  全选
                </button>
              </div>
              <el-checkbox-group v-model="aiCaseGenerateSelectedOptions" class="ai-case-option-list">
                <el-checkbox
                  v-for="option in aiCaseGenerateOptions.filter(item => item.group === group.key)"
                  :key="option.key"
                  :value="option.key"
                >
                  {{ option.label }}
                </el-checkbox>
              </el-checkbox-group>
            </div>
          </div>
        </section>

        <section class="ai-case-section ai-case-form-section">
          <label class="ai-case-form-field">
            <span>用例数</span>
            <el-select v-model="aiCaseGenerateCount" class="ai-case-form-control">
              <el-option label="自动" value="AUTO" />
              <el-option label="10 条" :value="10" />
              <el-option label="20 条" :value="20" />
              <el-option label="40 条" :value="40" />
              <el-option label="80 条" :value="80" />
            </el-select>
          </label>
          <label class="ai-case-form-field">
            <span>AI模型</span>
            <el-select
              v-model="aiCaseGenerateModel"
              class="ai-case-form-control"
              :loading="aiCaseGenerateProviderLoading"
              placeholder="选择 AI 连接池配置的模型"
              empty-text="暂无可用 AI 模型"
            >
              <el-option
                v-for="item in aiCaseGenerateModelOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </label>
          <div class="ai-case-form-switch">
            <el-switch v-model="aiCaseGenerateNoDuplicate" />
            <span>
              <strong>不重复生成用例</strong>
              <small>不生成与已有用例同用例类型的用例。关闭后，生成不受已有用例的影响</small>
            </span>
          </div>
          <label class="ai-case-form-field">
            <el-input
              v-model="aiCaseGeneratePrompt"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 5 }"
              placeholder="输入更多要求"
              class="ai-case-form-control"
            />
          </label>
          <button type="button" class="ai-case-generate-submit" @click="submitAiCaseGeneratePreview">
            <MagicStick />
            生成
          </button>
        </section>
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
  min-height: 760px;
  height: calc(100vh - 104px);
  gap: 0;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #ffffff;
  font-family: Inter, "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC", sans-serif;
  --el-color-primary: #2563eb;
  --el-color-primary-light-3: #3b82f6;
  --el-color-primary-light-5: #93c5fd;
  --el-color-primary-light-7: #bfdbfe;
  --el-color-primary-light-8: #dbeafe;
  --el-color-primary-light-9: #eff6ff;
  --el-color-primary-dark-2: #1d4ed8;
}

.api-automation-page :deep(.el-button--primary) {
  --el-button-bg-color: #2563eb;
  --el-button-border-color: #2563eb;
  --el-button-hover-bg-color: #1d4ed8;
  --el-button-hover-border-color: #1d4ed8;
  --el-button-active-bg-color: #1d4ed8;
  --el-button-active-border-color: #1d4ed8;
}

.api-automation-page :deep(.el-button--primary.is-plain) {
  --el-button-bg-color: #eff6ff;
  --el-button-border-color: #bfdbfe;
  --el-button-text-color: #2563eb;
  --el-button-hover-bg-color: #2563eb;
  --el-button-hover-border-color: #2563eb;
  --el-button-hover-text-color: #ffffff;
  --el-button-active-bg-color: #1d4ed8;
  --el-button-active-border-color: #1d4ed8;
}

.api-automation-page :deep(.el-button--primary.is-text) {
  --el-button-text-color: #2563eb;
  --el-button-hover-text-color: #1d4ed8;
  --el-button-hover-bg-color: #eff6ff;
}

.api-automation-page :deep(.el-checkbox__input.is-checked .el-checkbox__inner),
.api-automation-page :deep(.el-checkbox__input.is-indeterminate .el-checkbox__inner) {
  border-color: #2563eb;
  background-color: #2563eb;
}

.api-automation-page :deep(.el-checkbox__input.is-focus .el-checkbox__inner),
.api-automation-page :deep(.el-checkbox__inner:hover) {
  border-color: #2563eb;
}

.api-tab-placeholder {
  display: flex;
  min-height: 100%;
  align-items: center;
  justify-content: center;
  color: var(--ath-text-subtle);
  font-size: var(--ath-font-sm);
  text-align: center;
}

.api-tab-placeholder > div {
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

.api-tab-placeholder svg {
  width: 42px;
  height: 42px;
  border-radius: var(--ath-radius-lg);
  background: var(--ath-blue-soft);
  padding: 10px;
  color: var(--ath-primary);
}

.api-tab-placeholder p {
  margin: 0;
}

.api-tabs {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  flex-direction: column;
}

.api-tabs > :deep(.el-tabs__header) {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  height: 48px;
  margin: 0;
  padding: 6px 24px;
  border-bottom: 1px solid #e5e7eb;
  border-radius: 12px 12px 0 0;
  background: #ffffff;
}

.api-tabs > :deep(.el-tabs__header .el-tabs__nav-wrap) {
  display: inline-flex;
  align-items: center;
  width: auto;
  flex: 0 0 auto;
  height: 36px;
  border-radius: 10px;
  background: #f3f4f6;
  padding: 3px;
}

.api-tabs > :deep(.el-tabs__header .el-tabs__nav-wrap::after),
.api-tabs > :deep(.el-tabs__header .el-tabs__active-bar) {
  display: none;
}

.api-tabs > :deep(.el-tabs__header .el-tabs__nav-scroll),
.api-tabs > :deep(.el-tabs__header .el-tabs__nav) {
  display: inline-flex;
  width: auto;
  flex: 0 0 auto;
  height: 100%;
}

.api-tabs > :deep(.el-tabs__header .el-tabs__item) {
  height: 30px;
  padding: 0 18px;
  border-radius: var(--ath-radius-md);
  color: var(--ath-text-muted);
  font-size: var(--ath-font-sm);
  font-weight: var(--ath-weight-medium);
  line-height: 30px;
}

.api-tabs > :deep(.el-tabs__header .el-tabs__item.is-active) {
  background: #ffffff;
  color: var(--ath-text-strong);
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.12);
}

.api-tabs > :deep(.el-tabs__content) {
  flex: 1 1 auto;
  min-height: 0;
  height: 100%;
  background: #ffffff;
}

.api-tabs > :deep(.el-tabs__content > .el-tab-pane) {
  min-height: 0;
  height: 100%;
}

.shell-card {
  border: 0;
  border-radius: 0;
  background: #ffffff;
  box-shadow: none;
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
  grid-template-columns: 290px minmax(0, 1fr);
  min-height: 0;
  height: 100%;
  background: #ffffff;
  overflow: hidden;
}

.scenario-workbench {
  display: grid;
  grid-template-columns: 256px minmax(0, 1fr);
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.ms-scenario-workbench {
  border: 0;
  border-radius: 0;
  background: #fff;
  box-shadow: none;
}

.scenario-module-pane {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-width: 0;
  min-height: 0;
  padding: 0;
  border-right: 1px solid #e5e7eb;
  background: #fff;
}

.scenario-module-pane .ms-like-sidebar-tools {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
  padding: 16px 16px 12px;
}

.scenario-sidebar-primary {
  display: flex !important;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100% !important;
  height: 36px;
  min-height: 36px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  padding: 0;
}

.scenario-sidebar-primary :deep(.el-icon) {
  margin-right: 0;
}

.scenario-sidebar-primary :deep(> span) {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.scenario-sidebar-primary-icon {
  width: 16px;
  height: 16px;
}

.scenario-sidebar-search {
  display: block;
  width: 100% !important;
  height: 36px;
}

.scenario-sidebar-search :deep(.el-input) {
  width: 100% !important;
}

.scenario-sidebar-search :deep(.el-input__wrapper) {
  width: 100%;
  height: 36px;
  min-height: 36px;
  box-sizing: border-box;
  border-radius: 8px;
  box-shadow: inset 0 0 0 1px #e5e7eb;
}

.scenario-sidebar-search :deep(.el-input__inner) {
  height: 36px;
  line-height: 36px;
}

.scenario-sidebar-search-icon {
  width: 16px;
  height: 16px;
  color: #9ca3af;
}

.scenario-sidebar-search :deep(.el-input__wrapper.is-focus) {
  box-shadow: inset 0 0 0 1px #3b82f6, 0 0 0 2px rgba(59, 130, 246, 0.14);
}

.scenario-module-pane .ms-like-directory-shell {
  flex: 1 1 auto;
  min-height: 0;
  padding: 0 12px 12px;
  overflow: hidden;
}

.scenario-directory-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 40px;
  margin: 0 -4px 8px;
  padding: 0 8px;
  border-bottom: 1px solid var(--ath-border);
  color: var(--ath-text-main);
  font-size: var(--ath-font-sm);
  font-weight: var(--ath-weight-semibold);
  line-height: var(--ath-line-sm);
}

.scenario-directory-title-main,
.scenario-directory-title-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.scenario-directory-title-main small {
  color: var(--ath-text-subtle);
  font-size: var(--ath-font-xs);
  font-weight: var(--ath-weight-medium);
  line-height: var(--ath-line-xs);
}

.scenario-directory-collapse-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ath-text-muted);
  cursor: pointer;
}

.scenario-directory-collapse-button:hover {
  background: var(--ath-bg-muted);
  color: var(--ath-primary);
}

.ms-scenario-side-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 8px;
}

.ms-scenario-primary-button,
.ms-scenario-ghost-button {
  width: 100%;
  height: 32px;
  border-radius: 4px;
  font-size: 13px;
}

.ms-scenario-primary-button {
  border-color: #3b82f6;
  background: #3b82f6;
  color: #fff;
}

.ms-scenario-primary-button:hover {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
}

.ms-scenario-ghost-button {
  border-color: #3b82f6;
  background: #fff;
  color: #2563eb;
}

.ms-scenario-module-search {
  margin-bottom: 14px;
}

.ms-scenario-module-search :deep(.el-input__wrapper) {
  border-radius: 4px;
  box-shadow: 0 0 0 1px #e6e8ef inset;
}

.scenario-module-toolbar,
.scenario-module-actions,
.scenario-list-toolbar,
.scenario-list-filters,
.scenario-step-toolbar,
.scenario-step-name,
.scenario-step-actions,
.scenario-request-inline,
.scenario-module-node,
.scenario-module-node-actions {
  display: flex;
  align-items: center;
}

.scenario-module-toolbar {
  gap: 8px;
  margin-bottom: 8px;
}

.scenario-module-toolbar .el-input {
  flex: 1;
  min-width: 0;
}

.scenario-module-actions {
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: 6px;
}

.scenario-module-tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-right: 4px;
}

.scenario-module-tree :deep(.el-tree-node__content) {
  height: 32px;
  border-radius: 8px;
  color: #374151;
  font-size: 14px;
}

.scenario-module-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: #eff6ff;
  color: #2563eb;
}

.scenario-module-tree :deep(.el-tree-node__content:hover) {
  background: #f3f4f6;
}

.scenario-module-node {
  justify-content: space-between;
  width: 100%;
  min-width: 0;
  gap: 8px;
  color: var(--el-text-color-regular);
}

.ms-scenario-folder-icon {
  flex: 0 0 auto;
  width: 14px;
  color: #8b8f9a;
  font-size: 12px;
}

.scenario-module-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scenario-module-count {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.scenario-module-node-actions {
  flex: 0 0 auto;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.scenario-module-node:hover .scenario-module-node-actions,
.scenario-module-node:focus-within .scenario-module-node-actions {
  opacity: 1;
}

.scenario-module-node-actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.scenario-module-node-actions button:hover {
  background: var(--el-fill-color-light);
  color: #2563eb;
}

.scenario-module-more {
  display: inline-flex;
  align-items: center;
  height: 22px;
  line-height: 1;
}

.ms-scenario-recycle {
  display: grid;
  grid-template-columns: 18px 1fr auto;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  margin: 10px -12px -12px;
  padding: 0 16px;
  border: 0;
  border-top: 1px solid #e5e7eb;
  background: #fff;
  color: #303640;
  cursor: pointer;
  text-align: left;
}

.ms-scenario-recycle:hover {
  background: #eff6ff;
  color: #2563eb;
}

.scenario-main-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: #fff;
}

.scenario-editor-tabs {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}

.scenario-editor-tab-strip {
  min-height: 40px;
  padding: 0;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
}

.scenario-editor-tab-strip .ms-like-tab-strip-main {
  min-height: 40px;
}

.scenario-editor-tab-strip .ms-like-editor-tab {
  height: 40px;
  padding: 0 16px;
  border-right: 1px solid #e5e7eb;
  border-radius: 0;
  background: #f9fafb;
  color: #6b7280;
  font-size: 14px;
}

.scenario-editor-tab-strip .ms-like-editor-tab.active {
  background: #ffffff;
  color: #111827;
}

.scenario-editor-tab-strip .ms-like-editor-tab.active::after {
  height: 2px;
  background: #3b82f6;
}

.scenario-editor-tab-strip .ms-like-tab-add,
.scenario-editor-tab-strip .scenario-editor-more-button {
  width: 32px;
  height: 39px;
  border-radius: 0;
  color: #9ca3af;
}

.scenario-editor-tab-strip .ms-like-tab-add:hover,
.scenario-editor-tab-strip .scenario-editor-more-button:hover {
  background: #f3f4f6;
  color: #4b5563;
}

.scenario-editor-tabs :deep(.el-tabs__header) {
  display: none;
}

.scenario-editor-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.scenario-editor-tabs :deep(.el-tabs__active-bar) {
  display: none;
}

.scenario-editor-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.ms-scenario-list-shell {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 270px);
}

.ms-scenario-list-toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px;
}

.ms-scenario-search {
  width: 223px;
}

.ms-scenario-search :deep(.el-input__wrapper) {
  height: 36px;
  min-height: 36px;
  border-radius: 8px;
  box-shadow: inset 0 0 0 1px #e5e7eb;
}

.ms-scenario-search :deep(.el-input__inner) {
  height: 36px;
  line-height: 36px;
}

.ms-scenario-view-select {
  width: 150px;
}

.ms-scenario-tool-button,
.ms-scenario-icon-button {
  height: 32px;
  border-radius: 4px;
}

.ms-scenario-icon-button {
  width: 34px;
  padding: 0;
}

.scenario-editor-tabs :deep(.el-tab-pane) {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.scenario-editor-more-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 0;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.scenario-list-toolbar {
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.scenario-list-filters {
  flex: 0 0 auto;
  gap: 10px;
}

.scenario-table {
  width: 100%;
}

.ms-scenario-table {
  flex: 1;
}

.ms-scenario-table :deep(.el-table__header th) {
  height: 44px;
  background: #fff;
  color: #4f5663;
  font-weight: 500;
}

.ms-scenario-table :deep(.el-table__row td) {
  height: 48px;
}

.ms-scenario-table :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  border-color: #3b82f6;
  background-color: #3b82f6;
}

.scenario-link {
  max-width: 100%;
  border: 0;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  text-align: left;
}

.ms-scenario-name-link {
  border: 0;
  background: transparent;
  color: #303640;
  cursor: pointer;
  text-align: left;
}

.ms-scenario-name-link:hover {
  color: #2563eb;
}

.ms-scenario-priority {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.ms-scenario-priority i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ef4444;
}

.ms-scenario-status {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 8px;
  border-radius: 4px;
  background: #e8efff;
  color: #3867d6;
  font-size: 12px;
}

.ms-scenario-filter-icon {
  color: #9097a3;
  font-size: 12px;
}

.ms-scenario-action {
  margin-right: 12px;
  border: 0;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  font-size: 13px;
}

.ms-scenario-action:hover {
  color: #1d4ed8;
}

.ms-scenario-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
  padding: 16px;
  color: #303640;
  font-size: 13px;
}

.ms-scenario-pagination button,
.ms-scenario-page-current {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  border: 1px solid #dbeafe;
  border-radius: 4px;
  background: #fff;
  color: #2563eb;
}

.ms-scenario-pagination button {
  cursor: pointer;
}

.ms-scenario-page-current {
  border-color: #3b82f6;
}

.scenario-edit-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 288px;
  height: 100%;
  min-height: 0;
  background: #fff;
}

.scenario-edit-main {
  min-width: 0;
  min-height: 0;
  border-right: 1px solid #e5e7eb;
}

.scenario-edit-toolbar {
  min-height: 100%;
}

.scenario-detail-tabs {
  height: 100%;
}

.scenario-detail-tabs :deep(.el-tabs__header) {
  display: block !important;
  margin: 0;
  padding: 0 16px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}

.scenario-detail-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: #e5e7eb;
}

.scenario-detail-tabs :deep(.el-tabs__nav-scroll) {
  display: flex;
  align-items: center;
}

.scenario-detail-tabs :deep(.el-tabs__nav) {
  display: flex;
  align-items: center;
  gap: 20px;
  height: 40px;
}

.scenario-detail-tabs :deep(.el-tabs__item) {
  position: relative;
  height: 40px;
  padding: 0;
  min-width: auto;
  background: transparent !important;
  border: 0 !important;
  box-shadow: none !important;
  color: #4b5563;
  font-size: 14px;
  line-height: 40px;
  border-radius: 0;
  transition: color 0.18s ease;
}

.scenario-detail-tabs :deep(.el-tabs__item::after) {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 28px;
  height: 2px;
  border-radius: 999px;
  background: #2563eb;
  transform: translateX(-50%) scaleX(0);
  transform-origin: center;
  transition: transform 0.18s ease;
}

.scenario-detail-tabs :deep(.el-tabs__item:hover) {
  color: #2563eb;
}

.scenario-detail-tabs :deep(.el-tabs__item.is-active) {
  color: #2563eb;
  font-weight: 500;
}

.scenario-detail-tabs :deep(.el-tabs__item.is-active::after) {
  transform: translateX(-50%) scaleX(1);
}

.scenario-detail-tabs :deep(.el-tabs__active-bar) {
  display: none;
}

.scenario-detail-tabs :deep(.el-tabs__content) {
  height: calc(100% - 40px);
  padding: 0;
  overflow: hidden;
}

.scenario-detail-tabs :deep(.el-tab-pane) {
  height: 100%;
  padding: 0;
  overflow: hidden;
}

.scenario-property-panel {
  display: flex;
  min-height: 0;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
  padding: 12px;
  background: #ffffff;
}

.scenario-property-card {
  display: flex;
  flex: 1 1 auto;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  background: #f9fafb;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.scenario-property-header {
  display: grid;
  gap: 10px;
  padding: 12px;
  border-bottom: 1px solid #f3f4f6;
}

.scenario-property-environment-select {
  width: 100%;
}

.scenario-property-environment-select :deep(.el-select__wrapper),
.scenario-property-field :deep(.el-input__wrapper),
.scenario-property-field :deep(.el-select__wrapper),
.scenario-property-field :deep(.el-textarea__inner) {
  min-height: 36px;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: inset 0 0 0 1px #e5e7eb;
}

.scenario-property-field :deep(.el-textarea__inner) {
  min-height: 76px;
  padding: 8px 12px;
}

.scenario-property-run-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.scenario-property-run-button,
.scenario-property-save-button,
.scenario-property-delete-button {
  height: 36px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
}

.scenario-property-run-button {
  border-color: #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.scenario-property-run-button:hover {
  border-color: #1d4ed8;
  background: #1d4ed8;
  color: #ffffff;
}

.scenario-property-save-button {
  border-color: #e5e7eb;
  background: #ffffff;
  color: #374151;
}

.scenario-property-save-button:hover {
  border-color: #d1d5db;
  background: #f3f4f6;
  color: #111827;
}

.scenario-property-delete-button {
  width: 100%;
  border-color: #fecaca;
  background: #ffffff;
  color: #dc2626;
}

.scenario-property-body {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  padding: 12px;
  padding-bottom: 16px;
  overflow-y: auto;
}

.scenario-property-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
}

.scenario-property-field b {
  color: #ef4444;
  font-weight: 600;
}

.scenario-property-field .el-select,
.scenario-property-field .el-input,
.scenario-property-field .el-textarea {
  width: 100%;
}

.scenario-step-toolbar {
  justify-content: space-between;
  min-height: 42px;
  padding: 0 16px;
  border-bottom: 1px solid #f3f4f6;
  background: #f9fafb;
  color: #6b7280;
  font-size: 14px;
}

.scenario-step-toolbar :deep(.el-button--primary) {
  height: 30px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
}

.scenario-detail-tabs .section-title {
  min-height: 42px;
  padding: 0 16px;
  border-bottom: 1px solid #f3f4f6;
  background: #f9fafb;
  color: #6b7280;
  font-size: 14px;
  font-weight: 500;
}

.scenario-detail-tabs .section-title :deep(.el-button) {
  color: #2563eb;
}

.scenario-detail-tabs .kv-row,
.scenario-detail-tabs .scenario-assertion-row {
  margin: 12px 16px 0;
}

.scenario-detail-tabs :deep(.el-table) {
  padding: 12px 16px;
}

.scenario-step-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 34px;
  border: 1px dashed #93c5fd;
  border-radius: 4px;
  color: #2563eb;
  cursor: pointer;
}

.scenario-step-empty:hover {
  border-color: #3b82f6;
  background: #eff6ff;
}

:global(.scenario-add-step-menu) {
  min-width: 214px;
}

:global(.scenario-add-step-menu .el-dropdown-menu) {
  padding: 10px 8px;
}

:global(.scenario-add-step-menu .el-dropdown-menu__item) {
  height: 30px;
  border-radius: 4px;
  color: #303640;
  line-height: 30px;
}

:global(.scenario-add-step-menu .el-dropdown-menu__item:hover) {
  background: #eff6ff;
  color: #2563eb;
}

:global(.scenario-add-step-group-title) {
  padding: 7px 12px 4px;
  color: #8b95a5;
  font-size: 12px;
  line-height: 18px;
}

.scenario-import-drawer :deep(.el-drawer__header) {
  min-height: 56px;
  margin-bottom: 0;
  padding: 0 16px;
  border-bottom: 1px solid #e5e7eb;
}

.scenario-import-drawer :deep(.el-drawer__body) {
  padding: 0;
}

.scenario-import-drawer :deep(.el-drawer__footer) {
  padding: 14px 16px;
  border-top: 1px solid #e5e7eb;
}

.scenario-import-shell {
  display: flex;
  height: 100%;
  min-height: 680px;
  flex-direction: column;
}

.scenario-import-tabs {
  flex: 0 0 auto;
}

.scenario-import-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 16px;
}

.scenario-import-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background: #e5e7eb;
}

.scenario-import-content {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  flex: 1;
  min-height: 0;
}

.scenario-import-tree-pane {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border-right: 1px solid #e5e7eb;
}

.scenario-import-tree-controls {
  display: flex;
  gap: 8px;
}

.scenario-import-tree-controls .el-select {
  min-width: 0;
  flex: 1;
}

.scenario-import-protocol {
  max-width: 90px;
}

.scenario-import-tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.scenario-import-tree :deep(.el-tree-node__content) {
  height: 32px;
  border-radius: 4px;
}

.scenario-import-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: #eff6ff;
  color: #2563eb;
}

.scenario-import-tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-width: 0;
  gap: 8px;
}

.scenario-import-tree-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scenario-import-tree-count {
  flex: 0 0 auto;
  color: #98a2b3;
  font-size: 12px;
}

.scenario-import-table-pane {
  min-width: 0;
  padding: 16px;
}

.scenario-import-table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.scenario-import-table-title {
  color: #303640;
  font-size: 14px;
  font-weight: 600;
}

.scenario-import-table-title span {
  color: #667085;
  font-weight: 400;
}

.scenario-import-search {
  width: 260px;
}

.scenario-import-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.scenario-import-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #667085;
  font-size: 13px;
}

.scenario-import-summary strong {
  color: #2563eb;
  font-weight: 600;
}

.scenario-import-actions {
  display: flex;
  gap: 10px;
}

.scenario-step-tree {
  display: flex;
  flex-direction: column;
  gap: 0;
  height: calc(100% - 42px);
  overflow: auto;
}

.scenario-step-node {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  min-height: 48px;
  padding: 8px 16px;
  border: 0;
  border-bottom: 1px solid #f3f4f6;
  border-radius: 0;
  background: #fff;
}

.scenario-step-node + .scenario-step-node {
  border-radius: 0;
}

.scenario-step-node:last-child {
  border-bottom: 1px solid #f3f4f6;
  border-radius: 0;
}

.scenario-step-node.is-nested {
  background: #fbfcff;
}

.scenario-step-node:hover {
  background: rgba(239, 246, 255, 0.42);
}

.scenario-step-node-left,
.scenario-step-node-main,
.scenario-step-node-actions {
  display: flex;
  align-items: center;
  min-width: 0;
}

.scenario-step-node-left {
  gap: 8px;
}

.scenario-step-order {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #f3f4f6;
  color: #6b7280;
  font-size: 12px;
  font-weight: 600;
}

.scenario-step-run-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: 0;
  border-radius: 50%;
  background: #3b82f6;
  color: #fff;
  cursor: pointer;
}

.scenario-step-node-main {
  gap: 8px;
  padding: 0 12px;
}

.scenario-step-node-actions {
  justify-content: flex-end;
  gap: 6px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.scenario-step-node:hover .scenario-step-node-actions {
  opacity: 1;
}

.scenario-step-icon-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: #667085;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.scenario-step-icon-action:hover {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
}

.scenario-step-icon-action.is-text {
  color: #2563eb;
}

.scenario-step-icon-action.is-danger:hover {
  border-color: #fecaca;
  background: #fef2f2;
  color: #dc2626;
}

.scenario-step-resource-select {
  width: 150px;
}

.scenario-step-method-select {
  width: 112px;
}

.scenario-step-path-input {
  width: 260px;
}

.scenario-step-path-text,
.scenario-step-script-text {
  min-width: 0;
  overflow: hidden;
  color: #667085;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scenario-step-path-text {
  max-width: 320px;
}

.scenario-step-script-text {
  max-width: 220px;
}

.scenario-step-condition-input {
  width: 120px;
}

.scenario-step-operator-select {
  width: 92px;
}

.scenario-step-method,
.scenario-step-inline-label {
  flex: 0 0 auto;
  font-size: 13px;
}

.scenario-step-type-badge {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 8px;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  line-height: 22px;
  white-space: nowrap;
}

.scenario-step-type-badge.is-custom-request {
  border-color: #3b82f6;
  color: #2563eb;
}

.scenario-step-type-badge.is-api,
.scenario-step-type-badge.is-api-case {
  border-color: #3b82f6;
  color: #2563eb;
}

.scenario-step-type-badge.is-if-controller {
  border-color: #ec4899;
  color: #db2777;
}

.scenario-step-type-badge.is-loop-controller,
.scenario-step-type-badge.is-once-only-controller {
  border-color: #f97316;
  color: #ea580c;
}

.scenario-step-type-badge.is-constant-timer {
  border-color: #f59e0b;
  color: #d97706;
}

.scenario-step-type-badge.is-script {
  border-color: #14b8a6;
  color: #0d9488;
}

.scenario-step-name-text {
  min-width: 0;
  overflow: hidden;
  color: #111827;
  font-size: 14px;
  font-weight: 400;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scenario-step-name-text.is-strong {
  font-weight: 600;
}

.scenario-step-name-button {
  max-width: 220px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.scenario-step-name-button:hover {
  color: #2563eb;
}

.scenario-step-name-edit-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #3b82f6;
  cursor: pointer;
  font-size: 15px;
  opacity: 0;
}

.scenario-step-node-main:hover .scenario-step-name-edit-button {
  opacity: 1;
}

.scenario-step-name-edit-button:hover {
  color: #1d4ed8;
}

.scenario-step-name-inline-input {
  width: 220px;
}

.scenario-step-name-inline-input :deep(.el-input__wrapper) {
  min-height: 26px;
  box-shadow: 0 0 0 1px #3b82f6 inset;
}

.scenario-step-tree :deep(.el-input__wrapper),
.scenario-step-tree :deep(.el-select__wrapper),
.scenario-step-tree :deep(.el-input-number) {
  min-height: 24px;
  box-shadow: 0 0 0 1px #e5e7eb inset;
}

.scenario-step-tree :deep(.el-input__inner),
.scenario-step-tree :deep(.el-select__placeholder),
.scenario-step-tree :deep(.el-select__selected-item) {
  font-size: 12px;
}

:deep(.scenario-step-config-drawer .el-drawer__body) {
  padding: 0;
}

:deep(.scenario-script-operation-drawer .el-drawer__header) {
  display: flex;
  align-items: center;
  flex: 0 0 48px;
  height: 48px;
  min-height: 48px;
  margin-bottom: 0;
  padding: 0 16px !important;
  border-bottom: 1px solid #e5e7eb;
  box-sizing: border-box;
}

:deep(.scenario-script-operation-drawer .el-drawer__title) {
  margin: 0;
  line-height: 1;
}

.scenario-script-drawer-title {
  color: #111827;
  font-size: 15px;
  font-weight: 600;
  line-height: 1;
}

.scenario-step-config-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.scenario-step-config-request-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 18px 0;
}

.scenario-drawer-title-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  min-width: 0;
}

.scenario-drawer-step-order {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: #d0d5dd;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.scenario-drawer-step-title {
  min-width: 0;
  overflow: hidden;
  color: #111827;
  font-size: 16px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scenario-drawer-title-input {
  width: min(420px, 56vw);
}

.scenario-drawer-title-input :deep(.el-input__wrapper) {
  border-radius: 2px;
  box-shadow: 0 0 0 1px #8b5cf6 inset;
}

.scenario-drawer-title-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #8b5cf6 inset;
}

.scenario-custom-title-edit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 2px;
  background: transparent;
  color: #344054;
  cursor: pointer;
  font-size: 15px;
  line-height: 1;
}

.scenario-custom-title-edit:hover {
  border-color: #d0d5dd;
  background: #f2f4f7;
  color: #2563eb;
}

.scenario-step-config-name {
  flex: 1;
  min-width: 0;
}

.scenario-step-config-env {
  flex: 0 0 auto;
  color: #667085;
  font-size: 13px;
}

.scenario-step-protocol-select {
  width: 96px;
  flex: 0 0 auto;
}

.scenario-step-config-request-row .request-url-input {
  flex: 1;
}

.scenario-step-config-tabs {
  margin: 12px 18px 0;
}

:deep(.scenario-script-operation-drawer .scenario-step-config-tabs) {
  margin-top: 6px;
}

.scenario-step-config-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding: 14px 18px 18px;
}

.scenario-step-response-shell {
  flex: 0 0 340px;
}

.scenario-step-config-body .request-section {
  margin: 0;
}

.scenario-system-request-method {
  min-width: 56px;
}

.scenario-system-request-body {
  background: #fff;
}

.scenario-system-body-type {
  margin-bottom: 10px;
  color: #667085;
  font-size: 13px;
}

.scenario-system-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.scenario-system-list-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  color: #344054;
  font-size: 13px;
}

.scenario-step-config-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.scenario-script-editor-pane {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  padding: 4px 8px 0;
}

.scenario-script-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 8px;
  color: #344054;
  font-size: 13px;
}

.scenario-script-name-input {
  width: min(70%, 640px);
  min-width: 280px;
}

.scenario-script-field-label,
.scenario-script-editor-header {
  color: #344054;
  font-size: 13px;
  font-weight: 500;
}

.scenario-script-mode-tabs {
  display: flex;
  gap: 0;
  padding: 8px 8px 0;
}

.scenario-script-mode-tab-tooltip {
  display: inline-flex;
}

.scenario-script-mode-tab {
  height: 32px;
  padding: 0 14px;
  border: 1px solid #e5e7eb;
  border-right-width: 0;
  background: #f8fafc;
  color: #667085;
  font-size: 13px;
  cursor: pointer;
}

.scenario-script-mode-tab:first-child {
  border-top-left-radius: 4px;
  border-bottom-left-radius: 4px;
}

.scenario-script-mode-tab:last-child {
  border-right-width: 1px;
  border-top-right-radius: 4px;
  border-bottom-right-radius: 4px;
}

.scenario-script-mode-tab.active {
  background: #fff;
  color: #2563eb;
  border-color: #dbeafe;
  box-shadow: inset 0 0 0 1px #dbeafe;
}

.scenario-script-mode-tab.is-disabled {
  border-right-width: 1px;
  border-top-right-radius: 4px;
  border-bottom-right-radius: 4px;
  color: #98a2b3;
  background: #f8fafc;
  cursor: not-allowed;
}

.scenario-script-editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 48px;
  margin-top: 14px;
  padding: 0 10px;
  border-top: 1px solid #e5e7eb;
  border-bottom: 1px solid #e5e7eb;
}

.scenario-script-editor-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.scenario-script-code-shell {
  flex: 1 1 auto;
  min-height: 420px;
  height: calc(100vh - 315px);
  padding: 12px 10px 0;
}

.scenario-script-code-shell :deep(.ms-monaco-editor) {
  height: 100%;
}

.scenario-script-assertion-pane {
  flex: 1 1 auto;
  min-height: 0;
  margin: 0;
  padding: 16px;
  overflow: auto;
}

.scenario-script-result-shell {
  margin: 0 16px 16px;
  min-height: 220px;
}

.scenario-script-result-shell .response-error-banner {
  margin-top: 0;
}

.scenario-step-table {
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.scenario-step-table-header,
.scenario-step-line {
  display: grid;
  grid-template-columns: minmax(260px, 1.4fr) 160px minmax(280px, 1.6fr) 220px;
  gap: 12px;
  align-items: center;
  min-width: 980px;
}

.scenario-step-table-header {
  min-height: 40px;
  padding: 0 12px;
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-weight: 600;
}

.scenario-step-line {
  min-height: 48px;
  padding: 8px 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.scenario-step-name {
  min-width: 0;
  gap: 8px;
}

.scenario-step-config,
.scenario-request-inline {
  min-width: 0;
}

.scenario-request-inline {
  gap: 8px;
}

.scenario-request-inline .el-select {
  width: 120px;
  flex: 0 0 auto;
}

.scenario-request-inline .el-input {
  flex: 1;
  min-width: 0;
}

.scenario-step-actions {
  justify-content: flex-end;
  gap: 4px;
  white-space: nowrap;
}

.scenario-assertion-row {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) 220px minmax(160px, 1fr) 80px auto;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}

.ms-like-layout .ms-like-main {
  overflow: hidden;
}

.ms-like-sidebar {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-height: 0;
  border-right: 1px solid #e5e7eb;
  background: #fff;
  padding: 0;
}

.ms-like-sidebar-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 16px 16px 0;
  background: #ffffff;
}

.ms-like-sidebar-primary,
.ms-like-sidebar-secondary,
.ms-like-send-button,
.ms-like-curl-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 36px;
  border-radius: var(--ath-radius-md);
  border: 1px solid transparent;
  font-size: var(--ath-font-sm);
  font-weight: var(--ath-weight-medium);
  line-height: var(--ath-line-sm);
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.ms-like-sidebar-primary,
.ms-like-send-button {
  border-color: var(--ath-primary);
  background: var(--ath-primary);
  color: #fff;
}

.ms-like-sidebar-primary:hover,
.ms-like-send-button:hover:not(:disabled) {
  border-color: var(--ath-primary-hover);
  background: var(--ath-primary-hover);
}

.ms-like-sidebar-secondary,
.ms-like-curl-button {
  border-color: var(--ath-input-border);
  background: #fff;
  color: var(--ath-text-main);
}

.ms-like-sidebar-secondary:hover,
.ms-like-curl-button:hover {
  background: #f9fafb;
}

.ms-like-send-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.ms-like-button-icon,
.ms-like-tab-action-icon,
.tree-lucide-action-icon {
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
}

.ms-like-sidebar-search {
  position: relative;
  padding: 12px 16px 0;
  background: #ffffff;
}

.ms-like-sidebar-search .el-input {
  width: 100%;
}

.ms-like-sidebar-search :deep(.el-input__wrapper) {
  height: 36px;
  min-height: 36px;
  border-radius: var(--ath-radius-md);
  background: var(--ath-bg-page);
  box-shadow: inset 0 0 0 1px var(--ath-input-border);
  padding-left: 34px;
}

.ms-like-sidebar-search :deep(.el-input__wrapper:hover) {
  box-shadow: inset 0 0 0 1px #9ca3af;
}

.ms-like-sidebar-search :deep(.el-input.is-focus .el-input__wrapper) {
  box-shadow: inset 0 0 0 1px var(--ath-blue), 0 0 0 2px rgba(59, 130, 246, 0.16);
}

.ms-like-search-icon {
  position: absolute;
  left: 28px;
  top: 50%;
  z-index: 2;
  width: 16px;
  height: 16px;
  transform: translateY(-50%);
  color: #9ca3af;
  pointer-events: none;
}

.ms-like-directory-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--ath-text-main);
  font-size: var(--ath-font-sm);
  font-weight: var(--ath-weight-semibold);
  line-height: var(--ath-line-sm);
  min-height: 40px;
  margin-top: 12px;
  margin-right: 12px;
  margin-left: 12px;
  padding: 0 4px;
  border-bottom: 1px solid var(--ath-border);
  background: #ffffff;
}

.ms-like-directory-title-main {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  gap: 8px;
}

.ms-like-directory-title-main span:last-child {
  color: var(--ath-text-subtle);
  font-size: var(--ath-font-xs);
  font-weight: var(--ath-weight-medium);
  line-height: var(--ath-line-xs);
}

.ms-like-directory-collapse {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ath-text-muted);
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.ms-like-directory-collapse:hover {
  background: var(--ath-bg-muted);
  color: var(--ath-primary);
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

.ms-like-response-header {
  min-height: 40px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--ath-border);
  background: #ffffff;
}

.ms-like-tree-title,
.ms-like-response-title {
  color: var(--ath-text-main);
  font-size: var(--ath-font-sm);
  font-weight: var(--ath-weight-medium);
  line-height: var(--ath-line-sm);
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
  border: 0;
  border-radius: 0;
  padding: 8px 8px 12px;
  background: #fff;
}

.ms-like-directory-tree :deep(.el-tree-node__content) {
  min-height: 32px;
  height: auto;
  border-radius: var(--ath-radius-md);
  transition: background-color 0.15s ease;
}

.ms-like-directory-tree :deep(.el-tree-node__content:hover) {
  background: var(--ath-bg-muted);
}

.ms-like-directory-tree :deep(.el-tree-node__expand-icon.is-leaf) {
  color: transparent;
}

.ms-like-directory-tree :deep(.el-tree-node > .el-tree-node__content:has(.ms-like-directory-node.is-root) .el-tree-node__expand-icon) {
  visibility: hidden;
  pointer-events: none;
}

.ms-like-directory-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: var(--ath-blue-soft);
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
  gap: 7px;
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
  width: 16px;
  height: 16px;
  color: #60a5fa;
}

.tree-node-folder-svg.is-open .tree-node-folder-icon {
  color: #3b82f6;
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
.ms-like-directory-node:focus-within .ms-like-directory-actions,
.ms-like-directory-node.is-root .ms-like-directory-actions {
  opacity: 1;
}

.ms-like-directory-label,
.ms-like-directory-count {
  font-size: var(--ath-font-sm);
  line-height: var(--ath-line-sm);
}

.ms-like-directory-label {
  color: var(--ath-text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ms-like-directory-node.is-root .ms-like-directory-label {
  font-weight: var(--ath-weight-medium);
  color: var(--ath-text-strong);
}

.ms-like-directory-count {
  color: var(--ath-text-subtle);
  font-size: var(--ath-font-xs);
  line-height: var(--ath-line-xs);
}

.ms-like-directory-node.is-root .ms-like-directory-count {
  color: var(--ath-text-subtle);
}

.tree-icon-button {
  width: 24px;
  height: 24px;
  padding: 0;
}

.definition-tree-more-button {
  border-radius: 4px;
  color: var(--ath-text-muted);
}

.definition-tree-more-button:hover,
.definition-tree-more-button:focus-visible {
  background: var(--ath-blue-soft);
  color: var(--ath-primary);
}

:global(.definition-tree-action-menu) {
  min-width: 86px;
}

:global(.definition-tree-action-menu .el-dropdown-menu) {
  padding: 6px;
}

:global(.definition-tree-action-menu .definition-tree-action-item) {
  min-width: 72px;
  justify-content: center;
  padding: 8px 14px;
  line-height: 1.2;
}

:global(.definition-tree-action-menu .definition-tree-action-danger) {
  color: #e5484d;
}

:global(.definition-tree-action-menu .definition-tree-action-danger:hover) {
  background: #fff1f1;
  color: #d92d20;
}

.tree-collapse-icon {
  font-size: 14px;
  color: var(--ath-text-muted);
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
  background: #eff6ff;
}

.ms-like-tree-item-name {
  overflow: hidden;
  color: var(--ath-text-main);
  font-size: var(--ath-font-sm);
  line-height: var(--ath-line-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ms-like-method {
  font-size: var(--ath-font-xs);
  font-weight: var(--ath-weight-semibold);
  line-height: var(--ath-line-xs);
}

.ms-like-method.method-get {
  color: #15803d;
}

.ms-like-method.method-post {
  color: #ea580c;
}

.ms-like-method.method-put {
  color: #2563eb;
}

.ms-like-method.method-patch,
.ms-like-method.method-options {
  color: #7c3aed;
}

.ms-like-method.method-trace {
  color: #6b7280;
}

.ms-like-method.method-head {
  color: #15803d;
}

.ms-like-method.method-delete {
  color: #dc2626;
}

.case-drawer-readonly-section :deep(.processor-toolbar),
.case-drawer-readonly-section :deep(.assertion-toolbar),
.case-drawer-readonly-section :deep(.processor-detail-actions),
.case-drawer-readonly-section :deep(.assertion-detail-actions),
.case-drawer-readonly-section :deep(.processor-list-actions),
.case-drawer-readonly-section :deep(.assertion-list-actions),
.case-drawer-readonly-section :deep(.extractor-panel-header .add-row-button),
.case-drawer-readonly-section :deep(.assertion-table .add-row-button),
.case-drawer-readonly-section :deep(.editor-actions),
.case-drawer-readonly-section :deep(.extractor-more-trigger),
.case-drawer-readonly-section :deep(.extractor-delete-trigger),
.case-drawer-readonly-section :deep(.fast-extraction-suffix-button) {
  display: none !important;
}

.case-drawer-readonly-section :deep(.processor-sidebar .el-switch),
.case-drawer-readonly-section :deep(.assertion-sidebar .el-switch),
.case-drawer-readonly-section :deep(.processor-detail .el-input__wrapper),
.case-drawer-readonly-section :deep(.processor-detail .el-textarea__inner),
.case-drawer-readonly-section :deep(.processor-detail .el-select__wrapper),
.case-drawer-readonly-section :deep(.processor-detail .el-input-number),
.case-drawer-readonly-section :deep(.processor-detail .el-radio-group),
.case-drawer-readonly-section :deep(.assertion-detail .el-checkbox),
.case-drawer-readonly-section :deep(.assertion-detail .el-input__wrapper),
.case-drawer-readonly-section :deep(.assertion-detail .el-textarea__inner),
.case-drawer-readonly-section :deep(.assertion-detail .el-select__wrapper),
.case-drawer-readonly-section :deep(.assertion-detail .el-input-number),
.case-drawer-readonly-section :deep(.assertion-detail .el-radio-group),
.case-drawer-readonly-section :deep(.assertion-detail .fast-extraction-suffix-button) {
  pointer-events: none;
}

.case-drawer-readonly-section :deep(.processor-detail .monaco-editor textarea),
.case-drawer-readonly-section :deep(.assertion-detail .monaco-editor textarea) {
  pointer-events: none;
}

.case-drawer-readonly-section :deep(.processor-detail .el-input__wrapper),
.case-drawer-readonly-section :deep(.processor-detail .el-select__wrapper),
.case-drawer-readonly-section :deep(.processor-detail .el-input-number),
.case-drawer-readonly-section :deep(.assertion-detail .el-input__wrapper),
.case-drawer-readonly-section :deep(.assertion-detail .el-select__wrapper),
.case-drawer-readonly-section :deep(.assertion-detail .el-input-number) {
  background: #f8fafc;
}

.request-method-select {
  width: 104px;
  min-width: 104px;
  flex: 0 0 104px;
}

.request-method-option {
  font-size: var(--ath-font-sm);
  font-weight: var(--ath-weight-semibold);
  line-height: var(--ath-line-sm);
}

.request-method-get {
  color: #15803d;
}

.request-method-post {
  color: #ea580c;
}

.request-method-put {
  color: #2563eb;
}

.request-method-patch,
.request-method-options {
  color: #7c3aed;
}

.request-method-trace {
  color: #6b7280;
}

.request-method-head {
  color: #15803d;
}

.request-method-delete {
  color: #dc2626;
}

.request-method-select :deep(.el-select__wrapper) {
  min-height: 38px;
  padding: 0 10px;
  border-radius: var(--ath-radius-md);
}

.request-method-select :deep(.el-select__selected-item) {
  justify-content: center;
  font-size: var(--ath-font-sm);
  font-weight: var(--ath-weight-semibold);
  line-height: var(--ath-line-sm);
}

.request-method-select.request-method-get :deep(.el-select__wrapper) {
  border-color: #86efac;
  background: #dcfce7;
}

.request-method-select.request-method-post :deep(.el-select__wrapper) {
  border-color: #fdba74;
  background: #ffedd5;
}

.request-method-select.request-method-put :deep(.el-select__wrapper) {
  border-color: #93c5fd;
  background: #dbeafe;
}

.request-method-select.request-method-patch :deep(.el-select__wrapper),
.request-method-select.request-method-options :deep(.el-select__wrapper) {
  border-color: #c4b5fd;
  background: #ede9fe;
}

.request-method-select.request-method-trace :deep(.el-select__wrapper) {
  border-color: #d1d5db;
  background: #f3f4f6;
}

.request-method-select.request-method-head :deep(.el-select__wrapper) {
  border-color: #86efac;
  background: #dcfce7;
}

.request-method-select.request-method-delete :deep(.el-select__wrapper) {
  border-color: #fca5a5;
  background: #fee2e2;
}

.request-method-select.request-method-get :deep(.el-select__selected-item) {
  color: #15803d;
}

.request-method-select.request-method-post :deep(.el-select__selected-item) {
  color: #ea580c;
}

.request-method-select.request-method-put :deep(.el-select__selected-item) {
  color: #2563eb;
}

.request-method-select.request-method-patch :deep(.el-select__selected-item),
.request-method-select.request-method-options :deep(.el-select__selected-item) {
  color: #7c3aed;
}

.request-method-select.request-method-trace :deep(.el-select__selected-item) {
  color: #6b7280;
}

.request-method-select.request-method-head :deep(.el-select__selected-item) {
  color: #15803d;
}

.request-method-select.request-method-delete :deep(.el-select__selected-item) {
  color: #dc2626;
}

:deep(.request-method-popper .el-select-dropdown__item) {
  font-weight: 600;
}

:deep(.request-method-popper .request-method-get) {
  color: #15803d;
}

:deep(.request-method-popper .request-method-post) {
  color: #ea580c;
}

:deep(.request-method-popper .request-method-put) {
  color: #2563eb;
}

:deep(.request-method-popper .request-method-patch),
:deep(.request-method-popper .request-method-options) {
  color: #7c3aed;
}

:deep(.request-method-popper .request-method-trace) {
  color: #6b7280;
}

:deep(.request-method-popper .request-method-head) {
  color: #15803d;
}

:deep(.request-method-popper .request-method-delete) {
  color: #dc2626;
}

.case-drawer-method-tag.request-method-get {
  color: #15803d;
}

.case-drawer-method-tag.request-method-post {
  color: #ea580c;
}

.case-drawer-method-tag.request-method-put {
  color: #2563eb;
}

.case-drawer-method-tag.request-method-patch,
.case-drawer-method-tag.request-method-options {
  color: #7c3aed;
}

.case-drawer-method-tag.request-method-trace {
  color: #6b7280;
}

.case-drawer-method-tag.request-method-head {
  color: #15803d;
}

.case-drawer-method-tag.request-method-delete {
  color: #dc2626;
}

.ms-like-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: #ffffff;
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
  gap: 0;
  box-sizing: border-box;
  height: 40px;
  min-height: 40px;
  border: 0;
  border-bottom: 1px solid #e5e7eb;
  border-radius: 0;
  padding: 0;
  background: #ffffff;
  overflow: hidden;
}

.ms-like-tab-strip-main {
  display: flex;
  align-items: center;
  gap: 0;
  height: 100%;
  min-width: 0;
  flex: 1 1 auto;
}

.ms-like-tab-nav {
  flex: 0 1 auto;
  min-width: 0;
  height: 100%;
  display: flex;
  align-items: stretch;
  gap: 0;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.ms-like-tab-nav::-webkit-scrollbar {
  display: none;
}

.ms-like-tab-strip-actions,
.ms-like-tab-strip-primary {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
}

.ms-like-tab-strip-primary {
  margin-left: auto;
  gap: 8px;
  padding-bottom: 6px;
}

.ms-like-tab-scroll-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  flex: 0 0 auto;
  transition: background-color 0.16s ease, color 0.16s ease;
}

.ms-like-tab-scroll-button:hover:not(:disabled) {
  background: #f5f7fa;
  color: #344054;
}

.ms-like-tab-scroll-button:disabled {
  color: #c0c4cc;
  cursor: not-allowed;
}

.ms-like-editor-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  box-sizing: border-box;
  height: 40px;
  min-height: 40px;
  border: 0;
  border-right: 1px solid #e5e7eb;
  border-bottom: 3px solid transparent;
  border-radius: 0;
  background: #f9fafb;
  color: var(--ath-text-muted);
  padding: 0 16px;
  font-size: var(--ath-font-sm);
  line-height: var(--ath-line-sm);
  cursor: pointer;
}

.ms-like-editor-tab.active,
.ms-like-editor-tab:hover {
  background: #ffffff;
  color: var(--ath-text-strong);
}

.ms-like-editor-tab.active {
  border-bottom-color: var(--ath-blue);
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
  display: inline-flex;
  align-items: center;
  min-width: 0;
  height: 20px;
  line-height: var(--ath-line-sm);
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ms-like-editor-tab .ms-like-method {
  display: inline-flex;
  align-items: center;
  height: 18px;
  line-height: 18px;
  flex: 0 0 auto;
}

.ms-like-editor-tab-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #3b82f6;
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
  background: #f3f4f6;
  color: #111827;
  opacity: 1;
}

.ms-like-editor-tab-close :deep(.el-icon) {
  font-size: 14px;
  font-weight: 700;
}

.ms-like-tab-add {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 40px;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--el-text-color-secondary);
  line-height: 1;
  cursor: pointer;
  flex: 0 0 auto;
  transition: background-color 0.16s ease, color 0.16s ease;
}

.ms-like-tab-add:hover {
  background: #f9fafb;
  color: #344054;
}

.ms-like-tab-strip .scenario-editor-more-button {
  width: 36px;
  height: 40px;
  border-left: 0;
  border-radius: 0;
  background: #ffffff;
}

.ms-like-tab-strip .scenario-editor-more-button:hover {
  background: #f9fafb;
}

.scenario-header-environment-select {
  width: 180px;
  flex: 0 0 auto;
}

.scenario-header-run-button,
.scenario-header-save-button {
  height: 32px;
  padding: 0 14px;
  border-radius: 6px;
}

.ms-like-editor-shell {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  gap: 0;
  padding: 0;
  background: #ffffff;
  border: 0;
  border-radius: 0;
  overflow: hidden;
}

.ms-like-editor-shell.without-response .ms-like-request-shell {
  flex: 0 0 auto;
}

.ms-like-request-shell {
  display: flex;
  flex: 0 0 auto;
  min-height: 0;
  min-width: 0;
  flex-direction: column;
  gap: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: #fff;
  overflow: visible;
  box-shadow: none;
}

.ms-like-editor-scroll {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
  flex-direction: column;
  overflow-x: hidden;
  overflow-y: auto;
  background: #ffffff;
  scrollbar-color: #d7dbe3 transparent;
  scrollbar-width: thin;
}

.ms-like-request-row {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) 96px auto;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
}

.ms-like-url-compose {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr) 68px;
  align-items: center;
  min-width: 0;
  border: 1px solid var(--ath-input-border);
  border-radius: var(--ath-radius-md);
  background: #ffffff;
  overflow: hidden;
}

.ms-like-url-compose .request-method-select,
.ms-like-url-compose .request-url-input {
  min-width: 0;
}

.ms-like-url-compose .request-method-select :deep(.el-select__wrapper),
.ms-like-url-compose .request-url-input :deep(.el-input__wrapper) {
  border-radius: 0;
  box-shadow: none;
  min-height: 38px;
  font-size: var(--ath-font-sm);
  line-height: var(--ath-line-sm);
}

.ms-like-url-compose .request-method-select :deep(.el-select__wrapper) {
  border-right: 1px solid var(--ath-border);
  background: #f8fafc;
}

.ms-like-url-compose .request-url-input :deep(.el-input__wrapper) {
  padding-inline: 14px;
}

.ms-like-curl-button {
  height: 38px;
  border-width: 0 0 0 1px;
  border-color: #e5e7eb;
  border-radius: 0;
  color: var(--ath-primary);
  background: #ffffff;
  font-size: var(--ath-font-xs);
  font-weight: var(--ath-weight-medium);
}

.ms-like-send-button {
  flex: 0 0 96px;
  width: 96px;
  min-width: 96px;
  height: 38px;
  border-radius: var(--ath-radius-md);
  padding: 0 16px;
  white-space: nowrap;
}

.ms-like-save-dropdown :deep(.el-button-group) {
  display: inline-flex;
}

.ms-like-save-dropdown :deep(.el-button) {
  height: 38px;
  border-color: #d1d5db;
  background: #ffffff;
  color: var(--ath-text-main);
  font-size: var(--ath-font-sm);
  font-weight: var(--ath-weight-medium);
}

.ms-like-save-dropdown :deep(.el-button:first-child) {
  border-radius: 8px 0 0 8px;
  padding-inline: 14px;
}

.ms-like-save-dropdown :deep(.el-button:last-child) {
  border-radius: 0 8px 8px 0;
  padding-inline: 9px;
}

.ms-like-save-dropdown :deep(.el-button:hover:not(.is-disabled)) {
  border-color: #9ca3af;
  background: #f9fafb;
  color: #111827;
}

.ms-like-save-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.ms-like-request-content-panel {
  display: flex;
  flex: 0 0 auto;
  min-height: 0;
  min-width: 0;
  flex-direction: column;
  margin: 0;
  border: 0;
  border-radius: 0;
  background: #fff;
  overflow: visible;
}

.ms-like-editor-scroll > .ms-like-request-content-panel {
  position: sticky;
  top: 0;
  z-index: 30;
  background: #ffffff;
}

.ms-like-top-tabs,
.ms-like-response-tabs {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 0;
  height: 46px;
  min-height: 46px;
  overflow: hidden;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 16px;
  background: #ffffff;
}

.ms-like-response-tabs {
  height: 41px;
  min-height: 41px;
}

.ms-like-top-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  box-sizing: border-box;
  height: 45px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--ath-text-muted);
  font-size: var(--ath-font-sm);
  font-weight: var(--ath-weight-normal);
  line-height: var(--ath-line-sm);
  padding: 0 12px;
  cursor: pointer;
  white-space: nowrap;
}

.ms-like-top-tab.active {
  border-bottom-color: var(--ath-primary);
  color: var(--ath-primary);
  font-weight: var(--ath-weight-medium);
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
  display: flex;
  flex: 0 0 auto;
  min-height: 320px;
  overflow: visible;
  background: #fff;
  padding: 8px 16px 16px;
}

.case-list-request-body {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  overflow: hidden auto;
  padding: 14px 16px;
}

.ms-like-body-section {
  display: flex;
  flex: 0 0 auto;
  align-self: stretch;
  flex-direction: column;
  gap: 8px;
  overflow: visible;
}

.ms-like-body-type-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  width: fit-content;
  min-height: 24px;
  margin-bottom: 0;
  border: 0;
  border-radius: 0;
  background: #ffffff;
  overflow: visible;
  flex: 0 0 auto;
}

.ms-like-body-mode-shell {
  display: flex;
  flex: 0 0 auto;
  min-height: 300px;
  flex-direction: column;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  overflow: visible;
}

.ms-like-body-mode-shell.is-none {
  min-height: 300px;
  border: 0;
  border-radius: var(--ath-radius-sm);
  background: #f9fafb;
}

.ms-like-body-mode-shell > .ms-monaco-editor,
.ms-like-body-mode-shell > .ms-like-table-surface,
.ms-like-body-mode-shell > .ms-like-form-panel,
.ms-like-body-mode-shell > .ms-like-empty-body {
  width: 100%;
  flex: 0 0 auto;
  min-height: 300px;
}

.ms-like-body-mode-shell.is-none > .ms-like-empty-body {
  min-height: 300px;
}

.ms-like-body-mode-shell > .ms-monaco-editor {
  display: flex;
  flex-direction: column;
  min-height: 300px;
  height: auto;
  padding: 0;
  border: 0;
  border-radius: 0;
}

.ms-like-body-mode-shell > .ms-monaco-editor :deep(.ms-monaco-editor__toolbar) {
  align-items: center;
  box-sizing: border-box;
  height: 40px;
  padding: 0 12px;
  border-bottom: 0;
}

.ms-like-body-mode-shell > .ms-monaco-editor :deep(.ms-monaco-editor__body) {
  flex: 1 1 auto;
  min-height: 300px;
}

.ms-like-body-mode-shell > .ms-monaco-editor.is-adaptive :deep(.ms-monaco-editor__body) {
  min-height: 0;
}

.ms-like-body-mode-shell > .ms-like-table-surface,
.ms-like-body-mode-shell > .ms-like-form-panel {
  min-height: 0;
}

.ms-like-body-mode-shell > .ms-like-table-surface {
  height: 100%;
  border: 0;
  border-radius: 0;
}

.ms-like-body-chip {
  height: 24px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #ffffff;
  color: var(--ath-text-muted);
  font-size: var(--ath-font-xs);
  font-weight: var(--ath-weight-medium);
  line-height: var(--ath-line-xs);
  padding: 0 12px;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.ms-like-body-chip:first-child {
  border-radius: var(--ath-radius-sm);
}

.ms-like-body-chip:last-child {
  border-radius: var(--ath-radius-sm);
}

.ms-like-body-chip.active {
  background: #eff6ff;
  border-color: #93c5fd;
  color: #2563eb;
}

.ms-like-body-chip:hover {
  background: var(--ath-bg-muted);
  color: var(--ath-text-main);
}

.ms-like-body-chip.active:hover {
  background: #eff6ff;
  color: #2563eb;
}

.ms-like-body-chip.ghost {
  background: #fafafa;
  color: #9ca3af;
}

.ms-like-empty-body {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100px;
  border: 0;
  border-radius: var(--ath-radius-sm);
  background: #f9fafb;
  color: #9ca3af;
  font-size: 13px;
}

.ms-like-table-surface {
  gap: 0;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
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
  grid-template-columns: 24px 32px minmax(240px, 1fr) 150px minmax(240px, 1fr) 200px 80px minmax(220px, 1fr) 90px;
}

.ms-like-param-table-grid--header {
  grid-template-columns: 24px 32px repeat(3, minmax(0, 1fr)) 80px;
}

.ms-like-param-table-grid--body-form {
  grid-template-columns: 24px 32px 240px 150px 240px 200px minmax(220px, 1fr) 90px;
}

.ms-like-table-header {
  box-sizing: border-box;
  height: 40px;
  min-height: 40px;
  border-bottom: 1px solid #e5e7eb;
  background: #f9fafb;
  color: #6b7280;
  font-size: 12px;
  padding-right: 10px;
}

.ms-like-param-table .ms-like-table-header {
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
  line-height: 16px;
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
  padding-left: 0;
}

.ms-like-type-header {
  padding-left: 30px;
}

.ms-like-length-header {
  padding-left: 22px;
}

.ms-like-param-table .ms-like-header-input-title,
.ms-like-param-table .ms-like-table-header > span {
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
  line-height: 22px;
}

.ms-like-header-input-cell {
  min-width: 0;
}

.ms-like-table-row {
  min-height: 40px;
  border-bottom: 1px solid #f3f4f6;
  padding: 5px 10px 5px 0;
  transition: background-color 0.15s ease;
}

.ms-like-table-row:last-of-type {
  border-bottom: 0;
}

.ms-like-table-row:hover {
  background: #f9fafb;
}

.ms-like-table-row.is-dragging {
  opacity: 0.6;
}

.ms-like-table-row.is-drag-over {
  background: #eff6ff;
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
.ms-like-type-field :deep(.el-select),
.ms-like-header-input-cell :deep(.el-input) {
  width: 100%;
}

.ms-like-type-field {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.ms-like-required-button {
  width: 20px;
  height: 20px;
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
.ms-like-type-field :deep(.el-select),
.ms-like-table-row :deep(.el-input),
.ms-like-table-row :deep(.el-select) {
  width: 100%;
}

.ms-like-checkbox-field :deep(.el-input__wrapper),
.ms-like-name-field :deep(.el-input__wrapper),
.ms-like-type-field :deep(.el-select__wrapper),
.ms-like-table-row :deep(.el-input__wrapper),
.ms-like-table-row :deep(.el-select__wrapper) {
  box-shadow: inset 0 0 0 1px transparent;
  background: transparent;
  border-radius: 6px;
  min-height: 28px;
  padding: 0 8px;
  transition: box-shadow 0.15s ease, background-color 0.15s ease;
}

.ms-like-checkbox-field :deep(.el-input__inner),
.ms-like-name-field :deep(.el-input__inner),
.ms-like-type-field :deep(.el-select__placeholder),
.ms-like-type-field :deep(.el-select__selected-item),
.ms-like-table-row :deep(.el-input__inner),
.ms-like-table-row :deep(.el-select__placeholder),
.ms-like-table-row :deep(.el-select__selected-item) {
  font-size: 12px;
}

.ms-like-checkbox-field :deep(.el-input__wrapper:hover),
.ms-like-name-field :deep(.el-input__wrapper:hover),
.ms-like-type-field :deep(.el-select__wrapper:hover),
.ms-like-table-row :deep(.el-input__wrapper:hover),
.ms-like-table-row :deep(.el-select__wrapper:hover) {
  box-shadow: inset 0 0 0 1px #d0d5dd;
  background: #fff;
}

.ms-like-checkbox-field :deep(.el-input.is-focus .el-input__wrapper),
.ms-like-name-field :deep(.el-input.is-focus .el-input__wrapper),
.ms-like-type-field :deep(.el-select.is-focus .el-select__wrapper),
.ms-like-type-field :deep(.el-select__wrapper.is-focused),
.ms-like-table-row :deep(.el-input.is-focus .el-input__wrapper),
.ms-like-table-row :deep(.el-select.is-focus .el-select__wrapper),
.ms-like-table-row :deep(.el-select__wrapper.is-focused) {
  box-shadow: inset 0 0 0 1px #3b82f6;
  background: #fff;
}

.ms-like-switch-cell {
  color: #667085;
  font-size: 12px;
}

.ms-like-length-range-cell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: #6b7280;
  font-size: 12px;
}

.ms-like-length-range-cell :deep(.el-input-number) {
  width: 100%;
}

.ms-like-file-param-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.ms-like-file-pick,
.ms-like-file-clear {
  min-width: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
  line-height: 28px;
  padding: 0 8px;
}

.ms-like-file-pick {
  flex: 1;
  overflow: hidden;
  color: #374151;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ms-like-file-pick:hover {
  background: #f9fafb;
  color: #2563eb;
}

.ms-like-file-clear {
  flex: 0 0 auto;
  color: #c75450;
}

.ms-like-file-param-cell small {
  flex: 0 0 auto;
  color: #6b7280;
  font-size: 11px;
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
  color: #2563eb;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
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

:global(.api-case-drawer .ms-like-param-table) {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  flex-shrink: 1;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
  scrollbar-color: #cbd5e1 transparent;
}

:global(.api-case-drawer .api-case-drawer-body),
:global(.api-case-drawer .ms-like-request-body),
:global(.api-case-drawer .request-section),
:global(.api-case-drawer .ms-like-body-mode-shell) {
  width: 100%;
  min-width: 0;
  max-width: 100%;
}

:global(.api-case-drawer .ms-like-body-mode-shell > .ms-like-table-surface) {
  min-width: 0;
  max-width: 100%;
}

:global(.api-case-drawer .ms-like-param-table::-webkit-scrollbar) {
  height: 8px;
}

:global(.api-case-drawer .ms-like-param-table::-webkit-scrollbar-thumb) {
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.72);
}

:global(.api-case-drawer .ms-like-param-table::-webkit-scrollbar-track) {
  background: transparent;
}

:global(.api-case-drawer .ms-like-param-table .ms-like-table-header),
:global(.api-case-drawer .ms-like-param-table .ms-like-table-row) {
  min-width: 100%;
  padding-right: 0;
}

:global(.api-case-drawer .ms-like-param-table--query .ms-like-table-header.ms-like-param-table-grid--query),
:global(.api-case-drawer .ms-like-param-table--query .ms-like-table-row.ms-like-param-table-grid--query) {
  min-width: 1480px;
}

:global(.api-case-drawer .ms-like-param-table--header .ms-like-table-header.ms-like-param-table-grid--header),
:global(.api-case-drawer .ms-like-param-table--header .ms-like-table-row.ms-like-param-table-grid--header) {
  min-width: 940px;
}

:global(.api-case-drawer .ms-like-param-table--body-form .ms-like-table-header.ms-like-param-table-grid--body-form),
:global(.api-case-drawer .ms-like-param-table--body-form .ms-like-table-row.ms-like-param-table-grid--body-form) {
  min-width: 1400px;
}

:global(.api-case-drawer .ms-like-param-table .ms-like-link-button),
:global(.api-case-drawer .ms-like-param-table .ms-like-row-remove) {
  position: sticky;
  right: 0;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 28px;
  background: #fff;
  box-shadow: -1px 0 0 #e5e7eb;
}

:global(.api-case-drawer .ms-like-param-table .ms-like-table-header .ms-like-link-button) {
  z-index: 3;
  background: #f9fafb;
}

:global(.api-case-drawer .ms-like-param-table .ms-like-table-row:hover .ms-like-row-remove) {
  background: #f9fafb;
}

.ms-like-add-row {
  align-self: flex-start;
  padding: 9px 10px 11px;
}

.ms-like-form-panel {
  gap: 0;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.ms-like-form-row {
  display: grid;
  grid-template-columns: 128px minmax(0, 1fr);
  align-items: center;
  gap: 18px;
  border-bottom: 1px solid #f3f4f6;
  padding: 12px 18px;
}

.ms-like-form-row:last-of-type {
  border-bottom: 0;
}

.ms-like-form-row.align-start {
  align-items: start;
}

.ms-like-form-label {
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
}

.ms-like-form-control {
  width: 100%;
}

.ms-like-form-control :deep(.el-input__wrapper),
.ms-like-form-control :deep(.el-textarea__inner),
.ms-like-form-control.full-width :deep(.el-input__wrapper) {
  border-radius: 8px;
  box-shadow: inset 0 0 0 1px #d1d5db;
}

.ms-like-form-control :deep(.el-input__wrapper:hover),
.ms-like-form-control :deep(.el-textarea__inner:hover) {
  box-shadow: inset 0 0 0 1px #9ca3af;
}

.ms-like-form-control :deep(.el-input__wrapper.is-focus),
.ms-like-form-control :deep(.el-textarea__inner:focus) {
  box-shadow: inset 0 0 0 1px #3b82f6, 0 0 0 2px rgba(59, 130, 246, 0.12);
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
  color: #6b7280;
  font-size: 12px;
  padding: 12px 18px;
  border-top: 1px solid #f3f4f6;
  background: #f9fafb;
}

.ms-like-code-editor :deep(textarea) {
  border-radius: 4px;
  background: #fbfbfd;
}

.ms-like-response-shell {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-height: 0;
  min-width: 0;
  border: 0;
  border-top: 0;
  border-radius: 0;
  background: #fff;
  padding: 0;
  overflow: hidden;
  box-shadow: none;
}

.ms-like-editor-scroll > .ms-like-response-shell {
  flex: 0 0 auto;
  min-height: 360px;
  border-top: 1px solid #e5e7eb;
  overflow: visible;
}

.ms-like-editor-scroll > .ms-like-response-shell > .ms-like-response-header {
  position: sticky;
  top: 46px;
  z-index: 20;
}

.ms-like-response-content-panel {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
  flex-direction: column;
  border: 0;
  border-radius: 0;
  background: #fff;
  overflow: hidden;
}

.ms-like-editor-scroll > .ms-like-response-shell > .ms-like-response-content-panel {
  min-height: 300px;
}

.ms-like-response-metrics {
  color: var(--ath-text-subtle);
  font-size: var(--ath-font-xs);
  line-height: var(--ath-line-xs);
}

.ms-like-response-metric.is-success {
  color: #039855;
}

.ms-like-response-metric.is-failed {
  color: #d92d20;
}

.ms-like-response-metric.is-slow {
  color: #dc6803;
}

.ms-like-response-body {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  flex-direction: column;
  padding: 12px;
}

.ms-like-editor-scroll > .ms-like-response-shell .ms-like-response-body {
  min-height: 260px;
  overflow: visible;
}

.ms-like-response-empty {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  justify-content: center;
  min-height: 0;
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

.case-drawer-view-tabs {
  margin-bottom: 8px;
}

.case-drawer-history-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0 0 12px;
}

.case-drawer-history-list-shell {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.case-drawer-history-table {
  width: 100%;
}

.case-drawer-history-table :deep(.el-table__header-wrapper th.el-table__cell) {
  height: 40px;
  background: #f8fafc;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 500;
}

.case-drawer-history-table :deep(.el-table__row) {
  cursor: pointer;
}

.case-drawer-history-table :deep(.el-table__row.current-row > td.el-table__cell),
.case-drawer-history-table :deep(.el-table__row:hover > td.el-table__cell) {
  background: #eef4ff;
}

.case-drawer-history-result {
  font-weight: 600;
}

.case-drawer-history-result.is-passed {
  color: #16a34a;
}

.case-drawer-history-result.is-not-passed {
  color: #dc6803;
}

.case-drawer-history-result.is-no-assertion {
  color: var(--el-text-color-secondary);
}

.case-drawer-history-result.is-failed {
  color: #dc2626;
}

:global(.definition-import-dialog .el-dialog) {
  padding: 0;
  border-radius: 16px;
  overflow: hidden;
}

:global(.definition-import-dialog .el-dialog__header),
:global(.definition-import-dialog .el-dialog__body) {
  margin: 0;
  padding: 0;
}

:global(.api-soft-dialog .el-dialog) {
  padding: 0;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 20px 25px -5px rgba(15, 23, 42, 0.12), 0 8px 10px -6px rgba(15, 23, 42, 0.12);
}

:global(.api-soft-dialog .el-dialog__header) {
  display: flex;
  align-items: center;
  min-height: 64px;
  margin: 0;
  padding: 20px 24px;
  border-bottom: 1px solid #f3f4f6;
}

:global(.api-soft-dialog .el-dialog__title) {
  color: #111827;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}

:global(.api-soft-dialog .el-dialog__headerbtn) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  top: 16px;
  right: 18px;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: #9ca3af;
}

:global(.api-soft-dialog .el-dialog__headerbtn:hover) {
  background: #f3f4f6;
  color: #374151;
}

:global(.api-soft-dialog .el-dialog__headerbtn .el-dialog__close) {
  color: currentColor;
  font-size: 16px;
}

:global(.api-soft-dialog .el-dialog__body) {
  padding: 24px;
}

:global(.api-soft-dialog .el-dialog__footer) {
  padding: 16px 24px;
  border-top: 1px solid #f3f4f6;
  background: #ffffff;
}

:global(.api-soft-dialog .el-dialog__footer .el-button:not(.el-button--primary)),
:global(.api-soft-drawer .el-drawer__footer .el-button:not(.el-button--primary)) {
  --el-button-text-color: #111827;
  --el-button-border-color: #d1d5db;
  --el-button-bg-color: #ffffff;
  --el-button-hover-text-color: #111827;
  --el-button-hover-border-color: #9ca3af;
  --el-button-hover-bg-color: #f9fafb;
  --el-button-active-text-color: #111827;
  --el-button-active-border-color: #9ca3af;
  --el-button-active-bg-color: #f3f4f6;
  border-color: #d1d5db;
  background: #ffffff;
  color: #111827;
  font-weight: 500;
}

:global(.api-soft-dialog .el-dialog__footer .el-button:not(.el-button--primary):hover),
:global(.api-soft-dialog .el-dialog__footer .el-button:not(.el-button--primary):focus),
:global(.api-soft-drawer .el-drawer__footer .el-button:not(.el-button--primary):hover),
:global(.api-soft-drawer .el-drawer__footer .el-button:not(.el-button--primary):focus) {
  border-color: #9ca3af;
  background: #f9fafb;
  color: #111827;
}

:global(.api-soft-dialog .dialog-footer .el-button:not(.el-button--primary)),
:global(.api-soft-drawer .dialog-footer .el-button:not(.el-button--primary)) {
  --el-button-text-color: #111827;
  color: #111827;
}

:global(.api-soft-drawer .el-drawer) {
  border-radius: 16px 0 0 16px;
  overflow: hidden;
  box-shadow: -12px 0 24px rgba(15, 23, 42, 0.1);
}

:global(.api-soft-drawer .el-drawer__close-btn) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: #9ca3af;
  transition: color 0.18s ease, background-color 0.18s ease;
}

:global(.api-soft-drawer .el-drawer__close-btn:hover),
:global(.api-soft-drawer .el-drawer__close-btn:focus-visible) {
  background: #f3f4f6;
  color: #374151;
}

:global(.api-soft-drawer .el-drawer__close) {
  color: currentColor;
  font-size: 16px;
}

:global(.api-soft-drawer .el-drawer__header) {
  display: flex;
  align-items: center;
  min-height: 64px;
  margin: 0;
  padding: 20px 24px;
  border-bottom: 1px solid #f3f4f6;
}

:global(.api-soft-drawer .el-drawer__title) {
  color: #111827;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}

:global(.api-soft-drawer .el-drawer__body) {
  padding: 24px;
  background: #ffffff;
}

:global(.api-soft-drawer .el-drawer__footer) {
  padding: 16px 24px;
  border-top: 1px solid #f3f4f6;
  background: #ffffff;
}

.definition-import-modal {
  overflow: hidden;
  background: #ffffff;
}

.definition-import-header,
.definition-import-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.definition-import-header {
  padding: 20px 24px;
  border-bottom: 1px solid #f3f4f6;
}

.definition-import-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #111827;
  font-size: 16px;
  font-weight: 600;
}

.definition-import-title-icon {
  width: 20px;
  height: 20px;
  color: #3b82f6;
}

.definition-import-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
}

.definition-import-close:hover {
  background: #f3f4f6;
  color: #374151;
}

.definition-import-close svg {
  width: 16px;
  height: 16px;
}

.definition-import-body {
  display: grid;
  gap: 20px;
  padding: 24px;
}

.definition-import-section {
  display: grid;
  gap: 12px;
}

.definition-import-section-title {
  color: #374151;
  font-size: 14px;
  font-weight: 600;
}

.definition-import-format-list {
  display: grid;
  gap: 8px;
}

.definition-import-format {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px;
  border: 2px solid #e5e7eb;
  border-radius: 12px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.definition-import-format:hover {
  border-color: #d1d5db;
  background: #f9fafb;
}

.definition-import-format.active.is-green {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.definition-import-format.active.is-orange {
  border-color: #fed7aa;
  background: #fff7ed;
}

.definition-import-format.active.is-purple {
  border-color: #e9d5ff;
  background: #faf5ff;
}

.definition-import-format-icon,
.definition-import-check {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
}

.definition-import-format-icon {
  width: 36px;
  height: 36px;
  background: #f3f4f6;
  color: #9ca3af;
}

.definition-import-format.active.is-green .definition-import-format-icon {
  background: #dcfce7;
  color: #16a34a;
}

.definition-import-format.active.is-orange .definition-import-format-icon {
  background: #ffedd5;
  color: #ea580c;
}

.definition-import-format.active.is-purple .definition-import-format-icon {
  background: #f3e8ff;
  color: #9333ea;
}

.definition-import-format-icon svg {
  width: 18px;
  height: 18px;
}

.definition-import-format-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.definition-import-format-copy span {
  color: #111827;
  font-size: 14px;
  font-weight: 600;
}

.definition-import-format-copy small {
  overflow: hidden;
  color: #6b7280;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.definition-import-check {
  width: 18px;
  height: 18px;
  border: 2px solid #d1d5db;
  color: #2563eb;
}

.definition-import-check svg {
  width: 12px;
  height: 12px;
}

.definition-import-mode-switch {
  display: inline-flex;
  width: fit-content;
  gap: 4px;
  padding: 3px;
  border-radius: 10px;
  background: #f3f4f6;
}

.definition-import-mode-switch button {
  height: 30px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #4b5563;
  font-size: 14px;
  font-weight: 500;
  padding: 0 14px;
  cursor: pointer;
}

.definition-import-mode-switch button.active {
  background: #ffffff;
  color: #2563eb;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.12);
}

.definition-import-url :deep(.el-input__wrapper) {
  min-height: 42px;
  border-radius: 8px;
  box-shadow: inset 0 0 0 1px #d1d5db;
}

.definition-import-url :deep(.el-input.is-focus .el-input__wrapper),
.definition-import-url :deep(.el-input__wrapper.is-focus) {
  box-shadow: inset 0 0 0 1px #3b82f6, 0 0 0 2px rgba(59, 130, 246, 0.16);
}

.definition-import-upload {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 112px;
  border: 2px dashed #d1d5db;
  border-radius: 12px;
  background: #ffffff;
  color: #4b5563;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.definition-import-upload:hover {
  border-color: #60a5fa;
  background: #eff6ff;
}

.definition-import-upload input {
  display: none;
}

.definition-import-upload-icon {
  width: 24px;
  height: 24px;
  color: #9ca3af;
}

.definition-import-upload small {
  color: #9ca3af;
  font-size: 12px;
}

.definition-import-footer {
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #f3f4f6;
  background: #f9fafb;
}

.definition-import-cancel,
.definition-import-submit {
  height: 36px;
  border-radius: 8px;
  border: 1px solid transparent;
  padding: 0 16px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.definition-import-cancel {
  border-color: #d1d5db;
  background: #ffffff;
  color: #374151;
}

.definition-import-cancel:hover {
  background: #f3f4f6;
}

.definition-import-submit {
  border-color: #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.definition-import-submit:hover {
  border-color: #1d4ed8;
  background: #1d4ed8;
}

:global(.api-ai-case-drawer .el-drawer__header) {
  padding: 0;
  margin: 0;
  border-bottom: 1px solid #f3f4f6;
}

:global(.api-ai-case-drawer .el-drawer__body) {
  padding: 0;
  background: #ffffff;
}

:global(.api-ai-case-drawer .el-drawer__footer) {
  padding: 0;
  border-top: 1px solid #f3f4f6;
}

.ai-generation-tab-spinner {
  position: relative;
  width: 14px;
  height: 14px;
  border: 2px solid #bfdbfe;
  border-top-color: #2563eb;
  border-radius: 9999px;
}

.ai-generation-tab-spinner.spinning {
  animation: ai-generation-spin 0.8s linear infinite;
}

.ai-generation-tab-finished-icon {
  display: block;
  width: 14px;
  height: 14px;
  flex: 0 0 auto;
}

@keyframes ai-generation-spin {
  to {
    transform: rotate(360deg);
  }
}

.ai-generation-tab-icon {
  width: 14px;
  height: 14px;
  color: #2563eb;
}

.ai-generation-workspace {
  position: relative;
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  background: #ffffff;
  padding: 14px 16px 0;
}

.ai-generation-page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 2px 0 12px;
}

.ai-generation-page-header h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
}

.ai-generation-title-line {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.ai-generation-title-source {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding-left: 12px;
  border-left: 1px solid #e5e7eb;
  color: #64748b;
  font-size: 12px;
  font-weight: 400;
  line-height: var(--ath-line-sm);
}

.ai-generation-source-name,
.ai-generation-source-path {
  display: inline-block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-generation-source-name {
  max-width: 160px;
  color: #475569;
}

.ai-generation-source-path {
  max-width: 360px;
  color: #344054;
}

.ai-generation-header-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-width: 86px;
  height: 30px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  color: #475569;
  cursor: pointer;
  font-size: 12px;
  padding: 0 12px;
}

.ai-generation-header-action.is-stop {
  border-color: #d1d5db;
  color: #374151;
}

.ai-generation-header-action svg {
  width: 13px;
  height: 13px;
  color: #64748b;
}


.ai-generation-detail-workspace {
  display: flex;
  min-height: 0;
  flex: 1 1 auto;
  flex-direction: column;
  overflow: hidden;
  padding-bottom: 0;
}

.ai-generation-detail-status-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 40px;
  border-bottom: 1px solid #f1f5f9;
}

.ai-generation-detail-status-tabs {
  display: inline-flex;
  overflow: hidden;
  min-width: 360px;
  border-radius: 8px;
  background: #f5f7fb;
  padding: 2px;
}

.ai-generation-detail-status-tabs button {
  min-width: 112px;
  height: 28px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
}

.ai-generation-detail-status-tabs button.active {
  background: #ffffff;
  color: #334155;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.12);
}

.ai-generation-detail-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  border-bottom: 1px solid #edf2f7;
}

.ai-generation-detail-search {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: 160px;
  height: 28px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #ffffff;
  padding: 0 8px;
}

.ai-generation-detail-search svg {
  width: 14px;
  height: 14px;
  color: #94a3b8;
}

.ai-generation-detail-search input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  color: #334155;
  font-size: 12px;
}

.ai-generation-detail-search input::placeholder {
  color: #cbd5e1;
}

.ai-generation-detail-filter {
  width: 112px;
}

.ai-generation-detail-filter.is-wide {
  width: 168px;
}

.ai-generation-detail-filter :deep(.el-select__wrapper) {
  min-height: 28px;
  border-radius: 6px;
  box-shadow: inset 0 0 0 1px #e5e7eb;
}

.ai-generation-detail-filter :deep(.el-select__placeholder),
.ai-generation-detail-filter :deep(.el-select__selected-item) {
  color: #64748b;
  font-size: 12px;
}

.ai-generation-detail-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.ai-generation-detail-actions button,
.ai-generation-row-actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 28px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 500;
  padding: 0 10px;
}

.ai-generation-detail-actions button:disabled,
.ai-generation-row-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.ai-generation-detail-actions svg,
.ai-generation-row-actions svg {
  width: 13px;
  height: 13px;
}

.ai-generation-run-selected,
.ai-generation-row-run {
  border: 0;
  background: #334155;
  color: #ffffff;
}

.ai-generation-accept-selected,
.ai-generation-row-accept {
  border: 0;
  background: #5b7cff;
  color: #ffffff;
}

.ai-generation-discard-selected,
.ai-generation-row-discard {
  border: 1px solid #e5e7eb;
  background: #ffffff;
  color: #475569;
}

.ai-generation-detail-table {
  min-height: 0;
  flex: 1 1 auto;
  overflow: auto;
  scrollbar-color: rgba(148, 163, 184, 0.64) transparent;
  scrollbar-width: thin;
}

.ai-generation-detail-table::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.ai-generation-detail-table::-webkit-scrollbar-thumb {
  border: 2px solid transparent;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.64);
  background-clip: padding-box;
}

.ai-generation-detail-table::-webkit-scrollbar-track {
  background: transparent;
}

.ai-generation-empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 180px;
  color: #64748b;
  font-size: 13px;
}

.ai-generation-empty-state svg {
  width: 16px;
  height: 16px;
  color: #3b82f6;
}

.ai-generation-detail-head,
.ai-generation-detail-row {
  display: grid;
  grid-template-columns: 32px minmax(320px, 1fr) 170px 92px 110px 190px;
  align-items: center;
  column-gap: 12px;
}

.ai-generation-detail-head {
  position: sticky;
  top: 0;
  z-index: 1;
  min-height: 34px;
  border-bottom: 1px solid #eef2f7;
  background: #ffffff;
  color: #64748b;
  font-size: 13px;
  font-weight: 500;
}

.ai-generation-detail-head span {
  text-align: left;
}

.ai-generation-detail-row {
  min-height: 42px;
  border-bottom: 1px solid #edf2f7;
  color: #334155;
  cursor: pointer;
  font-size: 13px;
}

.ai-generation-detail-row:hover,
.ai-generation-detail-row.active {
  background: #f4f7fb;
}

.ai-generation-detail-row.active {
  box-shadow: none;
}

.ai-generation-row-selector {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  justify-self: center;
}

.ai-generation-row-index {
  color: #94a3b8;
  font-size: 12px;
  line-height: 1;
}

.ai-generation-row-loading {
  width: 16px;
  height: 16px;
  color: #3b82f6;
}

.ai-generation-row-checkbox {
  display: none;
}

.ai-generation-detail-row:hover .ai-generation-row-index,
.ai-generation-detail-row.active .ai-generation-row-index {
  display: none;
}

.ai-generation-detail-row:hover .ai-generation-row-checkbox,
.ai-generation-detail-row.active .ai-generation-row-checkbox {
  display: inline-flex;
}

.ai-generation-detail-name {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.ai-generation-detail-name span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-generation-inline-edit,
.ai-generation-row-more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
}

.ai-generation-inline-edit svg,
.ai-generation-row-more svg {
  width: 14px;
  height: 14px;
}

.ai-generation-detail-group-cell {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  min-width: 0;
}

.ai-generation-detail-group-type {
  color: #334155;
  font-size: 12px;
}

.ai-generation-detail-group-type.is-negative {
  color: #475569;
}

.ai-generation-run-result {
  color: #94a3b8;
}

.ai-generation-run-result.is-success {
  color: #16a34a;
}

.ai-generation-run-result.is-failed {
  color: #dc2626;
}

.ai-generation-row-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  min-width: 0;
  opacity: 0;
  pointer-events: none;
  white-space: nowrap;
}

.ai-generation-detail-row:hover .ai-generation-row-actions {
  opacity: 1;
  pointer-events: auto;
}

.ai-generation-row-actions button {
  flex: 0 0 auto;
  white-space: nowrap;
}

.ai-generation-case-url-line {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  border-bottom: 1px solid #edf2f7;
  padding: 0 16px;
}

.ai-generation-case-path {
  min-width: 0;
  flex: 1 1 auto;
  overflow: hidden;
  color: #334155;
  font-size: 13px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-generation-case-type-tag {
  flex: 0 0 auto;
  border-radius: 999px;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
  padding: 3px 10px;
}

.ai-generation-case-config-body {
  min-height: 278px;
  padding: 8px 16px 16px;
}

.ai-generation-case-body-mode {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 30px;
  margin-bottom: 8px;
}

.ai-generation-case-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 160px;
  color: #94a3b8;
  font-size: 13px;
}

.ai-generation-case-summary-grid {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr);
  gap: 12px 16px;
  max-width: 620px;
  color: #64748b;
  font-size: 13px;
  padding: 8px 0;
}

.ai-generation-case-summary-grid strong {
  min-width: 0;
  color: #334155;
  font-weight: 500;
}

.ai-case-drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 64px;
  padding: 16px 20px;
}

.ai-case-drawer-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
}

.ai-case-drawer-title svg {
  width: 18px;
  height: 18px;
  color: #2563eb;
}

.ai-case-drawer-subtitle {
  margin-top: 5px;
  max-width: 420px;
  overflow: hidden;
  color: #6b7280;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-case-drawer-body {
  display: block;
  min-height: 0;
  overflow: auto;
}

.ai-case-section {
  display: grid;
  gap: 12px;
  padding: 18px 24px;
  border-bottom: 1px solid #f3f4f6;
}

.ai-case-section-title,
.ai-case-option-group-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ai-case-section-title {
  color: #6b7280;
  font-size: 13px;
  font-weight: 400;
  line-height: 20px;
}

.ai-case-section-title small {
  color: #9ca3af;
  font-size: 12px;
  font-weight: 500;
}

.ai-case-option-groups {
  display: grid;
  gap: 20px;
}

.ai-case-option-group {
  display: grid;
  gap: 12px;
  padding-bottom: 18px;
  border-bottom: 1px solid #e5e7eb;
}

.ai-case-option-group:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.ai-case-option-group-title strong {
  color: #374151;
  font-size: 14px;
  font-weight: 500;
  line-height: 22px;
}

.ai-case-select-all-link {
  border: 0;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  font-size: 12px;
  padding: 0;
}

.ai-case-option-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(128px, max-content));
  gap: 12px 18px;
  justify-content: start;
}

.ai-case-option-list :deep(.el-checkbox) {
  height: 18px;
  margin-right: 0;
  color: #374151;
  font-size: 14px;
  font-weight: 400;
  min-width: 0;
  white-space: nowrap;
}

.ai-case-section :deep(.el-checkbox__input.is-checked .el-checkbox__inner),
.ai-case-section :deep(.el-checkbox__input.is-indeterminate .el-checkbox__inner) {
  border-color: #2563eb;
  background: #2563eb;
}

.ai-case-section :deep(.el-checkbox__input.is-checked + .el-checkbox__label) {
  color: #374151;
}

:global(.api-ai-case-drawer .el-switch.is-checked .el-switch__core) {
  border-color: #2563eb;
  background-color: #2563eb;
}


.ai-case-drawer-footer {
  display: grid;
  gap: 12px;
  padding: 18px 20px 20px;
  background: #ffffff;
}

.ai-case-form-section {
  gap: 16px;
  border-top: 1px solid #e5e7eb;
}

.ai-case-form-field {
  display: grid;
  gap: 6px;
}

.ai-case-form-field > span {
  color: #374151;
  font-size: 13px;
  font-weight: 500;
  line-height: 20px;
}

.ai-case-form-control {
  width: 100%;
}

.ai-case-form-control :deep(.el-select__wrapper),
.ai-case-form-control :deep(.el-textarea__inner) {
  border-radius: 8px;
  box-shadow: inset 0 0 0 1px #dbe2ea;
}

.ai-case-form-control :deep(.el-select__wrapper) {
  min-height: 36px;
  font-size: 13px;
}

.ai-case-form-control :deep(.el-select__wrapper.is-disabled) {
  background: #f9fafb;
  box-shadow: inset 0 0 0 1px #e5e7eb;
}

.ai-case-form-control :deep(.el-textarea__inner) {
  min-height: 98px !important;
  color: #374151;
  font-size: 13px;
  line-height: 1.5;
  padding: 12px;
}

.ai-case-form-control :deep(.el-select__wrapper.is-focused),
.ai-case-form-control :deep(.el-textarea__inner:focus) {
  box-shadow: inset 0 0 0 1px #2563eb, 0 0 0 2px rgba(37, 99, 235, 0.14);
}

.ai-case-form-switch {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  align-items: flex-start;
  gap: 10px;
}

.ai-case-form-switch :deep(.el-switch) {
  height: 20px;
}

.ai-case-form-switch > span {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.ai-case-form-switch strong {
  color: #374151;
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
}

.ai-case-form-switch small {
  color: #9ca3af;
  font-size: 12px;
  font-weight: 400;
  line-height: 1.55;
}


.ai-case-generate-submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 42px;
  border: 0;
  border-radius: 8px;
  background: #2563eb;
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.ai-case-generate-submit:hover {
  background: #1d4ed8;
}

.ai-case-generate-submit svg {
  width: 16px;
  height: 16px;
}


.ms-like-result-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.ms-like-result-pill.is-passed {
  background: #ecfdf3;
  color: #039855;
}

.ms-like-result-pill.is-not-passed {
  background: #fff7ed;
  color: #dc6803;
}

.ms-like-result-pill.is-no-assertion {
  background: #f5f7fa;
  color: var(--el-text-color-secondary);
}

.ms-like-result-pill.is-failed {
  background: #fef3f2;
  color: #d92d20;
}

.assertion-result-panel {
  padding: 0;
}

.assertion-result-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 160px;
  border: 1px dashed var(--el-border-color-light);
  border-radius: 8px;
  background: #fafafa;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.assertion-result-table {
  width: 100%;
}

.case-drawer-history-detail-button {
  padding-inline: 0;
  color: var(--el-color-primary);
}

.case-drawer-history-detail-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.case-drawer-history-back-button {
  height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-weight: 500;
}

.case-drawer-history-back-button:hover {
  background: #dbeafe;
}

.case-drawer-history-detail-head-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-width: 0;
}

.case-drawer-history-loading,
.case-drawer-history-empty,
.case-drawer-history-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.case-drawer-history-table-empty {
  min-height: 92px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.case-drawer-history-detail-shell {
  min-height: 320px;
}

.case-drawer-history-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0 16px 12px;
}

.case-drawer-history-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #101828;
}

.case-drawer-history-request-grid {
  display: none;
}

.case-drawer-change-history-table {
  margin: 0 16px 12px;
  width: calc(100% - 32px);
}

.case-drawer-change-history-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 52px;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: #f4f7ff;
  color: #3953d3;
  font-size: 12px;
  font-weight: 600;
}

.case-drawer-history-request-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.case-drawer-history-request-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.case-drawer-history-request-empty {
  min-height: 120px;
  border: 1px dashed var(--el-border-color-light);
  border-radius: 8px;
  background: #fafafa;
}

.case-drawer-history-request-tabs {
  margin-bottom: -2px;
}

.case-drawer-history-request-body {
  min-height: 220px;
}

.case-drawer-history-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 0 16px 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
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

.ms-like-request-body > .request-section,
.ms-like-request-body > .body-form-grid,
.ms-like-request-body > .case-list-panel {
  flex: 1 1 auto;
  min-height: 0;
}

.ms-like-request-body > .case-list-panel {
  padding: 0;
}

.case-list-request-body > .case-list-panel {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
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
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.ms-auth-panel--compact {
  min-width: 0;
}

.ms-auth-panel-title {
  color: #111827;
  font-size: 14px;
  font-weight: 600;
}

.ms-auth-radio-group {
  align-self: flex-start;
}

.ms-auth-radio-group :deep(.el-radio-button__inner) {
  height: 32px;
  border-color: #d1d5db;
  color: #374151;
  font-size: 13px;
  line-height: 30px;
  padding: 0 14px;
}

.ms-auth-radio-group :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  border-color: #3b82f6;
  background: #eff6ff;
  color: #2563eb;
  box-shadow: -1px 0 0 0 #3b82f6;
}

.ms-auth-form {
  display: grid;
  gap: 14px;
}

.ms-auth-form--compact {
  gap: 12px;
}

.ms-auth-form-item {
  display: grid;
  gap: 6px;
}

.ms-auth-form-label {
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
}

.ms-auth-form-control {
  width: min(100%, 450px);
}

.ms-auth-form-control :deep(.el-input__wrapper) {
  min-height: 34px;
  border-radius: 8px;
  box-shadow: inset 0 0 0 1px #d1d5db;
}

.empty-hint {
  padding: 18px;
  border: 1px dashed #d1d5db;
  border-radius: 8px;
  color: #9ca3af;
  background: #f9fafb;
  font-size: 13px;
  text-align: center;
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

.execution-workbench {
  display: grid;
  grid-template-columns: 256px minmax(0, 1fr);
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: #ffffff;
}

.execution-suite-pane {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  border-right: 1px solid #e5e7eb;
  background: #ffffff;
}

.execution-suite-tools {
  display: grid;
  gap: 12px;
  padding: 16px 16px 12px;
}

.execution-primary-button,
.execution-search-box,
.execution-case-toolbar button,
.execution-run-button,
.execution-save-button,
.execution-config-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.execution-primary-button {
  width: 100%;
  height: 36px;
  gap: 8px;
  border: 1px solid #2563eb;
  border-radius: 8px;
  background: #2563eb;
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.execution-primary-button:hover {
  border-color: #1d4ed8;
  background: #1d4ed8;
}

.execution-primary-button svg,
.execution-search-box svg,
.execution-tab-icon svg,
.execution-suite-tab svg,
.execution-sub-tab svg,
.execution-case-row svg,
.execution-config-panel svg {
  width: 16px;
  height: 16px;
}

.execution-search-box {
  position: relative;
  height: 36px;
  justify-content: flex-start;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  color: #9ca3af;
}

.execution-search-box svg {
  position: absolute;
  left: 12px;
}

.execution-search-box input {
  width: 100%;
  height: 100%;
  border: 0;
  outline: none;
  background: transparent;
  color: #111827;
  font-size: 14px;
  padding: 0 12px 0 36px;
}

.execution-suite-tree {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding: 0 12px 12px;
}

.execution-suite-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 30px;
  margin: 0 4px 8px;
  border-bottom: 1px solid #f3f4f6;
  color: #374151;
  font-size: 14px;
  font-weight: 600;
}

.execution-suite-title-row small,
.execution-suite-node small {
  border-radius: 4px;
  background: #f3f4f6;
  color: #9ca3af;
  font-size: 12px;
  font-weight: 400;
  padding: 1px 6px;
}

.execution-suite-node {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 32px;
  border-radius: 8px;
  color: #374151;
  font-size: 14px;
  padding: 0 6px;
  cursor: default;
}

.execution-suite-node:hover {
  background: #f3f4f6;
}

.execution-suite-node.active {
  background: #eff6ff;
  color: #2563eb;
  font-weight: 500;
}

.execution-suite-node span {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.execution-suite-branch {
  margin-left: 14px;
}

.execution-suite-chevron {
  width: 14px !important;
  height: 14px !important;
  color: #9ca3af;
}

.execution-suite-chevron.is-open {
  transform: rotate(90deg);
}

.execution-suite-chevron.is-placeholder {
  opacity: 0;
}

.execution-suite-folder {
  color: #9ca3af;
}

.execution-suite-folder.is-open,
.execution-suite-node.active .execution-suite-folder {
  color: #3b82f6;
}

.execution-main-pane {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  background: #ffffff;
}

.execution-suite-tab-strip {
  display: flex;
  align-items: center;
  min-height: 40px;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
}

.execution-suite-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  border: 0;
  border-right: 1px solid #e5e7eb;
  background: #ffffff;
  color: #111827;
  font-size: 14px;
  padding: 0 16px;
}

.execution-suite-tab::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 2px;
  background: #3b82f6;
}

.execution-suite-tab svg {
  color: #9ca3af;
}

.execution-tab-icon {
  width: 32px;
  height: 39px;
  border: 0;
  background: #ffffff;
  color: #9ca3af;
  cursor: pointer;
}

.execution-tab-icon:hover {
  background: #f3f4f6;
  color: #4b5563;
}

.execution-content-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 288px;
  flex: 1 1 auto;
  min-height: 0;
}

.execution-suite-content {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  border-right: 1px solid #e5e7eb;
  overflow: hidden;
}

.execution-sub-tab-row {
  display: flex;
  align-items: center;
  gap: 20px;
  min-height: 40px;
  padding: 0 16px;
  border-bottom: 1px solid #e5e7eb;
}

.execution-sub-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  border: 0;
  background: transparent;
  color: #4b5563;
  font-size: 14px;
  cursor: pointer;
}

.execution-sub-tab.active {
  color: #2563eb;
  font-weight: 500;
}

.execution-sub-tab.active::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 2px;
  border-radius: 999px;
  background: #2563eb;
}

.execution-suite-header {
  display: grid;
  gap: 8px;
  padding: 16px 24px;
  border-bottom: 1px solid #f3f4f6;
}

.execution-suite-name-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.execution-suite-name-row strong {
  color: #111827;
  font-size: 18px;
  font-weight: 600;
}

.execution-priority-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 24px;
  padding: 0 10px;
  border: 1px solid #fed7aa;
  border-radius: 6px;
  background: #ffedd5;
  color: #c2410c;
  font-size: 12px;
  font-weight: 700;
}

.execution-suite-header p {
  margin: 0;
  color: #6b7280;
  font-size: 14px;
}

.execution-suite-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #9ca3af;
  font-size: 12px;
}

.execution-suite-meta svg {
  width: 13px;
  height: 13px;
}

.execution-case-list {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
}

.execution-case-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 42px;
  padding: 0 16px;
  border-bottom: 1px solid #f3f4f6;
  background: #f9fafb;
  color: #6b7280;
  font-size: 14px;
}

.execution-case-toolbar > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.execution-case-toolbar button {
  gap: 6px;
  height: 30px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #ffffff;
  color: #374151;
  font-size: 13px;
  cursor: pointer;
  padding: 0 12px;
}

.execution-case-toolbar button:hover {
  background: #f3f4f6;
}

.execution-case-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 48px;
  border-bottom: 1px solid #f3f4f6;
  padding: 0 16px;
}

.execution-case-row:hover {
  background: rgba(239, 246, 255, 0.42);
}

.execution-case-row > svg {
  flex: 0 0 auto;
  color: #d1d5db;
}

.execution-case-row strong {
  width: 140px;
  flex: 0 0 auto;
  overflow: hidden;
  color: #111827;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.execution-case-type {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
  height: 22px;
  padding: 0 8px;
  border: 1px solid #bfdbfe;
  border-radius: 4px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 500;
}

.execution-case-type.is-scene {
  border-color: #e9d5ff;
  background: #faf5ff;
  color: #9333ea;
}

.execution-case-type svg {
  width: 12px;
  height: 12px;
}

.execution-case-row code,
.execution-case-desc {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  color: #9ca3af;
  font-family: Consolas, 'SFMono-Regular', Menlo, monospace;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.execution-case-desc {
  font-family: inherit;
}

.execution-case-more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #9ca3af;
  opacity: 0;
  cursor: pointer;
}

.execution-case-row:hover .execution-case-more {
  opacity: 1;
}

.execution-case-more:hover {
  background: #e5e7eb;
  color: #4b5563;
}

.execution-recent-panel {
  border-top: 1px solid #e5e7eb;
  padding: 12px 16px 16px;
}

.execution-recent-title {
  margin-bottom: 10px;
  color: #374151;
  font-size: 14px;
  font-weight: 600;
}

.execution-task-table :deep(.el-table__header th) {
  height: 36px;
  background: #f9fafb;
  color: #6b7280;
  font-size: 12px;
}

.execution-task-table :deep(.el-table__body td) {
  height: 40px;
  color: #374151;
  font-size: 13px;
}

.execution-config-panel {
  padding: 12px;
  background: #ffffff;
}

.execution-config-card {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  background: #f9fafb;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.execution-config-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 36px;
  gap: 8px;
  padding: 16px;
  border-bottom: 1px solid #f3f4f6;
}

.execution-config-select {
  min-width: 0;
}

.execution-config-select :deep(.el-select__wrapper),
.execution-config-body :deep(.el-select__wrapper) {
  min-height: 36px;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: inset 0 0 0 1px #e5e7eb;
}

.execution-config-icon {
  width: 36px;
  height: 36px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  color: #9ca3af;
}

.execution-run-buttons {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-column: 1 / -1;
  gap: 8px;
}

.execution-run-button,
.execution-save-button {
  height: 36px;
  gap: 6px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.execution-run-button {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.execution-run-button:hover {
  border-color: #1d4ed8;
  background: #1d4ed8;
}

.execution-save-button {
  border: 1px solid #e5e7eb;
  background: #ffffff;
  color: #374151;
}

.execution-save-button:hover {
  background: #f3f4f6;
}

.execution-config-body {
  display: grid;
  align-content: start;
  gap: 18px;
  padding: 16px;
  overflow: auto;
}

.execution-config-body label {
  display: grid;
  gap: 8px;
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
}

.execution-config-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.execution-config-switch > div {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #374151;
  font-size: 14px;
}

.execution-config-switch svg {
  color: #9ca3af;
}

.execution-config-stats {
  display: grid;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px solid #e5e7eb;
}

.execution-config-stats div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #9ca3af;
  font-size: 12px;
}

.execution-config-stats strong {
  color: #374151;
  font-weight: 500;
}

.execution-config-stats strong.is-success {
  color: #16a34a;
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

.batch-drawer-mode-row {
  display: inline-flex;
  justify-self: flex-start;
  gap: 4px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
  padding: 3px;
}

.batch-drawer-mode-row button {
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #4b5563;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  height: 28px;
  padding: 0 12px;
}

.batch-drawer-mode-row button.active {
  background: #fff;
  color: #2563eb;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}

.batch-drawer-hint {
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #f9fafb;
}

.batch-drawer-label {
  font-size: 12px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}

.batch-drawer-example {
  font-family: var(--el-font-family-monospace, Consolas, monospace);
  font-size: 12px;
  line-height: 1.7;
  color: #374151;
}

.batch-drawer-note {
  margin-top: 10px;
  font-size: 12px;
  color: #6b7280;
}

.batch-drawer-textarea :deep(.el-textarea__inner) {
  min-height: 360px;
  border-radius: 12px;
  border-color: #d1d5db;
  box-shadow: none;
  color: #111827;
  font-size: 13px;
  line-height: 1.6;
}

.batch-drawer-textarea :deep(.el-textarea__inner:focus) {
  border-color: #2563eb;
  box-shadow: 0 0 0 2px #dbeafe;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
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
  min-width: 0;
  max-width: 100%;
}

.case-list-panel > .editor-actions.left :deep(.el-button--primary) {
  border-color: #2563eb;
  background: #2563eb;
  color: #ffffff;
  font-size: 13px;
  font-weight: 500;
}

.case-list-panel > .editor-actions.left :deep(.el-button--primary:hover),
.case-list-panel > .editor-actions.left :deep(.el-button--primary:focus) {
  border-color: #1d4ed8;
  background: #1d4ed8;
  color: #ffffff;
}

.case-ai-generate-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 32px;
  border: 0;
  border-radius: 8px;
  background: #ffffff;
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
  padding: 0 12px;
  cursor: pointer;
}

.case-ai-generate-button::before {
  position: absolute;
  inset: 0;
  padding: 1px;
  border-radius: inherit;
  background: linear-gradient(93deg, #3370ff 0%, #8b5cf6 100%);
  content: "";
  pointer-events: none;
  mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  mask-composite: exclude;
}

.case-ai-generate-button svg {
  width: 15px;
  height: 15px;
  color: #7c3aed;
}

.case-ai-generate-button span {
  color: transparent;
  background: linear-gradient(90deg, #2563eb 0%, #7c3aed 100%);
  background-clip: text;
}

.case-ai-generate-button:hover {
  background: #f8fbff;
}

.case-ai-generate-button:disabled {
  cursor: not-allowed;
  opacity: 0.48;
}

.case-list-table :deep(.el-table__inner-wrapper) {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.case-list-table :deep(.el-table__header-wrapper th) {
  height: 40px;
  background: #f9fafb;
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
}

.case-list-table :deep(.el-table__body-wrapper td) {
  height: 42px;
  color: #374151;
  font-size: 13px;
}

.case-list-table :deep(.el-table__row:hover > td.el-table__cell) {
  background: #f9fafb;
}

.case-list-table-wrap {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
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
  gap: 4px;
  white-space: nowrap;
}

.case-list-action-button,
.case-list-more-button {
  color: #2563eb;
  font-weight: 500;
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
  color: #6b7280;
}

.case-list-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 2px 0;
}

.case-list-pagination-summary {
  color: #6b7280;
  font-size: 12px;
}

.case-list-pagination :deep(.el-pagination) {
  --el-pagination-button-width: 28px;
  --el-pagination-button-height: 28px;
}

.api-automation-page {
  border-color: var(--ath-border);
  border-radius: var(--ath-radius-lg);
  background: var(--ath-bg-panel);
  box-shadow: var(--ath-shadow-xs);
}

.api-tabs > :deep(.el-tabs__header) {
  border-bottom-color: var(--ath-border);
  background: var(--ath-bg-panel);
}

.api-tabs > :deep(.el-tabs__header .el-tabs__nav-wrap) {
  background: var(--ath-bg-muted);
}

.api-tabs > :deep(.el-tabs__header .el-tabs__item.is-active),
.api-tabs > :deep(.el-tabs__content),
.shell-card,
.ms-like-layout,
.ms-scenario-workbench,
.scenario-main-pane,
.ms-like-main {
  background: var(--ath-bg-panel);
}

.ms-like-sidebar,
.scenario-module-pane {
  border-right-color: var(--ath-border);
  background: var(--ath-bg-panel);
}

.ms-like-sidebar-actions,
.ms-like-sidebar-search,
.ms-like-directory-title-row,
.ms-like-directory-shell,
.scenario-module-pane .ms-like-sidebar-tools,
.scenario-module-pane .ms-like-directory-shell {
  background: var(--ath-bg-panel);
}

.ms-like-sidebar-secondary,
.ms-like-curl-button {
  border-color: var(--ath-input-border);
  background: var(--ath-bg-panel);
  color: var(--ath-text-main);
}

.ms-like-sidebar-secondary:hover,
.ms-like-curl-button:hover {
  border-color: var(--ath-border-strong);
  background: var(--ath-bg-page);
}

.ms-like-sidebar-search :deep(.el-input__wrapper),
.scenario-sidebar-search :deep(.el-input__wrapper),
.ms-scenario-search :deep(.el-input__wrapper) {
  height: var(--ath-control-height-md);
  min-height: var(--ath-control-height-md);
  border-radius: var(--ath-radius-md);
  background: var(--ath-bg-page);
  box-shadow: inset 0 0 0 1px var(--ath-input-border);
}

.ms-like-sidebar-search :deep(.el-input__wrapper:hover),
.scenario-sidebar-search :deep(.el-input__wrapper:hover),
.ms-scenario-search :deep(.el-input__wrapper:hover) {
  box-shadow: inset 0 0 0 1px var(--ath-border-strong);
}

.ms-like-sidebar-search :deep(.el-input.is-focus .el-input__wrapper),
.scenario-sidebar-search :deep(.el-input.is-focus .el-input__wrapper),
.scenario-sidebar-search :deep(.el-input__wrapper.is-focus),
.ms-scenario-search :deep(.el-input.is-focus .el-input__wrapper),
.ms-scenario-search :deep(.el-input__wrapper.is-focus) {
  box-shadow: inset 0 0 0 1px var(--ath-blue), var(--ath-focus-ring);
}

.ms-like-directory-title-row,
.scenario-directory-title-row {
  border-bottom-color: var(--ath-border);
  color: var(--ath-text-main);
}

.ms-like-directory-tree :deep(.el-tree-node__content),
.scenario-module-tree :deep(.el-tree-node__content) {
  border-radius: var(--ath-radius-md);
  color: var(--ath-text-main);
}

.ms-like-directory-tree :deep(.el-tree-node__content:hover),
.scenario-module-tree :deep(.el-tree-node__content:hover) {
  background: var(--ath-bg-muted);
}

.ms-like-directory-tree :deep(.el-tree-node.is-current > .el-tree-node__content),
.scenario-module-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: var(--ath-blue-soft);
  color: var(--ath-primary);
}

.scenario-editor-tab-strip,
.ms-like-tab-strip {
  border-bottom-color: var(--ath-border);
  background: var(--ath-bg-panel);
}

.scenario-editor-tab-strip .ms-like-editor-tab {
  border-right-color: var(--ath-border);
  background: var(--ath-bg-page);
  color: var(--ath-text-muted);
}

.scenario-editor-tab-strip .ms-like-editor-tab.active,
.ms-like-editor-tab.active,
.ms-like-editor-tab:hover {
  background: var(--ath-bg-panel);
  color: var(--ath-text-strong);
}

.scenario-editor-tab-strip .ms-like-editor-tab.active::after,
.ms-like-editor-tab.active {
  border-bottom-color: var(--ath-blue);
}

.ms-scenario-list-shell {
  min-height: 0;
  flex: 1 1 auto;
  background: var(--ath-bg-panel);
}

.ms-scenario-list-toolbar {
  align-items: center;
  min-height: 64px;
  border-bottom: 1px solid var(--ath-border-soft);
  background: var(--ath-bg-panel);
  padding: var(--ath-space-4);
}

.scenario-list-toolbar {
  border-bottom-color: var(--ath-border-soft);
  background: var(--ath-bg-panel);
}

.ms-scenario-tool-button,
.ms-scenario-icon-button {
  height: var(--ath-control-height-sm);
  border-radius: var(--ath-radius-md);
}

.ms-scenario-table,
.case-list-table {
  --el-table-header-bg-color: var(--ath-bg-page);
  --el-table-header-text-color: var(--ath-text-muted);
  --el-table-text-color: var(--ath-text-main);
  --el-table-border-color: var(--ath-border-soft);
  --el-table-row-hover-bg-color: #fbfdff;
  width: 100%;
  overflow: hidden;
  border: 1px solid var(--ath-border);
  border-radius: var(--ath-radius-lg);
}

.ms-scenario-table :deep(.el-table__inner-wrapper),
.case-list-table :deep(.el-table__inner-wrapper) {
  border: 0;
  border-radius: 0;
}

.ms-scenario-table :deep(.el-table__header th),
.case-list-table :deep(.el-table__header-wrapper th) {
  height: 44px;
  background: var(--ath-bg-page);
  color: var(--ath-text-muted);
  font-size: var(--ath-font-xs);
  font-weight: var(--ath-weight-semibold);
}

.ms-scenario-table :deep(.el-table__row td),
.case-list-table :deep(.el-table__body-wrapper td) {
  height: 48px;
  color: var(--ath-text-main);
  font-size: var(--ath-font-sm);
}

.ms-scenario-table :deep(.el-table__row:hover > td.el-table__cell),
.case-list-table :deep(.el-table__row:hover > td.el-table__cell) {
  background: #fbfdff;
}

.case-list-table-wrap {
  gap: var(--ath-space-3);
}

.case-list-pagination {
  min-height: 44px;
  border-top: 1px solid var(--ath-border-soft);
  padding: var(--ath-space-2) 0 0;
}

.case-list-pagination-summary {
  color: var(--ath-text-muted);
  font-size: var(--ath-font-xs);
}

.empty-hint {
  border-color: var(--ath-border-strong);
  border-radius: var(--ath-radius-lg);
  background: var(--ath-bg-page);
  color: var(--ath-text-subtle);
}

.ms-like-form-panel,
.metric-card,
.asset-item,
.history-row,
.result-item {
  border-color: var(--ath-border);
  border-radius: var(--ath-radius-lg);
  background: var(--ath-bg-panel);
}

:global(.api-soft-dialog .el-dialog),
:global(.api-soft-drawer .el-drawer) {
  border-color: var(--ath-border);
  box-shadow: var(--ath-shadow-dialog);
}

:global(.api-soft-dialog .el-dialog__header),
:global(.api-soft-drawer .el-drawer__header),
:global(.api-soft-dialog .el-dialog__footer),
:global(.api-soft-drawer .el-drawer__footer) {
  border-color: var(--ath-border-soft);
  background: var(--ath-bg-panel);
}

:global(.api-soft-dialog .el-dialog__body),
:global(.api-soft-drawer .el-drawer__body) {
  background: var(--ath-bg-panel);
}

.api-soft-dialog :deep(.el-form-item__label),
.api-soft-drawer :deep(.el-form-item__label) {
  color: var(--ath-text-main);
  font-size: var(--ath-font-sm);
  font-weight: var(--ath-weight-medium);
  line-height: var(--ath-line-sm);
}

.api-soft-dialog :deep(.el-input__wrapper),
.api-soft-dialog :deep(.el-select__wrapper),
.api-soft-drawer :deep(.el-input__wrapper),
.api-soft-drawer :deep(.el-select__wrapper) {
  min-height: var(--ath-control-height-md);
  border-radius: var(--ath-radius-md);
  background: var(--ath-bg-panel);
  box-shadow: inset 0 0 0 1px var(--ath-input-border);
}

.api-soft-dialog :deep(.el-input__wrapper:hover),
.api-soft-dialog :deep(.el-select__wrapper:hover),
.api-soft-drawer :deep(.el-input__wrapper:hover),
.api-soft-drawer :deep(.el-select__wrapper:hover) {
  box-shadow: inset 0 0 0 1px var(--ath-border-strong);
}

.api-soft-dialog :deep(.el-input.is-focus .el-input__wrapper),
.api-soft-dialog :deep(.el-select__wrapper.is-focused),
.api-soft-drawer :deep(.el-input.is-focus .el-input__wrapper),
.api-soft-drawer :deep(.el-select__wrapper.is-focused) {
  box-shadow: inset 0 0 0 1px var(--ath-blue), var(--ath-focus-ring);
}

.api-soft-dialog :deep(.el-textarea__inner),
.api-soft-drawer :deep(.el-textarea__inner) {
  border-color: var(--ath-input-border);
  border-radius: var(--ath-radius-md);
  color: var(--ath-text-main);
  font-size: var(--ath-font-sm);
  line-height: 1.6;
  box-shadow: none;
}

.api-soft-dialog :deep(.el-textarea__inner:hover),
.api-soft-drawer :deep(.el-textarea__inner:hover) {
  border-color: var(--ath-border-strong);
}

.api-soft-dialog :deep(.el-textarea__inner:focus),
.api-soft-drawer :deep(.el-textarea__inner:focus) {
  border-color: var(--ath-blue);
  box-shadow: var(--ath-focus-ring);
}

.api-soft-dialog :deep(.el-button),
.api-soft-drawer :deep(.el-button) {
  border-radius: var(--ath-radius-md);
  font-weight: var(--ath-weight-medium);
}

.api-soft-dialog :deep(.dialog-footer),
.api-soft-drawer :deep(.dialog-footer),
.scenario-step-config-footer,
.scenario-import-footer {
  align-items: center;
  gap: var(--ath-space-3);
}

.scenario-import-drawer :deep(.el-drawer__header),
.scenario-import-drawer :deep(.el-drawer__footer) {
  border-color: var(--ath-border-soft);
  background: var(--ath-bg-panel);
}

.scenario-import-drawer :deep(.el-drawer__header) {
  min-height: 64px;
  padding: 0 var(--ath-space-6);
}

.scenario-import-drawer :deep(.el-drawer__footer) {
  padding: var(--ath-space-4) var(--ath-space-6);
}

.scenario-import-shell {
  background: var(--ath-bg-panel);
}

.scenario-import-tabs :deep(.el-tabs__header) {
  padding: 0 var(--ath-space-6);
}

.scenario-import-tabs :deep(.el-tabs__nav-wrap::after) {
  background: var(--ath-border-soft);
}

.scenario-import-tabs :deep(.el-tabs__item) {
  height: 44px;
  color: var(--ath-text-muted);
  font-size: var(--ath-font-sm);
  font-weight: var(--ath-weight-medium);
}

.scenario-import-tabs :deep(.el-tabs__item.is-active) {
  color: var(--ath-primary);
}

.scenario-import-content {
  background: var(--ath-bg-panel);
}

.scenario-import-tree-pane {
  border-right-color: var(--ath-border);
  background: var(--ath-bg-subtle);
  padding: var(--ath-space-4);
}

.scenario-import-tree :deep(.el-tree) {
  background: transparent;
}

.scenario-import-tree :deep(.el-tree-node__content) {
  height: 34px;
  border-radius: var(--ath-radius-md);
  color: var(--ath-text-main);
}

.scenario-import-tree :deep(.el-tree-node__content:hover) {
  background: var(--ath-bg-muted);
}

.scenario-import-tree :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: var(--ath-blue-soft);
  color: var(--ath-primary);
}

.scenario-import-tree-label {
  color: inherit;
  font-size: var(--ath-font-sm);
}

.scenario-import-tree-count {
  color: var(--ath-text-subtle);
  font-size: var(--ath-font-xs);
}

.scenario-import-table-pane {
  padding: var(--ath-space-5);
}

.scenario-import-table-title {
  color: var(--ath-text-strong);
  font-size: var(--ath-font-sm);
  font-weight: var(--ath-weight-semibold);
  line-height: var(--ath-line-sm);
}

.scenario-import-table-title span,
.scenario-import-summary {
  color: var(--ath-text-muted);
}

.scenario-import-summary strong {
  color: var(--ath-primary);
}

.scenario-import-table-pane :deep(.el-table),
.scenario-step-config-body :deep(.el-table),
.report-drawer :deep(.el-table),
.assertion-result-table {
  --el-table-header-bg-color: var(--ath-bg-page);
  --el-table-header-text-color: var(--ath-text-muted);
  --el-table-text-color: var(--ath-text-main);
  --el-table-border-color: var(--ath-border-soft);
  --el-table-row-hover-bg-color: #fbfdff;
  border: 1px solid var(--ath-border);
  border-radius: var(--ath-radius-lg);
  overflow: hidden;
}

.scenario-import-table-pane :deep(.el-table__header-wrapper th),
.scenario-step-config-body :deep(.el-table__header-wrapper th),
.report-drawer :deep(.el-table__header-wrapper th),
.assertion-result-table :deep(.el-table__header-wrapper th) {
  height: 44px;
  background: var(--ath-bg-page);
  color: var(--ath-text-muted);
  font-size: var(--ath-font-xs);
  font-weight: var(--ath-weight-semibold);
}

.scenario-import-table-pane :deep(.el-table__body-wrapper td),
.scenario-step-config-body :deep(.el-table__body-wrapper td),
.report-drawer :deep(.el-table__body-wrapper td),
.assertion-result-table :deep(.el-table__body-wrapper td) {
  height: 48px;
  color: var(--ath-text-main);
  font-size: var(--ath-font-sm);
}

.scenario-step-config-drawer :deep(.el-drawer__body) {
  background: var(--ath-bg-page);
}

.scenario-step-config-shell {
  min-height: 100%;
  background: var(--ath-bg-page);
}

.scenario-drawer-title-row,
.scenario-script-drawer-title {
  color: var(--ath-text-strong);
}

.scenario-drawer-step-order {
  background: var(--ath-bg-muted);
  color: var(--ath-text-muted);
}

.scenario-drawer-step-title {
  color: var(--ath-text-strong);
  font-size: var(--ath-font-base);
  font-weight: var(--ath-weight-semibold);
}

.scenario-drawer-title-input :deep(.el-input__wrapper) {
  border-radius: var(--ath-radius-md);
  box-shadow: inset 0 0 0 1px var(--ath-blue);
}

.scenario-drawer-title-input :deep(.el-input.is-focus .el-input__wrapper),
.scenario-drawer-title-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: inset 0 0 0 1px var(--ath-blue), var(--ath-focus-ring);
}

.scenario-step-config-request-row,
.scenario-step-config-tabs,
.scenario-step-config-body,
.scenario-system-request-body,
.scenario-custom-request-body,
.scenario-script-editor-pane,
.scenario-script-assertion-pane,
.scenario-script-result-shell,
.ms-like-response-shell {
  border-color: var(--ath-border);
  background: var(--ath-bg-panel);
}

.scenario-step-config-tabs {
  border-bottom: 1px solid var(--ath-border-soft);
}

.scenario-system-list-item,
.scenario-step-table,
.batch-drawer-hint,
.metric-card,
.result-item {
  border-color: var(--ath-border);
  border-radius: var(--ath-radius-lg);
  background: var(--ath-bg-panel);
}

.scenario-system-list-item {
  color: var(--ath-text-main);
  font-size: var(--ath-font-sm);
}

.scenario-system-body-type,
.scenario-script-field,
.scenario-script-field-label,
.scenario-script-editor-header {
  color: var(--ath-text-main);
}

.scenario-script-mode-tab {
  border-color: var(--ath-border);
  background: var(--ath-bg-page);
  color: var(--ath-text-muted);
}

.scenario-script-mode-tab:first-child {
  border-top-left-radius: var(--ath-radius-md);
  border-bottom-left-radius: var(--ath-radius-md);
}

.scenario-script-mode-tab:last-child,
.scenario-script-mode-tab.is-disabled {
  border-top-right-radius: var(--ath-radius-md);
  border-bottom-right-radius: var(--ath-radius-md);
}

.scenario-script-mode-tab.active {
  border-color: var(--ath-blue);
  background: var(--ath-bg-panel);
  color: var(--ath-primary);
  box-shadow: inset 0 0 0 1px var(--ath-blue-soft);
}

.scenario-script-mode-tab.is-disabled {
  background: var(--ath-bg-muted);
  color: var(--ath-text-subtle);
}

.scenario-script-editor-header {
  border-color: var(--ath-border);
  background: var(--ath-bg-panel);
}

.batch-add-soft-drawer :deep(.el-drawer__body) {
  background: var(--ath-bg-panel);
}

.batch-drawer {
  gap: var(--ath-space-4);
}

.batch-drawer-mode-row {
  border-color: var(--ath-border);
  border-radius: var(--ath-radius-md);
  background: var(--ath-bg-muted);
}

.batch-drawer-mode-row button {
  border-radius: var(--ath-radius-sm);
  color: var(--ath-text-main);
  font-size: var(--ath-font-xs);
  font-weight: var(--ath-weight-medium);
}

.batch-drawer-mode-row button.active {
  background: var(--ath-bg-panel);
  color: var(--ath-primary);
  box-shadow: var(--ath-shadow-xs);
}

.batch-drawer-hint {
  background: var(--ath-bg-page);
}

.batch-drawer-label,
.batch-drawer-example {
  color: var(--ath-text-main);
}

.batch-drawer-note {
  color: var(--ath-text-muted);
}

.batch-drawer-textarea :deep(.el-textarea__inner) {
  border-color: var(--ath-input-border);
  border-radius: var(--ath-radius-lg);
  color: var(--ath-text-strong);
  font-size: var(--ath-font-sm);
  box-shadow: none;
}

.batch-drawer-textarea :deep(.el-textarea__inner:focus) {
  border-color: var(--ath-blue);
  box-shadow: var(--ath-focus-ring);
}

.report-drawer {
  gap: var(--ath-space-4);
}

.report-drawer .detail-grid {
  gap: var(--ath-space-3);
}

.report-drawer .detail-grid > div,
.step-card {
  border: 1px solid var(--ath-border);
  border-radius: var(--ath-radius-lg);
  background: var(--ath-bg-panel);
  padding: var(--ath-space-4);
  box-shadow: var(--ath-shadow-xs);
}

.report-drawer .detail-grid span,
.step-meta,
.snapshot-title {
  color: var(--ath-text-muted);
  font-size: var(--ath-font-xs);
}

.report-drawer .detail-grid strong,
.step-title {
  color: var(--ath-text-strong);
  font-size: var(--ath-font-sm);
}

pre {
  border-color: var(--ath-border);
  border-radius: var(--ath-radius-md);
  background: var(--ath-bg-page);
  color: var(--ath-text-main);
}

.ms-like-request-row {
  border-bottom-color: var(--ath-border);
  background: var(--ath-bg-panel);
  padding: var(--ath-space-3) var(--ath-space-4);
}

.ms-like-url-compose {
  border-color: var(--ath-input-border);
  border-radius: var(--ath-radius-md);
  background: var(--ath-bg-panel);
}

.ms-like-url-compose .request-method-select :deep(.el-select__wrapper) {
  border-right-color: var(--ath-border);
  background: var(--ath-bg-page);
}

.ms-like-curl-button {
  border-color: var(--ath-border);
  background: var(--ath-bg-panel);
  color: var(--ath-primary);
}

.ms-like-save-dropdown :deep(.el-button) {
  border-color: var(--ath-input-border);
  background: var(--ath-bg-panel);
  color: var(--ath-text-main);
}

.ms-like-save-dropdown :deep(.el-button:hover:not(.is-disabled)) {
  border-color: var(--ath-border-strong);
  background: var(--ath-bg-page);
  color: var(--ath-text-strong);
}

.ms-like-request-content-panel,
.ms-like-editor-scroll,
.ms-like-request-shell,
.ms-like-request-body,
.ms-like-response-content-panel {
  background: var(--ath-bg-panel);
}

.ms-like-top-tabs,
.ms-like-response-tabs {
  border-bottom-color: var(--ath-border);
  background: var(--ath-bg-panel);
  padding: 0 var(--ath-space-4);
}

.ms-like-top-tab {
  color: var(--ath-text-muted);
  font-size: var(--ath-font-sm);
}

.ms-like-top-tab:hover {
  color: var(--ath-text-main);
}

.ms-like-top-tab.active {
  border-bottom-color: var(--ath-primary);
  color: var(--ath-primary);
}

.ms-like-tab-badge {
  background: var(--ath-bg-muted);
  color: var(--ath-text-muted);
}

.ms-like-body-mode-shell,
.ms-like-table-surface,
.ms-like-form-panel {
  border-color: var(--ath-border);
  border-radius: var(--ath-radius-lg);
  background: var(--ath-bg-panel);
}

.ms-like-body-mode-shell.is-none,
.ms-like-empty-body {
  background: var(--ath-bg-page);
  color: var(--ath-text-subtle);
}

.ms-like-body-chip {
  border-color: var(--ath-input-border);
  border-radius: var(--ath-radius-sm);
  background: var(--ath-bg-panel);
  color: var(--ath-text-muted);
}

.ms-like-body-chip:hover {
  border-color: var(--ath-border-strong);
  background: var(--ath-bg-muted);
  color: var(--ath-text-main);
}

.ms-like-body-chip.active,
.ms-like-body-chip.active:hover {
  border-color: var(--ath-blue);
  background: var(--ath-blue-soft);
  color: var(--ath-primary);
}

.ms-like-table-header {
  border-bottom-color: var(--ath-border);
  background: var(--ath-bg-page);
  color: var(--ath-text-muted);
}

.ms-like-param-table .ms-like-header-input-title,
.ms-like-param-table .ms-like-table-header > span {
  color: var(--ath-text-muted);
  font-weight: var(--ath-weight-semibold);
}

.ms-like-table-row {
  min-height: 44px;
  border-bottom-color: var(--ath-border-soft);
}

.ms-like-table-row:hover {
  background: #fbfdff;
}

.ms-like-table-row.is-drag-over {
  background: var(--ath-blue-soft);
}

.ms-like-drag-dot {
  background: var(--ath-text-disabled);
}

.ms-like-table-row:hover .ms-like-drag-dot,
.ms-like-table-row.is-drag-over .ms-like-drag-dot {
  background: var(--ath-text-subtle);
}

.ms-like-checkbox-field :deep(.el-input__wrapper),
.ms-like-name-field :deep(.el-input__wrapper),
.ms-like-type-field :deep(.el-select__wrapper),
.ms-like-table-row :deep(.el-input__wrapper),
.ms-like-table-row :deep(.el-select__wrapper) {
  border-radius: var(--ath-radius-sm);
  background: transparent;
}

.ms-like-checkbox-field :deep(.el-input__wrapper:hover),
.ms-like-name-field :deep(.el-input__wrapper:hover),
.ms-like-type-field :deep(.el-select__wrapper:hover),
.ms-like-table-row :deep(.el-input__wrapper:hover),
.ms-like-table-row :deep(.el-select__wrapper:hover) {
  box-shadow: inset 0 0 0 1px var(--ath-input-border);
  background: var(--ath-bg-panel);
}

.ms-like-checkbox-field :deep(.el-input.is-focus .el-input__wrapper),
.ms-like-name-field :deep(.el-input.is-focus .el-input__wrapper),
.ms-like-type-field :deep(.el-select.is-focus .el-select__wrapper),
.ms-like-type-field :deep(.el-select__wrapper.is-focused),
.ms-like-table-row :deep(.el-input.is-focus .el-input__wrapper),
.ms-like-table-row :deep(.el-select.is-focus .el-select__wrapper),
.ms-like-table-row :deep(.el-select__wrapper.is-focused) {
  box-shadow: inset 0 0 0 1px var(--ath-blue), 0 0 0 2px rgba(59, 130, 246, 0.12);
  background: var(--ath-bg-panel);
}

.ms-like-switch-cell,
.ms-like-length-range-cell,
.ms-like-file-param-cell small {
  color: var(--ath-text-muted);
}

.ms-like-file-pick {
  color: var(--ath-text-main);
}

.ms-like-file-pick:hover {
  background: var(--ath-bg-page);
  color: var(--ath-primary);
}

.ms-like-link-button,
.ms-like-add-row,
.add-row-button {
  color: var(--ath-primary);
}

.ms-like-row-remove {
  color: var(--ath-red);
}

.ms-like-form-row {
  border-bottom-color: var(--ath-border-soft);
}

.ms-like-form-label {
  color: var(--ath-text-muted);
}

.ms-like-form-control :deep(.el-input__wrapper),
.ms-like-form-control :deep(.el-textarea__inner),
.ms-like-form-control.full-width :deep(.el-input__wrapper) {
  border-radius: var(--ath-radius-md);
  box-shadow: inset 0 0 0 1px var(--ath-input-border);
}

.ms-like-form-control :deep(.el-input__wrapper:hover),
.ms-like-form-control :deep(.el-textarea__inner:hover) {
  box-shadow: inset 0 0 0 1px var(--ath-border-strong);
}

.ms-like-form-control :deep(.el-input__wrapper.is-focus),
.ms-like-form-control :deep(.el-textarea__inner:focus) {
  box-shadow: inset 0 0 0 1px var(--ath-blue), var(--ath-focus-ring);
}

.ms-like-settings-hint {
  border-top-color: var(--ath-border-soft);
  background: var(--ath-bg-page);
  color: var(--ath-text-muted);
}

.ms-like-editor-scroll > .ms-like-response-shell {
  border-top-color: var(--ath-border);
}

.ms-like-response-header {
  border-bottom-color: var(--ath-border);
  background: var(--ath-bg-panel);
}

.ms-like-response-title {
  color: var(--ath-text-strong);
}

.ms-like-response-empty-window {
  border-color: var(--ath-border);
  background: var(--ath-bg-page);
}

.ms-like-response-empty-window span {
  background: var(--ath-text-subtle);
}

.ms-like-response-empty-text {
  color: var(--ath-text-muted);
}

.ms-like-response-empty-text span {
  color: var(--ath-primary);
}

.assertion-result-empty {
  color: var(--ath-text-subtle);
  background: var(--ath-bg-page);
}

:deep(.case-list-more-menu .case-list-menu-item) {
  color: var(--el-color-primary);
}

:deep(.case-list-more-menu .case-list-menu-item.is-danger) {
  color: var(--el-color-danger);
}

@media (max-width: 1480px) {
  .ms-like-layout {
    grid-template-columns: 272px minmax(0, 1fr);
  }

  .ms-like-param-table-grid--query {
      grid-template-columns: 24px 28px 220px 140px 220px 180px 72px minmax(180px, 1fr) 72px;
  }

  .ms-like-param-table-grid--header {
      grid-template-columns: 24px 28px repeat(3, minmax(0, 1fr)) 64px;
  }

  .ms-like-param-table-grid--body-form {
      grid-template-columns: 24px 28px 220px 140px 220px 180px minmax(180px, 1fr) 72px;
  }
}

@media (max-width: 1200px) {
  .api-automation-page,
  .api-tabs {
    min-height: 0;
  }

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

  .ms-like-layout {
    height: auto;
    min-height: 0;
  }

  .ms-like-editor-shell {
    display: flex;
    flex-direction: column;
    padding: 0;
  }

  .ms-like-request-shell,
  .ms-like-response-shell {
    flex: 0 0 auto;
    min-height: 0;
  }

  .ms-like-request-content-panel {
    margin: 0;
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
