<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowDown, ArrowUp, Delete, MagicStick, MoreFilled } from '@element-plus/icons-vue'
import ApiFastExtractionDrawer from './ApiFastExtractionDrawer.vue'
import MonacoCodeEditor from './MonacoCodeEditor.vue'
import type {
  ApiExtractProcessorConfig,
  ApiProcessorConfig,
  ApiProcessorExtractorConfig,
  ApiResponseSnapshot,
  ApiSqlProcessorConfig,
  DbConnectionItem,
} from '../types/api'
import type { FastExtractionConfig } from '../utils/apiFastExtraction'

type ExtractorScope = NonNullable<ApiProcessorExtractorConfig['extractScope']>
type ExtractorType = NonNullable<ApiProcessorExtractorConfig['extractType']>
const props = defineProps<{
  modelValue: ApiProcessorConfig[]
  stage: 'pre' | 'post'
  dbConnections?: DbConnectionItem[]
  latestResponse?: ApiResponseSnapshot | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: ApiProcessorConfig[]]
}>()

const processors = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const activeProcessorId = defineModel<string | null>('activeId', { default: null })

const PROCESSOR_LABELS: Record<ApiProcessorConfig['processorType'], string> = {
  SCRIPT: '脚本处理器',
  SQL: 'SQL 处理器',
  TIME_WAITING: '等待处理器',
  EXTRACT: '提取处理器',
}

const processorOptions = computed(() => (
  props.stage === 'pre'
    ? [
        { label: '脚本', value: 'SCRIPT' },
        { label: 'SQL', value: 'SQL' },
        { label: '等待', value: 'TIME_WAITING' },
      ]
    : [
        { label: '脚本', value: 'SCRIPT' },
        { label: 'SQL', value: 'SQL' },
        { label: '提取', value: 'EXTRACT' },
        { label: '等待', value: 'TIME_WAITING' },
      ]
))

const activeProcessor = computed(() => processors.value.find(item => item.id === activeProcessorId.value) ?? null)
const activeProcessorKey = computed(() => activeProcessor.value?.id ?? '')
const enabledDbConnections = computed(() => (props.dbConnections ?? []).filter(item => item.status === 1))
const hasResponseBody = computed(() => !!props.latestResponse?.body?.trim())

const moreSettingsVisibleKey = ref<string | null>(null)
const fastExtractionVisible = ref(false)
const activeExtractorTarget = ref<{ processorId: string, index: number } | null>(null)

const regexScopeOptions: Array<{ label: string, value: ExtractorScope }> = [
  { label: '响应体', value: 'BODY' },
  { label: '响应头', value: 'RESPONSE_HEADERS' },
  { label: '请求头', value: 'REQUEST_HEADERS' },
  { label: '状态码', value: 'RESPONSE_CODE' },
  { label: '响应消息', value: 'RESPONSE_MESSAGE' },
  { label: 'URL', value: 'URL' },
]

const bodyOnlyScopeOptions: Array<{ label: string, value: ExtractorScope }> = [
  { label: '响应体', value: 'BODY' },
]

watch(processors, (value) => {
  if (!value.length) {
    activeProcessorId.value = null
    return
  }
  if (!activeProcessorId.value || !value.some(item => item.id === activeProcessorId.value)) {
    activeProcessorId.value = value[0].id
  }
}, { deep: true, immediate: true })

function updateProcessors(next: ApiProcessorConfig[]) {
  processors.value = next
}

function cloneProcessorConfig(processor: ApiProcessorConfig): ApiProcessorConfig {
  return JSON.parse(JSON.stringify(processor)) as ApiProcessorConfig
}

