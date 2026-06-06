<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Check, ChevronDown, ClipboardCheck, Info, RotateCcw, Save, Sparkles, Zap } from '@lucide/vue'
import { ElMessage } from 'element-plus'
import { platformApi } from '../api/platform'
import type {
  AiCapabilityOverride,
  AiCaseConfig,
  AiModelCapabilities,
  AiProviderConnection,
  SaveAiCaseConfigPayload,
} from '../types/api'

type RoleType = 'CASE_GENERATOR' | 'CASE_REVIEWER'
type CapabilityKey =
  | 'textChat'
  | 'streamOutput'
  | 'structuredOutput'
  | 'imageInput'
  | 'longContext'
  | 'stableAvailable'

type RoleForm = {
  id: number | null
  providerConnectionId: number | null
  model: string
  promptTemplate: string
  reviewChecklist: string
  temperature: number
  topP: number
  maxCases: number
  status: number
  capabilityOverride: AiCapabilityOverride
  detectedCapabilities: AiModelCapabilities
  effectiveCapabilities: AiModelCapabilities
  supportsImageInput: boolean
}

type RoleCardMeta = {
  roleType: RoleType
  title: string
  subtitle: string
  iconClass: string
  iconType: 'generator' | 'reviewer'
}

type ModelPoolOption = {
  key: string
  providerId: number
  providerName: string
  modelName: string
  displayName: string
}

const router = useRouter()

const DEFAULT_SMART_MAX_CASES = 50
const DEFAULT_GENERATOR_PROMPT = `你是一名资深测试工程师，负责根据需求文档、业务规则、接口说明、页面原型或图片素材设计高质量测试用例。
请优先产出真实有价值、可执行、可验证的测试用例，而不是为了数量凑重复场景。

生成时请重点关注：
1. 覆盖正常场景、异常场景、边界值、等价类、状态迁移、组合/判定表、错误推测、端到端场景、非功能性、数据准备与清理。
2. 每条用例都要有清晰标题、前置条件、测试步骤、预期结果、优先级和测试类型。
3. 每条用例都要说明测试角度、生成依据和生成原因。
4. 生成依据应来自需求原文、业务规则、约束条件、图片/原型信息，或明确标注为基于风险的合理推断。
5. 步骤必须清晰可执行，预期结果必须客观可验证，避免“系统正常”等模糊表述。
6. 需求不明确时不要编造确定结论，应标明需要确认的点或风险假设。`
const DEFAULT_GENERATOR_CHECKLIST = '优先覆盖主流程、异常分支、边界/等价类、状态流转、组合条件、错误推测、端到端链路、非功能风险和数据准备/清理；避免重复、低价值或不可执行用例。'
const DEFAULT_REVIEW_PROMPT = `你是一名严格的测试架构师，负责对 AI 生成的测试用例进行质量评审、自动优化和覆盖补充。
评审时请先基于需求全文和全部候选用例判断整体覆盖率，再逐条给出结论。

评审时请重点检查：
1. 需求覆盖度：功能点、业务规则、约束条件是否有正向和反向覆盖。
2. 测试类型覆盖：正常、异常、边界、等价类、状态迁移、组合、错误推测、端到端、非功能、数据清理是否存在明显缺口。
3. 可执行性：前置条件、步骤、预期结果是否明确、可操作、可验证。
4. 依据可信度：生成依据是否能对应需求文本、图片/原型信息、业务规则或合理风险推断。
5. 优先级合理性：P0/P1 是否对应核心流程和高风险场景。
6. 冗余与低价值：是否存在重复、过泛、不可执行或与需求无关的用例。

处理原则：
1. 好的用例直接通过。
2. 有价值但表达不清、步骤不完整或预期不可验证的用例，直接给出优化后的完整用例。
3. 存在关键覆盖缺口时，直接补充新的完整用例。
4. 需求不明确或依据不足时，标记为建议确认。
5. 重复、低价值、不可执行或偏离需求的用例，标记为不推荐。`
const DEFAULT_REVIEW_CHECKLIST = '重点检查覆盖缺口、边界/等价类、组合条件、异常鲁棒性、可执行性、依据可信度、优先级合理性、重复低价值用例；需要时直接优化或补充完整用例。'
const LEGACY_GENERATOR_PROMPT = `你是一名资深测试工程师，擅长接口自动化测试用例的设计。
请根据接口信息生成完整的测试用例，包括正向用例、边界值用例和异常用例。
要求：
1. 用例描述清晰，步骤明确
2. 断言覆盖响应状态码、响应数据结构和业务逻辑
3. 优先考虑高频业务场景`
const LEGACY_GENERATOR_CHECKLIST = '优先覆盖主流程、边界条件、异常分支和高风险回归点，避免重复或低价值用例。'
const LEGACY_REVIEW_PROMPT = `你是一名资深 QA 评审专家，负责对测试用例进行质量评审。
请从以下维度评审用例：
1. 覆盖度：是否覆盖核心业务场景和边界条件
2. 可执行性：步骤是否清晰、断言是否合理
3. 冗余度：是否存在重复或低价值用例
请给出评审结论和改进建议。`
const LEGACY_REVIEW_CHECKLIST = '优先检查主流程、边界、异常、重复场景，以及步骤与预期结果是否清晰可验证。'

