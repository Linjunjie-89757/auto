<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  Check,
  CircleClose,
  CopyDocument,
  Download,
  FolderOpened,
  Memo,
  View,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { platformApi } from '../api/platform'
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
}

type DirectoryOption = {
  value: number | null
  label: string
}

const route = useRoute()
const router = useRouter()
const { workspaceCode } = useWorkspace()

const activeRecord = ref<AiGenerationTaskRecord | null>(null)
const casePreviewVisible = ref(false)
const adoptDialogVisible = ref(false)
const pathDialogVisible = ref(false)
const processDialogVisible = ref(false)
const activeCase = ref<DetailCaseRow | null>(null)
const directoryOptions = ref<DirectoryOption[]>([])
const loadingDirectories = ref(false)
const adopting = ref(false)
const savingPath = ref(false)
const requirementExpanded = ref(false)
const exporting = ref(false)
const selectedCaseIndexes = ref<number[]>([])
const adoptDialogMode = ref<'all' | 'selected'>('all')
let pollingTimer: number | null = null

const adoptForm = reactive({
  directoryId: null as number | null,
})
const adoptPathTouched = ref(false)

const pathForm = reactive({
  directoryId: null as number | null,
})
const pathTouched = ref(false)

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
  return record?.directoryName || '未设置默认路径'
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
  activeCase.value = row
  casePreviewVisible.value = true
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
    activeRecord.value = await patchAiGenerationRecord(workspaceCode.value, activeRecord.value.id, {
      directoryId: adoptForm.directoryId,
      directoryName: formatDirectoryNameFromOption(selectedOption) ?? activeRecord.value.directoryName,
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
  deleted.add(row.index)
  activeRecord.value = await patchAiGenerationRecord(workspaceCode.value, activeRecord.value.id, {
    deletedCaseIndexes: [...deleted],
  })
  ElMessage.success('用例已删除')
  await loadRecord()
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
    activeRecord.value = await patchAiGenerationRecord(workspaceCode.value, activeRecord.value.id, {
      adoptedCaseIndexes: [...adopted],
      savedCaseCount: adopted.size,
    })
    ElMessage.success('用例已采纳')
    await loadRecord()
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

onMounted(() => {
  void loadRecord()
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<template>
  <section class="detail-page-shell">
    <div v-if="activeRecord" class="detail-page-header">
      <div class="detail-page-header-main">
        <el-button class="back-button" text :icon="ArrowLeft" @click="goBack">返回记录页</el-button>
        <div class="detail-page-title">{{ activeRecord.requirementTitle }}</div>
        <div class="detail-page-meta">
          <span>{{ activeRecord.workspaceName }}</span>
          <span>{{ formatTime(activeRecord.createdAt) }}</span>
          <span class="status-pill" :class="getStatusClass(activeRecord.status)">{{ getStatusLabel(activeRecord.status) }}</span>
        </div>
      </div>

      <div class="detail-page-header-actions">
        <el-button :icon="View" @click="openProcessDialog">查看流程</el-button>
        <el-button type="primary" :icon="Download" :loading="exporting" @click="exportExcel">导出 Excel</el-button>
      </div>
    </div>

    <div v-if="activeRecord" class="panel-card detail-summary-card">
      <button class="detail-summary-toggle" type="button" @click="requirementExpanded = !requirementExpanded">
        <div class="detail-summary-header">
          <div>
            <div class="detail-summary-title-row">
              <Memo class="detail-summary-title-icon" />
              <span class="detail-summary-title">需求描述</span>
            </div>
            <div class="detail-summary-subtitle">
              {{ requirementExpanded ? '查看完整需求内容' : '点击展开查看完整需求内容' }}
            </div>
          </div>
          <component :is="requirementExpanded ? ArrowUp : ArrowDown" class="detail-summary-arrow" />
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
          <span class="detail-path-meta">
            当前采纳保存路径：{{ getDefaultDirectoryPath(activeRecord) }}
            <el-button class="path-edit-button" text @click="openPathDialog(activeRecord)">修改保存路径</el-button>
          </span>
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
      <el-table :data="availableCases" class="detail-case-table" border @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="52" />
        <el-table-column label="序号" type="index" width="72" align="center" />
        <el-table-column label="场景" min-width="240">
          <template #default="{ row }">
            <div class="case-cell-clamp">{{ formatCaseCellText(row.title) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="前置条件" min-width="220">
          <template #default="{ row }">
            <div class="case-cell-clamp">{{ formatCaseCellText(row.precondition) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作步骤" min-width="260">
          <template #default="{ row }">
            <div class="case-cell-clamp">{{ formatCaseCellText(row.steps) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="预期结果" min-width="240">
          <template #default="{ row }">
            <div class="case-cell-clamp">{{ formatCaseCellText(row.expectedResult) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="88" align="center">
          <template #default="{ row }">
            <span class="priority-chip" :class="`priority-${row.priority?.toLowerCase?.() || 'p3'}`">{{ row.priority }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <span class="status-pill" :class="row.adopted ? 'status-success' : 'status-neutral'">
              {{ row.adopted ? '已采纳' : '未采纳' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-action-row">
              <el-button type="primary" text @click="openCasePreview(row)">查看详情</el-button>
              <el-button type="success" text :disabled="row.adopted" @click="adoptSingleCase(row)">采纳</el-button>
              <el-button type="danger" text @click="deleteSingleCase(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-else class="panel-card detail-empty-card">
      <div class="detail-empty-title">任务记录不存在</div>
      <div class="detail-empty-text">这条 AI 生成任务可能已被删除，或当前空间下无法访问。</div>
    </div>

    <el-dialog v-model="casePreviewVisible" width="720px" destroy-on-close>
      <template #header>
        <div class="adopt-dialog-title">用例详情</div>
      </template>
      <template v-if="activeCase">
        <div class="case-preview-layout">
          <div class="case-preview-item">
            <div class="detail-summary-title">测试场景</div>
            <div class="detail-summary-content preview-content">{{ activeCase.title }}</div>
          </div>
          <div class="case-preview-item">
            <div class="detail-summary-title">前置条件</div>
            <div class="detail-summary-content preview-content">{{ activeCase.precondition || '-' }}</div>
          </div>
          <div class="case-preview-item">
            <div class="detail-summary-title">操作步骤</div>
            <div class="detail-summary-content preview-content">{{ activeCase.steps }}</div>
          </div>
          <div class="case-preview-item">
            <div class="detail-summary-title">预期结果</div>
            <div class="detail-summary-content preview-content">{{ activeCase.expectedResult }}</div>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="processDialogVisible" title="AI生成用例流程" width="760px" destroy-on-close>
      <template v-if="activeRecord">
        <div class="process-dialog-meta">
          <div>
            <div class="process-dialog-title">{{ activeRecord.requirementTitle }}</div>
            <div class="process-dialog-subtitle">
              {{ activeRecord.workspaceName }} / {{ activeRecord.outputMode === 'STREAM' ? '实时流式输出' : '完整输出' }}
            </div>
          </div>
          <span class="status-pill" :class="getStatusClass(activeRecord.status)">{{ getStatusLabel(activeRecord.status) }}</span>
        </div>

        <div class="process-step-list">
          <div
            v-for="step in processSteps"
            :key="step.index"
            class="process-step-card"
            :class="{
              'process-step-card-active': activeRecord.currentStep === step.index,
              'process-step-card-done': activeRecord.currentStep > step.index || activeRecord.status === 'COMPLETED',
              'process-step-card-failed': activeRecord.status === 'FAILED' && activeRecord.currentStep === step.index,
            }"
          >
            <div
              class="process-step-index"
              :class="{
                'process-step-index-active': activeRecord.currentStep === step.index && activeRecord.status !== 'FAILED',
                'process-step-index-done': activeRecord.currentStep > step.index || activeRecord.status === 'COMPLETED',
                'process-step-index-failed': activeRecord.status === 'FAILED' && activeRecord.currentStep === step.index,
              }"
            >
              {{ step.index }}
            </div>
            <div class="process-step-content">
              <div class="process-step-title">{{ step.title }}</div>
              <div class="process-step-desc">{{ step.description }}</div>
            </div>
          </div>
        </div>

        <div class="process-current-log">
          <div class="detail-summary-title">当前状态</div>
          <div class="process-current-text">{{ activeRecord.stepMessage }}</div>
          <div v-if="activeRecord.errorMessage" class="process-error-text">{{ activeRecord.errorMessage }}</div>
        </div>

        <div v-if="activeRecord.status === 'FAILED'" class="process-failure-card">
          <div class="detail-summary-title">失败位置</div>
          <div class="process-failure-stage">失败阶段：{{ getFailureStageLabel(activeRecord) }}</div>
          <div class="process-failure-text">{{ activeRecord.errorMessage || activeRecord.stepMessage }}</div>
        </div>
      </template>

      <template #footer>
        <div class="dialog-footer">
          <el-button
            v-if="activeRecord && ['PENDING', 'GENERATING', 'REVIEWING'].includes(activeRecord.status)"
            type="danger"
            @click="cancelGeneration"
          >
            取消生成
          </el-button>
          <el-button @click="processDialogVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>

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
  </section>
</template>

<style scoped>
.detail-page-shell,
.case-preview-layout {
  display: grid;
  gap: 16px;
}

.detail-page-header,
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
.detail-page-header {
  justify-content: space-between;
}

.detail-toolbar-card {
  padding: 0 20px;
}

.detail-page-header-main {
  display: grid;
  gap: 8px;
}

.detail-page-header-actions {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex-wrap: wrap;
}

.back-button {
  width: fit-content;
  padding-left: 0;
}

.detail-page-title {
  font-size: 30px;
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
}

.detail-summary-toggle {
  width: 100%;
  min-height: 90px;
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
  min-height: 90px;
}

.detail-summary-title-row {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}

.detail-summary-title,
.adopt-form-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1;
}

.detail-summary-title-icon {
  color: #f4a261;
  font-size: 14px;
}

.detail-summary-subtitle,
.adopt-dialog-copy,
.adopt-dialog-subcopy {
  font-size: 13px;
  color: #9aa4b2;
  line-height: 1;
}

.detail-summary-arrow {
  font-size: 15px;
  color: #98a2b3;
  margin-right: 2px;
}

.detail-summary-expanded {
  border-top: 1px solid rgba(226, 232, 240, 0.9);
  padding: 16px 18px 14px;
}

.detail-summary-content-shell {
  padding: 14px 14px 10px;
  border-radius: 0;
  background: #f7f9fc;
}

.detail-summary-content {
  padding: 14px 16px 18px;
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

.preview-content {
  max-height: none;
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

.process-dialog-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.process-dialog-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}

.process-dialog-subtitle,
.process-step-desc,
.process-current-text {
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-subtle);
}

.process-step-list {
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.process-step-card {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  padding: 14px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.82);
}

.process-step-card-active {
  border-color: rgba(36, 107, 255, 0.36);
  background: rgba(233, 240, 255, 0.82);
}

.process-step-card-done {
  border-color: rgba(20, 163, 109, 0.22);
}

.process-step-card-failed {
  border-color: rgba(240, 68, 56, 0.26);
  background: rgba(254, 242, 242, 0.92);
}

.process-step-index {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  font-size: 14px;
  font-weight: 700;
  color: var(--text-main);
}

.process-step-index-active,
.process-step-index-done {
  background: #2f88ff;
  color: #ffffff;
}

.process-step-index-failed {
  background: #f04438;
  color: #ffffff;
}

.process-current-log {
  margin-top: 18px;
  padding: 14px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.84);
}

.process-error-text {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--danger);
}

.process-failure-card {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid rgba(240, 68, 56, 0.18);
  border-radius: 10px;
  background: rgba(254, 242, 242, 0.96);
}

.process-failure-stage {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #b42318;
}

.process-failure-text {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: #7a271a;
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

.detail-path-meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
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

.path-edit-button {
  padding: 0;
  font-size: 13px;
  font-weight: 600;
  color: #175cd3;
}

.detail-toolbar-actions {
  gap: 14px;
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
