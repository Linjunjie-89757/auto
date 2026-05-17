<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, onUnmounted, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight, Edit, Filter, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { platformApi, resolveApiUrl } from '../api/platform'
import BugDetailDrawer from '../components/BugDetailDrawer.vue'
import BugEditorDrawer from '../components/BugEditorDrawer.vue'
import BugLinkDrawer from '../components/BugLinkDrawer.vue'
import CaseEditorDrawer from '../components/CaseEditorDrawer.vue'
import { useCaseCenterShared } from '../composables/useCaseCenterShared'
import { loadCaseExecutionContext, type CaseExecutionContext } from '../utils/caseExecutionContext'
import {
  executionStatusLabel,
  executionStatusTagClass,
  executionStatusTagType,
  formatDateTime,
  reviewStatusLabel,
  reviewStatusTagClass,
  reviewStatusTagType,
  type ExecutionStatus,
} from '../utils/casePresentation'
import type { BugAttachment, BugDetail, BugSummary, CaseDetail, CaseDirectoryNode, CaseItem, CreateBugPayload, CreateCasePayload, UpdateBugPayload } from '../types/api'

type PendingInlineImage = {
  src: string
  file: File
}

const route = useRoute()
const router = useRouter()
const { workspaceCode, canWriteWorkspace, loadSharedBase, workspaces, users } = useCaseCenterShared()

const loading = ref(false)
const caseSaving = ref(false)
const submitting = ref(false)
const activeTab = ref('detail')
const sidebarKeyword = ref('')
const sidebarExecutionStatus = ref<ExecutionStatus | ''>('')
const autoNext = ref(true)
const contextState = ref<CaseExecutionContext | null>(null)
const executionCases = ref<CaseItem[]>([])
const detail = ref<CaseDetail | null>(null)
const selectedExecutionStatus = ref<ExecutionStatus | ''>('')
const executionComment = ref('')
const executionNoteDraft = ref('')
const relatedBugs = ref<BugSummary[]>([])
const bugListLoading = ref(false)
const bugLinkDrawerVisible = ref(false)
const bugLinkLoading = ref(false)
const bugLinkKeyword = ref('')
const bugLinkAssociating = ref(false)
const bugCreateVisible = ref(false)
const bugDetailVisible = ref(false)
const bugDetailLoading = ref(false)
const bugDetailSummary = ref<Pick<BugSummary, 'bugNo' | 'title' | 'workspaceName' | 'status' | 'assigneeName'> | null>(null)
const bugSaving = ref(false)
const bugTransitioning = ref(false)
const bugCommenting = ref(false)
const bugBasicSaving = ref(false)
const bugDescriptionSaving = ref(false)
const bugCaseAssociating = ref(false)
const bugAttachmentUploading = ref(false)
const bugAttachmentRemovingId = ref<number | null>(null)
const pendingBugInlineImages = ref<PendingInlineImage[]>([])
const pendingBugFiles = ref<Array<{
  id: string
  file: File
  kind: 'attachment' | 'screenshot'
  previewUrl: string | null
}>>([])
const bugEditorMode = ref<'create' | 'edit'>('create')
const activeBugDetail = ref<BugDetail | null>(null)
const relatedBugDetails = ref<Record<number, BugDetail>>({})
const executionAttachmentUploading = ref(false)
const executionAttachmentRemovingId = ref<number | null>(null)
const executionAttachmentInput = ref<HTMLInputElement | null>(null)
const executionAttachmentDropActive = ref(false)
const executionAttachmentImageUrls = ref<Record<number, string>>({})
const activeExecutionImageAttachmentId = ref<number | null>(null)
const executionImagePreviewVisible = ref(false)
const executionImagePreviewTitle = ref('')
const executionImagePreviewUrl = ref('')
const executionImagePreviewScale = ref(1)
const executionImagePreviewOffset = ref({ x: 0, y: 0 })
const executionImagePreviewDragging = ref(false)
const executionImagePreviewDragOrigin = ref({ x: 0, y: 0, offsetX: 0, offsetY: 0 })
const caseEditorVisible = ref(false)
const EXECUTION_ATTACHMENT_MAX_COUNT = 15
const EXECUTION_ATTACHMENT_MAX_SIZE = 10 * 1024 * 1024
const caseModulePickerLoading = ref(false)
const caseModuleDirectoryTree = ref<CaseDirectoryNode[]>([])
const caseEditorForm = ref({
  id: null as number | null,
  workspaceCode: '',
  directoryId: null as number | null,
  title: '',
  priority: 'P1',
  sourceType: '',
  caseStatus: '',
  precondition: '',
  steps: '',
  expectedResult: '',
})
const bugForm = ref<CreateBugPayload & { workspaceCode: string }>({
  workspaceCode: '',
  title: '',
  description: '',
  priority: 'P1',
  severity: 'HIGH',
  assigneeId: null,
  relatedCaseId: null,
  tags: [],
})

const sidebarStatusOptions: Array<{ label: string, value: ExecutionStatus | '' }> = [
  { label: '全部状态', value: '' },
  { label: '未执行', value: 'NOT_RUN' },
  { label: '已通过', value: 'PASSED' },
  { label: '阻塞中', value: 'BLOCKED' },
  { label: '失败', value: 'FAILED' },
]
const currentCaseId = computed(() => {
  const rawId = route.params.id?.toString()
  const parsed = Number(rawId)
  return Number.isFinite(parsed) ? parsed : null
})
const directWorkspaceCode = computed(() => route.query.workspace?.toString() ?? workspaceCode.value)
const effectiveWorkspaceCode = computed(() => contextState.value?.workspaceCode || directWorkspaceCode.value)
const sidebarRequirementName = computed(() => {
  const path = contextState.value?.selectedNodePath?.trim()
  if (!path) {
    return currentWorkspaceName.value
  }
  const segments = path.split('/').map(item => item.trim()).filter(Boolean)
  return segments[segments.length - 1] || currentWorkspaceName.value
})
const visibleExecutionCases = computed(() => {
  const keyword = sidebarKeyword.value.trim().toLowerCase()
  return executionCases.value.filter((item) => {
    const matchesKeyword = !keyword
      || item.caseNo.toLowerCase().includes(keyword)
      || item.title.toLowerCase().includes(keyword)
    const matchesStatus = !sidebarExecutionStatus.value || item.executionStatus === sidebarExecutionStatus.value
    return matchesKeyword && matchesStatus
  })
})
const currentVisibleIndex = computed(() => visibleExecutionCases.value.findIndex(item => item.id === currentCaseId.value))
const activeCaseDisplayIndex = computed(() => (currentVisibleIndex.value >= 0 ? currentVisibleIndex.value + 1 : 0))
const canPreviewPreviousCase = computed(() => currentVisibleIndex.value > 0)
const canPreviewNextCase = computed(() => (
  currentVisibleIndex.value >= 0 && currentVisibleIndex.value < visibleExecutionCases.value.length - 1
))
const canEditCurrentCase = computed(() => !!detail.value && canWriteWorkspace(detail.value.workspaceCode))
const currentWorkspaceName = computed(() => (
  workspaces.value.find(item => item.code === effectiveWorkspaceCode.value)?.name || effectiveWorkspaceCode.value
))
const modulePath = computed(() => {
  if (!detail.value) {
    return '-'
  }
  const segments = [detail.value.workspaceName || currentWorkspaceName.value]
  if (detail.value.directoryName) {
    segments.push(detail.value.directoryName)
  }
  return segments.join(' / ')
})
const executionHistoryRows = computed(() => {
  if (!detail.value) {
    return []
  }
  if (!detail.value.executedAt && !detail.value.executorName && !detail.value.executionComment) {
    return []
  }
  return [{
    status: executionStatusLabel(detail.value.executionStatus),
    executorName: detail.value.executorName || '-',
    executedAt: formatDateTime(detail.value.executedAt),
    comment: detail.value.executionComment || '-',
  }]
})
const associatedBugs = computed(() => {
  if (currentCaseId.value === null) {
    return []
  }
  return relatedBugs.value.filter(item => item.relatedCaseId === currentCaseId.value)
})
const bugStatusLabelMap: Record<string, string> = {
  TODO: '待处理',
  ASSIGNED: '已指派',
  IN_PROGRESS: '处理中',
  PENDING_VERIFY: '待验证',
  CLOSED: '已关闭',
  REJECTED: '已拒绝',
}
const bugSeverityLabelMap: Record<string, string> = {
  CRITICAL: '致命',
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
}
const canSubmitCaseEdit = computed(() => !!caseEditorForm.value.title.trim() && !!caseEditorForm.value.workspaceCode)
const caseEditorWorkspaceName = computed(() => (
  workspaces.value.find(item => item.code === caseEditorForm.value.workspaceCode)?.name || caseEditorForm.value.workspaceCode
))
const availableLinkBugs = computed(() => {
  const keyword = bugLinkKeyword.value.trim().toLowerCase()
  return relatedBugs.value
    .filter(item => item.relatedCaseId === null)
    .filter(item => isBugModulePathMatched(item))
    .filter((item) => {
      if (!keyword) {
        return true
      }
      return item.bugNo.toLowerCase().includes(keyword) || item.title.toLowerCase().includes(keyword)
    })
})
const bugAssigneeOptions = computed(() => {
  if (!detail.value?.workspaceCode) {
    return []
  }
  return users.value.filter(item => item.workspaceCodes.includes(detail.value!.workspaceCode))
})
const canSubmitBugCreate = computed(() => (
  !!bugForm.value.workspaceCode
  && !!bugForm.value.title.trim()
  && !!extractPlainTextFromHtml(bugForm.value.description)
))
const executionBugSourceContext = computed(() => ({
  caseNo: detail.value?.caseNo || '-',
  caseTitle: detail.value?.title || '-',
  modulePath: modulePath.value,
  executionStatus: executionStatusLabel(selectedExecutionStatus.value || detail.value?.executionStatus || 'NOT_RUN'),
  actualResult: executionComment.value.trim() || '-',
  precondition: detail.value?.precondition || '',
  steps: detail.value?.steps || '',
  expectedResult: detail.value?.expectedResult || '',
}))
const executionImageAttachments = computed(() => (
  detail.value?.attachments.filter(item => isImageAttachment(item.contentType, item.fileName)) ?? []
))
const activeExecutionImageIndex = computed(() => (
  executionImageAttachments.value.findIndex(item => item.id === activeExecutionImageAttachmentId.value)
))
const canPreviewPreviousExecutionImage = computed(() => activeExecutionImageIndex.value > 0)
const canPreviewNextExecutionImage = computed(() => (
  activeExecutionImageIndex.value >= 0 && activeExecutionImageIndex.value < executionImageAttachments.value.length - 1
))
const executionImagePreviewCounter = computed(() => {
  if (activeExecutionImageIndex.value < 0) {
    return ''
  }
  return `${activeExecutionImageIndex.value + 1} / ${executionImageAttachments.value.length}`
})

