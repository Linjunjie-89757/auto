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
  Delete,
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
import type { AiGeneratedCase, CaseDirectoryNode, CreateCasePayload } from '../types/api'
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

type DirectoryOption = {
  value: number | null
  label: string
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
  { key: 'status', label: '状态', defaultVisible: true },
  { key: 'manualEdited', label: '人工修改', defaultVisible: false },
  { key: 'manualEditedByName', label: '操作人', defaultVisible: false },
]

const detailTableSettings = useTableSettings({
  storageKey: 'ai-record-detail-table-settings-v1',
  columns: detailTableColumns,
})

const activeRecord = ref<AiGenerationTaskRecord | null>(null)
const casePreviewVisible = ref(false)
const adoptDialogVisible = ref(false)
const pathDialogVisible = ref(false)
const processDialogVisible = ref(false)
const activeCaseCursor = ref(-1)
const directoryOptions = ref<DirectoryOption[]>([])
const loadingDirectories = ref(false)
const adopting = ref(false)
const savingPath = ref(false)
const savingCaseEdit = ref(false)
const requirementExpanded = ref(false)
const exporting = ref(false)
const selectedCaseIndexes = ref<number[]>([])
const adoptDialogMode = ref<'all' | 'selected'>('all')
const casePreviewEditing = ref(false)
let pollingTimer: number | null = null

const adoptForm = reactive({
  directoryId: null as number | null,
})
const adoptPathTouched = ref(false)