const roleMeta: RoleCardMeta[] = [
  {
    roleType: 'CASE_GENERATOR',
    title: '用例生成',
    subtitle: '根据接口信息或需求描述自动生成测试用例',
    iconClass: 'role-icon-blue',
    iconType: 'generator',
  },
  {
    roleType: 'CASE_REVIEWER',
    title: '用例评审',
    subtitle: '对现有测试用例进行质量评审并给出改进建议',
    iconClass: 'role-icon-green',
    iconType: 'reviewer',
  },
]

const capabilityMeta: Array<{ key: CapabilityKey; label: string; hint: string }> = [
  { key: 'textChat', label: '文本对话', hint: '是否具备基础文本理解和生成能力。' },
  { key: 'streamOutput', label: '流式输出', hint: '是否支持任务执行过程中分段返回结果。' },
  { key: 'structuredOutput', label: '结构化输出', hint: '是否能稳定返回可解析 JSON。' },
  { key: 'imageInput', label: '图片输入', hint: '是否可读取原型图、截图等图片素材。' },
  { key: 'longContext', label: '长上下文', hint: '是否适合处理更长的需求文本或多图输入。' },
  { key: 'stableAvailable', label: '最近可用', hint: '最近一次探测或测试是否成功。' },
]

const loading = ref(false)
const savingRole = ref<RoleType | null>(null)
const testingRole = ref<RoleType | null>(null)
const openModelRole = ref<RoleType | null>(null)
const promptExpanded = reactive<Record<RoleType, boolean>>({
  CASE_GENERATOR: false,
  CASE_REVIEWER: false,
})

const providers = ref<AiProviderConnection[]>([])

const forms = reactive<Record<RoleType, RoleForm>>({
  CASE_GENERATOR: createDefaultForm('CASE_GENERATOR'),
  CASE_REVIEWER: createDefaultForm('CASE_REVIEWER'),
})

function createUnknownCapabilities(): AiModelCapabilities {
  const unknown = { supported: null, source: 'UNKNOWN' as const, detail: null }
  return {
    textChat: { ...unknown },
    streamOutput: { ...unknown },
    structuredOutput: { ...unknown },
    imageInput: { ...unknown },
    longContext: { ...unknown },
    stableAvailable: { ...unknown },
  }
}

function applyOverrideToCapabilities(capabilities: AiModelCapabilities, override: AiCapabilityOverride): AiModelCapabilities {
  const next = JSON.parse(JSON.stringify(capabilities)) as AiModelCapabilities
  capabilityMeta.forEach(({ key }) => {
    const overrideValue = override[key]
    if (overrideValue === null || overrideValue === undefined) {
      return
    }
    next[key] = {
      supported: overrideValue,
      source: 'MANUAL',
      detail: '人工修正',
    }
  })
  return next
}

function createDefaultForm(roleType: RoleType): RoleForm {
  const detectedCapabilities = createUnknownCapabilities()
  const capabilityOverride: AiCapabilityOverride = {}
  const effectiveCapabilities = applyOverrideToCapabilities(detectedCapabilities, capabilityOverride)
  const defaultTemperature = roleType === 'CASE_GENERATOR' ? 0.7 : 0.5
  const defaultTopP = roleType === 'CASE_GENERATOR' ? 0.9 : 0.7
  return {
    id: null,
    providerConnectionId: null,
    model: '',
    promptTemplate: roleType === 'CASE_GENERATOR' ? DEFAULT_GENERATOR_PROMPT : DEFAULT_REVIEW_PROMPT,
    reviewChecklist: roleType === 'CASE_GENERATOR' ? DEFAULT_GENERATOR_CHECKLIST : DEFAULT_REVIEW_CHECKLIST,
    temperature: defaultTemperature,
    topP: defaultTopP,
    maxCases: DEFAULT_SMART_MAX_CASES,
    status: 1,
    capabilityOverride,
    detectedCapabilities,
    effectiveCapabilities,
    supportsImageInput: effectiveCapabilities.imageInput.supported === true,
  }
}

function resetRoleForm(roleType: RoleType) {
  Object.assign(forms[roleType], createDefaultForm(roleType))
}

function temperatureLabel(roleType: RoleType) {
  const value = forms[roleType].temperature
  if (value <= 0.3) return '保守'
  if (value <= 0.7) return '平衡'
  return '创意'
}

function temperatureTone(roleType: RoleType) {
  const value = forms[roleType].temperature
  if (value <= 0.3) return 'safe'
  if (value <= 0.7) return 'balanced'
  return 'creative'
}

function topPLabel(roleType: RoleType) {
  const value = forms[roleType].topP
  if (value <= 0.4) return '聚焦'
  if (value <= 0.7) return '均衡'
  return '发散'
}

function topPTone(roleType: RoleType) {
  const value = forms[roleType].topP
  if (value <= 0.4) return 'safe'
  if (value <= 0.7) return 'balanced'
  return 'creative'
}

function defaultPromptForRole(roleType: RoleType) {
  return roleType === 'CASE_GENERATOR' ? DEFAULT_GENERATOR_PROMPT : DEFAULT_REVIEW_PROMPT
}