function buildFallbackCase(detailRow: CaseDetail): CaseItem {
  return {
    id: detailRow.id,
    caseNo: detailRow.caseNo,
    title: detailRow.title,
    caseType: detailRow.caseType,
    priority: detailRow.priority,
    sourceType: detailRow.sourceType,
    status: detailRow.status,
    executionStatus: detailRow.executionStatus,
    ownerName: detailRow.ownerName,
    executorName: detailRow.executorName,
    executionComment: detailRow.executionComment,
    executionNote: detailRow.executionNote,
    executedAt: detailRow.executedAt,
    workspaceCode: detailRow.workspaceCode,
    workspaceName: detailRow.workspaceName,
    directoryId: detailRow.directoryId,
    directoryName: detailRow.directoryName,
    createdBy: detailRow.createdBy,
    createdByName: detailRow.createdByName,
    createdAt: detailRow.createdAt,
    updatedBy: detailRow.updatedBy,
    updatedByName: detailRow.updatedByName,
    updatedAt: detailRow.updatedAt,
    reviewStatus: detailRow.reviewStatus,
    reviewComment: detailRow.reviewComment,
    reviewedBy: detailRow.reviewedBy,
    reviewedByName: detailRow.reviewedByName,
    reviewedAt: detailRow.reviewedAt,
  }
}

function normalizeDirectoryLabel(path: string | null | undefined) {
  return (path ?? '')
    .split(/[\\/]+/)
    .map(segment => segment.trim())
    .filter(Boolean)
    .join(' / ')
}

function bugStatusLabel(status: string) {
  return bugStatusLabelMap[status] ?? status
}

function bugSeverityLabel(severity: string) {
  return bugSeverityLabelMap[severity] ?? severity
}

function escapeBugDescriptionHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
    .replace(/\r?\n/g, '<br>')
}

function buildExecutionBugDescription(target: CaseDetail) {
  return [
    { label: '用例标题：', content: target.title || '-' },
    { label: '前置条件：', content: target.precondition || '-' },
    { label: '测试步骤：', content: target.steps || '-' },
    { label: '预期结果：', content: target.expectedResult || '-' },
    { label: '实际结果：', content: executionComment.value.trim() || '-' },
  ]
    .map(item => `<p><strong>${item.label}</strong><br>${escapeBugDescriptionHtml(item.content)}</p>`)
    .join('')
}

function extractPlainTextFromHtml(content: string) {
  return content
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/(div|p|li|ul|ol)>/gi, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .trim()
}

function findDirectoryNameById(nodes: CaseDirectoryNode[], directoryId: number | null, trail: string[] = []): string {
  if (directoryId == null) {
    return ''
  }
  for (const node of nodes) {
    const nextTrail = [...trail, node.name]
    if (node.id === directoryId) {
      return nextTrail.join(' / ')
    }
    const childMatched = findDirectoryNameById(node.children ?? [], directoryId, nextTrail)
    if (childMatched) {
      return childMatched
    }
  }
  return ''
}

function syncExecutionInputs(target: CaseDetail | null) {
  executionComment.value = target?.executionComment ?? ''
  executionNoteDraft.value = target?.executionNote ?? ''
  if (target?.executionStatus === 'PASSED' || target?.executionStatus === 'BLOCKED' || target?.executionStatus === 'FAILED') {
    selectedExecutionStatus.value = target.executionStatus
    return
  }
  selectedExecutionStatus.value = ''
}

function isBugModulePathMatched(_bug: BugSummary) {
  // Hook for future module-path matching once bug records expose requirement/module path.
  return true
}

function buildUpdateBugPayload(bug: BugDetail, caseId: number): UpdateBugPayload {
  return {
    workspaceCode: bug.workspaceCode,
    title: bug.title,
    description: bug.description,
    priority: bug.priority,
    severity: bug.severity,
    assigneeId: bug.assigneeId,
    relatedCaseId: caseId,
    tags: bug.tags,
  }
}

function updateExecutionCollection(detailRow: CaseDetail) {
  const rowIndex = executionCases.value.findIndex(item => item.id === detailRow.id)
  if (rowIndex >= 0) {
    const current = executionCases.value[rowIndex]
    executionCases.value = executionCases.value.map(item => item.id === detailRow.id ? {
      ...current,
      ...detailRow,
    } : item)
    return
  }
  executionCases.value = [buildFallbackCase(detailRow)]
}

async function loadCaseDetail(caseId: number) {
  loading.value = true
  try {
    const detailRow = await platformApi.getCaseDetail(effectiveWorkspaceCode.value, caseId)
    detail.value = detailRow
    updateExecutionCollection(detailRow)
    syncExecutionInputs(detailRow)
    void loadRelatedBugs(detailRow.id, detailRow.workspaceCode)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    loading.value = false
  }
}

async function loadRelatedBugs(caseId: number, targetWorkspaceCode?: string) {
  const workspace = targetWorkspaceCode || effectiveWorkspaceCode.value
  bugListLoading.value = true
  try {
    const response = await platformApi.getBugs(workspace)
    relatedBugs.value = response.items.filter(item => item.relatedCaseId === caseId || item.relatedCaseId === null)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    bugListLoading.value = false
  }
}

async function loadCaseModuleDirectories(
  workspace: string,
  preferredDirectoryId?: number | null,
  preferredDirectoryName?: string | null,
) {
  if (!workspace) {
    caseModuleDirectoryTree.value = []
    return
  }
  caseModulePickerLoading.value = true
  try {
    const workspacesResponse = await platformApi.getCaseDirectories(workspace)
    const current = workspacesResponse.find(item => item.workspaceCode === workspace)
    caseModuleDirectoryTree.value = current?.children ?? []
    void preferredDirectoryId
    void preferredDirectoryName
  }
  catch (error) {
    caseModuleDirectoryTree.value = []
    ElMessage.error((error as Error).message)
  }
  finally {
    caseModulePickerLoading.value = false
  }
}

async function bootstrap() {
  await loadSharedBase()
  const savedContext = loadCaseExecutionContext()
  contextState.value = savedContext && (
    currentCaseId.value === null
    || savedContext.items.some(item => item.id === currentCaseId.value)
    || savedContext.workspaceCode === directWorkspaceCode.value
  )
    ? savedContext
    : null
  executionCases.value = contextState.value?.items ?? []
  if (currentCaseId.value !== null) {
    await loadCaseDetail(currentCaseId.value)
  }
}

function navigateToCase(caseId: number) {
  if (caseId === currentCaseId.value) {
    return
  }
  void router.replace({
    name: 'cases-execute',
    params: { id: caseId },
    query: route.query,
  })
}

function goBackToCaseManagement() {
  const returnQuery = contextState.value?.returnQuery
  void router.push({
    name: 'cases-manage',
    query: returnQuery && Object.keys(returnQuery).length
      ? returnQuery
      : (effectiveWorkspaceCode.value ? { workspace: effectiveWorkspaceCode.value } : {}),
  })
}

function moveCase(offset: -1 | 1) {
  const nextRow = visibleExecutionCases.value[currentVisibleIndex.value + offset]
  if (!nextRow) {
    return
  }
  navigateToCase(nextRow.id)
}

function applySidebarExecutionStatus(value: string | number | object) {
  sidebarExecutionStatus.value = typeof value === 'string' ? value as ExecutionStatus | '' : ''
}

function openLinkBugDialog() {
  bugLinkKeyword.value = ''
  bugLinkDrawerVisible.value = true
}

function openCreateBugDrawer() {
  if (!detail.value) {
    return
  }
  bugEditorMode.value = 'create'
  pendingBugFiles.value.forEach(item => {
    if (item.previewUrl) {
      URL.revokeObjectURL(item.previewUrl)
    }
  })
  pendingBugFiles.value = []
  bugForm.value = {
    workspaceCode: detail.value.workspaceCode,
    title: '',
    description: buildExecutionBugDescription(detail.value),
    priority: 'P1',
    severity: 'HIGH',
    assigneeId: null,
    relatedCaseId: detail.value.id,
    tags: [],
  }
  bugCreateVisible.value = true
}

function openEditBugDrawer() {
  if (!activeBugDetail.value) {
    return
  }
  bugDetailVisible.value = false
  bugEditorMode.value = 'edit'
  pendingBugFiles.value.forEach(item => {
    if (item.previewUrl) {
      URL.revokeObjectURL(item.previewUrl)
    }
  })
  pendingBugFiles.value = []
  bugForm.value = {
    workspaceCode: activeBugDetail.value.workspaceCode,
    title: activeBugDetail.value.title,
    description: activeBugDetail.value.description,
    priority: activeBugDetail.value.priority,
    severity: activeBugDetail.value.severity,
    assigneeId: activeBugDetail.value.assigneeId,
    relatedCaseId: activeBugDetail.value.relatedCaseId,
    tags: [...activeBugDetail.value.tags],
  }
  bugCreateVisible.value = true
}

async function openBugDetailDrawer(bugId: number) {
  if (!detail.value) {
    return
  }
  const summary = relatedBugs.value.find(item => item.id === bugId) ?? null
  bugDetailSummary.value = summary
    ? {
        bugNo: summary.bugNo,
        title: summary.title,
        workspaceName: summary.workspaceName,
        status: summary.status,
        assigneeName: summary.assigneeName,
      }
    : null
  bugDetailVisible.value = true
  bugDetailLoading.value = true
  activeBugDetail.value = relatedBugDetails.value[bugId] ?? null
  try {
    activeBugDetail.value = await platformApi.getBugDetail(detail.value.workspaceCode, bugId)
    relatedBugDetails.value = {
      ...relatedBugDetails.value,
      [bugId]: activeBugDetail.value,
    }
  }
  catch (error) {
    bugDetailVisible.value = false
    ElMessage.error((error as Error).message)
  }
  finally {
    bugDetailLoading.value = false
  }
}

