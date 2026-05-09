<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Calendar, Files, Operation, Plus, RefreshRight, Upload, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { platformApi } from '../api/platform'
import ListToolbar from '../components/ListToolbar.vue'
import TableSettingsDrawer from '../components/TableSettingsDrawer.vue'
import { useListToolbarState } from '../composables/useListToolbarState'
import type { TableSettingsColumn } from '../composables/useTableSettings'
import { useWorkspace } from '../composables/useWorkspace'
import { useWorkspaceAccess } from '../composables/useWorkspaceAccess'
import { automationModules } from '../data/platform'
import type {
  CaseDirectoryNode,
  CaseDirectoryWorkspace,
  CreateBugPayload,
  CreateReportPayload,
  CreateTaskPayload,
  ReportAttachment,
  ReportDetail,
  ReportItem,
  TaskDetail,
  TaskItem,
  UserItem,
  WorkspaceItem,
} from '../types/api'

const props = defineProps<{
  engine: keyof typeof automationModules
}>()
const route = useRoute()
const router = useRouter()

const moduleConfig = computed(() => automationModules[props.engine])
const engineCode = computed(() => props.engine.toUpperCase())
const { workspaceCode, isAllScope } = useWorkspace()
const { canWriteWorkspace } = useWorkspaceAccess()

const loading = ref(false)
const saving = ref(false)
const reportContentSaving = ref(false)
const reportAttachmentUploading = ref(false)
const reportAttachmentRemovingId = ref<number | null>(null)
const tasks = ref<TaskItem[]>([])
const reports = ref<ReportItem[]>([])
const users = ref<UserItem[]>([])
const workspaces = ref<WorkspaceItem[]>([])

const taskDialogVisible = ref(false)
const taskDialogMode = ref<'create' | 'edit'>('create')
const reportDialogVisible = ref(false)
const reportDialogMode = ref<'create' | 'edit'>('create')
const reportBugVisible = ref(false)
const taskDrawerVisible = ref(false)
const reportDrawerVisible = ref(false)
const taskDetailLoading = ref(false)
const reportDetailLoading = ref(false)
const taskDetail = ref<TaskDetail | null>(null)
const reportDetail = ref<ReportDetail | null>(null)
const reportUploadInput = ref<HTMLInputElement | null>(null)
const caseDirectoryWorkspaces = ref<CaseDirectoryWorkspace[]>([])

type CaseModuleOption = {
  value: string
  label: string
  workspaceCode: string
  directoryId: number | null
}

const taskForm = reactive<CreateTaskPayload & { id: number | null; workspaceCode: string }>({
  id: null,
  workspaceCode: workspaceCode.value === 'ALL' ? '' : workspaceCode.value,
  taskName: '',
  engineType: engineCode.value,
  status: 'READY',
  summary: '',
})
const taskCaseContext = reactive({
  directoryId: null as number | null,
  modulePath: '',
})

const reportForm = reactive<CreateReportPayload & { id: number | null; workspaceCode: string }>({
  id: null,
  workspaceCode: workspaceCode.value === 'ALL' ? '' : workspaceCode.value,
  taskId: null,
  reportName: '',
  result: 'SUCCESS',
  logSource: 'MANUAL',
  failureSummary: '',
})

const reportContentForm = reactive({
  failureSummary: '',
  logText: '',
  logSource: 'MANUAL',
})

const reportBugState = reactive<CreateBugPayload & { workspaceCode: string; reportId: number | null }>({
  workspaceCode: workspaceCode.value === 'ALL' ? '' : workspaceCode.value,
  reportId: null,
  title: '',
  description: '',
  priority: 'P1',
  severity: 'HIGH',
  assigneeId: null,
  tags: [],
})

const taskFilters = reactive({
  keyword: '',
  status: '',
  workspaceCode: '',
})

const taskFilterDefaults = {
  keyword: '',
  status: '',
  workspaceCode: '',
}

const reportFilters = reactive({
  keyword: '',
  result: '',
  logSource: '',
  workspaceCode: '',
})

const reportFilterDefaults = {
  keyword: '',
  result: '',
  logSource: '',
  workspaceCode: '',
}

const filteredTasks = computed(() => tasks.value.filter((item) => {
  if (item.engineType.toLowerCase() !== props.engine) {
    return false
  }
  const keyword = taskFilters.keyword.trim().toLowerCase()
  if (keyword) {
    const matched = item.taskName.toLowerCase().includes(keyword) || item.summary.toLowerCase().includes(keyword)
    if (!matched) {
      return false
    }
  }
  if (taskFilters.status && item.status !== taskFilters.status) {
    return false
  }
  if (isAllScope.value && taskFilters.workspaceCode && item.workspaceCode !== taskFilters.workspaceCode) {
    return false
  }
  return true
}))

