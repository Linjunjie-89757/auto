<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Plus, Promotion, RefreshRight, Setting, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { platformApi } from '../api/platform'
import ListToolbar from '../components/ListToolbar.vue'
import TableSettingsDrawer from '../components/TableSettingsDrawer.vue'
import { useListToolbarState } from '../composables/useListToolbarState'
import type { TableSettingsColumn } from '../composables/useTableSettings'
import { useWorkspace } from '../composables/useWorkspace'
import type { BugDetail, BugStats, BugSummary, CreateBugPayload, UserItem, WorkspaceItem } from '../types/api'

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

const PAGE_SIZE_OPTIONS = [10, 20, 30, 40, 50]

const { workspaceCode, isAllScope } = useWorkspace()

const loading = ref(false)
const saving = ref(false)
const bugs = ref<BugSummary[]>([])
const users = ref<UserItem[]>([])
const workspaces = ref<WorkspaceItem[]>([])
const stats = ref<BugStats>({
  total: 0,
  todo: 0,
  inProgress: 0,
  pendingVerify: 0,
  closed: 0,
  rejected: 0,
})
const detail = ref<BugDetail | null>(null)
const drawerVisible = ref(false)
const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const caseBugVisible = ref(false)
const reportBugVisible = ref(false)
const commentText = ref('')
const transitionStatus = ref('')
const transitionComment = ref('')
const assignUserId = ref<number | null>(null)
const pageNo = ref(1)

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
  { label: '待处理', value: 'TODO' },
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
  { key: 'severity', label: '严重程度', width: '96px' },
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

const formState = reactive<CreateBugPayload & { workspaceCode: string }>(emptyPayload())
const sourceBugState = reactive<CreateBugPayload & { workspaceCode: string; sourceId: number | null }>({
  ...emptyPayload(),
  sourceId: null,
})

