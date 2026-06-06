<script setup lang="ts">
import { computed } from 'vue'
import BugEditorForm from './BugEditorForm.vue'
import type { UserItem } from '../types/api'

type BugEditorFormModel = {
  workspaceCode: string
  title: string
  priority: string
  severity: string
  assigneeId: number | null
  tags: string[]
  description: string
}

type BugSourceContext = {
  caseNo: string
  caseTitle: string
  modulePath: string
  executionStatus: string
  actualResult: string
  precondition?: string
  steps?: string
  expectedResult?: string
}

type PendingBugFile = {
  id: string
  name: string
  size: number
  kind: 'attachment' | 'screenshot'
  previewUrl?: string | null
}

const props = withDefaults(defineProps<{
  modelValue: boolean
  title?: string
  form: BugEditorFormModel
  saving: boolean
  canSubmit: boolean
  users: UserItem[]
  sourceContext?: BugSourceContext | null
  pendingFiles?: PendingBugFile[]
}>(), {
  title: '创建缺陷',
  sourceContext: null,
  pendingFiles: () => [],
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'submit'): void
  (event: 'submit-and-continue'): void
  (event: 'add-files', files: File[]): void
  (event: 'remove-file', id: string): void
}>()

const isEditMode = computed(() => props.title?.includes('编辑'))
const primarySubmitText = computed(() => (isEditMode.value ? '保存' : '创建'))
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    size="1198px"
    class="bug-editor-drawer"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="bug-editor-header">
        <div class="bug-editor-header-title">{{ title }}</div>
      </div>
    </template>

    <BugEditorForm
      :form="form"
      :saving="saving"
      :users="users"
      :pending-files="pendingFiles"
      @add-files="emit('add-files', $event)"
      @remove-file="emit('remove-file', $event)"
    />

    <template #footer>
      <div class="bug-editor-footer">
        <el-button @click="emit('update:modelValue', false)">取消</el-button>
        <el-button
          v-if="!isEditMode"
          :loading="saving"
          :disabled="!canSubmit"
          @click="emit('submit-and-continue')"
        >
          保存并继续创建
        </el-button>
        <el-button type="primary" :loading="saving" :disabled="!canSubmit" @click="emit('submit')">
          {{ primarySubmitText }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.bug-editor-drawer :deep(.el-drawer) {
  min-width: 1198px;
}

.bug-editor-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: var(--ath-space-5) var(--ath-space-6) var(--ath-space-3);
  border-bottom: 1px solid var(--ath-border);
}

.bug-editor-drawer :deep(.el-drawer__body) {
  padding: 0;
  overflow: auto;
  background: var(--ath-bg-panel);
}

.bug-editor-drawer :deep(.el-drawer__footer) {
  padding: var(--ath-space-3) var(--ath-space-6);
  border-top: 1px solid var(--ath-border);
  background: var(--ath-bg-panel);
}

.bug-editor-header {
  display: flex;
  align-items: center;
  width: 100%;
}

.bug-editor-header-title {
  color: var(--ath-text-strong);
  font-size: var(--ath-font-lg);
  font-weight: var(--ath-weight-semibold);
  line-height: var(--ath-line-lg);
}

.bug-editor-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--ath-space-3);
}

.bug-editor-footer :deep(.el-button) {
  height: var(--ath-control-height-sm);
  margin-left: 0;
  border-radius: var(--ath-radius-sm);
}

@media (max-width: 1280px) {
  .bug-editor-drawer :deep(.el-drawer) {
    min-width: auto;
  }
}
</style>