async function associateBug(bugIds: number[]) {
  if (!currentCaseId.value || !detail.value) {
    return
  }
  bugLinkAssociating.value = true
  try {
    for (const bugId of bugIds) {
      const bug = await platformApi.getBugDetail(detail.value.workspaceCode, bugId)
      await platformApi.updateBug(detail.value.workspaceCode, bugId, buildUpdateBugPayload(bug, currentCaseId.value))
    }
    ElMessage.success(`Associated ${bugIds.length} bugs`)
    bugLinkDrawerVisible.value = false
    await loadRelatedBugs(currentCaseId.value, detail.value.workspaceCode)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    bugLinkAssociating.value = false
  }
}

async function reloadActiveBugDetail() {
  if (!detail.value || !activeBugDetail.value) {
    return
  }
  activeBugDetail.value = await platformApi.getBugDetail(detail.value.workspaceCode, activeBugDetail.value.id)
  relatedBugDetails.value = {
    ...relatedBugDetails.value,
    [activeBugDetail.value.id]: activeBugDetail.value,
  }
}

function buildDescriptionUpdatePayload(bug: BugDetail): UpdateBugPayload {
  return {
    workspaceCode: bug.workspaceCode,
    title: bug.title,
    description: bug.description,
    priority: bug.priority,
    severity: bug.severity,
    assigneeId: bug.assigneeId,
    relatedCaseId: bug.relatedCaseId,
    tags: bug.tags,
  }
}

function addPendingBugFiles(files: File[]) {
  const nextItems = files.map((file, index) => {
    const isImage = file.type.startsWith('image/')
    return {
      id: `${isImage ? 'screenshot' : 'attachment'}-${Date.now()}-${index}-${file.name}`,
      file,
      kind: isImage ? 'screenshot' as const : 'attachment' as const,
      previewUrl: isImage ? URL.createObjectURL(file) : null,
    }
  })
  pendingBugFiles.value = [...pendingBugFiles.value, ...nextItems]
}

function clearPendingBugFiles() {
  pendingBugFiles.value.forEach(item => {
    if (item.previewUrl) {
      URL.revokeObjectURL(item.previewUrl)
    }
  })
  pendingBugFiles.value = []
}

function removePendingBugFile(id: string) {
  const target = pendingBugFiles.value.find(item => item.id === id)
  if (target?.previewUrl) {
    URL.revokeObjectURL(target.previewUrl)
  }
  pendingBugFiles.value = pendingBugFiles.value.filter(item => item.id !== id)
}

async function uploadPendingBugFiles(workspaceCode: string, bugId: number) {
  if (!pendingBugFiles.value.length) {
    return [] as BugAttachment[]
  }
  const files = pendingBugFiles.value.map(item => item.file)
  const uploaded = await platformApi.uploadBugAttachment(workspaceCode, bugId, files)
  clearPendingBugFiles()
  return uploaded
}

function addPendingBugInlineImage(payload: PendingInlineImage) {
  pendingBugInlineImages.value = [...pendingBugInlineImages.value, payload]
}

function clearPendingBugInlineImages() {
  pendingBugInlineImages.value.forEach((item) => {
    URL.revokeObjectURL(item.src)
  })
  pendingBugInlineImages.value = []
}

async function uploadPendingBugInlineImages(workspaceCode: string, bugId: number, html: string) {
  const parser = new DOMParser()
  const doc = parser.parseFromString(`<div>${html}</div>`, 'text/html')
  const container = doc.body.firstElementChild as HTMLElement | null
  if (!container) {
    clearPendingBugInlineImages()
    return html
  }
  const unresolvedImages = Array.from(container.querySelectorAll('img')) as HTMLImageElement[]
  const consumedImages = new Set<HTMLImageElement>()
  for (const item of pendingBugInlineImages.value) {
    const exactMatches = Array.from(
      container.querySelectorAll(`img[src="${item.src.replaceAll('"', '&quot;')}"]`),
    ) as HTMLImageElement[]
    const fallbackMatch = unresolvedImages.find(image => {
      if (consumedImages.has(image)) {
        return false
      }
      const source = image.getAttribute('src') || ''
      return /^blob:|^data:/i.test(source)
    })
    const targetImages = exactMatches.length ? exactMatches : (fallbackMatch ? [fallbackMatch] : [])
    if (!targetImages.length) {
      URL.revokeObjectURL(item.src)
      continue
    }
    const [attachment] = await platformApi.uploadBugAttachment(workspaceCode, bugId, [item.file])
    const imageUrl = attachment.downloadUrl || `/api/bugs/${bugId}/attachments/${attachment.id}/download`
    targetImages.forEach((image) => {
      image.setAttribute('src', imageUrl)
      consumedImages.add(image)
    })
    URL.revokeObjectURL(item.src)
  }
  pendingBugInlineImages.value = []
  return container.innerHTML
}

async function submitCreateBug(keepOpen = false) {
  if (!detail.value || !currentCaseId.value) {
    return
  }
  if (!bugForm.value.title.trim()) {
    ElMessage.error('请输入缺陷标题')
    return
  }
  if (!extractPlainTextFromHtml(bugForm.value.description)) {
    ElMessage.error('请输入缺陷描述')
    return
  }
  bugSaving.value = true
  try {
    const payload = {
      workspaceCode: bugForm.value.workspaceCode,
      title: bugForm.value.title.trim(),
      description: bugForm.value.description.trim(),
      priority: bugForm.value.priority,
      severity: bugForm.value.severity,
      assigneeId: bugForm.value.assigneeId,
      tags: bugForm.value.tags,
    }
    if (bugEditorMode.value === 'create') {
      const createdBug = await platformApi.createBugFromCase(detail.value.workspaceCode, currentCaseId.value, {
        ...payload,
        relatedCaseId: currentCaseId.value,
      })
      await uploadPendingBugFiles(detail.value.workspaceCode, createdBug.id)
      ElMessage.success('已从用例创建缺陷')
      if (keepOpen) {
        openCreateBugDrawer()
        activeTab.value = 'bugs'
        await loadRelatedBugs(currentCaseId.value, detail.value.workspaceCode)
        return
      }
    }
    else if (activeBugDetail.value) {
      activeBugDetail.value = await platformApi.updateBug(detail.value.workspaceCode, activeBugDetail.value.id, {
        ...payload,
        relatedCaseId: currentCaseId.value,
      })
      await uploadPendingBugFiles(detail.value.workspaceCode, activeBugDetail.value.id)
      ElMessage.success('缺陷已更新')
    }
    bugCreateVisible.value = false
    clearPendingBugFiles()
    activeTab.value = 'bugs'
    await loadRelatedBugs(currentCaseId.value, detail.value.workspaceCode)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    bugSaving.value = false
  }
}