const statCards = computed(() => [
  { label: '缺陷总数', value: stats.value.total },
  { label: '待处理', value: stats.value.todo },
  { label: '处理中', value: stats.value.inProgress },
  { label: '待验证', value: stats.value.pendingVerify },
])

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
  try {
    detail.value = await platformApi.getBugDetail(workspaceCode.value, id)
    assignUserId.value = detail.value.assigneeId
    drawerVisible.value = true
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function openEditFromRow(id: number) {
  await openDetail(id)
  openEditDialog()
}

function openCreateDialog() {
  formMode.value = 'create'
  Object.assign(formState, emptyPayload())
  formVisible.value = true
}

function openEditDialog() {
  if (!detail.value) {
    return
  }
  formMode.value = 'edit'
  Object.assign(formState, {
    workspaceCode: detail.value.workspaceCode,
    title: detail.value.title,
    description: detail.value.description,
    priority: detail.value.priority,
    severity: detail.value.severity,
    assigneeId: detail.value.assigneeId,
    tags: [...detail.value.tags],
  })
  formVisible.value = true
}

async function submitBug() {
  if (isAllScope.value && !formState.workspaceCode) {
    ElMessage.error('全部空间视角下请先选择目标空间')
    return
  }
  saving.value = true
  try {
    const payload: CreateBugPayload = {
      workspaceCode: isAllScope.value ? formState.workspaceCode : undefined,
      title: formState.title,
      description: formState.description,
      priority: formState.priority,
      severity: formState.severity,
      assigneeId: formState.assigneeId,
      tags: formState.tags,
    }
    if (formMode.value === 'create') {
      await platformApi.createBug(workspaceCode.value, payload)
      ElMessage.success('缺陷创建成功')
    }
    else if (detail.value) {
      await platformApi.updateBug(workspaceCode.value, detail.value.id, payload)
      ElMessage.success('缺陷更新成功')
      await openDetail(detail.value.id)
    }
    formVisible.value = false
    await loadBaseData()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function submitAssign() {
  if (!detail.value || !assignUserId.value) {
    return
  }
  try {
    detail.value = await platformApi.assignBug(workspaceCode.value, detail.value.id, assignUserId.value)
    ElMessage.success('处理人已更新')
    await loadBaseData()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function submitTransition() {
  if (!detail.value || !transitionStatus.value) {
    return
  }
  try {
    detail.value = await platformApi.transitionBug(workspaceCode.value, detail.value.id, transitionStatus.value, transitionComment.value)
    transitionStatus.value = ''
    transitionComment.value = ''
    ElMessage.success('状态已更新')
    await loadBaseData()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function submitComment() {
  if (!detail.value || !commentText.value.trim()) {
    return
  }
  try {
    await platformApi.addBugComment(workspaceCode.value, detail.value.id, commentText.value)
    commentText.value = ''
    detail.value = await platformApi.getBugDetail(workspaceCode.value, detail.value.id)
    ElMessage.success('评论已添加')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function submitCaseBug() {
  if (!sourceBugState.sourceId) {
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
  Object.assign(formState, emptyPayload())
  pageNo.value = 1
  loadBaseData()
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
  <section class="page-shell">
    <div class="page-header">
      <div class="page-title">缺陷管理</div>
    </div>

    <div class="stats-grid">
      <article v-for="item in statCards" :key="item.label" class="metric-card">
        <div class="metric-label">{{ item.label }}</div>
        <div class="metric-value">{{ item.value }}</div>
        <div class="metric-trend">{{ isAllScope ? '全部空间汇总视角' : '当前空间缺陷概览' }}</div>
      </article>
    </div>

    <article class="panel-card">
      <div class="panel-header panel-header-bug-list">
        <ListToolbar title="缺陷列表">
          <template #filters>
            <el-input
              v-model="bugFilters.keyword"
              placeholder="搜索编号或名称"
              clearable
              class="toolbar-filter-input"
            />
            <el-select v-model="bugFilters.status" clearable placeholder="状态" class="toolbar-filter-select">
              <el-option v-for="item in bugStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="bugFilters.priority" clearable placeholder="优先级" class="toolbar-filter-select">
              <el-option v-for="item in priorityOptions" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="bugFilters.severity" clearable placeholder="严重程度" class="toolbar-filter-select">
              <el-option v-for="item in severityOptions" :key="item" :label="formatSeverity(item)" :value="item" />
            </el-select>
            <el-select v-model="bugFilters.assigneeId" clearable placeholder="处理人" class="toolbar-filter-select">
              <el-option v-for="item in users" :key="item.id" :label="item.displayName" :value="item.id" />
            </el-select>
            <el-select
              v-if="isAllScope"
              v-model="bugFilters.workspaceCode"
              clearable
              placeholder="所属空间"
              class="toolbar-filter-select"
            >
              <el-option v-for="item in workspaces" :key="item.code" :label="item.name" :value="item.code" />
            </el-select>
            <el-button text @click="resetFilters">
              <el-icon><RefreshRight /></el-icon>
              重置
            </el-button>
          </template>
        </ListToolbar>
        <el-button type="primary" class="bug-create-button" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          新建缺陷
        </el-button>
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
                <el-tooltip
                  v-if="column.key === 'title' || column.key === 'tags'"
                  :content="formatColumnValue(row, column.key)"
                  placement="top"
                >
                  <span class="bug-cell-text">{{ formatColumnValue(row, column.key) }}</span>
                </el-tooltip>
                <template v-else-if="column.key === 'status'">
                  <el-tag effect="plain">{{ formatColumnValue(row, column.key) }}</el-tag>
                </template>
                <template v-else>
                  <span class="bug-cell-text">{{ formatColumnValue(row, column.key) }}</span>
                </template>
              </div>
            </div>
          </div>
        </div>

        <div class="bug-table-actions">
          <div class="bug-actions-header">
            <div class="bug-actions-header-title">
              <span>操作</span>
              <el-button text class="table-settings-trigger" @click="bugListToolbar.settingsVisible.value = true">
                <el-icon><Setting /></el-icon>
              </el-button>
            </div>
          </div>
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
        </div>
      </div>

      <div class="table-pagination">
        <div class="table-pagination-left" />
        <div class="table-pagination-right">
          <div class="table-pagination-summary">共 {{ total }} 条 / {{ totalPages }} 页</div>
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

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新建缺陷' : '编辑缺陷'" width="640px">
      <el-form label-width="90px">
        <el-form-item v-if="isAllScope" label="所属空间" required>
          <el-select v-model="formState.workspaceCode" placeholder="请选择所属空间">
            <el-option v-for="item in workspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="缺陷名称" required>
          <el-input v-model="formState.title" />
        </el-form-item>
        <el-form-item label="缺陷描述" required>
          <el-input v-model="formState.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="formState.priority">
            <el-option v-for="item in priorityOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重程度">
          <el-select v-model="formState.severity">
            <el-option v-for="item in severityOptions" :key="item" :label="formatSeverity(item)" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理人">
          <el-select v-model="formState.assigneeId" clearable>
            <el-option v-for="item in users" :key="item.id" :label="item.displayName" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitBug">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawerVisible" title="缺陷详情" size="720px">
      <template v-if="detail">
        <div class="detail-grid">
          <div class="detail-card">
            <div class="detail-title">{{ detail.title }}</div>
            <div class="detail-meta">{{ detail.bugNo }} | {{ detail.workspaceName }} | {{ formatStatus(detail.status) }}</div>
            <p class="detail-body">{{ detail.description }}</p>
          </div>

          <div class="detail-card">
            <div class="detail-title">调整处理人</div>
            <div class="inline-form">
              <el-select v-model="assignUserId" placeholder="请选择处理人">
                <el-option v-for="user in users" :key="user.id" :label="user.displayName" :value="user.id" />
              </el-select>
              <el-button type="primary" @click="submitAssign">
                <el-icon><User /></el-icon>
                指派
              </el-button>
            </div>
          </div>

          <div class="detail-card">
            <div class="detail-title">流转状态</div>
            <div class="inline-form">
              <el-select v-model="transitionStatus" placeholder="请选择目标状态">
                <el-option v-for="statusItem in bugStatusOptions" :key="statusItem.value" :label="statusItem.label" :value="statusItem.value" />
              </el-select>
              <el-input v-model="transitionComment" placeholder="请输入流转说明" />
              <el-button type="primary" @click="submitTransition">
                <el-icon><Promotion /></el-icon>
                更新状态
              </el-button>
            </div>
          </div>

          <div class="detail-card">
            <div class="detail-title">评论记录</div>
            <div class="inline-form">
              <el-input v-model="commentText" placeholder="请输入评论内容" />
              <el-button type="primary" @click="submitComment">添加评论</el-button>
            </div>
            <div class="list-stack" style="margin-top: 12px;">
              <div v-for="comment in detail.comments" :key="comment.id" class="list-row">
                <div class="list-main">
                  <div class="list-title">{{ comment.commenterName }}</div>
                  <div class="list-meta">{{ comment.content }}</div>
                </div>
                <span class="status-pill status-neutral">{{ formatDateTime(comment.createdAt) }}</span>
              </div>
            </div>
          </div>

          <div class="detail-card">
            <div class="detail-title">处理记录</div>
            <div class="list-stack">
              <div v-for="flow in detail.flows" :key="flow.id" class="list-row">
                <div class="list-main">
                  <div class="list-title">{{ flow.operatorName }}：{{ formatStatus(flow.fromStatus) }} -> {{ formatStatus(flow.toStatus) }}</div>
                  <div class="list-meta">{{ flow.actionComment }}</div>
                </div>
                <span class="status-pill status-neutral">{{ formatDateTime(flow.createdAt) }}</span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="caseBugVisible" title="从用例创建缺陷" width="620px">
      <el-form label-width="90px">
        <el-form-item v-if="isAllScope" label="所属空间" required>
          <el-select v-model="sourceBugState.workspaceCode">
            <el-option v-for="item in workspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="缺陷名称"><el-input v-model="sourceBugState.title" /></el-form-item>
        <el-form-item label="缺陷描述"><el-input v-model="sourceBugState.description" type="textarea" :rows="4" /></el-form-item>
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
      </el-form>
      <template #footer>
        <el-button @click="reportBugVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReportBug">提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.panel-header-bug-list {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.panel-header-bug-list :deep(.list-toolbar) {
  min-width: 0;
  flex: 1;
}

.panel-header-bug-list :deep(.list-toolbar-subline) {
  justify-content: flex-start;
}

.panel-header-bug-list :deep(.list-toolbar-filters) {
  flex-wrap: nowrap;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
}

.panel-header-bug-list :deep(.list-toolbar-filters::-webkit-scrollbar) {
  height: 6px;
}

.bug-create-button {
  flex: 0 0 auto;
  align-self: flex-start;
}

.toolbar-filter-input {
  width: 200px;
}

.toolbar-filter-select {
  width: 136px;
}

.bug-table-shell {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 164px;
  width: 100%;
  flex: 1;
  min-height: 0;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  overflow: hidden;
  background: var(--bg-panel);
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
  min-height: 50px;
  border-bottom: 1px solid var(--line-soft);
  color: var(--text-subtle);
  font-size: 12px;
  font-weight: 600;
  background: #fbfcff;
}

.bug-grid-row {
  min-height: 52px;
  border-bottom: 1px solid var(--line-soft);
  font-size: 13px;
}

.bug-cell {
  display: flex;
  align-items: center;
  padding: 0 10px;
  min-width: 0;
}

.bug-cell-text {
  display: block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bug-table-actions {
  display: flex;
  flex-direction: column;
  width: 164px;
  min-width: 164px;
  border-left: 1px solid var(--line-soft);
  background: var(--bg-panel);
  z-index: 1;
}

.bug-actions-header {
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

.bug-actions-header-title {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.table-settings-trigger {
  width: 28px;
  height: 28px;
  padding: 0;
}

.bug-actions-row {
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

.table-pagination-summary {
  color: var(--text-subtle);
  font-size: 13px;
  white-space: nowrap;
  line-height: 32px;
}

.bug-table-scroll::-webkit-scrollbar {
  height: 10px;
}

.bug-table-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.bug-table-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.5);
}

.bug-table-scroll::-webkit-scrollbar-thumb:hover {
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

@media (max-width: 1200px) {
  .table-pagination,
  .table-pagination-right {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
