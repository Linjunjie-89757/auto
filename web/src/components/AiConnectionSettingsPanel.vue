<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  AlertCircle,
  ChevronLeft,
  ChevronRight,
  Check,
  Database,
  Download,
  Edit2,
  Eye,
  EyeOff,
  Plus,
  RefreshCw,
  Trash2,
  Wifi,
  WifiOff,
  X,
} from '@lucide/vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { platformApi } from '../api/platform'
import anthropicLogo from '../assets/ai-providers/anthropic.svg'
import azureLogo from '../assets/ai-providers/azure.svg'
import customLogo from '../assets/ai-providers/custom.svg'
import deepseekLogo from '../assets/ai-providers/deepseek.svg'
import googleLogo from '../assets/ai-providers/google.svg'
import kimiLogo from '../assets/ai-providers/kimi.svg'
import minimaxLogo from '../assets/ai-providers/minimax.svg'
import ollamaLogo from '../assets/ai-providers/ollama.svg'
import openaiLogo from '../assets/ai-providers/openai.svg'
import qwenLogo from '../assets/ai-providers/qwen.svg'
import xiaomiLogo from '../assets/ai-providers/xiaomi.svg'
import zhipuLogo from '../assets/ai-providers/zhipu.svg'
import type {
  AiProtocolType,
  AiProviderConnection,
  AiProviderModel,
  SaveAiProviderConnectionPayload,
} from '../types/api'

type ProviderDialogStep = 'provider' | 'config'

type ProviderDialogForm = {
  id: number | null
  connectionName: string
  protocolType: AiProtocolType
  baseUrl: string
  requestTimeoutSeconds: number | null
  modelName: string
  apiKey: string
  status: number
}

type ProviderBrand = {
  id: string
  name: string
  shortName: string
  desc: string
  color: string
  bg: string
  border: string
  text: string
  logoClass: string
  logoSrc?: string
  baseUrl: string
  models: string[]
  protocolType: AiProtocolType
  apiKeyLabel: string
  aliases: string[]
}

