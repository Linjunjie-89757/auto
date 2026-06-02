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

const DEFAULT_GENERATOR_PROMPT = `你是一名资深测试工程师，擅长接口自动化测试用例的设计。
请根据接口信息生成完整的测试用例，包括正向用例、边界值用例和异常用例。
要求：
1. 用例描述清晰，步骤明确
2. 断言覆盖响应状态码、响应数据结构和业务逻辑
3. 优先考虑高频业务场景`
const DEFAULT_GENERATOR_CHECKLIST = '优先覆盖主流程、边界条件、异常分支和高风险回归点，避免重复或低价值用例。'
const DEFAULT_REVIEW_PROMPT = `你是一名资深 QA 评审专家，负责对测试用例进行质量评审。
请从以下维度评审用例：
1. 覆盖度：是否覆盖核心业务场景和边界条件
2. 可执行性：步骤是否清晰、断言是否合理
3. 冗余度：是否存在重复或低价值用例
请给出评审结论和改进建议。`
const DEFAULT_REVIEW_CHECKLIST = '优先检查主流程、边界、异常、重复场景，以及步骤与预期结果是否清晰可验证。'

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

const providers = ref<AiProviderConnection[]>([])
const topPValues = ref<Record<RoleType, number>>({
  CASE_GENERATOR: 0.9,
  CASE_REVIEWER: 0.7,
})

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
  const capabilityOverride: AiCapabilityOverride = roleType === 'CASE_GENERATOR' ? { imageInput: true } : {}
  const effectiveCapabilities = applyOverrideToCapabilities(detectedCapabilities, capabilityOverride)
  return {
    id: null,
    providerConnectionId: null,
    model: '',
    promptTemplate: roleType === 'CASE_GENERATOR' ? DEFAULT_GENERATOR_PROMPT : DEFAULT_REVIEW_PROMPT,
    reviewChecklist: roleType === 'CASE_GENERATOR' ? DEFAULT_GENERATOR_CHECKLIST : DEFAULT_REVIEW_CHECKLIST,
    temperature: 0.3,
    maxCases: 20,
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

function statusText(status: number) {
  return status === 1 ? '启用中' : '已停用'
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
  const value = topPValues.value[roleType]
  if (value <= 0.4) return '聚焦'
  if (value <= 0.7) return '均衡'
  return '发散'
}

function topPTone(roleType: RoleType) {
  const value = topPValues.value[roleType]
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

function restoreDefaultPrompt(roleType: RoleType) {
  forms[roleType].promptTemplate = defaultPromptForRole(roleType)
  forms[roleType].reviewChecklist = defaultChecklistForRole(roleType)
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

function getProviderById(id: number | null) {
  if (!id) return null
  return providers.value.find(item => item.id === id) ?? null
}

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
  if (source.includes('qwen') || source.includes('通义') || source.includes('alibaba')) return 'provider-qwen'
  return 'provider-openai'
}

function providerOptionText(option: Pick<ModelPoolOption, 'providerName' | 'modelName'>) {
  const source = `${option.providerName} ${option.modelName}`.toLowerCase()
  if (source.includes('anthropic') || source.includes('claude')) return 'Anthropic'
  if (source.includes('deepseek')) return 'DeepSeek'
  if (source.includes('google') || source.includes('gemini')) return 'Google'
  if (source.includes('qwen') || source.includes('通义') || source.includes('alibaba')) return 'Alibaba'
  return option.providerName || 'OpenAI'
}

function toggleModelSelect(roleType: RoleType) {
  openModelRole.value = openModelRole.value === roleType ? null : roleType
}

function toggleRoleStatus(roleType: RoleType) {
  forms[roleType].status = forms[roleType].status === 1 ? 0 : 1
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
  forms[roleType].promptTemplate = config.promptTemplate
  forms[roleType].reviewChecklist = config.reviewChecklist ?? ''
  forms[roleType].temperature = config.temperature
  forms[roleType].maxCases = config.maxCases
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
    && form.maxCases >= 1
    && form.maxCases <= 100
}

function buildRolePayload(roleType: RoleType): SaveAiCaseConfigPayload {
  const form = forms[roleType]
  return {
    roleType,
    providerConnectionId: form.providerConnectionId,
    protocolType: getProviderById(form.providerConnectionId)?.protocolType ?? 'OPENAI_COMPATIBLE_CHAT',
    model: form.model.trim(),
    promptTemplate: form.promptTemplate.trim() || (roleType === 'CASE_GENERATOR' ? DEFAULT_GENERATOR_PROMPT : DEFAULT_REVIEW_PROMPT),
    reviewChecklist: form.reviewChecklist.trim() || (roleType === 'CASE_GENERATOR' ? DEFAULT_GENERATOR_CHECKLIST : DEFAULT_REVIEW_CHECKLIST),
    temperature: Number(form.temperature),
    maxCases: Number(form.maxCases),
    capabilityOverride: { ...form.capabilityOverride },
    supportsImageInput: form.supportsImageInput,
    status: form.status,
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
    if (forms[roleType].id) {
      await platformApi.updateAiCaseConfig('ALL', forms[roleType].id!, payload)
    } else {
      await platformApi.createAiCaseConfig('ALL', payload)
    }
    ElMessage.success(`${roleType === 'CASE_GENERATOR' ? '用例生成模型' : '用例评审模型'}已保存`)
    await loadConfig()
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
        :class="{ 'is-disabled': forms[meta.roleType].status !== 1 }"
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
          <div class="role-status">
            <span>{{ statusText(forms[meta.roleType].status).replace('启用中', '已启用') }}</span>
            <button
              type="button"
              class="native-toggle"
              :class="{ 'is-on': forms[meta.roleType].status === 1 }"
              :aria-pressed="forms[meta.roleType].status === 1"
              @click="toggleRoleStatus(meta.roleType)"
            />
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
                    <br>建议生成任务用 0.5，评审任务用 0.3
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
                {{ topPLabel(meta.roleType) }} ({{ topPValues[meta.roleType].toFixed(1) }})
              </span>
            </div>
            <input
              v-model.number="topPValues[meta.roleType]"
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
              <button type="button" class="restore-button" @click="restoreDefaultPrompt(meta.roleType)">
                <RotateCcw />
                恢复默认
              </button>
            </div>
            <el-input
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

          <div class="hidden-business-fields" aria-hidden="true">
            <el-input v-model="forms[meta.roleType].reviewChecklist" type="hidden" />
            <el-input-number v-model="forms[meta.roleType].maxCases" :min="1" :max="100" />
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
  gap: 24px;
  min-height: 100%;
  padding: 0;
  color: #111827;
}

.ai-config-tip {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 52px;
  border: 1px solid #bfdbfe;
  border-radius: 12px;
  background: #eff6ff;
  padding: 14px;
  color: #1d4ed8;
  font-size: 14px;
  line-height: 20px;
}

.ai-config-tip-icon {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  color: #3b82f6;
  stroke-width: 2;
}

.tip-link {
  border: 0;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  font: inherit;
  font-weight: 500;
  padding: 0;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.native-toggle,
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
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
}

.ai-config-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.ai-role-card {
  overflow: hidden;
  border: 1px solid #bfdbfe;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 4px 8px -2px rgba(59, 130, 246, 0.06), 0 2px 4px -2px rgba(59, 130, 246, 0.04);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.ai-role-card:hover {
  box-shadow: 0 10px 20px -8px rgba(59, 130, 246, 0.18);
}

.ai-role-card.is-disabled .ai-role-card-body {
  opacity: 0.4;
  pointer-events: none;
}

.ai-role-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  min-height: 80px;
  border-bottom: 1px solid #f3f4f6;
  padding: 20px 24px 16px;
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
  background: #eff6ff;
  color: #3b82f6;
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
  color: #111827;
  font-size: 14px;
  font-weight: 600;
  line-height: 20px;
}

.role-heading p {
  margin: 2px 0 0;
  color: #9ca3af;
  font-size: 12px;
  line-height: 16px;
}

.role-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding-top: 2px;
  color: #6b7280;
  font-size: 12px;
  white-space: nowrap;
}

.native-toggle {
  position: relative;
  width: 40px;
  height: 20px;
  flex: 0 0 40px;
  border-radius: 999px;
  background: #d1d5db;
  transition: background-color 0.2s ease;
}

.native-toggle::after {
  content: "";
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 999px;
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.18);
  transition: transform 0.2s ease;
}

