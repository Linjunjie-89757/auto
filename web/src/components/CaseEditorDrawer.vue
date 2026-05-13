<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight, FolderOpened } from '@element-plus/icons-vue'
import type { CaseDirectoryNode } from '../types/api'

type EditableCaseForm = {
  workspaceCode: string
  directoryId?: number | null
  title: string
  priority: string
  precondition: string
  steps: string
  expectedResult: string
}

type PathPickerTreeNode = {
  key: string
  id: number | null
  name: string
  fullPath: string
  selectable: boolean
  children: PathPickerTreeNode[]
}

const props = withDefaults(defineProps<{
  modelValue: boolean
  title: string
  form: EditableCaseForm
  saving: boolean
  canSubmit: boolean
  submitText?: string
  workspaceName: string
  directoryTree: CaseDirectoryNode[]
  modulePickerLoading?: boolean
  showNavigator?: boolean
  canGoPrev?: boolean
  canGoNext?: boolean
  currentIndex?: number
  totalCount?: number
  resolveDirectoryPath: (directoryId: number | null) => string
}>(), {
  submitText: '保存',
  modulePickerLoading: false,
  showNavigator: false,
  canGoPrev: false,
  canGoNext: false,
  currentIndex: 0,
  totalCount: 0,
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'submit'): void
  (event: 'prev'): void
  (event: 'next'): void
  (event: 'open-module-picker'): void
}>()

const modulePickerVisible = ref(false)
const modulePickerKeyword = ref('')
const modulePickerSelection = ref<number | null>(null)

const modulePath = computed(() => {
  if (!props.form.workspaceCode) {
    return '-'
  }
  const resolvedPath = props.resolveDirectoryPath(props.form.directoryId ?? null)
  return resolvedPath || props.workspaceName || props.form.workspaceCode
})

const modulePickerTree = computed<PathPickerTreeNode[]>(() => {
  if (!props.form.workspaceCode || !props.workspaceName) {
    return []
  }
  const appendNodes = (nodes: CaseDirectoryNode[], prefix = ''): PathPickerTreeNode[] => nodes.map((node) => {
    const fullPath = prefix ? `${prefix}/${node.name}` : node.name
    return {
      key: `dir:${node.id}`,
      id: node.id,
      name: node.name,
      fullPath,
      selectable: true,
      children: appendNodes(node.children ?? [], fullPath),
    }
  })
  return [{
    key: `workspace:${props.form.workspaceCode}`,
    id: null,
    name: props.workspaceName,
    fullPath: props.workspaceName,
    selectable: false,
    children: appendNodes(props.directoryTree),
  }]
})

const filteredModulePickerTree = computed<PathPickerTreeNode[]>(() => {
  const keyword = modulePickerKeyword.value.trim().toLowerCase()
  const filterNodes = (nodes: PathPickerTreeNode[]): PathPickerTreeNode[] => nodes.reduce<PathPickerTreeNode[]>((result, node) => {
    const children = filterNodes(node.children ?? [])
    const matched = !keyword || node.fullPath.toLowerCase().includes(keyword) || node.name.toLowerCase().includes(keyword)
    if (matched || children.length) {
      result.push({
        ...node,
        children,
      })
    }
    return result
  }, [])
  return filterNodes(modulePickerTree.value)
})

const modulePickerSelectedFullPath = computed(() => {
  if (!props.form.workspaceCode) {
    return ''
  }
  return props.resolveDirectoryPath(modulePickerSelection.value)
})

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) {
      modulePickerVisible.value = false
      return
    }
    modulePickerSelection.value = props.form.directoryId ?? null
    modulePickerKeyword.value = ''
  },
)

watch(
  () => props.form.directoryId,
  (directoryId) => {
    if (!modulePickerVisible.value) {
      modulePickerSelection.value = directoryId ?? null
    }
  },
)

function openModulePicker() {
  if (!props.form.workspaceCode) {
    return
  }
  modulePickerKeyword.value = ''
  modulePickerSelection.value = props.form.directoryId ?? null
  modulePickerVisible.value = true
  emit('open-module-picker')
}

function handleModulePickerNodeSelect(node: PathPickerTreeNode) {
  if (!node.selectable) {
    return
  }
  modulePickerSelection.value = node.id
}