function defaultChecklistForRole(roleType: RoleType) {
  return roleType === 'CASE_GENERATOR' ? DEFAULT_GENERATOR_CHECKLIST : DEFAULT_REVIEW_CHECKLIST
}

function legacyPromptForRole(roleType: RoleType) {
  return roleType === 'CASE_GENERATOR' ? LEGACY_GENERATOR_PROMPT : LEGACY_REVIEW_PROMPT
}

function legacyChecklistForRole(roleType: RoleType) {
  return roleType === 'CASE_GENERATOR' ? LEGACY_GENERATOR_CHECKLIST : LEGACY_REVIEW_CHECKLIST
}

function normalizePromptTemplate(roleType: RoleType, value: string | null | undefined) {
  const normalized = value?.trim()
  if (!normalized || normalized === legacyPromptForRole(roleType)) {
    return defaultPromptForRole(roleType)
  }
  return value ?? defaultPromptForRole(roleType)
}

function normalizeReviewChecklist(roleType: RoleType, value: string | null | undefined) {
  const normalized = value?.trim()
  if (!normalized || normalized === legacyChecklistForRole(roleType)) {
    return defaultChecklistForRole(roleType)
  }
  return value ?? defaultChecklistForRole(roleType)
}

function restoreDefaultPrompt(roleType: RoleType) {
  forms[roleType].promptTemplate = defaultPromptForRole(roleType)
  forms[roleType].reviewChecklist = defaultChecklistForRole(roleType)
}

function promptPreview(roleType: RoleType) {
  const lines = forms[roleType].promptTemplate
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
  return lines.join('\n') || '暂未配置角色提示词'
}

function togglePromptEditor(roleType: RoleType) {
  promptExpanded[roleType] = !promptExpanded[roleType]
}

const hasNoProviders = computed(() => providers.value.length === 0)
const modelPoolOptions = computed<ModelPoolOption[]>(() => {
  return providers.value
    .filter(provider => !!provider.modelName?.trim())
    .map(provider => {
      const modelName = provider.modelName!.trim()
      return {
        key: `${provider.id}::${modelName}`,
        providerId: provider.id,
        providerName: provider.connectionName,
        modelName,
        displayName: modelName,
      }
    })
})
const totalModelCount = computed(() => modelPoolOptions.value.length)

function selectedModelKey(roleType: RoleType) {
  const form = forms[roleType]
  if (!form.providerConnectionId || !form.model) return ''
  return `${form.providerConnectionId}::${form.model}`
}

function selectedModelOption(roleType: RoleType) {
  const selectedKey = selectedModelKey(roleType)
  return modelPoolOptions.value.find(option => option.key === selectedKey) ?? null
}

function selectedProviderClass(roleType: RoleType) {
  const selected = selectedModelOption(roleType)
  return selected ? providerOptionClass(selected) : ''
}

function selectedProviderText(roleType: RoleType) {
  const selected = selectedModelOption(roleType)
  return selected ? providerOptionText(selected) : ''
}

function providerOptionClass(option: Pick<ModelPoolOption, 'providerName' | 'modelName'>) {
  const source = `${option.providerName} ${option.modelName}`.toLowerCase()
  if (source.includes('anthropic') || source.includes('claude')) return 'provider-anthropic'
  if (source.includes('deepseek')) return 'provider-deepseek'
  if (source.includes('google') || source.includes('gemini')) return 'provider-google'
  if (source.includes('qwen') || source.includes('通义') || source.includes('alibaba') || source.includes('dashscope') || source.includes('aliyun') || source.includes('阿里')) return 'provider-qwen'
  return 'provider-openai'
}

function providerOptionText(option: Pick<ModelPoolOption, 'providerName' | 'modelName'>) {
  const source = `${option.providerName} ${option.modelName}`.toLowerCase()
  if (source.includes('anthropic') || source.includes('claude')) return 'Anthropic'
  if (source.includes('deepseek')) return 'DeepSeek'
  if (source.includes('google') || source.includes('gemini')) return 'Google'
  if (source.includes('qwen') || source.includes('通义') || source.includes('alibaba') || source.includes('dashscope') || source.includes('aliyun') || source.includes('阿里')) return '阿里云'
  return option.providerName || 'OpenAI'
}

function toggleModelSelect(roleType: RoleType) {
  openModelRole.value = openModelRole.value === roleType ? null : roleType
}

function selectModelOption(roleType: RoleType, option: ModelPoolOption) {
  openModelRole.value = null
  handleRoleModelPoolChanged(roleType, option.key)
}

function handleRoleModelPoolChanged(roleType: RoleType, value: string) {
  const [providerIdText, modelName] = value.split('::')
  const providerConnectionId = Number(providerIdText)
  forms[roleType].providerConnectionId = Number.isFinite(providerConnectionId) ? providerConnectionId : null
  forms[roleType].model = modelName ?? ''
  handleRoleConnectionChanged(roleType)
}

function recomputeEffectiveCapabilities(roleType: RoleType) {
  forms[roleType].effectiveCapabilities = applyOverrideToCapabilities(
    forms[roleType].detectedCapabilities,
    forms[roleType].capabilityOverride,
  )
  forms[roleType].supportsImageInput = forms[roleType].effectiveCapabilities.imageInput.supported === true
}

