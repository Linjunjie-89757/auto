<script setup lang="ts">
import { computed } from 'vue'
import { Close } from '@element-plus/icons-vue'

const props = defineProps<{
  modelValue: boolean
  title: string
  subtitle: string
  summaryName: string
  method: string
  path: string
  caseName: string
  priority: string
  priorityOptions: readonly string[]
  status: string
  statusOptions: readonly string[]
  tagsInput: string
  canDebug: boolean
  canWrite: boolean
  saving: boolean
  isEdit: boolean
  readOnly?: boolean
  primaryActionLabel?: string
  showFooter?: boolean
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'update:caseName', value: string): void
  (event: 'update:priority', value: string): void
  (event: 'update:status', value: string): void
  (event: 'update:tagsInput', value: string): void
  (event: 'requestClose'): void
  (event: 'debug'): void
  (event: 'create'): void
  (event: 'save'): void
}>()

const submitLabel = computed(() => (props.isEdit ? '保存' : '创建'))
const resolvedPrimaryActionLabel = computed(() => props.primaryActionLabel || '发送')
const resolvedShowFooter = computed(() => props.showFooter ?? true)
const resolvedReadOnly = computed(() => props.readOnly === true)

function handleDrawerModelValueChange(value: boolean) {
  if (!value) {
    emit('requestClose')
    return
  }
  emit('update:modelValue', value)
}

function handleCloseClick() {
  emit('requestClose')
}

function updateCaseName(value: string | number) {
  emit('update:caseName', String(value))
}

function updatePriority(value: string | number) {
  emit('update:priority', String(value))
}

function updateStatus(value: string | number) {
  emit('update:status', String(value))
}

function updateTagsInput(value: string | number) {
  emit('update:tagsInput', String(value))
}

function handleSubmitClick() {
  if (props.isEdit) {
    emit('save')
    return
  }
  emit('create')
}
</script>

<template>
  <el-drawer
    :model-value="props.modelValue"
    append-to-body
    destroy-on-close
    :with-header="false"
    :show-close="false"
    close-on-click-modal
    close-on-press-escape
    modal-class="api-case-drawer-modal"
    size="894px"
    class="api-case-drawer"
    @update:model-value="handleDrawerModelValueChange"
  >
    <div class="api-case-drawer-shell">
      <div class="api-case-drawer-top">
        <div class="api-case-drawer-header">
          <div class="api-case-drawer-title">{{ props.title }}</div>
          <div class="api-case-drawer-subtitle">{{ props.subtitle }}</div>
        </div>
        <el-button text class="api-case-drawer-close" @click="handleCloseClick">
          <el-icon><Close /></el-icon>
        </el-button>
      </div>

      <div class="api-case-drawer-scroll">
        <div class="api-case-drawer-summary-card">
          <div class="api-case-drawer-summary-main">
            <div class="api-case-drawer-summary-meta">
              <span :class="['api-case-drawer-method-tag', `request-method-${props.method.toLowerCase()}`]">{{ props.method }}</span>
              <span class="api-case-drawer-summary-path">{{ props.path || '未设置路径' }}</span>
            </div>
          </div>
        </div>

        <div class="api-case-drawer-name-row">
          <el-input
            :model-value="props.caseName"
            :disabled="resolvedReadOnly"
            maxlength="255"
            show-word-limit
            placeholder="请输入用例名称"
            class="api-case-drawer-name-input"
            @update:model-value="updateCaseName"
          />
          <el-button type="primary" :disabled="!props.canDebug" :loading="props.saving" @click="emit('debug')">
            {{ resolvedPrimaryActionLabel }}
          </el-button>
        </div>

        <div class="api-case-drawer-meta-row">
          <el-select
            :model-value="props.priority"
            :disabled="resolvedReadOnly"
            class="api-case-drawer-meta-field"
            placeholder="优先级"
            @update:model-value="updatePriority"
          >
            <el-option v-for="item in props.priorityOptions" :key="item" :label="item" :value="item" />
          </el-select>
          <el-select
            :model-value="props.status"
            :disabled="resolvedReadOnly"
            class="api-case-drawer-meta-field"
            placeholder="状态"
            @update:model-value="updateStatus"
          >
            <el-option v-for="item in props.statusOptions" :key="item" :label="item" :value="item" />
          </el-select>
          <el-input
            :model-value="props.tagsInput"
            :disabled="resolvedReadOnly"
            class="api-case-drawer-tags-field"
            placeholder="添加标签，回车结束"
            @update:model-value="updateTagsInput"
          />
        </div>

        <div class="api-case-drawer-tabs">
          <slot name="tabs" />
        </div>
        <div class="api-case-drawer-body">
          <slot name="body" />
        </div>
        <div class="api-case-drawer-response">
          <slot name="response" />
        </div>
      </div>

      <div v-if="resolvedShowFooter" class="api-case-drawer-footer">
        <el-button @click="handleCloseClick">取消</el-button>
        <el-button type="primary" :disabled="!props.canWrite" :loading="props.saving" @click="handleSubmitClick">
          {{ submitLabel }}
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
:global(.api-case-drawer-modal) {
  background: rgba(15, 23, 42, 0.28);
}