async function unlinkBug(bug: BugSummary | BugDetail) {
  if (!detail.value) {
    return
  }
  try {
    const target = 'description' in bug
      ? bug
      : await platformApi.getBugDetail(detail.value.workspaceCode, bug.id)
    await platformApi.updateBug(detail.value.workspaceCode, target.id, {
      workspaceCode: target.workspaceCode,
      title: target.title,
      description: target.description,
      priority: target.priority,
      severity: target.severity,
      assigneeId: target.assigneeId,
      relatedCaseId: null,
      tags: target.tags,
    })
    ElMessage.success('已取消关联缺陷')
    if (activeBugDetail.value?.id === target.id) {
      bugDetailVisible.value = false
      activeBugDetail.value = null
    }
    await loadRelatedBugs(currentCaseId.value ?? detail.value.id, detail.value.workspaceCode)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function submitBugTransition(payload: { status: string, comment: string }) {
  if (!detail.value || !activeBugDetail.value) {
    return
  }
  bugTransitioning.value = true
  try {
    activeBugDetail.value = await platformApi.transitionBug(
      detail.value.workspaceCode,
      activeBugDetail.value.id,
      payload.status,
      payload.comment,
    )
    ElMessage.success('缺陷状态已更新')
    await loadRelatedBugs(currentCaseId.value ?? detail.value.id, detail.value.workspaceCode)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    bugTransitioning.value = false
  }
}

async function saveBugBasic(payload: UpdateBugPayload) {
  if (!detail.value || !activeBugDetail.value) {
    return
  }
  bugBasicSaving.value = true
  try {
    activeBugDetail.value = await platformApi.updateBug(
      detail.value.workspaceCode,
      activeBugDetail.value.id,
      {
        ...payload,
        relatedCaseId: activeBugDetail.value.relatedCaseId,
      },
    )
    relatedBugDetails.value = {
      ...relatedBugDetails.value,
      [activeBugDetail.value.id]: activeBugDetail.value,
    }
    await loadRelatedBugs(currentCaseId.value ?? detail.value.id, detail.value.workspaceCode)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    bugBasicSaving.value = false
  }
}

async function associateBugCase(caseId: number) {
  if (!detail.value || !activeBugDetail.value) {
    return
  }
  bugCaseAssociating.value = true
  try {
    activeBugDetail.value = await platformApi.updateBug(
      detail.value.workspaceCode,
      activeBugDetail.value.id,
      {
        ...buildDescriptionUpdatePayload(activeBugDetail.value),
        relatedCaseId: caseId,
      },
    )
    relatedBugDetails.value = {
      ...relatedBugDetails.value,
      [activeBugDetail.value.id]: activeBugDetail.value,
    }
    await loadRelatedBugs(currentCaseId.value ?? detail.value.id, detail.value.workspaceCode)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    bugCaseAssociating.value = false
  }
}

async function unlinkActiveBugCase() {
  if (!activeBugDetail.value) {
    return
  }
  bugCaseAssociating.value = true
  try {
    await unlinkBug(activeBugDetail.value)
  }
  finally {
    bugCaseAssociating.value = false
  }
}

async function submitBugComment(content: string) {
  if (!detail.value || !activeBugDetail.value) {
    return
  }
  bugCommenting.value = true
  try {
    await platformApi.addBugComment(detail.value.workspaceCode, activeBugDetail.value.id, content)
    activeBugDetail.value = await platformApi.getBugDetail(detail.value.workspaceCode, activeBugDetail.value.id)
    ElMessage.success('评论已添加')
    await loadRelatedBugs(currentCaseId.value ?? detail.value.id, detail.value.workspaceCode)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    bugCommenting.value = false
  }
}

async function saveBugDescription(content: string) {
  if (!detail.value || !activeBugDetail.value) {
    return
  }
  bugDescriptionSaving.value = true
  try {
    const description = await uploadPendingBugInlineImages(
      detail.value.workspaceCode,
      activeBugDetail.value.id,
      content,
    )
    activeBugDetail.value = await platformApi.updateBug(
      detail.value.workspaceCode,
      activeBugDetail.value.id,
      {
        ...buildDescriptionUpdatePayload(activeBugDetail.value),
        description,
      },
    )
    relatedBugDetails.value = {
      ...relatedBugDetails.value,
      [activeBugDetail.value.id]: activeBugDetail.value,
    }
    await loadRelatedBugs(currentCaseId.value ?? detail.value.id, detail.value.workspaceCode)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    bugDescriptionSaving.value = false
  }
}

async function uploadBugAttachments(files: File[]) {
  if (!detail.value || !activeBugDetail.value) {
    return
  }
  bugAttachmentUploading.value = true
  try {
    await platformApi.uploadBugAttachment(detail.value.workspaceCode, activeBugDetail.value.id, files)
    await reloadActiveBugDetail()
    await loadRelatedBugs(currentCaseId.value ?? detail.value.id, detail.value.workspaceCode)
    ElMessage.success(`已上传 ${files.length} 个附件`)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    bugAttachmentUploading.value = false
  }
}

async function downloadBugAttachment(attachmentId: number) {
  if (!detail.value) {
    return
  }
  const attachment = activeBugDetail.value?.attachments.find(item => item.id === attachmentId)
  const sourceBug = activeBugDetail.value
  if (!attachment || !sourceBug) {
    return
  }
  try {
    await platformApi.downloadBugAttachment(detail.value.workspaceCode, sourceBug.id, attachmentId, attachment.fileName)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function isImageAttachment(contentType: string | null | undefined, fileName: string) {
  if (contentType?.toLowerCase().startsWith('image/')) {
    return true
  }
  return /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(fileName)
}

function revokeExecutionAttachmentImageUrls(keepIds: number[] = []) {
  const keep = new Set(keepIds)
  const nextUrls: Record<number, string> = {}
  Object.entries(executionAttachmentImageUrls.value).forEach(([key, value]) => {
    const attachmentId = Number(key)
    if (keep.has(attachmentId)) {
      nextUrls[attachmentId] = value
      return
    }
    URL.revokeObjectURL(value)
  })
  executionAttachmentImageUrls.value = nextUrls
}

async function ensureExecutionAttachmentImageUrl(attachmentId: number) {
  const cached = executionAttachmentImageUrls.value[attachmentId]
  if (cached) {
    return cached
  }
  const attachment = detail.value?.attachments.find(item => item.id === attachmentId)
  const workspace = detail.value?.workspaceCode
  if (!attachment || !detail.value || !workspace || !isImageAttachment(attachment.contentType, attachment.fileName)) {
    return ''
  }
  const response = await fetch(resolveApiUrl(
    attachment.downloadUrl || `/cases/${detail.value.id}/attachments/${attachment.id}/download`,
  ), {
    method: 'GET',
    credentials: 'include',
    headers: {
      'X-Workspace-Code': workspace,
    },
  })
  if (!response.ok) {
    throw new Error('图片预览加载失败')
  }
  const blob = await response.blob()
  const objectUrl = URL.createObjectURL(blob)
  executionAttachmentImageUrls.value = {
    ...executionAttachmentImageUrls.value,
    [attachmentId]: objectUrl,
  }
  return objectUrl
}

async function syncExecutionAttachmentImageUrls(attachments: CaseDetail['attachments']) {
  const imageAttachments = attachments.filter(item => isImageAttachment(item.contentType, item.fileName))
  revokeExecutionAttachmentImageUrls(imageAttachments.map(item => item.id))
  await Promise.all(imageAttachments.map(async (attachment) => {
    try {
      await ensureExecutionAttachmentImageUrl(attachment.id)
    }
    catch {
      // Let individual previews surface a user-facing error on demand.
    }
  }))
}

function openExecutionAttachmentPicker() {
  executionAttachmentInput.value?.click()
}

function filterExecutionAttachmentsForUpload(files: File[]) {
  const currentCount = detail.value?.attachments.length ?? 0
  const remainingCount = Math.max(0, EXECUTION_ATTACHMENT_MAX_COUNT - currentCount)
  if (remainingCount <= 0) {
    ElMessage.warning(`当前用例最多上传 ${EXECUTION_ATTACHMENT_MAX_COUNT} 个附件`)
    return []
  }

  const oversizedFiles = files.filter(file => file.size > EXECUTION_ATTACHMENT_MAX_SIZE)
  const validFiles = files.filter(file => file.size <= EXECUTION_ATTACHMENT_MAX_SIZE)

  if (oversizedFiles.length) {
    ElMessage.warning(`单个附件不能超过 ${Math.floor(EXECUTION_ATTACHMENT_MAX_SIZE / 1024 / 1024)}MB`)
  }

  if (!validFiles.length) {
    return []
  }

  if (validFiles.length > remainingCount) {
    ElMessage.warning(`当前用例最多上传 ${EXECUTION_ATTACHMENT_MAX_COUNT} 个附件，超出部分未添加`)
  }

  return validFiles.slice(0, remainingCount)
}

function handleExecutionAttachmentChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  if (!files.length) {
    return
  }
  const acceptedFiles = filterExecutionAttachmentsForUpload(files)
  if (!acceptedFiles.length) {
    return
  }
  void uploadExecutionAttachments(acceptedFiles)
}

function handleExecutionAttachmentPaste(event: ClipboardEvent) {
  if (!detail.value) {
    return
  }
  const files = Array.from(event.clipboardData?.items || [])
    .filter(item => item.kind === 'file')
    .map(item => item.getAsFile())
    .filter((item): item is File => !!item && item.type.startsWith('image/'))
    .map((file, index) => new File([file], `${detail.value!.caseNo.toLowerCase()}-paste-${Date.now()}-${index + 1}.png`, { type: file.type || 'image/png' }))
  if (!files.length) {
    return
  }
  event.preventDefault()
  const acceptedFiles = filterExecutionAttachmentsForUpload(files)
  if (!acceptedFiles.length) {
    return
  }
  void uploadExecutionAttachments(acceptedFiles)
}

function handleExecutionAttachmentDragEnter() {
  executionAttachmentDropActive.value = true
}

function handleExecutionAttachmentDragLeave(event: DragEvent) {
  const currentTarget = event.currentTarget as HTMLElement | null
  const relatedTarget = event.relatedTarget as Node | null
  if (currentTarget && relatedTarget && currentTarget.contains(relatedTarget)) {
    return
  }
  executionAttachmentDropActive.value = false
}

function handleExecutionAttachmentDrop(event: DragEvent) {
  executionAttachmentDropActive.value = false
  const files = Array.from(event.dataTransfer?.files || [])
  if (!files.length) {
    return
  }
  const acceptedFiles = filterExecutionAttachmentsForUpload(files)
  if (!acceptedFiles.length) {
    return
  }
  void uploadExecutionAttachments(acceptedFiles)
}

async function previewExecutionAttachment(attachmentId: number) {
  const attachment = detail.value?.attachments.find(item => item.id === attachmentId)
  if (!attachment || !detail.value) {
    return
  }
  if (!isImageAttachment(attachment.contentType, attachment.fileName)) {
    void downloadExecutionAttachment(attachmentId)
    return
  }
  try {
    resetExecutionImagePreview()
    activeExecutionImageAttachmentId.value = attachment.id
    executionImagePreviewTitle.value = attachment.fileName
    executionImagePreviewUrl.value = await ensureExecutionAttachmentImageUrl(attachment.id)
    executionImagePreviewVisible.value = true
  }
  catch (error) {
    ElMessage.error((error as Error).message || '图片预览加载失败')
  }
}

function resolveExecutionAttachmentUrl(attachmentId: number) {
  return executionAttachmentImageUrls.value[attachmentId] || ''
}

function resetExecutionImagePreview() {
  executionImagePreviewScale.value = 1
  executionImagePreviewOffset.value = { x: 0, y: 0 }
  executionImagePreviewDragging.value = false
}

function zoomExecutionImagePreview(delta: number) {
  const nextScale = Math.min(4, Math.max(1, Number((executionImagePreviewScale.value + delta).toFixed(2))))
  executionImagePreviewScale.value = nextScale
  if (nextScale === 1) {
    executionImagePreviewOffset.value = { x: 0, y: 0 }
  }
}

function handleExecutionImagePreviewWheel(event: WheelEvent) {
  event.preventDefault()
  zoomExecutionImagePreview(event.deltaY < 0 ? 0.2 : -0.2)
}

function handleExecutionImagePreviewPointerDown(event: MouseEvent) {
  if (executionImagePreviewScale.value <= 1) {
    return
  }
  executionImagePreviewDragging.value = true
  executionImagePreviewDragOrigin.value = {
    x: event.clientX,
    y: event.clientY,
    offsetX: executionImagePreviewOffset.value.x,
    offsetY: executionImagePreviewOffset.value.y,
  }
}

function handleExecutionImagePreviewPointerMove(event: MouseEvent) {
  if (!executionImagePreviewDragging.value) {
    return
  }
  executionImagePreviewOffset.value = {
    x: executionImagePreviewDragOrigin.value.offsetX + event.clientX - executionImagePreviewDragOrigin.value.x,
    y: executionImagePreviewDragOrigin.value.offsetY + event.clientY - executionImagePreviewDragOrigin.value.y,
  }
}

function handleExecutionImagePreviewPointerUp() {
  executionImagePreviewDragging.value = false
}

function toggleExecutionImagePreviewZoom() {
  if (executionImagePreviewScale.value > 1) {
    resetExecutionImagePreview()
    return
  }
  executionImagePreviewScale.value = 2
}

async function openExecutionImagePreviewByOffset(offset: -1 | 1) {
  const currentIndex = activeExecutionImageIndex.value
  if (currentIndex < 0) {
    return
  }
  const nextAttachment = executionImageAttachments.value[currentIndex + offset]
  if (!nextAttachment) {
    return
  }
  await previewExecutionAttachment(nextAttachment.id)
}

function handleExecutionImagePreviewKeydown(event: KeyboardEvent) {
  if (!executionImagePreviewVisible.value) {
    return
  }
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    void openExecutionImagePreviewByOffset(-1)
    return
  }
  if (event.key === 'ArrowRight') {
    event.preventDefault()
    void openExecutionImagePreviewByOffset(1)
  }
}

async function removeBugAttachment(attachmentId: number) {
  if (!detail.value || !activeBugDetail.value) {
    return
  }
  bugAttachmentRemovingId.value = attachmentId
  try {
    await platformApi.deleteBugAttachment(detail.value.workspaceCode, activeBugDetail.value.id, attachmentId)
    await reloadActiveBugDetail()
    ElMessage.success('附件已删除')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    bugAttachmentRemovingId.value = null
  }
}

async function uploadExecutionAttachments(files: File[]) {
  if (!detail.value) {
    return
  }
  executionAttachmentUploading.value = true
  try {
    await platformApi.uploadCaseExecutionAttachment(detail.value.workspaceCode, detail.value.id, files)
    detail.value = await platformApi.getCaseDetail(detail.value.workspaceCode, detail.value.id)
    updateExecutionCollection(detail.value)
    ElMessage.success(`已上传 ${files.length} 个执行附件`)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    executionAttachmentUploading.value = false
  }
}

async function downloadExecutionAttachment(attachmentId: number) {
  if (!detail.value) {
    return
  }
  const attachment = detail.value.attachments.find(item => item.id === attachmentId)
  if (!attachment) {
    return
  }
  try {
    await platformApi.downloadCaseExecutionAttachment(detail.value.workspaceCode, detail.value.id, attachmentId, attachment.fileName)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function removeExecutionAttachment(attachmentId: number) {
  if (!detail.value) {
    return
  }
  executionAttachmentRemovingId.value = attachmentId
  try {
    await platformApi.deleteCaseExecutionAttachment(detail.value.workspaceCode, detail.value.id, attachmentId)
    detail.value = await platformApi.getCaseDetail(detail.value.workspaceCode, detail.value.id)
    updateExecutionCollection(detail.value)
    ElMessage.success('执行附件已删除')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    executionAttachmentRemovingId.value = null
  }
}

function openCaseEdit() {
  if (!detail.value) {
    return
  }
  caseEditorForm.value = {
    id: detail.value.id,
    workspaceCode: detail.value.workspaceCode,
    directoryId: detail.value.directoryId ?? null,
    title: detail.value.title,
    priority: detail.value.priority,
    sourceType: detail.value.sourceType,
    caseStatus: detail.value.status,
    precondition: detail.value.precondition ?? '',
    steps: detail.value.steps ?? '',
    expectedResult: detail.value.expectedResult ?? '',
  }
  void loadCaseModuleDirectories(
    detail.value.workspaceCode,
    detail.value.directoryId ?? null,
    detail.value.directoryName,
  )
  caseEditorVisible.value = true
}

function resolveCaseEditorDirectoryPath(directoryId: number | null) {
  if (!caseEditorForm.value.workspaceCode) {
    return '-'
  }
  const workspaceName = caseEditorWorkspaceName.value
  if (directoryId === null) {
    return workspaceName
  }
  const resolvedName = findDirectoryNameById(caseModuleDirectoryTree.value, directoryId)
  if (resolvedName) {
    return `${workspaceName} / ${resolvedName}`
  }
  const fallbackName = normalizeDirectoryLabel(detail.value?.directoryName)
  return fallbackName ? `${workspaceName} / ${fallbackName}` : workspaceName
}

function ensureCaseModuleDirectoriesLoaded() {
  if (!caseEditorForm.value.workspaceCode || caseModuleDirectoryTree.value.length) {
    return
  }
  void loadCaseModuleDirectories(
    caseEditorForm.value.workspaceCode,
    caseEditorForm.value.directoryId,
    detail.value?.directoryName ?? null,
  )
}

async function submitCaseEdit() {
  if (!caseEditorForm.value.id) {
    return
  }
  if (!caseEditorForm.value.title.trim()) {
    ElMessage.error('请输入用例名称')
    return
  }
  caseSaving.value = true
  try {
    const payload: CreateCasePayload = {
      workspaceCode: caseEditorForm.value.workspaceCode,
      directoryId: caseEditorForm.value.directoryId,
      title: caseEditorForm.value.title.trim(),
      caseType: 'FUNCTION',
      priority: caseEditorForm.value.priority,
      sourceType: caseEditorForm.value.sourceType.trim(),
      caseStatus: caseEditorForm.value.caseStatus,
      ownerId: null,
      precondition: caseEditorForm.value.precondition.trim(),
      steps: caseEditorForm.value.steps.trim(),
      expectedResult: caseEditorForm.value.expectedResult.trim(),
    }
    await platformApi.updateCase(caseEditorForm.value.workspaceCode, caseEditorForm.value.id, payload)
    ElMessage.success('用例已更新')
    caseEditorVisible.value = false
    if (currentCaseId.value !== null) {
      await loadCaseDetail(currentCaseId.value)
    }
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    caseSaving.value = false
  }
}

async function submitExecution() {
  if (!detail.value || !currentCaseId.value) {
    return
  }
  if (!selectedExecutionStatus.value) {
    ElMessage.warning('请先选择执行结果')
    return
  }
  submitting.value = true
  try {
    const response = await platformApi.executeCase(effectiveWorkspaceCode.value, currentCaseId.value, {
      executionStatus: selectedExecutionStatus.value,
      executionComment: executionComment.value.trim(),
      executionNote: executionNoteDraft.value.trim(),
    })
    detail.value = response
    updateExecutionCollection(response)
    syncExecutionInputs(response)
    ElMessage.success('执行结果已更新')
    if (autoNext.value && canPreviewNextCase.value) {
      moveCase(1)
      return
    }
    if (autoNext.value && !canPreviewNextCase.value) {
      ElMessage.success('当前列表已经执行到最后一条')
    }
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    submitting.value = false
  }
}

watch(
  () => route.params.id,
  () => {
    if (currentCaseId.value !== null) {
      void loadCaseDetail(currentCaseId.value)
    }
  },
)

watch(visibleExecutionCases, (rows) => {
  if (!rows.length || currentCaseId.value === null) {
    return
  }
  if (!rows.some(item => item.id === currentCaseId.value)) {
    navigateToCase(rows[0].id)
  }
}, { flush: 'sync' })

watch(
  () => detail.value?.attachments ?? [],
  (attachments) => {
    void syncExecutionAttachmentImageUrls(attachments)
  },
  { deep: true },
)

watch(executionImagePreviewVisible, (visible) => {
  if (!visible) {
    resetExecutionImagePreview()
    activeExecutionImageAttachmentId.value = null
  }
})

watch(bugDetailVisible, (visible) => {
  if (!visible) {
    clearPendingBugInlineImages()
  }
})

onMounted(() => {
  window.addEventListener('keydown', handleExecutionImagePreviewKeydown)
  void bootstrap()
})

onBeforeUnmount(() => {
  revokeExecutionAttachmentImageUrls()
  clearPendingBugInlineImages()
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleExecutionImagePreviewKeydown)
  clearPendingBugFiles()
})
</script>

<template>
  <section class="execution-page" v-loading="loading">
    <aside class="execution-sidebar">
      <div class="execution-sidebar-header">
        <div class="execution-sidebar-title">{{ sidebarRequirementName }}</div>
      </div>
      <div class="execution-sidebar-toolbar">
        <el-input
          v-model="sidebarKeyword"
          placeholder="支持 ID / 标题模糊搜索"
          clearable
          :prefix-icon="Search"
          class="execution-sidebar-search"
        />
        <el-dropdown trigger="click" @command="applySidebarExecutionStatus">
          <el-button
            class="execution-sidebar-filter-button"
            :class="{ 'is-active': !!sidebarExecutionStatus }"
            circle
          >
            <el-icon><Filter /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="option in sidebarStatusOptions"
                :key="option.value || 'all'"
                :command="option.value"
                :class="{ 'is-active': sidebarExecutionStatus === option.value }"
              >
                {{ option.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <div class="execution-sidebar-list">
        <button
          v-for="item in visibleExecutionCases"
          :key="item.id"
          type="button"
          :class="['execution-sidebar-item', { 'is-active': item.id === currentCaseId }]"
          @click="navigateToCase(item.id)"
        >
          <div class="execution-sidebar-item-top">
            <span class="execution-sidebar-case-no">{{ item.caseNo }}</span>
            <el-tag
              size="small"
              effect="plain"
              :type="executionStatusTagType(item.executionStatus)"
              :class="executionStatusTagClass(item.executionStatus)"
            >
              {{ executionStatusLabel(item.executionStatus) }}
            </el-tag>
          </div>
          <div class="execution-sidebar-case-title">{{ item.title }}</div>
        </button>
        <el-empty v-if="!visibleExecutionCases.length" description="暂无匹配用例" :image-size="72" />
      </div>
      <div class="execution-sidebar-footer">
        <span>{{ executionCases.length }} 条</span>
        <span>{{ activeCaseDisplayIndex }}/{{ visibleExecutionCases.length || 0 }}</span>
      </div>
    </aside>

    <section class="execution-main">
      <div class="execution-main-backbar">
        <el-button class="execution-back-button" text :icon="ArrowLeft" @click="goBackToCaseManagement">返回用例管理</el-button>
      </div>
      <div class="execution-main-header">
        <div class="execution-main-header-content">
          <div class="execution-main-header-top">
            <el-tag
              effect="plain"
              :type="executionStatusTagType(detail?.executionStatus || 'NOT_RUN')"
              :class="executionStatusTagClass(detail?.executionStatus || 'NOT_RUN')"
            >
              {{ executionStatusLabel(detail?.executionStatus || 'NOT_RUN') }}
            </el-tag>
            <div class="execution-main-title">
              <span class="execution-main-case-no">[{{ detail?.caseNo || '-' }}]</span>
              <span class="execution-main-case-title">{{ detail?.title || '用例详情' }}</span>
            </div>
          </div>
        </div>
        <el-button v-if="canEditCurrentCase" @click="openCaseEdit">
          <el-icon><Edit /></el-icon>
          编辑
        </el-button>
      </div>

      <div class="execution-body">
        <el-tabs v-model="activeTab" class="execution-tabs">
          <el-tab-pane label="基本信息" name="basic">
            <div class="execution-section-grid">
              <section class="execution-card">
                <div class="execution-card-label">用例模块</div>
                <div class="execution-card-value execution-card-value-muted">{{ modulePath }}</div>
              </section>
              <section class="execution-card">
                <div class="execution-card-label">优先级</div>
                <div class="execution-card-value">{{ detail?.priority || '-' }}</div>
              </section>
              <section class="execution-card">
                <div class="execution-card-label">用例来源</div>
                <div class="execution-card-value">{{ detail?.sourceType || '-' }}</div>
              </section>
              <section class="execution-card">
                <div class="execution-card-label">创建时间</div>
                <div class="execution-card-value">{{ formatDateTime(detail?.createdAt || null) }}</div>
              </section>
              <section class="execution-card">
                <div class="execution-card-label">评审结果</div>
                <div class="execution-card-value">
                  <el-tag
                    effect="plain"
                    :type="reviewStatusTagType(detail?.reviewStatus || 'PENDING')"
                    :class="reviewStatusTagClass(detail?.reviewStatus || 'PENDING')"
                  >
                    {{ reviewStatusLabel(detail?.reviewStatus || 'PENDING') }}
                  </el-tag>
                </div>
              </section>
              <section class="execution-card">
                <div class="execution-card-label">评审人</div>
                <div class="execution-card-value">{{ detail?.reviewedByName || '-' }}</div>
              </section>
              <section class="execution-card">
                <div class="execution-card-label">执行人</div>
                <div class="execution-card-value">{{ detail?.executorName || '-' }}</div>
              </section>
              <section class="execution-card">
                <div class="execution-card-label">执行时间</div>
                <div class="execution-card-value">{{ formatDateTime(detail?.executedAt || null) }}</div>
              </section>
            </div>
          </el-tab-pane>

          <el-tab-pane label="详情" name="detail">
            <div class="execution-detail-stack">
              <section class="execution-detail-row">
                <div class="execution-detail-column">
                  <div class="execution-card-label">前置条件</div>
                  <div class="execution-rich-text execution-rich-text-tall">{{ detail?.precondition || '-' }}</div>
                </div>
                <div class="execution-detail-column">
                  <div class="execution-card-label">测试步骤</div>
                  <div class="execution-rich-text execution-rich-text-tall">{{ detail?.steps || '-' }}</div>
                </div>
                <div class="execution-detail-column">
                  <div class="execution-card-label">预期结果</div>
                  <div class="execution-rich-text execution-rich-text-tall">{{ detail?.expectedResult || '-' }}</div>
                </div>
                <div class="execution-detail-column">
                  <div class="execution-card-label">实际结果</div>
                  <el-input
                    v-model="executionComment"
                    type="textarea"
                    :rows="8"
                    resize="vertical"
                    placeholder="请输入实际结果"
                    class="execution-actual-result-input"
                  />
                </div>
              </section>
              <section
                class="execution-detail-card execution-evidence-card"
                :class="{ 'is-drop-active': executionAttachmentDropActive }"
                tabindex="0"
                @paste="handleExecutionAttachmentPaste"
                @dragenter.prevent="handleExecutionAttachmentDragEnter"
                @dragover.prevent="executionAttachmentDropActive = true"
                @dragleave="handleExecutionAttachmentDragLeave"
                @drop.prevent="handleExecutionAttachmentDrop"
              >
                <div class="execution-evidence-header">
                  <div>
                    <div class="execution-card-label">执行附件（{{ detail?.attachments?.length || 0 }}）</div>
                    <div class="execution-evidence-meta">点击加号上传，或在此区域按 Ctrl+V 粘贴图片</div>
                  </div>
                </div>
                <input
                  ref="executionAttachmentInput"
                  type="file"
                  multiple
                  style="display: none"
                  @change="handleExecutionAttachmentChange"
                >
                <div class="execution-evidence-files">
                  <div
                    v-for="attachment in detail?.attachments ?? []"
                    :key="attachment.id"
                    class="execution-evidence-file"
                  >
                    <button
                      type="button"
                      class="execution-evidence-file-remove"
                      :disabled="executionAttachmentRemovingId === attachment.id"
                      @click.stop="removeExecutionAttachment(attachment.id)"
                    >
                      ×
                    </button>
                    <div class="execution-evidence-file-preview">
                      <button
                        v-if="isImageAttachment(attachment.contentType, attachment.fileName)"
                        type="button"
                        class="execution-evidence-thumb"
                        @click="previewExecutionAttachment(attachment.id)"
                      >
                        <img :src="resolveExecutionAttachmentUrl(attachment.id)" :alt="attachment.fileName" class="execution-evidence-thumb-image">
                      </button>
                      <div v-else class="execution-evidence-file-fallback">
                        {{ (attachment.fileName.split('.').pop() || 'FILE').slice(0, 6).toUpperCase() }}
                      </div>
                    </div>
                    <button
                      type="button"
                      class="execution-evidence-file-trigger"
                      @click="previewExecutionAttachment(attachment.id)"
                    >
                      <span class="execution-evidence-file-name" :title="attachment.fileName">{{ attachment.fileName }}</span>
                    </button>
                  </div>
                  <button
                    type="button"
                    class="execution-evidence-add"
                    :disabled="executionAttachmentUploading"
                    @click="openExecutionAttachmentPicker"
                  >
                    <span class="execution-evidence-add-plus">+</span>
                    <span class="execution-evidence-add-text">
                      {{ executionAttachmentUploading ? '上传中...' : '上传附件' }}
                    </span>
                  </button>
                </div>
              </section>
              <section class="execution-detail-card">
                <div class="execution-card-label">备注</div>
                <el-input
                  v-model="executionNoteDraft"
                  type="textarea"
                  resize="none"
                  placeholder="请输入备注"
                  class="execution-note-input"
                />
              </section>
            </div>
          </el-tab-pane>

          <el-tab-pane name="bugs">
            <template #label>缺陷列表（{{ associatedBugs.length }}）</template>
            <section class="execution-detail-card" v-loading="bugListLoading">
              <div class="execution-tab-header">
                <div class="execution-bug-actions">
                  <el-button @click="openLinkBugDialog">关联缺陷</el-button>
                  <el-button type="primary" @click="openCreateBugDrawer">新建缺陷</el-button>
                </div>
              </div>
              <el-table
                v-if="associatedBugs.length"
                :data="associatedBugs"
                size="large"
                class="execution-bug-table"
                header-cell-class-name="execution-bug-table-header"
                cell-class-name="execution-bug-table-cell"
              >
                <el-table-column prop="bugNo" label="缺陷编号" width="170" />
                <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip />
                <el-table-column prop="priority" label="优先级" width="90" />
                <el-table-column label="严重程度" width="110">
                  <template #default="{ row }">
                    {{ bugSeverityLabel(row.severity) }}
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="110">
                  <template #default="{ row }">
                    <el-tag size="small" effect="plain">{{ bugStatusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="assigneeName" label="负责人" width="120">
                  <template #default="{ row }">
                    {{ row.assigneeName || '-' }}
                  </template>
                </el-table-column>
                  <el-table-column label="操作" width="120" fixed="right">
                    <template #default="{ row }">
                      <div class="execution-bug-action">
                        <el-button text type="primary" @click="openBugDetailDrawer(row.id)">查看</el-button>
                        <el-button text type="danger" @click="unlinkBug(row)">取消关联</el-button>
                      </div>
                    </template>
                  </el-table-column>
              </el-table>
              <el-empty v-else description="暂无关联缺陷" :image-size="84" class="execution-bug-empty" />
            </section>
          </el-tab-pane>

          <el-tab-pane label="执行历史" name="history">
            <section class="execution-detail-card">
              <div class="execution-tab-header">
                <div>
                  <div class="execution-card-title">最近执行记录</div>
                  <div class="execution-card-meta">当前接口未返回完整历史列表，这里先展示最近一次执行信息。</div>
                </div>
              </div>
              <div v-if="executionHistoryRows.length" class="execution-history-list">
                <div v-for="row in executionHistoryRows" :key="`${row.executedAt}-${row.status}`" class="execution-history-item">
                  <div class="execution-history-top">
                    <span>{{ row.status }}</span>
                    <span>{{ row.executedAt }}</span>
                  </div>
                  <div class="execution-history-meta">执行人：{{ row.executorName }}</div>
                  <div class="execution-history-comment">{{ row.comment }}</div>
                </div>
              </div>
              <el-empty v-else description="暂无执行历史" :image-size="84" />
            </section>
          </el-tab-pane>
        </el-tabs>
      </div>

      <footer v-if="activeTab === 'detail'" class="execution-footer">
        <div class="execution-footer-bar">
          <div class="execution-footer-nav">
            <el-button :disabled="!canPreviewPreviousCase" @click="moveCase(-1)">
              <el-icon><ArrowLeft /></el-icon>
              上一条
            </el-button>
            <div class="execution-footer-counter">{{ activeCaseDisplayIndex }}/{{ visibleExecutionCases.length || 0 }}</div>
            <el-button :disabled="!canPreviewNextCase" @click="moveCase(1)">
              下一条
              <el-icon><ArrowRight /></el-icon>
            </el-button>
            <div class="execution-auto-next">
              <span>自动下一条</span>
              <el-switch v-model="autoNext" />
            </div>
          </div>
          <div class="execution-submit-actions">
            <el-button @click="openCreateBugDrawer">添加缺陷</el-button>
            <el-button type="danger" plain :loading="submitting" @click="selectedExecutionStatus = 'FAILED'; submitExecution()">失败</el-button>
            <el-button type="primary" plain :loading="submitting" @click="selectedExecutionStatus = 'BLOCKED'; submitExecution()">阻塞</el-button>
            <el-button type="success" :loading="submitting" @click="selectedExecutionStatus = 'PASSED'; submitExecution()">通过</el-button>
          </div>
        </div>
      </footer>
    </section>
  </section>

  <BugLinkDrawer
    v-model="bugLinkDrawerVisible"
    v-model:keyword="bugLinkKeyword"
    :bugs="availableLinkBugs"
    :loading="bugLinkLoading"
    :associating="bugLinkAssociating"
    @associate="associateBug"
  />

  <CaseEditorDrawer
    v-model="caseEditorVisible"
    title="编辑用例"
    :form="caseEditorForm"
    :saving="caseSaving"
    :can-submit="canSubmitCaseEdit"
    submit-text="保存"
    :workspace-name="caseEditorWorkspaceName"
    :directory-tree="caseModuleDirectoryTree"
    :module-picker-loading="caseModulePickerLoading"
    :resolve-directory-path="resolveCaseEditorDirectoryPath"
    @open-module-picker="ensureCaseModuleDirectoriesLoaded"
    @submit="submitCaseEdit"
  />

  <BugEditorDrawer
    v-model="bugCreateVisible"
    :title="bugEditorMode === 'create' ? '创建缺陷' : '编辑缺陷'"
    :form="bugForm"
    :saving="bugSaving"
    :can-submit="canSubmitBugCreate"
    :users="bugAssigneeOptions"
    :source-context="executionBugSourceContext"
    :pending-files="pendingBugFiles.map(item => ({ id: item.id, name: item.file.name, size: item.file.size, kind: item.kind, previewUrl: item.previewUrl }))"
    @submit="submitCreateBug()"
    @submit-and-continue="submitCreateBug(true)"
    @add-files="addPendingBugFiles"
    @remove-file="removePendingBugFile"
  />

  <BugDetailDrawer
    v-model="bugDetailVisible"
    :detail="activeBugDetail"
    :summary="bugDetailSummary"
    :loading="bugDetailLoading"
    :users="bugAssigneeOptions"
    :basic-saving="bugBasicSaving"
    :description-saving="bugDescriptionSaving"
    :associating-case="bugCaseAssociating"
    :transitioning="bugTransitioning"
    :commenting="bugCommenting"
    :attachment-uploading="bugAttachmentUploading"
    :attachment-removing-id="bugAttachmentRemovingId"
    :can-write="!!detail && canWriteWorkspace(detail.workspaceCode)"
    @add-inline-image="addPendingBugInlineImage"
    @edit="openEditBugDrawer"
    @save-basic="saveBugBasic"
    @save-description="saveBugDescription"
    @associate-case="associateBugCase"
    @unlink-case="unlinkActiveBugCase"
    @upload-attachments="uploadBugAttachments"
    @download-attachment="downloadBugAttachment"
    @remove-attachment="removeBugAttachment"
    @transition="submitBugTransition"
    @comment="submitBugComment"
  />

  <el-dialog
    v-model="executionImagePreviewVisible"
    :title="executionImagePreviewTitle"
    width="min(960px, 92vw)"
    class="execution-evidence-preview-dialog"
  >
    <div class="execution-evidence-preview-toolbar">
      <div class="execution-evidence-preview-nav" v-if="executionImageAttachments.length > 1">
        <el-button plain size="small" :disabled="!canPreviewPreviousExecutionImage" @click="openExecutionImagePreviewByOffset(-1)">上一张</el-button>
        <div v-if="executionImagePreviewCounter" class="execution-evidence-preview-counter">{{ executionImagePreviewCounter }}</div>
        <el-button plain size="small" :disabled="!canPreviewNextExecutionImage" @click="openExecutionImagePreviewByOffset(1)">下一张</el-button>
      </div>
      <div v-else class="execution-evidence-preview-tip">滚轮缩放，拖拽查看局部</div>
      <div class="execution-evidence-preview-actions">
        <el-button plain size="small" class="execution-evidence-preview-icon-button" @click="zoomExecutionImagePreview(-0.2)">-</el-button>
        <span class="execution-evidence-preview-scale">{{ Math.round(executionImagePreviewScale * 100) }}%</span>
        <el-button plain size="small" class="execution-evidence-preview-icon-button" @click="zoomExecutionImagePreview(0.2)">+</el-button>
        <el-button plain size="small" @click="resetExecutionImagePreview">重置</el-button>
      </div>
    </div>
    <div class="execution-evidence-preview-shell">
      <div
        class="execution-evidence-preview-canvas"
        :class="{ 'is-draggable': executionImagePreviewScale > 1, 'is-dragging': executionImagePreviewDragging }"
        @wheel="handleExecutionImagePreviewWheel"
        @mousedown="handleExecutionImagePreviewPointerDown"
        @mousemove="handleExecutionImagePreviewPointerMove"
        @mouseup="handleExecutionImagePreviewPointerUp"
        @mouseleave="handleExecutionImagePreviewPointerUp"
        @dblclick="toggleExecutionImagePreviewZoom"
      >
        <img
          :src="executionImagePreviewUrl"
          :alt="executionImagePreviewTitle"
          class="execution-evidence-preview-image"
          :style="{
            transform: `translate(${executionImagePreviewOffset.x}px, ${executionImagePreviewOffset.y}px) scale(${executionImagePreviewScale})`,
          }"
        >
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.execution-page {
  display: grid;
  grid-template-columns: 292px minmax(0, 1fr);
  gap: 16px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.execution-sidebar,
.execution-main {
  min-height: 0;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: #ffffff;
}

.execution-sidebar {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.execution-sidebar-header {
  padding: 18px 18px 12px;
  border-bottom: 1px solid var(--line-soft);
}

.execution-sidebar-title,
.execution-card-title {
  font-size: 14px;
  font-weight: 700;
  color: #344054;
  line-height: 1.5;
  word-break: break-word;
}

.execution-sidebar-title {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.execution-sidebar-meta,
.execution-card-meta,
.execution-main-subtitle,
.execution-history-meta {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #667085;
}

.execution-sidebar-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  padding: 14px 18px 0;
  align-items: center;
}

.execution-sidebar-search {
  min-width: 0;
}

.execution-sidebar-filter-button {
  width: 34px;
  height: 34px;
  border-color: var(--line-soft);
  color: #667085;
  background: #ffffff;
}

.execution-sidebar-filter-button:hover {
  color: #7f56d9;
  border-color: rgba(127, 86, 217, 0.28);
  background: #fcfbff;
}

.execution-sidebar-filter-button.is-active {
  color: #7f56d9;
  border-color: rgba(127, 86, 217, 0.38);
  background: #f5f0ff;
}

.execution-sidebar-toolbar :deep(.el-dropdown-menu__item.is-active) {
  color: #7f56d9;
  background: #faf7ff;
}

.execution-sidebar-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 14px 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.execution-sidebar-item {
  position: relative;
  width: 100%;
  padding: 14px 14px 12px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: #ffffff;
  text-align: left;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease, transform 0.2s ease;
}

.execution-sidebar-item::before {
  content: '';
  position: absolute;
  left: -1px;
  top: 10px;
  bottom: 10px;
  width: 3px;
  border-radius: 999px;
  background: transparent;
  transition: background 0.2s ease;
}

.execution-sidebar-item:hover {
  border-color: rgba(127, 86, 217, 0.26);
  background: #fcfbff;
}

.execution-sidebar-item.is-active {
  border-color: rgba(127, 86, 217, 0.42);
  background: #faf7ff;
  box-shadow: 0 0 0 1px rgba(127, 86, 217, 0.12);
  transform: translateY(-1px);
}

.execution-sidebar-item.is-active::before {
  background: #7f56d9;
}

.execution-sidebar-item-top,
.execution-main-header-top,
.execution-tab-header,
.execution-submit-main,
.execution-submit-tools,
.execution-history-top,
.execution-footer-nav,
.execution-submit-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.execution-sidebar-case-title {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.55;
  color: #344054;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.execution-sidebar-case-no {
  color: #667085;
}

.execution-main-case-no {
  color: #6941c6;
}

.execution-sidebar-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 auto;
  padding: 12px 18px 16px;
  border-top: 1px solid var(--line-soft);
  font-size: 12px;
  color: #667085;
}

.execution-main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.execution-main-backbar {
  display: flex;
  align-items: center;
  padding: 12px 24px 0;
}

.execution-back-button {
  padding: 0;
  font-size: 13px;
  font-weight: 600;
  color: #175cd3;
}

.execution-back-button:hover,
.execution-back-button:focus-visible {
  color: #1849a9;
}

.execution-main-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--line-soft);
}

.execution-main-header-content {
  min-width: 0;
}

.execution-main-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.5;
  color: #101828;
  word-break: break-word;
}

.execution-main-case-no {
  flex: 0 0 auto;
  color: #667085;
  font-size: 13px;
  font-weight: 400;
}

.execution-main-case-title {
  min-width: 0;
  font-size: 16px;
  font-weight: 700;
}

.execution-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
  padding: 0 24px;
}

.execution-tabs {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}

.execution-tabs :deep(.el-tabs__header) {
  margin: 0;
  flex: 0 0 auto;
}

.execution-tabs :deep(.el-tabs__content) {
  min-height: 0;
  overflow: auto;
  padding: 20px 0 24px;
}

.execution-tabs :deep(.el-tab-pane) {
  min-height: 100%;
}

.execution-section-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.execution-card,
.execution-detail-card {
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: #ffffff;
  padding: 16px;
}

.execution-card-label {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 600;
  color: #667085;
}

.execution-card-value {
  min-height: 44px;
  padding: 12px 14px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 10px;
  background: #f8fafc;
  font-size: 13px;
  line-height: 1.6;
  color: #344054;
}

.execution-card-value-muted,
.execution-rich-text,
.execution-history-comment {
  white-space: pre-wrap;
  word-break: break-word;
}

.execution-detail-stack {
  display: grid;
  gap: 14px;
}

.execution-detail-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.execution-detail-column,
.execution-detail-card {
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: #ffffff;
  padding: 16px;
}

.execution-rich-text {
  min-height: 84px;
  padding: 14px 16px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 10px;
  background: #f8fafc;
  font-size: 13px;
  line-height: 1.8;
  color: #344054;
}

.execution-rich-text-tall {
  min-height: 216px;
}

.execution-actual-result-input :deep(.el-textarea__inner) {
  min-height: 216px !important;
  border-radius: 10px;
  line-height: 1.7;
}

.execution-note-placeholder {
  min-height: 64px;
}

.execution-note-input :deep(.el-textarea__inner) {
  min-height: 64px !important;
  padding: 14px 16px;
  border: 1px solid #d0d5dd;
  border-radius: 10px;
  background: #ffffff;
  box-shadow: none;
  font-size: 13px;
  line-height: 1.8;
  color: #344054;
  caret-color: #7f56d9;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.execution-note-input :deep(.el-textarea__inner:hover) {
  border-color: #98a2b3;
  background: #ffffff;
}

.execution-note-input :deep(.el-textarea__inner:focus) {
  border-color: #7f56d9;
  background: #ffffff;
  box-shadow: 0 0 0 3px rgba(127, 86, 217, 0.08);
}

.execution-bug-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.execution-bug-actions {
  flex-wrap: wrap;
  justify-content: flex-start;
}

.execution-bug-table {
  margin-top: 12px;
}

.execution-bug-table :deep(.el-table__cell) {
  vertical-align: middle;
}

.execution-bug-table :deep(.execution-bug-table-header .cell),
.execution-bug-table :deep(.execution-bug-table-cell .cell) {
  text-align: left;
}

.execution-bug-action {
  display: flex;
  justify-content: flex-start;
  align-items: center;
}

.execution-bug-action :deep(.el-button) {
  margin-left: 0;
  padding-left: 0;
  padding-right: 0;
  min-width: 0;
}

.execution-bug-empty {
  padding: 20px 0 8px;
}

.execution-evidence-files {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(104px, 104px));
  gap: 10px;
  margin-top: 14px;
  padding-top: 3px;
  max-height: 296px;
  overflow: auto;
  padding-right: 4px;
  align-items: start;
}

.execution-evidence-card {
  outline: none;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.execution-evidence-card.is-drop-active {
  border-color: rgba(64, 158, 255, 0.55);
  background: rgba(239, 246, 255, 0.72);
  box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.12);
}

.execution-evidence-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.execution-evidence-header {
  align-items: flex-start;
}

.execution-evidence-hint,
.execution-evidence-meta,
.execution-evidence-file-meta {
  font-size: 12px;
  line-height: 1.6;
  color: #667085;
}

.execution-evidence-hint {
  margin-top: 4px;
}

.execution-evidence-file {
  position: relative;
  display: grid;
  grid-template-rows: 104px auto;
  gap: 6px;
  width: 104px;
  padding: 0;
  border: none;
  border-radius: 0;
  background: transparent;
  transition: border-color 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
}

.execution-evidence-file:hover {
  transform: translateY(-1px);
}

.execution-evidence-file-remove {
  position: absolute;
  top: 6px;
  right: 6px;
  z-index: 1;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.68);
  color: #fff;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.18s ease, background 0.18s ease;
}

.execution-evidence-file:hover .execution-evidence-file-remove,
.execution-evidence-file:focus-within .execution-evidence-file-remove {
  opacity: 1;
  pointer-events: auto;
}

.execution-evidence-file-remove:disabled {
  cursor: wait;
  opacity: 0.7;
  pointer-events: auto;
}

.execution-evidence-file-preview {
  display: flex;
  min-height: 0;
  width: 104px;
  height: 104px;
}

.execution-evidence-file-fallback,
.execution-evidence-thumb {
  width: 100%;
  height: 100%;
}

.execution-evidence-thumb {
  flex: none;
  padding: 0;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 8px;
  background: #f8fafc;
  overflow: hidden;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.4);
}

