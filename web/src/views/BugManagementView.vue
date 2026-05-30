<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, RefreshRight, Search, Setting } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { platformApi } from '../api/platform'
import BugDetailDrawer from '../components/BugDetailDrawer.vue'
import TableSettingsDrawer from '../components/TableSettingsDrawer.vue'
import { useListToolbarState } from '../composables/useListToolbarState'
import type { TableSettingsColumn } from '../composables/useTableSettings'
import { useWorkspace } from '../composables/useWorkspace'
import type { BugDetail, BugStats, BugSummary, CreateBugPayload, UpdateBugPayload, UserItem, WorkspaceItem } from '../types/api'

type BugColumnKey =
  | 'bugNo'
  | 'title'
  | 'status'
  | 'priority'
  | 'severity'
  | 'assigneeName'
  | 'workspaceName'
  | 'tags'
  | 'reporterName'
  | 'createdAt'
  | 'updatedByName'
  | 'updatedAt'
  | 'relatedCaseCount'

type BugColumnSetting = {
  key: BugColumnKey
  label: string
  width: string
  required?: boolean
  allOnly?: boolean
}

type PendingInlineImage = {
  file: File
  src: string
}

const PAGE_SIZE_OPTIONS = [10, 20, 30, 40, 50]

const { workspaceCode, isAllScope } = useWorkspace()
const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const bugs = ref<BugSummary[]>([])
const users = ref<UserItem[]>([])
const workspaces = ref<WorkspaceItem[]>([])
const stats = ref<BugStats>({
  total: 0,
  todo: 0,
  assigned: 0,
  inProgress: 0,
  pendingVerify: 0,
  closed: 0,
  rejected: 0,
})
const detail = ref<BugDetail | null>(null)
const drawerVisible = ref(false)
const detailLoading = ref(false)
const caseBugVisible = ref(false)
const reportBugVisible = ref(false)
const pageNo = ref(1)
const drawerTransitioning = ref(false)
const drawerCommenting = ref(false)
const drawerAttachmentUploading = ref(false)
const drawerAttachmentRemovingId = ref<number | null>(null)
const drawerBasicSaving = ref(false)
const drawerCaseAssociating = ref(false)
const drawerDescriptionSaving = ref(false)
const drawerTitleSaving = ref(false)
const pendingDrawerInlineImages = ref<PendingInlineImage[]>([])

const bugFilters = reactive({
  keyword: '',
  status: '',
  priority: '',
  severity: '',
  assigneeId: null as number | null,
  workspaceCode: '',
})

const bugFilterDefaults = {
  keyword: '',
  status: '',
  priority: '',
  severity: '',
  assigneeId: null as number | null,
  workspaceCode: '',
}

const bugStatusOptions = [
  { label: '已指派', value: 'ASSIGNED' },
  { label: '处理中', value: 'IN_PROGRESS' },
  { label: '待验证', value: 'PENDING_VERIFY' },
  { label: '已关闭', value: 'CLOSED' },
  { label: '已拒绝', value: 'REJECTED' },
]

const priorityOptions = ['P0', 'P1', 'P2', 'P3']
const severityOptions = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

const bugStatusLabelMap = Object.fromEntries(bugStatusOptions.map(item => [item.value, item.label])) as Record<string, string>
const severityLabelMap: Record<string, string> = {
  CRITICAL: '致命',
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
}

const bugColumnSettings: BugColumnSetting[] = [
  { key: 'bugNo', label: '缺陷编号', width: '170px', required: true },
  { key: 'title', label: '缺陷名称', width: 'minmax(260px, 1fr)', required: true },
  { key: 'status', label: '状态', width: '112px', required: true },
  { key: 'priority', label: '优先级', width: '88px' },
  { key: 'severity', label: '严重程度', width: '112px' },
  { key: 'assigneeName', label: '处理人', width: '120px' },
  { key: 'workspaceName', label: '所属空间', width: '132px', allOnly: true },
  { key: 'tags', label: '标签', width: '190px' },
  { key: 'reporterName', label: '创建人', width: '120px' },
  { key: 'createdAt', label: '创建时间', width: '168px' },
  { key: 'updatedByName', label: '更新人', width: '120px' },
  { key: 'updatedAt', label: '更新时间', width: '168px' },
  { key: 'relatedCaseCount', label: '关联用例数', width: '112px' },
]

const defaultVisibleColumns: Record<BugColumnKey, boolean> = {
  bugNo: true,
  title: true,
  status: true,
  priority: true,
  severity: true,
  assigneeName: true,
  workspaceName: false,
  tags: false,
  reporterName: false,
  createdAt: false,
  updatedByName: false,
  updatedAt: false,
  relatedCaseCount: false,
}

const bugTableColumns = computed<TableSettingsColumn[]>(() => bugColumnSettings.map(column => ({
  key: column.key,
  label: column.label,
  required: column.required,
  defaultVisible: defaultVisibleColumns[column.key],
  allOnly: column.allOnly,
})))

