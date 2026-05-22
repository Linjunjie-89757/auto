<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import MonacoCodeEditor from './MonacoCodeEditor.vue'
import type {
  FastExtractionConfig,
  FastExtractionMode,
} from '../utils/apiFastExtraction'
import {
  buildJsonPathSegment,
  buildXPathSegment,
  testFastExtraction,
} from '../utils/apiFastExtraction'

interface ExplorerNode {
  id: string
  label: string
  path: string
  preview?: string
  children?: ExplorerNode[]
}

const props = withDefaults(defineProps<{
  visible: boolean
  response?: string | null
  mode: FastExtractionMode
  config?: FastExtractionConfig
  showMoreSetting?: boolean
}>(), {
  response: '',
  config: () => ({}),
  showMoreSetting: true,
})

const emit = defineEmits<{
  'update:visible': [value: boolean]
  apply: [config: FastExtractionConfig, matchResult: string[]]
}>()

const drawerVisible = computed({
  get: () => props.visible,
  set: value => emit('update:visible', value),
})

const expressionForm = ref<FastExtractionConfig>(createForm())
const matchResult = ref<string[]>([])
const matched = ref(false)
const jsonParseError = ref('')
const xmlParseError = ref('')

function createForm(): FastExtractionConfig {
  return {
    extractType: props.mode,
    expression: props.config?.expression || defaultExpression(props.mode),
    expressionMatchingRule: props.config?.expressionMatchingRule || 'EXPRESSION',
    responseFormat: props.config?.responseFormat || (props.mode === 'X_PATH' ? 'XML' : 'JSON'),
  }
}

function defaultExpression(mode: FastExtractionMode) {
  if (mode === 'X_PATH') {
    return '/root'
  }
  if (mode === 'REGEX') {
    return '.+'
  }
  return '$'
}

function stringifyPreview(value: unknown): string {
  if (value === null || value === undefined) {
    return ''
  }
  if (typeof value === 'string') {
    return value.length > 80 ? `${value.slice(0, 80)}...` : value
  }
  try {
    const text = JSON.stringify(value)
    return text.length > 80 ? `${text.slice(0, 80)}...` : text
  }
  catch {
    return String(value)
  }
}

function buildJsonTree(value: unknown, label = '$', path = '$', idPrefix = 'json'): ExplorerNode {
  if (Array.isArray(value)) {
    return {
      id: `${idPrefix}-${path}`,
      label,
      path,
      preview: `Array(${value.length})`,
      children: value.map((item, index) => {
        const childPath = buildJsonPathSegment(path, index)
        return buildJsonTree(item, `[${index}]`, childPath, idPrefix)
      }),
    }
  }

  if (value && typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>)
    return {
      id: `${idPrefix}-${path}`,
      label,
      path,
      preview: `Object(${entries.length})`,
      children: entries.map(([key, item]) => {
        const childPath = buildJsonPathSegment(path, key)
        return buildJsonTree(item, key, childPath, idPrefix)
      }),
    }
  }

  return {
    id: `${idPrefix}-${path}`,
    label,
    path,
    preview: stringifyPreview(value),
  }
}

const jsonTreeData = computed(() => {
  if (expressionForm.value.extractType !== 'JSON_PATH') {
    return []
  }
  jsonParseError.value = ''
  try {
    const parsed = JSON.parse(props.response || '{}')
    return [buildJsonTree(parsed)]
  }
  catch (error) {
    jsonParseError.value = (error as Error).message
    return []
  }
})

function buildXmlTree(root: Element): ExplorerNode {
  const siblings = root.parentElement
    ? Array.from(root.parentElement.children).filter(child => child.tagName === root.tagName)
    : [root]
  const index = siblings.indexOf(root) + 1
  const parentPath = root.parentElement ? buildElementPath(root.parentElement) : ''
  const path = buildXPathSegment(parentPath, root.tagName.toLowerCase(), siblings.length > 1 ? index : undefined)
  const elementChildren = Array.from(root.children)

  return {
    id: `xml-${path}`,
    label: root.tagName.toLowerCase(),
    path,
    preview: elementChildren.length ? `${elementChildren.length} children` : stringifyPreview(root.textContent?.trim() || ''),
    children: elementChildren.map(child => buildXmlTree(child)),
  }
}

function buildElementPath(element: Element): string {
  const chain: Element[] = []
  let current: Element | null = element
  while (current) {
    chain.unshift(current)
    current = current.parentElement
  }

  let path = ''
  chain.forEach((node) => {
    const siblings = node.parentElement
      ? Array.from(node.parentElement.children).filter(child => child.tagName === node.tagName)
      : [node]
    const index = siblings.indexOf(node) + 1
    path = buildXPathSegment(path, node.tagName.toLowerCase(), siblings.length > 1 ? index : undefined)
  })
  return path
}

const xmlTreeData = computed(() => {
  if (expressionForm.value.extractType !== 'X_PATH') {
    return []
  }
  xmlParseError.value = ''
  try {
    const parserType = expressionForm.value.responseFormat === 'HTML' ? 'text/html' : 'text/xml'
    const doc = new DOMParser().parseFromString(props.response || '', parserType)
    if (parserType === 'text/xml' && doc.querySelector('parsererror')) {
      throw new Error(doc.querySelector('parsererror')?.textContent || 'XML parse error')
    }
    const root = parserType === 'text/html' ? doc.body : doc.documentElement
    if (!root) {
      throw new Error('No root node found')
    }
    return [buildXmlTree(root)]
  }
  catch (error) {
    xmlParseError.value = (error as Error).message
    return []
  }
})