.execution-evidence-thumb-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.execution-evidence-file-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed rgba(15, 23, 42, 0.14);
  border-radius: 8px;
  background: linear-gradient(180deg, #f8fafc 0%, #f2f6fb 100%);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
  color: #667085;
}

.execution-evidence-file-trigger {
  display: grid;
  gap: 0;
  min-width: 0;
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
  align-self: end;
}

.execution-evidence-file-name {
  font-size: 11px;
  line-height: 1.45;
  color: #98a2b3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.18s ease;
}

.execution-evidence-file:hover .execution-evidence-file-name,
.execution-evidence-file:focus-within .execution-evidence-file-name {
  color: #667085;
}

.execution-evidence-add {
  display: grid;
  place-items: center;
  gap: 6px;
  width: 104px;
  height: 104px;
  padding: 10px;
  border: 1px dashed #d0d5dd;
  border-radius: 8px;
  background: #fcfcfd;
  color: #667085;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
}

.execution-evidence-add:hover {
  border-color: #98a2b3;
  background: #f8fafc;
  transform: translateY(-1px);
  box-shadow: inset 0 0 0 1px rgba(208, 213, 221, 0.45);
}

.execution-evidence-add:disabled {
  cursor: wait;
  opacity: 0.7;
}

.execution-evidence-add-plus {
  font-size: 28px;
  line-height: 1;
  font-weight: 500;
  color: #475467;
}