const providerBrands: ProviderBrand[] = [
  {
    id: 'openai',
    name: 'OpenAI',
    shortName: 'OpenAI',
    desc: 'GPT-4o、GPT-4 Turbo 等系列模型',
    color: '#10a37f',
    bg: '#e3f5f0',
    border: '#8be0c8',
    text: '#10a37f',
    logoClass: 'provider-logo-openai',
    logoSrc: openaiLogo,
    baseUrl: 'https://api.openai.com/v1',
    models: ['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo', 'gpt-3.5-turbo'],
    protocolType: 'OPENAI_COMPATIBLE_CHAT',
    apiKeyLabel: 'API Key',
    aliases: ['openai', 'api.openai.com', 'chatgpt'],
  },
  {
    id: 'anthropic',
    name: 'Anthropic',
    shortName: 'Anthropic',
    desc: 'Claude 3.5 Sonnet、Claude 3 Opus 等',
    color: '#cc785c',
    bg: '#f5ede8',
    border: '#e5c2af',
    text: '#c05f36',
    logoClass: 'provider-logo-anthropic',
    logoSrc: anthropicLogo,
    baseUrl: 'https://api.anthropic.com',
    models: ['claude-3-5-sonnet-20241022', 'claude-3-opus-20240229', 'claude-3-haiku-20240307'],
    protocolType: 'OPENAI_COMPATIBLE_CHAT',
    apiKeyLabel: 'API Key',
    aliases: ['anthropic', 'claude'],
  },
  {
    id: 'google',
    name: 'Google Gemini',
    shortName: 'Google',
    desc: 'Gemini 1.5 Pro、Gemini 1.5 Flash 等',
    color: '#4285f4',
    bg: '#f8fafc',
    border: '#e5e7eb',
    text: '#64748b',
    logoClass: 'provider-logo-google',
    logoSrc: googleLogo,
    baseUrl: 'https://generativelanguage.googleapis.com/v1',
    models: ['gemini-1.5-pro', 'gemini-1.5-flash', 'gemini-1.0-pro'],
    protocolType: 'OPENAI_COMPATIBLE_CHAT',
    apiKeyLabel: 'API Key',
    aliases: ['google', 'gemini'],
  },
  {
    id: 'deepseek',
    name: 'DeepSeek',
    shortName: 'DeepSeek',
    desc: 'DeepSeek V3、DeepSeek Coder 等',
    color: '#1c3ef0',
    bg: '#eef2ff',
    border: '#b9c5ff',
    text: '#1c3ef0',
    logoClass: 'provider-logo-deepseek',
    logoSrc: deepseekLogo,
    baseUrl: 'https://api.deepseek.com/v1',
    models: ['deepseek-chat', 'deepseek-coder', 'deepseek-reasoner'],
    protocolType: 'OPENAI_COMPATIBLE_CHAT',
    apiKeyLabel: 'API Key',
    aliases: ['deepseek'],
  },
  {
    id: 'qwen',
    name: '阿里云 / Qwen',
    shortName: '阿里云',
    desc: 'Qwen-Max、Qwen-Plus、Qwen-Turbo 等',
    color: '#ff8a3d',
    bg: '#fff7ed',
    border: '#fed7aa',
    text: '#fb923c',
    logoClass: 'provider-logo-qwen',
    logoSrc: qwenLogo,
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    models: ['qwen-max', 'qwen-plus', 'qwen-turbo', 'qwen-long'],
    protocolType: 'OPENAI_COMPATIBLE_CHAT',
    apiKeyLabel: 'DashScope API Key',
    aliases: ['qwen', 'dashscope', 'aliyun', '阿里', '通义'],
  },
  {
    id: 'azure',
    name: 'Azure OpenAI',
    shortName: 'Azure',
    desc: '微软 Azure 托管的 OpenAI 模型',
    color: '#63b3ed',
    bg: '#f8fafc',
    border: '#e5e7eb',
    text: '#93c5fd',
    logoClass: 'provider-logo-azure',
    logoSrc: azureLogo,
    baseUrl: 'https://{resource}.openai.azure.com',
    models: ['gpt-4o', 'gpt-4-turbo', 'gpt-35-turbo'],
    protocolType: 'AZURE_OPENAI',
    apiKeyLabel: 'Azure API Key',
    aliases: ['azure'],
  },
  {
    id: 'xiaomi',
    name: '小米 / MiMo',
    shortName: '小米',
    desc: 'MiMo 推理模型，小米开源系列',
    color: '#ff8f5f',
    bg: '#f8fafc',
    border: '#e5e7eb',
    text: '#94a3b8',
    logoClass: 'provider-logo-xiaomi',
    logoSrc: xiaomiLogo,
    baseUrl: 'https://api.mimo.xiaomi.com/v1',
    models: ['mimo-7b', 'mimo-7b-rl'],
    protocolType: 'OPENAI_COMPATIBLE_CHAT',
    apiKeyLabel: 'API Key',
    aliases: ['xiaomi', 'mimo', '小米'],
  },
  {
    id: 'zhipu',
    name: '智谱 AI',
    shortName: '智谱',
    desc: 'GLM-4、GLM-4-Flash 等系列模型',
    color: '#9fb4ff',
    bg: '#f8fafc',
    border: '#e5e7eb',
    text: '#94a3b8',
    logoClass: 'provider-logo-zhipu',
    logoSrc: zhipuLogo,
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    models: ['glm-4', 'glm-4-flash', 'glm-4-air', 'glm-3-turbo'],
    protocolType: 'OPENAI_COMPATIBLE_CHAT',
    apiKeyLabel: 'API Key',
    aliases: ['zhipu', 'glm', 'bigmodel', '智谱'],
  },
  {
    id: 'kimi',
    name: 'Kimi',
    shortName: 'Kimi',
    desc: 'Moonshot AI，擅长长文本理解',
    color: '#9ca3af',
    bg: '#f8fafc',
    border: '#e5e7eb',
    text: '#94a3b8',
    logoClass: 'provider-logo-kimi',
    logoSrc: kimiLogo,
    baseUrl: 'https://api.moonshot.cn/v1',
    models: ['moonshot-v1-8k', 'moonshot-v1-32k', 'moonshot-v1-128k'],
    protocolType: 'OPENAI_COMPATIBLE_CHAT',
    apiKeyLabel: 'API Key',
    aliases: ['kimi', 'moonshot'],
  },
  {
    id: 'minimax',
    name: 'MiniMax',
    shortName: 'MiniMax',
    desc: 'MiniMax-Text、abab 系列模型',
    color: '#f3a6a6',
    bg: '#f8fafc',
    border: '#e5e7eb',
    text: '#94a3b8',
    logoClass: 'provider-logo-minimax',
    logoSrc: minimaxLogo,
    baseUrl: 'https://api.minimax.chat/v1',
    models: ['abab6.5s-chat', 'abab6.5-chat', 'abab5.5-chat'],
    protocolType: 'OPENAI_COMPATIBLE_CHAT',
    apiKeyLabel: 'API Key',
    aliases: ['minimax', 'abab'],
  },
  {
    id: 'ollama',
    name: 'Ollama',
    shortName: 'Ollama',
    desc: '本地运行的开源大模型',
    color: '#9ca3af',
    bg: '#f8fafc',
    border: '#e5e7eb',
    text: '#94a3b8',
    logoClass: 'provider-logo-ollama',
    logoSrc: ollamaLogo,
    baseUrl: 'http://localhost:11434/v1',
    models: ['llama3', 'mistral', 'codellama', 'qwen2'],
    protocolType: 'OPENAI_COMPATIBLE_CHAT',
    apiKeyLabel: 'API Key（本地可留空）',
    aliases: ['ollama', 'llama', 'mistral'],
  },
  {
    id: 'custom',
    name: '自定义',
    shortName: '自定义',
    desc: '支持所有兼容 OpenAI API 规范的模型提供商',
    color: '#cbd5e1',
    bg: '#f8fafc',
    border: '#e5e7eb',
    text: '#94a3b8',
    logoClass: 'provider-logo-custom',
    logoSrc: customLogo,
    baseUrl: 'https://your-api-endpoint/v1',
    models: [],
    protocolType: 'OPENAI_COMPATIBLE_CHAT',
    apiKeyLabel: 'API Key',
    aliases: ['custom', '自定义'],
  },
]

const providerLoading = ref(false)
const providerDialogVisible = ref(false)
const providerDialogStep = ref<ProviderDialogStep>('provider')
const selectedBrandId = ref('openai')
const savingProvider = ref(false)
const providerTestingId = ref<number | null>(null)
const dialogTesting = ref(false)
const providerDialogModelLoading = ref(false)
const providerDialogModelRequestSeq = ref(0)
const providerDialogModelError = ref('')
const showModelDropdown = ref(false)
const apiKeyVisible = ref(false)
const apiKeySecretLoading = ref(false)
const apiKeyUsingSavedSecret = ref(false)

const providers = ref<AiProviderConnection[]>([])
const providerDialogModels = ref<AiProviderModel[]>([])

const providerDialogForm = reactive<ProviderDialogForm>({
  id: null,
  connectionName: '',
  protocolType: 'OPENAI_COMPATIBLE_CHAT',
  baseUrl: '',
  requestTimeoutSeconds: 180,
  modelName: '',
  apiKey: '',
  status: 1,
})

