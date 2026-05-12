<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { FolderOpened, RefreshRight, Setting } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { platformApi } from '../api/platform'
import AiGenerationProcessDialog from '../components/AiGenerationProcessDialog.vue'
import TableSettingsDrawer from '../components/TableSettingsDrawer.vue'
import { useTableSettings, type TableSettingsColumn } from '../composables/useTableSettings'
import { useWorkspace } from '../composables/useWorkspace'
import type { CaseDirectoryNode, CreateCasePayload } from '../types/api'
import type { AiGenerationTaskRecord } from '../utils/caseAiGenerationRecords'
import {
  cancelAiGenerationRecord,
  getAiGenerationRecord,
  listAiGenerationRecords,
  patchAiGenerationRecord,
  removeAiGenerationRecord,
  retryAiGenerationRecord,
} from '../utils/caseAiGenerationRecords'

type DirectoryOption = {
  value: number | null
  label: string
}

const router = useRouter()
const { workspaceCode } = useWorkspace()

const records = ref<AiGenerationTaskRecord[]>([])
const loading = ref(false)
const processDialogVisible = ref(false)
const adoptDialogVisible = ref(false)
const activeRecord = ref<AiGenerationTaskRecord | null>(null)
const statusFilter = ref('')
const directoryOptions = ref<DirectoryOption[]>([])
const loadingDirectories = ref(false)
const adopting = ref(false)
const retryingRecordId = ref('')
const PAGE_SIZE_OPTIONS = [10, 20, 30, 40, 50]
const pageNo = ref(1)
let pollingTimer: number | null = null

const adoptForm = reactive({
  directoryId: null as number | null,
})
const adoptPathTouched = ref(false)

const recordColumns: TableSettingsColumn[] = [
  { key: 'taskId', label: '任务 ID', required: true, defaultVisible: true },
  { key: 'workspaceName', label: '所属空间', defaultVisible: true },
  { key: 'requirementTitle', label: '关联需求', required: true, defaultVisible: true },
  { key: 'outputMode', label: '输出模式', defaultVisible: true },
  { key: 'status', label: '状态', defaultVisible: true },
  { key: 'generatedCount', label: '生成用例数', defaultVisible: true },
  { key: 'savedCaseCount', label: '已采纳数', defaultVisible: false },
  { key: 'createdAt', label: '生成时间', defaultVisible: true },
  { key: 'createdByName', label: '创建人', defaultVisible: false },
  { key: 'updatedAt', label: '更新时间', defaultVisible: false },
  { key: 'updatedByName', label: '更新人', defaultVisible: false },
  { key: 'directoryName', label: '当前采纳路径', defaultVisible: false },
]

const recordTableSettings = useTableSettings({
  storageKey: 'ai-record-table-settings-v2',
  columns: recordColumns,
  pageSizeEnabled: true,
  defaultPageSize: 10,
  pageSizeOptions: PAGE_SIZE_OPTIONS,
})

const processSteps = [
  { index: 1 as const, title: '任务已创建', description: '已记录需求内容、目标空间和输出模式。' },
  { index: 2 as const, title: 'AI 生成用例', description: '正在根据需求生成候选测试用例。' },
  { index: 3 as const, title: 'AI 自动评审', description: '正在进行自动评审和建议汇总。' },
  { index: 4 as const, title: '任务完成', description: '生成结果已进入 AI 生成用例记录页。' },
]

const filteredRecords = computed(() => {
  if (!statusFilter.value) {
    return records.value
  }
  return records.value.filter(item => item.status === statusFilter.value)
})

