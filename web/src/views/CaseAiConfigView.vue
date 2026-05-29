<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { MagicStick, RefreshRight, Setting } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { platformApi } from '../api/platform'
import type {
  AiCapabilityOverride,
  AiCapabilitySource,
  AiCaseConfig,
  AiModelCapabilities,
  AiProviderConnection,
  AiProviderModel,
  SaveAiCaseConfigPayload,
} from '../types/api'

type RoleType = 'CASE_GENERATOR' | 'CASE_REVIEWER'
type GenerationStyle = 'stable' | 'balanced' | 'creative' | 'custom'
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

const router = useRouter()

const DEFAULT_GENERATOR_PROMPT = `你是测试用例生成模型。请根据需求内容输出结构化测试用例，只返回 JSON，不要返回 markdown 或解释说明。`
const DEFAULT_GENERATOR_CHECKLIST = '优先覆盖主流程、边界条件、异常分支和高风险回归点，避免重复或低价值用例。'
const DEFAULT_REVIEW_PROMPT = `你是测试用例评审模型。请对候选测试用例做完整性与覆盖性评审，只返回结构化 JSON。`
const DEFAULT_REVIEW_CHECKLIST = '优先检查主流程、边界、异常、重复场景，以及步骤与预期结果是否清晰可验证。'

