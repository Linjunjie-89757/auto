<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  Check,
  CircleClose,
  CopyDocument,
  Download,
  FolderOpened,
  Memo,
  Setting,
  View,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { platformApi } from '../api/platform'
import AiGenerationProcessDialog from '../components/AiGenerationProcessDialog.vue'
import TableSettingsDrawer from '../components/TableSettingsDrawer.vue'
import { useTableSettings, type TableSettingsColumn } from '../composables/useTableSettings'
import { useWorkspace } from '../composables/useWorkspace'
import type { AiGeneratedCase, AiGenerationTaskEvent, CaseDirectoryNode, CreateCasePayload } from '../types/api'
import type { AiGenerationTaskRecord } from '../utils/caseAiGenerationRecords'
import {
  cancelAiGenerationRecord,
  getAiGenerationRecord,
  patchAiGenerationRecord,
} from '../utils/caseAiGenerationRecords'

type DetailCaseRow = AiGeneratedCase & {
  index: number
  adopted: boolean
  deleted: boolean
  manualEdited?: boolean
}

type CaseReviewState = 'PENDING' | 'ADOPTED' | 'DISCARDED'

type DirectoryOption = {
  value: number | null
  label: string
}

type PathPickerSelection = {
  directoryId: number | null
}

type PathPickerTreeNode = {
  key: string
  id: number | null
  name: string
  fullPath: string
  selectable: boolean
  children: PathPickerTreeNode[]
}

const route = useRoute()
const router = useRouter()
const { workspaceCode } = useWorkspace()

const detailTableColumns: TableSettingsColumn[] = [
  { key: 'title', label: '用例标题', required: true, defaultVisible: true },
  { key: 'precondition', label: '前置条件', required: true, defaultVisible: true },
  { key: 'steps', label: '操作步骤', required: true, defaultVisible: true },
  { key: 'expectedResult', label: '预期结果', required: true, defaultVisible: true },
  { key: 'savedDirectoryName', label: '最终保存路径', defaultVisible: true },
  { key: 'priority', label: '优先级', defaultVisible: true },
  { key: 'aiReview', label: 'AI评审', defaultVisible: true },
  { key: 'status', label: '状态', defaultVisible: true },
  { key: 'manualEdited', label: '人工修改', defaultVisible: false },
  { key: 'manualEditedByName', label: '操作人', defaultVisible: false },
]

const detailTableSettings = useTableSettings({
  storageKey: 'ai-record-detail-table-settings-v1',
  columns: detailTableColumns,
})

const activeRecord = ref<AiGenerationTaskRecord | null>(null)
const loadingRecord = ref(true)
const casePreviewVisible = ref(false)
const adoptDialogVisible = ref(false)
const processDialogVisible = ref(false)
const pathPickerVisible = ref(false)
const activeCaseCursor = ref(-1)
const adoptDirectoryOptions = ref<DirectoryOption[]>([])
const pathDirectoryOptions = ref<DirectoryOption[]>([])
const pathDirectoryTree = ref<CaseDirectoryNode[]>([])
const loadingAdoptDirectories = ref(false)
const loadingPathDirectories = ref(false)
const adopting = ref(false)
const savingPath = ref(false)
const savingCaseEdit = ref(false)
const requirementExpanded = ref(false)
const casePreviewActiveTab = ref<'detail' | 'analysis'>('detail')
const casePreviewScrollRef = ref<HTMLElement | null>(null)
const streamConnected = ref(false)
const exporting = ref(false)
const selectedCaseIndexes = ref<number[]>([])
const adoptDialogMode = ref<'all' | 'selected'>('all')
const casePreviewEditing = ref(false)
let pollingTimer: number | null = null
let streamAbortController: AbortController | null = null
let streamTaskId: string | null = null
let streamRefreshTimer: number | null = null

function getRecordSnapshotFromHistory(taskId?: string) {
  const snapshot = window.history.state?.recordSnapshot as AiGenerationTaskRecord | undefined
  if (!snapshot || !taskId || snapshot.id !== taskId) {
    return null
  }
  return snapshot
}

function seedRecordSnapshot(taskId?: string) {
  const snapshot = getRecordSnapshotFromHistory(taskId)
  if (!snapshot) {
    return false
  }
  activeRecord.value = snapshot
  loadingRecord.value = false
  return true
}

const adoptForm = reactive({
  directoryId: null as number | null,
})
const adoptPathTouched = ref(false)

const pathForm = reactive({
  workspaceCode: '',
  directoryId: null as number | null,
})
const pathTouched = ref(false)
const pathPickerKeyword = ref('')
const pathPickerSelection = reactive<PathPickerSelection>({
  directoryId: null,
})
const caseEditForm = reactive({
  title: '',
  priority: 'P3',
  precondition: '',
  steps: '',
  expectedResult: '',
})

const processSteps = [
  { index: 1 as const, title: '任务已创建', description: '已记录需求内容、目标空间和输出模式。' },
  { index: 2 as const, title: 'AI 生成用例', description: '正在根据需求生成候选测试用例。' },
  { index: 3 as const, title: 'AI 自动评审', description: '正在进行自动评审和建议汇总。' },
  { index: 4 as const, title: '任务完成', description: '生成结果已进入 AI 生成用例记录页。' },
]

const detailCases = computed<DetailCaseRow[]>(() => {
  const record = activeRecord.value
  if (!record) {
    return []
  }
  const adoptedIndexes = new Set(record.adoptedCaseIndexes ?? [])
  const deletedIndexes = new Set(record.deletedCaseIndexes ?? [])
  return record.generatedCases.map((item, index) => ({
    ...item,
    index,
    adopted: adoptedIndexes.has(index),
    deleted: deletedIndexes.has(index),
  }))
})

const availableCases = computed(() => detailCases.value)
const activeCase = computed<DetailCaseRow | null>(() => availableCases.value[activeCaseCursor.value] ?? null)
const activeCaseDisplayIndex = computed(() => (activeCase.value ? activeCaseCursor.value + 1 : 0))
const canPreviewPreviousCase = computed(() => activeCaseCursor.value > 0)
const canPreviewNextCase = computed(() => activeCaseCursor.value >= 0 && activeCaseCursor.value < availableCases.value.length - 1)
const adoptableCases = computed(() => availableCases.value.filter(item => getCaseReviewState(item) === 'PENDING'))
const selectedCases = computed(() => availableCases.value.filter(item => selectedCaseIndexes.value.includes(item.index)))
const selectedAdoptableCases = computed(() => selectedCases.value.filter(item => getCaseReviewState(item) === 'PENDING'))
const selectedDiscardableCases = computed(() => selectedCases.value.filter(item => getCaseReviewState(item) === 'PENDING'))
const selectedCount = computed(() => selectedCases.value.length)
const pendingCaseCount = computed(() => availableCases.value.filter(item => getCaseReviewState(item) === 'PENDING').length)
const adoptedCaseCount = computed(() => availableCases.value.filter(item => getCaseReviewState(item) === 'ADOPTED').length)
const discardedCaseCount = computed(() => availableCases.value.filter(item => getCaseReviewState(item) === 'DISCARDED').length)
const initialCaseCount = computed(() => availableCases.value.filter(item => getAiSource(item) === 'INITIAL').length)
const optimizedCaseCount = computed(() => availableCases.value.filter(item => getAiReviewStatus(item) === 'OPTIMIZED').length)
const supplementedCaseCount = computed(() => availableCases.value.filter(item => getAiReviewStatus(item) === 'SUPPLEMENTED' || getAiSource(item) === 'REVIEW_SUPPLEMENTED').length)
const confirmRequiredCaseCount = computed(() => availableCases.value.filter(item => getAiReviewStatus(item) === 'CONFIRM_REQUIRED').length)
const notRecommendedCaseCount = computed(() => availableCases.value.filter(item => getAiReviewStatus(item) === 'NOT_RECOMMENDED').length)
const adoptDialogCases = computed(() => (
  adoptDialogMode.value === 'selected' ? selectedAdoptableCases.value : adoptableCases.value
))
const outputEvents = computed(() => [...(activeRecord.value?.events ?? [])].sort((left, right) => (left.seq ?? 0) - (right.seq ?? 0)))
const showRealtimeOutputBoard = computed(() => {
  const record = activeRecord.value
  return !!record && record.outputMode === 'STREAM' && isRunningRecord(record)
})
const showCompletedOutputBoard = computed(() => {
  const record = activeRecord.value
  return !!record && record.status === 'COMPLETED' && outputEvents.value.length > 0
})
const showTaskOutputBoard = computed(() => !!activeRecord.value && (
  activeRecord.value.status === 'FAILED'
  || showRealtimeOutputBoard.value
  || showCompletedOutputBoard.value
))
const outputBoardTitle = computed(() => {
  if (activeRecord.value?.status === 'FAILED') {
    return '任务失败详情'
  }
  if (activeRecord.value?.status === 'COMPLETED') {
    return 'AI 输出记录'
  }
  return '实时输出'
})
const outputBoardStatusLabel = computed(() => activeRecord.value ? getStatusLabel(activeRecord.value.status) : '-')
const outputBoardStatusClass = computed(() => activeRecord.value ? getStatusClass(activeRecord.value.status) : 'status-neutral')
const outputConnectionLabel = computed(() => {
  if (!showRealtimeOutputBoard.value) {
    return '未连接'
  }
  return streamConnected.value ? '实时连接中' : '轮询兜底'
})
const outputConnectionClass = computed(() => streamConnected.value ? 'status-success' : 'status-warning')
const generationModelInfo = computed(() => {
  const event = [...outputEvents.value].reverse().find(item => item.phase === 'GENERATING' && item.model)
  return getModelDisplayInfo(event?.provider ?? activeRecord.value?.provider, event?.model ?? activeRecord.value?.model)
})
const reviewModelInfo = computed(() => {
  const event = [...outputEvents.value].reverse().find(item => item.phase === 'REVIEWING' && item.model)
  return getModelDisplayInfo(event?.provider, event?.model)
})
const outputTimeline = computed(() => {
  const record = activeRecord.value
  const currentStep = record?.currentStep ?? 1
  return [
    {
      key: 'GENERATING',
      label: '用例生成',
      meta: formatModelLabel(generationModelInfo.value),
      active: currentStep === 2,
      done: currentStep > 2 || outputEvents.value.some(item => item.eventType === 'GENERATION_COMPLETED'),
    },
    {
      key: 'REVIEWING',
      label: 'AI评审',
      meta: formatModelLabel(reviewModelInfo.value),
      active: currentStep === 3,
      done: currentStep > 3 || outputEvents.value.some(item => item.eventType === 'TASK_COMPLETED'),
    },
  ]
})

function getCaseReviewState(row: DetailCaseRow | null | undefined): CaseReviewState {
  if (!row) {
    return 'PENDING'
  }
  if (row.adopted) {
    return 'ADOPTED'
  }
  if (row.deleted) {
    return 'DISCARDED'
  }
  return 'PENDING'
}

function getCaseReviewStateLabel(row: DetailCaseRow | null | undefined) {
  const state = getCaseReviewState(row)
  if (state === 'ADOPTED') {
    return '已采纳'
  }
  if (state === 'DISCARDED') {
    return '已弃用'
  }
  return '未采纳'
}

