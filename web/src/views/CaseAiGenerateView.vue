<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { MagicStick, Plus, RefreshRight, Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { platformApi, resolveApiUrl } from '../api/platform'
import { useCaseCenterShared } from '../composables/useCaseCenterShared'
import type {
  AiCaseConfig,
  AiGeneratedCase,
  AiInvalidCaseItem,
  AiRequirementAsset,
  AiReviewResult,
  CaseDirectoryNode,
  CaseDirectoryWorkspace,
  CreateCasePayload,
} from '../types/api'

type EditableGeneratedCase = AiGeneratedCase & {
  selected: boolean
}

type RequirementAssetView = AiRequirementAsset & {
  selected: boolean
}

type ModuleOption = {
  value: number | null
  label: string
}

type ReviewExecutionSource = 'AUTO' | 'MANUAL'

const CASE_TYPE_LABELS: Record<string, string> = {
  FUNCTION: '功能',
  BOUNDARY: '边界',
  EXCEPTION: '异常',
  REGRESSION: '回归',
}

const REVIEW_RESULT_LABELS: Record<string, string> = {
  APPROVE: '建议通过',
  REJECT: '建议驳回',
  SUGGEST: '建议补充',
}

type AiGenerateDraft = {
  activeTab: 'requirement' | 'generate'
  autoReviewEnabled: boolean
  selectedWorkspaceCode: string
  form: {
    requirementTitle: string
    requirementContent: string
    maxCases: number
    directoryId: number | null
  }
  generatedCases: EditableGeneratedCase[]
  generationWarnings: string[]
  invalidCases: AiInvalidCaseItem[]
  aiReviewResult: AiReviewResult | null
  selectedSuggestions: string[]
  supplementNotes: string
  requirementAssets: RequirementAssetView[]
  generationMeta: {
    systemMaxCases: number
    requestedMaxCases: number
    effectiveMaxCases: number
    actualGeneratedCount: number
  }
  reviewSource: ReviewExecutionSource | null
}

const AI_GENERATE_DRAFT_STORAGE_PREFIX = 'case-ai-generate-draft-v3'
const AI_GENERATE_TAB_STORAGE_KEY = 'case-ai-generate-active-tab-v1'

const { workspaceCode, isAllScope, writableWorkspaces, loadSharedBase } = useCaseCenterShared()

const loadingConfig = ref(false)
const loadingDirectories = ref(false)
const generating = ref(false)
const saving = ref(false)
const reviewing = ref(false)
const supplementing = ref(false)
const regeneratingIndex = ref<number | null>(null)
const importingRequirement = ref(false)
const uploadingAssets = ref(false)
const hydratingDraft = ref(false)

const selectedWorkspaceCode = ref(workspaceCode.value === 'ALL' ? '' : workspaceCode.value)
const activeConfig = ref<AiCaseConfig | null>(null)
const generatedCases = ref<EditableGeneratedCase[]>([])
const generationWarnings = ref<string[]>([])
const invalidCases = ref<AiInvalidCaseItem[]>([])
const aiReviewResult = ref<AiReviewResult | null>(null)
const selectedSuggestions = ref<string[]>([])
const directoryWorkspace = ref<CaseDirectoryWorkspace | null>(null)
const moduleOptions = ref<ModuleOption[]>([])
const requirementAssets = ref<RequirementAssetView[]>([])
const requirementFileInput = ref<HTMLInputElement | null>(null)
const requirementAssetInput = ref<HTMLInputElement | null>(null)
const detailDrawerVisible = ref(false)
const reviewDrawerVisible = ref(false)
const candidateEditDrawerVisible = ref(false)
const candidateEditIndex = ref<number | null>(null)
const candidateEditDraft = ref<EditableGeneratedCase | null>(null)
const autoReviewEnabled = ref(true)
const lastReviewSource = ref<ReviewExecutionSource | null>(null)
const lastGenerationProvider = ref('')
const lastGenerationModel = ref('')
const lastGenerationWorkspaceName = ref('')
const lastGenerationRawContent = ref('')

const generationMeta = reactive({
  systemMaxCases: 0,
  requestedMaxCases: 0,
  effectiveMaxCases: 0,
  actualGeneratedCount: 0,
})

const form = reactive({
  requirementTitle: '',
  requirementContent: '',
  maxCases: 12,
  directoryId: null as number | null,
})

const supplementNotes = ref('')
const activeTab = ref<'requirement' | 'generate'>('requirement')

const targetWorkspaceCode = computed(() => (isAllScope.value ? selectedWorkspaceCode.value : workspaceCode.value))
const selectedCount = computed(() => generatedCases.value.filter(item => item.selected).length)
const selectedSuggestionCount = computed(() => selectedSuggestions.value.length)
const activeCandidateDraft = computed(() => candidateEditDraft.value)
const canGoPrevCandidate = computed(() => candidateEditIndex.value != null && candidateEditIndex.value > 0)
const canGoNextCandidate = computed(() => candidateEditIndex.value != null && candidateEditIndex.value < generatedCases.value.length - 1)
const canGenerate = computed(() => !!targetWorkspaceCode.value && !!form.requirementTitle.trim() && !!form.requirementContent.trim())
const canSave = computed(() => !!targetWorkspaceCode.value && selectedCount.value > 0)
const canReviewGeneratedCases = computed(() => generatedCases.value.length > 0 && canGenerate.value)
const hasSelectedAssets = computed(() => requirementAssets.value.some(item => item.selected))
const canSupplementGenerate = computed(() => (
  canGenerate.value
  && generatedCases.value.length > 0
  && (selectedSuggestions.value.length > 0 || !!supplementNotes.value.trim())
))
const configStatusText = computed(() => {
  if (!targetWorkspaceCode.value) return '请先选择目标空间'
  if (loadingConfig.value) return '正在加载 AI 配置'
  if (!activeConfig.value) return '当前还没有可用的 AI 生成配置'
  return `${activeConfig.value.provider} / ${activeConfig.value.model} / temperature ${activeConfig.value.temperature.toFixed(1)} / 系统上限 ${activeConfig.value.maxCases} 条 / ${activeConfig.value.supportsImageInput ? '图文可用' : '仅文本'}`
})
const generationMetaText = computed(() => {
  if (!generationMeta.actualGeneratedCount) {
    return ''
  }
  return `本次请求 ${generationMeta.requestedMaxCases} 条，系统上限 ${generationMeta.systemMaxCases} 条，实际生成 ${generationMeta.actualGeneratedCount} 条。`
})
const assetCapabilityText = computed(() => {
  if (!requirementAssets.value.length) {
    return '当前未选择需求图片素材，本次会按纯文本需求生成。'
  }
  if (!activeConfig.value) {
    return '已选择需求图片素材，等待加载当前生成模型能力。'
  }
  return activeConfig.value.supportsImageInput
    ? '当前生成模型支持图片输入，勾选的需求图片会参与本次生成。'
    : '当前生成模型仅支持文本。若要使用需求图片生成，请先切换到支持图片输入的模型。'
})
const reviewSourceText = computed(() => {
  if (lastReviewSource.value === 'AUTO') return '自动评审'
  if (lastReviewSource.value === 'MANUAL') return '手动评审'
  return '未评审'
})

function flattenModules(nodes: CaseDirectoryNode[], prefix = ''): ModuleOption[] {
  return nodes.flatMap((node) => {
    const label = prefix ? `${prefix} / ${node.name}` : node.name
    return [
      {
        value: node.id,
        label,
      },
      ...flattenModules(node.children ?? [], label),
    ]
  })
}

function resetGeneratedCases() {
  generatedCases.value = []
  generationWarnings.value = []
  invalidCases.value = []
  aiReviewResult.value = null
  lastReviewSource.value = null
  candidateEditDrawerVisible.value = false
  candidateEditIndex.value = null
  selectedSuggestions.value = []
  supplementNotes.value = ''
  generationMeta.systemMaxCases = 0
  generationMeta.requestedMaxCases = 0
  generationMeta.effectiveMaxCases = 0
  generationMeta.actualGeneratedCount = 0
}

function resetRequirementAssets() {
  requirementAssets.value = []
}

function resetDraftState() {
  form.requirementTitle = ''
  form.requirementContent = ''
  form.maxCases = 12
  form.directoryId = null
  resetGeneratedCases()
  resetRequirementAssets()
}

function resolveDraftStorageKey(targetCode: string) {
  return `${AI_GENERATE_DRAFT_STORAGE_PREFIX}:${targetCode || '__EMPTY__'}`
}

function createDraftSnapshot(): AiGenerateDraft {
  return {
    activeTab: activeTab.value,
    autoReviewEnabled: autoReviewEnabled.value,
    selectedWorkspaceCode: selectedWorkspaceCode.value,
    form: {
      requirementTitle: form.requirementTitle,
      requirementContent: form.requirementContent,
      maxCases: form.maxCases,
      directoryId: form.directoryId,
    },
    generatedCases: generatedCases.value.map(item => ({ ...item })),
    generationWarnings: [...generationWarnings.value],
    invalidCases: invalidCases.value.map(item => ({ ...item })),
    aiReviewResult: aiReviewResult.value
      ? {
        ...aiReviewResult.value,
        issues: [...aiReviewResult.value.issues],
        suggestions: [...aiReviewResult.value.suggestions],
      }
      : null,
    selectedSuggestions: [...selectedSuggestions.value],
    supplementNotes: supplementNotes.value,
    requirementAssets: requirementAssets.value.map(item => ({ ...item })),
    generationMeta: {
      systemMaxCases: generationMeta.systemMaxCases,
      requestedMaxCases: generationMeta.requestedMaxCases,
      effectiveMaxCases: generationMeta.effectiveMaxCases,
      actualGeneratedCount: generationMeta.actualGeneratedCount,
    },
    reviewSource: lastReviewSource.value,
  }
}

function persistDraft(targetCode = targetWorkspaceCode.value) {
  if (hydratingDraft.value) {
    return
  }
  localStorage.setItem(resolveDraftStorageKey(targetCode), JSON.stringify(createDraftSnapshot()))
}

function applyDraft(draft: AiGenerateDraft | null) {
  hydratingDraft.value = true
  try {
    if (!draft) {
      resetDraftState()
      return
    }
    activeTab.value = draft.activeTab ?? 'requirement'
    autoReviewEnabled.value = draft.autoReviewEnabled ?? true
    selectedWorkspaceCode.value = draft.selectedWorkspaceCode ?? selectedWorkspaceCode.value
    form.requirementTitle = draft.form.requirementTitle ?? ''
    form.requirementContent = draft.form.requirementContent ?? ''
    form.maxCases = Number(draft.form.maxCases ?? 12) || 12
    form.directoryId = typeof draft.form.directoryId === 'number' ? draft.form.directoryId : null
    generatedCases.value = Array.isArray(draft.generatedCases) ? draft.generatedCases.map(item => ({ ...item })) : []
    generationWarnings.value = Array.isArray(draft.generationWarnings) ? [...draft.generationWarnings] : []
    invalidCases.value = Array.isArray(draft.invalidCases) ? draft.invalidCases.map(item => ({ ...item })) : []
    aiReviewResult.value = draft.aiReviewResult
      ? {
        ...draft.aiReviewResult,
        issues: [...draft.aiReviewResult.issues],
        suggestions: [...draft.aiReviewResult.suggestions],
      }
      : null
    selectedSuggestions.value = Array.isArray(draft.selectedSuggestions) ? [...draft.selectedSuggestions] : []
    supplementNotes.value = draft.supplementNotes ?? ''
    requirementAssets.value = Array.isArray(draft.requirementAssets) ? draft.requirementAssets.map(item => ({ ...item })) : []
    generationMeta.systemMaxCases = draft.generationMeta?.systemMaxCases ?? 0
    generationMeta.requestedMaxCases = draft.generationMeta?.requestedMaxCases ?? 0
    generationMeta.effectiveMaxCases = draft.generationMeta?.effectiveMaxCases ?? 0
    generationMeta.actualGeneratedCount = draft.generationMeta?.actualGeneratedCount ?? 0
    lastReviewSource.value = draft.reviewSource ?? null
  } finally {
    hydratingDraft.value = false
  }
}

function loadDraft(targetCode = targetWorkspaceCode.value) {
  const raw = localStorage.getItem(resolveDraftStorageKey(targetCode))
  if (!raw) {
    applyDraft(null)
    return
  }
  try {
    applyDraft(JSON.parse(raw) as AiGenerateDraft)
  } catch {
    localStorage.removeItem(resolveDraftStorageKey(targetCode))
    applyDraft(null)
  }
}

function mapRequirementAssets(items: AiRequirementAsset[]) {
  return items.map(item => ({ ...item, selected: true }))
}

function setAllSelected(checked: boolean) {
  generatedCases.value = generatedCases.value.map(item => ({ ...item, selected: checked }))
}

function summarizeText(value: string | null | undefined, max = 96) {
  if (!value) {
    return '-'
  }
  const normalized = value.replace(/\s+/g, ' ').trim()
  if (!normalized) {
    return '-'
  }
  return normalized.length > max ? `${normalized.slice(0, max)}...` : normalized
}

function getCaseTypeLabel(caseType: string | null | undefined) {
  if (!caseType) {
    return '-'
  }
  return CASE_TYPE_LABELS[caseType] ?? caseType
}

function getPriorityClass(priority: string | null | undefined) {
  if (!priority) {
    return 'priority-chip priority-chip-default'
  }
  return `priority-chip priority-chip-${priority.toLowerCase()}`
}

function getReviewResultLabel(result: string | null | undefined) {
  if (!result) {
    return '-'
  }
  return REVIEW_RESULT_LABELS[result] ?? result
}

function getReviewResultClass(result: string | null | undefined) {
  if (!result) {
    return 'review-result-chip review-result-chip-neutral'
  }
  const normalized = result.toLowerCase()
  return `review-result-chip review-result-chip-${normalized}`
}

function cloneEditableCandidate(item: EditableGeneratedCase): EditableGeneratedCase {
  return {
    ...item,
    warnings: [...(item.warnings ?? [])],
  }
}

function syncCandidateDraft(index: number | null) {
  if (index == null) {
    candidateEditDraft.value = null
    return
  }
  const current = generatedCases.value[index]
  candidateEditDraft.value = current ? cloneEditableCandidate(current) : null
}

function openCandidateEditor(index: number) {
  if (!generatedCases.value[index]) {
    return
  }
  candidateEditIndex.value = index
  syncCandidateDraft(index)
  candidateEditDrawerVisible.value = true
}

function closeCandidateEditor() {
  candidateEditDrawerVisible.value = false
  syncCandidateDraft(candidateEditIndex.value)
}

function goToCandidate(offset: -1 | 1) {
  if (candidateEditIndex.value == null) {
    return
  }
  const nextIndex = candidateEditIndex.value + offset
  if (nextIndex < 0 || nextIndex >= generatedCases.value.length) {
    return
  }
  candidateEditIndex.value = nextIndex
  syncCandidateDraft(nextIndex)
}

function saveCandidateEditor() {
  if (candidateEditIndex.value == null || !candidateEditDraft.value) {
    return
  }
  generatedCases.value.splice(candidateEditIndex.value, 1, cloneEditableCandidate(candidateEditDraft.value))
  candidateEditDrawerVisible.value = false
  ElMessage.success('候选用例已保存')
}

function removeGeneratedCase(index: number) {
  generatedCases.value.splice(index, 1)
  if (candidateEditIndex.value == null) {
    return
  }
  if (!generatedCases.value.length) {
    candidateEditIndex.value = null
    candidateEditDraft.value = null
    candidateEditDrawerVisible.value = false
    return
  }
  if (candidateEditIndex.value === index) {
    const nextIndex = Math.min(index, generatedCases.value.length - 1)
    candidateEditIndex.value = nextIndex
    if (!generatedCases.value[nextIndex]) {
      candidateEditDraft.value = null
      candidateEditDrawerVisible.value = false
      return
    }
    syncCandidateDraft(nextIndex)
    return
  }
  if (candidateEditIndex.value > index) {
    candidateEditIndex.value -= 1
    syncCandidateDraft(candidateEditIndex.value)
  }
}

function triggerRequirementImport() {
  requirementFileInput.value?.click()
}

function triggerAssetUpload() {
  requirementAssetInput.value?.click()
}

async function handleRequirementFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }
  importingRequirement.value = true
  try {
    const response = await platformApi.importRequirementDocument(workspaceCode.value, file)
    form.requirementTitle = response.title || file.name.replace(/\.[^.]+$/, '')
    form.requirementContent = response.content
    requirementAssets.value = mapRequirementAssets(response.assets)
    ElMessage.success(`已导入需求文档：${response.fileName}`)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    importingRequirement.value = false
    input.value = ''
  }
}

