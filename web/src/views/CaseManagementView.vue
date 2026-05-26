<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  ArrowRight,
  Folder,
  FolderOpened,
  Fold,
  MoreFilled,
  Plus,
  RefreshRight,
  Setting,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { platformApi } from '../api/platform'
import CaseEditorDrawer from '../components/CaseEditorDrawer.vue'
import ListToolbar from '../components/ListToolbar.vue'
import { useCaseCenterShared } from '../composables/useCaseCenterShared'
import { useListToolbarState } from '../composables/useListToolbarState'
import TableSettingsDrawer from '../components/TableSettingsDrawer.vue'
import { saveCaseExecutionContext } from '../utils/caseExecutionContext'
import {
  executionStatusLabel,
  executionStatusTagClass,
  executionStatusTagType,
  formatDateTime,
  normalizeReviewStatus,
  reviewStatusLabel,
  reviewStatusTagClass,
  reviewStatusTagType,
  type ReviewStatus,
} from '../utils/casePresentation'
import type {
  BatchDeleteCasesPayload,
  BatchMoveCasesPayload,
  BatchUpdateCasesPayload,
  CaseDirectoryNode,
  CaseDirectoryWorkspace,
  CaseItem,
  CreateBugPayload,
  CreateCasePayload,
  ReviewCasePayload,
} from '../types/api'
type TreeNode = {
  id: string
  label: string
  workspaceCode: string
  type: 'root' | 'workspace' | 'module'
  directoryId: number | null
  children: TreeNode[]
}
type MoveTargetOption = {
  value: number | null
  label: string
}
type CaseColumnKey =
  | 'caseNo'
  | 'title'
  | 'priority'
  | 'sourceType'
  | 'reviewStatus'
  | 'reviewedByName'
  | 'reviewedAt'
  | 'executionStatus'
  | 'executorName'
  | 'executedAt'
  | 'workspaceName'
  | 'directoryName'
  | 'createdByName'
  | 'createdAt'
  | 'updatedByName'
  | 'updatedAt'
type CaseColumnSetting = {
  key: CaseColumnKey
  label: string
  width: string
  required?: boolean
  allOnly?: boolean
}
const ROOT_NODE_ID = 'case-directory-root'
const CASE_TABLE_SETTINGS_KEY = 'case-table-settings-v2'
const CASE_VIEW_MEMORY_KEY = 'case-view-memory-v1'
const DEFAULT_PAGE_SIZE = 10
const PAGE_SIZE_OPTIONS = [10, 20, 30, 40, 50]
const {
  workspaceCode,
  isAllScope,
  users,
  workspaces,
  writableWorkspaces,
  canWriteWorkspace,
  canCreateCase,
  loadSharedBase,
} = useCaseCenterShared()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const moduleSaving = ref(false)
const cases = ref<CaseItem[]>([])
const directoryWorkspaces = ref<CaseDirectoryWorkspace[]>([])
const total = ref(0)
const totalPages = ref(0)
const pageNo = ref(1)
const selectedNodeId = ref(ROOT_NODE_ID)
const treeRenderKey = ref(0)
const expandedTreeKeys = ref<string[]>([])
const selectedCaseIds = ref<number[]>([])
const moduleDialogVisible = ref(false)
const moduleDialogMode = ref<'create' | 'rename'>('create')
const moveDialogVisible = ref(false)
const caseEditorVisible = ref(false)
const caseEditorMode = ref<'create' | 'edit' | 'copy'>('create')
const reviewDrawerVisible = ref(false)
const batchMoveVisible = ref(false)
const batchEditVisible = ref(false)
const bugVisible = ref(false)
const caseCellOverflowState = reactive<Record<string, boolean>>({})
const moduleForm = reactive({
  label: '',
})
const moveForm = reactive({
  targetParentId: null as number | null,
})
const batchMoveForm = reactive<BatchMoveCasesPayload>({
  caseIds: [],
  targetDirectoryId: null,
})
const batchEditForm = reactive<BatchUpdateCasesPayload>({
  caseIds: [],
  priority: '',
  reviewStatus: '',
  executionStatus: '',
})
const caseForm = reactive<CreateCasePayload & { id: number | null; workspaceCode: string }>({
  id: null,
  workspaceCode: '',
  directoryId: null,
  title: '',
  caseType: 'FUNCTION',
  priority: 'P1',
  sourceType: '手工创建',
  caseStatus: '草稿',
  ownerId: null,
  precondition: '',
  steps: '',
  expectedResult: '',
})
const reviewForm = reactive<ReviewCasePayload & {
  id: number | null
  title: string
  priority: string
  precondition: string
  steps: string
  expectedResult: string
}>({
  id: null,
  title: '',
  priority: 'P1',
  precondition: '',
  steps: '',
  expectedResult: '',
  reviewStatus: 'PENDING',
  reviewComment: '',
})
const bugState = reactive<CreateBugPayload & { workspaceCode: string; caseId: number | null }>({
  workspaceCode: '',
  caseId: null,
  title: '',
  description: '',
  priority: 'P1',
  severity: 'HIGH',
  assigneeId: null,
  tags: [],
})
const caseFilters = reactive({
  keyword: '',
  priority: '',
  reviewStatus: '',
  executionStatus: '',
  executorName: '',
  createdByName: '',
  workspaceCode: '',
})
const caseFilterDefaults = {
  keyword: '',
  priority: '',
  reviewStatus: '',
  executionStatus: '',
  executorName: '',
  createdByName: '',
  workspaceCode: '',
}
const caseColumnSettings: CaseColumnSetting[] = [
  { key: 'caseNo', label: '用例编号', width: '168px', required: true },
  { key: 'title', label: '用例名称', width: 'minmax(320px, 1fr)', required: true },
  { key: 'priority', label: '优先级', width: '88px' },
  { key: 'sourceType', label: '用例来源', width: '120px' },
  { key: 'reviewStatus', label: '评审状态', width: '112px' },
  { key: 'reviewedByName', label: '评审人', width: '110px' },
  { key: 'reviewedAt', label: '评审时间', width: '156px' },
  { key: 'executionStatus', label: '执行状态', width: '112px' },
  { key: 'executorName', label: '执行人', width: '104px' },
  { key: 'executedAt', label: '执行时间', width: '156px' },
  { key: 'workspaceName', label: '所属空间', width: '128px' },
  { key: 'directoryName', label: '所属模块', width: '152px' },
  { key: 'createdByName', label: '创建人', width: '130px' },
  { key: 'createdAt', label: '创建时间', width: '176px' },
  { key: 'updatedByName', label: '更新人', width: '130px' },
  { key: 'updatedAt', label: '更新时间', width: '176px' },
]
const defaultVisibleColumns: Record<CaseColumnKey, boolean> = {
  caseNo: true,
  title: true,
  priority: true,
  sourceType: false,
  reviewStatus: true,
  reviewedByName: false,
  reviewedAt: false,
  executionStatus: true,
  executorName: true,
  executedAt: false,
  workspaceName: false,
  directoryName: true,
  createdByName: false,
  createdAt: false,
  updatedByName: false,
  updatedAt: false,
}
const caseTableColumns = computed(() => caseColumnSettings.map(column => ({
  key: column.key,
  label: column.label,
  required: column.required,
  defaultVisible: defaultVisibleColumns[column.key],
  allOnly: column.allOnly,
})))
const caseListToolbar = useListToolbarState({
  tableSettingsKey: CASE_TABLE_SETTINGS_KEY,
  filterStorageKey: 'case-list-filters-v1',
  columns: caseTableColumns,
  filters: caseFilters,
  filterDefaults: caseFilterDefaults,
  pageSizeEnabled: true,
  defaultPageSize: DEFAULT_PAGE_SIZE,
  pageSizeOptions: PAGE_SIZE_OPTIONS,
})
const pageSize = caseListToolbar.pageSize
const hydratingCaseViewMemory = ref(false)
const caseManageBootstrapped = ref(false)
let caseLoadSequence = 0

function resolveCaseViewMemoryKey(targetWorkspaceCode: string) {
  return `${CASE_VIEW_MEMORY_KEY}:${targetWorkspaceCode || '__EMPTY__'}`
}

function loadCaseViewMemory(targetWorkspaceCode: string) {
  const raw = localStorage.getItem(resolveCaseViewMemoryKey(targetWorkspaceCode))
  if (!raw) {
    return null
  }
  try {
    const parsed = JSON.parse(raw) as { selectedNodeId?: string; pageNo?: number; expandedTreeKeys?: string[] }
    return {
      selectedNodeId: parsed.selectedNodeId || ROOT_NODE_ID,
      pageNo: typeof parsed.pageNo === 'number' && parsed.pageNo > 0 ? parsed.pageNo : 1,
      expandedTreeKeys: Array.isArray(parsed.expandedTreeKeys) ? parsed.expandedTreeKeys.filter(item => typeof item === 'string') : [],
    }
  } catch {
    localStorage.removeItem(resolveCaseViewMemoryKey(targetWorkspaceCode))
    return null
  }
}

