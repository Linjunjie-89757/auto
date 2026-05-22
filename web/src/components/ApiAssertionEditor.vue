<script setup lang="ts">
import { computed, nextTick, onMounted, onUpdated, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ApiFastExtractionDrawer from './ApiFastExtractionDrawer.vue'
import MonacoCodeEditor from './MonacoCodeEditor.vue'
import type {
  ApiAssertionConfig,
  ApiAssertionCondition,
  ApiAssertionGroupConfig,
  ApiAssertionItemConfig,
  ApiAssertionType,
  ApiResponseSnapshot,
} from '../types/api'
import type { FastExtractionConfig } from '../utils/apiFastExtraction'
import { testFastExtraction } from '../utils/apiFastExtraction'

const props = defineProps<{
  modelValue: ApiAssertionConfig[]
  latestResponse?: ApiResponseSnapshot | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: ApiAssertionConfig[]]
}>()

const assertions = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const rootRef = ref<HTMLElement | null>(null)
const activeAssertionId = defineModel<string | null>('activeId', { default: null })

const assertionOptions: Array<{ label: string, value: ApiAssertionType }> = [
  { label: '状态码', value: 'RESPONSE_CODE' },
  { label: '响应头', value: 'RESPONSE_HEADER' },
  { label: '响应体', value: 'RESPONSE_BODY' },
  { label: '响应时间', value: 'RESPONSE_TIME' },
  { label: '变量', value: 'VARIABLE' },
  { label: '脚本', value: 'SCRIPT' },
]

const conditionOptions: Array<{ label: string, value: ApiAssertionCondition }> = [
  { label: '等于', value: 'EQUALS' },
  { label: '不等于', value: 'NOT_EQUALS' },
  { label: '包含', value: 'CONTAINS' },
  { label: '不包含', value: 'NOT_CONTAINS' },
  { label: '为空', value: 'EMPTY' },
  { label: '不为空', value: 'NOT_EMPTY' },
  { label: '开头是', value: 'START_WITH' },
  { label: '结尾是', value: 'END_WITH' },
  { label: '正则匹配', value: 'REGEX' },
  { label: '大于', value: 'GT' },
  { label: '大于等于', value: 'GT_OR_EQUALS' },
  { label: '小于', value: 'LT' },
  { label: '小于等于', value: 'LT_OR_EQUALS' },
  { label: '长度等于', value: 'LENGTH_EQUALS' },
  { label: '长度不等于', value: 'LENGTH_NOT_EQUALS' },
  { label: '长度大于', value: 'LENGTH_GT' },
  { label: '长度大于等于', value: 'LENGTH_GT_OR_EQUALS' },
  { label: '长度小于', value: 'LENGTH_LT' },
  { label: '长度小于等于', value: 'LENGTH_LT_OR_EQUALS' },
  { label: '不检查', value: 'UNCHECKED' },
]

const activeAssertion = computed(() => assertions.value.find(item => item.id === activeAssertionId.value) ?? null)

watch(assertions, (value) => {
  const normalized = value.map(normalizeAssertion)
  if (JSON.stringify(normalized) !== JSON.stringify(value)) {
    assertions.value = normalized
    return
  }
  if (!normalized.length) {
    activeAssertionId.value = null
    return
  }
  if (!activeAssertionId.value || !normalized.some(item => item.id === activeAssertionId.value)) {
    activeAssertionId.value = normalized[0].id ?? null
  }
}, { deep: true, immediate: true })