function applyLoadedRole(roleType: RoleType, config: AiCaseConfig | null) {
  resetRoleForm(roleType)
  if (!config) {
    return
  }
  forms[roleType].id = config.id
  forms[roleType].providerConnectionId = config.providerConnectionId
  forms[roleType].model = config.model
  forms[roleType].promptTemplate = normalizePromptTemplate(roleType, config.promptTemplate)
  forms[roleType].reviewChecklist = normalizeReviewChecklist(roleType, config.reviewChecklist)
  forms[roleType].temperature = config.temperature
  forms[roleType].topP = config.topP ?? (roleType === 'CASE_GENERATOR' ? 0.9 : 0.7)
  forms[roleType].maxCases = config.maxCases ?? DEFAULT_SMART_MAX_CASES
  forms[roleType].status = config.status
  forms[roleType].capabilityOverride = config.capabilityOverride ?? {}
  forms[roleType].detectedCapabilities = config.detectedCapabilities ?? createUnknownCapabilities()
  forms[roleType].effectiveCapabilities = config.effectiveCapabilities ?? applyOverrideToCapabilities(
    forms[roleType].detectedCapabilities,
    forms[roleType].capabilityOverride,
  )
  forms[roleType].supportsImageInput = config.supportsImageInput
}

async function loadProviders() {
  try {
    providers.value = await platformApi.getAiProviderConnections('ALL')
  } catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function loadConfig() {
  loading.value = true
  resetRoleForm('CASE_GENERATOR')
  resetRoleForm('CASE_REVIEWER')
  try {
    const response = await platformApi.getAiCaseConfig('ALL')
    applyLoadedRole('CASE_GENERATOR', response.generatorConfig)
    applyLoadedRole('CASE_REVIEWER', response.reviewerConfig)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

function goToAiConnections() {
  router.push({
    path: '/settings',
    query: { tab: 'aiConnection' },
  })
}

function handleRoleConnectionChanged(roleType: RoleType) {
  forms[roleType].detectedCapabilities = createUnknownCapabilities()
  recomputeEffectiveCapabilities(roleType)
}

async function testRoleConnection(roleType: RoleType) {
  if (!canSaveRole(roleType)) {
    ElMessage.error('请先补全当前角色绑定配置')
    return
  }
  testingRole.value = roleType
  try {
    const response = await platformApi.testAiCaseConfig('ALL', buildRolePayload(roleType))
    ElMessage.success(response.message || '测试连接成功')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    testingRole.value = null
  }
}

function canSaveRole(roleType: RoleType) {
  const form = forms[roleType]
  return !!form.providerConnectionId
    && !!form.model.trim()
    && !!form.promptTemplate.trim()
    && form.temperature >= 0
    && form.temperature <= 1
    && form.topP >= 0.1
    && form.topP <= 1
}

function buildRolePayload(roleType: RoleType): SaveAiCaseConfigPayload {
  const form = forms[roleType]
  return {
    roleType,
    providerConnectionId: form.providerConnectionId,
    model: form.model.trim(),
    promptTemplate: form.promptTemplate.trim() || (roleType === 'CASE_GENERATOR' ? DEFAULT_GENERATOR_PROMPT : DEFAULT_REVIEW_PROMPT),
    reviewChecklist: form.reviewChecklist.trim() || (roleType === 'CASE_GENERATOR' ? DEFAULT_GENERATOR_CHECKLIST : DEFAULT_REVIEW_CHECKLIST),
    temperature: Number(form.temperature),
    topP: Number(form.topP),
  }
}

async function saveRole(roleType: RoleType) {
  if (!canSaveRole(roleType)) {
    ElMessage.error('请先补全当前角色绑定配置')
    return
  }
  savingRole.value = roleType
  try {
    const payload = buildRolePayload(roleType)
    let savedConfig: AiCaseConfig
    if (forms[roleType].id) {
      savedConfig = await platformApi.updateAiCaseConfig('ALL', forms[roleType].id!, payload)
    } else {
      savedConfig = await platformApi.createAiCaseConfig('ALL', payload)
    }
    applyLoadedRole(roleType, savedConfig)
    ElMessage.success(`${roleType === 'CASE_GENERATOR' ? '用例生成模型' : '用例评审模型'}已保存`)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    savingRole.value = null
  }
}

function handleDocumentPointerDown(event: MouseEvent) {
  const target = event.target
  if (target instanceof Element && target.closest('.model-select-native')) {
    return
  }
  openModelRole.value = null
}

onMounted(async () => {
  document.addEventListener('mousedown', handleDocumentPointerDown)
  await Promise.all([loadProviders(), loadConfig()])
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', handleDocumentPointerDown)
})
</script>

<template>
  <section v-loading="loading" class="ai-config-modern-page">
    <div class="ai-config-tip">
      <Info class="ai-config-tip-icon" />
      <span>
        模型来自连接池，共 <strong>{{ totalModelCount }}</strong> 个可选。如需添加，请前往
        <button type="button" class="tip-link" @click="goToAiConnections">系统设置 → AI 连接</button>
      </span>
    </div>

    <div v-if="hasNoProviders" class="empty-inline">
      你还没有可用的 AI 连接，请先到系统设置中创建。
      <el-button link type="primary" @click="goToAiConnections">去创建连接</el-button>
    </div>

    <div class="ai-config-card-grid">
      <article
        v-for="meta in roleMeta"
        :key="meta.roleType"
        class="ai-role-card"
        :class="{ 'is-prompt-expanded': promptExpanded[meta.roleType] }"
      >
        <header class="ai-role-card-header">
          <div class="role-heading">
            <div class="role-icon" :class="meta.iconClass">
              <Zap v-if="meta.iconType === 'generator'" class="role-icon-svg" />
              <ClipboardCheck v-else class="role-icon-svg" />
            </div>
            <div>
              <h3>{{ meta.title }}</h3>
              <p>{{ meta.subtitle }}</p>
            </div>
          </div>
        </header>

        <div class="ai-role-card-body">
          <div class="form-block">
            <label class="field-label">选择模型</label>
            <div class="field-help">从 AI 连接池中选择已配置的模型</div>
            <div class="model-row">
              <div class="model-select-native">
                <button
                  type="button"
                  class="model-select-trigger"
                  :class="{ 'is-open': openModelRole === meta.roleType }"
                  @click="toggleModelSelect(meta.roleType)"
                >
                  <template v-if="selectedModelOption(meta.roleType)">
                    <span class="model-select-name">{{ selectedModelOption(meta.roleType)?.displayName }}</span>
                    <span
                      class="provider-chip"
                      :class="selectedProviderClass(meta.roleType)"
                    >
                      {{ selectedProviderText(meta.roleType) }}
                    </span>
                  </template>
                  <span v-else class="model-select-placeholder">请选择模型</span>
                  <ChevronDown class="model-select-chevron" />
                </button>

                <div v-if="openModelRole === meta.roleType" class="model-select-dropdown">
                  <button
                    v-for="option in modelPoolOptions"
                    :key="option.key"
                    type="button"
                    class="model-select-option"
                    :class="{ 'is-selected': selectedModelKey(meta.roleType) === option.key }"
                    @click="selectModelOption(meta.roleType, option)"
                  >
                    <div class="model-option-copy">
                      <div class="model-option-title-row">
                        <span class="model-option-title">{{ option.displayName }}</span>
                        <span
                          v-if="option.displayName.toLowerCase().includes('gpt-4o') || option.displayName.toLowerCase().includes('sonnet')"
                          class="model-badge"
                        >
                          推荐
                        </span>
                      </div>
                      <span class="provider-chip option-provider-chip" :class="providerOptionClass(option)">
                        {{ providerOptionText(option) }}
                      </span>
                    </div>
                    <Check v-if="selectedModelKey(meta.roleType) === option.key" class="model-option-check" />
                  </button>
                  <div v-if="modelPoolOptions.length === 0" class="model-select-empty">暂无可选模型</div>
                </div>
              </div>
              <button
                type="button"
                class="test-button"
                :disabled="!canSaveRole(meta.roleType)"
                @click="testRoleConnection(meta.roleType)"
              >
                <Sparkles :class="{ 'is-loading': testingRole === meta.roleType }" />
                {{ testingRole === meta.roleType ? '连接中...' : '测试连接' }}
              </button>
            </div>
          </div>

          <div class="slider-block">
            <div class="slider-header">
              <div class="slider-label">
                <span>创意度 (Temperature)</span>
                <span class="field-tooltip">
                  <Info class="inline-info-icon" />
                  <span class="field-tooltip-popover">
                    控制回答的随机性与创意程度。
                    <br>• 偏低（精准）：回答更稳定、一致
                    <br>• 偏高（创意）：回答更多样、发散
                    <br>建议生成任务用 0.7，评审任务用 0.5
                  </span>
                </span>
              </div>
              <span class="slider-value" :class="`tone-${temperatureTone(meta.roleType)}`">
                {{ temperatureLabel(meta.roleType) }} ({{ forms[meta.roleType].temperature.toFixed(1) }})
              </span>
            </div>
            <input
              v-model.number="forms[meta.roleType].temperature"
              type="range"
              min="0"
              max="1"
              step="0.1"
              class="native-range"
            >
            <div class="slider-scale">
              <span>精准</span>
              <span>创意</span>
            </div>
          </div>

          <div class="slider-block">
            <div class="slider-header">
              <div class="slider-label">
                <span>采样范围 (Top-p)</span>
                <span class="field-tooltip">
                  <Info class="inline-info-icon" />
                  <span class="field-tooltip-popover">
                    控制 AI 选词的候选范围（核采样）。
                    <br>• 偏低（聚焦）：用词更精准、克制
                    <br>• 偏高（发散）：表达更丰富、多样
                    <br>建议生成任务用 0.9，评审任务用 0.7
                  </span>
                </span>
              </div>
              <span class="slider-value" :class="`tone-${topPTone(meta.roleType)}`">
                {{ topPLabel(meta.roleType) }} ({{ forms[meta.roleType].topP.toFixed(1) }})
              </span>
            </div>
            <input
              v-model.number="forms[meta.roleType].topP"
              type="range"
              min="0.1"
              max="1"
              step="0.1"
              class="native-range"
            >
            <div class="slider-scale">
              <span>聚焦</span>
              <span>发散</span>
            </div>
          </div>

          <div class="prompt-block">
            <div class="prompt-header">
              <label class="field-label">角色提示词</label>
              <div class="prompt-actions">
                <button type="button" class="restore-button" @click="restoreDefaultPrompt(meta.roleType)">
                  <RotateCcw />
                  恢复默认
                </button>
                <button
                  type="button"
                  class="prompt-toggle-button"
                  :class="{ 'is-expanded': promptExpanded[meta.roleType] }"
                  @click="togglePromptEditor(meta.roleType)"
                >
                  {{ promptExpanded[meta.roleType] ? '收起编辑' : '展开编辑' }}
                  <ChevronDown />
                </button>
              </div>
            </div>
            <button
              v-if="!promptExpanded[meta.roleType]"
              type="button"
              class="prompt-preview-card"
              @click="togglePromptEditor(meta.roleType)"
            >
              {{ promptPreview(meta.roleType) }}
            </button>
            <el-input
              v-if="promptExpanded[meta.roleType]"
              v-model="forms[meta.roleType].promptTemplate"
              type="textarea"
              :rows="7"
              resize="none"
              class="prompt-textarea"
            />
            <p class="prompt-hint">
              <Info />
              提示词会附加在每次 AI 请求前，影响生成结果的风格和质量
            </p>
          </div>

          <button
            type="button"
            class="save-config-button"
            :disabled="!canSaveRole(meta.roleType)"
            @click="saveRole(meta.roleType)"
          >
            <Save v-if="savingRole !== meta.roleType" />
            <Sparkles v-else class="is-loading" />
            {{ savingRole === meta.roleType ? '保存中...' : '保存配置' }}
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.ai-config-modern-page {
  display: flex;
  flex-direction: column;
  gap: var(--ath-space-6);
  min-height: auto !important;
  padding: 0;
  color: var(--ath-text-strong);
}

.ai-config-tip {
  display: flex;
  align-items: center;
  gap: var(--ath-space-3);
  min-height: 52px;
  border: 1px solid #bfdbfe;
  border-radius: var(--ath-radius-lg);
  background: var(--ath-blue-soft);
  padding: 14px var(--ath-space-4);
  color: var(--ath-primary-hover);
  font-size: 14px;
  line-height: 20px;
}

.ai-config-tip-icon {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  color: var(--ath-blue);
  stroke-width: 2;
}

.tip-link {
  border: 0;
  background: transparent;
  color: var(--ath-primary);
  cursor: pointer;
  font: inherit;
  font-weight: 500;
  padding: 0;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.model-select-trigger,
.model-select-option,
.test-button,
.restore-button,
.save-config-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 0;
  font: inherit;
  cursor: pointer;
}

.empty-inline {
  border-radius: 12px;
  color: var(--ath-text-muted);
  font-size: 13px;
  line-height: 1.7;
}

.ai-config-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--ath-space-6);
  align-items: stretch;
}

.ai-role-card {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 205px);
  min-height: 520px;
  overflow: visible;
  border: 1px solid var(--ath-border);
  border-radius: var(--ath-radius-xl);
  background: #ffffff;
  box-shadow: var(--ath-shadow-xs);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.ai-role-card.is-prompt-expanded {
  height: auto;
  min-height: 0;
}

.ai-role-card:hover {
  border-color: var(--ath-border-strong);
  box-shadow: var(--ath-shadow-card-hover);
}

.ai-role-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--ath-space-4);
  min-height: 80px;
  border-bottom: 1px solid var(--ath-border-soft);
  padding: var(--ath-space-5) var(--ath-space-6) var(--ath-space-4);
}