.execution-evidence-add-text {
  font-size: 11px;
  line-height: 1.5;
  text-align: center;
  color: #667085;
}

.execution-evidence-preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  min-height: 32px;
}

.execution-evidence-preview-nav,
.execution-evidence-preview-actions {
  display: flex;
  align-items: center;
}

.execution-evidence-preview-nav {
  gap: 8px;
}

.execution-evidence-preview-actions {
  gap: 6px;
  margin-left: auto;
}

.execution-evidence-preview-tip,
.execution-evidence-preview-scale {
  font-size: 12px;
  line-height: 1.5;
  color: #667085;
}

.execution-evidence-preview-counter {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 56px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid rgba(208, 213, 221, 0.9);
  border-radius: 8px;
  background: #f2f4f7;
}

:global(.execution-evidence-preview-dialog .el-button.is-plain) {
  min-width: 32px;
  height: 32px;
  padding: 0 12px;
  border-color: rgba(208, 213, 221, 0.9);
  background: #ffffff;
  color: #475467;
  border-radius: 8px;
}

:global(.execution-evidence-preview-dialog .el-button.is-plain:hover),
:global(.execution-evidence-preview-dialog .el-button.is-plain:focus-visible) {
  border-color: rgba(127, 86, 217, 0.45);
  background: #f8f5ff;
  color: #6941c6;
}