function getCaseReviewStateClass(row: DetailCaseRow | null | undefined) {
  const state = getCaseReviewState(row)
  if (state === 'ADOPTED') {
    return 'status-success'
  }
  if (state === 'DISCARDED') {
    return 'status-neutral'
  }
  return 'status-info'
}

function isRunningRecord(record: AiGenerationTaskRecord | null | undefined) {
  return !!record && ['PENDING', 'GENERATING', 'REVIEWING'].includes(record.status)
}

function getModelDisplayInfo(provider?: string | null, model?: string | null) {
  const normalizedModel = model && model !== '-' ? model : ''
  const normalizedProvider = provider && provider !== '-' ? provider : ''
  const providerMap: Record<string, { label: string, className: string }> = {
    OPENAI_COMPATIBLE_CHAT: { label: '兼容接口', className: 'provider-compatible' },
    OPENAI_COMPATIBLE_RESPONSES: { label: '兼容接口', className: 'provider-compatible' },
    AZURE_OPENAI: { label: 'Azure OpenAI', className: 'provider-openai' },
    OPENAI: { label: 'OpenAI', className: 'provider-openai' },
    DEEPSEEK: { label: 'DeepSeek', className: 'provider-deepseek' },
    DASHSCOPE: { label: '通义千问', className: 'provider-qwen' },
    QWEN: { label: '通义千问', className: 'provider-qwen' },
    ANTHROPIC: { label: 'Anthropic', className: 'provider-anthropic' },
  }
  const normalizedKey = normalizedProvider.toUpperCase()
  const matchedProvider = providerMap[normalizedKey]
  return {
    providerLabel: matchedProvider?.label || normalizedProvider || '未就绪',
    providerClass: matchedProvider?.className || 'provider-default',
    modelLabel: normalizedModel || '未就绪',
  }
}

function formatModelLabel(info: { providerLabel: string, modelLabel: string }) {
  if (info.providerLabel === '未就绪') {
    return info.modelLabel
  }
  if (info.modelLabel === '未就绪') {
    return info.providerLabel
  }
  return `${info.providerLabel} ${info.modelLabel}`
}

function getAiReviewStatus(row: DetailCaseRow | null | undefined) {
  return row?.aiReviewStatus || 'PENDING'
}

function getAiSource(row: DetailCaseRow | null | undefined) {
  return row?.aiSource || 'INITIAL'
}

function getAiSourceLabel(row: DetailCaseRow | null | undefined) {
  const source = getAiSource(row)
  if (source === 'REVIEW_OPTIMIZED') {
    return '评审优化'
  }
  if (source === 'REVIEW_SUPPLEMENTED') {
    return '评审补充'
  }
  return '初始生成'
}

function getAiSourceClass(row: DetailCaseRow | null | undefined) {
  const source = getAiSource(row)
  if (source === 'REVIEW_OPTIMIZED') {
    return 'status-warning'
  }
  if (source === 'REVIEW_SUPPLEMENTED') {
    return 'status-info'
  }
  return 'status-neutral'
}

function getAiReviewStatusLabel(row: DetailCaseRow | null | undefined) {
  const status = getAiReviewStatus(row)
  if (status === 'APPROVED') {
    return '通过'
  }
  if (status === 'OPTIMIZED') {
    return '已优化'
  }
  if (status === 'SUPPLEMENTED') {
    return '已补充'
  }
  if (status === 'CONFIRM_REQUIRED') {
    return '建议确认'
  }
  if (status === 'NOT_RECOMMENDED') {
    return '不推荐'
  }
  if (status === 'SUGGESTED') {
    return '建议优化'
  }
  if (status === 'REJECTED') {
    return '需重生成'
  }
  if (activeRecord.value?.status === 'GENERATING') {
    return '生成中'
  }
  return '待评审'
}

function getAiReviewStatusClass(row: DetailCaseRow | null | undefined) {
  const status = getAiReviewStatus(row)
  if (status === 'APPROVED') {
    return 'status-success'
  }
  if (status === 'REJECTED' || status === 'NOT_RECOMMENDED') {
    return 'status-danger'
  }
  if (status === 'SUGGESTED' || status === 'OPTIMIZED') {
    return 'status-warning'
  }
  if (status === 'SUPPLEMENTED') {
    return 'status-info'
  }
  if (status === 'CONFIRM_REQUIRED') {
    return 'status-purple'
  }
  return 'status-info'
}

function getOutputEventClass(event: AiGenerationTaskEvent) {
  if (event.level === 'ERROR') {
    return 'is-error'
  }
  if (event.level === 'WARN') {
    return 'is-warn'
  }
  return 'is-info'
}

function formatEventTime(value: string | null) {
  if (!value) {
    return '--:--:--'
  }
  return new Date(value).toLocaleTimeString('zh-CN', { hour12: false })
}

function shouldRefreshForEvent(event: AiGenerationTaskEvent) {
  return [
    'CASE_GENERATED',
    'CASE_REVIEWED',
    'GENERATION_COMPLETED',
    'TASK_COMPLETED',
    'TASK_FAILED',
    'TASK_CANCELED',
  ].includes(event.eventType)
}

function canEditCase(row: DetailCaseRow | null | undefined) {
  return !!row && getCaseReviewState(row) !== 'ADOPTED'
}

function startPolling() {
  stopPolling()
  pollingTimer = window.setInterval(() => {
    void loadRecord()
  }, 2500)
}

function stopPolling() {
  if (pollingTimer != null) {
    window.clearInterval(pollingTimer)
    pollingTimer = null
  }
}

function stopEventStream() {
  if (streamAbortController) {
    streamAbortController.abort()
    streamAbortController = null
  }
  streamTaskId = null
  streamConnected.value = false
}

function mergeTaskEvent(event: AiGenerationTaskEvent) {
  if (!activeRecord.value || activeRecord.value.id !== event.taskId) {
    return
  }
  const events = activeRecord.value.events ?? []
  const existingIndex = events.findIndex(item => item.seq === event.seq)
  const nextEvents = existingIndex >= 0
    ? events.map(item => (item.seq === event.seq ? event : item))
    : [...events, event]
  activeRecord.value = {
    ...activeRecord.value,
    events: nextEvents.sort((left, right) => (left.seq ?? 0) - (right.seq ?? 0)),
  }
  if (shouldRefreshForEvent(event)) {
    scheduleRecordRefresh()
  }
}

function scheduleRecordRefresh() {
  if (streamRefreshTimer != null) {
    return
  }
  streamRefreshTimer = window.setTimeout(() => {
    streamRefreshTimer = null
    void refreshRecordSilently()
  }, 350)
}

async function refreshRecordSilently() {
  const taskId = route.params.taskId?.toString()
  if (!taskId) {
    return
  }
  try {
    const nextRecord = await getAiGenerationRecord(workspaceCode.value, taskId)
    if (nextRecord) {
      activeRecord.value = nextRecord
      syncRecordWatchers(nextRecord)
    }
  }
  catch {
    // Keep the visible stream state; normal polling will retry.
  }
}

function startEventStream(record: AiGenerationTaskRecord) {
  if (streamTaskId === record.id && streamAbortController) {
    return
  }
  stopEventStream()
  const controller = new AbortController()
  streamAbortController = controller
  streamTaskId = record.id
  streamConnected.value = true
  void platformApi.streamAiGenerationTaskEvents(record.workspaceCode || workspaceCode.value, record.id, {
    signal: controller.signal,
    onEvent: mergeTaskEvent,
  }).then(() => {
    if (streamTaskId === record.id) {
      streamConnected.value = false
      streamAbortController = null
      streamTaskId = null
      void refreshRecordSilently()
    }
  }).catch((error) => {
    if ((error as Error).name !== 'AbortError' && streamTaskId === record.id) {
      streamConnected.value = false
    }
    if (streamTaskId === record.id) {
      streamAbortController = null
      streamTaskId = null
    }
  })
}

function syncEventStream(record: AiGenerationTaskRecord | null) {
  if (record && record.outputMode === 'STREAM' && isRunningRecord(record)) {
    startEventStream(record)
    return
  }
  stopEventStream()
}

function syncRecordWatchers(record: AiGenerationTaskRecord | null) {
  if (isRunningRecord(record)) {
    startPolling()
  } else {
    stopPolling()
  }
  syncEventStream(record)
}

async function loadRecord() {
  const taskId = route.params.taskId?.toString()
  if (!taskId) {
    activeRecord.value = null
    loadingRecord.value = false
    syncRecordWatchers(null)
    return
  }
  const hasSnapshot = seedRecordSnapshot(taskId)
  loadingRecord.value = !hasSnapshot
  try {
    const nextRecord = await getAiGenerationRecord(workspaceCode.value, taskId)
    activeRecord.value = nextRecord
    syncRecordWatchers(activeRecord.value)
  } catch (error) {
    ElMessage.error((error as Error).message)
    activeRecord.value = null
    syncRecordWatchers(null)
  } finally {
    loadingRecord.value = false
  }
}

function goBack() {
  router.push({ name: 'cases-ai-records', query: route.query })
}

function getStatusLabel(status: AiGenerationTaskRecord['status']) {
  const labelMap: Record<AiGenerationTaskRecord['status'], string> = {
    PENDING: '需求解析中',
    GENERATING: '生成中',
    REVIEWING: '评审中',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELED: '已取消',
  }
  return labelMap[status]
}

function getStatusClass(status: AiGenerationTaskRecord['status']) {
  const classMap: Record<AiGenerationTaskRecord['status'], string> = {
    PENDING: 'status-info',
    GENERATING: 'status-info',
    REVIEWING: 'status-warning',
    COMPLETED: 'status-success',
    FAILED: 'status-danger',
    CANCELED: 'status-neutral',
  }
  return classMap[status]
}

function getDefaultDirectoryPath(record: AiGenerationTaskRecord | null) {
  if (!record?.directoryName) {
    return '未设置默认路径'
  }
  const workspaceLabel = record.workspaceName || record.workspaceCode
  return workspaceLabel ? `${workspaceLabel}/${record.directoryName}` : record.directoryName
}

const pathPickerWorkspaceName = computed(() => (
  activeRecord.value?.workspaceName || activeRecord.value?.workspaceCode || ''
))

function formatTime(value: string | null) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function getFailureStageLabel(record: AiGenerationTaskRecord) {
  const labelMap: Record<AiGenerationTaskRecord['currentStep'], string> = {
    1: '任务创建',
    2: 'AI 生成用例',
    3: 'AI 自动评审',
    4: '任务完成',
  }
  return labelMap[record.currentStep]
}

function getFailureSuggestions(record: AiGenerationTaskRecord) {
  if (record.currentStep === 2) {
    return [
      '检查当前空间的编写模型配置和服务可用性。',
      '确认需求内容和文档解析结果是否完整。',
      '稍后重新发起生成任务，判断是否为临时波动。',
    ]
  }
  if (record.currentStep === 3) {
    return [
      '检查当前空间的评审模型配置和网络状态。',
      '若候选用例已生成，可保留当前结果并稍后重试。',
      '查看模型网关或代理服务是否存在 5xx 异常。',
    ]
  }
  return [
    '检查任务参数和当前空间配置。',
    '查看服务端日志中的详细报错信息。',
    '稍后重新生成以确认是否为偶发异常。',
  ]
}

function flattenDirectories(nodes: CaseDirectoryNode[], prefix = ''): DirectoryOption[] {
  return nodes.flatMap((node) => {
    const label = prefix ? `${prefix} / ${node.name}` : node.name
    return [
      { value: node.id, label },
      ...flattenDirectories(node.children ?? [], label),
    ]
  })
}

