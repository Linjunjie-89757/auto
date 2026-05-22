<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
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
import { testFastExtraction } from '../utils/apiFastExtraction'

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

const processorOptions = computed(() => props.stage === 'pre'
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
    ])

const activeProcessor = computed(() => processors.value.find(item => item.id === activeProcessorId.value) ?? null)
const enabledDbConnections = computed(() => (props.dbConnections ?? []).filter(item => item.status === 1))

const processorTypeLabelMap: Record<ApiProcessorConfig['processorType'], string> = {
  SCRIPT: '脚本处理器',
  SQL: 'SQL 处理器',
  TIME_WAITING: '等待处理器',
  EXTRACT: '提取处理器',
}

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

function processorTypeLabel(type: ApiProcessorConfig['processorType']) {
  return processorTypeLabelMap[type] || type
}

function displayProcessorName(processor: ApiProcessorConfig) {
  const name = (processor.name || '').trim()
  if (processor.processorType === 'SCRIPT' && (name === 'Pre Script' || name === 'Post Script')) {
    return props.stage === 'pre' ? '前置脚本' : '后置脚本'
  }
  if (processor.processorType === 'TIME_WAITING' && name === 'Wait') {
    return '等待'
  }
  if (processor.processorType === 'EXTRACT' && name === 'Extract') {
    return '提取'
  }
  return name || processorTypeLabel(processor.processorType)
}

function emptyExtractor(): ApiProcessorExtractorConfig {
  return {
    name: '',
    variableName: '',
    variableType: 'TEMPORARY',
    extractType: 'JSON_PATH',
    extractScope: 'BODY',
    expression: '$.data.id',
    expressionMatchingRule: 'EXPRESSION',
    resultMatchingRule: 'RANDOM',
    resultMatchingRuleNum: 1,
    responseFormat: 'JSON',
    enabled: true,
  }
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
  copy.name = `${copy.name || '处理器'} 副本`
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
  const targetIndex = index + delta
  if (index < 0 || targetIndex < 0 || targetIndex >= processors.value.length) {
    return
  }
  const next = [...processors.value]
  const [item] = next.splice(index, 1)
  next.splice(targetIndex, 0, item)
  updateProcessors(next)
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
  processor.extractors.splice(index + 1, 0, JSON.parse(JSON.stringify(processor.extractors[index])) as ApiProcessorExtractorConfig)
}

function removeExtractorRow(processor: ApiExtractProcessorConfig, index: number) {
  processor.extractors.splice(index, 1)
  if (!processor.extractors.length) {
    processor.extractors.push(emptyExtractor())
  }
}

function clearScript(processor: { script: string }) {
  processor.script = ''
}

function formatScript(processor: { script: string }) {
  processor.script = processor.script.trim()
}

function syncDataSourceName(processor: ApiSqlProcessorConfig) {
  const dataSource = enabledDbConnections.value.find(item => item.id === processor.dataSourceId)
  processor.dataSourceName = dataSource?.connectionName ?? ''
}

