<script setup lang="ts">
import type { UserItem } from '../types/api'

type BugEditorForm = {
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
}

const props = withDefaults(defineProps<{
  modelValue: boolean
  title?: string
  form: BugEditorForm
  saving: boolean
  canSubmit: boolean
  users: UserItem[]
  sourceContext?: BugSourceContext | null
}>(), {
  title: '新建缺陷',
  sourceContext: null,
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'submit'): void
}>()

const priorityOptions = ['P0', 'P1', 'P2', 'P3']
const severityOptions = [
  { label: '致命', value: 'CRITICAL' },
  { label: '高', value: 'HIGH' },
  { label: '中', value: 'MEDIUM' },
  { label: '低', value: 'LOW' },
]
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    size="720px"
    class="bug-editor-drawer"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form label-position="top" class="bug-editor-form">
      <section v-if="sourceContext" class="bug-editor-context-card">
        <div class="bug-editor-context-title">来源信息</div>
        <div class="bug-editor-context-grid">
          <div class="bug-editor-context-item">
            <div class="bug-editor-context-label">用例编号</div>
            <div class="bug-editor-context-value">{{ sourceContext.caseNo || '-' }}</div>
          </div>
          <div class="bug-editor-context-item">
            <div class="bug-editor-context-label">执行结果</div>
            <div class="bug-editor-context-value">{{ sourceContext.executionStatus || '-' }}</div>
          </div>
          <div class="bug-editor-context-item bug-editor-context-item-full">
            <div class="bug-editor-context-label">用例标题</div>
            <div class="bug-editor-context-value">{{ sourceContext.caseTitle || '-' }}</div>
          </div>
          <div class="bug-editor-context-item bug-editor-context-item-full">
            <div class="bug-editor-context-label">用例模块</div>
            <div class="bug-editor-context-value">{{ sourceContext.modulePath || '-' }}</div>
          </div>
          <div class="bug-editor-context-item bug-editor-context-item-full">
            <div class="bug-editor-context-label">实际结果</div>
            <div class="bug-editor-context-value bug-editor-context-value-block">{{ sourceContext.actualResult || '-' }}</div>
          </div>
        </div>
      </section>

      <el-form-item label="缺陷标题" required class="bug-editor-form-item">
        <el-input v-model="form.title" placeholder="请输入缺陷标题" />
      </el-form-item>

      <div class="bug-editor-meta-row">
        <el-form-item label="优先级" class="bug-editor-form-item">
          <el-segmented v-model="form.priority" :options="priorityOptions" />
        </el-form-item>

        <el-form-item label="严重程度" class="bug-editor-form-item">
          <el-select v-model="form.severity" placeholder="请选择严重程度">
            <el-option
              v-for="item in severityOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </div>

      <div class="bug-editor-meta-row">
        <el-form-item label="负责人" class="bug-editor-form-item">
          <el-select v-model="form.assigneeId" clearable placeholder="请选择负责人">
            <el-option
              v-for="item in users"
              :key="item.id"
              :label="item.displayName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="标签" class="bug-editor-form-item">
          <el-select
            v-model="form.tags"
            multiple
            filterable
            allow-create
            default-first-option
            collapse-tags
            collapse-tags-tooltip
            placeholder="输入后回车创建标签"
          />
        </el-form-item>
      </div>

      <el-form-item label="缺陷描述" required class="bug-editor-form-item">
        <el-input
          v-model="form.description"
          type="textarea"
          :autosize="{ minRows: 10, maxRows: 18 }"
          resize="vertical"
          placeholder="请输入缺陷描述"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="drawer-footer">
        <div class="drawer-submit">
          <el-button @click="emit('update:modelValue', false)">取消</el-button>
          <el-button type="primary" :loading="saving" :disabled="!canSubmit" @click="emit('submit')">
            提交
          </el-button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.bug-editor-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 18px 20px 0;
  align-items: flex-start;
}

.bug-editor-drawer :deep(.el-drawer__body) {
  padding: 12px 20px 0;
}

.bug-editor-drawer :deep(.el-drawer__footer) {
  padding: 16px 20px 20px;
  border-top: 1px solid var(--line-soft);
}

.bug-editor-drawer :deep(.el-input__wrapper),
.bug-editor-drawer :deep(.el-textarea__inner),
.bug-editor-drawer :deep(.el-select__wrapper) {
  border-radius: 10px;
}

.bug-editor-drawer :deep(.el-textarea__inner) {
  line-height: 1.7;
}

.bug-editor-form {
  display: grid;
  gap: 16px;
}

.bug-editor-context-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: #fcfcfd;
}

.bug-editor-context-title {
  font-size: 13px;
  font-weight: 700;
  color: #344054;
}

.bug-editor-context-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.bug-editor-context-item {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.bug-editor-context-item-full {
  grid-column: 1 / -1;
}

.bug-editor-context-label {
  font-size: 12px;
  line-height: 1.5;
  color: #667085;
}

.bug-editor-context-value {
  font-size: 13px;
  line-height: 1.6;
  color: #344054;
  word-break: break-word;
}

.bug-editor-context-value-block {
  white-space: pre-wrap;
}

.bug-editor-meta-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.bug-editor-form-item {
  margin-bottom: 0;
}

.bug-editor-form :deep(.el-form-item__label) {
  margin-bottom: 10px;
  padding: 0;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--text-subtle);
}

.bug-editor-form :deep(.el-form-item__content) {
  min-width: 0;
}

.bug-editor-form :deep(.el-segmented) {
  width: fit-content;
}

.drawer-footer,
.drawer-submit {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 1200px) {
  .bug-editor-context-grid,
  .bug-editor-meta-row {
    grid-template-columns: 1fr;
  }

  .drawer-footer,
  .drawer-submit {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