.role-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.role-icon {
  display: grid;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 12px;
}

.role-icon-blue {
  background: var(--ath-blue-soft);
  color: var(--ath-blue);
}

.role-icon-green {
  background: #ecfdf5;
  color: #22c55e;
}

.role-icon-svg {
  width: 20px;
  height: 20px;
  stroke-width: 2;
}

.role-heading h3 {
  margin: 0;
  color: var(--ath-text-strong);
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
}

.role-heading p {
  margin: 2px 0 0;
  color: var(--ath-text-subtle);
  font-size: 12px;
  line-height: 16px;
}

.ai-role-card-body {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  flex-direction: column;
  gap: var(--ath-space-4);
  padding: var(--ath-space-4) var(--ath-space-6);
}

.form-block,
.prompt-block,
.slider-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.prompt-block {
  flex: 1 1 auto;
  min-height: 0;
}

.field-label,
.slider-label {
  color: var(--ath-text-main);
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
}

.field-help {
  margin-top: -4px;
  color: var(--ath-text-subtle);
  font-size: 12px;
  line-height: 16px;
}

.model-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.model-select-native {
  position: relative;
  min-width: 0;
}

.model-select-trigger {
  width: 100%;
  min-height: 42px;
  gap: 8px;
  border: 1px solid var(--ath-border);
  border-radius: var(--ath-radius-lg);
  background: #ffffff;
  padding: 10px 12px;
  color: var(--ath-text-main);
  font-size: 14px;
  line-height: 20px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.model-select-trigger:hover,
.model-select-trigger.is-open {
  border-color: #93c5fd;
  box-shadow: var(--ath-focus-ring);
}

.model-select-name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  color: #1f2937;
  font-weight: 500;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-select-placeholder {
  flex: 1 1 auto;
  color: var(--ath-text-subtle);
  text-align: left;
}

.model-select-chevron {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  color: var(--ath-text-subtle);
  stroke-width: 2;
  transition: transform 0.15s ease;
}

.model-select-trigger.is-open .model-select-chevron {
  transform: rotate(180deg);
}

.model-select-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 20;
  max-height: 240px;
  overflow-y: auto;
  border: 1px solid var(--ath-border);
  border-radius: var(--ath-radius-lg);
  background: #ffffff;
  box-shadow: var(--ath-shadow-dialog);
  padding: 6px 0;
  overscroll-behavior: contain;
  scrollbar-width: thin;
}