function normalizeDirectoryLabel(path: string | null | undefined) {
  return (path ?? '')
    .split(/[\\/]+/)
    .map(segment => segment.trim())
    .filter(Boolean)
    .join(' / ')
}

function formatDirectoryNameFromOption(option: DirectoryOption | undefined) {
  return option?.label
    ? option.label.split(' / ').map(segment => segment.trim()).filter(Boolean).join('/')
    : null
}

function formatCaseCellText(value: string | null | undefined) {
  const raw = (value ?? '').replace(/\r\n/g, '\n').trim()
  if (!raw) {
    return '-'
  }
  if (/\n/.test(raw)) {
    return raw
  }
  return raw.replace(/\s*(\d+[\.\u3001])\s*/g, '\n$1 ').replace(/^\n+/, '').trim()
}

function getCaseSavedDirectoryName(row: DetailCaseRow) {
  return row.adopted ? (row.savedDirectoryName || getDefaultDirectoryPath(activeRecord.value)) : '-'
}

function handleSelectionChange(rows: DetailCaseRow[]) {
  selectedCaseIndexes.value = rows.map(item => item.index)
}

async function loadDirectoryOptions(record: AiGenerationTaskRecord) {
  loadingAdoptDirectories.value = true
  try {
    const workspaces = await platformApi.getCaseDirectories(record.workspaceCode)
    const current = workspaces.find(item => item.workspaceCode === record.workspaceCode)
    adoptDirectoryOptions.value = flattenDirectories(current?.children ?? [])
    const fallbackOption = adoptDirectoryOptions.value.find(item => item.label === normalizeDirectoryLabel(record.directoryName))
    adoptForm.directoryId = record.directoryId ?? fallbackOption?.value ?? null
  } catch (error) {
    adoptDirectoryOptions.value = []
    ElMessage.error((error as Error).message)
  } finally {
    loadingAdoptDirectories.value = false
  }
}

function syncPathDirectoryOptions(targetWorkspaceCode: string, preferredDirectoryName?: string | null, preferredDirectoryId?: number | null) {
  void targetWorkspaceCode
  pathDirectoryOptions.value = flattenDirectories(pathDirectoryTree.value)
  const fallbackOption = pathDirectoryOptions.value.find(item => item.label === normalizeDirectoryLabel(preferredDirectoryName))
  pathForm.directoryId = preferredDirectoryId ?? fallbackOption?.value ?? null
}

function syncPickerDirectorySelection(targetWorkspaceCode: string, preferredDirectoryId?: number | null) {
  void targetWorkspaceCode
  const options = flattenDirectories(pathDirectoryTree.value)
  const hasPreferred = preferredDirectoryId != null && options.some(item => item.value === preferredDirectoryId)
  pathPickerSelection.directoryId = hasPreferred ? preferredDirectoryId : null
}

async function loadPathDirectoryOptions(targetWorkspaceCode: string, preferredDirectoryName?: string | null, preferredDirectoryId?: number | null) {
  loadingPathDirectories.value = true
  try {
    const workspaces = await platformApi.getCaseDirectories(targetWorkspaceCode)
    const current = workspaces.find(item => item.workspaceCode === targetWorkspaceCode)
    pathDirectoryTree.value = current?.children ?? []
    syncPathDirectoryOptions(targetWorkspaceCode, preferredDirectoryName, preferredDirectoryId)
  } catch (error) {
    pathDirectoryTree.value = []
    pathDirectoryOptions.value = []
    pathForm.directoryId = null
    ElMessage.error((error as Error).message)
  } finally {
    loadingPathDirectories.value = false
  }
}

const filteredPathPickerDirectories = computed(() => {
  if (!pathForm.workspaceCode || !pathPickerWorkspaceName.value) {
    return [] as PathPickerTreeNode[]
  }
  const keyword = pathPickerKeyword.value.trim().toLowerCase()
  const filterNodes = (nodes: PathPickerTreeNode[]): PathPickerTreeNode[] => nodes.reduce<PathPickerTreeNode[]>((result, node) => {
    const children = filterNodes(node.children ?? [])
    const matched = !keyword || node.fullPath.toLowerCase().includes(keyword) || node.name.toLowerCase().includes(keyword)
    if (matched || children.length) {
      result.push({
        ...node,
        children,
      })
    }
    return result
  }, [])
  const appendFullPath = (nodes: CaseDirectoryNode[], prefix = ''): PathPickerTreeNode[] => nodes.map((node) => {
    const fullPath = prefix ? `${prefix} / ${node.name}` : node.name
    return {
      key: `dir:${node.id}`,
      id: node.id,
      name: node.name,
      fullPath,
      selectable: true,
      children: appendFullPath(node.children ?? [], fullPath),
    }
  })
  const root: PathPickerTreeNode = {
    key: `workspace:${pathForm.workspaceCode}`,
    id: null,
    name: pathPickerWorkspaceName.value,
    fullPath: pathPickerWorkspaceName.value,
    selectable: false,
    children: appendFullPath(pathDirectoryTree.value),
  }
  return filterNodes([root])
})

const pathPickerSelectedOption = computed(() => {
  if (pathPickerSelection.directoryId == null) {
    return null
  }
  return flattenDirectories(pathDirectoryTree.value)
    .find(item => item.value === pathPickerSelection.directoryId) ?? null
})

const pathPickerSelectedFullPath = computed(() => {
  if (!pathPickerSelectedOption.value) {
    return ''
  }
  return pathPickerWorkspaceName.value ? `${pathPickerWorkspaceName.value} / ${pathPickerSelectedOption.value.label}` : pathPickerSelectedOption.value.label
})

function openCasePreview(row: DetailCaseRow) {
  activeCaseCursor.value = availableCases.value.findIndex(item => item.index === row.index)
  casePreviewEditing.value = false
  casePreviewActiveTab.value = 'detail'
  if (casePreviewScrollRef.value) {
    casePreviewScrollRef.value.scrollTop = 0
  }
  syncCaseEditForm(row)
  casePreviewVisible.value = true
}

function switchCasePreviewTab(tab: 'detail' | 'analysis') {
  if (casePreviewActiveTab.value === tab) {
    return
  }
  casePreviewActiveTab.value = tab
  requestAnimationFrame(() => {
    if (casePreviewScrollRef.value) {
      casePreviewScrollRef.value.scrollTop = 0
    }
  })
}

function syncCaseEditForm(row: DetailCaseRow | null) {
  caseEditForm.title = row?.title ?? ''
  caseEditForm.priority = row?.priority ?? 'P3'
  caseEditForm.precondition = row?.precondition ?? ''
  caseEditForm.steps = row?.steps ?? ''
  caseEditForm.expectedResult = row?.expectedResult ?? ''
}

function moveCasePreview(offset: -1 | 1) {
  const nextCursor = activeCaseCursor.value + offset
  if (nextCursor < 0 || nextCursor >= availableCases.value.length) {
    return
  }
  activeCaseCursor.value = nextCursor
  casePreviewEditing.value = false
  syncCaseEditForm(activeCase.value)
}

function startCaseEditing() {
  if (!activeCase.value) {
    return
  }
  if (!canEditCase(activeCase.value)) {
    ElMessage.info('已采纳用例请前往用例管理进行后续处理')
    return
  }
  syncCaseEditForm(activeCase.value)
  casePreviewEditing.value = true
}

function cancelCaseEditing() {
  syncCaseEditForm(activeCase.value)
  casePreviewEditing.value = false
}

function openProcessDialog() {
  if (!activeRecord.value) {
    ElMessage.info('当前任务记录不存在')
    return
  }
  processDialogVisible.value = true
}

async function openAdoptDialog(record: AiGenerationTaskRecord, mode: 'all' | 'selected' = 'selected') {
  activeRecord.value = await getAiGenerationRecord(workspaceCode.value, record.id) ?? record
  if (!activeRecord.value) {
    return
  }
  if (mode === 'selected' && !selectedAdoptableCases.value.length) {
    ElMessage.info(selectedCount.value ? '当前选中的用例里没有可采纳项' : '请先勾选需要采纳的用例')
    return
  }
  adoptDialogMode.value = mode
  adoptPathTouched.value = false
  await loadDirectoryOptions(activeRecord.value)
  adoptDialogVisible.value = true
}

async function openPathDialog(record: AiGenerationTaskRecord) {
  activeRecord.value = await getAiGenerationRecord(workspaceCode.value, record.id) ?? record
  if (!activeRecord.value) {
    ElMessage.warning('当前任务记录不存在，请刷新后重试')
    return
  }
  pathTouched.value = false
  pathForm.workspaceCode = activeRecord.value.workspaceCode
  await loadPathDirectoryOptions(
    activeRecord.value.workspaceCode,
    activeRecord.value.directoryName,
    activeRecord.value.directoryId,
  )
  pathPickerKeyword.value = ''
  syncPickerDirectorySelection(pathForm.workspaceCode, pathForm.directoryId)
  pathPickerVisible.value = true
}

function handlePathPickerNodeSelect(node: CaseDirectoryNode) {
  if ('selectable' in node && !node.selectable) {
    return
  }
  pathPickerSelection.directoryId = node.id
}

function confirmPathPickerSelection() {
  if (pathPickerSelection.directoryId == null) {
    ElMessage.warning('请先选择保存路径')
    return
  }
  pathTouched.value = true
  syncPathDirectoryOptions(pathForm.workspaceCode, null, pathPickerSelection.directoryId)
  void confirmPathChange()
}

async function confirmPathChange() {
  if (!activeRecord.value) {
    ElMessage.warning('当前任务记录不存在，请关闭弹窗后重试')
    return
  }
  if (pathForm.directoryId == null) {
    pathTouched.value = true
    ElMessage.warning('请先选择保存路径')
    return
  }

  savingPath.value = true
  try {
    const selectedOption = pathDirectoryOptions.value.find(item => item.value === pathForm.directoryId)
    activeRecord.value = await patchAiGenerationRecord(workspaceCode.value, activeRecord.value.id, {
      workspaceCode: pathForm.workspaceCode,
      directoryId: pathForm.directoryId,
      directoryName: formatDirectoryNameFromOption(selectedOption) ?? activeRecord.value.directoryName,
    })
    adoptForm.directoryId = activeRecord.value.directoryId
    pathPickerVisible.value = false
    await router.replace({
      query: {
        ...route.query,
        workspace: workspaceCode.value === 'ALL' ? 'ALL' : activeRecord.value.workspaceCode,
      },
    })
    ElMessage.success('默认保存路径已更新')
    await loadRecord()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    savingPath.value = false
  }
}