function testExtractor(extractor: ApiProcessorExtractorConfig) {
  const response = props.latestResponse
  if (!response) {
    ElMessage.warning('请先执行一次接口，再测试提取表达式')
    return
  }
  try {
    const values = extractPreviewValues(extractor, response)
    if (!values.length) {
      ElMessage.warning('未匹配到结果')
      return
    }
    ElMessage.success(values.length === 1 ? `匹配结果：${values[0]}` : `匹配 ${values.length} 条：${values.slice(0, 3).join(', ')}`)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

function extractPreviewValues(extractor: ApiProcessorExtractorConfig, response: ApiResponseSnapshot) {
  const scope = extractor.extractScope || legacyScope(extractor.sourceType)
  const source = scope === 'RESPONSE_HEADERS'
    ? JSON.stringify(response.headers ?? {})
    : scope === 'RESPONSE_CODE'
      ? String(response.statusCode ?? '')
      : response.body ?? ''
  return testFastExtraction(source, {
    expression: extractor.expression || '',
    extractType: extractor.extractType || legacyType(extractor.sourceType),
    expressionMatchingRule: extractor.expressionMatchingRule || 'EXPRESSION',
    responseFormat: extractor.responseFormat || 'XML',
  })
}

function legacyScope(sourceType?: string) {
  if (sourceType === 'HEADER') {
    return 'RESPONSE_HEADERS'
  }
  if (sourceType === 'STATUS_CODE') {
    return 'RESPONSE_CODE'
  }
  return 'BODY'
}

function legacyType(sourceType?: string) {
  return sourceType === 'BODY_JSONPATH' || !sourceType ? 'JSON_PATH' : 'REGEX'
}

const fastExtractionVisible = ref(false)
const activeExtractorTarget = ref<{ processorId: string, index: number } | null>(null)
const hasResponseBody = computed(() => !!props.latestResponse?.body?.trim())

function openFastExtraction(processor: ApiExtractProcessorConfig, index: number) {
  if (!hasResponseBody.value) {
    return
  }
  activeExtractorTarget.value = {
    processorId: processor.id,
    index,
  }
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
const fastExtractionTitle = computed(() => hasResponseBody.value ? '快速提取' : '请先发送获取响应内容')

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
  fastExtractionVisible.value = false
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
              <el-dropdown-item v-for="option in processorOptions" :key="option.value" :command="option.value">
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
            <button type="button" class="ghost-action" :disabled="index === 0" @click.stop="moveProcessor(item.id, -1)">上移</button>
            <button type="button" class="ghost-action" :disabled="index === processors.length - 1" @click.stop="moveProcessor(item.id, 1)">下移</button>
          </div>
        </button>
      </div>
      <div v-else class="processor-empty">暂无处理器</div>
    </aside>

    <section class="processor-detail" :data-testid="`processor-detail-${props.stage}`">
      <template v-if="activeProcessor">
        <div class="processor-detail-header">
          <div class="processor-detail-fields" :data-testid="`processor-name-${props.stage}`">
            <el-input v-model="activeProcessor.name" placeholder="处理器名称" :data-testid="`processor-name-${props.stage}`" />
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
          <div class="processor-hint">可使用 setVar/getVar/removeVar/log/fail/request/response。</div>
        </template>

        <template v-else-if="activeProcessor.processorType === 'SQL'">
          <div class="processor-form-grid">
            <label :data-testid="`processor-sql-datasource-${props.stage}`">
              <span>数据库连接</span>
              <el-select v-model="activeProcessor.dataSourceId" filterable clearable placeholder="选择数据库连接" :data-testid="`processor-sql-datasource-${props.stage}`" @change="syncDataSourceName(activeProcessor)">
                <el-option v-for="item in enabledDbConnections" :key="item.id" :label="item.connectionName" :value="item.id" />
              </el-select>
            </label>
            <label :data-testid="`processor-sql-timeout-${props.stage}`">
              <span>鏌ヨ瓒呮椂(ms)</span>
              <el-input-number v-model="activeProcessor.queryTimeout" :min="1000" :step="1000" :data-testid="`processor-sql-timeout-${props.stage}`" />
            </label>
            <label :data-testid="`processor-sql-variable-names-${props.stage}`">
              <span>按列存储变量</span>
              <el-input v-model="activeProcessor.variableNames" placeholder="id,email" :data-testid="`processor-sql-variable-names-${props.stage}`" />
            </label>
            <label :data-testid="`processor-sql-result-variable-${props.stage}`">
              <span>完整结果变量</span>
              <el-input v-model="activeProcessor.resultVariable" placeholder="resultJson" :data-testid="`processor-sql-result-variable-${props.stage}`" />
            </label>
          </div>
          <MonacoCodeEditor v-model="activeProcessor.script" language="text" height="260px" :data-testid="`processor-sql-script-${props.stage}`" />
          <div class="extractor-table compact">
            <div class="extractor-table-header two-cols">
              <span>变量名</span>
              <span>鍒楀悕</span>
              <span></span>
            </div>
            <div v-for="(param, index) in activeProcessor.extractParams || []" :key="`${activeProcessor.id}-sql-${index}`" class="extractor-table-row two-cols" :data-testid="`processor-sql-extract-row-${props.stage}-${index}`">
              <el-input v-model="param.key" placeholder="变量名" :data-testid="`processor-sql-extract-key-${props.stage}-${index}`" />
              <el-input v-model="param.value" placeholder="列名" :data-testid="`processor-sql-extract-value-${props.stage}-${index}`" />
              <button type="button" class="ghost-action danger" @click="removeSqlExtractParam(activeProcessor, index)">删除</button>
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
          <div class="extractor-table">
            <div
              v-for="(extractor, index) in activeProcessor.extractors"
              :key="`${activeProcessor.id}-${index}`"
              class="extractor-card"
              :data-testid="`processor-extract-row-${props.stage}-${index}`"
            >
              <div class="extractor-card-head">
                <el-checkbox v-model="extractor.enabled" />
                 <el-input v-model="extractor.variableName" placeholder="变量名" :data-testid="`processor-extract-variable-${props.stage}-${index}`" />
                <el-select v-model="extractor.variableType" :data-testid="`processor-extract-variable-type-${props.stage}-${index}`">
                   <el-option label="临时变量" value="TEMPORARY" />
                   <el-option label="环境变量" value="ENVIRONMENT" />
                </el-select>
                 <el-button text type="primary" @click="testExtractor(extractor)">测试</el-button>
                 <el-button text type="primary" @click="duplicateExtractorRow(activeProcessor, index)">复制</el-button>
                 <el-button text type="danger" @click="removeExtractorRow(activeProcessor, index)">删除</el-button>
              </div>
              <div class="processor-form-grid">
                <label>
                   <span>提取方式</span>
                  <el-select v-model="extractor.extractType" :data-testid="`processor-extract-type-${props.stage}-${index}`">
                    <el-option label="JSONPath" value="JSON_PATH" />
                    <el-option label="XPath" value="X_PATH" />
                     <el-option label="正则" value="REGEX" />
                  </el-select>
                </label>
                <label>
                   <span>提取范围</span>
                  <el-select v-model="extractor.extractScope" :data-testid="`processor-extract-scope-${props.stage}-${index}`">
                     <el-option label="响应体" value="BODY" />
                     <el-option label="反转义响应体" value="UNESCAPED_BODY" />
                     <el-option label="文档响应体" value="BODY_AS_DOCUMENT" />
                    <el-option label="URL" value="URL" />
                     <el-option label="请求头" value="REQUEST_HEADERS" />
                     <el-option label="响应头" value="RESPONSE_HEADERS" />
                     <el-option label="响应码" value="RESPONSE_CODE" />
                     <el-option label="响应消息" value="RESPONSE_MESSAGE" />
                  </el-select>
                </label>
                <label>
                   <span>匹配规则</span>
                  <el-select v-model="extractor.resultMatchingRule" :data-testid="`processor-extract-match-rule-${props.stage}-${index}`">
                     <el-option label="随机" value="RANDOM" />
                     <el-option label="指定" value="SPECIFIC" />
                     <el-option label="全部" value="ALL" />
                  </el-select>
                </label>
                <label>
                   <span>指定序号</span>
                  <el-input-number v-model="extractor.resultMatchingRuleNum" :min="1" :step="1" :data-testid="`processor-extract-match-num-${props.stage}-${index}`" />
                </label>
                <label>
                   <span>正则分组</span>
                  <el-select v-model="extractor.expressionMatchingRule" :data-testid="`processor-extract-regex-rule-${props.stage}-${index}`">
                     <el-option label="完整匹配" value="EXPRESSION" />
                     <el-option label="分组 1" value="GROUP" />
                  </el-select>
                </label>
                <label>
                   <span>响应格式</span>
                  <el-select v-model="extractor.responseFormat" :data-testid="`processor-extract-response-format-${props.stage}-${index}`">
                    <el-option label="JSON" value="JSON" />
                    <el-option label="XML" value="XML" />
                    <el-option label="HTML" value="HTML" />
                  </el-select>
                </label>
              </div>
              <el-input v-model="extractor.expression" placeholder="提取表达式" :data-testid="`processor-extract-expression-${props.stage}-${index}`">
                <template #suffix>
                  <button
                    type="button"
                    :class="['fast-extraction-suffix-button', { disabled: !hasResponseBody }]"
                    :disabled="!hasResponseBody"
                    :title="fastExtractionTitle"
                    aria-label="快速提取"
                    @click.stop="openFastExtraction(activeProcessor, index)"
                  >
                    <el-icon><MagicStick /></el-icon>
                  </button>
                </template>
              </el-input>
              <el-input v-model="extractor.description" placeholder="描述" :data-testid="`processor-extract-description-${props.stage}-${index}`" />
            </div>
            <button type="button" class="add-row-button" @click="addExtractorRow(activeProcessor)">+ 添加提取项</button>
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
.editor-actions,
.extractor-card-head {
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
  gap: 8px;
}

.ghost-action {
  border: none;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.ghost-action:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.ghost-action.danger {
  color: var(--el-color-danger);
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

.extractor-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.extractor-card-head {
  flex-wrap: wrap;
}

.extractor-card-head :deep(.el-input) {
  width: 180px;
}

.extractor-card-head :deep(.el-select) {
  width: 140px;
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

@media (max-width: 1100px) {
  .processor-editor,
  .processor-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>