async function handleAssetFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  if (!files.length) {
    return
  }
  uploadingAssets.value = true
  try {
    const response = await platformApi.uploadRequirementAssets(workspaceCode.value, files)
    requirementAssets.value = [...requirementAssets.value, ...mapRequirementAssets(response)]
    ElMessage.success(`已上传 ${response.length} 个需求素材`)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    uploadingAssets.value = false
    input.value = ''
  }
}

async function removeRequirementAsset(asset: RequirementAssetView) {
  try {
    await platformApi.deleteRequirementAsset(workspaceCode.value, asset.id)
    requirementAssets.value = requirementAssets.value.filter(item => item.id !== asset.id)
    ElMessage.success('已移除需求素材')
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function assetPreviewUrl(asset: RequirementAssetView) {
  return asset.downloadUrl ? resolveApiUrl(asset.downloadUrl) : ''
}

function normalizeRequestedMaxCases(showMessage = false) {
  if (!activeConfig.value) {
    return
  }
  if (form.maxCases > activeConfig.value.maxCases) {
    form.maxCases = activeConfig.value.maxCases
    if (showMessage) {
      ElMessage.warning(`已按系统上限调整为 ${activeConfig.value.maxCases} 条`)
    }
  }
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
    normalizeRequestedMaxCases()
  } catch (error) {
    activeConfig.value = null
    ElMessage.error((error as Error).message)
  } finally {
    loadingConfig.value = false
  }
}

async function loadDirectories() {
  if (!targetWorkspaceCode.value) {
    directoryWorkspace.value = null
    moduleOptions.value = []
    form.directoryId = null
    return
  }
  loadingDirectories.value = true
  try {
    const workspaces = await platformApi.getCaseDirectories(targetWorkspaceCode.value)
    directoryWorkspace.value = workspaces.find(item => item.workspaceCode === targetWorkspaceCode.value) ?? null
    moduleOptions.value = flattenModules(directoryWorkspace.value?.children ?? [])
    if (form.directoryId !== null && !moduleOptions.value.some(item => item.value === form.directoryId)) {
      form.directoryId = null
    }
  } catch (error) {
    directoryWorkspace.value = null
    moduleOptions.value = []
    ElMessage.error((error as Error).message)
  } finally {
    loadingDirectories.value = false
  }
}

function toExistingCasePayload(item: AiGeneratedCase) {
  return {
    title: item.title,
    caseType: item.caseType,
    priority: item.priority,
    precondition: item.precondition,
    steps: item.steps,
    expectedResult: item.expectedResult,
  }
}

function applyGenerationResult(
  response: {
    workspaceName: string
    provider: string
    model: string
    rawContent: string
    systemMaxCases: number
    requestedMaxCases: number
    effectiveMaxCases: number
    actualGeneratedCount: number
    generatedCases: AiGeneratedCase[]
    warnings: string[]
    invalidCases: AiInvalidCaseItem[]
  },
  mode: 'replace' | 'append',
) {
  lastGenerationWorkspaceName.value = response.workspaceName
  lastGenerationProvider.value = response.provider
  lastGenerationModel.value = response.model
  lastGenerationRawContent.value = response.rawContent
  generationMeta.systemMaxCases = response.systemMaxCases
  generationMeta.requestedMaxCases = response.requestedMaxCases
  generationMeta.effectiveMaxCases = response.effectiveMaxCases
  generationMeta.actualGeneratedCount = response.actualGeneratedCount

  const mapped = response.generatedCases.map(item => ({ ...item, selected: true }))
  generatedCases.value = mode === 'append' ? [...generatedCases.value, ...mapped] : mapped
  generationWarnings.value = mode === 'append'
    ? [...generationWarnings.value, ...response.warnings]
    : response.warnings
  invalidCases.value = mode === 'append'
    ? [...invalidCases.value, ...response.invalidCases]
    : response.invalidCases
}

function buildSupplementNotes() {
  const sections: string[] = []
  if (selectedSuggestions.value.length) {
    sections.push(`请优先补齐以下评审建议：\n${selectedSuggestions.value.map((item, index) => `${index + 1}. ${item}`).join('\n')}`)
  }
  if (supplementNotes.value.trim()) {
    sections.push(`人工补充意见：\n${supplementNotes.value.trim()}`)
  }
  return sections.join('\n\n')
}

async function runGenerate(mode: 'replace' | 'append') {
  if (!canGenerate.value) {
    ElMessage.error('请先补全目标空间、需求标题和需求内容')
    return false
  }
  if (!activeConfig.value || activeConfig.value.status !== 1) {
    ElMessage.error('当前没有可用的 AI 生成配置，请先到 AI 配置页启用生成模型')
    return false
  }
  if (hasSelectedAssets.value && !activeConfig.value.supportsImageInput) {
    ElMessage.error('当前模型不支持图片输入，请切换到支持图片输入的模型后再试')
    return false
  }

  if (mode === 'append') {
    supplementing.value = true
  } else {
    generating.value = true
  }

  try {
    const response = await platformApi.generateAiCases(workspaceCode.value, {
      workspaceCode: isAllScope.value ? targetWorkspaceCode.value : undefined,
      requirementTitle: form.requirementTitle.trim(),
      requirementContent: form.requirementContent.trim(),
      assetIds: requirementAssets.value.filter(item => item.selected).map(item => item.id),
      improvementNotes: mode === 'append' ? buildSupplementNotes() : undefined,
      existingCases: mode === 'append' ? generatedCases.value.map(toExistingCasePayload) : undefined,
      maxCases: form.maxCases,
    })
    applyGenerationResult(response, mode)
    if (mode === 'append') {
      selectedSuggestions.value = []
      supplementNotes.value = ''
    }
    ElMessage.success(mode === 'append' ? `已追加 ${response.actualGeneratedCount} 条候选用例` : `已生成 ${response.actualGeneratedCount} 条候选用例`)
    return true
  } catch (error) {
    ElMessage.error((error as Error).message)
    return false
  } finally {
    generating.value = false
    supplementing.value = false
  }
}

async function runGenerateWithOptionalReview(mode: 'replace' | 'append') {
  const succeeded = await runGenerate(mode)
  if (!succeeded) {
    return
  }
  if (generatedCases.value.length && autoReviewEnabled.value) {
    await runAiReview('AUTO', { silent: true })
    if (aiReviewResult.value) {
      ElMessage.success(mode === 'append' ? '已追加候选并完成自动评审' : '已生成候选用例并完成自动评审')
    }
  }
}

async function regenerateCase(index: number) {
  const current = generatedCases.value[index]
  if (!current) {
    return
  }
  if (!canGenerate.value) {
    ElMessage.error('请先补全需求信息后再重生')
    return
  }
  regeneratingIndex.value = index
  try {
    const response = await platformApi.generateAiCases(workspaceCode.value, {
      workspaceCode: isAllScope.value ? targetWorkspaceCode.value : undefined,
      requirementTitle: form.requirementTitle.trim(),
      requirementContent: `${form.requirementContent.trim()}\n\n请重点补一条不同于现有候选的测试用例，参考当前目标用例：${current.title}`,
      assetIds: requirementAssets.value.filter(item => item.selected).map(item => item.id),
      existingCases: generatedCases.value.map(toExistingCasePayload),
      maxCases: 1,
    })
    const replacement = response.generatedCases[0]
    if (!replacement) {
      throw new Error('模型未返回可替换的候选用例')
    }
    generatedCases.value.splice(index, 1, { ...replacement, selected: true })
    ElMessage.success('已重生当前候选用例')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    regeneratingIndex.value = null
  }
}

async function runAiReview(source: ReviewExecutionSource = 'MANUAL', options?: { silent?: boolean }) {
  if (!canReviewGeneratedCases.value) {
    ElMessage.error('请先生成候选用例后再做 AI 评审')
    return
  }
  reviewing.value = true
  try {
    const response = await platformApi.reviewAiGeneratedCases(workspaceCode.value, {
      requirementTitle: form.requirementTitle.trim(),
      requirementContent: form.requirementContent.trim(),
      generatedCases: generatedCases.value.map(toExistingCasePayload),
    })
    aiReviewResult.value = response
    selectedSuggestions.value = [...response.suggestions]
    lastReviewSource.value = source
    if (!options?.silent) {
      ElMessage.success(source === 'AUTO' ? '已完成自动评审' : 'AI 评审已完成')
    }
    return response
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    reviewing.value = false
  }
}

function adoptAiReviewSummary() {
  if (!aiReviewResult.value?.summary) {
    return
  }
  supplementNotes.value = supplementNotes.value.trim()
    ? `${supplementNotes.value.trim()}\n${aiReviewResult.value.summary}`
    : aiReviewResult.value.summary
  ElMessage.success('已把评审总结加入补充说明')
}

function openDetailDrawer() {
  detailDrawerVisible.value = true
}

function openReviewDrawer() {
  reviewDrawerVisible.value = true
}

async function saveGeneratedCases() {
  const casesToSave = generatedCases.value.filter(item => item.selected)
  if (!casesToSave.length) {
    ElMessage.error('请先勾选要写入用例中心的候选用例')
    return
  }
  saving.value = true
  try {
    for (const item of casesToSave) {
      const payload: CreateCasePayload = {
        workspaceCode: isAllScope.value ? targetWorkspaceCode.value : undefined,
        directoryId: form.directoryId,
        title: item.title.trim(),
        caseType: item.caseType || 'FUNCTION',
        priority: item.priority || 'P1',
        sourceType: 'AI生成',
        caseStatus: '草稿',
        ownerId: null,
        precondition: item.precondition.trim(),
        steps: item.steps.trim(),
        expectedResult: item.expectedResult.trim(),
      }
      await platformApi.createCase(workspaceCode.value, payload)
    }
    ElMessage.success(`已写入 ${casesToSave.length} 条用例到用例中心`)
    resetGeneratedCases()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    saving.value = false
  }
}

async function generateAndGo() {
  if (!canGenerate.value || generating.value || supplementing.value) {
    return
  }
  await runGenerateWithOptionalReview('replace')
  if (generatedCases.value.length) {
    activeTab.value = 'generate'
  }
}

watch(
  [workspaceCode, selectedWorkspaceCode],
  async ([currentWorkspace]) => {
    if (!isAllScope.value) {
      selectedWorkspaceCode.value = currentWorkspace
    }
    await Promise.all([loadConfig(), loadDirectories()])
    loadDraft()
  },
)

watch(
  [
    activeTab,
    selectedWorkspaceCode,
    autoReviewEnabled,
    () => form.requirementTitle,
    () => form.requirementContent,
    () => form.maxCases,
    () => form.directoryId,
    generatedCases,
    generationWarnings,
    invalidCases,
    aiReviewResult,
    selectedSuggestions,
    supplementNotes,
    requirementAssets,
    () => generationMeta.systemMaxCases,
    () => generationMeta.requestedMaxCases,
    () => generationMeta.effectiveMaxCases,
    () => generationMeta.actualGeneratedCount,
  ],
  () => {
    persistDraft()
    localStorage.setItem(AI_GENERATE_TAB_STORAGE_KEY, activeTab.value)
  },
  { deep: true },
)

watch(candidateEditDrawerVisible, (visible) => {
  if (!visible) {
    syncCandidateDraft(candidateEditIndex.value)
  }
})

onMounted(async () => {
  const rememberedTab = localStorage.getItem(AI_GENERATE_TAB_STORAGE_KEY)
  if (rememberedTab === 'requirement' || rememberedTab === 'generate') {
    activeTab.value = rememberedTab
  }
  await loadSharedBase()
  await Promise.all([loadConfig(), loadDirectories()])
  loadDraft()
  normalizeRequestedMaxCases()
})
</script>

<template>
  <section class="page-shell">
    <div class="ai-generate-layout">
      <article class="panel-card ai-workspace-panel">
        <el-tabs v-model="activeTab" class="ai-inner-tabs">
          <el-tab-pane label="需求" name="requirement">
            <div class="requirement-stage">
              <div class="panel-header">
                <div>
                  <div class="panel-title">需求输入</div>
                  <div class="panel-subtitle">先整理本次需求和材料，再一键生成候选用例。</div>
                </div>
              </div>

              <el-form label-width="92px" class="generate-config-form">
                <div class="generate-config-meta">
                  <el-form-item v-if="isAllScope" label="目标空间" required>
                    <el-select v-model="selectedWorkspaceCode" placeholder="请选择目标空间">
                      <el-option
                        v-for="item in writableWorkspaces"
                        :key="item.code"
                        :label="item.name"
                        :value="item.code"
                        />
                      </el-select>
                    </el-form-item>
                </div>

                <div class="requirement-top-actions">
                  <div class="requirement-editor-actions">
                    <input
                      ref="requirementFileInput"
                      type="file"
                      accept=".txt,.md,.doc,.docx,.pdf"
                      class="hidden-file-input"
                      @change="handleRequirementFileChange"
                    >
                    <el-button :loading="importingRequirement" @click="triggerRequirementImport">
                      <el-icon><Upload /></el-icon>
                      上传文档
                    </el-button>
                    <input
                      ref="requirementAssetInput"
                      type="file"
                      accept=".png,.jpg,.jpeg,.webp"
                      multiple
                      class="hidden-file-input"
                      @change="handleAssetFileChange"
                    >
                    <el-button :loading="uploadingAssets" @click="triggerAssetUpload">
                      <el-icon><Upload /></el-icon>
                      上传图片
                    </el-button>
                    <el-button
                      type="primary"
                      :loading="generating"
                      :disabled="!canGenerate"
                      @click="generateAndGo"
                    >
                      <el-icon><MagicStick /></el-icon>
                      生成用例
                    </el-button>
                  </div>
                </div>

                <el-form-item label="需求标题" required>
                  <el-input
                    v-model="form.requirementTitle"
                    placeholder="例如：成员停用后按空间视角的展示规则"
                  />
                </el-form-item>
              </el-form>

              <section class="detail-card requirement-editor-card">
                <div class="requirement-editor-grid">
                  <div class="requirement-text-area">
                    <div class="requirement-block-title">需求文本</div>
                    <el-input
                      v-model="form.requirementContent"
                      type="textarea"
                      :rows="18"
                      resize="vertical"
                      placeholder="这里填写最终要交给 AI 的需求描述、约束、重点风险和覆盖目标。"
                    />
                  </div>

                  <aside class="requirement-material-area">
                    <div class="requirement-block-title">需求材料</div>
                    <div class="material-summary">
                      <div class="material-summary-item">
                        <span class="material-summary-label">当前配置</span>
                        <span class="material-summary-value">{{ configStatusText }}</span>
                      </div>
                      <div class="material-summary-item">
                        <span class="material-summary-label">图片能力</span>
                        <span class="material-summary-value">{{ assetCapabilityText }}</span>
                      </div>
                    </div>

                    <div v-if="requirementAssets.length" class="requirement-asset-list">
                      <div v-for="asset in requirementAssets" :key="asset.id" class="requirement-asset-item">
                        <div class="requirement-asset-top">
                          <el-checkbox v-model="asset.selected" />
                          <span class="requirement-asset-meta">{{ asset.sourceType }}</span>
                          <el-button text type="danger" @click="removeRequirementAsset(asset)">移除</el-button>
                        </div>
                        <a
                          v-if="assetPreviewUrl(asset)"
                          :href="assetPreviewUrl(asset)"
                          target="_blank"
                          rel="noreferrer"
                          class="requirement-asset-preview"
                        >
                          <img :src="assetPreviewUrl(asset)" :alt="asset.fileName" class="requirement-asset-image">
                        </a>
                        <div v-else class="requirement-asset-preview requirement-asset-fallback">文档预览</div>
                        <a
                          v-if="assetPreviewUrl(asset)"
                          :href="assetPreviewUrl(asset)"
                          target="_blank"
                          rel="noreferrer"
                          class="requirement-asset-link"
                        >
                          {{ asset.fileName }}
                        </a>
                        <span v-else class="requirement-asset-link">{{ asset.fileName }}</span>
                      </div>
                    </div>
                    <div v-else class="empty-block compact-block material-empty">
                      <div class="empty-title">还没有需求材料</div>
                      <div class="empty-desc">可以上传需求文档或图片，作为 AI 生成时的辅助输入。</div>
                    </div>
                  </aside>
                </div>
              </section>
            </div>
          </el-tab-pane>

          <el-tab-pane label="用例生成" name="generate">
            <div class="generate-tab-layout">
              <div class="generate-content-grid">
                <article class="detail-card generate-action-card">
                  <div class="panel-header compact-header">
                    <div>
                      <div class="panel-title">生成操作</div>
                      <div class="panel-subtitle">先生成候选用例，再决定要不要做补漏评审。</div>
                    </div>
                    <el-button text @click="openDetailDrawer">查看详情</el-button>
                  </div>

                  <div class="generate-action-note">
                    当前空间：{{ targetWorkspaceCode || '未选择' }}；已生成 {{ generatedCases.length }} 条候选用例。
                  </div>
                  <div class="generate-review-toggle-row">
                    <div class="generate-review-toggle-main">
                      <span class="generate-review-toggle-label">自动评审</span>
                      <el-switch v-model="autoReviewEnabled" inline-prompt active-text="开" inactive-text="关" />
                      <span class="generate-review-toggle-state">
                        {{ autoReviewEnabled ? '已开启' : '已关闭' }}
                      </span>
                    </div>
                    <span class="generate-review-toggle-desc">
                      {{ autoReviewEnabled ? '生成后自动跑一轮 AI 评审，并把结果直接带到候选区上方。' : '当前只生成候选，用你手动点击“评审本轮候选”再做补漏。' }}
                    </span>
                  </div>
                  <div class="generate-config-actions">
                    <el-button
                      type="primary"
                      :loading="generating"
                      :disabled="!canGenerate"
                      @click="runGenerateWithOptionalReview('replace')"
                    >
                      <el-icon><MagicStick /></el-icon>
                      生成候选用例
                    </el-button>
                    <el-button
                      :loading="supplementing"
                      :disabled="!canGenerate || !generatedCases.length"
                      @click="runGenerateWithOptionalReview('append')"
                    >
                      <el-icon><RefreshRight /></el-icon>
                      追加生成
                    </el-button>
                  </div>
                  <div v-if="generationMetaText" class="generate-mini-meta">{{ generationMetaText }}</div>
                </article>

                <article class="detail-card generate-review-card">
                  <div class="panel-header compact-header">
                    <div>
                      <div class="panel-title">评审控制</div>
                      <div class="panel-subtitle">针对当前整批候选做覆盖检查，决定后续补漏方向。</div>
                    </div>
                    <div class="review-panel-actions">
                      <el-button text :disabled="!aiReviewResult" @click="openReviewDrawer">查看详情</el-button>
                      <el-button
                        :loading="reviewing"
                        :disabled="!canReviewGeneratedCases"
                        @click="runAiReview('MANUAL')"
                      >
                        {{ aiReviewResult ? '重新评审本轮候选' : '评审本轮候选' }}
                      </el-button>
                    </div>
                  </div>

                  <div class="review-control-meta">
                    <div class="review-control-item">
                      <span class="review-control-label">评审来源</span>
                      <span class="review-control-value">{{ reviewSourceText }}</span>
                    </div>
                    <div class="review-control-item">
                      <span class="review-control-label">建议补充</span>
                      <span class="review-control-value">{{ aiReviewResult?.suggestions?.length ?? 0 }} 条</span>
                    </div>
                    <div class="review-control-item">
                      <span class="review-control-label">发现问题</span>
                      <span class="review-control-value">{{ aiReviewResult?.issues?.length ?? 0 }} 项</span>
                    </div>
                  </div>

                  <div v-if="aiReviewResult" class="review-control-summary">
                    <span :class="getReviewResultClass(aiReviewResult.result)">{{ getReviewResultLabel(aiReviewResult.result) }}</span>
                    <span class="review-control-summary-text">{{ aiReviewResult.summary }}</span>
                  </div>
                  <div v-else-if="generatedCases.length" class="review-control-idle">
                    <div class="review-control-idle-title">当前还没做评审</div>
                    <div class="review-control-idle-text">
                      {{ autoReviewEnabled ? '可重新发起一轮评审，检查这批候选是否还需要补漏。' : '这批候选还没评审，点击右上角按钮即可开始手动评审。' }}
                    </div>
                  </div>
                  <div v-else class="empty-block compact-block review-empty-block">
                    <div class="empty-title">还没有评审结果</div>
                    <div class="empty-desc">
                      {{ autoReviewEnabled ? '生成候选用例后会自动补一轮 AI 评审。' : '先生成候选用例，再按你的节奏手动评审。' }}
                    </div>
                  </div>
                </article>
              </div>

              <div v-if="generationWarnings.length || invalidCases.length" class="ai-feedback-panel generate-feedback-row">
                <div v-if="generationWarnings.length" class="ai-feedback-block">
                  <div class="ai-feedback-title">生成提示</div>
                  <ul class="ai-feedback-list">
                    <li v-for="(warning, index) in generationWarnings" :key="`warning-${index}`">{{ warning }}</li>
                  </ul>
                </div>
                <div v-if="invalidCases.length" class="ai-feedback-block ai-feedback-block-danger">
                  <div class="ai-feedback-title">未纳入结果的候选项</div>
                  <ul class="ai-feedback-list">
                    <li v-for="item in invalidCases" :key="`invalid-${item.index}`">
                      第 {{ item.index }} 项<span v-if="item.title">（{{ item.title }}）</span>：{{ item.reason }}
                    </li>
                  </ul>
                </div>
              </div>

              <section v-if="aiReviewResult" class="detail-card ai-review-summary-banner">
                <div class="ai-review-summary-main">
                  <span :class="getReviewResultClass(aiReviewResult.result)">{{ getReviewResultLabel(aiReviewResult.result) }}</span>
                  <div class="ai-review-summary-copy">
                    <div class="ai-review-summary-title">{{ aiReviewResult.summary }}</div>
                    <div class="ai-review-summary-meta">
                      <span>{{ reviewSourceText }}</span>
                      <span>问题 {{ aiReviewResult.issues.length }} 项</span>
                      <span>建议 {{ aiReviewResult.suggestions.length }} 条</span>
                      <span>已选 {{ selectedSuggestionCount }} 条</span>
                    </div>
                  </div>
                </div>
                <div class="ai-review-summary-actions">
                  <el-button text @click="openReviewDrawer">查看评审详情</el-button>
                  <el-button text @click="adoptAiReviewSummary">采纳总结</el-button>
                  <el-button
                    type="primary"
                    :loading="supplementing"
                    :disabled="!canSupplementGenerate"
                    @click="runGenerate('append')"
                  >
                    按评审意见追加生成
                  </el-button>
                </div>
              </section>

              <article class="detail-card ai-generated-panel">
                <div class="panel-header compact-header">
                  <div>
                    <div class="panel-title">候选用例编辑区</div>
                    <div class="panel-subtitle">筛选、微调候选用例后，再勾选写入正式用例中心。</div>
                  </div>
                  <div class="result-toolbar-actions">
                    <div class="generated-toolbar-meta">已选 {{ selectedCount }} / {{ generatedCases.length }}</div>
                    <el-button :disabled="!generatedCases.length" @click="setAllSelected(true)">全选</el-button>
                    <el-button :disabled="!generatedCases.length" @click="setAllSelected(false)">取消全选</el-button>
                    <el-button type="primary" :loading="saving" :disabled="!canSave" @click="saveGeneratedCases">
                      <el-icon><Plus /></el-icon>
                      写入用例中心
                    </el-button>
                  </div>
                </div>

                <div v-if="generatedCases.length" class="generated-case-list">
                  <div v-for="(item, index) in generatedCases" :key="index" class="generated-case-card">
                    <div class="generated-case-head">
                      <div class="generated-case-main-meta">
                        <el-checkbox v-model="item.selected" />
                        <el-tag size="small" effect="plain">{{ getCaseTypeLabel(item.caseType) }}</el-tag>
                        <span :class="getPriorityClass(item.priority)">{{ item.priority }}</span>
                        <span class="generated-case-title">{{ item.title || `候选用例 ${index + 1}` }}</span>
                      </div>
                      <div class="generated-case-meta">
                        <el-button text @click="openCandidateEditor(index)">编辑</el-button>
                        <el-button text :loading="regeneratingIndex === index" @click="regenerateCase(index)">重生</el-button>
                        <el-button text type="danger" @click="removeGeneratedCase(index)">删除</el-button>
                      </div>
                    </div>

                    <div v-if="item.warnings.length" class="generated-case-warnings">
                      <span v-for="(warning, warningIndex) in item.warnings" :key="`${index}-${warningIndex}`" class="generated-case-warning">
                        {{ warning }}
                      </span>
                    </div>

                    <div class="generated-case-summary-grid">
                      <div class="generated-case-summary-item">
                        <span class="generated-case-summary-label">前置条件</span>
                        <span class="generated-case-summary-value">{{ summarizeText(item.precondition, 120) }}</span>
                      </div>
                      <div class="generated-case-summary-item">
                        <span class="generated-case-summary-label">测试步骤</span>
                        <span class="generated-case-summary-value">{{ summarizeText(item.steps, 140) }}</span>
                      </div>
                      <div class="generated-case-summary-item">
                        <span class="generated-case-summary-label">预期结果</span>
                        <span class="generated-case-summary-value">{{ summarizeText(item.expectedResult, 120) }}</span>
                      </div>
                      <div class="generated-case-summary-item">
                        <span class="generated-case-summary-label">风险备注</span>
                        <span class="generated-case-summary-value">{{ summarizeText(item.riskNotes, 120) }}</span>
                      </div>
                    </div>
                  </div>
                </div>

                <div v-else class="empty-block">
                  <div class="empty-title">还没有生成结果</div>
                  <div class="empty-desc">先回到“需求”页整理输入，然后在这里生成候选用例。</div>
                </div>
              </article>
            </div>
          </el-tab-pane>
        </el-tabs>
      </article>
    </div>
  

    <el-drawer v-model="detailDrawerVisible" title="生成详情" size="44%">
      <div class="detail-drawer-content">
        <div class="detail-drawer-block">
          <div class="detail-drawer-label">模型</div>
          <div class="detail-drawer-value">{{ lastGenerationProvider || '-' }} / {{ lastGenerationModel || '-' }}</div>
        </div>
        <div class="detail-drawer-block">
          <div class="detail-drawer-label">空间</div>
          <div class="detail-drawer-value">{{ lastGenerationWorkspaceName || targetWorkspaceCode || '-' }}</div>
        </div>
        <div class="detail-drawer-block">
          <div class="detail-drawer-label">生成摘要</div>
          <div class="detail-drawer-value">{{ generationMetaText || '还没有生成记录。' }}</div>
        </div>
        <div v-if="generationWarnings.length" class="detail-drawer-block">
          <div class="detail-drawer-label">提示</div>
          <ul class="detail-drawer-list">
            <li v-for="(warning, index) in generationWarnings" :key="`drawer-warning-${index}`">{{ warning }}</li>
          </ul>
        </div>
        <div v-if="invalidCases.length" class="detail-drawer-block">
          <div class="detail-drawer-label">未纳入结果</div>
          <ul class="detail-drawer-list">
            <li v-for="item in invalidCases" :key="`drawer-invalid-${item.index}`">
              第 {{ item.index }} 项<span v-if="item.title">（{{ item.title }}）</span>：{{ item.reason }}
            </li>
          </ul>
        </div>
        <div class="detail-drawer-block">
          <div class="detail-drawer-label">原始返回</div>
          <pre class="detail-drawer-pre">{{ lastGenerationRawContent || '暂无原始返回。' }}</pre>
        </div>
      </div>
    </el-drawer>

    <el-drawer v-model="reviewDrawerVisible" title="评审详情" size="46%">
      <div class="detail-drawer-content">
        <div class="detail-drawer-block">
          <div class="detail-drawer-label">评审结果</div>
          <div class="detail-drawer-value">
            <span v-if="aiReviewResult" :class="getReviewResultClass(aiReviewResult.result)">{{ getReviewResultLabel(aiReviewResult.result) }}</span>
            <span v-else>-</span>
          </div>
        </div>
        <div class="detail-drawer-block">
          <div class="detail-drawer-label">评审来源</div>
          <div class="detail-drawer-value">{{ reviewSourceText }}</div>
        </div>
        <div class="detail-drawer-block">
          <div class="detail-drawer-label">评审总结</div>
          <div class="detail-drawer-value">{{ aiReviewResult?.summary || '还没有评审结果。' }}</div>
        </div>
        <div v-if="aiReviewResult?.issues?.length" class="detail-drawer-block">
          <div class="detail-drawer-label">问题点</div>
          <ul class="detail-drawer-list">
            <li v-for="(issue, index) in aiReviewResult.issues" :key="`drawer-issue-${index}`">{{ issue }}</li>
          </ul>
        </div>
        <div v-if="aiReviewResult?.suggestions?.length" class="detail-drawer-block">
          <div class="detail-drawer-label">补充建议</div>
          <div class="ai-review-summary-line">已选 {{ selectedSuggestionCount }} / {{ aiReviewResult.suggestions.length }}</div>
          <el-checkbox-group v-model="selectedSuggestions" class="ai-suggestion-list">
            <el-checkbox
              v-for="(suggestion, index) in aiReviewResult.suggestions"
              :key="`drawer-suggestion-${index}`"
              :label="suggestion"
              :value="suggestion"
              class="ai-suggestion-item"
            >
              <span class="ai-review-card-index">{{ index + 1 }}</span>
              <span class="ai-review-card-content">{{ suggestion }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </div>
        <div class="detail-drawer-block">
          <div class="detail-drawer-label">人工补充意见</div>
          <el-input
            v-model="supplementNotes"
            type="textarea"
            :rows="6"
            resize="vertical"
            placeholder="可以补充你自己的评审意见，再基于这些意见追加生成。"
          />
        </div>
        <div class="detail-drawer-actions">
          <el-button text @click="adoptAiReviewSummary" :disabled="!aiReviewResult?.summary">采纳总结</el-button>
          <el-button
            type="primary"
            :loading="supplementing"
            :disabled="!canSupplementGenerate"
            @click="runGenerate('append')"
          >
            按评审意见追加生成
          </el-button>
        </div>
        <div class="detail-drawer-block">
          <div class="detail-drawer-label">原始返回</div>
          <pre class="detail-drawer-pre">{{ aiReviewResult?.rawContent || '暂无原始返回。' }}</pre>
        </div>
      </div>
    </el-drawer>

    <el-drawer
      v-model="candidateEditDrawerVisible"
      title="编辑候选用例"
      size="52%"
      :destroy-on-close="false"
    >
      <template v-if="activeCandidateDraft">
        <div class="candidate-edit-drawer">
          <div class="candidate-edit-meta">
            <el-tag size="small" effect="plain">{{ getCaseTypeLabel(activeCandidateDraft.caseType) }}</el-tag>
            <span :class="getPriorityClass(activeCandidateDraft.priority)">{{ activeCandidateDraft.priority }}</span>
            <span class="candidate-edit-index">第 {{ candidateEditIndex === null ? 0 : candidateEditIndex + 1 }} / {{ generatedCases.length }} 条</span>
          </div>

          <el-form label-width="84px" class="generated-case-form">
            <el-form-item label="用例名称">
              <el-input v-model="activeCandidateDraft.title" />
            </el-form-item>
            <div class="generated-case-row">
              <el-form-item label="优先级">
                <el-select v-model="activeCandidateDraft.priority">
                  <el-option label="P0" value="P0" />
                  <el-option label="P1" value="P1" />
                  <el-option label="P2" value="P2" />
                  <el-option label="P3" value="P3" />
                </el-select>
              </el-form-item>
              <el-form-item label="用例类型">
                <el-select v-model="activeCandidateDraft.caseType">
                  <el-option label="功能" value="FUNCTION" />
                  <el-option label="边界" value="BOUNDARY" />
                  <el-option label="异常" value="EXCEPTION" />
                  <el-option label="回归" value="REGRESSION" />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item label="前置条件">
              <el-input v-model="activeCandidateDraft.precondition" type="textarea" :rows="3" resize="vertical" />
            </el-form-item>
            <el-form-item label="测试步骤">
              <el-input v-model="activeCandidateDraft.steps" type="textarea" :rows="6" resize="vertical" />
            </el-form-item>
            <el-form-item label="预期结果">
              <el-input v-model="activeCandidateDraft.expectedResult" type="textarea" :rows="4" resize="vertical" />
            </el-form-item>
            <el-form-item label="风险备注">
              <el-input v-model="activeCandidateDraft.riskNotes" type="textarea" :rows="3" resize="vertical" />
            </el-form-item>
          </el-form>
        </div>
      </template>

      <template #footer>
        <div class="candidate-edit-footer">
          <div class="candidate-edit-nav">
            <el-button :disabled="!canGoPrevCandidate" @click="goToCandidate(-1)">上一条</el-button>
            <el-button :disabled="!canGoNextCandidate" @click="goToCandidate(1)">下一条</el-button>
          </div>
          <div class="candidate-edit-actions">
            <el-button @click="closeCandidateEditor">取消</el-button>
            <el-button type="primary" @click="saveCandidateEditor">保存</el-button>
          </div>
        </div>
      </template>
    </el-drawer>
</section>
</template>

<style scoped>
.ai-generate-layout {
  display: grid;
  gap: 16px;
}

.ai-workspace-panel,
.generate-review-card,
.ai-generated-panel,
.requirement-main,
.requirement-side {
  min-width: 0;
}

.ai-inner-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}

.ai-inner-tabs :deep(.el-tabs__content) {
  overflow: visible;
}

.requirement-stage {
  display: grid;
  gap: 14px;
}

.generate-tab-layout {
  display: grid;
  gap: 16px;
}

.generate-content-grid {
  display: grid;
  grid-template-columns: minmax(340px, 1fr) minmax(320px, 0.9fr);
  gap: 16px;
  align-items: start;
}

.generate-action-card,
.generate-review-card {
  height: 100%;
}

.panel-header-actions {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.compact-header {
  margin-bottom: 10px;
}

.requirement-editor-card {
  padding: 16px;
}

.requirement-top-actions {
  margin-bottom: 18px;
}

.requirement-editor-actions {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.requirement-editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.9fr);
  gap: 16px;
  align-items: stretch;
}

.requirement-block-title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
}