async function confirmAdoptAll() {
  if (!activeRecord.value) {
    ElMessage.warning('当前任务记录不存在，请关闭弹窗后重试')
    return
  }
  if (adoptForm.directoryId == null) {
    adoptPathTouched.value = true
    ElMessage.warning('请先选择保存路径')
    return
  }
  if (!adoptDialogCases.value.length) {
    ElMessage.info(adoptDialogMode.value === 'selected' ? '当前选中的用例里没有可采纳项' : '当前没有可采纳的用例')
    return
  }

  const adoptCount = adoptDialogCases.value.length
  adopting.value = true
  try {
    for (const item of adoptDialogCases.value) {
      const payload: CreateCasePayload = {
        directoryId: adoptForm.directoryId,
        title: item.title,
        caseType: item.caseType,
        priority: item.priority,
        sourceType: 'AI生成',
        caseStatus: '草稿',
        ownerId: null,
        precondition: item.precondition,
        steps: item.steps,
        expectedResult: item.expectedResult,
      }
      await platformApi.createCase(activeRecord.value.workspaceCode, payload)
    }

    const adopted = new Set(activeRecord.value.adoptedCaseIndexes ?? [])
    const discarded = new Set(activeRecord.value.deletedCaseIndexes ?? [])
    adoptDialogCases.value.forEach(item => adopted.add(item.index))
    adoptDialogCases.value.forEach(item => discarded.delete(item.index))
    const selectedOption = adoptDirectoryOptions.value.find(item => item.value === adoptForm.directoryId)
    const directoryName = formatDirectoryNameFromOption(selectedOption) ?? activeRecord.value.directoryName
    const adoptedCaseIndexes = adoptDialogCases.value.map(item => item.index)
    activeRecord.value = await patchAiGenerationRecord(workspaceCode.value, activeRecord.value.id, {
      directoryId: adoptForm.directoryId,
      directoryName,
      generatedCases: activeRecord.value.generatedCases.map((item, index) => (
        adoptedCaseIndexes.includes(index)
          ? { ...item, savedDirectoryName: directoryName ?? getDefaultDirectoryPath(activeRecord.value) }
          : item
      )),
      adoptedCaseIndexes: [...adopted],
      deletedCaseIndexes: [...discarded],
      savedCaseCount: adopted.size,
    })

    selectedCaseIndexes.value = selectedCaseIndexes.value.filter(index => !adoptDialogCases.value.some(item => item.index === index))
    adoptDialogVisible.value = false
    ElMessage.success(`已采纳 ${adoptCount} 条用例到用例管理`)
    await loadRecord()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    adopting.value = false
  }
}

async function saveCasePreviewEdit() {
  if (!activeRecord.value || !activeCase.value) {
    return
  }
  const targetIndex = activeCase.value.index
  savingCaseEdit.value = true
  try {
    activeRecord.value = await patchAiGenerationRecord(workspaceCode.value, activeRecord.value.id, {
      generatedCases: activeRecord.value.generatedCases.map((item, index) => (
        index === targetIndex
          ? {
              ...item,
              title: caseEditForm.title.trim(),
              priority: caseEditForm.priority,
              precondition: caseEditForm.precondition.trim(),
              steps: caseEditForm.steps.trim(),
              expectedResult: caseEditForm.expectedResult.trim(),
              manualEdited: true,
              manualEditedByName: activeRecord.value?.updatedByName || item.manualEditedByName,
              manualEditedAt: new Date().toISOString(),
            }
          : item
      )),
    })
    const nextCursor = availableCases.value.findIndex(item => item.index === targetIndex)
    activeCaseCursor.value = nextCursor >= 0 ? nextCursor : activeCaseCursor.value
    casePreviewEditing.value = false
    syncCaseEditForm(activeCase.value)
    ElMessage.success('用例修改已保存')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    savingCaseEdit.value = false
  }
}

async function discardSingleCase(row: DetailCaseRow) {
  if (!activeRecord.value) {
    return
  }
  if (getCaseReviewState(row) !== 'PENDING') {
    return
  }
  await ElMessageBox.confirm('确定弃用这条生成用例吗？弃用后仍可查看并支持取消弃用。', '弃用用例', {
    type: 'warning',
    confirmButtonText: '弃用',
    cancelButtonText: '取消',
  })

  const deleted = new Set(activeRecord.value.deletedCaseIndexes ?? [])
  deleted.add(row.index)
  activeRecord.value = await patchAiGenerationRecord(workspaceCode.value, activeRecord.value.id, {
    deletedCaseIndexes: [...deleted],
  })
  casePreviewEditing.value = false
  syncCaseEditForm(activeCase.value)
  ElMessage.success('用例已弃用')
}

async function discardSelectedCases() {
  if (!activeRecord.value) {
    ElMessage.warning('当前任务记录不存在，请刷新后重试')
    return
  }
  if (!selectedDiscardableCases.value.length) {
    ElMessage.info(selectedCount.value ? '当前选中的用例里没有可弃用项' : '请先勾选需要弃用的用例')
    return
  }

  const discardCount = selectedDiscardableCases.value.length
  await ElMessageBox.confirm(
    `确定弃用已选中的 ${discardCount} 条生成用例吗？弃用后仍可查看并支持取消弃用。`,
    '批量弃用用例',
    {
      type: 'warning',
      confirmButtonText: '弃用',
      cancelButtonText: '取消',
    },
  )

  const deleted = new Set(activeRecord.value.deletedCaseIndexes ?? [])
  selectedDiscardableCases.value.forEach(item => deleted.add(item.index))
  activeRecord.value = await patchAiGenerationRecord(workspaceCode.value, activeRecord.value.id, {
    deletedCaseIndexes: [...deleted],
  })
  casePreviewEditing.value = false
  syncCaseEditForm(activeCase.value)
  ElMessage.success(`已弃用 ${discardCount} 条生成用例`)
  await loadRecord()
}

