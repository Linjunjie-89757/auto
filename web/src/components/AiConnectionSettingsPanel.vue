<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ListToolbar from './ListToolbar.vue'
import { platformApi } from '../api/platform'
import type {
  AiProtocolType,
  AiProviderConnection,
  AiProviderModel,
  SaveAiProviderConnectionPayload,
} from '../types/api'

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

const protocolOptions: Array<{ label: string; value: AiProtocolType }> = [
  { label: 'OpenAI Compatible Chat', value: 'OPENAI_COMPATIBLE_CHAT' },
  { label: 'OpenAI Compatible Responses', value: 'OPENAI_COMPATIBLE_RESPONSES' },
  { label: 'Azure OpenAI', value: 'AZURE_OPENAI' },
]

const providerLoading = ref(false)
const providerDialogVisible = ref(false)
const savingProvider = ref(false)
const providerTestingId = ref<number | null>(null)
const providerFetchingId = ref<number | null>(null)
const providerDialogModelLoading = ref(false)
const providerDialogModelRequestSeq = ref(0)

const providers = ref<AiProviderConnection[]>([])
const providerDialogModels = ref<AiProviderModel[]>([])

const filters = reactive({
  keyword: '',
  protocolType: '',
  status: '',
})

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

const filteredProviders = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return providers.value.filter((item) => {
    const matchKeyword = !keyword
      || item.connectionName.toLowerCase().includes(keyword)
      || item.baseUrl.toLowerCase().includes(keyword)
      || item.protocolType.toLowerCase().includes(keyword)
    const matchProtocol = !filters.protocolType || item.protocolType === filters.protocolType
    const matchStatus = !filters.status || String(item.status) === filters.status
    return matchKeyword && matchProtocol && matchStatus
  })
})