function createProcessor(type: ApiProcessorConfig['processorType']): ApiProcessorConfig {
  const id = `${props.stage}-${type.toLowerCase()}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  if (type === 'SCRIPT') {
    return {
      id,
      processorType: 'SCRIPT',
      name: props.stage === 'pre' ? '前置脚本' : '后置脚本',
      enabled: true,
      scriptLanguage: 'JAVASCRIPT',
      script: '',
    }
  }
  if (type === 'SQL') {
    return {
      id,
      processorType: 'SQL',
      name: 'SQL',
      enabled: true,
      script: '',
      dataSourceId: null,
      dataSourceName: '',
      queryTimeout: 30000,
      variableNames: '',
      extractParams: [],
      resultVariable: '',
    }
  }
  if (type === 'TIME_WAITING') {
    return {
      id,
      processorType: 'TIME_WAITING',
      name: '等待',
      enabled: true,
      delayMs: 1000,
    }
  }
  return {
    id,
    processorType: 'EXTRACT',
    name: '提取',
    enabled: true,
    extractors: [emptyExtractor()],
  }
}

function emptyExtractor(): ApiProcessorExtractorConfig {
  return {
    name: '',
    variableName: '',
    description: '',
    variableType: 'TEMPORARY',
    extractType: 'JSON_PATH',
    extractScope: 'BODY',
    expression: '',
    expressionMatchingRule: 'EXPRESSION',
    resultMatchingRule: 'RANDOM',
    resultMatchingRuleNum: 1,
    responseFormat: 'JSON',
    enabled: true,
  }
}

function processorTypeLabel(type: ApiProcessorConfig['processorType']) {
  return PROCESSOR_LABELS[type] || type
}

function displayProcessorName(processor: ApiProcessorConfig) {
  const trimmed = (processor.name || '').trim()
  if (processor.processorType === 'SCRIPT' && (trimmed === 'Pre Script' || trimmed === 'Post Script')) {
    return props.stage === 'pre' ? '前置脚本' : '后置脚本'
  }
  if (processor.processorType === 'TIME_WAITING' && trimmed === 'Wait') {
    return '等待'
  }
  if (processor.processorType === 'EXTRACT' && trimmed === 'Extract') {
    return '提取'
  }
  return trimmed || processorTypeLabel(processor.processorType)
}

function addProcessor(type: ApiProcessorConfig['processorType']) {
  const next = [...processors.value, createProcessor(type)]
  updateProcessors(next)
  activeProcessorId.value = next[next.length - 1].id
}

function handleAddProcessorCommand(command: string | number | object) {
  addProcessor(command as ApiProcessorConfig['processorType'])
}

function duplicateProcessor(id: string) {
  const index = processors.value.findIndex(item => item.id === id)
  if (index < 0) {
    return
  }
  const copy = cloneProcessorConfig(processors.value[index])
  copy.id = `${copy.id}-copy-${Math.random().toString(36).slice(2, 8)}`
  copy.name = `${displayProcessorName(copy)} 副本`
  const next = [...processors.value]
  next.splice(index + 1, 0, copy)
  updateProcessors(next)
  activeProcessorId.value = copy.id
}

function removeProcessor(id: string) {
  const next = processors.value.filter(item => item.id !== id)
  updateProcessors(next)
  if (activeProcessorId.value === id) {
    activeProcessorId.value = next[0]?.id ?? null
  }
}

function moveProcessor(id: string, delta: number) {
  const index = processors.value.findIndex(item => item.id === id)
  const target = index + delta
  if (index < 0 || target < 0 || target >= processors.value.length) {
    return
  }
  const next = [...processors.value]
  const [item] = next.splice(index, 1)
  next.splice(target, 0, item)
  updateProcessors(next)
}

function clearScript(processor: { script: string }) {
  processor.script = ''
}

function formatScript(processor: { script: string }) {
  processor.script = processor.script.trim()
}

function syncDataSourceName(processor: ApiSqlProcessorConfig) {
  const selected = enabledDbConnections.value.find(item => item.id === processor.dataSourceId)
  processor.dataSourceName = selected?.connectionName ?? ''
}

function addSqlExtractParam(processor: ApiSqlProcessorConfig) {
  processor.extractParams = [...(processor.extractParams ?? []), { key: '', value: '', enabled: true }]
}

function removeSqlExtractParam(processor: ApiSqlProcessorConfig, index: number) {
  processor.extractParams = (processor.extractParams ?? []).filter((_, currentIndex) => currentIndex !== index)
}

function addExtractorRow(processor: ApiExtractProcessorConfig) {
  processor.extractors.push(emptyExtractor())
}

function duplicateExtractorRow(processor: ApiExtractProcessorConfig, index: number) {
  const source = processor.extractors[index]
  if (!source) {
    return
  }
  processor.extractors.splice(index + 1, 0, JSON.parse(JSON.stringify(source)) as ApiProcessorExtractorConfig)
}

function removeExtractorRow(processor: ApiExtractProcessorConfig, index: number) {
  processor.extractors.splice(index, 1)
  if (!processor.extractors.length) {
    processor.extractors.push(emptyExtractor())
  }
}

function copyExtractorRow(processor: ApiExtractProcessorConfig, index: number) {
  duplicateExtractorRow(processor, index)
}

function normalizedExtractorType(extractor: ApiProcessorExtractorConfig): ExtractorType {
  return extractor.extractType || 'JSON_PATH'
}

function extractScopeOptions(extractor: ApiProcessorExtractorConfig) {
  return normalizedExtractorType(extractor) === 'REGEX' ? regexScopeOptions : bodyOnlyScopeOptions
}

function defaultScopeByType(type: ExtractorType): ExtractorScope {
  return type === 'REGEX' ? 'BODY' : 'BODY'
}

function normalizeExtractorByType(extractor: ApiProcessorExtractorConfig) {
  const type = normalizedExtractorType(extractor)
  const scopes = extractScopeOptions(extractor)
  if (!scopes.some(item => item.value === extractor.extractScope)) {
    extractor.extractScope = defaultScopeByType(type)
  }
  if (type !== 'REGEX') {
    extractor.expressionMatchingRule = 'EXPRESSION'
  }
  if (type === 'JSON_PATH') {
    extractor.responseFormat = 'JSON'
  }
  else if (type === 'X_PATH') {
    extractor.responseFormat = extractor.responseFormat === 'HTML' ? 'HTML' : 'XML'
  }
  else if (!extractor.responseFormat) {
    extractor.responseFormat = 'JSON'
  }
}

function handleExtractorTypeChange(extractor: ApiProcessorExtractorConfig) {
  normalizeExtractorByType(extractor)
}

function showRegexSettings(extractor: ApiProcessorExtractorConfig) {
  return normalizedExtractorType(extractor) === 'REGEX'
}

function showXPathSettings(extractor: ApiProcessorExtractorConfig) {
  return normalizedExtractorType(extractor) === 'X_PATH'
}

function showSpecificResultIndex(extractor: ApiProcessorExtractorConfig) {
  return (extractor.resultMatchingRule || 'RANDOM') === 'SPECIFIC'
}

function extractorExpressionPlaceholder(extractor: ApiProcessorExtractorConfig) {
  const type = normalizedExtractorType(extractor)
  if (type === 'JSON_PATH') {
    return '例如 $.data.token'
  }
  if (type === 'X_PATH') {
    return '例如 /response/data/token'
  }
  return '例如 "token":"([^"]+)"'
}

function openFastExtraction(processor: ApiExtractProcessorConfig, index: number) {
  if (!hasResponseBody.value) {
    return
  }
  activeExtractorTarget.value = { processorId: processor.id, index }
  fastExtractionVisible.value = true
}

const activeFastExtractionConfig = computed<FastExtractionConfig>(() => {
  const target = activeExtractorTarget.value
  if (!target) {
    return emptyExtractor()
  }
  const processor = processors.value.find(item => item.id === target.processorId)
  if (!processor || processor.processorType !== 'EXTRACT') {
    return emptyExtractor()
  }
  return processor.extractors[target.index] || emptyExtractor()
})

const activeFastExtractionMode = computed(() => activeFastExtractionConfig.value.extractType || 'JSON_PATH')
const fastExtractionTitle = computed(() => (hasResponseBody.value ? '快速提取' : '请先发送获取响应内容'))

function handleFastExtractionApply(config: FastExtractionConfig) {
  const target = activeExtractorTarget.value
  if (!target) {
    return
  }
  const processor = processors.value.find(item => item.id === target.processorId)
  if (!processor || processor.processorType !== 'EXTRACT') {
    return
  }
  const extractor = processor.extractors[target.index]
  if (!extractor) {
    return
  }
  extractor.expression = config.expression || extractor.expression
  extractor.extractType = config.extractType || extractor.extractType
  extractor.expressionMatchingRule = config.expressionMatchingRule || extractor.expressionMatchingRule
  extractor.responseFormat = config.responseFormat || extractor.responseFormat
  normalizeExtractorByType(extractor)
  fastExtractionVisible.value = false
}

function setMoreSettingsVisible(processorId: string, index: number, visible: boolean) {
  moreSettingsVisibleKey.value = visible ? `${processorId}-${index}` : null
}
</script>

<template>
  <div class="processor-editor">
    <aside class="processor-sidebar">
      <div class="processor-toolbar">
        <el-dropdown @command="handleAddProcessorCommand">
          <el-button type="primary" plain>添加</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item
                v-for="option in processorOptions"
                :key="option.value"
                :command="option.value"
                :disabled="option.value === 'EXTRACT' && processors.some(item => item.processorType === 'EXTRACT')"
              >
                {{ option.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <div v-if="processors.length" class="processor-list">
        <button
          v-for="(item, index) in processors"
          :key="item.id"
          type="button"
          :class="['processor-list-item', { active: item.id === activeProcessorId }]"
          @click="activeProcessorId = item.id"
        >
          <div class="processor-list-item-main">
            <el-switch v-model="item.enabled" size="small" @click.stop />
            <div class="processor-list-copy">
              <div class="processor-list-title">{{ displayProcessorName(item) }}</div>
              <div class="processor-list-meta">{{ processorTypeLabel(item.processorType) }}</div>
            </div>
          </div>
          <div class="processor-list-actions">
            <el-button text :icon="ArrowUp" :disabled="index === 0" @click.stop="moveProcessor(item.id, -1)" />
            <el-button text :icon="ArrowDown" :disabled="index === processors.length - 1" @click.stop="moveProcessor(item.id, 1)" />
          </div>
        </button>
      </div>
      <div v-else class="processor-empty">暂无处理器</div>
    </aside>

    <section class="processor-detail" :data-testid="`processor-detail-${props.stage}`">
      <template v-if="activeProcessor">
        <div class="processor-detail-header">
          <div class="processor-detail-fields">
            <el-input
              v-model="activeProcessor.name"
              placeholder="处理器名称"
              :data-testid="`processor-name-${props.stage}`"
            />
            <el-tag size="small" effect="plain">{{ processorTypeLabel(activeProcessor.processorType) }}</el-tag>
          </div>
          <div class="processor-detail-actions">
            <el-button text type="primary" @click="duplicateProcessor(activeProcessor.id)">复制</el-button>
            <el-button text type="danger" @click="removeProcessor(activeProcessor.id)">删除</el-button>
          </div>
        </div>

        <template v-if="activeProcessor.processorType === 'SCRIPT'">
          <div class="editor-actions">
            <el-tag size="small">JavaScript</el-tag>
            <el-button size="small" @click="clearScript(activeProcessor)">清空</el-button>
            <el-button size="small" @click="formatScript(activeProcessor)">格式化</el-button>
          </div>
          <MonacoCodeEditor v-model="activeProcessor.script" language="text" height="360px" />
          <div class="processor-hint">可使用 setVar / getVar / removeVar / log / fail / request / response。</div>
        </template>

        <template v-else-if="activeProcessor.processorType === 'SQL'">
          <div class="processor-form-grid">
            <label>
              <span>数据库连接</span>
              <el-select
                v-model="activeProcessor.dataSourceId"
                filterable
                clearable
                placeholder="选择数据库连接"
                :data-testid="`processor-sql-datasource-${props.stage}`"
                @change="syncDataSourceName(activeProcessor)"
              >
                <el-option
                  v-for="item in enabledDbConnections"
                  :key="item.id"
                  :label="item.connectionName"
                  :value="item.id"
                />
              </el-select>
            </label>
            <label>
              <span>查询超时(ms)</span>
              <el-input-number v-model="activeProcessor.queryTimeout" :min="1000" :step="1000" />
            </label>
            <label>
              <span>按列存储变量</span>
              <el-input v-model="activeProcessor.variableNames" placeholder="id,email" />
            </label>
            <label>
              <span>完整结果变量</span>
              <el-input v-model="activeProcessor.resultVariable" placeholder="resultJson" />
            </label>
          </div>

          <MonacoCodeEditor
            v-model="activeProcessor.script"
            language="text"
            height="260px"
            :data-testid="`processor-sql-script-${props.stage}`"
          />

          <div class="extractor-table compact">
            <div class="extractor-table-header two-cols">
              <span>变量名</span>
              <span>列名</span>
              <span></span>
            </div>
            <div
              v-for="(param, index) in activeProcessor.extractParams || []"
              :key="`${activeProcessor.id}-sql-${index}`"
              class="extractor-table-row two-cols"
            >
              <el-input v-model="param.key" placeholder="变量名" />
              <el-input v-model="param.value" placeholder="列名" />
              <el-button text type="danger" @click="removeSqlExtractParam(activeProcessor, index)">删除</el-button>
            </div>
            <button type="button" class="add-row-button" @click="addSqlExtractParam(activeProcessor)">+ 添加提取参数</button>
          </div>
        </template>

        <template v-else-if="activeProcessor.processorType === 'TIME_WAITING'">
          <div class="processor-form-row">
            <span class="processor-form-label">等待时长(ms)</span>
            <el-input-number v-model="activeProcessor.delayMs" :min="1" :max="600000" :step="100" />
          </div>
        </template>

        <template v-else>
          <div class="extractor-panel">
            <div class="extractor-panel-header">
              <div class="extractor-panel-title">提取参数</div>
              <button type="button" class="add-row-button" @click="addExtractorRow(activeProcessor)">+ 添加提取项</button>
            </div>

            <div v-if="activeProcessor.extractors.length" class="extractor-grid-scroll">
              <div class="extractor-grid">
                <div class="extractor-grid-header">
                  <span>变量名</span>
                  <span>描述</span>
                  <span>变量类型</span>
                  <span>提取方式</span>
                  <span>提取范围</span>
                  <span>表达式</span>
                  <span class="extractor-grid-header-action">操作</span>
                </div>

                <div
                  v-for="(extractor, index) in activeProcessor.extractors"
                  :key="`${activeProcessor.id}-${index}`"
                  class="extractor-grid-row"
                  :data-testid="`processor-extract-row-${props.stage}-${index}`"
                >
                  <div class="extractor-grid-cell">
                    <el-input
                      v-model="extractor.variableName"
                      placeholder="例如 token"
                      size="small"
                      :data-testid="`processor-extract-variable-${props.stage}-${index}`"
                    />
                  </div>

                  <div class="extractor-grid-cell">
                    <el-input
                      v-model="extractor.description"
                      placeholder="可选"
                      size="small"
                      :data-testid="`processor-extract-description-${props.stage}-${index}`"
                    />
                  </div>

                  <div class="extractor-grid-cell">
                    <el-select v-model="extractor.variableType" size="small">
                      <el-option label="临时变量" value="TEMPORARY" />
                      <el-option label="环境变量" value="ENVIRONMENT" />
                    </el-select>
                  </div>

                  <div class="extractor-grid-cell">
                    <el-select v-model="extractor.extractType" size="small" @change="handleExtractorTypeChange(extractor)">
                      <el-option label="JSONPath" value="JSON_PATH" />
                      <el-option label="XPath" value="X_PATH" />
                      <el-option label="Regex" value="REGEX" />
                    </el-select>
                  </div>

                  <div class="extractor-grid-cell">
                    <el-select
                      v-model="extractor.extractScope"
                      size="small"
                      :disabled="normalizedExtractorType(extractor) !== 'REGEX'"
                    >
                      <el-option
                        v-for="option in extractScopeOptions(extractor)"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                      />
                    </el-select>
                  </div>

                  <div class="extractor-grid-cell extractor-grid-cell--expression">
                    <el-input
                      v-model="extractor.expression"
                      :placeholder="extractorExpressionPlaceholder(extractor)"
                      size="small"
                    >
                      <template #suffix>
                        <div class="extractor-expression-actions">
                          <el-tooltip :content="fastExtractionTitle">
                            <button
                              type="button"
                              :class="['fast-extraction-suffix-button', { disabled: !hasResponseBody }]"
                              :disabled="!hasResponseBody"
                              aria-label="快速提取"
                              @click.stop="openFastExtraction(activeProcessor, index)"
                            >
                              <el-icon><MagicStick /></el-icon>
                            </button>
                          </el-tooltip>
                        </div>
                      </template>
                    </el-input>
                  </div>

                  <div class="extractor-grid-cell extractor-grid-cell--actions">
                    <el-popover
                      placement="bottom-end"
                      :width="340"
                      trigger="click"
                      :visible="moreSettingsVisibleKey === `${activeProcessorKey}-${index}`"
                      @update:visible="(value: boolean) => setMoreSettingsVisible(activeProcessorKey, index, value)"
                    >
                      <template #reference>
                        <el-button text class="extractor-icon-button extractor-more-trigger" :icon="MoreFilled" />
                      </template>

                      <div class="extractor-more-settings">
                        <button
                          type="button"
                          class="extractor-more-copy"
                          @click="copyExtractorRow(activeProcessor, index)"
                        >
                          复制当前提取项
                        </button>

                        <div class="extractor-more-divider"></div>

                        <div class="extractor-more-title">高级设置</div>

                        <div class="extractor-more-group">
                          <div class="extractor-more-label">结果匹配规则</div>
                          <el-radio-group v-model="extractor.resultMatchingRule" size="small">
                            <el-radio value="RANDOM">随机</el-radio>
                            <el-radio value="SPECIFIC">指定</el-radio>
                            <el-radio value="ALL">全部</el-radio>
                          </el-radio-group>
                        </div>

                        <div v-if="showSpecificResultIndex(extractor)" class="extractor-more-group">
                          <div class="extractor-more-label">指定序号</div>
                          <el-input-number v-model="extractor.resultMatchingRuleNum" :min="1" :step="1" size="small" />
                        </div>

                        <div v-if="showRegexSettings(extractor)" class="extractor-more-group">
                          <div class="extractor-more-label">正则匹配规则</div>
                          <el-radio-group v-model="extractor.expressionMatchingRule" size="small">
                            <el-radio value="EXPRESSION">整段匹配</el-radio>
                            <el-radio value="GROUP">分组 1</el-radio>
                          </el-radio-group>
                        </div>

                        <div v-if="showXPathSettings(extractor)" class="extractor-more-group">
                          <div class="extractor-more-label">内容格式</div>
                          <el-radio-group v-model="extractor.responseFormat" size="small">
                            <el-radio value="XML">XML</el-radio>
                            <el-radio value="HTML">HTML</el-radio>
                          </el-radio-group>
                        </div>
                      </div>
                    </el-popover>
                    <el-button
                      text
                      type="danger"
                      class="extractor-icon-button extractor-delete-trigger"
                      :icon="Delete"
                      @click="removeExtractorRow(activeProcessor, index)"
                    />
                  </div>
                </div>
              </div>
            </div>

            <div v-else class="processor-empty processor-empty--inline">暂无提取项</div>
          </div>
        </template>
      </template>

      <div v-else class="processor-empty">请选择一个处理器进行编辑</div>
    </section>

    <ApiFastExtractionDrawer
      v-model:visible="fastExtractionVisible"
      :mode="activeFastExtractionMode"
      :config="activeFastExtractionConfig"
      :response="props.latestResponse?.body || ''"
      :show-more-setting="true"
      @apply="handleFastExtractionApply"
    />
  </div>
</template>

<style scoped>
.processor-editor {
  display: grid;
  grid-template-columns: minmax(220px, 260px) minmax(0, 1fr);
  gap: 12px;
  min-height: 420px;
}

.processor-sidebar,
.processor-detail {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.processor-sidebar {
  display: flex;
  flex-direction: column;
}

.processor-toolbar {
  display: flex;
  justify-content: flex-start;
  padding: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.processor-list {
  display: flex;
  flex-direction: column;
  padding: 8px;
  gap: 8px;
}

.processor-list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  text-align: left;
  cursor: pointer;
}

.processor-list-item.active {
  border-color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 8%, white);
}

.processor-list-item-main,
.processor-detail-header,
.processor-detail-fields,
.processor-form-row,
.editor-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.processor-list-copy,
.processor-detail-fields {
  min-width: 0;
}

.processor-list-title,
.processor-list-meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.processor-list-title {
  color: var(--el-text-color-primary);
}

.processor-list-meta,
.processor-form-label,
.processor-hint,
.processor-form-grid span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.processor-list-actions,
.processor-detail-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.processor-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
}

.processor-detail-header {
  justify-content: space-between;
  flex-wrap: wrap;
}

.processor-detail-fields {
  flex: 1;
  min-width: 280px;
}

.processor-detail-fields :deep(.el-input) {
  flex: 1;
}

.processor-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.processor-form-grid label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.extractor-table {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.extractor-table.compact {
  gap: 8px;
}

.extractor-table-header,
.extractor-table-row {
  display: grid;
  gap: 10px;
  align-items: center;
}

.extractor-table-header.two-cols,
.extractor-table-row.two-cols {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
}

.extractor-table-header {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.extractor-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.extractor-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.extractor-panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.extractor-grid {
  width: max-content;
  min-width: 100%;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: visible;
}

.extractor-grid-scroll {
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 4px;
  scrollbar-width: thin;
  scrollbar-color: #d7dbe3 transparent;
}

.extractor-grid-scroll::-webkit-scrollbar {
  height: 6px;
}

.extractor-grid-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.extractor-grid-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background-color: #d7dbe3;
}

.extractor-grid-scroll::-webkit-scrollbar-thumb:hover {
  background-color: #d7dbe3;
}

.extractor-grid-header,
.extractor-grid-row {
  display: grid;
  grid-template-columns: 150px 150px 130px 110px 96px 220px 56px;
  gap: 8px;
  align-items: start;
  padding: 8px 12px;
}

.extractor-grid-header {
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-weight: 400;
}

.extractor-grid-header-action {
  display: flex;
  justify-content: center;
}

.extractor-grid-row + .extractor-grid-row {
  border-top: 1px solid var(--el-border-color-lighter);
}

.extractor-grid-cell {
  min-width: 0;
}

.extractor-grid-cell :deep(.el-select) {
  width: 100%;
}

.extractor-grid-cell--expression {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.extractor-grid-cell--actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-height: 32px;
  white-space: nowrap;
  min-width: 56px;
}

.extractor-expression-actions {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.fast-extraction-suffix-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  background: transparent;
  color: #165dff;
  cursor: pointer;
}

.fast-extraction-suffix-button.disabled {
  color: var(--el-text-color-placeholder);
  cursor: not-allowed;
}

.fast-extraction-suffix-button:focus-visible {
  outline: 2px solid rgba(22, 93, 255, 0.2);
  outline-offset: 1px;
}

.extractor-more-settings {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.extractor-more-copy {
  border: none;
  background: transparent;
  color: var(--el-text-color-primary);
  text-align: left;
  padding: 0;
  cursor: pointer;
  font-size: 13px;
}

.extractor-more-title {
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 500;
}

.extractor-more-divider {
  height: 1px;
  background: var(--el-border-color-lighter);
}

.extractor-more-trigger {
  color: #909399;
}

.extractor-icon-button {
  width: 18px;
  min-width: 18px;
  height: 28px;
  padding: 0;
}

.extractor-delete-trigger {
  color: #909399;
}

.extractor-more-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.extractor-more-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.add-row-button {
  width: fit-content;
  border: none;
  background: transparent;
  color: var(--el-color-primary);
  cursor: pointer;
  padding: 0;
}

.processor-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  color: var(--el-text-color-secondary);
}

.processor-empty--inline {
  min-height: 160px;
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
}

@media (max-width: 1400px) {
  .extractor-grid {
    min-width: 100%;
  }
}

@media (max-width: 1100px) {
  .processor-editor,
  .processor-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