async function adoptSingleCase(row: DetailCaseRow) {
  if (!activeRecord.value || getCaseReviewState(row) !== 'PENDING') {
    return
  }

  const payload: CreateCasePayload = {
    directoryId: activeRecord.value.directoryId,
    title: row.title,
    caseType: row.caseType,
    priority: row.priority,
    sourceType: 'AI生成',
    caseStatus: '草稿',
    ownerId: null,
    precondition: row.precondition,
    steps: row.steps,
    expectedResult: row.expectedResult,
  }

  try {
    await platformApi.createCase(activeRecord.value.workspaceCode, payload)
    const adopted = new Set(activeRecord.value.adoptedCaseIndexes ?? [])
    const discarded = new Set(activeRecord.value.deletedCaseIndexes ?? [])
    adopted.add(row.index)
    discarded.delete(row.index)
    const savedDirectoryName = activeRecord.value.directoryName ?? getDefaultDirectoryPath(activeRecord.value)
    activeRecord.value = await patchAiGenerationRecord(workspaceCode.value, activeRecord.value.id, {
      generatedCases: activeRecord.value.generatedCases.map((item, index) => (
        index === row.index ? { ...item, savedDirectoryName } : item
      )),
      adoptedCaseIndexes: [...adopted],
      deletedCaseIndexes: [...discarded],
      savedCaseCount: adopted.size,
    })
    ElMessage.success('用例已采纳')
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function restoreSingleCase(row: DetailCaseRow) {
  if (!activeRecord.value || getCaseReviewState(row) !== 'DISCARDED') {
    return
  }
  const deleted = new Set(activeRecord.value.deletedCaseIndexes ?? [])
  deleted.delete(row.index)
  activeRecord.value = await patchAiGenerationRecord(workspaceCode.value, activeRecord.value.id, {
    deletedCaseIndexes: [...deleted],
  })
  casePreviewEditing.value = false
  syncCaseEditForm(activeCase.value)
  ElMessage.success('已取消弃用')
}

async function cancelGeneration() {
  if (!activeRecord.value || !['PENDING', 'GENERATING', 'REVIEWING'].includes(activeRecord.value.status)) {
    return
  }
  await ElMessageBox.confirm(
    '确认取消当前 AI 生成任务吗？取消后后续步骤将不再继续执行。',
    '取消生成任务',
    {
      type: 'warning',
      confirmButtonText: '取消生成',
      cancelButtonText: '返回',
    },
  )

  activeRecord.value = await cancelAiGenerationRecord(workspaceCode.value, activeRecord.value.id)
  ElMessage.success('已取消当前生成任务')
  await loadRecord()
}

async function copyRequirementContent() {
  const content = activeRecord.value?.requirementContent?.trim()
  if (!content) {
    ElMessage.warning('暂无需求描述可复制')
    return
  }
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('需求描述已复制')
  } catch {
    ElMessage.error('复制失败，请稍后重试')
  }
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function exportExcel() {
  if (!activeRecord.value) {
    return
  }
  exporting.value = true
  try {
      const rows = detailCases.value.map((item) => `
        <tr>
          <td>${escapeHtml(`CASE_${String(item.index + 1).padStart(3, '0')}`)}</td>
          <td>${escapeHtml(item.title ?? '')}</td>
          <td>${escapeHtml(item.precondition ?? '')}</td>
          <td>${escapeHtml(item.steps ?? '')}</td>
          <td>${escapeHtml(item.expectedResult ?? '')}</td>
          <td>${escapeHtml(item.priority ?? '')}</td>
          <td>${escapeHtml(getCaseReviewStateLabel(item))}</td>
        </tr>
      `).join('')

    const html = `
      <html xmlns:o="urn:schemas-microsoft-com:office:office"
            xmlns:x="urn:schemas-microsoft-com:office:excel"
            xmlns="http://www.w3.org/TR/REC-html40">
        <head><meta charset="UTF-8" /></head>
        <body>
          <table border="1">
            <tr><th colspan="7">AI生成用例记录</th></tr>
            <tr><td>任务ID</td><td colspan="6">${escapeHtml(activeRecord.value.id)}</td></tr>
            <tr><td>所属空间</td><td colspan="6">${escapeHtml(activeRecord.value.workspaceName || activeRecord.value.workspaceCode)}</td></tr>
            <tr><td>关联需求</td><td colspan="6">${escapeHtml(activeRecord.value.requirementTitle)}</td></tr>
            <tr><td>默认保存路径</td><td colspan="6">${escapeHtml(getDefaultDirectoryPath(activeRecord.value))}</td></tr>
            <tr><td>状态</td><td colspan="6">${escapeHtml(getStatusLabel(activeRecord.value.status))}</td></tr>
            <tr><td>生成时间</td><td colspan="6">${escapeHtml(formatTime(activeRecord.value.createdAt))}</td></tr>
            <tr><td>需求描述</td><td colspan="6">${escapeHtml(activeRecord.value.requirementContent || '')}</td></tr>
            <tr>
              <th>测试用例编号</th>
              <th>测试场景</th>
              <th>前置条件</th>
              <th>操作步骤</th>
              <th>预期结果</th>
              <th>优先级</th>
              <th>用例状态</th>
            </tr>
            ${rows}
          </table>
        </body>
      </html>
    `

    const blob = new Blob([`\uFEFF${html}`], { type: 'application/vnd.ms-excel;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${activeRecord.value.requirementTitle || 'ai-cases'}.xls`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } finally {
    exporting.value = false
  }
}

watch(() => route.params.taskId, () => {
  stopEventStream()
  seedRecordSnapshot(route.params.taskId?.toString())
  void loadRecord()
})

watch(casePreviewVisible, (visible) => {
  if (!visible) {
    casePreviewEditing.value = false
    activeCaseCursor.value = -1
  }
})

watch(availableCases, (rows) => {
  if (!rows.length) {
    if (casePreviewVisible.value) {
      casePreviewVisible.value = false
    }
    activeCaseCursor.value = -1
    return
  }
  if (activeCaseCursor.value >= rows.length) {
    activeCaseCursor.value = rows.length - 1
  }
  if (activeCaseCursor.value >= 0 && !casePreviewEditing.value) {
    syncCaseEditForm(activeCase.value)
  }
})

onMounted(() => {
  detailTableSettings.load()
  seedRecordSnapshot(route.params.taskId?.toString())
  void loadRecord()
})

onBeforeUnmount(() => {
  stopPolling()
  stopEventStream()
  if (streamRefreshTimer != null) {
    window.clearTimeout(streamRefreshTimer)
    streamRefreshTimer = null
  }
})
</script>

<template>
  <section class="detail-page-shell">
    <div v-if="activeRecord" class="detail-page-header">
      <div class="detail-page-header-row">
        <el-button class="back-button" text :icon="ArrowLeft" @click="goBack">返回记录页</el-button>
        <div class="detail-page-header-right">
          <div class="detail-page-path">
            <span class="detail-page-path-label">当前采纳保存路径：</span>
            <span class="detail-page-path-value">{{ getDefaultDirectoryPath(activeRecord) }}</span>
            <el-button class="path-edit-button" text @click="openPathDialog(activeRecord)">修改保存路径</el-button>
          </div>
          <div class="detail-page-header-actions">
          <el-button :icon="View" @click="openProcessDialog">查看流程</el-button>
          <el-button type="primary" :icon="Download" :loading="exporting" @click="exportExcel">导出 Excel</el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="activeRecord" class="panel-card detail-summary-card">
      <button class="detail-summary-toggle" type="button" @click="requirementExpanded = !requirementExpanded">
        <div class="detail-summary-header">
          <div class="detail-summary-title-row">
            <Memo class="detail-summary-title-icon" />
            <span class="detail-summary-title">{{ activeRecord.requirementTitle }}</span>
            <span class="detail-summary-inline-tip">点击展开查看完整需求内容</span>
          </div>
          <span class="detail-summary-arrow">
            <component :is="requirementExpanded ? ArrowUp : ArrowDown" />
          </span>
        </div>
      </button>

      <div v-if="requirementExpanded" class="detail-summary-expanded">
        <div class="detail-summary-content-shell">
          <div class="detail-summary-content">{{ activeRecord.requirementContent || '-' }}</div>
        </div>
        <div class="detail-summary-actions">
          <el-button class="copy-requirement-button" :icon="CopyDocument" @click="copyRequirementContent">复制需求描述</el-button>
        </div>
      </div>
    </div>

    <div v-if="showTaskOutputBoard && activeRecord" class="panel-card task-output-card" :class="{ 'is-failed': activeRecord.status === 'FAILED' }">
      <div class="task-output-header">
        <div>
          <div class="task-output-title">{{ outputBoardTitle }}</div>
          <div v-if="activeRecord.status !== 'COMPLETED'" class="task-output-subtitle">{{ activeRecord.stepMessage }}</div>
        </div>
        <div class="task-output-pills">
          <span class="status-pill" :class="outputBoardStatusClass">{{ outputBoardStatusLabel }}</span>
          <span v-if="showRealtimeOutputBoard" class="status-pill" :class="outputConnectionClass">{{ outputConnectionLabel }}</span>
        </div>
      </div>

      <template v-if="activeRecord.status === 'FAILED'">
        <div class="failure-detail-grid">
          <div class="failure-detail-item">
            <div class="failure-detail-label">失败阶段</div>
            <div class="failure-detail-value">{{ getFailureStageLabel(activeRecord) }}</div>
          </div>
          <div class="failure-detail-item">
            <div class="failure-detail-label">结束时间</div>
            <div class="failure-detail-value">{{ formatTime(activeRecord.finishedAt) }}</div>
          </div>
          <div class="failure-detail-item failure-detail-item-full">
            <div class="failure-detail-label">失败原因</div>
            <div class="failure-detail-value failure-detail-error">{{ activeRecord.errorMessage || activeRecord.stepMessage }}</div>
          </div>
          <div class="failure-detail-item failure-detail-item-full">
            <div class="failure-detail-label">建议处理</div>
            <ul class="failure-detail-list">
              <li v-for="item in getFailureSuggestions(activeRecord)" :key="item">{{ item }}</li>
            </ul>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="task-output-models">
          <span class="task-output-model-chip">
            <span class="task-output-model-label">生成模型</span>
            <span class="task-output-provider" :class="generationModelInfo.providerClass">{{ generationModelInfo.providerLabel }}</span>
            <span class="task-output-model-name">{{ generationModelInfo.modelLabel }}</span>
          </span>
          <span class="task-output-model-chip">
            <span class="task-output-model-label">评审模型</span>
            <span class="task-output-provider" :class="reviewModelInfo.providerClass">{{ reviewModelInfo.providerLabel }}</span>
            <span class="task-output-model-name">{{ reviewModelInfo.modelLabel }}</span>
          </span>
        </div>
        <div class="task-output-body">
          <div class="task-output-timeline">
            <div
              v-for="item in outputTimeline"
              :key="item.key"
              class="task-output-timeline-item"
              :class="{ 'is-active': item.active, 'is-done': item.done }"
            >
              <span class="task-output-timeline-dot" />
              <div class="task-output-timeline-main">
                <div class="task-output-timeline-label">{{ item.label }}</div>
                <div class="task-output-timeline-meta">{{ item.meta }}</div>
              </div>
            </div>
          </div>
          <div class="task-output-log">
            <div v-if="!outputEvents.length" class="task-output-empty">等待任务输出事件...</div>
            <div
              v-for="event in outputEvents"
              :key="event.seq"
              class="task-output-log-row"
              :class="getOutputEventClass(event)"
            >
              <span class="task-output-log-time">{{ formatEventTime(event.createdAt) }}</span>
              <span class="task-output-log-message">{{ event.message }}</span>
            </div>
          </div>
        </div>
      </template>

    </div>

    <div v-if="activeRecord" class="panel-card detail-toolbar-card">
      <div class="detail-toolbar-row">
          <div class="detail-toolbar-meta">
            <span>最终用例数：{{ activeRecord.generatedCases.length }}</span>
            <span>初始生成：{{ initialCaseCount }}</span>
            <span>已优化：{{ optimizedCaseCount }}</span>
            <span>已补充：{{ supplementedCaseCount }}</span>
            <span>建议确认：{{ confirmRequiredCaseCount }}</span>
            <span>不推荐：{{ notRecommendedCaseCount }}</span>
            <span>待处理：{{ pendingCaseCount }}</span>
            <span>已采纳：{{ adoptedCaseCount }}</span>
            <span>已弃用：{{ discardedCaseCount }}</span>
          </div>

        <div class="detail-toolbar-actions">
          <el-button
            class="batch-action-button batch-action-button-adopt"
            :class="{ 'is-active': selectedAdoptableCases.length > 0 }"
            :icon="Check"
            :disabled="selectedAdoptableCases.length === 0"
            @click="openAdoptDialog(activeRecord, 'selected')"
          >
            批量采纳（{{ selectedAdoptableCases.length }}）
          </el-button>
          <el-button
            class="batch-action-button batch-action-button-delete"
            :icon="CircleClose"
            :disabled="selectedDiscardableCases.length === 0"
            :class="{ 'is-active': selectedDiscardableCases.length > 0 }"
            @click="discardSelectedCases"
          >
            批量弃用（{{ selectedDiscardableCases.length }}）
          </el-button>
        </div>
      </div>
    </div>

    <div v-if="activeRecord" class="panel-card detail-table-card">
      <div class="detail-table-wrap">
        <el-table :data="availableCases" class="detail-case-table" border @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="52" />
          <el-table-column label="序号" type="index" width="72" align="center" />
          <template v-for="column in detailTableSettings.visibleColumns.value" :key="column.key">
            <el-table-column v-if="column.key === 'title'" label="用例标题" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="case-cell-clamp">{{ formatCaseCellText(row.title) }}</div>
              </template>
            </el-table-column>
            <el-table-column v-else-if="column.key === 'precondition'" label="前置条件" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="case-cell-clamp">{{ formatCaseCellText(row.precondition) }}</div>
              </template>
            </el-table-column>
            <el-table-column v-else-if="column.key === 'steps'" label="操作步骤" min-width="260" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="case-cell-clamp">{{ formatCaseCellText(row.steps) }}</div>
              </template>
            </el-table-column>
            <el-table-column v-else-if="column.key === 'expectedResult'" label="预期结果" min-width="240" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="case-cell-clamp">{{ formatCaseCellText(row.expectedResult) }}</div>
              </template>
            </el-table-column>
            <el-table-column v-else-if="column.key === 'savedDirectoryName'" label="最终保存路径" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="detail-cell-text">{{ getCaseSavedDirectoryName(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column v-else-if="column.key === 'priority'" label="优先级" width="88" align="center">
              <template #default="{ row }">
                <span class="priority-chip" :class="`priority-${row.priority?.toLowerCase?.() || 'p3'}`">{{ row.priority }}</span>
              </template>
            </el-table-column>
            <el-table-column v-else-if="column.key === 'aiReview'" label="AI评审" width="132" align="center" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="ai-review-cell">
                  <span class="status-pill" :class="getAiSourceClass(row)">
                    {{ getAiSourceLabel(row) }}
                  </span>
                  <span class="status-pill" :class="getAiReviewStatusClass(row)">
                    {{ getAiReviewStatusLabel(row) }}
                  </span>
                  <span v-if="row.aiReviewSummary" class="ai-review-summary">{{ row.aiReviewSummary }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column v-else-if="column.key === 'status'" label="状态" width="100" align="center">
              <template #default="{ row }">
                <span class="status-pill" :class="getCaseReviewStateClass(row)">
                  {{ getCaseReviewStateLabel(row) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column v-else-if="column.key === 'manualEdited'" label="人工修改" width="88" align="center">
              <template #default="{ row }">
                <span class="detail-cell-text">{{ row.manualEdited ? '是' : '否' }}</span>
              </template>
            </el-table-column>
            <el-table-column v-else-if="column.key === 'manualEditedByName'" label="操作人" width="120">
              <template #default="{ row }">
                <span class="detail-cell-text">{{ row.manualEditedByName || '-' }}</span>
              </template>
            </el-table-column>
          </template>
          <el-table-column width="120" fixed="right" align="center">
            <template #header>
              <div class="table-action-header">
                <span>操作</span>
                <el-button
                  text
                  class="table-settings-trigger"
                  @click="detailTableSettings.settingsVisible.value = true"
                >
                  <el-icon class="table-action-header-icon"><Setting /></el-icon>
                </el-button>
              </div>
            </template>
            <template #default="{ row }">
              <div class="table-action-row table-action-row-text">
                <el-button class="table-action-link" type="primary" text @click="openCasePreview(row)">查看详情</el-button>
                <el-button v-if="getCaseReviewState(row) === 'PENDING'" class="table-action-link" type="success" text @click="adoptSingleCase(row)">采纳</el-button>
                <el-button v-if="getCaseReviewState(row) === 'PENDING'" class="table-action-link" type="danger" text @click="discardSingleCase(row)">弃用</el-button>
                <el-button v-if="getCaseReviewState(row) === 'DISCARDED'" class="table-action-link table-action-link-neutral" text @click="restoreSingleCase(row)">取消弃用</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div v-else-if="loadingRecord" class="panel-card detail-empty-card">
      <div class="detail-empty-title">正在加载任务详情...</div>
      <div class="detail-empty-text">稍等一下，正在读取这条 AI 生成任务记录。</div>
    </div>

    <div v-else class="panel-card detail-empty-card">
      <div class="detail-empty-title">任务记录不存在</div>
      <div class="detail-empty-text">这条 AI 生成任务可能已被删除，或当前空间下无法访问。</div>
    </div>

    <el-drawer
      v-model="casePreviewVisible"
      size="760px"
      direction="rtl"
      destroy-on-close
      class="detail-preview-drawer"
    >
      <template #header>
        <div class="detail-preview-header detail-preview-header-review">
          <div>
            <div class="adopt-dialog-title">用例详情</div>
            <div class="detail-preview-subtitle">逐条审阅 AI 生成用例，可直接编辑后保存或采纳。</div>
          </div>
          <div v-if="activeCase" class="case-preview-tabbar">
            <button
              class="case-preview-tab"
              :class="{ 'is-active': casePreviewActiveTab === 'detail' }"
              type="button"
              @click="switchCasePreviewTab('detail')"
            >
              用例详情
            </button>
            <button
              class="case-preview-tab"
              :class="{ 'is-active': casePreviewActiveTab === 'analysis' }"
              type="button"
              @click="switchCasePreviewTab('analysis')"
            >
              AI 分析
            </button>
          </div>
        </div>
      </template>
      <template v-if="activeCase">
        <div class="case-preview-shell">
          <div ref="casePreviewScrollRef" class="case-preview-scroll">
          <template v-if="casePreviewActiveTab === 'detail'">
            <div class="detail-preview-layout">
              <div class="case-preview-tagline">
                <span class="status-pill" :class="getAiSourceClass(activeCase)">{{ getAiSourceLabel(activeCase) }}</span>
                <span class="status-pill" :class="getAiReviewStatusClass(activeCase)">{{ getAiReviewStatusLabel(activeCase) }}</span>
              </div>
              <div class="case-preview-layout">
                <div class="case-preview-item case-preview-item-full">
                  <div class="case-preview-label">用例标题</div>
                  <el-input
                    v-if="casePreviewEditing"
                    v-model="caseEditForm.title"
                    maxlength="200"
                    placeholder="请输入测试场景"
                  />
                  <div v-else class="case-preview-content">{{ activeCase.title || '-' }}</div>
                </div>
                <div class="case-preview-item case-preview-item-priority">
                  <div class="case-preview-label">用例优先级</div>
                  <el-segmented
                    v-if="casePreviewEditing"
                    v-model="caseEditForm.priority"
                    :options="['P0', 'P1', 'P2', 'P3']"
                    class="case-preview-priority-segmented"
                  />
                  <div v-else class="case-preview-priority-display">
                    <span class="priority-chip" :class="`priority-${activeCase.priority?.toLowerCase?.() || 'p3'}`">
                      {{ activeCase.priority || 'P3' }}
                    </span>
                  </div>
                </div>
                <div class="case-preview-item">
                  <div class="case-preview-label">前置条件</div>
                  <el-input
                    v-if="casePreviewEditing"
                    v-model="caseEditForm.precondition"
                    type="textarea"
                    :rows="6"
                    resize="none"
                    placeholder="请输入前置条件"
                  />
                  <div v-else class="case-preview-content">{{ activeCase.precondition || '-' }}</div>
                </div>
                <div class="case-preview-item">
                  <div class="case-preview-label">操作步骤</div>
                  <el-input
                    v-if="casePreviewEditing"
                    v-model="caseEditForm.steps"
                    type="textarea"
                    :rows="6"
                    resize="none"
                    placeholder="请输入操作步骤"
                  />
                  <div v-else class="case-preview-content">{{ activeCase.steps || '-' }}</div>
                </div>
                <div class="case-preview-item case-preview-item-full">
                  <div class="case-preview-label">预期结果</div>
                  <el-input
                    v-if="casePreviewEditing"
                    v-model="caseEditForm.expectedResult"
                    type="textarea"
                    :rows="5"
                    resize="none"
                    placeholder="请输入预期结果"
                  />
                  <div v-else class="case-preview-content">{{ activeCase.expectedResult || '-' }}</div>
                </div>
              </div>
            </div>
          </template>
          <template v-else>
            <div class="case-ai-analysis">
              <div class="case-ai-analysis-grid">
                <div class="case-preview-item">
                  <div class="case-preview-label">测试角度</div>
                  <div class="case-preview-content">{{ activeCase.testAngle || '-' }}</div>
                </div>
                <div class="case-preview-item">
                  <div class="case-preview-label">生成依据</div>
                  <div class="case-preview-content">{{ activeCase.requirementEvidence || '-' }}</div>
                </div>
                <div class="case-preview-item">
                  <div class="case-preview-label">生成原因</div>
                  <div class="case-preview-content">{{ activeCase.generationReason || '-' }}</div>
                </div>
                <div class="case-preview-item">
                  <div class="case-preview-label">评审意见</div>
                  <div class="case-preview-content">{{ activeCase.reviewComment || activeCase.aiReviewSummary || '-' }}</div>
                </div>
                <div v-if="activeCase.optimizationReason" class="case-preview-item">
                  <div class="case-preview-label">优化原因</div>
                  <div class="case-preview-content">{{ activeCase.optimizationReason }}</div>
                </div>
                <div v-if="activeCase.supplementReason || activeCase.coverageGap" class="case-preview-item">
                  <div class="case-preview-label">补充原因 / 覆盖缺口</div>
                  <div class="case-preview-content">{{ activeCase.supplementReason || activeCase.coverageGap }}</div>
                </div>
              </div>
              <div v-if="activeCase.originalCaseSnapshot" class="case-version-compare">
                <div class="case-version-card">
                  <div class="case-preview-label">原始版本</div>
                  <div class="case-version-title">{{ activeCase.originalCaseSnapshot.title || '-' }}</div>
                  <div class="case-version-content">{{ activeCase.originalCaseSnapshot.steps || '-' }}</div>
                  <div class="case-version-content">{{ activeCase.originalCaseSnapshot.expectedResult || '-' }}</div>
                </div>
                <div class="case-version-card is-current">
                  <div class="case-preview-label">优化后版本</div>
                  <div class="case-version-title">{{ activeCase.title || '-' }}</div>
                  <div class="case-version-content">{{ activeCase.steps || '-' }}</div>
                  <div class="case-version-content">{{ activeCase.expectedResult || '-' }}</div>
                </div>
              </div>
            </div>
          </template>
          </div>
        </div>
      </template>
      <template #footer>
        <div class="dialog-footer case-review-footer">
          <div class="case-review-footer-nav" v-if="availableCases.length">
            <el-button class="case-review-nav-button" :icon="ArrowLeft" :disabled="!canPreviewPreviousCase" @click="moveCasePreview(-1)">
              上一条
            </el-button>
            <div class="case-review-nav-counter">
              {{ activeCaseDisplayIndex }}/{{ availableCases.length }}
            </div>
            <el-button class="case-review-nav-button" :disabled="!canPreviewNextCase" @click="moveCasePreview(1)">
              下一条
              <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
          <div class="case-review-footer-right">
            <el-button v-if="casePreviewEditing" :icon="CircleClose" @click="cancelCaseEditing">取消编辑</el-button>
            <el-button
              v-else-if="activeCase && canEditCase(activeCase)"
              :icon="Memo"
              @click="startCaseEditing"
            >
              编辑
            </el-button>
            <el-button
              v-if="!casePreviewEditing && activeCase && getCaseReviewState(activeCase) === 'PENDING'"
              type="success"
              :icon="Check"
              @click="adoptSingleCase(activeCase)"
            >
              采纳
            </el-button>
            <el-button
              v-if="!casePreviewEditing && activeCase && getCaseReviewState(activeCase) === 'PENDING'"
              type="danger"
              plain
              :icon="CircleClose"
              @click="discardSingleCase(activeCase)"
            >
              弃用
            </el-button>
            <el-button
              v-if="!casePreviewEditing && activeCase && getCaseReviewState(activeCase) === 'ADOPTED'"
              type="success"
              :icon="Check"
              disabled
            >
              已采纳
            </el-button>
            <el-button
              v-if="!casePreviewEditing && activeCase && getCaseReviewState(activeCase) === 'DISCARDED'"
              type="info"
              :icon="CircleClose"
              disabled
            >
              已弃用
            </el-button>
            <el-button
              v-if="!casePreviewEditing && activeCase && getCaseReviewState(activeCase) === 'DISCARDED'"
              class="case-review-action-neutral"
              @click="restoreSingleCase(activeCase)"
            >
              取消弃用
            </el-button>
            <el-button
              v-if="casePreviewEditing"
              type="primary"
              :icon="Check"
              :loading="savingCaseEdit"
              @click="saveCasePreviewEdit"
            >
              保存
            </el-button>
          </div>
        </div>
      </template>
    </el-drawer>

    <AiGenerationProcessDialog
      v-model="processDialogVisible"
      :record="activeRecord"
      :steps="processSteps"
      :status-label="activeRecord ? getStatusLabel(activeRecord.status) : ''"
      :status-class="activeRecord ? getStatusClass(activeRecord.status) : ''"
      :show-cancel-button="!!activeRecord && ['PENDING', 'GENERATING', 'REVIEWING'].includes(activeRecord.status)"
      :cancel-disabled="!activeRecord || !['PENDING', 'GENERATING', 'REVIEWING'].includes(activeRecord.status)"
      @cancel="cancelGeneration"
    />

    <el-dialog v-model="adoptDialogVisible" width="620px" destroy-on-close class="adopt-dialog">
      <template #header>
        <div class="adopt-dialog-title">{{ adoptDialogMode === 'selected' ? '批量采纳' : '全部采纳' }}</div>
      </template>
      <template v-if="activeRecord">
        <div class="adopt-dialog-body">
          <div class="adopt-dialog-notice">
            <div class="adopt-dialog-copy">
              {{ `确定要${adoptDialogMode === 'selected' ? '批量采纳' : '全部采纳'}任务 "${activeRecord.requirementTitle}" 的 ${adoptDialogCases.length} 条用例吗？` }}
            </div>
            <div class="adopt-dialog-subcopy">
              采纳后会把本次任务中可用的生成用例统一保存到用例管理中。
            </div>
          </div>
          <div class="adopt-dialog-form-card">
            <div class="adopt-form-title">保存配置</div>
            <el-form label-position="top">
              <el-form-item required>
                <template #label>
                  <span>保存路径 <span class="dialog-required">*</span></span>
                </template>
                <el-select
                  v-model="adoptForm.directoryId"
                  :class="{ 'is-invalid-select': adoptPathTouched && adoptForm.directoryId == null }"
                  :loading="loadingAdoptDirectories"
                  placeholder="请选择保存路径"
                  @change="adoptPathTouched = true"
                >
                  <el-option
                    v-for="item in adoptDirectoryOptions"
                    :key="String(item.value)"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <div v-if="adoptPathTouched && adoptForm.directoryId == null" class="dialog-field-error">请选择保存路径</div>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </template>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="adoptDialogVisible = false">取消</el-button>
          <el-button type="primary" :icon="FolderOpened" :loading="adopting" @click="confirmAdoptAll">确认采纳</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="pathPickerVisible" width="760px" destroy-on-close class="path-picker-dialog">
      <template #header>
        <div class="adopt-dialog-title">选择保存路径</div>
      </template>
      <template v-if="activeRecord">
        <div class="path-picker-layout">
          <div class="path-picker-current">
            <span class="path-picker-current-label">当前保存路径</span>
            <span class="path-picker-current-value">{{ getDefaultDirectoryPath(activeRecord) }}</span>
          </div>
          <el-input
            v-model="pathPickerKeyword"
            clearable
            placeholder="搜索目录名称"
            class="path-picker-search"
          />
          <div class="path-picker-tree-panel" :class="{ 'is-invalid': pathTouched && pathForm.directoryId == null }">
            <div v-if="loadingPathDirectories" class="path-picker-empty">
              正在加载目录...
            </div>
            <div v-else-if="!filteredPathPickerDirectories.length" class="path-picker-empty">
              未找到匹配的目录
            </div>
            <el-tree
              v-else
              :data="filteredPathPickerDirectories"
              node-key="key"
              highlight-current
              :expand-on-click-node="false"
              :default-expanded-keys="pathForm.workspaceCode ? [`workspace:${pathForm.workspaceCode}`] : []"
              :current-node-key="pathPickerSelection.directoryId != null ? `dir:${pathPickerSelection.directoryId}` : undefined"
              class="path-picker-tree"
              @node-click="handlePathPickerNodeSelect"
            >
              <template #default="{ data }">
                <div class="path-picker-tree-node" :class="{ 'is-workspace': !data.selectable }">
                  <span class="path-picker-tree-node-label">{{ data.name }}</span>
                </div>
              </template>
            </el-tree>
          </div>
          <div v-if="pathTouched && pathForm.directoryId == null" class="dialog-field-error">请选择保存路径</div>
          <div class="path-picker-selected-panel">
            <div class="path-picker-selected-label">已选路径</div>
            <div class="path-picker-selected-value">
              {{ pathPickerSelectedFullPath || '请在上方目录树中选择保存路径' }}
            </div>
          </div>
        </div>
      </template>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="pathPickerVisible = false">取消</el-button>
          <el-button type="primary" :icon="FolderOpened" :loading="savingPath" :disabled="pathPickerSelection.directoryId == null" @click="confirmPathPickerSelection">确认修改</el-button>
        </div>
      </template>
    </el-dialog>

    <TableSettingsDrawer
      v-model="detailTableSettings.settingsVisible.value"
      :columns="detailTableSettings.settingsColumns.value.map(column => ({
        key: column.key,
        label: column.label,
        required: column.required,
        visible: column.required ? true : detailTableSettings.visibleColumns.value.some(item => item.key === column.key),
        draggable: detailTableSettings.canDragColumn(column.key),
      }))"
      :dragging-key="detailTableSettings.draggingColumnKey.value"
      @toggle-column="detailTableSettings.toggleColumnVisibility"
      @drag-start="detailTableSettings.handleDragStart"
      @drag-end="detailTableSettings.handleDragEnd"
      @drop-column="detailTableSettings.moveColumnToTarget"
      @reset="detailTableSettings.reset"
    />
  </section>
</template>

<style scoped>
.detail-page-shell {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.detail-toolbar-row,
.detail-toolbar-meta,
.detail-toolbar-actions,
.table-action-row,
.dialog-footer,
.detail-page-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.dialog-footer {
  justify-content: flex-end;
}

.detail-toolbar-row,
.detail-page-header-row {
  justify-content: space-between;
}

.detail-toolbar-card {
  padding: 0 20px;
  min-width: 0;
}

.detail-page-header {
  display: grid;
  gap: 12px;
}

.detail-page-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.detail-page-header-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  flex: 1 1 auto;
  flex-wrap: wrap;
}

.detail-page-header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.detail-page-path {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 14px;
  color: #344054;
}

.detail-page-path-label {
  color: #667085;
}

.detail-page-path-value {
  color: #101828;
}

.back-button {
  width: fit-content;
  min-height: 38px;
  padding: 0 10px 0 0;
  font-size: 15px;
  font-weight: 600;
  color: #175cd3;
}

.back-button :deep(.el-icon) {
  margin-right: 4px;
  font-size: 15px;
}

.detail-page-title {
  font-size: 36px;
  font-weight: 700;
  color: var(--text-main);
}

.detail-page-meta {
  font-size: 13px;
  color: var(--text-subtle);
}

.detail-summary-card,
.detail-table-card {
  padding: 0;
  min-width: 0;
}

.detail-table-wrap {
  width: 100%;
  max-width: 100%;
  overflow: hidden;
}

.detail-summary-toggle {
  display: block;
  width: 100%;
  min-height: 88px;
  padding: 0 28px;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.detail-summary-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 88px;
}

.detail-summary-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  line-height: 1.2;
}

.detail-summary-title,
.adopt-form-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.2;
}

.detail-summary-title-icon {
  color: #f04438;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  font-size: 16px;
  line-height: 1;
  flex: 0 0 auto;
}

.detail-summary-title-icon :deep(svg) {
  display: block;
  width: 18px;
  height: 18px;
}

.detail-summary-subtitle,
.adopt-dialog-copy,
.adopt-dialog-subcopy {
  font-size: 13px;
  color: #9aa4b2;
  line-height: 1.6;
}

.detail-summary-inline-tip {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-subtle);
  line-height: 1.5;
}

.detail-summary-arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  color: #98a2b3;
}

.detail-summary-arrow :deep(svg) {
  display: block;
  width: 16px;
  height: 16px;
}

.detail-summary-expanded {
  border-top: 1px solid rgba(226, 232, 240, 0.9);
  padding: 12px 16px;
}

.detail-summary-content-shell {
  padding: 10px 10px 8px;
  border-radius: 0;
  background: #f7f9fc;
}

.detail-summary-content {
  padding: 12px 14px;
  border-left: 4px solid #4f9cff;
  border-radius: 4px;
  background: #eef4fb;
  font-size: 13px;
  line-height: 1.8;
  color: #475467;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 402px;
  overflow: auto;
}

.detail-summary-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.task-output-card {
  padding: 18px 20px;
  border: 1px solid rgba(16, 24, 40, 0.08);
  background: rgba(255, 255, 255, 0.92);
}

.task-output-card.is-failed {
  border-color: rgba(240, 68, 56, 0.14);
  background: rgba(254, 242, 242, 0.72);
}

.task-output-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.task-output-title {
  font-size: 18px;
  font-weight: 700;
  color: #101828;
}

.task-output-card.is-failed .task-output-title {
  color: #7a271a;
}

.task-output-subtitle {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: #667085;
}

.task-output-pills {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.task-output-models {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
  color: #475467;
  font-size: 12px;
}

.task-output-model-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e4e7ec;
}

.task-output-model-label {
  color: #667085;
  font-weight: 600;
}

.task-output-provider {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 7px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  line-height: 20px;
}

.task-output-provider.provider-compatible {
  background: rgba(239, 246, 255, 0.95);
  color: #2563eb;
}

.task-output-provider.provider-openai {
  background: rgba(236, 253, 245, 0.95);
  color: #047857;
}

.task-output-provider.provider-deepseek {
  background: rgba(238, 242, 255, 0.95);
  color: #4f46e5;
}

.task-output-provider.provider-qwen {
  background: rgba(255, 247, 237, 0.95);
  color: #c2410c;
}

.task-output-provider.provider-anthropic {
  background: rgba(250, 245, 255, 0.95);
  color: #7e22ce;
}

.task-output-provider.provider-default {
  background: rgba(242, 244, 247, 0.95);
  color: #475467;
}

.task-output-model-name {
  max-width: 260px;
  overflow: hidden;
  color: #344054;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-output-body {
  display: grid;
  grid-template-columns: minmax(180px, 240px) minmax(0, 1fr);
  gap: 18px;
  margin-top: 12px;
}

.task-output-timeline {
  display: grid;
  gap: 12px;
  padding: 14px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #eef2f6;
}

.task-output-timeline-item {
  position: relative;
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr);
  gap: 10px;
  color: #667085;
}

.task-output-timeline-item + .task-output-timeline-item::before {
  content: '';
  position: absolute;
  left: 6px;
  top: -14px;
  width: 1px;
  height: 16px;
  background: #d0d5dd;
}

.task-output-timeline-dot {
  width: 12px;
  height: 12px;
  margin-top: 3px;
  border-radius: 999px;
  border: 2px solid #d0d5dd;
  background: #ffffff;
}

.task-output-timeline-item.is-active .task-output-timeline-dot {
  border-color: #2f6bff;
  box-shadow: 0 0 0 4px rgba(47, 107, 255, 0.12);
}

.task-output-timeline-item.is-done .task-output-timeline-dot {
  border-color: #12b76a;
  background: #12b76a;
}

.task-output-timeline-label {
  font-size: 13px;
  font-weight: 700;
  color: #344054;
}

.task-output-timeline-meta {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.4;
  color: #667085;
  word-break: break-word;
}

.task-output-log {
  min-height: 168px;
  max-height: 260px;
  overflow: auto;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid #eef2f6;
  background: #fcfcfd;
}

.task-output-empty {
  display: flex;
  align-items: center;
  min-height: 140px;
  color: #98a2b3;
  font-size: 13px;
}

.task-output-log-row {
  display: grid;
  grid-template-columns: 78px minmax(0, 1fr);
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid rgba(234, 236, 240, 0.8);
  font-size: 13px;
  line-height: 1.5;
}

.task-output-log-row:last-child {
  border-bottom: 0;
}

.task-output-log-time {
  color: #98a2b3;
  font-variant-numeric: tabular-nums;
}

.task-output-log-message {
  color: #344054;
  word-break: break-word;
}

.task-output-log-row.is-warn .task-output-log-message {
  color: #b54708;
}

.task-output-log-row.is-error .task-output-log-message {
  color: #b42318;
}

.failure-detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.failure-detail-title {
  font-size: 18px;
  font-weight: 700;
  color: #7a271a;
}

.failure-detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 16px;
}

.failure-detail-item {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.78);
}

