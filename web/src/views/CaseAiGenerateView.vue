<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  CircleClose,
  DocumentAdd,
  MagicStick,
  RefreshRight,
  View,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { platformApi } from '../api/platform'
import { useCaseCenterShared } from '../composables/useCaseCenterShared'
import type {
  AiCaseConfig,
  AiRequirementAsset,
  CaseDirectoryNode,
} from '../types/api'
import type {
  AiGenerationOutputMode,
  AiGenerationTaskRecord,
} from '../utils/caseAiGenerationRecords'
import {
  cancelAiGenerationRecord,
  createAiGenerationRecord,
  getAiGenerationRecord,
  listAiGenerationRecords,
} from '../utils/caseAiGenerationRecords'

type RequirementAssetView = AiRequirementAsset & {
  selected: boolean
}

type DirectoryOption = {
  value: string
  label: string
  directoryId: number | null
}

const router = useRouter()
const { workspaceCode, isAllScope, writableWorkspaces, loadSharedBase } = useCaseCenterShared()

const loadingConfig = ref(false)
const loadingDirectories = ref(false)
const importingRequirement = ref(false)
const generating = ref(false)
const processDialogVisible = ref(false)

const selectedWorkspaceCode = ref(workspaceCode.value === 'ALL' ? '' : workspaceCode.value)
const activeConfig = ref<AiCaseConfig | null>(null)
const reviewerConfig = ref<AiCaseConfig | null>(null)
const requirementAssets = ref<RequirementAssetView[]>([])
const latestTaskRecord = ref<AiGenerationTaskRecord | null>(null)
const manualTaskRecordId = ref('')
const documentTaskRecordId = ref('')
const taskRecords = ref<AiGenerationTaskRecord[]>([])
const uploadedDocument = ref<{
  fileName: string
  fileSize: number
} | null>(null)
const uploadedRequirementTitle = ref('')
const uploadedRequirementContent = ref('')

const requirementFileInput = ref<HTMLInputElement | null>(null)
const titleInputRef = ref<HTMLElement | null>(null)
const textareaFieldRef = ref<HTMLElement | null>(null)
const uploadPanelBodyRef = ref<HTMLElement | null>(null)
const uploadBoxOffset = ref(0)
const uploadBoxHeight = ref(320)
const directoryOptions = ref<DirectoryOption[]>([])
const directoryTree = ref<CaseDirectoryNode[]>([])
const manualDirectoryBasePath = ref('')
const documentDirectoryBasePath = ref('')
let syncingManualDirectoryPath = false
let syncingDocumentDirectoryPath = false
let layoutObserver: ResizeObserver | null = null
let taskPollingTimer: number | null = null

const form = reactive({
  requirementTitle: '',
  requirementContent: '',
  manualDirectoryPath: '',
  outputMode: 'STREAM' as AiGenerationOutputMode,
})

const documentForm = reactive({
  directoryPath: '',
})

const processSteps = [
  {
    index: 1 as const,
    title: '任务已创建',
    description: '已记录需求内容、目标空间和输出模式。',
  },
  {
    index: 2 as const,
    title: 'AI 生成用例',
    description: '正在根据需求描述和附件生成候选测试用例。',
  },
  {
    index: 3 as const,
    title: 'AI 自动评审',
    description: '正在自动完成用例评审和建议汇总。',
  },
  {
    index: 4 as const,
    title: '任务完成',
    description: '生成结果已进入 AI 生成用例记录页。',
  },
]

const targetWorkspaceCode = computed(() => (isAllScope.value ? selectedWorkspaceCode.value : workspaceCode.value))
const currentWorkspaceName = computed(() => {
  if (!targetWorkspaceCode.value) {
    return ''
  }
  return writableWorkspaces.value.find(item => item.code === targetWorkspaceCode.value)?.name ?? targetWorkspaceCode.value
})
const hasProcessingTask = computed(() => (
  !!latestTaskRecord.value
  && !['COMPLETED', 'FAILED', 'CANCELED'].includes(latestTaskRecord.value.status)
))
const canGenerate = computed(() => (
  !!targetWorkspaceCode.value
  && !!form.requirementTitle.trim()
  && !!form.requirementContent.trim()
  && !!form.manualDirectoryPath.trim()
  && !!activeConfig.value
))
const canGenerateDocument = computed(() => (
  !!targetWorkspaceCode.value
  && !!uploadedDocument.value
  && !!uploadedRequirementTitle.value.trim()
  && !!uploadedRequirementContent.value.trim()
  && !!documentForm.directoryPath.trim()
  && !!activeConfig.value
))
const hasImportedDocumentAssets = computed(() => requirementAssets.value.length > 0)
const textOnlyGenerationNotice = computed(() => {
  if (!hasImportedDocumentAssets.value || !activeConfig.value || activeConfig.value.supportsImageInput) {
    return ''
  }
  return '当前编写模型不支持图片输入，将自动忽略文档中的图片素材，按纯文本需求继续生成测试用例。'
})
const aiConfigReady = computed(() => (
  !!currentWorkspaceName.value
  && !!activeConfig.value
  && !!reviewerConfig.value
  && !!form.outputMode
))
const aiConfigStatusText = computed(() => (
  aiConfigReady.value ? '配置完整，可直接生成' : '配置缺失，请检查模型配置'
))
const aiConfigStatusClass = computed(() => (
  aiConfigReady.value ? 'config-status-success' : 'config-status-danger'
))
const recentTaskRecords = computed(() => {
  const priorityMap: Record<AiGenerationTaskRecord['status'], number> = {
    PENDING: 0,
    GENERATING: 1,
    REVIEWING: 2,
    FAILED: 3,
    COMPLETED: 4,
    CANCELED: 5,
  }

  return [...taskRecords.value]
    .sort((left, right) => {
      const priorityDiff = priorityMap[left.status] - priorityMap[right.status]
      if (priorityDiff !== 0) {
        return priorityDiff
      }
      return new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime()
    })
    .slice(0, 3)
})