const bugListToolbar = useListToolbarState({
  tableSettingsKey: 'bug-table-settings-v2',
  filterStorageKey: 'bug-list-filters-v1',
  columns: bugTableColumns,
  filters: bugFilters,
  filterDefaults: bugFilterDefaults,
  isColumnAvailable: column => !column.allOnly || isAllScope.value,
  pageSizeEnabled: true,
  defaultPageSize: 10,
  pageSizeOptions: PAGE_SIZE_OPTIONS,
})

const pageSize = bugListToolbar.pageSize

const emptyPayload = (): CreateBugPayload & { workspaceCode: string } => ({
  workspaceCode: workspaceCode.value === 'ALL' ? '' : workspaceCode.value,
  title: '',
  description: '',
  priority: 'P1',
  severity: 'HIGH',
  assigneeId: null,
  tags: [],
})

const sourceBugState = reactive<CreateBugPayload & { workspaceCode: string; sourceId: number | null }>({
  ...emptyPayload(),
  sourceId: null,
})

const statusCounts = computed(() => bugs.value.reduce<Record<string, number>>((counts, item) => {
  counts[item.status] = (counts[item.status] ?? 0) + 1
  return counts
}, {}))

const statCards = computed(() => [
  { label: '缺陷总数', value: bugs.value.length, status: '', description: '全部缺陷' },
  { label: '待处理', value: statusCounts.value.ASSIGNED ?? 0, status: 'ASSIGNED', description: '已指派待处理' },
  { label: '处理中', value: statusCounts.value.IN_PROGRESS ?? 0, status: 'IN_PROGRESS', description: '正在处理中' },
  { label: '待验证', value: statusCounts.value.PENDING_VERIFY ?? 0, status: 'PENDING_VERIFY', description: '等待验证结果' },
])

function selectStatCard(status: string) {
  bugFilters.status = status
  pageNo.value = 1
}

function isStatCardActive(status: string) {
  if (status === '') {
    return !bugFilters.status
  }
  return bugFilters.status === status
}

const filteredBugs = computed(() => bugs.value.filter(item => {
  const keyword = bugFilters.keyword.trim().toLowerCase()
  if (keyword) {
    const matched = item.bugNo.toLowerCase().includes(keyword) || item.title.toLowerCase().includes(keyword)
    if (!matched) {
      return false
    }
  }
  if (bugFilters.status && item.status !== bugFilters.status) {
    return false
  }
  if (bugFilters.priority && item.priority !== bugFilters.priority) {
    return false
  }
  if (bugFilters.severity && item.severity !== bugFilters.severity) {
    return false
  }
  if (bugFilters.assigneeId !== null) {
    const user = users.value.find(entry => entry.id === bugFilters.assigneeId)
    if (item.assigneeName !== (user?.displayName ?? '')) {
      return false
    }
  }
  if (isAllScope.value && bugFilters.workspaceCode && item.workspaceCode !== bugFilters.workspaceCode) {
    return false
  }
  return true
}))