.failure-detail-item-full {
  grid-column: 1 / -1;
}

.failure-detail-label {
  font-size: 12px;
  font-weight: 600;
  color: #b42318;
}

.failure-detail-value {
  font-size: 13px;
  line-height: 1.7;
  color: #344054;
  word-break: break-word;
}

.failure-detail-error {
  color: #7a271a;
}

.failure-detail-list {
  margin: 0;
  padding-left: 18px;
  color: #344054;
  font-size: 13px;
  line-height: 1.8;
}

.detail-toolbar-row {
  min-height: 68px;
  padding: 16px 2px;
}

.detail-toolbar-meta {
  gap: 10px;
  font-size: 14px;
  color: #344054;
  line-height: 1.4;
}

.detail-preview-header {
  display: grid;
  width: 100%;
  min-width: 0;
  gap: 4px;
}

.detail-preview-header-review {
  grid-template-columns: minmax(0, 1fr);
  align-items: start;
  gap: 6px;
  padding-right: 44px;
}

.detail-preview-subtitle {
  font-size: 13px;
  line-height: 1.5;
  color: var(--text-subtle);
}

.detail-preview-layout {
  display: grid;
  gap: 16px;
}

.case-review-nav-button {
  min-width: 88px;
  height: 34px;
  border-radius: 8px;
}