const pageSize = recordTableSettings.pageSize
const total = computed(() => filteredRecords.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const pagedRecords = computed(() => {
  const start = (pageNo.value - 1) * pageSize.value
  return filteredRecords.value.slice(start, start + pageSize.value)
})

const stats = computed(() => ({
  total: records.value.length,
  completed: records.value.filter(item => item.status === 'COMPLETED').length,
  running: records.value.filter(item => ['PENDING', 'GENERATING', 'REVIEWING'].includes(item.status)).length,
  failed: records.value.filter(item => item.status === 'FAILED').length,
}))

const adoptableCases = computed(() => {
  const record = activeRecord.value
  if (!record) {
    return []
  }
  const adoptedIndexes = new Set(record.adoptedCaseIndexes ?? [])
  const deletedIndexes = new Set(record.deletedCaseIndexes ?? [])
  return record.generatedCases
    .map((item, index) => ({ ...item, index }))
    .filter(item => !adoptedIndexes.has(item.index) && !deletedIndexes.has(item.index))
})

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

function makeTaskCode(recordId: string) {
  return recordId
}

function formatTime(value: string | null) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function getDefaultDirectoryPath(record: AiGenerationTaskRecord) {
  return record.directoryName || '未设置默认路径'
}

function getOutputModeLabel(outputMode: AiGenerationTaskRecord['outputMode']) {
  return outputMode === 'STREAM' ? '实时流式输出' : '完整输出'
}

function getPrimaryActionLabel(status: AiGenerationTaskRecord['status']) {
  if (status === 'COMPLETED') {
    return '全部采纳'
  }
  if (status === 'FAILED') {
    return '重新生成'
  }
  return '查看流程'
}

function getPrimaryActionType(status: AiGenerationTaskRecord['status']) {
  if (status === 'COMPLETED') {
    return 'success'
  }
  if (status === 'FAILED') {
    return 'warning'
  }
  return 'primary'
}

function isRunningStatus(status: AiGenerationTaskRecord['status']) {
  return ['PENDING', 'GENERATING', 'REVIEWING'].includes(status)
}

function handlePageChange(value: number) {
  pageNo.value = value
}

function updatePageSizeSetting(size: number) {
  recordTableSettings.updatePageSize(size)
  pageNo.value = 1
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

function startPolling() {
  stopPolling()
  pollingTimer = window.setInterval(() => {
    void loadRecords()
  }, 2500)
}

function stopPolling() {
  if (pollingTimer != null) {
    window.clearInterval(pollingTimer)
    pollingTimer = null
  }
}

async function loadRecords() {
  loading.value = true
  try {
    records.value = await listAiGenerationRecords(workspaceCode.value)
    if (activeRecord.value) {
      activeRecord.value = records.value.find(item => item.id === activeRecord.value?.id) ?? activeRecord.value
    }
    const maxPage = Math.max(1, Math.ceil(filteredRecords.value.length / pageSize.value))
    if (pageNo.value > maxPage) {
      pageNo.value = maxPage
    }
    if (records.value.some(item => ['PENDING', 'GENERATING', 'REVIEWING'].includes(item.status))) {
      startPolling()
    } else {
      stopPolling()
    }
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function persistRecordPatch(recordId: string, patch: Partial<AiGenerationTaskRecord>) {
  const nextRecord = await patchAiGenerationRecord(workspaceCode.value, recordId, {
    directoryId: patch.directoryId,
    directoryName: patch.directoryName,
    adoptedCaseIndexes: patch.adoptedCaseIndexes,
    deletedCaseIndexes: patch.deletedCaseIndexes,
    savedCaseCount: patch.savedCaseCount,
  })
  if (activeRecord.value?.id === recordId) {
    activeRecord.value = nextRecord
  }
  await loadRecords()
  return nextRecord
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

function openDetail(record: AiGenerationTaskRecord) {
  router.push({
    name: 'cases-ai-record-detail',
    params: { taskId: record.id },
    query: { workspace: workspaceCode.value },
  })
}

async function openProcessDialog(record: AiGenerationTaskRecord) {
  activeRecord.value = await getAiGenerationRecord(workspaceCode.value, record.id) ?? record
  if (!activeRecord.value) {
    ElMessage.warning('当前任务记录不存在，请刷新后重试')
    return
  }
  processDialogVisible.value = true
}

function handlePrimaryAction(record: AiGenerationTaskRecord) {
  if (record.status === 'COMPLETED') {
    void openAdoptDialog(record)
    return
  }
  if (record.status === 'FAILED') {
    void retryFailedTask(record)
    return
  }
  void openProcessDialog(record)
}

async function retryFailedTask(record: AiGenerationTaskRecord) {
  retryingRecordId.value = record.id
  try {
    activeRecord.value = await retryAiGenerationRecord(workspaceCode.value, record.id)
    processDialogVisible.value = true
    await loadRecords()
    ElMessage.success('已创建新的重试任务，后台会继续执行')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    retryingRecordId.value = ''
  }
}

async function cancelGeneration(record: AiGenerationTaskRecord) {
  await ElMessageBox.confirm(
    '确认取消当前 AI 生成任务吗？取消后后续步骤将不再继续执行。',
    '取消生成任务',
    {
      type: 'warning',
      confirmButtonText: '取消生成',
      cancelButtonText: '返回',
    },
  )

  activeRecord.value = await cancelAiGenerationRecord(workspaceCode.value, record.id)
  await loadRecords()
  ElMessage.success('已取消当前生成任务')
}

async function openAdoptDialog(record: AiGenerationTaskRecord) {
  activeRecord.value = await getAiGenerationRecord(workspaceCode.value, record.id) ?? record
  if (!activeRecord.value) {
    return
  }
  adoptPathTouched.value = false
  await loadDirectoryOptions(activeRecord.value)
  adoptDialogVisible.value = true
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
  if (!adoptableCases.value.length) {
    ElMessage.info('当前没有可采纳的用例')
    return
  }

  const adoptCount = adoptableCases.value.length
  adopting.value = true
  try {
    for (const item of adoptableCases.value) {
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
    adoptableCases.value.forEach(item => adopted.add(item.index))
    await persistRecordPatch(activeRecord.value.id, {
      directoryId: adoptForm.directoryId,
      adoptedCaseIndexes: [...adopted],
      savedCaseCount: adopted.size,
    })

    adoptDialogVisible.value = false
    ElMessage.success(`已采纳 ${adoptCount} 条用例到用例管理`)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    adopting.value = false
  }
}

async function deleteTask(record: AiGenerationTaskRecord) {
  await ElMessageBox.confirm(
    '确定删除本次生成任务和所有用例吗？',
    '删除生成任务',
    {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    },
  )

  await removeAiGenerationRecord(workspaceCode.value, record.id)
  if (activeRecord.value?.id === record.id) {
    adoptDialogVisible.value = false
    activeRecord.value = null
  }
  await loadRecords()
  ElMessage.success('生成任务已删除')
}


watch(filteredRecords, () => {
  const maxPage = Math.max(1, Math.ceil(filteredRecords.value.length / pageSize.value))
  if (pageNo.value > maxPage) {
    pageNo.value = maxPage
  }
  if (pageNo.value < 1) {
    pageNo.value = 1
  }
})

onMounted(() => {
  recordTableSettings.load()
  void loadRecords()
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<template>
  <section class="ai-record-page">
    <div class="panel-card record-filter-card">
      <div class="record-filter-row">
        <div class="record-filter-item">
          <div class="record-filter-label">状态筛选：</div>
          <el-select v-model="statusFilter" clearable placeholder="全部状态" style="width: 180px">
            <el-option label="需求解析中" value="PENDING" />
            <el-option label="生成中" value="GENERATING" />
            <el-option label="评审中" value="REVIEWING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="失败" value="FAILED" />
          </el-select>
          <el-button :icon="RefreshRight" :loading="loading" @click="loadRecords">刷新</el-button>
        </div>
      </div>
    </div>

    <template v-if="records.length">
      <div class="panel-card record-stats-card">
        <div class="stats-row">
          <div class="stats-item">
            <div class="stats-value">{{ stats.total }}</div>
            <div class="stats-label">任务总数</div>
          </div>
          <div class="stats-item">
            <div class="stats-value">{{ stats.completed }}</div>
            <div class="stats-label">已完成</div>
          </div>
          <div class="stats-item">
            <div class="stats-value">{{ stats.running }}</div>
            <div class="stats-label">进行中</div>
          </div>
          <div class="stats-item">
            <div class="stats-value">{{ stats.failed }}</div>
            <div class="stats-label">失败</div>
          </div>
        </div>
      </div>

      <div class="panel-card record-table-card">
        <div class="record-table-wrap">
          <el-table :data="pagedRecords" class="record-table" border stripe>
            <el-table-column type="index" label="序号" width="72" align="center" />
            <template v-for="column in recordTableSettings.visibleColumns.value" :key="column.key">
              <el-table-column v-if="column.key === 'taskId'" label="任务 ID" min-width="180">
                <template #default="{ row }">
                  <span class="task-code-text">{{ makeTaskCode(row.id) }}</span>
                </template>
              </el-table-column>
              <el-table-column v-else-if="column.key === 'workspaceName'" label="所属空间" min-width="140" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="workspace-text">{{ row.workspaceName || row.workspaceCode }}</span>
                </template>
              </el-table-column>
              <el-table-column v-else-if="column.key === 'requirementTitle'" label="关联需求" min-width="360" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="record-requirement-title">{{ row.requirementTitle }}</div>
                </template>
              </el-table-column>
              <el-table-column v-else-if="column.key === 'outputMode'" label="输出模式" width="132" align="center">
                <template #default="{ row }">
                  <span>{{ getOutputModeLabel(row.outputMode) }}</span>
                </template>
              </el-table-column>
              <el-table-column v-else-if="column.key === 'status'" label="状态" width="110" align="center">
                <template #default="{ row }">
                  <span class="status-pill" :class="getStatusClass(row.status)">{{ getStatusLabel(row.status) }}</span>
                </template>
              </el-table-column>
              <el-table-column v-else-if="column.key === 'generatedCount'" label="生成用例数" width="110" align="center">
                <template #default="{ row }">
                  <span class="case-count-pill">{{ row.generatedCount }}</span>
                </template>
              </el-table-column>
              <el-table-column v-else-if="column.key === 'savedCaseCount'" label="已采纳数" width="98" align="center">
                <template #default="{ row }">
                  <span class="case-count-pill case-count-pill-success">{{ row.savedCaseCount }}</span>
                </template>
              </el-table-column>
              <el-table-column v-else-if="column.key === 'createdAt'" label="生成时间" min-width="168">
                <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column v-else-if="column.key === 'createdByName'" label="创建人" min-width="120" align="center">
                <template #default="{ row }">{{ row.createdByName || '-' }}</template>
              </el-table-column>
              <el-table-column v-else-if="column.key === 'updatedAt'" label="更新时间" min-width="168">
                <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
              </el-table-column>
              <el-table-column v-else-if="column.key === 'updatedByName'" label="更新人" min-width="120" align="center">
                <template #default="{ row }">{{ row.updatedByName || '-' }}</template>
              </el-table-column>
              <el-table-column v-else-if="column.key === 'directoryName'" label="当前采纳路径" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="record-muted-text">{{ getDefaultDirectoryPath(row) }}</span>
                </template>
              </el-table-column>
            </template>
            <el-table-column width="240" fixed="right" align="center">
              <template #header>
                <div class="table-action-header">
                  <span>操作</span>
                  <el-button text class="table-settings-trigger" @click="recordTableSettings.settingsVisible.value = true">
                    <el-icon class="table-action-header-icon"><Setting /></el-icon>
                  </el-button>
                </div>
              </template>
              <template #default="{ row }">
                <div class="table-action-row table-action-row-text">
                  <el-button text type="primary" @click="openDetail(row)">查看详情</el-button>
                  <el-button
                    text
                    :type="getPrimaryActionType(row.status)"
                    :loading="row.status === 'FAILED' && retryingRecordId === row.id"
                    @click="handlePrimaryAction(row)"
                  >
                    {{ getPrimaryActionLabel(row.status) }}
                  </el-button>
                  <el-button text type="danger" @click="deleteTask(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="table-pagination">
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
              @current-change="handlePageChange"
              @size-change="updatePageSizeSetting"
            />
          </div>
        </div>
      </div>
    </template>
    <div v-else class="panel-card record-empty-card">
      <div class="record-empty-content">
        <div class="record-empty-icon">📝</div>
        <div class="record-empty-title">暂无生成任务</div>
        <div class="record-empty-text">
          还没有 AI生成用例任务，去
          <router-link class="record-empty-link" :to="{ name: 'cases-ai-generate', query: { workspace: workspaceCode } }">AI生成用例</router-link>
          页面创建一个任务吧！
        </div>
      </div>
    </div>


    <TableSettingsDrawer
      v-model="recordTableSettings.settingsVisible.value"
      :columns="recordTableSettings.settingsColumns.value.map(column => ({
        key: column.key,
        label: column.label,
        required: column.required,
        visible: column.required ? true : recordTableSettings.visibleColumns.value.some(item => item.key === column.key),
        draggable: recordTableSettings.canDragColumn(column.key),
      }))"
      :page-size-enabled="true"
      :page-size="recordTableSettings.pageSizeDisplay.value"
      :page-size-options="PAGE_SIZE_OPTIONS"
      :dragging-key="recordTableSettings.draggingColumnKey.value"
      @page-size-change="updatePageSizeSetting"
      @toggle-column="recordTableSettings.toggleColumnVisibility"
      @drag-start="recordTableSettings.handleDragStart"
      @drag-end="recordTableSettings.handleDragEnd"
      @drop-column="recordTableSettings.moveColumnToTarget"
      @reset="recordTableSettings.reset"
    />

    <el-dialog v-model="adoptDialogVisible" width="620px" destroy-on-close class="adopt-dialog">
      <template #header>
        <div class="adopt-dialog-title">全部采纳</div>
      </template>
      <template v-if="activeRecord">
        <div class="adopt-dialog-body">
          <div class="adopt-dialog-notice">
            <div class="adopt-dialog-copy">
              {{ `确定要全部采纳任务 "${activeRecord.requirementTitle}" 的 ${adoptableCases.length} 条用例吗？` }}
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

    <AiGenerationProcessDialog
      v-model="processDialogVisible"
      :record="activeRecord"
      :steps="processSteps"
      :status-label="activeRecord ? getStatusLabel(activeRecord.status) : ''"
      :status-class="activeRecord ? getStatusClass(activeRecord.status) : ''"
      :show-cancel-button="!!activeRecord && isRunningStatus(activeRecord.status)"
      :cancel-disabled="!activeRecord || !isRunningStatus(activeRecord.status)"
      @cancel="activeRecord && cancelGeneration(activeRecord)"
    />
  </section>
</template>

<style scoped>
.ai-record-page {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.record-filter-card,
.record-stats-card,
.record-table-card {
  padding: 20px 22px;
  min-width: 0;
}

.record-filter-row,
.record-filter-item,
.table-action-row,
.process-dialog-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.record-table-wrap {
  width: 100%;
  max-width: 100%;
  overflow: hidden;
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
  width: 24px;
  height: 24px;
  padding: 0;
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

.table-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.table-pagination-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.table-pagination-summary {
  color: var(--text-subtle);
  font-size: 13px;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

.record-empty-card {
  min-height: 430px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 28px 52px;
}

.record-empty-content {
  width: min(100%, 420px);
  display: grid;
  justify-items: center;
  gap: 0;
  text-align: center;
}

.record-empty-icon {
  font-size: 4rem;
  line-height: 1;
  margin-bottom: 18px;
}

.record-empty-title {
  margin-bottom: 16px;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.4;
  color: #2c3e50;
}

.record-empty-text {
  font-size: 14px;
  line-height: 1.7;
  color: #667085;
  white-space: nowrap;
}

.record-empty-link {
  color: #2f88ff;
  font-weight: 500;
  text-decoration: none;
}

.record-empty-link:hover {
  color: #1f6fe5;
  text-decoration: underline;
}

.record-filter-label,
.stats-label,
.adopt-dialog-copy,
.adopt-dialog-subcopy {
  font-size: 13px;
  color: var(--text-subtle);
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.stats-item {
  text-align: center;
}

.stats-value {
  font-size: 40px;
  font-weight: 700;
  color: #2f88ff;
  line-height: 1;
}

.stats-label {
  margin-top: 10px;
}

.record-table :deep(.el-table__header-wrapper th) {
  background: rgba(248, 250, 252, 0.96);
  color: var(--text-main);
  font-weight: 700;
}

.record-table {
  width: 100%;
  min-width: 0;
}

.record-table :deep(.el-table__cell) {
  padding-top: 14px;
  padding-bottom: 14px;
}

.record-table :deep(.el-table-fixed-column--right) {
  background: var(--bg-panel);
  box-shadow: -8px 0 16px rgba(15, 23, 42, 0.06);
}

.record-table :deep(.el-table__body-wrapper .el-scrollbar__wrap) {
  overflow-x: auto;
}

.task-code-text {
  color: var(--text-main);
}

.workspace-text {
  color: var(--text-main);
}

.record-requirement-title {
  min-width: 0;
  font-size: 13px;
  color: var(--text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.case-count-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.12);
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 700;
}

.case-count-pill-success {
  background: rgba(18, 183, 106, 0.12);
  color: #027a48;
}

.record-muted-text {
  color: #667085;
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

.adopt-form-title {
  margin-bottom: 14px;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
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

.record-action-button {
  min-width: 88px;
  height: 34px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 600;
  border-radius: 6px;
}

.record-action-button-retry:not(.is-disabled) {
  --el-button-bg-color: #f79009;
  --el-button-border-color: #f79009;
  --el-button-hover-bg-color: #e57f06;
  --el-button-hover-border-color: #e57f06;
  --el-button-active-bg-color: #d96d00;
  --el-button-active-border-color: #d96d00;
  --el-button-text-color: #ffffff;
}

.record-action-button-process:not(.is-disabled) {
  --el-button-bg-color: #f8fbff;
  --el-button-border-color: #cfe0fb;
  --el-button-hover-bg-color: #eef5ff;
  --el-button-hover-border-color: #b8d0f7;
  --el-button-active-bg-color: #e6f0ff;
  --el-button-active-border-color: #a9c3f4;
  --el-button-text-color: #2f6fdd;
}

.record-table .table-action-row {
  justify-content: center;
  gap: 10px;
  flex-wrap: nowrap;
}

.record-table .table-action-row-text {
  gap: 0;
}

.process-dialog-meta {
  justify-content: space-between;
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

.process-step-title,
.process-current-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
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
  color: #b42318;
}

@media (max-width: 1200px) {
  .stats-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: 1fr;
  }

  .record-empty-text {
    white-space: normal;
  }
}
</style>