.native-toggle.is-on {
  background: #3b82f6;
}

.native-toggle.is-on::after {
  transform: translateX(20px);
}

.ai-role-card-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 20px 24px;
}

.form-block,
.prompt-block,
.slider-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-label,
.slider-label {
  color: #374151;
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
}

.field-help {
  margin-top: -4px;
  color: #9ca3af;
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
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #ffffff;
  padding: 10px 12px;
  color: #374151;
  font-size: 14px;
  line-height: 20px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.model-select-trigger:hover,
.model-select-trigger.is-open {
  border-color: #93c5fd;
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
  color: #9ca3af;
  text-align: left;
}

.model-select-chevron {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  color: #9ca3af;
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
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 20px 25px -5px rgba(15, 23, 42, 0.12), 0 8px 10px -6px rgba(15, 23, 42, 0.10);
  padding: 6px 0;
  overscroll-behavior: contain;
  scrollbar-width: thin;
}

.model-select-dropdown::-webkit-scrollbar {
  width: 6px;
}

.model-select-dropdown::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: #d1d5db;
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
  background: #f9fafb;
}

.model-select-option.is-selected {
  background: #eff6ff;
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
  background: #eff6ff;
  color: #2563eb;
  padding: 1px 6px;
  font-size: 12px;
  line-height: 16px;
}