function formatFileSize(size: number) {
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(2)} KB`
  }
  return `${(size / (1024 * 1024)).toFixed(2)} MB`
}

function getTaskStatusLabel(status: AiGenerationTaskRecord['status']) {
  const labelMap: Record<AiGenerationTaskRecord['status'], string> = {
    PENDING: '已创建',
    GENERATING: '生成中',
    REVIEWING: '评审中',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELED: '已终止',
  }
  return labelMap[status]
}

function getTaskStatusTone(status: AiGenerationTaskRecord['status']) {
  const toneMap: Record<AiGenerationTaskRecord['status'], string> = {
    PENDING: 'status-info',
    GENERATING: 'status-info',
    REVIEWING: 'status-warning',
    COMPLETED: 'status-success',
    FAILED: 'status-danger',
    CANCELED: 'status-neutral',
  }
  return toneMap[status]
}

function getFailureStepLabel(step: AiGenerationTaskRecord['currentStep']) {
  const labelMap: Record<AiGenerationTaskRecord['currentStep'], string> = {
    1: '任务创建',
    2: 'AI 生成用例',
    3: 'AI 自动评审',
    4: '任务完成',
  }
  return labelMap[step]
}

function pickLatestTaskRecord(records: AiGenerationTaskRecord[]) {
  return records.find(item => ['PENDING', 'GENERATING', 'REVIEWING', 'FAILED'].includes(item.status))
    ?? records[0]
    ?? null
}

function startTaskPolling() {
  stopTaskPolling()
  taskPollingTimer = window.setInterval(() => {
    void refreshLatestTaskRecord()
  }, 2500)
}

function stopTaskPolling() {
  if (taskPollingTimer != null) {
    window.clearInterval(taskPollingTimer)
    taskPollingTimer = null
  }
}

async function refreshLatestTaskRecord() {
  if (!targetWorkspaceCode.value) {
    taskRecords.value = []
    latestTaskRecord.value = null
    stopTaskPolling()
    return
  }

  taskRecords.value = await listAiGenerationRecords(targetWorkspaceCode.value)
  latestTaskRecord.value = pickLatestTaskRecord(taskRecords.value)
  if (taskRecords.value.some(item => ['PENDING', 'GENERATING', 'REVIEWING'].includes(item.status))) {
    startTaskPolling()
  } else {
    stopTaskPolling()
  }
}

function formatTaskTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

async function openTaskProcessDialog(recordId?: string) {
  await refreshLatestTaskRecord()
  latestTaskRecord.value = recordId && targetWorkspaceCode.value
    ? await getAiGenerationRecord(targetWorkspaceCode.value, recordId)
    : pickLatestTaskRecord(taskRecords.value)

  if (!latestTaskRecord.value) {
    ElMessage.info('当前还没有生成任务')
    return
  }
  processDialogVisible.value = true
}

async function openScopedProcessDialog(source: 'manual' | 'document') {
  const recordId = source === 'manual' ? manualTaskRecordId.value : documentTaskRecordId.value
  if (!recordId) {
    ElMessage.info('请先生成测试用例')
    return
  }

  if (!targetWorkspaceCode.value) {
    ElMessage.info('请先选择目标空间')
    return
  }

  latestTaskRecord.value = await getAiGenerationRecord(targetWorkspaceCode.value, recordId)
  if (!latestTaskRecord.value) {
    ElMessage.info('当前任务记录不存在，请重新生成测试用例')
    return
  }
  processDialogVisible.value = true
}

function openTaskDetail(recordId: string) {
  router.push({
    name: 'cases-ai-record-detail',
    params: { taskId: recordId },
  })
}

function openTaskRecordsPage() {
  router.push({
    name: 'cases-ai-records',
  })
}

function normalizeDirectorySegments(path: string) {
  return path
    .split(/[\\/]+/)
    .map(segment => segment.trim())
    .filter(Boolean)
}

function normalizeDirectoryPath(path: string) {
  return normalizeDirectorySegments(path).join('/')
}

function buildDirectoryPath(basePath: string, title: string) {
  const normalizedBasePath = normalizeDirectoryPath(basePath)
  const normalizedTitle = title.trim()
  if (!normalizedBasePath) {
    return normalizedTitle
  }
  return normalizedTitle ? `${normalizedBasePath}/${normalizedTitle}` : normalizedBasePath
}

function findDirectoryBasePath(path: string) {
  const normalizedPath = normalizeDirectoryPath(path)
  if (!normalizedPath) {
    return ''
  }
  const matchedOption = [...directoryOptions.value]
    .sort((left, right) => right.value.length - left.value.length)
    .find(item => normalizedPath === item.value || normalizedPath.startsWith(`${item.value}/`))
  return matchedOption?.value ?? ''
}

function flattenDirectories(nodes: CaseDirectoryNode[], prefix = ''): DirectoryOption[] {
  return nodes.flatMap((node) => {
    const label = prefix ? `${prefix}/${node.name}` : node.name
    return [
      { value: label, label, directoryId: node.id },
      ...flattenDirectories(node.children ?? [], label),
    ]
  })
}

async function loadConfig() {
  if (!targetWorkspaceCode.value) {
    activeConfig.value = null
    return
  }
  loadingConfig.value = true
  try {
    const response = await platformApi.getAiCaseConfig(workspaceCode.value, targetWorkspaceCode.value)
    activeConfig.value = response.generatorConfig
    reviewerConfig.value = response.reviewerConfig
  } catch (error) {
    activeConfig.value = null
    reviewerConfig.value = null
    ElMessage.error((error as Error).message)
  } finally {
    loadingConfig.value = false
  }
}

async function loadDirectoryOptions() {
  if (!targetWorkspaceCode.value) {
    directoryTree.value = []
    directoryOptions.value = []
    return
  }

  loadingDirectories.value = true
  try {
    const workspaces = await platformApi.getCaseDirectories(targetWorkspaceCode.value)
    const current = workspaces.find(item => item.workspaceCode === targetWorkspaceCode.value)
    directoryTree.value = current?.children ?? []
    directoryOptions.value = flattenDirectories(directoryTree.value)
  } catch (error) {
    directoryTree.value = []
    directoryOptions.value = []
    ElMessage.error((error as Error).message)
  } finally {
    loadingDirectories.value = false
  }
}

async function ensureDirectoryPath(path: string) {
  if (!targetWorkspaceCode.value) {
    throw new Error('请先选择目标空间')
  }

  const segments = normalizeDirectorySegments(path)
  if (!segments.length) {
    throw new Error('请先填写用例保存路径')
  }

  let siblings = directoryTree.value
  let parentId: number | null = null
  let currentNode: CaseDirectoryNode | null = null
  let createdAny = false

  for (const segment of segments) {
    let matchedNode = siblings.find(item => item.name === segment) ?? null

    if (!matchedNode) {
      matchedNode = await platformApi.createCaseDirectory(targetWorkspaceCode.value, {
        parentId,
        name: segment,
      })
      createdAny = true
      siblings.push(matchedNode)
    }

    currentNode = matchedNode
    parentId = matchedNode.id
    siblings = matchedNode.children ?? []
  }

  if (createdAny) {
    await loadDirectoryOptions()
  }

  return {
    directoryId: currentNode?.id ?? null,
    directoryName: segments.join('/'),
  }
}

function triggerRequirementImport() {
  if (!targetWorkspaceCode.value) {
    ElMessage.warning('请先选择目标空间')
    return
  }
  requirementFileInput.value?.click()
}

async function handleRequirementFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !targetWorkspaceCode.value) {
    return
  }
  importingRequirement.value = true
  try {
    const response = await platformApi.importRequirementDocument(targetWorkspaceCode.value, file)
    uploadedRequirementTitle.value = response.title || file.name.replace(/\.[^.]+$/, '')
    uploadedRequirementContent.value = response.content
    requirementAssets.value = response.assets.map(item => ({ ...item, selected: true }))
    uploadedDocument.value = {
      fileName: response.fileName || file.name,
      fileSize: file.size,
    }
    ElMessage.success(`已导入需求文档：${response.fileName}`)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    importingRequirement.value = false
    input.value = ''
  }
}

function clearImportedDocument() {
  uploadedDocument.value = null
  requirementAssets.value = []
  uploadedRequirementTitle.value = ''
  uploadedRequirementContent.value = ''
  documentDirectoryBasePath.value = ''
  documentForm.directoryPath = ''
  documentTaskRecordId.value = ''
}

function handleManualDirectorySelect(value: string) {
  const normalizedPath = normalizeDirectoryPath(value)
  manualDirectoryBasePath.value = findDirectoryBasePath(normalizedPath)
  syncingManualDirectoryPath = true
  form.manualDirectoryPath = buildDirectoryPath(manualDirectoryBasePath.value || normalizedPath, form.requirementTitle)
}

function handleDocumentDirectorySelect(value: string) {
  const normalizedPath = normalizeDirectoryPath(value)
  documentDirectoryBasePath.value = findDirectoryBasePath(normalizedPath)
  syncingDocumentDirectoryPath = true
  documentForm.directoryPath = buildDirectoryPath(documentDirectoryBasePath.value || normalizedPath, uploadedRequirementTitle.value)
}

async function handleGenerateCases(source: 'manual' | 'document' = 'manual') {
  const requirementTitle = source === 'document'
    ? uploadedRequirementTitle.value.trim()
    : form.requirementTitle.trim()
  const requirementContent = source === 'document'
    ? uploadedRequirementContent.value.trim()
    : form.requirementContent.trim()
  const directoryPath = source === 'document'
    ? normalizeDirectoryPath(documentForm.directoryPath)
    : normalizeDirectoryPath(form.manualDirectoryPath)
  const canRun = source === 'document' ? canGenerateDocument.value : canGenerate.value

  if (!canRun || !targetWorkspaceCode.value) {
    ElMessage.warning(source === 'document'
      ? '请先上传需求文档，并确认文档标题、用例保存路径、目标空间和 AI 配置可用'
      : '请先补充需求标题、需求描述、用例保存路径，并确认目标空间和 AI 配置可用')
    return
  }

  if (!directoryPath) {
    ElMessage.warning(source === 'document'
      ? '请先选择用例保存模块路径，并确认文档标题已填写'
      : '请先选择用例保存模块路径，并确认需求标题已填写')
    return
  }

  if (textOnlyGenerationNotice.value) {
    ElMessage.warning(textOnlyGenerationNotice.value)
  }

  generating.value = true

  try {
    const resolvedDirectory = await ensureDirectoryPath(directoryPath)
    const baseRecord = await createAiGenerationRecord(targetWorkspaceCode.value, {
      requirementTitle,
      requirementContent,
      outputMode: form.outputMode,
      directoryId: resolvedDirectory.directoryId,
      directoryName: resolvedDirectory.directoryName ?? directoryPath,
      assetIds: source === 'document' && activeConfig.value?.supportsImageInput
        ? requirementAssets.value.filter(item => item.selected).map(item => item.id)
        : [],
    })
    if (source === 'document') {
      documentTaskRecordId.value = baseRecord.id
    } else {
      manualTaskRecordId.value = baseRecord.id
    }
    latestTaskRecord.value = baseRecord
    await refreshLatestTaskRecord()
    processDialogVisible.value = true
    startTaskPolling()
    ElMessage.success('AI 生成任务已创建，后台会继续执行')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    generating.value = false
  }
}

async function terminateTask() {
  if (!latestTaskRecord.value || !hasProcessingTask.value) {
    return
  }
  await ElMessageBox.confirm(
    '确认取消当前 AI 生成任务吗？取消后后续步骤将不再继续执行。',
    '取消生成任务',
    {
      type: 'warning',
      confirmButtonText: '取消生成',
      cancelButtonText: '取消',
    },
  )
  latestTaskRecord.value = await cancelAiGenerationRecord(latestTaskRecord.value.workspaceCode, latestTaskRecord.value.id)
  await refreshLatestTaskRecord()
  ElMessage.success('已取消当前生成任务')
}

function syncUploadBoxGeometry() {
  const titleElement = titleInputRef.value
  const textareaElement = textareaFieldRef.value
  const uploadBodyElement = uploadPanelBodyRef.value

  if (!titleElement || !textareaElement || !uploadBodyElement) {
    return
  }

  const titleRect = titleElement.getBoundingClientRect()
  const textareaRect = textareaElement.getBoundingClientRect()
  const uploadBodyRect = uploadBodyElement.getBoundingClientRect()

  uploadBoxOffset.value = Math.max(titleRect.top - uploadBodyRect.top, 0)
  uploadBoxHeight.value = Math.max(textareaRect.bottom - titleRect.top, 320)
}

function bindLayoutObserver() {
  layoutObserver?.disconnect()
  layoutObserver = new ResizeObserver(() => {
    syncUploadBoxGeometry()
  })

  if (titleInputRef.value) {
    layoutObserver.observe(titleInputRef.value)
  }
  if (textareaFieldRef.value) {
    layoutObserver.observe(textareaFieldRef.value)
  }
  if (uploadPanelBodyRef.value) {
    layoutObserver.observe(uploadPanelBodyRef.value)
  }
}

const _templateKeepAlive = {
  CircleClose,
  RefreshRight,
  processSteps,
  aiConfigStatusText,
  aiConfigStatusClass,
  getTaskStatusLabel,
  getTaskStatusTone,
  triggerRequirementImport,
  handleRequirementFileChange,
  terminateTask,
}
void _templateKeepAlive

watch(
  () => workspaceCode.value,
  () => {
    selectedWorkspaceCode.value = workspaceCode.value === 'ALL' ? '' : workspaceCode.value
  },
)

watch(
  () => targetWorkspaceCode.value,
  async () => {
    requirementAssets.value = []
    manualDirectoryBasePath.value = ''
    documentDirectoryBasePath.value = ''
    form.manualDirectoryPath = ''
    documentForm.directoryPath = ''
    await loadConfig()
    await loadDirectoryOptions()
    await refreshLatestTaskRecord()
  },
)

watch(
  () => form.requirementTitle,
  (title) => {
    if (!manualDirectoryBasePath.value) {
      return
    }
    syncingManualDirectoryPath = true
    form.manualDirectoryPath = buildDirectoryPath(manualDirectoryBasePath.value, title)
  },
)

watch(
  () => uploadedRequirementTitle.value,
  (title) => {
    if (!documentDirectoryBasePath.value) {
      return
    }
    syncingDocumentDirectoryPath = true
    documentForm.directoryPath = buildDirectoryPath(documentDirectoryBasePath.value, title)
  },
)

watch(
  () => form.manualDirectoryPath,
  (value) => {
    if (syncingManualDirectoryPath) {
      syncingManualDirectoryPath = false
      return
    }
    manualDirectoryBasePath.value = findDirectoryBasePath(value)
  },
)

watch(
  () => documentForm.directoryPath,
  (value) => {
    if (syncingDocumentDirectoryPath) {
      syncingDocumentDirectoryPath = false
      return
    }
    documentDirectoryBasePath.value = findDirectoryBasePath(value)
  },
)

onMounted(async () => {
  await loadSharedBase()
  await loadConfig()
  await loadDirectoryOptions()
  await refreshLatestTaskRecord()
  await nextTick()
  bindLayoutObserver()
  syncUploadBoxGeometry()
  window.addEventListener('resize', syncUploadBoxGeometry)
})

onBeforeUnmount(() => {
  layoutObserver?.disconnect()
  stopTaskPolling()
  window.removeEventListener('resize', syncUploadBoxGeometry)
})
</script>

<template>
  <section class="ai-generate-page">
    <div class="main-content-grid">
      <div class="panel-card input-panel">
        <div class="panel-title-row">
          <div>
            <div class="section-title section-title-with-icon">
              <span class="section-title-icon" aria-hidden="true">✍️</span>
              <span>手动输入需求描述</span>
            </div>
          </div>
        </div>

        <div class="form-stack">
          <div class="field-label">需求标题<span class="field-required">*</span></div>
          <div ref="titleInputRef">
          <el-input
            v-model="form.requirementTitle"
            maxlength="120"
            placeholder="请输入需求标题，例如：用户登录功能需求"
          />
          </div>
          <div class="field-label">用例保存路径 <span class="field-required">*</span></div>
          <el-select
            v-model="form.manualDirectoryPath"
            class="directory-path-select"
            filterable
            allow-create
            clearable
            :loading="loadingDirectories"
            placeholder="请选择模块路径，选中后会自动拼接需求标题"
            @change="handleManualDirectorySelect"
          >
            <el-option
              v-for="item in directoryOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <div class="field-label">需求描述<span class="field-required">*</span></div>
          <div ref="textareaFieldRef">
          <el-input
            v-model="form.requirementContent"
            class="requirement-textarea"
            type="textarea"
            :autosize="{ minRows: 10, maxRows: 18 }"
            resize="vertical"
            placeholder="请详细描述您的需求，包括功能描述、使用场景、业务流程等"
          />
          </div>
          <div class="char-count">{{ form.requirementContent.length }}/5000</div>

          <div class="path-action-stack">
            <el-button
              class="generate-primary-btn"
              type="success"
              :icon="MagicStick"
              :loading="generating"
              :disabled="!canGenerate"
              @click="handleGenerateCases('manual')"
            >
              生成测试用例
            </el-button>
            <el-button
              class="flow-secondary-btn"
              :icon="View"
              :disabled="!manualTaskRecordId"
              @click="openScopedProcessDialog('manual')"
            >
              查看生成流程
            </el-button>
          </div>
        </div>
      </div>

      <div class="panel-card upload-panel">
        <div class="panel-title-row">
          <div>
            <div class="section-title section-title-with-icon">
              <span class="section-title-icon" aria-hidden="true">📄</span>
              <span>上传需求文档</span>
            </div>
          </div>
        </div>

        <div ref="uploadPanelBodyRef" class="upload-panel-body">
          <template v-if="uploadedDocument">
            <div
              class="upload-success-shell"
              :style="{ marginTop: `${uploadBoxOffset}px`, minHeight: `${uploadBoxHeight}px` }"
            >
              <div class="upload-success-box">
                <div class="upload-success-file">
                  <el-icon class="upload-success-icon"><DocumentAdd /></el-icon>
                  <div class="upload-success-meta">
                    <div class="upload-success-name">{{ uploadedDocument.fileName }}</div>
                    <div class="upload-success-size">{{ formatFileSize(uploadedDocument.fileSize) }}</div>
                  </div>
                  <button class="upload-remove-btn" type="button" @click="clearImportedDocument">×</button>
                </div>
              </div>

              <div class="upload-detail-form">
                <div class="field-label">文档标题</div>
                <el-input
                  v-model="uploadedRequirementTitle"
                  placeholder="请输入文档标题"
                />

                <div class="field-label">用例保存路径 <span class="field-required">*</span></div>
                <el-select
                  v-model="documentForm.directoryPath"
                  class="directory-path-select"
                  filterable
                  allow-create
                  clearable
                  :loading="loadingDirectories"
                  placeholder="请选择模块路径，选中后会自动拼接文档标题"
                  @change="handleDocumentDirectorySelect"
                >
                  <el-option
                    v-for="item in directoryOptions"
                    :key="`document-${item.value}`"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <div class="field-label">图片能力提示</div>
                <div class="upload-hint-box" :class="{ 'upload-hint-box-warning': !!textOnlyGenerationNotice }">
                  {{ textOnlyGenerationNotice || '当前模型支持图文输入，如文档中包含图片素材，将一并参与本次测试用例生成。' }}
                </div>

                <div class="upload-card-actions">
                  <el-button
                    class="generate-primary-btn"
                    type="success"
                    :icon="MagicStick"
                    :loading="generating"
                    :disabled="!canGenerateDocument"
                    @click="handleGenerateCases('document')"
                  >
                    生成测试用例
                  </el-button>
                  <el-button
                    class="flow-secondary-btn"
                    :icon="View"
                    :disabled="!documentTaskRecordId"
                    @click="openScopedProcessDialog('document')"
                  >
                    查看生成流程
                  </el-button>
                </div>
              </div>
            </div>
          </template>
          <button
            v-else
            class="upload-large-box"
            type="button"
            :style="{ marginTop: `${uploadBoxOffset}px`, minHeight: `${uploadBoxHeight}px` }"
            @click="triggerRequirementImport"
          >
            <el-icon class="upload-box-icon"><DocumentAdd /></el-icon>
            <div class="upload-box-center">
              <div class="upload-box-title">拖拽文件到此处或点击选择文件</div>
              <div class="upload-box-desc">支持 PDF、Word、TXT、Markdown 格式</div>
            </div>
            <span class="upload-primary-btn">选择文件</span>
          </button>
        </div>
      </div>
    </div>

    <div class="panel-card ai-config-card">
      <div class="panel-title-row">
        <div>
          <div class="section-title section-title-with-icon">
            <span class="section-title-icon output-section-icon" aria-hidden="true">📤</span>
            <span>输出模式设置</span>
          </div>
          <div class="section-desc">先选择本次任务的输出方式。</div>
        </div>
      </div>

      <div class="output-mode-grid output-mode-grid-visual">
        <label class="output-mode-card output-mode-card-visual" :class="{ 'output-mode-card-active': form.outputMode === 'STREAM' }">
          <input v-model="form.outputMode" type="radio" value="STREAM">
          <div class="output-mode-title">
            <span class="output-mode-icon output-mode-icon-stream" aria-hidden="true">⚡</span>
            <span>实时流式输出</span>
          </div>
          <div class="output-mode-desc">优先展示任务执行进度和阶段状态。</div>
        </label>
        <label class="output-mode-card output-mode-card-visual" :class="{ 'output-mode-card-active': form.outputMode === 'COMPLETE' }">
          <input v-model="form.outputMode" type="radio" value="COMPLETE">
          <div class="output-mode-title">
            <span class="output-mode-icon output-mode-icon-complete" aria-hidden="true">📄</span>
            <span>完整输出</span>
          </div>
          <div class="output-mode-desc">等待生成和评审全部完成后统一返回结果。</div>
        </label>
      </div>
    </div>

    <div class="panel-card ai-config-card">
      <div class="panel-title-row">
        <div>
          <div class="section-title section-title-with-icon">
            <span class="section-title-icon" aria-hidden="true">🤖</span>
            <span>当前 AI 配置</span>
          </div>
          <div class="section-desc">展示当前空间下本次生成任务会使用的 AI 配置摘要。</div>
        </div>
        <el-button :icon="RefreshRight" text @click="loadConfig">刷新配置</el-button>
      </div>

      <div class="ai-config-grid ai-config-grid-five">
        <div class="config-info-item config-info-item-status">
          <div class="config-info-label">配置状态</div>
          <div class="config-status-panel" :class="aiConfigStatusClass">
            <span class="config-status-dot" />
            <span class="config-status-text">{{ aiConfigStatusText }}</span>
          </div>
        </div>
        <div class="config-info-item">
          <div class="config-info-label">当前空间</div>
          <div class="config-info-value">{{ currentWorkspaceName || '-' }}</div>
        </div>
        <div class="config-info-item">
          <div class="config-info-label">编写模型</div>
          <div class="config-info-value">
            {{ activeConfig ? `${activeConfig.provider} / ${activeConfig.model} / 温度 ${activeConfig.temperature.toFixed(1)}` : '-' }}
          </div>
        </div>
        <div class="config-info-item">
          <div class="config-info-label">评审模型</div>
          <div class="config-info-value">
            {{ reviewerConfig ? `${reviewerConfig.provider} / ${reviewerConfig.model} / 温度 ${reviewerConfig.temperature.toFixed(1)}` : '-' }}
          </div>
        </div>
        <div class="config-info-item">
          <div class="config-info-label">输出模式</div>
          <div class="config-info-value">{{ form.outputMode === 'STREAM' ? '实时流式输出' : '完整输出' }}</div>
        </div>
      </div>

      <div class="recent-task-card">
        <div class="panel-title-row recent-task-header">
          <div>
            <div class="section-title section-title-with-icon">
              <span class="section-title-icon" aria-hidden="true">🕘</span>
              <span>最近任务</span>
            </div>
            <div class="section-desc">展示最近 3 条任务，优先显示进行中、失败和待处理任务。</div>
          </div>
          <el-button :icon="RefreshRight" text @click="refreshLatestTaskRecord">刷新任务</el-button>
        </div>

        <div v-if="recentTaskRecords.length" class="recent-task-list">
          <div v-for="task in recentTaskRecords" :key="task.id" class="recent-task-item">
            <div class="recent-task-main">
              <div class="recent-task-top">
                <div class="recent-task-title">{{ task.requirementTitle }}</div>
                <span class="status-pill" :class="getTaskStatusTone(task.status)">
                  {{ getTaskStatusLabel(task.status) }}
                </span>
              </div>
              <div class="recent-task-meta">
                <span>{{ task.workspaceName }}</span>
                <span>{{ task.outputMode === 'STREAM' ? '实时流式输出' : '完整输出' }}</span>
                <span>{{ formatTaskTime(task.updatedAt) }}</span>
              </div>
            </div>
            <div class="recent-task-actions">
              <el-button class="recent-task-button recent-task-button-primary" :icon="View" @click="openTaskProcessDialog(task.id)">查看流程</el-button>
              <el-button class="recent-task-button recent-task-button-secondary" @click="openTaskDetail(task.id)">查看详情</el-button>
            </div>
          </div>
        </div>

        <div v-else class="recent-task-empty">还没有最近任务，生成一次测试用例后会显示在这里。</div>

        <div class="recent-task-footer">
          <el-button text @click="openTaskRecordsPage">查看全部记录</el-button>
        </div>
      </div>
    </div>
    <input
      ref="requirementFileInput"
      class="hidden-file-input"
      type="file"
      accept=".doc,.docx,.pdf,.md,.txt"
      @change="handleRequirementFileChange"
    >

    <el-dialog
      v-model="processDialogVisible"
      title="AI 用例生成流程"
      width="760px"
      destroy-on-close
    >
      <template v-if="latestTaskRecord">
        <div class="process-dialog-meta">
          <div>
            <div class="process-dialog-title">{{ latestTaskRecord.requirementTitle }}</div>
            <div class="process-dialog-subtitle">
              {{ latestTaskRecord.workspaceName }} / {{ latestTaskRecord.outputMode === 'STREAM' ? '实时流式输出' : '完整输出' }}
            </div>
          </div>
          <span class="status-pill" :class="getTaskStatusTone(latestTaskRecord.status)">
            {{ getTaskStatusLabel(latestTaskRecord.status) }}
          </span>
        </div>

        <div class="process-step-list">
          <div
            v-for="step in processSteps"
            :key="step.index"
            class="process-step-card"
            :class="{
              'process-step-card-active': latestTaskRecord.currentStep === step.index,
              'process-step-card-done': latestTaskRecord.currentStep > step.index || latestTaskRecord.status === 'COMPLETED',
              'process-step-card-failed': latestTaskRecord.status === 'FAILED' && latestTaskRecord.currentStep === step.index,
            }"
          >
            <div class="process-step-index">{{ step.index }}</div>
            <div class="process-step-content">
              <div class="process-step-title">{{ step.title }}</div>
              <div class="process-step-desc">{{ step.description }}</div>
            </div>
          </div>
        </div>

        <div class="process-current-log">
          <div class="section-title small-title">当前状态</div>
          <div class="process-current-text">{{ latestTaskRecord.stepMessage }}</div>
          <div v-if="latestTaskRecord.errorMessage" class="process-error-text">{{ latestTaskRecord.errorMessage }}</div>
        </div>

        <div v-if="latestTaskRecord.status === 'FAILED'" class="process-failure-card">
          <div class="section-title small-title">失败位置</div>
          <div class="process-failure-stage">失败阶段：{{ getFailureStepLabel(latestTaskRecord.currentStep) }}</div>
          <div class="process-failure-text">{{ latestTaskRecord.errorMessage || latestTaskRecord.stepMessage }}</div>
        </div>
      </template>

      <template #footer>
        <div class="process-dialog-footer">
          <el-button @click="processDialogVisible = false">关闭</el-button>
          <el-button
            type="danger"
            :icon="CircleClose"
            :disabled="!hasProcessingTask"
            @click="terminateTask"
          >
            取消生成
          </el-button>
        </div>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.ai-generate-page {
  display: grid;
  gap: 16px;
}

.ai-output-mode-card,
.input-panel,
.upload-panel,
.bottom-action-card {
  padding: 18px;
}

.input-panel,
.upload-panel {
  min-height: 520px;
}

.section-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}

.section-title-with-icon {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
}

.section-title-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  font-size: 22px;
  line-height: 1;
  flex-shrink: 0;
}

.output-section-icon {
  font-size: 20px;
  transform: translateY(-1px);
}

.section-title.small-title {
  font-size: 14px;
}

.section-desc,
.char-count,
.upload-box-desc,
.process-dialog-subtitle,
.process-step-desc,
.process-current-text {
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-subtle);
}

.section-desc {
  margin-top: 6px;
}

.output-mode-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.output-mode-grid-visual {
  gap: 18px;
}

.output-mode-card {
  display: grid;
  gap: 4px;
  padding: 14px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.78);
  cursor: pointer;
}

.output-mode-card-visual {
  min-height: 116px;
  align-content: start;
  padding: 20px 22px;
  border-radius: 14px;
  background: #fff;
  box-shadow: inset 0 0 0 1px rgba(221, 229, 240, 0.92);
}

.output-mode-card input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.output-mode-card-active {
  border-color: rgba(36, 107, 255, 0.72);
  background: rgba(233, 240, 255, 0.92);
  box-shadow: inset 0 0 0 1px rgba(36, 107, 255, 0.3);
}

.output-mode-title {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.35;
  color: var(--text-main);
}

.output-mode-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  font-size: 18px;
  line-height: 1;
  flex-shrink: 0;
}

.output-mode-icon-stream {
  transform: translateY(-1px);
}

.output-mode-icon-complete {
  transform: translateY(-1px);
}

.output-mode-desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--text-subtle);
  max-width: 30ch;
}

.main-content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
}

.panel-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  min-height: 36px;
  margin-bottom: 18px;
}

.form-stack {
  display: grid;
  gap: 12px;
  min-height: 100%;
  align-content: start;
}

.field-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
}

.field-required {
  color: #ef4444;
}

.field-tip {
  margin-top: -4px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-subtle);
}

.path-preview-card {
  display: grid;
  gap: 6px;
  margin-top: -2px;
  padding: 12px 14px;
  border: 1px solid rgba(36, 107, 255, 0.14);
  border-radius: 10px;
  background: rgba(239, 246, 255, 0.8);
}

.path-preview-label {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  color: #175cd3;
}

.path-preview-value {
  font-size: 13px;
  line-height: 1.7;
  color: #1f2937;
  word-break: break-word;
}

.directory-path-select {
  width: 100%;
}

.requirement-textarea :deep(.el-textarea__inner) {
  min-height: 280px !important;
  padding: 14px 14px 16px;
  line-height: 1.75;
  font-size: 14px;
  border-radius: 10px;
}

.requirement-textarea :deep(.el-textarea) {
  display: block;
}

.char-count {
  text-align: right;
  margin-top: -2px;
  padding-bottom: 8px;
}

.action-bar,
.process-dialog-meta,
.process-dialog-footer {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.path-action-stack,
.upload-card-actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 10px;
}

.generate-primary-btn {
  width: 100%;
  height: 54px;
  border-radius: 10px;
  border-color: #2fb15d;
  background: #2fb15d;
  font-size: 15px;
  font-weight: 600;
}

.generate-primary-btn:hover,
.generate-primary-btn:focus {
  border-color: #24974d;
  background: #24974d;
}

.generate-primary-btn:disabled {
  border-color: #c7cdd6;
  background: #c7cdd6;
  color: #fff;
}

.flow-secondary-btn {
  width: 100%;
  height: 54px;
  margin-left: 0;
  border-radius: 10px;
  border-color: var(--line-soft);
  background: #fff;
  color: var(--text-main);
  font-size: 15px;
  font-weight: 500;
}

.flow-secondary-btn:hover,
.flow-secondary-btn:focus {
  border-color: rgba(36, 107, 255, 0.28);
  color: #175cd3;
  background: rgba(239, 246, 255, 0.72);
}

.upload-large-box {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  width: 100%;
  min-height: 320px;
  border: 1px dashed var(--line-soft);
  border-radius: 10px;
  background: #fff;
  color: var(--text-main);
  cursor: pointer;
  padding: 20px;
  margin-bottom: 8px;
}

.upload-large-box:hover {
  border-color: rgba(36, 107, 255, 0.34);
  background: rgba(233, 240, 255, 0.72);
}

.upload-panel-body {
  display: grid;
  align-content: start;
}

.upload-success-shell {
  display: grid;
  align-content: start;
  gap: 16px;
}

.upload-success-box {
  padding: 14px;
  border: 1px dashed var(--line-soft);
  border-radius: 12px;
  background: #fff;
}

.upload-success-file {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 28px;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.92);
}

.upload-success-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  background: rgba(237, 233, 254, 0.8);
  color: #7c3aed;
  font-size: 20px;
}

.upload-success-meta {
  min-width: 0;
  text-align: center;
}

.upload-success-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
  line-height: 1.6;
  word-break: break-word;
}

.upload-success-size {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-subtle);
}

.upload-remove-btn {
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: #f43f5e;
  font-size: 28px;
  line-height: 1;
  cursor: pointer;
}

.upload-detail-form {
  display: grid;
  gap: 12px;
}

.upload-hint-box {
  padding: 12px 14px;
  border: 1px solid rgba(36, 107, 255, 0.14);
  border-radius: 10px;
  background: rgba(239, 246, 255, 0.82);
  font-size: 13px;
  line-height: 1.75;
  color: #1d4ed8;
}

.upload-hint-box-warning {
  border-color: rgba(245, 158, 11, 0.26);
  background: rgba(255, 247, 237, 0.92);
  color: #9a3412;
}

.upload-box-icon {
  font-size: 28px;
}

.upload-box-center {
  display: grid;
  gap: 10px;
  text-align: center;
}

.upload-box-title {
  font-size: 14px;
  color: var(--text-main);
}

.upload-primary-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 84px;
  height: 38px;
  padding: 0 16px;
  border-radius: 8px;
  background: #409eff;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.ai-config-card {
  padding: 20px 22px;
}

.ai-config-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 6px;
}

.ai-config-grid-five {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.config-info-item {
  display: grid;
  grid-template-rows: auto 1fr;
  gap: 12px;
  min-height: 108px;
  padding: 16px 18px;
  border: 1px solid rgba(221, 229, 240, 0.9);
  border-radius: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.03);
  overflow: hidden;
}

.config-info-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-subtle);
  line-height: 1.4;
}

.config-info-value {
  font-size: 15px;
  font-weight: 500;
  line-height: 1.65;
  color: var(--text-main);
  word-break: break-word;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
}

.config-info-item-status {
  align-content: start;
}

.config-status-panel {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  max-width: 100%;
  min-height: 36px;
  padding: 8px 12px;
  border-radius: 10px;
  border: 1px solid transparent;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.3;
}

.config-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: currentColor;
  flex-shrink: 0;
}

.config-status-text {
  white-space: normal;
}

.config-status-success {
  color: #067647;
  background: rgba(236, 253, 243, 0.95);
  border-color: rgba(18, 183, 106, 0.2);
}

.config-status-danger {
  color: #b42318;
  background: rgba(254, 242, 242, 0.96);
  border-color: rgba(240, 68, 56, 0.18);
}

.recent-task-card {
  margin-top: 16px;
  padding: 18px 20px 16px;
  border: 1px solid rgba(221, 229, 240, 0.9);
  border-radius: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
}

.recent-task-header {
  margin-bottom: 14px;
}

.recent-task-list {
  display: grid;
  gap: 10px;
}

.recent-task-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.84);
}

.recent-task-main {
  min-width: 0;
  display: grid;
  gap: 4px;
  flex: 1;
}

.recent-task-top {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.recent-task-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  color: var(--text-main);
  word-break: break-word;
}

.recent-task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-subtle);
}

.recent-task-meta span:not(:last-child)::after {
  content: '·';
  margin-left: 8px;
  color: #c0c8d2;
}

.recent-task-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.recent-task-button {
  min-width: 84px;
  height: 32px;
  padding: 0 12px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
}

.recent-task-button-primary {
  border-color: rgba(36, 107, 255, 0.18);
  background: rgba(239, 246, 255, 0.88);
  color: #175cd3;
}

.recent-task-button-primary:hover,
.recent-task-button-primary:focus {
  border-color: rgba(36, 107, 255, 0.3);
  background: rgba(219, 234, 254, 0.92);
  color: #175cd3;
}

.recent-task-button-secondary {
  border-color: rgba(208, 213, 221, 0.9);
  color: #475467;
}

.recent-task-button-secondary:hover,
.recent-task-button-secondary:focus {
  border-color: rgba(152, 162, 179, 0.9);
  color: #344054;
}

.recent-task-empty {
  padding: 16px 0 8px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-subtle);
}

.recent-task-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.process-dialog-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
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

.process-step-title {
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

.hidden-file-input {
  display: none;
}

@media (max-width: 1280px) {
  .main-content-grid {
    grid-template-columns: 1fr;
  }

  .input-panel,
  .upload-panel {
    min-height: auto;
  }

  .upload-panel-body {
    padding-top: 0;
  }

  .ai-config-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ai-config-grid-five {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .recent-task-item {
    align-items: flex-start;
    flex-direction: column;
  }

  .recent-task-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 900px) {
  .output-mode-grid {
    grid-template-columns: 1fr;
  }

  .ai-config-grid {
    grid-template-columns: 1fr;
  }

  .ai-config-grid-five {
    grid-template-columns: 1fr;
  }

  .path-action-stack,
  .upload-card-actions {
    grid-template-columns: 1fr;
  }
}
</style>