function handleNodePick(data: ExplorerNode) {
  expressionForm.value.expression = data.path
}

function testExpression() {
  try {
    matchResult.value = testFastExtraction(props.response || '', expressionForm.value)
    matched.value = true
  }
  catch {
    matchResult.value = []
    matched.value = true
  }
}

function closeDrawer() {
  drawerVisible.value = false
}

function confirmApply() {
  if (!expressionForm.value.expression?.trim()) {
    return
  }
  if (!matched.value) {
    testExpression()
  }
  emit('apply', {
    ...expressionForm.value,
    extractType: expressionForm.value.extractType || props.mode,
  }, [...matchResult.value])
  drawerVisible.value = false
}

watch(
  () => [props.visible, props.mode, props.config] as const,
  ([visible]) => {
    if (!visible) {
      return
    }
    expressionForm.value = createForm()
    matchResult.value = []
    matched.value = false
  },
  { deep: true },
)
</script>

<template>
  <el-drawer
    v-model="drawerVisible"
    title="快速提取"
    size="720px"
    append-to-body
    destroy-on-close
  >
    <div class="fast-extraction-drawer">
      <div v-if="expressionForm.extractType === 'REGEX'" class="fast-extraction-preview">
        <MonacoCodeEditor
          :model-value="props.response || ''"
          language="text"
          :read-only="true"
          :show-format-button="false"
          height="336px"
        />
      </div>

      <div v-else-if="expressionForm.extractType === 'JSON_PATH'" class="fast-extraction-tree-shell">
        <div v-if="jsonParseError" class="fast-extraction-empty">
          当前响应内容不是合法 JSON：{{ jsonParseError }}
        </div>
        <el-tree
          v-else
          :data="jsonTreeData"
          node-key="id"
          default-expand-all
          empty-text="暂无可提取内容"
          class="fast-extraction-tree"
          @node-click="handleNodePick"
        >
          <template #default="{ data }">
            <div class="fast-extraction-tree-node">
              <span class="fast-extraction-tree-label">{{ data.label }}</span>
              <span v-if="data.preview" class="fast-extraction-tree-preview">{{ data.preview }}</span>
            </div>
          </template>
        </el-tree>
      </div>

      <div v-else class="fast-extraction-tree-shell">
        <div v-if="xmlParseError" class="fast-extraction-empty">
          当前响应内容不是合法 XML/HTML：{{ xmlParseError }}
        </div>
        <el-tree
          v-else
          :data="xmlTreeData"
          node-key="id"
          default-expand-all
          empty-text="暂无可提取内容"
          class="fast-extraction-tree"
          @node-click="handleNodePick"
        >
          <template #default="{ data }">
            <div class="fast-extraction-tree-node">
              <span class="fast-extraction-tree-label">{{ data.label }}</span>
              <span v-if="data.preview" class="fast-extraction-tree-preview">{{ data.preview }}</span>
            </div>
          </template>
        </el-tree>
      </div>

      <div class="fast-extraction-form">
        <label class="fast-extraction-field">
          <span class="fast-extraction-label">表达式</span>
          <div class="fast-extraction-input-row">
            <el-input
              v-model="expressionForm.expression"
              placeholder="请输入表达式"
              maxlength="255"
            />
            <el-button type="primary" plain :disabled="!expressionForm.expression?.trim()" @click="testExpression">
              测试
            </el-button>
          </div>
        </label>

        <div v-if="showMoreSetting" class="fast-extraction-settings">
          <label v-if="expressionForm.extractType === 'REGEX'" class="fast-extraction-field">
            <span class="fast-extraction-label">正则匹配规则</span>
            <el-radio-group v-model="expressionForm.expressionMatchingRule" size="small">
              <el-radio-button label="EXPRESSION">完整匹配</el-radio-button>
              <el-radio-button label="GROUP">分组 1</el-radio-button>
            </el-radio-group>
          </label>
        </div>
      </div>

      <div class="fast-extraction-match">
        <div class="fast-extraction-match-title">匹配结果</div>
        <div v-if="matched && !matchResult.length" class="fast-extraction-empty">未匹配到结果</div>
        <pre v-else-if="matchResult.length" class="fast-extraction-match-pre">{{ matchResult.join('\n') }}</pre>
        <div v-else class="fast-extraction-empty">点击测试查看匹配结果</div>
      </div>
    </div>

    <template #footer>
      <div class="fast-extraction-footer">
        <el-button @click="closeDrawer">取消</el-button>
        <el-button type="primary" :disabled="!expressionForm.expression?.trim()" @click="confirmApply">确认</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.fast-extraction-drawer {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.fast-extraction-preview,
.fast-extraction-tree-shell {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: #fff;
}

.fast-extraction-tree-shell {
  max-height: 400px;
  overflow: auto;
}

.fast-extraction-tree {
  padding: 12px;
}

.fast-extraction-tree-node {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.fast-extraction-tree-label {
  color: var(--el-text-color-primary);
  font-family: var(--el-font-family-monospace, Consolas, monospace);
}

.fast-extraction-tree-preview {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fast-extraction-form,
.fast-extraction-match {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.fast-extraction-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.fast-extraction-label,
.fast-extraction-match-title {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 500;
}

.fast-extraction-input-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.fast-extraction-settings {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.fast-extraction-match {
  padding: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}

.fast-extraction-match-pre {
  margin: 0;
  max-height: 280px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--el-font-family-monospace, Consolas, monospace);
  color: #165dff;
}

.fast-extraction-empty {
  padding: 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.fast-extraction-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  width: 100%;
}
</style>