const defaultSavedApiKeyMaskLength = 16

const selectedBrand = computed(() => providerBrands.find(item => item.id === selectedBrandId.value) ?? providerBrands[0])
const apiKeyInputType = computed(() => (apiKeyVisible.value ? 'text' : 'password'))
const connectedCount = computed(() => providers.value.filter(item => item.status === 1 && item.lastVerifiedAt).length)
const errorCount = computed(() => providers.value.filter(item => item.status === 0).length)

function providerSearchText(provider: AiProviderConnection) {
  return `${provider.connectionName} ${provider.baseUrl} ${provider.modelName ?? ''}`.toLowerCase()
}

function inferProviderBrand(provider: AiProviderConnection) {
  const source = providerSearchText(provider)
  return providerBrands.find(brand => brand.aliases.some(alias => source.includes(alias.toLowerCase())))
    ?? providerBrands.find(brand => brand.id === 'custom')
    ?? providerBrands[0]
}

function providerHasConnection(brand: ProviderBrand) {
  return providers.value.some(provider => inferProviderBrand(provider).id === brand.id)
}

function connectionStatus(provider: AiProviderConnection) {
  if (provider.status === 0) {
    return {
      label: '连接异常',
      className: 'status-error',
      icon: WifiOff,
    }
  }
  if (provider.lastVerifiedAt) {
    return {
      label: '已连接',
      className: 'status-connected',
      icon: Wifi,
    }
  }
  return {
    label: '未测试',
    className: 'status-untested',
    icon: AlertCircle,
  }
}

function formatDate(value: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 10)
}

function buildSavedApiKeyMask(provider: AiProviderConnection) {
  const maskLength = provider.apiKeyMasked?.length || defaultSavedApiKeyMaskLength
  return 'x'.repeat(maskLength)
}

