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
    if (taskForm.summary.includes('\u6765\u6E90\u6A21\u5757\uFF1A')) {
      taskForm.summary = taskForm.summary.replace(/\u6765\u6E90\u6A21\u5757\uFF1A[^\n]*/g, `\u6765\u6E90\u6A21\u5757\uFF1A${taskCaseContext.modulePath}`)
    }
    else if (taskCaseContext.modulePath) {
      taskForm.summary = [taskForm.summary.trim(), `\u6765\u6E90\u6A21\u5757\uFF1A${taskCaseContext.modulePath}`].filter(Boolean).join('\n')
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
  taskForm.taskName = `${caseTitle} \u6267\u884C\u4EFB\u52A1`
  taskForm.summary = `\u6765\u6E90\u7528\u4F8B\uFF1A${caseTitle}`
  if (targetWorkspaceCode) {
    taskForm.workspaceCode = targetWorkspaceCode
  }
  taskCaseContext.directoryId = targetDirectoryId ?? null
  taskCaseContext.modulePath = targetModulePath ?? ''
  if (taskCaseContext.modulePath) {
    taskForm.summary = `${taskForm.summary}\n\u6765\u6E90\u6A21\u5757\uFF1A${taskCaseContext.modulePath}`
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
  openTaskCreateFromCase(caseTitle || '\u672A\u547D\u540D\u7528\u4F8B', targetWorkspaceCode || undefined)
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
    ElMessage.error('\u5728\u5168\u90E8\u7A7A\u95F4\u4E0B\u8BF7\u9009\u62E9\u76EE\u6807\u7A7A\u95F4')
    return
  }
  if (!taskForm.taskName.trim()) {
    ElMessage.error('\u8BF7\u8F93\u5165\u4EFB\u52A1\u540D\u79F0')
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
      ElMessage.success('\u4EFB\u52A1\u521B\u5EFA\u6210\u529F')
    }
    else if (taskForm.id !== null) {
      await platformApi.updateTask(workspaceCode.value, taskForm.id, payload)
      ElMessage.success('\u4EFB\u52A1\u66F4\u65B0\u6210\u529F')
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
    await ElMessageBox.confirm(`\u786E\u8BA4\u5220\u9664\u4EFB\u52A1“${row.taskName}”\u5417\uFF1F`, '\u5220\u9664\u4EFB\u52A1', { type: 'warning' })
    await platformApi.deleteTask(workspaceCode.value, row.id)
    ElMessage.success('\u4EFB\u52A1\u5220\u9664\u6210\u529F')
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
    ElMessage.success('\u4EFB\u52A1\u72B6\u6001\u5DF2\u66F4\u65B0')
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
    ElMessage.error('\u5728\u5168\u90E8\u7A7A\u95F4\u4E0B\u8BF7\u9009\u62E9\u76EE\u6807\u7A7A\u95F4')
    return
  }
  if (!reportForm.taskId) {
    ElMessage.error('\u8BF7\u9009\u62E9\u5173\u8054\u4EFB\u52A1')
    return
  }
  if (!reportForm.reportName.trim()) {
    ElMessage.error('\u8BF7\u8F93\u5165\u62A5\u544A\u540D\u79F0')
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
      ElMessage.success('\u62A5\u544A\u521B\u5EFA\u6210\u529F')
    }
    else if (reportForm.id !== null) {
      await platformApi.updateReport(workspaceCode.value, reportForm.id, payload)
      ElMessage.success('\u62A5\u544A\u66F4\u65B0\u6210\u529F')
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
    await ElMessageBox.confirm(`\u786E\u8BA4\u5220\u9664\u62A5\u544A“${row.reportName}”\u5417\uFF1F`, '\u5220\u9664\u62A5\u544A', { type: 'warning' })
    await platformApi.deleteReport(workspaceCode.value, row.id)
    ElMessage.success('\u62A5\u544A\u5220\u9664\u6210\u529F')
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
    ElMessage.success('\u62A5\u544A\u5185\u5BB9\u5DF2\u4FDD\u5B58')
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
    ElMessage.success(`\u5DF2\u4E0A\u4F20 ${uploaded.length} \u4E2A\u9644\u4EF6`)
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
    ElMessage.success('\u9644\u4EF6\u5220\u9664\u6210\u529F')
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
    title: `${row.reportName} - \u7F3A\u9677`,
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
    ElMessage.success('\u5DF2\u4ECE\u62A5\u544A\u521B\u5EFA\u7F3A\u9677')
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
  { key: 'taskName', label: '\u4EFB\u52A1\u540D\u79F0', required: true, defaultVisible: true },
  { key: 'status', label: '\u72B6\u6001', defaultVisible: true },
  { key: 'summary', label: '\u6458\u8981', defaultVisible: true },
  { key: 'workspaceName', label: '\u6240\u5C5E\u7A7A\u95F4', defaultVisible: true, allOnly: true },
]

const reportColumns: TableSettingsColumn[] = [
  { key: 'reportName', label: '\u62A5\u544A\u540D\u79F0', required: true, defaultVisible: true },
  { key: 'result', label: '\u7ED3\u679C', defaultVisible: true },
  { key: 'failureSummary', label: '\u5931\u8D25\u6458\u8981', defaultVisible: true },
  { key: 'workspaceName', label: '\u6240\u5C5E\u7A7A\u95F4', defaultVisible: true, allOnly: true },
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
          &#x65B0;&#x5EFA;&#x4EFB;&#x52A1;
        </el-button>
        <el-button v-if="canCreateData" @click="openReportCreate">
          <el-icon><Calendar /></el-icon>
          &#x65B0;&#x5EFA;&#x62A5;&#x544A;
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
            <div class="panel-title">V1 &#x80FD;&#x529B;&#x62C6;&#x89E3;</div>
            <div class="panel-subtitle">&#x5148;&#x628A;&#x4EFB;&#x52A1;&#x4E0E;&#x62A5;&#x544A;&#x4E2D;&#x5FC3;&#x505A;&#x6210;&#x771F;&#x5B9E;&#x53EF;&#x7EF4;&#x62A4;&#x7684;&#x6570;&#x636E;&#x5C42;&#x80FD;&#x529B;&#x3002;</div>
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
                row[2] === '\u4F18\u5148' ? 'status-warning' : 'status-neutral',
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
            title="&#x6267;&#x884C;&#x4EFB;&#x52A1;"
            show-settings
            @settings="taskListToolbar.settingsVisible.value = true"
          >
            <template #filters>
              <el-input
                v-model="taskFilters.keyword"
                placeholder="&#x641C;&#x7D22;&#x4EFB;&#x52A1;&#x540D;&#x79F0;&#x6216;&#x6458;&#x8981;"
                clearable
                class="toolbar-filter-input"
              />
              <el-select v-model="taskFilters.status" clearable placeholder="&#x72B6;&#x6001;" class="toolbar-filter-select">
                <el-option v-for="item in taskStatusOptions" :key="item" :label="item" :value="item" />
              </el-select>
              <el-select
                v-if="isAllScope"
                v-model="taskFilters.workspaceCode"
                clearable
                placeholder="&#x6240;&#x5C5E;&#x7A7A;&#x95F4;"
                class="toolbar-filter-select"
              >
                <el-option v-for="item in workspaces" :key="item.code" :label="item.name" :value="item.code" />
              </el-select>
              <el-button text @click="resetTaskFilters">
                <el-icon><RefreshRight /></el-icon>
                &#x91CD;&#x7F6E;
              </el-button>
            </template>
            <template #actions>
              <el-button v-if="canCreateData" type="primary" plain @click="openTaskCreate">
                <el-icon><Plus /></el-icon>
                &#x65B0;&#x5EFA;&#x4EFB;&#x52A1;
              </el-button>
            </template>
          </ListToolbar>
        </div>
        <el-table v-loading="loading" :data="filteredTasks" size="large">
          <template v-for="column in taskListToolbar.visibleColumns.value" :key="column.key">
            <el-table-column v-if="column.key === 'taskName'" prop="taskName" label="&#x4EFB;&#x52A1;&#x540D;&#x79F0;" min-width="220" />
            <el-table-column v-else-if="column.key === 'status'" prop="status" label="&#x72B6;&#x6001;" width="110" />
            <el-table-column v-else-if="column.key === 'summary'" prop="summary" label="&#x6458;&#x8981;" min-width="220" />
            <el-table-column v-else-if="column.key === 'workspaceName'" prop="workspaceName" label="&#x6240;&#x5C5E;&#x7A7A;&#x95F4;" width="130" />
          </template>
          <el-table-column label="&#x64CD;&#x4F5C;" width="250">
            <template #default="{ row }">
              <el-button text type="primary" @click="openTaskDetail(row.id)">&#x67E5;&#x770B;</el-button>
              <template v-if="canWriteRow(row.workspaceCode)">
                <el-button text type="primary" @click="openTaskEdit(row)">&#x7F16;&#x8F91;</el-button>
                <el-button text type="danger" @click="confirmDeleteTask(row)">&#x5220;&#x9664;</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </article>
    </div>

    <article class="panel-card">
      <div class="panel-header">
        <ListToolbar
          title="&#x6267;&#x884C;&#x62A5;&#x544A;"
          show-settings
          @settings="reportListToolbar.settingsVisible.value = true"
        >
          <template #filters>
            <el-input
              v-model="reportFilters.keyword"
              placeholder="&#x641C;&#x7D22;&#x62A5;&#x544A;&#x540D;&#x79F0;&#x6216;&#x5931;&#x8D25;&#x6458;&#x8981;"
              clearable
              class="toolbar-filter-input"
            />
            <el-select v-model="reportFilters.result" clearable placeholder="&#x7ED3;&#x679C;" class="toolbar-filter-select">
              <el-option v-for="item in reportResultOptions" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="reportFilters.logSource" clearable placeholder="&#x65E5;&#x5FD7;&#x6765;&#x6E90;" class="toolbar-filter-select">
              <el-option v-for="item in reportLogSourceOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select
              v-if="isAllScope"
              v-model="reportFilters.workspaceCode"
              clearable
              placeholder="&#x6240;&#x5C5E;&#x7A7A;&#x95F4;"
              class="toolbar-filter-select"
            >
              <el-option v-for="item in workspaces" :key="item.code" :label="item.name" :value="item.code" />
            </el-select>
            <el-button text @click="resetReportFilters">
              <el-icon><RefreshRight /></el-icon>
              &#x91CD;&#x7F6E;
            </el-button>
          </template>
          <template #actions>
            <el-button v-if="canCreateData" type="primary" plain @click="openReportCreate">
              <el-icon><Files /></el-icon>
              &#x65B0;&#x5EFA;&#x62A5;&#x544A;
            </el-button>
          </template>
        </ListToolbar>
      </div>
      <el-table v-loading="loading" :data="filteredReports" size="large">
        <template v-for="column in reportListToolbar.visibleColumns.value" :key="column.key">
          <el-table-column v-if="column.key === 'reportName'" prop="reportName" label="&#x62A5;&#x544A;&#x540D;&#x79F0;" min-width="220" />
          <el-table-column v-else-if="column.key === 'result'" prop="result" label="&#x7ED3;&#x679C;" width="100" />
          <el-table-column v-else-if="column.key === 'failureSummary'" prop="failureSummary" label="&#x5931;&#x8D25;&#x6458;&#x8981;" min-width="260" />
          <el-table-column v-else-if="column.key === 'workspaceName'" prop="workspaceName" label="&#x6240;&#x5C5E;&#x7A7A;&#x95F4;" width="130" />
        </template>
        <el-table-column label="&#x64CD;&#x4F5C;" width="320">
          <template #default="{ row }">
            <el-button text type="primary" @click="openReportDetail(row.id)">&#x67E5;&#x770B;</el-button>
            <template v-if="canWriteRow(row.workspaceCode)">
              <el-button text type="primary" @click="openReportEdit(row)">&#x7F16;&#x8F91;</el-button>
              <el-button text type="danger" @click="confirmDeleteReport(row)">&#x5220;&#x9664;</el-button>
              <el-button text type="warning" @click="openReportBugDialog(row)">&#x63D0;&#x7F3A;&#x9677;</el-button>
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
      :title="taskDialogMode === 'create' ? '&#x65B0;&#x5EFA;&#x4EFB;&#x52A1;' : '&#x7F16;&#x8F91;&#x4EFB;&#x52A1;'"
      width="620px"
    >
      <el-form label-width="100px">
        <el-form-item v-if="isAllScope" label="&#x76EE;&#x6807;&#x7A7A;&#x95F4;" required>
          <el-select v-model="taskForm.workspaceCode" placeholder="&#x8BF7;&#x9009;&#x62E9;&#x76EE;&#x6807;&#x7A7A;&#x95F4;">
            <el-option v-for="item in writableWorkspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="&#x4EFB;&#x52A1;&#x540D;&#x79F0;" required>
          <el-input v-model="taskForm.taskName" />
        </el-form-item>
        <el-form-item label="&#x7528;&#x4F8B;&#x6A21;&#x5757;">
          <el-select v-model="taskCaseModuleValue" clearable placeholder="&#x8BF7;&#x9009;&#x62E9;&#x6765;&#x6E90;&#x7528;&#x4F8B;&#x6A21;&#x5757;">
            <el-option
              v-for="item in taskCaseModuleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="&#x6267;&#x884C;&#x5F15;&#x64CE;">
          <el-input :model-value="engineCode" disabled />
        </el-form-item>
        <el-form-item label="&#x4EFB;&#x52A1;&#x72B6;&#x6001;" required>
          <el-select v-model="taskForm.status">
            <el-option v-for="item in taskStatusOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="&#x6458;&#x8981;">
          <el-input v-model="taskForm.summary" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialogVisible = false">&#x53D6;&#x6D88;</el-button>
        <el-button type="primary" :loading="saving" @click="submitTask">&#x4FDD;&#x5B58;</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="reportDialogVisible"
      :title="reportDialogMode === 'create' ? '&#x65B0;&#x5EFA;&#x62A5;&#x544A;' : '&#x7F16;&#x8F91;&#x62A5;&#x544A;'"
      width="680px"
    >
      <el-form label-width="100px">
        <el-form-item v-if="isAllScope" label="&#x76EE;&#x6807;&#x7A7A;&#x95F4;" required>
          <el-select v-model="reportForm.workspaceCode" placeholder="&#x8BF7;&#x9009;&#x62E9;&#x76EE;&#x6807;&#x7A7A;&#x95F4;">
            <el-option v-for="item in writableWorkspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="&#x5173;&#x8054;&#x4EFB;&#x52A1;" required>
          <el-select v-model="reportForm.taskId" placeholder="&#x8BF7;&#x9009;&#x62E9;&#x4EFB;&#x52A1;">
            <el-option
              v-for="item in reportTaskOptions"
              :key="item.id"
              :label="item.taskName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="&#x62A5;&#x544A;&#x540D;&#x79F0;" required>
          <el-input v-model="reportForm.reportName" />
        </el-form-item>
        <el-form-item label="&#x6267;&#x884C;&#x7ED3;&#x679C;" required>
          <el-select v-model="reportForm.result">
            <el-option v-for="item in reportResultOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="&#x65E5;&#x5FD7;&#x6765;&#x6E90;">
          <el-select v-model="reportForm.logSource">
            <el-option
              v-for="item in reportLogSourceOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="&#x5931;&#x8D25;&#x6458;&#x8981;">
          <el-input v-model="reportForm.failureSummary" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportDialogVisible = false">&#x53D6;&#x6D88;</el-button>
        <el-button type="primary" :loading="saving" @click="submitReport">&#x4FDD;&#x5B58;</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reportBugVisible" title="&#x4ECE;&#x62A5;&#x544A;&#x521B;&#x5EFA;&#x7F3A;&#x9677;" width="640px">
      <el-form label-width="90px">
        <el-form-item v-if="isAllScope" label="&#x76EE;&#x6807;&#x7A7A;&#x95F4;" required>
          <el-select v-model="reportBugState.workspaceCode">
            <el-option v-for="item in writableWorkspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="&#x6807;&#x9898;" required>
          <el-input v-model="reportBugState.title" />
        </el-form-item>
        <el-form-item label="&#x63CF;&#x8FF0;" required>
          <el-input v-model="reportBugState.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="&#x8D1F;&#x8D23;&#x4EBA;">
          <el-select v-model="reportBugState.assigneeId" clearable>
            <el-option v-for="item in users" :key="item.id" :label="item.displayName" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportBugVisible = false">&#x53D6;&#x6D88;</el-button>
        <el-button type="primary" :loading="saving" @click="submitReportBug">&#x63D0;&#x4EA4;</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="taskDrawerVisible" title="&#x4EFB;&#x52A1;&#x8BE6;&#x60C5;" size="720px">
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
              <div class="detail-title">&#x72B6;&#x6001;&#x6D41;&#x8F6C;</div>
              <p class="detail-body">&#x521B;&#x5EFA;&#x65F6;&#x95F4;&#xFF1A;{{ taskDetail.createdAt }}</p>
              <p class="detail-body">&#x66F4;&#x65B0;&#x65F6;&#x95F4;&#xFF1A;{{ taskDetail.updatedAt }}</p>
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
                  &#x8F6C;&#x4E3A; {{ status }}
                </el-button>
                <span v-if="!taskTransitionOptions.length" class="status-pill status-neutral">&#x7EC8;&#x6001;</span>
              </div>
            </div>
            <div class="detail-card">
              <div class="detail-title">&#x5173;&#x8054;&#x62A5;&#x544A;</div>
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
                  <el-button text type="primary" @click="openReportDetail(report.id)">&#x67E5;&#x770B;</el-button>
                </div>
              </div>
              <p v-else class="detail-body">&#x6682;&#x65E0;&#x5173;&#x8054;&#x62A5;&#x544A;</p>
            </div>
          </div>
        </template>
      </div>
    </el-drawer>

    <el-drawer v-model="reportDrawerVisible" title="&#x62A5;&#x544A;&#x8BE6;&#x60C5;" size="720px">
      <div v-loading="reportDetailLoading">
        <template v-if="reportDetail">
          <div class="detail-grid">
            <div class="detail-card">
              <div class="detail-title">{{ reportDetail.reportName }}</div>
              <div class="detail-meta">
                {{ reportDetail.workspaceName }} | {{ reportDetail.taskName }} | {{ reportDetail.result }} | {{ reportDetail.logSource }}
              </div>
              <p class="detail-body">&#x521B;&#x5EFA;&#x65F6;&#x95F4;&#xFF1A;{{ reportDetail.createdAt }}</p>
              <p class="detail-body">&#x66F4;&#x65B0;&#x65F6;&#x95F4;&#xFF1A;{{ reportDetail.updatedAt }}</p>
            </div>

            <div class="detail-card">
              <div class="panel-header">
                <div>
                  <div class="detail-title">&#x62A5;&#x544A;&#x5185;&#x5BB9;</div>
                  <div class="detail-meta">&#x652F;&#x6301;&#x7EF4;&#x62A4;&#x5931;&#x8D25;&#x6458;&#x8981;&#x4E0E;&#x6267;&#x884C;&#x65E5;&#x5FD7;&#x3002;</div>
                </div>
                <el-button
                  v-if="canEditCurrentReport"
                  type="primary"
                  size="small"
                  :loading="reportContentSaving"
                  @click="saveReportContent"
                >
                  &#x4FDD;&#x5B58;&#x5185;&#x5BB9;
                </el-button>
              </div>
              <el-form label-width="88px">
                <el-form-item label="&#x65E5;&#x5FD7;&#x6765;&#x6E90;">
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
                <el-form-item label="&#x5931;&#x8D25;&#x6458;&#x8981;">
                  <el-input
                    v-model="reportContentForm.failureSummary"
                    type="textarea"
                    :rows="3"
                    :disabled="!canEditCurrentReport"
                  />
                </el-form-item>
                <el-form-item label="&#x6267;&#x884C;&#x65E5;&#x5FD7;">
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
                  <div class="detail-title">&#x9644;&#x4EF6;</div>
                  <div class="detail-meta">&#x652F;&#x6301;&#x4E0A;&#x4F20;&#x3001;&#x4E0B;&#x8F7D;&#x548C;&#x5220;&#x9664;&#x5355;&#x4E2A;&#x9644;&#x4EF6;&#x3002;</div>
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
                  &#x4E0A;&#x4F20;&#x9644;&#x4EF6;
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
                      {{ attachment.contentType || '\u672A\u77E5\u7C7B\u578B' }} | {{ formatFileSize(attachment.fileSize) }}
                    </div>
                  </div>
                  <div class="toolbar-group">
                    <el-button
                      v-if="attachment.downloadUrl"
                      text
                      type="primary"
                      @click="downloadReportAttachment(attachment)"
                    >
                      &#x4E0B;&#x8F7D;
                    </el-button>
                    <el-button
                      v-if="canEditCurrentReport && attachment.id > 0"
                      text
                      type="danger"
                      :loading="reportAttachmentRemovingId === attachment.id"
                      @click="removeReportAttachment(attachment)"
                    >
                      &#x5220;&#x9664;
                    </el-button>
                  </div>
                </div>
              </div>
              <p v-else class="detail-body">&#x6682;&#x65E0;&#x9644;&#x4EF6;</p>
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