.model-select-dropdown::-webkit-scrollbar {
  width: 6px;
}

.model-select-dropdown::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: var(--ath-border-strong);
}

.model-select-option {
  width: 100%;
  min-height: 56px;
  justify-content: space-between;
  gap: 8px;
  border-radius: 0;
  background: #ffffff;
  color: #1f2937;
  padding: 10px 12px;
  text-align: left;
  transition: background-color 0.15s ease;
}

.model-select-option:hover {
  background: var(--ath-bg-page);
}

.model-select-option.is-selected {
  background: var(--ath-blue-soft);
}

.model-option-copy {
  min-width: 0;
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
}

.model-option-title-row {
  display: flex;
  align-items: center;
  max-width: 100%;
  gap: 8px;
}

.model-option-title {
  overflow: hidden;
  color: #1f2937;
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-badge {
  flex: 0 0 auto;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  background: var(--ath-blue-soft);
  color: var(--ath-primary);
  padding: 1px 6px;
  font-size: 12px;
  line-height: 16px;
}

.model-option-check {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  color: var(--ath-blue);
  stroke-width: 2;
}

.model-select-empty {
  padding: 12px;
  color: var(--ath-text-subtle);
  font-size: 13px;
  text-align: center;
}

.provider-chip {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 12px;
  line-height: 16px;
  white-space: nowrap;
}

.option-provider-chip {
  padding: 1px 6px;
}

.provider-openai {
  background: #dcfce7;
  color: #15803d;
}

.provider-anthropic {
  background: #ffedd5;
  color: #c2410c;
}

.provider-google {
  background: #dbeafe;
  color: var(--ath-primary);
}

.provider-deepseek {
  background: #f3e8ff;
  color: #7e22ce;
}

.provider-qwen {
  background: #fee2e2;
  color: #b91c1c;
}

.test-button {
  height: 42px;
  border: 1px solid var(--ath-border);
  border-radius: var(--ath-radius-lg);
  background: #ffffff;
  color: var(--ath-text-main);
  padding: 0 14px;
  font-size: 14px;
  line-height: 20px;
  white-space: nowrap;
  transition: background 0.15s ease, border-color 0.15s ease;
}

.test-button svg {
  width: 14px;
  height: 14px;
  stroke-width: 2;
}

.test-button:hover:not(:disabled) {
  border-color: #bfdbfe;
  background: var(--ath-bg-page);
  box-shadow: var(--ath-focus-ring);
}

.test-button:disabled,
.save-config-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.slider-header,
.prompt-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.prompt-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
}