.api-case-drawer :deep(.el-drawer) {
  max-width: calc(100vw - 24px);
  overflow: hidden;
  background: #fff;
  box-shadow: -24px 0 56px rgba(15, 23, 42, 0.18);
}

.api-case-drawer :deep(.el-drawer__body) {
  padding: 0;
  overflow: hidden;
}

.api-case-drawer-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: #fff;
}

.api-case-drawer-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px 10px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: #fff;
}

.api-case-drawer-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.api-case-drawer-title {
  font-size: 17px;
  font-weight: 600;
  line-height: 24px;
  color: #101828;
}

.api-case-drawer-subtitle {
  font-size: 13px;
  line-height: 20px;
  color: #667085;
}

.api-case-drawer-close {
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  padding: 0;
  border-radius: 8px;
  color: rgba(102, 112, 133, 0.4);
  transition: color 0.18s ease, background-color 0.18s ease;
}

.api-case-drawer-close:hover,
.api-case-drawer-close:focus-visible {
  color: #344054;
  background: #f2f4f7;
}

.api-case-drawer-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding: 14px 16px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  scrollbar-width: thin;
  scrollbar-color: #cbd5e1 transparent;
}

.api-case-drawer-scroll::-webkit-scrollbar {
  width: 8px;
}

.api-case-drawer-scroll::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.72);
}

.api-case-drawer-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.api-case-drawer-summary-card {
  padding: 0 2px;
}

.api-case-drawer-summary-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.api-case-drawer-summary-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.api-case-drawer-method-tag {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  height: 24px;
  padding: 0 8px;
  border: 1px solid currentColor;
  border-radius: 4px;
  background: #fff;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
}

.api-case-drawer-method-tag.request-method-get {
  color: #16a34a;
}

.api-case-drawer-method-tag.request-method-post {
  color: #f97316;
}

.api-case-drawer-method-tag.request-method-put,
.api-case-drawer-method-tag.request-method-options,
.api-case-drawer-method-tag.request-method-head {
  color: #3b82f6;
}

.api-case-drawer-method-tag.request-method-delete {
  color: #dc2626;
}

.api-case-drawer-method-tag.request-method-patch {
  color: #ec4899;
}

.api-case-drawer-method-tag.request-method-trace {
  color: #8b5cf6;
}

.api-case-drawer-summary-path {
  min-width: 0;
  font-size: 13px;
  line-height: 20px;
  color: #475467;
  word-break: break-all;
}

.api-case-drawer-name-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
}

.api-case-drawer-name-input :deep(.el-input__wrapper),
.api-case-drawer-meta-field :deep(.el-select__wrapper),
.api-case-drawer-tags-field :deep(.el-input__wrapper) {
  min-height: 36px;
}

.api-case-drawer-meta-row {
  display: grid;
  grid-template-columns: 140px 140px minmax(0, 1fr);
  gap: 12px;
}

.api-case-drawer-tabs,
.api-case-drawer-body,
.api-case-drawer-response {
  min-width: 0;
}

.api-case-drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid rgba(15, 23, 42, 0.06);
  box-shadow: 0 -8px 20px rgba(15, 23, 42, 0.04);
  background: rgba(255, 255, 255, 0.96);
}

@media (max-width: 960px) {
  .api-case-drawer-meta-row {
    grid-template-columns: 1fr;
  }

  .api-case-drawer-name-row {
    grid-template-columns: 1fr;
  }
}
</style>