const roleMeta: Array<{ roleType: RoleType; title: string; subtitle: string }> = [
  {
    roleType: 'CASE_GENERATOR',
    title: '用例生成模型',
    subtitle: '负责根据需求内容生成候选测试用例。',
  },
  {
    roleType: 'CASE_REVIEWER',
    title: '用例评审模型',
    subtitle: '负责评估生成结果的覆盖性、完整性和可执行性。',
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

const capabilitySourceText: Record<AiCapabilitySource, string> = {
  DECLARED: '接口声明',
  INFERRED: '规则推断',
  PROBED: '主动探测',
  MANUAL: '人工修正',
  UNKNOWN: '未知',
}

const loading = ref(false)
const providerLoading = ref(false)
const savingRole = ref<RoleType | null>(null)
const probingRole = ref<RoleType | null>(null)
const providerFetchingId = ref<number | null>(null)
const bootstrappingLegacy = ref(false)
const hasLegacyConfig = ref(false)
const canBootstrapFromLegacy = ref(false)

const providers = ref<AiProviderConnection[]>([])
const providerModels = reactive<Record<number, AiProviderModel[]>>({})
const generationStyle = ref<Record<RoleType, GenerationStyle>>({
  CASE_GENERATOR: 'balanced',
  CASE_REVIEWER: 'balanced',
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
  syncStyleFromTemperature(roleType)
}

function syncStyleFromTemperature(roleType: RoleType) {
  const temperature = Number(forms[roleType].temperature.toFixed(1))
  if (temperature === 0.2) {
    generationStyle.value[roleType] = 'stable'
  } else if (temperature === 0.3) {
    generationStyle.value[roleType] = 'balanced'
  } else if (temperature === 0.5) {
    generationStyle.value[roleType] = 'creative'
  } else {
    generationStyle.value[roleType] = 'custom'
  }
}

function applyStyle(roleType: RoleType, style: GenerationStyle) {
  generationStyle.value[roleType] = style
  if (style === 'stable') {
    forms[roleType].temperature = 0.2
  } else if (style === 'balanced') {
    forms[roleType].temperature = 0.3
  } else if (style === 'creative') {
    forms[roleType].temperature = 0.5
  }
}

function statusText(status: number) {
  return status === 1 ? '启用中' : '已停用'
}

function temperatureLabel(roleType: RoleType) {
  const style = generationStyle.value[roleType]
  if (style === 'stable') return '稳定'
  if (style === 'creative') return '发散'
  if (style === 'balanced') return '均衡'
  return '自定义'
}

function reviewChecklistLabel(roleType: RoleType) {
  return roleType === 'CASE_GENERATOR' ? '补充要求' : '评审清单'
}

function capabilityStateText(supported: boolean | null) {
  if (supported === true) return '支持'
  if (supported === false) return '不支持'
  return '未知'
}

function overrideValueForSelect(value: boolean | null | undefined) {
  if (value === true) return 'true'
  if (value === false) return 'false'
  return 'inherit'
}

const providerSelectOptions = computed(() => providers.value.map(item => ({
  label: `${item.connectionName} / ${item.protocolType}`,
  value: item.id,
})))

const hasNoProviders = computed(() => providers.value.length === 0)

function getProviderById(id: number | null) {
  if (!id) return null
  return providers.value.find(item => item.id === id) ?? null
}

function getRoleModels(roleType: RoleType) {
  const connectionId = forms[roleType].providerConnectionId
  if (!connectionId) return []
  return providerModels[connectionId] ?? []
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
  syncStyleFromTemperature(roleType)
}

async function loadProviders() {
  providerLoading.value = true
  try {
    providers.value = await platformApi.getAiProviderConnections('ALL')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    providerLoading.value = false
  }
}

async function loadProviderModels(connectionId: number, forceFetch = false) {
  if (!connectionId) {
    return []
  }
  try {
    if (forceFetch) {
      const response = await platformApi.fetchAiProviderModels('ALL', connectionId)
      providerModels[connectionId] = response.models
      ElMessage.success(response.message || '模型列表已刷新')
    } else {
      providerModels[connectionId] = await platformApi.getAiProviderModels('ALL', connectionId)
    }
  } catch (error) {
    ElMessage.error((error as Error).message)
    providerModels[connectionId] = providerModels[connectionId] ?? []
  }
  return providerModels[connectionId] ?? []
}

async function loadConfig() {
  loading.value = true
  resetRoleForm('CASE_GENERATOR')
  resetRoleForm('CASE_REVIEWER')
  try {
    const response = await platformApi.getAiCaseConfig('ALL')
    hasLegacyConfig.value = response.hasLegacyConfig
    canBootstrapFromLegacy.value = response.canBootstrapFromLegacy
    applyLoadedRole('CASE_GENERATOR', response.generatorConfig)
    applyLoadedRole('CASE_REVIEWER', response.reviewerConfig)
    const connectionIds = Array.from(new Set(
      [forms.CASE_GENERATOR.providerConnectionId, forms.CASE_REVIEWER.providerConnectionId]
        .filter((item): item is number => typeof item === 'number' && item > 0),
    ))
    await Promise.all(connectionIds.map(connectionId => loadProviderModels(connectionId)))
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function bootstrapLegacyConfig() {
  bootstrappingLegacy.value = true
  try {
    const response = await platformApi.bootstrapAiCaseConfigFromLegacy('ALL')
    hasLegacyConfig.value = response.hasLegacyConfig
    canBootstrapFromLegacy.value = response.canBootstrapFromLegacy
    ElMessage.success('旧版 AI 配置已复制到当前账号')
    await Promise.all([loadProviders(), loadConfig()])
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    bootstrappingLegacy.value = false
  }
}

function goToAiConnections() {
  router.push({
    path: '/settings',
    query: { tab: 'aiConnection' },
  })
}

async function handleRoleConnectionChanged(roleType: RoleType) {
  const connectionId = forms[roleType].providerConnectionId
  forms[roleType].detectedCapabilities = createUnknownCapabilities()
  recomputeEffectiveCapabilities(roleType)
  if (!connectionId) {
    return
  }
  await loadProviderModels(connectionId)
  if (forms[roleType].model.trim()) {
    const matched = (providerModels[connectionId] ?? []).find(item => item.modelName === forms[roleType].model.trim())
    if (matched) {
      forms[roleType].detectedCapabilities = matched.detectedCapabilities
      recomputeEffectiveCapabilities(roleType)
    }
  }
}

async function handleRoleModelChanged(roleType: RoleType) {
  const connectionId = forms[roleType].providerConnectionId
  const model = forms[roleType].model.trim()
  if (!connectionId || !model) {
    forms[roleType].detectedCapabilities = createUnknownCapabilities()
    recomputeEffectiveCapabilities(roleType)
    return
  }
  const cached = (providerModels[connectionId] ?? []).find(item => item.modelName === model)
  if (cached) {
    forms[roleType].detectedCapabilities = cached.detectedCapabilities
    recomputeEffectiveCapabilities(roleType)
  }
  await probeRoleModel(roleType)
}

async function probeRoleModel(roleType: RoleType) {
  const connectionId = forms[roleType].providerConnectionId
  const model = forms[roleType].model.trim()
  if (!connectionId || !model) {
    return
  }
  probingRole.value = roleType
  try {
    const result = await platformApi.probeAiProviderModel('ALL', connectionId, model)
    const nextModels = providerModels[connectionId] ?? []
    const index = nextModels.findIndex(item => item.modelName === result.modelName)
    if (index >= 0) {
      nextModels.splice(index, 1, result)
    } else {
      nextModels.unshift(result)
    }
    providerModels[connectionId] = [...nextModels]
    forms[roleType].detectedCapabilities = result.detectedCapabilities
    recomputeEffectiveCapabilities(roleType)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    probingRole.value = null
  }
}

function updateCapabilityOverride(roleType: RoleType, key: CapabilityKey, value: string) {
  forms[roleType].capabilityOverride[key] = value === 'inherit' ? null : value === 'true'
  recomputeEffectiveCapabilities(roleType)
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

watch(() => forms.CASE_GENERATOR.temperature, () => syncStyleFromTemperature('CASE_GENERATOR'))
watch(() => forms.CASE_REVIEWER.temperature, () => syncStyleFromTemperature('CASE_REVIEWER'))

onMounted(async () => {
  await Promise.all([loadProviders(), loadConfig()])
})
</script>

<template>
  <section class="page-shell ai-role-page">
    <article class="panel-card overview-card">
      <div class="panel-header">
        <div>
          <div class="panel-title">AI角色配置</div>
          <div class="panel-subtitle">这里负责绑定你自己的 AI 连接，并配置生成模型、评审模型及对应 Prompt。</div>
        </div>
        <div class="panel-header-actions">
          <el-button @click="loadConfig">
            <el-icon><RefreshRight /></el-icon>
            刷新配置
          </el-button>
          <el-button type="primary" @click="goToAiConnections">
            <el-icon><Setting /></el-icon>
            管理AI连接
          </el-button>
        </div>
      </div>

      <div v-if="canBootstrapFromLegacy" class="legacy-banner">
        <div>
          <div class="legacy-title">检测到旧版全局 AI 配置</div>
          <div class="legacy-desc">当前账号还没有个人 AI 配置，可以一键复制旧版配置到“我的连接”和“我的角色配置”。</div>
        </div>
        <el-button type="primary" :loading="bootstrappingLegacy" @click="bootstrapLegacyConfig">复制为我的配置</el-button>
      </div>

      <div v-else-if="hasLegacyConfig" class="legacy-note">
        旧版全局 AI 配置仍保留在系统中，但当前页面只展示并使用你自己的 AI 配置。
      </div>

      <div v-if="hasNoProviders" class="empty-inline">
        你还没有可用的 AI 连接，请先到系统设置中创建。
        <el-button link type="primary" @click="goToAiConnections">去创建连接</el-button>
      </div>
    </article>

    <div class="ai-config-grid">
      <article v-for="meta in roleMeta" :key="meta.roleType" class="panel-card ai-config-card">
        <div class="panel-header">
          <div>
            <div class="panel-title">{{ meta.title }}</div>
            <div class="panel-subtitle">{{ meta.subtitle }}</div>
          </div>
          <div class="panel-header-actions">
            <el-button
              :loading="probingRole === meta.roleType"
              :disabled="!forms[meta.roleType].providerConnectionId || !forms[meta.roleType].model.trim()"
              @click="probeRoleModel(meta.roleType)"
            >
              <el-icon><MagicStick /></el-icon>
              探测能力
            </el-button>
            <el-button
              type="primary"
              :loading="savingRole === meta.roleType"
              :disabled="!canSaveRole(meta.roleType)"
              @click="saveRole(meta.roleType)"
            >
              <el-icon><Setting /></el-icon>
              保存绑定
            </el-button>
          </div>
        </div>

        <el-form label-width="100px" :disabled="loading" class="ai-config-form">
          <el-form-item label="绑定连接">
            <div class="field-stack">
              <el-select
                v-model="forms[meta.roleType].providerConnectionId"
                placeholder="请选择已保存的 AI 连接"
                @change="handleRoleConnectionChanged(meta.roleType)"
              >
                <el-option
                  v-for="option in providerSelectOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
              <div v-if="hasNoProviders" class="field-hint">
                当前没有可绑定的 AI 连接。
                <el-button link type="primary" @click="goToAiConnections">去创建连接</el-button>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="模型">
            <div class="field-stack">
              <div class="inline-row">
                <el-select
                  v-model="forms[meta.roleType].model"
                  filterable
                  allow-create
                  default-first-option
                  clearable
                  placeholder="先获取模型列表，或直接手工输入模型名"
                  class="flex-select"
                  @change="handleRoleModelChanged(meta.roleType)"
                >
                  <el-option
                    v-for="item in getRoleModels(meta.roleType)"
                    :key="item.modelName"
                    :label="item.displayName || item.modelName"
                    :value="item.modelName"
                  />
                </el-select>
                <el-button
                  :loading="providerFetchingId === forms[meta.roleType].providerConnectionId"
                  :disabled="!forms[meta.roleType].providerConnectionId"
                  @click="loadProviderModels(forms[meta.roleType].providerConnectionId!, true)"
                >
                  <el-icon><RefreshRight /></el-icon>
                  获取模型
                </el-button>
              </div>
              <div class="field-hint">
                支持下拉选择，也支持在服务端不提供 `/models` 时直接手工输入模型名称。
              </div>
            </div>
          </el-form-item>

          <el-form-item label="状态">
            <div class="status-toggle-row">
              <el-switch v-model="forms[meta.roleType].status" :active-value="1" :inactive-value="0" />
              <span class="status-toggle-text">{{ statusText(forms[meta.roleType].status) }}</span>
            </div>
          </el-form-item>

          <el-form-item label="生成风格">
            <el-segmented
              :model-value="generationStyle[meta.roleType]"
              :options="[
                { label: '稳定', value: 'stable' },
                { label: '均衡', value: 'balanced' },
                { label: '发散', value: 'creative' },
                { label: '自定义', value: 'custom' },
              ]"
              @change="(value: string | number | boolean) => applyStyle(meta.roleType, value as GenerationStyle)"
            />
          </el-form-item>

          <el-form-item label="Temperature">
            <div class="temperature-field">
              <el-slider v-model="forms[meta.roleType].temperature" :min="0" :max="1" :step="0.1" />
              <div class="temperature-meta">
                <span class="temperature-badge">{{ temperatureLabel(meta.roleType) }}</span>
                <span>{{ forms[meta.roleType].temperature.toFixed(1) }}</span>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="系统上限">
            <el-input-number v-model="forms[meta.roleType].maxCases" :min="1" :max="100" />
          </el-form-item>

          <el-form-item label="能力矩阵">
            <div class="capability-panel">
              <div class="capability-header">
                <div>能力项</div>
                <div>自动探测</div>
                <div>人工修正</div>
                <div>最终生效</div>
              </div>
              <div v-for="item in capabilityMeta" :key="item.key" class="capability-row">
                <div class="capability-name">
                  <div>{{ item.label }}</div>
                  <div class="capability-hint">{{ item.hint }}</div>
                </div>
                <div class="capability-value">
                  <el-tag size="small" effect="plain" :type="forms[meta.roleType].detectedCapabilities[item.key].supported === true ? 'success' : forms[meta.roleType].detectedCapabilities[item.key].supported === false ? 'danger' : 'info'">
                    {{ capabilityStateText(forms[meta.roleType].detectedCapabilities[item.key].supported) }}
                  </el-tag>
                  <span class="source-text">{{ capabilitySourceText[forms[meta.roleType].detectedCapabilities[item.key].source] }}</span>
                </div>
                <div>
                  <el-select
                    :model-value="overrideValueForSelect(forms[meta.roleType].capabilityOverride[item.key])"
                    @change="(value: string) => updateCapabilityOverride(meta.roleType, item.key, value)"
                  >
                    <el-option label="跟随自动探测" value="inherit" />
                    <el-option label="强制支持" value="true" />
                    <el-option label="强制禁用" value="false" />
                  </el-select>
                </div>
                <div class="capability-value">
                  <el-tag size="small" :type="forms[meta.roleType].effectiveCapabilities[item.key].supported === true ? 'success' : forms[meta.roleType].effectiveCapabilities[item.key].supported === false ? 'danger' : 'info'">
                    {{ capabilityStateText(forms[meta.roleType].effectiveCapabilities[item.key].supported) }}
                  </el-tag>
                  <span class="source-text">{{ capabilitySourceText[forms[meta.roleType].effectiveCapabilities[item.key].source] }}</span>
                </div>
              </div>
            </div>
          </el-form-item>

          <el-form-item label="Prompt 模板">
            <el-input v-model="forms[meta.roleType].promptTemplate" type="textarea" :rows="10" resize="vertical" />
          </el-form-item>

          <el-form-item :label="reviewChecklistLabel(meta.roleType)">
            <el-input v-model="forms[meta.roleType].reviewChecklist" type="textarea" :rows="5" resize="vertical" />
          </el-form-item>
        </el-form>

        <div class="detail-card ai-config-summary">
          <div class="detail-title">{{ meta.title }}</div>
          <div class="detail-meta">
            {{ forms[meta.roleType].providerConnectionId ? '已绑定连接，可直接参与当前账号的 AI 业务链路。' : '尚未绑定连接。' }}
          </div>
          <div class="detail-body"><strong>当前连接：</strong>{{ getProviderById(forms[meta.roleType].providerConnectionId)?.connectionName ?? '-' }}</div>
          <div class="detail-body"><strong>当前模型：</strong>{{ forms[meta.roleType].model || '-' }}</div>
          <div class="detail-body"><strong>Temperature：</strong>{{ forms[meta.roleType].temperature.toFixed(1) }}（{{ temperatureLabel(meta.roleType) }}）</div>
          <div class="detail-body"><strong>系统上限：</strong>{{ forms[meta.roleType].maxCases }} 条</div>
          <div class="detail-body"><strong>图片输入：</strong>{{ forms[meta.roleType].supportsImageInput ? '已开启' : '未开启' }}</div>
          <div class="detail-body"><strong>状态：</strong>{{ statusText(forms[meta.roleType].status) }}</div>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.ai-role-page {
  display: grid;
  gap: 16px;
}

.overview-card {
  display: grid;
  gap: 12px;
}

.panel-header-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.legacy-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 10px;
  background: rgba(36, 107, 255, 0.08);
  border: 1px solid rgba(36, 107, 255, 0.16);
}

.legacy-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
}

.legacy-desc,
.legacy-note,
.empty-inline {
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-subtle);
}

.legacy-note {
  padding: 2px 0;
}

.ai-config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.ai-config-form {
  margin-top: 4px;
}

.field-stack {
  width: 100%;
}

.inline-row {
  display: flex;
  gap: 10px;
  width: 100%;
}

.flex-select {
  flex: 1;
  min-width: 0;
}

.field-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-subtle);
  line-height: 1.6;
}