const filteredReports = computed(() => reports.value.filter((item) => {
  const matchedTask = tasks.value.find(task => task.id === item.taskId)
  if (matchedTask?.engineType.toLowerCase() !== props.engine) {
    return false
  }
  const keyword = reportFilters.keyword.trim().toLowerCase()
  if (keyword) {
    const matched = item.reportName.toLowerCase().includes(keyword) || item.failureSummary.toLowerCase().includes(keyword)
    if (!matched) {
      return false
    }
  }
  if (reportFilters.result && item.result !== reportFilters.result) {
    return false
  }
  if (reportFilters.logSource && item.logSource !== reportFilters.logSource) {
    return false
  }
  if (isAllScope.value && reportFilters.workspaceCode && item.workspaceCode !== reportFilters.workspaceCode) {
    return false
  }
  return true
}))
const writableWorkspaces = computed(() => workspaces.value.filter(item => !item.allScope && canWriteWorkspace(item.code)))
const reportTaskOptions = computed(() => filteredTasks.value.filter((item) => {
  if (!isAllScope.value) {
    return true
  }
  return !reportForm.workspaceCode || item.workspaceCode === reportForm.workspaceCode
}))
const canCreateData = computed(() => (
  isAllScope.value ? writableWorkspaces.value.length > 0 : canWriteWorkspace(workspaceCode.value)
))
const canEditCurrentReport = computed(() => (
  reportDetail.value ? canWriteWorkspace(reportDetail.value.workspaceCode) : false
))
const taskTransitionOptions = computed(() => {
  if (!taskDetail.value) {
    return []
  }
  switch (taskDetail.value.status) {
    case 'READY':
      return ['RUNNING', 'CANCELED']
    case 'RUNNING':
      return ['SUCCESS', 'FAILED', 'CANCELED']
    default:
      return []
  }
})