.requirement-text-area,
.requirement-material-area {
  min-width: 0;
  height: 100%;
}

.requirement-text-area {
  display: flex;
  flex-direction: column;
}

.requirement-text-area :deep(.el-textarea) {
  flex: 1;
}

.requirement-text-area :deep(.el-textarea__inner) {
  min-height: 520px;
  max-height: 100%;
  box-sizing: border-box;
}

.hidden-file-input {
  display: none;
}

.generate-config-form {
  margin-top: 2px;
}

.generate-config-meta {
  display: grid;
  grid-template-columns: repeat(1, minmax(0, 1fr));
  gap: 12px;
}

.generate-config-meta :deep(.el-form-item) {
  margin-bottom: 18px;
}

.generate-config-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
}

.generate-config-note {
  font-size: 13px;
  color: var(--text-subtle);
  line-height: 1.6;
}

.material-summary {
  display: grid;
  gap: 10px;
  margin-bottom: 12px;
}

.material-summary-item {
  display: grid;
  gap: 4px;
}

.material-summary-label {
  font-size: 12px;
  color: var(--text-subtle);
}

.material-summary-value {
  font-size: 13px;
  color: var(--text-main);
  line-height: 1.5;
}

.material-empty {
  min-height: 220px;
  flex: 1;
}