.model-option-check {
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  color: #3b82f6;
  stroke-width: 2;
}

.model-select-empty {
  padding: 12px;
  color: #9ca3af;
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
  color: #2563eb;
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
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #ffffff;
  color: #4b5563;
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
  background: #f9fafb;
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
  color: #9ca3af;
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
  background: #111827;
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
  border-right: 4px solid #111827;
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
  color: #2563eb;
}

.tone-balanced {
  color: #16a34a;
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
  background: #e5e7eb;
}

.native-range::-webkit-slider-thumb {
  width: 14px;
  height: 14px;
  margin-top: -4px;
  border: 0;
  border-radius: 999px;
  appearance: none;
  background: #3b82f6;
  box-shadow: 0 0 0 2px #ffffff, 0 1px 4px rgba(37, 99, 235, 0.35);
}

.native-range::-moz-range-track {
  height: 6px;
  border-radius: 999px;
  background: #e5e7eb;
}

.native-range::-moz-range-progress {
  height: 6px;
  border-radius: 999px;
  background: #e5e7eb;
}

.native-range::-moz-range-thumb {
  width: 14px;
  height: 14px;
  border: 0;
  border-radius: 999px;
  background: #3b82f6;
  box-shadow: 0 0 0 2px #ffffff, 0 1px 4px rgba(37, 99, 235, 0.35);
}

.slider-scale {
  display: flex;
  justify-content: space-between;
  color: #9ca3af;
  font-size: 12px;
  line-height: 16px;
}

.restore-button {
  gap: 4px;
  background: transparent;
  color: #9ca3af;
  font-size: 12px;
  padding: 0;
  transition: color 0.15s ease;
}

.restore-button:hover {
  color: #2563eb;
}

.restore-button svg,
.save-config-button svg {
  width: 16px;
  height: 16px;
  stroke-width: 2;
}

.restore-button svg {
  width: 12px;
  height: 12px;
}

.prompt-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 2px 0 0;
  color: #9ca3af;
  font-size: 12px;
  line-height: 16px;
}

.hidden-business-fields {
  display: none;
}

.save-config-button {
  width: 100%;
  height: 42px;
  border-radius: 12px;
  background: #2563eb;
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
  transition: background 0.15s ease, transform 0.15s ease;
}

.save-config-button:hover:not(:disabled) {
  background: #1d4ed8;
}

.save-config-button:active:not(:disabled) {
  transform: translateY(1px);
}

.is-loading {
  animation: spin 0.9s linear infinite;
}

:deep(.prompt-textarea .el-textarea__inner) {
  min-height: 170px !important;
  border-radius: 12px;
  box-shadow: 0 0 0 1px #e5e7eb inset;
  color: #374151;
  font-size: 14px;
  line-height: 22px;
  padding: 12px;
  transition: box-shadow 0.15s ease;
}

:deep(.prompt-textarea .el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px #3b82f6 inset, 0 0 0 2px rgba(59, 130, 246, 0.12);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1280px) {
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