.case-review-nav-counter {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 64px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #ffffff;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  color: #344054;
}

.detail-preview-meta-label,
.case-preview-label {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--text-subtle);
}

.case-preview-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
}

.case-preview-item {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: #ffffff;
}

.case-preview-item-full {
  grid-column: auto;
}

.case-preview-item-priority {
  align-content: start;
}

.case-preview-priority-display {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
}

.case-preview-priority-segmented {
  width: fit-content;
}

.case-preview-content {
  min-height: 72px;
  padding: 14px 16px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.06);
  font-size: 13px;
  line-height: 1.8;
  color: #344054;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-preview-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 16px 20px 8px;
  align-items: flex-start;
  overflow: visible;
  position: relative;
  z-index: 3;
  background: #fff;
}

:global(.detail-preview-drawer .el-drawer__header) {
  margin-bottom: 0;
  padding: 16px 20px 8px;
  align-items: flex-start;
  overflow: visible;
  position: relative;
  z-index: 3;
  background: #fff;
}

.detail-preview-drawer :deep(.el-drawer__headerbtn) {
  position: absolute;
  top: 18px;
  right: 20px;
  z-index: 5;
}

:global(.detail-preview-drawer .el-drawer__headerbtn) {
  position: absolute;
  top: 18px;
  right: 20px;
  z-index: 5;
}

