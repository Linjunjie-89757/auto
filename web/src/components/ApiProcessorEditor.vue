<script setup lang="ts">
import { computed, watch } from 'vue'
import MonacoCodeEditor from './MonacoCodeEditor.vue'
import type { ApiExtractProcessorConfig, ApiProcessorConfig, ApiProcessorExtractorConfig } from '../types/api'

const props = defineProps<{
  modelValue: ApiProcessorConfig[]
  stage: 'pre' | 'post'
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
      { label: '等待', value: 'TIME_WAITING' },
    ]
  : [
      { label: '脚本', value: 'SCRIPT' },
      { label: '提取', value: 'EXTRACT' },
      { label: '等待', value: 'TIME_WAITING' },
    ])

const activeProcessor = computed(() => processors.value.find(item => item.id === activeProcessorId.value) ?? null)

const processorTypeLabelMap: Record<ApiProcessorConfig['processorType'], string> = {
  SCRIPT: '脚本处理器',
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
      script: '',
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
  if (processor.processorType === 'SCRIPT') {
    if (name === 'Pre Script' || name === '前置脚本') {
      return '前置脚本'
    }
    if (name === 'Post Script' || name === '后置脚本') {
      return '后置脚本'
    }
  }
  if (processor.processorType === 'TIME_WAITING' && (name === 'Wait' || name === '等待')) {
    return '等待'
  }
  if (processor.processorType === 'EXTRACT' && (name === 'Extract' || name === '提取')) {
    return '提取'
  }
  return name || processorTypeLabel(processor.processorType)
}

function emptyExtractor(): ApiProcessorExtractorConfig {
  return {
    name: '',
    sourceType: 'BODY_JSONPATH',
    expression: '',
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
  const target = processors.value[index]
  const copy = cloneProcessorConfig(target)
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

function addExtractorRow(processor: ApiExtractProcessorConfig) {
  processor.extractors.push(emptyExtractor())
}

function removeExtractorRow(processor: ApiExtractProcessorConfig, index: number) {
  processor.extractors.splice(index, 1)
  if (!processor.extractors.length) {
    processor.extractors.push(emptyExtractor())
  }
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
            <button type="button" class="ghost-action" :disabled="index === 0" @click.stop="moveProcessor(item.id, -1)">↑</button>
            <button type="button" class="ghost-action" :disabled="index === processors.length - 1" @click.stop="moveProcessor(item.id, 1)">↓</button>
          </div>
        </button>
      </div>
      <div v-else class="processor-empty">暂无处理器。</div>
    </aside>

    <section class="processor-detail">
      <template v-if="activeProcessor">
        <div class="processor-detail-header">
          <div class="processor-detail-fields">
            <el-input v-model="activeProcessor.name" placeholder="处理器名称" />
            <el-tag size="small" effect="plain">{{ processorTypeLabel(activeProcessor.processorType) }}</el-tag>
          </div>
          <div class="processor-detail-actions">
            <el-button text type="primary" @click="duplicateProcessor(activeProcessor.id)">复制</el-button>
            <el-button text type="danger" @click="removeProcessor(activeProcessor.id)">删除</el-button>
          </div>
        </div>

        <template v-if="activeProcessor.processorType === 'SCRIPT'">
          <MonacoCodeEditor
            v-model="activeProcessor.script"
            language="text"
            height="360px"
          />
        </template>

        <template v-else-if="activeProcessor.processorType === 'TIME_WAITING'">
          <div class="processor-form-row">
            <span class="processor-form-label">等待时长（毫秒）</span>
            <el-input-number v-model="activeProcessor.delayMs" :min="1" :step="100" />
          </div>
        </template>

        <template v-else>
          <div class="extractor-table">
            <div class="extractor-table-header">
              <span>变量名</span>
              <span>来源</span>
              <span>表达式</span>
              <span></span>
            </div>
            <div
              v-for="(extractor, index) in activeProcessor.extractors"
              :key="`${activeProcessor.id}-${index}`"
              class="extractor-table-row"
            >
              <label class="extractor-name-cell">
                <el-checkbox v-model="extractor.enabled" />
                <el-input v-model="extractor.name" placeholder="变量名" />
              </label>
              <el-select v-model="extractor.sourceType">
                <el-option label="BODY_JSONPATH" value="BODY_JSONPATH" />
                <el-option label="HEADER" value="HEADER" />
                <el-option label="STATUS_CODE" value="STATUS_CODE" />
              </el-select>
              <el-input v-model="extractor.expression" :placeholder="extractor.sourceType === 'STATUS_CODE' ? '可选' : '提取表达式'" />
              <button type="button" class="ghost-action danger" @click="removeExtractorRow(activeProcessor, index)">删除</button>
            </div>
            <button type="button" class="add-row-button" @click="addExtractorRow(activeProcessor)">+ 添加一行</button>
          </div>
        </template>
      </template>

      <div v-else class="processor-empty">请选择一个处理器进行编辑。</div>
    </section>
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

.processor-list-item-main {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.processor-list-copy {
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

.processor-list-meta {
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

.processor-detail-header,
.processor-detail-fields,
.processor-form-row,
.extractor-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
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

.processor-form-label {
  color: var(--el-text-color-secondary);
}

.extractor-table {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.extractor-table-header,
.extractor-table-row {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(140px, 180px) minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.extractor-table-header {
  color: var(--el-text-color-secondary);
  font-size: 12px;
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
  .processor-editor {
    grid-template-columns: 1fr;
  }
}
</style>