function withTimeout<T>(promise: Promise<T>, timeoutMs: number, message: string) {
  return Promise.race([
    promise,
    new Promise<T>((_, reject) => {
      window.setTimeout(() => reject(new Error(message)), timeoutMs)
    }),
  ])
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

function resetProviderDialog() {
  providerDialogModelRequestSeq.value += 1
  providerDialogModelLoading.value = false
  providerDialogModelError.value = ''
  showModelDropdown.value = false
  dialogTesting.value = false
  apiKeyVisible.value = false
  apiKeySecretLoading.value = false
  apiKeyUsingSavedSecret.value = false
  providerDialogStep.value = 'provider'
  selectedBrandId.value = 'openai'
  providerDialogForm.id = null
  providerDialogForm.connectionName = ''
  providerDialogForm.protocolType = 'OPENAI_COMPATIBLE_CHAT'
  providerDialogForm.baseUrl = ''
  providerDialogForm.requestTimeoutSeconds = 180
  providerDialogForm.modelName = ''
  providerDialogForm.apiKey = ''
  providerDialogForm.status = 1
  providerDialogModels.value = []
}

function applyBrandDefaults(brand: ProviderBrand) {
  selectedBrandId.value = brand.id
  providerDialogForm.connectionName = `${brand.shortName} 连接`
  providerDialogForm.protocolType = brand.protocolType
  providerDialogForm.baseUrl = brand.baseUrl
  providerDialogForm.modelName = brand.models[0] ?? ''
  providerDialogModelError.value = ''
}

function selectBrand(brand: ProviderBrand) {
  applyBrandDefaults(brand)
  providerDialogStep.value = 'config'
}

function openCreateProviderDialog() {
  resetProviderDialog()
  providerDialogVisible.value = true
}

function openEditProviderDialog(provider: AiProviderConnection) {
  resetProviderDialog()
  const brand = inferProviderBrand(provider)
  selectedBrandId.value = brand.id
  providerDialogStep.value = 'config'
  providerDialogForm.id = provider.id
  providerDialogForm.connectionName = provider.connectionName
  providerDialogForm.protocolType = provider.protocolType || brand.protocolType
  providerDialogForm.baseUrl = provider.baseUrl
  providerDialogForm.requestTimeoutSeconds = provider.requestTimeoutSeconds ?? 180
  providerDialogForm.modelName = provider.modelName ?? ''
  providerDialogForm.apiKey = provider.apiKeyConfigured ? buildSavedApiKeyMask(provider) : ''
  apiKeyUsingSavedSecret.value = provider.apiKeyConfigured
  providerDialogForm.status = provider.status
  providerDialogVisible.value = true
}

async function saveProvider() {
  if (!providerDialogForm.connectionName.trim() || !providerDialogForm.baseUrl.trim()) {
    ElMessage.error('请先填写连接名称和 API URL')
    return
  }
  if (!providerDialogForm.modelName.trim()) {
    ElMessage.error('请先选择或填写模型名称')
    return
  }
  if (!providerDialogForm.id && !providerDialogForm.apiKey.trim()) {
    ElMessage.error('新建连接时必须填写 API Key')
    return
  }
  const payload: SaveAiProviderConnectionPayload = {
    connectionName: providerDialogForm.connectionName.trim(),
    baseUrl: providerDialogForm.baseUrl.trim(),
    modelName: providerDialogForm.modelName.trim(),
    apiKey: apiKeyUsingSavedSecret.value ? undefined : providerDialogForm.apiKey.trim() || undefined,
  }
  savingProvider.value = true
  try {
    if (providerDialogForm.id) {
      await platformApi.updateAiProviderConnection('ALL', providerDialogForm.id, payload)
      ElMessage.success('AI 连接已更新')
    } else {
      await platformApi.createAiProviderConnection('ALL', payload)
      ElMessage.success('AI 连接已创建')
    }
    providerDialogVisible.value = false
    await loadProviders()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    savingProvider.value = false
  }
}

function applyDialogModels(models: AiProviderModel[]) {
  providerDialogModels.value = models
  if (!models.length) {
    showModelDropdown.value = false
    return
  }
  const current = providerDialogForm.modelName.trim()
  const matched = models.find(item => item.modelName === current)
  providerDialogForm.modelName = matched?.modelName ?? models[0].modelName
  showModelDropdown.value = true
}

function handleApiKeyInput() {
  if (apiKeyUsingSavedSecret.value) {
    apiKeyUsingSavedSecret.value = false
  }
}

async function toggleApiKeyVisible() {
  if (!providerDialogForm.id) {
    apiKeyVisible.value = !apiKeyVisible.value
    return
  }
  if (apiKeyUsingSavedSecret.value) {
    if (!providerDialogForm.id || apiKeySecretLoading.value) {
      return
    }
    apiKeySecretLoading.value = true
    try {
      const response = await withTimeout(
        platformApi.getAiProviderConnectionSecret('ALL', providerDialogForm.id),
        15000,
        '读取 API Key 超时，请确认后端已重启并包含最新接口',
      )
      providerDialogForm.apiKey = response.apiKey
      apiKeyUsingSavedSecret.value = false
      apiKeyVisible.value = true
    } catch (error) {
      ElMessage.error((error as Error).message)
    } finally {
      apiKeySecretLoading.value = false
    }
    return
  }
  apiKeyVisible.value = !apiKeyVisible.value
}

async function loadDialogModels(provider?: AiProviderConnection) {
  if (providerDialogModelLoading.value) {
    return
  }
  providerDialogModelError.value = ''
  showModelDropdown.value = false
  if (!providerDialogForm.baseUrl.trim() || (!providerDialogForm.id && !providerDialogForm.apiKey.trim())) {
    providerDialogModelError.value = '请先填写 API URL 和 API Key'
    return
  }
  const requestSeq = providerDialogModelRequestSeq.value + 1
  providerDialogModelRequestSeq.value = requestSeq
  providerDialogModelLoading.value = true
  try {
    const response = providerDialogForm.id && (apiKeyUsingSavedSecret.value || !providerDialogForm.apiKey.trim())
      ? await platformApi.fetchAiProviderModels('ALL', providerDialogForm.id)
      : await platformApi.previewAiProviderModels('ALL', {
          baseUrl: providerDialogForm.baseUrl.trim(),
          apiKey: providerDialogForm.apiKey.trim(),
        })
    if (requestSeq !== providerDialogModelRequestSeq.value) {
      return
    }
    applyDialogModels(response.models)
    showModelDropdown.value = response.models.length > 0
  } catch (error) {
    if (requestSeq !== providerDialogModelRequestSeq.value) {
      return
    }
    if (provider?.modelName && !providerDialogForm.modelName.trim()) {
      providerDialogForm.modelName = provider.modelName
    }
    providerDialogModelError.value = (error as Error).message
  } finally {
    if (requestSeq === providerDialogModelRequestSeq.value) {
      providerDialogModelLoading.value = false
    }
  }
}

function isMessageBoxCancel(error: unknown) {
  return error === 'cancel'
    || error === 'close'
    || (error instanceof Error && (error.message === 'cancel' || error.message === 'close'))
}

async function testProvider(provider: AiProviderConnection) {
  providerTestingId.value = provider.id
  try {
    const response = await platformApi.testAiProviderConnection('ALL', provider.id)
    ElMessage.success(response.message || '连接测试成功')
    await loadProviders()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    providerTestingId.value = null
  }
}

async function testDialogProvider() {
  if (!providerDialogForm.id) {
    ElMessage.info('保存连接后即可测试连接状态')
    return
  }
  const provider = providers.value.find(item => item.id === providerDialogForm.id)
  if (!provider) {
    ElMessage.info('请保存后再测试连接')
    return
  }
  dialogTesting.value = true
  try {
    await testProvider(provider)
  } finally {
    dialogTesting.value = false
  }
}

async function deleteProvider(provider: AiProviderConnection) {
  try {
    const usages: string[] = []
    try {
      const aiConfig = await platformApi.getAiCaseConfig('ALL')
      if (aiConfig.generatorConfig?.providerConnectionId === provider.id) {
        usages.push('AI 用例生成')
      }
      if (aiConfig.reviewerConfig?.providerConnectionId === provider.id) {
        usages.push('AI 用例评审')
      }
    } catch (error) {
      ElMessage.warning(`未能确认连接使用情况：${(error as Error).message}`)
    }
    const message = usages.length
      ? `连接“${provider.connectionName}”正在用于：${usages.join('、')}。删除后这些配置将失效，需要重新选择模型。是否确认删除？`
      : `确定删除连接“${provider.connectionName}”吗？`
    await ElMessageBox.confirm(message, '删除 AI 连接', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
    await platformApi.deleteAiProviderConnection('ALL', provider.id)
    ElMessage.success('AI 连接已删除')
    await loadProviders()
  } catch (error) {
    if (!isMessageBoxCancel(error)) {
      ElMessage.error((error as Error).message)
    }
  }
}

onMounted(() => {
  void loadProviders()
})
</script>

<template>
  <div class="ai-pool-page" v-loading="providerLoading">
    <header class="ai-pool-header">
      <div>
        <h2>AI 连接池</h2>
        <p>
          管理接入的 AI 大模型服务，连接池中的模型可在
          <span>用例中心 → AI 配置</span>
          中使用
        </p>
      </div>
      <button type="button" class="primary-add-button" @click="openCreateProviderDialog">
        <Plus :size="16" />
        添加连接
      </button>
    </header>

    <section class="ai-pool-stats">
      <div class="stat-card">
        <span>连接总数</span>
        <strong class="stat-blue">{{ providers.length }}</strong>
      </div>
      <div class="stat-card">
        <span>正常连接</span>
        <strong class="stat-green">{{ connectedCount }}</strong>
      </div>
      <div class="stat-card">
        <span>异常连接</span>
        <strong class="stat-red">{{ errorCount }}</strong>
      </div>
      <div class="stat-card">
        <span>可用供应商</span>
        <strong class="stat-purple">{{ providerBrands.length }}</strong>
      </div>
    </section>

    <section v-if="providers.length" class="connection-grid">
      <article v-for="provider in providers" :key="provider.id" class="connection-card">
        <div class="provider-logo" :class="[inferProviderBrand(provider).logoClass, { 'has-logo-image': inferProviderBrand(provider).logoSrc }]">
          <img v-if="inferProviderBrand(provider).logoSrc" :src="inferProviderBrand(provider).logoSrc" :alt="inferProviderBrand(provider).name" />
          <span>{{ inferProviderBrand(provider).shortName.slice(0, 1) }}</span>
        </div>
        <div class="connection-main">
          <div class="connection-title-row">
            <h3>{{ provider.connectionName }}</h3>
            <span class="connection-status" :class="connectionStatus(provider).className">
              <component :is="connectionStatus(provider).icon" :size="12" />
              {{ connectionStatus(provider).label }}
            </span>
          </div>
          <div class="connection-tags">
            <span
              class="provider-chip"
              :style="{
                backgroundColor: inferProviderBrand(provider).bg,
                color: inferProviderBrand(provider).text,
              }"
            >
              {{ inferProviderBrand(provider).shortName }}
            </span>
            <span class="model-chip">{{ provider.modelName || '-' }}</span>
          </div>
          <div class="connection-url">{{ provider.baseUrl }}</div>
          <div class="connection-date">添加于 {{ formatDate(provider.lastVerifiedAt) }}</div>
        </div>
        <div class="connection-actions">
          <button
            type="button"
            title="测试连接"
            :disabled="providerTestingId === provider.id"
            @click="testProvider(provider)"
          >
            <Wifi :size="16" :class="{ 'is-pulsing': providerTestingId === provider.id }" />
          </button>
          <button type="button" title="编辑" @click="openEditProviderDialog(provider)">
            <Edit2 :size="16" />
          </button>
          <button type="button" title="删除" class="danger-action" @click="deleteProvider(provider)">
            <Trash2 :size="16" />
          </button>
        </div>
      </article>
    </section>

    <section v-else class="empty-connections">
      <div class="empty-icon"><Database :size="32" /></div>
      <strong>暂无连接配置</strong>
      <p>点击右上角「添加连接」开始配置 AI 模型</p>
      <button type="button" class="primary-add-button" @click="openCreateProviderDialog">
        <Plus :size="16" />
        添加第一个连接
      </button>
    </section>

    <section class="supported-providers">
      <h3>支持的供应商</h3>
      <div class="supported-grid">
        <div
          v-for="brand in providerBrands"
          :key="brand.id"
          class="supported-card"
          :class="{ 'has-connection': providerHasConnection(brand) }"
          :style="providerHasConnection(brand) ? { backgroundColor: brand.bg, borderColor: brand.border } : undefined"
        >
          <div class="provider-logo provider-logo-small" :class="[brand.logoClass, { 'has-logo-image': brand.logoSrc }]">
            <img v-if="brand.logoSrc" :src="brand.logoSrc" :alt="brand.name" />
            <span>{{ brand.shortName.slice(0, 1) }}</span>
          </div>
          <span>{{ brand.shortName }}</span>
          <i v-if="providerHasConnection(brand)" />
        </div>
      </div>
    </section>

    <div v-if="providerDialogVisible" class="connection-modal-mask">
      <div class="connection-modal">
        <header class="connection-modal-header">
          <div>
            <h2>{{ providerDialogForm.id ? '编辑连接' : providerDialogStep === 'provider' ? '选择供应商' : '配置连接' }}</h2>
            <button
              v-if="!providerDialogForm.id && providerDialogStep === 'config'"
              type="button"
              class="back-provider-button"
              @click="providerDialogStep = 'provider'"
            >
              <ChevronLeft :size="13" />
              重新选择供应商
            </button>
          </div>
          <button type="button" class="modal-close" @click="providerDialogVisible = false">
            <X :size="18" />
          </button>
        </header>

        <div class="connection-modal-body">
          <section v-if="providerDialogStep === 'provider'" class="provider-select-step">
            <p>选择要接入的 AI 服务供应商</p>
            <div class="provider-select-grid">
              <button
                v-for="brand in providerBrands"
                :key="brand.id"
                type="button"
                class="provider-select-card"
                @click="selectBrand(brand)"
              >
                <div class="provider-logo" :class="[brand.logoClass, { 'has-logo-image': brand.logoSrc }]">
                  <img v-if="brand.logoSrc" :src="brand.logoSrc" :alt="brand.name" />
                  <span>{{ brand.shortName.slice(0, 1) }}</span>
                </div>
                <div>
                  <strong>{{ brand.name }}</strong>
                  <span>{{ brand.desc }}</span>
                </div>
                <ChevronRight class="provider-select-arrow" :size="16" />
              </button>
            </div>
          </section>

          <section v-else class="connection-form-step">
            <div class="selected-provider-card">
              <div class="provider-logo provider-logo-medium" :class="[selectedBrand.logoClass, { 'has-logo-image': selectedBrand.logoSrc }]">
                <img v-if="selectedBrand.logoSrc" :src="selectedBrand.logoSrc" :alt="selectedBrand.name" />
                <span>{{ selectedBrand.shortName.slice(0, 1) }}</span>
              </div>
              <div>
                <strong>{{ selectedBrand.name }}</strong>
                <span>{{ selectedBrand.desc }}</span>
              </div>
            </div>

            <label class="form-field">
              <span>连接名称</span>
              <input v-model="providerDialogForm.connectionName" placeholder="例如：OpenAI 官方 / DeepSeek 代理 / 内网网关" />
            </label>

            <label class="form-field">
              <span class="field-label-row">
                API Url
                <button type="button" @click="providerDialogForm.baseUrl = selectedBrand.baseUrl">恢复默认</button>
              </span>
              <input v-model="providerDialogForm.baseUrl" class="mono-input" :placeholder="selectedBrand.baseUrl" />
              <small>支持自定义代理地址或私有部署地址</small>
            </label>

            <label class="form-field">
              <span>{{ selectedBrand.apiKeyLabel }}</span>
              <div class="api-key-input">
                <input
                  v-model="providerDialogForm.apiKey"
                  :type="apiKeyInputType"
                  class="mono-input"
                  :placeholder="providerDialogForm.id ? '不修改请保留当前密钥' : '请输入 API Key'"
                  @input="handleApiKeyInput"
                />
                <button type="button" :disabled="apiKeySecretLoading" @click="toggleApiKeyVisible">
                  <EyeOff v-if="apiKeyVisible" :size="16" />
                  <Eye v-else :size="16" />
                </button>
              </div>
            </label>

            <label class="form-field">
              <span class="field-label-row">
                模型名称
                <button type="button" class="fetch-model-button" :disabled="providerDialogModelLoading" @click="loadDialogModels()">
                  <RefreshCw v-if="providerDialogModelLoading" :size="13" class="is-spinning" />
                  <Download v-else :size="13" />
                  {{ providerDialogModelLoading ? '获取中...' : '获取模型列表' }}
                </button>
              </span>
              <div class="model-select-wrap">
                <input
                  v-model="providerDialogForm.modelName"
                  class="mono-input"
                  placeholder="例如：gpt-4o、deepseek-chat、qwen-max"
                />
                <button
                  v-if="providerDialogModels.length"
                  type="button"
                  class="model-dropdown-toggle"
                  @click="showModelDropdown = !showModelDropdown"
                >
                  <ChevronRight :size="14" :class="{ 'is-open': showModelDropdown }" />
                </button>
                <div v-if="showModelDropdown && providerDialogModels.length" class="model-dropdown-panel">
                  <div class="model-dropdown-head">
                    <span>共 {{ providerDialogModels.length }} 个模型</span>
                    <span>点击选择</span>
                  </div>
                  <div class="model-dropdown-list">
                    <button
                      v-for="item in providerDialogModels"
                      :key="item.modelName"
                      type="button"
                      :class="{ 'is-selected': item.modelName === providerDialogForm.modelName }"
                      @click="providerDialogForm.modelName = item.modelName; showModelDropdown = false"
                    >
                      <span>{{ item.displayName || item.modelName }}</span>
                      <Check v-if="item.modelName === providerDialogForm.modelName" class="model-dropdown-check" :size="14" />
                    </button>
                  </div>
                </div>
              </div>
              <p v-if="providerDialogModelError" class="model-fetch-message is-error">
                <AlertCircle :size="13" />
                {{ providerDialogModelError }}
              </p>
            </label>
          </section>
        </div>

        <footer v-if="providerDialogStep === 'config'" class="connection-modal-footer">
          <button type="button" class="test-connection-button" :disabled="dialogTesting" @click="testDialogProvider">
            <Wifi :size="16" :class="{ 'is-pulsing': dialogTesting }" />
            {{ dialogTesting ? '测试中...' : '测试连接' }}
          </button>
          <div>
            <button type="button" class="secondary-button" @click="providerDialogVisible = false">取消</button>
            <button type="button" class="save-button" :disabled="savingProvider" @click="saveProvider">
              {{ savingProvider ? '保存中...' : providerDialogForm.id ? '保存修改' : '添加连接' }}
            </button>
          </div>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ai-pool-page {
  min-height: 100%;
  padding: 31px 32px;
  overflow-y: auto;
  background: #f9fafb;
  font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif;
  scrollbar-width: none;
}

.ai-pool-page::-webkit-scrollbar,
.connection-modal-body::-webkit-scrollbar {
  display: none;
}

.ai-pool-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 27px;
}