.requirement-material-area {
  display: flex;
  flex-direction: column;
}

.requirement-asset-list {
  flex: 1;
  align-content: start;
}

.generate-config-actions,
.result-toolbar-actions,
.generated-case-main-meta,
.generated-case-meta,
.ai-review-actions,
.ai-review-footer,
.review-panel-actions,
.ai-review-summary-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.result-toolbar-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.generated-toolbar-meta,
.asset-card-summary,
.ai-review-summary-line {
  font-size: 12px;
  color: var(--text-subtle);
}

.ai-feedback-panel {
  display: grid;
  gap: 10px;
}

.generate-feedback-row {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.ai-feedback-block {
  padding: 12px 14px;
  border-radius: 10px;
  background: rgba(59, 130, 246, 0.08);
}

.ai-feedback-block-neutral {
  background: rgba(15, 23, 42, 0.04);
}

.ai-feedback-block-danger {
  background: rgba(239, 68, 68, 0.08);
}

.ai-feedback-title,
.ai-review-section-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
}

.ai-feedback-list {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 18px;
  color: var(--text-subtle);
  font-size: 13px;
}

.ai-feedback-text {
  color: var(--text-subtle);
  font-size: 13px;
  line-height: 1.6;
}

.ai-review-section {
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
}

.ai-review-section-issues {
  background: rgba(254, 242, 242, 0.78);
}