function persistCaseViewMemory(targetWorkspaceCode = workspaceCode.value) {
  if (hydratingCaseViewMemory.value) {
    return
  }
  localStorage.setItem(resolveCaseViewMemoryKey(targetWorkspaceCode), JSON.stringify({
    selectedNodeId: selectedNodeId.value,
    pageNo: pageNo.value,
    expandedTreeKeys: expandedTreeKeys.value,
  }))
}
function mapDirectoryNode(node: CaseDirectoryNode): TreeNode {
  return {
    id: `module:${node.id}`,
    label: node.name,
    workspaceCode: node.workspaceCode,
    type: 'module',
    directoryId: node.id,
    children: node.children.map(mapDirectoryNode),
  }
}
const visibleTreeWorkspaces = computed(() => {
  if (directoryWorkspaces.value.length) {
    return directoryWorkspaces.value
  }
  const available = workspaces.value.filter(item => !item.allScope)
  if (isAllScope.value) {
    return available.map(item => ({
      workspaceCode: item.code,
      workspaceName: item.name,
      children: [],
    }))
  }
  const current = available.find(item => item.code === workspaceCode.value)
  return current
    ? [{
        workspaceCode: current.code,
        workspaceName: current.name,
        children: [],
      }]
    : []
})
const treeData = computed<TreeNode[]>(() => [
  {
    id: ROOT_NODE_ID,
    label: '用例目录',
    workspaceCode: isAllScope.value ? 'ALL' : workspaceCode.value,
    type: 'root',
    directoryId: null,
    children: visibleTreeWorkspaces.value.map(item => ({
      id: `workspace:${item.workspaceCode}`,
      label: item.workspaceName,
      workspaceCode: item.workspaceCode,
      type: 'workspace',
      directoryId: null,
      children: item.children.map(mapDirectoryNode),
    })),
  },
])
function flattenNodes(nodes: TreeNode[]): TreeNode[] {
  const result: TreeNode[] = []
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
function collectExpandableNodeIds(nodes: TreeNode[]): string[] {
  const ids: string[] = []
  const stack = [...nodes]
  while (stack.length) {
    const current = stack.shift()
    if (!current) {
      continue
    }
    if (current.children.length) {
      ids.push(current.id)
      stack.unshift(...current.children)
    }
  }
  return ids
}
const flatTreeNodes = computed(() => flattenNodes(treeData.value))
const selectedNode = computed(() => flatTreeNodes.value.find(item => item.id === selectedNodeId.value) ?? null)
const selectedDirectoryNode = computed(() => selectedNode.value?.type === 'module' ? selectedNode.value : null)
function findParentNode(targetId: string): TreeNode | null {
  for (const node of flatTreeNodes.value) {
    if (node.children.some(child => child.id === targetId)) {
      return node
    }
  }
  return null
}
function findTreeNode(targetId: string): TreeNode | null {
  return flatTreeNodes.value.find(item => item.id === targetId) ?? null
}
function collectDescendantNodeIds(node: TreeNode): string[] {
  const ids: string[] = []
  const stack = [...node.children]
  while (stack.length) {
    const current = stack.pop()
    if (!current) {
      continue
    }
    ids.push(current.id)
    if (current.children.length) {
      stack.push(...current.children)
    }
  }
  return ids
}
const selectedNodePath = computed(() => {
  const node = selectedNode.value
  if (!node || node.type === 'root') {
    return '用例目录'
  }
  const labels: string[] = []
  let cursor: TreeNode | null = node
  while (cursor && cursor.type !== 'root') {
    labels.unshift(cursor.label)
    cursor = findParentNode(cursor.id)
  }
  return `用例目录 / ${labels.join(' / ')}`
})
const activeOwnerOptions = computed(() => users.value.filter(item => item.status === 1))
const caseUserOptions = computed(() => users.value
  .filter(item => item.status === 1)
  .map(item => item.displayName))
const canSubmitCase = computed(() => !!caseForm.title.trim() && !!caseForm.workspaceCode)
const canSubmitModule = computed(() => !!moduleForm.label.trim())
const canSubmitBug = computed(() => {
  const hasWorkspace = !isAllScope.value || !!bugState.workspaceCode
  return hasWorkspace && !!bugState.title.trim() && !!bugState.description.trim()
})
const canCreateCaseInSelection = computed(() => {
  const node = selectedNode.value
  if (!node || node.type === 'root') {
    return canCreateCase.value
  }
  return canWriteWorkspace(node.workspaceCode)
})
const activeListWorkspaceCode = computed(() => {
  const node = selectedNode.value
  if (!node || node.type === 'root') {
    return workspaceCode.value
  }
  return node.workspaceCode
})
const activeDirectoryId = computed(() => selectedNode.value?.type === 'module' ? selectedNode.value.directoryId : null)
function getWorkspaceNameByCode(targetWorkspaceCode: string) {
  return directoryWorkspaces.value.find(item => item.workspaceCode === targetWorkspaceCode)?.workspaceName ?? targetWorkspaceCode
}
function formatCaseModulePath(targetWorkspaceCode: string, targetDirectoryId: number | null) {
  const workspaceName = getWorkspaceNameByCode(targetWorkspaceCode)
  if (targetDirectoryId === null) {
    return workspaceName
  }
  const option = buildDirectoryOptions(getWorkspaceDirectories(targetWorkspaceCode)).find(item => item.value === targetDirectoryId)
  return option ? `${workspaceName} / ${option.label}` : workspaceName
}
function normalizeCaseStatus(status: string) {
  return status?.trim() || '草稿'
}
function resetCaseForm() {
  caseForm.id = null
  caseForm.workspaceCode = isAllScope.value ? '' : workspaceCode.value
  caseForm.directoryId = null
  caseForm.title = ''
  caseForm.caseType = 'FUNCTION'
  caseForm.priority = 'P1'
  caseForm.sourceType = '手工创建'
  caseForm.caseStatus = '草稿'
  caseForm.ownerId = null
  caseForm.precondition = ''
  caseForm.steps = ''
  caseForm.expectedResult = ''
}
function resetReviewForm() {
  reviewForm.id = null
  reviewForm.title = ''
  reviewForm.priority = 'P1'
  reviewForm.precondition = ''
  reviewForm.steps = ''
  reviewForm.expectedResult = ''
  reviewForm.reviewStatus = 'PENDING'
  reviewForm.reviewComment = ''
}
function resetBatchEditForm() {
  batchEditForm.caseIds = []
  batchEditForm.priority = ''
  batchEditForm.reviewStatus = ''
  batchEditForm.executionStatus = ''
}
function ensureSelectedNode() {
  if (selectedNode.value) {
    return
  }
  if (isAllScope.value) {
    selectedNodeId.value = ROOT_NODE_ID
    return
  }
  selectedNodeId.value = treeData.value[0]?.children[0]?.id ?? ROOT_NODE_ID
}
function sanitizeExpandedTreeKeys(keys: string[]) {
  const available = new Set(collectExpandableNodeIds(treeData.value))
  const normalized = keys.filter(key => available.has(key))
  if (available.has(ROOT_NODE_ID) && !normalized.includes(ROOT_NODE_ID)) {
    normalized.unshift(ROOT_NODE_ID)
  }
  return normalized
}
function syncExpandedTreeKeys(expandAll = true, preferredKeys?: string[]) {
  if (preferredKeys) {
    const nextKeys = sanitizeExpandedTreeKeys(preferredKeys)
    expandedTreeKeys.value = nextKeys.length ? nextKeys : [ROOT_NODE_ID]
  } else {
    expandedTreeKeys.value = expandAll ? [...collectExpandableNodeIds(treeData.value)] : [ROOT_NODE_ID]
  }
  treeRenderKey.value += 1
}
function ensureSelectedNodeExpanded() {
  if (!selectedNodeId.value || selectedNodeId.value === ROOT_NODE_ID) {
    return
  }
  const keys = new Set(expandedTreeKeys.value)
  let cursor = findParentNode(selectedNodeId.value)
  while (cursor) {
    if (cursor.children.length) {
      keys.add(cursor.id)
    }
    cursor = findParentNode(cursor.id)
  }
  expandedTreeKeys.value = sanitizeExpandedTreeKeys([...keys])
}
function getWorkspaceDirectories(targetWorkspaceCode: string) {
  return directoryWorkspaces.value.find(item => item.workspaceCode === targetWorkspaceCode)?.children ?? []
}
function buildDirectoryOptions(nodes: CaseDirectoryNode[], prefix = ''): MoveTargetOption[] {
  const options: MoveTargetOption[] = []
  for (const node of nodes) {
    const label = prefix ? `${prefix} / ${node.name}` : node.name
    options.push({ value: node.id, label })
    if (node.children.length) {
      options.push(...buildDirectoryOptions(node.children, label))
    }
  }
  return options
}
const currentDirectoryOptions = computed<MoveTargetOption[]>(() => {
  if (!caseForm.workspaceCode) {
    return []
  }
  return [{ value: null, label: '空间根目录' }, ...buildDirectoryOptions(getWorkspaceDirectories(caseForm.workspaceCode))]
})
function collectDescendantDirectoryIds(node: TreeNode) {
  const ids = new Set<number>()
  const stack = [node]
  while (stack.length) {
    const current = stack.pop()
    if (!current) {
      continue
    }
    if (current.type === 'module' && current.directoryId !== null) {
      ids.add(current.directoryId)
    }
    stack.push(...current.children)
  }
  return ids
}
const moveTargetOptions = computed<MoveTargetOption[]>(() => {
  const node = selectedDirectoryNode.value
  if (!node) {
    return []
  }
  const workspace = directoryWorkspaces.value.find(item => item.workspaceCode === node.workspaceCode)
  if (!workspace) {
    return []
  }
  const blockedIds = collectDescendantDirectoryIds(node)
  const options: MoveTargetOption[] = [{ value: null, label: workspace.workspaceName }]
  const walk = (items: CaseDirectoryNode[], prefix: string[]) => {
    for (const item of items) {
      if (item.id === node.directoryId || blockedIds.has(item.id)) {
        continue
      }
      const label = [...prefix, item.name].join(' / ')
      options.push({ value: item.id, label })
      if (item.children.length) {
        walk(item.children, [...prefix, item.name])
      }
    }
  }
  walk(workspace.children, [workspace.workspaceName])
  return options
})
const hasSelection = computed(() => selectedCaseIds.value.length > 0)
const filteredCases = computed(() => cases.value.filter((item) => {
  const keyword = caseFilters.keyword.trim().toLowerCase()
  if (keyword) {
    const matched = item.caseNo.toLowerCase().includes(keyword) || item.title.toLowerCase().includes(keyword)
    if (!matched) {
      return false
    }
  }
  if (caseFilters.priority && item.priority !== caseFilters.priority) {
    return false
  }
  if (caseFilters.reviewStatus && item.reviewStatus !== caseFilters.reviewStatus) {
    return false
  }
  if (caseFilters.executionStatus && item.executionStatus !== caseFilters.executionStatus) {
    return false
  }
  if (caseFilters.executorName && item.executorName !== caseFilters.executorName) {
    return false
  }
  if (caseFilters.createdByName && item.createdByName !== caseFilters.createdByName) {
    return false
  }
  if (isAllScope.value && caseFilters.workspaceCode && item.workspaceCode !== caseFilters.workspaceCode) {
    return false
  }
  return true
}))
const allCurrentPageSelected = computed(() => (
  filteredCases.value.length > 0 && selectedCaseIds.value.length === filteredCases.value.length
))
const selectionIndeterminate = computed(() => (
  selectedCaseIds.value.length > 0 && selectedCaseIds.value.length < filteredCases.value.length
))
const batchMoveWorkspaceCode = computed(() => {
  const rows = filteredCases.value.filter(item => selectedCaseIds.value.includes(item.id))
  const workspaceCodes = [...new Set(rows.map(item => item.workspaceCode))]
  return workspaceCodes.length === 1 ? workspaceCodes[0] : ''
})
const batchMoveDirectoryOptions = computed<MoveTargetOption[]>(() => {
  if (!batchMoveWorkspaceCode.value) {
    return []
  }
  return [{ value: null, label: '空间根目录' }, ...buildDirectoryOptions(getWorkspaceDirectories(batchMoveWorkspaceCode.value))]
})
const currentCaseIndex = computed(() => {
  const activeId = caseEditorVisible.value && caseEditorMode.value === 'edit'
    ? caseForm.id
    : reviewDrawerVisible.value
      ? reviewForm.id
      : null
  if (activeId === null) {
    return -1
  }
  return filteredCases.value.findIndex(item => item.id === activeId)
})
const currentCaseDisplayIndex = computed(() => (currentCaseIndex.value >= 0 ? currentCaseIndex.value + 1 : 0))
const canGoPrev = computed(() => currentCaseIndex.value > 0)
const canGoNext = computed(() => currentCaseIndex.value >= 0 && currentCaseIndex.value < filteredCases.value.length - 1)
const visibleColumns = computed(() => caseListToolbar.visibleColumns.value
  .map(column => caseColumnSettings.find(item => item.key === column.key))
  .filter((item): item is CaseColumnSetting => !!item))
const caseGridTemplateColumns = computed(() => ['56px', ...visibleColumns.value.map(item => item.width)].join(' '))
const caseGridMinWidth = computed(() => {
  const totalWidth = 56 + visibleColumns.value.reduce((total, item) => {
    if (item.width.includes('minmax')) {
      return total + 320
    }
    return total + Number.parseInt(item.width, 10)
  }, 0)
  return `${totalWidth}px`
})
function caseEditorTitle() {
  if (caseEditorMode.value === 'create') {
    return '新建用例'
  }
  if (caseEditorMode.value === 'copy') {
    return '复制用例'
  }
  return '编辑用例'
}
function canWriteRow(row: CaseItem) {
  return canWriteWorkspace(row.workspaceCode)
}
function resetCaseFilters() {
  caseListToolbar.filterMemory.reset()
}
function updatePageSizeSetting(size: number) {
  caseListToolbar.updatePageSize(size)
  pageNo.value = 1
}
function getCaseColumnValue(row: CaseItem, key: CaseColumnKey) {
  switch (key) {
    case 'caseNo':
      return row.caseNo
    case 'title':
      return row.title
    case 'priority':
      return row.priority
    case 'sourceType':
      return row.sourceType || '-'
    case 'reviewStatus':
      return reviewStatusLabel(row.reviewStatus)
    case 'reviewedByName':
      return row.reviewedByName || '-'
    case 'reviewedAt':
      return formatDateTime(row.reviewedAt)
    case 'executionStatus':
      return executionStatusLabel(row.executionStatus)
    case 'executorName':
      return row.executorName || '-'
    case 'executedAt':
      return formatDateTime(row.executedAt)
    case 'workspaceName':
      return row.workspaceName
    case 'directoryName':
      return formatCaseModulePath(row.workspaceCode, row.directoryId)
    case 'createdByName':
      return row.createdByName || '-'
    case 'createdAt':
      return formatDateTime(row.createdAt)
    case 'updatedByName':
      return row.updatedByName || '-'
    case 'updatedAt':
      return formatDateTime(row.updatedAt)
    default:
      return '-'
  }
}

function shouldUseCellTooltip(key: CaseColumnKey) {
  return key === 'title'
    || key === 'directoryName'
}
function makeCaseCellOverflowKey(caseId: number, key: CaseColumnKey) {
  return `${caseId}:${key}`
}
function updateCaseCellOverflow(caseId: number, key: CaseColumnKey, event: MouseEvent) {
  const target = event.currentTarget as HTMLElement | null
  if (!target) {
    return
  }
  caseCellOverflowState[makeCaseCellOverflowKey(caseId, key)] = target.scrollWidth > target.clientWidth
}
function shouldShowCaseCellTooltip(caseId: number, key: CaseColumnKey) {
  return !!caseCellOverflowState[makeCaseCellOverflowKey(caseId, key)]
}
function isTreeNodeExpanded(nodeId: string) {
  return expandedTreeKeys.value.includes(nodeId)
}
async function loadCases() {
  const requestId = ++caseLoadSequence
  loading.value = true
  try {
    const previousExpandedKeys = [...expandedTreeKeys.value]
    const [directoryList, casePage] = await Promise.all([
      platformApi.getCaseDirectories(workspaceCode.value),
      platformApi.getCases(activeListWorkspaceCode.value, {
        pageNo: pageNo.value,
        pageSize: pageSize.value,
        directoryId: activeDirectoryId.value,
      }),
    ])
    if (requestId !== caseLoadSequence) {
      return
    }
    directoryWorkspaces.value = directoryList
    cases.value = casePage.items
    total.value = casePage.total
    totalPages.value = casePage.totalPages
    pageNo.value = casePage.pageNo
    pageSize.value = casePage.pageSize
    selectedCaseIds.value = []
    syncExpandedTreeKeys(false, previousExpandedKeys.length ? previousExpandedKeys : undefined)
    ensureSelectedNode()
    ensureSelectedNodeExpanded()
  }
  catch (error) {
    if (requestId !== caseLoadSequence) {
      return
    }
    ElMessage.error((error as Error).message)
  }
  finally {
    if (requestId === caseLoadSequence) {
      loading.value = false
    }
  }
}
async function bootstrap() {
  caseListToolbar.load()
  caseManageBootstrapped.value = false
  const memory = loadCaseViewMemory(workspaceCode.value)
  hydratingCaseViewMemory.value = true
  try {
    selectedNodeId.value = memory?.selectedNodeId ?? ROOT_NODE_ID
    pageNo.value = memory?.pageNo ?? 1
    expandedTreeKeys.value = memory?.expandedTreeKeys?.length ? memory.expandedTreeKeys : [ROOT_NODE_ID]
  } finally {
    hydratingCaseViewMemory.value = false
  }
  await loadSharedBase()
  await loadCases()
  caseManageBootstrapped.value = true
  void maybeOpenEditCaseFromRoute()
}
function currentRouteQueryRecord() {
  return Object.fromEntries(
    Object.entries(route.query)
      .filter(([, value]) => typeof value === 'string')
      .map(([key, value]) => [key, value as string]),
  )
}
function getPreferredWorkspaceCode() {
  const node = selectedNode.value
  if (!node || node.type === 'root') {
    return isAllScope.value ? '' : workspaceCode.value
  }
  return node.workspaceCode
}
async function maybeOpenEditCaseFromRoute() {
  const rawId = route.query.editCaseId?.toString()
  if (!rawId || !caseManageBootstrapped.value) {
    return
  }
  const caseId = Number(rawId)
  if (!Number.isFinite(caseId) || caseEditorVisible.value) {
    return
  }
  await router.replace({
    path: route.path,
    query: Object.fromEntries(Object.entries(route.query).filter(([key]) => key !== 'editCaseId')),
  })
  await openCaseEdit(caseId)
}
function openCaseCreate() {
  if (isAllScope.value && selectedNode.value?.type === 'root') {
    ElMessage.warning('请先在目录树中选择一个具体空间或模块后再新建用例')
    return
  }
  resetCaseForm()
  caseEditorMode.value = 'create'
  caseForm.workspaceCode = getPreferredWorkspaceCode()
  caseForm.directoryId = selectedDirectoryNode.value?.directoryId ?? null
  caseEditorVisible.value = true
}
async function fillCaseFormById(id: number, mode: 'edit' | 'copy') {
  const detail = await platformApi.getCaseDetail(activeListWorkspaceCode.value, id)
  caseEditorMode.value = mode
  caseForm.id = mode === 'edit' ? detail.id : null
  caseForm.workspaceCode = detail.workspaceCode
  caseForm.directoryId = detail.directoryId
  caseForm.title = mode === 'copy' ? `Copy-${detail.title}` : detail.title
  caseForm.caseType = detail.caseType
  caseForm.priority = detail.priority
  caseForm.sourceType = detail.sourceType
  caseForm.caseStatus = detail.status
  caseForm.ownerId = detail.ownerId
  caseForm.precondition = detail.precondition ?? ''
  caseForm.steps = detail.steps ?? ''
  caseForm.expectedResult = detail.expectedResult ?? ''
}
async function openCaseEdit(id: number) {
  try {
    await fillCaseFormById(id, 'edit')
    caseEditorVisible.value = true
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}
async function openCaseCopy(id: number) {
  try {
    await fillCaseFormById(id, 'copy')
    caseEditorVisible.value = true
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}
function executeCase(row: CaseItem) {
  saveCaseExecutionContext({
    workspaceCode: activeListWorkspaceCode.value,
    returnQuery: currentRouteQueryRecord(),
    selectedNodePath: selectedNodePath.value,
    sourceLabel: activeListWorkspaceCode.value === workspaceCode.value ? '当前列表' : '跨空间列表',
    items: filteredCases.value,
  })
  void router.push({
    name: 'cases-execute',
    params: { id: row.id },
    query: route.query,
  })
}
async function openCaseReview(id: number) {
  try {
    const detail = await platformApi.getCaseDetail(activeListWorkspaceCode.value, id)
    reviewForm.id = detail.id
    reviewForm.title = detail.title
    reviewForm.priority = detail.priority
    reviewForm.precondition = detail.precondition ?? ''
    reviewForm.steps = detail.steps ?? ''
    reviewForm.expectedResult = detail.expectedResult ?? ''
    reviewForm.reviewStatus = normalizeReviewStatus(detail.reviewStatus)
    reviewForm.reviewComment = detail.reviewComment ?? ''
    reviewDrawerVisible.value = true
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}
async function submitCase() {
  if (isAllScope.value && !caseForm.workspaceCode) {
    ElMessage.error('全部空间视角下请先选择目标空间')
    return
  }
  if (!caseForm.title.trim()) {
    ElMessage.error('请输入用例名称')
    return
  }
  saving.value = true
  try {
    const payload: CreateCasePayload = {
      workspaceCode: isAllScope.value ? caseForm.workspaceCode : undefined,
      directoryId: caseForm.directoryId,
      title: caseForm.title.trim(),
      caseType: 'FUNCTION',
      priority: caseForm.priority,
      sourceType: caseForm.sourceType.trim(),
      caseStatus: normalizeCaseStatus(caseForm.caseStatus),
      ownerId: null,
      precondition: caseForm.precondition.trim(),
      steps: caseForm.steps.trim(),
      expectedResult: caseForm.expectedResult.trim(),
    }
    if (caseEditorMode.value === 'edit' && caseForm.id !== null) {
      await platformApi.updateCase(activeListWorkspaceCode.value, caseForm.id, payload)
      ElMessage.success('用例已更新')
    } else {
      await platformApi.createCase(activeListWorkspaceCode.value, payload)
      ElMessage.success(caseEditorMode.value === 'copy' ? '复制用例已创建' : '用例已创建')
      pageNo.value = 1
    }
    caseEditorVisible.value = false
    await loadCases()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}
async function submitReview(status: ReviewStatus) {
  if (!reviewForm.id) {
    return
  }
  saving.value = true
  try {
    await platformApi.reviewCase(activeListWorkspaceCode.value, reviewForm.id, {
      reviewStatus: status,
      reviewComment: reviewForm.reviewComment?.trim() || '',
    })
    reviewForm.reviewStatus = status
    ElMessage.success(status === 'PASSED' ? '评审已通过' : '评审已驳回')
    await loadCases()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}
async function confirmDeleteCase(row: CaseItem) {
  try {
    await ElMessageBox.confirm(`确认删除用例“${row.title}”吗？`, '删除确认', { type: 'warning' })
    await platformApi.deleteCase(activeListWorkspaceCode.value, row.id)
    ElMessage.success('用例已删除')
    if (filteredCases.value.length === 1 && pageNo.value > 1) {
      pageNo.value -= 1
    }
    await loadCases()
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}
function openBugDialog(row: CaseItem) {
  bugState.workspaceCode = row.workspaceCode
  bugState.caseId = row.id
  bugState.title = `${row.title} - 缺陷`
  bugState.description = ''
  bugState.priority = 'P1'
  bugState.severity = 'HIGH'
  bugState.assigneeId = null
  bugState.tags = []
  bugVisible.value = true
}
async function submitCaseBug() {
  if (!bugState.caseId) {
    return
  }
  if (isAllScope.value && !bugState.workspaceCode) {
    ElMessage.error('请先选择目标空间')
    return
  }
  if (!bugState.title.trim()) {
    ElMessage.error('请输入缺陷标题')
    return
  }
  if (!bugState.description.trim()) {
    ElMessage.error('请输入缺陷描述')
    return
  }
  saving.value = true
  try {
    await platformApi.createBugFromCase(activeListWorkspaceCode.value, bugState.caseId, {
      workspaceCode: isAllScope.value ? bugState.workspaceCode : undefined,
      title: bugState.title.trim(),
      description: bugState.description.trim(),
      priority: bugState.priority,
      severity: bugState.severity,
      assigneeId: bugState.assigneeId,
      tags: bugState.tags,
    })
    ElMessage.success('已从用例创建缺陷')
    bugVisible.value = false
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}
function handleTreeSelect(data: TreeNode | null) {
  selectedNodeId.value = data?.id ?? ROOT_NODE_ID
  pageNo.value = 1
}
function collapseAllTreeChildren() {
  syncExpandedTreeKeys(false)
}
function handleTreeNodeExpand(_: TreeNode, treeNode: { key: string }) {
  const keys = new Set(expandedTreeKeys.value)
  keys.add(String(treeNode.key))
  expandedTreeKeys.value = sanitizeExpandedTreeKeys([...keys])
  persistCaseViewMemory()
}
function handleTreeNodeCollapse(_: TreeNode, treeNode: { key: string }) {
  const collapsedKey = String(treeNode.key)
  const currentNode = findTreeNode(collapsedKey)
  if (!currentNode) {
    expandedTreeKeys.value = expandedTreeKeys.value.filter(key => key !== collapsedKey)
    persistCaseViewMemory()
    return
  }
  const blockedKeys = new Set([collapsedKey, ...collectDescendantNodeIds(currentNode)])
  expandedTreeKeys.value = expandedTreeKeys.value.filter(key => !blockedKeys.has(key))
  persistCaseViewMemory()
}
function openCreateModuleForNode(node: TreeNode) {
  if (node.type === 'root' || !canWriteWorkspace(node.workspaceCode)) {
    return
  }
  selectedNodeId.value = node.id
  moduleDialogMode.value = 'create'
  moduleForm.label = ''
  moduleDialogVisible.value = true
}
function openRenameModule(node: TreeNode) {
  if (node.type !== 'module' || !canWriteWorkspace(node.workspaceCode)) {
    return
  }
  selectedNodeId.value = node.id
  moduleDialogMode.value = 'rename'
  moduleForm.label = node.label
  moduleDialogVisible.value = true
}
async function submitModule() {
  const node = selectedNode.value
  if (!node || node.type === 'root') {
    ElMessage.error('请先选择目标目录')
    return
  }
  if (!moduleForm.label.trim()) {
    ElMessage.error('请先填写模块名称')
    return
  }
  moduleSaving.value = true
  try {
    if (moduleDialogMode.value === 'rename' && node.type === 'module' && node.directoryId !== null) {
      await platformApi.renameCaseDirectory(activeListWorkspaceCode.value, node.directoryId, {
        name: moduleForm.label.trim(),
      })
      ElMessage.success('目录已重命名')
    } else {
      await platformApi.createCaseDirectory(activeListWorkspaceCode.value, {
        workspaceCode: isAllScope.value ? node.workspaceCode : undefined,
        parentId: node.type === 'workspace' ? null : node.directoryId,
        name: moduleForm.label.trim(),
      })
      ElMessage.success('子模块已创建')
    }
    moduleDialogVisible.value = false
    await loadCases()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    moduleSaving.value = false
  }
}
function openMoveModule(node: TreeNode) {
  if (node.type !== 'module' || !canWriteWorkspace(node.workspaceCode)) {
    return
  }
  selectedNodeId.value = node.id
  moveForm.targetParentId = null
  moveDialogVisible.value = true
}
async function submitMoveModule() {
  const node = selectedDirectoryNode.value
  if (!node || node.directoryId === null) {
    return
  }
  moduleSaving.value = true
  try {
    await platformApi.moveCaseDirectory(activeListWorkspaceCode.value, node.directoryId, {
      targetParentId: moveForm.targetParentId,
    })
    moveDialogVisible.value = false
    ElMessage.success('子模块已移动')
    await loadCases()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    moduleSaving.value = false
  }
}
async function confirmDeleteModule(node: TreeNode) {
  if (node.type !== 'module' || node.directoryId === null) {
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除模块“${node.label}”吗？`, '删除模块', { type: 'warning' })
    await platformApi.deleteCaseDirectory(activeListWorkspaceCode.value, node.directoryId)
    if (selectedNodeId.value === node.id) {
      selectedNodeId.value = `workspace:${node.workspaceCode}`
    }
    ElMessage.success('子模块已删除')
    await loadCases()
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}
function handleModuleAction(command: string, node: TreeNode) {
  if (command === 'rename') {
    openRenameModule(node)
    return
  }
  if (command === 'move') {
    openMoveModule(node)
    return
  }
  void confirmDeleteModule(node)
}
function handleCaseCommand(command: string, row: CaseItem) {
  if (command === 'review') {
    void openCaseReview(row.id)
    return
  }
  if (command === 'bug') {
    openBugDialog(row)
    return
  }
  if (command === 'copy') {
    void openCaseCopy(row.id)
    return
  }
  void confirmDeleteCase(row)
}
function clearSelection() {
  selectedCaseIds.value = []
}
function toggleSelectAll(checked: boolean | string | number) {
  selectedCaseIds.value = checked ? filteredCases.value.map(item => item.id) : []
}
function toggleCaseSelection(caseId: number, checked: boolean | string | number) {
  if (checked) {
    if (!selectedCaseIds.value.includes(caseId)) {
      selectedCaseIds.value = [...selectedCaseIds.value, caseId]
    }
    return
  }
  selectedCaseIds.value = selectedCaseIds.value.filter(item => item !== caseId)
}
function openBatchMove() {
  if (!hasSelection.value) {
    return
  }
  if (!batchMoveWorkspaceCode.value) {
    ElMessage.warning('批量移动暂不支持跨空间混选')
    return
  }
  batchMoveForm.caseIds = [...selectedCaseIds.value]
  batchMoveForm.targetDirectoryId = null
  batchMoveVisible.value = true
}
function openBatchEdit() {
  if (!hasSelection.value) {
    return
  }
  resetBatchEditForm()
  batchEditForm.caseIds = [...selectedCaseIds.value]
  batchEditVisible.value = true
}
async function submitBatchMove() {
  if (!batchMoveForm.caseIds.length) {
    return
  }
  saving.value = true
  try {
    await platformApi.batchMoveCases(activeListWorkspaceCode.value, batchMoveForm)
    batchMoveVisible.value = false
    ElMessage.success('批量移动已完成')
    await loadCases()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}
async function submitBatchEdit() {
  if (!batchEditForm.caseIds.length) {
    return
  }
  if (!batchEditForm.priority && !batchEditForm.reviewStatus && !batchEditForm.executionStatus) {
    ElMessage.error('请至少选择一个要修改的字段')
    return
  }
  saving.value = true
  try {
    await platformApi.batchUpdateCases(activeListWorkspaceCode.value, {
      caseIds: batchEditForm.caseIds,
      priority: batchEditForm.priority || undefined,
      reviewStatus: batchEditForm.reviewStatus || undefined,
      executionStatus: batchEditForm.executionStatus || undefined,
    })
    batchEditVisible.value = false
    ElMessage.success('批量编辑已完成')
    await loadCases()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}
async function confirmBatchDelete() {
  if (!hasSelection.value) {
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除当前页选中的 ${selectedCaseIds.value.length} 条用例吗？`, '批量删除', {
      type: 'warning',
    })
    const payload: BatchDeleteCasesPayload = { caseIds: [...selectedCaseIds.value] }
    await platformApi.batchDeleteCases(activeListWorkspaceCode.value, payload)
    ElMessage.success('批量删除已完成')
    if (filteredCases.value.length === selectedCaseIds.value.length && pageNo.value > 1) {
      pageNo.value -= 1
    }
    await loadCases()
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}
async function moveToCase(offset: -1 | 1, mode: 'edit' | 'review') {
  const nextRow = filteredCases.value[currentCaseIndex.value + offset]
  if (!nextRow) {
    return
  }
  if (mode === 'edit') {
    await openCaseEdit(nextRow.id)
    return
  }
  if (mode === 'review') {
    await openCaseReview(nextRow.id)
  }
}
function handlePageChange(value: number) {
  const targetPage = Math.min(Math.max(value, 1), Math.max(totalPages.value, 1))
  pageNo.value = targetPage
}
function handlePageSizeChange(value: number) {
  updatePageSizeSetting(value)
}
watch(filteredCases, (rows) => {
  const visibleIds = new Set(rows.map(item => item.id))
  selectedCaseIds.value = selectedCaseIds.value.filter(id => visibleIds.has(id))
}, { flush: 'sync' })
watch(
  () => [selectedNodeId.value, pageNo.value, pageSize.value],
  () => {
    if (!caseManageBootstrapped.value || hydratingCaseViewMemory.value) {
      return
    }
    persistCaseViewMemory()
    void loadCases()
  },
)
watch(workspaceCode, async () => {
  resetCaseForm()
  resetReviewForm()
  caseManageBootstrapped.value = false
  const memory = loadCaseViewMemory(workspaceCode.value)
  hydratingCaseViewMemory.value = true
  try {
    selectedNodeId.value = memory?.selectedNodeId ?? ROOT_NODE_ID
    pageNo.value = memory?.pageNo ?? 1
    expandedTreeKeys.value = memory?.expandedTreeKeys?.length ? memory.expandedTreeKeys : [ROOT_NODE_ID]
  } finally {
    hydratingCaseViewMemory.value = false
  }
  await bootstrap()
})
watch(
  () => route.query.editCaseId,
  () => {
    void maybeOpenEditCaseFromRoute()
  },
)
watch(
  () => caseForm.workspaceCode,
  newValue => {
    if (!newValue) {
      caseForm.directoryId = null
      return
    }
    const allowedIds = new Set(currentDirectoryOptions.value.map(item => item.value))
    if (!allowedIds.has(caseForm.directoryId ?? null)) {
      caseForm.directoryId = null
    }
  },
)
onMounted(bootstrap)
</script>
<template>
  <section class="page-shell">
    <div class="case-manage-layout">
      <article class="panel-card case-tree-panel">
        <el-tree
          :key="treeRenderKey"
          :data="treeData"
          node-key="id"
          :default-expanded-keys="expandedTreeKeys"
          highlight-current
          :expand-on-click-node="false"
          :current-node-key="selectedNodeId"
          class="case-tree"
          @current-change="handleTreeSelect"
          @node-expand="handleTreeNodeExpand"
          @node-collapse="handleTreeNodeCollapse"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <div class="tree-node-main">
                <span
                  v-if="data.type === 'workspace'"
                  :class="['tree-node-folder-svg', { 'is-open': isTreeNodeExpanded(data.id) }]"
                  aria-hidden="true"
                >
                  <el-icon class="tree-node-folder-icon">
                    <FolderOpened v-if="isTreeNodeExpanded(data.id)" />
                    <Folder v-else />
                  </el-icon>
                </span>
                <span :class="['tree-node-label', { 'tree-node-label-root': data.type === 'root' }]">{{ data.label }}</span>
              </div>
              <div class="tree-node-actions" @click.stop>
                <el-button
                  v-if="data.type === 'root'"
                  text
                  class="tree-icon-button"
                  title="收起全部子模块"
                  @click.stop="collapseAllTreeChildren"
                >
                  <el-icon class="tree-collapse-icon"><Fold /></el-icon>
                </el-button>
                <el-button
                  v-if="data.type !== 'root' && canWriteWorkspace(data.workspaceCode)"
                  text
                  class="tree-icon-button"
                  @click.stop="openCreateModuleForNode(data)"
                >
                  <el-icon><Plus /></el-icon>
                </el-button>
                <el-dropdown
                  v-if="data.type === 'module' && canWriteWorkspace(data.workspaceCode)"
                  trigger="click"
                  popper-class="case-tree-action-menu"
                  @command="(command: string | number | object) => handleModuleAction(String(command), data)"
                >
                  <el-button
                    text
                    class="tree-icon-button tree-more-button"
                    title="更多操作"
                    aria-label="更多操作"
                    @click.stop
                  >
                    <el-icon><MoreFilled /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="rename" class="case-tree-action-item">
                        重命名
                      </el-dropdown-item>
                      <el-dropdown-item command="delete" class="case-tree-action-item case-tree-action-danger">
                        删除
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>
          </template>
        </el-tree>
      </article>

      <article class="panel-card case-table-panel">
        <div class="panel-header panel-header-case-list">
          <ListToolbar title="用例列表">
            <template #filters>
              <el-input
                v-model="caseFilters.keyword"
                placeholder="搜索编号或名称"
                clearable
                class="toolbar-filter-input"
              />
              <el-select v-model="caseFilters.priority" clearable placeholder="优先级" class="toolbar-filter-select">
                <el-option label="P0" value="P0" />
                <el-option label="P1" value="P1" />
                <el-option label="P2" value="P2" />
                <el-option label="P3" value="P3" />
              </el-select>
              <el-select v-model="caseFilters.reviewStatus" clearable placeholder="评审状态" class="toolbar-filter-select">
                <el-option label="未评审" value="PENDING" />
                <el-option label="已通过" value="PASSED" />
                <el-option label="不通过" value="REJECTED" />
              </el-select>
              <el-select v-model="caseFilters.executionStatus" clearable placeholder="执行状态" class="toolbar-filter-select">
                <el-option label="未执行" value="NOT_RUN" />
                <el-option label="已通过" value="PASSED" />
                <el-option label="阻塞中" value="BLOCKED" />
                <el-option label="失败" value="FAILED" />
              </el-select>
              <el-select v-model="caseFilters.executorName" clearable placeholder="执行人" class="toolbar-filter-select">
                <el-option v-for="name in caseUserOptions" :key="'executor-' + name" :label="name" :value="name" />
              </el-select>
              <el-select v-model="caseFilters.createdByName" clearable placeholder="创建人" class="toolbar-filter-select">
                <el-option v-for="name in caseUserOptions" :key="'creator-' + name" :label="name" :value="name" />
              </el-select>
              <el-select
                v-if="isAllScope"
                v-model="caseFilters.workspaceCode"
                clearable
                placeholder="所属空间"
                class="toolbar-filter-select"
              >
                <el-option v-for="item in writableWorkspaces" :key="item.code" :label="item.name" :value="item.code" />
              </el-select>
              <el-button text @click="resetCaseFilters">
                <el-icon><RefreshRight /></el-icon>
                重置
              </el-button>
            </template>
          </ListToolbar>
          <el-button v-if="canCreateCaseInSelection" type="primary" class="case-create-button" @click="openCaseCreate">
            <el-icon><Plus /></el-icon>
            新建用例
          </el-button>
        </div>

        <div v-loading="loading" class="case-table-shell">
          <div class="case-table-data">
            <div class="case-table-scroll">
              <div class="case-grid case-grid-header" :style="{ gridTemplateColumns: caseGridTemplateColumns, minWidth: caseGridMinWidth }">
                <div class="case-cell case-cell-selection">
                  <el-checkbox
                    :model-value="allCurrentPageSelected"
                    :indeterminate="selectionIndeterminate"
                    @change="toggleSelectAll"
                  />
                </div>
                <div
                  v-for="column in visibleColumns"
                  :key="'header-' + column.key"
                  :class="['case-cell', 'case-cell-' + column.key]"
                  :data-column-key="column.key"
                >
                  {{ column.label }}
                </div>
              </div>

              <div
                v-for="row in filteredCases"
                :key="row.id"
                class="case-grid case-grid-row"
                :style="{ gridTemplateColumns: caseGridTemplateColumns, minWidth: caseGridMinWidth }"
                :data-case-id="row.id"
                :data-case-title="row.title"
              >
                <div class="case-cell case-cell-selection">
                  <el-checkbox
                    :model-value="selectedCaseIds.includes(row.id)"
                    @change="(value: string | number | boolean) => toggleCaseSelection(row.id, value)"
                  />
                </div>
                <div
                  v-for="column in visibleColumns"
                  :key="row.id + '-' + column.key"
                  :class="['case-cell', 'case-cell-' + column.key]"
                >
                  <template v-if="column.key === 'reviewStatus'">
                    <el-tag :type="reviewStatusTagType(row.reviewStatus)" effect="plain" :class="reviewStatusTagClass(row.reviewStatus)">
                      {{ reviewStatusLabel(row.reviewStatus) }}
                    </el-tag>
                  </template>
                  <template v-else-if="column.key === 'executionStatus'">
                    <el-tag :type="executionStatusTagType(row.executionStatus)" effect="plain" :class="executionStatusTagClass(row.executionStatus)">
                      {{ executionStatusLabel(row.executionStatus) }}
                    </el-tag>
                  </template>
                  <template v-else-if="shouldUseCellTooltip(column.key)">
                    <el-tooltip :content="getCaseColumnValue(row, column.key)" placement="top" :show-after="180" :disabled="!shouldShowCaseCellTooltip(row.id, column.key)">
                      <span class="case-cell-text" @mouseenter="updateCaseCellOverflow(row.id, column.key, $event)">{{ getCaseColumnValue(row, column.key) }}</span>
                    </el-tooltip>
                  </template>
                  <template v-else>
                    {{ getCaseColumnValue(row, column.key) }}
                  </template>
                </div>
              </div>
            </div>
          </div>

          <div class="case-table-actions">
            <div class="case-actions-header">
              <div class="case-actions-header-title">
                <span>操作</span>
                <el-button text class="table-settings-trigger" @click="caseListToolbar.settingsVisible.value = true">
                  <el-icon><Setting /></el-icon>
                </el-button>
              </div>
            </div>
            <div
              v-for="row in filteredCases"
              :key="'action-' + row.id"
              class="case-actions-row"
              :data-case-id="row.id"
              :data-case-title="row.title"
            >
              <div v-if="canWriteRow(row)" class="row-actions">
                <el-button text type="primary" size="small" @click="openCaseEdit(row.id)">编辑</el-button>
                <el-button text type="primary" size="small" @click="executeCase(row)">执行</el-button>
                <el-dropdown trigger="click" @command="(command: string | number | object) => handleCaseCommand(String(command), row)">
                  <el-button text type="primary" size="small">
                    <el-icon><MoreFilled /></el-icon>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="review">评审</el-dropdown-item>
                      <el-dropdown-item command="bug">提缺陷</el-dropdown-item>
                      <el-dropdown-item command="copy">复制</el-dropdown-item>
                      <el-dropdown-item command="delete">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
              <span v-else class="panel-subtitle">只读</span>
            </div>
          </div>
        </div>

        <div class="table-pagination">
          <div class="table-pagination-left">
            <div v-if="hasSelection" class="table-selection-actions">
              <div class="batch-toolbar-meta">已选 {{ selectedCaseIds.length }} 条</div>
              <div class="toolbar-group">
                <el-button size="small" @click="openBatchMove">移动到</el-button>
                <el-button size="small" @click="openBatchEdit">批量编辑</el-button>
                <el-button size="small" type="danger" plain @click="confirmBatchDelete">批量删除</el-button>
                <el-button size="small" @click="clearSelection">取消</el-button>
              </div>
            </div>
          </div>
          <div class="table-pagination-right">
            <div class="table-pagination-summary">共 {{ total }} 条 / {{ totalPages || 1 }} 页</div>
            <el-pagination
              v-model:current-page="pageNo"
              v-model:page-size="pageSize"
              :page-sizes="PAGE_SIZE_OPTIONS"
              :pager-count="7"
              size="small"
              layout="sizes, prev, pager, next, jumper"
              :total="total"
              @current-change="handlePageChange"
              @size-change="handlePageSizeChange"
            />
          </div>
        </div>
      </article>
    </div>

    <TableSettingsDrawer
      v-model="caseListToolbar.settingsVisible.value"
      :columns="caseListToolbar.drawerColumns.value"
      :page-size-enabled="true"
      :page-size="caseListToolbar.pageSizeDisplay.value"
      :page-size-options="PAGE_SIZE_OPTIONS"
      :dragging-key="caseListToolbar.draggingColumnKey.value"
      @page-size-change="updatePageSizeSetting"
      @toggle-column="caseListToolbar.toggleColumnVisibility"
      @drag-start="caseListToolbar.handleDragStart"
      @drag-end="caseListToolbar.handleDragEnd"
      @drop-column="caseListToolbar.moveColumnToTarget"
      @reset="caseListToolbar.reset"
    />

    <el-dialog v-model="moduleDialogVisible" :title="moduleDialogMode === 'create' ? '新建子模块' : '重命名子模块'" width="420px">
      <el-form label-width="90px" @submit.prevent="submitModule">
        <el-form-item label="所属位置">
          <span>{{ selectedNodePath }}</span>
        </el-form-item>
        <el-form-item label="模块名称" required>
          <el-input v-model="moduleForm.label" maxlength="30" show-word-limit @keydown.enter.prevent="submitModule" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="moduleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="moduleSaving" :disabled="!canSubmitModule" @click="submitModule">
          {{ moduleDialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="moveDialogVisible" title="移动子模块" width="420px">
      <el-form label-width="90px">
        <el-form-item label="当前模块">
          <span>{{ selectedDirectoryNode?.label || '-' }}</span>
        </el-form-item>
        <el-form-item label="移动到">
          <el-select v-model="moveForm.targetParentId" placeholder="请选择目标位置" clearable>
            <el-option v-for="item in moveTargetOptions" :key="String(item.value)" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="moveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="moduleSaving" @click="submitMoveModule">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchMoveVisible" title="批量移动用例" width="420px">
      <el-form label-width="90px">
        <el-form-item label="目标目录">
          <el-select v-model="batchMoveForm.targetDirectoryId" placeholder="请选择目标目录" clearable>
            <el-option v-for="item in batchMoveDirectoryOptions" :key="String(item.value)" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchMoveVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitBatchMove">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchEditVisible" title="批量编辑用例" width="420px">
      <el-form label-width="90px">
        <el-form-item label="优先级">
          <el-select v-model="batchEditForm.priority" clearable placeholder="不修改">
            <el-option label="P0" value="P0" />
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
            <el-option label="P3" value="P3" />
          </el-select>
        </el-form-item>
        <el-form-item label="评审状态">
          <el-select v-model="batchEditForm.reviewStatus" clearable placeholder="不修改">
            <el-option label="未评审" value="PENDING" />
            <el-option label="已通过" value="PASSED" />
            <el-option label="不通过" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行状态">
          <el-select v-model="batchEditForm.executionStatus" clearable placeholder="不修改">
            <el-option label="未执行" value="NOT_RUN" />
            <el-option label="已通过" value="PASSED" />
            <el-option label="阻塞中" value="BLOCKED" />
            <el-option label="失败" value="FAILED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitBatchEdit">保存</el-button>
      </template>
    </el-dialog>

    <CaseEditorDrawer
      v-model="caseEditorVisible"
      :title="caseEditorTitle()"
      :form="caseForm"
      :saving="saving"
      :can-submit="canSubmitCase"
      :submit-text="caseEditorMode === 'copy' ? '创建复制用例' : '保存'"
      :workspace-name="getWorkspaceNameByCode(caseForm.workspaceCode)"
      :directory-tree="getWorkspaceDirectories(caseForm.workspaceCode)"
      :resolve-directory-path="directoryId => formatCaseModulePath(caseForm.workspaceCode, directoryId)"
      :show-navigator="caseEditorMode === 'edit'"
      :can-go-prev="canGoPrev"
      :can-go-next="canGoNext"
      :current-index="currentCaseDisplayIndex"
      :total-count="filteredCases.length"
      @prev="moveToCase(-1, 'edit')"
      @next="moveToCase(1, 'edit')"
      @submit="submitCase"
    />

    <el-drawer v-model="reviewDrawerVisible" title="用例评审" size="720px" class="case-detail-drawer">
      <el-form label-position="top" class="review-form case-detail-form">
        <el-form-item label="用例标题" class="review-title-form-item case-detail-form-item case-detail-form-item-full">
          <div class="review-title-card">
            <div class="detail-title">{{ reviewForm.title }}</div>
            <el-tag size="small" effect="plain" class="review-priority-tag">{{ reviewForm.priority }}</el-tag>
          </div>
        </el-form-item>
        <el-form-item label="前置条件" class="case-detail-form-item">
          <el-input
            :model-value="reviewForm.precondition"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            resize="vertical"
            readonly
            class="case-detail-readonly-field"
          />
        </el-form-item>
        <el-form-item label="测试步骤" class="case-detail-form-item">
          <el-input
            :model-value="reviewForm.steps"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 10 }"
            resize="vertical"
            readonly
            class="case-detail-readonly-field"
          />
        </el-form-item>
        <el-form-item label="预期结果" class="case-detail-form-item case-detail-form-item-full">
          <el-input
            :model-value="reviewForm.expectedResult"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            resize="vertical"
            readonly
            class="case-detail-readonly-field"
          />
        </el-form-item>
        <el-form-item label="评审意见" class="case-detail-form-item case-detail-form-item-full">
          <el-input v-model="reviewForm.reviewComment" type="textarea" :rows="4" resize="vertical" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="drawer-footer">
          <div class="drawer-nav">
            <el-button :disabled="!canGoPrev" @click="moveToCase(-1, 'review')">
              <el-icon><ArrowLeft /></el-icon>
              上一条
            </el-button>
            <div class="drawer-nav-counter">
              {{ currentCaseDisplayIndex }}/{{ filteredCases.length }}
            </div>
            <el-button :disabled="!canGoNext" @click="moveToCase(1, 'review')">
              下一条
              <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
          <div class="drawer-submit">
            <el-button @click="reviewDrawerVisible = false">关闭</el-button>
            <el-button type="danger" plain :loading="saving" @click="submitReview('REJECTED')">驳回</el-button>
            <el-button type="primary" :loading="saving" @click="submitReview('PASSED')">通过</el-button>
          </div>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="bugVisible" title="从用例创建缺陷" width="640px">
      <el-form label-width="90px">
        <el-form-item v-if="isAllScope" label="目标空间" required>
          <el-select v-model="bugState.workspaceCode">
            <el-option v-for="item in writableWorkspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="bugState.title" />
        </el-form-item>
        <el-form-item label="描述" required>
          <el-input v-model="bugState.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="处理人">
          <el-select v-model="bugState.assigneeId" clearable>
            <el-option v-for="item in activeOwnerOptions" :key="item.id" :label="item.displayName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="bugState.priority">
            <el-option label="P0" value="P0" />
            <el-option label="P1" value="P1" />
            <el-option label="P2" value="P2" />
            <el-option label="P3" value="P3" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重程度">
          <el-select v-model="bugState.severity">
            <el-option label="致命" value="CRITICAL" />
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bugVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!canSubmitBug" @click="submitCaseBug">提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>
<style scoped>
.case-manage-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  min-height: 0;
}
.case-tree-panel,
.case-table-panel {
  min-height: 700px;
  display: flex;
  flex-direction: column;
}
.panel-header-case-list {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.panel-header-case-list :deep(.list-toolbar) {
  min-width: 0;
  flex: 1;
}
.panel-header-case-list :deep(.list-toolbar-subline) {
  justify-content: flex-start;
}
.panel-header-case-list :deep(.list-toolbar-filters) {
  flex-wrap: nowrap;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
}
.panel-header-case-list :deep(.list-toolbar-filters::-webkit-scrollbar) {
  height: 6px;
}
.case-create-button {
  flex: 0 0 auto;
  align-self: flex-start;
}
.case-editor-form :deep(.el-form-item) {
  margin-bottom: 16px;
}
.case-editor-meta-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.case-editor-meta-row :deep(.el-form-item) {
  margin-bottom: 0;
}
.case-tree {
  flex: 1;
  overflow: auto;
  padding-top: 6px;
  padding-right: 6px;
}
.case-tree > :deep(.el-tree-node > .el-tree-node__content .el-tree-node__expand-icon) {
  visibility: hidden;
  pointer-events: none;
}
.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-width: 0;
}
.tree-node-main {
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
.tree-node-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tree-node-label-root {
  font-weight: 700;
  color: #101828;
}
.tree-node-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s ease;
}
.tree-node:hover .tree-node-actions,
.tree-node:focus-within .tree-node-actions {
  opacity: 1;
}
.tree-icon-button {
  width: 24px;
  height: 24px;
  padding: 0;
}
.tree-more-button {
  border-radius: 4px;
  color: #667085;
}
.tree-more-button:hover,
.tree-more-button:focus-visible {
  background: #f4effc;
  color: #7c3aed;
}
.tree-collapse-icon {
  color: var(--text-subtle);
}
:global(.case-tree-action-menu) {
  min-width: 86px;
}
:global(.case-tree-action-menu .el-dropdown-menu) {
  padding: 6px;
}
:global(.case-tree-action-menu .case-tree-action-item) {
  min-width: 72px;
  justify-content: center;
  padding: 8px 14px;
  line-height: 1.2;
}
:global(.case-tree-action-menu .case-tree-action-danger) {
  color: #e5484d;
}
:global(.case-tree-action-menu .case-tree-action-danger:hover) {
  background: #fff1f1;
  color: #d92d20;
}
.batch-toolbar-meta {
  font-size: 13px;
  color: var(--text-subtle);
}
.toolbar-filter-input {
  width: 200px;
}
.toolbar-filter-select {
  width: 118px;
}
.case-table-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 164px;
  flex: 1;
  min-height: 0;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  overflow: hidden;
  background: var(--bg-panel);
}
.case-table-data {
  min-width: 0;
  overflow: hidden;
}
.case-table-scroll {
  height: 100%;
  overflow-x: auto;
  overflow-y: hidden;
}
.case-grid {
  display: grid;
}
.case-grid-header {
  min-height: 50px;
  border-bottom: 1px solid var(--line-soft);
  color: var(--text-subtle);
  font-size: 12px;
  font-weight: 600;
  background: #fbfcff;
}
.case-grid-row {
  min-height: 52px;
  border-bottom: 1px solid var(--line-soft);
  font-size: 13px;
}
.case-cell {
  display: flex;
  align-items: center;
  padding: 0 10px;
  min-width: 0;
}
.case-cell-selection {
  justify-content: center;
  padding: 0;
}
.case-cell-caseNo,
.case-cell-title,
.case-cell-directoryName,
.case-cell-workspaceName,
.case-cell-createdAt,
.case-cell-updatedAt {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.case-cell-text {
  display: block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.case-table-actions {
  display: flex;
  flex-direction: column;
  border-left: 1px solid var(--line-soft);
  background: var(--bg-panel);
}
.case-actions-header {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 50px;
  padding: 0 8px;
  border-bottom: 1px solid var(--line-soft);
  color: var(--text-subtle);
  font-size: 13px;
  font-weight: 600;
  background: #fbfcff;
}
.case-actions-header-title {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.table-settings-trigger {
  width: 28px;
  height: 28px;
  padding: 0;
}
.case-actions-row {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 52px;
  padding: 0 8px;
  border-bottom: 1px solid var(--line-soft);
  font-size: 13px;
}
.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  white-space: nowrap;
}
.row-actions :deep(.el-button) {
  font-size: 14px;
}
.table-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-top: 16px;
  flex-wrap: nowrap;
}
.table-pagination-left {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
  min-height: 32px;
}
.table-pagination-right {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  white-space: nowrap;
}
.table-selection-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
  min-width: 0;
  min-height: 32px;
}
.table-selection-actions :deep(.toolbar-group) {
  display: inline-flex;
  flex-wrap: nowrap;
  gap: 8px;
  align-items: center;
}
.table-selection-actions :deep(.el-button) {
  height: 28px;
  padding-inline: 10px;
}
.table-pagination-summary {
  color: var(--text-subtle);
  font-size: 13px;
  white-space: nowrap;
  line-height: 32px;
}
.case-table-scroll::-webkit-scrollbar {
  height: 10px;
}
.case-table-scroll::-webkit-scrollbar-track {
  background: transparent;
}
.case-table-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.5);
}
.case-table-scroll::-webkit-scrollbar-thumb:hover {
  background: rgba(120, 134, 156, 0.72);
}
:deep(.table-pagination .el-pagination) {
  flex-wrap: nowrap;
  white-space: nowrap;
}
:deep(.table-pagination .el-pagination__sizes),
:deep(.table-pagination .el-pagination__jump) {
  margin-left: 8px;
}
.settings-section + .settings-section {
  margin-top: 24px;
}
.settings-title {
  font-size: 15px;
  font-weight: 700;
}
.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.settings-note {
  margin-top: 6px;
  color: var(--text-subtle);
  font-size: 12px;
}
.page-size-options {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}
.page-size-option-active {
  font-weight: 600;
}
.settings-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}
.settings-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  padding: 8px 10px;
  min-height: 44px;
}
.settings-item-dragging {
  opacity: 0.65;
  border-color: rgba(36, 107, 255, 0.45);
  background: rgba(36, 107, 255, 0.04);
}
.settings-item-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.settings-item-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 13px;
}
.settings-drag-handle {
  cursor: grab;
  color: var(--text-subtle);
}
.settings-required {
  color: var(--text-subtle);
  font-size: 12px;
}
.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.case-detail-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 18px 20px 0;
  align-items: flex-start;
}

.case-detail-drawer :deep(.el-drawer__body) {
  padding: 12px 20px 0;
}

.case-detail-drawer :deep(.el-drawer__footer) {
  padding: 16px 20px 20px;
  border-top: 1px solid var(--line-soft);
}

.case-detail-drawer :deep(.el-input__wrapper),
.case-detail-drawer :deep(.el-textarea__inner),
.case-detail-drawer :deep(.el-select__wrapper) {
  border-radius: 10px;
}
.case-detail-form {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}
.case-detail-form-item {
  margin-bottom: 0;
}
.directory-path-input {
  width: 100%;
}
.directory-path-input :deep(.el-input__wrapper) {
  cursor: default;
}
.directory-path-input-with-action :deep(.el-input__suffix) {
  margin-left: 8px;
}
.path-action-icon-button {
  width: 24px;
  height: 24px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #98a2b3;
  cursor: pointer;
}
.path-action-icon-button:focus-visible {
  outline: 2px solid rgba(23, 92, 211, 0.24);
  outline-offset: 1px;
}
.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}
.path-picker-layout {
  display: grid;
  gap: 16px;
}
.path-picker-current {
  display: grid;
  gap: 6px;
}
.path-picker-current-label,
.path-picker-selected-label {
  font-size: 12px;
  color: #667085;
  line-height: 1.5;
}
.path-picker-current-value,
.path-picker-tree-node-label,
.path-picker-selected-value {
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
.path-picker-selected-panel {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.case-detail-drawer :deep(.el-textarea__inner) {
  line-height: 1.7;
}

.case-detail-form {
  margin-top: 16px;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
}

.case-detail-form-item {
  margin-bottom: 0;
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: #ffffff;
}

.case-detail-form-item-full {
  grid-column: 1 / -1;
}

.case-detail-form :deep(.el-form-item__label) {
  min-height: auto;
  margin-bottom: 10px;
  padding: 0;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--text-subtle);
}

.case-detail-form :deep(.el-form-item__content) {
  display: block;
  min-width: 0;
}

.case-detail-form :deep(.el-segmented) {
  width: fit-content;
}

.case-detail-readonly-field :deep(.el-input__wrapper),
.case-detail-readonly-field :deep(.el-textarea__inner) {
  background: #f8fafc;
  border-color: rgba(15, 23, 42, 0.06);
  color: #344054;
  box-shadow: none;
}

.case-detail-readonly-field :deep(.el-input__wrapper) {
  min-height: 44px;
}

.case-detail-readonly-field :deep(.el-textarea__inner) {
  min-height: 132px !important;
  padding: 14px 16px;
}

.review-form .case-detail-readonly-field :deep(.el-textarea__inner) {
  min-height: 0 !important;
  padding: 12px 14px;
}

.case-detail-readonly-field :deep(.el-input__inner),
.case-detail-readonly-field :deep(.el-textarea__inner) {
  color: #344054;
}

.drawer-nav,
.drawer-submit {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.drawer-nav-counter {
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
.review-title-form-item :deep(.el-form-item__content) {
  display: block;
}
.review-title-card {
  position: relative;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: #ffffff;
  padding: 20px 72px 20px 24px;
}
.detail-title {
  font-size: 13px;
  line-height: 1.7;
  font-weight: 700;
  color: #344054;
}
.review-priority-tag {
  position: absolute;
  top: 18px;
  right: 20px;
}
.review-ai-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0 4px;
  grid-column: 1 / -1;
}
.review-ai-result {
  margin-bottom: 4px;
  grid-column: 1 / -1;
}
.review-ai-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.review-ai-section {
  margin-top: 14px;
}
.review-ai-section-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
}
.review-ai-list {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 6px;
  color: var(--text-subtle);
  font-size: 13px;
}
.review-ai-raw {
  margin-top: 14px;
}
.status-tag-passed {
  --el-tag-text-color: #0f7a43;
  --el-tag-bg-color: rgba(15, 122, 67, 0.1);
  --el-tag-border-color: rgba(15, 122, 67, 0.22);
}
.status-tag-failed {
  --el-tag-text-color: #c53532;
  --el-tag-bg-color: rgba(197, 53, 50, 0.1);
  --el-tag-border-color: rgba(197, 53, 50, 0.22);
}
.status-tag-blocked {
  --el-tag-text-color: #6d42c7;
  --el-tag-bg-color: rgba(109, 66, 199, 0.1);
  --el-tag-border-color: rgba(109, 66, 199, 0.22);
}
.status-tag-pending {
  --el-tag-text-color: #667085;
  --el-tag-bg-color: rgba(102, 112, 133, 0.1);
  --el-tag-border-color: rgba(102, 112, 133, 0.22);
}
@media (max-width: 1200px) {
  .case-manage-layout {
    grid-template-columns: 1fr;
  }
  .case-tree-panel,
  .case-table-panel {
    min-height: auto;
  }
  .case-table-shell {
    grid-template-columns: 1fr;
  }
  .case-table-actions {
    border-left: 0;
    border-top: 1px solid var(--line-soft);
  }
  .table-pagination,
  .table-pagination-right,
  .drawer-footer {
    flex-direction: column;
    align-items: stretch;
  }
  .case-editor-meta-row {
    grid-template-columns: 1fr;
  }
  .table-selection-actions,
  .drawer-nav,
  .drawer-submit {
    justify-content: space-between;
  }
}
</style>