.ai-pool-header h2 {
  margin: 0;
  color: #111827;
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
}

.ai-pool-header p {
  margin: 2px 0 0;
  color: #6b7280;
  font-size: 14px;
  line-height: 1.6;
}

.ai-pool-header p span {
  color: #2563eb;
}

.primary-add-button,
.save-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 17px;
  border: 0;
  border-radius: 14px;
  color: #fff;
  background: #2563eb;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.18s ease, transform 0.18s ease;
}

.primary-add-button:hover,
.save-button:hover {
  background: #1d4ed8;
}

.ai-pool-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 28px;
}

.stat-card {
  min-height: 86px;
  padding: 17px 20px;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  background: #fff;
}

.stat-card span {
  display: block;
  margin-bottom: 4px;
  color: #6b7280;
  font-size: 12px;
}

.stat-card strong {
  display: block;
  font-size: 28px;
  line-height: 1.1;
}

.stat-blue {
  color: #2563eb;
}

.stat-green {
  color: #16a34a;
}

.stat-red {
  color: #ef4444;
}

.stat-purple {
  color: #7c3aed;
}

.connection-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 13px;
}

.connection-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 16px;
  min-height: 129px;
  padding: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  background: #fff;
  transition: box-shadow 0.18s ease, border-color 0.18s ease;
}