:global(.execution-evidence-preview-dialog .el-button.is-disabled) {
  border-color: rgba(208, 213, 221, 0.7);
  background: #f8fafc;
  color: #98a2b3;
}

:global(.execution-evidence-preview-dialog .execution-evidence-preview-icon-button) {
  min-width: 32px;
  padding: 0 8px;
  font-size: 16px;
  font-weight: 600;
  line-height: 1;
}

.execution-evidence-preview-shell {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  height: min(68vh, 640px);
  max-height: 68vh;
  overflow: hidden;
  background: #f8fafc;
  border-radius: 12px;
}

.execution-evidence-preview-canvas {
  position: relative;
  width: 100%;
  height: 100%;
  max-height: 100%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: zoom-in;
}

.execution-evidence-preview-canvas.is-draggable {
  cursor: grab;
}

.execution-evidence-preview-canvas.is-dragging {
  cursor: grabbing;
}

.execution-evidence-preview-image {
  display: block;
  max-width: min(100%, 1000px);
  max-height: 100%;
  object-fit: contain;
  transform-origin: center center;
  transition: transform 0.16s ease;
  user-select: none;
  -webkit-user-drag: none;
}

:global(.execution-evidence-preview-dialog) {
  margin-top: 6vh;
}

:global(.execution-evidence-preview-dialog .el-dialog) {
  margin: 0 auto 5vh !important;
  max-height: 88vh;
  display: flex;
  flex-direction: column;
}

