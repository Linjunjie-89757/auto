<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Connection, Hide, Setting, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useCaseCenterShared } from '../composables/useCaseCenterShared'
import { platformApi } from '../api/platform'
import type { AiCaseConfig, AiProtocolType, SaveAiCaseConfigPayload } from '../types/api'

type GenerationStyle = 'stable' | 'balanced' | 'creative' | 'custom'
type RoleType = 'CASE_GENERATOR' | 'CASE_REVIEWER'

type RoleForm = {
  id: number | null
  protocolType: AiProtocolType
  model: string
  baseUrl: string
  apiKey: string
  apiKeyMasked: string
  promptTemplate: string
  reviewChecklist: string
  temperature: number
  maxCases: number
  supportsImageInput: boolean
  status: number
}

const DEFAULT_GENERATOR_PROMPT = `你是自动化测试平台中的用例生成模型。
请根据需求内容输出结构化测试用例，返回结果将直接用于测试团队评审。

输出要求：
1. 只返回 JSON，不要返回 markdown 或额外说明。
2. 可以返回数组，或 {"cases":[...]} 结构。
3. 每条用例都必须包含：
   - title
   - caseType
   - priority
   - precondition
   - steps
   - expectedResult
   - riskNotes
4. caseType 只允许：FUNCTION、BOUNDARY、EXCEPTION、REGRESSION
5. priority 只允许：P0、P1、P2、P3
6. 标题、步骤、预期结果必须具体、可执行、可验证。`

const DEFAULT_GENERATOR_CHECKLIST = `优先覆盖主流程、边界条件、异常分支和高风险回归点，避免重复或低价值用例。`

const DEFAULT_REVIEW_PROMPT = `你是自动化测试平台中的用例评审模型。
请对候选测试用例做完整性与覆盖性评审，并返回结构化 JSON。

输出要求：
1. 只返回 JSON，不要返回 markdown 或额外说明。
2. 返回结构必须包含：
   - result
   - summary
   - issues
   - suggestions
3. result 只允许：APPROVE、REJECT、SUGGEST
4. issues 用于指出缺失场景、重复场景、不可执行步骤或不可验证结果。
5. suggestions 用于给出可以继续补充生成的方向。`

const DEFAULT_REVIEW_CHECKLIST = `优先检查主流程、边界、异常、重复场景以及步骤与预期结果是否清晰可验证。`

const roleMeta: Array<{
  roleType: RoleType
  title: string
  subtitle: string
}> = [
  {
    roleType: 'CASE_GENERATOR',
    title: '用例生成模型',
    subtitle: '负责根据需求内容生成候选测试用例。',
  },
  {
    roleType: 'CASE_REVIEWER',
    title: '用例评审模型',
    subtitle: '负责对生成结果做覆盖性和完整性评审。',
  },
]

const protocolOptions: Array<{ label: string; value: AiProtocolType }> = [
  { label: '通用兼容（Chat Completions）', value: 'OPENAI_CHAT_COMPLETIONS' },
  { label: '通用兼容（Responses API）', value: 'OPENAI_RESPONSES' },
  { label: 'Azure OpenAI', value: 'AZURE_OPENAI' },
]

const { isPlatformAdmin, loadSharedBase } = useCaseCenterShared()

const loading = ref(false)
const savingRole = ref<RoleType | null>(null)
const testingRole = ref<RoleType | null>(null)
const loadingSecretRole = ref<RoleType | null>(null)
const generationStyle = ref<Record<RoleType, GenerationStyle>>({
  CASE_GENERATOR: 'balanced',
  CASE_REVIEWER: 'balanced',
})
const revealedApiKeys = reactive<Record<RoleType, string>>({
  CASE_GENERATOR: '',
  CASE_REVIEWER: '',
})
const revealApiKey = reactive<Record<RoleType, boolean>>({
  CASE_GENERATOR: false,
  CASE_REVIEWER: false,
})

const forms = reactive<Record<RoleType, RoleForm>>({
  CASE_GENERATOR: createDefaultForm('CASE_GENERATOR'),
  CASE_REVIEWER: createDefaultForm('CASE_REVIEWER'),
})

function createDefaultForm(roleType: RoleType): RoleForm {
  return {
    id: null,
    protocolType: 'OPENAI_CHAT_COMPLETIONS',
    model: 'gpt-5.5',
    baseUrl: 'https://api.openai.com/v1',
    apiKey: '',
    apiKeyMasked: '',
    promptTemplate: roleType === 'CASE_GENERATOR' ? DEFAULT_GENERATOR_PROMPT : DEFAULT_REVIEW_PROMPT,
    reviewChecklist: roleType === 'CASE_GENERATOR' ? DEFAULT_GENERATOR_CHECKLIST : DEFAULT_REVIEW_CHECKLIST,
    temperature: 0.3,
    maxCases: 20,
    supportsImageInput: roleType === 'CASE_GENERATOR',
    status: 1,
  }
}