const total = computed(() => filteredBugs.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const pagedBugs = computed(() => {
  const start = (pageNo.value - 1) * pageSize.value
  return filteredBugs.value.slice(start, start + pageSize.value)
})
const activeDrawerBugIndex = computed(() => {
  if (!detail.value) {
    return null
  }
  const index = pagedBugs.value.findIndex(item => item.id === detail.value?.id)
  return index >= 0 ? index : null
})

const visibleColumns = computed(() => bugListToolbar.visibleColumns.value
  .map(column => bugColumnSettings.find(item => item.key === column.key))
  .filter((item): item is BugColumnSetting => !!item))

const bugGridTemplateColumns = computed(() => visibleColumns.value.map(item => item.width).join(' '))
const bugGridMinWidth = computed(() => {
  const totalWidth = visibleColumns.value.reduce((sum, item) => {
    if (item.width.endsWith('px')) {
      return sum + Number.parseInt(item.width, 10)
    }
    if (item.width.startsWith('minmax(')) {
      const match = item.width.match(/minmax\((\d+)px/)
      return sum + (match ? Number.parseInt(match[1], 10) : 260)
    }
    return sum + 120
  }, 0)
  return `${totalWidth}px`
})

function formatDateTime(value: string | null | undefined) {
  if (!value) {
    return '-'
  }
  return value.slice(0, 16).replace('T', ' ')
}

function formatStatus(value: string | null | undefined) {
  if (!value) {
    return '-'
  }
  return bugStatusLabelMap[value] ?? value
}

function formatSeverity(value: string | null | undefined) {
  if (!value) {
    return '-'
  }
  return severityLabelMap[value] ?? value
}

function formatColumnValue(row: BugSummary, key: BugColumnKey) {
  switch (key) {
    case 'bugNo':
      return row.bugNo
    case 'title':
      return row.title
    case 'status':
      return formatStatus(row.status)
    case 'priority':
      return row.priority
    case 'severity':
      return formatSeverity(row.severity)
    case 'assigneeName':
      return row.assigneeName || '-'
    case 'workspaceName':
      return row.workspaceName || '-'
    case 'tags':
      return row.tags?.length ? row.tags.join('、') : '-'
    case 'reporterName':
      return row.reporterName || '-'
    case 'createdAt':
      return formatDateTime(row.createdAt)
    case 'updatedByName':
      return row.updatedByName || '-'
    case 'updatedAt':
      return formatDateTime(row.updatedAt)
    case 'relatedCaseCount':
      return String(row.relatedCaseCount ?? 0)
    default:
      return '-'
  }
}

function formatCompactDateTime(value: string | null | undefined) {
  if (!value) {
    return '-'
  }
  return value.slice(5, 16).replace('T', ' ')
}

function rowTitleMeta(row: BugSummary) {
  const meta: string[] = []
  if (isAllScope.value && row.workspaceName) {
    meta.push(row.workspaceName)
  }
  if (row.updatedAt) {
    meta.push(`更新于 ${formatCompactDateTime(row.updatedAt)}`)
  }
  return meta.join(' · ')
}

function statusTone(status: string | null | undefined) {
  switch (status) {
    case 'ASSIGNED':
      return 'assigned'
    case 'IN_PROGRESS':
      return 'processing'
    case 'PENDING_VERIFY':
      return 'verify'
    case 'CLOSED':
      return 'success'
    case 'REJECTED':
      return 'muted'
    default:
      return 'neutral'
  }
}

function priorityTone(priority: string | null | undefined) {
  switch (priority) {
    case 'P0':
      return 'critical'
    case 'P1':
      return 'p1'
    case 'P2':
      return 'p2'
    case 'P3':
      return 'low'
    default:
      return 'neutral'
  }
}

function severityTone(severity: string | null | undefined) {
  switch (severity) {
    case 'CRITICAL':
      return 'critical'
    case 'HIGH':
      return 'high'
    case 'MEDIUM':
      return 'medium'
    case 'LOW':
      return 'low'
    default:
      return 'neutral'
  }
}

function resetFilters() {
  bugListToolbar.filterMemory.reset()
  pageNo.value = 1
}

function updatePageSizeSetting(value: number) {
  bugListToolbar.updatePageSize(value)
  pageNo.value = 1
}

async function loadBaseData() {
  loading.value = true
  try {
    const [bugPage, bugStats, userList, workspaceList] = await Promise.all([
      platformApi.getBugs(workspaceCode.value),
      platformApi.getBugStats(workspaceCode.value),
      platformApi.getUsers(),
      platformApi.getSwitchableWorkspaces(),
    ])
    bugs.value = bugPage.items
    stats.value = bugStats
    users.value = userList
    workspaces.value = workspaceList.filter(item => !item.allScope)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    loading.value = false
  }
}

async function openDetail(id: number) {
  detailLoading.value = true
  drawerVisible.value = true
  try {
    detail.value = await platformApi.getBugDetail(workspaceCode.value, id)
  }
  catch (error) {
    drawerVisible.value = false
    ElMessage.error((error as Error).message)
  }
  finally {
    detailLoading.value = false
  }
}

async function openEditFromRow(id: number) {
  router.push({ path: `/bugs/${id}/edit`, query: { workspace: workspaceCode.value } })
}

function openCreateDialog() {
  router.push({ path: '/bugs/create', query: { workspace: workspaceCode.value } })
}

async function submitDrawerTransition(payload: { status: string, comment: string }) {
  if (!detail.value) {
    return
  }
  drawerTransitioning.value = true
  try {
    detail.value = await platformApi.transitionBug(workspaceCode.value, detail.value.id, payload.status, payload.comment)
    ElMessage.success('状态已更新')
    await loadBaseData()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    drawerTransitioning.value = false
  }
}

async function submitDrawerComment(content: string) {
  if (!detail.value) {
    return
  }
  drawerCommenting.value = true
  try {
    await platformApi.addBugComment(workspaceCode.value, detail.value.id, content)
    detail.value = await platformApi.getBugDetail(workspaceCode.value, detail.value.id)
    ElMessage.success('评论已添加')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    drawerCommenting.value = false
  }
}

async function navigateDrawerBug(step: -1 | 1) {
  const currentIndex = activeDrawerBugIndex.value
  if (currentIndex === null) {
    return
  }
  const target = pagedBugs.value[currentIndex + step]
  if (!target) {
    return
  }
  await openDetail(target.id)
}

function addPendingDrawerInlineImage(payload: PendingInlineImage) {
  pendingDrawerInlineImages.value = [...pendingDrawerInlineImages.value, payload]
}

function clearPendingDrawerInlineImages() {
  pendingDrawerInlineImages.value.forEach((item) => {
    URL.revokeObjectURL(item.src)
  })
  pendingDrawerInlineImages.value = []
}

async function uploadPendingDrawerInlineImages(workspaceCode: string, bugId: number, html: string) {
  const parser = new DOMParser()
  const doc = parser.parseFromString(`<div>${html}</div>`, 'text/html')
  const container = doc.body.firstElementChild as HTMLElement | null
  if (!container) {
    clearPendingDrawerInlineImages()
    return html
  }
  const unresolvedImages = Array.from(container.querySelectorAll('img')) as HTMLImageElement[]
  const consumedImages = new Set<HTMLImageElement>()
  for (const item of pendingDrawerInlineImages.value) {
    const exactMatches = Array.from(
      container.querySelectorAll(`img[src="${item.src.replaceAll('"', '&quot;')}"]`),
    ) as HTMLImageElement[]
    const fallbackMatch = unresolvedImages.find((image) => {
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
  pendingDrawerInlineImages.value = []
  return container.innerHTML
}

async function saveDrawerDescription(content: string) {
  if (!detail.value) {
    return
  }
  drawerDescriptionSaving.value = true
  try {
    const description = await uploadPendingDrawerInlineImages(
      detail.value.workspaceCode,
      detail.value.id,
      content,
    )
    detail.value = await platformApi.updateBug(detail.value.workspaceCode, detail.value.id, {
      workspaceCode: detail.value.workspaceCode,
      title: detail.value.title,
      description,
      priority: detail.value.priority,
      severity: detail.value.severity,
      assigneeId: detail.value.assigneeId,
      relatedCaseId: detail.value.relatedCaseId,
      tags: detail.value.tags,
    })
    ElMessage.success('缺陷描述已更新')
    await loadBaseData()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    drawerDescriptionSaving.value = false
  }
}

async function saveDrawerTitle(title: string) {
  if (!detail.value) {
    return
  }
  drawerTitleSaving.value = true
  try {
    detail.value = await platformApi.updateBug(detail.value.workspaceCode, detail.value.id, {
      workspaceCode: detail.value.workspaceCode,
      title,
      description: detail.value.description,
      priority: detail.value.priority,
      severity: detail.value.severity,
      assigneeId: detail.value.assigneeId,
      relatedCaseId: detail.value.relatedCaseId,
      tags: detail.value.tags,
    })
    ElMessage.success('缺陷标题已更新')
    await loadBaseData()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    drawerTitleSaving.value = false
  }
}

async function saveDrawerBasic(payload: UpdateBugPayload) {
  if (!detail.value) {
    return
  }
  drawerBasicSaving.value = true
  try {
    detail.value = await platformApi.updateBug(detail.value.workspaceCode, detail.value.id, {
      workspaceCode: detail.value.workspaceCode,
      title: detail.value.title,
      description: detail.value.description,
      priority: payload.priority,
      severity: payload.severity,
      assigneeId: payload.assigneeId,
      relatedCaseId: detail.value.relatedCaseId,
      tags: payload.tags,
    })
    ElMessage.success('基本信息已更新')
    await loadBaseData()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    drawerBasicSaving.value = false
  }
}

async function associateDrawerCase(caseId: number) {
  if (!detail.value) {
    return
  }
  drawerCaseAssociating.value = true
  try {
    detail.value = await platformApi.updateBug(detail.value.workspaceCode, detail.value.id, {
      workspaceCode: detail.value.workspaceCode,
      title: detail.value.title,
      description: detail.value.description,
      priority: detail.value.priority,
      severity: detail.value.severity,
      assigneeId: detail.value.assigneeId,
      relatedCaseId: caseId,
      tags: detail.value.tags,
    })
    ElMessage.success('关联用例已更新')
    await loadBaseData()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    drawerCaseAssociating.value = false
  }
}

async function unlinkDrawerCase() {
  if (!detail.value) {
    return
  }
  drawerCaseAssociating.value = true
  try {
    detail.value = await platformApi.updateBug(detail.value.workspaceCode, detail.value.id, {
      workspaceCode: detail.value.workspaceCode,
      title: detail.value.title,
      description: detail.value.description,
      priority: detail.value.priority,
      severity: detail.value.severity,
      assigneeId: detail.value.assigneeId,
      relatedCaseId: null,
      tags: detail.value.tags,
    })
    ElMessage.success('已取消关联用例')
    await loadBaseData()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    drawerCaseAssociating.value = false
  }
}

function editBugFromDrawer() {
  if (!detail.value) {
    return
  }
  drawerVisible.value = false
  void router.push({ path: `/bugs/${detail.value.id}/edit`, query: { workspace: workspaceCode.value } })
}

async function uploadDrawerAttachments(files: File[]) {
  if (!detail.value || !files.length) {
    return
  }
  drawerAttachmentUploading.value = true
  try {
    detail.value = await platformApi.uploadBugAttachment(workspaceCode.value, detail.value.id, files)
      .then(() => platformApi.getBugDetail(workspaceCode.value, detail.value!.id))
    ElMessage.success('附件已上传')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    drawerAttachmentUploading.value = false
  }
}

async function downloadDrawerAttachment(attachmentId: number) {
  if (!detail.value) {
    return
  }
  const attachment = detail.value.attachments.find(item => item.id === attachmentId)
  if (!attachment) {
    return
  }
  try {
    await platformApi.downloadBugAttachment(workspaceCode.value, detail.value.id, attachmentId, attachment.fileName)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function removeDrawerAttachment(attachmentId: number) {
  if (!detail.value) {
    return
  }
  drawerAttachmentRemovingId.value = attachmentId
  try {
    await platformApi.deleteBugAttachment(workspaceCode.value, detail.value.id, attachmentId)
    detail.value = await platformApi.getBugDetail(workspaceCode.value, detail.value.id)
    ElMessage.success('附件已删除')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    drawerAttachmentRemovingId.value = null
  }
}

async function submitCaseBug() {
  if (!sourceBugState.sourceId) {
    return
  }
  if (sourceBugState.assigneeId === null) {
    ElMessage.warning('请选择处理人')
    return
  }
  saving.value = true
  try {
    await platformApi.createBugFromCase(workspaceCode.value, sourceBugState.sourceId, {
      workspaceCode: isAllScope.value ? sourceBugState.workspaceCode : undefined,
      title: sourceBugState.title,
      description: sourceBugState.description,
      priority: sourceBugState.priority,
      severity: sourceBugState.severity,
      assigneeId: sourceBugState.assigneeId,
      tags: sourceBugState.tags,
    })
    caseBugVisible.value = false
    ElMessage.success('已从用例创建缺陷')
    await loadBaseData()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function submitReportBug() {
  if (!sourceBugState.sourceId) {
    return
  }
  if (sourceBugState.assigneeId === null) {
    ElMessage.warning('请选择处理人')
    return
  }
  saving.value = true
  try {
    await platformApi.createBugFromReport(workspaceCode.value, sourceBugState.sourceId, {
      workspaceCode: isAllScope.value ? sourceBugState.workspaceCode : undefined,
      title: sourceBugState.title,
      description: sourceBugState.description,
      priority: sourceBugState.priority,
      severity: sourceBugState.severity,
      assigneeId: sourceBugState.assigneeId,
      tags: sourceBugState.tags,
    })
    reportBugVisible.value = false
    ElMessage.success('已从报告创建缺陷')
    await loadBaseData()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

watch(workspaceCode, () => {
  pageNo.value = 1
  loadBaseData()
})

watch(drawerVisible, (visible) => {
  if (!visible) {
    clearPendingDrawerInlineImages()
  }
})

watch(filteredBugs, () => {
  if (pageNo.value > totalPages.value) {
    pageNo.value = totalPages.value
  }
  if (pageNo.value < 1) {
    pageNo.value = 1
  }
})

onMounted(() => {
  bugListToolbar.load()
  loadBaseData()
})
</script>

<template>
  <section class="page-shell bug-page-shell">
    <div class="stats-grid bug-stats-grid">
      <article
        v-for="item in statCards"
        :key="item.label"
        :class="[
          'metric-card',
          'bug-stat-card',
          `bug-stat-card-${statusTone(item.status)}`,
          { 'bug-stat-card-active': isStatCardActive(item.status) },
        ]"
        role="button"
        tabindex="0"
        @click="selectStatCard(item.status)"
        @keydown.enter.prevent="selectStatCard(item.status)"
        @keydown.space.prevent="selectStatCard(item.status)"
      >
        <div class="bug-stat-head">
          <div class="metric-label">{{ item.label }}</div>
          <span :class="['bug-stat-dot', `bug-stat-dot-${statusTone(item.status)}`]" />
        </div>
        <div class="metric-value">{{ item.value }}</div>
        <div class="metric-trend">{{ item.description }}</div>
      </article>
    </div>

    <article class="panel-card bug-list-panel">
      <div class="bug-toolbar-row">
        <div class="bug-toolbar-left">
          <el-input
            v-model="bugFilters.keyword"
            placeholder="搜索缺陷编号、说明..."
            :prefix-icon="Search"
            clearable
            class="bug-filter-search"
          />
          <el-select v-model="bugFilters.status" clearable :value-on-clear="''" placeholder="状态" class="bug-filter-select">
            <el-option v-for="item in bugStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="bugFilters.priority" clearable placeholder="优先级" class="bug-filter-select">
            <el-option v-for="item in priorityOptions" :key="item" :label="item" :value="item" />
          </el-select>
          <el-select v-model="bugFilters.severity" clearable placeholder="严重程度" class="bug-filter-select">
            <el-option v-for="item in severityOptions" :key="item" :label="formatSeverity(item)" :value="item" />
          </el-select>
          <el-select v-model="bugFilters.assigneeId" clearable placeholder="处理人" class="bug-filter-select">
            <el-option v-for="item in users" :key="item.id" :label="item.displayName" :value="item.id" />
          </el-select>
          <el-select
            v-if="isAllScope"
            v-model="bugFilters.workspaceCode"
            clearable
            placeholder="所属空间"
            class="bug-filter-select"
          >
            <el-option v-for="item in workspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
          <el-button class="bug-filter-reset" @click="resetFilters">
            <el-icon><RefreshRight /></el-icon>
            重置
          </el-button>
        </div>
        <div class="bug-toolbar-right">
          <el-button type="primary" class="bug-create-button" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            新建缺陷
          </el-button>
        </div>
      </div>

      <div v-loading="loading" class="bug-table-shell">
        <div class="bug-table-data">
          <div class="bug-table-scroll">
            <div class="bug-grid bug-grid-header" :style="{ gridTemplateColumns: bugGridTemplateColumns, minWidth: bugGridMinWidth }">
              <div
                v-for="column in visibleColumns"
                :key="'header-' + column.key"
                :class="['bug-cell', 'bug-cell-' + column.key]"
              >
                {{ column.label }}
              </div>
            </div>

            <template v-if="pagedBugs.length">
              <div
                v-for="row in pagedBugs"
                :key="row.id"
                class="bug-grid bug-grid-row"
                :style="{ gridTemplateColumns: bugGridTemplateColumns, minWidth: bugGridMinWidth }"
              >
                <div
                  v-for="column in visibleColumns"
                  :key="row.id + '-' + column.key"
                  :class="['bug-cell', 'bug-cell-' + column.key]"
                >
                  <el-button
                    v-if="column.key === 'bugNo'"
                    text
                    type="primary"
                    class="bug-no-trigger"
                    @click="openDetail(row.id)"
                  >
                    {{ formatColumnValue(row, column.key) }}
                  </el-button>

                  <div v-else-if="column.key === 'title'" class="bug-title-cell">
                    <el-tooltip :content="row.title" placement="top">
                      <span class="bug-title-text">{{ row.title }}</span>
                    </el-tooltip>
                    <span v-if="rowTitleMeta(row)" class="bug-title-meta">{{ rowTitleMeta(row) }}</span>
                  </div>

                  <span
                    v-else-if="column.key === 'status'"
                    :class="['bug-status-pill', `bug-status-pill-${statusTone(row.status)}`]"
                  >
                    {{ formatColumnValue(row, column.key) }}
                  </span>

                  <span
                    v-else-if="column.key === 'priority'"
                    :class="['bug-badge', `bug-badge-${priorityTone(row.priority)}`]"
                  >
                    {{ formatColumnValue(row, column.key) }}
                  </span>

                  <span
                    v-else-if="column.key === 'severity'"
                    :class="['bug-badge', `bug-badge-${severityTone(row.severity)}`]"
                  >
                    {{ formatColumnValue(row, column.key) }}
                  </span>

                  <el-tooltip
                    v-else-if="column.key === 'tags'"
                    :content="formatColumnValue(row, column.key)"
                    placement="top"
                  >
                    <span class="bug-cell-text">{{ formatColumnValue(row, column.key) }}</span>
                  </el-tooltip>

                  <span v-else class="bug-cell-text">{{ formatColumnValue(row, column.key) }}</span>
                </div>
              </div>
            </template>

            <div v-else class="bug-table-empty">
              当前筛选条件下暂无缺陷记录
            </div>
          </div>
        </div>

        <div class="bug-table-actions">
          <div class="bug-actions-header">
            <span>操作</span>
            <el-button text class="bug-table-settings-trigger" @click="bugListToolbar.settingsVisible.value = true">
              <el-icon><Setting /></el-icon>
            </el-button>
          </div>
          <template v-if="pagedBugs.length">
            <div
              v-for="row in pagedBugs"
              :key="'action-' + row.id"
              class="bug-actions-row"
            >
              <div class="row-actions">
                <el-button text type="primary" size="small" @click="openDetail(row.id)">查看</el-button>
                <el-button text type="primary" size="small" @click="openEditFromRow(row.id)">编辑</el-button>
              </div>
            </div>
          </template>
          <div v-else class="bug-actions-empty">-</div>
        </div>
      </div>

      <div class="table-pagination">
        <div class="table-pagination-summary">
          共 {{ total }} 条 / {{ totalPages }} 页
        </div>
        <div class="table-pagination-right">
          <el-pagination
            v-model:current-page="pageNo"
            v-model:page-size="pageSize"
            :page-sizes="PAGE_SIZE_OPTIONS"
            :pager-count="7"
            size="small"
            layout="sizes, prev, pager, next, jumper"
            :total="total"
            @size-change="updatePageSizeSetting"
          />
        </div>
      </div>
    </article>

    <TableSettingsDrawer
      v-model="bugListToolbar.settingsVisible.value"
      :columns="bugListToolbar.drawerColumns.value"
      :page-size-enabled="true"
      :page-size="bugListToolbar.pageSizeDisplay.value"
      :page-size-options="PAGE_SIZE_OPTIONS"
      :dragging-key="bugListToolbar.draggingColumnKey.value"
      @page-size-change="updatePageSizeSetting"
      @toggle-column="bugListToolbar.toggleColumnVisibility"
      @drag-start="bugListToolbar.handleDragStart"
      @drag-end="bugListToolbar.handleDragEnd"
      @drop-column="bugListToolbar.moveColumnToTarget"
      @reset="bugListToolbar.reset"
    />

    <BugDetailDrawer
      v-model="drawerVisible"
      :detail="detail"
      default-tab="detail"
      :users="users"
      :loading="detailLoading"
      :current-index="activeDrawerBugIndex"
      :total-count="pagedBugs.length"
      :title-saving="drawerTitleSaving"
      :basic-saving="drawerBasicSaving"
      :description-saving="drawerDescriptionSaving"
      :associating-case="drawerCaseAssociating"
      :transitioning="drawerTransitioning"
      :commenting="drawerCommenting"
      :attachment-uploading="drawerAttachmentUploading"
      :attachment-removing-id="drawerAttachmentRemovingId"
      :can-write="true"
      @save-basic="saveDrawerBasic"
      @save-title="saveDrawerTitle"
      @add-inline-image="addPendingDrawerInlineImage"
      @save-description="saveDrawerDescription"
      @navigate-prev="navigateDrawerBug(-1)"
      @navigate-next="navigateDrawerBug(1)"
      @associate-case="associateDrawerCase"
      @unlink-case="unlinkDrawerCase"
      @transition="submitDrawerTransition"
      @comment="submitDrawerComment"
      @edit="editBugFromDrawer"
      @upload-attachments="uploadDrawerAttachments"
      @download-attachment="downloadDrawerAttachment"
      @remove-attachment="removeDrawerAttachment"
    />

    <el-dialog v-model="caseBugVisible" title="从用例创建缺陷" width="620px">
      <el-form label-width="90px">
        <el-form-item v-if="isAllScope" label="所属空间" required>
          <el-select v-model="sourceBugState.workspaceCode">
            <el-option v-for="item in workspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="缺陷名称"><el-input v-model="sourceBugState.title" /></el-form-item>
        <el-form-item label="缺陷描述"><el-input v-model="sourceBugState.description" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="处理人" required>
          <el-select v-model="sourceBugState.assigneeId" placeholder="请选择">
            <el-option v-for="item in users" :key="item.id" :label="item.displayName" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="caseBugVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCaseBug">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reportBugVisible" title="从报告创建缺陷" width="620px">
      <el-form label-width="90px">
        <el-form-item v-if="isAllScope" label="所属空间" required>
          <el-select v-model="sourceBugState.workspaceCode">
            <el-option v-for="item in workspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="缺陷名称"><el-input v-model="sourceBugState.title" /></el-form-item>
        <el-form-item label="缺陷描述"><el-input v-model="sourceBugState.description" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="处理人" required>
          <el-select v-model="sourceBugState.assigneeId" placeholder="请选择">
            <el-option v-for="item in users" :key="item.id" :label="item.displayName" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportBugVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReportBug">提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.bug-page-shell {
  gap: 32px;
}

.bug-create-button {
  height: 36px;
  padding: 0 16px;
  border-radius: 8px;
  border: 0;
  box-shadow: none;
}

.detail-body :deep(img) {
  display: block;
  max-width: 100%;
  max-height: 420px;
  margin: 10px 0 14px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  object-fit: contain;
}

.bug-stats-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 24px;
}

.bug-stat-card {
  cursor: pointer;
  min-width: 0;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
  box-shadow: none;
  transition: all 150ms ease;
}

.bug-stat-card:hover,
.bug-stat-card:focus-visible {
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
}

.bug-stat-card-active {
  border-color: #d1d5db;
}

.bug-stat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.bug-stat-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  flex: 0 0 auto;
}

.bug-stat-dot-neutral {
  background: #3b82f6;
}

.bug-stat-dot-assigned {
  background: #a855f7;
}

.bug-stat-dot-processing {
  background: #22c55e;
}

.bug-stat-dot-verify {
  background: #f97316;
}

.bug-stat-dot-success {
  background: #22c55e;
}

.bug-stat-dot-muted {
  background: #94a3b8;
}

.bug-list-panel {
  padding: 0;
  overflow: hidden;
  margin-bottom: 24px;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  background: #fff;
  box-shadow: none;
}

.bug-toolbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 24px;
  border-bottom: 1px solid #e5e7eb;
}

.bug-toolbar-left,
.bug-toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.bug-toolbar-left {
  min-width: 0;
  flex: 1 1 auto;
  flex-wrap: wrap;
}

.bug-toolbar-right {
  flex: 0 0 auto;
  margin-left: auto;
}

.bug-filter-search {
  width: 224px;
}

.bug-filter-select {
  width: 136px;
}

.bug-filter-reset {
  height: 36px;
  padding: 0 14px;
  border-color: #d1d5db;
  border-radius: 8px;
  color: #374151;
}

.bug-table-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 150px;
  width: 100%;
  min-height: 0;
  border-top: 0;
  background: #fff;
}

.bug-table-data {
  min-width: 0;
  overflow: hidden;
}

.bug-table-scroll {
  height: 100%;
  overflow-x: auto;
  overflow-y: hidden;
}

.bug-grid {
  display: grid;
}

.bug-grid-header {
  min-height: 44px;
  border-bottom: 1px solid #e5e7eb;
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
  background: #f9fafb;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.bug-grid-header .bug-cell {
  padding: 12px 24px;
}

.bug-grid-row {
  height: 74px;
  min-height: 74px;
  border-bottom: 1px solid #e5e7eb;
  font-size: 14px;
  background: #fff;
  transition: background 0.16s ease;
}

.bug-grid-row:hover {
  background: #f9fafb;
}

.bug-cell {
  display: flex;
  align-items: center;
  padding: 16px 24px;
  min-width: 0;
}

.bug-cell-text {
  display: block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #374151;
}

.bug-no-trigger {
  padding: 0;
  min-width: 0;
  font-size: 14px;
  font-weight: 500;
}

.bug-title-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 3px;
  width: 100%;
  min-width: 0;
}

.bug-title-text {
  display: block;
  width: 100%;
  max-width: 320px;
  overflow: hidden;
  color: #111827;
  font-weight: 400;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bug-title-meta {
  display: block;
  width: 100%;
  overflow: hidden;
  color: #94a3b8;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bug-status-pill,
.bug-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  font-size: 12px;
  font-weight: 500;
  line-height: 16px;
  white-space: nowrap;
}

.bug-status-pill {
  min-height: 22px;
  border-radius: 9999px;
  padding: 2px 10px;
}

.bug-badge {
  min-height: 20px;
  border-radius: 4px;
  padding: 2px 8px;
}

.bug-status-pill-assigned {
  border: 1px solid #bfdbfe;
  background: #eff6ff;
  color: #1d4ed8;
}

.bug-status-pill-processing {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #15803d;
}

.bug-status-pill-verify {
  border: 1px solid #fed7aa;
  background: #fff7ed;
  color: #c2410c;
}

.bug-status-pill-success {
  border: 1px solid #bbf7d0;
  background: #f0fdf4;
  color: #047857;
}

.bug-status-pill-muted {
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #64748b;
}

.bug-status-pill-neutral,
.bug-badge-neutral {
  background: #f1f5f9;
  color: #64748b;
}

.bug-badge-critical {
  background: #fee2e2;
  color: #b91c1c;
}

.bug-badge-p1 {
  background: #fee2e2;
  color: #b91c1c;
}

.bug-badge-p2 {
  background: #fef9c3;
  color: #a16207;
}

.bug-badge-high {
  background: #fef9c3;
  color: #a16207;
}

.bug-badge-medium {
  background: #f3f4f6;
  color: #374151;
}

.bug-badge-low {
  background: #f3f4f6;
  color: #374151;
}

.bug-table-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  color: #94a3b8;
  font-size: 14px;
}

.bug-table-actions {
  display: flex;
  flex-direction: column;
  position: sticky;
  right: 0;
  width: 150px;
  min-width: 150px;
  border-left: 1px solid #e5e7eb;
  background: #fff;
}

.bug-actions-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 44px;
  padding: 0 8px;
  border-bottom: 1px solid #e5e7eb;
  color: #6b7280;
  font-size: 12px;
  font-weight: 600;
  background: #f9fafb;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  white-space: nowrap;
}

.bug-table-settings-trigger {
  width: 18px;
  height: 18px;
  min-width: 18px;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #94a3b8;
  box-shadow: none;
  transition: background-color 0.16s ease, color 0.16s ease;
}

.bug-table-settings-trigger:hover,
.bug-table-settings-trigger:focus-visible {
  background: rgba(148, 163, 184, 0.14);
  color: #475569;
}

.bug-table-settings-trigger:deep(.el-icon) {
  font-size: 12px;
}

.bug-actions-row,
.bug-actions-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 74px;
  min-height: 74px;
  padding: 0 6px;
  border-bottom: 1px solid #e5e7eb;
}

.bug-actions-empty {
  color: #cbd5e1;
}

.row-actions {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}

.row-actions :deep(.el-button) {
  font-size: 14px;
  font-weight: 500;
}

.table-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #e5e7eb;
}

.table-pagination-summary {
  color: #374151;
  font-size: 14px;
  line-height: 1.5;
}

.table-pagination-right {
  display: inline-flex;
  align-items: center;
  min-width: 0;
}

.bug-table-scroll::-webkit-scrollbar {
  height: 8px;
}

.bug-table-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.bug-table-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.44);
}