function resetProviderDialog() {
  providerDialogModelRequestSeq.value += 1
  providerDialogModelLoading.value = false
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

function resetFilters() {
  filters.keyword = ''
  filters.protocolType = ''
  filters.status = ''
}

function statusText(status: number) {
  return status === 1 ? '启用中' : '已停用'
}

function formatTime(value: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function formatRequestTimeout(value: number | null) {
  return value == null ? '系统默认' : `${value} 秒`
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

function openCreateProviderDialog() {
  resetProviderDialog()
  providerDialogVisible.value = true
}

function openEditProviderDialog(provider: AiProviderConnection) {
  resetProviderDialog()
  providerDialogForm.id = provider.id
  providerDialogForm.connectionName = provider.connectionName
  providerDialogForm.protocolType = provider.protocolType
  providerDialogForm.baseUrl = provider.baseUrl
  providerDialogForm.requestTimeoutSeconds = provider.requestTimeoutSeconds ?? 180
  providerDialogForm.modelName = provider.modelName ?? ''
  providerDialogForm.apiKey = ''
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
    protocolType: providerDialogForm.protocolType,
    baseUrl: providerDialogForm.baseUrl.trim(),
    requestTimeoutSeconds: providerDialogForm.requestTimeoutSeconds,
    modelName: providerDialogForm.modelName.trim(),
    apiKey: providerDialogForm.apiKey.trim() || undefined,
    status: providerDialogForm.status,
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
    return
  }
  const current = providerDialogForm.modelName.trim()
  const matched = models.find(item => item.modelName === current)
  providerDialogForm.modelName = matched?.modelName ?? models[0].modelName
}

async function loadDialogModels(provider?: AiProviderConnection) {
  if (providerDialogModelLoading.value) {
    return
  }
  if (!providerDialogForm.baseUrl.trim() || !providerDialogForm.apiKey.trim()) {
    ElMessage.error('请先填写 API URL 和 API Key')
    return
  }
  const requestSeq = providerDialogModelRequestSeq.value + 1
  providerDialogModelRequestSeq.value = requestSeq
  providerDialogModelLoading.value = true
  try {
    const response = await platformApi.previewAiProviderModels('ALL', {
      protocolType: providerDialogForm.protocolType,
      baseUrl: providerDialogForm.baseUrl.trim(),
      requestTimeoutSeconds: providerDialogForm.requestTimeoutSeconds,
      apiKey: providerDialogForm.apiKey.trim(),
    })
    if (requestSeq !== providerDialogModelRequestSeq.value) {
      return
    }
    applyDialogModels(response.models)
    ElMessage.success(response.message || `已获取到 ${response.models.length} 个模型`)
  } catch (error) {
    if (requestSeq !== providerDialogModelRequestSeq.value) {
      return
    }
    if (provider?.modelName && !providerDialogForm.modelName.trim()) {
      providerDialogForm.modelName = provider.modelName
    }
    ElMessage.error((error as Error).message)
  } finally {
    if (requestSeq === providerDialogModelRequestSeq.value) {
      providerDialogModelLoading.value = false
    }
  }
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

async function fetchModelsForProvider(provider: AiProviderConnection) {
  providerFetchingId.value = provider.id
  try {
    const response = await platformApi.fetchAiProviderModels('ALL', provider.id)
    ElMessage.success(response.message || '模型列表已刷新')
    await loadProviders()
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    providerFetchingId.value = null
  }
}

async function deleteProvider(provider: AiProviderConnection) {
  try {
    await ElMessageBox.confirm(`确定删除连接“${provider.connectionName}”吗？`, '删除 AI 连接', {
      type: 'warning',
    })
    await platformApi.deleteAiProviderConnection('ALL', provider.id)
    ElMessage.success('AI 连接已删除')
    await loadProviders()
  } catch (error) {
    if ((error as Error).message !== 'cancel') {
      ElMessage.error((error as Error).message)
    }
  }
}

onMounted(() => {
  void loadProviders()
})
</script>

<template>
  <div class="ai-connection-panel">
    <ListToolbar title="AI连接管理">
      <template #filters>
        <el-input v-model="filters.keyword" placeholder="搜索连接名称 / 协议 / API URL" clearable class="toolbar-filter-input" />
        <el-select v-model="filters.protocolType" placeholder="协议类型" clearable class="toolbar-filter-select">
          <el-option v-for="option in protocolOptions" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable class="toolbar-filter-select">
          <el-option label="启用" value="1" />
          <el-option label="停用" value="0" />
        </el-select>
        <el-button text @click="resetFilters">
          <el-icon><RefreshRight /></el-icon>
          重置
        </el-button>
      </template>
      <template #actions>
        <el-button type="primary" @click="openCreateProviderDialog">
          <el-icon><Plus /></el-icon>
          新建连接
        </el-button>
      </template>
    </ListToolbar>

    <el-table :data="filteredProviders" v-loading="providerLoading" size="large">
      <el-table-column prop="connectionName" label="连接名称" min-width="180" />
      <el-table-column prop="protocolType" label="协议类型" min-width="220" />
      <el-table-column prop="baseUrl" label="API URL" min-width="280" />
      <el-table-column label="请求超时" width="120">
        <template #default="{ row }">{{ formatRequestTimeout(row.requestTimeoutSeconds) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <span class="status-pill" :class="row.status === 1 ? 'status-success' : 'status-neutral'">
            {{ statusText(row.status) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="modelCount" label="缓存模型数" width="110" />
      <el-table-column label="最近验证" min-width="160">
        <template #default="{ row }">{{ formatTime(row.lastVerifiedAt) }}</template>
      </el-table-column>
      <el-table-column label="最近拉取模型" min-width="180">
        <template #default="{ row }">{{ formatTime(row.lastFetchModelsAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button text type="primary" @click="openEditProviderDialog(row)">编辑</el-button>
            <el-button text :loading="providerTestingId === row.id" @click="testProvider(row)">测试连接</el-button>
            <el-button text :loading="providerFetchingId === row.id" @click="fetchModelsForProvider(row)">获取模型</el-button>
            <el-button text type="danger" @click="deleteProvider(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="providerDialogVisible"
      :title="providerDialogForm.id ? '编辑 AI 连接' : '新建 AI 连接'"
      width="640px"
      destroy-on-close
      @closed="resetProviderDialog"
    >
      <el-form label-width="96px">
        <el-form-item label="连接名称">
          <el-input v-model="providerDialogForm.connectionName" placeholder="例如：OpenAI 官方 / DeepSeek 代理 / 内网网关" />
        </el-form-item>
        <el-form-item label="协议类型">
          <el-select v-model="providerDialogForm.protocolType">
            <el-option v-for="option in protocolOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="API URL">
          <el-input v-model="providerDialogForm.baseUrl" placeholder="https://your-api-endpoint.com/v1" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="providerDialogForm.apiKey"
            type="password"
            show-password
            :placeholder="providerDialogForm.id ? '留空表示继续使用当前已保存的 API Key' : '请输入 API Key'"
          />
        </el-form-item>
        <el-form-item label="模型名称">
          <div class="field-stack">
            <div class="inline-row">
              <el-select
                v-model="providerDialogForm.modelName"
                filterable
                allow-create
                default-first-option
                clearable
                placeholder="先获取模型列表，或直接手动输入模型名称"
                class="flex-select"
              >
                <el-option
                  v-for="item in providerDialogModels"
                  :key="item.modelName"
                  :label="item.displayName || item.modelName"
                  :value="item.modelName"
                />
              </el-select>
              <el-button :loading="providerDialogModelLoading" @click="loadDialogModels()">
                <el-icon><RefreshRight /></el-icon>
                获取模型列表
              </el-button>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="请求超时">
          <el-input-number
            v-model="providerDialogForm.requestTimeoutSeconds"
            :min="10"
            :max="600"
            :step="10"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="状态">
          <div class="status-toggle-row">
            <el-switch v-model="providerDialogForm.status" :active-value="1" :inactive-value="0" />
            <span class="status-toggle-text">{{ statusText(providerDialogForm.status) }}</span>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="providerDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="savingProvider" @click="saveProvider">保存连接</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ai-connection-panel {
  display: grid;
  gap: 12px;
}

.toolbar-filter-input {
  width: 320px;
}

.toolbar-filter-select {
  width: 220px;
}

.field-stack {
  display: grid;
  gap: 6px;
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

.table-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 64px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1.2;
}

.status-success {
  background: #ecfdf3;
  color: #047857;
}

.status-neutral {
  background: #f3f4f6;
  color: #4b5563;
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

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 900px) {
  .inline-row {
    flex-direction: column;
  }
}
</style>