.detail-preview-drawer :deep(.el-drawer) {
  display: flex;
  flex-direction: column;
  max-height: 100vh;
}

.detail-preview-drawer :deep(.el-drawer__body) {
  flex: 1 1 auto;
  min-height: 0;
  overflow: unset;
  padding: 0 20px;
}

:global(.detail-preview-drawer .el-drawer__body) {
  flex: 1 1 auto;
  min-height: 0;
  overflow: unset;
  padding: 0 20px;
}

.detail-preview-drawer :deep(.el-drawer__footer) {
  flex: 0 0 auto;
  padding: 16px 20px 20px;
  border-top: 1px solid var(--line-soft);
  position: relative;
  z-index: 3;
  background: #fff;
}

:global(.detail-preview-drawer .el-drawer__footer) {
  flex: 0 0 auto;
  padding: 16px 20px 20px;
  border-top: 1px solid var(--line-soft);
  position: relative;
  z-index: 3;
  background: #fff;
}

.detail-preview-drawer :deep(.el-textarea__inner),
.detail-preview-drawer :deep(.el-input__wrapper) {
  border-radius: 10px;
}

.detail-preview-drawer :deep(.el-textarea__inner) {
  min-height: 132px !important;
  line-height: 1.7;
}

.case-review-footer {
  justify-content: space-between;
}

.case-review-footer-nav,
.case-review-footer-right {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.case-review-action-neutral {
  color: #667085;
  border-color: rgba(208, 213, 221, 0.92);
  background: #ffffff;
}

.case-review-action-neutral:hover,
.case-review-action-neutral:focus-visible {
  color: #475467;
  border-color: rgba(152, 162, 179, 0.96);
  background: #f8fafc;
}

.case-cell-clamp {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
  white-space: pre-line;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.75;
  font-weight: 500;
  color: #111827;
}

.detail-cell-text {
  color: var(--text-main);
}

.ai-review-cell {
  display: grid;
  justify-items: center;
  gap: 4px;
  min-width: 0;
}

.ai-review-summary {
  display: block;
  max-width: 112px;
  overflow: hidden;
  color: #667085;
  font-size: 11px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.path-edit-button {
  padding: 0;
  font-size: 13px;
  font-weight: 600;
  color: #175cd3;
}

.detail-toolbar-actions {
  gap: 14px;
}

.table-action-header {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #344054;
  font-weight: 700;
}

.table-settings-trigger {
  padding: 0;
  min-height: auto;
}

.table-action-header-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  color: #667085;
}

.table-action-header-icon :deep(svg) {
  display: block;
  width: 14px;
  height: 14px;
}

.detail-toolbar-actions :deep(.batch-action-button) {
  min-width: 132px;
  height: 40px;
  padding: 0 18px;
  border-width: 1px;
  border-style: solid;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 700;
}

.detail-toolbar-actions :deep(.batch-action-button.is-disabled),
.detail-toolbar-actions :deep(.batch-action-button:disabled) {
  background: #c9ced6;
  border-color: #c9ced6;
  color: #ffffff;
  opacity: 1;
}

.detail-toolbar-actions :deep(.batch-action-button-adopt.is-active:not(.is-disabled)) {
  background: #28b463;
  border-color: #28b463;
  color: #ffffff;
}

.detail-toolbar-actions :deep(.batch-action-button-delete.is-active:not(.is-disabled)) {
  background: #ef4d3f;
  border-color: #ef4d3f;
  color: #ffffff;
}

.copy-requirement-button {
  height: 32px;
  padding: 0 14px;
  border-color: #d0d5dd;
  color: #667085;
  background: #fff;
  border-radius: 6px;
  font-size: 13px;
}

.detail-case-table :deep(.el-table__header-wrapper th) {
  background: rgba(248, 250, 252, 0.96);
  color: var(--text-main);
  font-weight: 700;
}

.detail-case-table :deep(.el-table__cell) {
  padding-top: 14px;
  padding-bottom: 14px;
}

.detail-case-table :deep(.el-table-fixed-column--right) {
  background: var(--bg-panel);
  box-shadow: -8px 0 16px rgba(15, 23, 42, 0.06);
}

.detail-case-table :deep(.el-table__body-wrapper .el-scrollbar__wrap) {
  overflow-x: auto;
}

.table-action-row-text {
  display: grid;
  justify-items: center;
  align-content: center;
  grid-auto-rows: 24px;
  gap: 4px;
  min-height: 80px;
  flex-wrap: nowrap;
}

.table-action-link {
  width: 72px;
  height: 24px;
  margin: 0;
  padding: 0;
  justify-content: center;
}

.table-action-link-neutral {
  color: #667085;
}

.table-action-link-neutral:hover,
.table-action-link-neutral:focus-visible {
  color: #475467;
}

.priority-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 26px;
  padding: 0 8px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
}

.priority-p0 {
  background: rgba(254, 228, 226, 0.92);
  color: #b42318;
}

.priority-p1 {
  background: rgba(255, 247, 237, 0.92);
  color: #b54708;
}

.priority-p2 {
  background: rgba(239, 246, 255, 0.92);
  color: #1d4ed8;
}

.priority-p3 {
  background: rgba(242, 244, 247, 0.96);
  color: #475467;
}

.adopt-dialog-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
}

.adopt-dialog-body {
  display: grid;
  gap: 18px;
}

.adopt-dialog-notice {
  padding: 14px 16px;
  border: 1px solid rgba(59, 130, 246, 0.14);
  border-radius: 12px;
  background: rgba(239, 246, 255, 0.72);
}

.adopt-dialog-subcopy {
  margin-top: 8px;
  line-height: 1.7;
}

.adopt-dialog-form-card {
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: #fff;
}

.path-dialog-current {
  margin-bottom: 14px;
  font-size: 13px;
  line-height: 1.6;
  color: #667085;
}

.adopt-form-title {
  margin-bottom: 14px;
}

.dialog-required {
  color: #ef4444;
}

.adopt-dialog-form-card :deep(.is-invalid-select .el-select__wrapper) {
  box-shadow: 0 0 0 1px #f04438 inset;
}

.dialog-field-error {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: #f04438;
}

.path-picker-layout {
  display: grid;
  gap: 16px;
}

.path-picker-current {
  display: grid;
  gap: 6px;
}

.path-picker-current-label {
  font-size: 12px;
  color: #667085;
  line-height: 1.5;
}

.path-picker-current-value {
  font-size: 13px;
  line-height: 1.7;
  color: #344054;
  word-break: break-word;
}

.path-picker-tree-panel {
  min-height: 320px;
  max-height: 360px;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: #fff;
}

.path-picker-tree-panel.is-invalid {
  border-color: #f04438;
}

.path-picker-empty {
  min-height: 296px;
  display: grid;
  place-items: center;
  font-size: 13px;
  color: #98a2b3;
  text-align: center;
}

.path-picker-tree-node {
  display: flex;
  align-items: center;
  min-height: 34px;
  width: 100%;
}

.path-picker-tree-node.is-workspace {
  font-weight: 700;
  color: #101828;
  cursor: default;
}

.path-picker-tree-node-label {
  font-size: 13px;
  line-height: 1.7;
  color: #344054;
  word-break: break-word;
}

.path-picker-selected-panel {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.path-picker-selected-label {
  font-size: 12px;
  color: #667085;
  line-height: 1.5;
}

.path-picker-selected-value {
  font-size: 13px;
  line-height: 1.7;
  color: #344054;
  word-break: break-word;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-info {
  background: rgba(219, 234, 254, 0.92);
  color: #175cd3;
}

.status-warning {
  background: rgba(255, 245, 223, 0.92);
  color: #b54708;
}

.status-success {
  background: rgba(233, 248, 241, 0.92);
  color: #067647;
}

.status-danger {
  background: rgba(254, 228, 226, 0.92);
  color: #b42318;
}

.status-purple {
  background: rgba(243, 232, 255, 0.92);
  color: #7e22ce;
}

.status-neutral {
  background: rgba(242, 244, 247, 0.96);
  color: #475467;
}

.case-preview-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.case-preview-tabbar {
  position: relative;
  z-index: 4;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 28px;
  height: 40px;
  margin-top: 2px;
  background: #fff;
  box-shadow: inset 0 -1px 0 #b8c4d4, 0 4px 10px rgba(15, 23, 42, 0.06);
}

.case-preview-tab {
  position: relative;
  height: 40px;
  padding: 0;
  border: 0;
  background: transparent;
  font-size: 14px;
  font-weight: 600;
  line-height: 40px;
  color: #344054;
  cursor: pointer;
}

.case-preview-tab.is-active {
  color: #2563eb;
}

.case-preview-tab.is-active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  border-radius: 999px;
  background: #2563eb;
}

.case-preview-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding-top: 16px;
  padding-bottom: 20px;
  position: relative;
  background: #fff;
  clip-path: inset(0 0 0 0);
  overscroll-behavior: contain;
}

.case-preview-scroll > .detail-preview-layout,
.case-preview-scroll > .case-ai-analysis {
  margin-top: 0;
}

.case-preview-tagline {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.case-ai-analysis {
  display: grid;
  gap: 18px;
}

.case-ai-analysis-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.case-version-compare {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.case-version-card {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: #f8fafc;
}

.case-version-card.is-current {
  border-color: rgba(37, 99, 235, 0.18);
  background: rgba(239, 246, 255, 0.72);
}

.case-version-title {
  font-size: 14px;
  font-weight: 700;
  line-height: 1.6;
  color: #101828;
}

.case-version-content {
  font-size: 13px;
  line-height: 1.7;
  color: #475467;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-empty-card {
  min-height: 360px;
  display: grid;
  place-items: center;
  justify-content: center;
  gap: 14px;
  text-align: center;
  padding: 36px 24px;
}

.detail-empty-title {
  font-size: 22px;
  font-weight: 700;
  color: #2c3e50;
}

.detail-empty-text {
  font-size: 14px;
  color: #667085;
}

@media (max-width: 1200px) {
  .detail-page-title {
    font-size: 24px;
  }

  .detail-page-header {
    align-items: flex-start;
  }

  .task-output-header,
  .task-output-body {
    grid-template-columns: 1fr;
  }

  .task-output-header {
    flex-direction: column;
  }

  .task-output-pills {
    justify-content: flex-start;
  }

  .failure-detail-grid {
    grid-template-columns: 1fr;
  }

  .case-ai-analysis-grid,
  .case-version-compare {
    grid-template-columns: 1fr;
  }
}
</style>