.slider-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.field-tooltip {
  position: relative;
  display: inline-flex;
  align-items: center;
  line-height: 0;
}

.inline-info-icon,
.prompt-hint svg {
  width: 14px;
  height: 14px;
  color: var(--ath-text-subtle);
  stroke-width: 2;
}

.field-tooltip:hover .inline-info-icon {
  color: #60a5fa;
}

.field-tooltip-popover {
  pointer-events: none;
  position: absolute;
  left: 20px;
  top: 50%;
  z-index: 30;
  width: 208px;
  transform: translateY(-50%) translateX(-2px);
  border-radius: 12px;
  background: var(--ath-text-strong);
  color: #ffffff;
  box-shadow: 0 20px 25px -5px rgba(15, 23, 42, 0.22), 0 8px 10px -6px rgba(15, 23, 42, 0.16);
  font-size: 12px;
  font-weight: 400;
  line-height: 20px;
  opacity: 0;
  padding: 10px 12px;
  text-align: left;
  white-space: normal;
  transition: opacity 0.12s ease, transform 0.12s ease;
}

.field-tooltip-popover::before {
  content: "";
  position: absolute;
  top: 50%;
  right: 100%;
  width: 0;
  height: 0;
  transform: translateY(-50%);
  border-top: 4px solid transparent;
  border-right: 4px solid var(--ath-text-strong);
  border-bottom: 4px solid transparent;
}

.field-tooltip:hover .field-tooltip-popover {
  opacity: 1;
  transform: translateY(-50%) translateX(0);
}