function createId(type: string) {
  return `assertion-${type.toLowerCase()}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function normalizeAssertion(assertion: ApiAssertionConfig): ApiAssertionConfig {
  const assertionType = normalizeAssertionType(assertion)
  const next: ApiAssertionConfig = {
    ...assertion,
    id: assertion.id || createId(assertionType),
    assertionType,
    enabled: assertion.enabled !== false,
    name: assertion.name || defaultAssertionName(assertionType),
  }
  if (assertionType === 'RESPONSE_CODE') {
    next.condition = normalizeCondition(assertion.condition || assertion.operator || 'EQUALS')
    next.expectedValue = assertion.expectedValue ?? '200'
  }
  if (assertionType === 'RESPONSE_HEADER') {
    next.assertions = normalizeHeaderItems(assertion)
  }
  if (assertionType === 'RESPONSE_BODY') {
    next.assertionBodyType = normalizeBodyType(assertion)
    next.jsonPathAssertion = normalizeGroup(assertion.jsonPathAssertion, assertion, 'JSON_PATH')
    next.xpathAssertion = normalizeGroup(assertion.xpathAssertion, assertion, 'X_PATH')
    next.regexAssertion = normalizeGroup(assertion.regexAssertion, assertion, 'REGEX')
  }
  if (assertionType === 'RESPONSE_TIME') {
    next.condition = 'LT_OR_EQUALS'
    next.expectedValue = assertion.expectedValue ?? '1000'
  }
  if (assertionType === 'VARIABLE') {
    next.variableAssertionItems = assertion.variableAssertionItems?.length
      ? assertion.variableAssertionItems.map(normalizeItem)
      : [normalizeItem({ variableName: '', condition: 'EQUALS', expectedValue: '' })]
  }
  if (assertionType === 'SCRIPT') {
    next.scriptLanguage = 'JAVASCRIPT'
    next.script = assertion.script ?? ''
  }
  return next
}

function normalizeAssertionType(assertion: ApiAssertionConfig): ApiAssertionType {
  const type = (assertion.assertionType || assertion.type || 'RESPONSE_CODE').toUpperCase()
  if (type === 'STATUS_CODE') {
    return 'RESPONSE_CODE'
  }
  if (type === 'HEADER_EQUALS' || type === 'HEADER_CONTAINS') {
    return 'RESPONSE_HEADER'
  }
  if (type === 'BODY_JSONPATH_EQUALS' || type === 'BODY_JSONPATH_CONTAINS') {
    return 'RESPONSE_BODY'
  }
  if (type === 'RESPONSE_TIME_LE') {
    return 'RESPONSE_TIME'
  }
  return assertionOptions.some(item => item.value === type) ? type as ApiAssertionType : 'RESPONSE_CODE'
}

function normalizeCondition(value?: string): ApiAssertionCondition {
  const condition = (value || 'EQUALS').toUpperCase()
  if (condition === 'HEADER_CONTAINS' || condition === 'BODY_JSONPATH_CONTAINS') {
    return 'CONTAINS'
  }
  if (condition === 'RESPONSE_TIME_LE') {
    return 'LT_OR_EQUALS'
  }
  return conditionOptions.some(item => item.value === condition) ? condition as ApiAssertionCondition : 'EQUALS'
}

function normalizeBodyType(assertion: ApiAssertionConfig) {
  const type = (assertion.assertionBodyType || '').toUpperCase()
  if (type === 'XPATH') {
    return 'X_PATH'
  }
  if (type === 'X_PATH' || type === 'REGEX') {
    return type
  }
  return 'JSON_PATH'
}

function normalizeHeaderItems(assertion: ApiAssertionConfig) {
  if (assertion.assertions?.length) {
    return assertion.assertions.map(normalizeItem)
  }
  return [normalizeItem({
    header: assertion.subject || '',
    condition: assertion.type === 'HEADER_CONTAINS' ? 'CONTAINS' : assertion.operator || 'EQUALS',
    expectedValue: assertion.expectedValue || '',
  })]
}

function normalizeGroup(group: ApiAssertionGroupConfig | undefined, assertion: ApiAssertionConfig, type: 'JSON_PATH' | 'X_PATH' | 'REGEX'): ApiAssertionGroupConfig {
  const isLegacyJson = type === 'JSON_PATH' && (assertion.type === 'BODY_JSONPATH_EQUALS' || assertion.type === 'BODY_JSONPATH_CONTAINS')
  const assertions = group?.assertions?.length
    ? group.assertions.map(normalizeItem)
    : isLegacyJson
      ? [normalizeItem({
          expression: assertion.subject || '$',
          condition: assertion.type === 'BODY_JSONPATH_CONTAINS' ? 'CONTAINS' : assertion.operator || 'EQUALS',
          expectedValue: assertion.expectedValue || '',
        })]
      : [normalizeItem({ expression: defaultExpression(type), condition: 'EQUALS', expectedValue: '' })]
  return {
    assertions,
    responseFormat: group?.responseFormat || 'XML',
  }
}

function normalizeItem(item: ApiAssertionItemConfig): ApiAssertionItemConfig {
  return {
    ...item,
    enabled: item.enabled !== false,
    condition: normalizeCondition(item.condition),
    expectedValue: item.expectedValue ?? '',
  }
}

function defaultExpression(type: 'JSON_PATH' | 'X_PATH' | 'REGEX') {
  if (type === 'X_PATH') {
    return '/root'
  }
  if (type === 'REGEX') {
    return '.+'
  }
  return '$.data'
}

function defaultAssertionName(type: ApiAssertionType) {
  const option = assertionOptions.find(item => item.value === type)
  return option?.label ?? '断言'
}

function assertionTypeLabel(type?: string) {
  return assertionOptions.find(item => item.value === type)?.label ?? type ?? '断言'
}

function displayAssertionName(assertion: ApiAssertionConfig) {
  return assertion.name?.trim() || assertionTypeLabel(assertion.assertionType)
}

function updateAssertions(next: ApiAssertionConfig[]) {
  assertions.value = next
}

function createAssertion(type: ApiAssertionType): ApiAssertionConfig {
  return normalizeAssertion({ id: createId(type), assertionType: type, enabled: true })
}

function addAssertion(type: ApiAssertionType) {
  const next = [...assertions.value, createAssertion(type)]
  updateAssertions(next)
  activeAssertionId.value = next[next.length - 1].id ?? null
}

function handleAddCommand(command: string | number | object) {
  addAssertion(command as ApiAssertionType)
}

function duplicateAssertion(id: string) {
  const index = assertions.value.findIndex(item => item.id === id)
  if (index < 0) {
    return
  }
  const copy = clone(assertions.value[index])
  copy.id = createId(copy.assertionType || 'RESPONSE_CODE')
  copy.name = `${copy.name || '断言'} 副本`
  const next = [...assertions.value]
  next.splice(index + 1, 0, copy)
  updateAssertions(next)
  activeAssertionId.value = copy.id ?? null
}

function removeAssertion(id: string) {
  const next = assertions.value.filter(item => item.id !== id)
  updateAssertions(next)
  if (activeAssertionId.value === id) {
    activeAssertionId.value = next[0]?.id ?? null
  }
}

function moveAssertion(id: string, delta: number) {
  const index = assertions.value.findIndex(item => item.id === id)
  const targetIndex = index + delta
  if (index < 0 || targetIndex < 0 || targetIndex >= assertions.value.length) {
    return
  }
  const next = [...assertions.value]
  const [item] = next.splice(index, 1)
  next.splice(targetIndex, 0, item)
  updateAssertions(next)
}

function activeBodyGroup(assertion: ApiAssertionConfig): ApiAssertionGroupConfig {
  if (assertion.assertionBodyType === 'X_PATH') {
    assertion.xpathAssertion ??= { assertions: [normalizeItem({ expression: '/root', condition: 'EQUALS', expectedValue: '' })], responseFormat: 'XML' }
    return assertion.xpathAssertion
  }
  if (assertion.assertionBodyType === 'REGEX') {
    assertion.regexAssertion ??= { assertions: [normalizeItem({ expression: '.+', condition: 'EQUALS', expectedValue: '' })], responseFormat: 'XML' }
    return assertion.regexAssertion
  }
  assertion.jsonPathAssertion ??= { assertions: [normalizeItem({ expression: '$.data', condition: 'EQUALS', expectedValue: '' })], responseFormat: 'XML' }
  return assertion.jsonPathAssertion
}

function addItem(items: ApiAssertionItemConfig[], patch: ApiAssertionItemConfig = {}) {
  items.push(normalizeItem({ condition: 'EQUALS', expectedValue: '', enabled: true, ...patch }))
}

function duplicateItem(items: ApiAssertionItemConfig[], index: number) {
  items.splice(index + 1, 0, clone(items[index]))
}

function removeItem(items: ApiAssertionItemConfig[], index: number, fallback: ApiAssertionItemConfig) {
  items.splice(index, 1)
  if (!items.length) {
    items.push(normalizeItem(fallback))
  }
}

function clearScript(assertion: ApiAssertionConfig) {
  assertion.script = ''
}

function formatScript(assertion: ApiAssertionConfig) {
  assertion.script = (assertion.script || '').trim()
}

function updateActiveScript(value: string) {
  if (activeAssertion.value) {
    activeAssertion.value.script = value
  }
}

function testBodyExpression(assertion: ApiAssertionConfig, item: ApiAssertionItemConfig) {
  const response = props.latestResponse
  if (!response) {
    ElMessage.warning('请先执行一次接口，再测试表达式')
    return
  }
  try {
    const values = extractPreviewValues(assertion, item, response)
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

function extractPreviewValues(assertion: ApiAssertionConfig, item: ApiAssertionItemConfig, response: ApiResponseSnapshot) {
  const source = response.body ?? ''
  return testFastExtraction(source, {
    expression: item.expression || '',
    extractType: assertion.assertionBodyType || 'JSON_PATH',
    responseFormat: activeBodyGroup(assertion).responseFormat || 'XML',
  })
}

const fastExtractionVisible = ref(false)
const activeBodyTarget = ref<{ assertionId: string, index: number } | null>(null)
const hasResponseBody = computed(() => !!props.latestResponse?.body?.trim())

function openFastExtraction(index: number) {
  if (!hasResponseBody.value || !activeAssertion.value?.id) {
    return
  }
  activeBodyTarget.value = {
    assertionId: activeAssertion.value.id,
    index,
  }
  fastExtractionVisible.value = true
}

const activeFastExtractionConfig = computed<FastExtractionConfig>(() => {
  const target = activeBodyTarget.value
  if (!target) {
    return { expression: '$', extractType: 'JSON_PATH', responseFormat: 'XML' }
  }
  const assertion = assertions.value.find(item => item.id === target.assertionId)
  if (!assertion) {
    return { expression: '$', extractType: 'JSON_PATH', responseFormat: 'XML' }
  }
  const group = activeBodyGroup(assertion)
  const item = group.assertions[target.index]
  return {
    expression: item?.expression || defaultExpression(assertion.assertionBodyType || 'JSON_PATH'),
    extractType: assertion.assertionBodyType || 'JSON_PATH',
    responseFormat: group.responseFormat || 'XML',
  }
})

const activeFastExtractionMode = computed(() => activeFastExtractionConfig.value.extractType || 'JSON_PATH')

function handleFastExtractionApply(config: FastExtractionConfig, matchResult: string[]) {
  const target = activeBodyTarget.value
  if (!target) {
    return
  }
  const assertion = assertions.value.find(item => item.id === target.assertionId)
  if (!assertion) {
    return
  }
  const group = activeBodyGroup(assertion)
  const item = group.assertions[target.index]
  if (!item) {
    return
  }
  item.expression = config.expression || item.expression
  if (assertion.assertionBodyType === 'X_PATH' && (config.responseFormat === 'XML' || config.responseFormat === 'HTML')) {
    group.responseFormat = config.responseFormat
  }
  if (matchResult.length && assertion.assertionBodyType !== 'X_PATH') {
    item.expectedValue = matchResult[0]
  }
  fastExtractionVisible.value = false
}

function syncFastExtractionTriggers() {
  nextTick(() => {
    const root = rootRef.value
    if (!root) {
      return
    }
    const inputs = root.querySelectorAll<HTMLInputElement>('[data-testid^="assertion-body-expression-"]')
    inputs.forEach((input, index) => {
      const wrapper = input.closest('.el-input')?.querySelector<HTMLElement>('.el-input__wrapper')
      if (!wrapper) {
        return
      }
      let trigger = wrapper.querySelector<HTMLElement>('.fast-extraction-dom-trigger')
      if (!trigger) {
        trigger = document.createElement('span')
        trigger.className = 'fast-extraction-trigger fast-extraction-dom-trigger'
        wrapper.appendChild(trigger)
      }
      trigger.innerHTML = '<i class="el-icon"><svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path fill="currentColor" d="M480 64a32 32 0 0 1 64 0v384h384a32 32 0 0 1 0 64H544v384a32 32 0 0 1-64 0V512H96a32 32 0 0 1 0-64h384V64zm176 80a32 32 0 0 1 45.248 0l178.752 178.752a32 32 0 0 1-45.248 45.248L656 189.248A32 32 0 0 1 656 144zM323.248 656a32 32 0 0 1 45.248 45.248L189.248 880a32 32 0 1 1-45.248-45.248L323.248 656z"></path></svg></i>'
      trigger.classList.toggle('disabled', !hasResponseBody.value)
      trigger.setAttribute('title', hasResponseBody.value ? '快速提取' : '请先发送获取响应内容')
      trigger.onclick = (event) => {
        event.stopPropagation()
        openFastExtraction(index)
      }
    })
  })
}

onMounted(syncFastExtractionTriggers)
onUpdated(syncFastExtractionTriggers)

watch([activeAssertionId, hasResponseBody], () => {
  syncFastExtractionTriggers()
})
</script>

<template>
  <div ref="rootRef" class="assertion-editor" data-testid="api-assertion-editor">
    <aside class="assertion-sidebar">
      <div class="assertion-toolbar">
        <el-dropdown @command="handleAddCommand">
          <el-button type="primary" plain data-testid="assertion-add-button">添加断言</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="option in assertionOptions" :key="option.value" :command="option.value">
                {{ option.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <div v-if="assertions.length" class="assertion-list">
        <button
          v-for="(item, index) in assertions"
          :key="item.id"
          type="button"
          :class="['assertion-list-item', { active: item.id === activeAssertionId }]"
          @click="activeAssertionId = item.id ?? null"
        >
          <div class="assertion-list-item-main">
            <el-switch v-model="item.enabled" size="small" @click.stop />
            <div class="assertion-list-copy">
              <div class="assertion-list-title">{{ displayAssertionName(item) }}</div>
              <div class="assertion-list-meta">{{ assertionTypeLabel(item.assertionType) }}</div>
            </div>
          </div>
          <div class="assertion-list-actions">
            <button type="button" class="ghost-action" :disabled="index === 0" @click.stop="moveAssertion(item.id || '', -1)">↑</button>
            <button type="button" class="ghost-action" :disabled="index === assertions.length - 1" @click.stop="moveAssertion(item.id || '', 1)">↓</button>
          </div>
        </button>
      </div>
      <div v-else class="assertion-empty">暂无断言</div>
    </aside>

    <section class="assertion-detail" data-testid="assertion-detail">
      <template v-if="activeAssertion">
        <div class="assertion-detail-header">
          <div class="assertion-detail-fields">
            <el-input v-model="activeAssertion.name" placeholder="断言名称" data-testid="assertion-name-input" />
            <el-tag size="small" effect="plain">{{ assertionTypeLabel(activeAssertion.assertionType) }}</el-tag>
          </div>
          <div class="assertion-detail-actions">
            <el-button text type="primary" @click="duplicateAssertion(activeAssertion.id || '')">复制</el-button>
            <el-button text type="danger" @click="removeAssertion(activeAssertion.id || '')">删除</el-button>
          </div>
        </div>

        <template v-if="activeAssertion.assertionType === 'RESPONSE_CODE'">
          <div class="assertion-form-grid">
            <label>
              <span>条件</span>
              <el-select v-model="activeAssertion.condition" data-testid="assertion-code-condition">
                <el-option v-for="option in conditionOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </label>
            <label>
              <span>期望值</span>
              <el-input v-model="activeAssertion.expectedValue" placeholder="200" data-testid="assertion-code-expected" />
            </label>
          </div>
        </template>

        <template v-else-if="activeAssertion.assertionType === 'RESPONSE_HEADER'">
          <div class="assertion-table">
            <div v-for="(item, index) in activeAssertion.assertions" :key="`${activeAssertion.id}-header-${index}`" class="assertion-table-row" :data-testid="`assertion-header-row-${index}`">
              <el-checkbox v-model="item.enabled" />
              <el-input v-model="item.header" placeholder="响应头名" :data-testid="`assertion-header-name-${index}`" />
              <el-select v-model="item.condition">
                <el-option v-for="option in conditionOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
              <el-input v-model="item.expectedValue" placeholder="期望值" />
              <el-button text type="primary" @click="duplicateItem(activeAssertion.assertions || [], index)">复制</el-button>
              <el-button text type="danger" @click="removeItem(activeAssertion.assertions || [], index, { header: '', condition: 'EQUALS', expectedValue: '' })">删除</el-button>
            </div>
            <button type="button" class="add-row-button" @click="addItem(activeAssertion.assertions || (activeAssertion.assertions = []), { header: '' })">+ 添加响应头断言</button>
          </div>
        </template>

        <template v-else-if="activeAssertion.assertionType === 'RESPONSE_BODY'">
          <div class="assertion-body-toolbar">
            <el-radio-group v-model="activeAssertion.assertionBodyType" data-testid="assertion-body-type">
              <el-radio-button value="JSON_PATH">JSONPath</el-radio-button>
              <el-radio-button value="X_PATH">XPath</el-radio-button>
              <el-radio-button value="REGEX">Regex</el-radio-button>
            </el-radio-group>
            <el-select v-if="activeAssertion.assertionBodyType === 'X_PATH'" v-model="activeBodyGroup(activeAssertion).responseFormat" class="assertion-format-select" data-testid="assertion-xpath-format">
              <el-option label="XML" value="XML" />
              <el-option label="HTML" value="HTML" />
            </el-select>
          </div>
          <div class="assertion-table">
            <div v-for="(item, index) in activeBodyGroup(activeAssertion).assertions" :key="`${activeAssertion.id}-body-${activeAssertion.assertionBodyType}-${index}`" class="assertion-table-row assertion-table-row--body" :data-testid="`assertion-body-row-${index}`">
              <el-checkbox v-model="item.enabled" />
              <el-input v-model="item.expression" placeholder="表达式" :data-testid="`assertion-body-expression-${index}`" />
              <el-select v-model="item.condition">
                <el-option v-for="option in conditionOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
              <el-input v-model="item.expectedValue" placeholder="期望值" :data-testid="`assertion-body-expected-${index}`" />
              <el-button text type="primary" @click="testBodyExpression(activeAssertion, item)">测试</el-button>
              <el-button text type="primary" @click="duplicateItem(activeBodyGroup(activeAssertion).assertions, index)">复制</el-button>
              <el-button text type="danger" @click="removeItem(activeBodyGroup(activeAssertion).assertions, index, { expression: defaultExpression(activeAssertion.assertionBodyType || 'JSON_PATH'), condition: 'EQUALS', expectedValue: '' })">删除</el-button>
            </div>
            <button type="button" class="add-row-button" @click="addItem(activeBodyGroup(activeAssertion).assertions, { expression: defaultExpression(activeAssertion.assertionBodyType || 'JSON_PATH') })">+ 添加响应体断言</button>
          </div>
        </template>

        <template v-else-if="activeAssertion.assertionType === 'RESPONSE_TIME'">
          <div class="assertion-form-row">
            <span class="assertion-form-label">最大耗时(ms)</span>
            <el-input-number v-model="activeAssertion.expectedValue" :min="1" :step="100" data-testid="assertion-time-expected" />
          </div>
        </template>

        <template v-else-if="activeAssertion.assertionType === 'VARIABLE'">
          <div class="assertion-hint">可校验后置 SQL 写入的变量，例如 firstToken / id_1 / sqlRows。</div>
          <div class="assertion-table">
            <div v-for="(item, index) in activeAssertion.variableAssertionItems" :key="`${activeAssertion.id}-variable-${index}`" class="assertion-table-row" :data-testid="`assertion-variable-row-${index}`">
              <el-checkbox v-model="item.enabled" />
              <el-input v-model="item.variableName" placeholder="变量名" :data-testid="`assertion-variable-name-${index}`" />
              <el-select v-model="item.condition">
                <el-option v-for="option in conditionOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
              <el-input v-model="item.expectedValue" placeholder="期望值" :data-testid="`assertion-variable-expected-${index}`" />
              <el-button text type="primary" @click="duplicateItem(activeAssertion.variableAssertionItems || [], index)">复制</el-button>
              <el-button text type="danger" @click="removeItem(activeAssertion.variableAssertionItems || [], index, { variableName: '', condition: 'EQUALS', expectedValue: '' })">删除</el-button>
            </div>
            <button type="button" class="add-row-button" @click="addItem(activeAssertion.variableAssertionItems || (activeAssertion.variableAssertionItems = []), { variableName: '' })">+ 添加变量断言</button>
          </div>
        </template>

        <template v-else>
          <div class="editor-actions">
            <el-tag size="small">JavaScript</el-tag>
            <el-button size="small" @click="clearScript(activeAssertion)">清空</el-button>
            <el-button size="small" @click="formatScript(activeAssertion)">格式化</el-button>
          </div>
          <MonacoCodeEditor
            :model-value="activeAssertion.script || ''"
            language="text"
            height="360px"
            data-testid="assertion-script-editor"
            @update:model-value="updateActiveScript"
          />
          <div class="assertion-hint">可使用 getVar/log/fail/request/response，例如 if (response.statusCode !== 200) fail('状态码不正确')。</div>
        </template>
      </template>

      <div v-else class="assertion-empty">请选择一个断言进行编辑</div>
    </section>
    <ApiFastExtractionDrawer
      v-model:visible="fastExtractionVisible"
      :mode="activeFastExtractionMode"
      :config="activeFastExtractionConfig"
      :response="props.latestResponse?.body || ''"
      :show-more-setting="false"
      @apply="handleFastExtractionApply"
    />
  </div>
</template>

<style scoped>
.assertion-editor {
  display: grid;
  grid-template-columns: minmax(220px, 260px) minmax(0, 1fr);
  gap: 12px;
  min-height: 420px;
}

.assertion-sidebar,
.assertion-detail {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.assertion-sidebar {
  display: flex;
  flex-direction: column;
}

.assertion-toolbar {
  display: flex;
  justify-content: flex-start;
  padding: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.assertion-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
}

.assertion-list-item {
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

.assertion-list-item.active {
  border-color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 8%, white);
}

.assertion-list-item-main,
.assertion-detail-header,
.assertion-detail-fields,
.assertion-list-actions,
.assertion-detail-actions,
.assertion-form-row,
.editor-actions,
.assertion-body-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.assertion-list-copy,
.assertion-detail-fields {
  min-width: 0;
}

.assertion-list-title,
.assertion-list-meta {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assertion-list-meta,
.assertion-form-label,
.assertion-hint,
.assertion-form-grid span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.assertion-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
}

.assertion-detail-header {
  justify-content: space-between;
  flex-wrap: wrap;
}

.assertion-detail-fields {
  flex: 1;
  min-width: 280px;
}

.assertion-detail-fields :deep(.el-input) {
  flex: 1;
}

.assertion-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.assertion-form-grid label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.assertion-table {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.assertion-table-row {
  display: grid;
  grid-template-columns: auto minmax(160px, 1fr) 170px minmax(160px, 1fr) auto auto;
  gap: 8px;
  align-items: center;
}

.assertion-table-row--body {
  grid-template-columns: auto minmax(200px, 1.3fr) 170px minmax(160px, 1fr) auto auto auto;
}

.fast-extraction-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #165dff;
}

.fast-extraction-trigger.disabled {
  color: var(--el-text-color-placeholder);
}

:deep(.fast-extraction-dom-trigger) {
  margin-left: 8px;
  cursor: pointer;
}

:deep(.fast-extraction-dom-trigger.disabled) {
  cursor: not-allowed;
}

:deep(.fast-extraction-dom-trigger .el-icon) {
  display: inline-flex;
  width: 14px;
  height: 14px;
}

.assertion-format-select {
  width: 120px;
}

.ghost-action,
.add-row-button {
  border: none;
  background: transparent;
  color: var(--el-color-primary);
  cursor: pointer;
  padding: 0;
}

.ghost-action {
  color: var(--el-text-color-secondary);
}

.ghost-action:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.assertion-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 1100px) {
  .assertion-editor,
  .assertion-form-grid,
  .assertion-table-row,
  .assertion-table-row--body {
    grid-template-columns: 1fr;
  }
}
</style>
