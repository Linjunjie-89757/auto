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
  padding: 13px 14px;
  border-bottom: 1px solid #ebeef5;
}

.bug-editor-drawer :deep(.el-drawer__body) {
  padding: 0;
  overflow: auto;
}

.bug-editor-drawer :deep(.el-drawer__footer) {
  padding: 14px 20px 16px;
  border-top: 1px solid #ebeef5;
}

.bug-editor-header {
  display: flex;
  align-items: center;
  width: 100%;
}

.bug-editor-header-title {
  font-size: 16px;
  font-weight: 700;
  line-height: 24px;
  font-family: "Helvetica Neue", Arial, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  color: #323233;
}

.bug-editor-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 1280px) {
  .bug-editor-drawer :deep(.el-drawer) {
    min-width: auto;
  }
}
</style>