.ai-review-section-suggestions {
  background: rgba(239, 246, 255, 0.82);
}

.ai-review-section-notes {
  background: rgba(248, 250, 252, 0.92);
}

.ai-review-card-list {
  display: grid;
  gap: 8px;
}

.ai-review-card-item {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding: 10px 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.82);
}

.ai-review-card-item-issue {
  background: rgba(255, 255, 255, 0.86);
}

.ai-review-card-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  color: var(--text-subtle);
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  flex-shrink: 0;
}

.ai-review-card-content {
  min-width: 0;
  color: var(--text-main);
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}

.ai-suggestion-list {
  display: grid;
  gap: 8px;
}

.ai-suggestion-item {
  margin-right: 0;
  padding: 10px 12px;
  border: 1px solid rgba(59, 130, 246, 0.14);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.86);
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}

.ai-suggestion-item :deep(.el-checkbox__label) {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  white-space: normal;
  padding-left: 8px;
}

.ai-suggestion-item :deep(.el-checkbox__input.is-checked + .el-checkbox__label) {
  color: inherit;
}

.ai-suggestion-item:has(.el-checkbox__input.is-checked) {
  border-color: rgba(59, 130, 246, 0.28);
  background: rgba(219, 234, 254, 0.72);
  box-shadow: inset 0 0 0 1px rgba(59, 130, 246, 0.08);
}