.slider-value {
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.tone-safe {
  color: var(--ath-primary);
}

.tone-balanced {
  color: var(--ath-green);
}

.tone-creative {
  color: #f97316;
}

.native-range {
  width: 100%;
  height: 6px;
  margin: 5px 0 2px;
  appearance: none;
  background: transparent;
  cursor: pointer;
}

.native-range:focus {
  outline: none;
}

.native-range::-webkit-slider-runnable-track {
  height: 6px;
  border-radius: 999px;
  background: var(--ath-border);
}

.native-range::-webkit-slider-thumb {
  width: 14px;
  height: 14px;
  margin-top: -4px;
  border: 0;
  border-radius: 999px;
  appearance: none;
  background: var(--ath-blue);
  box-shadow: 0 0 0 2px #ffffff, 0 1px 4px rgba(37, 99, 235, 0.35);
}

.native-range::-moz-range-track {
  height: 6px;
  border-radius: 999px;
  background: var(--ath-border);
}

.native-range::-moz-range-progress {
  height: 6px;
  border-radius: 999px;
  background: var(--ath-border);
}

.native-range::-moz-range-thumb {
  width: 14px;
  height: 14px;
  border: 0;
  border-radius: 999px;
  background: var(--ath-blue);
  box-shadow: 0 0 0 2px #ffffff, 0 1px 4px rgba(37, 99, 235, 0.35);
}

.slider-scale {
  display: flex;
  justify-content: space-between;
  color: var(--ath-text-subtle);
  font-size: 12px;
  line-height: 16px;
}

.restore-button {
  gap: 4px;
  background: transparent;
  color: var(--ath-text-subtle);
  font-size: 12px;
  padding: 0;
  transition: color 0.15s ease;
}

.restore-button:hover {
  color: var(--ath-primary);
}

.restore-button svg,
.prompt-toggle-button svg,
.save-config-button svg {
  width: 16px;
  height: 16px;
  stroke-width: 2;
}

.restore-button svg {
  width: 12px;
  height: 12px;
}

.prompt-toggle-button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: transparent;
  color: var(--ath-primary);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 500;
  padding: 0;
}

.prompt-toggle-button svg {
  width: 13px;
  height: 13px;
  transition: transform 0.15s ease;
}

.prompt-toggle-button.is-expanded svg {
  transform: rotate(180deg);
}

.prompt-preview-card {
  display: flex;
  width: 100%;
  min-height: 0;
  flex: 1 1 auto;
  align-items: flex-start;
  border: 1px solid var(--ath-border);
  border-radius: var(--ath-radius-lg);
  background: var(--ath-bg-subtle);
  color: var(--ath-text-muted);
  cursor: pointer;
  padding: 12px;
  text-align: left;
  white-space: pre-line;
  overflow: hidden;
  font-size: 13px;
  line-height: 21px;
  transition: border-color 0.15s ease, background-color 0.15s ease, box-shadow 0.15s ease;
}

.prompt-preview-card:hover {
  border-color: #bfdbfe;
  background: #ffffff;
  box-shadow: var(--ath-shadow-xs);
}

.prompt-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 2px 0 0;
  color: var(--ath-text-subtle);
  font-size: 12px;
  line-height: 16px;
}

.hidden-business-fields {
  display: none;
}

.save-config-button {
  width: 100%;
  height: 42px;
  flex: 0 0 42px;
  margin-top: auto;
  border-radius: var(--ath-radius-lg);
  background: var(--ath-primary);
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  transition: background 0.15s ease, transform 0.15s ease;
}

.save-config-button:hover:not(:disabled) {
  background: var(--ath-primary-hover);
}

.save-config-button:active:not(:disabled) {
  transform: translateY(1px);
}

.is-loading {
  animation: spin 0.9s linear infinite;
}

:deep(.prompt-textarea .el-textarea__inner) {
  height: 320px !important;
  min-height: 320px !important;
  max-height: 320px !important;
  overflow-y: auto;
  border-radius: 12px;
  box-shadow: 0 0 0 1px var(--ath-border) inset;
  color: var(--ath-text-main);
  font-size: 14px;
  line-height: 22px;
  padding: 12px;
  scrollbar-color: var(--ath-text-disabled) transparent;
  scrollbar-width: thin;
  transition: box-shadow 0.15s ease;
}

:deep(.prompt-textarea .el-textarea__inner::-webkit-scrollbar) {
  width: 6px;
}

:deep(.prompt-textarea .el-textarea__inner::-webkit-scrollbar-track) {
  background: transparent;
}

:deep(.prompt-textarea .el-textarea__inner::-webkit-scrollbar-thumb) {
  border-radius: 999px;
  background: var(--ath-text-disabled);
}

:deep(.prompt-textarea .el-textarea__inner::-webkit-scrollbar-thumb:hover) {
  background: #94a3b8;
}

:deep(.prompt-textarea .el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px var(--ath-blue) inset, 0 0 0 2px rgba(59, 130, 246, 0.12);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1536px) {
  .ai-config-card-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .ai-config-tip,
  .empty-inline {
    align-items: flex-start;
    flex-direction: column;
  }

  .model-row {
    grid-template-columns: 1fr;
  }
}
</style>