.status-toggle-row {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.status-toggle-text {
  font-size: 13px;
  color: var(--text-subtle);
}

.temperature-field {
  width: 100%;
}

.temperature-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 13px;
  color: var(--text-subtle);
}

.temperature-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.1);
  color: #2563eb;
}

.capability-panel {
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  overflow: hidden;
}

.capability-header,
.capability-row {
  display: grid;
  grid-template-columns: 1.4fr 1fr 1fr 1fr;
  gap: 12px;
  padding: 12px 14px;
  align-items: center;
}

.capability-header {
  background: var(--el-fill-color-light);
  color: var(--text-subtle);
  font-size: 12px;
  font-weight: 600;
}

.capability-row + .capability-row {
  border-top: 1px solid var(--el-border-color-lighter);
}

.capability-name {
  min-width: 0;
}

.capability-hint {
  margin-top: 4px;
  color: var(--text-subtle);
  font-size: 12px;
  line-height: 1.5;
}

.capability-value {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.source-text {
  color: var(--text-subtle);
  font-size: 12px;
}

.ai-config-summary {
  margin-top: 12px;
}

@media (max-width: 1280px) {
  .ai-config-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .legacy-banner {
    flex-direction: column;
    align-items: flex-start;
  }

  .capability-header {
    display: none;
  }

  .capability-row {
    grid-template-columns: 1fr;
  }

  .inline-row {
    flex-direction: column;
  }
}
</style>