.review-result-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.review-result-chip-approve {
  color: #067647;
  background: rgba(220, 252, 231, 0.92);
  border-color: rgba(34, 197, 94, 0.22);
}

.review-result-chip-reject {
  color: #b42318;
  background: rgba(254, 228, 226, 0.92);
  border-color: rgba(240, 68, 56, 0.22);
}

.review-result-chip-suggest,
.review-result-chip-neutral {
  color: #175cd3;
  background: rgba(219, 234, 254, 0.92);
  border-color: rgba(59, 130, 246, 0.22);
}

.ai-review-footer {
  justify-content: flex-end;
  margin-top: 14px;
}

.review-empty-block {
  min-height: 132px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.generate-summary-title {
  margin-top: 8px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-main);
}

.generate-summary-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-subtle);
}

.generate-summary-text {
  margin-top: 12px;
  white-space: pre-wrap;
  color: var(--text-main);
  line-height: 1.7;
  max-height: 180px;
  overflow: auto;
}

.generate-action-note {
  margin-top: 8px;
  margin-bottom: 14px;
  font-size: 13px;
  color: var(--text-subtle);
  line-height: 1.6;
}

.generate-review-toggle-row {
  display: flex;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.92);
}

.generate-review-toggle-main {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.generate-review-toggle-label,
.review-control-label {
  font-size: 12px;
  color: var(--text-subtle);
}

.generate-review-toggle-state {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  color: var(--text-main);
  font-size: 12px;
  line-height: 1;
}

.generate-review-toggle-desc {
  flex: 1 1 100%;
  font-size: 12px;
  color: var(--text-subtle);
  line-height: 1.5;
}

.generate-mini-meta {
  margin-top: 14px;
  font-size: 12px;
  color: var(--text-subtle);
  line-height: 1.6;
}

.review-control-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.review-control-item {
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.88);
}

