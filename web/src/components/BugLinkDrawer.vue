<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import type { ElTable } from 'element-plus'
import type { BugSummary } from '../types/api'
import { formatDateTime } from '../utils/casePresentation'

const props = withDefaults(defineProps<{
  modelValue: boolean
  bugs: BugSummary[]
  keyword: string
  loading?: boolean
  associating?: boolean
}>(), {
  loading: false,
  associating: false,
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'update:keyword', value: string): void
  (event: 'associate', bugIds: number[]): void
}>()

const textLabels = {
  title: '\u5173\u8054\u7f3a\u9677',
  placeholder: '\u901a\u8fc7\u7f3a\u9677\u7f16\u53f7 / \u7f3a\u9677\u540d\u79f0\u641c\u7d22',
  empty: '\u6682\u65e0\u53ef\u5173\u8054\u7f3a\u9677',
  bugNo: '\u7f3a\u9677\u7f16\u53f7',
  bugName: '\u7f3a\u9677\u540d\u79f0',
  status: '\u72b6\u6001',
  priority: '\u4f18\u5148\u7ea7',
  severity: '\u4e25\u91cd\u7a0b\u5ea6',
  assignee: '\u5904\u7406\u4eba',
  reporter: '\u521b\u5efa\u4eba',
  createdAt: '\u521b\u5efa\u65f6\u95f4',
  cancel: '\u53d6\u6d88',
  associate: '\u5173\u8054',
} as const

const tableRef = ref<InstanceType<typeof ElTable> | null>(null)
const selectedBugIds = ref<number[]>([])

const statusLabelMap: Record<string, string> = {
  TODO: '\u5f85\u6307\u6d3e',
  ASSIGNED: '\u5df2\u6307\u6d3e',
  IN_PROGRESS: '\u5904\u7406\u4e2d',
  PENDING_VERIFY: '\u5f85\u9a8c\u8bc1',
  CLOSED: '\u5df2\u5173\u95ed',
  REJECTED: '\u5df2\u62d2\u7edd',
}

const priorityLabelMap: Record<string, string> = {
  P0: 'P0',
  P1: 'P1',
  P2: 'P2',
  P3: 'P3',
}

const severityLabelMap: Record<string, string> = {
  CRITICAL: '\u81f4\u547d',
  HIGH: '\u9ad8',
  MEDIUM: '\u4e2d',
  LOW: '\u4f4e',
}

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const keywordValue = computed({
  get: () => props.keyword,
  set: value => emit('update:keyword', value),
})

const canSubmit = computed(() => selectedBugIds.value.length > 0 && !props.associating)

function handleSelectionChange(rows: BugSummary[]) {
  selectedBugIds.value = rows.map(row => row.id)
}

function handleClose() {
  visible.value = false
}

function handleAssociate() {
  if (!selectedBugIds.value.length) {
    return
  }
  emit('associate', selectedBugIds.value)
}

function formatStatus(status: string) {
  return statusLabelMap[status] ?? status
}

function formatPriority(priority: string) {
  return priorityLabelMap[priority] ?? priority
}

function formatSeverity(severity: string) {
  return severityLabelMap[severity] ?? severity
}

watch(() => props.modelValue, (value) => {
  if (!value) {
    selectedBugIds.value = []
    tableRef.value?.clearSelection()
  }
})

watch(() => props.bugs, () => {
  selectedBugIds.value = selectedBugIds.value.filter(id => props.bugs.some(item => item.id === id))
})
</script>

<template>
  <el-drawer
    v-model="visible"
    :title="textLabels.title"
    size="1198px"
    class="bug-link-drawer"
    destroy-on-close
  >
    <div class="bug-link-drawer-body">
      <div class="bug-link-drawer-toolbar">
        <el-input
          v-model="keywordValue"
          :placeholder="textLabels.placeholder"
          clearable
          :prefix-icon="Search"
        />
      </div>

      <div class="bug-link-drawer-table">
        <el-table
          ref="tableRef"
          v-loading="loading"
          :data="bugs"
          size="large"
          height="100%"
          :empty-text="textLabels.empty"
          row-key="id"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="52" reserve-selection />
          <el-table-column prop="bugNo" :label="textLabels.bugNo" width="160" />
          <el-table-column prop="title" :label="textLabels.bugName" min-width="240" show-overflow-tooltip />
          <el-table-column :label="textLabels.status" width="110">
            <template #default="{ row }">
              {{ formatStatus(row.status) }}
            </template>
          </el-table-column>
          <el-table-column :label="textLabels.priority" width="90">
            <template #default="{ row }">
              {{ formatPriority(row.priority) }}
            </template>
          </el-table-column>
          <el-table-column :label="textLabels.severity" width="110">
            <template #default="{ row }">
              {{ formatSeverity(row.severity) }}
            </template>
          </el-table-column>
          <el-table-column prop="assigneeName" :label="textLabels.assignee" width="120" />
          <el-table-column prop="reporterName" :label="textLabels.reporter" width="120" />
          <el-table-column :label="textLabels.createdAt" width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <template #footer>
      <div class="bug-link-drawer-footer">
        <el-button @click="handleClose">{{ textLabels.cancel }}</el-button>
        <el-button type="primary" :disabled="!canSubmit" :loading="associating" @click="handleAssociate">
          {{ textLabels.associate }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.bug-link-drawer {
  --el-drawer-padding-primary: 20px;
}

.bug-link-drawer-body {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.bug-link-drawer-toolbar {
  margin-bottom: 16px;
}

.bug-link-drawer-table {
  flex: 1;
  min-height: 420px;
}

.bug-link-drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