.connection-card:hover {
  border-color: #d1d5db;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
}

.connection-main {
  min-width: 0;
  flex: 1;
}

.connection-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.connection-title-row h3 {
  margin: 0;
  color: #111827;
  font-size: 14px;
  font-weight: 700;
}

.connection-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border: 1px solid;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1.2;
}

.status-connected {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #16a34a;
}

.status-error {
  border-color: #fecaca;
  background: #fef2f2;
  color: #ef4444;
}

.status-untested {
  border-color: #e5e7eb;
  background: #f9fafb;
  color: #9ca3af;
}

.connection-tags {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.provider-chip,
.model-chip {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  min-height: 20px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1.3;
}

.model-chip {
  border-radius: 8px;
  background: #f9fafb;
  color: #4b5563;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.connection-url {
  margin-top: 8px;
  overflow: hidden;
  color: #9ca3af;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.connection-date {
  margin-top: 2px;
  color: #9ca3af;
  font-size: 12px;
}

.connection-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  opacity: 0;
  transition: opacity 0.18s ease;
}

.connection-card:hover .connection-actions {
  opacity: 1;
}

.connection-actions button,
.modal-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  transition: background-color 0.18s ease, color 0.18s ease;
}

.connection-actions button:hover {
  background: #eff6ff;
  color: #2563eb;
}