.review-control-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
}

.review-control-summary {
  margin-top: 14px;
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(59, 130, 246, 0.16);
  border-radius: 10px;
  background: rgba(239, 246, 255, 0.78);
}

.review-control-summary-text {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-main);
}

.review-control-idle {
  margin-top: 14px;
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px dashed rgba(15, 23, 42, 0.14);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.72);
}

.review-control-idle-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
}

.review-control-idle-text {
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-subtle);
}

.ai-review-summary-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
}

.ai-review-summary-main {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}

.ai-review-summary-copy {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.ai-review-summary-title {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-main);
}

.ai-review-summary-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--text-subtle);
}

.ai-review-summary-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.asset-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.requirement-assets-card {
  margin-top: 12px;
}

.requirement-asset-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  margin-top: 10px;
}

.requirement-asset-item {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.82);
}

.requirement-asset-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.requirement-asset-top .el-button {
  margin-left: auto;
}

.requirement-asset-preview {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.05);
}

.requirement-asset-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-subtle);
  font-size: 12px;
}

.requirement-asset-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.requirement-asset-link {
  color: var(--text-main);
  text-decoration: none;
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}

.requirement-asset-link:hover {
  text-decoration: underline;
}

.requirement-asset-meta {
  font-size: 12px;
  color: var(--text-subtle);
}