const taskStatusOptions = ['READY', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELED']
const reportResultOptions = ['SUCCESS', 'FAILED']

function resetTaskFilters() {
  taskListToolbar.filterMemory.reset()
}

function resetReportFilters() {
  reportListToolbar.filterMemory.reset()
}

async function loadExecution() {
  loading.value = true
  try {
    const [taskPage, reportPage, userList, workspaceList] = await Promise.all([
      platformApi.getTasks(workspaceCode.value),
      platformApi.getReports(workspaceCode.value),
      platformApi.getUsers(),
      platformApi.getSwitchableWorkspaces(),
    ])
    tasks.value = taskPage.items
    reports.value = reportPage.items
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

function resetTaskForm() {
  taskForm.id = null
  taskForm.workspaceCode = workspaceCode.value === 'ALL' ? '' : workspaceCode.value
  taskForm.taskName = ''
  taskForm.engineType = engineCode.value
  taskForm.status = 'READY'
  taskForm.summary = ''
  taskCaseContext.directoryId = null
  taskCaseContext.modulePath = ''
}

function resetReportForm() {
  reportForm.id = null
  reportForm.workspaceCode = workspaceCode.value === 'ALL' ? '' : workspaceCode.value
  reportForm.taskId = null
  reportForm.reportName = ''
  reportForm.result = 'SUCCESS'
  reportForm.logSource = 'MANUAL'
  reportForm.failureSummary = ''
}

function syncReportContentForm(detail: ReportDetail | null) {
  reportContentForm.failureSummary = detail?.failureSummary ?? ''
  reportContentForm.logText = detail?.logText ?? ''
  reportContentForm.logSource = detail?.logSource ?? 'MANUAL'
}

function makeCaseModuleValue(targetWorkspaceCode: string, targetDirectoryId: number | null) {
  return `${targetWorkspaceCode}::${targetDirectoryId ?? 'ROOT'}`
}
function parseCaseModuleValue(value: string) {
  const [targetWorkspaceCode, directoryToken] = value.split('::')
  return {
    workspaceCode: targetWorkspaceCode,
    directoryId: directoryToken && directoryToken !== 'ROOT' ? Number(directoryToken) : null,
  }
}
function buildDirectoryOptions(nodes: CaseDirectoryNode[], prefix = ''): CaseModuleOption[] {
  const options: CaseModuleOption[] = []
  for (const node of nodes) {
    const label = prefix ? `${prefix} / ${node.name}` : node.name
    options.push({
      value: '',
      label,
      workspaceCode: node.workspaceCode,
      directoryId: node.id,
    })
    if (node.children.length) {
      options.push(...buildDirectoryOptions(node.children, label))
    }
  }
  return options
}
function getWorkspaceNameByCode(targetWorkspaceCode: string) {
  return workspaces.value.find(item => item.code === targetWorkspaceCode)?.name ?? targetWorkspaceCode
}
function formatCaseModulePath(targetWorkspaceCode: string, targetDirectoryId: number | null) {
  const workspace = caseDirectoryWorkspaces.value.find(item => item.workspaceCode === targetWorkspaceCode)
  const workspaceName = workspace?.workspaceName ?? getWorkspaceNameByCode(targetWorkspaceCode)
  if (targetDirectoryId === null) {
    return workspaceName
  }
  const option = buildDirectoryOptions(workspace?.children ?? []).find(item => item.directoryId === targetDirectoryId)
  return option ? `${workspaceName} / ${option.label}` : workspaceName
}
async function ensureCaseDirectoryWorkspaces(targetWorkspaceCode?: string) {
  const scopeCode = targetWorkspaceCode || workspaceCode.value
  caseDirectoryWorkspaces.value = await platformApi.getCaseDirectories(scopeCode)
}
const taskCaseModuleOptions = computed<CaseModuleOption[]>(() => {
  const scopes = taskForm.workspaceCode
    ? caseDirectoryWorkspaces.value.filter(item => item.workspaceCode === taskForm.workspaceCode)
    : caseDirectoryWorkspaces.value
  return scopes.flatMap((workspace) => {
    const rootOption: CaseModuleOption = {
      value: makeCaseModuleValue(workspace.workspaceCode, null),
      label: workspace.workspaceName,
      workspaceCode: workspace.workspaceCode,
      directoryId: null,
    }
    const childOptions = buildDirectoryOptions(workspace.children).map(item => ({
      ...item,
      value: makeCaseModuleValue(workspace.workspaceCode, item.directoryId),
      label: `${workspace.workspaceName} / ${item.label}`,
    }))
    return [rootOption, ...childOptions]
  })
})
const taskCaseModuleValue = computed({
  get: () => {
    if (!taskForm.workspaceCode) {
      return ''
    }
    return makeCaseModuleValue(taskForm.workspaceCode, taskCaseContext.directoryId)
  },
  set: (value: string) => {
    if (!value) {
      return
    }
    const parsed = parseCaseModuleValue(value)
    taskForm.workspaceCode = parsed.workspaceCode
    taskCaseContext.directoryId = parsed.directoryId
    taskCaseContext.modulePath = formatCaseModulePath(parsed.workspaceCode, parsed.directoryId)
    if (taskForm.summary.includes('来源模块：')) {
      taskForm.summary = taskForm.summary.replace(/来源模块：[^\n]*/g, `来源模块：${taskCaseContext.modulePath}`)
    }
    else if (taskCaseContext.modulePath) {
      taskForm.summary = [taskForm.summary.trim(), `来源模块：${taskCaseContext.modulePath}`].filter(Boolean).join('\n')
    }
  },
})

function openTaskCreate() {
  resetTaskForm()
  taskDialogMode.value = 'create'
  taskDialogVisible.value = true
  void ensureCaseDirectoryWorkspaces(taskForm.workspaceCode || undefined)
}

function openTaskCreateFromCase(
  caseTitle: string,
  targetWorkspaceCode?: string,
  targetDirectoryId?: number | null,
  targetModulePath?: string,
) {
  resetTaskForm()
  taskForm.taskName = `${caseTitle} ????`
  taskForm.summary = `?????${caseTitle}`
  if (targetWorkspaceCode) {
    taskForm.workspaceCode = targetWorkspaceCode
  }
  taskCaseContext.directoryId = targetDirectoryId ?? null
  taskCaseContext.modulePath = targetModulePath ?? ''
  if (taskCaseContext.modulePath) {
    taskForm.summary = `${taskForm.summary}\n?????${taskCaseContext.modulePath}`
  }
  taskDialogMode.value = 'create'
  taskDialogVisible.value = true
  void ensureCaseDirectoryWorkspaces(taskForm.workspaceCode || undefined)
}

function consumeCaseExecuteIntent() {
  if (route.query.openTask !== '1') {
    return
  }
  const caseTitle = typeof route.query.caseTitle === 'string' ? route.query.caseTitle : ''
  const targetWorkspaceCode = typeof route.query.workspace === 'string' ? route.query.workspace : ''
  openTaskCreateFromCase(caseTitle || '未命名用例', targetWorkspaceCode || undefined)
  void router.replace({
    query: {
      ...route.query,
      openTask: undefined,
      caseTitle: undefined,
    },
  })
}

function openTaskEdit(row: TaskItem) {
  taskForm.id = row.id
  taskForm.workspaceCode = row.workspaceCode
  taskForm.taskName = row.taskName
  taskForm.engineType = row.engineType
  taskForm.status = row.status
  taskForm.summary = row.summary ?? ''
  const modulePathMatch = taskForm.summary.match(/来源模块：([^\n]+)/)
  taskCaseContext.modulePath = modulePathMatch?.[1]?.trim() ?? ''
  taskDialogMode.value = 'edit'
  taskDialogVisible.value = true
  void ensureCaseDirectoryWorkspaces(taskForm.workspaceCode || undefined)
}

async function openTaskDetail(id: number) {
  taskDetailLoading.value = true
  taskDrawerVisible.value = true
  try {
    taskDetail.value = await platformApi.getTaskDetail(workspaceCode.value, id)
  }
  catch (error) {
    taskDrawerVisible.value = false
    ElMessage.error((error as Error).message)
  }
  finally {
    taskDetailLoading.value = false
  }
}

async function submitTask() {
  if (isAllScope.value && !taskForm.workspaceCode) {
    ElMessage.error('???????????????')
    return
  }
  if (!taskForm.taskName.trim()) {
    ElMessage.error('????????')
    return
  }
  saving.value = true
  try {
    const payload: CreateTaskPayload = {
      workspaceCode: isAllScope.value ? taskForm.workspaceCode : undefined,
      taskName: taskForm.taskName.trim(),
      engineType: engineCode.value,
      status: taskForm.status,
      summary: taskForm.summary.trim(),
    }
    if (taskDialogMode.value === 'create') {
      await platformApi.createTask(workspaceCode.value, payload)
      ElMessage.success('??????')
    }
    else if (taskForm.id !== null) {
      await platformApi.updateTask(workspaceCode.value, taskForm.id, payload)
      ElMessage.success('??????')
    }
    taskDialogVisible.value = false
    await loadExecution()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function confirmDeleteTask(row: TaskItem) {
  try {
    await ElMessageBox.confirm(`???????${row.taskName}???`, '????', { type: 'warning' })
    await platformApi.deleteTask(workspaceCode.value, row.id)
    ElMessage.success('??????')
    await loadExecution()
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

async function transitionTask(toStatus: string) {
  if (!taskDetail.value) {
    return
  }
  saving.value = true
  try {
    taskDetail.value = await platformApi.transitionTask(workspaceCode.value, taskDetail.value.id, { toStatus })
    await loadExecution()
    ElMessage.success('???????')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

function openReportCreate() {
  resetReportForm()
  reportDialogMode.value = 'create'
  reportDialogVisible.value = true
}

function openReportEdit(row: ReportItem) {
  reportForm.id = row.id
  reportForm.workspaceCode = row.workspaceCode
  reportForm.taskId = row.taskId
  reportForm.reportName = row.reportName
  reportForm.result = row.result
  reportForm.logSource = row.logSource
  reportForm.failureSummary = row.failureSummary ?? ''
  reportDialogMode.value = 'edit'
  reportDialogVisible.value = true
}

async function openReportDetail(id: number) {
  reportDetailLoading.value = true
  reportDrawerVisible.value = true
  try {
    reportDetail.value = await platformApi.getReportDetail(workspaceCode.value, id)
    syncReportContentForm(reportDetail.value)
  }
  catch (error) {
    reportDrawerVisible.value = false
    ElMessage.error((error as Error).message)
  }
  finally {
    reportDetailLoading.value = false
  }
}

async function reloadCurrentReportDetail() {
  if (!reportDetail.value) {
    return
  }
  reportDetail.value = await platformApi.getReportDetail(workspaceCode.value, reportDetail.value.id)
  syncReportContentForm(reportDetail.value)
}

async function submitReport() {
  if (isAllScope.value && !reportForm.workspaceCode) {
    ElMessage.error('???????????????')
    return
  }
  if (!reportForm.taskId) {
    ElMessage.error('????????')
    return
  }
  if (!reportForm.reportName.trim()) {
    ElMessage.error('????????')
    return
  }
  saving.value = true
  try {
    const payload: CreateReportPayload = {
      workspaceCode: isAllScope.value ? reportForm.workspaceCode : undefined,
      taskId: reportForm.taskId,
      reportName: reportForm.reportName.trim(),
      result: reportForm.result,
      logSource: reportForm.logSource,
      failureSummary: reportForm.failureSummary.trim(),
    }
    if (reportDialogMode.value === 'create') {
      await platformApi.createReport(workspaceCode.value, payload)
      ElMessage.success('??????')
    }
    else if (reportForm.id !== null) {
      await platformApi.updateReport(workspaceCode.value, reportForm.id, payload)
      ElMessage.success('??????')
    }
    reportDialogVisible.value = false
    await loadExecution()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function confirmDeleteReport(row: ReportItem) {
  try {
    await ElMessageBox.confirm(`???????${row.reportName}???`, '????', { type: 'warning' })
    await platformApi.deleteReport(workspaceCode.value, row.id)
    ElMessage.success('??????')
    if (reportDetail.value?.id === row.id) {
      reportDrawerVisible.value = false
      reportDetail.value = null
      syncReportContentForm(null)
    }
    await loadExecution()
  }
  catch (error) {
    if (error !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

async function saveReportContent() {
  if (!reportDetail.value) {
    return
  }
  reportContentSaving.value = true
  try {
    reportDetail.value = await platformApi.updateReportContent(workspaceCode.value, reportDetail.value.id, {
      failureSummary: reportContentForm.failureSummary,
      logText: reportContentForm.logText,
      logSource: reportContentForm.logSource,
    })
    syncReportContentForm(reportDetail.value)
    await loadExecution()
    ElMessage.success('???????')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    reportContentSaving.value = false
  }
}

function triggerReportAttachmentUpload() {
  reportUploadInput.value?.click()
}

async function handleReportAttachmentChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  if (!files.length || !reportDetail.value) {
    return
  }
  reportAttachmentUploading.value = true
  try {
    const uploaded = await platformApi.uploadReportAttachment(workspaceCode.value, reportDetail.value.id, files)
    await reloadCurrentReportDetail()
    await loadExecution()
    ElMessage.success(`??? ${uploaded.length} ???`)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    reportAttachmentUploading.value = false
  }
}

async function removeReportAttachment(item: ReportAttachment) {
  if (!reportDetail.value || item.id <= 0) {
    return
  }
  reportAttachmentRemovingId.value = item.id
  try {
    await platformApi.deleteReportAttachment(workspaceCode.value, reportDetail.value.id, item.id)
    await reloadCurrentReportDetail()
    await loadExecution()
    ElMessage.success('??????')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    reportAttachmentRemovingId.value = null
  }
}

async function downloadReportAttachment(item: ReportAttachment) {
  if (!reportDetail.value || item.id <= 0) {
    return
  }
  try {
    await platformApi.downloadReportAttachment(workspaceCode.value, reportDetail.value.id, item.id, item.fileName)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function openReportBugDialog(row: ReportItem) {
  Object.assign(reportBugState, {
    workspaceCode: isAllScope.value ? row.workspaceCode : workspaceCode.value,
    reportId: row.id,
    title: `${row.reportName} - ??`,
    description: row.failureSummary,
    priority: 'P1',
    severity: 'HIGH',
    assigneeId: null,
    tags: [],
  })
  reportBugVisible.value = true
}

async function submitReportBug() {
  if (!reportBugState.reportId) return
  saving.value = true
  try {
    await platformApi.createBugFromReport(workspaceCode.value, reportBugState.reportId, {
      workspaceCode: isAllScope.value ? reportBugState.workspaceCode : undefined,
      title: reportBugState.title,
      description: reportBugState.description,
      priority: reportBugState.priority,
      severity: reportBugState.severity,
      assigneeId: reportBugState.assigneeId,
      tags: reportBugState.tags,
    })
    reportBugVisible.value = false
    ElMessage.success('????????')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

function canWriteRow(workspace: string) {
  return canWriteWorkspace(workspace)
}

function formatFileSize(size: number | null) {
  if (size === null || size === undefined) {
    return '-'
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`
  }
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

const reportLogSourceOptions = [
  { label: 'MANUAL', value: 'MANUAL' },
  { label: 'API', value: 'API' },
  { label: 'WEB', value: 'WEB' },
  { label: 'APP', value: 'APP' },
  { label: 'SYSTEM', value: 'SYSTEM' },
]

const taskColumns: TableSettingsColumn[] = [
  { key: 'taskName', label: '????', required: true, defaultVisible: true },
  { key: 'status', label: '??', defaultVisible: true },
  { key: 'summary', label: '??', defaultVisible: true },
  { key: 'workspaceName', label: '????', defaultVisible: true, allOnly: true },
]

const reportColumns: TableSettingsColumn[] = [
  { key: 'reportName', label: '????', required: true, defaultVisible: true },
  { key: 'result', label: '??', defaultVisible: true },
  { key: 'failureSummary', label: '????', defaultVisible: true },
  { key: 'workspaceName', label: '????', defaultVisible: true, allOnly: true },
]

const taskListToolbar = useListToolbarState({
  tableSettingsKey: `task-table-settings-${props.engine}-v1`,
  filterStorageKey: computed(() => `task-list-filters-${props.engine}-v1`),
  columns: taskColumns,
  filters: taskFilters,
  filterDefaults: taskFilterDefaults,
  isColumnAvailable: column => !column.allOnly || isAllScope.value,
})

const reportListToolbar = useListToolbarState({
  tableSettingsKey: `report-table-settings-${props.engine}-v1`,
  filterStorageKey: computed(() => `report-list-filters-${props.engine}-v1`),
  columns: reportColumns,
  filters: reportFilters,
  filterDefaults: reportFilterDefaults,
  isColumnAvailable: column => !column.allOnly || isAllScope.value,
})

watch([workspaceCode, () => props.engine], () => {
  resetTaskForm()
  resetReportForm()
  taskListToolbar.filterMemory.load()
  reportListToolbar.filterMemory.load()
  taskDrawerVisible.value = false
  reportDrawerVisible.value = false
  taskDetail.value = null
  reportDetail.value = null
  syncReportContentForm(null)
  loadExecution()
})

onMounted(() => {
  taskListToolbar.load()
  reportListToolbar.load()
  loadExecution()
  consumeCaseExecuteIntent()
})
</script>

<template>
  <section class="page-shell">
    <div class="page-header">
      <div class="page-title">{{ moduleConfig.title }}</div>
      <div class="page-actions">
        <el-button v-if="canCreateData" type="primary" @click="openTaskCreate">
          <el-icon><VideoPlay /></el-icon>
          新建任务
        </el-button>
        <el-button v-if="canCreateData" @click="openReportCreate">
          <el-icon><Calendar /></el-icon>
          新建报告
        </el-button>
      </div>
    </div>

    <div class="stats-grid">
      <article
        v-for="item in moduleConfig.stats"
        :key="item.label"
        class="metric-card"
      >
        <div class="metric-label">{{ item.label }}</div>
        <div class="metric-value">{{ item.value }}</div>
        <div class="metric-trend">{{ item.trend }}</div>
      </article>
    </div>

    <div class="double-grid">
      <article class="panel-card">
        <div class="panel-header">
          <div>
            <div class="panel-title">V1 能力拆解</div>
            <div class="panel-subtitle">先把任务与报告中心做成真实可维护的数据层能力。</div>
          </div>
          <el-icon><Operation /></el-icon>
        </div>
        <div class="matrix-list">
          <div
            v-for="row in moduleConfig.tasks"
            :key="row[0]"
            class="matrix-row"
          >
            <div class="matrix-name">{{ row[0] }}</div>
            <div class="matrix-desc">{{ row[1] }}</div>
            <span
              :class="[
                'status-pill',
                row[2] === '优先' ? 'status-warning' : 'status-neutral',
              ]"
            >
              {{ row[2] }}
            </span>
          </div>
        </div>
      </article>

      <article class="panel-card">
        <div class="panel-header">
          <ListToolbar
            title="执行任务"
            show-settings
            @settings="taskListToolbar.settingsVisible.value = true"
          >
            <template #filters>
              <el-input
                v-model="taskFilters.keyword"
                placeholder="搜索任务名称或摘要"
                clearable
                class="toolbar-filter-input"
              />
              <el-select v-model="taskFilters.status" clearable placeholder="状态" class="toolbar-filter-select">
                <el-option v-for="item in taskStatusOptions" :key="item" :label="item" :value="item" />
              </el-select>
              <el-select
                v-if="isAllScope"
                v-model="taskFilters.workspaceCode"
                clearable
                placeholder="所属空间"
                class="toolbar-filter-select"
              >
                <el-option v-for="item in workspaces" :key="item.code" :label="item.name" :value="item.code" />
              </el-select>
              <el-button text @click="resetTaskFilters">
                <el-icon><RefreshRight /></el-icon>
                重置
              </el-button>
            </template>
            <template #actions>
              <el-button v-if="canCreateData" type="primary" plain @click="openTaskCreate">
                <el-icon><Plus /></el-icon>
                新建任务
              </el-button>
            </template>
          </ListToolbar>
        </div>
        <el-table v-loading="loading" :data="filteredTasks" size="large">
          <template v-for="column in taskListToolbar.visibleColumns.value" :key="column.key">
            <el-table-column v-if="column.key === 'taskName'" prop="taskName" label="任务名称" min-width="220" />
            <el-table-column v-else-if="column.key === 'status'" prop="status" label="状态" width="110" />
            <el-table-column v-else-if="column.key === 'summary'" prop="summary" label="摘要" min-width="220" />
            <el-table-column v-else-if="column.key === 'workspaceName'" prop="workspaceName" label="所属空间" width="130" />
          </template>
          <el-table-column label="操作" width="250">
            <template #default="{ row }">
              <el-button text type="primary" @click="openTaskDetail(row.id)">查看</el-button>
              <template v-if="canWriteRow(row.workspaceCode)">
                <el-button text type="primary" @click="openTaskEdit(row)">编辑</el-button>
                <el-button text type="danger" @click="confirmDeleteTask(row)">删除</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </article>
    </div>

    <article class="panel-card">
      <div class="panel-header">
        <ListToolbar
          title="执行报告"
          show-settings
          @settings="reportListToolbar.settingsVisible.value = true"
        >
          <template #filters>
            <el-input
              v-model="reportFilters.keyword"
              placeholder="搜索报告名称或失败摘要"
              clearable
              class="toolbar-filter-input"
            />
            <el-select v-model="reportFilters.result" clearable placeholder="结果" class="toolbar-filter-select">
              <el-option v-for="item in reportResultOptions" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="reportFilters.logSource" clearable placeholder="日志来源" class="toolbar-filter-select">
              <el-option v-for="item in reportLogSourceOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select
              v-if="isAllScope"
              v-model="reportFilters.workspaceCode"
              clearable
              placeholder="所属空间"
              class="toolbar-filter-select"
            >
              <el-option v-for="item in workspaces" :key="item.code" :label="item.name" :value="item.code" />
            </el-select>
            <el-button text @click="resetReportFilters">
              <el-icon><RefreshRight /></el-icon>
              重置
            </el-button>
          </template>
          <template #actions>
            <el-button v-if="canCreateData" type="primary" plain @click="openReportCreate">
              <el-icon><Files /></el-icon>
              新建报告
            </el-button>
          </template>
        </ListToolbar>
      </div>
      <el-table v-loading="loading" :data="filteredReports" size="large">
        <template v-for="column in reportListToolbar.visibleColumns.value" :key="column.key">
          <el-table-column v-if="column.key === 'reportName'" prop="reportName" label="报告名称" min-width="220" />
          <el-table-column v-else-if="column.key === 'result'" prop="result" label="结果" width="100" />
          <el-table-column v-else-if="column.key === 'failureSummary'" prop="failureSummary" label="失败摘要" min-width="260" />
          <el-table-column v-else-if="column.key === 'workspaceName'" prop="workspaceName" label="所属空间" width="130" />
        </template>
        <el-table-column label="操作" width="320">
          <template #default="{ row }">
            <el-button text type="primary" @click="openReportDetail(row.id)">查看</el-button>
            <template v-if="canWriteRow(row.workspaceCode)">
              <el-button text type="primary" @click="openReportEdit(row)">编辑</el-button>
              <el-button text type="danger" @click="confirmDeleteReport(row)">删除</el-button>
              <el-button text type="warning" @click="openReportBugDialog(row)">提缺陷</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </article>

    <TableSettingsDrawer
      v-model="taskListToolbar.settingsVisible.value"
      :columns="taskListToolbar.drawerColumns.value"
      :dragging-key="taskListToolbar.draggingColumnKey.value"
      @toggle-column="taskListToolbar.toggleColumnVisibility"
      @drag-start="taskListToolbar.handleDragStart"
      @drag-end="taskListToolbar.handleDragEnd"
      @drop-column="taskListToolbar.moveColumnToTarget"
      @reset="taskListToolbar.reset"
    />

    <TableSettingsDrawer
      v-model="reportListToolbar.settingsVisible.value"
      :columns="reportListToolbar.drawerColumns.value"
      :dragging-key="reportListToolbar.draggingColumnKey.value"
      @toggle-column="reportListToolbar.toggleColumnVisibility"
      @drag-start="reportListToolbar.handleDragStart"
      @drag-end="reportListToolbar.handleDragEnd"
      @drop-column="reportListToolbar.moveColumnToTarget"
      @reset="reportListToolbar.reset"
    />

    <el-dialog
      v-model="taskDialogVisible"
      :title="taskDialogMode === 'create' ? '新建任务' : '编辑任务'"
      width="620px"
    >
      <el-form label-width="100px">
        <el-form-item v-if="isAllScope" label="目标空间" required>
          <el-select v-model="taskForm.workspaceCode" placeholder="请选择目标空间">
            <el-option v-for="item in writableWorkspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务名称" required>
          <el-input v-model="taskForm.taskName" />
        </el-form-item>
        <el-form-item label="用例模块">
          <el-select v-model="taskCaseModuleValue" clearable placeholder="请选择来源用例模块">
            <el-option
              v-for="item in taskCaseModuleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="执行引擎">
          <el-input :model-value="engineCode" disabled />
        </el-form-item>
        <el-form-item label="任务状态" required>
          <el-select v-model="taskForm.status">
            <el-option v-for="item in taskStatusOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="taskForm.summary" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitTask">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="reportDialogVisible"
      :title="reportDialogMode === 'create' ? '新建报告' : '编辑报告'"
      width="680px"
    >
      <el-form label-width="100px">
        <el-form-item v-if="isAllScope" label="目标空间" required>
          <el-select v-model="reportForm.workspaceCode" placeholder="请选择目标空间">
            <el-option v-for="item in writableWorkspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联任务" required>
          <el-select v-model="reportForm.taskId" placeholder="请选择任务">
            <el-option
              v-for="item in reportTaskOptions"
              :key="item.id"
              :label="item.taskName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="报告名称" required>
          <el-input v-model="reportForm.reportName" />
        </el-form-item>
        <el-form-item label="执行结果" required>
          <el-select v-model="reportForm.result">
            <el-option v-for="item in reportResultOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="日志来源">
          <el-select v-model="reportForm.logSource">
            <el-option
              v-for="item in reportLogSourceOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="失败摘要">
          <el-input v-model="reportForm.failureSummary" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReport">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reportBugVisible" title="从报告创建缺陷" width="640px">
      <el-form label-width="90px">
        <el-form-item v-if="isAllScope" label="目标空间" required>
          <el-select v-model="reportBugState.workspaceCode">
            <el-option v-for="item in writableWorkspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="reportBugState.title" />
        </el-form-item>
        <el-form-item label="描述" required>
          <el-input v-model="reportBugState.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="reportBugState.assigneeId" clearable>
            <el-option v-for="item in users" :key="item.id" :label="item.displayName" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportBugVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReportBug">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="taskDrawerVisible" title="任务详情" size="720px">
      <div v-loading="taskDetailLoading">
        <template v-if="taskDetail">
          <div class="detail-grid">
            <div class="detail-card">
              <div class="detail-title">{{ taskDetail.taskName }}</div>
              <div class="detail-meta">
                {{ taskDetail.workspaceName }} | {{ taskDetail.engineType }} | {{ taskDetail.status }}
              </div>
              <p class="detail-body">{{ taskDetail.summary || '-' }}</p>
            </div>
            <div class="detail-card">
              <div class="detail-title">状态流转</div>
              <p class="detail-body">创建时间：{{ taskDetail.createdAt }}</p>
              <p class="detail-body">更新时间：{{ taskDetail.updatedAt }}</p>
              <div class="toolbar-group">
                <el-button
                  v-for="status in taskTransitionOptions"
                  :key="status"
                  v-if="canWriteRow(taskDetail.workspaceCode)"
                  type="primary"
                  plain
                  size="small"
                  :loading="saving"
                  @click="transitionTask(status)"
                >
                  转为 {{ status }}
                </el-button>
                <span v-if="!taskTransitionOptions.length" class="status-pill status-neutral">终态</span>
              </div>
            </div>
            <div class="detail-card">
              <div class="detail-title">关联报告</div>
              <div v-if="taskDetail.reports.length" class="list-stack">
                <div
                  v-for="report in taskDetail.reports"
                  :key="report.id"
                  class="list-row"
                >
                  <div>
                    <div class="list-title">{{ report.reportName }}</div>
                    <div class="detail-meta">{{ report.result }} | {{ report.failureSummary || '-' }}</div>
                  </div>
                  <el-button text type="primary" @click="openReportDetail(report.id)">查看</el-button>
                </div>
              </div>
              <p v-else class="detail-body">暂无关联报告</p>
            </div>
          </div>
        </template>
      </div>
    </el-drawer>

    <el-drawer v-model="reportDrawerVisible" title="报告详情" size="720px">
      <div v-loading="reportDetailLoading">
        <template v-if="reportDetail">
          <div class="detail-grid">
            <div class="detail-card">
              <div class="detail-title">{{ reportDetail.reportName }}</div>
              <div class="detail-meta">
                {{ reportDetail.workspaceName }} | {{ reportDetail.taskName }} | {{ reportDetail.result }} | {{ reportDetail.logSource }}
              </div>
              <p class="detail-body">创建时间：{{ reportDetail.createdAt }}</p>
              <p class="detail-body">更新时间：{{ reportDetail.updatedAt }}</p>
            </div>

            <div class="detail-card">
              <div class="panel-header">
                <div>
                  <div class="detail-title">报告内容</div>
                  <div class="detail-meta">支持维护失败摘要与执行日志。</div>
                </div>
                <el-button
                  v-if="canEditCurrentReport"
                  type="primary"
                  size="small"
                  :loading="reportContentSaving"
                  @click="saveReportContent"
                >
                  保存内容
                </el-button>
              </div>
              <el-form label-width="88px">
                <el-form-item label="日志来源">
                  <el-select
                    v-model="reportContentForm.logSource"
                    :disabled="!canEditCurrentReport"
                  >
                    <el-option
                      v-for="item in reportLogSourceOptions"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="失败摘要">
                  <el-input
                    v-model="reportContentForm.failureSummary"
                    type="textarea"
                    :rows="3"
                    :disabled="!canEditCurrentReport"
                  />
                </el-form-item>
                <el-form-item label="执行日志">
                  <el-input
                    v-model="reportContentForm.logText"
                    type="textarea"
                    :rows="8"
                    :disabled="!canEditCurrentReport"
                  />
                </el-form-item>
              </el-form>
            </div>

            <div class="detail-card">
              <div class="panel-header">
                <div>
                  <div class="detail-title">附件</div>
                  <div class="detail-meta">支持上传、下载和删除单个附件。</div>
                </div>
                <el-button
                  v-if="canEditCurrentReport"
                  type="primary"
                  plain
                  size="small"
                  :loading="reportAttachmentUploading"
                  @click="triggerReportAttachmentUpload"
                >
                  <el-icon><Upload /></el-icon>
                  上传附件
                </el-button>
              </div>
              <input
                ref="reportUploadInput"
                type="file"
                multiple
                style="display: none"
                @change="handleReportAttachmentChange"
              >
              <div v-if="reportDetail.attachments.length" class="list-stack">
                <div
                  v-for="attachment in reportDetail.attachments"
                  :key="`${attachment.id}-${attachment.fileName}`"
                  class="list-row"
                >
                  <div>
                    <div class="list-title">{{ attachment.fileName }}</div>
                    <div class="detail-meta">
                      {{ attachment.contentType || '未知类型' }} | {{ formatFileSize(attachment.fileSize) }}
                    </div>
                  </div>
                  <div class="toolbar-group">
                    <el-button
                      v-if="attachment.downloadUrl"
                      text
                      type="primary"
                      @click="downloadReportAttachment(attachment)"
                    >
                      下载
                    </el-button>
                    <el-button
                      v-if="canEditCurrentReport && attachment.id > 0"
                      text
                      type="danger"
                      :loading="reportAttachmentRemovingId === attachment.id"
                      @click="removeReportAttachment(attachment)"
                    >
                      删除
                    </el-button>
                  </div>
                </div>
              </div>
              <p v-else class="detail-body">暂无附件</p>
            </div>
          </div>
        </template>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.toolbar-filter-input {
  width: 220px;
}

.toolbar-filter-select {
  width: 140px;
}
</style>