.connection-actions .danger-action:hover {
  background: #fef2f2;
  color: #ef4444;
}

.provider-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  flex: 0 0 48px;
  overflow: hidden;
  border-radius: 14px;
  color: #fff;
  font-size: 20px;
  font-weight: 800;
  box-shadow: 0 2px 7px rgba(15, 23, 42, 0.12);
}

.provider-logo img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.provider-logo img + span {
  display: none;
}

.provider-logo.has-logo-image {
  background: transparent;
  box-shadow: none;
}

.provider-logo-small {
  width: 40px;
  height: 40px;
  flex-basis: 40px;
  border-radius: 12px;
  font-size: 16px;
}

.provider-logo-medium {
  width: 40px;
  height: 40px;
  flex-basis: 40px;
  border-radius: 12px;
}

.provider-logo-openai {
  background: #000;
}

.provider-logo-openai span {
  font-size: 0;
}

.provider-logo-openai::before {
  width: 24px;
  height: 24px;
  content: '';
  background:
    radial-gradient(circle at 50% 12%, #fff 0 12%, transparent 13%),
    radial-gradient(circle at 82% 32%, #fff 0 12%, transparent 13%),
    radial-gradient(circle at 82% 70%, #fff 0 12%, transparent 13%),
    radial-gradient(circle at 50% 88%, #fff 0 12%, transparent 13%),
    radial-gradient(circle at 18% 70%, #fff 0 12%, transparent 13%),
    radial-gradient(circle at 18% 32%, #fff 0 12%, transparent 13%);
}

.provider-logo-anthropic {
  background: #c68642;
  font-family: Georgia, serif;
  font-size: 28px;
}

.provider-logo-google {
  background: #fff;
  color: #c7d2fe;
}

.provider-logo-deepseek {
  background: #1c3ef0;
}

.provider-logo-qwen {
  background: #ffc29b;
}

.provider-logo-azure {
  background: #9fc9ea;
}

.provider-logo-xiaomi {
  background: #ffc29b;
}

.provider-logo-zhipu {
  background: #c7d2fe;
}

.provider-logo-kimi {
  background: #9ca3af;
}

.provider-logo-minimax {
  background: #f3a6a6;
}

.provider-logo-ollama {
  background: #a3a3a3;
}

.provider-logo-custom {
  background: #f1f5f9;
  color: #cbd5e1;
}

.provider-logo.has-logo-image {
  background: transparent;
  color: inherit;
}

.provider-logo-kimi.has-logo-image {
  padding: 9px;
  background: #000;
  box-shadow: 0 2px 7px rgba(15, 23, 42, 0.12);
}

.provider-logo.has-logo-image::before {
  display: none;
}

.empty-connections {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 260px;
  text-align: center;
}

.empty-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  margin-bottom: 16px;
  border-radius: 18px;
  background: #f3f4f6;
  color: #9ca3af;
}

.empty-connections strong {
  color: #4b5563;
  font-size: 14px;
}

.empty-connections p {
  margin: 6px 0 16px;
  color: #9ca3af;
  font-size: 12px;
}

.supported-providers {
  margin-top: 35px;
}

.supported-providers h3 {
  margin: 0 0 13px;
  color: #374151;
  font-size: 14px;
  font-weight: 600;
}

.supported-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.supported-card {
  display: flex;
  min-height: 103px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #f9fafb;
  color: #9ca3af;
  font-size: 12px;
}

.supported-card:not(.has-connection) .provider-logo {
  opacity: 0.35;
}

.supported-card.has-connection {
  color: #374151;
  font-weight: 600;
}

.supported-card i {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: #22c55e;
}

.connection-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(15, 23, 42, 0.32);
  backdrop-filter: blur(6px);
}

.connection-modal {
  display: flex;
  width: min(672px, 100%);
  max-height: 90vh;
  flex-direction: column;
  overflow: hidden;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 28px 80px rgba(15, 23, 42, 0.28);
}

.connection-modal-header,
.connection-modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  gap: 16px;
  border-color: #f3f4f6;
}

.connection-modal-header {
  padding: 20px 24px 16px;
  border-bottom: 1px solid #f3f4f6;
}

.connection-modal-header h2 {
  margin: 0;
  color: #111827;
  font-size: 16px;
  font-weight: 600;
}

.modal-close:hover {
  background: #f3f4f6;
  color: #4b5563;
}

.modal-close {
  width: 28px;
  height: 28px;
  border-radius: 8px;
}

.back-provider-button,
.field-label-row button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #2563eb;
  font-size: 12px;
  cursor: pointer;
}

.fetch-model-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  padding: 5px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: #6b7280;
  font-size: 12px;
  line-height: 16px;
  cursor: pointer;
  transition: border-color 0.18s ease, color 0.18s ease, background-color 0.18s ease;
}

.fetch-model-button:hover:not(:disabled) {
  border-color: #93c5fd;
  background: #f8fbff;
  color: #3b82f6;
}

.fetch-model-button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.back-provider-button {
  margin-top: 2px;
}

.connection-modal-body {
  flex: 1;
  overflow-y: auto;
  scrollbar-width: none;
}

.provider-select-step,
.connection-form-step {
  padding: 24px;
}

.provider-select-step > p {
  margin: 0 0 20px;
  color: #6b7280;
  font-size: 14px;
}

.provider-select-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.provider-select-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 82px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, background-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.provider-select-card:hover {
  border-color: #93c5fd;
  background: #f8fbff;
  transform: translateY(-1px);
  box-shadow: 0 14px 34px rgba(37, 99, 235, 0.1);
}

.provider-select-card:hover .provider-select-arrow {
  color: #2563eb;
}

.provider-select-card strong,
.selected-provider-card strong {
  display: block;
  color: #111827;
  font-size: 14px;
}

.provider-select-card span,
.selected-provider-card span {
  display: block;
  margin-top: 2px;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.4;
}

.provider-select-card > div:nth-child(2) {
  min-width: 0;
  flex: 1;
}

.provider-select-arrow {
  flex: 0 0 auto;
  margin-left: auto;
  color: #9ca3af;
  transition: color 0.18s ease, transform 0.18s ease;
}

.provider-select-card:hover .provider-select-arrow {
  transform: translateX(2px);
}

.connection-form-step {
  display: grid;
  gap: 20px;
}

.selected-provider-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #f8fbff;
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.03);
}