:global(.execution-evidence-preview-dialog .el-dialog__body) {
  padding-top: 16px;
}

@media (max-width: 960px) {
  .execution-evidence-files {
    grid-template-columns: repeat(auto-fill, minmax(96px, 96px));
  }

  .execution-evidence-file,
  .execution-evidence-add {
    width: 96px;
  }

  .execution-evidence-file {
    grid-template-rows: 96px auto;
  }

  .execution-evidence-file-preview,
  .execution-evidence-add {
    height: 96px;
  }

  .execution-evidence-file-preview {
    width: 96px;
  }

  .execution-evidence-preview-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .execution-evidence-preview-nav {
    justify-content: flex-start;
  }

  .execution-evidence-preview-actions {
    justify-content: flex-end;
    margin-left: 0;
  }

  .execution-evidence-preview-shell {
    min-height: 280px;
    height: min(62vh, 520px);
    max-height: 62vh;
  }

  :global(.execution-evidence-preview-dialog) {
    margin-top: 4vh;
  }

  :global(.execution-evidence-preview-dialog .el-dialog) {
    margin-bottom: 4vh !important;
    max-height: 90vh;
  }
}

.execution-history-list {
  display: grid;
  gap: 12px;
}

.execution-history-item {
  padding: 14px 16px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: #fcfcfd;
}

.execution-history-top {
  align-items: flex-start;
  color: #344054;
  font-size: 13px;
  font-weight: 600;
}

.execution-history-comment {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.7;
  color: #475467;
}

.execution-footer {
  margin-top: auto;
  padding: 16px 24px 22px;
  border-top: 1px solid var(--line-soft);
  background: #ffffff;
  flex: 0 0 auto;
}

.execution-footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.execution-footer-counter {
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
  color: #344054;
}

.execution-auto-next {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #475467;
}

.execution-submit-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
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
  --el-tag-text-color: #b54708;
  --el-tag-bg-color: rgba(247, 144, 9, 0.12);
  --el-tag-border-color: rgba(247, 144, 9, 0.28);
}

.status-tag-pending {
  --el-tag-text-color: #667085;
  --el-tag-bg-color: rgba(102, 112, 133, 0.1);
  --el-tag-border-color: rgba(102, 112, 133, 0.22);
}

@media (max-width: 1280px) {
  .execution-page {
    grid-template-columns: 1fr;
    height: 100%;
    overflow: visible;
  }

  .execution-sidebar-toolbar {
    grid-template-columns: 1fr;
  }

  .execution-section-grid {
    grid-template-columns: 1fr;
  }

  .execution-detail-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .execution-main-header,
  .execution-footer-bar,
  .execution-footer-nav {
    flex-direction: column;
    align-items: stretch;
  }

  .execution-detail-row {
    grid-template-columns: 1fr;
  }

  .case-detail-form {
    grid-template-columns: 1fr;
  }

  .execution-submit-actions {
    justify-content: stretch;
  }

  .execution-footer {
    flex: 0 0 auto;
  }
}
</style>