.bug-table-scroll::-webkit-scrollbar-thumb:hover {
  background: rgba(120, 134, 156, 0.72);
}

:deep(.table-pagination .el-pagination) {
  flex-wrap: nowrap;
  white-space: nowrap;
}

:deep(.bug-filter-search .el-input__wrapper),
:deep(.bug-filter-select .el-select__wrapper) {
  min-height: 36px;
  border-radius: 8px;
  box-shadow: 0 0 0 1px #d1d5db inset;
}

:deep(.bug-filter-search .el-input__wrapper) {
  padding-left: 12px;
}

:deep(.bug-filter-search .el-input__prefix) {
  color: #9ca3af;
}

:deep(.bug-create-button.el-button) {
  background: #2563eb;
}

:deep(.bug-create-button.el-button:hover) {
  background: #1d4ed8;
}

@media (max-width: 1280px) {
  .bug-stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .bug-toolbar-left {
    width: 100%;
  }
}

@media (max-width: 960px) {
  .table-pagination {
    flex-direction: column;
    align-items: stretch;
  }

  .bug-toolbar-row,
  .table-pagination-right {
    flex-direction: column;
    align-items: stretch;
  }

  .bug-toolbar-left,
  .bug-toolbar-right {
    width: 100%;
  }
}

@media (max-width: 720px) {
  .bug-stats-grid {
    grid-template-columns: 1fr;
  }

  .bug-toolbar-row {
    padding-left: 14px;
    padding-right: 14px;
  }

  .table-pagination {
    padding-left: 14px;
    padding-right: 14px;
  }

  .bug-table-shell {
    grid-template-columns: minmax(0, 1fr);
  }

  .bug-table-actions {
    display: none;
  }

  .bug-filter-select,
  .bug-filter-search,
  .bug-toolbar-right :deep(.el-button),
  .bug-filter-reset {
    width: 100%;
  }
}
</style>