function getDefaultPrompt(roleType: RoleType) {
  return roleType === 'CASE_GENERATOR' ? DEFAULT_GENERATOR_PROMPT : DEFAULT_REVIEW_PROMPT
}

function getDefaultChecklist(roleType: RoleType) {
  return roleType === 'CASE_GENERATOR' ? DEFAULT_GENERATOR_CHECKLIST : DEFAULT_REVIEW_CHECKLIST
}

function resetForm(roleType: RoleType) {
  const defaults = createDefaultForm(roleType)
  Object.assign(forms[roleType], defaults)
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

function statusText(roleType: RoleType) {
  return forms[roleType].status === 1 ? '启用中' : '已停用'
}

function temperatureLabel(roleType: RoleType) {
  const style = generationStyle.value[roleType]
  if (style === 'stable') return '稳定'
  if (style === 'creative') return '发散'
  if (style === 'balanced') return '均衡'
  return '自定义'
}

function capabilityHint(roleType: RoleType) {
  if (roleType === 'CASE_GENERATOR') {
    return forms[roleType].supportsImageInput
      ? '当前生成模型将按图文模式工作，请确认所选模型本身支持图片输入。'
      : '当前生成模型按纯文本模式工作。若要结合需求图片生成，请开启图片输入并切换到支持视觉的模型。'
  }
  return '评审模型默认按文本评审使用，只有明确需要图文评审时才建议切换为支持视觉的模型。'
}

function baseUrlHint(roleType: RoleType) {
  const protocolType = forms[roleType].protocolType
  if (protocolType === 'OPENAI_RESPONSES') {
    return '填写服务根地址，例如 https://xxx/v1，系统会自动访问 /responses'
  }
  if (protocolType === 'AZURE_OPENAI') {
    return '填写 Azure OpenAI 对应的服务地址；当前阶段仍按 Azure 专用方式调用'
  }
  return '填写服务根地址，例如 https://xxx/v1，系统会自动访问 /chat/completions'
}

function protocolLabel(protocolType: AiProtocolType) {
  return protocolOptions.find(option => option.value === protocolType)?.label ?? protocolType
}

function apiKeyHint(roleType: RoleType) {
  if (forms[roleType].apiKey.trim()) {
    return '已输入新的 API Key，保存后会覆盖当前配置。'
  }
  if (revealApiKey[roleType]) {
    return '当前正在显示已保存的明文 API Key，请注意周围环境并及时关闭。'
  }
  return forms[roleType].id === null
    ? '首次创建时必须填写 API Key'
    : ''
}

function resetApiKeyState(roleType: RoleType) {
  forms[roleType].apiKey = ''
  revealedApiKeys[roleType] = ''
  revealApiKey[roleType] = false
}

function displayedApiKey(roleType: RoleType) {
  if (forms[roleType].apiKey.trim()) {
    return forms[roleType].apiKey
  }
  if (revealApiKey[roleType] && revealedApiKeys[roleType]) {
    return revealedApiKeys[roleType]
  }
  return forms[roleType].id !== null ? forms[roleType].apiKeyMasked : ''
}

function updateApiKey(roleType: RoleType, value: string) {
  forms[roleType].apiKey = value
  if (value.trim()) {
    revealApiKey[roleType] = false
  }
}

async function toggleApiKeyReveal(roleType: RoleType) {
  if (!forms[roleType].id) {
    return
  }
  if (revealApiKey[roleType]) {
    revealApiKey[roleType] = false
    return
  }
  if (!revealedApiKeys[roleType]) {
    loadingSecretRole.value = roleType
    try {
      const response = await platformApi.getAiCaseConfigSecret('ALL', forms[roleType].id!)
      revealedApiKeys[roleType] = response.apiKey
    } catch (error) {
      ElMessage.error((error as Error).message)
      return
    } finally {
      loadingSecretRole.value = null
    }
  }
  revealApiKey[roleType] = true
}

function canSave(roleType: RoleType) {
  const form = forms[roleType]
  return isPlatformAdmin.value
    && !!form.protocolType
    && !!form.model.trim()
    && !!form.baseUrl.trim()
    && form.temperature >= 0
    && form.temperature <= 1
    && form.maxCases >= 1
    && form.maxCases <= 100
    && (!!form.id || !!form.apiKey.trim())
}

function canTest(roleType: RoleType) {
  const form = forms[roleType]
  return isPlatformAdmin.value
    && !!form.protocolType
    && !!form.model.trim()
    && !!form.baseUrl.trim()
    && (!!form.id || !!form.apiKey.trim())
}

function buildPayload(roleType: RoleType): SaveAiCaseConfigPayload {
  const form = forms[roleType]
  const promptTemplate = form.promptTemplate.trim() || getDefaultPrompt(roleType)
  const reviewChecklist = form.reviewChecklist.trim() || getDefaultChecklist(roleType)
  return {
    roleType,
    protocolType: form.protocolType,
    model: form.model.trim(),
    baseUrl: form.baseUrl.trim(),
    apiKey: form.apiKey.trim() || undefined,
    promptTemplate,
    reviewChecklist,
    temperature: Number(form.temperature),
    maxCases: Number(form.maxCases),
    supportsImageInput: form.supportsImageInput,
    status: form.status,
  }
}

function applyLoadedConfig(roleType: RoleType, config: AiCaseConfig | null) {
  resetForm(roleType)
  if (!config) {
    return
  }
  forms[roleType].id = config.id
  forms[roleType].protocolType = config.protocolType
  forms[roleType].model = config.model
  forms[roleType].baseUrl = config.baseUrl
  forms[roleType].promptTemplate = config.promptTemplate
  forms[roleType].reviewChecklist = config.reviewChecklist ?? ''
  forms[roleType].temperature = config.temperature
  forms[roleType].maxCases = config.maxCases
  forms[roleType].supportsImageInput = config.supportsImageInput
  forms[roleType].status = config.status
  forms[roleType].apiKeyMasked = config.apiKeyMasked
  resetApiKeyState(roleType)
  syncStyleFromTemperature(roleType)
}

async function loadConfig() {
  resetForm('CASE_GENERATOR')
  resetForm('CASE_REVIEWER')
  loading.value = true
  try {
    const response = await platformApi.getAiCaseConfig('ALL')
    applyLoadedConfig('CASE_GENERATOR', response.generatorConfig)
    applyLoadedConfig('CASE_REVIEWER', response.reviewerConfig)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function testConnection(roleType: RoleType) {
  if (!canTest(roleType)) {
    ElMessage.error('请先补全测试连接所需的配置项')
    return
  }
  testingRole.value = roleType
  try {
    const response = await platformApi.testAiCaseConfig('ALL', buildPayload(roleType))
    ElMessage.success(response.message || 'AI 连接测试成功')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    testingRole.value = null
  }
}

async function saveConfig(roleType: RoleType) {
  if (!canSave(roleType)) {
    ElMessage.error('请先补全当前模型配置的必填项')
    return
  }
  savingRole.value = roleType
  try {
    if (!forms[roleType].promptTemplate.trim()) {
      forms[roleType].promptTemplate = getDefaultPrompt(roleType)
    }
    if (!forms[roleType].reviewChecklist.trim()) {
      forms[roleType].reviewChecklist = getDefaultChecklist(roleType)
    }
    const payload = buildPayload(roleType)
    let savedConfig: AiCaseConfig
    if (forms[roleType].id === null) {
      savedConfig = await platformApi.createAiCaseConfig('ALL', payload)
    } else {
      savedConfig = await platformApi.updateAiCaseConfig('ALL', forms[roleType].id!, payload)
    }
    applyLoadedConfig(roleType, savedConfig)
    ElMessage.success(`${roleType === 'CASE_GENERATOR' ? '用例生成模型' : '用例评审模型'}已保存`)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    savingRole.value = null
  }
}

watch(() => forms.CASE_GENERATOR.temperature, () => syncStyleFromTemperature('CASE_GENERATOR'))
watch(() => forms.CASE_REVIEWER.temperature, () => syncStyleFromTemperature('CASE_REVIEWER'))

onMounted(async () => {
  await loadSharedBase()
  await loadConfig()
})
</script>

<template>
  <section class="page-shell">
    <div class="ai-config-grid">
      <article v-for="meta in roleMeta" :key="meta.roleType" class="panel-card ai-config-card">
        <div class="panel-header">
          <div>
            <div class="panel-title">{{ meta.title }}</div>
            <div class="panel-subtitle">{{ meta.subtitle }}</div>
          </div>
          <div class="panel-header-actions">
            <el-button
              v-if="isPlatformAdmin"
              :loading="testingRole === meta.roleType"
              :disabled="!canTest(meta.roleType)"
              @click="testConnection(meta.roleType)"
            >
              <el-icon><Connection /></el-icon>
              测试连接
            </el-button>
            <el-button
              v-if="isPlatformAdmin"
              type="primary"
              :loading="savingRole === meta.roleType"
              :disabled="!canSave(meta.roleType)"
              @click="saveConfig(meta.roleType)"
            >
              <el-icon><Setting /></el-icon>
              保存配置
            </el-button>
          </div>
        </div>

        <el-form label-width="96px" class="ai-config-form" :disabled="loading || !isPlatformAdmin">
          <el-form-item label="接入类型">
            <el-select v-model="forms[meta.roleType].protocolType">
              <el-option
                v-for="option in protocolOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="模型">
            <el-input v-model="forms[meta.roleType].model" />
            <div class="field-hint">{{ capabilityHint(meta.roleType) }}</div>
          </el-form-item>
          <el-form-item label="Base URL">
            <el-input v-model="forms[meta.roleType].baseUrl" />
            <div class="field-hint">{{ baseUrlHint(meta.roleType) }}</div>
          </el-form-item>
          <el-form-item label="API Key">
            <el-input
              :model-value="displayedApiKey(meta.roleType)"
              placeholder="请输入 API Key"
              @update:model-value="(value: string) => updateApiKey(meta.roleType, value)"
            >
              <template #suffix>
                <el-button
                  v-if="forms[meta.roleType].id !== null"
                  link
                  :loading="loadingSecretRole === meta.roleType"
                  @click="toggleApiKeyReveal(meta.roleType)"
                >
                  <el-icon>
                    <component :is="revealApiKey[meta.roleType] ? Hide : View" />
                  </el-icon>
                </el-button>
              </template>
            </el-input>
            <div v-if="revealApiKey[meta.roleType]" class="secret-alert">
              正在显示已保存的明文 API Key
            </div>
            <div class="field-hint">{{ apiKeyHint(meta.roleType) }}</div>
          </el-form-item>
          <el-form-item label="状态">
            <div class="status-pair-row">
              <div class="status-pair-item">
                <div class="status-toggle-row">
                  <el-switch v-model="forms[meta.roleType].status" :active-value="1" :inactive-value="0" />
                  <span class="status-toggle-text">{{ statusText(meta.roleType) }}</span>
                </div>
              </div>
              <div v-if="meta.roleType === 'CASE_GENERATOR'" class="status-pair-item">
                <span class="status-pair-label">图片输入</span>
                <div class="status-toggle-row">
                  <el-switch v-model="forms[meta.roleType].supportsImageInput" />
                  <span class="status-toggle-text">{{ forms[meta.roleType].supportsImageInput ? '已开启' : '已关闭' }}</span>
                </div>
              </div>
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
          <el-form-item label="系统生成上限">
            <el-input-number v-model="forms[meta.roleType].maxCases" :min="1" :max="100" />
          </el-form-item>
          <el-form-item label="Prompt 模板">
            <el-input v-model="forms[meta.roleType].promptTemplate" type="textarea" :rows="12" resize="vertical" />
          </el-form-item>
          <el-form-item :label="meta.roleType === 'CASE_GENERATOR' ? '补充要求' : '评审清单'">
            <el-input v-model="forms[meta.roleType].reviewChecklist" type="textarea" :rows="5" resize="vertical" />
          </el-form-item>
        </el-form>

        <div class="detail-card ai-config-summary">
          <div class="detail-title">{{ meta.title }}</div>
          <div class="detail-meta">
            {{ forms[meta.roleType].id ? '全局已保存该模型配置' : '全局还未保存该模型配置' }}
          </div>
          <div class="detail-body"><strong>当前接入：</strong>{{ protocolLabel(forms[meta.roleType].protocolType) }} / {{ forms[meta.roleType].model }}</div>
          <div class="detail-body"><strong>Temperature：</strong>{{ forms[meta.roleType].temperature.toFixed(1) }}（{{ temperatureLabel(meta.roleType) }}）</div>
          <div class="detail-body"><strong>系统生成上限：</strong>{{ forms[meta.roleType].maxCases }} 条</div>
          <div v-if="meta.roleType === 'CASE_GENERATOR'" class="detail-body"><strong>图文生成：</strong>{{ forms[meta.roleType].supportsImageInput ? '已开启' : '未开启' }}</div>
          <div class="detail-body"><strong>状态：</strong>{{ statusText(meta.roleType) }}</div>
        </div>
      </article>
    </div>

    <article v-if="!isPlatformAdmin" class="panel-card">
      <div class="empty-block compact-block">
        <div class="empty-title">当前账号只读</div>
        <div class="empty-desc">AI 配置维护仅对管理员开放，你当前可以查看现有配置，但不能修改。</div>
      </div>
    </article>
  </section>
</template>

<style scoped>
.ai-config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.panel-header-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.ai-config-form {
  margin-top: 4px;
}

.field-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--text-subtle);
}

.secret-alert {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
  font-size: 12px;
  line-height: 1.4;
}

.status-toggle-row {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.status-pair-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: center;
  column-gap: 28px;
  width: 100%;
}

.status-pair-item {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  justify-self: start;
}

.status-pair-label {
  font-size: 13px;
  color: var(--text-subtle);
  flex-shrink: 0;
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

.ai-config-summary {
  margin-top: 12px;
}

.compact-block {
  margin-top: 0;
}

@media (max-width: 1200px) {
  .ai-config-grid {
    grid-template-columns: 1fr;
  }
}
</style>