function confirmModulePickerSelection() {
  props.form.directoryId = modulePickerSelection.value
  modulePickerVisible.value = false
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    size="720px"
    class="case-editor-drawer"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-position="top" class="case-editor-form">
      <el-form-item label="用例名称" required class="case-editor-form-item">
        <el-input v-model="form.title" placeholder="请输入用例名称" />
      </el-form-item>
      <el-form-item label="用例模块" class="case-editor-form-item">
        <el-input :model-value="modulePath" readonly class="directory-path-input directory-path-input-with-action">
          <template #suffix>
            <button type="button" class="path-action-icon-button" aria-label="修改目录" @click="openModulePicker">
              <el-icon><FolderOpened /></el-icon>
            </button>
          </template>
        </el-input>
      </el-form-item>
      <el-form-item label="优先级" class="case-editor-form-item">
        <el-segmented v-model="form.priority" :options="['P0', 'P1', 'P2', 'P3']" />
      </el-form-item>
      <el-form-item label="前置条件" class="case-editor-form-item">
        <el-input v-model="form.precondition" type="textarea" :rows="3" resize="vertical" />
      </el-form-item>
      <el-form-item label="测试步骤" class="case-editor-form-item">
        <el-input v-model="form.steps" type="textarea" :rows="6" resize="vertical" />
      </el-form-item>
      <el-form-item label="预期结果" class="case-editor-form-item">
        <el-input v-model="form.expectedResult" type="textarea" :rows="4" resize="vertical" />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="drawer-footer">
        <div v-if="showNavigator" class="drawer-nav">
          <el-button :disabled="!canGoPrev" @click="emit('prev')">
            <el-icon><ArrowLeft /></el-icon>
            上一条
          </el-button>
          <div class="drawer-nav-counter">{{ currentIndex }}/{{ totalCount }}</div>
          <el-button :disabled="!canGoNext" @click="emit('next')">
            下一条
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <div class="drawer-submit">
          <el-button @click="emit('update:modelValue', false)">取消</el-button>
          <el-button type="primary" :loading="saving" :disabled="!canSubmit" @click="emit('submit')">
            {{ submitText }}
          </el-button>
        </div>
      </div>
    </template>
  </el-drawer>

  <el-dialog v-model="modulePickerVisible" width="760px" destroy-on-close class="path-picker-dialog">
    <template #header>
      <div class="path-picker-title">选择保存路径</div>
    </template>
    <div class="path-picker-layout">
      <div class="path-picker-current">
        <span class="path-picker-current-label">当前保存路径</span>
        <span class="path-picker-current-value">{{ modulePath }}</span>
      </div>
      <el-input
        v-model="modulePickerKeyword"
        clearable
        placeholder="搜索目录名称"
        class="path-picker-search"
      />
      <div class="path-picker-tree-panel">
        <div v-if="modulePickerLoading" class="path-picker-empty">
          正在加载目录...
        </div>
        <div v-else-if="!filteredModulePickerTree.length" class="path-picker-empty">
          未找到匹配的目录
        </div>
        <el-tree
          v-else
          :data="filteredModulePickerTree"
          node-key="key"
          highlight-current
          :expand-on-click-node="false"
          :default-expanded-keys="form.workspaceCode ? [`workspace:${form.workspaceCode}`] : []"
          :current-node-key="modulePickerSelection != null ? `dir:${modulePickerSelection}` : undefined"
          class="path-picker-tree"
          @node-click="handleModulePickerNodeSelect"
        >
          <template #default="{ data }">
            <div class="path-picker-tree-node" :class="{ 'is-workspace': !data.selectable }">
              <span class="path-picker-tree-node-label">{{ data.name }}</span>
            </div>
          </template>
        </el-tree>
      </div>
      <div class="path-picker-selected-panel">
        <div class="path-picker-selected-label">已选路径</div>
        <div class="path-picker-selected-value">
          {{ modulePickerSelectedFullPath || '请在上方目录树中选择保存路径' }}
        </div>
      </div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="modulePickerVisible = false">取消</el-button>
        <el-button type="primary" :icon="FolderOpened" @click="confirmModulePickerSelection">
          确认修改
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.case-editor-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 18px 20px 0;
  align-items: flex-start;
}

.case-editor-drawer :deep(.el-drawer__body) {
  padding: 12px 20px 0;
}

.case-editor-drawer :deep(.el-drawer__footer) {
  padding: 16px 20px 20px;
  border-top: 1px solid var(--line-soft);
}

.case-editor-drawer :deep(.el-input__wrapper),
.case-editor-drawer :deep(.el-textarea__inner) {
  border-radius: 10px;
}

.case-editor-drawer :deep(.el-textarea__inner) {
  line-height: 1.7;
}

.case-editor-form {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

.case-editor-form-item {
  margin-bottom: 0;
}

.directory-path-input {
  width: 100%;
}

.directory-path-input :deep(.el-input__wrapper) {
  cursor: default;
}

.directory-path-input-with-action :deep(.el-input__suffix) {
  margin-left: 8px;
}

.path-action-icon-button {
  width: 24px;
  height: 24px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #98a2b3;
  cursor: pointer;
}

.path-action-icon-button:focus-visible {
  outline: 2px solid rgba(23, 92, 211, 0.24);
  outline-offset: 1px;
}

.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.drawer-nav,
.drawer-submit {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.drawer-submit {
  margin-left: auto;
}

.drawer-nav-counter {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 64px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #ffffff;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  color: #344054;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.path-picker-title {
  font-size: 16px;
  font-weight: 600;
  color: #101828;
}

.path-picker-layout {
  display: grid;
  gap: 16px;
}

.path-picker-current {
  display: grid;
  gap: 6px;
}

.path-picker-current-label,
.path-picker-selected-label {
  font-size: 12px;
  color: #667085;
  line-height: 1.5;
}

.path-picker-current-value,
.path-picker-tree-node-label,
.path-picker-selected-value {
  font-size: 13px;
  line-height: 1.7;
  color: #344054;
  word-break: break-word;
}

.path-picker-tree-panel {
  min-height: 320px;
  max-height: 360px;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: #fff;
}

.path-picker-empty {
  min-height: 296px;
  display: grid;
  place-items: center;
  font-size: 13px;
  color: #98a2b3;
  text-align: center;
}

.path-picker-tree-node {
  display: flex;
  align-items: center;
  min-height: 34px;
  width: 100%;
}

.path-picker-tree-node.is-workspace {
  font-weight: 700;
  color: #101828;
  cursor: default;
}

.path-picker-selected-panel {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid rgba(15, 23, 42, 0.06);
}

@media (max-width: 1200px) {
  .drawer-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .drawer-nav,
  .drawer-submit {
    justify-content: space-between;
  }
}
</style>
