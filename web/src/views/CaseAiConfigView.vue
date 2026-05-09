<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Connection, Hide, Setting, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useCaseCenterShared } from '../composables/useCaseCenterShared'
import { platformApi } from '../api/platform'
import type { AiCaseConfig, SaveAiCaseConfigPayload } from '../types/api'

type GenerationStyle = 'stable' | 'balanced' | 'creative' | 'custom'
type RoleType = 'CASE_GENERATOR' | 'CASE_REVIEWER'
type RoleForm = {
  id: number | null
  provider: string
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

const DEFAULT_GENERATOR_PROMPT = `你是自动化测试工作平台中的测试用例生成模型。

请根据需求内容生成结构化测试用例，输出结果用于公司内部测试团队直接评审与补充。

输入变量说明：
- {{requirement_text}}：需求原文
- {{case_count}}：希望生成的用例条数
- {{ratio_functional}}：功能用例占比
- {{ratio_boundary}}：边界用例占比
- {{ratio_exception}}：异常用例占比

生成要求：
1. 优先覆盖主流程、核心业务链路与关键回归点
2. 必须补充边界条件、异常分支、兜底场景
3. 用例标题要清晰明确，能直接看出测试目标
4. 测试步骤要可执行，不要过于空泛
5. 预期结果要可验证，不要只写“成功”或“正常”
6. 避免重复用例，避免明显重叠场景
7. 如果需求描述不完整，请基于常见业务风险补足必要候选用例

输出要求：
1. 仅返回 JSON，不要返回 markdown、解释说明或多余文本
2. 返回数组，或 {"cases":[...]} 结构
3. 每条用例包含以下字段：
   - title
   - caseType
   - priority
   - precondition
   - steps
   - expectedResult
   - riskNotes
4. caseType 只允许：FUNCTION、BOUNDARY、EXCEPTION、REGRESSION
5. priority 只允许：P0、P1、P2、P3`

const DEFAULT_GENERATOR_CHECKLIST = `请优先保证主流程、边界、异常三类场景都有覆盖。
如果需求中缺少细节，请补足高风险候选用例，避免只生成一批主流程用例。`

const DEFAULT_REVIEW_PROMPT = `你是自动化测试工作平台中的用例评审模型。

请对已生成的测试用例进行完整性与覆盖性评审，并给出可继续补充生成的建议。

评审重点：
1. 是否覆盖主流程
2. 是否遗漏边界条件
3. 是否遗漏异常场景与兜底场景
4. 是否存在重复或高度相似的用例
5. 标题、步骤、预期结果是否清晰、可执行、可验证
6. 是否存在明显不完整、不可落地或无意义的用例

输出要求：
1. 仅返回 JSON，不要返回 markdown、解释说明或多余文本
2. 返回结构必须包含：
   - result
   - summary
   - issues
   - suggestions
3. result 只允许：APPROVE、REJECT、SUGGEST
4. 当评审通过时，result 输出 APPROVE
5. 当存在明显缺失或质量问题时，指出问题并给出补充建议
6. suggestions 需要给出可直接用于二次补充生成的场景方向`

const DEFAULT_REVIEW_CHECKLIST = `请优先识别缺失场景、重复场景、步骤不清晰、预期结果不可验证的问题。
如果评审通过，请明确输出 APPROVE；如果不通过，请给出可以继续补充生成的建议。`

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
    provider: 'OPENAI',
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
  if (style === 'balanced') return '平衡'
  return '自定义'
}

function capabilityHint(roleType: RoleType) {
  if (roleType === 'CASE_GENERATOR') {
    return forms[roleType].supportsImageInput
      ? '当前生成模型将按图文模式工作，请确认所填模型本身支持图片输入。'
      : '当前生成模型按纯文本模式工作。若要使用需求图片生成，请开启“图片输入”并换成多模态模型。'
  }
  return '评审模型默认按文本评审使用。只有你明确需要图文评审时，才建议换成支持图像理解的模型。'
}

function apiKeyHint(roleType: RoleType) {
  if (forms[roleType].apiKey.trim()) {
    return '已输入新的 API Key，保存后会覆盖当前配置'
  }
  if (revealApiKey[roleType]) {
    return '当前正在展示已保存的明文 API Key，请注意周围环境并及时关闭'
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
    && !!form.provider.trim()
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
    && !!form.provider.trim()
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
    provider: form.provider.trim(),
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
  forms[roleType].provider = config.provider
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
          <el-form-item label="提供方">
            <el-select v-model="forms[meta.roleType].provider">
              <el-option label="OpenAI" value="OPENAI" />
              <el-option label="Azure OpenAI" value="AZURE_OPENAI" />
              <el-option label="Internal Proxy" value="INTERNAL_PROXY" />
            </el-select>
          </el-form-item>
          <el-form-item label="模型">
            <el-input v-model="forms[meta.roleType].model" />
            <div class="field-hint">{{ capabilityHint(meta.roleType) }}</div>
          </el-form-item>
          <el-form-item label="Base URL">
            <el-input v-model="forms[meta.roleType].baseUrl" />
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
                { label: '平衡', value: 'balanced' },
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
          <div class="detail-body"><strong>当前模型：</strong>{{ forms[meta.roleType].provider }} / {{ forms[meta.roleType].model }}</div>
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