const pathForm = reactive({
  directoryId: null as number | null,
})
const pathTouched = ref(false)
const caseEditForm = reactive({
  title: '',
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

const availableCases = computed(() => detailCases.value.filter(item => !item.deleted))
const activeCase = computed<DetailCaseRow | null>(() => availableCases.value[activeCaseCursor.value] ?? null)
const activeCaseDisplayIndex = computed(() => (activeCase.value ? activeCaseCursor.value + 1 : 0))
const canPreviewPreviousCase = computed(() => activeCaseCursor.value > 0)
const canPreviewNextCase = computed(() => activeCaseCursor.value >= 0 && activeCaseCursor.value < availableCases.value.length - 1)
const adoptableCases = computed(() => availableCases.value.filter(item => !item.adopted))
const selectedCases = computed(() => availableCases.value.filter(item => selectedCaseIndexes.value.includes(item.index)))
const selectedAdoptableCases = computed(() => selectedCases.value.filter(item => !item.adopted))
const selectedCount = computed(() => selectedCases.value.length)
const adoptDialogCases = computed(() => (
  adoptDialogMode.value === 'selected' ? selectedAdoptableCases.value : adoptableCases.value
))

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

async function loadRecord() {
  const taskId = route.params.taskId?.toString()
  if (!taskId) {
    activeRecord.value = null
    return
  }
  try {
    activeRecord.value = await getAiGenerationRecord(workspaceCode.value, taskId)
    if (activeRecord.value && ['PENDING', 'GENERATING', 'REVIEWING'].includes(activeRecord.value.status)) {
      startPolling()
    } else {
      stopPolling()
    }
  } catch (error) {
    ElMessage.error((error as Error).message)
    activeRecord.value = null
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
  return row.savedDirectoryName || (row.adopted ? getDefaultDirectoryPath(activeRecord.value) : '-')
}

function handleSelectionChange(rows: DetailCaseRow[]) {
  selectedCaseIndexes.value = rows.map(item => item.index)
}

async function loadDirectoryOptions(record: AiGenerationTaskRecord) {
  loadingDirectories.value = true
  try {
    const workspaces = await platformApi.getCaseDirectories(record.workspaceCode)
    const current = workspaces.find(item => item.workspaceCode === record.workspaceCode)
    directoryOptions.value = flattenDirectories(current?.children ?? [])
    const fallbackOption = directoryOptions.value.find(item => item.label === normalizeDirectoryLabel(record.directoryName))
    adoptForm.directoryId = record.directoryId ?? fallbackOption?.value ?? null
  } catch (error) {
    directoryOptions.value = []
    ElMessage.error((error as Error).message)
  } finally {
    loadingDirectories.value = false
  }
}

function openCasePreview(row: DetailCaseRow) {
  activeCaseCursor.value = availableCases.value.findIndex(item => item.index === row.index)
  casePreviewEditing.value = false
  syncCaseEditForm(row)
  casePreviewVisible.value = true
}

function syncCaseEditForm(row: DetailCaseRow | null) {
  caseEditForm.title = row?.title ?? ''
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
  await loadDirectoryOptions(activeRecord.value)
  pathForm.directoryId = adoptForm.directoryId
  pathDialogVisible.value = true
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
    const selectedOption = directoryOptions.value.find(item => item.value === pathForm.directoryId)
    activeRecord.value = await patchAiGenerationRecord(workspaceCode.value, activeRecord.value.id, {
      directoryId: pathForm.directoryId,
      directoryName: formatDirectoryNameFromOption(selectedOption) ?? activeRecord.value.directoryName,
    })
    adoptForm.directoryId = pathForm.directoryId
    pathDialogVisible.value = false
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
    adoptDialogCases.value.forEach(item => adopted.add(item.index))
    const selectedOption = directoryOptions.value.find(item => item.value === adoptForm.directoryId)
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

function updatePreviewCursorAfterDelete(deletedIndex: number, previousCursor: number) {
  if (!casePreviewVisible.value || activeCase.value?.index !== deletedIndex) {
    return
  }
  if (!availableCases.value.length) {
    casePreviewVisible.value = false
    casePreviewEditing.value = false
    activeCaseCursor.value = -1
    return
  }
  activeCaseCursor.value = Math.min(previousCursor, availableCases.value.length - 1)
  casePreviewEditing.value = false
  syncCaseEditForm(activeCase.value)
}

async function deleteSingleCase(row: DetailCaseRow) {
  if (!activeRecord.value) {
    return
  }
  await ElMessageBox.confirm('确定删除这条生成用例吗？', '删除用例', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })

  const deleted = new Set(activeRecord.value.deletedCaseIndexes ?? [])
  const previousCursor = availableCases.value.findIndex(item => item.index === row.index)
  deleted.add(row.index)
  activeRecord.value = await patchAiGenerationRecord(workspaceCode.value, activeRecord.value.id, {
    deletedCaseIndexes: [...deleted],
  })
  selectedCaseIndexes.value = selectedCaseIndexes.value.filter(index => index !== row.index)
  updatePreviewCursorAfterDelete(row.index, previousCursor)
  ElMessage.success('用例已删除')
}

async function deleteSelectedCases() {
  if (!activeRecord.value) {
    ElMessage.warning('当前任务记录不存在，请刷新后重试')
    return
  }
  if (!selectedCount.value) {
    ElMessage.info('请先勾选需要删除的用例')
    return
  }

  const deleteCount = selectedCount.value
  await ElMessageBox.confirm(
    `确定删除已选中的 ${deleteCount} 条生成用例吗？`,
    '批量删除用例',
    {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    },
  )

  const deleted = new Set(activeRecord.value.deletedCaseIndexes ?? [])
  selectedCaseIndexes.value.forEach(index => deleted.add(index))
  activeRecord.value = await patchAiGenerationRecord(workspaceCode.value, activeRecord.value.id, {
    deletedCaseIndexes: [...deleted],
  })
  selectedCaseIndexes.value = []
  ElMessage.success(`已删除 ${deleteCount} 条生成用例`)
  await loadRecord()
}

async function adoptSingleCase(row: DetailCaseRow) {
  if (!activeRecord.value || row.adopted || row.deleted) {
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
    adopted.add(row.index)
    const savedDirectoryName = activeRecord.value.directoryName ?? getDefaultDirectoryPath(activeRecord.value)
    activeRecord.value = await patchAiGenerationRecord(workspaceCode.value, activeRecord.value.id, {
      generatedCases: activeRecord.value.generatedCases.map((item, index) => (
        index === row.index ? { ...item, savedDirectoryName } : item
      )),
      adoptedCaseIndexes: [...adopted],
      savedCaseCount: adopted.size,
    })
    ElMessage.success('用例已采纳')
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
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
    const rows = availableCases.value.map((item) => `
      <tr>
        <td>${escapeHtml(`CASE_${String(item.index + 1).padStart(3, '0')}`)}</td>
        <td>${escapeHtml(item.title ?? '')}</td>
        <td>${escapeHtml(item.precondition ?? '')}</td>
        <td>${escapeHtml(item.steps ?? '')}</td>
        <td>${escapeHtml(item.expectedResult ?? '')}</td>
        <td>${escapeHtml(item.priority ?? '')}</td>
        <td>${escapeHtml(item.adopted ? '已采纳' : '未采纳')}</td>
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
              <th>采纳状态</th>
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
  void loadRecord()
})

onBeforeUnmount(() => {
  stopPolling()
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

    <div v-if="activeRecord?.status === 'FAILED'" class="panel-card failure-detail-card">
      <div class="failure-detail-header">
        <div class="failure-detail-title">任务失败详情</div>
        <span class="status-pill status-danger">失败</span>
      </div>
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
    </div>

    <div v-if="activeRecord" class="panel-card detail-toolbar-card">
      <div class="detail-toolbar-row">
        <div class="detail-toolbar-meta">
          <span>生成用例数：{{ activeRecord.generatedCases.length }}</span>
          <span>当前用例数：{{ availableCases.length }}</span>
          <span>已采纳：{{ activeRecord.adoptedCaseIndexes.length }}</span>
          <span>已删除：{{ activeRecord.deletedCaseIndexes.length }}</span>
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
            :disabled="selectedCount === 0"
            :class="{ 'is-active': selectedCount > 0 }"
            @click="deleteSelectedCases"
          >
            批量删除（{{ selectedCount }}）
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
            <el-table-column v-else-if="column.key === 'status'" label="状态" width="100" align="center">
              <template #default="{ row }">
                <span class="status-pill" :class="row.adopted ? 'status-success' : 'status-neutral'">
                  {{ row.adopted ? '已采纳' : '未采纳' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column v-else-if="column.key === 'manualEdited'" label="人工修改" width="88" align="center">
              <template #default="{ row }">
                <span class="detail-cell-text">{{ row.manualEdited ? '是' : '否' }}</span>
              </template>
            </el-table-column>
            <el-table-column v-else-if="column.key === 'manualEditedByName'" label="操作人" width="120" show-overflow-tooltip>
              <template #default="{ row }">
                <span class="detail-cell-text">{{ row.manualEditedByName || '-' }}</span>
              </template>
            </el-table-column>
          </template>
          <el-table-column width="168" fixed="right" align="center">
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
                <el-button type="primary" text @click="openCasePreview(row)">查看详情</el-button>
                <el-button type="success" text :disabled="row.adopted" @click="adoptSingleCase(row)">采纳</el-button>
                <el-button type="danger" text @click="deleteSingleCase(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <div v-else class="panel-card detail-empty-card">
      <div class="detail-empty-title">任务记录不存在</div>
      <div class="detail-empty-text">这条 AI 生成任务可能已被删除，或当前空间下无法访问。</div>
    </div>

    <el-dialog v-model="casePreviewVisible" width="920px" destroy-on-close class="detail-preview-dialog">
      <template #header>
        <div class="detail-preview-header detail-preview-header-review">
          <div>
            <div class="adopt-dialog-title">用例详情</div>
            <div class="detail-preview-subtitle">逐条审阅 AI 生成用例，可直接编辑后保存或采纳。</div>
          </div>
          <div class="case-review-nav" v-if="availableCases.length">
            <el-button class="case-review-nav-button" :icon="ArrowLeft" :disabled="!canPreviewPreviousCase" @click="moveCasePreview(-1)">
              上一条
            </el-button>
            <div class="case-review-nav-count">第 {{ activeCaseDisplayIndex }} 条 / 共 {{ availableCases.length }} 条</div>
            <el-button class="case-review-nav-button" :icon="ArrowRight" :disabled="!canPreviewNextCase" @click="moveCasePreview(1)">
              下一条
            </el-button>
          </div>
        </div>
      </template>
      <template v-if="activeCase">
        <div class="detail-preview-layout">
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
      <template #footer>
        <div class="dialog-footer case-review-footer">
          <div class="case-review-footer-left">
            <el-button v-if="casePreviewEditing" :icon="CircleClose" @click="cancelCaseEditing">取消编辑</el-button>
            <el-button
              v-else
              :icon="Memo"
              @click="startCaseEditing"
            >
              编辑
            </el-button>
            <el-button
              type="success"
              :icon="Check"
              :disabled="!activeCase || activeCase.adopted"
              @click="activeCase && adoptSingleCase(activeCase)"
            >
              {{ activeCase?.adopted ? '已采纳' : '采纳' }}
            </el-button>
            <el-button
              type="danger"
              plain
              :icon="Delete"
              :disabled="!activeCase"
              @click="activeCase && deleteSingleCase(activeCase)"
            >
              删除
            </el-button>
          </div>
          <div class="case-review-footer-right">
            <el-button
              v-if="casePreviewEditing"
              type="primary"
              :icon="Check"
              :loading="savingCaseEdit"
              @click="saveCasePreviewEdit"
            >
              保存
            </el-button>
            <el-button @click="casePreviewVisible = false">关闭</el-button>
          </div>
        </div>
      </template>
    </el-dialog>

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
                  :loading="loadingDirectories"
                  placeholder="请选择保存路径"
                  @change="adoptPathTouched = true"
                >
                  <el-option
                    v-for="item in directoryOptions"
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

    <el-dialog v-model="pathDialogVisible" width="620px" destroy-on-close class="adopt-dialog">
      <template #header>
        <div class="adopt-dialog-title">修改保存路径</div>
      </template>
      <template v-if="activeRecord">
        <div class="adopt-dialog-body">
          <div class="adopt-dialog-notice">
            <div class="adopt-dialog-copy">
              为任务“{{ activeRecord.requirementTitle }}”调整后续采纳时使用的默认保存路径。
            </div>
            <div class="adopt-dialog-subcopy">
              修改后会作为本任务后续“采纳 / 批量采纳”的默认保存路径，不影响已保存的用例。
            </div>
          </div>
          <div class="adopt-dialog-form-card">
            <div class="adopt-form-title">路径配置</div>
            <div class="path-dialog-current">当前保存路径：{{ getDefaultDirectoryPath(activeRecord) }}</div>
            <el-form label-position="top">
              <el-form-item required>
                <template #label>
                  <span>新保存路径 <span class="dialog-required">*</span></span>
                </template>
                <el-select
                  v-model="pathForm.directoryId"
                  :class="{ 'is-invalid-select': pathTouched && pathForm.directoryId == null }"
                  :loading="loadingDirectories"
                  placeholder="请选择新的保存路径"
                  @change="pathTouched = true"
                >
                  <el-option
                    v-for="item in directoryOptions"
                    :key="`path-${String(item.value)}`"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <div v-if="pathTouched && pathForm.directoryId == null" class="dialog-field-error">请选择保存路径</div>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </template>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="pathDialogVisible = false">取消</el-button>
          <el-button type="primary" :icon="FolderOpened" :loading="savingPath" @click="confirmPathChange">确认修改</el-button>
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

.failure-detail-card {
  padding: 18px 20px;
  border: 1px solid rgba(240, 68, 56, 0.14);
  background: rgba(254, 242, 242, 0.72);
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
  gap: 4px;
}

.detail-preview-header-review {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 16px;
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

.case-review-nav {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 999px;
  background: #f8fafc;
}

.case-review-nav-button {
  min-width: 96px;
  height: 34px;
  border-radius: 999px;
}

.case-review-nav-count {
  min-width: 156px;
  padding: 0 8px;
  text-align: center;
  font-size: 13px;
  font-weight: 700;
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
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
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
  grid-column: 1 / -1;
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

.detail-preview-dialog :deep(.el-textarea__inner),
.detail-preview-dialog :deep(.el-input__wrapper) {
  border-radius: 10px;
}

.detail-preview-dialog :deep(.el-textarea__inner) {
  min-height: 132px !important;
  line-height: 1.7;
}

.case-review-footer {
  justify-content: space-between;
}

.case-review-footer-left,
.case-review-footer-right {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
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
  justify-content: center;
  flex-wrap: wrap;
}

.detail-preview-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
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

.status-neutral {
  background: rgba(242, 244, 247, 0.96);
  color: #475467;
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

  .failure-detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