.form-field {
  display: grid;
  gap: 6px;
}

.form-field > span,
.field-label-row {
  color: #374151;
  font-size: 14px;
  font-weight: 500;
}

.field-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.form-field input,
.form-field select {
  width: 100%;
  min-height: 42px;
  padding: 9px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
  color: #111827;
  font-size: 14px;
  outline: none;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.form-field input:focus,
.form-field select:focus {
  border-color: #60a5fa;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.form-field small {
  color: #9ca3af;
  font-size: 12px;
}

.mono-input {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.api-key-input,
.model-select-wrap {
  position: relative;
}

.api-key-input input {
  padding-right: 42px;
}

.api-key-input button {
  position: absolute;
  right: 12px;
  top: 50%;
  color: #9ca3af;
  transform: translateY(-50%);
}

.api-key-input button {
  display: inline-flex;
  width: 26px;
  height: 26px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
}

.model-select-wrap input {
  padding-right: 42px;
}

.model-dropdown-toggle {
  position: absolute;
  right: 10px;
  top: 50%;
  display: inline-flex;
  width: 26px;
  height: 26px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 8px;
  background: #f3f4f6;
  color: #6b7280;
  cursor: pointer;
  transform: translateY(-50%);
  transition: background-color 0.18s ease;
}

.model-dropdown-toggle:hover {
  background: #e5e7eb;
}

.model-dropdown-toggle svg {
  transition: transform 0.18s ease;
  transform: rotate(90deg);
}

.model-dropdown-toggle svg.is-open {
  transform: rotate(-90deg);
}

.model-dropdown-panel {
  position: absolute;
  z-index: 10;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.16);
}

.model-dropdown-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #f3f4f6;
  color: #9ca3af;
  font-size: 12px;
}

.model-dropdown-head span:last-child {
  color: #2563eb;
}

.model-dropdown-list {
  max-height: 192px;
  overflow-y: auto;
  scrollbar-width: none;
}

.model-dropdown-list::-webkit-scrollbar {
  display: none;
}

.model-dropdown-list button {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 40px;
  padding: 10px 12px;
  border: 0;
  background: #fff;
  color: #374151;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 14px;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.model-dropdown-list button:hover,
.model-dropdown-list button.is-selected {
  background: #eff6ff;
  color: #1d4ed8;
}

.model-dropdown-list span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-dropdown-check {
  flex: 0 0 auto;
  color: #1d4ed8;
}

.model-fetch-message {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin: 8px 0 0;
  color: #2563eb;
  font-size: 12px;
  line-height: 1.45;
}

.model-fetch-message svg {
  flex: 0 0 auto;
  margin-top: 1px;
}

.model-fetch-message.is-error {
  color: #ef4444;
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.connection-modal-footer {
  padding: 16px 24px;
  border-top: 1px solid #f3f4f6;
}

.connection-modal-footer > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.test-connection-button,
.secondary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 36px;
  padding: 7px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
  color: #4b5563;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.save-button {
  min-height: 36px;
  padding: 7px 20px;
  border-radius: 12px;
}

.test-connection-button:hover,
.secondary-button:hover {
  background: #f9fafb;
}

.is-pulsing {
  animation: pulse 1s ease-in-out infinite;
}

.is-spinning {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.45;
  }
}

@media (max-width: 1280px) {
  .supported-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 920px) {
  .ai-pool-page {
    padding: 20px;
  }

  .ai-pool-header,
  .connection-modal-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .ai-pool-stats,
  .connection-grid,
  .provider-select-grid,
  .form-row {
    grid-template-columns: 1fr;
  }

  .supported-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .connection-actions {
    opacity: 1;
  }
}
</style>