.generated-case-list {
  display: grid;
  gap: 10px;
}

.generated-case-card {
  padding: 12px 14px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.78);
}

.generated-case-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.generated-case-main-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.generated-case-title {
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-main);
  line-height: 1.5;
}

.priority-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.priority-chip-p0 {
  color: #b42318;
  background: rgba(254, 228, 226, 0.92);
  border-color: rgba(240, 68, 56, 0.2);
}

.priority-chip-p1 {
  color: #b54708;
  background: rgba(255, 239, 213, 0.92);
  border-color: rgba(245, 158, 11, 0.24);
}

.priority-chip-p2 {
  color: #175cd3;
  background: rgba(219, 234, 254, 0.92);
  border-color: rgba(59, 130, 246, 0.2);
}

.priority-chip-p3,
.priority-chip-default {
  color: #475467;
  background: rgba(242, 244, 247, 0.96);
  border-color: rgba(152, 162, 179, 0.24);
}

.generated-case-meta {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.generated-case-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.generated-case-summary-item {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.generated-case-summary-label {
  font-size: 12px;
  color: var(--text-subtle);
}

.generated-case-summary-value {
  font-size: 12px;
  color: var(--text-main);
  line-height: 1.5;
  white-space: normal;
  word-break: break-word;
}

.generated-case-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.generated-case-row :deep(.el-form-item) {
  margin-bottom: 18px;
}

.generated-case-form {
  margin-top: 4px;
}

.candidate-edit-drawer {
  display: grid;
  gap: 14px;
}

.candidate-edit-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.candidate-edit-index {
  font-size: 12px;
  color: var(--text-subtle);
}

.candidate-edit-footer {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.candidate-edit-nav,
.candidate-edit-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.generated-case-warnings {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.generated-case-warning {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
  font-size: 12px;
}

@media (max-width: 1200px) {
  .generated-case-summary-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.detail-drawer-content {
  display: grid;
  gap: 16px;
}

.detail-drawer-block {
  display: grid;
  gap: 8px;
}

.detail-drawer-label {
  font-size: 12px;
  color: var(--text-subtle);
}

.detail-drawer-value {
  color: var(--text-main);
  line-height: 1.7;
  white-space: pre-wrap;
}

.detail-drawer-list {
  margin: 0;
  padding-left: 18px;
  display: grid;
  gap: 6px;
  color: var(--text-main);
}

.detail-drawer-pre {
  margin: 0;
  padding: 12px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.05);
  color: var(--text-main);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 360px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
}

.detail-drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 1280px) {
  .generate-content-grid {
    grid-template-columns: 1fr;
  }

  .generate-config-meta,
  .generated-case-row {
    grid-template-columns: 1fr;
  }

  .requirement-editor-grid {
    grid-template-columns: 1fr;
  }

  .generate-feedback-row,
  .review-control-meta,
  .ai-review-summary-banner {
    grid-template-columns: 1fr;
  }

  .ai-review-summary-banner {
    display: grid;
  }

  .generate-config-footer {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>

